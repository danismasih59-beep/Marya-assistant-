package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF13131B))
            .systemBarsPadding()
            .imePadding()
        ) {
          MyraWebViewContainer(
            initialUrl = "file:///android_asset/www/index.html"
          )
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MyraWebViewContainer(
  initialUrl: String,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var canGoBack by remember { mutableStateOf(false) }

  BackHandler(enabled = canGoBack) {
    webViewInstance?.let { wv ->
      if (wv.canGoBack()) {
        wv.goBack()
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webViewInstance?.apply {
        stopLoading()
        clearHistory()
        removeAllViews()
        destroy()
      }
      webViewInstance = null
    }
  }

  AndroidView(
    modifier = modifier.fillMaxSize(),
    factory = { ctx ->
      WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(0xFF13131B.toInt())

        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          databaseEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          loadWithOverviewMode = true
          useWideViewPort = true
          builtInZoomControls = false
          displayZoomControls = false
          cacheMode = WebSettings.LOAD_DEFAULT
          mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        addJavascriptInterface(MyraNativeBridge(ctx, this), "MyraNative")

        webViewClient = object : WebViewClient() {
          override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            canGoBack = view?.canGoBack() == true
          }

          override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            canGoBack = view?.canGoBack() == true
          }

          override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            return if (url.startsWith("file:///android_asset/") || url.startsWith("file://")) {
              false
            } else if (url.startsWith("http://") || url.startsWith("https://") ||
              url.startsWith("mailto:") || url.startsWith("tel:")
            ) {
              try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                ctx.startActivity(intent)
              } catch (e: Exception) {
                Log.e("MyraWebView", "Error opening external url: $url", e)
              }
              true
            } else {
              false
            }
          }

          override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
          ) {
            super.onReceivedError(view, request, error)
            Log.w("MyraWebView", "WebView error: ${error?.description} for ${request?.url}")
          }
        }

        webChromeClient = object : WebChromeClient() {
          override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            Log.d("MyraJS", "${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
            return true
          }
        }

        loadUrl(initialUrl)
        webViewInstance = this
      }
    },
    update = { wv ->
      canGoBack = wv.canGoBack()
    }
  )
}

class MyraNativeBridge(private val context: Context, private val webView: WebView) {
  @JavascriptInterface
  fun showToast(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
  }

  @JavascriptInterface
  fun vibrate(durationMs: Long) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator?.vibrate(
          VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        )
      } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        @Suppress("DEPRECATION")
        vibrator?.vibrate(durationMs)
      }
    } catch (e: Exception) {
      Log.e("MyraBridge", "Vibration failed", e)
    }
  }

  @JavascriptInterface
  fun getAppVersion(): String {
    return "4.2.0-titan"
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}

