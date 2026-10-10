# 部署与运行

演示启动见 [README](../README.md)。本指南涵盖本地 Vault、服务器拉取、Webhook、反向代理和维护。

## 运行结构与配置

Compose 启动 app、MySQL 8.4、Redis 7.4。MySQL/Redis 不开放宿主机端口，数据分别存于命名卷。Java 17 和 Maven 位于多阶段镜像构建中；运行镜像保留 system Git、SSH 和 curl。应用进程使用 UID/GID 10001，启动时 Flyway 自动执行数据库迁移。

| 配置 | 用途 / 默认 |
| --- | --- |
| `HOST_BIND` / `PORT` | 宿主机监听地址和端口，`127.0.0.1:8080` |
| `DB_PASSWORD` / `MYSQL_ROOT_PASSWORD` | 应用数据库账号和 MySQL root 密码，必须配置且不同 |
| `REDIS_PASSWORD` | Redis 密码，可空；演示脚本会生成 |
| `SITE_NAME` / `SITE_DESCRIPTION` / `SITE_AUTHOR` | 站点标题、介绍、作者 |
| `SITE_URL` | 完整公开来源，如 `https://blog.example.com` |
| `SITE_GITHUB` / `SITE_EMAIL` | 可选外链和邮箱 |
| `PUBLISH_ROOT` | Vault 中的仓库相对发布根目录，默认 `博客` |
| `CORS_ORIGINS` | 允许访问 API 的精确来源，逗号分隔，无尾斜杠；同源留空 |
| `SYNC_FETCH_ENABLED` | 是否由应用拉取 Git，默认 false；生产覆盖文件强制 true |
| `SYNC_STARTUP_ENABLED` | 启动时完整同步，默认 true |
| `SYNC_POLL_ENABLED` | 定时完整同步，默认 true |
| `SYNC_POLL_INTERVAL` / `SYNC_POLL_INITIAL_DELAY` | ISO-8601 时长，默认 `PT10M`；演示为 `PT5S` |
| `VAULT_WEBHOOK_SECRET` | HMAC 密钥；留空禁用 Webhook |

Compose 内部固定数据库 `blog`、用户 `blog`、Redis `redis:6379`、Vault `/vault`、HTTP `8080`。直接运行 JAR 时对应变量是 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`VAULT_DIR`、`PORT`；默认 JDBC URL 见 `application.yaml`。

首次初始化数据库后，MySQL 镜像不会根据新 `.env` 自动重设已有账户密码。轮换凭据时需要同时修改数据库账户及应用配置。不要通过删除卷来解决普通密码问题。

配置修改后执行 `docker compose up -d`，代码/模板修改后执行 `docker compose up --build -d --wait`。只有 `restart` 不会载入新的环境变量配置。

## 本地 Vault 模式

`data/vault` 必须是完整 Git 仓库，至少有一个提交。可以继续使用演示副本，也可以在首次启动前把自己的仓库完整克隆到这个路径。不要直接覆盖已经存在的目录。不要用只有 `.git` 指向宿主机其他位置的 worktree，否则容器内可能无法读取 Git 对象。

默认 Compose 把它只读挂载到 `/vault`，`SYNC_FETCH_ENABLED=false`。在宿主机完成编辑、Git 提交或拉取，应用会读取提交快照。启动和每次定时同步都会完整核对内容，即使 HEAD 未变，也能更新后补图片引用和清理失效记录。

首次同步不完整时应用会启动失败；运行中某些文章解析失败时会记录日志，并在后续定时周期重试。`/actuator/health` 用于检查数据库与 Redis 可用性，不提供最近一次内容同步详情。同步问题应查看应用日志。

修改 `PUBLISH_ROOT` 之前，先把 Vault 内容移动并提交到新目录，然后更新 `.env`、图片完整路径并重建 app 容器。完整同步会撤下旧根目录中不再符合当前范围的内容。

