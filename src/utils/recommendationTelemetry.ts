export interface WatchTelemetryPayload {
  watch_duration: number
  video_duration: number
  finished: boolean
  session_id?: string
  swipe_seconds?: number
  traffic_source?: string
  last_position?: number
  profile_sample?: boolean
}

export type WatchFlushReason = 'heartbeat' | 'pause' | 'switch' | 'ended' | 'unmount'

export interface WatchFlushPolicy {
  finished: boolean
  profileSample: boolean
}

/**
 * Settles one completion for a looping media lifecycle.
 *
 * Browsers normally suppress `ended` while the native `loop` attribute is
 * enabled. A genuine loop is recognizable as a jump from the final playback
 * window to the opening window. Explicit seeks clear the previous sample so
 * dragging the progress control from the end back to the start is not treated
 * as a completion.
 */
export class LoopCompletionTracker {
  private previousTime: number | null = null
  private settled = false

  observe(currentTime: number, duration: number): boolean {
    const current = Number(currentTime)
    const total = Number(duration)
    if (!Number.isFinite(current) || current < 0 || !Number.isFinite(total) || total <= 0) {
      this.previousTime = null
      return false
    }

    const previous = this.previousTime
    this.previousTime = Math.min(current, total)
    if (this.settled || previous === null) return false

    const endWindow = Math.min(2, Math.max(0.25, total * 0.08))
    const startWindow = Math.min(1, Math.max(0.1, total * 0.03))
    const rewindDistance = Math.max(0.2, total * 0.5)
    const wrapped =
      previous >= total - endWindow &&
      current <= startWindow &&
      previous - current >= rewindDistance

    return wrapped ? this.complete() : false
  }

  /** Share the same once-only settlement with a native `ended` event. */
  complete(): boolean {
    if (this.settled) return false
    this.settled = true
    return true
  }

  /** Call before an explicit seek so a backward drag cannot mimic a loop. */
  markSeek(): void {
    this.previousTime = null
  }

  /** Begin a new video lifecycle. */
  reset(): void {
    this.previousTime = null
    this.settled = false
  }
}

/**
 * Return a media duration in seconds for recommendation telemetry and labels.
 *
 * The recommendation DTO exposes its top-level `duration` in milliseconds,
 * while the nested legacy `video.duration` field (and HTMLMediaElement)
 * use seconds. Keeping this conversion in one place prevents completion
 * rates from being calculated against a value that is 1,000x too large.
 */
export function videoDurationSeconds(item: any, mediaDurationSeconds = 0): number {
  const measured = Number(mediaDurationSeconds)
  if (Number.isFinite(measured) && measured > 0) return measured

  const dtoDurationMs = Number(item?.duration)
  if (Number.isFinite(dtoDurationMs) && dtoDurationMs > 0) return dtoDurationMs / 1000

  const nestedDurationSeconds = Number(item?.video?.duration)
  if (Number.isFinite(nestedDurationSeconds) && nestedDurationSeconds > 0) {
    return nestedDurationSeconds
  }
  return 0
}

/** Keep periodic progress writes out of profile aggregation. */
export function watchFlushPolicy(reason: WatchFlushReason): WatchFlushPolicy {
  return {
    finished: reason === 'ended',
    profileSample: reason !== 'heartbeat'
  }
}

export type WatchTelemetrySender = (
  videoId: string,
  payload: WatchTelemetryPayload
) => Promise<unknown>

interface PendingSample {
  videoId: string
  payload: WatchTelemetryPayload
  /** The token at enqueue time; stale samples must not cross an account switch. */
  authKey: string
}

function finitePositive(value: unknown): number {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : 0
}

function currentAuthKey(): string {
  if (typeof localStorage === 'undefined') return ''
  return localStorage.getItem('token') || ''
}

/**
 * Coalesces progress reports by video and flushes them at a bounded cadence.
 * Watch history writes are monotonic on the server, so retaining the greatest
 * progress/position is both loss-tolerant and safe when reports race.
 */
