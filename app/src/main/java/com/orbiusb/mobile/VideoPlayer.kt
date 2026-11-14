package com.orbiusb.mobile

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Decodifica H.264 con MediaCodec (hardware) y lo dibuja directamente en la superficie.
 * Cada paquete: 1 byte (1 = fotograma clave) + una unidad de acceso completa en formato Annex-B.
 * Si el decodificador va retrasado se descarta lo pendiente y se espera al siguiente fotograma clave.
 */
class VideoPlayer(
    private val surface: Surface,
    private val onSize: (Int, Int) -> Unit,
    private val onFailure: (String) -> Unit
) {
    private val queue = ArrayBlockingQueue<ByteArray>(8)
    @Volatile private var running = true
    @Volatile private var needKey = true
    private var codec: MediaCodec? = null
    private var gotOutput = false
    private val thread = Thread({ loop() }, "orbiusb-h264")

    fun start() = thread.start()

    fun stop() {
        running = false
        thread.interrupt()
    }

    fun submit(payload: ByteArray) {
        if (payload.size < 2) return
        val key = payload[0].toInt() == 1
        if (needKey && !key) return
        if (!queue.offer(payload)) {
            queue.clear()
            needKey = true
            if (!key) return
            queue.offer(payload)
        }
        if (key) needKey = false
    }

    private fun loop() {
        var deadline = 0L
        try {
            while (running) {
                val payload = queue.poll(50, TimeUnit.MILLISECONDS)
                if (payload == null) {
                    drain(0)
                    continue
                }
                if (codec == null) {
                    if (payload[0].toInt() != 1) continue
                    codec = create()
                    deadline = System.currentTimeMillis() + 4000
                }
                if (!feed(payload)) needKey = true
                drain(8000)
                if (!gotOutput && System.currentTimeMillis() > deadline) {
                    throw IllegalStateException("el decodificador no produce imagen")
                }
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            if (running) onFailure(e.message ?: e.javaClass.simpleName)
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            codec = null
        }
    }

    private fun create(): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720)
        format.setInteger(MediaFormat.KEY_MAX_WIDTH, 1920)
        format.setInteger(MediaFormat.KEY_MAX_HEIGHT, 1088)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
        if (Build.VERSION.SDK_INT >= 23) format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        if (Build.VERSION.SDK_INT >= 30) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        decoder.configure(format, surface, null, 0)
        decoder.start()
        return decoder
    }

    private fun feed(payload: ByteArray): Boolean {
        val c = codec ?: return false
        val index = c.dequeueInputBuffer(20_000)
        if (index < 0) return false
        val buffer = c.getInputBuffer(index) ?: return false
        val size = payload.size - 1
        if (size > buffer.capacity()) return false
        buffer.clear()
        buffer.put(payload, 1, size)
        c.queueInputBuffer(index, 0, size, System.nanoTime() / 1000, 0)
        return true
    }

    private fun drain(firstTimeoutUs: Long) {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        var timeout = firstTimeoutUs
        while (true) {
            val out = c.dequeueOutputBuffer(info, timeout)
            timeout = 0
            when {
                out >= 0 -> {
                    gotOutput = true
                    c.releaseOutputBuffer(out, true) // se pinta de inmediato
                }
                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = c.outputFormat
                    var w = f.getInteger(MediaFormat.KEY_WIDTH)
                    var h = f.getInteger(MediaFormat.KEY_HEIGHT)
                    if (f.containsKey("crop-left") && f.containsKey("crop-right") &&
                        f.containsKey("crop-top") && f.containsKey("crop-bottom")) {
                        w = f.getInteger("crop-right") - f.getInteger("crop-left") + 1
                        h = f.getInteger("crop-bottom") - f.getInteger("crop-top") + 1
                    }
                    onSize(w, h)
                }
                else -> return
            }
        }
    }
}
