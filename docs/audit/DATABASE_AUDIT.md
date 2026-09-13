# 数据库审计

## 已发现

- `t_like`、`t_video_collect`、`t_follow`、`t_watch_history` 有业务唯一键，方向正确。
- `t_watch_history` 具备 `(user_id, video_id)` 唯一键，但历史查询使用 `LIMIT offset,limit`，深分页会退化。
- 会话列表存在 N+1；评论、通知和分析接口大量使用字符串拼接 `LIMIT`。
- 视频推荐、following 等路径可能先取最多 200 条候选再在 JVM 排序，规模增大后会造成内存和 DB 放大。
- `ORDER BY RAND()` 用于随机好友，数据量大时不可接受。
- 部分统计按请求实时 `COUNT(*)`，热门视频会形成热点查询。

## 索引/迁移

需要用真实生产数据执行 `EXPLAIN`，重点覆盖：

- `t_message` 双向会话 `(from_user_id,to_user_id,id)` 与反向索引。
- `t_watch_history(user_id,update_time,id)`。
- `t_like(user_id,create_time,video_id)`、`t_video_collect(user_id,create_time,video_id)`。
- 通知按 `(user_id,is_read,id)`。

迁移必须保持 backward compatible；任何新 upsert SQL 依赖的列必须在应用切换前完成 migration。

## 分页策略

保留旧 page/pageSize API 兼容层，新增 cursor API，以 `(create_time,id)` 或单调 ID 作为游标；禁止新接口继续引入大 offset。
