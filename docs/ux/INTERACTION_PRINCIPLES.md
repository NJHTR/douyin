# Interaction Principles

## Immediate feedback

点击点赞、收藏、关注等低风险动作时先更新视觉状态，再发送请求。请求失败必须恢复原状态，并用“暂时无法完成操作，请稍后重试”这类系统责任文案提示用户。

## Reversibility

点赞、关注、收藏使用直接动作；删除消息、退出群聊等破坏性操作需要确认或短时撤销。撤销动作应保留原始内容和上下文，不要求用户重新创建。

## Progressive disclosure

视频首层只展示作者、标题和核心动作。评论、分享、更多操作分别放入 Sheet 或独立层级，避免一次暴露过多决策。

## Accessibility

可操作元素使用真实 button/link，命中区不小于 44px，提供 `aria-label`，键盘可聚焦，并在 `prefers-reduced-motion` 下降低动画。
