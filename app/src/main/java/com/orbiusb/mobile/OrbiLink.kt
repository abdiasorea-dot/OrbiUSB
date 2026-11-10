package com.orbiusb.mobile

import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Protocolo OrbiUSB sobre el canal AOA.
 * Cabecera: "ORBI" + tipo (1 byte) + longitud del contenido (4 bytes, big-endian).
 * La lectura se hace en un hilo propio; los callbacks del listener llegan en ese hilo.
 */
class OrbiLink(
    descriptor: ParcelFileDescriptor,
    private val info: () -> JSONObject,
    private val listener: Listener
) {
    interface Listener {
        fun onHello(peer: JSONObject)
        fun onImage(bytes: ByteArray)
        fun onMirror(on: Boolean)
        fun onClosed(source: OrbiLink)
    }

    companion object {
        const val T_HELLO = 1
        const val T_HELLO_ACK = 2
        const val T_IMAGE = 3
        const val T_IMAGE_ACK = 4
        const val T_PING = 5
        const val T_PONG = 6
        const val T_MIRROR = 7
        private const val HEADER = 9
        private const val MAX_PAYLOAD = 16 * 1024 * 1024
        // Siempre se lee con un búfer de 16 KB: si fuera menor, el controlador AOA descarta el resto del paquete.
        private const val READ_SIZE = 16384
    }

    private val input = FileInputStream(descriptor.fileDescriptor)
    private val output = FileOutputStream(descriptor.fileDescriptor)
    @Volatile private var running = true
    private var buf = ByteArray(64 * 1024)
    private var len = 0
    private val thread = Thread({ loop() }, "orbiusb-link")

    fun start() = thread.start()

    fun close() {
        running = false
    }

    @Synchronized
    fun send(type: Int, payload: ByteArray = ByteArray(0)) {
        val frame = ByteBuffer.allocate(HEADER + payload.size)
        frame.put('O'.code.toByte()).put('R'.code.toByte()).put('B'.code.toByte()).put('I'.code.toByte())
        frame.put(type.toByte())
        frame.putInt(payload.size)
        frame.put(payload)
        output.write(frame.array())
    }

    private fun loop() {
        val chunk = ByteArray(READ_SIZE)
        try {
            while (running) {
                val n = input.read(chunk)
                if (n < 0) break
                if (n > 0) {
                    append(chunk, n)
                    drain()
                }
            }
        } catch (_: IOException) {
        } finally {
            running = false
            listener.onClosed(this)
        }
    }

    private fun append(src: ByteArray, n: Int) {
        if (len + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, len + n))
        System.arraycopy(src, 0, buf, len, n)
        len += n
    }

    private fun drain() {
        while (len >= HEADER) {
            if (buf[0] != 'O'.code.toByte() || buf[1] != 'R'.code.toByte() ||
                buf[2] != 'B'.code.toByte() || buf[3] != 'I'.code.toByte()) {
                throw IOException("Datos inválidos en el canal USB")
            }
            val type = buf[4].toInt() and 0xFF
            val size = ByteBuffer.wrap(buf, 5, 4).int
            if (size < 0 || size > MAX_PAYLOAD) throw IOException("Tamaño de paquete inválido")
            if (len < HEADER + size) return
            val payload = buf.copyOfRange(HEADER, HEADER + size)
            System.arraycopy(buf, HEADER + size, buf, 0, len - HEADER - size)
            len -= HEADER + size
            handle(type, payload)
        }
    }

    private fun handle(type: Int, payload: ByteArray) {
        when (type) {
            T_HELLO -> {
                val peer = try { JSONObject(String(payload, Charsets.UTF_8)) } catch (_: Exception) { JSONObject() }
                send(T_HELLO_ACK, info().toString().toByteArray(Charsets.UTF_8))
                listener.onHello(peer)
            }
            T_PING -> send(T_PONG)
            T_IMAGE -> listener.onImage(payload)
            T_MIRROR -> {
                val flags = try { JSONObject(String(payload, Charsets.UTF_8)) } catch (_: Exception) { JSONObject() }
                listener.onMirror(flags.optBoolean("on", false))
            }
        }
    }
}
