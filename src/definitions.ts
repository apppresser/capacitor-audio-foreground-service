import type { PluginListenerHandle } from '@capacitor/core';

/**
 * Notification id used by @capgo/capacitor-native-audio
 * (`ee.forgr.audio.NativeAudio`, private `NOTIFICATION_ID`).
 * The foreground service uses the same id so Android keeps a single
 * media notification.
 */
export const NATIVE_AUDIO_NOTIFICATION_ID = 1001;

/**
 * Notification channel id used by @capgo/capacitor-native-audio.
 */
export const NATIVE_AUDIO_CHANNEL_ID = 'native_audio_channel';

export interface AudioForegroundOptions {
  /**
   * Title for the placeholder notification shown until native audio
   * publishes its media notification on the same id.
   *
   * @default "Playing audio"
   */
  title?: string;

  /**
   * Second line of the placeholder notification.
   */
  artist?: string;

  /**
   * Notification id to promote to a foreground-service notification.
   * Leave this at the default so it matches @capgo/capacitor-native-audio.
   *
   * @default 1001
   */
  notificationId?: number;

  /**
   * Notification channel id. Leave this at the default so the placeholder
   * and the native-audio notification share one channel.
   *
   * @default "native_audio_channel"
   */
  channelId?: string;

  /**
   * Hold a partial wake lock for the lifetime of the service so playback
   * continues while the CPU would otherwise sleep.
   *
   * @default true
   */
  wakeLock?: boolean;
}

export interface AudioForegroundUpdateOptions {
  /**
   * Replacement placeholder title. Ignored once native audio owns the notification.
   */
  title?: string;

  /**
   * Replacement placeholder text. Ignored once native audio owns the notification.
   */
  artist?: string;
}

export interface AudioForegroundRunningResult {
  /**
   * `true` when the Android media foreground service is running.
   * iOS and web resolve `false` because they have no foreground service.
   */
  running: boolean;
}

export interface AudioForegroundPlugin {
  /**
   * Start an Android `mediaPlayback` foreground service.
   *
   * Call this while the app is visible, after
   * `NativeAudio.configure({ backgroundPlayback: true, showNotification: true })`,
   * and before the user leaves the app. If native audio has already posted
   * notification `1001`, that notification becomes the foreground notification
   * and keeps its playback controls. Otherwise a placeholder is shown and
   * replaced in place when playback starts.
   *
   * On iOS and web this resolves with `running: false`.
   */
  start(options?: AudioForegroundOptions): Promise<AudioForegroundRunningResult>;

  /**
   * Update the placeholder notification title or artist.
   * Has no effect once @capgo/capacitor-native-audio has replaced the
   * placeholder with its media notification.
   */
  update(options: AudioForegroundUpdateOptions): Promise<void>;

  /**
   * Stop the foreground service and remove its notification.
   * Call this when playback ends. Safe to call when the service is already stopped.
   */
  stop(): Promise<void>;

  /**
   * Whether the Android foreground service is currently running.
   */
  isRunning(): Promise<AudioForegroundRunningResult>;

  /**
   * Fired when the service leaves the foreground, including after `stop()`.
   */
  addListener(eventName: 'stopped', listenerFunc: () => void): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners for this plugin.
   */
  removeAllListeners(): Promise<void>;
}
