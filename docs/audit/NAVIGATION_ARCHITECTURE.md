# Navigation Architecture and Interaction Audit

状态：`Baseline / In Progress`  
审计日期：2026-08-22  
范围：Vue Router、页面转场、底部 Tab、聊天层级、对话框/底部弹层、返回与手势语义。

这份文档是当前实现的导航契约。静态扫描结论不会替代真实浏览器、认证数据和网络故障注入；未执行动态验证的项目必须保持 `Pending`。

## 1. 导航模型

### 1.1 容器语义

| 容器 | 语义 | 进入 | 退出/返回 | 典型页面 |
|---|---|---|---|---|
| Root/Tab | 顶层工作区，切换不应叠加历史页 | `push` 到 canonical Tab 路径 | 再次点击当前 Tab 刷新，不退出 App | `/home`, `/shop`, `/message`, `/me` |
| Page | 当前工作区中的完整页面 | `push` | `router.back()` 回到实际来源 | 搜索、个人页、商品、设置 |
| Detail Page | Page 的下一层 | `push` | `pop` 回 Detail 来源页 | `/video-detail`, `/people/user-home/:uid` |
| Modal/Dialog | 临时决策，不创建业务页面层级 | `v-model`/服务调用 | Confirm/Cancel/遮罩/ESC 关闭 | 删除、备注、举报 |
| Bottom Sheet | 当前页面的上下文操作 | `FromBottomDialog` | 下滑、遮罩、Android Back 关闭，回到宿主 | 评论、分享、选择操作 |
| Drawer/Popover | 局部菜单 | 局部状态 | 外部点击/Back 关闭 | 消息更多、群菜单 |
| Full-screen viewer | 媒体沉浸查看 | `push` 或全屏状态 | Back/关闭回媒体来源 | 图片、视频详情 |
| External | 离开应用的系统/第三方页面 | `window.open`/系统 API | 由平台返回 | 外部协议、支付（当前未形成统一契约） |

### 1.2 Canonical Tab routes

| Tab | Canonical route | 现有别名/入口 | 状态要求 |
|---|---|---|---|
| 首页 | `/home` | `/` redirect、`/slide` alias、底部 Tab | 切换 Tab 保留 Feed 位置和已加载页；当前动态验证 Pending |
| 商城 | `/shop` | 个人页、底部 Tab | 所有入口必须到同一 feature；商品列表返回保留列表位置 |
| 发布 | `/publish` | 底部中央按钮 | 完成/取消的返回语义需由发布流程决定 |
| 消息 | `/message` | 底部 Tab | 未读徽章与列表状态一致 |
| 我 | `/me` | 底部 Tab | 个人页入口与设置子页保持返回栈 |

底部 Tab 是横向切换，不应把 `/home -> /shop -> /message` 当作父子层级。Tab 之间使用无转场或同级转场；子页面才使用 push/pop 语义。

## 2. 页面栈契约

### 2.1 典型栈

```text
/home
  -> /home/search?q=abc
  -> /people/user-home/:uid
  -> /message/chat?user_id=:uid

Back(chat)   = user profile
Back(profile) = search (保留 q=abc)
Back(search)  = home (保留 Feed/搜索入口状态)
```

```text
/message
  -> /message/group-chat?group_id=:id
  -> /message/group-chat/detail?group_id=:id
  -> /people/user-home/:uid

Back(member)      = group detail
Back(group detail) = group chat
Back(group chat)   = message
```

```text
/video-detail
  -> Comment Bottom Sheet
  -> User Profile Page

Close(profile) = Comment Sheet
Close(sheet)   = video detail
Back(video)    = 原始 Feed/Search/Profile 来源，不得无条件跳 Home
```

### 2.2 Push、Replace 和 Reset

| 场景 | 语义 | 规则 |
|---|---|---|
| 普通详情/设置/搜索结果 | `push` | Back 必须回真实来源 |
| 登录成功、注册完成 | `replace` | Back 不应回登录表单 |
| 支付成功/不可重复的结果页 | `replace` 或明确 reset | 防止回到已完成支付步骤 |
| 关闭 Modal/Sheet | 局部状态变更 | 不调用 `navigate('/home')` |
| Tab 切换 | canonical Tab 导航 | 不复制同一 Tab 历史栈 |

代码审计规则：禁止用固定首页路径模拟返回；除非产品决策明确要求退出 Feature，否则优先 `router.back()` 或关闭宿主容器。

## 3. Back 行为矩阵

| 入口/平台 | Page | Modal | Bottom Sheet | Drawer/Popover |
|---|---|---|---|---|
| 页面返回按钮 | `router.back()` | Close | Close | Close |
| 浏览器 Back | `pop` | Close（需统一 history 策略） | Close（需统一 history 策略） | Close |
| Android Back | `pop` | Close | Close | Close |
| iOS Edge Swipe | `pop`，只在边缘开始 | 不抢宿主手势 | 不抢宿主手势 | 不抢宿主手势 |
| ESC | 页面无动作 | Close | Close | Close |
| 遮罩点击 | 页面无动作 | 仅低风险 Modal | Close | Close |
| 下滑 | 页面无动作 | 无 | 达到阈值才 Close | 无 |

当前实现证据：`BaseHeader`、聊天页、视频详情、直播页主要调用 `router.back()`；`FromBottomDialog` 自己处理 touch close。浏览器 Back 与 Modal/Sheet 的 history 集成尚未形成统一测试，标记 `Pending`。

## 4. 手势所有权

