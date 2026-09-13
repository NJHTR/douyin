export type FeedbackKind =
  | 'chat'
  | 'group'
  | 'follow'
  | 'like'
  | 'comment'
  | 'collect'
  | 'mention'
  | 'friend'
  | 'call'

/** Semantic sound tokens. Business code must use these tokens instead of files. */
export type SoundEvent =
  | 'ui.tap' | 'ui.selection' | 'ui.success' | 'ui.warning' | 'ui.error' | 'ui.confirm' | 'ui.cancel'
  | 'navigation.push' | 'navigation.pop' | 'sheet.open' | 'sheet.close'
  | 'message.received' | 'message.sent' | 'message.failed' | 'message.retry'
  | 'like' | 'follow' | 'favorite'
  | 'upload.start' | 'upload.progress' | 'upload.complete' | 'upload.failed'
  | 'call.incoming' | 'call.ringing' | 'call.connecting' | 'call.connected'
  | 'call.reconnecting' | 'call.rejected' | 'call.busy' | 'call.timeout' | 'call.ended' | 'call.failed'
  | 'notification.important' | 'notification.normal' | 'notification.background'

export type SoundPriority = 0 | 1 | 2 | 3 | 4 | 5

export interface SoundContext {
  /** Suppress notification feedback while the corresponding conversation is open. */
  isCurrentConversation?: boolean
  /** Background events are silent unless explicitly important. */
  isBackground?: boolean
  /** Stable id used for event-level deduplication. */
  eventId?: string
  /** Override the default cooldown for a single semantic event. */
  cooldownMs?: number
}

export interface SoundPolicy {
  kind: FeedbackKind
  priority: SoundPriority
  volume: number
  cooldownMs: number
  interruptible: boolean
  dedupe: boolean
  haptic: boolean
}

