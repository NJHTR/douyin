# 可观测性审计

## 已有

- Micrometer `MeterRegistry` 有本地 no-op/simple fallback。
- RTC 有容量、QOE、timeout、ledger、Kafka lag 相关指标和结构化日志。
- Kafka outbox/consumer 有 retry、DLQ、ledger 日志。

## 缺口

- 没有统一 traceId/requestId 在 HTTP -> DB -> Redis -> Kafka -> WS -> RTC 之间贯通的证据。
- 普通视频/消息/推荐接口缺少 p50/p95/p99、DB query、Redis hit、队列深度和降级计数。
- WebSocket outbound queue 需要 active connections、queue depth、overflow、disconnect reason、delivery latency。
- Live media 需要 join success、first frame、TTFB、rebuffer、RTT/jitter/loss/bitrate。
- 日志需审计 `Authorization`、token、密码、URL query secret 等敏感字段脱敏。
