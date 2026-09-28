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

## 🔄 第十六轮：转生玩法（神位传承）+ wiki 内容治理（2026-09-26）

1. **转生端点与流程（后端）**：`POST /api/action/prestige`（照 breakthrough 惯例：200 + success=false 表拦截，不抛异常）；门槛 `PRESTIGE_MIN_LEVEL=50`（设计文档 Lv.100/策略指南 Lv.90，但后端无经验系统、90 天仿真上限 Lv.77，照搬会让玩法 5 个月不可达——按后端节奏校准，注释留档，调一处常量即可改回）；重置：level=1、gold/soulPower 清零（防存量魂力秒升回原等级的零成本刷属性漏洞）、已装备魂环/魂骨**卸回背包**（与现有 unequip 完全同源的数据操作；实测 equipped 表为独立属性拷贝行、卸回不受背包容量校验）；保留：装备背包、天赋（+1 点）、成就、塔层、推图（currentStage 回 1 按 shared doPrestige 先例）、图鉴、Boss币。
2. **双口径转生倍率** `×(1+0.1×转数)`（shared 引擎双先例：属性公式转生倍率 + 修为产出 prestigeMultiplier）：属性（基础 atk/hp + bonusFor 装备/成就加成）与收入（修炼/战斗/塔/离线的 gold+exp）同时乘；签到/任务/宗门/商店固定表不乘（留存与交易口径不膨胀）；Boss币与魂环负荷容量口径不乘（保守，留档）。战力明细五行→**六行**（prestige 行=倍率增量，六行求和恒等，200 组随机含 0~5 转回归）；prestigeCount=0 时倍率恒等，既有数值断言零改动。
3. **90 天仿真镜像转生**：穿装画像 90 天 3 转（day 32/58/83）、末级 Lv.32、利用率 72.6% 带内、9/9 槽、**收敛锁原样通过**；零装备画像 2 转（44/83）且带对照组精确复现旧曲线验证镜像纯增量；转生清金币=健康回收（存量 2.1M→207k）。
4. **前端**：战斗卡第 4 钮「🔄 转生」（Lv.50 门禁 + 禁用提示「转生需要 Lv.50（当前 Lv.X）」/达标提示「转生后全属性+20%」）+ **不可逆确认弹窗**（重置项/保留项两栏清单、当前→目标转数与本次倍率、Escape/遮罩关闭、打开聚焦取消钮的破坏性安全默认）；PowerDetailPanel 抽出为独立组件并加第 6 行（六行/五行/四行动态回退）；`prestigeHint` 纯函数抽至 lib/（Next 16 路由类型检查禁止 page.tsx 导出非路由成员）。
5. **wiki 内容治理**：境界分类修正（16 境界每 10 级一档 Lv.1+/11+/…/151+，附突破消耗公式 120×L^1.55）+ 新增转生系统条目（门槛/收益/重置保留清单/与天赋成就联动）；tab 行横滚收纳 6 分类。
6. **集成验证**：mvn test 基线 150→**160 全绿**；真库冒烟：门槛拦截/重置/保留/卸环回背包/prestige_1 解锁/六行恒等（prestige 行 23=倍率增量）/收入 ×1.1（修炼 14→15）逐项通过；浏览器 E2E：Lv.1 禁用态 → Lv.50 达标态 → 确认弹窗 → 转生成功 toast + Lv 回 1 + 2转展示，375px 零溢出、console 0 error。
7. **遗留**：武魂重抽/品质池与魂核解锁（文档 §2.3/百科）为转生第二轮增量；负荷容量不乘转生倍率（保守口径留档）；dev 库 r16 系列测试用户。

## ⚔️ 第十七轮：战斗模型扩展——五属性全面接入结算（2026-09-26）

