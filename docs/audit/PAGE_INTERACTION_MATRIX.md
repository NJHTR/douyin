# Page Interaction Matrix

状态：静态链路基线；`Pending` 表示需要真实浏览器、认证数据或后端故障注入。

| Page | Action | Expected | Actual/evidence | API/permission | Loading/success/failure/empty | Navigation/state update |
|---|---|---|---|---|---|---|
| 个人页 | 点击 SeekFlow商城 | 进入正式商城首页 | 已改为 `$nav('/shop')`，待动态回归 | `/shop`，无需新增权限 | 覆盖 loading/empty/error | 与底部 Tab `/shop` 同页 |
| 首页搜索 | 搜索用户 | 展示可访问用户并可进入主页 | 调用 `/user/search` | 全站用户 scope | 有 loading/empty，异常被置空需补 error/retry | 搜索词与返回页状态需保留 |
| 消息更多搜索 | 搜索用户/好友 | 搜索全站用户并明确 scope | 已改为 `/user/search`，不再只过滤本地好友 | 全站用户 scope；friend request 仍受 ACL/互关规则约束 | loading/error/empty/retry/submitting | 待动态验证结果和关系状态传播 |
| 消息加号 | 添加朋友 | 打开全站用户搜索 | 原 `/message/add-friend` 无路由；现复用 `MoreSearch.vue` | `/user/search` + friend ACL | loading/error/empty/retry | route fixed; pending E2E |
| 消息页设置 | 添加好友 | 输入用户后得到结果并可发送申请 | 入口链路待确认 | `/user/friend/request` 的互相关注限制 | submitting/success/conflict/error | 成功后联系人和主页关系状态更新 |
| 私聊 | 发送图片 | 上传后发送 IMAGE 消息 | 两段请求，失败有 toast | upload + message ACL | loading/error/rollback | 消息去重、刷新恢复 |
| 群聊 | 发送图片 | 支持则同契约成功；不支持则禁用并说明 | 图片按钮存在，独立链路，catch 静默 | upload + group ACL | 当前缺少可见失败反馈 | 需 capability-driven UI |
| 商城列表 | 点击商品 | 进入详情，返回保留列表位置 | `/shop/detail` | 商品可见性 | loading/error/empty/retry | Deep Link 与列表入口一致 |
| 视频详情 | 点赞/收藏/关注 | 状态和计数立即且可回滚 | API 分散于多个组件 | 内容/用户权限 | submitting/error | Feed、详情、主页传播 |
| 群聊详情 | 添加/移除成员 | 权限允许时成功并刷新成员 | 有 toast 分支 | group member/admin ACL | loading/error/empty | 返回群聊状态一致 |

## Required Trace Format

## Static Audit Additions

| Page | Action | Expected | Static repair | Remaining validation |
|---|---|---|---|---|
| 首页/消息侧栏 | 钱包/历史/设置 | 进入与个人中心相同页面 | 已复用三个 canonical route | Back/refresh/login |
| 消息侧栏 | 最近常看作者 | 进入用户主页 | 已从 `_no` 改为 `/people/user-home/:uid` | 主页数据和返回状态 |
| 订单 | 加载/取消/确认收货 | 失败可见、可重试、不可重复提交 | 已补 success 判断和状态门禁 | 真实订单状态机 |
| 钱包 | 加载/充值 | 失败可见、金额校验、刷新明细 | 已补 error/retry/submitting | 真实余额与幂等性 |
| 支付弹窗 | 选择方式/下单/支付 | 未选方式不能前进，失败可重试 | 已修复禁用样式仍可前进的问题 | 支付回调与订单刷新 |
| 登录设备 | 加载/退出设备 | 失败可见、操作结果明确 | 已补 success 判断、重试和 toast | 多设备登录态 |

每个未决项补齐：`User Action -> Route -> Page -> Component -> API -> Permission -> DB/Cache -> Response -> Store/Event -> UI`，并附成功、失败、空、重试、返回、刷新和重复点击证据。
