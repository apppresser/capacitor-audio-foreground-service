import { registerPlugin } from '@capacitor/core';

import type { AudioForegroundPlugin } from './definitions';

const AudioForeground = registerPlugin<AudioForegroundPlugin>('AudioForeground', {
  web: () => import('./web').then((m) => new m.AudioForegroundWeb()),
});

export * from './definitions';
export { AudioForeground };