1. **战斗数学升级（resolveBattle）**：从「atk/hp 纯减算互拍」升级为完整属性模型——伤害分**物理/魔法 85%/15% 混合**（物攻 vs 物防、魔攻 vs 魔防）；防御减算 `1−def/(def+200)` 下限 10%（DEF_K=200，shared 引擎同源）；**玩家暴击**（暴击率百分点掷点 × 爆伤倍率，基础 150%；怪物 v1 不暴击）；每回合掷点次序固定并注释（玩家 类型→暴击→浮动 三掷、怪物 类型→浮动 两掷），resolveBattle 之外的既有 RNG 掷点零增删。签名升级为参数对象 CombatStats/MonsterStats（battle/塔日志/getGameState/仿真四处同源组装）。
2. **属性来源接线**：玩家 matk=基础×0.5+等级成长、pdef/mdef=2×等级、critDmg 基础 150；EquipmentBonus 扩七字段，**achievementBonus 从只兑现 hp/atk 升级为全七字段求和**（cult_50 的 critRate 2、cult_100 的 critDmg 15 等两轮遗留的"空气属性"全部转正）；装备侧核实 affixesJson 从不生成五属性（转生武魂下轮接入，注释留档）。
3. **怪物曲线与战力口径**：monsterStats 升级数据类（+matk/pdef/mdef=atk×0.35）；powerOf 新权重（魔攻×0.5/双防×0.2/暴击率×5/爆伤×0.2）；**战力明细六行恒等维持**（200 组随机×五属性成就×0~5 转回归）；GameState 尾部 `combatStats`（有效魔攻/双防/暴击率/爆伤，向后兼容）。
4. **校准（本轮核心工序）**：杠杆 = PLAYER_DEF_PER_LEVEL / MONSTER_DEF_FACTOR / 怪物系数。结果**初值即收敛**——玩家双防（2/级）与怪物攻击（+25/图）同量级线性、攻防两端 defFactor 减免对冲，90 天穿装画像 Lv.78（基线 77）、推图 8-15 不变、利用率 72.6% 不动，**收敛锁零改动通过**。GameBalance 新区块记录全部常量与校准依据。
5. **前端**：战力明细面板底部新增战斗属性摘要行（魔攻/物防/魔防/暴击/爆伤 chips，缺失隐藏、暴击 0% 也显示）；wiki 新增战斗系统分类（结算规则/暴击/属性来源/可验算减伤示例）；测试 44→**47**。
6. **集成验证**：mvn test 基线 160→**178 全绿**（BattleMathTest 15 条新数学 + 既有适配）；真库冒烟：Lv.1 面板精确匹配（matk 30/pdef 2/mdef 2/爆伤 150）、战斗与塔日志新数学正常（W/L 3/2）、成就实喂链路（Lv.100 五成就 → 暴击 15%/爆伤 165%）、六行恒等；浏览器 E2E：面板摘要行与冒烟数据逐字一致、wiki 7 分类、375px 零溢出、console 0 error。
7. **遗留**：装备词缀五属性数据仍不生成（武魂觉醒下轮接入时一并）；怪物暴击留待需要时；battleSoulPower 仍为恒 100 死值（武魂觉醒的消费点）；dev 库 r17 测试用户。

## 💠 第十八轮：武魂觉醒系统（转生第二轮增量）（2026-09-26）

1. **觉醒玩法（后端）**：`POST /api/action/awaken`——12 武魂定义从 shared Models.kt 逐字移植入 GameBalance（名称/稀有度/七属性，测试硬断言锚定抽查）；品质池按转数门槛过滤（0转 ≤精良 / 1转 ≤稀有 / 2转 ≤史诗 / 3~4转 ≤传说 / ≥5转 全量，shared getAvailablePool 同款）；权重 roll 逐字 §2.2（40/25/15/8/2/0.5）。首醒免费；重醒 5000 金（文档未定价的实现决策，防无限刷池；品质上限由转数门槛兜底）。
2. **属性接入**：武魂七属性并入 CombatStats（第十七轮管道直接消费）与 powerOf（权重与装备一致）；**战力明细六行→七行**（soul 行差值法，200 组随机含 12 魂/未觉醒混合回归）；battleSoulPower 从恒 100 死值活化为武魂战力公式值（60~455，宗门 Boss 伤害自动受益）；Profile 尾部 soulRarity 由名字反查（零迁移）。转生不清武魂（文档 §15.2 重置项不含）。
3. **仿真镜像**：SimPlayer 第 1 天免费首醒 + 每转后重醒一次；收敛锁原样通过（9/9 槽、死环率 0%、利用率 72.6% 逐位不变）；deltas：推图 5-9→6-1（正向推进）、等级 23→22（重醒金消耗）、报告补觉醒事件（30 天柔骨兔·精良 / 90 天昊天锤·史诗）。
4. **前端**：状态卡武魂名+稀有度徽章（六档主题色板）+ 首醒按钮（免费直醒不弹窗）+ 重醒确认弹窗（当前品质池提示、花费、可能低于当前品质警示、Escape/遮罩关闭）；战力明细第 7 行 💠武魂（四~七行动态回退）；lib/soul.ts 纯函数（Next 16 路由导出限制）；wiki 武魂系统条目（8 分类：觉醒机制/品质池门槛表/稀有度权重/属性生效/转生联动）。测试 47→**59**。
5. **集成验证**：mvn test 基线 178→**197 全绿**（MartialSoulPoolTest 8 + MartialSoulIntegrationTest 10）；真库冒烟：首醒免费写入/重醒扣金/金币不足拦截/3转池 12 连醒无 MYTHIC（门槛生效）/soulRarity 反查/七行恒等（soul 行 141，power 115→256）；浏览器 E2E：徽章/重醒弹窗全流程（取消+确认）/第七行/375px 零溢出/console 0 error。集成期两个"疑点"均为冒烟脚本自身笔误（读错 JSON 层级、测试预算少给 1 万金），后端零缺陷。
6. **遗留**：流派系统（SUPPORT/CONTROL/ASSASSIN 解锁门槛+流派系数，shared isSchoolUnlocked/doChooseSchool 参考就绪）为觉醒第二轮增量；武魂技能（缠绕/无视防御）需技能系统支撑（文档 §16.1）；battleSoulPower 随武魂品质浮动后宗门 Boss 伤害随之变化（预期行为）。

## 🎓 第十九轮：流派系统（觉醒第三增量）（2026-09-26）

