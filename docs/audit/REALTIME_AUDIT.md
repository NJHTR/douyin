# WebSocket/WebRTC/Presence 审计

## WebSocket

- `SessionManager` 支持用户多设备和 Redis fanout；工作区已增加每连接 bounded outbound queue，慢连接应断开而不阻塞 Kafka/HTTP 线程。
- Redis Pub/Sub 丢消息后依赖 reconnect reconciliation，符合“fanout 非事实”原则。
- `ChatWebSocketHandler` 每条 call_signal 曾重复创建 ObjectMapper，并信任客户端目标 ID；群消息成员 ACL、消息大小、频率、schema 需集中校验。
- `LiveStreamHandler` 仅保留本机 `WebSocketSession` 引用；房间 chat/like/viewer-count/end 通过独立 Redis Pub/Sub channel 扇出，主播断线宽限期由 Redis TTL lease 和短锁保护。Redis/MySQL 仍是在线投影与房间状态事实来源。
- Dashboard 每 5 秒同步查库并广播，实例数增大后会重复打 DB。

## RTC

- CallService 已有显式 CallState/ParticipantState machine、version、event ledger、device lifecycle、TTL 和 reconciliation。
- 已覆盖 ringing、offline retain、multi-device、late accept、timeout、cross-node LiveKit 等 focused 测试/手工流程。
- 仍需压测 10/100/1k 并发创建、accept/end，以及 WebSocket 断线、LiveKit webhook 重复/乱序、Redis timeout index 不可用。

## Presence

Presence 应按 `(user,device,connection,gateway)` TTL/heartbeat 建模；数据库历史只能作为审计，不能当永久在线状态。
