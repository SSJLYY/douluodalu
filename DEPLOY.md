# Rocky Linux 9 部署教程

从零开始在 Rocky 9 服务器上部署「斗罗大陆·放置传说」（MySQL + Spring Boot 后端 + Next.js 前端）。

## 目录

1. [服务器基础配置](#1-服务器基础配置)
2. [安装 Node.js 22](#2-安装-nodejs-22)
3. [安装 JDK 21、Maven 与 MySQL 8](#3-安装-jdk-21maven-与-mysql-8)
4. [安装 Git 并拉取项目](#4-安装-git-并拉取项目)
5. [安装 PM2 进程管理](#5-安装-pm2-进程管理)
6. [开放防火墙端口](#6-开放防火墙端口)
7. [构建并启动后端](#7-构建并启动后端)
8. [构建并启动前端](#8-构建并启动前端)
9. [配置 Nginx 反向代理](#9-配置-nginx-反向代理)
10. [配置 SSL 证书 (HTTPS)](#10-配置-ssl-证书-https)
11. [设置开机自启](#11-设置开机自启)
12. [常用运维命令](#12-常用运维命令)
13. [Docker 部署（未在本机验证）](#13-docker-部署未在本机验证)
14. [监控与备份（Docker 部署）](#14-监控与备份docker-部署)

---

## 1. 服务器基础配置

### 1.1 更新系统

```bash
# 更新所有软件包
dnf update -y

# 安装基础工具
dnf install -y curl wget vim tar gzip
```

### 1.2 设置时区

```bash
timedatectl set-timezone Asia/Shanghai
timedatectl status
```

### 1.3 创建应用目录

```bash
mkdir -p /opt/app
```

---

## 2. 安装 Node.js 22

Rocky 9 默认仓库中的 Node.js 版本较旧，使用 NodeSource 官方仓库安装 v22 LTS。

```bash
# 导入 NodeSource GPG 密钥
rpm --import https://rpm.nodesource.com/gpgkey/NODESOURCE-GPG-SIGNING-KEY-EL

# 添加 NodeSource v22 仓库
curl -fsSL https://rpm.nodesource.com/setup_22.x | bash -

# 安装 Node.js
dnf install -y nodejs

# 验证安装
node --version
npm --version
```

预期输出类似：
```
v22.14.0
10.9.2
```

---

## 3. 安装 JDK 21、Maven 与 MySQL 8

后端为 Spring Boot 3.2 (Kotlin)，需要 JDK（编译目标 17，建议直接装 21）与 Maven；数据层为 MySQL 8，建表由 **Flyway 在后端启动时自动完成**（V1~V12 迁移脚本），无需手动执行 SQL。

```bash
# JDK 21 + Maven
dnf install -y java-21-openjdk-devel maven
java -version && mvn -version

# MySQL 8（Rocky 9 使用 mysql 8.0 模块或官方仓库）
dnf install -y mysql-server
systemctl enable --now mysqld

# 初始化并设置 root 密码
mysql_secure_installation
```

创建数据库与专用账号：

```sql
CREATE DATABASE douluo_game DEFAULT CHARSET utf8mb4;
CREATE USER 'douluo'@'localhost' IDENTIFIED BY '强密码';
GRANT ALL PRIVILEGES ON douluo_game.* TO 'douluo'@'localhost';
FLUSH PRIVILEGES;
```

---

## 4. 安装 Git 并拉取项目

```bash
# 安装 Git
dnf install -y git

# 克隆项目
cd /opt/app
git clone https://github.com/SSJLYY/douluodalu.git
```

---

## 5. 安装 PM2 进程管理

PM2 用于守护 Node.js 进程（前端），同时也可托管 Java 进程（后端），提供自动重启、日志管理和开机自启。

```bash
# 全局安装 PM2
npm install -g pm2

# 验证安装
pm2 --version
```

---

## 6. 开放防火墙端口

Rocky 9 默认使用 `firewalld` 管理防火墙。后端仅由 Nginx 反代访问，**不需要**对外开放 8080。

```bash
# 检查防火墙状态
systemctl status firewalld

# 开放 HTTP (80) 和 HTTPS (443) 端口
firewall-cmd --permanent --add-service=http
firewall-cmd --permanent --add-service=https

# 重载防火墙使配置生效
firewall-cmd --reload

# 查看已开放规则
firewall-cmd --list-all
```

如果使用云服务器（阿里云/腾讯云等），还需在云控制台的**安全组**中放行 80、443 端口。数据库端口 3306 不要对公网开放。

---

## 7. 构建并启动后端

### 7.1 环境变量清单

后端全部通过环境变量配置（见 `backend/src/main/resources/application.yml`）：

| 变量 | 必填 | 说明 |
|------|------|------|
| `JWT_SECRET` | **是** | JWT 签名密钥，**必须 ≥ 32 字节**，否则生产环境启动即失败 |
| `DB_PASSWORD` | **是** | MySQL 密码（默认值 `change-me` 仅供本地） |
| `DB_USER` | 可选 | 默认 `root`，建议用专用账号 `douluo` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 可选 | 默认 `127.0.0.1` / `3306` / `douluo_game` |
| `CORS_ORIGINS` | **是** | 允许的跨域来源，逗号分隔，如 `https://your-domain.com` |
| `SERVER_PORT` | 可选 | 后端监听端口，默认 `8080` |
| `RATELIMIT_ENABLED` / `AUTH_RPM` / `GLOBAL_RPM` | 可选 | 限流开关与阈值（默认开，认证 10 RPM / 全局 300 RPM） |
| `JWT_EXPIRATION` | 可选 | token 有效期毫秒，默认 86400000（24h） |
| `FLYWAY_ENABLED` | 可选 | 默认 `true`，启动时自动执行迁移 |

### 7.2 打包并启动

```bash
cd /opt/app/douluodalu/backend
mvn -B package -DskipTests

# 生成一个强随机 JWT_SECRET（≥32 字节）
openssl rand -base64 48
```

建议把环境变量写入 `/opt/app/douluodalu/backend/env.sh`（权限 600，勿提交进 git）：

```bash
export DB_USER=douluo
export DB_PASSWORD='强密码'
export JWT_SECRET='上一步生成的≥32字节随机串'
export CORS_ORIGINS='https://your-domain.com'
```

```bash
# 用 PM2 托管
source /opt/app/douluodalu/backend/env.sh
pm2 start "java -jar /opt/app/douluodalu/backend/target/douluo-game-1.0.0.jar" --name douluo-backend
pm2 save
```

首次启动时 Flyway 自动建表（`users`、`player_profile`、`guild`、`audit_log` 等，迁移脚本 V1~V12）。验证：

```bash
curl http://127.0.0.1:8080/api/health
```

---

## 8. 构建并启动前端

前端 API 地址通过 `NEXT_PUBLIC_API_URL` 在**构建时**内联进产物，必须在 build 前配置好。

```bash
cd /opt/app/douluodalu/frontend

# 指向对外域名（由 Nginx 反代 /api 到后端，避免跨域）
echo 'NEXT_PUBLIC_API_URL=https://your-domain.com' > .env.local

npm install

# 生产构建（package.json 已固定 `next build --webpack`，
# 因仓库路径含中文时 Turbopack 会 panic，请勿改回）
npm run build

# 使用 PM2 启动
pm2 start npm --name "douluo" -- start

pm2 status
pm2 logs douluo
```

前端监听 `http://localhost:3000`。

---

## 9. 配置 Nginx 反向代理

### 9.1 安装 Nginx

```bash
dnf install -y nginx

# 启动并设置开机自启
systemctl enable nginx
systemctl start nginx
```

SELinux 开启时需允许 Nginx 反代本机端口：

```bash
setsebool -P httpd_can_network_connect 1
```

### 9.2 配置站点

```bash
vim /etc/nginx/conf.d/douluo.conf
```

写入以下配置（将 `your-domain.com` 替换为实际域名）：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 日志
    access_log /var/log/nginx/douluo_access.log;
    error_log /var/log/nginx/douluo_error.log;

    # 后端 API：/api/** 反代到 Spring Boot (8080)
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket (STOMP /ws)
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }

    # Swagger 文档如需对外可放开，否则建议删除此段
    # location /swagger-ui.html { proxy_pass http://127.0.0.1:8080; }

    # 反向代理到 Next.js
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }

    # 静态资源缓存
    location /_next/static {
        proxy_pass http://127.0.0.1:3000;
        proxy_cache_valid 200 1d;
        add_header Cache-Control "public, max-age=86400";
    }
}
```

注意：`/api/` 的 `proxy_pass` **不要**在末尾加 `/`，保留原始路径传给后端（Controller 自带 `/api` 前缀）。

### 9.3 验证并重载配置

```bash
# 检查配置语法
nginx -t

# 重载 Nginx
systemctl reload nginx
```

完成后通过 `http://your-domain.com` 即可访问。

---

## 10. 配置 SSL 证书 (HTTPS)

使用 Let's Encrypt 免费证书。`CORS_ORIGINS` 与 `NEXT_PUBLIC_API_URL` 均使用 `https://` 域名。

### 10.1 安装 Certbot

```bash
dnf install -y epel-release
dnf install -y certbot python3-certbot-nginx
```

### 10.2 申请证书

```bash
# 将 your-domain.com 替换为实际域名
certbot --nginx -d your-domain.com

# 按提示输入邮箱并同意条款
```

Certbot 会自动修改 Nginx 配置，添加 HTTPS 重定向。

### 10.3 验证自动续期

```bash
# 测试证书自动续期（不会真正续期）
certbot renew --dry-run
```

Certbot 会通过 systemd timer 自动续期，无需手动操作。可以确认 timer 状态：

```bash
systemctl status certbot-renew.timer
```

---

## 11. 设置开机自启

```bash
# PM2 生成启动脚本（同时托管前后端两个进程）
pm2 startup

# 执行上述命令输出的指令（会提示复制粘贴一条 systemd 命令）
# 例如：
# env PATH=$PATH:/usr/bin pm2 startup systemd -u root --hp /root

# 保存当前进程列表
pm2 save
```

验证：

```bash
# 查看 PM2 是否已注册为 systemd 服务
systemctl status pm2-root
```

---

## 12. 常用运维命令

### PM2 管理

```bash
pm2 status                    # 查看所有应用状态
pm2 logs douluo               # 查看前端日志
pm2 logs douluo-backend       # 查看后端日志
pm2 restart douluo            # 重启前端
pm2 restart douluo-backend    # 重启后端
pm2 stop douluo               # 停止应用
pm2 delete douluo             # 删除应用
```

### 更新代码后重新部署

```bash
cd /opt/app/douluodalu
git pull

# 后端：重新打包并重启（Flyway 自动执行新增迁移）
source backend/env.sh
cd backend && mvn -B package -DskipTests && cd ..
pm2 restart douluo-backend

# 前端：如 NEXT_PUBLIC_API_URL 有变必须重新 build（构建时内联）
cd frontend
npm install
npm run build
pm2 restart douluo
```

### Nginx 管理

```bash
systemctl status nginx    # 查看状态
systemctl reload nginx    # 重载配置（不中断服务）
systemctl restart nginx   # 重启
nginx -t                  # 检查配置语法
```

### 查看日志

```bash
# Nginx 访问/错误日志
tail -f /var/log/nginx/douluo_access.log
tail -f /var/log/nginx/douluo_error.log

# PM2 应用日志
pm2 logs douluo --lines 100
pm2 logs douluo-backend --lines 100
```

### 数据库备份

数据库为 MySQL 8（不再是 SQLite），使用 mysqldump 定时备份：

```bash
mysqldump -u douluo -p'强密码' douluo_game \
  > /opt/backup/douluo_game.$(date +%Y%m%d).sql

# 恢复
mysql -u douluo -p'强密码' douluo_game < /opt/backup/douluo_game.20260924.sql
```

### 防火墙管理

```bash
firewall-cmd --list-all     # 查看当前规则
firewall-cmd --reload       # 重载规则
```

### Prometheus 监控

后端通过 `micrometer-registry-prometheus` 暴露指标端点 `GET http://127.0.0.1:8080/actuator/prometheus`（文本格式）。

- 非生产环境：默认匿名可访问（`app.monitoring.prometheus-public=true`），本地 `curl` 即可验证。
- 生产环境（`SPRING_PROFILES_ACTIVE=prod`）：默认不放行且 exposure 仅 health，双保险关闭；如需抓取，在 env.sh 加 `export APP_MONITORING_PROMETHEUS_PUBLIC=true`（Spring 宽松绑定映射 `app.monitoring.prometheus-public`），并仅限内网/反代 ACL 访问。

Prometheus 服务端 `prometheus.yml` 抓取片段示例：

```yaml
scrape_configs:
  - job_name: douluo-backend
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
    static_configs:
      - targets: ['your-server:8080']
```

---

## 13. Docker 部署（未在本机验证）

> ⚠️ **诚实声明**：以下 Dockerfile 与 compose 编写机上没有 Docker，**未经 `docker build` / `docker compose up` 实跑验证**（Linux 上首次部署请逐条 review，尤其后端 stage-2 的 curl 安装与 jar 名 `douluo-game-*.jar`）。前端镜像的 standalone 产物已在本地用 `node server.js` 冒烟验证通过（`/`、`/game` 均 200），构建流程与 CI 一致。

仓库根目录已提供：`backend/Dockerfile`、`frontend/Dockerfile`、两个 `.dockerignore`、`docker-compose.yml`、`.env.example`。

### 13.1 前置：安装 Docker 与 Compose 插件

```bash
dnf install -y yum-utils
yum-config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable --now docker
```

### 13.2 配置环境变量

```bash
cd /opt/app/douluodalu
cp .env.example .env
chmod 600 .env
vim .env
```

必填项（缺失时 `docker compose` 会直接报错拒绝启动）：

| 变量 | 说明 |
|------|------|
| `MYSQL_ROOT_PASSWORD` | mysql 容器 root 密码 |
| `DB_PASSWORD` | 业务账号 `douluo` 的密码（compose 会创建库 `DB_NAME`，Flyway 自动建表） |
| `JWT_SECRET` | **必须 ≥ 32 字节**，用 `openssl rand -base64 48` 生成 |
| `CORS_ORIGINS` | 后端允许的跨域来源 |
| `NEXT_PUBLIC_API_URL` | 前端**构建时内联**的后端地址——改它必须加 `--build` 重新构建镜像 |

### 13.3 构建并启动

```bash
docker compose up -d --build
docker compose ps        # 三个服务应为 running (healthy)
docker compose logs -f backend
```

启动顺序由 healthcheck 串联：mysql 健康后才起 backend，backend `/api/health` 通过后（约 60s start_period）才起 frontend。

### 13.4 验证

```bash
curl http://127.0.0.1:8080/api/health          # 后端：{"status":"ok",... database:"UP"}
curl -I http://127.0.0.1:3000                  # 前端：200
docker inspect --format='{{.State.Health.Status}}' douluo-mysql douluo-backend douluo-frontend
```

浏览器访问 `http://<服务器IP>:3000`。对外服务仍建议按第 9、10 节配 Nginx + HTTPS，`NEXT_PUBLIC_API_URL` / `CORS_ORIGINS` 换成 https 域名后 `docker compose up -d --build frontend`。

### 13.5 常用操作

```bash
docker compose restart backend                 # 只重启后端
docker compose build frontend && docker compose up -d frontend   # 改了地址重新构建前端
docker compose down                            # 停止（保留数据卷）
docker volume rm douluodalu_mysql-data         # 彻底清库（危险）
docker exec -it douluo-mysql mysqladmin shutdown   # 手动关 mysql
# 备份：docker exec douluo-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" douluo_game' > backup.sql
```

---

## 14. 监控与备份（Docker 部署）

监控栈由 compose 的 `monitoring` profile 隔离，默认 `up` 不启动：

```bash
docker compose --profile monitoring up -d     # 连带业务容器一起启动监控栈
```

| 服务 | 地址 | 说明 |
|------|------|------|
| Prometheus | http://127.0.0.1:9090 | 抓 `backend:8080/actuator/prometheus`（15s），告警规则在 `ops/prometheus/alerts.yml` |
| Grafana | http://127.0.0.1:3001 | 默认 admin/admin（首登改密，或在 `.env` 设 `GRAFANA_ADMIN_PASSWORD`） |

两端口默认只绑回环，公网访问请走 Nginx 反代 + ACL。Grafana 由 provisioning 自动注入 Prometheus 数据源与「斗罗大陆 · 放置传说 总览」仪表盘（`ops/grafana/douluo-dashboard.json`，11 面板）；未挂载 provisioning 时可手动 import 该 JSON。告警共 5 条：InstanceDown（up=0 持续 1m，critical）、HighErrorRate（5xx >5% 持续 5m，critical）、HighLatencyP99（P99 >2s 持续 10m，warning）、JvmMemoryPressure（老年代 >85% 持续 10m，warning）、DbPoolExhausted（Hikari pending >0 持续 5m，critical），阈值理由见 alerts.yml 注释。

### 业务指标契约（Micrometer 计数器，逐字使用）

| 指标名 | tag | 含义 |
|------|-----|------|
| `douluo_battle_total` | `outcome=win\|lose` | 战斗次数（按胜负分） |
| `douluo_checkin_total` | — | 每日签到 |
| `douluo_checkin_makeup_total` | — | 补签 |
| `douluo_awaken_total` | `kind=first\|reawaken` | 觉醒（首次/再觉醒） |
| `douluo_prestige_total` | — | 转生 |
| `douluo_school_choose_total` | — | 流派选择 |
| `douluo_quest_claim_total` | `quest_id` | 任务领取（按任务分） |

### 数据库备份（Docker 部署）

```bash
./ops/backup/backup.sh     # 手动执行：mysqldump+gzip 输出 /opt/backup/douluo，默认保留 7 天
```

cron 每日 03:00 自动备份：
```
0 3 * * * cd /opt/app/douluodalu && ./ops/backup/backup.sh >> /var/log/douluo-backup.log 2>&1
```

保留天数由 `BACKUP_RETAIN_DAYS` 控制（默认 7），恢复命令见 `ops/backup/backup.sh` 头注释；PM2 部署形态的备份见第 12 节。指标契约与启用细节另见 `ops/README.md`。

