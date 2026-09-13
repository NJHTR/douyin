# 安全审计

## P0/P1

- `ChatWebSocketHandler` 对 `to_user_id/to_user_ids/group_id` 的 ACL 校验不集中，存在越权投递/伪造旧 call_signal 的风险，必须按当前登录用户和群成员重新查询权限。
- Redis 幂等/锁 fail-open 会放大重复操作和状态越权风险。
- 上传接口没有看到完整 magic number、病毒扫描、codec/时长隔离和 presigned quarantine 流程。
- 开发配置包含默认 JWT secret、MinIO 默认地址和宽松 WebSocket origins；生产必须拒绝默认密钥并限制 origin。
- 需要逐一检查 `/api/video/{id}`、用户主页、评论、消息、直播房间、RTC token 的对象级授权（IDOR）。

## 已有保护

- RTC webhook 支持官方 JWT/HMAC 签名并 fail-closed。
- JWT 从环境注入；RTC token 有 TTL。
- WebSocket handshake 有鉴权拦截器，二进制直播控制帧被拒绝。

## 验证清单

越权 ID、过期 JWT、重放 token、超大 WS payload、SQL/JSON 注入、路径穿越、SSRF MinIO/SRS endpoint、恶意媒体和限流绕过。
