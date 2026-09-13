# 模块化审计

## 当前边界

项目是模块化单体：`controller/service/mapper/entity` 按技术层组织，RTC/直播/消息部分有更清晰的领域包；视频/推荐/画像仍互相调用。

## P3 问题

- Controller 直接依赖多个 Service、KafkaTemplate、ObjectMapper 和媒体组件，测试/替换成本高。
- ContentFeatureService 同时是 worker、行为入口、标签和画像 facade。
- WebSocket handler 包含协议、鉴权、持久化和推送，边界过宽。
- 通知、互动、推荐、分析共享实体和 Mapper，数据所有权不清晰。

## 建议

先在单体内按 `identity/content/social/interaction/feed/notification/realtime/media` 建 application/domain/infrastructure 包；通过 command/event interface 隔离，不改变 API 和表。只有独立资源或吞吐达到阈值才拆部署单元。
