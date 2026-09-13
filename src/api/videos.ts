import { request } from '@/utils/request'
import { getBrowsingSessionId } from '@/utils/session'
import type { RecommendationChannel } from '@/utils/recommendation'

export function historyOther(params?: any, data?: any) {
  return request({ url: '/video/historyOther', method: 'get', params, data })
}

export function historyOtherCursor(params?: { cursor?: string | null; pageSize?: number }) {
  return request({ url: '/video/historyOther/cursor', method: 'get', params })
}

export function historyVideo(params?: any, data?: any) {
  return request({ url: '/video/history', method: 'get', params, data })
}

export function historyVideoCursor(params?: { cursor?: string | null; pageSize?: number }) {
  return request({ url: '/video/history/cursor', method: 'get', params })
}

export interface RecommendationRequest {
  start?: number
  pageSize?: number
  cursor?: string | null
  feedMode?: RecommendationChannel
  /** Kept as an alias while older server instances are being upgraded. */
  channel?: RecommendationChannel
  strategy?: RecommendationChannel
}

function recommendationParams(
  params: RecommendationRequest | undefined,
  defaultMode: RecommendationChannel
) {
  const feedMode = params?.feedMode || params?.channel || params?.strategy || defaultMode
  return {
    ...params,
    feedMode,
    // A page/session marker prevents an intermediary from serving one user's
    // first page to another user when GET response caching is misconfigured.
    client_session_id: getBrowsingSessionId()
  }
}

export function recommendedVideo(params?: RecommendationRequest, data?: any) {
  return request({
    url: '/video/recommended',
    method: 'get',
    params: recommendationParams(params, 'HOME'),
    headers: { 'Cache-Control': 'no-cache', Pragma: 'no-cache' },
    data
  })
}

export function recommendedLongVideo(params?: RecommendationRequest, data?: any) {
  return request({
    url: '/video/long/recommended/',
    method: 'get',
    params: recommendationParams(params, 'LONG_VIDEO'),
    headers: { 'Cache-Control': 'no-cache', Pragma: 'no-cache' },
    data
  })
}

export function followingVideos(params?: any, data?: any) {
  return request({
    url: '/video/following',
    method: 'get',
    params: { ...params, feedMode: 'FOLLOWING', client_session_id: getBrowsingSessionId() },
    headers: { 'Cache-Control': 'no-cache', Pragma: 'no-cache' },
    data
  })
}

export function trendingVideos(params?: any, data?: any) {
  return request({
    url: '/video/trending',
    method: 'get',
    params: { ...params, feedMode: 'HOT', client_session_id: getBrowsingSessionId() },
    headers: { 'Cache-Control': 'no-cache', Pragma: 'no-cache' },
    data
  })
}

export function myVideo(params?: any, data?: any) {
  return request({ url: '/video/my', method: 'get', params, data })
}

export function privateVideo(params?: any, data?: any) {
  return request({ url: '/video/private', method: 'get', params, data })
}

export function likeVideo(params?: any, data?: any) {
  return request({ url: '/video/like', method: 'get', params, data })
}

export function videoComments(params?: any, data?: any) {
  return request({ url: '/video/comments', method: 'get', params, data })
}

export function publishVideo(data: {
  video_url: string
  cover_url?: string
  desc?: string
  duration?: number
  music_title?: string
  music_id?: number
  bgm_volume?: number
  bgm_start_offset?: number
  trim_start?: number
  trim_end?: number
  segments?: string
  type?: string
  image_urls?: string
}) {
  return request({ url: '/video', method: 'post', data })
}

export function toggleVideoLike(videoId: number | string) {
  return request({ url: `/video/like/${videoId}`, method: 'post' })
}

export function recordShare(videoId: number | string) {
  return request({ url: `/video/share/${videoId}`, method: 'post' })
}

export function postComment(data: {
  video_id: string
  content: string
  parent_id?: string
  reply_to_user_id?: string
}) {
  return request({ url: '/video/comments', method: 'post', data })
}

export function toggleCommentLike(commentId: string) {
  return request({ url: `/video/comment/like/${commentId}`, method: 'post' })
}

export function getCommentReplies(commentId: string) {
  return request({ url: `/video/comment/replies/${commentId}`, method: 'get' })
}

export function toggleCollect(videoId: number | string) {
  return request({ url: `/video/collect/${videoId}`, method: 'post' })
}

export function searchVideos(keyword: string) {
  return request({ url: '/video/search', method: 'get', params: { keyword } })
}

export function recordWatch(
  videoId: string,
  data: {
    watch_duration: number
    video_duration: number
    finished: boolean
    session_id?: string
    swipe_seconds?: number
    traffic_source?: string
    last_position?: number
    profile_sample?: boolean
  }
) {
  return request({ url: `/video/watch/${videoId}`, method: 'post', data })
}

/** 获取视频的上次播放位置，用于断点续播 */
export function getLastPosition(videoId: string) {
  return request({ url: `/video/watch/position/${videoId}`, method: 'get' })
}

/** 搜索输入联想 */
export function searchSuggestions(params: { q: string; video_id?: number; userId?: number }) {
  return request({ url: '/search/suggestions', method: 'get', params })
}

/** 搜索页"猜你想搜" */
export function getGuessYouWant() {
  return request({ url: '/search/guess', method: 'get' })
}

/** 搜索热榜 */
export function getHotRank(params?: { limit?: number }) {
  return request({ url: '/search/hot-rank', method: 'get', params })
}

/** 视频页"猜你想搜" */
export function videoSearchHints(videoId: number, params?: any) {
  return request({ url: `/video/${videoId}/search-hints`, method: 'get', params })
}

/** AI 智能搜索总结 */
export function searchSummary(keyword: string) {
  return request({ url: '/search/summary', method: 'get', params: { keyword } })
}
