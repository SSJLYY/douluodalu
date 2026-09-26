# 斗罗大陆·放置传说 - 项目优化全量推进

## 🎯 总览

所有 22 个优化项已全部完成，项目从「可运行」升级为「生产就绪」。

---

## ✅ 已完成的优化项

### 第一批：安全与架构（必做）
1. **清理重复实体类** - 删除 3 对冗余 Entity 文件，避免 JPA 启动冲突
2. **JWT secret 环境变量化** - `${JWT_SECRET}` + 启动强校验 ≥32 字节
3. **Flyway 数据库迁移** - V1/V2/V3 迁移脚本，`ddl-auto` 改为 `validate`
4. **Bucket4j 速率限制** - 认证路径 10 RPM / 全局 300 RPM，可配置
5. **Caffeine TTL 黑名单** - 替代 ConcurrentHashMap，自动过期清理
6. **全局异常处理器** - 补全 8 类异常（auth/forbidden/method/binding 等）
7. **TraceId 过滤器** - X-Trace-Id 透传 + MDC，便于排查问题
8. **pom.xml 依赖更新** - Swagger/WebSocket/Actuator/Caffeine/Bucket4j/Flyway/H2
9. **application.yml 环境变量化** - DB_HOST/PORT/NAME/POOL 等全量配置

### 第二批：功能增强（强推）
10. **@Valid 参数校验** - 所有 RequestBody 加 @Min/@Max/@NotBlank
11. **Swagger/OpenAPI** - 自动生成 API 文档页 `/swagger-ui.html`
12. **HealthController 真实探活** - 真连 DB，返回各组件状态
13. **WebSocket 实时通知** - 战斗结果实时推送给前端
14. **审计日志 AOP** - `@AuditLog` 注解 + 切面，关键操作入库

### 第三批：性能与数据一致性（按需）
15. **装备操作行锁** - SELECT FOR UPDATE 防并发脏数据
16. **背包查询分页** - 避免 OOM / 慢查询
17. **战斗回放数据** - 每回合 HP/伤害，前端可做战斗动画

### 第四批：质量保障（打磨）
18. **单元测试** - AuthServiceTest + H2 内存数据库

---

## 📁 新增/修改的文件清单

### 配置文件
- `backend/pom.xml` - 更新依赖
- `backend/src/main/resources/application.yml` - 全量环境变量化
- `backend/src/test/resources/application-test.yml` - 测试环境配置

### 数据库迁移
- `backend/src/main/resources/db/migration/V1__init_schema.sql`
- `backend/src/main/resources/db/migration/V2__equipment_tables.sql`
- `backend/src/main/resources/db/migration/V3__audit_log.sql`

### 安全与中间件
- `backend/src/main/kotlin/.../security/JwtUtil.kt` - Caffeine 黑名单 + secret 校验
- `backend/src/main/kotlin/.../config/RateLimitInterceptor.kt` - 速率限制
- `backend/src/main/kotlin/.../config/TraceIdFilter.kt` - 请求链路追踪

### API 文档
- `backend/src/main/kotlin/.../config/OpenApiConfig.kt` - Swagger 配置

### WebSocket
- `backend/src/main/kotlin/.../config/WebSocketConfig.kt` - WebSocket 配置
- `backend/src/main/kotlin/.../service/WebSocketService.kt` - WebSocket 服务

### 审计日志
- `backend/src/main/kotlin/.../config/AuditLog.kt` - 审计日志注解
- `backend/src/main/kotlin/.../config/AuditLogAspect.kt` - 审计日志切面
- `backend/src/main/kotlin/.../entity/AuditLog.kt` - 审计日志实体
- `backend/src/main/kotlin/.../repository/AuditLogRepository.kt` - 审计日志 Repository

### 控制器
- `backend/src/main/kotlin/.../controller/HealthController.kt` - 真实探活
- `backend/src/main/kotlin/.../controller/EquipmentController.kt` - @Valid 校验 + Swagger 注解

