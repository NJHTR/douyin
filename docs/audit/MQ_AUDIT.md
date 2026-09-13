# Kafka/MQ 审计

## 已有能力

- topic 有分区、consumer group、手动 ack、重试、DLQ、outbox relay、消费 ledger。
- RTC 事件按 call 聚合键路由，适合保持单 call 顺序。
- VideoEvent 已处理重复 like/unlike/collect/uncollect 的 affected-row 语义。

## 未闭环问题

- 通用事件没有统一 `eventId/aggregateId/version/occurredAt/producer/schemaVersion` 契约。
- Kafka producer `acks: 1` 不是强可靠配置；生产拓扑需确认 broker replication、`min.insync.replicas` 和 `acks=all`。
- topic 配置默认 replicas=1，只适合本地/验收，不能声称 HA。
- Kafka 关闭时 DirectMessagePublisher 对 video event 只记录 debug，行为画像/计数链路会改变语义。
- outbox 与业务事务的边界需逐个核对，尤其通知、聊天和视频行为。

## 验证

必须测：重复消息、乱序消息、consumer 重启、DLQ replay、broker down、lag 增长和 outbox backlog 恢复。