export const SOUND_POLICY: Record<SoundEvent, SoundPolicy> = {
  'ui.tap': { kind: 'like', priority: 5, volume: 0.16, cooldownMs: 120, interruptible: true, dedupe: true, haptic: false },
  'ui.selection': { kind: 'like', priority: 5, volume: 0.12, cooldownMs: 120, interruptible: true, dedupe: true, haptic: true },
  'ui.success': { kind: 'friend', priority: 3, volume: 0.32, cooldownMs: 250, interruptible: true, dedupe: true, haptic: true },
  'ui.warning': { kind: 'mention', priority: 2, volume: 0.28, cooldownMs: 350, interruptible: true, dedupe: true, haptic: true },
  'ui.error': { kind: 'comment', priority: 2, volume: 0.3, cooldownMs: 350, interruptible: true, dedupe: true, haptic: true },
  'ui.confirm': { kind: 'friend', priority: 3, volume: 0.28, cooldownMs: 250, interruptible: true, dedupe: true, haptic: true },
  'ui.cancel': { kind: 'comment', priority: 4, volume: 0.2, cooldownMs: 180, interruptible: true, dedupe: true, haptic: false },
  'navigation.push': { kind: 'like', priority: 5, volume: 0.1, cooldownMs: 180, interruptible: true, dedupe: true, haptic: false },
  'navigation.pop': { kind: 'like', priority: 5, volume: 0.1, cooldownMs: 180, interruptible: true, dedupe: true, haptic: false },
  'sheet.open': { kind: 'like', priority: 5, volume: 0.1, cooldownMs: 180, interruptible: true, dedupe: true, haptic: false },
  'sheet.close': { kind: 'like', priority: 5, volume: 0.1, cooldownMs: 180, interruptible: true, dedupe: true, haptic: false },
  'message.received': { kind: 'chat', priority: 1, volume: 0.42, cooldownMs: 650, interruptible: true, dedupe: true, haptic: true },
  'message.sent': { kind: 'like', priority: 5, volume: 0.14, cooldownMs: 250, interruptible: true, dedupe: true, haptic: false },
  'message.failed': { kind: 'comment', priority: 2, volume: 0.3, cooldownMs: 400, interruptible: true, dedupe: true, haptic: true },
  'message.retry': { kind: 'mention', priority: 3, volume: 0.22, cooldownMs: 400, interruptible: true, dedupe: true, haptic: false },
  like: { kind: 'like', priority: 4, volume: 0.18, cooldownMs: 220, interruptible: true, dedupe: true, haptic: true },
  follow: { kind: 'follow', priority: 4, volume: 0.24, cooldownMs: 300, interruptible: true, dedupe: true, haptic: true },
  favorite: { kind: 'collect', priority: 4, volume: 0.22, cooldownMs: 300, interruptible: true, dedupe: true, haptic: true },
  'upload.start': { kind: 'like', priority: 4, volume: 0.16, cooldownMs: 250, interruptible: true, dedupe: true, haptic: false },
  'upload.progress': { kind: 'like', priority: 5, volume: 0.08, cooldownMs: 800, interruptible: true, dedupe: true, haptic: false },
  'upload.complete': { kind: 'friend', priority: 3, volume: 0.32, cooldownMs: 300, interruptible: true, dedupe: true, haptic: true },
  'upload.failed': { kind: 'comment', priority: 2, volume: 0.3, cooldownMs: 400, interruptible: true, dedupe: true, haptic: true },
  'call.incoming': { kind: 'call', priority: 0, volume: 0.75, cooldownMs: 900, interruptible: false, dedupe: true, haptic: true },
  'call.ringing': { kind: 'call', priority: 0, volume: 0.7, cooldownMs: 1200, interruptible: false, dedupe: true, haptic: true },
  'call.connecting': { kind: 'mention', priority: 1, volume: 0.34, cooldownMs: 300, interruptible: true, dedupe: true, haptic: true },
  'call.connected': { kind: 'friend', priority: 1, volume: 0.4, cooldownMs: 500, interruptible: true, dedupe: true, haptic: true },
  'call.reconnecting': { kind: 'mention', priority: 1, volume: 0.3, cooldownMs: 1200, interruptible: true, dedupe: true, haptic: true },
  'call.rejected': { kind: 'comment', priority: 1, volume: 0.3, cooldownMs: 500, interruptible: true, dedupe: true, haptic: true },
  'call.busy': { kind: 'comment', priority: 1, volume: 0.3, cooldownMs: 500, interruptible: true, dedupe: true, haptic: true },
  'call.timeout': { kind: 'comment', priority: 1, volume: 0.3, cooldownMs: 500, interruptible: true, dedupe: true, haptic: true },
  'call.ended': { kind: 'comment', priority: 1, volume: 0.26, cooldownMs: 500, interruptible: true, dedupe: true, haptic: true },
  'call.failed': { kind: 'comment', priority: 1, volume: 0.3, cooldownMs: 500, interruptible: true, dedupe: true, haptic: true },
  'notification.important': { kind: 'mention', priority: 1, volume: 0.46, cooldownMs: 650, interruptible: true, dedupe: true, haptic: true },
  'notification.normal': { kind: 'comment', priority: 3, volume: 0.28, cooldownMs: 900, interruptible: true, dedupe: true, haptic: true },
  'notification.background': { kind: 'comment', priority: 5, volume: 0.14, cooldownMs: 1500, interruptible: true, dedupe: true, haptic: false }
}

