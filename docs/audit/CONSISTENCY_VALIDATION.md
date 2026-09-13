# Consistency Validation

本轮验证 RTC WebSocket 旧信令不再产生状态副作用，Redis 锁/幂等在依赖故障时 fail-closed。

已通过：

- `hangup` 等未授权 legacy 信令不调用状态/消息副作用。
- 合法通知必须先通过 call participant/target ACL。
- Redis SETNX 异常抛出协调不可用异常，不返回假成功。

未覆盖：Docker 多实例并发 accept/timeout、Redis 故障恢复窗口和 Kafka 重排压测。
