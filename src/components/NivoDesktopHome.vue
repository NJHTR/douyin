<template>
  <main class="nivo-home" :class="{ 'theater-mode': theaterMode }">
    <nav class="channels" aria-label="内容频道">
      <button
        v-for="channel in channels"
        :key="channel"
        type="button"
        :class="{ active: activeChannel === channel }"
        @click="activeChannel = channel"
      >
        {{ channel }}
      </button>
    </nav>
    <section v-if="loading" class="content-layout skeleton-layout" aria-label="正在加载内容">
      <div class="main-column">
        <div class="skeleton hero-skeleton"></div>
        <div class="skeleton-row">
          <div v-for="i in 3" :key="i" class="skeleton card-skeleton"></div>
        </div>
      </div>
      <div class="right-rail">
        <div v-for="i in 3" :key="i" class="skeleton rail-skeleton"></div>
      </div>
    </section>
    <section v-else-if="!apiVideos.length" class="content-empty">暂时没有可展示的内容</section>
    <section v-else class="content-layout">
      <div class="main-column">
        <article
          class="hero-card"
          :style="{ backgroundImage: `url(${featured.cover})` }"
          @click="openVideo(featured.id)"
        >
          <template v-if="featured.kind === 'video'"
            ><video
              ref="heroVideo"
              class="hero-image"
              :src="featured.videoUrl"
              :poster="featured.cover"
              autoplay
              muted
              loop
              playsinline
              controlslist="nodownload noplaybackrate noremoteplayback"
              disableremoteplayback
              @timeupdate="syncVideoProgress"
              @loadedmetadata="syncVideoProgress"
              @play="handleFeaturedPlay"
              @pause="handleFeaturedPause"
              @ended="handleFeaturedEnded"
            ></video></template
          ><template v-else-if="featured.kind === 'image'"
            ><img
              class="hero-image"
              :src="_checkImgUrl(featured.imageUrls[activeImageIndex] || featured.cover)"
              :alt="featured.title"
          /></template>
          <div v-else class="hero-text-media">
            <p>{{ featured.title }}</p>
          </div>
          <div class="hero-shade"></div>
          <span class="hero-kicker">{{ activeChannel }} · NIVO 精选</span>
          <div class="hero-copy">
            <h1>{{ featured.title }}</h1>
            <p>@{{ featured.author }} · {{ featured.date }}</p>
            <span>{{ featured.description }}</span>
          </div>
          <NivoVideoControls
            v-if="featured"
            :media-type="featured.kind"
            :playing="playing"
            :muted="heroMuted"
            :current-time="featured.kind === 'image' ? imageProgress : heroCurrentTime"
            :duration="featured.kind === 'image' ? 1 : heroDuration"
            @toggle-play="toggleHeroPlayback"
            @toggle-mute="toggleHeroMute"
            @seek="seekHero"
            @speed="setHeroSpeed"
            @picture-in-picture="toggleHeroPictureInPicture"
            @theater="theaterMode = !theaterMode"
            @fullscreen="toggleHeroFullscreen"
            @danmaku="addHeroDanmaku"
          />
          <div class="hero-danmaku">
            <span v-for="item in heroDanmaku" :key="item.id">{{ item.content }}</span>
          </div>
          <div class="hero-actions">
            <button type="button" aria-label="喜欢">
              <Icon icon="solar:heart-linear" /><b>{{ featured.likes }}</b></button
            ><button type="button" aria-label="评论" @click.stop="showComments = true">
              <Icon icon="solar:chat-round-dots-linear" /><b>{{ featured.comments }}</b></button
            ><button type="button" aria-label="收藏">
              <Icon icon="solar:star-linear" /><b>收藏</b></button
            ><button type="button" aria-label="分享"><Icon icon="solar:share-linear" /></button>
          </div>
        </article>
        <div class="feed-heading">
          <h2>继续观看</h2>
          <button type="button" @click="refreshFeed">
            <Icon icon="solar:refresh-linear" /> 换一批
          </button>
        </div>
        <div class="video-grid">
          <article
            v-for="video in visibleVideos"
            :key="video.id"
            class="video-card"
            @click="openVideo(video.id)"
          >
            <div class="video-cover" :style="{ '--media-bg': `url(${video.cover})` }">
              <video
                v-if="video.kind === 'video' && video.videoUrl"
                :src="video.videoUrl"
                :poster="video.cover"
                muted
                loop
                playsinline
                preload="metadata"
                @mouseenter="playPreview"
                @mouseleave="pausePreview"
              ></video
              ><img v-else :src="video.cover" :alt="video.title" /><span
                v-if="video.kind === 'video'"
                >{{ video.duration }}</span
              >
              <div v-if="video.kind === 'image'" class="mini-segments">
                <i
                  v-for="(_, index) in video.imageUrls"
                  :key="index"
                  :class="{ filled: index === 0 }"
                ></i>
              </div>
            </div>
            <h3>{{ video.title }}</h3>
            <p>@{{ video.author }} · {{ video.views }}次观看</p>
          </article>
        </div>
      </div>
      <aside class="right-rail" aria-label="右侧推荐">
        <div class="rail-heading">
          <h2>热门推荐</h2>
          <button type="button" @click="refreshFeed">换一换</button>
        </div>
        <article
          v-for="item in railItems"
          :key="item.id"
          class="rail-video"
          @click="openVideo(item.id)"
        >
          <div class="rail-cover" :style="{ '--media-bg': `url(${item.cover})` }">
            <img :src="item.cover" :alt="item.title" /><span v-if="item.kind === 'video'">{{
              item.duration
            }}</span>
            <div v-if="item.kind === 'image'" class="mini-segments">
              <i
                v-for="(_, index) in item.imageUrls"
                :key="index"
                :class="{ filled: index === 0 }"
              ></i>
            </div>
          </div>
          <div class="rail-copy">
            <h3>{{ item.title }}</h3>
            <p>@{{ item.author }} · {{ item.views }}次观看</p>
          </div>
        </article>
      </aside>
    </section>
    <div v-if="showComments" class="context-drawer">
      <div class="drawer-header">
        <b>评论</b
        ><button type="button" aria-label="关闭评论" @click="showComments = false">
          <Icon icon="solar:close-circle-linear" />
        </button>
      </div>
      <p class="drawer-search">大家都在搜：{{ featured?.title || '精选内容' }}</p>
      <div v-if="commentsLoading" class="drawer-empty">正在加载评论...</div>
      <div v-else-if="!comments.length" class="drawer-empty">还没有评论</div>
      <div v-for="comment in comments" :key="comment.id || comment.author" class="comment">
        <img :src="comment.avatar" alt="" />
        <div>
          <strong>{{ comment.author }}</strong>
          <p>{{ comment.text }}</p>
          <small>{{ comment.time }}</small>
          <div class="comment-actions">
            <button type="button" @click="replyToFeaturedComment(comment)">回复</button>
            <button type="button" @click="shareFeaturedComment(comment)">分享</button>
            <button type="button" @click="likeFeaturedComment(comment)">
              <Icon icon="solar:heart-linear" />{{ comment.likes }}
            </button>
          </div>
        </div>
      </div>
      <button class="drawer-login" type="button" @click="bus.emit('DESKTOP_LOGIN')">
        登录后参与评论
      </button>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { recommendedVideo, toggleCommentLike, videoComments } from '@/api/videos'