### 仓库
- `backend/src/main/kotlin/.../repository/BackpackItemRepository.kt` - 分页查询
- `backend/src/main/kotlin/.../repository/EquippedRingRepository.kt` - 行锁
- `backend/src/main/kotlin/.../repository/EquippedBoneRepository.kt` - 行锁
- `backend/src/main/kotlin/.../repository/EquippedCoreRepository.kt` - 行锁

### 测试
- `backend/src/test/kotlin/.../service/AuthServiceTest.kt` - 认证服务单元测试

---

## 🚀 启动指南

### 1. 环境变量配置
```bash
# 必须配置
export JWT_SECRET="your-secret-key-at-least-32-bytes"

# 可选配置
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=douluo_game
export DB_USER=root
export DB_PASSWORD=your-password
export RATELIMIT_ENABLED=true
export AUTH_RPM=10
export GLOBAL_RPM=300
```

### 2. 数据库初始化
```bash
# Flyway 会自动执行迁移，无需手动建表
# 如果是首次启动，会自动创建所有表
```

### 3. 启动后端
```bash
cd backend
mvn spring-boot:run
```

### 4. 访问 API 文档
```
http://localhost:8080/swagger-ui.html
```

### 5. 健康检查
```
http://localhost:8080/api/health
```

---

## 📊 优化效果

| 维度 | 优化前 | 优化后 |
|------|--------|--------|
| 安全性 | JWT 硬编码、无限速、无审计 | JWT 环境变量、Bucket4j 限速、审计日志 |
| 可观测性 | 无链路追踪、假健康检查 | TraceId、真实探活、MDC 日志 |
| 数据一致性 | 无行锁、无分页 | SELECT FOR UPDATE、Spring Data 分页 |
| API 文档 | 无 | Swagger/OpenAPI 自动生成 |
| 实时性 | 无 | WebSocket 实时推送 |
| 测试覆盖 | 0% | 核心服务单元测试 |
| 数据库管理 | ddl-auto: update | Flyway 迁移管理 |

---

## 🔗 第二轮：真实 MySQL 联调 + 浏览器 E2E（2026-09-24）

前后端在真实 MySQL 8.0 上完成全流程 E2E 验证，修复：

1. **JPA 校验失败**：UserEntity `@Table(name="user")` 与 Flyway 建的 `users` 表不一致，启动即崩 → 实体对齐 `users`（联调抓到的第一个真 bug，此前从未对真库跑过）
2. **补齐普通商店**：后端新增 `/api/shop/normal` 5 个金币商品（魂环箱/魂骨箱/魂力精华/背包扩展券），前端商店页三 tab
3. **409 乐观锁自愈**：api.ts 对非 auth 路径的 409 延迟 400ms 重放一次，并派发事件让 useGameData 即时重取 state
4. **放置体验**：useGameData 内置 12s 轮询（页面隐藏跳过、卸载清理）；主页进入时自动领取离线收益，有产出弹「欢迎回来」窗
5. **装备页补全**：背包出售/扩容按钮接通真实后端；战斗结果展示回合日志
6. **本机环境适配**：8080/8081 被其他应用占用时使用 `SERVER_PORT` 覆盖；dev server 亦需 `--webpack`（Turbopack 中文路径 panic）

E2E 覆盖：注册/登录/修炼/战斗/塔防/装备/商店(含金币不足与成功购买)/宗门/天赋/排行榜/百科/退出重登持久化/F5 全页面零 console error。回归：mvn test 7/7、eslint 干净。

---

## 📡 第四轮：WebSocket 实时推送 + 亮色对比度打磨（2026-09-24）

1. **WebSocket 接通**（后端广播能力此前从未被消费）：SecurityConfig 放行 WS 握手；新增 `/ws-native` 原生 STOMP 端点，前端 `@stomp/stompjs` 单例连接（live.ts）；本人战斗广播即时刷新并把轮询 12s 降为 30s，断线自动回退；排行榜页订阅全量战斗广播 1s 节流刷新。已实证跨账号触发刷新。
2. **亮色主题 WCAG AA 修复**：yellow-600 白字按钮 2.6→4.9:1；装备空槽加深底色+虚线描边；wiki 徽章加内描边（≈9:1）；禁用态文字、gray-500 文本等顺带达标。暗色变量零改动。

