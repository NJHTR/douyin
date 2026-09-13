<template>
  <main
    class="desktop-player"
    :class="{ 'panel-closed': !panelOpen, dimmed: controlsDimmed, 'theater-mode': theaterMode }"
    @mousemove="wakeControls"
    @click="wakeControls"
  >
    <section class="player-column">
      <div class="detail-topbar">
        <button class="back" type="button" aria-label="关闭视频" @click.stop="router.back">
          <Icon icon="solar:close-linear" />
        </button>
        <form class="detail-search" @submit.prevent="submitSearch">
          <Icon icon="solar:magnifer-linear" /><input
            v-model="search"
            placeholder="搜索你感兴趣的内容"
            aria-label="搜索"
          /><button type="submit">搜索</button>
        </form>
      </div>
      <div
        class="player-stage"
        :style="{ '--media-bg': `url(${cover})` }"
        @click="handleStageClick"
        @dblclick.stop="handleDoubleLike"
      >
        <video
          v-if="mediaKind === 'video' && videoUrl"
          ref="detailVideo"
          :src="videoUrl"
          :poster="cover"
          autoplay
          :muted="muted"
          playsinline
          controlslist="nodownload noplaybackrate noremoteplayback"
          disableremoteplayback
          @timeupdate="syncProgress"
        ></video
        ><img
          v-else-if="mediaKind === 'image' && imageUrls.length"
          class="detail-image"
          :src="_checkImgUrl(imageUrls[activeImageIndex])"
          :alt="title"
        />
        <div v-else-if="mediaKind === 'text'" class="detail-text">
          <p>{{ title }}</p>
        </div>
        <div v-else class="player-empty">正在加载内容...</div>
        <div v-if="mediaKind === 'image' && imageUrls.length > 1" class="detail-segments">
          <i
            v-for="(_, index) in imageUrls"
            :key="index"
            :class="{ filled: index < activeImageIndex }"
            ><b v-if="index === activeImageIndex" :style="{ width: `${imageProgress * 100}%` }"></b
          ></i>
        </div>
        <div class="player-overlay">
          <strong>@{{ author }}</strong
          ><span> · {{ title }}</span>
          <p>{{ description }}</p>
        </div>
        <div class="detail-action-rail">
          <button
            class="detail-author-button"
            type="button"
            aria-label="关注作者"
            @click.stop="bus.emit('DESKTOP_LOGIN')"
          >
            <img :src="authorAvatar" alt="作者头像" /><i>+</i>
          </button>
          <button
            type="button"
            aria-label="点赞"
            :class="{ active: isLiked }"
            @click.stop="toggleLike"
          >
            <Icon icon="solar:heart-bold" /><b>{{ likes }}</b>
          </button>
          <button type="button" aria-label="评论" @click.stop="panelOpen = true">
            <Icon icon="solar:chat-round-dots-bold" /><b>{{ comments.length }}</b>
          </button>
          <button type="button" aria-label="收藏" @click.stop="toggleCollectAction">
            <Icon icon="solar:star-bold" /><b>{{ collects }}</b>
          </button>
          <button type="button" aria-label="分享" @click.stop="shareVideo">
            <Icon icon="solar:share-bold" /><b>{{ shares }}</b>
          </button>
        </div>
        <NivoVideoControls
          v-if="mediaKind !== 'image' || imageUrls.length"
          class="detail-custom-controls"
          :media-type="mediaKind"
          :playing="isPlaying"
          :muted="muted"
          :current-time="mediaKind === 'image' ? imageProgress : detailCurrentTime"
          :duration="mediaKind === 'image' ? 1 : detailDuration"
          @toggle-play="toggleVideo"
          @toggle-mute="toggleMute"
          @seek="seekVideoRatio"
          @speed="setPlaybackSpeed"
          @picture-in-picture="togglePictureInPicture"
          @theater="theaterMode = !theaterMode"
          @fullscreen="toggleFullscreen"
          @danmaku="addDanmaku"
        />
        <div class="detail-danmaku">
          <span v-for="item in danmakuItems" :key="item.id">{{ item.content }}</span>
        </div>
      </div>
    </section>
    <aside v-if="panelOpen" class="comments-panel">
      <nav class="detail-tabs">
        <button
          v-for="tab in tabs"
          :key="tab"
          type="button"
          :class="{ active: activeTab === tab }"
          @click="activeTab = tab"
        >
          {{ tab }}</button
        ><button
          class="close-panel"
          type="button"
          aria-label="关闭评论面板"
          @click="panelOpen = false"
        >
          <Icon icon="solar:close-linear" />
        </button>
      </nav>
      <template v-if="activeTab === '评论'"
        ><p class="search-hint">大家都在搜：{{ title }}</p>
        <p class="comment-count">全部评论 ({{ comments.length }})</p>
        <div v-if="loading" class="empty">正在加载评论...</div>
        <div v-else-if="!comments.length" class="empty">还没有评论</div>
        <article v-for="comment in comments" :key="comment.id" class="comment">
          <img v-if="comment.avatar" :src="comment.avatar" alt="" />
          <div class="comment-avatar-placeholder" v-else></div>
          <div>
            <strong>{{ comment.author }}</strong>
            <p>{{ comment.text }}</p>
            <small>{{ comment.time }} · {{ comment.location }}</small>
            <div class="comment-actions">
              <button type="button" @click="replyToComment(comment)">回复</button>
              <button type="button" @click="shareComment(comment)">分享</button>
              <button type="button" @click="likeComment(comment)">
                <Icon icon="solar:heart-linear" />{{ comment.likes }}
              </button>
            </div>
          </div>
        </article>
        <form class="comment-composer" @submit.prevent="submitComment">
          <input
            v-model="commentDraft"
            :readonly="!store.isLoggedIn"
            placeholder="留下你的精彩评论吧"
            aria-label="评论内容"
            @click="ensureCommentLogin"
          />
          <button type="submit" :disabled="commentSending">
            {{ commentSending ? '发送中' : '发送' }}
          </button>
        </form></template
      >
      <div v-else class="tab-placeholder">{{ activeTab }}内容将在接口返回后展示</div>
    </aside>
  </main>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import {
  postComment,
  recordShare,
  recommendedVideo,
  toggleCollect,
  toggleCommentLike,
  toggleVideoLike,
  videoComments
} from '@/api/videos'
import { _checkImgUrl, _formatNumber } from '@/utils'
import { videoDurationSeconds } from '@/utils/recommendationTelemetry'
import { useBaseStore } from '@/store/pinia'
import NivoVideoControls from './NivoVideoControls.vue'
import bus from '@/utils/bus'
const route = useRoute()
const router = useRouter()
const store = useBaseStore()
const video = ref<any>(null)
const comments = ref<any[]>([])
const loading = ref(true)
const activeTab = ref('评论')
const panelOpen = ref(true)
const search = ref('')
const detailVideo = ref<HTMLVideoElement | null>(null)
const isPlaying = ref(true)
const muted = ref(true)
const progress = ref(0)
const elapsed = ref('00:00')
const controlsDimmed = ref(false)
const detailCurrentTime = ref(0)
const detailDuration = ref(0)
const theaterMode = ref(false)
const danmakuItems = ref<Array<{ id: number; content: string }>>([])
const isLiked = ref(false)
const likeCount = ref<number | null>(null)
const collectCount = ref<number | null>(null)
const shareCount = ref<number | null>(null)
const commentDraft = ref('')
const commentSending = ref(false)
let idleTimer: number | null = null
let imageTimer: number | null = null
let stageClickTimer: number | null = null
const tabs = ['详情', 'TA的作品', '评论', '合集', 'AI抖音', '相关推荐']
const title = computed(() => video.value?.desc || video.value?.title || '视频详情')
const author = computed(
  () => video.value?.author?.nickname || video.value?.author_name || '内容作者'
)
const authorAvatar = computed(() =>
  _checkImgUrl(
    video.value?.author?.avatar_168x168?.url_list?.[0] ||
      video.value?.author?.avatar ||
      video.value?.author_avatar ||
      ''
  )
)
const likes = computed(() =>
  _formatNumber(
    likeCount.value ?? video.value?.statistics?.digg_count ?? video.value?.digg_count ?? 0
  )
)
const collects = computed(() =>
  _formatNumber(collectCount.value ?? video.value?.statistics?.collect_count ?? 0)
)
const shares = computed(() =>
  _formatNumber(shareCount.value ?? video.value?.statistics?.share_count ?? 0)
)
const imageUrls = computed(() => {
  const raw = video.value?.image_urls
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
})
const mediaKind = computed(() =>
  video.value?.type === 'text'
    ? 'text'
    : video.value?.type === 'image' || imageUrls.value.length
      ? 'image'
      : 'video'
)
const activeImageIndex = ref(0)
const imageProgress = ref(0)
const cover = computed(() =>
  _checkImgUrl(
    video.value?.video?.cover?.url_list?.[0] || video.value?.cover_url || imageUrls.value[0] || ''
  )
)
const videoUrl = computed(() =>
  _checkImgUrl(video.value?.video?.play_addr?.url_list?.[0] || video.value?.video_url || '')
)
const description = computed(() => video.value?.desc || '发现更多精彩内容')
const durationLabel = computed(() => {
  const seconds = videoDurationSeconds(video.value, detailDuration.value)
  return seconds
    ? `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`
    : '00:00'
})
const timelineWidth = computed(
  () => `${(mediaKind.value === 'image' ? imageProgress.value : progress.value) * 100}%`
)

