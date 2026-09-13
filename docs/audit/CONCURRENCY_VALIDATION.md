# 并发与容灾验证记录

## 本地 Docker 基础验收（2026-08-22）

拓扑：`deploy/p0/docker-compose.yml` 启动两个无状态 Spring API 副本：

- `api-a`: `http://localhost:9191`
- `api-b`: `http://localhost:9192`
- 两副本共享外部 MySQL、Redis、Kafka；依赖地址通过未提交的 `deploy/p0/.env` 注入。

结果：

- Compose 配置静态校验通过。
- 镜像构建通过，两个实例均进入 `healthy`。
- `deploy/p0/acceptance.ps1 -BaseUrls http://localhost:9191,http://localhost:9192` 通过：两容器存在、健康、根 HTTP 请求无 5xx。
- 执行 `fault-injection.ps1 -Action stop-api-b` 后，`api-a` 仍返回 HTTP 200。
- 执行 `fault-injection.ps1 -Action restore-api-b` 后，`api-b` 恢复健康，双实例验收再次通过。

启动期间修复的装配问题：

- `WebSocketOutboundDispatcher`、`LiveProviderSessionService`、`LiveKitEgressHttpPort`、`P2pService`、`SrsCallbackController` 的生产构造器明确 `@Autowired`。
- `InMemoryStageStore`、`InMemoryP2pStateStore` 注册为本地默认 Bean。
- Kafka 封面消费者仅在 Kafka 和 MinIO 同时启用时创建，避免依赖条件不一致。
- P0 Compose 增加可注入的 MinIO 配置，并使用现有测试 MinIO 完成启动。

## 尚未验证

- Redis down / MySQL 延迟或连接池耗尽。
- 带真实 JWT 和测试账号的 RTC 创建、接听、超时、跨副本竞争。
- WebSocket 慢客户端队列、跨副本 fanout、ACL 业务接口。
- QPS、并发 `RINGING`、timeout lag、p99 延迟或 10k/100k/1m 容量。

以上项目在没有隔离依赖和可重复负载脚本前不得标记为通过。

## 本地 Kafka 故障恢复验收（2026-08-24）

拓扑：Windows Kafka 3.9.2 使用隔离日志目录，双 listener 分离主机与 Docker 网络：

- `LOCAL://localhost:9092`：broker 内部通信和主机验收工具。
- `DOCKER://host.docker.internal:29092`：两个 Spring API 容器。

结果：

- Kafka API version 协商和业务 topic 自动创建通过。
- `douyin-server` consumer group 在两个 API 实例间完成业务分区分配，恢复后 lag 为 0。
- 独立 `codex-acceptance` topic 的 produce/consume payload 往返通过。
- 强制停止 broker 后，`api-a:9193`、`api-b:9192` 仍返回 health HTTP 200。
- broker 重新监听耗时约 6.4 秒；consumer group 自动恢复，恢复后再次 produce/consume 往返通过。
- Docker acceptance 再次通过。

边界：这是单机单 broker 故障恢复，不证明 Kafka 三 broker HA、消息业务 E2E、跨地域恢复或生产 RTO/RPO。

## P1-10 直播控制面多实例验证（2026-08-24）

- `LiveStreamHandler` 的房间/session/user/presence 映射从 `static` 改为实例字段；这些集合只保存本机 socket，不再作为全局房间事实。
- 新增 `LiveRoomClusterBus`，使用独立 Redis channel `douyin:websocket:live-fanout:v1` 分发 chat、like、viewer-count 和 end 事件；订阅端只做本地投递，不回发，避免环路。
- 新增 `LiveHostPresenceService`：主播 socket 以 Redis ZSET TTL lease 表示在线，自动关播前通过 `setIfAbsent` 短锁和二次 lease 校验；Redis 不可用时自动关播暂停，不误杀有效直播。
- 契约测试覆盖：节点间房间事件投递、本地 socket 集合隔离、跨节点主播租约阻止误关播、end 事件双节点发送并关闭。
- 完整后端回归：372 tests，0 failures，0 errors。
- Docker 镜像重建并强制重建双 API 副本；`api-a`（9193）和 `api-b`（9192）均 healthy，共享 Redis 的集群配置启动正常。

边界：当前验证仍是单机 Docker + 单 Redis；未证明 Redis Sentinel/Cluster 故障转移、跨地域网络分区或大规模 WebSocket 连接容量。
