# 审计实施进度

## Phase 1：审计

- [x] 扫描后端、前端、SQL、Docker、RTC、直播和部署目录。
- [x] 识别事实表、projection、cache、event、WS/RTC 实时链路。
- [x] 建立 P0/P1/P2/P3 issue register。
- [x] 记录本地编译、focused test、前端 build 的验证边界。
- [x] 后端 Maven 全量测试完成（退出码 0；包含预期的 SRS/超时/慢连接告警）。
- [x] Phase 1 审计快照收尾，未继续修改业务代码。
- [x] Phase 2 P0 修复批次完成：P0-01、P0-02、P0-03、P0-04、P0-06 已实现并通过回归测试；原 P0-05 互动计数修复仍保留为已实现未提交。
- [ ] 生产级压测和完整故障注入（当前本机 Docker 仅完成 API 副本停机恢复，详见 `CONCURRENCY_VALIDATION.md`）。

## 已执行验证（本轮）

```text
后端: mvn -q -DskipTests compile                         PASS
后端: mvn -q test                                       PASS (exit code 0)
后端: VideoServiceEngagementTest                         PASS
前端: pnpm test                                          PASS (6 files, 32 tests)
前端: pnpm build                                         PASS
仓库: git diff --check                                   PASS
```

2026-08-29 推荐/Docker 验收：

```text
Docker: docker compose up -d --build                     PASS (two API replicas healthy)
P0 smoke: deploy/p0/acceptance.ps1                       PASS
Recommendation: ordinary two-replica acceptance          PASS
Recommendation: real signals (-SeedBehaviorSignals)     PASS (A/B differed after completion/quick-skip)
Backend Surefire: 76 classes / 391 tests                 PASS (0 failures, 0 errors, 0 skipped)
```

本轮推荐验收使用当前库内约 17 条可推荐内容。普通模式验证了同一账号/会话的稳定分页、跨 API 副本共享候选池、HOT/FOLLOWING/FRIENDS/LONG_VIDEO 通道约束；真实完播和快速划过信号均成功写入，随后观察到 A/B HOME 顺序差异。该差异来自真实观看证据，验收脚本没有使用账号 ID、哈希、日期种子或随机排序输入。

此前两个账号经常得到相同的 `12345` 顺序，根因不只是内容数量少：当 24 小时曝光集合覆盖了约 17 条全部可推荐视频时，未曝光候选池为空，服务层会降级到全局 HOT，绕过画像排序。现在曝光池耗尽时会回收“已曝光但未完播”的内容，并重新经过证据驱动的画像/内容/社交排序；最近已完播内容仍保持排除。这样小目录仍能继续个性化排序，同时不会为了制造差异而强行打散等价证据下的结果。

前端构建仍有既有的 chunk 体积、circular chunk 及全量 `vue-tsc` 历史类型错误提示；它们未作为本轮通过标准，也未在 Phase 1 修改。

本轮交互静态修复后复验：前端 6 个测试文件、32 个测试通过；后端 `mvn -q test` 通过；生产构建和 `git diff --check` 通过。`pnpm type-check` 仍被仓库既有类型错误阻断。

## 工作区已有但未提交的实现

- 互动关系/计数原子 SQL、Kafka consumer affected-row 语义。
- 观看历史 upsert、客户端观看心跳节流、派生画像 bounded queue。
- WebSocket outbound bounded queue。
- RTC 多设备、TTL、reconciliation、三 VM 部署文件。

这些改动不等于 Phase 1 审计结论；进入下一阶段前应拆成小批次提交并逐项补集成/并发测试。

## Phase 2 验证边界

- [x] 后端编译、后端全量测试、P0 专项测试。
- [x] 前端测试（5 个文件、30 个测试）和生产构建。
- [x] `git diff --check`。
- [x] Docker 两 API 实例启动、健康检查、HTTP smoke 和单副本停机/恢复。
- [ ] Redis/Kafka/DB 主动故障注入、带认证 RTC/WS 竞争和真实并发压测。

## P1 修复进度

