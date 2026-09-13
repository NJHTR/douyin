# 客户端卸载与交互审计

## 已观察

- Vue 端有当前视频播放、滑动、RTC store、SRS/LiveKit 播放器和本地断点位置。
- 工作区已移除 `HTMLElement.prototype.addEventListener` 原型代理，改为局部捕获点击抑制；`v-click` 监听器可卸载。
- 工作区已把图文/视频观看心跳从 5 秒降为 15 秒，并限制只在活动项计时。

## 未完成

- 下一条/下下条预加载没有统一的 Wi-Fi/蜂窝/低电量/后台策略；需要 metadata/thumbnail/segment 分级预取和内存上限。
- Axios 默认 timeout 120 秒，对互动接口过长；应按接口类别设置短超时、取消和重试策略。
- 观看事件应支持批量、`sendBeacon`、离线队列和 eventId，避免页面关闭丢失且避免重复。
- WebSocket 重连需要指数退避+jitter、连接上限和状态恢复指标。