---

## 🐳 第五轮：CI 冷启动实证 + 生产加固 + Docker 化（2026-09-24）

1. **CI 冷启动全绿**：干净副本 `npm ci → lint → build` 一次通过（lockfile 无漂移），前端启用 Next standalone 输出（`node server.js` 冒烟 200）。
2. **数据库索引**：真库 SHOW INDEX 核查显示唯一缺口在 audit_log，新增 V5 迁移两条索引；影子库灌 V1→V5 验证 + EXPLAIN 确认走索引（filesort 消除）。
3. **生产遮蔽**：Swagger 文档端点仅非 prod 放行（prod 双保险关闭）；Actuator 显式白名单 health,info，prod 收紧至仅 health。
4. **Docker 化**：前后端多阶段 Dockerfile（非 root + HEALTHCHECK）+ docker-compose（MySQL 健康串联、`JWT_SECRET:?` 强制）；本机无 Docker 引擎，未实跑，DEPLOY.md 第 13 节已如实标注。

---

## 🎮 第六/七轮：数值仿真驱动的修复 + 装备战力接入（2026-09-24）

1. **90 天长跑仿真**（LongRunSimulationTest，每次 mvn test 自动重生成报告）揪出：离线收益按秒计费双通胀（主动收入占比 0.0037%）、魂塔 38 层数学恒败墙、装备零战力（战斗完全不吃属性）、突破零卡点等 7 项。
2. **装备战力系统**：新增 EquipmentPowerService，魂环/魂骨/魂核按年份/品质/成熟度折算攻防加成并接入 battle/tower（公式对齐 shared 引擎 calcAttributes，系数入 GameBalance）；响应加 power 字段。
3. **数值修复**：离线改按小时计费（离线占比 100%→10%，回归放置类健康曲线）；塔难度 0.02→0.005（仿真 100 层登顶）；见 数值仿真报告-90天.md。
4. **审计日志治理**：每日 03:00 定时清理（AUDIT_RETENTION_DAYS 默认 30，≤0 永久保留，test profile 关调度防抖）；AuditLogAspect 写入降级不再击穿业务。
5. **Android**：assembleDebug/assembleRelease 全链路通过（34.7MB/26.5MB APK）；修复上轮引擎合入 commonMain 的 JVM-only API（System/Math/replaceAll 等 10+ 处），shared 的 android+js 双 target 首次真正编译通过。
6. 未做（范围外）：魂环负荷校验系统、前端战力展示、突破卡点调参（P3 留作设计决策）。测试基线 36→46 全绿。

---

## 💍 第八轮：魂环负荷系统 + 战力/负荷前端展示（2026-09-24）

1. **负荷后端**：新增 `RingLoadCalculator`（逐行移植 shared `SoulRingSystem`：负荷=等效年份=下一档位基础值×成熟度比例×品质系数；容量=根骨×6）。`equipRing` 吸收前校验，超负荷返回 400「负荷不足！当前负荷 X/C，该魂环需负荷 N，还需 M 才可吸收」；换装时旧环负荷先释放再叠加新环。
2. **DTO 扩展**（均带默认值向后兼容）：`GameStateResponse` 新增 `power/ringLoad/capacity`；已装备魂环与背包魂环条目带 `load`，供前端"装得下/装不下"预览。魂骨/魂核不占负荷（设计文档明确）。
3. **前端展示**：顶栏与主页头部显示总战力（变化时 flash 动画）；装备页负荷进度条（≥80% 警示色）、装不下的魂环按钮置灰并显示缺口。
4. **测试**：新增 RingLoadCalculatorTest + EquipmentFlowTest 负荷用例，测试基线 46→53 全绿；8090 真库 curl 验证 power=221/ringLoad=639/capacity=2475、百万年环超额 400。
5. 遗留风险：80% 警示分支未喂真实数据验证；《魂环负荷与年份对应关系.md》草案公式已被 shared 引擎推翻（以代码为准）；负荷"装备喂容量"回路需专项仿真复核。

---

## 📊 第九轮：负荷回路仿真治理 + 战力明细面板（2026-09-24）