export class WatchBehaviorBatcher {
  private readonly pending = new Map<string, PendingSample>()
  private timer: ReturnType<typeof setTimeout> | null = null
  private flushing: Promise<void> | null = null

  constructor(
    private readonly sender: WatchTelemetrySender,
    private readonly flushIntervalMs = 20_000,
    private readonly authKeyProvider: () => string = currentAuthKey
  ) {}

  enqueue(videoId: string | number, payload: WatchTelemetryPayload): void {
    const id = String(videoId || '').trim()
    const duration = finitePositive(payload.watch_duration)
    if (!id || duration <= 0) return

    const authKey = this.authKeyProvider()
    // Do not queue anonymous telemetry: the endpoint requires a user and a
    // later login must never inherit an old anonymous page's samples.
    if (!authKey) return

    const key = `${authKey}:${id}`
    const previous = this.pending.get(key)
    if (!previous) {
      this.pending.set(key, {
        videoId: id,
        authKey,
        payload: {
          ...payload,
          watch_duration: duration,
          video_duration: finitePositive(payload.video_duration),
          swipe_seconds: finitePositive(payload.swipe_seconds) || duration,
          last_position: finitePositive(payload.last_position),
          finished: Boolean(payload.finished),
          profile_sample: Boolean(payload.profile_sample)
        }
      })
    } else {
      const old = previous.payload
      old.watch_duration = Math.max(finitePositive(old.watch_duration), duration)
      old.video_duration = Math.max(
        finitePositive(old.video_duration),
        finitePositive(payload.video_duration)
      )
      old.swipe_seconds = Math.max(
        finitePositive(old.swipe_seconds),
        finitePositive(payload.swipe_seconds)
      )
      old.last_position = Math.max(
        finitePositive(old.last_position),
        finitePositive(payload.last_position)
      )
      old.finished = Boolean(old.finished || payload.finished)
      old.profile_sample = Boolean(old.profile_sample || payload.profile_sample)
      if (payload.session_id) old.session_id = payload.session_id
      if (payload.traffic_source) old.traffic_source = payload.traffic_source
    }
    this.schedule()
  }

  get size(): number {
    return this.pending.size
  }

  private schedule(): void {
    if (this.timer !== null) return
    this.timer = setTimeout(() => {
      this.timer = null
      void this.flush()
    }, this.flushIntervalMs)
  }

  async flush(): Promise<void> {
    if (this.flushing) return this.flushing
    if (!this.pending.size) return

    const currentAuth = this.authKeyProvider()
    const batch = [...this.pending.values()]
    this.pending.clear()
    // If the account changed while a page was alive, silently discard the old
    // account's samples instead of sending them under the new Authorization.
    const eligible = batch.filter((item) => item.authKey && item.authKey === currentAuth)
    if (!eligible.length) return

    this.flushing = Promise.all(
      eligible.map(async (item) => {
        try {
          await this.sender(item.videoId, item.payload)
        } catch {
          // Re-queue only if the same account is still active.  A transient
          // network failure should not erase a useful profile signal.
          if (this.authKeyProvider() !== item.authKey) return
          const key = `${item.authKey}:${item.videoId}`
          const existing = this.pending.get(key)
          if (!existing) this.pending.set(key, item)
          else {
            existing.payload.watch_duration = Math.max(
              existing.payload.watch_duration,
              item.payload.watch_duration
            )
            existing.payload.last_position = Math.max(
              existing.payload.last_position || 0,
              item.payload.last_position || 0
            )
            existing.payload.finished = Boolean(existing.payload.finished || item.payload.finished)
            existing.payload.profile_sample = Boolean(
              existing.payload.profile_sample || item.payload.profile_sample
            )
          }
        }
      })
    ).then(() => undefined)
    try {
      await this.flushing
    } finally {
      this.flushing = null
      if (this.pending.size) this.schedule()
    }
  }

  dispose(): void {
    if (this.timer !== null) clearTimeout(this.timer)
    this.timer = null
    this.pending.clear()
  }
}