const SOUND_PACK: Partial<Record<SoundEvent, string>> = {
  'ui.tap': '/sounds/uisfx/soft/notification.mp3',
  'ui.selection': '/sounds/uisfx/soft/notification.mp3',
  'ui.success': '/sounds/uisfx/soft/success.mp3',
  'ui.warning': '/sounds/uisfx/soft/warning.mp3',
  'ui.error': '/sounds/uisfx/soft/error.mp3',
  'ui.confirm': '/sounds/uisfx/soft/success.mp3',
  'ui.cancel': '/sounds/uisfx/soft/cancel.mp3',
  'navigation.push': '/sounds/uisfx/soft/connect.mp3',
  'navigation.pop': '/sounds/uisfx/soft/cancel.mp3',
  'sheet.open': '/sounds/uisfx/soft/connect.mp3',
  'sheet.close': '/sounds/uisfx/soft/cancel.mp3',
  'message.received': '/sounds/uisfx/soft/receive.mp3',
  'message.sent': '/sounds/uisfx/soft/send.mp3',
  'message.failed': '/sounds/uisfx/soft/error.mp3',
  'message.retry': '/sounds/uisfx/soft/connecting.mp3',
  like: '/sounds/uisfx/soft/notification.mp3',
  follow: '/sounds/uisfx/soft/success.mp3',
  favorite: '/sounds/uisfx/soft/complete.mp3',
  'upload.start': '/sounds/uisfx/soft/connecting.mp3',
  'upload.progress': '/sounds/uisfx/soft/notification.mp3',
  'upload.complete': '/sounds/uisfx/soft/complete.mp3',
  'upload.failed': '/sounds/uisfx/soft/error.mp3',
  'call.incoming': '/sounds/uisfx/soft/notification.mp3',
  'call.ringing': '/sounds/uisfx/soft/notification.mp3',
  'call.connecting': '/sounds/uisfx/soft/connecting.mp3',
  'call.connected': '/sounds/uisfx/soft/connect.mp3',
  'call.reconnecting': '/sounds/uisfx/soft/connecting.mp3',
  'call.rejected': '/sounds/uisfx/soft/cancel.mp3',
  'call.busy': '/sounds/uisfx/soft/warning.mp3',
  'call.timeout': '/sounds/uisfx/soft/warning.mp3',
  'call.ended': '/sounds/uisfx/soft/cancel.mp3',
  'call.failed': '/sounds/uisfx/soft/error.mp3',
  'notification.important': '/sounds/uisfx/soft/notification.mp3',
  'notification.normal': '/sounds/uisfx/soft/notification.mp3'
}

export interface NotificationFeedbackSettings {
  enabled: boolean
  soundEnabled: boolean
  vibrationEnabled: boolean
  reducedFeedback: boolean
  volume: number
  soundUrls: Partial<Record<FeedbackKind, string>>
  vibrationPatterns: Partial<Record<FeedbackKind, number[]>>
}

const STORAGE_KEY = 'douyin.notification-feedback.v1'
const DEFAULT_PATTERN: number[] = [80]
const DEFAULT_PATTERNS: Record<FeedbackKind, number[]> = {
  chat: [60],
  group: [40, 40, 40],
  follow: [90, 50, 90],
  like: [35],
  comment: [60, 35, 60],
  collect: [100, 50, 100],
  mention: [45, 35, 45],
  friend: [120, 60, 120],
  call: [220, 100, 220, 100, 220]
}
let settings: NotificationFeedbackSettings = readSettings()
let audioContext: AudioContext | null = null
let audioUnlocked = false
let unlockInstalled = false
let callRingtoneTimer: ReturnType<typeof setInterval> | null = null
let callRingtoneGeneration = 0
const lastPlayedAt = new Map<string, number>()
const activeAudio = new Set<HTMLAudioElement>()

function readSettings(): NotificationFeedbackSettings {
  const defaults: NotificationFeedbackSettings = {
    enabled: true,
    soundEnabled: true,
    vibrationEnabled: true,
    reducedFeedback: false,
    volume: 0.55,
    soundUrls: {},
    vibrationPatterns: {}
  }
  try {
    if (typeof localStorage === 'undefined') return defaults
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    return {
      ...defaults,
      ...stored,
      volume: Math.max(0, Math.min(1, Number(stored.volume ?? defaults.volume))),
      soundUrls: stored.soundUrls || {},
      vibrationPatterns: stored.vibrationPatterns || {}
    }
  } catch {
    return defaults
  }
}

function persist() {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
    }
  } catch (error) {
    console.warn('[notification-feedback] settings could not be persisted', error)
  }
}

export function getNotificationFeedbackSettings(): NotificationFeedbackSettings {
  return JSON.parse(JSON.stringify(settings))
}

export function updateNotificationFeedbackSettings(
  patch: Partial<NotificationFeedbackSettings>
): NotificationFeedbackSettings {
  settings = { ...settings, ...patch }
  persist()
  return getNotificationFeedbackSettings()
}