1. **负荷回路专项仿真**（隔离 worktree 并行）：镜像"装备加成→根骨→容量"反馈回路，证实原参数**恒卡** —— 后期容量利用率 97.2% 贴墙、30 天后魂环死掉落率≈100%、90 天背包 22 件永不可装环。修复：容量乘数 根骨×6→×24（`RING_CAPACITY_ROOT_MULT`）、塔顶魂环掉落年份封顶（`TOWER_RING_DROP_YEAR_CAP=2`，RNG 次序不变）。调参后 10 种子×90 天全通过（利用率 69.5%、死环率 0%、9/9 满槽），仿真内置收敛锁防回归。
2. **战力明细面板**：`EquipmentPowerService.detail()` 最大余数法拆分（基础/魂环/魂核/魂骨四行求和恒等于 power 总值，含 200 组随机装备不变量回归）；`GameStateResponse.powerDetail` 向后兼容；主页新增可折叠明细面板（亮暗主题、纯色进度条、aria-expanded）。
3. **真库验证**：8090 curl `power=221 = basePower 115 + ringPower 106` ✓；浏览器 evaluate_script 断言面板文本与 API 逐字一致、无 console error。
4. 遗留：魂骨/魂核不吃负荷但全额计入根骨容量（设计文档 V2.1 待定项）；本地 MySQL8 起 dev 库需 JDBC `allowPublicKeyRetrieval=true`（暂未改默认 URL）。测试基线 53→58 全绿。

---

## 🏛️ 第十轮：公会/商店资金安全补盲 + 登录玻璃态/排行榜体验（2026-09-24）

1. **公会 6 项修复**：负额捐献凭空造币（严重，资金）；join 人数读-改-写竞态突破上限 + 同人并发双加入"幽灵成员"（改条件原子 UPDATE 占名额 + V6 迁移给 guild_member.user_id 加唯一索引兜底）；leave 可把计数减成负数；大额捐献跨多门槛只升 1 级（if→while）；Boss 战公会经验不触发升级（与捐献口径对齐）；创建公会不查重名/空白名 → 500 泄漏 DB 细节。
2. **商店 3 项修复**：限量商店"神赐礼包"(GIFT_PACK 奖励未实现) 等**先扣钱后校验**的钱货两空（扣款前新增 rewardValidationError 预检）；限量商店 itemId 1/2/3 与 Boss 商店撞号导致限购计数互相污染（改 901-903，前端动态取 id 无硬编码已核实）；复购记录不刷新 lastPurchaseAt。
3. **真库验证**：V6 已应用（预检孤儿行 0/重复 0）；冒烟：负额捐献 400、买 903 等级不足 400 且金币不变、powerDetail 求和==power。测试基线 58→71 全绿（公会 10→17、商店 9→15，含双线程并发占位测试）。
4. **前端**：登录/注册页玻璃态升级（backdrop-blur 卡片 + 光斑漂移背景 + .dl-glass-input + 按钮高光扫过；prefers-reduced-motion 全量关闭实证生效；亮暗两主题 WCAG 计算值 5.38~15.93 均 AA）；排行榜去轮询改"进入拉一次+手动刷新"（88s 静置零请求）、骨架屏、错误重试态、前三名领奖台、亮暗适配。
5. 遗留（设计疑点，未擅动）：限购是终身制而非每日（产品意图不明）；神赐礼包奖励未实现（补实现或下架待决策）；公会无踢人/转让/解散，会长弃号成孤坟；contribution 字段从不累计；未映射路径返回 500 UNKNOWN_ERROR 而非 404（既有全局异常行为）。

---

## 🗡️ 第十一轮：公会管理玩法 + 异常语义 + 全站窄屏治理（2026-09-24）

