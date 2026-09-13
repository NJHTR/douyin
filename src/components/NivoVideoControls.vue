<template>
  <div class="nivo-video-controls" @click.stop @dblclick.stop>
    <div
      v-if="mediaType !== 'text'"
      class="control-progress"
      role="slider"
      tabindex="0"
      aria-label="视频进度"
      :aria-valuenow="Math.round(progress * 100)"
      @pointerdown="startSeek"
      @pointermove="moveSeek"
      @pointerup="endSeek"
      @pointercancel="endSeek"
    >
      <i :style="{ width: `${progress * 100}%` }"><b></b></i>
    </div>
    <div class="control-row">
      <button
        v-if="mediaType !== 'text'"
        type="button"
        :aria-label="playing ? '暂停' : '播放'"
        @click="$emit('toggle-play')"
      >
        <Icon :icon="playing ? 'solar:pause-bold' : 'solar:play-bold'" />
      </button>
      <span v-if="mediaType === 'video'" class="control-time"
        >{{ formatTime(currentTime) }}/{{ formatTime(duration) }}</span
      >
      <button type="button" class="danmaku-mode" aria-label="弹幕设置">弹</button>
      <button type="button" class="danmaku-mode" aria-label="弹幕显示">弹</button>
      <form class="danmaku-input" @submit.prevent="sendDanmaku">
        <Icon icon="solar:emoji-funny-square-linear" />
        <input v-model="danmaku" placeholder="发一条友好的弹幕吧" aria-label="弹幕内容" />
        <button type="submit" :disabled="!danmaku.trim()">发送</button>
      </form>
      <label class="control-toggle">
        <input v-model="autoplay" type="checkbox" /><i></i><span>连播</span>
      </label>
      <label class="control-toggle">
        <input v-model="clearScreen" type="checkbox" /><i></i><span>清屏</span>
      </label>
      <button
        type="button"
        class="text-tool"
        :class="{ active: smartMode }"
        @click="smartMode = !smartMode"
      >
        智能
      </button>
      <button
        v-if="mediaType === 'video'"
        type="button"
        class="speed-tool"
        aria-label="切换倍速"
        @click="cycleSpeed"
      >
        {{ playbackRate.toFixed(1) }}x
      </button>
      <button type="button" aria-label="播放列表">
        <Icon icon="solar:playlist-minimalistic-2-linear" />
      </button>
      <button
        v-if="mediaType === 'video'"
        type="button"
        aria-label="画中画"
        @click="$emit('picture-in-picture')"
      >
        <Icon icon="solar:window-frame-linear" />
      </button>
      <button
        v-if="mediaType === 'video'"
        type="button"
        :aria-label="muted ? '取消静音' : '静音'"
        @click="$emit('toggle-mute')"
      >
        <Icon :icon="muted ? 'solar:volume-cross-linear' : 'solar:volume-loud-linear'" />
      </button>
      <button type="button" aria-label="剧场模式" @click="$emit('theater')">
        <Icon icon="solar:monitor-linear" />
      </button>
      <button type="button" aria-label="全屏" @click="$emit('fullscreen')">
        <Icon icon="solar:full-screen-linear" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'

const props = withDefaults(
  defineProps<{
    playing: boolean
    muted: boolean
    currentTime: number
    duration: number
    rate?: number
    mediaType?: 'video' | 'image' | 'text'
  }>(),
  { rate: 1, mediaType: 'video' }
)
const emit = defineEmits<{
  'toggle-play': []
  'toggle-mute': []
  seek: [ratio: number]
  speed: [rate: number]
  'picture-in-picture': []
  theater: []
  fullscreen: []
  danmaku: [content: string]
}>()

const danmaku = ref('')
const autoplay = ref(true)
const clearScreen = ref(false)
const smartMode = ref(false)
const playbackRate = ref(props.rate)
const seeking = ref(false)
const progress = computed(() =>
  props.duration > 0 ? Math.min(1, Math.max(0, props.currentTime / props.duration)) : 0
)

function formatTime(value: number) {
  const seconds = Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}
