package it.alex.campo;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Aggiornamento dell'app nativa (APK).
 * L'interfaccia si aggiorna da sola (arriva da GitHub Pages); qui si controlla solo se
 * serve installare un APK nuovo: in quel caso si scarica, si verifica e si avvia l'installazione.
 */
public class Updater {
    static final String BASE = "https://raw.githubusercontent.com/raccoltaalle-boop/campo-app/dist/";
    static final long MIN_INTERVAL_MS = 30L * 60 * 1000;

    private final MainActivity act;
    private volatile boolean busy = false;
    private long lastCheck = -MIN_INTERVAL_MS;
    String pendingSha = null;

    Updater(MainActivity a) { act = a; }

    int myVersion() { return BuildConfig.VERSION_CODE; }

    /** Controlla se c'è un APK più nuovo (al massimo ogni 30 minuti, salvo force). */
    void check(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (busy || (!force && now - lastCheck < MIN_INTERVAL_MS)) return;
        lastCheck = now;
        busy = true;
        new Thread(() -> {
            try {
                JSONObject v = new JSONObject(new String(get(BASE + "version.json?t=" + System.currentTimeMillis()), StandardCharsets.UTF_8));
                int apk = v.optInt("apk", 0);
                String sha = v.optString("apkSha256", "");
                String notes = v.optString("notes", "");
                boolean required = v.optBoolean("required", true);
                if (apk > myVersion()) act.runOnUiThread(() -> ask(apk, notes, sha, required));
            } catch (Exception ignored) {
            } finally {
                busy = false;
            }
        }).start();
    }

    private void ask(int apk, String notes, String sha, boolean required) {
        if (act.isFinishing()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(act)
                .setTitle("Aggiornamento dell'app (versione " + apk + ")")
                .setMessage((notes.isEmpty() ? "" : notes + "\n\n")
                        + "Per continuare serve installare la nuova versione. I dati del campo restano.")
                .setPositiveButton("Installa", (d, w) -> install(sha))
                .setCancelable(!required);
        if (!required) b.setNegativeButton("Più tardi", null);
        b.show();
    }

    void install(String sha) {
        if (!act.getPackageManager().canRequestPackageInstalls()) {
            toast("Consenti a Campo di installare app, poi torna indietro: l'aggiornamento riparte da solo");
            pendingSha = sha;
            try {
                act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + act.getPackageName())));
            } catch (Exception ignored) { }
            return;
        }
        toast("Scarico l'aggiornamento…");
        new Thread(() -> {
            try {
                byte[] apk = get(BASE + "campo.apk?t=" + System.currentTimeMillis());
                if (!sha.isEmpty() && !sha256(apk).equalsIgnoreCase(sha))
                    throw new Exception("file non integro, riprova tra qualche minuto");
                PackageInstaller pi = act.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                p.setAppPackageName(act.getPackageName());
                if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                int id = pi.createSession(p);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    try (OutputStream o = s.openWrite("app", 0, apk.length)) {
                        o.write(apk);
                        s.fsync(o);
                    }
                    Intent i = new Intent(act, InstallReceiver.class);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    s.commit(PendingIntent.getBroadcast(act, id, i, flags).getIntentSender());
                }
            } catch (Exception e) {
                toast("Aggiornamento non riuscito: " + e.getMessage());
                lastCheck = -MIN_INTERVAL_MS;
            }
        }).start();
    }

    /** Dopo il permesso "installa app sconosciute" riprende da solo. */
    void resume() {
        if (pendingSha != null && act.getPackageManager().canRequestPackageInstalls()) {
            String s = pendingSha;
            pendingSha = null;
            install(s);
        }
    }

    void toast(String msg) { act.runOnUiThread(() -> Toast.makeText(act, msg, Toast.LENGTH_LONG).show()); }

    static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(60000);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    static String sha256(byte[] data) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
