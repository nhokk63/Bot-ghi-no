package com.nhokk63.sono

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var credentialManager: CredentialManager
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileCallback ?: return@registerForActivityResult
        fileCallback = null

        val fromPicker = WebChromeClient.FileChooserParams.parseResult(
            result.resultCode,
            result.data
        )
        val value = when {
            fromPicker != null && fromPicker.isNotEmpty() -> fromPicker
            result.resultCode == Activity.RESULT_OK && cameraUri != null -> arrayOf(cameraUri!!)
            else -> null
        }
        cameraUri = null
        callback.onReceiveValue(value)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialManager = CredentialManager.create(this)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.setSupportZoom(false)
            addJavascriptInterface(JsBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return request?.url?.let(assetLoader::shouldInterceptRequest)
                        ?: super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.host == "appassets.androidplatform.net") return false
                    return runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    }.getOrDefault(true)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectNativeHooks()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    newCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = newCallback ?: return false
                    openFileChooser(fileChooserParams)
                    return true
                }
            }
        }

        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    """
                    (() => {
                      const overlay = document.getElementById('overlay');
                      if (overlay && !overlay.classList.contains('hidden')) {
                        document.getElementById('closeSheet')?.click();
                        return 'handled';
                      }
                      const back = document.querySelector('[data-back]');
                      if (back) { back.click(); return 'handled'; }
                      return 'exit';
                    })()
                    """.trimIndent()
                ) { result ->
                    if (result?.contains("exit") == true) finish()
                }
            }
        })
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidBridge")
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun openFileChooser(params: WebChromeClient.FileChooserParams?) {
        val accepts = params?.acceptTypes.orEmpty().joinToString(",").lowercase()
        val wantsJson = accepts.contains("json") || accepts.contains(".json")

        if (wantsJson) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain"))
            }
            fileChooserLauncher.launch(intent)
            return
        }

        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        val cameraIntent = runCatching {
            val dir = File(cacheDir, "camera").apply { mkdirs() }
            val output = File.createTempFile("sono-evidence-", ".jpg", dir)
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                output
            )
            cameraUri = uri
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }.getOrNull()

        val chooser = Intent.createChooser(pickIntent, "Ảnh bằng chứng")
        if (cameraIntent != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        }
        fileChooserLauncher.launch(chooser)
    }

    private fun injectNativeHooks() {
        webView.evaluateJavascript(
            """
            (() => {
              if (window.__SONO_ANDROID_BRIDGE__) return;
              window.__SONO_ANDROID_BRIDGE__ = true;

              window.__sonoNativeToast = function(message) {
                const el = document.getElementById('toast');
                if (!el) return;
                el.textContent = String(message || '');
                el.classList.remove('hidden');
                clearTimeout(window.__sonoAndroidToastTimer);
                window.__sonoAndroidToastTimer = setTimeout(() => el.classList.add('hidden'), 3200);
              };

              document.addEventListener('click', function(event) {
                const firebase = event.target.closest?.('[data-settings-firebase]');
                const account = event.target.closest?.('[data-settings-account]');
                if (firebase || account) {
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  AndroidBridge.openFirebaseMenu();
                }
              }, true);
            })();
            """.trimIndent(),
            null
        )
    }

    inner class JsBridge {
        @JavascriptInterface
        fun openFirebaseMenu() {
            runOnUiThread { showFirebaseMenu() }
        }

        @JavascriptInterface
        fun signInGoogle() {
            runOnUiThread { lifecycleScope.launch { signInGoogleInternal() } }
        }

        @JavascriptInterface
        fun syncNow() {
            runOnUiThread { requestLocalStateAndSync(silent = false) }
        }

        @JavascriptInterface
        fun restoreCloud() {
            runOnUiThread { restoreFromCloud() }
        }
    }

    private fun showFirebaseMenu() {
        if (!firebaseReady()) {
            AlertDialog.Builder(this)
                .setTitle("Firebase chưa cấu hình")
                .setMessage(
                    "Copy google-services.json vào thư mục app/, Sync Gradle, sau đó build lại ứng dụng. " +
                        "Thông tin Firebase sẽ được gắn cố định lúc build, không phải nhập trong app."
                )
                .setPositiveButton("Đã hiểu", null)
                .show()
            return
        }

        val user = currentUser()
        val title = if (user == null) "Firebase & tài khoản" else "Firebase · ${user.email ?: user.uid}"
        val items = if (user == null) {
            arrayOf("Đăng nhập Google", "Đóng")
        } else {
            arrayOf(
                "Đồng bộ dữ liệu lên Firestore",
                "Khôi phục dữ liệu từ Firestore",
                "Đăng xuất Google",
                "Đóng"
            )
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            // Khong dung setMessage voi setItems: setMessage che mat danh sach nut dang nhap.
            .setItems(items) { dialog, which ->
                if (user == null) {
                    if (which == 0) lifecycleScope.launch { signInGoogleInternal() }
                    else dialog.dismiss()
                } else {
                    when (which) {
                        0 -> requestLocalStateAndSync(silent = false)
                        1 -> confirmRestoreFromCloud()
                        2 -> {
                            FirebaseAuth.getInstance().signOut()
                            webToast("Đã đăng xuất Google")
                        }
                        else -> dialog.dismiss()
                    }
                }
            }
            .show()
    }

    private fun firebaseReady(): Boolean {
        return runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this)
            FirebaseApp.getApps(this).isNotEmpty()
        }.getOrDefault(false)
    }

    private fun currentUser() = if (firebaseReady()) {
        runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
    } else null

    private suspend fun signInGoogleInternal() {
        if (!firebaseReady()) {
            webToast("Thiếu google-services.json. Xem FIREBASE_SETUP.md")
            return
        }

        val clientIdRes = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (clientIdRes == 0) {
            webToast("Thiếu default_web_client_id. Bật Google Sign-In rồi tải lại google-services.json")
            return
        }

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(clientIdRes))
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(this, request)
            val credential = result.credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                webToast("Google không trả về ID token hợp lệ")
                return
            }

            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
            FirebaseAuth.getInstance()
                .signInWithCredential(firebaseCredential)
                .addOnSuccessListener { auth ->
                    val signedUser = auth.user
                    val prefs = getSharedPreferences("sono-account-lock", MODE_PRIVATE)
                    val boundUid = prefs.getString("uid", null)
                    if (boundUid != null && signedUser?.uid != boundUid) {
                        FirebaseAuth.getInstance().signOut()
                        webToast("Máy này đang giữ sổ của tài khoản Google khác. Không cho đổi tài khoản để tránh lộ hoặc trộn dữ liệu. Hãy sao lưu trước khi chuyển máy.")
                    } else if (signedUser != null) {
                        if (boundUid == null) prefs.edit().putString("uid", signedUser.uid).apply()
                        webToast("Đã đăng nhập ${signedUser.email ?: "Google"}. Chỉ bấm Đồng bộ sau khi kiểm tra đúng sổ của mình.")
                    }
                }
                .addOnFailureListener { e -> webToast("Đăng nhập Firebase lỗi: ${e.message}") }
        } catch (e: Exception) {
            webToast("Đăng nhập Google lỗi: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun requestLocalStateAndSync(silent: Boolean) {
        val user = currentUser() ?: run {
            if (!silent) webToast("Chưa đăng nhập Google")
            return
        }

        webView.evaluateJavascript(
            "localStorage.getItem('sono-full-local-v1')"
        ) { encoded ->
            val stateJson = runCatching {
                JSONTokener(encoded).nextValue() as? String
            }.getOrNull()
            if (stateJson.isNullOrBlank()) {
                if (!silent) webToast("Không đọc được dữ liệu Sổ Nợ trên máy")
                return@evaluateJavascript
            }
            uploadState(user.uid, stateJson, silent)
        }
    }

    private fun uploadState(uid: String, stateJson: String, silent: Boolean) {
        if (!firebaseReady()) return
        val db = FirebaseFirestore.getInstance()
        val root = runCatching { JSONObject(stateJson) }.getOrElse {
            if (!silent) webToast("Dữ liệu local không hợp lệ")
            return
        }

        val owner = root.optString("ownerUid").takeIf { it.isNotBlank() && it != "null" }
        if (owner != null && owner != uid) {
            webToast("Sổ trên máy thuộc tài khoản khác. Không đồng bộ để tránh lộ dữ liệu.")
            return
        }
        if (root.optBoolean("demo", false)) {
            webToast("Đây là dữ liệu mẫu. Hãy xóa dữ liệu mẫu trước khi đồng bộ Firebase.")
            return
        }
        val people = root.optJSONArray("people") ?: JSONArray()
        val debts = root.optJSONArray("debts") ?: JSONArray()
        val operations = mutableListOf<(WriteBatch) -> Unit>()

        for (i in 0 until people.length()) {
            val obj = people.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            val ref = db.collection("users").document(uid).collection("people").document(id)
            val map = jsonObjectToMap(obj)
            operations += { batch -> batch.set(ref, map) }
        }

        for (i in 0 until debts.length()) {
            val obj = debts.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            val ref = db.collection("users").document(uid).collection("debts").document(id)
            val map = jsonObjectToMap(obj)
            operations += { batch -> batch.set(ref, map) }
        }

        val metaRef = db.collection("users").document(uid).collection("meta").document("state")
        operations += { batch ->
            batch.set(
                metaRef,
                mapOf(
                    "version" to root.optInt("version", 5),
                    "lastSyncMillis" to System.currentTimeMillis(),
                    "peopleCount" to people.length(),
                    "debtCount" to debts.length(),
                    "photosStoredInFirebase" to false
                )
            )
        }

        commitOperations(db, operations, 0, silent)
    }

    private fun commitOperations(
        db: FirebaseFirestore,
        operations: List<(WriteBatch) -> Unit>,
        offset: Int,
        silent: Boolean
    ) {
        if (offset >= operations.size) {
            if (!silent) webToast("Đã đồng bộ dữ liệu công nợ lên Firebase")
            return
        }
        val end = minOf(offset + 400, operations.size)
        val batch = db.batch()
        operations.subList(offset, end).forEach { it(batch) }
        batch.commit()
            .addOnSuccessListener { commitOperations(db, operations, end, silent) }
            .addOnFailureListener { e ->
                if (!silent) webToast("Đồng bộ Firebase lỗi: ${e.message}")
            }
    }

    private fun confirmRestoreFromCloud() {
        AlertDialog.Builder(this)
            .setTitle("Khôi phục từ Firebase?")
            .setMessage(
                "Dữ liệu công nợ trên máy sẽ được thay bằng bản Firestore. " +
                    "Ảnh bằng chứng không nằm trên Firebase; muốn khôi phục ảnh phải dùng JSON sao lưu có ảnh."
            )
            .setNegativeButton("Huỷ", null)
            .setPositiveButton("Khôi phục") { _, _ -> restoreFromCloud() }
            .show()
    }

    private fun restoreFromCloud() {
        val user = currentUser() ?: run {
            webToast("Chưa đăng nhập Google")
            return
        }
        val db = FirebaseFirestore.getInstance()
        val base = db.collection("users").document(user.uid)

        base.collection("people").get()
            .addOnSuccessListener { peopleSnap ->
                base.collection("debts").get()
                    .addOnSuccessListener { debtSnap ->
                        if (peopleSnap.isEmpty && debtSnap.isEmpty) {
                            webToast("Firebase chưa có dữ liệu để khôi phục")
                            return@addOnSuccessListener
                        }
                        val people = JSONArray()
                        peopleSnap.documents.forEach { people.put(JSONObject(it.data ?: emptyMap<String, Any>())) }
                        val debts = JSONArray()
                        debtSnap.documents.forEach { debts.put(JSONObject(it.data ?: emptyMap<String, Any>())) }

                        val state = JSONObject()
                            .put("version", 5)
                            .put("ownerUid", user.uid)
                            .put("demo", false)
                            .put("people", people)
                            .put("debts", debts)
                        installRemoteState(state.toString())
                    }
                    .addOnFailureListener { e -> webToast("Đọc khoản nợ lỗi: ${e.message}") }
            }
            .addOnFailureListener { e -> webToast("Đọc người nợ lỗi: ${e.message}") }
    }

    private fun installRemoteState(stateJson: String) {
        val quoted = JSONObject.quote(stateJson)
        webView.evaluateJavascript(
            """
            (() => {
              localStorage.setItem('sono-full-local-v1', $quoted);
              location.reload();
              return true;
            })()
            """.trimIndent(),
            null
        )
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = jsonValue(obj.opt(key))
        }
        return result
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> (0 until value.length()).map { jsonValue(value.opt(it)) }
        else -> value
    }

    private fun webToast(message: String) {
        if (!::webView.isInitialized) return
        webView.post {
            webView.evaluateJavascript(
                "window.__sonoNativeToast?.(${JSONObject.quote(message)})",
                null
            )
        }
    }
}
