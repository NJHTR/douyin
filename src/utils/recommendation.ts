/**
 * Shared feed vocabulary used by the client and the recommendation API.
 *
 * Keeping this mapping at the edge prevents a channel tab from becoming a
 * purely visual control.  The server may add or replace ranking strategies
 * without requiring every page to invent its own parameter names.
 */
export type RecommendationChannel =
  | 'HOME'
  | 'HOT'
  | 'FOLLOWING'
  | 'FRIENDS'
  | 'LIVE'
  | 'LONG_VIDEO'
  | 'EXPERIENCE'

const CHANNEL_BY_LABEL: Record<string, RecommendationChannel> = {
  全部: 'HOME',
  公开课: 'EXPERIENCE',
  游戏: 'HOME',
  二次元: 'HOME',
  音乐: 'HOME',
  影视: 'EXPERIENCE',
  美食: 'HOME',
  知识: 'EXPERIENCE',
  小剧场: 'EXPERIENCE',
  '生活 vlog': 'HOME',
  体育: 'HOT',
  旅行: 'EXPERIENCE',
  亲子: 'EXPERIENCE',
  动物: 'HOT',
  三农: 'EXPERIENCE',
  汽车: 'HOT'
}

/** Resolve a visible channel label into the stable API channel contract. */
export function recommendationChannelForLabel(label: string): RecommendationChannel {
  return CHANNEL_BY_LABEL[label] || 'HOME'
}

/**
 * Map a virtual mobile list id to the traffic source stored with watch
 * history.  The value is deliberately bounded and matches the server's
 * profile-source vocabulary.
 */
export function trafficSourceForFeed(uniqueId: unknown): string {
  const value = String(uniqueId || '').toLowerCase()
  if (value.includes('follow')) return 'FOLLOWING'
  if (value.includes('friend')) return 'FRIENDS'
  if (value.includes('hot') || value.includes('trend')) return 'HOT'
  if (value.includes('live')) return 'LIVE'
  if (value.includes('long')) return 'LONG_VIDEO'
  if (value.includes('experience') || value.includes('discover')) return 'EXPERIENCE'
  return 'HOME_RECOMMEND'
}