1. **流派数据（后端）**：6 流派从 shared ModelsExt.kt 逐字移植——均衡/物理/法系 3 基础开局可选，辅助（Lv.50+1转）/控制（Lv.70+2转）/暗杀（Lv.90+3转）3 特殊门槛解锁；SchoolMods 全系数表（如暗杀 atk×1.40/双防×0.60/暴击+12%/爆伤+15%，物理 atk×1.30/魔攻×0.45）。系数口径注释写死：作用于「基础(已乘转生)+装备/成就/武魂加成(已乘转生)」之和（对齐 shared「基础+武魂之后乘」），暴击走加数；chosenSchool=null 恒等零漂移。
2. **选择流程**：`POST /api/action/school`（照 breakthrough 惯例 200+success=false 拦截；校验顺序 未知→已选→门槛→扣金）；首选免费、重选 `RESCHOOL_COST_GOLD=5000`（对齐重醒惯例）；转生不清流派；**选流派不重 roll 武魂**（shared 会 roll——实现分歧注释留档）；零新增掷点（197 基线零漂移可保）。
3. **系数接入（四处同源）**：playerCombatStats（atk/matk/pdef/mdef 乘 mod、暴击加数）+ maxHp 乘在加总后（`schoolScaledMaxHp`，武魂 hp 吃到系数，对齐 shared 口径）+ battle/tower/getGameState 的 power 字段全部接入（偏离契约处：battle/tower power 也接入，保证四处口径一致并纳入塔胜率）；**战力明细七行→八行**（school 行差值法，恒等由构造保证：七行之和≡不含流派、八行之和≡含流派）。
4. **仿真镜像**：SimPlayer/EquipSimPlayer 第 1 天选 BALANCED 建模（温和系数），全部走生产纯函数无手抄；收敛锁原样通过（9/9 槽、死环率 0%、利用率 72.6%）；deltas 温和正向：90 天等级 22→29、推图 6-1→7-2。
5. **前端**：状态卡流派徽章（已选=可点重选入口）+ 选择弹窗（六卡列表：icon/定位/系数摘要/门槛状态，未达标禁用带当前进度、当前项标「当前」）；战力明细第 8 行 🎓流派（四~八行动态回退）；lib/school.ts 纯函数（schoolUnlocked/schoolRequirementText 与后端同口径）；wiki 流派系统条目（9 分类）。测试 59→**72**（含修复上次超时智能体残留的半成品：chooseSchool 方法缺类型定义）。
6. **集成验证**：mvn test 基线 197→**213 全绿**（SchoolIntegrationTest 14 + BattleMathTest 2）；真库冒烟：门槛拦截（精确当前进度提示）/PHYSICAL 免费选（atk×1.30、matk 13=30×0.45 逐位）/同派拒绝/无金拒绝/改选扣金 5000/SUPPORT 门槛解锁（matk 271=302×0.90 截断）/八行恒等（school 行 40）；浏览器 E2E：徽章/弹窗六卡门槛状态/当前标记/375px 零溢出/console 0 error。
7. **遗留**：武魂 school 归属字段与流派池过滤（shared randomAwakenForSchool 当前恒等全池，实装无增益）；武魂技能（文档 §16.1，需技能架构）；battleSoulPower 与流派系数未挂钩（宗门 Boss 伤害不受流派影响，口径留档）。

## ✨ 第二十轮：武魂技能系统（战斗最后一环）（2026-09-26）

1. **触发模型——冷却制、零新增掷点**：技能在第 r 回合释放当且仅当 `(r-1) % cooldown == 0`（cooldown 3~5，首回合即放）；技能回合的三掷照常发生（保持 RNG 流逐位不变），技能只改变伤害结算或改为治疗。**BattleMathTest 一行未改全绿** + 专项测试证明「cooldown 极大的技能流与无技能流同种子整场逐位相等」——无技能路径零漂移的硬性验收达成。
2. **四类结算**（12 武魂技能值从 shared Models.kt 逐字移植，4 条 cooldown 以 shared 为权威修正）：单体（缠绕 140%…海神之怒 250%，过 defFactor+暴击）、多段（幽冥突刺 2段×100% = 总倍率 2.0 单发等价，段数=(power/50).coerceIn(2,5)）、**无视防御（有效防御 ×0.5 软化**——文档「完全无视」会随怪防无限放大击穿平衡，按 shared 破甲击系先例重标定，power 180/300 照文档）、治疗（回复 maxHp×power/30~50%，该回合不攻击，封顶 maxHp）。BERSERK 无实现锚点留档。
3. **回放与展示**：BattleRoundLog 尾部 `skillName` 可空字段（技能回合标记，随 battleLog 返回）；前端回放技能高亮行 ✨（flex-wrap 换行不溢出）+ 治疗回合 💚 标记（替代误导性「输出 0」）；状态卡武魂区展示技能名；ProfileDto 尾部 soulSkillName；wiki 武魂分类补技能段（冷却机制+四类表）。测试 72→**76**。
4. **四处同源**：battle/towerBattle/getGameState/塔日志重模拟统一从 profile.martialSoulName 反查技能（CombatStats.skill 携带 + playerMaxHp 尾参），塔日志回放一致性断言保持。
5. **集成验证**：mvn test 基线 213→**226 全绿**（SoulSkillTest 11 + 集成 2）；真库冒烟：觉醒后 soulSkillName 暴露、3 场战斗全部第 1 回合触发缠绕、治疗回合 200→274（+74 ≈ maxHp×30%）；浏览器 E2E：回放第 1 回合「✨ 技能「缠绕」」高亮 + 状态卡「技能：缠绕」+ wiki 技能段，375px 零溢出、console 0 error。
6. **仿真收敛锁原样通过**：主画像技能正向 buff——90 天推图 7-2→**8-10**（首次抵达 8 号图终局）、金币 +19%、等级 29→30；负荷回路画像（无武魂）逐位不变。
7. **遗留**：BERSERK 类型无实现锚点（副作用机制不存在）；HEAL 与跨场次 currentHp 持久化的长线影响未专项仿真；怪物技能（v1 明确不做）；README 特性清单本轮已刷新至 20 轮现状。