/** Runtime snapshot of the semantic policy, useful to debug product feedback. */
export function getSoundPolicy(event: SoundEvent): SoundPolicy {
  return SOUND_POLICY[event]
}

function shouldSuppress(event: SoundEvent, context: SoundContext, policy: SoundPolicy): boolean {
  if (context.isCurrentConversation && event === 'message.received') return true
  if (context.isBackground && event === 'notification.background') return true
  const now = Date.now()
  const key = context.eventId ? `${event}:${context.eventId}` : event
  const last = lastPlayedAt.get(key)
  const cooldown = context.cooldownMs ?? policy.cooldownMs
  if (policy.dedupe && last !== undefined && now - last < cooldown) return true
  lastPlayedAt.set(key, now)
  if (lastPlayedAt.size > 200) {
    const oldest = lastPlayedAt.keys().next().value
    if (oldest) lastPlayedAt.delete(oldest)
  }
  return false
}

export function resetNotificationFeedbackSettings() {
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* ignore storage access errors */
  }
  settings = readSettings()
  return getNotificationFeedbackSettings()
}

export function setVibrationPattern(kind: FeedbackKind, pattern: number[]) {
  settings.vibrationPatterns = { ...settings.vibrationPatterns, [kind]: pattern }
  persist()
}

export function setCustomNotificationSound(kind: FeedbackKind, file: File): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!file.type.startsWith('audio/')) {
      reject(new Error('请选择音频文件'))
      return
    }
    if (file.size > 2 * 1024 * 1024) {
      reject(new Error('自定义声音不能超过 2MB'))
      return
    }
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error || new Error('声音读取失败'))
    reader.onload = () => {
      settings.soundUrls = { ...settings.soundUrls, [kind]: String(reader.result || '') }
      persist()
      resolve()
    }
    reader.readAsDataURL(file)
  })
}

export function installNotificationFeedbackUnlock(): () => void {
  if (unlockInstalled || typeof window === 'undefined') return () => {}
  unlockInstalled = true
  const unlock = () => {
    audioUnlocked = true
    if (audioContext?.state === 'suspended') void audioContext.resume()
    window.removeEventListener('pointerdown', unlock)
    window.removeEventListener('keydown', unlock)
    unlockInstalled = false
  }
  window.addEventListener('pointerdown', unlock, { once: true, passive: true })
  window.addEventListener('keydown', unlock, { once: true })
  return () => {
    window.removeEventListener('pointerdown', unlock)
    window.removeEventListener('keydown', unlock)
    unlockInstalled = false
  }
}

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null
  const AudioContextCtor = window.AudioContext || (window as any).webkitAudioContext
  if (!AudioContextCtor) return null
  if (!audioContext) audioContext = new AudioContextCtor()
  return audioContext
}

function scheduleTone(
  context: AudioContext,
  frequency: number,
  start: number,
  duration: number,
  peak: number,
  type: OscillatorType = 'sine',
  endFrequency?: number
) {
  const oscillator = context.createOscillator()
  const gain = context.createGain()
  oscillator.type = type
  oscillator.frequency.setValueAtTime(frequency, start)
  if (endFrequency) oscillator.frequency.exponentialRampToValueAtTime(endFrequency, start + duration)
  gain.gain.setValueAtTime(0.0001, start)
  gain.gain.exponentialRampToValueAtTime(Math.max(0.01, peak), start + 0.008)
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration)
  oscillator.connect(gain)
  gain.connect(context.destination)
  oscillator.start(start)
  oscillator.stop(start + duration + 0.02)
}

function scheduleNoise(
  context: AudioContext,
  start: number,
  duration: number,
  peak: number,
  frequency: number
) {
  const frameCount = Math.max(1, Math.floor(context.sampleRate * duration))
  const buffer = context.createBuffer(1, frameCount, context.sampleRate)
  const data = buffer.getChannelData(0)
  for (let i = 0; i < frameCount; i++) data[i] = Math.random() * 2 - 1
  const source = context.createBufferSource()
  const filter = context.createBiquadFilter()
  const gain = context.createGain()
  source.buffer = buffer
  filter.type = 'bandpass'
  filter.frequency.value = frequency
  filter.Q.value = 0.7
  gain.gain.setValueAtTime(0.0001, start)
  gain.gain.exponentialRampToValueAtTime(Math.max(0.01, peak), start + 0.006)
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration)
  source.connect(filter)
  filter.connect(gain)
  gain.connect(context.destination)
  source.start(start)
  source.stop(start + duration + 0.02)
}

