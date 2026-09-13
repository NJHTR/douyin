# Security Validation

本轮覆盖 JWT secret 启动门禁、WebSocket Origin 生产门禁、单聊/群聊/RTC 信令 ACL。

已通过：

- 空或弱 JWT secret 拒绝启动。
- 生产 WebSocket Origin 为空或通配符拒绝启动。
- 不存在的单聊目标、非群成员、非通话参与者不能通过 WebSocket 入口。

未覆盖：真实反向代理 Origin、跨节点握手、密钥轮换和生产证书链。
