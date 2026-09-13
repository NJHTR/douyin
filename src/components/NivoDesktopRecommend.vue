<template>
  <main class="recommend-shell" :class="{ 'theater-mode': recommendTheater }" @wheel="handleWheel">
    <div class="recommend-page" :class="{ 'panel-collapsed': !panelOpen }">
      <section
        :key="transitionKey"
        class="recommend-stage"
        :class="stageMotion"
        :style="{ '--media-bg': `url(${cover})` }"
        @click="handleStageClick"
        @dblclick.stop="handleDoubleLike"
      >
        <video
          v-if="mediaKind === 'video' && videoUrl"
          ref="recommendVideo"
          :src="videoUrl"
          :poster="cover"
          autoplay
          :muted="recommendMuted"
          loop
          playsinline
          controlslist="nodownload noplaybackrate noremoteplayback"
          disableremoteplayback
          @timeupdate="syncVideoProgress"
          @loadedmetadata="syncVideoProgress"
          @play="handleRecommendationPlay"
          @pause="handleRecommendationPause"
          @ended="handleRecommendationEnded"
        ></video
        ><img
          v-else-if="mediaKind === 'image' && imageUrls.length"
          class="stage-image"
          :src="_checkImgUrl(imageUrls[activeImageIndex])"
          :alt="title"
        />
        <div v-else-if="mediaKind === 'text'" class="stage-text">
          <p>{{ title }}</p>
        </div>
        <div v-else class="stage-empty">正在加载推荐内容...</div>
        <div class="stage-shade"></div>
        <div class="danmaku">
          <span v-for="item in danmakuItems" :key="item.id">{{ item.content }}</span>
        </div>
        <div class="recommend-copy">
          <strong>@{{ author }}</strong
          ><span> · {{ date }}</span>
          <p>{{ title }}</p>
          <small>{{ description }}</small>
        </div>
        <div class="recommend-actions">
          <button
            class="recommend-author-button"
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
            @click.stop="handleDoubleLike"
          >
            <Icon icon="solar:heart-bold" /><b>{{ likes }}</b></button
          ><button type="button" aria-label="评论" @click="openPanel('评论')">
            <Icon icon="solar:chat-round-dots-bold" /><b>{{ comments.length }}</b></button
          ><button type="button" aria-label="收藏">
            <Icon icon="solar:star-bold" /><b>{{ collects }}</b></button
          ><button type="button" aria-label="分享">
            <Icon icon="solar:share-bold" /><b>{{ shares }}</b></button
          ><button type="button" aria-label="听抖音音">
            <Icon icon="solar:headphones-round-bold" /><b>听声音</b>
          </button>
        </div>
        <div v-if="mediaKind === 'image' && imageUrls.length > 1" class="image-progress">
          <i
            v-for="(_, index) in imageUrls"
            :key="index"
            :class="{ filled: index < activeImageIndex, active: index === activeImageIndex }"
            ><b
              :style="index === activeImageIndex ? { width: `${imageProgress * 100}%` } : undefined"
            ></b
          ></i>
        </div>
        <NivoVideoControls
          v-if="mediaKind !== 'image' || imageUrls.length"
          :media-type="mediaKind"
          :playing="recommendPlaying"
          :muted="recommendMuted"
          :current-time="mediaKind === 'image' ? imageProgress : recommendCurrentTime"
          :duration="mediaKind === 'image' ? 1 : recommendDuration"
          @toggle-play="toggleRecommendPlayback"
          @toggle-mute="toggleRecommendMute"
          @seek="seekRecommend"
          @speed="setRecommendSpeed"
          @picture-in-picture="toggleRecommendPictureInPicture"
          @theater="recommendTheater = !recommendTheater"
          @fullscreen="toggleRecommendFullscreen"
          @danmaku="addDanmaku"
        />
      </section>
      <aside v-if="panelOpen" class="recommend-panel">
        <nav class="panel-tabs">
          <button
            v-for="tab in tabs"
            :key="tab"
            type="button"
            :class="{ active: activePanel === tab }"
            @click="selectPanel(tab)"
          >
            {{ tab }}</button
          ><button
            class="panel-close"
            type="button"
            aria-label="关闭右侧面板"
            title="关闭右侧面板"
            @click="panelOpen = false"
          >
            <Icon icon="solar:close-circle-linear" />
          </button>
        </nav>
        <template v-if="activePanel === '评论'"
          ><p class="search-hint">大家都在搜：{{ title }}</p>
          <p class="comment-count">全部评论 ({{ comments.length }})</p>
          <div v-if="loading" class="empty">正在加载评论...</div>
          <div v-else-if="!comments.length" class="empty">还没有评论</div>
          <article v-for="item in comments" :key="item.id" class="comment">
            <img :src="item.avatar" alt="" />
            <div>
              <strong>{{ item.author }}</strong>
              <p>{{ item.text }}</p>
              <small>{{ item.time }} · {{ item.location }}</small>
              <div class="comment-actions">
                <button type="button" @click="replyToComment(item)">回复</button>
                <button type="button" @click="shareComment(item)">分享</button>
                <button type="button" @click="likeComment(item)">
                  <Icon icon="solar:heart-linear" />{{ item.likes }}
                </button>
              </div>
            </div>
          </article>
          <form class="panel-composer" @submit.prevent="submitComment">
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
        ><template v-else-if="activePanel === '详情'"
          ><div class="panel-content detail-content">
            <div class="detail-author">
              <img :src="authorAvatar" alt="" /><strong>@{{ author }}</strong
              ><button type="button" @click="bus.emit('DESKTOP_LOGIN')">关注</button>
            </div>
            <h2>{{ title }}</h2>
            <p class="detail-desc">{{ description }}</p>
            <div class="detail-stats">
              <span>获赞 {{ likes }}</span
              ><span>收藏 {{ collects }}</span
              ><span>分享 {{ shares }}</span>
            </div>
          </div></template
        ><template v-else-if="activePanel === 'TA的作品'"
          ><div class="panel-content">
            <p class="section-title">{{ author }}的作品</p>
            <div v-if="panelLoading" class="empty">正在加载作品...</div>
            <div v-else-if="!works.length" class="empty">暂无更多作品</div>
            <div v-else class="works-grid">
              <article v-for="item in works" :key="item.id">
                <img :src="item.cover" alt="" /><span>{{ item.title }}</span>
              </article>
            </div>
          </div></template
        ><template v-else-if="activePanel === 'AI抖音'"
          ><div class="panel-content ai-content">
            <p class="section-title">AI 总结</p>
            <div v-if="panelLoading" class="empty">正在生成总结...</div>
            <p v-else class="ai-summary">
              {{ aiSummary || '暂时无法生成该视频的总结，换个视频试试吧。' }}
            </p>
          </div></template
        ><template v-else
          ><div class="panel-content">
            <div class="section-title">
              相关推荐 <button type="button" @click="loadRelated">换一换</button>
            </div>
            <article v-for="item in related" :key="item.id" class="related-item">
              <img :src="item.cover" alt="" />
              <div>
                <strong>{{ item.title }}</strong
                ><small>@{{ item.author }} · {{ item.likes }}</small>
              </div>
            </article>
            <div v-if="!related.length" class="empty">暂无相关推荐</div>
          </div></template
        >
      </aside>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Icon } from '@iconify/vue'