## 📅 第二十一轮：成熟度打磨——补签/成就补齐/指标面板（2026-09-26）

1. **补签机制（签到闭环）**：`POST /api/game/checkin/makeup`——只能补「昨天」这一天，花费 500 金，**只修复连签不补发当日奖励**（定价与语义为实现决策，注释留档）；streak 基准=昨天前最后一条记录快照+1；uk_user_date 防重 + DIVE 归一（穿过事务边界时静默 return 会炸 UnexpectedRollbackException，故归一为 400 而非 200+success=false——实现注释留档）；`CheckInStatus.makeupAvailable` 后端精确计算三态（从未签到/断签/昨日已签）。已知边界：先签今天再补昨日时，今天行快照不回改，前向 streak 不修复（「只能补昨天」的固有语义，留档）。
2. **成就补齐 16→21**（设计文档 §10 全表对齐）：+cult_150 神王降临 / battle_200 千战精英 / battle_1000 万战传说 / tower_100 杀戮之王 / prestige_5 五世轮回——全部走现有五类口径管道（前端零改动自动渲染）；文档「DEF+n」统一落 pdef+mdef 双防（多数既有口径）。
3. **业务计数器（7 个，名字逐字契约）**：`douluo.battle.total`(outcome tag)/`douluo.checkin.total`/`douluo.checkin.makeup.total`/`douluo.awaken.total`(kind tag)/`douluo.prestige.total`/`douluo.school.choose.total`/`douluo.quest.claim.total`(questId tag)——MeterRegistry 构造注入，成功出口 increment；Prometheus 端点实测可见。
4. **运维栈（ops/ 新目录）**：prometheus.yml + alerts.yml（5 条告警：InstanceDown/HighErrorRate/HighLatencyP99/JvmMemoryPressure/DbPoolExhausted，各带阈值理由）；Grafana 11 面板手写仪表盘 + 自动 provisioning（业务速率/资源/JVM/连接池）；backup.sh（mysqldump+gzip+保留期，bash -n 过）；docker-compose 增 prometheus/grafana 服务（`profiles: ["monitoring"]` 隔离，UI 端口绑 127.0.0.1）；DEPLOY.md 补「监控与备份」。本机无 Docker，compose 用 PyYAML 模拟校验（诚实沿用「未实跑」声明）。
5. **死数据清理（谨慎模式）**：GameModels 删 12 个零引用符号（MapStats/MapData/RealmData/EquipAffix/TowerData 等，344→199 行，头部留档去向）；towerBossKills 列保留+「预留」标注；GameBalance 扫描无零引用项。
6. **集成验证**：mvn test 基线 226→**247 全绿**；真库冒烟：makeupAvailable 三态/补签扣金连签 1→2→3/21 项成就含 5 新项/计数器进 Prometheus 端点；浏览器 E2E：断签态补签条渲染→点击→扣金 2000→500→按钮消失、成就页 21 行含 5 新项、375px 零溢出、console 0 error。
7. **遗留**：makeupAvailable 需要的昨日 exists 查询在 signedToday 分支多一次查询（量级可忽略）；备份未实跑恢复演练（脚本逻辑 bash -n 级验证）；监控栈未在真 Docker 实跑（本机无引擎，与第五轮 Docker 化同款诚实声明）。

## 🗺️ 第二十二轮：地图扩展 8→11 张 + 宗门 Boss 周榜（2026-09-26）

