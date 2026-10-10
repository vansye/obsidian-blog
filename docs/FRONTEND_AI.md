# 用 AI 改造前端

后端负责公开内容、Markdown 渲染和图片访问，前端负责把这些内容展示出来。你可以只改默认 Thymeleaf 页面，也可以把整个界面换成 React、Vue、Astro 或纯 HTML。

## 给 AI 的最小上下文

先提供本文件、[OpenAPI](openapi.yaml)、[客户端](../client/blog-client.mjs) 和 [fixtures](fixtures/README.md)。只有修改默认页面时，才需要提供 `app/src/main/resources/templates/` 与 `static/css/site.css`。无需向前端 AI 提供私有 Vault、`.env`、数据库或 Deploy Key。

站点结构：

| 页面路由 | 内容 | 读取接口 |
| --- | --- | --- |
| `/` | 站点介绍、近期文章 | `/api/v1/site` + `/api/v1/sections/ARCHIVE?page=0&size=6` |
| `/archive` | 文章归档 | `/api/v1/sections/ARCHIVE` |
| `/notes` | 随笔 | `/api/v1/sections/NOTES` |
| `/lab` | 项目 | `/api/v1/sections/LAB` |
| `/p/{path}` | 文章正文及上下篇 | `/api/v1/posts/{path}` + `/api/v1/posts/{path}/neighbors` |
| `/about` | 关于与友链 | `/api/v1/about` + `/api/v1/links` |

`/api/v1/posts` 返回所有普通文章（ARCHIVE、NOTES、LAB），不含关于和友链。`/sections/{section}` 也支持 `ABOUT`、`LINKS`，但默认界面把它们合并在关于页。

## 字段和交互约定

`site` 包含 `name`、`description`、`author`、`url`、`github`、`email`、`navigation: [{label,path}]`。GitHub 和邮箱可能为空；为空时不显示对应链接。

列表响应是 `{ items, total, page, size }`。`page` 从 **0** 开始，默认 0；`size` 默认 20，范围 1–100；`page * size` 不超过 1,000,000。非法参数返回 400。下一页是否存在用 `(page + 1) * size < total` 判断。不要把列表为空视为错误。

摘要字段为 `plate`、`slug`、`section`、`title`、`description`、`date`、`wordCount`、`readMinutes`、`tags`、`canonicalPath`。详情额外包含 `bodyHtml`、`updated`。日期是 `YYYY-MM-DD` 或 null。`slug`、`description` 可能为空，`tags` 可以是 `[]`。

链接直接使用 `canonicalPath`。`plate` 是不透明标识，不要从中推导日期、路径或栏目。上下篇为 `{ newer, older }`，每一项是摘要或 null，范围限于当前栏目。

不存在和未发布内容都返回 404。错误是 `application/problem+json`，典型字段：

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "The requested content is unavailable.",
  "code": "NOT_FOUND"
}
```

参数错误的 `code` 为 `INVALID_ARGUMENT`。`about` 或 `links` 404 时可以显示空介绍/隐藏友链，整个关于页仍然可用。详情 404 要显示明确的找不到页面状态；SSR 页面也应返回 HTTP 404。网络错误应提供重试入口，不能把旧文章一直保留为当前内容。

## 正文、图片与缓存

`bodyHtml` 已由后端 Markdown 渲染器生成，源 HTML 被转义。React 使用 `dangerouslySetInnerHTML`、Vue 使用 `v-html`、Astro 使用 `set:html` 时，仅注入这个后端返回的字段。不要把搜索参数或任意外站 HTML 混入正文。

正文需要完整支持标题、引用、列表、任务列表、链接、图片、代码块、表格。图片 `max-width: 100%` 且保持比例；`pre` 和宽表格可横向滚动，整个页面不能因此溢出。图片 alt、段落内链接与代码转义应保留。

**推荐同源反向代理**：前端域名下的 `/api/v1/` 和 `/assets/` 转到后端。这样 `bodyHtml` 中的 `/assets/...` 自然可用，浏览器也不需要 CORS。

必须跨域时：

1. 后端 `.env` 设置 `CORS_ORIGINS=https://frontend.example`，多个精确来源用逗号分隔，不带路径和尾部 `/`，然后重建应用容器配置。
2. 客户端 `baseUrl` 设置后端**来源**（如 `https://api.example`），不要附加 `/api/v1`。
3. 使用 DOM 解析转换正文中的后端图片路径。仅设置 fetch 的 base URL 不会改变 HTML 图片来源。

```javascript
import { createBlogClient, resolveAssetUrls } from './client/blog-client.mjs';

const backendOrigin = 'https://api.example';
const api = createBlogClient({ baseUrl: backendOrigin });
const post = await api.post('start-with-a-note');
document.querySelector('h1').textContent = post.title;
document.querySelector('.prose').innerHTML = resolveAssetUrls(post.bodyHtml, backendOrigin);
```