function scheduleKnock(context: AudioContext, start: number, peak: number) {
  scheduleTone(context, 155, start, 0.11, peak, 'sine', 72)
  scheduleNoise(context, start, 0.045, peak * 0.45, 950)
}

function scheduleChime(context: AudioContext, start: number, frequency: number, peak: number) {
  scheduleTone(context, frequency, start, 0.32, peak, 'sine', frequency * 0.98)
  scheduleTone(context, frequency * 2, start, 0.22, peak * 0.35, 'sine')
}

async function playDefaultTone(kind: FeedbackKind) {
  if (!audioUnlocked) return
  const context = getAudioContext()
  if (!context) return
  if (context.state === 'suspended') await context.resume().catch(() => {})
  const now = context.currentTime + 0.01
  const peak = Math.min(0.48, Math.max(0.04, settings.volume * 0.42))

  switch (kind) {
    // 具有辨识度的双敲门声，作为私聊默认音。
    case 'chat':
      scheduleKnock(context, now, peak)
      scheduleKnock(context, now + 0.18, peak * 0.9)
      break
    // 群聊用三连敲，和私聊保持明显区别。
    case 'group':
      scheduleKnock(context, now, peak)
      scheduleKnock(context, now + 0.16, peak * 0.9)
      scheduleKnock(context, now + 0.32, peak * 0.8)
      break
    // 关注/收藏使用上扬的短铃声。
    case 'follow':
      scheduleChime(context, now, 620, peak)
      scheduleChime(context, now + 0.16, 820, peak * 0.9)
      scheduleChime(context, now + 0.32, 1040, peak * 0.8)
      break
    case 'collect':
      scheduleChime(context, now, 740, peak)
      scheduleChime(context, now + 0.18, 980, peak * 0.85)
      break
    // 点赞是短促气泡音，评论带一段轻微的咳嗽感噪声。
    case 'like':
      scheduleTone(context, 360, now, 0.08, peak, 'triangle', 720)
      break
    case 'comment':
      scheduleNoise(context, now, 0.16, peak * 0.72, 900)
      scheduleNoise(context, now + 0.13, 0.12, peak * 0.58, 1250)
      scheduleTone(context, 170, now, 0.22, peak * 0.38, 'sine', 110)
      break
    case 'mention':
      scheduleTone(context, 760, now, 0.13, peak, 'sine', 980)
      scheduleTone(context, 980, now + 0.16, 0.16, peak * 0.85, 'sine', 1180)
      break
    case 'friend':
      scheduleTone(context, 430, now, 0.2, peak, 'triangle', 520)
      scheduleTone(context, 650, now + 0.2, 0.25, peak * 0.9, 'triangle', 760)
      break
    // 来电使用双段电话铃，存在感最强。
    case 'call':
      scheduleTone(context, 440, now, 0.38, peak, 'sine', 520)
      scheduleTone(context, 660, now, 0.38, peak * 0.75, 'sine', 760)
      scheduleTone(context, 440, now + 0.55, 0.38, peak, 'sine', 520)
      scheduleTone(context, 660, now + 0.55, 0.38, peak * 0.75, 'sine', 760)
      break
  }
}

async function playCustomSound(url: string, volume = settings.volume): Promise<boolean> {
  const audio = new Audio(url)
  audio.volume = volume
  activeAudio.add(audio)
  audio.addEventListener('ended', () => activeAudio.delete(audio), { once: true })
  return audio.play().then(() => true).catch(() => {
    activeAudio.delete(audio)
    return false
  })
}

/**
 * The only public sound entry point for product interactions. It owns policy,
 * deduplication, volume hierarchy, haptic pairing and accessibility suppression.
 */
