# Interaction Matrix

| Action risk | Examples | Default behavior | Failure behavior |
| --- | --- | --- | --- |
| Low | 点赞、收藏、关注 | Direct + optimistic | Rollback + concise notice |
| Medium | 取消收藏、取消发送 | Direct or short Undo | Restore when possible |
| High | 删除消息、退出群聊 | Confirm | Keep context until confirmed |
| Destructive | 删除账号 | Explicit confirmation | Explain consequences |
| Irreversible | Permanent deletion | Double confirmation | Never silently discard |
