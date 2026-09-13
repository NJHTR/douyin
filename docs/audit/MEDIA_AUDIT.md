# 媒体链路审计

## 当前状态

- 视频/图片/语音上传仍通过 `/api/upload/*` MultipartFile 到业务服务，再写 MinIO。
- MinIO 已支持对象写入、预签名 URL 和 range 读取，但 API 尚未提供完整 upload session/presigned upload 流程。
- 视频合成/裁剪和封面提取存在同步调用路径；特征提取由本地 Python worker 执行。
- 直播媒体使用 SRS WHIP/WHEP/HLS/HTTP-FLV；控制 WebSocket 明确拒绝二进制媒体。

## P1 风险

- 大文件经过 Spring/Tomcat 会占用 API 带宽、线程和临时磁盘；生产应改为 presigned multipart upload。
- 上传校验需检查 magic number、真实 MIME、大小、时长、codec、路径和恶意文件，不应只信扩展名/Content-Type。
- 转码、封面、特征提取需要任务状态、租约、重试、DLQ、幂等和可重建记录；当前本地单实例 worker 不具备多实例 claim。
- CDN、ABR、多码率、断点和首帧指标尚未形成验收数据。