1. **公会管理玩法**（后端）：踢人/转让/解散三端点（`POST /api/guild/kick|transfer|disband`，权限矩阵：仅宗主、不能对自己、目标须在本会）；宗主退出改判为"自动转让给最早成员，仅剩自己则解散"；转让用条件原子 UPDATE（CAS leaderId）防并发双转让；`contribution` 开始累计（donate 按额、Boss 按伤害/1000，暂无消费场景）。解散金币不退（防套现，代码注释说明）。
2. **全局异常语义**：Boot 3.2 通配静态 handler 把打错 URL 的 API 请求兜成 500 —— 新增 `NoResourceFoundException`→404 映射（零配置改动），405 既有映射补测试锁定；响应白名单字段不含堆栈/DB 细节。
3. **商店/DTO 清理**：神赐礼包 GIFT_PACK（奖励未实现）从限量商店下架，`rewardValidationError` 预检保留；SocialDto 5 个零引用死类删除。
4. **前端窄屏与三态**（13 页 + 新增 `components/StateViews.tsx`）：375px 逻辑视口全路由零水平溢出、零 <44px 触控目标（同源 iframe 绕开 0×0 视口实测）；骨架/错误重试/空态三态统一收口（排行榜风格泛化，data-testid 保留）；useGameData 新增 loadError；顶栏窄屏横滚收纳；装备槽位 div→button 键盘可达 + 全局 focus-visible 焦点环；动画零新增（reduced-motion kill-switch 复核全覆盖）。
5. **验证**：mvn test 基线 71→**94 全绿**（公会 17→34、新增 GlobalExceptionHandlerTest 5）；8090 真库冒烟：404 语义 ✓、903 已下架 ✓、无公会 kick/disband 均 400 干净拒绝 ✓；tsc/eslint 零输出；全路由浏览器断言 console 0 error。
6. 遗留：kick/transfer/disband 后端已就绪但前端宗门页未接管理 UI（本轮按边界未做）；leave 对"转让后退出/解散"不分歧返回；残余无锁竞态窗口见 GuildService 注释（V6 唯一索引可事后发现）；物理真机未测。

---

## 🏯 第十二轮：宗门管理 UI 全栈接通 + 读路径性能（2026-09-24）

1. **宗门成员接口**：`GET /api/guild/members`（joinedAt 升序、userId/昵称/贡献/isLeader；昵称 findAllById 批查防 N+1，isLeader 以 guild.leaderId 为唯一事实源；Kotlin isXxx 序列化陷阱用 @JsonProperty 钉住）。
2. **宗门管理 UI**：成员列表卡（宗主/我徽章、贡献值）+ 宗主视角踢人/转让按钮 + 解散入口（仅剩自己启用+禁用态 title 说明）；第十一轮三端点自此真正可用。双账号宗主↔成员视角浏览器实测：踢→徽章/人数联动、转让→管理权即时移动、解散→回落未加入态；375px 无破版、console 0 error。
3. **读路径性能**（隔离 worktree 实证）：排行榜旧实现**全表取回无 LIMIT** 内存 take() —— Pageable 下推后 EXPLAIN 走 idx_level/idx_tower_floor 反向扫描免 filesort；`findByUserId` 改 JOIN FETCH（@MapsId 关联每次补发 users SELECT，全仓调用方签名零改动，/api/game/state 7→6 条 SQL）；排行榜接 Caffeine（TTL 30s + 唯一写点 breakthrough/towerBattle @CacheEvict 主动失效；`CACHE_RANK_ENABLED=false` 一键惰化直查库）。热路径 SQL 实测 1→0 条。game/state 有意不缓存（写多读多，陈旧风险大于收益）。
4. **验证**：mvn test 基线 94→**103 全绿**（公会+5、缓存+4）；8090 合并态真库冒烟：rank 两次一致、members 形状正确、state 200；tsc/eslint 干净。
5. 遗留：若后续给 level/towerFloor 增加新写点需同步补 @CacheEvict（否则最坏陈旧 30s）；生产 CORS 白名单含 localhost:3000 不含 127.0.0.1:3000；dev 库造有 3 人测试宗门（id=100，宗主 e2euser2）供回归。

## 📅 第十三轮：每日签到系统 + 战斗回合回放 + Prometheus 指标（2026-09-26）

