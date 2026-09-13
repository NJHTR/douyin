# Navigation Architecture

`Home -> Video` 使用 Push 或保持同一 Feed 上下文；`Video -> Comments` 使用 Bottom Sheet；`Comments -> Profile` 使用 Push。关闭 Sheet 回到视频，Back 回到上一层，Home 永远回到首页根路由。

路由切换必须依据层级决定 `go` 或 `back` 过渡。返回后保留 Feed 当前索引、播放位置、评论滚动位置和未发送草稿。Modal 只处理当前页面内的短任务，不替代页面导航。
