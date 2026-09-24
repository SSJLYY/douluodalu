# 斗罗大陆·放置传说 (Douluo Continent Idle Legend)

修仙放置类游戏，前后端分离架构：Next.js Web 前端 + Spring Boot (Kotlin) 真实后端 + MySQL 8 数据库，另有一个不连后端的 Android 客户端。

## 架构

```
浏览器 ──▶ Next.js 前端 (:3000)
              │  (REST 请求，JWT 认证)
              ▼
        /api/** ──▶ Spring Boot 后端 (:8080)
                        │
                        ▼
                     MySQL 8 (Flyway V1~V4 自动建表/迁移)
```

## 技术栈

| 层 | 技术 | 版本 |
|------|------|------|
| 前端框架 | Next.js（App Router，`--webpack` 模式） | 16.2.9 |
| UI | React + TypeScript | 19.2.4 / 5.x |
| 样式 | Tailwind CSS | 4.x |
| 后端 | Spring Boot + Kotlin | 3.2.5 / 1.9.25 |
| 运行时 | JDK（编译目标 17，本机以 JDK 21 运行） | 21 |
| 数据库 | MySQL 8 + Flyway 迁移（V1~V4） | 8.0 |
| 认证 | JWT（Caffeine 黑名单）+ Bucket4j 限流 | — |
| 构建 | 前端 npm / 后端 Maven | Node ≥ 22 |

后端还包含：全局异常处理器、TraceId 链路日志、`@AuditLog` 审计日志切面、`@Version` 乐观锁 + SELECT FOR UPDATE 行锁、Swagger/OpenAPI、Actuator 探活、WebSocket(STOMP, `/ws`)。

## 功能模块（与后端 Controller 一一对应）

- **修炼系统** `/api/action/cultivate|breakthrough` -- 冥想修炼/境界突破/魂力成长
- **战斗系统** `/api/action/battle` -- 野外遇敌/胜败判定/装备掉落，战斗结果含回合日志
- **杀戮之都** `/api/action/tower` -- 塔防式爬层挑战
- **装备系统** `/api/equipment/**` -- 魂环/魂骨/魂核装备、卸下，背包出售/扩容
- **商店系统** `/api/shop/normal|boss|limited` -- 普通金币商店（魂环箱/魂骨箱等）/ Boss 币商店 / 限时珍品，前端三 tab
- **宗门系统** `/api/guild/**` -- 创建/加入/退出/捐献/宗门 Boss 挑战/宗门商店
- **天赋系统** `/api/talent/**` -- 战神/魂师/财富/神圣四分支升级
- **排行榜** `/api/rank/level|tower` -- 等级排行/塔层排行
- **离线收益** `/api/game/offline-claim` -- 挂机离线补偿，进入主页自动领取
- **账户** `/api/auth/register|login|me|logout` -- JWT 登录态，退出后 token 进黑名单

前端页面：登录注册（`/`）、游戏主页（`/game`）、修炼、装备、商店、杀戮之都、天赋、宗门、排行榜、百科（`/game/wiki`，纯静态内容）。

## 快速开始

### 1. 启动后端

```bash
# 先建库（Flyway 会自动建表，无需手动执行 SQL）
mysql -uroot -p -e "CREATE DATABASE douluo_game DEFAULT CHARSET utf8mb4;"

# 设置环境变量（JWT_SECRET 必须 ≥ 32 字节，否则启动失败）
set DB_PASSWORD=你的MySQL密码
set JWT_SECRET=至少32字节的随机密钥字符串

cd backend
mvn spring-boot:run
```

后端监听 `http://localhost:8080`，API 文档在 `http://localhost:8080/swagger-ui.html`。

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:3000`。前端默认请求 `http://localhost:8080`，可用 `frontend/.env.local` 的 `NEXT_PUBLIC_API_URL` 覆盖。

### 3. 端口被占用时

本机若 8080/8081 被其他应用占用，用环境变量换端口，并同步改前端地址：

```bash
# 后端换到 8090
set SERVER_PORT=8090
mvn spring-boot:run

# frontend/.env.local
NEXT_PUBLIC_API_URL=http://localhost:8090
```

## 常见问题

- **后端启动即崩 / JWT 报错**：`JWT_SECRET` 未设置或不足 32 字节，生产环境启动会强校验。
- **前端 dev/build 崩溃（Turbopack panic）**：本仓库路径含中文，Turbopack 会 panic。`package.json` 的 `dev`/`build` 已固定为 `next dev --webpack` / `next build --webpack`，请勿改回 Turbopack。
- **端口被占**：后端用 `SERVER_PORT=xxxx` 覆盖，同时同步 `frontend/.env.local` 的 `NEXT_PUBLIC_API_URL`（见上文快速开始第 3 步）。
- **CORS 报错**：后端默认允许 `http://localhost:3000`，其他来源通过环境变量 `CORS_ORIGINS`（逗号分隔）配置。
- **409 冲突**：装备/商店写入使用 `@Version` 乐观锁，前端会自动重放一次并刷新状态，属正常自愈。

## 项目结构

```
├── frontend/               # Next.js 16 前端（App Router）
│   ├── src/app/            # 页面：登录 + /game/{cultivation,equipment,shop,tower,talent,social,wiki}
│   ├── src/lib/api.ts      # REST 客户端（JWT、409 自愈、状态轮询）
│   └── .env.local          # NEXT_PUBLIC_API_URL
├── backend/                # Spring Boot 3.2 + Kotlin 后端
│   └── src/main/
│       ├── kotlin/com/douluodalu/game/   # controller / service / repository / entity / security / config
│       └── resources/db/migration/       # Flyway V1~V4
├── shared/                 # Android/KMP 共享游戏引擎（commonMain / androidMain）
├── app/                    # Android 客户端
└── DEPLOY.md               # Rocky 9 服务器部署教程
```

## Android 端

`app/` + `shared/` 为 Android 客户端：游戏逻辑收敛到 `shared` 的 KMP 引擎（commonMain），存档保存在设备本地，**不连接 Spring Boot 后端**，与 Web 端数据互通无关。原 `web/` 目录（Compose for Web）已从 Gradle 构建中移除，属遗留死代码，不再维护。

## 部署

参考 [DEPLOY.md](./DEPLOY.md) 获取 Rocky 9 服务器上「MySQL + Spring Boot + Next.js + Nginx」的完整部署教程。