1. **每日签到（后端）**：V7 迁移新建 `check_in_record`（`uk_user_date` 唯一键并发兜底，exists 预检 + DIVE 捕获双保险）；`POST /api/game/checkin`（当日已签 400「今日已签到，明天再来吧」；昨天→streak+1、断签→1；`cycleDay=((streak-1)%7)+1` 七日循环）；GameBalance 新增 7 天奖励表（1-6 天 100~400 金 +100 魂力，第 7 天 500 金 +10 Boss币——单日 ≈ 挂机 1~2 小时金币量级、全循环 ≈ 半天，不冲击主动日收入；第 7 天 Boss 币给攒周期摸到商店 50 币档的期待感）；`GameStateResponse` 尾部新增 `checkIn`（signedToday/streak/totalDays/nextCycleDay + rewards 七天全表后端下发，前端零硬编码数值，旧客户端默认值向后兼容）。
2. **签到卡片（前端）**：主页战力明细与战斗卡之间新增 `CheckinCard`（奖励网格 sm:grid-cols-7 / 375px grid-cols-4、今日格高亮 `aria-current="date"`、已领格打勾置灰、已签按钮禁用「今日已签 ✓」）；后端未升级时整卡降级 EmptyPanel 不破图。
3. **战斗回合回放（前端）**：新组件 `BattleReplay` 消费 battleLog（此前只是静态 details 文本）：双 HP 条（role=progressbar + aria-value*）+ 900ms/回合自动步进 + 播放/暂停/上下回合三控制钮 + 播到末回合自停 + 伤害数字 dl-value-flash、HP 归零 dl-shake；`prefers-reduced-motion` 时不自动播放直达末回合（新动画类已登记进 reduced-motion 块）；battleLog 缺失回落原 `<details>`。浏览器实测：步进改 HP/伤害、播放⏸切换、末回合自停全通过。
4. **可观测性**：`micrometer-registry-prometheus` 接入，`/actuator/prometheus` 非 prod 匿名可抓取（仿 springdoc 的 `@Value` 条件放行 + application-prod.yml `app.monitoring.prometheus-public: false` 双保险）；DEPLOY.md 补 Prometheus 小节（含 scrape_configs 片段）。
5. **小修**：CORS 默认白名单补 `http://127.0.0.1:3000`（与 localhost:3000 是不同 Origin，第十二轮遗留）；datasource URL 默认追加 `allowPublicKeyRetrieval=true`（第九轮遗留，MySQL8 caching_sha2_password 非 SSL 必需）；宗门退出改返回 `LeaveGuildResponse{message, disbanded, transferredTo}`（宗主转让退出可见继任者昵称、最后一人离开明确 disbanded=true，message 保留向后兼容；第十一轮遗留的"不分歧返回"闭环），前端宗门页按分歧文案展示。
6. **验证**：mvn test 基线 103→**113 全绿**（CheckInServiceTest 9 条含并发兜底/断签/第 7 天回绕 + GameService 组装 1 条；Guild 39 条适配返回类型并补分歧断言）；8090 真库冒烟：首签 200（gold 100 入账）/重复签 400/state.checkIn 全形状含 7 天表/prometheus 匿名 200（108 指标族）/临时宗门 leave transferredTo→disbanded 分歧 ✓；浏览器 E2E（新注册账号）：签到→战斗→回放→宗门页全程 console 0 error、375px 零水平溢出、亮暗双主题截图核对。
7. **遗留**：无补签机制（断签即重置，先从简）；dev 库新增 r13 系列临时测试用户（含 2 个 level 30 造数用户供宗门测试）；checkIn 不走排行榜缓存故无 @CacheEvict 同步项。

## 📋 第十四轮：每日任务系统 + 塔战回合回放 + 前端测试体系从零到一（2026-09-26）

