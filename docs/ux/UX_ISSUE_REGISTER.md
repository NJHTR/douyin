# UX Issue Register

| Area | Risk | Current guard | Follow-up |
| --- | --- | --- | --- |
| Feed engagement | 状态延迟或失败丢反馈 | 点赞/收藏/关注乐观更新并回滚 | 接入统一请求状态指标 |
| Navigation | 返回后上下文丢失 | 路由 keep-alive 与深度过渡 | 为 Feed 索引增加持久化测试 |
| Gesture | Sheet 与 Feed 抢手势 | 容器捕获和 touch-action | 移动端真机验收 |
| Accessibility | 图标按钮难以点击或读屏 | 44px 命中区、aria-label | 键盘路径自动化测试 |
| Recovery | 上传/评论失败 | 保留内容并提供重试 | 统一失败状态组件 |
