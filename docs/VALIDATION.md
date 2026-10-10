# 复现验证记录

验证日期：2026-10-09。此记录针对本目录源码；源码仓库为 [vansye/obsidian-blog](https://github.com/vansye/obsidian-blog)，未部署到真实服务器。

## 已完成

| 检查 | 结果 |
| --- | --- |
| `mvn -f app/pom.xml -Pintegration verify` | 200 项单元测试、6 项集成测试通过，无跳过 |
| JavaScript 客户端、示例代理、初始化保护 | 综合 Node 测试 10 项通过；后续代理异常 URL 与可空元信息修正另经相关测试、语法和浏览器检查 |
| PowerShell / Git Bash 初始化 | 均验证凭据生成、UTF-8 中文、保留已有 `.env`、拒绝覆盖 Vault；Git Bash 2 项保护测试通过 |
| Docker Compose 首次启动 | 镜像从源码构建，空 MySQL 自动迁移，app/MySQL/Redis 全部健康 |
| 原目录之外启动 | 仅复制分发源码到系统临时目录；新建 Vault、凭据、数据卷，以独立 Compose 项目成功启动 |
| 独立副本内容生命周期 | 新增文章 200、正文更新生效、撤回文章 404、携带旧 ETag 请求已撤回附件仍为 404 |
| 独立副本重启 | 健康 UP，6 篇原示例正常，撤回文章保持 404 |
| 默认前端 | 首页、归档、随笔、项目、关于、文章、空分页与 404 经 HTTP/模板测试 |
| 手机浏览器 | 390px 宽度下长标题、表格和代码无横向溢出；图片实际加载；键盘焦点可到“跳到正文” |
| 独立前端 | 实际代理读取 API 和图片；真实 DOM 测试确认根相对附件 URL 转换，代码文本及外站 URL 保持原样 |
| 文档截图 | `screenshots/default.png` 与 `screenshots/mobile.png` 来自新默认界面；`screenshots/customization.png` 仅为原站定制效果展示 |

集成测试使用临时 Git 工作树与 Testcontainers MySQL 8.4、Redis 7.4，覆盖签名、去重、公开字段、参数错误、CORS、ETag/304、发布排除、元信息更新、附件撤回、不同扩展名的相同字节、部分失败下的撤回，以及重启切换发布根。首次同步完成时间早于 HTTP 服务开始接受请求。

## 范围与已知提示

- 本地验证环境为 Windows、Java 17、Docker Desktop。Shell 初始化也在 Git Bash 下执行；未在独立 macOS/Linux 主机上做整套部署验收。
- GitHub Deploy Key、真实 GitHub Webhook 网络投递与公网 HTTPS 需要部署者的内容仓库和域名；本次验证了签名请求与本地处理链路，未配置真实部署密钥。
- 默认 Docker/Testcontainers 命令不需要临时 API 参数。Testcontainers 固定为 1.21.4，已验证与本机 Docker 29 配合。
- 本机全局 Maven settings 有既存 `mirrors` 标签警告；项目未修改它。Docker 内的 Maven 构建不使用该文件。
- Flyway 在 MySQL 8.4 上输出“高于声明测试版本”的提示；空库迁移、读写与重启验证实际通过。升级数据库或迁移依赖时仍应运行集成测试。
- 临时复现容器已停止；独立测试数据卷和临时源码副本保留在本机。主演示工作区中的 `.env`、`data/`、`app/target/` 均为本地运行产物，不进入源码包。

代码、接口文档、规则与本地运行行为已核对。原博客服务、实际个人 Vault 和平台长期记忆不在这次脚手架实施的修改范围内。