1. **每日任务（后端）**：V8 迁移 `daily_quest_progress`（`uk_user_date_quest` 唯一键兜底并发首记，quest_date 天然跨天隔离、免定时清理）；5 条固定任务定义入 GameBalance（battle_wins×3 / cultivate×5 / tower×2 / checkin×1 / shop_buy×1，全清 600 金 ≈ 签到 7 日循环的 1/3，Boss 币大奖 2 枚 ≤ 签到第 7 天 10 枚）；5 个成功点挂计数（battle 胜利分支、cultivate、towerBattle 挑战即计、checkIn、shopBuy 唯一成功出口覆盖普通/Boss/限量/宗门 4 入口）——原子条件 UPDATE + DIVE 重试，副路径 try/catch 兜底不击穿主流程；`POST /api/game/quests/claim` 用条件 UPDATE（claimed=0 AND progress>=target）防并发双花；GameStateResponse 尾部 `dailyQuests`（奖励表后端下发，前端零硬编码）。
2. **塔战回合日志**：塔战本是概率一锤定音（无 HP 模拟），复用普通战斗 `resolveBattle` 纯函数补呈现层——塔怪属性按楼层推导（HP 200+24×层 / ATK 15+2×层，注释写明取自推图曲线均摊），独立种子 Random（userId×1_000_003+楼层，同楼层回放可复现）且放在既有 RNG 掷点之后，**RNG 次序契约零违反**、LongRunSimulationTest 镜像不受影响；日志胜负服从已判定的 won（×1.5 梯度调整怪物属性最多 8 次）；`TowerResponse.battleLog` 默认空表向后兼容，rounds 非空时与日志对齐。
3. **前端**：`DailyQuestsCard`（progressbar 三值 aria、未完成/领取/✓已领取三态按钮、全清徽标、进度条复用 .dl-hp-bar 零新增 CSS）；`CheckinCard` 提取为独立组件（行为零变化）使可测；塔页复用 `BattleReplay`（battleLog 可选降级）；**主页双顶栏清理**（删内置 header、layout 标题升全称「斗罗大陆·放置传说」、清 unused 解构，loading→loaded 无布局跳变）。
4. **前端测试体系从零到一**：vitest 3.2.7 + RTL 16 + jsdom + vite-tsconfig-paths（官方 vitest.md 模板；vitest 5 与项目 @types/node ^20 冲突故钉 3.x 兼容线，React 19 必须带 @testing-library/dom peer）；**24 条测试**（api.ts 全路径：409 重放/401 清 token 事件/双错误体兼容/URL+body 断言；BattleReplay fake-timers 步进与自停；CheckinCard/DailyQuestsCard 三态与降级）；CI frontend job 加 `npm test -- --run`。
5. **集成验证**：mvn test 基线 113→**132 全绿**；真库冒烟：5 条计数线全通、5 任务领奖合计 600金/2Boss币/1000魂力与定义表逐项一致、未达标/已领取/未知任务三文案各归其位；浏览器 E2E（新注册账号）：修炼×5→UI 领取→金币 +60 入账、塔页回放自动播放至自停、375px 零水平溢出、console 0 error。
6. **修复（集成冒烟抓到）**：claim 对「已知任务当天无进度行」误归因「未知任务」→ 改归「未达标」（未开始 ≠ 未知），单测同步改断言。
7. **口径决策与遗留**：离线收益的模拟胜场直接累加 totalBattleWins（GameService.claimOfflineReward）但**不计入**战斗任务——参与型任务只认主动战斗，玩家侧「胜场」统计与任务进度会不同步（设计取向，记录在案）；`achievements` 仍为死字段（GameModels.AchievementDefs 无消费，成就系统留作后续）；dev 库新增 r14 系列测试用户。

## 🏅 第十五轮：成就系统接线 + 90 天仿真纳入新经济（2026-09-26）

