import { WebPlugin } from '@capacitor/core';

import type {
  AudioForegroundOptions,
  AudioForegroundPlugin,
  AudioForegroundRunningResult,
  AudioForegroundUpdateOptions,
} from './definitions';

export class AudioForegroundWeb extends WebPlugin implements AudioForegroundPlugin {
  async start(_options?: AudioForegroundOptions): Promise<AudioForegroundRunningResult> {
    return { running: false };
  }

  async update(_options: AudioForegroundUpdateOptions): Promise<void> {
    return;
  }

  async stop(): Promise<void> {
    return;
  }

  async isRunning(): Promise<AudioForegroundRunningResult> {
    return { running: false };
  }
}
