# capacitor-audio-foreground-service

Android media foreground service for apps that play audio with [`@capgo/capacitor-native-audio`](https://github.com/Cap-go/capacitor-native-audio).

That plugin can keep the player from pausing when the app backgrounds (`backgroundPlayback: true`) and can show a media notification (`showNotification: true`). It does not start a foreground service. On Android 12 and later, background playback is killed unless the app is in a `mediaPlayback` foreground service. This plugin starts that service.

It uses native audio's notification id `1001` and channel `native_audio_channel`. One notification stays on screen: the media controls native audio already draws. Tapping play, pause, or skip still goes to native audio.

iOS does not use a foreground service. `start()` resolves `{ running: false }` there. Background audio on iOS still comes from native audio plus the `audio` background mode.

## Install

From the app, with this repo checked out beside it:

```bash
npm install ../capacitor-audio-foreground-service @capgo/capacitor-native-audio
npx cap sync android
```

Requires Capacitor 8. The plugin manifest adds `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `WAKE_LOCK`. `cap sync` merges them into the app.

## Play in the background

Call `start()` while the app is visible. Android rejects a foreground start from the background.

```typescript
import { NativeAudio } from '@capgo/capacitor-native-audio';
import { AudioForeground } from 'capacitor-audio-foreground-service';

await NativeAudio.configure({
  backgroundPlayback: true,
  showNotification: true,
  focus: true,
});

await AudioForeground.start({
  title: 'Ready',
  artist: 'My App',
});

await NativeAudio.preload({
  assetId: 'episode',
  assetPath: 'https://example.com/episode.mp3',
  isUrl: true,
  notificationMetadata: {
    title: 'Episode 1',
    artist: 'My App',
  },
});

await NativeAudio.play({ assetId: 'episode' });
```

`start()` before `play()` shows a short placeholder ("Ready" / "My App"). `play()` replaces that notification in place with the native-audio controls. If you call `start()` after `play()`, the existing media notification is promoted and the placeholder never appears.

Leave the service running across pause. Stop it when playback is finished, otherwise the notification stays:

```typescript
await NativeAudio.stop({ assetId: 'episode' });
await AudioForeground.stop();
```

`AudioForeground.addListener('stopped', ...)` fires when the service exits.

Only keep the service running while audio is actually playing. `mediaPlayback` is for playback, and Play review treats a silent foreground service as misuse.

## API

| Method | Android | iOS / web |
| --- | --- | --- |
| `start(options?)` | Starts the service. Resolves `{ running: true }`. | Resolves `{ running: false }`. |
| `update({ title, artist })` | Updates the placeholder only. | Resolves. |
| `stop()` | Stops the service and removes its notification. | Resolves. |
| `isRunning()` | `{ running: true \| false }` | `{ running: false }` |

`start` options:

| Option | Default | Purpose |
| --- | --- | --- |
| `title` | `"Playing audio"` | Placeholder title until native audio posts metadata. |
| `artist` | empty | Placeholder second line. |
| `notificationId` | `1001` | Must match native audio. Change it only if that plugin's id changes. |
| `channelId` | `"native_audio_channel"` | Shared notification channel. |
| `wakeLock` | `true` | Partial wake lock while the service is running. |

Changing `notificationId` produces a second notification, because native audio keeps posting `1001`.

## iOS

Add the audio background mode in the app `Info.plist` if it is not there yet:

```xml
<key>UIBackgroundModes</key>
<array>
  <string>audio</string>
</array>
```

Native audio owns the iOS audio session. This plugin does not.