- [x] P1-01 会话列表 N+1：单条窗口/聚合查询返回最近消息和未读数，用户资料一次批量查询；已增加服务层查询契约测试。
- [x] P1-02 观看历史/画像并发写入：观看事实改为唯一键原子 upsert，进度/完播单调合并，重复观看仅按 session 变化计数；画像派生更新使用有界异步队列；已增加服务层和 SQL 契约测试。
- [x] P1-03 画像 read-modify-write 多实例丢更新：画像增量入口使用 `INSERT IGNORE` + `SELECT ... FOR UPDATE`，在事务内完成跨实例串行化；已完成编译和全量后端测试。
- [x] P1-04 ContentFeature worker lease/claim：数据库 CAS 抢占 `extract_status`，1 小时租约过期可接管；启动/定时恢复按 4096 条批次执行；内存队列上限 4096 且按 `videoId` 去重；新增恢复索引和 Worker 契约测试。
- [x] P1-05 通知线程池隔离：移除 Controller 对公共 `ForkJoinPool` 的依赖；Kafka 模式同步写 outbox，直连模式使用专用有界通知队列；新增派发器回归测试。
- [x] P1-06 推荐历史无界/N+1：点赞、收藏、曝光、完播历史改为 Mapper 轻量限量查询，单类行为最多加载 2000 条；候选辅助数据继续批量加载；新增查询上限契约测试。
- [x] P1-07a 视频观看历史 cursor：新增 `/api/video/history/cursor`，使用 `(update_time, id)` keyset 边界、`pageSize + 1` 判定 `hasMore`，保留旧 offset 接口；前端视频历史已切换 cursor；新增 `(user_id, update_time, id)` 索引迁移和契约测试。
- [x] P1-07b 影视综观看历史新增 `/api/video/historyOther/cursor`，由 SQL 联接 `t_video_content` 做分类过滤并使用 `(update_time,id)` keyset；前端第二页签已切换 cursor，保留旧 offset 接口兼容；新增 `migration_046_watch_history_category_index.sql`。
- [x] P1-08a/P1-09a 上传边界：新增短时 MinIO PUT 直传签发与 quarantine promote；旧 multipart/分片接口增加大小、扩展名、MIME、JWT 和用户隔离校验；分片合并限制总大小；新增有界 quarantine 清理和 `UploadPolicyTest`。
- [x] P1-08b 视频发布上传器优先使用 presigned PUT，签发/CORS/确认失败时自动回退到认证分片接口；旧分片业务失败响应不再被误判为成功。
- [x] 上传用户级限流：presign 20/min、multipart/music 30/min、merge/complete 30/min、chunk 600/min per user+uploadId；Redis 不可用时 fail-closed，新增限流契约测试。
- [ ] P1-08c 需完成浏览器真实 PUT、对象存储 CORS/HTTPS、失败回退和断点恢复端到端验收；当前直传是单 PUT，尚不支持对象存储 multipart 续传。

## Feed/推荐基础设施进度

- [x] 场景化通道：HOME、HOT、FOLLOWING、FRIENDS、LIVE、LONG_VIDEO、EXPERIENCE。
- [x] 真实信号驱动的召回、排序和重排；相同证据下保持相同排序，不按账号 ID/哈希/日期/随机数分流。
- [x] Redis 共享推荐池和跨副本 cursor/session 稳定性。
- [x] 客户端观看 telemetry 秒单位统一、暂停/切换/卸载结算和有界行为历史查询。
- [x] 推荐验收脚本支持真实完播/快速划过播种，并在证据不足时仅告警。
- [x] 用当前本地 Docker 的真实完播/快划样本验证个性化排序变化；已观察到 A/B HOME 差异。
- [ ] 增加更多已审核内容、内容特征和真实行为样本，继续验证特征新鲜度、候选池耗尽恢复和降级指标；本地 Docker 结果不代表生产规模。

P1-02/P1-03/P1-04/P1-07a/P1-08a/P1-09a 验证边界：当前已完成本地单元/契约测试、编译和 Docker 双 API 启动 smoke；尚未在隔离 MySQL/MinIO 上执行真实锁等待、连接池耗尽、租约接管延迟、深分页基准、预签名 PUT/断点恢复或 1k/10k RPS 压测。P1-07a 的新索引需在目标数据库执行 `migration_045_watch_history_cursor_index.sql`。

## 下一阶段顺序

1. 拆分并提交 P0 小批次；Docker API 副本验收已完成，补齐依赖故障和业务接口验收。
2. 封闭 P1：会话 N+1、cursor、content worker lease、上传 presigned/quarantine、外部超时。
3. 建立对账、事件 schema/version、trace/metrics 和故障演练。
4. 再做 Feed、客户端预加载和模块化重构。

## Phase 3：交互一致性专项

- [x] 将 `INTERACTION CONSISTENCY AUDIT` 加入主执行提示词。
- [x] 建立 Feature Parity、Page Interaction、UX Issue、User Journey、Interaction Consistency 五份文档。
- [x] 修复个人页商城入口复用 `/shop`。
- [x] 为群聊图片发送失败增加可见错误反馈。
- [x] 增加私聊/群聊共享 `MessageCapability` 契约，并处理群聊 upload/send 的失败响应。
- [ ] 统一首页/消息/添加好友的 User Search Contract 与 Search Scope。
- [ ] 完成私聊/群聊图片消息真实后端端到端契约验证（当前仅完成前端能力契约和失败反馈）。
- [ ] 完成 20 个核心功能的 2～3 入口动态旅程、返回/刷新/离线/重复点击回归。

## Phase 3 静态盘点更新（2026-08-22）

- [x] 完成 `_no`、路由字面量、用户可见 `catch` 和异步状态的源码级盘点。
- [x] 首页/消息侧栏钱包、观看历史、设置入口复用 canonical route；面对面页设置修复不存在路由。
- [x] 订单、钱包、支付、登录设备补齐 `success=false` 处理、Loading/Error/Retry 和重复提交门禁。
- [x] `/user/search` 登录态补齐关注/互关/好友/申请状态，添加好友页明确互关前提。
- [x] 请求错误统一保留 `code/msg/message/count`，HTTP 错误不再改写为成功。
- [ ] 登录态、真实后端 E2E、网络故障和跨入口人工验收按用户要求暂缓。
