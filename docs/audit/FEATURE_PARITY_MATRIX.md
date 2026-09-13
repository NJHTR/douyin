# Feature Parity Matrix

状态：`2026-08-22` 基线审计。`Observed` 为静态代码证据，`Pending` 需浏览器/API 联调确认。

| Feature | Entry | Page/component | API/domain | Permission/scope | State | Implemented | Consistent | Evidence / next check |
|---|---|---|---|---|---|---|---|---|
| 商城首页 | 底部 Tab | `/shop`, `Shop.vue` | `/shop/recommended` | 登录态/商品可见性 | loading/empty/search | Yes | Pending | [routes.ts](/D:/Ivscode/douyin/src/router/routes.ts:26), [Shop.vue](/D:/Ivscode/douyin/src/pages/shop/Shop.vue:1) |
| 商城首页 | 个人页“SeekFlow商城” | `Me.vue` | `/shop` | 同上 | navigation/loading/empty | Route exists | Fixed pending E2E | [Me.vue](/D:/Ivscode/douyin/src/pages/me/Me.vue:150) now calls `/shop` |
| 商品详情 | 商城列表/购物车/订单 | `GoodsDetail.vue` | `/shop/detail/:id` | 商品存在 | loading/error/empty | Yes | Pending | 验证 query/state 与返回恢复 |
| 用户搜索 | 首页搜索 | `SearchPage.vue` | `/user/search` | 全站用户 | loading/empty/error | Yes | Pending | [user.ts](/D:/Ivscode/douyin/src/api/user.ts:92) |
| 用户搜索 | 消息“更多搜索” | `MoreSearch.vue` | `/user/search` | 全站用户 scope | loading/error/empty/retry | Fixed pending E2E | Pending | 复用 canonical User Search Contract |
| 添加好友搜索 | 消息加号“添加朋友” | `/message/add-friend` → `MoreSearch.vue` | `/user/search` + `/user/friend/request` | 全站用户；好友申请策略 | loading/error/empty/submitting | Route fixed | Pending | 路由现在与用户搜索 feature 复用，待动态验收 |
| 添加好友 | 个人主页 | `UserHome.vue` | `/user/friend/request` | 互相关注规则 | submitting/success/error | Yes | Pending | [user.ts](/D:/Ivscode/douyin/src/api/user.ts:144) |
| 发送图片 | 私聊 | `Chat.vue` | `/api/upload/image` + `/message` | 会话权限 | loading/error/rollback | Yes | Pending | [Chat.vue](/D:/Ivscode/douyin/src/pages/message/chat/Chat.vue:457) |
| 发送图片 | 群聊 | `GroupChat.vue` | `uploadImage` + `sendGroupMessage` | 群成员/群能力 | submitting/error | Yes | Pending | [GroupChat.vue](/D:/Ivscode/douyin/src/pages/message/chat/GroupChat.vue:555)；需验证 500、能力声明和错误反馈 |
| 关注 | 首页/搜索/个人主页/视频 | 多组件 | `/user/follow/:userId` | 目标用户可见 | optimistic/rollback | Yes | Pending | 统一 `isAttention` 与缓存传播 |
| 点赞/收藏 | Feed/详情/收藏 | `ItemToolbar`, `VideoDetail` | video/comment like/collect | 内容权限 | optimistic/rollback | Yes | Pending | 对账事实表与 projection |
| 已读/未读 | 消息列表/会话 | `Message.vue`, chat pages | mark-read/message state | 会话成员 | read/unread/loading | Yes | Pending | 返回、刷新、WS 事件恢复 |

## Canonical Contract Decisions

## Static Audit Additions

| Feature | Entries | Canonical target | Static result | Dynamic status |
|---|---|---|---|---|
| 钱包 | 首页侧栏/消息侧栏/个人中心 | `/me/wallet` | 三入口已统一；页面有 loading/error/retry | 登录态待人工验收 |
| 观看历史 | 首页侧栏/消息侧栏/个人中心 | `/me/right-menu/look-history` | 三入口已统一 | 数据恢复待人工验收 |
| 设置 | 首页侧栏/消息侧栏/个人中心 | `/me/right-menu/setting` | 三入口已统一 | 登录态待人工验收 |
| 订单 | 商城/个人中心 | `/shop/orders` | 加载和操作失败不再伪装为空 | 后端状态机待人工验收 |
| 购物消息 | 商城 | `/message/shop-messages` | 不再跳普通消息首页 | 消息数据待人工验收 |
| 最近常看作者 | 首页/消息侧栏 | `/people/user-home/:uid` | 两入口复用用户主页 | 登录数据待人工验收 |

- `/shop` 是商城首页 canonical route；所有入口不得指向 TODO 或另一套商城实现。
- “全站用户搜索”和“当前好友过滤”是不同 scope；若消息设置实际为添加好友，必须改用明确的 User Search Contract，而不是静默复用好友列表。
- 私聊与群聊是否支持图片必须由 `MessageCapability` 返回；UI 不得展示可点击但必然失败的按钮。