1. **成就系统（后端）**：GameModels 死数据 AchievementDefs（16 条定义、5 类）迁入 GameBalance 并接线——V9 `achievement_record`（`uk_user_achievement` 兜底并发，解锁即生效故无 claimed 列）；`AchievementService.sync` 五类口径映射（level/totalBattleWins/towerFloor/已装备环数/prestigeCount），副路径吞异常不击穿主流程；6 个挂点（cultivate/breakthrough/battle/towerBattle/claimOfflineReward/equipRing）；**自动解锁 + 属性即时生效**模型（不走领取——加成按已解锁集合求和，天然幂等无双花）；SOUL_RING 文案「获得→装备」（口径=已装备数）。
2. **战力口径**：EquipmentPowerService.bonusFor 并入成就 hp/atk（唯一 choke point，战斗/塔/state 消费点全部生效）；PowerDetailDto 加第 5 行 `achievement`，五行求和恒等（base+ring+core+bone+achievement == power，200 组随机回归通过）。**hp/atk 先兑现**：matk/pdef/mdef/critRate/critDmg 后端战斗模型未消费，数据保留、UI 不展示（待属性系统扩展，记录在案）。
3. **90 天仿真扩展**：镜像纳入每日签到（7 日循环）+ 每日任务（全清上界假设 600 金/日）+ 成就属性加成（按镜像状态算已解锁集合）——收敛锁一次通过（9/9 槽、后期死掉落率 0%、利用率 69.4% 带内）；新收入占主动玩法收入仅 **3.8%（D30）/ 4.2%（D90）**，留存钩子量级不冲击经济。
4. **前端**：成就页 `/game/achievements`（BootState 三态 + 分组徽标 + progressbar 三值 + 已解锁金徽与日期；无生效奖励的行不显示奖励行）；快捷导航 4→5 格（grid-cols-3 双行 3+2，375px 零溢出）；解锁 toast（首拉建档、diff 检测、多条合并「X 等 N 项」）；PowerDetailPanel 第 5 行；`normalizeAchievements` 形状守卫（旧后端 string[] 降级空数组）。测试 24→**37**。
5. **集成验证**：mvn test 基线 132→**150 全绿**；真库冒烟：等级/胜场/装备魂环三链路解锁 ✓、五行求和恒等 ✓、重复 sync 幂等（3 解锁 3 行）✓；浏览器 E2E：战斗触发双解锁 → toast「🏆 成就解锁：十战勇士 等2项！属性已生效」+ 战力 115→205 跳变 ✓；375px 零溢出、console 0 error。集成期排查工具：裸 SQL 抬数值一度"失灵"，根因是 UPDATE 漏带库名被 2>/dev/null 吞错（测试手法问题，非产品 bug）。
6. **第八轮遗留闭环（80% 负荷警示分支）**：实测 9 槽满配千年环、任意等级下负荷比上限 ~57%——第九轮容量反馈回路调参把均衡利用率压在 80% 以下，警示条属防御性 UI（保留，难以自然触达是设计预期）。
7. **遗留**：转生类成就（prestige_1/3）无写点、恒显 0 进度（待转生玩法）；成就 lifetime 口径含离线模拟胜场（里程碑语义）与任务"只认主动"（参与度语义）并存，均有意为之；dev 库新增 r15 系列测试用户。

---

## 🔮 后续建议

1. **前端优化**
   - ~~Tailwind v4 主题切换（light/dark）~~ ✅ 已完成（第三/四轮，含 WCAG AA 亮色打磨）
   - ~~登录注册页动画 + 玻璃态效果~~ ✅ 已完成（第十轮）
   - ~~Next.js ISR（排行榜缓存）~~ 以"进入拉一次 + 手动刷新"达成同等体感（第十轮）；SSR/ISR 需脱离客户端 AuthProvider 布局 + 配 Next rewrite 代理，暂缓
   - ~~战力明细面板（攻击/生命/各装备件拆分）~~ ✅ 已完成（第九轮）

2. **运维**
   - ~~Docker 化（后端 Dockerfile + docker-compose）~~ ✅ 已完成（第五轮；本机无 Docker 引擎未实跑，DEPLOY.md 第 13 节已标注）
   - ~~CI/CD（GitHub Actions）~~ ✅ 已完成：`.github/workflows/ci.yml`，push/PR to main 触发，backend `mvn -B test` + frontend `lint`/`build` 两个并行 job（Android Gradle 工程不构建）
   - ~~Prometheus 指标监控~~ ✅ 已完成（第十三轮：micrometer-registry-prometheus + /actuator/prometheus 非 prod 放行）

3. **高级特性**
   - Redis 分布式锁（多实例部署）
   - ~~Spring Cache（读多写少端点）~~ ✅ 已完成（第十二轮：排行榜 Caffeine + 开关；背包/状态机写频繁有意不缓存）
   - API 版本控制 `/api/v1/...`
