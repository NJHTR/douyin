# Douyin 项目审计总览

审计阶段：Phase 1（只审计，不继续修改业务代码）  
审计日期：2026-08-22  
工作区：`dev/full`

## 技术栈

- 后端：Java 17、Spring Boot 3.3、MyBatis-Plus、MySQL、Redis、Kafka、MinIO。
- 前端：Vue 3、Vite、Pinia、TypeScript、Axios、LiveKit Client。
- 实时：Spring WebSocket、Redis Pub/Sub、RTC 呼叫状态机、LiveKit SFU、SRS WHIP/WHEP、TURN。
- 媒体：MinIO 对象存储；上传接口仍由业务服务接收 MultipartFile；直播媒体不经过控制 WebSocket。
- 部署：本地 Docker Compose；已有三 VM RTC 角色化部署文件（API、LiveKit、TURN、edge）。

## 模块

`identity/user`、`content/video`、`social/interaction`、`feed/recommendation`、`notification/message`、`media/live`、`rtc/call`、`analytics/admin`、`infrastructure/kafka/redis`。

当前代码形态是模块化单体，而不是微服务。数据库 Mapper 和 Service 仍是主要边界；RTC 已有独立 domain/repository/service 子包。

## 真实调用链

### 视频浏览

`Vue slide -> /api/video/recommended -> VideoController -> VideoServiceImpl -> RecommendationEngine/VideoMapper -> MySQL`。

观看进度由 `BaseVideo/TextSlide/ImageSlide` 上报 `/api/video/watch/{id}`，写入 `t_watch_history`，再更新用户画像。画像是派生数据，不应阻塞观看事实写入。

### 互动

`Toolbar -> /api/video/like|collect -> VideoController -> VideoServiceImpl -> t_like/t_video_collect + t_video counters`。Kafka 开启时，部分 VideoEvent 由 outbox/consumer 投影；数据库关系表是事实，计数是投影。

### 聊天/通知

`WebSocket -> ChatWebSocketHandler -> MessagePublisher -> Kafka(outbox) -> KafkaMessageConsumer -> t_message + SessionManager`。旧 `call_signal` 仍在 ChatWebSocketHandler 直接转发并写通话消息，绕过 RTC 权威状态机。

### RTC

`HTTP/WS client -> RtcCallController/CallService -> rtc_call_session + participant/device/event/outbox -> Kafka/Redis timeout index -> LiveKit token/webhook -> client`。MySQL RTC 表是控制面事实；Redis 仅作索引、限流和查找加速。

### 直播

`LiveController -> LiveService -> MySQL`；媒体通过 SRS WHIP/WHEP 或 HLS/HTTP-FLV，控制事件通过 LiveStreamHandler。直播 socket 注册表只保存本机连接，房间控制事件经 Redis Pub/Sub 扇出，主播在线由 Redis TTL lease 投影，跨实例不再依赖单 JVM map。

## 事实与投影

- 事实：用户关系、视频关系、消息、RTC session/event、直播房间主记录。
- 投影：`like_count/share_count/play_count`、Redis cache、推荐 feed、用户画像、搜索提示、在线连接 fanout。
- 事件：Kafka topic、outbox、consumer ledger/DLQ。

## 当前验证边界

已具备：后端 compile、互动 focused test、前端 `pnpm build`、RTC 多 VM 手工流程。  
尚缺：真实压测曲线、Redis/Kafka/MySQL 故障注入、跨地域网络、对象存储直传、生产级指标采集与恢复演练。

详细问题见 [ISSUE_REGISTER.md](ISSUE_REGISTER.md)。
