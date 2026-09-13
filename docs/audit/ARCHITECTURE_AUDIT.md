# 架构审计

## 结论

项目适合继续保持模块化单体。RTC 控制面已经形成较清晰的 domain/state machine/repository 边界；视频、社交、消息、推荐仍以共享 Service/Mapper 互相调用，存在边界泄漏，但没有足够证据证明现在就应该拆微服务。

## 优点

- RTC 有显式状态机、事件账本、outbox、TTL/reconciliation 和多设备模型。
- Kafka 具备 outbox、重试、DLQ、消费 ledger 和 lag probe 配置。
- LiveKit/SRS/TURN 已从 API 节点拆出部署角色。
- MySQL 关系表保留了可重建 Redis/计数/推荐投影所需的事实。

## 主要结构问题

1. `VideoController` 直接编排上传、合成、通知、画像和 Kafka，应用层边界过宽。
2. `ContentFeatureService` 同时承担 Python worker、画像入口、标签、曝光和恢复逻辑。
3. `ChatWebSocketHandler` 同时做协议解析、ACL 缺省、旧 RTC 信令、消息落库和推送。
4. 直播房间连接集合已限定为实例本地 socket 引用；房间控制事件通过 Redis Pub/Sub 扇出，Redis presence/主播租约和 MySQL 状态分别承担在线投影与持久事实，避免把 JVM map 当作全局状态。
5. 共享 Mapper 被多个领域直接使用，难以在未来做数据所有权和独立扩展。

## 推荐边界

`controller -> application command -> domain/service -> repository -> projection adapter`。先在单体内拆包和接口，只有出现独立吞吐、资源或部署需求时再拆服务。
