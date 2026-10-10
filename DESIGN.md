# 架构与边界

Obsidian Blog Starter 是一份可以独立部署的博客模板。Obsidian Vault 的 Git 提交是内容源；MySQL 是可重建的查询模型；Redis 提供同步锁；Spring Boot 同时提供公开读取 API 和一套简洁的 Thymeleaf 页面。

## 使用路径

- 演示：复制 `example-vault`，初始化独立 Git 仓库，Docker Compose 启动全部依赖，不需要 Obsidian 或 GitHub 账号。
- 本地 Vault：挂载已有 Git 工作树，只读且关闭 fetch；仅发布已提交内容。
- GitHub 部署：在服务器维护独立、完整的 clone，用只读 deploy key 拉取。Webhook 触发快速同步，定时全量拉取同步负责补偿遗漏。

配置、命令与挂载方式以 [README](README.md) 和 [部署文档](docs/DEPLOYMENT.md) 为准。

## 内容与发布

`PUBLISH_ROOT` 默认 `博客`，必须是安全的仓库内相对目录。`PublicationLayout` 统一传入发布闸门、板块分类和附件索引，避免它们使用不同根目录。内部结构固定为 `文章/`、`随笔/`、`项目/`、`_assets/`、`关于.md`、`友链.md`。

一篇文章必须同时满足允许路径、未命中禁止目录、有效 frontmatter 和显式 `publish: true`。缺省、错误或无法判断均不公开。读取来自 Git 对象库，工作树中的未提交修改不进入网站。原始 Markdown、仓库路径和提交 SHA 只供内部同步使用，不进入公共 DTO。

Markdown 支持表格、删除线、任务列表和受限 Obsidian 图片语法。普通双链转为文字，笔记嵌入不展开；原始 HTML 被转义，危险 URL 被过滤。目录与语法示例见示例 Vault。

## 同步与一致性

1. 单实例本地锁与 Redis 锁保护整个过程。
2. 获取锁后按 delivery ID 去重，随后执行可选的 `git fetch --prune origin` 与 `git merge --ff-only @{u}`。
3. Webhook 尽量按 Git diff 同步，无法计算 diff 时退回全量。定时任务始终做全量，修复文章先提交、附件后提交等增量盲点。
4. 构建发布报告后，在单个 MySQL 事务中更新文章、附件引用和同步日志。全部耗时从 fetch 前开始计量。
5. 全量遍历成功后，下架已删除或明确不发布的文章；仅保护本次实际读取失败的文章路径。无关附件失败不能阻止撤回。
6. `partial` 不推进最近完整提交。后续定时任务继续尝试；失败的 fetch 不会被记录成已同步的 delivery。

应用启动时，在 HTTP 服务开始接受请求前做一次全量重建。切换发布根后重新启动即可撤回旧根内容。首次重建失败会阻止启动。

支持一个应用实例、一份 Vault、一个数据库及独立 Redis 实例。水平扩容和多个节点共同修改同一 clone 不在当前部署合同内。Redis 锁有过期时间，本地锁保证同一进程中的 fetch 与同步不会交错。

## 存储与附件

Flyway 从空数据库执行 `V1__baseline.sql`。`post`、`asset`、`post_asset`、`sync_log` 和 `plate_seq` 组成派生存储；A/N/L/P 四个序列分配稳定文章编号。slug 可以变化，编号保留。数据库凭据由环境注入。

附件 URL 是内容 SHA256 前 16 位加扩展名，附件记录按完整哈希与扩展名共同识别。相同字节使用不同扩展名时，各 URL 都能访问；服务端仍校验扩展名，避免任意 MIME 替换。附件字节按 Git blob ID 读取，最多 20 MiB。

只有被当前已发布文章引用的附件可访问。JSON 与附件均使用 ETag 和 `Cache-Control: no-cache`；撤回后即使请求带旧 ETag，也必须得到 404。客户端已经保存的副本无法由服务器追回。

## 前端边界

仓库仅包含一套全新简洁界面，使用系统字体与 CSS 变量。用户原博客的模板、样式、字体和插画未包含在分发内容里；定制效果只在 README 截图中展示。

前端可以直接改 Thymeleaf，也可以独立使用 React、Vue、Astro 或普通 HTML。公共契约以 [OpenAPI](docs/openapi.yaml) 为准；[FRONTEND_AI](docs/FRONTEND_AI.md) 提供页面映射、调用示例、样例数据和可复制提示词。`client/` 提供无框架依赖的 JS 客户端，`examples/plain-frontend/` 演示独立页面。

推荐前端代理 `/api/v1` 和 `/assets` 到同一后端。跨域部署只放行配置的公开读取来源；正文里的相对图片地址需由客户端解析成后端地址。Webhook 与健康检查不开放跨域读取。

## 验证与维护

- `mvn -f app/pom.xml test`：纯逻辑、前端渲染、同步协调和 OpenAPI 字段契约。
- `mvn -f app/pom.xml -Pintegration verify`：必须有可用 Docker；真实 MySQL/Redis、临时 Git、Webhook/缓存/撤回/切换根目录。缺少 Docker 时失败，不静默跳过。
- `node --test client/blog-client.test.mjs`：独立客户端行为。
- `scripts/verify-demo.ps1` 或 `.sh`：已运行演示环境的 HTTP 验证。

项目自有代码使用 MIT；依赖与定制截图说明见 `THIRD_PARTY_NOTICES.md`。源码可公开分发，`.env`、本地 Vault、数据库卷及构建输出不属于开源交付内容。发布到远程仓库和真实服务器是单独的操作。