1. **地图扩展**：MAP_NAMES 追加神王殿(8)/至高神庭(9)/创世之巅(10)（续神界线提案，设计文档无第 9 张后设定），MAX_MAP_ID 7→10；monsterStats/掉落/Boss币公式线性自适应零改动。**前置纠偏**：此前以为「越界显示未知」，实测 MAP_NAMES 本有 8 条（第 8 张神界废墟）、「8-10」是 1-based 展示——真实缺口是玩家满图驻留（7 图 15 关永久踏步），扩展后 90 天仿真推图 11-15 抵达创世之巅终局。
2. **战斗掉落年份封顶 `BATTLE_RING_DROP_YEAR_CAP = 3`**（本轮唯一生产平衡改动）：地图 8-10 使战斗掉落进入 tier-4 百万年环（负荷 60 万~999 万），90 天仿真容量结构性装不下 → 死掉落率 66.8% 破锁；按 TOWER_RING_DROP_YEAR_CAP=2 先例封顶 3（与 GameBalance「3~4 档保留为终局/轮回内容」设计注释一致）。**地图 0-7 逐位零漂移**（第十七轮校准成果未动），封顶后收敛锁全绿（死掉落率 0%、利用率 72.5%、10 种子）。智能体曾尝试 AskUserQuestion 征询未获回复（自主模式），按最小伤害原则执行并留档一行可回退。
3. **宗门 Boss 周榜**：V10 迁移 guild_member.weekly_boss_damage；challengeBoss 同处累加；`GuildWeeklyResetService` 每周一 03:03 结算前 3 名（第 1 名 Boss币+50/金币+5000…第 3 名 +20/+2000，GuildBalance 常量化）后全量清零；`GET /api/guild/boss/rank`（降序/只含>0/上限 10/昵称批查防 N+1/myRank）；**SchedulingConfig 放宽**——@EnableScheduling 从 audit.cleanup-enabled 条件解绑为无条件（各调度 Bean 自带条件），application-test.yml 补 guild.weekly-reset-enabled=false。
4. **前端**：MAP_NAMES 抽至 lib/maps.ts 单一事实源（Next 16 路由导出限制，page.tsx 导入）+ mapNames.test 钉契约；wiki 地图分类 +3；宗门页 Boss 卡新增「本周伤害榜」（BossRankPanel 纯组件：Top3 领奖台/不足 3 条全列表/自己行高亮(我)/空榜灰字/失败静默）；拉取时机=进宗门页+挑战成功后。测试 82→**91**。
5. **集成验证**：mvn test 基线 247→**261 全绿**；真库冒烟：神界废墟 15 关胜利→推进神王殿、战斗名「神王殿·1层怪物」、双账号 Boss 挑战→周榜降序+myRank 互为 1/2+DB 累加；浏览器 E2E：周榜面板（我的排名 #2、(我) 高亮、2 人不立台）、地图 8 显示「神王殿」（期间一个假 bug：拿新号看新地图名，数据本身没到位）、375px 零溢出、console 0 error。
6. **遗留**：设计文档 6.1 的地图解锁等级/费用从未实现（推进无门槛，历史行为保持）；周重置未实跑（cron 周一 03:03，逻辑由单测覆盖）；新图怪物仍为线性数值+无技能（文档无更高层设定）。

## 🐲 第二十三轮：共享血量宗门 Boss（weekly raid 化）+ 每日任务 7 条（2026-09-26）

1. **共享血池（旗舰）**：Boss 血量从「每次挑战即时算、各自独立结算」升级为**全宗门共享的周血池**——V11 迁移 `guild_boss` 表（guild_id PK/current_hp/max_hp/killed/week_start，惰性初始化），周池 = (1800+宗门等级×650)×10（≈全员 10 次挑战量级）；成员挑战从同一池原子扣血，**扣减后归零即全宗门击杀**：击杀者额外 +30 Boss币/+3000 金（量级对齐周榜 Top1=50 币），killed 后本周挑战拒绝（下周一重生）。
2. **并发正确性**：扣血用 **SELECT FOR UPDATE 行锁，锁锚选 guild 行而非 boss 行**——boss 行本周首挑战前不存在（条件 UPDATE 无行可命中、并发双 INSERT 撞主键污染事务），guild 行恒存在是天然锚点，顺带覆盖惰性建行竞态；锁序 guild→boss→member 与周重置（boss→member）一致防循环等待。周重生顺序：**Boss 重生 → 周榜结算 → 清零**（单事务）。
3. **每日任务 5→7**：+guild_donate（宗门捐献 1 次，150 金）/ +breakthrough（境界突破 2 次，150 金+200 魂力——突破耗魂力故返魂力）；挂点走 recordProgress 通用入口（GuildService 注入 DailyQuestService 无循环依赖）；前端任务卡 defs 驱动零改动自动渲染 7 条。
4. **前端**：Boss 卡整体抽为 GuildBossPanel（照 BossRankPanel 先例）：共享血条（progressbar 三值+千分位）、击杀横幅 ☠（归零态+挑战禁用+title）、击杀高亮 🎉、status 404/失败降级隐藏血条但挑战照旧（旧后端容错）；拉取时机=进宗门页+挑战成功后。测试 91→**97**。
5. **集成验证**：mvn test 基线 261→**283 全绿**；真库冒烟：惰性初始化满血 24500 精确、双成员共扣同一池（24500→18328→12126）、第 3 挑战击杀（击杀者 Boss币+30、message「全员协力击杀！」）、击杀后 400+状态归零、新任务钩子（捐献/突破进度各 +1/+2）；浏览器 E2E：击杀态 ☠ 横幅+按钮禁用、7 任务卡渲染、375px 零溢出、console 0 error。ops/README.md 指标契约表补第 9 行（douluo_guild_boss_kill_total，由主线补录）。
6. **遗留**：周重生的 Boss 重生未实跑（cron 周一，逻辑单测覆盖）；raid 血池数值未做专项平衡仿真（宗门 Boss 不在 90 天镜像内，量级按「全员 10 次」设定）；击杀奖励仅击杀者（全员奖励走既有周榜 Top3）。

## 🔒 第二十四轮：发布候选——安全自审 + 全量回归（2026-09-26）