`resolveAssetUrls` 用浏览器 `DOMParser` 解析，只修改形如 `/assets/{16位小写哈希}.{扩展名}` 的 `img[src]`。外站、协议相对 URL、data URL、正文文字和链接不会被加前缀。不要对整段 HTML 做字符串替换。此 helper 不是通用净化器。SSR 环境没有 DOMParser，推荐使用同源代理；若一定要转换，可在浏览器挂载阶段调用，或传入适用于服务器的 HTML DOM parser。

JSON API 和图片均返回 ETag 与 `Cache-Control: no-cache`。这是**每次使用前重新验证**，允许条件请求返回 304；不是禁用全部缓存。客户端内存中只保留带 ETag 的成功响应，下次发送 `If-None-Match`，收到 304 使用原内容；404/错误会移除该请求的缓存。浏览器也可能把 304 合成为缓存的 200。`clearCache()` 可主动丢弃客户端缓存。

不要给 `/assets/` 加永久 `immutable` 缓存：图片在最后一篇引用文章撤下后会变成 404。CDN、Service Worker 或静态生成也必须遵守刷新/失效规则。Astro 静态构建的内容只在重新构建后更新；需要实时同步时使用 SSR 或客户端获取，并自行安排重建触发。

## 两条改造路径

### 路径 A：原地改默认主题

入口：`app/src/main/resources/templates/` 与 `app/src/main/resources/static/css/site.css`。Java 控制器已经准备好页面数据。

| 模板 | 主要模型字段 |
| --- | --- |
| fragments.html | 全局 `site`、`pageTitle`、`pageDescription`、`canonicalUrl`、`activePath` |
| home.html | `items`、`total` |
| listing.html | `items`、`page`、`heading`、`listingPath`、`hasPrevious`、`hasNext`、页码 |
| post.html | `post`、`neighbors`、`sectionPath`、`sectionLabel` |
| about.html | 可空的 `about`、`links` |
| error/404.html | 页面元数据 |

主题变量包括 `--color-background`、`--color-surface`、`--color-text`、`--color-muted`、`--color-border`、`--color-accent`、`--font-body`、`--font-mono`、`--space-*`、`--width-page`、`--width-reading`。优先从这些变量调整整体视觉。

复制给 AI：

```text
请改造这个 Obsidian Blog Starter 的默认 Thymeleaf 前端。
先阅读 docs/FRONTEND_AI.md、docs/openapi.yaml 和当前 templates、site.css。
我的风格要求：[填写颜色、字体、布局、参考图、希望保留的元素]。
页面包括首页、归档、随笔、项目、文章详情、关于、空状态和 404。
保留现有模型字段、URL、分页与发布边界，使用 CSS 变量统一视觉。
正文必须覆盖图片、代码、表格、长标题、空标签及无上下篇的情况。
请直接实现并验证桌面/手机宽度、键盘焦点、文字对比度、标题层级。
完成后列出改动文件、预览方法与实际验证结果。
```

### 路径 B：独立前端

`client/blog-client.mjs` 无依赖，可直接复制到项目中：

```javascript
import { createBlogClient, BlogApiError } from './blog-client.mjs';
const api = createBlogClient(); // 同源代理；也可注入 fetch 供测试/SSR 使用
const site = await api.site();
const page = await api.section('ARCHIVE', { page: 0, size: 20 });
try {
  const post = await api.post('hello-world');
} catch (error) {
  if (error instanceof BlogApiError && error.status === 404) {
    // 渲染找不到页面。
  } else {
    throw error;
  }
}
```

示例 `examples/plain-frontend` 已演示路由、分页、正文、上下篇、关于页、404 和同源代理。执行 `node examples/plain-frontend/server.mjs` 即可体验。它是开发示例服务器，生产环境使用自己的 Web Server/SSR 部署；SPA 直接访问深层路由时需要回退到 `index.html`。

复制给 AI：

```text
请为已有博客后端实现一个独立 [React / Vue / Astro / 纯 HTML] 前端。
先阅读 docs/openapi.yaml、docs/FRONTEND_AI.md、client/blog-client.mjs，
用 docs/fixtures 中的普通、图片、代码、表格、长标题、空列表和 404 样本设计。
视觉要求：[填写]。
实现 /、/archive、/notes、/lab、/p/:path、/about，数据映射按指南。
使用现有公开 API；文章链接使用 canonicalPath，不自行解析 Markdown。
开发与生产都安排 /api/v1/ 和 /assets/ 同源代理；如跨域，使用 DOM helper 处理图片。
保留分页、上下篇、空状态、404、网络重试、移动端、键盘访问与基础 SEO。
提供本地运行、后端地址配置、生产部署说明，并实际验证后汇报。
```

验收时至少打开普通文章、`images-and-space`、`code-and-tables`、`a-long-title`、无内容的栏目和不存在的路径。再把公开文章取消发布并提交，确认刷新后详情和已失去公开引用的图片不再展示。图片撤下验证请用独立测试副本，避免修改自己的正式内容。
