# 性能审计

## 主要放大器

- 深 offset、N+1、`ORDER BY RAND()`、全表恢复、每请求画像写、同步 dashboard 查询。
- 热点视频计数/评论/元数据缺少统一 cache stampede 保护。
- API 上传大文件、同步 FFmpeg/MinIO、AI Python 进程会占用请求或本地资源。
- 前端旧观看心跳造成高 QPS；工作区已部分降低，但 profile projection 仍需按事件/批处理治理。

## 基线和缺口

已执行：后端 `mvn -q -DskipTests compile`、互动 focused test、前端 `pnpm build`。  
未执行：wrk/k6/Gatling 压测、JFR、MySQL EXPLAIN/slow log、Redis MONITOR/latency、Kafka lag 曲线、移动网络首帧/重缓冲测试。

## 优先指标

Feed/like/watch/WS/RTC 分别记录 P50/P95/P99、QPS、错误率、DB pool、Redis pool、Kafka lag、队列深度、GC/CPU/内存。
