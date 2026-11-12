package com.capacitorplugins.audioforeground;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Media-playback foreground service that keeps the process alive while
 * @capgo/capacitor-native-audio plays.
 *
 * Native audio posts its media notification with id {@link #DEFAULT_NOTIFICATION_ID}
 * on channel {@link #DEFAULT_CHANNEL_ID} and does not call {@code startForeground}.
 * This service promotes that notification when it already exists. Until then it
 * shows a placeholder on the same id, which native audio replaces in place
 * when playback starts.
 */
public class AudioForegroundService extends Service {

    public static final String ACTION_START = "com.capacitorplugins.audioforeground.START";
    public static final String ACTION_UPDATE = "com.capacitorplugins.audioforeground.UPDATE";
    public static final String ACTION_STOP = "com.capacitorplugins.audioforeground.STOP";

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_NOTIFICATION_ID = "notificationId";
    public static final String EXTRA_CHANNEL_ID = "channelId";
    public static final String EXTRA_WAKE_LOCK = "wakeLock";

    /**
     * Matches the private {@code NOTIFICATION_ID} in
     * {@code ee.forgr.audio.NativeAudio}.
     */
    public static final int DEFAULT_NOTIFICATION_ID = 1001;

    /**
     * Matches the private {@code CHANNEL_ID} in
     * {@code ee.forgr.audio.NativeAudio}.
     */
    public static final String DEFAULT_CHANNEL_ID = "native_audio_channel";

    static final String EXTRA_PLACEHOLDER = "com.capacitorplugins.audioforeground.placeholder";

    private static final String TAG = "AudioForegroundService";
    private static final String WAKE_LOCK_TAG = "AudioForeground:playback";

    private static final Object START_LOCK = new Object();
    private static volatile StartListener startListener;
    private static final CopyOnWriteArrayList<StopListener> stopListeners = new CopyOnWriteArrayList<>();
    private static volatile boolean running = false;

    private static volatile int activeSession = 0;

    private final int sessionId = System.identityHashCode(this);
    private int notificationId = DEFAULT_NOTIFICATION_ID;
    private String channelId = DEFAULT_CHANNEL_ID;
    private String title = "Playing audio";
    private String artist = "";
    private boolean useWakeLock = true;
    private boolean inForeground = false;
    private PowerManager.WakeLock wakeLock;

    public static boolean isRunning() {
        return running;
    }

    public static boolean listenForStart(StartListener listener) {
        synchronized (START_LOCK) {
            if (startListener != null) {
                return false;
            }
            startListener = listener;
            return true;
        }
    }

    public static void cancelStartListener() {
        completeStart(false, null);
    }

    static void addStopListener(StopListener listener) {
        stopListeners.add(listener);
    }

    static void removeStopListener(StopListener listener) {
        stopListeners.remove(listener);
    }

    public static String describe(Exception error) {
        String detail = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        if (error instanceof SecurityException) {
            return (
                "Missing foreground-service permission. Run cap sync so FOREGROUND_SERVICE, " +
                "FOREGROUND_SERVICE_MEDIA_PLAYBACK, and WAKE_LOCK are merged into the app. " +
                detail
            );
        }
        if (isStartNotAllowed(error)) {
            return "Android blocked the foreground service because the app is in the background. Call start() while the app is visible. " + detail;
        }
        return "Unable to start the audio foreground service. " + detail;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            try {
                ensureForeground(buildPlaceholder());
            } catch (Exception error) {
                Log.e(TAG, "Failed to restore the foreground service", error);
            }
            shutdown();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            completeStart(false, "Audio foreground service was stopped before it finished starting");
            shutdown();
            return START_NOT_STICKY;
        }

        applyOptions(intent);

        if (ACTION_UPDATE.equals(intent.getAction())) {
            if (inForeground) {
                refreshPlaceholder();
            }
            return START_NOT_STICKY;
        }

        try {
            Notification existing = findActiveNotification(notificationId);
            Notification notification;
            if (existing != null && !isPlaceholder(existing)) {
                notification = existing;
                Log.i(TAG, "Promoting native-audio notification " + notificationId + " to the foreground");
            } else {
                notification = buildPlaceholder();
                Log.i(TAG, "Starting foreground with placeholder notification " + notificationId);
            }
            ensureForeground(notification);
        } catch (Exception error) {
            Log.e(TAG, "Failed to enter the foreground", error);
            completeStart(false, describe(error));
            shutdown();
            return START_NOT_STICKY;
        }

        try {
            if (useWakeLock) {
                acquireWakeLock();
            } else {
                releaseWakeLock();
            }
        } catch (Exception error) {
            Log.w(TAG, "Continuing without a wake lock", error);
        }
        completeStart(true, null);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        if (activeSession == sessionId) {
            activeSession = 0;
            boolean wasRunning = running;
            running = false;
            if (wasRunning || inForeground) {
                inForeground = false;
                dispatchStopped();
            }
        }
        super.onDestroy();
    }

    private void applyOptions(Intent intent) {
        if (intent.hasExtra(EXTRA_NOTIFICATION_ID)) {
            int requestedId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID);
            if (requestedId > 0) {
                notificationId = requestedId;
            }
        }
        if (intent.hasExtra(EXTRA_CHANNEL_ID)) {
            String requestedChannel = intent.getStringExtra(EXTRA_CHANNEL_ID);
            if (requestedChannel != null && !requestedChannel.trim().isEmpty()) {
                channelId = requestedChannel;
            }
        }
        if (intent.hasExtra(EXTRA_TITLE)) {
            String requestedTitle = intent.getStringExtra(EXTRA_TITLE);
            if (requestedTitle != null && !requestedTitle.trim().isEmpty()) {
                title = requestedTitle;
            }
        }
        if (intent.hasExtra(EXTRA_ARTIST)) {
            String requestedArtist = intent.getStringExtra(EXTRA_ARTIST);
            artist = requestedArtist == null ? "" : requestedArtist;
        }
        if (intent.hasExtra(EXTRA_WAKE_LOCK)) {
            useWakeLock = intent.getBooleanExtra(EXTRA_WAKE_LOCK, true);
        }
        if (title == null || title.trim().isEmpty()) {
            title = getString(R.string.audio_foreground_notification_title);
        }
    }

    private void refreshPlaceholder() {
        Notification existing = findActiveNotification(notificationId);
        if (existing != null && !isPlaceholder(existing)) {
            return;
        }
        NotificationManager manager = getNotificationManager();
        if (manager == null) {
            return;
        }
        manager.notify(notificationId, buildPlaceholder());
    }

    private void ensureForeground(Notification notification) {
        ensureChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(notificationId, notification);
        }
        inForeground = true;
        running = true;
        activeSession = sessionId;
    }

    private Notification buildPlaceholder() {
        ensureChannel();
        Bundle extras = new Bundle();
        extras.putBoolean(EXTRA_PLACEHOLDER, true);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_audio)
            .setContentTitle(title)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addExtras(extras);

        if (artist != null && !artist.isEmpty()) {
            builder.setContentText(artist);
        }

        PendingIntent contentIntent = launchIntent();
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent);
        }

        return builder.build();
    }

    private PendingIntent launchIntent() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch == null) {
            return null;
        }
        launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, notificationId, launch, flags);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
            channelId,
            getString(R.string.audio_foreground_channel_name),
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.audio_foreground_channel_description));
        channel.setSound(null, null);
        NotificationManager manager = getNotificationManager();
        if (manager == null) {
            throw new IllegalStateException("NotificationManager is not available");
        }
        manager.createNotificationChannel(channel);
    }

    private Notification findActiveNotification(int id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        NotificationManager manager = getNotificationManager();
        if (manager == null) {
            return null;
        }
        StatusBarNotification[] active = manager.getActiveNotifications();
        if (active == null) {
            return null;
        }
        for (StatusBarNotification statusBarNotification : active) {
            if (statusBarNotification.getId() == id) {
                return statusBarNotification.getNotification();
            }
        }
        return null;
    }

    private static boolean isPlaceholder(Notification notification) {
        return notification.extras != null && notification.extras.getBoolean(EXTRA_PLACEHOLDER, false);
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void shutdown() {
        boolean shouldNotify = inForeground || (activeSession == sessionId && running);
        inForeground = false;
        if (activeSession == sessionId) {
            activeSession = 0;
            running = false;
        }
        releaseWakeLock();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception error) {
            Log.w(TAG, "stopForeground failed", error);
        }
        stopSelf();
        if (shouldNotify) {
            dispatchStopped();
        }
    }

    private static void dispatchStopped() {
        List<StopListener> listeners = stopListeners;
        for (StopListener listener : listeners) {
            listener.onStopped();
        }
    }

    private static void completeStart(boolean ok, String message) {
        StartListener listener;
        synchronized (START_LOCK) {
            listener = startListener;
            startListener = null;
        }
        if (listener != null) {
            listener.onComplete(ok, message);
        }
    }

    private static boolean isStartNotAllowed(Exception error) {
        return (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            "android.app.ForegroundServiceStartNotAllowedException".equals(error.getClass().getName())
        );
    }

    public interface StartListener {
        void onComplete(boolean ok, String message);
    }

    public static final class StartResult {

        public final boolean ok;
        public final String message;

        public StartResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    interface StopListener {
        void onStopped();
    }
}
