# Interaction Consistency Report

## Executive Summary

本轮将交互一致性作为独立的 Product Integrity 层，位于架构、业务正确性和推荐之外。静态扫描发现至少三个需要优先闭环的断点：个人页商城入口 no-op、消息搜索 scope 与首页用户搜索不同、私聊/群聊图片消息存在平行链路且群聊错误反馈不足。

## Canonical Decisions

1. 商城 canonical route 为 `/shop`；首页、个人页、底部 Tab、商品详情返回和 Deep Link 必须复用同一个 feature。
2. 用户搜索必须显式声明 scope。全站搜索、好友过滤、群成员搜索不可共用模糊文案；本质相同的搜索应下沉到统一服务和 DTO。
3. 消息能力由会话类型和权限共同计算。前端依据 `SupportedCapabilities` 显示/禁用控件，后端仍执行最终授权；失败必须可见、可理解、可重试。本轮新增共享 `messageCapabilities.ts`，私聊和群聊图片能力默认保持 parity。
4. Follow/Like/Favorite/Friend/Read 等状态以服务端事实为准，通过事件、缓存失效和必要的 optimistic rollback 传播到 Feed、Profile、Search、Message、Contacts。

## Findings

- `Me.vue` 的商城入口曾使用 `_no`，与已有 `/shop` 形成确定的入口不一致；本轮已改为复用 `/shop`，待动态回归。
- `SearchPage.vue` 与 `MoreSearch.vue` 现在都调用 `/user/search`；此前 `MoreSearch.vue` 只对 `store.friends.all` 做本地过滤，无法完成“添加陌生用户”的同一用户搜索语义。
- `Chat.vue` 和 `GroupChat.vue` 都展示图片按钮，但上传方式、发送 API、loading 字段和错误处理不同；本轮已补群聊图片失败提示，能力契约仍未统一。
- 搜索、商城、聊天页面各自实现 loading/empty/error 文案和恢复逻辑，尚无统一错误码到用户提示的策略。

## Exit Criteria

## Static Audit Update (2026-08-22)

- 已对 `src` 下 `_no` 入口、路由跳转、用户可见 `catch` 做源码级盘点。
- 核心入口占位已优先复用现有 canonical route；无模块支撑的次要入口统一显示“该功能即将上线”。
- 请求封装已保留 `code/msg/message/count` 元数据，订单、钱包、支付、登录设备改为显式处理 `success=false`。
- 好友申请搜索明确“全站搜索；加好友需双方互相关注”，避免把搜索成功误解为申请必然成功。
- 登录验收和真实后端 E2E 按用户要求暂缓，后续人工验收需覆盖 Journey 1-7 以及失败、返回、刷新场景。

随机抽取至少 20 个核心功能，每个从 2～3 个入口复测；所有 P1 项关闭或有明确产品决策、禁用态和回归证据；Journey J1-J7 动态通过；Deep Link、返回、刷新、重复点击和网络失败均有记录。未完成浏览器/API 联调前，本报告保持 `Baseline / In Progress`。
