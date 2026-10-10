# 面向 AI 与维护者

## 项目边界

- 这是独立的 Obsidian 博客模板，所有路径相对本仓库。
- Java 17 / Spring Boot / MySQL 8.4 / Redis 7.4；默认页面使用 Thymeleaf。
- 演示启动仅要求 Git 和 Docker Compose；不要新增宿主机 Java/Node 前置依赖。
- 不提交 `.env`、`data/`、`secrets/`、私有 Vault 或个人凭据。

## 修改前先读

- 接口：`docs/openapi.yaml`。
- 前端：`docs/FRONTEND_AI.md`、`client/blog-client.mjs`、`docs/fixtures/`。
- 部署：`docs/DEPLOYMENT.md`。
- 设计：`DESIGN.md`。

## 内容与 API 契约

- 仅发布 Git 已提交、位于白名单路径、显式 `publish: true` 的内容。
- 不放宽私密目录排除，不暴露原始 Markdown、Vault 路径或 Git SHA。
- `/api/v1/` 是前端只读边界；同步和数据库细节不进入公开 API。
- 列表零起始分页，空列表返回 200；未发布与不存在的详情均为 404。
- 文章链接使用 `canonicalPath`；`plate` 是不透明标识。
- `bodyHtml` 来自后端渲染器；不得直接渲染任意第三方 HTML。
- 图片可能被撤下，ETag 必须重新验证，不能无限期使用 immutable 缓存。
- 跨域接入必须同时处理 API CORS 和正文根相对图片地址。

## 文件入口

- 默认页面：`app/src/main/resources/templates/`。
- 主题变量：`app/src/main/resources/static/css/site.css`。
- 站点字段：环境变量与 `SiteProperties`。
- 数据库结构变更：新增 Flyway migration，不改已部署的旧 migration。
- 公共 DTO 变更：同步更新 OpenAPI、客户端、fixtures 和前端文档。

## 验证

- 后端：`mvn -f app/pom.xml test`。
- 数据库集成：`mvn -f app/pom.xml -Pintegration verify`，需要 Docker。
- JS 与启动保护：`node --test client/blog-client.test.mjs scripts/test-setup.mjs`。
- 示例代理：`node --test examples/plain-frontend/server.test.mjs`。
- 容器演示：`scripts/verify-demo.ps1` 或 `sh scripts/verify-demo.sh`。
- 前端至少检查手机/桌面、长标题、空列表、404、图片、代码块、表格及键盘焦点。
- 独立示例：`node examples/plain-frontend/server.mjs`。

## 操作约束

- 初始化不得覆盖已有 Vault 或 `.env`。
- 默认 Vault 只读挂载；生产拉取使用独立部署副本与只读 Deploy Key。
- 一个 Vault/数据库对应一个应用实例；不要直接扩副本数。
- 不用 reset/force push 修复部署分叉；先检查并保留已有内容。
- 保持 Windows PowerShell 初始化脚本为 UTF-8 BOM，Shell 脚本为 LF。
- 先执行与变更相关的检查，再陈述验证结果；不把预期当作已通过。
