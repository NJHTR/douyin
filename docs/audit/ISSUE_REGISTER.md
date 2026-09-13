# Issue Register

状态：Phase 1 审计快照。`Open` 表示未修复；`Implemented-uncommitted` 表示工作区已有改动但尚未形成审计阶段提交。

## P0

| ID | 问题 | 根因/证据 | 状态 |
|---|---|---|---|
| P0-01 | 旧 call_signal 绕过 RTC 状态机 | ChatWebSocketHandler 直接转发并写通话记录 | Implemented-uncommitted |
| P0-02 | Redis 锁/幂等故障时 fail-open | RedisCacheService.tryLock/tryAcquireIdempotent 返回假成功 | Implemented-uncommitted |
| P0-03 | WebSocket 目标/群 ACL 不集中 | 客户端提供 to_user_ids/group_id，handler 未统一成员校验 | Implemented-uncommitted |
| P0-04 | 生产默认密钥风险 | application.yml 开发默认 secret，配置可被误带入生产 | Implemented-uncommitted |
| P0-05 | 关系事实与计数读改写竞态 | like/collect/share/author counter 原实现 | Implemented-uncommitted |
| P0-06 | WebSocket Origin 未按环境收紧 | origin 配置缺失/通配符时缺少生产启动门禁 | Implemented-uncommitted |

## P1

| ID | 问题 | 影响 | 状态 |
|---|---|---|---|
| P1-01 | 会话列表 N+1 | 消息量/会话数放大 DB QPS | Implemented-uncommitted：批量汇总查询 |
| P1-02 | 观看历史 read-before-write + 高频心跳 | 写洪峰、唯一键竞争 | Implemented-uncommitted：原子 upsert、单调进度、session-aware repeat、bounded profile queue |
| P1-03 | 画像 read-modify-write 多实例丢更新 | 推荐特征不稳定 | Implemented-uncommitted：事务内 `INSERT IGNORE` + `SELECT ... FOR UPDATE` |
| P1-04 | ContentFeature 全量恢复、无 lease/claim | 启动慢、重复处理、无法横向扩展 | Implemented-uncommitted：CAS claim、租约接管、有界恢复/队列 |
| P1-05 | 公共 ForkJoinPool 发通知 | 线程池互相影响、失败不可重放 | Implemented-uncommitted：Kafka 同步 outbox、直连专用有界队列 |
| P1-06 | 推荐历史无界/N+1 | Feed 延迟随用户历史线性增长 | Implemented-uncommitted：行为历史轻量查询并限制 2000 条 |
| P1-07 | 深 offset/固定 limit | Implemented-uncommitted：视频和影视综观看历史均使用 `(update_time,id)` cursor；影视综分类在数据库侧联接过滤 |
| P1-08 | 上传经 API 传大文件 | Partially implemented：前端优先 presigned PUT + quarantine promote，旧分片仅兼容回退；用户级 Redis 限流已补；真实 CORS/HTTPS 与断点续传 E2E 待验收 |
| P1-09 | 上传校验/隔离不足 | Implemented-uncommitted：统一大小/扩展名/MIME/魔数校验，JWT、用户隔离、分片/合并上限和 quarantine 清理 |
| P1-10 | 直播房间 JVM static 状态 | 多实例状态不一致 | Implemented-uncommitted：socket 注册表改为实例本地；房间控制事件 Redis Pub/Sub 扇出；主播 Redis 租约 + 自动关播锁；跨节点契约测试 |
| P1-11 | Redis cache stampede/big pattern delete | 热点过期打爆 DB，删除内存峰值 |
| P1-12 | Kafka replicas=1/acks=1 基线 | 本地配置不具备 HA 证明 |
| P1-13 | 外部调用超时/熔断不统一 | 下游故障拖垮请求线程 |
| P1-14 | 计数/评论实时 COUNT | 热门内容热点查询 |
| P1-15 | 缺少统一事件 version/ordering | 乱序事件无法通用丢弃 |

## P2

| ID | 问题 |
|---|---|
| P2-01 | 客户端无统一 Wi-Fi/蜂窝/低电量预加载策略 |
| P2-02 | WebSocket 重连退避、队列和恢复指标不完整 |
| P2-03 | AI 摘要全局锁、sleep 和内容 N+1 |
| P2-04 | Dashboard 5 秒同步查询/广播 |
| P2-05 | 推荐失败 fallback、single-flight、stale refresh 未统一 |
| P2-06 | 日志/traceId/指标在普通业务链路不完整 |
| P2-07 | 观看事件无离线批量/sendBeacon/eventId |
| P2-08 | 评论/通知列表 cursor 和 projection 不统一 |

## P3

| ID | 问题 |
|---|---|
| P3-01 | Controller 编排过宽 |
| P3-02 | ContentFeatureService 职责过多 |
| P3-03 | Handler 同时做协议、权限、持久化、推送 |
| P3-04 | 领域间共享 Mapper/Entity，数据所有权不清晰 |
| P3-05 | 缺少 ADR、模块契约和统一 API error code |

## Top 20 立即修复

`P0-01, P0-02, P0-03, P0-04, P0-06, P1-01, P1-02, P1-03, P1-04, P1-05, P1-06, P1-07, P1-08, P1-09, P1-10, P1-11, P1-12, P1-13, P1-14, P1-15`。

## 验收字段

每个 issue 进入修复阶段时必须补：owner、变更文件、迁移、feature flag、回滚方案、单测、集成测、压测、故障注入和对账结果。
