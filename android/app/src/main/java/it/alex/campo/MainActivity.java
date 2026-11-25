package it.alex.campo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Guscio Android dell'app Campo: apre la versione pubblicata su GitHub Pages,
 * così chi usa l'app trova sempre l'ultima versione senza reinstallare nulla.
 */
public class MainActivity extends Activity {
    static final String HOST = "raccoltaalle-boop.github.io";
    static final String URL = "https://" + HOST + "/campo-app/";
    static final int REQ_LOC = 7;
    static final long RELOAD_AFTER_MS = 6L * 60 * 60 * 1000; // ricarica l'interfaccia dopo 6 ore in background

    WebView web;
    long loadedAt;
    String geoOrigin;
    GeolocationPermissions.Callback geoCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#1E4D31"));
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " CampoApp/" + BuildConfigVersion.NAME);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // link esterni (es. crediti delle mappe) nel browser
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) showOffline();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, false);
                } else {
                    geoOrigin = origin;
                    geoCallback = cb;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                }
            }
        });

        if (state != null) web.restoreState(state);
        else load();
    }

    boolean online() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    void load() {
        // online: prende sempre la versione più recente; offline: usa quella salvata
        web.getSettings().setCacheMode(online() ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        web.loadUrl(URL);
        loadedAt = SystemClock.elapsedRealtime();
    }

    void showOffline() {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='font-family:sans-serif;background:#f3f5ef;color:#15211a;padding:32px;text-align:center'>"
                + "<h2 style='color:#1e4d31'>Nessuna connessione</h2>"
                + "<p>Serve internet per aprire l'app la prima volta. Le innaffiature segnate offline vengono inviate appena torna la rete.</p>"
                + "<p><a href='" + URL + "' style='display:inline-block;margin-top:12px;padding:14px 22px;background:#1769c2;color:#fff;border-radius:10px;text-decoration:none'>Riprova</a></p>"
                + "</body></html>";
        web.loadDataWithBaseURL(URL, html, "text/html", "utf-8", null);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_LOC && geoCallback != null) {
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            geoCallback.invoke(geoOrigin, ok, false);
            geoCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (SystemClock.elapsedRealtime() - loadedAt > RELOAD_AFTER_MS && online()) load();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        // prima chiude le schede aperte nell'app, poi torna indietro o esce
        web.evaluateJavascript("(function(){var s=document.getElementById('sheet');"
                + "if(s&&!s.hidden){document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));return 1}"
                + "if(document.body.className){document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));return 1}"
                + "return 0})()", v -> {
            if (!"1".equals(v)) {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });
    }
}
