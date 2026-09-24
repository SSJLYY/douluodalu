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

## 🔮 后续建议

1. **前端优化**
   - ~~Tailwind v4 主题切换（light/dark）~~ ✅ 已完成（第三/四轮，含 WCAG AA 亮色打磨）
   - ~~登录注册页动画 + 玻璃态效果~~ ✅ 已完成（第十轮）
   - ~~Next.js ISR（排行榜缓存）~~ 以"进入拉一次 + 手动刷新"达成同等体感（第十轮）；SSR/ISR 需脱离客户端 AuthProvider 布局 + 配 Next rewrite 代理，暂缓
   - ~~战力明细面板（攻击/生命/各装备件拆分）~~ ✅ 已完成（第九轮）

2. **运维**
   - Docker 化（后端 Dockerfile + docker-compose）— 未完成
   - ~~CI/CD（GitHub Actions）~~ ✅ 已完成：`.github/workflows/ci.yml`，push/PR to main 触发，backend `mvn -B test` + frontend `lint`/`build` 两个并行 job（Android Gradle 工程不构建）
   - Prometheus 指标监控

3. **高级特性**
   - Redis 分布式锁（多实例部署）
   - Spring Cache（背包/装备读多写少）
   - API 版本控制 `/api/v1/...`