watch(
  [videoUrl, mediaKind],
  async ([url, kind]) => {
    if (!url || kind !== 'video') return
    await nextTick()
    const el = detailVideo.value
    if (!el) return
    el.muted = muted.value
    isPlaying.value = true
    try {
      await el.play()
    } catch {
      el.muted = true
      muted.value = true
      await el.play().catch(() => {})
    }
  },
  { flush: 'post' }
)
function mapComment(item: any) {
  return {
    id: item.id,
    author: item.user?.nickname || item.nickname || '用户',
    text: item.content || '',
    likes: _formatNumber(item.like_count || item.digg_count || 0),
    avatar: _checkImgUrl(item.user?.avatar || item.avatar || ''),
    time: item.create_time || '刚刚',
    location: item.ip_location || ''
  }
}
onMounted(async () => {
  const feed = await recommendedVideo({ start: 0, pageSize: 20, feedMode: 'EXPERIENCE' })
  const list = feed.success ? (Array.isArray(feed.data) ? feed.data : feed.data?.list || []) : []
  const id = String(route.query.id || '')
  video.value = list.find((item: any) => String(item.aweme_id ?? item.id) === id) || list[0] || null
  const videoId = video.value?.aweme_id ?? video.value?.id
  if (videoId) {
    const res = await videoComments({ id: videoId, pageNo: 1, pageSize: 30 })
    if (res.success)
      comments.value = (Array.isArray(res.data) ? res.data : res.data?.list || []).map(mapComment)
  }
  loading.value = false
  if (mediaKind.value === 'image' && imageUrls.value.length > 1)
    imageTimer = window.setInterval(() => {
      imageProgress.value += 0.02
      if (imageProgress.value >= 1) {
        imageProgress.value = 0
        activeImageIndex.value = (activeImageIndex.value + 1) % imageUrls.value.length
      }
    }, 100)
  wakeControls()
})
onUnmounted(() => {
  if (idleTimer) window.clearTimeout(idleTimer)
  if (imageTimer) window.clearInterval(imageTimer)
  if (stageClickTimer) window.clearTimeout(stageClickTimer)
})
watch(mediaKind, (kind) => {
  if (kind !== 'image' && imageTimer) {
    window.clearInterval(imageTimer)
    imageTimer = null
  }
})
function wakeControls() {
  controlsDimmed.value = false
  if (idleTimer) window.clearTimeout(idleTimer)
  idleTimer = window.setTimeout(() => (controlsDimmed.value = true), 3500)
}
function toggleVideo() {
  if (mediaKind.value === 'image') {
    isPlaying.value = !isPlaying.value
    if (imageTimer) window.clearInterval(imageTimer)
    imageTimer = isPlaying.value
      ? window.setInterval(() => {
          imageProgress.value += 0.02
          if (imageProgress.value >= 1) {
            imageProgress.value = 0
            activeImageIndex.value = (activeImageIndex.value + 1) % imageUrls.value.length
          }
        }, 100)
      : null
    wakeControls()
    return
  }
  if (!detailVideo.value) return
  if (detailVideo.value.paused) {
    void detailVideo.value.play()
    isPlaying.value = true
  } else {
    detailVideo.value.pause()
    isPlaying.value = false
  }
  wakeControls()
}
function addDanmaku(content: string) {
  const id = Date.now()
  danmakuItems.value.push({ id, content })
  window.setTimeout(() => {
    danmakuItems.value = danmakuItems.value.filter((item) => item.id !== id)
  }, 6000)
}
function toggleMute() {
  if (!detailVideo.value) return
  detailVideo.value.muted = !detailVideo.value.muted
  muted.value = detailVideo.value.muted
  wakeControls()
}
function handleStageClick(event: MouseEvent) {
  if ((event.target as Element | null)?.closest('.player-controls, .detail-action-rail')) return
  if (stageClickTimer) window.clearTimeout(stageClickTimer)
  stageClickTimer = window.setTimeout(() => {
    toggleVideo()
    stageClickTimer = null
  }, 220)
}
async function handleDoubleLike() {
  if (stageClickTimer) window.clearTimeout(stageClickTimer)
  stageClickTimer = null
  if (!store.isLoggedIn) {
    bus.emit('DESKTOP_LOGIN')
    return
  }
  await toggleLike()
}
async function toggleLike() {
  const id = video.value?.aweme_id ?? video.value?.id
  if (!id || !store.isLoggedIn) {
    if (!store.isLoggedIn) bus.emit('DESKTOP_LOGIN')
    return
  }
  const previous = isLiked.value
  const previousCount = likeCount.value
  isLiked.value = !previous
  likeCount.value = Math.max(
    0,
    Number(likeCount.value ?? video.value?.statistics?.digg_count ?? 0) + (isLiked.value ? 1 : -1)
  )
  try {
    const res = await toggleVideoLike(id)
    if (res?.success) {
      isLiked.value = Boolean(res.data?.isLoved ?? res.data?.liked ?? isLiked.value)
      likeCount.value = Number(res.data?.likeCount ?? res.data?.count ?? likeCount.value)
    }
  } catch {
    isLiked.value = previous
    likeCount.value = previousCount
  }
}
async function toggleCollectAction() {
  const id = video.value?.aweme_id ?? video.value?.id
  if (!id || !store.isLoggedIn) {
    bus.emit('DESKTOP_LOGIN')
    return
  }
  try {
    const res = await toggleCollect(id)
    if (res?.success && res.data?.count != null) collectCount.value = Number(res.data.count)
  } catch {
    // Keep the server-backed count unchanged when the action fails.
  }
}
async function shareVideo() {
  const id = video.value?.aweme_id ?? video.value?.id
  if (!id) return
  try {
    const res = await recordShare(id)
    if (res?.success && res.data?.count != null) shareCount.value = Number(res.data.count)
  } catch {
    // Sharing can still use the browser share affordance when available.
  }
  if (navigator.share) {
    void navigator
      .share({ title: title.value, text: description.value, url: location.href })
      .catch(() => {})
  }
}
function seekVideo(event: MouseEvent) {
  if (!detailVideo.value?.duration) return
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
  detailVideo.value.currentTime = ratio * detailVideo.value.duration
  progress.value = ratio
  wakeControls()
}
function seekVideoRatio(ratio: number) {
  if (!detailVideo.value?.duration) return
  detailVideo.value.currentTime = ratio * detailVideo.value.duration
  progress.value = ratio
  detailCurrentTime.value = detailVideo.value.currentTime
  wakeControls()
}
function setPlaybackSpeed(rate: number) {
  if (detailVideo.value) detailVideo.value.playbackRate = rate
}
async function togglePictureInPicture() {
  if (!detailVideo.value || !document.pictureInPictureEnabled) return
  if (document.pictureInPictureElement) await document.exitPictureInPicture()
  else await detailVideo.value.requestPictureInPicture?.()
}
function toggleFullscreen() {
  const target = document.querySelector('.player-stage') as HTMLElement | null
  if (!target) return
  if (document.fullscreenElement) void document.exitFullscreen()
  else void target.requestFullscreen?.()
  wakeControls()
}
function ensureCommentLogin() {
  if (!store.isLoggedIn) bus.emit('DESKTOP_LOGIN')
}
async function submitComment() {
  if (!store.isLoggedIn) {
    bus.emit('DESKTOP_LOGIN')
    return
  }
  const content = commentDraft.value.trim()
  const id = video.value?.aweme_id ?? video.value?.id
  if (!content || !id || commentSending.value) return
  commentSending.value = true
  try {
    const res = await postComment({ video_id: String(id), content })
    if (res?.success) {
      commentDraft.value = ''
      await loadComments()
    }
  } finally {
    commentSending.value = false
  }
}
function replyToComment(comment: any) {
  if (!store.isLoggedIn) {
    bus.emit('DESKTOP_LOGIN')
    return
  }
  commentDraft.value = `@${comment.author} `
  requestAnimationFrame(() =>
    (document.querySelector('.comment-composer input') as HTMLInputElement | null)?.focus()
  )
}
async function shareComment(comment: any) {
  const text = `${comment.author}：${comment.text}`
  if (navigator.share) await navigator.share({ text, url: location.href }).catch(() => {})
  else await navigator.clipboard?.writeText(text).catch(() => {})
}
async function likeComment(comment: any) {
  if (!store.isLoggedIn) {
    bus.emit('DESKTOP_LOGIN')
    return
  }
  if (!comment.id) return
  const res = await toggleCommentLike(String(comment.id))
  if (res?.success) {
    comment.likes = _formatNumber(res.data?.likeCount ?? res.data?.count ?? 0)
  }
}
async function loadComments() {
  const id = video.value?.aweme_id ?? video.value?.id
  if (!id) return
  loading.value = true
  const res = await videoComments({ id, pageNo: 1, pageSize: 30 })
  if (res?.success)
    comments.value = (Array.isArray(res.data) ? res.data : res.data?.list || []).map(mapComment)
  loading.value = false
}
function syncProgress(event: Event) {
  const media = event.target as HTMLVideoElement
  if (!media.duration) return
  progress.value = media.currentTime / media.duration
  detailCurrentTime.value = media.currentTime
  detailDuration.value = media.duration
  elapsed.value = `${String(Math.floor(media.currentTime / 60)).padStart(2, '0')}:${String(Math.floor(media.currentTime % 60)).padStart(2, '0')}`
}
function submitSearch() {
  if (search.value.trim()) router.push({ path: '/home/search', query: { q: search.value.trim() } })
}
</script>
<style scoped lang="less">
.desktop-player {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(390px, 28%);
  height: 100%;
  min-height: 0;
  background: #0d0e15;
  color: #f2f3f7;
}
.back {
  position: absolute;
  z-index: 2;
  top: 18px;
  left: 18px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  color: #fff;
  background: #0008;
  cursor: pointer;
}
.back svg {
  font-size: 20px;
}
.player-column {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 20px 22px 16px;
}
.player-stage {
  display: grid;
  place-items: center;
  height: calc(100% - 105px);
  min-height: 340px;
  overflow: hidden;
  border-radius: 10px;
  background: #050608;
}
.player-stage video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.player-empty,
.empty {
  color: #8e93a3;
}
.player-meta {
  padding: 16px 4px 0;
}
.player-meta strong {
  display: block;
  color: #fff;
  font-size: 15px;
}
.player-meta span {
  display: block;
  margin-top: 7px;
  font-size: 18px;
}
.player-meta p {
  margin: 7px 0 0;
  color: #a6aab7;
  font-size: 13px;
}
.comments-panel {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 20px 20px 78px;
  overflow: auto;
  border-left: 1px solid #282a35;
  background: #191a24;
}
.comments-panel header {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding-bottom: 17px;
  border-bottom: 1px solid #30323d;
}
.comments-panel header b {
  font-size: 18px;
}
.comments-panel header span {
  color: #989dab;
  font-size: 12px;
}
.search-hint {
  margin: 17px 0 4px;
  color: #ffd334;
  font-size: 12px;
}
.comment {
  display: flex;
  gap: 11px;
  padding: 16px 0;
  border-bottom: 1px solid #272934;
}
.comment img {
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
}
.comment strong {
  font-size: 13px;
}
.comment p {
  margin: 6px 0;
  color: #d8dae1;
  font-size: 13px;
  line-height: 1.45;
}
.comment small,
.comment-actions {
  color: #858b9a;
  font-size: 11px;
}
.comment-actions {
  margin-top: 9px;
}
.comment-actions button {
  margin-right: 12px;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
}
.comment-actions button:hover {
  color: #fff;
}
.comment-actions svg {
  margin-right: 3px;
  vertical-align: -2px;
}
.comment-login {
  position: absolute;
  right: 20px;
  bottom: 18px;
  left: 20px;
  height: 48px;
  border: 0;
  border-radius: 9px;
  color: #fff;
  background: #535562;
  cursor: pointer;
}
.detail-tabs {
  display: flex;
  align-items: center;
  gap: 22px;
  min-height: 44px;
  border-bottom: 1px solid #30323d;
  white-space: nowrap;
}
.detail-tabs button {
  position: relative;
  padding: 0 0 13px;
  border: 0;
  color: #b8bbc6;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
}
.detail-tabs button.active {
  color: #fff;
  font-weight: 700;
}
.detail-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  border-radius: 3px;
  background: #ff315b;
  content: '';
}
.detail-tabs .close-panel {
  margin-left: auto;
  padding: 0;
  color: #888d9c;
}
.detail-tabs .close-panel::after {
  display: none;
}
.detail-tabs .close-panel svg {
  font-size: 20px;
}
.comment-count {
  margin: 7px 0 0;
  color: #e9ebf1;
  font-size: 12px;
}
.tab-placeholder {
  display: grid;
  flex: 1;
  place-items: center;
  color: #858b9a;
  font-size: 13px;
}
.detail-action-rail {
  position: absolute;
  z-index: 4;
  right: 22px;
  bottom: 118px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  transition: opacity 0.35s ease;
}
.desktop-player.dimmed .detail-action-rail {
  opacity: 0.2;
}
.detail-action-rail button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  color: #fff;
  background: transparent;
  text-shadow: 0 1px 4px #000;
  cursor: pointer;
}
.detail-action-rail svg {
  font-size: 30px;
}
.detail-action-rail b {
  font-size: 12px;
  font-weight: 500;
}
.detail-author-button {
  position: relative;
  margin-bottom: 2px;
}
.detail-author-button img {
  width: 44px;
  height: 44px;
  border: 2px solid #fff;
  border-radius: 50%;
  object-fit: cover;
  background: #30323c;
}
.detail-author-button i {
  position: absolute;
  right: 50%;
  bottom: -5px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #fff;
  background: #ff315b;
  font-size: 14px;
  font-style: normal;
  transform: translateX(50%);
}
.detail-action-rail button.active {
  color: #ff4969;
}
</style>

