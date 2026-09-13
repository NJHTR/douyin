# UX Issue Register

状态：`Open` 表示已确认或高概率问题，`Pending` 表示需要动态验证；优先级遵循 P0/P1/P2/P3。

| ID | Priority | Issue | Root/evidence | Scope | Owner | Fix/verification |
|---|---|---|---|---|---|---|
| UX-P1-01 | P1 | 个人页 SeekFlow商城点击无反应 | `Me.vue` 曾使用 `_no`；正式 `/shop` 已存在 | route consistency | Frontend | Fixed in worktree: 复用 `/shop`；待入口 E2E 回归 |
| UX-P1-02 | P1 | 消息搜索与用户搜索结果不一致 | 首页调用 `/user/search`；`MoreSearch.vue` 只过滤本地好友 | search contract | Frontend/Backend | 明确 scope；统一 UserSearchService 或改文案/入口 |
| UX-P1-05 | P1 | 消息加号“添加朋友”指向不存在路由 | `Message.vue` 导航 `/message/add-friend`，原 routes 未声明 | route consistency | Frontend | Fixed in worktree: route 复用 `MoreSearch.vue`；待导航 E2E |
| UX-P1-03 | P1 | 私聊/群聊图片能力链路不一致 | 两页均显示图片按钮但使用不同 upload/send API；群聊失败分支曾静默结束 | message capability | Messaging | Partial: 共享 capability 契约、群聊响应失败可见；仍需统一 transport 和端到端契约 |
| UX-P1-04 | P1 | 同一关系状态跨页面可能陈旧 | follow/like/collect 由多个组件读取和修改 | state propagation | Social | canonical store/event/cache invalidation；验证返回和刷新 |
| UX-P2-01 | P2 | Loading/Empty/Error 文案和反馈分散 | 搜索、聊天、商城各自维护状态；部分 catch 仅置空或 console | async state | Frontend | 统一状态模型和错误策略，增加 retry |
| UX-P2-02 | P2 | 重复点击与提交态未形成统一门禁 | 多个按钮依赖页面局部 `loading/sending` | idempotency | Cross-module | disable/debounce + server idempotency tests |
| UX-P2-03 | P2 | 返回/刷新状态恢复未形成证据 | router 有 exclude/scroll 逻辑，页面 store 仍各自维护 | navigation state | Frontend | 增加 journey E2E，记录 query/scroll/state |
| UX-P3-01 | P3 | TODO/no-op 入口仍暴露给用户 | 代码中存在 `_no` 和“未实现”交互 | product integrity | Product/Frontend | hidden/disabled/Coming Soon policy |
| UX-P1-04 | P1 | 导航转场曾用扁平路由数组下标判断层级 | `router/index.ts` 与 `App.vue` 无法表达真实父子栈 | navigation semantics | Frontend | 已改为 URL 层级 fallback；复杂跳转需 route meta 与旅程 E2E |
| UX-P1-05 | P1 | 群聊图片入口运行时调用布尔值 | `GroupChat.vue::pickImage` 原实现为 `false(...)` | messaging interaction | Frontend | 已修复并补上传/发送失败反馈、重复提交保护；待动态回归 |

## Triage Rule

## Static Inventory (2026-08-22)

本轮不执行登录态和浏览器 E2E，先完成源码级全量盘点。当前仓库仍有约 80 处直接 `@click="_no"`、约 11 处 `@click.stop="_no"`，主要集中在：首页/消息侧栏第三方小程序与扩展工具；商城充值和分类入口；完整榜单；音乐创作；外部平台分享；个人中心生活服务；通知、举报和收藏等次要操作。

确认问题如下：

| ID | Priority | Issue | Evidence | Resolution |
|---|---|---|---|---|
| UX-P1-06 | P1 | 核心入口存在可点击占位操作 | 首页/消息侧栏的钱包、历史、设置曾走 `_no`；个人主页溢出菜单无真实实现 | 三个入口已复用 canonical route；个人主页隐藏未接入菜单 |
| UX-P1-07 | P1 | 订单/钱包/支付/设备管理失败被吞掉 | 页面忽略 `success=false` 或空 `catch` | 已补 Loading/Error/Retry、操作提示和重复提交门禁 |
| UX-P1-08 | P1 | 好友搜索与申请权限语义不清 | `/user/search` 全站搜索；申请接口要求双方互关 | UI 明示申请前提；关系增强 DTO 仍可继续完善 |
| UX-P2-04 | P2 | 响应类型丢失业务元数据 | `ApiResponse` 原仅声明 `data/success` | 已保留 `code/msg/message/count` |
| UX-P2-05 | P2 | 次要功能仍有大量 `_no` | 无对应页面/API 的扩展入口 | 统一改为“该功能即将上线”；人工决定隐藏或 disabled |
| UX-P2-06 | P2 | 背景补充请求静默失败 | 搜索补充、已读回执、访客记录等空 catch | 允许非关键背景任务静默；主流程必须显式失败状态 |

这些占位项均已登记，不在没有业务 API/页面契约的情况下臆造实现。验收时需逐项决定隐藏、禁用或保留 Coming Soon。

关键入口不可用、同业务一个入口成功另一个失败、关键操作点击后 500、搜索结果明显不一致、消息能力不一致或状态明显错误均按 P1；仅视觉/文案轻微差异按 P3。不得用 Toast 掩盖真实未实现链路。