1. **安全自审（56 个端点认证矩阵全核对，防御性自审）**，修复 5 项：
   - **高：限流可被 X-Forwarded-For 伪造绕过**（登录防爆破失效）——仅当 TCP 对端为可信代理（回环/RFC1918，覆盖 nginx 同机部署）才采信转发头且取**末段**（代理追加语义下的真实客户端）；不可信对端一律 remoteAddr
   - **高：JWT 黑名单 TTL(1h) < token 有效期(24h)**——登出 token 可「复活」23 小时；黑名单生效 TTL = max(配置值, token 过期时间)
   - 中：/api/auth/me 匿名 500 → 401；GuildController 四个 body 零校验（超长宗门名直达 DB 500）→ @Valid + 长度/范围对齐 V1 列宽；/api/health 匿名端点回显 DB 异常细节 → 固定 "DOWN"
   - 矩阵结论：56 端点认证全覆盖（第二十三轮 boss/status、boss/rank 均落 authenticated，MockMvc 匿名 403 实测）；IDOR 零发现（userId 全部取自 principal）；SQL 注入零发现（11 处 @Query 全参数化）；CORS 正确；限流评估 300 RPM 全局 + 10 RPM auth 够用（battle 高频 60 RPM 量级）
2. **全量回归清扫（浏览器，12 路由 × 双主题 × 375px）**：零 console error 零溢出；完整用户旅程（觉醒→流派→战斗→回放技能高亮→任务→成就→宗门→wiki）0 error。发现并修复 4 项前端问题：
   - **高：hydration mismatch**——带 token 访问 `/` 时 SSR/客户端首帧不一致（isLoading 用 localStorage 惰性初始化，整树重建）→ authChecked 两段式鉴权门控（AuthContext + layout）
   - 中：/game 布局在鉴权完成前误踢已登录用户（与上联动修复）
   - 低：匿名 /game/* 在鉴权前发 state 请求 → 403 console 噪音（refresh 无 token 跳过）
   - 低：三个确认弹窗 focus 触发滚动到底部（focus preventScroll 修复，标题与首选项可见）
3. **集成验证**：mvn test 基线 283→**310 全绿**（安全测试 27 条：认证矩阵 MockMvc 匿名 403/限流伪造不可绕过/JWT 黑名单覆盖生命周期/校验边界值）；前端 97 条全绿 + build 成功；实机验证：/api/auth/me 匿名 401（原 500）、同源 30 次登录整齐 429（10 RPM 触发）、带 token 访问 `/` 零 error（修复前 2 条 hydration error）。
4. **遗留**：/api/auth/me 在 auth 限流桶（SPA 高频刷新可能先触顶，建议读端点划归 global 桶）；多实例部署前置 Redis（黑名单/限流桶均为单机内存）；WebSocket allowedOriginPatterns("*") 随生产域名收敛；nginx 跨机部署需扩展 isTrustedProxy 范围。
5. 回归清扫由子智能体执行（子代理环境 IAB 不可用，改用本地 Playwright 等价执行，40 张截图存证于临时目录）。

## 🤖 第二十五轮：放置体验补全——自动战斗 + 全服公告 + 数据治理（2026-09-27）

1. **自动战斗/自动突破/自动推图三开关**（放置类游戏的灵魂功能补全）：Profile 的 autoBattle/autoBreakthrough 两个死字段激活——`PUT /api/game/settings`（全后端第一个 PUT，三字段显式传值+部分更新语义，返回完整 Profile）；前端 `useAutoBattle` 客户端循环（2s/tick 直调 api.battle() 不走 runAction，状态刷新交给 WS 广播 debouncedRefresh；document.hidden 跳过；429 按 60s 退避；上场未返回防堆积；自动突破=魂力达 120·L^1.55 阈值顺手调一次）+ 战斗卡三 toggle chips（乐观更新+失败回滚）。频率核算：30(battle)+5(state) RPM ≈ 300 桶 12%。**设计依据**：autoBattle/autoBreakthrough 本就被 shared 引擎注释定性为客户端行为，离线侧已有离线收益闭环，在线侧前端循环正好衔接。
2. **WS 全服公告激活**：broadcastAnnouncement（零调用死代码）首个生产调用——宗门 Boss 击杀时全服广播「⚔️ 「XX宗门」全员协力击杀了宗门 Boss！」；前端 live.ts 加 /topic/announcement 订阅 + useLiveAnnouncement hook，公告走 message 条 toast（📢 前缀 + testid）。真库实测跨用户 WS 推送全链路（A 宗门击杀 → B 浏览器 toast）。
3. **/api/auth/me 限流分级**（第二十四轮遗留）：me 读端点从 auth 桶（10 RPM）移入 global 桶（300）——SPA 高频刷新不再触顶，login 防爆破不变（单测：me×11 全 200 而 login×11 第 11 次 429、me 与 global 共享桶验证）。
4. **dev 数据治理脚本（ops/cleanup/）**：34 个测试账号（e2e/r13-r24 系列）按用户名正则清理；9 表 users 级联自动清 + backpack_item/shop_purchase_record 无 FK 手动清 + 测试号宗主宗门连带删；默认 DRY-RUN、--apply 真删（交互 yes 二次确认）、--keep-guild-ids 保留回归夹具、--purge-audit 清审计；干跑实测（31 用户/3 宗门/28+8 孤儿行预估）。**修复超时智能体残留的 count_manual 未定义函数 bug**。
5. **集成验证**：mvn test 基线 310→**319 全绿**；真库冒烟：PUT 全量/缺字段 400/持久化回读、me×11 无 429、击杀广播触发；浏览器 E2E：三开关初始态与翻转、公告 toast 跨用户全链路 ✓、375px 零溢出。**自动战斗实弹验证受 IAB 文档可见性限制**（面板后台时 document.hidden=true，循环按设计跳过——正确行为：后台页签不烧限流配额；循环逻辑由 7 条 fake-timer 单测覆盖：翻转启停/429 暂停/hidden 跳过/防堆积/突破阈值/失败静默）。
6. **遗留**：WS allowedOriginPatterns("*") 生产收敛；多账号同 IP 同开自动战斗共享 300 RPM 桶（429 退避已内置）；raid 血池专项平衡仿真仍留档。

