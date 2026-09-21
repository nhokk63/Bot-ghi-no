# Ban dau chua bat minify. Khi bat R8, giu JavascriptInterface de WebView goi duoc.
-keepclassmembers class com.nhokk63.sono.MainActivity$JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