## Linux 服务器：GitHub 独立部署副本

服务器克隆博客应用仓库后，在仓库根目录操作。服务器上的 Vault 是**独立完整部署副本**，不在其中写作。一个 Vault/数据库只运行一个 app 实例。

1. 复制 `.env.example` 到 `.env`，逐个替换示例密码。可用 `openssl rand -hex 24` 在服务器生成随机值。设置真实 `SITE_URL`，保留 `SYNC_STARTUP_ENABLED=true`，生产定时周期使用 `PT10M`。不要提交 `.env`。
2. 为这个 Vault 单独创建无口令 Deploy Key，将公钥添加到 GitHub 仓库的 Settings → Deploy keys，**不勾选 Allow write access**。不要复用个人 SSH 私钥。

```sh
mkdir -p secrets data
chmod 700 secrets
ssh-keygen -t ed25519 -f secrets/vault_key -N '' -C 'blog-vault-readonly'
```

3. 准备 GitHub host key。下面命令收集候选值；必须把输出指纹与 [GitHub 官方 SSH 指纹](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints) 比对，确认后再继续。不要把 `ssh-keyscan` 的结果自动视为可信，也不要关闭 host key 检查。

```sh
ssh-keyscan github.com > secrets/vault_known_hosts
ssh-keygen -lf secrets/vault_known_hosts
```

4. 使用新 Deploy Key 完整克隆，替换下面的仓库地址：

```sh
GIT_SSH_COMMAND="ssh -i $PWD/secrets/vault_key -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$PWD/secrets/vault_known_hosts" git clone git@github.com:YOUR_ACCOUNT/YOUR_VAULT.git data/vault
git -C data/vault status --short --branch
```

确认当前分支有 upstream（普通 `git clone` 默认配置）。容器中 system Git 将执行 `git fetch --prune origin` 后 `git merge --ff-only @{u}`。它不会 reset、不强推，不会覆盖分叉或本地修改。遇到这些情况同步停止并记录错误，先检查部署副本状态。

5. 赋予容器应用用户读取密钥、写入部署副本的权限。以下是 Docker 标准 Linux UID 映射示例；启用 rootless/user namespace 时按实际映射设置：

```sh
sudo chown -R 10001:10001 data/vault
sudo chown 10001:10001 secrets/vault_key
sudo chmod 600 secrets/vault_key
chmod 644 secrets/vault_known_hosts
```

6. 启动生产覆盖配置：

```sh
docker compose -f compose.yaml -f compose.production.yaml up --build -d --wait
```

此配置只把单个私钥与 known_hosts 只读挂载，Vault 改为可写挂载，并启用拉取。不要把宿主机整个 `~/.ssh` 挂进去。以后更新、查看状态和停止时也使用相同的两个 `-f` 参数，避免意外退回本地只读配置。

Windows Docker Desktop 的文件权限映射与 Linux 不同；本地演示可直接使用 PowerShell 脚本，长期服务器部署建议按上面的 Linux 流程配置。

## Webhook 与定时拉取

无需 Webhook 也可以运行：保留定时同步即可。想让提交更快出现时，在 GitHub Vault 仓库设置：

- Payload URL：`https://blog.example.com/api/sync/webhook`。
- Content type：`application/json`。
- Secret：与 `VAULT_WEBHOOK_SECRET` 完全一致。
- Events：只选择 push。GitHub 初始 ping 也会被接受。

服务对**原始请求体**校验 `X-Hub-Signature-256`（HMAC SHA-256），读取 `X-GitHub-Event` 与 `X-GitHub-Delivery`。不要让中间代理重新序列化 JSON。delivery 标识用于去重，签名密钥为空时拒绝请求。

| HTTP 状态 | 含义 |
| --- | --- |
| 200 | `synced`、`already handled`、`pong` 或不处理的事件 `ignored` |
| 202 | 同步忙碌，或部分失败已交由后续定时同步重试 |
| 400 | delivery 标识无效 |
| 401 | 签名无效或 Webhook 未启用 |
| 500 | 同步失败，查看服务器日志 |

