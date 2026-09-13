import { recordWatch } from '@/api/videos'
import { WatchBehaviorBatcher, type WatchTelemetryPayload } from '@/utils/recommendationTelemetry'

let singleton: WatchBehaviorBatcher | null = null
let lifecycleInstalled = false

export function getRecommendationWatchBatcher(): WatchBehaviorBatcher {
  if (!singleton)
    singleton = new WatchBehaviorBatcher((videoId, payload) => recordWatch(videoId, payload))
  if (!lifecycleInstalled && typeof window !== 'undefined') {
    const flush = () => {
      if (singleton) void singleton.flush()
    }
    window.addEventListener('pagehide', flush)
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') flush()
    })
    lifecycleInstalled = true
  }
  return singleton
}

export function queueRecommendationWatch(
  videoId: string | number,
  payload: WatchTelemetryPayload
): void {
  getRecommendationWatchBatcher().enqueue(videoId, payload)
}
