# 部署到服务器

从一台空白服务器到公网可访问，按顺序走一遍。全程只用到 Docker，服务器上不需要装 JDK、Node 或 MySQL。

## 一、买服务器

| 项 | 建议 |
|---|---|
| 配置 | 2 核 2G 起，够跑四个容器（实测内存占用约 1G） |
| 系统 | Ubuntu 22.04 或 24.04 |
| 厂商 | 阿里云 / 腾讯云的轻量应用服务器，学生认证后更便宜 |

**安全组只放行这几个端口**：

- `22` SSH
- `80` 网站
- `443` HTTPS（上证书才需要）

`8080` 默认别开。要看接口文档时临时放开，看完关掉。

## 二、装 Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker
```

**国内必须配镜像源**，不然拉不动镜像：

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{
  "registry-mirrors": ["https://docker.1panel.live"]
}
EOF
sudo systemctl restart docker
```

验证：`docker info --format "{{json .RegistryConfig.Mirrors}}"` 应该输出上面那个地址。

## 三、拉代码

```bash
git clone https://gitee.com/LUO__MINGYu00/campus-activity-platform.git
cd campus-activity-platform
```

用 HTTPS 拉会提示输账号密码。想免密就在服务器上生成 SSH key（`ssh-keygen -t ed25519`），把公钥加到 Gitee 的 SSH 公钥设置里，然后换 SSH 地址拉。

## 四、写配置

```bash
cp .env.example .env
openssl rand -base64 48        # 生成一个随机密钥，复制下来
nano .env
```

`.env` 里要改两个值：

- `MYSQL_ROOT_PASSWORD` — 换成强密码
- `JWT_SECRET` — 粘贴上一步生成的随机串

`WEB_PORT` 在服务器上保持 `80`，不用动；本机 80 被占用时才改成 8000。

`.env` 已经被 `.gitignore` 排除，不会进仓库。

**nano 怎么用**：方向键移动光标，直接改内容，改完按 `Ctrl+O` 回车保存，再按 `Ctrl+X` 退出。页面底部一直显示着这些快捷键，忘了抬头看就行。

如果你手滑用了 `vi` 打不开局面：按 `Esc`，然后输入 `:q!` 回车，就能不保存强退出来。

## 五、启动

```bash
docker compose up -d --build
```

第一次会拉基础镜像并编译前后端，视网络大概 5 到 15 分钟。看到四个容器都 Started 就好了：

```bash
docker compose ps
```

MySQL 和 Redis 显示 `healthy` 才算真的就绪——后端是等它们健康之后才启动的。

## 六、验证

```bash
curl -I http://localhost                        # 期望 200
curl http://localhost/api/activities            # 期望 {"code":0,...,"records":[]}
```

然后在浏览器打开 `http://你的服务器IP`，用 `admin / admin123` 登录管理端，建一场活动试试。

**记得改掉默认密码**，或者至少把 `admin` 账号删掉。演示账号是给本机开发用的，公网上留着等于门没锁。

## 七、域名和 HTTPS（可选）

没有域名也能跑，直接用 IP 访问。本项目里二维码存的就是当前访问地址，用 IP 一样能扫。

有域名的话：

1. 把域名解析到服务器 IP
2. 服务器上用 certbot 签证书，或者用宝塔面板图形化操作
3. 签完之后有两个做法：
   - **简单做法**：宿主机再跑一个 Nginx 占 80/443 终止 HTTPS，反代到容器的 `127.0.0.1:WEB_PORT`，同时把 compose 里的 `WEB_PORT` 改成 `127.0.0.1:8000`
   - **彻底做法**：把证书挂进 `web` 容器，在 `deploy/nginx.conf` 里加一个 443 的 server 块

## 八、日常运维

**看日志**

```bash
docker compose logs -f backend      # 跟后端
docker compose logs --tail 100 web  # 看最近 100 行
```

**备份数据库**

```bash
docker compose exec -T mysql mysqldump -uroot -p'你的密码' activity_platform > backup-$(date +%F).sql
```

建议加个 crontab 每天跑一次，否则容器一删数据就没了。

**更新代码**

```bash
git pull                          # 拉最新代码
docker compose up -d --build      # 重建并重启
```

数据在 Docker volume 里，重建容器不会丢。`.env` 被 git 忽略，`git pull` 不会覆盖你在服务器上填的真实密码。

几条要知道的：

- **只改了代码、没改依赖时，`git pull` 之后 `--build` 会很快**——Docker 的层缓存会命中，通常十几秒。
- **构建失败不会影响正在跑的服务**。compose 会先构建，构建成功才替换容器，所以构建炸了旧版本还在运行，不会把线上搞挂。
- **改回旧版本**：

  ```bash
  git log --oneline          # 找到要回退到哪个提交
  git checkout <提交号>
  docker compose up -d --build
  ```

  想回到最新版就 `git checkout main && git pull`。

**改数据库结构要额外做一步（容易忽略）**

容器里的 MySQL 只在**数据目录为空时**执行 `sql/schema.sql`。库已经建好之后，你就算改了 `sql/schema.sql` 并重新部署，**表结构也不会变**——新加的字段根本不存在，后端一跑就报 `Unknown column`。

改表结构时要手动执行：

```bash
# 进 MySQL 手动改
docker compose exec mysql mysql -uroot -p activity_platform

# 或者把 ALTER 语句写成一个文件在宿主机上执行
docker compose exec -T mysql mysql -uroot -p activity_platform < 你的改动.sql
```

开发阶段如果数据不重要，最省事的办法是 `docker compose down -v` 清库重建，让 `schema.sql` 重新跑一遍。

**连进容器排查**

```bash
docker compose exec mysql mysql -uroot -p
docker compose exec redis redis-cli
docker compose exec backend sh
```

**清理磁盘**

构建缓存会越积越多，40G 的系统盘跑久了也会紧张。看一下占用：

```bash
df -h /
docker system df
```

清理没用的构建缓存和悬空镜像（不会动正在跑的容器和数据卷）：

```bash
docker builder prune -f
docker image prune -f
```

容器日志已经配了轮转（每个容器最多 30MB），不会出现日志把盘撑满的情况。

## 九、几个必须知道的坑

**改了 `MYSQL_ROOT_PASSWORD` 但数据库已经初始化过——密码不会变。**

`MYSQL_ROOT_PASSWORD` 只在数据库第一次创建时生效（也就是 volume 为空时）。之后再改 `.env`，MySQL 里还是老密码，后端会因为连不上而启动失败。

要么进容器手动改密码，要么接受教育成本直接清库重来：

```bash
docker compose down -v      # 连数据一起删
docker compose up -d --build
```

**接口文档默认对公网开放。**

`8080` 端口暴露着，任何人能打开 Swagger。平时把安全组里这条关掉，要用时再开。

## 十、这套配置还没做的事

如实记录，免得以为它已经是个能扛生产流量的部署：

- **后端用 root 连数据库**。更严谨的做法是建一个只对 `activity_platform` 有权限的专用账号。
- **单机单实例**。MySQL、Redis 和后端都在同一台机器上，机器挂了服务就没了。
- **没有做数据库自动备份**。上面给了命令，但要自己挂 crontab。
- **没有监控告警**。
