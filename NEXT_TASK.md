# Next Task

1. [x] 使用 Docker Compose 启动两个后端实例并完成健康、HTTP smoke、单副本停机/恢复验收（Redis/Kafka/MySQL 使用既有外部实例）。
2. 注入 Redis down、Kafka unavailable、DB latency、WebSocket slow client，记录恢复和降级行为；必须在可破坏副本或隔离依赖上执行。
3. [x] 完成 P1-01 会话列表 N+1 批量查询改造和服务层回归测试。
4. [x] 完成 P1-02 至 P1-06 的观看、画像、Worker、通知和推荐历史治理。
5. [x] 完成 P1-07a 视频观看历史 cursor 分页；P1-07b 影视综历史也已改为数据库分类过滤 + cursor，旧 offset 接口保留兼容。
6. [x] P1-08a/P1-08b/P1-09a 已落地 presigned/quarantine、前端优先直传、兼容回退、上传策略、用户级 Redis 限流和旧分片安全边界。
7. 下一步：真实浏览器验证 MinIO CORS/HTTPS、直传失败回退、过期对象清理和跨实例确认；再决定是否增加对象存储 multipart 断点续传。

8. [x] 重建最新推荐镜像并启动双 API 副本（`9193`/`9192`）。
9. [x] 运行 `acceptance.ps1`、普通 `recommendation-acceptance.ps1` 和 `-SeedBehaviorSignals` 推荐验收；等价证据下 A/B 相同仅告警，不强制差异。
10. [x] 使用重建后的双 API 镜像运行普通推荐验收和 `-SeedBehaviorSignals`；真实完播/快划后已观察到基于证据的 A/B HOME 差异。
11. 下一步：增加足够的已审核视频、内容特征和真实观看/跳过样本，观察画像更新、候选池变化和排序指标；不得用账号分桶或随机数替代证据。

12. 并行执行交互一致性专项：继续处理关系状态传播和消息端到端联调；用户搜索 scope、商城入口、图片能力契约已完成静态修复，按 `docs/audit/USER_JOURNEY_TEST_REPORT.md` 运行动态旅程。

当前不能宣称：10k/100k/1m 并发、跨地域一致性或生产 HA 已验证。
交互专项当前也不能宣称完成：浏览器/API 联调、网络失败、返回/刷新和跨入口回归尚未全部执行。

本轮静态盘点已完成并修复核心入口、请求错误契约、搜索关系状态、订单/钱包/支付/设备管理状态机。下一步由人工登录验收 20 个核心功能的多入口、返回、刷新、重复点击和网络失败路径。
