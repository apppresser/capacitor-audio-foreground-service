package com.capacitorplugins.audioforeground;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@CapacitorPlugin(name = "AudioForeground")
public class AudioForegroundPlugin extends Plugin {

    private static final long START_TIMEOUT_SECONDS = 5;

    private final AudioForegroundService.StopListener stopListener = () -> {
        Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> {
            if (getBridge() == null) {
                return;
            }
            notifyListeners("stopped", new JSObject());
        });
    };

    @Override
    public void load() {
        AudioForegroundService.addStopListener(stopListener);
    }

    @Override
    protected void handleOnDestroy() {
        AudioForegroundService.removeStopListener(stopListener);
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (getContext() == null) {
            call.reject("Application context is not available");
            return;
        }

        int notificationId = call.getInt("notificationId", AudioForegroundService.DEFAULT_NOTIFICATION_ID);
        if (notificationId <= 0) {
            call.reject("notificationId must be a positive integer");
            return;
        }

        String channelId = call.getString("channelId", AudioForegroundService.DEFAULT_CHANNEL_ID);
        if (channelId == null || channelId.trim().isEmpty()) {
            call.reject("channelId must not be empty");
            return;
        }

        Intent intent = new Intent(getContext(), AudioForegroundService.class);
        intent.setAction(AudioForegroundService.ACTION_START);
        putOptionalString(intent, AudioForegroundService.EXTRA_TITLE, call.getString("title"));
        putOptionalString(intent, AudioForegroundService.EXTRA_ARTIST, call.getString("artist"));
        intent.putExtra(AudioForegroundService.EXTRA_NOTIFICATION_ID, notificationId);
        intent.putExtra(AudioForegroundService.EXTRA_CHANNEL_ID, channelId);
        Boolean wakeLock = call.getBoolean("wakeLock", true);
        intent.putExtra(AudioForegroundService.EXTRA_WAKE_LOCK, wakeLock == null || wakeLock);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AudioForegroundService.StartResult> result = new AtomicReference<>();
        boolean listening = AudioForegroundService.listenForStart((ok, message) -> {
            result.set(new AudioForegroundService.StartResult(ok, message));
            latch.countDown();
        });
        if (!listening) {
            call.reject("Audio foreground service is already starting");
            return;
        }

        try {
            ContextCompat.startForegroundService(getContext(), intent);
            if (!latch.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                AudioForegroundService.cancelStartListener();
                call.reject(
                    "Timed out starting the audio foreground service. Call start() while the app is in the foreground."
                );
                return;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            AudioForegroundService.cancelStartListener();
            call.reject("Interrupted while starting the audio foreground service");
            return;
        } catch (Exception ex) {
            AudioForegroundService.cancelStartListener();
            call.reject(AudioForegroundService.describe(ex), ex);
            return;
        }

        AudioForegroundService.StartResult startResult = result.get();
        if (startResult == null || !startResult.ok) {
            String message = startResult != null && startResult.message != null
                ? startResult.message
                : "Unable to start the audio foreground service";
            call.reject(message);
            return;
        }

        JSObject ret = new JSObject();
        ret.put("running", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void update(PluginCall call) {
        if (getContext() == null) {
            call.reject("Application context is not available");
            return;
        }
        if (!AudioForegroundService.isRunning()) {
            call.reject("Audio foreground service is not running");
            return;
        }

        Intent intent = new Intent(getContext(), AudioForegroundService.class);
        intent.setAction(AudioForegroundService.ACTION_UPDATE);
        putOptionalString(intent, AudioForegroundService.EXTRA_TITLE, call.getString("title"));
        putOptionalString(intent, AudioForegroundService.EXTRA_ARTIST, call.getString("artist"));

        try {
            getContext().startService(intent);
            call.resolve();
        } catch (Exception ex) {
            call.reject("Unable to update the audio foreground service. " + ex.getMessage(), ex);
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (!AudioForegroundService.isRunning() || getContext() == null) {
            call.resolve();
            return;
        }

        Intent intent = new Intent(getContext(), AudioForegroundService.class);
        intent.setAction(AudioForegroundService.ACTION_STOP);
        try {
            getContext().startService(intent);
            call.resolve();
        } catch (Exception ex) {
            call.reject("Unable to stop the audio foreground service. " + ex.getMessage(), ex);
        }
    }

    @PluginMethod
    public void isRunning(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("running", AudioForegroundService.isRunning());
        call.resolve(ret);
    }

    private static void putOptionalString(Intent intent, String key, String value) {
        if (value != null) {
            intent.putExtra(key, value);
        }
    }
}