## 🔨 第二十六轮：魂骨强化系统（金币回收池）（2026-09-27）

1. **强化端点**：`POST /api/equipment/bone/enhance` 双路径（itemIndex 背包骨 / slotIndex 已装骨 0-5，二选一校验）；上限 `BONE_ENHANCE_MAX_LEVEL=15`（文档 §4.2 写 0~5，但掉落已白送 max(1, level/10) 最高 +9、测试数据 +11——按现状校准，差异双向留档）；费用曲线 `800×(等级+1)²×(年份+1)×品质倍率`（0→1 千年 2400、9→10 百万年完美 880k，满配累计 ~99.2 万/件 ≈ 10~15 天主动收入——补 840k~2.6M 存量无处的金币 sink，前期单次 ≤ 日收入 20%）；**100% 成功无失败机制**（文档未定价项的「实现决策+锚点」惯例，费用陡增替代概率失败）；属性乘区沿用既有 0.10/级零改动（改文档的 0.15 会推翻仿真收敛结论）。
2. **战力明细八行→九行**：enhance 行差值法（骨行改按全骨 enhanceLevel 归零口径重算，enhance 行 = 含强化 − 归零，与 prestige/soul/school 同款），九行之和恒等 == power（200 组随机含强化 0~15 混合回归）；无强化骨时恒 0 退化八行与旧版逐位一致。
3. **前端**：背包 BONE 卡第三钮「强化（X金币）」（费用公式镜像 lib/equipment.ts 同源 + 三锚点测试：800/38400/880000）；已装骨槽内嵌强化钮（HTML button 嵌套问题→外层 div[role=button]+键盘等价+stopPropagation，仅骨槽降级，环/核不动）；PowerDetailPanel 第 9 行 🔨 强化（四~九行动态回退）。测试 106→**117**。
4. **仿真镜像**：EquipSimPlayer 金币分支加强化策略（金币 ≥3 万防线时强化最优骨）+ 扣费镜像；10 种子收敛锁全过（利用率 72.4~72.5% 带内、9/9 槽、满包丢掉落与关闭强化完全一致）；**强化消耗占日收入 62.9~78.0%**——健康的金币回收（此前存量 840k~2.6M 无处去）。
5. **集成验证**：mvn test 基线 319→**327 全绿**；真库冒烟：背包强化费用逐位（2400=800×1²×2×1.5）、已装槽路径（9600/21600）、双参数 400、上限拒绝、九行恒等（enhance 行 49→64 随强化增长）；浏览器 E2E：已装骨槽内嵌强化钮、强化成功 toast（+3→+4 花 38400）、主页第九行强化加成 64。
6. **遗留（设计发现）**：塔掉落魂骨白送强化 max(1, towerLevel/10) 满层可达 +30，超过主动强化上限 +15——终局塔骨不可再强化（端点正确拒绝）；若对齐 min(15, ...) 会削减既有玩家战力，留档为主线决策。affixesJson 死列与 coreLevel 死字段维持留档。

## 🎴 第二十七轮：魂骨词缀系统（最后一列死列激活）（2026-09-27）

