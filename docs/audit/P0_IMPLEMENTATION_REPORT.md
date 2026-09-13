# P0 Implementation Report

状态：P0 修复批次已完成代码实现、本地回归和 Docker 两实例基础验收；依赖故障、认证业务竞争和压力边界仍未完成。

## P0-01：RTC legacy `call_signal`

- 修复前：`ChatWebSocketHandler` 可直接转发任意信令，并在 `call_reject`/`hangup` 时直接写通话消息。
- 根因：WebSocket handler 同时承担协议、状态和持久化职责，绕过 `CallService`。
- 修复：旧信令仅保留为经过 ACL 的通知提示；不再修改 RTC 状态、不再写通话记录。状态和记录只由 `CallService` 状态机及其投影完成。
- 变更：`ChatWebSocketHandler`、`WebSocketAuthorizationService`。
- 测试：不支持的 `hangup` 信令无任何副作用；合法 `call_request` 仅在参与者 ACL 通过后转发。

## P0-02：Redis 锁/幂等 fail-open

- 修复前：Redis 异常时 `tryAcquireIdempotent` 返回 `true`，`tryLock` 返回假 token。
- 修复：抛出 `RedisCoordinationUnavailableException`，调用方停止可能产生重复副作用的操作。
- 补充：Redis 限流脚本不可用时拒绝请求，不再无限制放行。
- 变更：`RedisCacheService`、`RedisCoordinationUnavailableException`。
- 测试：Redis SETNX 异常分别验证幂等和锁操作均失败关闭。

## P0-03：WebSocket ACL

- 修复前：目标用户、群组和通话参与者由 handler 分散判断或完全信任客户端字段。
- 修复：新增集中 `WebSocketAuthorizationService`，统一校验用户存在、群成员和 RTC 参与者/目标；Kafka consumer 的群成员校验继续保留为二次防线。
- 测试：未授权单聊、群消息和通话信令均被拒绝。

## P0-04：JWT secret

- 修复前：`application.yml` 带有共享默认 JWT secret。
- 修复：默认值改为空，`JwtUtil` 启动时强制要求有效 Base64 且至少 256 bit；缺失或弱密钥直接拒绝启动。
- 测试：空 secret 和 128 bit secret 均被拒绝。

## P0-06：WebSocket Origin

- 修复前：Origin 配置缺失时没有生产启动门禁。
- 修复：生产 profile 下 Origin 为空或 `*` 时启动失败；默认仅允许本地开发 Origin，正式域名必须通过 `WEBSOCKET_ALLOWED_ORIGINS` 注入。
- 测试：生产通配符 Origin 构造配置失败。

## 验证结果

```text
后端: mvn -q -DskipTests compile                         PASS
后端: mvn -q test                                       PASS
后端: P0 专项测试                                       PASS
前端: pnpm test                                          PASS (5 files, 30 tests)
前端: pnpm build                                         PASS
仓库: git diff --check                                   PASS
```

## 回滚和剩余风险

- 代码回滚：按 P0 文件批次回滚即可；没有数据库 migration。
- 配置回滚：恢复 `JWT_SECRET` 和 `WEBSOCKET_ALLOWED_ORIGINS` 环境变量，不应恢复代码内默认密钥。
- Docker 基础验收：`api-a`/`api-b` 均健康；停止 `api-b` 后 `api-a` 仍返回 HTTP 200；恢复 `api-b` 后两实例再次健康。镜像构建和启动期间发现并修复了多个 Spring Bean 条件/构造器装配缺口，记录见 `CONCURRENCY_VALIDATION.md`。
- 剩余风险：尚未在 Docker 中执行 Redis down、Kafka unavailable、DB 慢查询、连接池耗尽、带认证 RTC/WS 竞争和真实并发压测；这些不应宣称为生产级通过。

## P1-01 会话列表 N+1（已实现）

`MessageServiceImpl#getConversations` 原先对每个会话分别查询最后消息、未读数和用户资料，数据库请求量随会话数线性放大。现改为 `MessageMapper.selectConversationSummaries`：在最近 200 条消息窗口内使用窗口函数选出每个对话的最新消息，并在同一条 SQL 中聚合未读数；用户资料使用既有 `listByIds` 一次批量加载。`MessageServiceImplConversationTest` 验证批量查询契约和字段映射。
