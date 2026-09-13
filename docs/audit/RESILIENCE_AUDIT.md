# 容灾与恢复审计

## 已有恢复机制

- RTC TTL worker、timeout index fallback、reconciliation、event ledger 和 history retention 已有 focused tests。
- Kafka outbox stale processing、retry、DLQ、ledger purge 已配置。
- Redis Pub/Sub 丢失后，WS/RTC reconnect 会通过持久化状态 reconciliation。

## 尚未证明

- Redis 全部不可用时，呼叫限流、幂等、锁、presence 和 timeout 的 fail-closed/fallback 行为。
- Kafka broker down、DB 慢查询/锁表、连接池耗尽时 API 是否有界降级。
- SRS/LiveKit/TURN 节点断开、跨节点网络分区和重复 webhook。
- MinIO 不可用、上传中断、转码进程被杀后的任务恢复。

## 演练要求

每次演练记录故障注入、检测时间、错误率、恢复时间、重复事件数、数据对账结果和是否需要人工修复。
