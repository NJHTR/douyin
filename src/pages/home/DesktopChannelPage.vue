<template>
  <main class="channel-page">
    <div class="channel-heading">
      <div>
        <span class="eyebrow">抖音桌面端</span>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <button type="button" @click="router.push('/publish')">
        <Icon icon="solar:add-square-linear" /> 投稿
      </button>
    </div>
    <div class="channel-tabs">
      <button
        v-for="tab in tabs"
        :key="tab"
        type="button"
        :class="{ active: tab === activeTab }"
        @click="activeTab = tab"
      >
        {{ tab }}
      </button>
    </div>
    <section v-if="cards.length" class="channel-grid">
      <article
        v-for="item in cards"
        :key="item.id"
        class="channel-card"
        @click="router.push({ path: '/video-detail', query: { id: item.id } })"
      >
        <div class="cover" :style="{ '--media-bg': `url(${item.cover})` }">
          <img :src="item.cover" :alt="item.title" /><span>{{ item.duration }}</span
          ><button
            type="button"
            aria-label="播放"
            @click.stop="router.push({ path: '/video-detail', query: { id: item.id } })"
          >
            <Icon icon="solar:play-bold" />
          </button>
        </div>
        <h2>{{ item.title }}</h2>
        <p>@{{ item.author }} · {{ item.views }}次观看</p>
      </article>
    </section>
    <div v-else class="channel-empty">
      {{ route.path === '/series' ? '短剧内容正在建设中' : '暂无内容' }}
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import poster1 from '@/assets/img/poster/1.jpg'
import poster3 from '@/assets/img/poster/3.jpg'
import music8 from '@/assets/img/music-cover/8.jpg'
import music12 from '@/assets/img/music-cover/12.jpg'
import {
  recommendedVideo,
  recommendedLongVideo,
  followingVideos,
  trendingVideos
} from '@/api/videos'
import { _checkImgUrl, _formatNumber } from '@/utils'
import { videoDurationSeconds } from '@/utils/recommendationTelemetry'
const route = useRoute()
const router = useRouter()
const activeTab = ref('全部')
const configs: Record<string, { title: string; description: string }> = {
  '/aisearch': { title: 'AI抖音', description: '用 AI 发现灵感，探索更多有趣内容' },
  '/follow': { title: '关注', description: '看看你关注的创作者正在分享什么' },
  '/friend': { title: '朋友', description: '朋友的动态和正在观看的内容' },
  '/vs': { title: '放映厅', description: '精选长视频与电影内容，一起看更精彩的作品' },
  '/series': { title: '短剧', description: '热门短剧连播，追更每一集精彩剧情' },
  '/microgame': { title: '小游戏', description: '随时打开即玩，发现更多轻松有趣的小游戏' }
}
const config = computed(() => configs[route.path] || configs['/follow'])
const title = computed(() => config.value.title)
const description = computed(() => config.value.description)
const tabs = ['全部', '热门', '最新', '推荐', '音乐', '知识', '生活']
const cards = ref<any[]>([])
const fallback = [
  {
    id: 'channel-1',
    title: '把复杂的生活，过成喜欢的样子',
    author: 'NIVO 日常研究所',
    views: '12.8万',
    duration: '03:42',
    cover: poster1
  },
  {
    id: 'channel-2',
    title: '今晚的城市，适合慢一点走',
    author: '林间放映室',
    views: '8.4万',
    duration: '05:18',
    cover: poster3
  },
  {
    id: 'channel-3',
    title: '一首歌的时间，听见春天',
    author: 'Mori Music',
    views: '6.1万',
    duration: '04:06',
    cover: music8
  },
  {
    id: 'channel-4',
    title: '三分钟看懂一个新知识',
    author: '开眼知识局',
    views: '21.7万',
    duration: '03:00',
    cover: music12
  }
]
function mapCard(item: any, index: number) {
  const cover =
    item?.video?.cover?.url_list?.[0] ||
    item?.video?.origin_cover?.url_list?.[0] ||
    item?.cover_url ||
    fallback[index % fallback.length].cover
  const seconds = videoDurationSeconds(item)
  return {
    id: String(item.aweme_id ?? item.id),
    title: item.desc || item.title || '推荐视频',
    author: item.author?.nickname || '抖音用户',
    views: _formatNumber(item.statistics?.play_count || 0),
    duration: seconds
      ? `${Math.floor(seconds / 60)
          .toString()
          .padStart(2, '0')}:${Math.floor(seconds % 60)
          .toString()
          .padStart(2, '0')}`
      : '03:42',
    cover: _checkImgUrl(cover)
  }
}
async function loadCards() {
  if (route.path === '/series') {
    cards.value = []
    return
  }
  let res
  if (route.path === '/vs') res = await recommendedLongVideo({ start: 0, pageSize: 12 })
  else if (route.path === '/follow') res = await followingVideos({ start: 0, pageSize: 12 })
  else if (route.path === '/friend')
    res = await recommendedVideo({ start: 0, pageSize: 12, feedMode: 'FRIENDS' })
  else res = await recommendedVideo({ start: 0, pageSize: 12, feedMode: 'EXPERIENCE' })
  if (res.success) {
    const list = Array.isArray(res.data) ? res.data : res.data?.list || []
    cards.value = list.map(mapCard)
    if (!cards.value.length) cards.value = fallback
  } else cards.value = fallback
}
onMounted(loadCards)
watch(() => route.path, loadCards)
</script>