export async function playSound(event: SoundEvent, context: SoundContext = {}): Promise<boolean> {
  const policy = SOUND_POLICY[event]
  if (!policy || !settings.enabled || settings.reducedFeedback) return false
  if (shouldSuppress(event, context, policy)) return false
  if (settings.soundEnabled) {
    const customUrl = settings.soundUrls[policy.kind] || SOUND_PACK[event]
    if (customUrl) {
      const played = await playCustomSound(customUrl, Math.min(1, settings.volume * policy.volume / 0.42))
      if (!played) {
        const contextAudio = getAudioContext()
        if (contextAudio) {
          const previousVolume = settings.volume
          settings.volume = Math.min(1, previousVolume * policy.volume / 0.42)
          await playDefaultTone(policy.kind)
          settings.volume = previousVolume
        }
      }
    } else {
      const contextAudio = getAudioContext()
      if (contextAudio) {
        const previousVolume = settings.volume
        settings.volume = Math.min(1, previousVolume * policy.volume / 0.42)
        await playDefaultTone(policy.kind)
        settings.volume = previousVolume
      }
    }
  }
  if (policy.haptic && settings.vibrationEnabled && typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    const pattern = settings.vibrationPatterns[policy.kind] || DEFAULT_PATTERNS[policy.kind] || DEFAULT_PATTERN
    navigator.vibrate(settings.reducedFeedback ? pattern.slice(0, 1) : pattern)
  }
  return true
}

/** Namespaced aliases keep call sites expressive while retaining one policy owner. */
export const sound = { play: playSound }
export const interaction = {
  feedback: (input: { event: SoundEvent; context?: SoundContext }) => playSound(input.event, input.context)
}

export async function playNotificationFeedback(kind: FeedbackKind) {
  const event: SoundEvent = kind === 'chat' || kind === 'group' ? 'message.received' :
    kind === 'follow' ? 'follow' : kind === 'like' ? 'like' : kind === 'collect' ? 'favorite' :
      kind === 'call' ? 'call.ringing' : 'notification.normal'
  return playSound(event, { cooldownMs: SOUND_POLICY[event].cooldownMs })
}

/**
 * 通话建立前的持续提示音。必须由通话生命周期显式停止，避免在接听、
 * 拒绝、超时或挂断后继续播放。
 */
export function startCallRingtone(intervalMs = 1800) {
  stopCallRingtone()
  const generation = ++callRingtoneGeneration
  const tick = () => {
    if (generation !== callRingtoneGeneration) return
    void playSound('call.ringing', { eventId: `ring-${generation}` })
  }
  tick()
  callRingtoneTimer = setInterval(tick, intervalMs)
}

export function stopCallRingtone() {
  callRingtoneGeneration++
  if (callRingtoneTimer) {
    clearInterval(callRingtoneTimer)
    callRingtoneTimer = null
  }
  if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    navigator.vibrate(0)
  }
}

export function stopAllSound() {
  stopCallRingtone()
  activeAudio.forEach((audio) => {
    audio.pause()
    audio.currentTime = 0
  })
  activeAudio.clear()
  if (audioContext?.state === 'running') void audioContext.suspend().catch(() => {})
}

export function installSoundLifecycle(): () => void {
  if (typeof window === 'undefined') return () => {}
  const onVisibility = () => {
    if (document.visibilityState !== 'visible') {
      // Long-lived ringing must never leak into a background route/tab.
      if (callRingtoneTimer) stopCallRingtone()
      activeAudio.forEach((audio) => audio.pause())
    }
  }
  const onPageHide = () => stopAllSound()
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('pagehide', onPageHide)
  return () => {
    document.removeEventListener('visibilitychange', onVisibility)
    window.removeEventListener('pagehide', onPageHide)
    stopAllSound()
  }
}

export function notificationKindFromType(type: unknown): FeedbackKind {
  switch (Number(type)) {
    case 1:
      return 'follow'
    case 2:
      return 'like'
    case 3:
      return 'comment'
    case 4:
      return 'collect'
    case 5:
      return 'mention'
    case 6:
    case 7:
      return 'friend'
    default:
      return 'comment'
  }
}