import { _checkImgUrl, _formatNumber } from '@/utils'
import { recommendationChannelForLabel, trafficSourceForFeed } from '@/utils/recommendation'
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
const router = useRouter()
const baseStore = useBaseStore()
const activeChannel = ref('全部')
const refreshKey = ref(0)
const playing = ref(true)
const heroMuted = ref(true)
const heroCurrentTime = ref(0)
const heroDuration = ref(0)
const theaterMode = ref(false)
const heroDanmaku = ref<Array<{ id: number; content: string }>>([])
const showComments = ref(false)
const commentsLoading = ref(false)
const loading = ref(true)
const activeImageIndex = ref(0)
const imageProgress = ref(0)
const imageTimer = ref<number | null>(null)
const heroVideo = ref<HTMLVideoElement | null>(null)
const channels = [
  '全部',
  '公开课',
  '游戏',
  '二次元',
  '音乐',
  '影视',
  '美食',
  '知识',
  '小剧场',
  '生活 vlog',
  '体育',
  '旅行',
  '亲子',
  '动物',
  '三农',
  '汽车'
]
const apiVideos = ref<any[]>([])
const comments = ref<any[]>([])
const watchBatcher = getRecommendationWatchBatcher()
let loadSequence = 0
let featuredWatchTimer: ReturnType<typeof setInterval> | null = null
let featuredWatchSeconds = 0
let featuredLastHeartbeatSeconds = -1
let featuredLastProfileSampleSeconds = -1
let featuredLastFinishedSeconds = -1
const featuredCompletion = new LoopCompletionTracker()
function formatDate(value: unknown) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return '刚刚'
  const date = new Date(numeric < 1e12 ? numeric * 1000 : numeric)
  return Number.isNaN(date.getTime()) ? '刚刚' : date.toLocaleDateString('zh-CN')
}
const featured = computed(() => apiVideos.value[0] || null)
const railItems = computed(() => apiVideos.value.slice(2, 4))
const visibleVideos = computed(() => {
  const source = apiVideos.value
  const offset = refreshKey.value % Math.max(source.length - 1, 1)
  return [...source.slice(1 + offset), ...source.slice(1, 1 + offset)]
})
function normalizeVideo(item: any, index: number) {
  const cover =
    item?.video?.cover?.url_list?.[0] ||
    item?.video?.origin_cover?.url_list?.[0] ||
    item?.cover_url ||
    item?.cover ||
    ''
  const imageUrls = Array.isArray(item?.image_urls)
    ? item.image_urls
    : typeof item?.image_urls === 'string'
      ? (() => {
          try {
            const parsed = JSON.parse(item.image_urls)
            return Array.isArray(parsed) ? parsed : []
          } catch {
            return []
          }
        })()
      : []
  const mediaType =
    item?.type === 'text'
      ? 'text'
      : imageUrls.length > 0 || item?.type === 'image'
        ? 'image'
        : 'video'
  const durationSeconds = videoDurationSeconds(item)
  return {
    id: String(item?.aweme_id ?? item?.id ?? `api-${index}`),
    title: item?.desc || item?.title || '内容',
    author: item?.author?.nickname || item?.author_name || '内容作者',
    views: _formatNumber(item?.statistics?.play_count ?? item?.play_count ?? 0),
    duration: durationSeconds
      ? `${Math.floor(durationSeconds / 60)
          .toString()
          .padStart(2, '0')}:${Math.floor(durationSeconds % 60)
          .toString()
          .padStart(2, '0')}`
      : '03:42',
    cover: _checkImgUrl(cover || imageUrls[0] || ''),
    videoUrl: _checkImgUrl(item?.video?.play_addr?.url_list?.[0] || item?.video_url || ''),
    imageUrls,
    kind: mediaType,
    date: formatDate(item?.create_time),
    description: item?.desc || '来自精选内容流',
    likes: _formatNumber(item?.statistics?.digg_count ?? item?.digg_count ?? 0),
    comments: _formatNumber(item?.statistics?.comment_count ?? item?.comment_count ?? 0),
    raw: item,
    frames: mediaType === 'image' ? Math.max(imageUrls.length, 1) : 0
  }
}
async function loadFeed() {
  const sequence = ++loadSequence
  loading.value = true
  const mode = recommendationChannelForLabel(activeChannel.value)
  const res = await recommendedVideo({ start: 0, pageSize: 12, feedMode: mode })
  if (sequence !== loadSequence) return
  if (res.success) {
    const list = Array.isArray(res.data) ? res.data : res.data?.list || []
    apiVideos.value = list
      .map(normalizeVideo)
      .filter(
        (item) =>
          item.raw?.video ||
          item.raw?.cover_url ||
          item.raw?.cover ||
          item.raw?.image_urls ||
          item.raw?.type === 'text'
      )
  }
  loading.value = false
  activeImageIndex.value = 0
  imageProgress.value = 0
}

