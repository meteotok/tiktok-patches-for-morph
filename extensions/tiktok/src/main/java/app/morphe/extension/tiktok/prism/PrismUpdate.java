package app.morphe.extension.tiktok.prism;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Update prompt. On launch Prism reads prism-update.json from the repository; when it names a
 * newer build, the user gets "Обновить", which downloads the APK and opens Android's installer.
 * Nothing is installed without the user confirming in the system installer.
 */
@SuppressWarnings("unused")
public final class PrismUpdate {
    /** Build number of this mod version. Bump together with gradle.properties. */
    public static final int CURRENT_CODE = 810;
    public static final String CURRENT_NAME = "0.8.10";

    private static final String[] MANIFEST_URLS = {
            "https://raw.githubusercontent.com/meteotok/tiktok-patches-for-morph/claude/project-thread-thdlg5/prism-update.json",
            "https://cdn.jsdelivr.net/gh/meteotok/tiktok-patches-for-morph@claude/project-thread-thdlg5/prism-update.json",
    };
    private static final String APK_MIME = "application/vnd.android.package-archive";

    private static boolean checked;

    private PrismUpdate() {
    }

    /** Checks once per app start, from the first resumed main activity. */
    public static void checkOnce(Activity activity) {
        if (checked) {
            return;
        }
        checked = true;
        try {
            if (!Settings.PRISM_UPDATE_CHECK.get()) {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }
        WeakReference<Activity> ref = new WeakReference<>(activity);
        Thread thread = new Thread(() -> {
            JSONObject manifest = fetchManifest();
            if (manifest == null) {
                return;
            }
            int code = manifest.optInt("code", 0);
            String url = manifest.optString("url", "");
            if (code <= CURRENT_CODE || url.isEmpty()
                    || code == Settings.PRISM_UPDATE_SKIPPED.get()) {
                return;
            }
            String name = manifest.optString("name", String.valueOf(code));
            String notes = manifest.optString("notes", "");
            Utils.runOnMainThread(() -> {
                Activity current = ref.get();
                if (current != null && !current.isFinishing()) {
                    showPrompt(current, code, name, notes, url);
                }
            });
        }, "Prism-update");
        thread.setDaemon(true);
        thread.start();
    }

    private static JSONObject fetchManifest() {
        for (String manifestUrl : MANIFEST_URLS) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(manifestUrl).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setUseCaches(false);
                if (connection.getResponseCode() != 200) {
                    continue;
                }
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && body.length() < 16 * 1024) {
                        body.append(line).append('\n');
                    }
                }
                return new JSONObject(body.toString());
            } catch (Throwable throwable) {
                Logger.printDebug(() -> "Prism: update check failed: " + throwable);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    private static void showPrompt(Activity activity, int code, String name, String notes, String url) {
        String message = "Сейчас стоит " + CURRENT_NAME + ", вышла " + name + "."
                + (notes.isEmpty() ? "" : "\n\n" + notes);
        new AlertDialog.Builder(activity)
                .setTitle("Доступно обновление Prism")
                .setMessage(message)
                .setPositiveButton("Обновить", (dialog, which) -> download(activity, name, url))
                .setNegativeButton("Позже", null)
                .setNeutralButton("Пропустить", (dialog, which) -> Settings.PRISM_UPDATE_SKIPPED.save(code))
                .show();
    }

    private static void download(Activity activity, String name, String url) {
        Context context = activity.getApplicationContext();
        try {
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("Prism " + name)
                    .setDescription("Обновление Prism")
                    .setMimeType(APK_MIME)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                            "prism-" + name + ".apk");
            long id = manager.enqueue(request);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) {
                        return;
                    }
                    try {
                        context.unregisterReceiver(this);
                    } catch (Throwable ignored) {
                        // Already gone.
                    }
                    install(context, manager, id, url);
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            Utils.showToastLong("Качаю Prism " + name + ", установщик откроется сам");
        } catch (Throwable throwable) {
            Logger.printDebug(() -> "Prism: update download failed: " + throwable);
            openInBrowser(activity, url);
        }
    }

    private static void install(Context context, DownloadManager manager, long id, String url) {
        try {
            Uri apk = manager.getUriForDownloadedFile(id);
            if (apk == null) {
                Utils.showToastLong("Не скачалось, открываю ссылку в браузере");
                openInBrowser(context, url);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apk, APK_MIME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Throwable throwable) {
            Logger.printDebug(() -> "Prism: could not open the installer: " + throwable);
            openInBrowser(context, url);
        }
    }

    private static void openInBrowser(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {
            // No browser: nothing more to do.
        }
    }
}