<style scoped lang="less">
.player-stage::before {
  position: absolute;
  z-index: 0;
  inset: -24px;
  background-image: var(--media-bg);
  background-position: center;
  background-size: cover;
  filter: blur(24px);
  opacity: 0.62;
  transform: scale(1.08);
  content: '';
}
.player-stage video,
.player-stage .detail-image,
.player-stage .detail-text,
.player-stage .player-empty {
  position: relative;
  z-index: 1;
}
.player-stage video,
.player-stage .detail-image {
  object-fit: contain;
}
</style>
<style scoped lang="less">
.desktop-player {
  position: fixed;
  z-index: 50;
  inset: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(430px, 28%);
  background: #050608;
  transition: grid-template-columns 0.25s ease;
}
.desktop-player.panel-closed {
  grid-template-columns: 1fr;
}
.player-column {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  padding: 0;
}
.player-stage {
  position: relative;
  flex: 1 1 auto;
  width: 100%;
  height: 100%;
  min-height: 0;
  max-height: 100%;
  border-radius: 0;
}
.player-stage video {
  display: block;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  background: #050608;
}
.detail-topbar {
  position: absolute;
  z-index: 4;
  top: 18px;
  left: 22px;
  display: flex;
  align-items: center;
  gap: 14px;
  opacity: 0.82;
  transition: opacity 0.35s ease;
}
.desktop-player.dimmed .detail-topbar,
.desktop-player.dimmed .detail-custom-controls {
  opacity: 0;
  pointer-events: none;
}
.detail-image {
  display: block;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  background: #050608;
}
.detail-text {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  padding: 10%;
  box-sizing: border-box;
  background: #252733;
}
.detail-text p {
  max-width: 760px;
  color: #fff;
  font-size: 38px;
  line-height: 1.45;
  text-align: center;
}
.detail-segments {
  position: absolute;
  z-index: 3;
  right: 24px;
  bottom: 60px;
  left: 24px;
  display: flex;
  gap: 4px;
  height: 4px;
}
.detail-segments i {
  position: relative;
  flex: 1;
  overflow: hidden;
  border-radius: 4px;
  background: #fff6;
}
.detail-segments i.filled {
  background: #fff;
}
.detail-segments i b {
  position: absolute;
  inset: 0 auto 0 0;
  background: #ff315b;
}
.detail-segments {
  display: none;
}
.detail-danmaku {
  position: absolute;
  z-index: 5;
  top: 28%;
  right: 10%;
  left: 10%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  text-shadow: 0 2px 5px #000;
  pointer-events: none;
}
.detail-danmaku span {
  animation: detail-danmaku 6s linear forwards;
  white-space: nowrap;
}
@keyframes detail-danmaku {
  from {
    transform: translateX(90%);
  }
  to {
    transform: translateX(-110%);
  }
}
.back {
  position: static;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: #10111699;
}
.back svg {
  font-size: 22px;
}
.detail-search {
  display: flex;
  align-items: center;
  width: min(360px, 30vw);
  height: 42px;
  padding: 0 12px;
  gap: 8px;
  border: 1px solid #fff7;
  border-radius: 10px;
  color: #fff;
  background: #12141a88;
  backdrop-filter: blur(12px);
}
.detail-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  color: #fff;
  background: transparent;
}
.detail-search input::placeholder {
  color: #fff9;
}
.detail-search button {
  border: 0;
  color: #fff;
  background: transparent;
  cursor: pointer;
}
.player-overlay {
  position: absolute;
  z-index: 3;
  right: 100px;
  bottom: 88px;
  left: 25px;
  color: #fff;
  text-shadow: 0 2px 5px #000;
  transition: opacity 0.35s ease;
}
.desktop-player.dimmed .player-overlay {
  opacity: 0.2;
}
.player-overlay strong {
  font-size: 16px;
}
.player-overlay span {
  color: #ddd;
  font-size: 13px;
}
.player-overlay p {
  margin: 8px 0 0;
  color: #eee;
  font-size: 15px;
}
.player-controls {
  position: absolute;
  z-index: 4;
  right: 20px;
  bottom: 17px;
  left: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  color: #fff;
  background: #11131acc;
  backdrop-filter: blur(10px);
  transition: opacity 0.35s ease;
}
.player-controls button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  color: #fff;
  background: transparent;
  cursor: pointer;
}
.player-controls span {
  font-size: 12px;
  white-space: nowrap;
}
.timeline {
  flex: 1;
  position: relative;
  height: 3px;
  min-width: 80px;
  border-radius: 3px;
  background: #fff5;
  cursor: pointer;
}
.timeline i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: #fff;
}
.comments-panel {
  position: relative;
  z-index: 6;
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 0 20px 78px;
  overflow: auto;
  border-left: 1px solid #ffffff16;
  background: #17181ecc;
  backdrop-filter: blur(18px);
}
.detail-tabs {
  min-height: 66px;
  padding-top: 24px;
}
.detail-tabs .close-panel {
  margin-left: auto;
}
.comment-avatar-placeholder {
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #30323c;
}
.comment img {
  background: #30323c;
}
.comment-login {
  background: #343641;
}
.comment-composer {
  position: absolute;
  z-index: 3;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  gap: 8px;
  margin: 0;
  padding: 12px 20px;
  border-top: 1px solid #ffffff16;
  background: #17181ef5;
  backdrop-filter: blur(14px);
}
.comment-composer input {
  flex: 1;
  min-width: 0;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #3a3c48;
  border-radius: 9px;
  outline: 0;
  color: #f5f6f8;
  background: #242631;
}
.comment-composer input:focus {
  border-color: #646879;
}
.comment-composer input::placeholder {
  color: #8f94a4;
}
.comment-composer button {
  width: 58px;
  border: 0;
  border-radius: 9px;
  color: #fff;
  background: #ff315b;
  cursor: pointer;
}
.comment-composer button:disabled {
  cursor: wait;
  opacity: 0.6;
}
@media (max-width: 900px) {
  .desktop-player {
    grid-template-columns: 1fr;
  }
  .comments-panel {
    position: absolute;
    inset: 0 0 0 auto;
    width: min(92vw, 430px);
  }
  .detail-search {
    width: min(300px, 55vw);
  }
}
</style>