function flushFeaturedWatch(
  reason: WatchFlushReason = 'heartbeat',
  item: any = featured.value,
  includePosition = true
) {
  if (!item || !featuredWatchSeconds || !baseStore.isLoggedIn) return
  const { finished, profileSample } = watchFlushPolicy(reason)
  if (!profileSample && featuredWatchSeconds === featuredLastHeartbeatSeconds) return
  if (profileSample && !finished && featuredWatchSeconds === featuredLastProfileSampleSeconds)
    return
  if (finished && featuredWatchSeconds === featuredLastFinishedSeconds) return
  // Keep this cumulative for the monotonic history upsert; the video-change
  // watcher resets the counter after the previous item is flushed.
  watchBatcher.enqueue(item.id, {
    watch_duration: featuredWatchSeconds,
    video_duration: videoDurationSeconds(item.raw, heroDuration.value),
    finished,
    session_id: getBrowsingSessionId(),
    swipe_seconds: featuredWatchSeconds,
    traffic_source: trafficSourceForFeed(recommendationChannelForLabel(activeChannel.value)),
    last_position: includePosition ? heroCurrentTime.value : 0,
    profile_sample: profileSample
  })
  featuredLastHeartbeatSeconds = featuredWatchSeconds
  if (profileSample) featuredLastProfileSampleSeconds = featuredWatchSeconds
  if (finished) featuredLastFinishedSeconds = featuredWatchSeconds
}