1. **词缀生成**：affixesJson 死列（V1 建表恒 null）激活——rollBackpackDrop 的 BONE 分支末尾 roll 词缀（条数=品质+1 即 1~5 条照 §3.1 档位精神、5 类型不重复抽取、数值按品质线性插值：暴击率 1→8/暴伤 5→40/物防魔防 3→60/魔攻 4→80，类型池照 §4.3 拼装提案）；**RNG 次序变更**：BONE 分支追加「逐条类型 nextInt」掷点（数值由品质决定无掷点），方法 KDoc 写死次序①~⑥；battle 内联掉落 BONE 不加词缀（普通/稀有掉落分层，留档）。
2. **随件搬运 + 消费**：V12 迁移 equipped_bone 加 affixes_json（equip 属性拷贝模式必须随件搬运）；equipBone/unequipBone/prestige 三处搬运（漏一处即同类丢词缀 bug）；EquipmentPowerService.bonus() 骨循环解析词缀累加五属性进 EquipmentBonus（第十七轮管道消费）；**detail 归因修复**——装备侧五属性折算从 achievement 行拆到骨行（最大余数法分配），无词缀时退化与旧口径逐位一致。
3. **WS origin 收敛**（第二十四轮遗留）：WS_ALLOWED_ORIGINS 环境变量（缺省 "*" 向后兼容），application-prod.yml 示例注释照 CORS_ORIGINS 先例。
4. **前端**：AffixChips 纯组件（四层解析容错：非法 JSON/非数组/缺字段/未知类型全部安全降级）+ 骨卡两处渲染（背包 item 档全称 11px / 骨槽 slot 档缩写 10px：暴/爆伤/物防/魔防/魔攻）+ stopPropagation 防误触宿主点击。测试 117→**136**。
5. **集成验证**：mvn test 基线 327→**344 全绿**（BoneAffixTest 6 + WebSocketConfigTest 4 + 既有适配）；真库冒烟：塔掉落 4 骨全带词缀（品质 4 → 5 条满档值逐位）、装备搬运保留、combatStats 消费（魔攻 515 含词缀 +80）、卸装带回；浏览器 E2E：词缀 chips 两处渲染（按类型配色 flex-wrap）、375px 零溢出、console 0 error。
6. **过程中的真 bug**（后端智能体自抓自修）：detail 首版漏把词缀折算计入 fiveRowSum，词缀增量漏进 prestige 差值行造成双重计入——被新增的 200 组随机含词缀回归用例暴露（差值恒等于词缀折算值 80），修复并注释留档。
7. **遗留**：战斗内联掉落 BONE 无词缀（普通/稀有分层设计）；词缀无重 roll/转移机制（天然防刷）；文档无词缀数值表——本轮数值表为「§4.3 类型池 + §3.1 条数框架」拼装提案，已注释标注。

## 🏗️ 第二十八轮：架构重构——GameService 拆分 + 死代码清理 + 前端组件化（2026-09-28）

1. **后端拆上帝类**：`EquipService.kt`（330 行）自 GameService 逐行搬移装备域 7 方法（equip/unequip 环·骨·核 + enhanceBone，EnhanceResult 随迁）；GameService 保留同名 `@Transactional` 门面委托（调用面与既有测试零改动），事务边界=门面开事务、EquipService REQUIRED 加入，拆分前后逐项等价。GameService 1121 行（净 -318）。随件单点化：RingLoadCalculator 吸收 `absorptionCapacityFor`（getGameState 展示与 equipRing 校验同源）、GameBalance 新增 `playerBaseMaxHp(level)` 公式（GameService 与 RingLoadCalculator 共用防漂移）。
2. **PlayerSaveNotFoundException 类型化**：7 个抛出点（GameService/CheckInService/DailyQuestService/EquipService）从「中文子串猜 IllegalStateException」改为类型化异常 → GlobalExceptionHandler 404；`handleIllegalState` 删掉 `contains("存档")` 猜测一律 500，响应体逐字段不变。
3. **样板收敛 + 安全补强**：`AuthenticationExtensions.userId()` 收敛 6 控制器 40+ 处 `principal as Long` 强转；TalentController 升级端点无效分支改抛 IllegalArgumentException 走统一 400；`DatasourcePasswordValidator`（prod profile 下 datasource.password=change-me/空 → 启动失败，非 prod 告警，照 JwtUtil.validateSecret 先例）。
4. **死代码/遗留物删除（-2743 行）**：`web/` 整目录（旧 Kotlin/JS 客户端 911 行，settings.gradle 本就未 include）、shared jsMain（PlatformTime/WebStorage + js(IR) target，commonMain/androidMain expect/actual 配对完整不受影响）、四份已被 Flyway V1~V12 取代的手写 SQL（backend/database/、database/init.sql）、build_deploy.bat/start_server.bat。全仓 grep 无悬空引用。
5. **前端组件化 + 数据单实例化**：`GameDataProvider` 挂 game/layout.tsx——整个 /game 树一份 useGameData 实例（12s 轮询/WS/首拉）；page.tsx 864→144 行，拆出 7 组件（PlayerStatusBar/BattlePanel 含 useAutoBattle 内聚/QuickNav/OfflineRewardModal/PrestigeDialog/ReawakenDialog/SchoolDialog，DOM 与拆分前逐字节一致）；hooks.ts 增 `useModalEscape`（Esc 关闭+聚焦取消钮，三 Dialog 采用）；8 个 game 页 import 切至 GameDataContext。
6. **测试**：后端 344→**356 全绿**（+DatasourcePasswordValidatorTest 4 / ControllerSmokeTest 6（Action/Equipment/Shop 匿名 401·403 与 JWT 200 成对冒烟）/ GlobalExceptionHandlerTest +2 类型映射）；前端 136→**140 全绿 + build 成功**（hooks.modal.test +4）；package.json 增 test:run/typecheck 脚本。**runMessageAction 迁移为渐进式**（page 补签/BattlePanel 突破/PrestigeDialog/equipment/guild 已迁，checkin/claimQuest/guild Boss 仍手写三参，留后续轮）。
7. **文档/杂项**：README/DEPLOY/docker-compose 同步 Flyway V1~V12 与 web/ 删除说明；.gitignore 增 backend/logs 与 *.tsbuildinfo；数值仿真报告-90天.md 重跑补账（第二十七轮 RNG 次序变更后）。**typecheck 脚本未接入 CI**（ci.yml 仍只 lint/build，留后续轮）。

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
