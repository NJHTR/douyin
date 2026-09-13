# Feed/推荐审计

## 当前链路

推荐引擎串行执行多路召回，再从 MySQL hydrate 视频、内容和用户信息；结果由 VideoService 转成 VO。推荐缓存存在，但引擎使用和失效策略不统一。

## 风险

- 用户点赞、收藏、完播历史查询没有统一时间窗口和上限，用户历史增长会线性放大单次推荐成本。
- 多路召回可能重复查询内容特征和作者资料；需要 batch hydration 和耗时分段指标。
- 曝光记录仍在请求链路写入，滚动浏览会形成写入洪峰。
- following/recommended 使用固定 page/offset 或内存候选排序，不是 cursor feed。
- 缺少统一 seen/dedup、冷启动、降级和 prefetch 契约。

## 推荐演进

保持 MySQL 为事实，先做 bounded recall + batch hydration + Redis 短 TTL + single-flight；行为事件再逐步进入 Kafka。推荐失败必须 fallback 到有界热门 feed，而不是首页 500。
