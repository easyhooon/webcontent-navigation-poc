package com.example.navtest

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.web.WebContent
import androidx.web.WebContentView
import androidx.web.WebFeature
import kotlinx.coroutines.delay

private const val TAG = "NavTest"

private val HTML = """
<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>
<body style='font:18px sans-serif;padding:16px'>
<p>loaded at <b id='loaded'></b></p><p>tick <b id='tick'>0</b></p>
<script>
const loadedAt = new Date().toLocaleTimeString();
document.getElementById('loaded').textContent = loadedAt;
window.__mark = 'mark-' + Date.now();
let n = 0;
const L = (e) => console.log(e + ' loadedAt=' + loadedAt + ' mark=' + window.__mark + ' tick=' + n + ' vis=' + document.visibilityState + ' size=' + innerWidth + 'x' + innerHeight);
setInterval(() => { n++; document.getElementById('tick').textContent = n; if (n % 5 === 0) L('alive'); }, 200);
['DOMContentLoaded', 'load', 'pageshow', 'pagehide', 'resize'].forEach(t => window.addEventListener(t, () => L(t)));
['visibilitychange', 'freeze', 'resume'].forEach(t => document.addEventListener(t, () => L('document.' + t)));
L('script start');
</script></body></html>
""".trimIndent()

/** console.log를 logcat으로 보낸다. */
private class LoggingChromeClient(private val label: String) : WebChromeClient() {
    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        Log.i(TAG, "[$label] JS ${message.message()}")
        return true
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.setUp(label: String) {
    settings.javaScriptEnabled = true
    webChromeClient = LoggingChromeClient(label)
    loadDataWithBaseURL("https://example.com/", HTML, "text/html", "utf-8", null)
}

/** 목적지(NavBackStackEntry) 범위 ViewModel이 WebContent를 들고 있는다. */
class WebContentHolder : ViewModel() {
    private var content: WebContent? = null

    fun createView(context: Context): WebView {
        val reused = content != null
        val webContent = content ?: WebContent().also { content = it }
        val view = webContent.attach(context) { WebContentView(it) }
        Log.i(TAG, "[content] attach reused=$reused")
        if (!reused) view.setUp("content")
        return view
    }

    fun release(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        content?.detach()
        Log.i(TAG, "[content] detach")
    }

    override fun onCleared() {
        content?.close()
        content = null
        Log.i(TAG, "[content] close (ViewModel cleared)")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "WEB_CONTENT supported=${WebFeature.isFeatureSupported(WebFeature.WEB_CONTENT)}")
        // adb: am start -n com.example.navtest/.MainActivity --es scenario plain|content
        val scenario = intent.getStringExtra("scenario")
        setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "home") {
                composable("home") { Home(nav) }
                composable("plain") { PlainWebScreen(nav) }
                composable("content") { ContentWebScreen(nav) }
                composable("other") { Other(nav) }
            }
            if (scenario != null) {
                LaunchedEffect(Unit) { runScenario(nav, scenario) }
            }
        }
    }
}

/** home → web → other(5초) → back(web) → back(home, 백스택에서 제거) 순서로 자동 진행한다. */
private suspend fun runScenario(nav: NavHostController, route: String) {
    Log.i(TAG, "=== scenario $route start")
    delay(1500)
    nav.navigate(route); Log.i(TAG, "=== navigate -> $route")
    delay(4000)
    nav.navigate("other"); Log.i(TAG, "=== navigate -> other (stay 5s)")
    delay(5000)
    nav.popBackStack(); Log.i(TAG, "=== back -> $route")
    delay(4000)
    nav.popBackStack(); Log.i(TAG, "=== back -> home ($route popped)")
    delay(2000)
    Log.i(TAG, "=== scenario $route end")
}

@Composable
private fun Home(nav: NavHostController) {
    Column(Modifier.padding(24.dp)) {
        Button(onClick = { nav.navigate("plain") }) { Text("WebView (plain)") }
        Button(onClick = { nav.navigate("content") }) { Text("WebContent") }
    }
}

@Composable
private fun Other(nav: NavHostController) {
    Column(Modifier.padding(24.dp)) {
        Text("Other screen")
        Button(onClick = { nav.popBackStack() }) { Text("Back") }
    }
}

@Composable
private fun PlainWebScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        Button(onClick = { nav.navigate("other") }) { Text("Go to other") }
        AndroidView(
            factory = { context ->
                Log.i(TAG, "[plain] create WebView")
                WebView(context).apply { setUp("plain") }
            },
            onRelease = {
                Log.i(TAG, "[plain] destroy WebView")
                it.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ContentWebScreen(nav: NavHostController) {
    val holder: WebContentHolder = viewModel()
    Column(Modifier.fillMaxSize()) {
        Button(onClick = { nav.navigate("other") }) { Text("Go to other") }
        AndroidView(
            factory = { context -> holder.createView(context) },
            onRelease = { holder.release(it) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
