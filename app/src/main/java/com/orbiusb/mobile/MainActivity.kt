package com.orbiusb.mobile

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("orbiusb_settings", Context.MODE_PRIVATE) }
    private val bg = 0xFF0D1119.toInt()
    private val panel = 0xFF171E2A.toInt()
    private val panel2 = 0xFF202B3B.toInt()
    private val white = 0xFFF2F6FC.toInt()
    private val muted = 0xFFA6B4C8.toInt()
    private val blue = 0xFF388BFF.toInt()
    private val green = 0xFF55D69E.toInt()
    private lateinit var content: LinearLayout
    private lateinit var navHome: Button
    private lateinit var navSettings: Button
    private var selectedPage = "home"
    private var selectedOrientation = "Automática"
    private var selectedQuality = 3
    private lateinit var resolutionSpinner: Spinner
    private lateinit var qualityValue: TextView
    private lateinit var qualitySeek: SeekBar

    private lateinit var usbSession: UsbAccessorySession
    private var link: OrbiLink? = null
    private val ui = Handler(Looper.getMainLooper())
    private var connected = false
    private var statusTitle = "Esperando conexión USB"
    private var statusDetail = "Conecta la tablet al PC y abre OrbiUSB en el PC."
    private var lastBitmap: Bitmap? = null
    private var shellRoot: View? = null
    private var mirrorView: ImageView? = null
    private var mirrorOn = false
    private var statusTitleView: TextView? = null
    private var statusDetailView: TextView? = null
    private var imageView: ImageView? = null
    private val poll = object : Runnable {
        override fun run() {
            if (::usbSession.isInitialized) usbSession.refresh()
            ui.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedOrientation = prefs.getString("orientation", "Automática") ?: "Automática"
        selectedQuality = prefs.getInt("quality", 3)
        buildShell()
        showHome()
        usbSession = UsbAccessorySession(this) { isConnected, message ->
            runOnUiThread { onUsbState(isConnected, message) }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::usbSession.isInitialized) usbSession.refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::usbSession.isInitialized) usbSession.start()
        ui.postDelayed(poll, 1500)
    }

    override fun onPause() {
        ui.removeCallbacks(poll)
        if (::usbSession.isInitialized) usbSession.stop()
        super.onPause()
    }

    private fun deviceInfo(): JSONObject {
        val dm = resources.displayMetrics
        return JSONObject()
            .put("app", "OrbiUSB-Android")
            .put("version", "0.3.0")
            .put("protocol", 1)
            .put("model", Build.MODEL)
            .put("width", dm.widthPixels)
            .put("height", dm.heightPixels)
    }

    private fun onUsbState(isConnected: Boolean, message: String) {
        if (isConnected) {
            if (link == null) {
                val descriptor = usbSession.fileDescriptor() ?: return
                val newLink = OrbiLink(descriptor, { deviceInfo() }, linkListener)
                link = newLink
                setStatus(false, "Canal USB abierto", "Esperando que el PC se identifique…")
                newLink.start()
            }
        } else {
            setMirror(false)
            link?.close()
            link = null
            setStatus(false, "En espera de conexión", message)
        }
    }

    private val linkListener = object : OrbiLink.Listener {
        override fun onHello(peer: JSONObject) {
            runOnUiThread {
                setStatus(true, "Conectado al PC", "Identificado: ${peer.optString("app", "OrbiUSB-PC")} ${peer.optString("version", "")}")
            }
        }

        override fun onMirror(on: Boolean) {
            runOnUiThread { setMirror(on) }
        }

        override fun onImage(bytes: ByteArray) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            runOnUiThread {
                if (mirrorOn) {
                    mirrorView?.setImageBitmap(bitmap)
                } else {
                    lastBitmap = bitmap
                    imageView?.setImageBitmap(bitmap)
                    imageView?.visibility = View.VISIBLE
                }
            }
            try {
                link?.send(OrbiLink.T_IMAGE_ACK, JSONObject().put("w", bitmap.width).put("h", bitmap.height).toString().toByteArray(Charsets.UTF_8))
            } catch (_: java.io.IOException) {
            }
        }

        override fun onClosed(source: OrbiLink) {
            runOnUiThread {
                if (link === source) {
                    link = null
                    setMirror(false)
                    usbSession.close()
                    setStatus(false, "Conexión USB cerrada", "Vuelve a conectar la tablet o reinicia OrbiUSB en el PC.")
                }
            }
        }
    }

    /** Pantalla completa para el duplicado; al salir se restaura la interfaz normal. */
    private fun setMirror(on: Boolean) {
        if (on == mirrorOn) return
        mirrorOn = on
        if (on) {
            val view = ImageView(this).apply {
                setBackgroundColor(0xFF000000.toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            mirrorView = view
            setContentView(view)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        } else {
            mirrorView = null
            shellRoot?.let { setContentView(it) }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mirrorOn) {
            setMirror(false)
            val current = link
            Thread {
                try {
                    current?.send(OrbiLink.T_MIRROR, JSONObject().put("on", false).toString().toByteArray(Charsets.UTF_8))
                } catch (_: java.io.IOException) {
                }
            }.start()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun setStatus(ok: Boolean, title: String, detail: String) {
        connected = ok
        statusTitle = title
        statusDetail = detail
        renderStatus()
    }

    private fun renderStatus() {
        statusTitleView?.text = (if (connected) "●   " else "○   ") + statusTitle
        statusTitleView?.setTextColor(if (connected) green else white)
        statusDetailView?.text = statusDetail
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(22))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(12))
            setBackgroundColor(0xFF121823.toInt())
        }
        navHome = navButton("⌂  Conexión") { showHome() }
        navSettings = navButton("⚙  Ajustes") { showSettings() }
        nav.addView(navHome, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        nav.addView(navSettings, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(nav)
        shellRoot = root
        setContentView(root)
    }

    private fun showHome() {
        selectedPage = "home"
        updateNav()
        content.removeAllViews()
        content.addView(label("ORB I USB", 12f, blue, true))
        content.addView(label("En espera de conexión", 27f, white, true, top = 8))
        content.addView(label("Conecta el cable USB de tu tablet al PC y abre OrbiUSB en el PC. Mantén esta app abierta.", 14f, muted, false, top = 8))

        val status = card()
        status.addView(label("ESTADO DEL DISPOSITIVO", 11f, muted, true))
        statusTitleView = label("", 16f, white, true, top = 14)
        statusDetailView = label("", 13f, muted, false, top = 8)
        status.addView(statusTitleView)
        status.addView(statusDetailView)
        renderStatus()
        content.addView(status, marginParams(top = 26))
        imageView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(panel2)
            visibility = if (lastBitmap != null) View.VISIBLE else View.GONE
            lastBitmap?.let { setImageBitmap(it) }
        }
        content.addView(imageView, marginParams(top = 18))

        content.addView(label("Configuración actual", 18f, white, true, top = 26))
        val summary = card()
        summary.addView(summaryRow("Resolución", prefs.getString("resolution", "1280 × 800") ?: "1280 × 800"))
        summary.addView(summaryRow("Orientación", selectedOrientation))
        summary.addView(summaryRow("Calidad", qualityLabel(selectedQuality)))
        content.addView(summary, marginParams(top = 12))
        content.addView(actionButton("Configurar pantalla", blue) { showSettings() }, marginParams(top = 18))
        content.addView(label("Las preferencias se guardan en este dispositivo. La transmisión de vídeo aún está en desarrollo; por ahora el PC puede enviar una imagen.", 12f, muted, false, top = 18))
    }

    private fun showSettings() {
        selectedPage = "settings"
        updateNav()
        content.removeAllViews()
        content.addView(label("AJUSTES", 12f, blue, true))
        content.addView(label("Pantalla", 27f, white, true, top = 8))
        content.addView(label("Personaliza cómo quieres recibir la imagen del PC.", 14f, muted, false, top = 6))

        val resolutionCard = card()
        resolutionCard.addView(label("Resolución de salida", 16f, white, true))
        resolutionCard.addView(label("Elige el tamaño de imagen que se enviará a la tablet.", 13f, muted, false, top = 5))
        resolutionSpinner = Spinner(this)
        val resolutions = listOf("1280 × 800", "1920 × 1080", "1600 × 900", "1280 × 720", "1024 × 768", "Automática")
        resolutionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resolutions)
        val currentResolution = prefs.getString("resolution", "1280 × 800") ?: "1280 × 800"
        resolutionSpinner.setSelection(resolutions.indexOf(currentResolution).coerceAtLeast(0))
        resolutionCard.addView(resolutionSpinner, marginParams(top = 12))
        content.addView(resolutionCard, marginParams(top = 22))

        val orientationCard = card()
        orientationCard.addView(label("Orientación", 16f, white, true))
        orientationCard.addView(label("Preferencia para la imagen recibida.", 13f, muted, false, top = 5))
        val orientationRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Automática", "Horizontal", "Vertical").forEach { option ->
            val button = actionButton(option, if (selectedOrientation == option) blue else panel2) {
                selectedOrientation = option
                showSettings()
            }
            orientationRow.addView(button, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                topMargin = dp(12); if (option != "Vertical") rightMargin = dp(7)
            })
        }
        orientationCard.addView(orientationRow)
        content.addView(orientationCard, marginParams(top = 12))

        val qualityCard = card()
        val qualityHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        qualityHeader.addView(label("Calidad de imagen", 16f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        qualityValue = label(qualityLabel(selectedQuality), 13f, blue, true)
        qualityHeader.addView(qualityValue)
        qualityCard.addView(qualityHeader)
        qualityCard.addView(label("Mayor calidad consume más ancho de banda y recursos.", 13f, muted, false, top = 5))
        qualitySeek = SeekBar(this).apply {
            max = 4
            progress = selectedQuality - 1
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            thumbTintList = android.content.res.ColorStateList.valueOf(blue)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedQuality = progress + 1
                    qualityValue.text = qualityLabel(selectedQuality)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        qualityCard.addView(qualitySeek, marginParams(top = 8))
        val scale = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scale.addView(label("Ahorro", 12f, muted, false), LinearLayout.LayoutParams(0, -2, 1f))
        scale.addView(label("Máxima", 12f, muted, false))
        qualityCard.addView(scale)
        content.addView(qualityCard, marginParams(top = 12))

        content.addView(actionButton("Guardar preferencias", blue) {
            prefs.edit()
                .putString("resolution", resolutionSpinner.selectedItem.toString())
                .putString("orientation", selectedOrientation)
                .putInt("quality", selectedQuality)
                .apply()
            Toast.makeText(this, "Preferencias guardadas", Toast.LENGTH_SHORT).show()
            showSettings()
        }, marginParams(top = 20))
        content.addView(label("Nota: estos ajustes ya se pueden seleccionar y guardar, pero todavía no modifican una transmisión real. Eso se conectará al implementar el motor de vídeo.", 12f, muted, false, top = 14))
    }

    private fun updateNav() {
        if (!::navHome.isInitialized) return
        navHome.setBackgroundColor(if (selectedPage == "home") blue else panel2)
        navSettings.setBackgroundColor(if (selectedPage == "settings") blue else panel2)
    }

    private fun navButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setTextColor(white)
        setBackgroundColor(panel2)
        setOnClickListener { action() }
    }

    private fun actionButton(text: String, color: Int, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setTextColor(white)
        setBackgroundColor(color)
        setOnClickListener { action() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(panel)
    }

    private fun summaryRow(title: String, value: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(title, 14f, muted, false), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label(value, 14f, white, true))
        return row.apply { setPadding(0, dp(7), 0, dp(7)) }
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean, top: Int = 0) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (top > 0) setPadding(0, dp(top), 0, 0)
    }

    private fun marginParams(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun qualityLabel(value: Int) = when (value) { 1 -> "Muy baja"; 2 -> "Baja"; 3 -> "Equilibrada"; 4 -> "Alta"; else -> "Máxima" }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