<style scoped lang="less">
.channel-page {
  max-width: 1500px;
  margin: 0 auto;
  padding: 34px 32px 60px;
  color: #f4f4f7;
}
.channel-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}
.eyebrow {
  color: #ff4a6d;
  font-size: 12px;
  font-weight: 700;
}
.channel-heading h1 {
  margin: 8px 0 5px;
  font-size: 30px;
}
.channel-heading p {
  margin: 0;
  color: #9296a6;
  font-size: 14px;
}
.channel-heading > button {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 15px;
  border: 0;
  border-radius: 9px;
  color: #fff;
  background: #ff315b;
  font-weight: 700;
  cursor: pointer;
}
.channel-tabs {
  display: flex;
  gap: 27px;
  margin-top: 30px;
  border-bottom: 1px solid #292b37;
}
.channel-tabs button {
  position: relative;
  height: 45px;
  padding: 0;
  border: 0;
  color: #979aa8;
  background: transparent;
  cursor: pointer;
}
.channel-tabs button.active {
  color: #fff;
  font-weight: 700;
}
.channel-tabs button.active:after {
  position: absolute;
  right: 50%;
  bottom: -1px;
  width: 22px;
  height: 3px;
  transform: translateX(50%);
  border-radius: 3px;
  background: #ff315b;
  content: '';
}
.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 26px 18px;
  margin-top: 24px;
}
.channel-card {
  min-width: 0;
  cursor: pointer;
}
.cover {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  border-radius: 9px;
  background: #242631;
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.25s;
}
.channel-card:hover .cover img {
  transform: scale(1.04);
}
.cover span {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 3px 5px;
  border-radius: 4px;
  color: #fff;
  background: #0009;
  font-size: 11px;
}
.cover button {
  position: absolute;
  left: 10px;
  bottom: 10px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  color: #fff;
  background: #ff315b;
  opacity: 0;
  cursor: pointer;
  transition: opacity 0.2s;
}
.channel-card:hover .cover button {
  opacity: 1;
}
.channel-card h2 {
  overflow: hidden;
  margin: 10px 0 5px;
  color: #f2f3f6;
  font-size: 15px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.channel-card p {
  margin: 0;
  color: #858998;
  font-size: 12px;
}
</style>

<style scoped lang="less">
.cover::before {
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
.cover img {
  position: relative;
  z-index: 1;
  object-fit: contain;
}
.cover span,
.cover button {
  z-index: 2;
}
</style>

<style scoped lang="less">
.channel-page {
  width: 100%;
  max-width: none;
  box-sizing: border-box;
}
.channel-empty {
  display: grid;
  place-items: center;
  min-height: 340px;
  color: #8f94a4;
}
</style>