import {
  postComment,
  recommendedVideo,
  searchSummary,
  toggleCommentLike,
  toggleVideoLike,
  videoComments
} from '@/api/videos'
import { userVideoList } from '@/api/user'
import { _checkImgUrl, _formatNumber } from '@/utils'
import { getBrowsingSessionId } from '@/utils/session'
import { getRecommendationWatchBatcher } from '@/utils/recommendationTelemetryClient'
import {
  LoopCompletionTracker,
  videoDurationSeconds,
  watchFlushPolicy,
  type WatchFlushReason
} from '@/utils/recommendationTelemetry'
import { useBaseStore } from '@/store/pinia'
import NivoVideoControls from './NivoVideoControls.vue'
import bus from '@/utils/bus'
const store = useBaseStore()
const watchBatcher = getRecommendationWatchBatcher()
const panelOpen = ref(false)
const activePanel = ref('评论')
const video = ref<any>(null)
const feed = ref<any[]>([])
const currentIndex = ref(0)
const transitionKey = ref(0)
const stageMotion = ref('')
const comments = ref<any[]>([])
const works = ref<any[]>([])
const related = ref<any[]>([])
const aiSummary = ref('')
const loading = ref(true)
const panelLoading = ref(false)
const commentDraft = ref('')
const commentSending = ref(false)
const recommendVideo = ref<HTMLVideoElement | null>(null)
const recommendPlaying = ref(true)
const recommendMuted = ref(true)
const recommendCurrentTime = ref(0)
const recommendDuration = ref(0)
const recommendTheater = ref(false)
const isLiked = ref(false)
const likeCount = ref<number | null>(null)
const danmakuItems = ref<Array<{ id: number; content: string }>>([])
let stageClickTimer: number | null = null
const activeImageIndex = ref(0)
const imageProgress = ref(0)
let imageTimer: number | null = null
let wheelLock = false
let watchTimer: ReturnType<typeof setInterval> | null = null
let watchSeconds = 0
let lastHeartbeatSeconds = -1
let lastProfileSampleSeconds = -1
let lastFinishedSeconds = -1
const recommendationCompletion = new LoopCompletionTracker()
const tabs = ['详情', 'TA的作品', '评论', 'AI抖音', '相关推荐']
const title = computed(() => video.value?.desc || video.value?.title || '推荐内容')
const author = computed(
  () => video.value?.author?.nickname || video.value?.author_name || '内容作者'
)
const authorId = computed(
  () =>
    video.value?.author?.uid || video.value?.author?.id || video.value?.uid || video.value?.user_id
)
const authorAvatar = computed(
  () =>
    _checkImgUrl(video.value?.author?.avatar || video.value?.author_avatar || '') ||
    '/gznxl-gu0vc-001.ico'
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
const cover = computed(() =>
  _checkImgUrl(
    video.value?.video?.cover?.url_list?.[0] || video.value?.cover_url || imageUrls.value[0] || ''
  )
)
const videoUrl = computed(() =>
  _checkImgUrl(video.value?.video?.play_addr?.url_list?.[0] || video.value?.video_url || '')
)
const description = computed(() => video.value?.desc || '来自推荐内容流')

// autoplay 在 Edge 中可能因元素刚替换或策略判断而不触发；显式播放，
// 并在带声音播放被拒绝时回退到静音，避免每条视频都要手动点一次。
watch(
  [videoUrl, mediaKind],
  async ([url, kind]) => {
    if (!url || kind !== 'video') return
    await nextTick()
    const el = recommendVideo.value
    if (!el) return
    el.muted = recommendMuted.value
    recommendPlaying.value = true
    try {
      await el.play()
    } catch {
      el.muted = true
      recommendMuted.value = true
      await el.play().catch(() => {})
    }
  },
  { flush: 'post' }
)

function flushRecommendationWatch(
  reason: WatchFlushReason = 'heartbeat',
  item: any = video.value,
  includePosition = true
) {
  const id = item?.aweme_id ?? item?.id
  if (!id || !watchSeconds || !store.isLoggedIn) return
  const { finished, profileSample } = watchFlushPolicy(reason)
  if (!profileSample && watchSeconds === lastHeartbeatSeconds) return
  if (profileSample && !finished && watchSeconds === lastProfileSampleSeconds) return
  if (finished && watchSeconds === lastFinishedSeconds) return
  // The API upsert is monotonic (GREATEST), so every heartbeat carries the
  // cumulative session progress; reset only when the video changes.
  watchBatcher.enqueue(String(id), {
    watch_duration: watchSeconds,
    video_duration: videoDurationSeconds(item, recommendDuration.value),
    finished,
    session_id: getBrowsingSessionId(),
    swipe_seconds: watchSeconds,
    traffic_source: 'HOME_RECOMMEND',
    last_position: includePosition ? recommendCurrentTime.value : 0,
    profile_sample: profileSample
  })
  lastHeartbeatSeconds = watchSeconds
  if (profileSample) lastProfileSampleSeconds = watchSeconds
  if (finished) lastFinishedSeconds = watchSeconds
}

function startRecommendationWatch() {
  if (watchTimer !== null) return
  watchTimer = setInterval(() => {
    if (recommendPlaying.value && video.value) {
      watchSeconds += 1
      if (watchSeconds % 15 === 0) flushRecommendationWatch('heartbeat')
    }
  }, 1000)
}

function stopRecommendationWatch(reason: WatchFlushReason = 'unmount') {
  if (watchTimer !== null) clearInterval(watchTimer)
  watchTimer = null
  flushRecommendationWatch(reason)
}
function formatDate(value: unknown) {
  if (value == null || value === '') return '刚刚'
  const raw = String(value).trim()
  const numeric = Number(raw)
  const date = Number.isFinite(numeric)
    ? new Date(numeric < 1e12 ? numeric * 1000 : numeric)
    : new Date(raw)
  return Number.isNaN(date.getTime()) ? '刚刚' : date.toLocaleDateString('zh-CN')
}
const date = computed(() => formatDate(video.value?.create_time))
const likes = computed(() =>
  _formatNumber(likeCount.value ?? video.value?.statistics?.digg_count ?? 0)
)
const collects = computed(() => _formatNumber(video.value?.statistics?.collect_count || 0))
const shares = computed(() => _formatNumber(video.value?.statistics?.share_count || 0))
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
function unwrap(res: any) {
  return res?.success ? (Array.isArray(res.data) ? res.data : res.data?.list || []) : []
}
function mapWork(item: any) {
  return {
    id: item.aweme_id || item.id,
    title: item.desc || item.title || '未命名作品',
    cover: _checkImgUrl(item.video?.cover?.url_list?.[0] || item.cover_url || '')
  }
}
function mapRelated(item: any) {
  return {
    id: item.aweme_id || item.id,
    title: item.desc || item.title || '推荐视频',
    author: item.author?.nickname || item.author_name || '内容作者',
    cover: _checkImgUrl(item.video?.cover?.url_list?.[0] || item.cover_url || ''),
    likes: _formatNumber(item.statistics?.digg_count || 0)
  }
}
function openPanel(tab: string) {
  panelOpen.value = true
  selectPanel(tab)
}
function handleStageClick(event: MouseEvent) {
  if (
    (event.target as Element | null)?.closest(
      '.nivo-video-controls, .recommend-actions, .recommend-panel'
    )
  )
    return
  if (stageClickTimer) window.clearTimeout(stageClickTimer)
  stageClickTimer = window.setTimeout(() => {
    toggleRecommendPlayback()
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
  const id = video.value?.aweme_id ?? video.value?.id
  if (!id) return
  isLiked.value = true
  likeCount.value = Number(likeCount.value ?? video.value?.statistics?.digg_count ?? 0) + 1
  const res = await toggleVideoLike(id)
  if (res?.success) {
    isLiked.value = Boolean(res.data?.isLoved ?? res.data?.liked ?? true)
    likeCount.value = Number(res.data?.likeCount ?? likeCount.value)
  }
}
function addDanmaku(content: string) {
  const id = Date.now()
  danmakuItems.value.push({ id, content })
  window.setTimeout(() => {
    danmakuItems.value = danmakuItems.value.filter((item) => item.id !== id)
  }, 6000)
}
function syncVideoProgress(event: Event) {
  const media = event.target as HTMLVideoElement
  recommendCurrentTime.value = media.currentTime || 0
  recommendDuration.value = Number.isFinite(media.duration) ? media.duration : 0
  recommendMuted.value = media.muted
  if (media.duration > 0 && recommendationCompletion.observe(media.currentTime, media.duration)) {
    flushRecommendationWatch('ended')
  }
}
function handleRecommendationPlay() {
  recommendPlaying.value = true
}
function handleRecommendationPause() {
  recommendPlaying.value = false
  flushRecommendationWatch('pause')
}
function handleRecommendationEnded() {
  if (recommendationCompletion.complete()) flushRecommendationWatch('ended')
  // Preserve the looping feed experience on browsers that still emit
  // `ended` despite the native loop attribute.
  recommendPlaying.value = true
  if (recommendVideo.value) {
    recommendVideo.value.currentTime = 0
    void recommendVideo.value.play().catch(() => {})
  }
}
function toggleRecommendPlayback() {
  if (mediaKind.value !== 'video') {
    if (recommendPlaying.value) handleRecommendationPause()
    else handleRecommendationPlay()
    return
  }
  if (!recommendVideo.value) return
  if (recommendVideo.value.paused) void recommendVideo.value.play().catch(() => {})
  else recommendVideo.value.pause()
}
function replyToComment(item: any) {
  if (!store.isLoggedIn) return bus.emit('DESKTOP_LOGIN')
  commentDraft.value = `@${item.author} `
  requestAnimationFrame(() =>
    (document.querySelector('.panel-composer input') as HTMLInputElement | null)?.focus()
  )
}
async function shareComment(item: any) {
  const text = `${item.author}：${item.text}`
  if (navigator.share) await navigator.share({ text, url: location.href }).catch(() => {})
  else await navigator.clipboard?.writeText(text).catch(() => {})
}
async function likeComment(item: any) {
  if (!store.isLoggedIn) return bus.emit('DESKTOP_LOGIN')
  if (!item.id) return
  const res = await toggleCommentLike(String(item.id))
  if (res?.success) item.likes = _formatNumber(res.data?.likeCount ?? res.data?.count ?? 0)
}
function toggleRecommendMute() {
  if (!recommendVideo.value) return
  recommendVideo.value.muted = !recommendVideo.value.muted
  recommendMuted.value = recommendVideo.value.muted
}
function seekRecommend(ratio: number) {
  if (!recommendVideo.value?.duration) return
  recommendationCompletion.markSeek()
  recommendVideo.value.currentTime = ratio * recommendVideo.value.duration
}
function setRecommendSpeed(rate: number) {
  if (recommendVideo.value) recommendVideo.value.playbackRate = rate
}
async function toggleRecommendPictureInPicture() {
  if (!recommendVideo.value || !document.pictureInPictureEnabled) return
  if (document.pictureInPictureElement) await document.exitPictureInPicture()
  else await recommendVideo.value.requestPictureInPicture?.()
}
function toggleRecommendFullscreen() {
  const target = (recommendVideo.value?.closest('.recommend-stage') ||
    document.querySelector('.recommend-stage')) as HTMLElement | null
  if (!target) return
  if (document.fullscreenElement) void document.exitFullscreen()
  else void target.requestFullscreen?.()
}
function handleWheel(event: WheelEvent) {
  if (event.target instanceof Element && event.target.closest('.recommend-panel')) return
  if (wheelLock || Math.abs(event.deltaY) < 12 || feed.value.length < 2) return
  event.preventDefault()
  wheelLock = true
  stageMotion.value = event.deltaY > 0 ? 'slide-up' : 'slide-down'
  currentIndex.value =
    (currentIndex.value + (event.deltaY > 0 ? 1 : -1) + feed.value.length) % feed.value.length
  video.value = feed.value[currentIndex.value]
  isLiked.value = false
  likeCount.value = null
  danmakuItems.value = []
  comments.value = []
  activeImageIndex.value = 0
  imageProgress.value = 0
  transitionKey.value += 1
  loadCommentsForCurrent()
  window.setTimeout(() => {
    wheelLock = false
  }, 560)
}
async function loadCommentsForCurrent() {
  const videoId = video.value?.aweme_id ?? video.value?.id
  if (!videoId) return
  loading.value = true
  const res = await videoComments({ id: videoId, pageNo: 1, pageSize: 30 })
  comments.value = unwrap(res).map(mapComment)
  loading.value = false
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
      await loadCommentsForCurrent()
    }
  } finally {
    commentSending.value = false
  }
}
async function selectPanel(tab: string) {
  activePanel.value = tab
  if (tab === 'TA的作品' && !works.value.length && authorId.value) {
    panelLoading.value = true
    works.value = unwrap(
      await userVideoList({ uid: authorId.value, user_id: authorId.value, page: 1, pageSize: 20 })
    ).map(mapWork)
    panelLoading.value = false
  }
  if (tab === 'AI抖音' && !aiSummary.value) {
    panelLoading.value = true
    const res = await searchSummary(title.value)
    aiSummary.value = res?.success ? res.data?.summary || res.data?.content || res.data || '' : ''
    panelLoading.value = false
  }
}
function loadRelated() {
  related.value = feed.value
    .filter((item) => item !== video.value)
    .slice(0, 10)
    .map(mapRelated)
}

watch(
  () => video.value?.aweme_id ?? video.value?.id,
  (next, previous) => {
    if (previous && next !== previous) {
      recommendationCompletion.reset()
      const previousItem = feed.value.find(
        (item) => String(item?.aweme_id ?? item?.id) === String(previous)
      )
      flushRecommendationWatch('switch', previousItem, false)
    } else if (!previous && next) {
      recommendationCompletion.reset()
    }
    watchSeconds = 0
    lastHeartbeatSeconds = -1
    lastProfileSampleSeconds = -1
    lastFinishedSeconds = -1
    recommendCurrentTime.value = 0
    recommendDuration.value = 0
    if (next) startRecommendationWatch()
  }
)
function syncImageLoop() {
  if (imageTimer) window.clearInterval(imageTimer)
  imageTimer = null
  if (mediaKind.value === 'image' && imageUrls.value.length > 1)
    imageTimer = window.setInterval(() => {
      if (!recommendPlaying.value) return
      imageProgress.value += 0.02
      if (imageProgress.value >= 1) {
        imageProgress.value = 0
        activeImageIndex.value = (activeImageIndex.value + 1) % imageUrls.value.length
      }
    }, 100)
}
onMounted(async () => {
  const res = await recommendedVideo({ start: 0, pageSize: 20, feedMode: 'HOME' })
  feed.value = unwrap(res)
  video.value = feed.value[0] || null
  loadRelated()
  await loadCommentsForCurrent()
  loading.value = false
  syncImageLoop()
})
onUnmounted(() => {
  if (imageTimer) window.clearInterval(imageTimer)
  if (stageClickTimer) window.clearTimeout(stageClickTimer)
  stopRecommendationWatch('unmount')
})
</script>

<style scoped lang="less">
.recommend-shell {
  display: block;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #090a0e;
}
.recommend-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(430px, 28%);
  height: 100%;
  min-height: 0;
  background: #090a0e;
  color: #f3f4f8;
  transition: grid-template-columns 0.24s ease;
}
.recommend-page.panel-collapsed {
  grid-template-columns: 1fr;
}
.recommend-shell.theater-mode .recommend-page {
  grid-template-columns: 1fr;
}
.recommend-shell.theater-mode .recommend-panel {
  display: none;
}
.recommend-stage {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #050607;
}
.recommend-stage.slide-up {
  animation: recommend-slide-up 480ms cubic-bezier(0.22, 0.61, 0.36, 1);
  will-change: transform;
}
.recommend-stage.slide-down {
  animation: recommend-slide-down 480ms cubic-bezier(0.22, 0.61, 0.36, 1);
  will-change: transform;
}
@keyframes recommend-slide-up {
  from {
    transform: translate3d(0, 100%, 0);
  }
  to {
    transform: translate3d(0, 0, 0);
  }
}
@keyframes recommend-slide-down {
  from {
    transform: translate3d(0, -100%, 0);
  }
  to {
    transform: translate3d(0, 0, 0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .recommend-stage.slide-up,
  .recommend-stage.slide-down {
    animation: none;
  }
}
.recommend-stage video,
.stage-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.stage-text {
  display: grid;
  place-items: center;
  height: 100%;
  padding: 10%;
  box-sizing: border-box;
  background: #242633;
}
.stage-text p {
  max-width: 760px;
  color: #fff;
  font-size: 36px;
  line-height: 1.45;
  text-align: center;
}
.stage-shade {
  position: absolute;
  z-index: 2;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, #0007 0%, transparent 22%, transparent 65%, #000b 100%);
}
.stage-empty {
  display: grid;
  place-items: center;
  height: 100%;
  color: #9095a4;
}
.image-progress {
  position: absolute;
  z-index: 4;
  right: 12%;
  bottom: 22px;
  left: 12%;
  display: flex;
  gap: 4px;
  height: 4px;
}
.image-progress i {
  position: relative;
  flex: 1;
  overflow: hidden;
  border-radius: 4px;
  background: #ffffff66;
}
.image-progress i.filled {
  background: #fff;
}
.image-progress i b {
  position: absolute;
  inset: 0 auto 0 0;
  display: block;
  background: #ff315b;
}
.danmaku {
  position: absolute;
  z-index: 3;
  top: 18px;
  right: 12%;
  left: 12%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  color: #f5f5f7;
  font-size: 17px;
  text-shadow: 0 1px 3px #000;
  pointer-events: none;
}
.danmaku span:first-child {
  font-size: 18px;
}
.danmaku span {
  animation: recommend-danmaku 6s linear forwards;
  white-space: nowrap;
}
@keyframes recommend-danmaku {
  from {
    transform: translateX(90%);
  }
  to {
    transform: translateX(-110%);
  }
}
.recommend-copy {
  position: absolute;
  z-index: 3;
  right: 90px;
  bottom: 82px;
  left: 22px;
  text-shadow: 0 2px 5px #000;
}
.recommend-copy strong {
  font-size: 15px;
}
.recommend-copy span {
  color: #d6d8df;
  font-size: 13px;
}
.recommend-copy p {
  margin: 8px 0 5px;
  font-size: 15px;
}
.recommend-copy small {
  color: #ffcf24;
  font-size: 13px;
}
.recommend-actions {
  position: absolute;
  z-index: 4;
  right: 18px;
  bottom: 82px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 17px;
}
.recommend-actions button,
.panel-restore {
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
.recommend-actions svg {
  font-size: 28px;
}
.recommend-actions b {
  font-size: 12px;
  font-weight: 500;
}
.recommend-author-button {
  position: relative;
  margin-bottom: 4px;
}
.recommend-author-button img {
  display: block;
  width: 44px;
  height: 44px;
  border: 2px solid #fff;
  border-radius: 50%;
  object-fit: cover;
  background: #30323c;
}
.recommend-author-button i {
  position: absolute;
  right: 50%;
  bottom: -6px;
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
.panel-restore {
  display: none;
}
.recommend-panel {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 0 20px 72px;
  overflow: auto;
  border-left: 1px solid #282a35;
  background: #191a24;
}
.panel-tabs {
  display: flex;
  align-items: flex-end;
  gap: 21px;
  min-height: 64px;
  border-bottom: 1px solid #30323d;
  white-space: nowrap;
}
.panel-tabs button {
  position: relative;
  padding: 0 0 16px;
  border: 0;
  color: #b8bbc6;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
}
.panel-tabs button.active {
  color: #fff;
  font-weight: 700;
}
.panel-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  border-radius: 3px;
  background: #ff315b;
  content: '';
}
.panel-tabs .panel-close {
  margin-left: auto;
  padding: 0 0 16px;
}
.panel-tabs .panel-close::after {
  display: none;
}
.panel-tabs .panel-close svg {
  font-size: 20px;
}
.panel-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.panel-content h2 {
  margin: 22px 0 8px;
  font-size: 18px;
}
.detail-content {
  padding-top: 20px;
}
.detail-author {
  display: flex;
  align-items: center;
  gap: 10px;
}
.detail-author img {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
}
.detail-author button {
  margin-left: auto;
  padding: 6px 16px;
  border: 0;
  border-radius: 6px;
  color: #fff;
  background: #ff315b;
  cursor: pointer;
}
.detail-desc,
.ai-summary {
  color: #c8cad4;
  font-size: 13px;
  line-height: 1.65;
}
.detail-stats {
  display: flex;
  gap: 26px;
  margin: 20px 0;
  color: #a1a5b3;
  font-size: 12px;
}
.section-title {
  margin: 20px 0 14px;
  font-size: 16px;
  font-weight: 700;
}
.section-title button {
  float: right;
  border: 0;
  color: #46b8ff;
  background: transparent;
  cursor: pointer;
}
.works-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.works-grid article img {
  width: 100%;
  aspect-ratio: 1 / 1.35;
  border-radius: 6px;
  object-fit: cover;
}
.works-grid article span {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: #c9cbd4;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-hint {
  margin: 18px 0 4px;
  color: #ffd334;
  font-size: 12px;
}
.comment-count {
  margin: 7px 0;
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
.ai-content {
  padding-top: 20px;
}
.ai-summary {
  padding: 16px;
  border-radius: 8px;
  background: #252631;
}
.related-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #272934;
}
.related-item img {
  flex: 0 0 135px;
  width: 135px;
  height: 76px;
  border-radius: 7px;
  object-fit: cover;
}
.related-item div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: space-between;
}
.related-item strong {
  overflow: hidden;
  color: #e4e5eb;
  font-size: 13px;
}
.related-item small {
  color: #898e9e;
  font-size: 11px;
}
.empty {
  display: grid;
  place-items: center;
  min-height: 160px;
  color: #858b9a;
}
.panel-login {
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
.panel-composer {
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
  background: #191a24f5;
  backdrop-filter: blur(14px);
}
.panel-composer input {
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
.panel-composer input:focus {
  border-color: #646879;
}
.panel-composer input::placeholder {
  color: #8f94a4;
}
.panel-composer button {
  width: 58px;
  border: 0;
  border-radius: 9px;
  color: #fff;
  background: #ff315b;
  cursor: pointer;
}
.panel-composer button:disabled {
  cursor: wait;
  opacity: 0.6;
}
@media (max-width: 1100px) {
  .recommend-page {
    grid-template-columns: minmax(0, 1fr) 390px;
  }
  .recommend-actions {
    right: 11px;
  }
  .panel-tabs {
    gap: 13px;
  }
}
.recommend-page {
  height: 100%;
  min-height: 100%;
  overflow: hidden;
  overscroll-behavior-y: contain;
}
.recommend-stage {
  min-height: 0;
}
</style>

<style scoped lang="less">
.recommend-stage::before {
  position: absolute;
  z-index: 0;
  inset: -24px;
  background-image: var(--media-bg);
  background-position: center;
  background-size: cover;
  filter: blur(24px);
  opacity: 0.6;
  transform: scale(1.08);
  content: '';
}
.recommend-stage video,
.recommend-stage .stage-image,
.recommend-stage .stage-text,
.recommend-stage .stage-empty {
  position: relative;
  z-index: 1;
}
.recommend-stage video,
.recommend-stage .stage-image {
  object-fit: contain;
}
</style>