function emitSeek(event: PointerEvent) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  emit('seek', Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width)))
}
function startSeek(event: PointerEvent) {
  seeking.value = true
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  emitSeek(event)
}
function moveSeek(event: PointerEvent) {
  if (seeking.value) emitSeek(event)
}
function endSeek(event: PointerEvent) {
  if (!seeking.value) return
  emitSeek(event)
  seeking.value = false
}
function cycleSpeed() {
  const rates = [0.75, 1, 1.25, 1.5, 2]
  playbackRate.value = rates[(rates.indexOf(playbackRate.value) + 1) % rates.length]
  emit('speed', playbackRate.value)
}
function sendDanmaku() {
  const content = danmaku.value.trim()
  if (!content) return
  emit('danmaku', content)
  danmaku.value = ''
}
</script>

<style scoped lang="less">
.nivo-video-controls {
  position: absolute;
  z-index: 6;
  right: 12px;
  bottom: 10px;
  left: 12px;
  color: #fff;
  background: #090a0ce8;
  border-radius: 7px;
  box-shadow: 0 8px 24px #0005;
  backdrop-filter: blur(10px);
}
.control-progress {
  position: absolute;
  right: 0;
  bottom: 100%;
  left: 0;
  height: 4px;
  background: #ffffff45;
  cursor: pointer;
}
.control-progress::before {
  position: absolute;
  inset: -7px 0;
  content: '';
}
.control-progress i {
  position: relative;
  display: block;
  height: 100%;
  background: #ff315b;
}
.control-progress b {
  position: absolute;
  top: 50%;
  right: -5px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 2px #ff315b;
  opacity: 0;
  transform: translateY(-50%);
}
.control-progress:hover b {
  opacity: 1;
}
.control-row {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 0 10px;
}
.control-row > button {
  display: grid;
  flex: 0 0 28px;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  color: #f5f5f7;
  background: transparent;
  font-size: 13px;
  cursor: pointer;
}
.control-row > button:hover,
.control-row > button.active {
  color: #ff4969;
}
.control-row svg {
  font-size: 21px;
}
.control-time {
  flex: 0 0 auto;
  font-size: 12px;
  white-space: nowrap;
}
.danmaku-mode {
  font-weight: 800;
}
.danmaku-input {
  display: flex;
  flex: 1 1 260px;
  align-items: center;
  min-width: 120px;
  height: 34px;
  padding-left: 10px;
  overflow: hidden;
  border-radius: 7px;
  background: #2a2b30;
}
.danmaku-input svg {
  color: #cfd1d8;
  font-size: 19px;
}
.danmaku-input input {
  flex: 1;
  min-width: 0;
  padding: 0 8px;
  border: 0;
  outline: 0;
  color: #fff;
  background: transparent;
}
.danmaku-input input::placeholder {
  color: #9b9da6;
}
.danmaku-input button {
  align-self: center;
  min-width: 58px;
  height: 28px;
  margin: 0 3px 0 0;
  border-radius: 6px;
  border: 0;
  border-radius: 7px;
  color: #fff;
  background: #4a4b50;
  cursor: pointer;
}
.danmaku-input button:disabled {
  color: #989aa2;
  cursor: default;
}
.control-toggle {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  cursor: pointer;
}
.control-toggle input {
  position: absolute;
  opacity: 0;
}
.control-toggle i {
  position: relative;
  width: 28px;
  height: 16px;
  border-radius: 9px;
  background: #55575e;
}
.control-toggle i::after {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #fff;
  content: '';
  transition: transform 0.18s ease;
}
.control-toggle input:checked + i {
  background: #ff315b;
}
.control-toggle input:checked + i::after {
  transform: translateX(12px);
}
.text-tool,
.speed-tool {
  width: auto !important;
  flex-basis: auto !important;
  white-space: nowrap;
}
@media (max-width: 1180px) {
  .danmaku-input {
    flex-basis: 140px;
  }
  .control-toggle,
  .text-tool,
  .control-row > button:nth-last-child(4) {
    display: none;
  }
}
</style>
