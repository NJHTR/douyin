import { describe, expect, it, vi } from 'vitest'
import { recommendationChannelForLabel, trafficSourceForFeed } from '@/utils/recommendation'
import {
  LoopCompletionTracker,
  WatchBehaviorBatcher,
  videoDurationSeconds,
  watchFlushPolicy,
  type WatchFlushReason
} from '@/utils/recommendationTelemetry'

describe('recommendation feed contract', () => {
  it('maps visible channels and virtual lists to stable modes', () => {
    expect(recommendationChannelForLabel('全部')).toBe('HOME')
    expect(recommendationChannelForLabel('影视')).toBe('EXPERIENCE')
    expect(trafficSourceForFeed('follow')).toBe('FOLLOWING')
    expect(trafficSourceForFeed('hot')).toBe('HOT')
    expect(trafficSourceForFeed('home')).toBe('HOME_RECOMMEND')
  })
})

describe('WatchBehaviorBatcher', () => {
  it('keeps heartbeats out of profile sampling until the watch lifecycle settles', async () => {
    const sent: any[] = []
    const sender = vi.fn(async (_id: string, payload: any) => sent.push(payload))
    const batcher = new WatchBehaviorBatcher(sender, 60_000, () => 'account-a')

    for (const watchDuration of [15, 30]) {
      const policy = watchFlushPolicy('heartbeat')
      batcher.enqueue('42', {
        watch_duration: watchDuration,
        video_duration: 90,
        finished: policy.finished,
        profile_sample: policy.profileSample
      })
      await batcher.flush()
    }

    const terminal = watchFlushPolicy('switch')
    batcher.enqueue('42', {
      watch_duration: 34,
      video_duration: 90,
      finished: terminal.finished,
      profile_sample: terminal.profileSample
    })
    await batcher.flush()
    batcher.dispose()

    expect(sent.map((payload) => payload.profile_sample)).toEqual([false, false, true])
  })

  it('samples only terminal lifecycle reasons and marks only ended as finished', () => {
    const reasons: WatchFlushReason[] = ['heartbeat', 'pause', 'switch', 'ended', 'unmount']

    expect(reasons.map((reason) => [reason, watchFlushPolicy(reason)])).toEqual([
      ['heartbeat', { finished: false, profileSample: false }],
      ['pause', { finished: false, profileSample: true }],
      ['switch', { finished: false, profileSample: true }],
      ['ended', { finished: true, profileSample: true }],
      ['unmount', { finished: false, profileSample: true }]
    ])
  })

  it('coalesces progress by account and video before sending', async () => {
    const sent: Array<[string, any]> = []
    const sender = vi.fn(async (id: string, payload: any) => {
      sent.push([id, payload])
    })
    const batcher = new WatchBehaviorBatcher(sender, 60_000, () => 'account-a')

    batcher.enqueue('42', {
      watch_duration: 3,
      video_duration: 10,
      finished: false,
      last_position: 2,
      traffic_source: 'HOME_RECOMMEND'
    })
    batcher.enqueue('42', {
      watch_duration: 9,
      video_duration: 10,
      finished: true,
      last_position: 8,
      profile_sample: true
    })

    expect(batcher.size).toBe(1)
    await batcher.flush()
    expect(sender).toHaveBeenCalledTimes(1)
    expect(sent[0][0]).toBe('42')
    expect(sent[0][1]).toMatchObject({
      watch_duration: 9,
      last_position: 8,
      finished: true,
      profile_sample: true
    })
  })

  it('drops queued samples when the account changes', async () => {
    let account = 'a'
    const sender = vi.fn(async () => {})
    const batcher = new WatchBehaviorBatcher(sender, 60_000, () => account)
    batcher.enqueue('7', { watch_duration: 5, video_duration: 10, finished: false })
    account = 'b'
    await batcher.flush()
    expect(sender).not.toHaveBeenCalled()
    expect(batcher.size).toBe(0)
  })
})

describe('video duration normalization', () => {
  it('converts the recommendation DTO top-level millisecond duration to seconds', () => {
    expect(videoDurationSeconds({ duration: 12500 })).toBe(12.5)
  })

  it('prefers a measured HTML media duration over DTO metadata', () => {
    expect(videoDurationSeconds({ duration: 12500 }, 12.25)).toBe(12.25)
  })

  it('falls back to nested legacy duration when the DTO field is absent', () => {
    expect(videoDurationSeconds({ video: { duration: 18 } })).toBe(18)
  })
})

describe('LoopCompletionTracker', () => {
  it('settles a native loop when playback wraps from the end to the start', () => {
    const tracker = new LoopCompletionTracker()

    expect(tracker.observe(0, 30)).toBe(false)
    expect(tracker.observe(28.5, 30)).toBe(false)
    expect(tracker.observe(0.2, 30)).toBe(true)
  })

  it('does not treat an explicit backward seek as a completed loop', () => {
    const tracker = new LoopCompletionTracker()

    tracker.observe(29.5, 30)
    tracker.markSeek()
    expect(tracker.observe(0, 30)).toBe(false)

    expect(tracker.observe(29.5, 30)).toBe(false)
    expect(tracker.observe(0, 30)).toBe(true)
  })

  it('settles only once until the next video lifecycle is reset', () => {
    const tracker = new LoopCompletionTracker()

    expect(tracker.complete()).toBe(true)
    expect(tracker.complete()).toBe(false)
    expect(tracker.observe(29.5, 30)).toBe(false)
    expect(tracker.observe(0, 30)).toBe(false)

    tracker.reset()
    expect(tracker.observe(29.5, 30)).toBe(false)
    expect(tracker.observe(0, 30)).toBe(true)
  })
})
