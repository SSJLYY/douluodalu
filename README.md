# 斗罗大陆·放置传说 (Douluo Continent Idle Legend)

基于 Next.js 16 构建的修仙放置类 Web 游戏。

## 技术栈

| 技术 | 版本 |
|------|------|
| Node.js | >= 22 |
| Next.js | 16.2.9 (Turbopack) |
| React | 19.2.4 |
| TypeScript | 5.x |
| Tailwind CSS | 4.x |
| SQLite | node:sqlite (实验性) |

## 功能模块

- **战斗系统** -- 野外遇敌/胜败判定/装备掉落
- **修炼系统** -- 冥想修炼/境界突破/魂力成长
- **装备系统** -- 9魂环/6魂骨/3魂核槽位，装备/卸下/出售
- **商店系统** -- Boss商店(Boss币)/限时珍品/宗门商店(金币)
- **宗门系统** -- 创建/加入/退出/捐献/挑战Boss/宗门商店
- **杀戮之都** -- 100层塔挑战/楼层推进/杀戮值系统
- **天赋系统** -- 战神/魂师/财富/神圣四分支
- **排行榜** -- 等级排行/塔层排行
- **离线收益** -- 挂机离线后领取补偿

## 快速开始

### 环境准备

```bash
# 需要 Node.js >= 22
node --version
```

### 安装依赖

```bash
cd frontend
npm install
```

### 启动开发服务器

```bash
npm run dev -- --hostname 0.0.0.0
```

访问 http://localhost:3000

### 生产构建

```bash
npm run build
npm start
```

## 项目结构

```
frontend/
├── src/
│   ├── app/                    # Next.js App Router
│   │   ├── layout.tsx          # 根布局
│   │   ├── page.tsx            # 首页 (登录/注册)
│   │   ├── globals.css         # 全局样式 (暗黑主题)
│   │   ├── api/
│   │   │   ├── [...path]/route.ts  # 统一API代理路由
│   │   │   └── auth/           # 认证路由
│   │   └── game/               # 游戏页面
│   │       ├── page.tsx        # 主战斗页面
│   │       ├── cultivation/    # 修炼
│   │       ├── equipment/      # 装备
│   │       ├── shop/           # 商店
│   │       ├── tower/         # 杀戮之都
│   │       ├── talent/        # 天赋
│   │       ├── wiki/          # 百科
│   │       └── social/        # 宗门/排行榜
│   ├── lib/
│   │   ├── api.ts             # 前端API客户端
│   │   └── server/
│   │       └── auth-store.ts  # 后端数据层 (SQLite)
│   └── contexts/
│       └── AuthContext.tsx     # 认证上下文
├── data/                       # SQLite数据库文件 (运行时生成)
├── next.config.ts              # Next.js配置
├── package.json
└── tsconfig.json
```

## 数据库

使用 Node.js 内置 `node:sqlite` 模块，数据文件位于 `data/app.db`。

核心数据表：

| 表名 | 用途 |
|------|------|
| users | 用户账户 |
| sessions | 登录会话 |
| profiles | 角色属性表 |
| backpack_items | 背包物品 |
| equipped_rings | 已装备魂环 |
| equipped_bones | 已装备魂骨 |
| equipped_cores | 已装备魂核 |
| guilds | 宗门信息 |
| guild_members | 宗门成员 |
| talents | 天赋数据 |
| achievements | 成就数据 |
| kv_store | 键值存储 |

## 部署

参考 [DEPLOY.md](./DEPLOY.md) 获取 Rocky 9 服务器完整部署教程。