| 区域 | 主手势 | 竞争手势 | 优先级规则 |
|---|---|---|---|
| Feed 视频 | 垂直切换 | 横向返回、播放器拖拽 | 先判定方向；垂直锁定后不得触发横向返回 |
| 图片查看器 | Pinch/Pan | Edge Back | 非边缘区域由媒体手势消费；边缘短距离可取消返回 |
| 评论/消息列表 | 垂直滚动 | 页面返回 | 内容滚动优先，只有 edge-start 才交给返回手势 |
| Bottom Sheet | 内部滚动 | 下滑关闭 | 仅滚动容器已到顶部且达到关闭阈值才 dismiss |
| 语音输入 | Press/Hold/Move | 页面滚动 | 录音期间阻止页面滚动；滑出取消，松手发送 |
| 消息长按 | Long press | 点击/双击 | 长按菜单出现前不触发普通点击；外部点击和 Back 关闭 |
| 视频双击 | Double tap Like | 单击播放/暂停 | 延迟单击确认，避免双击先产生两次单击 |

静态扫描确认 Feed、图片、语音和底部弹层均存在 touch handlers；阈值、取消动画、跨设备行为需要 Playwright/真机验证。

## 5. 消息交互状态契约

图片消息必须遵循：

```text
SELECTED -> UPLOADING -> UPLOADED -> SENDING -> SENT
                         |                    |
                         +-> FAILED <---------+
                              |
                           RETRYING
```

要求：

- 选择图片后不能让整个聊天页面进入全局 Loading；至少显示上传/发送中状态。
- 上传失败只重试上传；上传成功但消息发送失败只重试发送。
- 发送按钮和图片入口必须在 `sending` 时禁止重复提交。
- 群聊与私聊必须使用相同的错误可见性、Retry 语义和消息去重规则；能力不支持时应禁用并说明原因。

本轮已修复群聊图片入口的运行时异常，并补充上传失败、消息发送失败和重复提交保护。群聊与私聊的 API/能力 DTO 仍未统一，状态为 `P1 / Pending`。

## 6. 状态、反馈和恢复

任何用户操作至少提供一种即时反馈：控件状态变化、局部 Loading、进度、动画、Toast、Haptic 或 Sound。错误消息必须说明：发生了什么、用户能做什么、是否可 Retry。

| 操作 | Loading | 成功 | 失败 | 恢复 |
|---|---|---|---|---|
| Like/Follow/Favorite | 局部 submitting | 状态立即更新 | 回滚并提示 | 再次点击重试 |
| 文本/图片发送 | sending/upload progress | Sent | Failed 状态 | Retry，不重新输入/选择 |
| 网络重连 | Reconnecting | Reconnected | 明确离线 | Retry/继续等待 |
| 危险操作 | 不强制全页 Loading | 明确结果 | 保留原状态 | 取消或再次确认 |

禁止用“操作失败”覆盖真实错误，也禁止用 Toast 掩盖没有实现的入口。

## 7. 已确认问题与优先级

| ID | 优先级 | 结论 | 证据/状态 |
|---|---|---|---|
| NAV-P1-01 | P1 | 群聊图片入口点击会调用布尔值 | 已修复 `GroupChat.vue::pickImage`；需动态回归 |
| NAV-P1-02 | P1 | 扁平 routes 数组下标不能表达父子关系 | 已改为 URL 层级 fallback；复杂业务跳转仍需 route meta/旅程测试 |
| NAV-P1-03 | P1 | 私聊/群聊图片能力链路不一致 | `INTERACTION_CONSISTENCY_REPORT.md` 已记录；需 capability-driven UI 和统一契约 |
| NAV-P1-04 | P1 | Modal/Sheet 的浏览器 Back 语义未统一 | 静态无法证明；需动态测试 history 集成 |
| NAV-P2-01 | P2 | Feed、评论、聊天滚动位置恢复缺乏证据 | keep-alive 与局部 store 并存；需 Playwright 记录 scroll position |
| NAV-P2-02 | P2 | 长按、双击、垂直/横向滑动存在竞争 | 已有 handler，但未完成阈值和优先级测试 |
| NAV-P2-03 | P2 | 消息发送/上传尚未统一 optimistic 状态机 | 群聊有 sending，私聊实现不同；需统一组件或协议 |

## 8. 验收用例

### 栈与返回

1. `A -> B -> C`：C Back 到 B，B Back 到 A，A Back 退出当前 Feature。
2. `Page -> Sheet -> Profile`：Profile Back 到 Sheet，关闭 Sheet 回 Page。
3. 登录成功后 Back 不回登录页；支付成功后 Back 不重复提交。
4. Tab 切换后返回原 Tab，Feed/Chat 的位置和输入状态符合产品决策。

### 操作与恢复

1. 图片选择、上传中、发送中、成功、上传失败、发送失败、Retry、取消。
2. 文本/关注/点赞连续点击只产生一次服务端操作。
3. Offline、Timeout、Reconnecting、Reconnected 各有对应 UI，不显示陈旧“在线”。
4. 键盘出现时输入框、发送按钮和 Bottom Sheet 不被遮挡；点击外部/Back 可收起键盘。

### 动态证据格式

每条用例记录：认证用户、viewport/设备、入口、route、请求/响应、权限、Loading、Success、Failure、Empty、Retry、Back、Refresh、Duplicate Click、Offline/Timeout、最终 UI、截图和控制台错误。

## 9. Exit Criteria

- `NAV-P1-*` 全部关闭，或有明确产品决策、禁用态和回归证据。
- 至少覆盖 30 个核心操作；每个操作验证正常、Loading、Success、Failure、Retry、Back、Refresh、Duplicate Click、Network Failure。
- J1-J7 用户旅程在桌面和移动 viewport 动态通过；未执行项保持 `Pending`。
- 统一路由 meta/导航服务后，删除基于路径字符串的临时转场判断，并为返回栈、Sheet/Modal history 和手势阈值补自动化测试。

