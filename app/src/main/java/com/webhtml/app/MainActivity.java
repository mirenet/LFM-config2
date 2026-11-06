package com.webhtml.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.annotation.Keep;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private List<String> externalDomains = new ArrayList<>();
    private String startURL = "";
    private String broAgent = "";
    private boolean portrait = true;
    private boolean screenOn = false;
    private boolean allowZoom = false; 
    private boolean forceDark = false;

    private WebView webView;
    private WebView overlayWebView; 
    private MenuControler menuControler;

    private ValueCallback<Uri[]> uploadMessage;
    private final static int FILE_CHOOSER_RESULT_CODE = 1;
    private DownloadHelper downloadHelper;

    private final static int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private final static int MEDIA_PERMISSION_REQUEST_CODE = 101;
    
    private String pendingGeolocationOrigin;
    private GeolocationPermissions.Callback pendingGeolocationCallback;
    private PermissionRequest pendingPermissionRequest;

    @SuppressLint({"SetJavaScriptEnabled", "QueryPermissionsNeeded"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadConfigFromInternalStorage();

        if (forceDark) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            );
        }

        super.onCreate(savedInstanceState);

        if (portrait) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (screenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Donji WebView
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#070707"));
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.addView(webView);

        // 2. Gornji WebView (menu.html)
        overlayWebView = new WebView(this);
        overlayWebView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlayWebView.setBackgroundColor(Color.TRANSPARENT);
        overlayWebView.getSettings().setJavaScriptEnabled(true);
        overlayWebView.getSettings().setDomStorageEnabled(true);
        overlayWebView.getSettings().setAllowFileAccess(true);
        overlayWebView.getSettings().setAllowFileAccessFromFileURLs(true);
        overlayWebView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        overlayWebView.setVisibility(View.GONE);

        overlayWebView.addJavascriptInterface(new ConfigSaverInterface(this), "AndroidBridgeSaver");
        
        overlayWebView.addJavascriptInterface(new Object() {
            @Keep
            @JavascriptInterface
            public void closeMenu() {
                runOnUiThread(() -> {
                    if (overlayWebView != null) overlayWebView.setVisibility(View.GONE);
                });
            }
        }, "AndroidMenuBridge");

        overlayWebView.loadUrl("file:///android_asset/menu.html");
        rootLayout.addView(overlayWebView);

        setContentView(rootLayout);

        menuControler = new MenuControler(overlayWebView);
        downloadHelper = new DownloadHelper(this);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        webSettings.setSupportZoom(allowZoom);
        webSettings.setBuiltInZoomControls(allowZoom);
        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        if (broAgent != null && !broAgent.trim().isEmpty()) {
            webSettings.setUserAgentString(broAgent);
        }

        if (forceDark) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, true);
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @SuppressWarnings("deprecation")
                boolean fallback = true;
                WebSettingsCompat.setForceDark(webSettings, WebSettingsCompat.FORCE_DARK_ON);
            }
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(downloadHelper, "AndroidBridge");
        webView.addJavascriptInterface(new ConfigSaverInterface(this), "AndroidBridgeSaver");

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (menuControler != null && menuControler.isMenuVisible()) {
                    menuControler.toggleMenu();
                    return;
                }

                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrlLoading(view, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, url);
            }

            private boolean handleUrlLoading(WebView view, String url) {
                if (isExternalDomain(url)) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        view.getContext().startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        return true;
                    }
                }

                if (url.startsWith("file://") || url.startsWith("http://") || url.startsWith("https://")) {
                    return false; 
                }
                return true;
            }

            private boolean isExternalDomain(String url) {
                if (externalDomains == null || externalDomains.isEmpty()) return false;
                try {
                    Uri uri = Uri.parse(url);
                    String host = uri.getHost();
                    if (host == null) return false;
                    for (String domainEntry : externalDomains) {
                        String cleanDomain = domainEntry.trim().toLowerCase();
                        if (cleanDomain.startsWith("https://")) cleanDomain = cleanDomain.substring(8);
                        if (cleanDomain.startsWith("http://")) cleanDomain = cleanDomain.substring(7);
                        if (cleanDomain.endsWith("/")) cleanDomain = cleanDomain.substring(0, cleanDomain.length() - 1);
                        if (host.equalsIgnoreCase(cleanDomain) || host.toLowerCase().endsWith("." + cleanDomain)) return true;
                    }
                } catch (Exception ignored) {}
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("file://") && view == webView) {
                    view.evaluateJavascript("window.webhtml = true;", null);
                }
            }
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String urlStr = request.getUrl().toString();
                if (urlStr.endsWith("appConfig.js")) {
                    try {
                        File configFile = new File(getFilesDir(), "appConfig.js");
                        if (configFile.exists()) {
                            InputStream targetStream = new FileInputStream(configFile);
                            return new WebResourceResponse("application/javascript", "UTF-8", targetStream);
                        }
                    } catch (Exception ignored) {}
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        // ODREĐIVANJE ŠTA SE UČITAVA U DONJI WEBVIEW NA STARTU
        if (startURL != null && !startURL.trim().isEmpty() && !startURL.startsWith("file:///")) {
            webView.loadUrl(startURL);
        } else {
            // Ako nema startURL-a, proveravamo IndexedDB (da li postoji uvezeni HTML)
            loadAppToWebView();
        }
    }

    // Pomoćna metoda za učitavanje sačuvane HTML aplikacije iz IndexedDB-a u donji WebView
    private void loadAppToWebView() {
        // Pošto IndexedDB zahteva JS, injektujemo malu skriptu da pročita bazu i pošalje sadržaj Javi, 
        // ILI učitavamo pomocni HTML koji to radi. Najlakše i najsigurnije je preko sledeće logike:
        webView.loadUrl("file:///android_asset/loader_bridge.html"); // Ili čitanje direktno
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (menuControler != null && menuControler.handleTouchEvent(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void loadConfigFromInternalStorage() {
        File configFile = new File(getFilesDir(), "appConfig.js");
        if (!configFile.exists()) copyAssetToFile("appConfig.js", configFile);

        try (FileInputStream inputStream = new FileInputStream(configFile)) {
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            String jsContent = new String(buffer, "UTF-8");

            portrait = parseBoolean(jsContent, "portrait", true);
            screenOn = parseBoolean(jsContent, "screenOn", false);
            allowZoom = parseBoolean(jsContent, "allowZoom", false); 
            forceDark = parseBoolean(jsContent, "forceDark", false);
            startURL = parseString(jsContent, "startURL", "");
            broAgent = parseString(jsContent, "broAgent", "");
            externalDomains = parseArray(jsContent, "external");

        } catch (Exception e) {
            copyAssetToFile("appConfig.js", configFile);
            loadConfigFromInternalStorage();
        }
    }

    private void copyAssetToFile(String assetName, File outFile) {
        try {
            File parentDir = outFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();
            try (InputStream in = getAssets().open(assetName);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (Exception ignored) {}
    }

    @Keep
    public static class ConfigSaverInterface {
        Context mContext;
        ConfigSaverInterface(Context c) { mContext = c; }

        @Keep
        @JavascriptInterface
        public String loadConfigFileRaw() {
            try {
                File configFile = new File(mContext.getFilesDir(), "appConfig.js");
                if (!configFile.exists()) return "// Fajl ne postoji";
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    byte[] buffer = new byte[(int) configFile.length()];
                    fis.read(buffer);
                    return new String(buffer, "UTF-8");
                }
            } catch (Exception e) {
                return "// Greška: " + e.getMessage();
            }
        }

        @Keep
        @JavascriptInterface
        public void saveConfigFile(String jsContent) {
            try {
                File dir = mContext.getFilesDir();
                if (!dir.exists()) dir.mkdirs();
                File configFile = new File(dir, "appConfig.js");
                try (FileOutputStream fos = new FileOutputStream(configFile)) {
                    fos.write(jsContent.getBytes());
                }
            } catch (Exception ignored) {}
        }

        // NOVO: Briše startURL iz appConfig.js fajla kada se uvozi HTML fajl
        @Keep
        @JavascriptInterface
        public void clearStartUrl() {
            try {
                File configFile = new File(mContext.getFilesDir(), "appConfig.js");
                if (configFile.exists()) {
                    FileInputStream fis = new FileInputStream(configFile);
                    byte[] buffer = new byte[(int) configFile.length()];
                    fis.read(buffer);
                    fis.close();
                    String content = new String(buffer, "UTF-8");
                    
                    // Zamenjujemo bilo koji startURL sa praznim stringom
                    String updated = content.replaceAll("startURL\\s*:\\s*[\"'][^\"']*[\"']", "startURL: \"\"");
                    
                    FileOutputStream fos = new FileOutputStream(configFile);
                    fos.write(updated.getBytes());
                    fos.close();
                }
            } catch (Exception ignored) {}
        }

        @Keep
        @JavascriptInterface
        public void restartApp() {
            try {
                Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                    if (mContext instanceof Activity) {
                        ((Activity) mContext).finish();
                    }
                    System.exit(0);
                }
            } catch (Exception ignored) {}
        }
    }

    private boolean parseBoolean(String content, String key, boolean defaultValue) {
        try {
            Pattern pattern = Pattern.compile(key + "\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) return Boolean.parseBoolean(matcher.group(1));
        } catch (Exception ignored) {}
        return defaultValue;
    }

    private String parseString(String content, String key, String defaultValue) {
        try {
            Pattern pattern = Pattern.compile(key + "\\s*:\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) return matcher.group(1);
        } catch (Exception ignored) {}
        return defaultValue;
    }

    private List<String> parseArray(String content, String key) {
        List<String> list = new ArrayList<>();
        try {
            Pattern pattern = Pattern.compile(key + "\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String arrayContent = matcher.group(1).trim();
                if (!arrayContent.isEmpty()) {
                    Pattern itemPattern = Pattern.compile("[\"']([^\"']+)[\"']");
                    Matcher itemMatcher = itemPattern.matcher(arrayContent);
                    while (itemMatcher.find()) list.add(itemMatcher.group(1));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
        if (overlayWebView != null) overlayWebView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (overlayWebView != null) overlayWebView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        if (overlayWebView != null) {
            if (overlayWebView.getParent() instanceof ViewGroup) ((ViewGroup) overlayWebView.getParent()).removeView(overlayWebView);
            overlayWebView.removeAllViews();
            overlayWebView.destroy();
            overlayWebView = null;
        }
        super.onDestroy();
    }
}
