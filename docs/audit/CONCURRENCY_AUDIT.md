# 并发审计

## 已确认高风险

- 点赞、收藏、分享和作者获赞原先是读改写；工作区当前已改为唯一键/受影响行数/SQL 原子递增，但仍需数据库并发集成测试验证计数对账。
- 观看历史原先先查再插/改，且前端多组件 5 秒心跳；工作区当前增加唯一键 upsert、15 秒心跳和 bounded profile queue。
- 会话列表 `getConversations()` 对每个会话重复查询最后消息、未读数和用户资料，明显 N+1。
- `CompletableFuture.runAsync` 使用公共 ForkJoinPool 发送通知，异常不可重放。
- AI 搜索摘要全局 `synchronized`，失败 sleep 阻塞请求线程，并对每个视频查询内容特征。
- `ContentFeatureService` 启动恢复曾全表加载、单线程、无限队列；即使增加本地 bounded queue，多实例 claim/lease 仍未解决。
- WebSocket 推送曾同步写；工作区新增每连接有界 outbound queue，但需要验证慢客户端、关闭和多实例 fanout。

## 线程池/连接池

- Spring Kafka、Tomcat、Redis Lettuce 有配置，但 HTTP 下游和 Python worker 的资源上限不统一。
- SRS client 已有连接/读取超时；其他外部调用需要逐一核对。
- 不能用扩大线程池掩盖数据库连接池或下游延迟。

## 必须补的压测

1. 100 个并发 like/unlike，同一用户/视频和不同视频两组。
2. 观看心跳 1k/10k RPS，检查 MySQL lock wait、连接池和画像队列。
3. 1k 个 WebSocket 慢客户端，验证队列满时只断开慢连接。
4. Kafka consumer 降速 10 倍，记录 lag、outbox backlog 和 API 延迟。