function startFeaturedWatch() {
  if (featuredWatchTimer !== null) return
  featuredWatchTimer = setInterval(() => {
    if (playing.value && featured.value) {
      featuredWatchSeconds += 1
      if (featuredWatchSeconds % 15 === 0) flushFeaturedWatch('heartbeat')
    }
  }, 1000)
}

function stopFeaturedWatch(reason: WatchFlushReason = 'unmount') {
  if (featuredWatchTimer !== null) clearInterval(featuredWatchTimer)
  featuredWatchTimer = null
  flushFeaturedWatch(reason)
}
async function loadComments() {
  const id = featured.value?.id
  if (!id) return
  commentsLoading.value = true
  const res = await videoComments({ id, pageNo: 1, pageSize: 20 })
  commentsLoading.value = false
  if (res.success) {
    const list = Array.isArray(res.data) ? res.data : res.data?.list || []
    comments.value = list.map((item: any) => ({
      id: item.id || item.comment_id,
      author: item.user?.nickname || item.nickname || '用户',
      text: item.content || '',
      likes: _formatNumber(item.like_count || item.digg_count || 0),
      avatar: _checkImgUrl(item.user?.avatar || item.avatar || ''),
      time: item.create_time || '刚刚'
    }))
  } else comments.value = []
}
function replyToFeaturedComment(_comment?: any) {
  bus.emit('DESKTOP_LOGIN')
}
async function shareFeaturedComment(comment: any) {
  const text = `${comment.author}：${comment.text}`
  if (navigator.share) await navigator.share({ text, url: location.href }).catch(() => {})
  else await navigator.clipboard?.writeText(text).catch(() => {})
}
async function likeFeaturedComment(comment: any) {
  if (!comment.id) return
  const res = await toggleCommentLike(String(comment.id))
  if (res?.success) comment.likes = _formatNumber(res.data?.likeCount ?? res.data?.count ?? 0)
}
watch(showComments, (open) => {
  if (open) loadComments()
})
onMounted(loadFeed)
watch(activeChannel, () => {
  refreshKey.value += 1
  void loadFeed()
})
watch(
  featured,
  (next, previous) => {
    if (previous && next?.id !== previous?.id) {
      featuredCompletion.reset()
      flushFeaturedWatch('switch', previous, false)
      featuredWatchSeconds = 0
      featuredLastHeartbeatSeconds = -1
      featuredLastProfileSampleSeconds = -1
      featuredLastFinishedSeconds = -1
      heroCurrentTime.value = 0
      heroDuration.value = 0
    } else if (!previous && next) {
      featuredCompletion.reset()
    }
    activeImageIndex.value = 0
    imageProgress.value = 0
    if (next) startFeaturedWatch()
  },
  { immediate: true }
)
function openVideo(id: string) {
  router.push({ path: '/video-detail', query: { id } })
}
function refreshFeed() {
  refreshKey.value += 1
  void loadFeed()
}
function playPreview(event: Event) {
  const video = event.currentTarget as HTMLVideoElement
  void video.play().catch(() => {})
}
function pausePreview(event: Event) {
  const video = event.currentTarget as HTMLVideoElement
  video.pause()
  video.currentTime = 0
}
function syncVideoProgress(e: Event) {
  const el = e.target as HTMLVideoElement
  if (!el?.duration || featured.value?.kind !== 'video') return
  const completedLoop = featuredCompletion.observe(el.currentTime, el.duration)
  imageProgress.value = Math.min(1, el.currentTime / el.duration)
  heroCurrentTime.value = el.currentTime
  heroDuration.value = el.duration
  heroMuted.value = el.muted
  if (completedLoop) flushFeaturedWatch('ended')
}
function handleFeaturedPlay() {
  playing.value = true
}
function handleFeaturedPause() {
  playing.value = false
  flushFeaturedWatch('pause')
}
function handleFeaturedEnded() {
  if (featuredCompletion.complete()) flushFeaturedWatch('ended')
  // Keep the visible loop even on browsers that still dispatch `ended` with
  // the native loop attribute enabled.
  playing.value = true
  if (heroVideo.value) {
    heroVideo.value.currentTime = 0
    void heroVideo.value.play().catch(() => {})
  }
}
function toggleHeroPlayback() {
  if (featured.value?.kind !== 'video') {
    if (playing.value) handleFeaturedPause()
    else handleFeaturedPlay()
    return
  }
  if (!heroVideo.value) return
  if (heroVideo.value.paused) void heroVideo.value.play().catch(() => {})
  else heroVideo.value.pause()
}
function addHeroDanmaku(content: string) {
  const id = Date.now()
  heroDanmaku.value.push({ id, content })
  window.setTimeout(() => {
    heroDanmaku.value = heroDanmaku.value.filter((item) => item.id !== id)
  }, 6000)
}
function toggleHeroMute() {
  if (!heroVideo.value) return
  heroVideo.value.muted = !heroVideo.value.muted
  heroMuted.value = heroVideo.value.muted
}
function seekHero(ratio: number) {
  if (!heroVideo.value?.duration) return
  featuredCompletion.markSeek()
  heroVideo.value.currentTime = ratio * heroVideo.value.duration
}
function setHeroSpeed(rate: number) {
  if (heroVideo.value) heroVideo.value.playbackRate = rate
}
async function toggleHeroPictureInPicture() {
  if (!heroVideo.value || !document.pictureInPictureEnabled) return
  if (document.pictureInPictureElement) await document.exitPictureInPicture()
  else await heroVideo.value.requestPictureInPicture?.()
}
function toggleHeroFullscreen() {
  const target = (heroVideo.value?.closest('.hero-card') ||
    document.querySelector('.hero-card')) as HTMLElement | null
  if (!target) return
  if (document.fullscreenElement) void document.exitFullscreen()
  else void target.requestFullscreen?.()
}
function startImageLoop() {
  if (imageTimer.value) return
  imageTimer.value = window.setInterval(() => {
    if (!playing.value) return
    if (featured.value?.kind !== 'image' || !featured.value?.imageUrls?.length) return
    imageProgress.value += 0.01
    if (imageProgress.value >= 1) {
      imageProgress.value = 0
      activeImageIndex.value = (activeImageIndex.value + 1) % featured.value.imageUrls.length
    }
  }, 100)
}
function stopImageLoop() {
  if (imageTimer.value) window.clearInterval(imageTimer.value)
  imageTimer.value = null
}
watch(
  () => featured.value?.kind,
  (kind) => {
    if (kind === 'image') startImageLoop()
    else stopImageLoop()
  },
  { immediate: true }
)
watch(playing, (active) => {
  if (!heroVideo.value) return
  if (active) void heroVideo.value.play().catch(() => {})
  else heroVideo.value.pause()
})
onUnmounted(() => {
  stopImageLoop()
  stopFeaturedWatch('unmount')
})
const progressSegments = computed(() =>
  featured.value?.kind === 'image'
    ? featured.value.imageUrls
    : featured.value?.kind === 'video'
      ? [1]
      : []
)
const progressSegmentIndex = computed(() =>
  featured.value?.kind === 'image'
    ? activeImageIndex.value
    : featured.value?.kind === 'video'
      ? 0
      : -1
)
const formattedProgress = computed(() =>
  featured.value?.kind === 'image'
    ? `${String(activeImageIndex.value + 1).padStart(2, '0')}/${String(featured.value.imageUrls.length).padStart(2, '0')}`
    : '00:00'
)
</script>

