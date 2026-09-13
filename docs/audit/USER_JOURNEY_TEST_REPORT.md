# User Journey Test Report

状态：`2026-08-22` 静态基线，动态 E2E 尚未在本轮执行。未执行项不能标记为通过。

| Journey | Path | Static result | Dynamic status | Acceptance |
|---|---|---|---|---|
| J1 | 新用户 → 首页 → 搜索用户 → 主页 → 关注 → 消息 → 私信 → 返回首页 | Routes/API exist | Pending | 关注状态、会话状态和返回位置一致 |
| J2 | 首页 → 搜索 → 用户 → 添加好友 → 消息 → 图片私信 | User search and friend request contracts exist | Pending | 搜索同一用户、申请结果、图片上传和消息渲染闭环 |
| J3 | 消息 → 加号“添加朋友” → 搜索用户 → 添加好友 | Route was missing; now `/message/add-friend` reuses canonical search | Pending E2E | 搜索结果、申请状态、互关限制和错误提示一致 |
| J4 | 消息 → 群聊 → 文本 → 图片 → 文件 | Group route and image handler exist | Pending | capability-driven controls；无静默失败或 500 |
| J5 | 首页 → 视频 → 评论 → 点赞 → 收藏 → 关注作者 | APIs/components exist | Pending | optimistic rollback and cross-page counts |
| J6 | 首页/个人页 → SeekFlow商城 → 商品 → 详情 → 返回 | Personal entry now targets canonical `/shop` | Pending E2E | Must match J7 route/data/state |
| J7 | Bottom Tab → 商城 → 商品 | `/shop` route exists | Pending | Must match J6 route/data/state |

## Required Dynamic Matrix

每条旅程至少记录：认证用户、设备/viewport、入口、route、request/response、权限、Loading、Success、Failure、Empty、Retry、Back、Refresh、Duplicate Click、Offline/Timeout、最终 UI 状态。建议使用 Playwright 覆盖桌面和移动 viewport，并保存截图/控制台/网络日志。
