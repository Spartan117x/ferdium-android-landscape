package com.hiram.ferdium

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

class MainActivity : Activity() {

    companion object {
        const val TAG = "Ferdium"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val PREFS_NAME = "ferdium_prefs"
        const val KEY_SERVICES = "services_json_v3"
    }

    data class ServiceItem(
        val id: String,
        val serviceType: String,
        var name: String,
        var monogram: String,
        var url: String,
        var domain: String,
        val color: Int,
        var isDesktopUA: Boolean,
        var zoom: Int,
        val profileId: String
    )

    data class Preset(
        val type: String,
        val name: String,
        val monogram: String,
        val url: String,
        val domain: String,
        val color: Int,
        val defaultDesktopUA: Boolean,
        val defaultZoom: Int
    )

    private val presets = listOf(
        Preset("whatsapp", "WhatsApp", "WA", "https://web.whatsapp.com", "whatsapp.com", Color.parseColor("#25D366"), true, 75),
        Preset("telegram", "Telegram", "TG", "https://web.telegram.org/a/", "telegram.org", Color.parseColor("#229ED9"), true, 80),
        Preset("messenger", "Messenger", "MS", "https://www.messenger.com/", "messenger.com", Color.parseColor("#0084FF"), true, 80),
        Preset("instagram", "Instagram", "IG", "https://www.instagram.com/accounts/login/", "instagram.com", Color.parseColor("#E1306C"), true, 80),
        Preset("gmail", "Gmail", "GM", "https://mail.google.com", "google.com", Color.parseColor("#EA4335"), false, 85),
        Preset("facebook", "Facebook", "FB", "https://www.facebook.com/login", "facebook.com", Color.parseColor("#1877F2"), true, 75),
        Preset("tiktok", "TikTok", "TT", "https://www.tiktok.com/login", "tiktok.com", Color.parseColor("#FE2C55"), true, 70),
        Preset("x", "X", "X", "https://x.com/i/flow/login", "x.com", Color.parseColor("#1D9BF0"), true, 80),
        Preset("drive", "Drive", "GD", "https://drive.google.com", "google.com", Color.parseColor("#34A853"), false, 80),
        Preset("discord", "Discord", "DC", "https://discord.com/login", "discord.com", Color.parseColor("#5865F2"), true, 75),
        Preset("slack", "Slack", "SL", "https://app.slack.com/client", "slack.com", Color.parseColor("#4A154B"), true, 75),
        Preset("chatgpt", "ChatGPT", "AI", "https://chatgpt.com", "openai.com", Color.parseColor("#10A37F"), false, 85),
        Preset("custom", "Personalizado", "WEB", "https://", "", Color.parseColor("#7C3AED"), false, 80)
    )

    private val services = ArrayList<ServiceItem>()
    private val webViews = ArrayList<WebView?>()
    private val tabViews = ArrayList<LinearLayout>()

    private var currentIndex = 0
    private var mobileUA: String = ""

