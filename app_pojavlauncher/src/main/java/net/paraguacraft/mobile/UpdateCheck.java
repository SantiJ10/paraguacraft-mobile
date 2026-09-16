package net.paraguacraft.mobile;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Actualiza el APK desde latest-mobile.json: descarga, SHA-256 e instala
 * con PackageInstaller. Nunca abre el navegador ni GitHub.
 */
public final class UpdateCheck {
    private static final String TAG = "PgUpdate";
    public static final String MANIFEST_URL = "https://paraguacraft.pages.dev/latest-mobile.json";
    private static final String PREF_URL = "pg_pending_apk_url";
    private static final String PREF_SHA = "pg_pending_apk_sha";
    private static final String PREF_VER = "pg_pending_apk_ver";

    private UpdateCheck() {}

    public static void maybePrompt(Activity activity) {
        if (activity == null || !Tools.isOnline(activity)) return;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                JSONObject json = fetchJson(MANIFEST_URL);
                if (json == null) return;
                int remoteCode = json.optInt("versionCode", 0);
                String remoteName = json.optString("version", "");
                String url = json.optString("download_url", "");
                String notes = json.optString("notes", "");
                String sha = json.optString("sha256", "");
                int localCode = net.kdt.pojavlaunch.BuildConfig.PG_MOBILE_VERSION_CODE;
                if (remoteCode <= localCode || url.isEmpty()) return;
                if (url.contains("github.com/") && url.contains("/releases/tag/")) {
                    Log.w(TAG, "Manifest apunta a página HTML, se ignora");
                    return;
                }
                Tools.runOnUiThread(() -> showPrompt(activity, remoteName, notes, url, sha));
            } catch (Exception e) {
                Log.i(TAG, "Sin update", e);
            }
        });
    }

    public static void retryPending(Activity activity) {
        if (activity == null) return;
        SharedPreferences p = LauncherPreferences.DEFAULT_PREF;
        if (p == null) return;
        String url = p.getString(PREF_URL, "");
        if (url.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            return;
        }
        String ver = p.getString(PREF_VER, "");
        String sha = p.getString(PREF_SHA, "");
        p.edit().remove(PREF_URL).remove(PREF_SHA).remove(PREF_VER).apply();
        startDownload(activity, ver, url, sha);
    }

    private static void showPrompt(Activity activity, String version, String notes, String url, String sha) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Actualización Paraguacraft " + version)
                .setMessage((notes.isEmpty() ? "Hay una versión nueva del launcher." : notes)
                        + "\n\nSe descarga e instala acá, sin ir al navegador.")
                .setPositiveButton("Actualizar", (d, w) -> startDownload(activity, version, url, sha))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void savePending(String version, String url, String sha) {
        if (LauncherPreferences.DEFAULT_PREF == null) return;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(PREF_URL, url)
                .putString(PREF_SHA, sha == null ? "" : sha)
                .putString(PREF_VER, version == null ? "" : version)
                .apply();
    }

    private static void startDownload(Activity activity, String version, String url, String sha) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            savePending(version, url, sha);
            new AlertDialog.Builder(activity)
                    .setTitle("Permitir instalar")
                    .setMessage("Android pide permiso para que Paraguacraft instale su propia actualización. Cuando vuelvas, se descarga sola.")
                    .setPositiveButton("Permitir", (d, w) -> {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())));
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(true);
        TextView label = new TextView(activity);
        label.setPadding(48, 24, 48, 8);
        label.setText("Descargando " + version + "…");
        android.widget.LinearLayout box = new android.widget.LinearLayout(activity);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(24, 8, 24, 8);
        box.addView(label);
        box.addView(bar);
        AlertDialog dlg = new AlertDialog.Builder(activity)
                .setTitle("Actualizando")
                .setView(box)
                .setCancelable(false)
                .show();

        PojavApplication.sExecutorService.execute(() -> {
            File apk = new File(activity.getCacheDir(), "Paraguacraft-update.apk");
            try {
                if (apk.exists() && !apk.delete()) Log.w(TAG, "no se pudo borrar APK vieja");
                DownloadUtils.downloadFileMonitored(url, apk, new byte[64 * 1024], (curr, max) -> {
                    Tools.runOnUiThread(() -> {
                        if (max > 0) {
                            bar.setIndeterminate(false);
                            bar.setMax(max);
                            bar.setProgress(curr);
                            label.setText("Descargando " + (curr / (1024 * 1024)) + " / " + (max / (1024 * 1024)) + " MB");
                        }
                    });
                });
                if (sha != null && sha.length() >= 32) {
                    String got = sha256(apk);
                    if (!sha.equalsIgnoreCase(got)) {
                        throw new IllegalStateException("SHA-256 no coincide");
                    }
                }
                Tools.runOnUiThread(() -> {
                    dlg.dismiss();
                    installApk(activity, apk);
                });
            } catch (Exception e) {
                Log.e(TAG, "download", e);
                Tools.runOnUiThread(() -> {
                    dlg.dismiss();
                    Toast.makeText(activity, "No se pudo descargar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void installApk(Activity activity, File apk) {
        try {
            PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(sessionId);
                 InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("update.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
                Intent cb = new Intent(activity, activity.getClass());
                cb.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent pi = PendingIntent.getActivity(activity, sessionId, cb, flags);
                session.commit(pi.getIntentSender());
            }
        } catch (Exception e) {
            Log.e(TAG, "packageInstaller", e);
            try {
                Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(intent);
            } catch (Exception e2) {
                Log.e(TAG, "install fallback", e2);
                Toast.makeText(activity, "No se pudo abrir el instalador.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static JSONObject fetchJson(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "Paraguacraft-Mobile");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        if (conn.getResponseCode() != 200) return null;
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            String raw = new String(bos.toByteArray(), StandardCharsets.UTF_8).replace("\uFEFF", "");
            return new JSONObject(raw);
        }
    }
}
