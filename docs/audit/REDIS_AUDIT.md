# Redis 审计

## 当前用途

缓存视频/用户、推荐 feed、限流、幂等、分布式锁、RTC timeout/session lookup、presence、WebSocket Pub/Sub。

## 风险

1. `deleteByPattern` 把全量 SCAN 结果收集到内存，key 数量大时形成 big key/内存峰值。
2. 限流脚本是固定窗口而非滑动窗口；Redis 故障时放行，核心呼叫 admission 不安全。
3. 幂等只保存占位值，不保存响应摘要/最终结果，重复请求无法重放同一响应。
4. 锁失败返回随机假 token，调用方会误以为拿到锁。
5. 推荐缓存没有统一 single-flight/stale-while-revalidate，热点过期可能击穿 MySQL。
6. Redis Pub/Sub 是易失消息，只能做 fanout，不能作为通话或消息事实。

## 结论

缓存、热词等非关键场景可 fail-open；呼叫创建、支付、幂等、状态迁移必须 fail-closed 或回退数据库 CAS。需要 Redis 命令耗时、命中率、key cardinality、slowlog 和连接池指标。
