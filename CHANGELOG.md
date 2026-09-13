# Changelog

## 2026-08-25

- Feed/推荐基础设施收口：HOME、HOT、FOLLOWING、FRIENDS、LIVE、LONG_VIDEO、EXPERIENCE 使用明确的场景通道；候选召回、排序和重排保持全局稳定 tie-break，并通过 Redis 共享推荐池保证跨 API 副本的会话稳定性。
- 推荐个性化只使用真实行为、画像、内容、社交和曝光证据；移除按用户 ID、哈希、日期或随机数强行制造差异的路径。等价证据下不同账号得到相同排序是合法结果。
- 协同召回、探索召回、观看/点赞/收藏/曝光历史和有限排序补齐稳定顺序与上限；多样性重排改为延后候选而不是直接丢弃候选。
- 前端观看 telemetry 统一秒单位，兼容 API 顶层毫秒时长和旧嵌套秒字段；观看信号在暂停、切换和卸载时补偿结算。
- 新增 `deploy/p0/recommendation-acceptance.ps1` 真实行为验收路径：可记录自然完播与快速划走信号，差异不足时只告警，不伪造 A/B 差异。
- 本地 Docker 双 API 重建后健康、普通推荐验收和真实信号验收均通过；当前约 17 条可推荐内容下 A/B 排序仍可能相同，不能据此宣称推荐质量或大规模容量已验证。

## 2026-08-23

- P1-07a：新增视频观看历史 cursor 接口，按 `(update_time, id)` 稳定 keyset 分页并使用 `pageSize + 1` 生成 `hasMore`，保留旧 offset 接口兼容。
- P1-07a：前端观看历史的视频页签切换为 opaque cursor；新增 `(user_id, update_time, id)` 索引迁移与契约测试。影视综页签仍保留旧分页，未宣称整个 P1-07 完成。
- P1-08a/P1-09a：新增 MinIO presigned PUT 和 quarantine promote；统一上传大小、扩展名、MIME、媒体魔数校验；旧分片接口增加 JWT、用户隔离、分片/总大小上限，并增加有界隔离对象清理。前端视频大文件直传迁移和真实 MinIO E2E 仍待完成。
- P1-08b：视频发布上传器优先使用 presigned PUT，失败时回退到认证分片接口；修复旧分片上传未检查业务失败响应的问题。真实 MinIO CORS/HTTPS 和断点续传仍待验收。
- 上传会话边界：移除 upload/music 路径的 SessionFilter 白名单绕过，音乐上传补充 JWT 校验；公开音乐查询仍可匿名访问。
- P1-07b：影视综观看历史新增数据库侧分类过滤和 `/api/video/historyOther/cursor` keyset 接口；前端第二页签切换 cursor；新增分类索引迁移 `migration_046_watch_history_category_index.sql`，旧 offset 接口继续兼容。
- 上传限流：presign、multipart/music、chunk、merge/complete 增加用户级 Redis 固定窗口门禁，Redis 故障 fail-closed 并返回 `429`；新增上传限流契约测试。

## 2026-08-24

- 完成本地 Kafka unavailable/recovery 演练：Windows host 与 Docker 使用双 listener，两个 API 在 broker 停止期间保持健康，broker 恢复后 consumer group 自动重分配且 lag 为 0，恢复前后消息往返均通过。
- 增加 `deploy/p0/kafka-local.properties.example`，记录可迁移的 Windows host + Docker Kafka 网络配置。

## 2026-08-22

- P0：隔离 legacy RTC `call_signal`，状态和通话记录统一由 RTC 状态机处理。
- P0：Redis 锁和幂等 Redis 故障改为 fail-closed。
- P0：新增集中 WebSocket 单聊、群聊和 RTC 参与者 ACL。
- P0：移除代码内 JWT 默认密钥，增加 Base64/256-bit 启动校验。
- P0：生产 WebSocket Origin 增加显式配置和通配符启动门禁。
- 增加对应单元、权限、故障和回归测试。
- 新增全项目交互一致性专项：Feature Parity、页面交互、用户旅程、问题台账和一致性报告。
- 修复个人页“SeekFlow商城”入口无响应，统一跳转 canonical `/shop`。
- 群聊图片消息失败时增加用户可见提示和错误日志，避免静默失败。
- 消息加号“添加朋友”新增 `/message/add-friend` 路由，复用全站 `/user/search`，补齐 Loading/Empty/Error/Retry/提交态。
- 私聊与群聊新增共享 `MessageCapability` 契约；群聊图片上传或发送失败响应不再静默结束。
- 静态修复首页/消息侧栏钱包、历史、设置入口及面对面设置断链；订单、钱包、支付、登录设备补齐错误/重试/提交态；用户搜索返回关系状态并明确好友申请互关前提。
- 统一请求层保留 `code/msg/message/count`，HTTP 错误不再被错误归一为成功；未实现次要入口统一提示“该功能即将上线”。
- P1-02：观看历史使用唯一键原子 upsert，旧心跳不能回退观看进度/位置，重复观看只在 session 变化时递增；画像派生更新继续走有界异步队列。
- P1-03：用户画像增量更新增加 MySQL 行锁边界（`INSERT IGNORE` + `SELECT ... FOR UPDATE`），跨 API 实例串行化 read-modify-write。
- P1-04：内容特征 Worker 增加数据库 CAS claim 和 1 小时租约接管，启动/定时恢复限制批次；内存队列改为 4096 容量并按视频 ID 去重，新增恢复索引和契约测试。
- P1-05：移除关注/互动通知对公共 `ForkJoinPool` 的依赖；Kafka 模式同步写 outbox 保证失败可见，直连模式使用专用有界通知派发器。
- P1-06：推荐引擎的点赞、收藏、曝光和完播历史改为轻量、限量 Mapper 查询，单类行为最多加载 2000 条，限制老用户 Feed 查询放大。
