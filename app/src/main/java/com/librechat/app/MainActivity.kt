package com.librechat.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.window.OnBackInvokedDispatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.librechat.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val userAgent =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.5615.135 Mobile Safari/537.36"
    private val chatUrl = "https://team.ttg.ai/c/new"
    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val uri = data.data
                    if (uri != null) {
                        fileUploadCallback?.onReceiveValue(arrayOf(uri))
                        fileUploadCallback = null
                    }
                }
            } else {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide() // Hide the action bar
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        webView = binding.webView

        // Add android:configChanges to handle orientation and screen size changes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#343541")

        webView.settings.userAgentString = userAgent
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.addJavascriptInterface(WebViewInterface(this), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false

                if (url.toString().contains(chatUrl)) {
                    return false
                }

                // Load the clicked hyperlink in the current WebView
                view?.loadUrl(url.toString())

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript(
                    """
                    (function() {
                      if (navigator.clipboard && navigator.clipboard.writeText) {
                        navigator.clipboard.writeText = function(text) {
                          window.ReactNativeWebView.postMessage(JSON.stringify({
                            type: 'clipboard',
                            data: text
                          }));
                          return Promise.resolve();
                        };
                      }
                
                      function patchInputs() {
                        var textareas = document.querySelectorAll('textarea');
                        textareas.forEach(function(textarea) {
                          textarea.addEventListener('keydown', function(e) {
                            if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey) {
                              e.stopPropagation();
                            }
                          }, true);
                        });
                      }
                      document.addEventListener('DOMContentLoaded', patchInputs);
                      setTimeout(patchInputs, 2000);
                
                      true;
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (fileUploadCallback != null) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                }

                fileUploadCallback = filePathCallback

                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                contentSelectionIntent.type = "*/*"

                val intentArray: Array<Intent?> = arrayOfNulls(0)
                val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Select File")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)

                fileChooser.launch(chooserIntent)
                return true
            }
        }

        // Check if WebView already has a saved state
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(chatUrl)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (webView.canGoBack() && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            webView.goBack()
        else
            super.onBackPressed()
    }

    private class WebViewInterface(private val context: Context) {
        @JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied!", text)

            clipboard.setPrimaryClip(clip)
        }
    }
}
