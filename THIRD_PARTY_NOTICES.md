# 第三方软件说明

MIT 许可覆盖本仓库原创部分，不重新许可第三方软件。Maven 和 Docker 会下载各依赖及其传递依赖；对应 JAR、源码和镜像中的 LICENSE/NOTICE 文件仍然有效。

主要直接依赖与运行组件如下。准确版本以 `app/pom.xml`、Spring Boot 依赖管理与 Compose 镜像标签为准；下表不替代完整依赖清单。

| 组件 | 主要许可 | 项目 |
| --- | --- | --- |
| Spring Boot / Spring Framework / Spring Security / Spring Data | Apache-2.0 | https://spring.io/projects |
| Thymeleaf | Apache-2.0 | https://www.thymeleaf.org/ |
| MyBatis-Plus / MyBatis | Apache-2.0 | https://github.com/baomidou/mybatis-plus |
| MySQL Connector/J | GPL-2.0 with Universal FOSS Exception | https://dev.mysql.com/doc/connector-j/en/ |
| Flyway Community | Apache-2.0（所用开源模块；以分发许可为准） | https://github.com/flyway/flyway |
| Eclipse JGit | EDL-1.0（BSD-3-Clause） | https://www.eclipse.org/jgit/ |
| commonmark-java 及扩展 | BSD-2-Clause | https://github.com/commonmark/commonmark-java |
| Lombok | MIT | https://projectlombok.org/ |
| Testcontainers Java | MIT | https://java.testcontainers.org/ |
| Maven | Apache-2.0 | https://maven.apache.org/ |
| Eclipse Temurin / OpenJDK | GPL-2.0 with Classpath Exception | https://adoptium.net/ |
| MySQL Community Server 8.4 | GPL-2.0 | https://www.mysql.com/ |
| Redis 7.4 | RSALv2 或 SSPLv1（双许可） | https://redis.io/legal/licenses/ |
| Git | GPL-2.0 | https://git-scm.com/ |
| curl | curl license | https://curl.se/docs/copyright.html |
| OpenSSH | BSD 系许可，详见源码 | https://www.openssh.com/ |

容器还包含 Ubuntu/Alpine 系统软件，各自许可见镜像内软件包说明。Redis 7.4 并非 MIT/Apache 许可软件；涉及再分发或提供托管服务时应查阅其许可原文。

可以执行 `mvn -f app/pom.xml dependency:tree` 检查实际依赖图。示例插图由本项目以 SVG 几何图形绘制，随项目按 MIT 许可发布；默认模板未引入外部照片、字体或图标包。Obsidian 为第三方产品名称，本项目不是其官方产品。

`docs/screenshots/customization.png` 是原个人网站的视觉展示，属于展示例外，不在本模板的 MIT 再利用授权范围内。它不是第二套可复用主题，原站设计、图片及其他素材的权利不因展示而转让。仓库原创模板代码与 `example-vault` 示例（包括 demo.svg）按 MIT 授权。
