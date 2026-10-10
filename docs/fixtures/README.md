# 前端样本

这些是与公开 API 字段一致的**静态设计样本**，供离线布局、组件开发和 AI 提示词使用，不是演示数据库导出。`plate` 应视为不透明标识；不要从中推导排序、栏目或文件路径。

| 文件 | 场景 |
| --- | --- |
| site.json | 站点配置和导航 |
| page.json | 列表响应及摘要字段 |
| normal.json | 正文、标题、引用、标签 |
| image.json | 根相对图片路径及 alt 文本 |
| code.json | 代码缩进和 HTML 转义 |
| table.json | 表格及小屏横向滚动 |
| long-title.json | 长标题、空标签、null 摘要、无 slug |
| empty.json | HTTP 200 的空列表 |
| not-found.json | HTTP 404 的 ProblemDetail |
| neighbors.json | 没有上下篇时的 null |

`image.json` 使用虚构内容哈希。离线预览可将该路径映射到 `example-vault/博客/_assets/demo.svg`；连接实际后端时，从文章 API 返回的 `bodyHtml` 读取真实图片地址，不要硬编码哈希。