<style scoped lang="less">
.nivo-home {
  position: relative;
  width: 100%;
  padding: 0 25px 55px;
  color: #f4f4f7;
  box-sizing: border-box;
}
.channels {
  display: flex;
  gap: 27px;
  height: 61px;
  align-items: end;
  overflow: auto;
  border-bottom: 1px solid #242532;
  white-space: nowrap;
}
.channels button {
  height: 45px;
  padding: 0;
  border: 0;
  color: #a7aab7;
  background: transparent;
  font-size: 15px;
  cursor: pointer;
}
.channels button:hover {
  color: #fff;
}
.channels button.active {
  position: relative;
  color: #fff;
  font-weight: 700;
}
.channels button.active:after {
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 18px;
  height: 3px;
  transform: translateX(-50%);
  border-radius: 3px;
  background: #ff315b;
  content: '';
}
.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 375px;
  gap: 21px;
  margin-top: 17px;
}
.hero-card {
  position: relative;
  height: calc(100vh - 215px);
  max-height: 730px;
  min-height: 430px;
  overflow: hidden;
  border-radius: 11px;
  background: #08090d;
  cursor: pointer;
}
.hero-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.9);
}
.hero-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, #06070a33 0%, transparent 45%, #08090dcc 100%);
}
.hero-kicker {
  position: absolute;
  top: 21px;
  left: 25px;
  color: #ffec70;
  font-size: 16px;
  font-weight: 700;
}
.hero-copy {
  position: absolute;
  left: 25px;
  bottom: 92px;
  text-shadow: 0 2px 5px #000;
}
.hero-copy h1 {
  max-width: 640px;
  margin: 0 0 8px;
  font-size: 28px;
  line-height: 1.2;
}
.hero-copy p {
  margin: 0 0 9px;
  color: #e8e8ec;
  font-size: 14px;
}
.hero-copy span {
  color: #d8d8de;
  font-size: 13px;
}
.hero-controls {
  position: absolute;
  right: 23px;
  bottom: 20px;
  left: 23px;
  display: flex;
  align-items: center;
  gap: 11px;
  color: #e5e5ea;
  font-size: 12px;
}
.hero-controls button {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  color: #fff;
  background: transparent;
  cursor: pointer;
}
.hero-controls button svg {
  font-size: 17px;
}
.progress {
  flex: 1;
  height: 3px;
  border-radius: 3px;
  background: #ffffff55;
}
.progress i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: #fff;
}
.hero-actions {
  position: absolute;
  right: 19px;
  bottom: 92px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.hero-actions button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 0;
  border: 0;
  color: #fff;
  background: transparent;
  text-shadow: 0 1px 4px #000;
  cursor: pointer;
}
.hero-actions svg {
  font-size: 25px;
}
.hero-actions b {
  font-size: 11px;
  font-weight: 500;
}
.feed-heading,
.rail-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.feed-heading {
  margin: 26px 0 14px;
}
.feed-heading h2,
.rail-heading h2 {
  margin: 0;
  font-size: 18px;
}
.feed-heading button,
.rail-heading button {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  color: #9ba0ae;
  background: transparent;
  cursor: pointer;
}
.video-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px 15px;
}
.video-card {
  min-width: 0;
  cursor: pointer;
}
.video-cover {
  position: relative;
  aspect-ratio: 16/10;
  overflow: hidden;
  border-radius: 7px;
  background: #242632;
}
.video-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.25s;
}
.video-card:hover img {
  transform: scale(1.04);
}
.video-cover span,
.rail-cover span {
  position: absolute;
  right: 7px;
  bottom: 6px;
  padding: 2px 5px;
  border-radius: 3px;
  color: #fff;
  background: #000b;
  font-size: 11px;
}
.video-cover button {
  position: absolute;
  inset: 50% auto auto 50%;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  transform: translate(-50%, -50%);
  border: 0;
  border-radius: 50%;
  color: #fff;
  background: #ff315bdd;
  opacity: 0;
  cursor: pointer;
}
.video-card:hover .video-cover button {
  opacity: 1;
}
.video-cover button svg {
  font-size: 17px;
}
.video-card h3 {
  overflow: hidden;
  margin: 10px 0 5px;
  color: #f1f1f4;
  font-size: 14px;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.video-card p,
.rail-copy p,
.friend p {
  margin: 0;
  color: #8f94a4;
  font-size: 11px;
}
.right-rail {
  min-width: 0;
  padding: 2px 0 0;
}
.rail-heading {
  margin: 0 0 14px;
}
.rail-video {
  display: flex;
  gap: 11px;
  margin: 0 0 17px;
  cursor: pointer;
}
.rail-cover {
  position: relative;
  flex: 0 0 154px;
  height: 92px;
  overflow: hidden;
  border-radius: 7px;
  background: #252732;
}
.rail-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.rail-copy {
  min-width: 0;
  padding-top: 2px;
}
.rail-copy h3 {
  display: -webkit-box;
  overflow: hidden;
  margin: 0 0 7px;
  color: #f3f3f5;
  font-size: 14px;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.friends {
  padding-top: 9px;
  border-top: 1px solid #292a36;
}
.friend {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
}
.friend img {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
}
.friend strong {
  display: block;
  color: #f0f0f3;
  font-size: 13px;
}
.friend p {
  margin-top: 3px;
}
.friend svg {
  margin-left: auto;
  color: #ff4969;
}
.context-drawer {
  position: fixed;
  z-index: 20;
  top: 70px;
  right: 0;
  bottom: 0;
  width: 390px;
  padding: 0 21px;
  overflow: auto;
  border-left: 1px solid #30313e;
  color: #eef0f5;
  background: #1b1c27;
  box-shadow: -15px 0 35px #0005;
}
.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 63px;
  border-bottom: 1px solid #30313e;
}
.drawer-header button {
  border: 0;
  color: #a6aab8;
  background: transparent;
  cursor: pointer;
}
.drawer-header svg {
  font-size: 21px;
}
.drawer-search {
  color: #ffd837;
  font-size: 12px;
}
.comment {
  display: flex;
  gap: 11px;
  padding: 15px 0;
}
.comment img {
  width: 34px;
  height: 34px;
  border-radius: 50%;
}
.comment strong {
  font-size: 13px;
}
.comment p {
  margin: 7px 0;
  color: #d5d7de;
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
.drawer-login {
  position: sticky;
  bottom: 0;
  padding: 17px;
  margin: 20px 0 15px;
  border-radius: 9px;
  color: #ff4969;
  text-align: center;
  background: #2c2e3a;
}
@media (max-width: 1150px) {
  .content-layout {
    grid-template-columns: minmax(0, 1fr) 300px;
  }
  .hero-actions {
    right: 12px;
  }
  .rail-cover {
    flex-basis: 125px;
  }
}
@media (max-width: 900px) {
  .content-layout {
    display: block;
  }
  .right-rail {
    display: none;
  }
  .hero-card {
    height: calc(100vh - 190px);
    min-height: 400px;
  }
  .video-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .nivo-home {
    padding: 0 15px 30px;
  }
  .channels {
    gap: 19px;
  }
  .hero-copy h1 {
    font-size: 22px;
  }
  .hero-actions {
    right: 10px;
  }
  .video-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style scoped lang="less">
.video-cover img,
.video-cover video,
.rail-cover img {
  object-fit: contain !important;
}
</style>

<style scoped lang="less">
/* Preserve source media proportions and use a blurred copy only as the letterbox fill. */
.video-cover::before,
.rail-cover::before {
  position: absolute;
  z-index: 0;
  inset: -18px;
  background-image: var(--media-bg);
  background-position: center;
  background-size: cover;
  filter: blur(18px);
  opacity: 0.55;
  transform: scale(1.08);
  content: '';
}
.video-cover img,
.video-cover video,
.rail-cover img {
  position: relative;
  z-index: 1;
  object-fit: contain;
}
.video-cover video {
  display: block;
}
.video-cover span,
.rail-cover span,
.mini-segments {
  z-index: 2;
}
</style>

<style scoped lang="less">
.nivo-home {
  width: 100%;
  max-width: none;
  margin: 0;
  box-sizing: border-box;
}
.content-layout {
  grid-template-columns: minmax(0, 3.2fr) minmax(360px, 1fr);
  margin-top: 0;
}
.rail-video {
  display: block;
  margin-bottom: 18px;
}
.rail-cover {
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 9;
}
.rail-copy {
  padding-top: 8px;
}
.context-drawer {
  width: min(560px, 28vw);
  min-width: 390px;
}
.skeleton-layout {
  display: grid;
}
.skeleton {
  border-radius: 10px;
  background: linear-gradient(90deg, #20222c 25%, #2b2e39 37%, #20222c 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.35s ease infinite;
}
.hero-skeleton {
  height: calc(100vh - 215px);
  min-height: 430px;
}
.skeleton-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 15px;
  margin-top: 20px;
}
.card-skeleton {
  height: 180px;
}
.rail-skeleton {
  height: 145px;
  margin-bottom: 18px;
}
.content-empty {
  display: grid;
  min-height: 420px;
  place-items: center;
  color: #8f94a4;
}
.hero-text-media {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  padding: 40px;
  box-sizing: border-box;
  color: #f5f5f8;
  background: #292b38;
  text-align: center;
}
.hero-text-media p {
  max-width: 700px;
  font-size: 34px;
  line-height: 1.35;
}
.progress-segments {
  display: flex;
  flex: 1;
  gap: 3px;
  height: 3px;
}
.progress-segments i,
.mini-segments i {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  border-radius: 2px;
  background: #ffffff55;
}
.progress-segments i.filled,
.mini-segments i.filled {
  background: #fff;
}
.progress-segments i b {
  position: absolute;
  inset: 0 auto 0 0;
  display: block;
  background: #ff315b;
}
.mini-segments {
  position: absolute;
  right: 8px;
  bottom: 8px;
  left: 8px;
  z-index: 1;
  display: flex;
  gap: 3px;
  height: 3px;
}
.drawer-login {
  width: 100%;
  border: 0;
  color: #fff;
  background: #ff315b;
  cursor: pointer;
}
.video-cover video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
@keyframes skeleton-shimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: 0 0;
  }
}
.feed-heading {
  height: 22px;
  margin: 22px 0 0;
}
.feed-heading h2,
.feed-heading button {
  display: none;
}
.right-rail > .rail-heading {
  display: none;
}
.channels {
  position: sticky;
  z-index: 8;
  top: 0;
  height: 52px;
  align-items: center;
  border-bottom: 0;
  background: #12131c;
  backdrop-filter: none;
}
.channels {
  justify-content: flex-start;
}
.content-layout {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-auto-rows: minmax(180px, auto);
  gap: 20px;
  margin-top: 0;
}
.content-layout:not(.skeleton-layout) .main-column,
.content-layout:not(.skeleton-layout) .video-grid {
  display: contents;
}
.content-layout:not(.skeleton-layout) .right-rail {
  display: grid;
  grid-column: 4;
  grid-row: 1 / span 2;
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 20px;
  min-width: 0;
}
.hero-card {
  grid-column: 1 / span 3;
  grid-row: 1 / span 2;
  height: auto;
  min-height: 0;
  aspect-ratio: 16 / 9;
  align-self: stretch;
}
.right-rail > .rail-video {
  display: flex;
  flex-direction: column;
  min-height: 0;
  margin: 0;
}
.rail-cover {
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 9;
  flex: 0 0 auto;
}
.video-card {
  grid-column: span 1;
}
.feed-heading {
  display: none;
}
@media (max-width: 1150px) {
  .content-layout {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 14px;
  }
  .content-layout:not(.skeleton-layout) .right-rail {
    gap: 14px;
  }
  .hero-card {
    grid-column: 1 / span 3;
  }
  .rail-copy h3 {
    font-size: 12px;
  }
}
@media (max-width: 900px) {
  .content-layout {
    display: block;
  }
  .main-column,
  .right-rail,
  .video-grid {
    display: block;
  }
  .hero-card {
    height: calc(100vh - 190px);
    min-height: 400px;
    aspect-ratio: auto;
  }
  .right-rail {
    display: none;
  }
}
.content-layout:not(.skeleton-layout) {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-template-rows: auto auto;
  align-items: start;
}
.content-layout:not(.skeleton-layout) .hero-card {
  grid-column: 1 / span 3;
  grid-row: 1;
  width: 100%;
  aspect-ratio: 16 / 9;
  height: auto;
}
.content-layout:not(.skeleton-layout) .right-rail {
  grid-column: 4;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}
.content-layout:not(.skeleton-layout) .right-rail > .rail-video {
  display: block;
  flex: 1 1 0;
  width: 100%;
  min-height: 0;
}
.content-layout:not(.skeleton-layout) .right-rail > .rail-video .rail-cover {
  height: auto;
  aspect-ratio: 16 / 9;
}
.content-layout:not(.skeleton-layout) .video-grid {
  grid-column: 1 / -1;
  grid-row: 2;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px 15px;
}
@media (max-width: 900px) {
  .content-layout:not(.skeleton-layout) {
    display: block;
  }
  .content-layout:not(.skeleton-layout) .main-column,
  .content-layout:not(.skeleton-layout) .video-grid {
    display: block;
  }
  .content-layout:not(.skeleton-layout) .right-rail {
    display: none;
  }
  .content-layout:not(.skeleton-layout) .hero-card {
    width: 100%;
    aspect-ratio: auto;
    height: calc(100vh - 190px);
    min-height: 400px;
  }
}
.content-layout:not(.skeleton-layout) .hero-card {
  aspect-ratio: 2.2 / 1;
  max-height: 680px;
  background-position: center;
  background-size: cover;
}
.hero-card::before {
  position: absolute;
  z-index: 0;
  inset: -28px;
  background: inherit;
  background-position: center;
  background-size: cover;
  filter: blur(24px);
  opacity: 0.58;
  transform: scale(1.08);
  content: '';
}
.hero-image,
.hero-text-media {
  position: relative;
  z-index: 1;
  object-fit: contain;
}
.hero-shade {
  z-index: 2;
}
.hero-kicker,
.hero-copy,
.hero-controls,
.hero-actions {
  z-index: 3;
}
.hero-copy {
  bottom: 78px;
}
.hero-actions {
  display: none;
}
.hero-controls {
  bottom: 14px;
}
.hero-danmaku {
  position: absolute;
  z-index: 4;
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
.hero-danmaku span {
  animation: hero-danmaku 6s linear forwards;
  white-space: nowrap;
}
@keyframes hero-danmaku {
  from {
    transform: translateX(90%);
  }
  to {
    transform: translateX(-110%);
  }
}
.nivo-home.theater-mode .content-layout:not(.skeleton-layout) .right-rail,
.nivo-home.theater-mode .video-grid,
.nivo-home.theater-mode .feed-heading {
  display: none;
}
.nivo-home.theater-mode .content-layout:not(.skeleton-layout) .hero-card {
  grid-column: 1 / -1;
  width: 100%;
  max-height: calc(100vh - 140px);
  aspect-ratio: 16 / 9;
}
</style>
