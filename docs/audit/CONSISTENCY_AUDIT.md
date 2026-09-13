# 一致性审计

## 事实模型

- 关系表是 like/collect/follow/message/call 的业务事实。
- 视频和用户计数、画像、推荐和 Redis 是 projection，不能成为唯一事实。
- RTC `rtc_call_session.state + state_version + event ledger` 是权威控制状态。

## P0/P1 风险

- 旧 WebSocket `call_signal` 可直接转发并写通话记录，可能绕过 `CallService` 的 ACL、幂等和状态转换。
- Redis `tryAcquireIdempotent` 和 `tryLock` 在 Redis 故障时 fail-open/返回假 token；呼叫 admission、关键幂等和分布式锁不应采用该策略。
- Follow、comment、notification 等关系和计数仍需逐项检查 affected-row 语义。
- Kafka VideoEvent 已有 eventId ledger，但 `VideoEvent` 缺少显式 aggregate version/occurred ordering，消费者无法通用丢弃旧版本。
- 用户画像 read-modify-write 在多实例仍可能 lost update；本地 striped queue 只能降低单实例竞态。

## 对账需求

建立定时对账：

`COUNT(t_like) vs t_video.like_count`、`COUNT(t_video_collect) vs collect_count`、`COUNT(t_comment) vs comment_count`、follow 双向计数、outbox/ledger 与 projection。差异写入 repair task，不在请求线程修复。