    private lateinit var tabBar: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var tvStatus: TextView
    private lateinit var btnDesktop: Button
    private lateinit var btnBack: Button
    private lateinit var btnReload: Button
    private lateinit var btnZoomOut: Button
    private lateinit var btnZoomIn: Button
    private lateinit var tvZoom: TextView
    private lateinit var btnAddService: Button

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Modo horizontal por defecto para trabajar en formato apaisado
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Abarcar toda la pantalla / ventana sin letterboxing en cutouts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_main)

        tabBar = findViewById(R.id.tabBar)
        webContainer = findViewById(R.id.webContainer)
        tvStatus = findViewById(R.id.tvStatus)
        btnDesktop = findViewById(R.id.btnDesktop)
        btnBack = findViewById(R.id.btnBack)
        btnReload = findViewById(R.id.btnReload)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        tvZoom = findViewById(R.id.tvZoom)
        btnAddService = findViewById(R.id.btnAddService)

        // Habilitar cookies globalmente
        CookieManager.getInstance().setAcceptCookie(true)

        // Habilitar depuración de WebView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Cargar servicios guardados o los 9 por defecto
        services.clear()
        services.addAll(loadServices())
        for (i in services.indices) {
            webViews.add(null)
        }

        // Construir barra de pestañas
        rebuildTabs()

        // Botón para añadir nuevas ventanas / cuentas
        btnAddService.setOnClickListener {
            showAddServiceDialog()
        }

        btnReload.setOnClickListener {
            webViews.getOrNull(currentIndex)?.reload()
        }

        btnBack.setOnClickListener {
            val currentWebView = webViews.getOrNull(currentIndex)
            if (currentWebView != null && currentWebView.canGoBack()) {
                currentWebView.goBack()
            }
        }

        btnDesktop.setOnClickListener {
            toggleDesktopMode()
        }

        btnZoomOut.setOnClickListener {
            adjustZoom(-5)
        }

        btnZoomIn.setOnClickListener {
            adjustZoom(5)
        }

        tvZoom.setOnClickListener {
            resetZoom()
        }

        // Mostrar servicio inicial (o el especificado en el Intent)
        val targetIndex = intent?.getIntExtra("service_index", 0) ?: 0
        showService(if (targetIndex in services.indices) targetIndex else 0)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val targetIndex = intent?.getIntExtra("service_index", -1) ?: -1
        if (targetIndex in services.indices) {
            showService(targetIndex)
        }
    }

    private fun getDefaultServices(): List<ServiceItem> {
        return listOf(
            ServiceItem("wa_default", "whatsapp", "WhatsApp", "WA", "https://web.whatsapp.com", "whatsapp.com", Color.parseColor("#25D366"), true, 75, "profile_wa_default"),
            ServiceItem("tg_default", "telegram", "Telegram", "TG", "https://web.telegram.org/a/", "telegram.org", Color.parseColor("#229ED9"), true, 80, "profile_tg_default"),
            ServiceItem("ms_default", "messenger", "Messenger", "MS", "https://www.messenger.com/", "messenger.com", Color.parseColor("#0084FF"), true, 80, "profile_ms_default"),
            ServiceItem("ig_default", "instagram", "Instagram", "IG", "https://www.instagram.com/accounts/login/", "instagram.com", Color.parseColor("#E1306C"), true, 80, "profile_ig_default"),
            ServiceItem("gm_default", "gmail", "Gmail", "GM", "https://mail.google.com", "google.com", Color.parseColor("#EA4335"), false, 85, "profile_gm_default"),
            ServiceItem("fb_default", "facebook", "Facebook", "FB", "https://www.facebook.com/login", "facebook.com", Color.parseColor("#1877F2"), true, 75, "profile_fb_default"),
            ServiceItem("tt_default", "tiktok", "TikTok", "TT", "https://www.tiktok.com/login", "tiktok.com", Color.parseColor("#FE2C55"), true, 70, "profile_tt_default"),
            ServiceItem("x_default", "x", "X", "X", "https://x.com/i/flow/login", "x.com", Color.parseColor("#1D9BF0"), true, 80, "profile_x_default"),
            ServiceItem("gd_default", "drive", "Drive", "GD", "https://drive.google.com", "google.com", Color.parseColor("#34A853"), false, 80, "profile_gd_default")
        )
    }

    private fun saveServices() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            for (s in services) {
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("serviceType", s.serviceType)
                    put("name", s.name)
                    put("monogram", s.monogram)
                    put("url", s.url)
                    put("domain", s.domain)
                    put("color", s.color)
                    put("isDesktopUA", s.isDesktopUA)
                    put("zoom", s.zoom)
                    put("profileId", s.profileId)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_SERVICES, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving services: ${e.message}")
        }
    }

    private fun loadServices(): List<ServiceItem> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SERVICES, null)
        val list = ArrayList<ServiceItem>()
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val svcType = obj.optString("serviceType", "custom")
                    val rawZoom = obj.getInt("zoom")
                    val finalZoom = if (svcType == "whatsapp" && rawZoom <= 65) 75 else rawZoom
                    list.add(
                        ServiceItem(
                            id = obj.optString("id", "svc_$i"),
                            serviceType = svcType,
                            name = obj.getString("name"),
                            monogram = obj.getString("monogram"),
                            url = obj.getString("url"),
                            domain = obj.optString("domain", ""),
                            color = obj.getInt("color"),
                            isDesktopUA = obj.getBoolean("isDesktopUA"),
                            zoom = finalZoom,
                            profileId = obj.optString("profileId", "profile_svc_$i")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved services: ${e.message}")
                list.clear()
            }
        }

        if (list.isEmpty()) {
            val defaults = ArrayList(getDefaultServices())
            // Migrar si existían servicios personalizados de versiones previas
            val oldV2 = prefs.getString("services_json_v2", null)
            if (!oldV2.isNullOrEmpty()) {
                try {
                    val oldArray = JSONArray(oldV2)
                    for (i in 0 until oldArray.length()) {
                        val obj = oldArray.getJSONObject(i)
                        val id = obj.optString("id", "")
                        if (!id.endsWith("_default")) {
                            defaults.add(
                                ServiceItem(
                                    id = id,
                                    serviceType = obj.optString("serviceType", "custom"),
                                    name = obj.getString("name"),
                                    monogram = obj.getString("monogram"),
                                    url = obj.getString("url"),
                                    domain = obj.optString("domain", ""),
                                    color = obj.getInt("color"),
                                    isDesktopUA = obj.getBoolean("isDesktopUA"),
                                    zoom = obj.getInt("zoom"),
                                    profileId = obj.optString("profileId", "profile_custom_$i")
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
            try {
                val array = JSONArray()
                for (s in defaults) {
                    val obj = JSONObject().apply {
                        put("id", s.id)
                        put("serviceType", s.serviceType)
                        put("name", s.name)
                        put("monogram", s.monogram)
                        put("url", s.url)
                        put("domain", s.domain)
                        put("color", s.color)
                        put("isDesktopUA", s.isDesktopUA)
                        put("zoom", s.zoom)
                        put("profileId", s.profileId)
                    }
                    array.put(obj)
                }
                prefs.edit().putString(KEY_SERVICES, array.toString()).apply()
            } catch (_: Exception) {}
            return defaults
        }
        return list
    }

    private fun toggleDesktopMode() {
        if (currentIndex !in services.indices) return
        val service = services[currentIndex]
        service.isDesktopUA = !service.isDesktopUA
        saveServices()

        val currentWebView = webViews.getOrNull(currentIndex)
        if (currentWebView != null) {
            currentWebView.settings.userAgentString = if (service.isDesktopUA) DESKTOP_UA else mobileUA
            updateDesktopButton()
            currentWebView.reload()
            tvStatus.text = "${service.name}: " + if (service.isDesktopUA) "PC ON" else "MÓVIL"
        }
    }

    private fun adjustZoom(delta: Int) {
        if (currentIndex !in services.indices) return
        val service = services[currentIndex]
        val newZoom = (service.zoom + delta).coerceIn(45, 130)
        service.zoom = newZoom
        saveServices()
        applyZoom(webViews.getOrNull(currentIndex), newZoom, service.serviceType)
        updateZoomLabel()
    }

    private fun resetZoom() {
        if (currentIndex !in services.indices) return
        val service = services[currentIndex]
        val preset = presets.find { it.type == service.serviceType }
        val defZoom = preset?.defaultZoom ?: 75
        service.zoom = defZoom
        saveServices()
        applyZoom(webViews.getOrNull(currentIndex), defZoom, service.serviceType)
        updateZoomLabel()
    }

    private fun updateZoomLabel() {
        if (currentIndex in services.indices) {
            tvZoom.text = "${services[currentIndex].zoom}%"
        }
    }

    private fun applyZoom(webView: WebView?, zoomPercent: Int, type: String = "") {
        if (webView == null) return
        val factor = zoomPercent / 100.0

        try {
            webView.settings.textZoom = zoomPercent
        } catch (_: Exception) {}

        val serviceType = if (type.isNotEmpty()) type else if (currentIndex in services.indices) services[currentIndex].serviceType else ""
        val isWhatsApp = serviceType == "whatsapp"

        val js = if (isWhatsApp) {
            """
            (function() {
                try {
                    var factor = ${factor};
                    var css = 'html, body { ' +
                              '  width: 100% !important; ' +
                              '  height: 100% !important; ' +
                              '  margin: 0 !important; ' +
                              '  padding: 0 !important; ' +
                              '  overflow-x: hidden !important; ' +
                              '  overflow-y: auto !important; ' +
                              '} ' +
                              'body { ' +
                              '  zoom: ' + factor + ' !important; ' +
                              '  -webkit-text-size-adjust: ${zoomPercent}% !important; ' +
                              '} ' +
                              '#wa-popovers-bucket:empty { ' +
                              '  height: 0px !important; ' +
                              '  min-height: 0px !important; ' +
                              '  max-height: 0px !important; ' +
                              '  flex: 0 0 auto !important; ' +
                              '} ' +
                              '.app-wrapper-web { ' +
                              '  width: 100% !important; ' +
                              '  height: 100% !important; ' +
                              '  min-height: 100% !important; ' +
                              '} ' +
                              'div.xe4h88v, div[tabindex=\"-1\"].two { ' +
                              '  height: 100% !important; ' +
                              '  min-height: 100% !important; ' +
                              '  flex: 1 1 auto !important; ' +
                              '}';
                    var s = document.getElementById('ferdium-zoom-style');
                    if (!s) {
                        s = document.createElement('style');
                        s.id = 'ferdium-zoom-style';
                        (document.head || document.documentElement).appendChild(s);
                    }
                    s.innerHTML = css;

                    var vp = document.querySelector('meta[name="viewport"]');
                    if (vp) {
                        var c = vp.getAttribute('content') || '';
                        if (c.indexOf('user-scalable=no') !== -1) {
                            vp.setAttribute('content', c.replace(/user-scalable\s*=\s*no/g, 'user-scalable=yes') + ', maximum-scale=5.0');
                        }
                    }
                } catch(e) {}
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    var factor = ${factor};
                    var s = document.getElementById('ferdium-zoom-style');
                    if (factor !== 1.0) {
                        var css = 'body { ' +
                                  '  zoom: ' + factor + ' !important; ' +
                                  '  -webkit-text-size-adjust: ${zoomPercent}% !important; ' +
                                  '}';
                        if (!s) {
                            s = document.createElement('style');
                            s.id = 'ferdium-zoom-style';
                            (document.head || document.documentElement).appendChild(s);
                        }
                        s.innerHTML = css;
                    } else if (s) {
                        s.remove();
                    }

                    var vp = document.querySelector('meta[name="viewport"]');
                    if (vp) {
                        var c = vp.getAttribute('content') || '';
                        if (c.indexOf('user-scalable=no') !== -1) {
                            vp.setAttribute('content', c.replace(/user-scalable\s*=\s*no/g, 'user-scalable=yes') + ', maximum-scale=5.0');
                        }
                    }
                } catch(e) {}
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(js, null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(service: ServiceItem): WebView {
        val webView = WebView(this)

        // AISLAMIENTO MULTI-PERFIL (Android 14 / Chromium WebView Multi-Profile)
        // Se ejecuta ANTES de cualquier carga de URL para garantizar cookies y almacenamiento aislados
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                ProfileStore.getInstance().getOrCreateProfile(service.profileId)
                WebViewCompat.setProfile(webView, service.profileId)
            } catch (e: Exception) {
                Log.e(TAG, "Error configurando perfil ${service.profileId}: ${e.message}")
            }
        }

        webView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        val settings = webView.settings
        if (mobileUA.isEmpty()) {
            mobileUA = settings.userAgentString
        }

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        settings.textZoom = service.zoom
        settings.userAgentString = if (service.isDesktopUA) DESKTOP_UA else mobileUA

        // Cookies aisladas por perfil
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                val profile = ProfileStore.getInstance().getProfile(service.profileId)
                val cm = profile?.cookieManager ?: CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(webView, true)
            } catch (_: Exception) {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(webView, true)
            }
        } else {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null) return false
                val uri = request.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: ""

                // Bloquear esquemas externos (snssdk://, intent://, market://) para no salir de Ferdium
                if (scheme != "http" && scheme != "https") {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                applyZoom(view, service.zoom, service.serviceType)

                // Limpieza visual, auto-recarga de QR y ajuste de encuadre para WhatsApp Web
                if (service.serviceType == "whatsapp" || service.name.contains("WhatsApp", ignoreCase = true) || service.url.contains("web.whatsapp.com")) {
                    val waClean = """
                        (function() {
                            try {
                                function hidePromoBanner() {
                                    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                                    var node;
                                    while (node = walker.nextNode()) {
                                        if (node.nodeValue && (node.nodeValue.indexOf('Descarga WhatsApp') !== -1 || node.nodeValue.indexOf('Download WhatsApp') !== -1)) {
                                            var el = node.parentElement;
                                            while (el && el !== document.body) {
                                                if (el.className && (el.className.indexOf('x1lliihq xh8yej3') !== -1 || (el.offsetHeight > 40 && el.offsetHeight < 300 && el.offsetWidth > 300))) {
                                                    el.style.setProperty('display', 'none', 'important');
                                                    el.style.setProperty('height', '0px', 'important');
                                                    el.style.setProperty('margin', '0px', 'important');
                                                    return;
                                                }
                                                el = el.parentElement;
                                            }
                                        }
                                    }
                                }
                                function keepQRFresh() {
                                    var btn = document.querySelector('[data-testid="link_device_qr_expired_refresh_button"]') ||
                                              Array.from(document.querySelectorAll('button, [role="button"]')).find(function(b) {
                                                  var t = (b.innerText || '').toLowerCase();
                                                  return t.includes('actualizar') || t.includes('recargar') || t.includes('reload');
                                              });
                                    if (btn) {
                                        btn.click();
                                    }
                                    var canvas = document.querySelector('canvas');
                                    if (canvas && parseFloat(window.getComputedStyle(canvas).opacity) < 0.5) {
                                        canvas.style.setProperty('opacity', '1', 'important');
                                    }
                                }
                                hidePromoBanner();
                                keepQRFresh();
                                if (!window._ferdiunWAFreshObserver) {
                                    window._ferdiunWAFreshObserver = new MutationObserver(function() {
                                        hidePromoBanner();
                                        keepQRFresh();
                                    });
                                    window._ferdiunWAFreshObserver.observe(document.body, { childList: true, subtree: true, attributes: true });
                                }
                            } catch(e) {}
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(waClean, null)
                }

                // Auto-asistencia de inicio de sesión para Instagram (saltar banner de tableta)
                if (service.serviceType == "instagram" || (url != null && url.contains("instagram.com"))) {
                    val igAuto = """
                        (function() {
                            try {
                                var btn = Array.from(document.querySelectorAll('a, button, [role="button"]')).find(function(el) {
                                    var t = (el.innerText || '').trim().toLowerCase();
                                    return t === 'iniciar sesión' || t === 'log in';
                                });
                                if (btn && !window._igAutoClicked) {
                                    window._igAutoClicked = true;
                                    btn.click();
                                }
                            } catch(e) {}
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(igAuto, null)
                }

                // Auto-asistencia para Google Workspace (Gmail / Drive): pulsar "Acceder"
                if (service.serviceType == "gmail" || service.serviceType == "drive" || (url != null && url.contains("workspace.google.com"))) {
                    val gAuto = """
                        (function() {
                            try {
                                var btn = Array.from(document.querySelectorAll('a, button')).find(function(el) {
                                    var t = (el.innerText || '').trim().toLowerCase();
                                    return t === 'acceder' || t === 'sign in';
                                });
                                if (btn && !window._gAutoClicked) {
                                    window._gAutoClicked = true;
                                    btn.click();
                                }
                            } catch(e) {}
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(gAuto, null)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (view == webViews.getOrNull(currentIndex)) {
                    if (newProgress < 100) {
                        tvStatus.text = "${service.name}: ${newProgress}%"
                    } else {
                        tvStatus.text = "${service.name}: listo"
                        try {
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                                ProfileStore.getInstance().getProfile(service.profileId)?.cookieManager?.flush()
                            } else {
                                CookieManager.getInstance().flush()
                            }
                        } catch (_: Exception) {}
                    }
                }
                // Aplicar redimensión en renderizado inicial
                if (newProgress in 35..65) {
                    applyZoom(view, service.zoom, service.serviceType)
                }
            }
        }

        return webView
    }

    private fun buildTab(index: Int): LinearLayout {
        val service = services[index]
        val density = resources.displayMetrics.density

        val tab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val padH = (2 * density).toInt()
            val padV = (2 * density).toInt()
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                (58 * density).toInt(),
                (31 * density).toInt()
            ).apply {
                val mY = (1 * density).toInt()
                setMargins(0, mY, 0, mY)
            }
        }

        // Círculo del servicio compacto con monograma
        val iconSize = (19 * density).toInt()
        val circleContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(service.color)
            }
        }

        val tvIcon = TextView(this).apply {
            text = service.monogram
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        circleContainer.addView(tvIcon)

        // Etiqueta del servicio
        val tvLabel = TextView(this).apply {
            text = service.name
            textSize = 6.5f
            setTextColor(Color.parseColor("#D1D5DB"))
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (0.5f * density).toInt(), 0, 0)
            }
        }

        tab.addView(circleContainer)
        tab.addView(tvLabel)

        tab.setOnClickListener {
            showService(index)
        }

        tab.setOnLongClickListener {
            showServiceOptionsDialog(index)
            true
        }

        return tab
    }

    private fun rebuildTabs() {
        tabBar.removeAllViews()
        tabViews.clear()
        for (i in services.indices) {
            val tab = buildTab(i)
            tabViews.add(tab)
            tabBar.addView(tab)
        }
    }

    private fun showService(index: Int) {
        if (index !in services.indices) return
        currentIndex = index
        val density = resources.displayMetrics.density
        val service = services[index]

        // Carga bajo demanda: se crea el WebView solo al abrir la pestaña
        var webView = webViews.getOrNull(index)
        if (webView == null) {
            webView = buildWebView(service)
            if (index < webViews.size) {
                webViews[index] = webView
            } else {
                webViews.add(webView)
            }
            webContainer.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            webView.loadUrl(service.url)
        } else {
            applyZoom(webView, service.zoom, service.serviceType)
        }

        for (i in services.indices) {
            val isSelected = (i == index)
            webViews.getOrNull(i)?.visibility = if (isSelected) View.VISIBLE else View.GONE

            if (i in tabViews.indices) {
                val tab = tabViews[i]
                if (isSelected) {
                    tab.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 5 * density
                        setColor(Color.parseColor("#2B2548"))
                        setStroke((1.2f * density).toInt(), Color.parseColor("#7C3AED"))
                    }
                    tab.alpha = 1.0f
                } else {
                    tab.background = null
                    tab.alpha = 0.65f
                }
            }
        }

        updateDesktopButton()
        updateZoomLabel()
        tvStatus.text = "${service.name}: listo"
    }

    private fun updateDesktopButton() {
        if (currentIndex in services.indices) {
            val isDesktop = services[currentIndex].isDesktopUA
            btnDesktop.text = if (isDesktop) "PC" else "MOV"
            btnDesktop.setBackgroundColor(
                if (isDesktop) Color.parseColor("#7C3AED") else Color.parseColor("#262638")
            )
        }
    }

    private fun showAddServiceDialog() {
        val density = resources.displayMetrics.density
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_service, null)

        val layoutPresetChips = dialogView.findViewById<LinearLayout>(R.id.layoutPresetChips)
        val etServiceName = dialogView.findViewById<EditText>(R.id.etServiceName)
        val etServiceMonogram = dialogView.findViewById<EditText>(R.id.etServiceMonogram)
        val etServiceUrl = dialogView.findViewById<EditText>(R.id.etServiceUrl)
        val cbDesktopMode = dialogView.findViewById<CheckBox>(R.id.cbDesktopMode)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelAdd)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmAdd)

        var selectedPreset: Preset = presets[0] // WhatsApp por defecto
        val chipViews = ArrayList<LinearLayout>()

        fun updatePresetFields(preset: Preset) {
            selectedPreset = preset
            val count = services.count { it.serviceType == preset.type }
            val numStr = if (count > 0) " ${count + 1}" else ""
            val monoNumStr = if (count > 0) "${count + 1}" else ""

            etServiceName.setText("${preset.name}$numStr")
            etServiceMonogram.setText("${preset.monogram}$monoNumStr".take(4))
            etServiceUrl.setText(preset.url)
            cbDesktopMode.isChecked = preset.defaultDesktopUA

            // Resaltar chip seleccionado
            for (i in presets.indices) {
                val chip = chipViews.getOrNull(i) ?: continue
                val isSel = (presets[i].type == preset.type)
                chip.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14 * density
                    setColor(if (isSel) Color.parseColor("#3B2E58") else Color.parseColor("#26263A"))
                    if (isSel) {
                        setStroke((1.2f * density).toInt(), Color.parseColor("#A78BFA"))
                    }
                }
            }
        }

        // Construir chips de presets
        layoutPresetChips.removeAllViews()
        for (preset in presets) {
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = (8 * density).toInt()
                val padV = (5 * density).toInt()
                setPadding(padH, padV, padH, padV)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, (6 * density).toInt(), 0)
                }
            }

            val dot = View(this).apply {
                val dotSize = (10 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    setMargins(0, 0, (5 * density).toInt(), 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(preset.color)
                }
            }

            val tvTitle = TextView(this).apply {
                text = preset.name
                textSize = 10.5f
                setTextColor(Color.WHITE)
            }

            chip.addView(dot)
            chip.addView(tvTitle)

            chip.setOnClickListener {
                updatePresetFields(preset)
            }

            chipViews.add(chip)
            layoutPresetChips.addView(chip)
        }

        // Seleccionar WhatsApp al inicio
        updatePresetFields(presets[0])

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val name = etServiceName.text.toString().trim()
            val monogram = etServiceMonogram.text.toString().trim()
            val rawUrl = etServiceUrl.text.toString().trim()
            val isDesktop = cbDesktopMode.isChecked

            if (name.isEmpty()) {
                Toast.makeText(this, "Ingresa un nombre para la ventana", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (rawUrl.isEmpty()) {
                Toast.makeText(this, "Ingresa una URL válida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val formattedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                "https://$rawUrl"
            } else {
                rawUrl
            }

            val finalMonogram = if (monogram.isEmpty()) name.take(2).uppercase() else monogram
            val domain = try {
                URI(formattedUrl).host ?: ""
            } catch (_: Exception) {
                ""
            }

            val now = System.currentTimeMillis()
            val profileId = "profile_${selectedPreset.type}_$now"

            val newService = ServiceItem(
                id = "svc_$now",
                serviceType = selectedPreset.type,
                name = name,
                monogram = finalMonogram,
                url = formattedUrl,
                domain = domain,
                color = selectedPreset.color,
                isDesktopUA = isDesktop,
                zoom = selectedPreset.defaultZoom,
                profileId = profileId
            )

            services.add(newService)
            webViews.add(null)
            saveServices()
            rebuildTabs()
            showService(services.size - 1)

            dialog.dismiss()
            Toast.makeText(this, "Ventana '$name' creada con sesión aislada", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            val dialogWidth = (460 * density).toInt()
            window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun showServiceOptionsDialog(index: Int) {
        val service = services.getOrNull(index) ?: return
        val options = arrayOf(
            "↻ Recargar ventana",
            "🧹 Limpiar cookies y sesión",
            "🗑️ Eliminar ventana"
        )

        AlertDialog.Builder(this)
            .setTitle(service.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Recargar
                        webViews.getOrNull(index)?.reload()
                    }
                    1 -> { // Limpiar cookies / sesión
                        clearServiceSession(index)
                    }
                    2 -> { // Eliminar ventana
                        confirmDeleteService(index)
                    }
                }
            }
            .show()
    }

    private fun clearServiceSession(index: Int) {
        val service = services.getOrNull(index) ?: return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                val profile = ProfileStore.getInstance().getProfile(service.profileId)
                profile?.cookieManager?.removeAllCookies(null)
                profile?.webStorage?.deleteAllData()
            } catch (e: Exception) {
                Log.e(TAG, "Error al limpiar sesión: ${e.message}")
            }
        }
        val wv = webViews.getOrNull(index)
        wv?.clearCache(true)
        wv?.reload()
        Toast.makeText(this, "Sesión de ${service.name} limpiada", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteService(index: Int) {
        if (services.size <= 1) {
            Toast.makeText(this, "Debe quedar al menos una ventana activa", Toast.LENGTH_SHORT).show()
            return
        }
        val service = services.getOrNull(index) ?: return
        AlertDialog.Builder(this)
            .setTitle("Eliminar ventana")
            .setMessage("¿Deseas eliminar '${service.name}'?\nEsta ventana y sus datos de sesión serán eliminados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                deleteService(index)
            }
            .show()
    }

    private fun deleteService(index: Int) {
        val service = services.getOrNull(index) ?: return
        val wv = webViews.getOrNull(index)
        if (wv != null) {
            webContainer.removeView(wv)
            wv.stopLoading()
            wv.destroy()
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                ProfileStore.getInstance().deleteProfile(service.profileId)
            } catch (e: Exception) {
                Log.e(TAG, "Error al eliminar perfil de WebView: ${e.message}")
            }
        }

        services.removeAt(index)
        webViews.removeAt(index)
        saveServices()
        rebuildTabs()

        val nextIndex = if (currentIndex >= services.size) services.size - 1 else currentIndex
        showService(nextIndex.coerceAtLeast(0))
        Toast.makeText(this, "Ventana '${service.name}' eliminada", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val currentWebView = webViews.getOrNull(currentIndex)
        if (currentWebView != null && currentWebView.canGoBack()) {
            currentWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        for (webView in webViews) {
            webView?.stopLoading()
            webView?.destroy()
        }
        super.onDestroy()
    }
}