Webhook 成功只代表服务器处理结果，不能用于推导每篇文章一定公开。定时同步是 webhook 丢失、忙碌和部分失败时的补偿，生产中应保持开启。

## HTTPS 与独立前端

默认只监听 `127.0.0.1:8080`。同机 Nginx/Caddy 可代理到这个地址。示例 Caddy 配置（Caddy 的安装、域名 DNS 与证书由部署环境负责）：

```caddyfile
blog.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

如果反向代理也在容器中，可让它加入同一个 Docker 网络并代理 `app:8080`。只有需要外部直接访问宿主机端口时才设置 `HOST_BIND=0.0.0.0`，并按实际网络安排防火墙和 HTTPS。

独立前端建议同域代理 `/api/v1/` 和 `/assets/`，保留 ETag、Cache-Control、If-None-Match 与 304。Webhook 仍需单独转发 `/api/sync/webhook`（若启用）。其他路由交给前端服务器；SPA 深层路由需要回退首页。跨域方案见 [前端指南](FRONTEND_AI.md)。

不要给图片或 API 配置永久缓存：文章撤下后，其详情与失去公开引用的图片应重新验证为 404。

## 检查、备份与恢复

检查：

```sh
docker compose ps
docker compose logs --tail=100 app
docker compose exec app curl --fail http://localhost:8080/actuator/health
```

健康端点只有状态，不公开组件配置和细节。演示 Vault 可以运行 `sh scripts/verify-demo.sh`；正式内容的 slug 不同，不适用这套演示断言。

应备份完整 Vault Git 仓库、MySQL、`.env` 和部署密钥。数据库虽然可重新从内容生成，但保留数据库能保留稳定 `plate` 和历史状态。Redis 用于同步锁，正常停机后可重建；公开内容的 HTTP 缓存通过 ETag 重新验证。下面是 Linux 下数据库逻辑备份示例：

```sh
mkdir -p backups
chmod 700 backups
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --user=root --single-transaction --no-tablespaces blog' > backups/blog.sql
```

恢复到已初始化的数据库前，先停止 app，确认目标库，再导入：

```sh
docker compose stop app
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root blog' < backups/blog.sql
docker compose up -d --wait app
```

生产覆盖部署在这些命令里也加上两个 `-f` 参数。备份应离线或加密保存；密钥副本不要放进应用仓库。升级前先备份，保留旧镜像；Flyway 迁移可能改变数据库结构，不能假设只换旧镜像就能回滚数据库。

## 常见问题

| 现象 | 检查 |
| --- | --- |
| 保存后网页没有更新 | 是否在 Vault 中 Git 提交、是否在允许路径、有无 `publish: true`、是否等过同步周期 |
| app 启动失败 | MySQL/Redis 是否健康；Vault 是否完整 Git 仓库；启动同步日志是否有无法读取文件、重复 slug 或缺图 |
| 图片不显示 | 图片是否已提交、完整路径是否正确、是否属于当前发布范围、有无公开文章引用 |
| 拉取失败 | key/known_hosts 权限、Deploy Key 对应仓库、upstream、网络、分叉或工作区修改 |
| 环境变量改了没生效 | 使用 `docker compose up -d` 重新创建容器，不只 restart |
| 修改数据库密码后无法连接 | 已有数据库账户密码不会随 `.env` 自动改变 |
| 跨域 API 成功但正文图片失败 | 图片仍是根相对 URL，需要同源代理或 `resolveAssetUrls` |
| 重复运行 setup 被拒绝 | 保护现有数据的正常行为；用 `docker compose up --build -d --wait` 复用 |

故障处理中保留 Vault 和数据卷，先看日志及 Git 状态，再处理明确原因。
