package com.example.mediagrab

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URLEncoder

/**
 * Owns the WebView so the page survives switching between tabs.
 *
 * NOTE: the state flows are deliberately NOT called url/title/progress, because inside
 * WebView.apply { } those names would resolve to the WebView's own getUrl()/getTitle()/getProgress().
 */
class BrowserController(context: Context) {

    val currentUrl = MutableStateFlow("")
    val currentTitle = MutableStateFlow("")
    val loadProgress = MutableStateFlow(100)
    val canGoForwardState = MutableStateFlow(false)

    /** True while we show the start page (site shortcuts) instead of a web page. */
    val atStart = MutableStateFlow(true)

    val webView: WebView = createWebView(context)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val view = WebView(context)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        view.settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                // Only http(s) loads inside the browser; ignore app deep links (intent://, market://).
                val scheme = request.url.scheme
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(v: WebView, pageUrl: String?, favicon: Bitmap?) {
                currentUrl.value = pageUrl.orEmpty()
                atStart.value = false
                canGoForwardState.value = v.canGoForward()
            }

            // Fires for single-page-app navigation too (e.g. tapping a video on a mobile site).
            override fun doUpdateVisitedHistory(v: WebView, pageUrl: String?, isReload: Boolean) {
                currentUrl.value = pageUrl.orEmpty()
                canGoForwardState.value = v.canGoForward()
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, newProgress: Int) {
                loadProgress.value = newProgress
            }

            override fun onReceivedTitle(v: WebView, newTitle: String?) {
                currentTitle.value = newTitle.orEmpty()
            }
        }
        return view
    }

    fun load(input: String) {
        val target = normalize(input)
        atStart.value = false
        currentUrl.value = target
        webView.loadUrl(target)
    }

    fun reload() = webView.reload()

    fun goStart() { atStart.value = true }

    /** Handles the system Back button. Returns false if there is nothing left to go back to. */
    fun back(): Boolean = when {
        atStart.value -> false
        webView.canGoBack() -> { webView.goBack(); true }
        else -> { atStart.value = true; true }
    }

    private fun normalize(raw: String): String {
        val s = raw.trim()
        return when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.contains(" ") || !s.contains(".") ->
                "https://www.google.com/search?q=" + URLEncoder.encode(s, "UTF-8")
            else -> "https://$s"
        }
    }
}
