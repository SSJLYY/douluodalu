# ops/cleanup/ —— dev 数据治理脚本

清理本机 dev 数据库中 E2E / 冒烟测试残留的账号数据（第二十五轮交付）。**只适用于本机 dev MySQL8**（`root/123456`，库 `douluo_game`）；脚本是真实 `DELETE`，严禁指向共享/生产环境。

- `cleanup-dev-data.sh` —— 按用户名正则清理测试账号及其全部业务数据；**默认 DRY-RUN**，`--apply` 才真删；`--keep-guild-ids` 可保留回归夹具宗门。删除顺序、每张表的处理方式见下文。

## 为什么默认干跑

`DELETE` 没有撤销——脚本没有（也不应该有）事务回滚到任意时点的能力，恢复只能靠删除前的备份。默认 DRY-RUN 保证：

1. 直接回车只打印**将执行的每一条 DELETE 原文**和**影响行数估计**（对同一 WHERE 跑 `SELECT COUNT`），对数据库零写入；
2. 把"看清楚会删什么 → 备份 → 加 `--apply` → 输 `yes` 确认"强制为一个四步流程，任何一步都可以反悔；
3. `--apply` 在交互终端还要求输入 `yes` 二次确认，在 cron / 管道等非交互环境则必须再显式加 `--yes`——杜绝误粘贴、误 cron。

DRY-RUN 是只读的（纯 `SELECT`），可以放心随便跑。

## 用法

```bash
# 1) 预览（默认，只读）：打印待删用户/宗门明细 + 每步 DELETE 与影响行数
./ops/cleanup/cleanup-dev-data.sh

# 2) 预览：保留回归夹具宗门（其宗主与成员账号一并豁免删除）
./ops/cleanup/cleanup-dev-data.sh --keep-guild-ids 100,102

# 3) 备份后真删（交互终端会要求输入 yes）
./ops/cleanup/cleanup-dev-data.sh --apply --keep-guild-ids 100,102

# 4) 非交互全量清理（连审计日志一起清）
./ops/cleanup/cleanup-dev-data.sh --apply --yes --purge-audit
```

| 参数 | 说明 |
|------|------|
| （缺省） | DRY-RUN：只打印 DELETE 与影响行数估计，不改任何数据 |
| `--apply` | 真正执行删除；交互终端要求输入 `yes` 二次确认 |
| `--keep-guild-ids 100,102` | 逗号分隔宗门 ID：这些宗门连同其宗主、成员用户**一并豁免**（用于保留跨轮回归夹具，如宗门 100） |
| `--purge-audit` | 连带删除 `audit_log` 中孤儿审计行（默认保留，后端 30 天自动清理任务会收） |
| `--yes` | `--apply` 时跳过交互确认（cron / 脚本环境必须加） |
| `-h` / `--help` | 帮助 |

环境变量（默认值即本机 dev 配置，一般不用动）：`DB_HOST=127.0.0.1`、`DB_PORT=3306`、`DB_NAME=douluo_game`、`DB_USER=root`、`DB_PASSWORD=123456`、`USERNAME_PATTERN='^(e2e|r1[3-9]|r2[0-4])'`（清理匹配范围，改前务必重新人工核对用户列表）。

## 删除范围

清理对象：`users.username REGEXP '^(e2e|r1[3-9]|r2[0-4])'`，即第 1~24 轮 E2E/冒烟残留账号（`e2e%` 与 `r13%`~`r24%` 前缀）。外键结论于 2026-09-26 用 `information_schema` 实测核对。

| 表 | 清理方式 | 依据 |
|----|----------|------|
| `player_profile` | 随 `users` 行 **ON DELETE CASCADE** 自动清 | FK `user_id → users.id`，CASCADE |
| `player_talent` | 同上 | 同上 |
| `guild_member` | 同上；宗门处理步骤中还会按 guild 显式删一遍 | FK `user_id → users.id` 与 `guild_id → guild.id` 均 CASCADE |
| `equipped_ring` | 同上 | FK `user_id → users.id`，CASCADE |
| `equipped_bone` | 同上 | 同上 |
| `equipped_core` | 同上 | 同上 |
| `check_in_record` | 同上 | 同上 |
| `daily_quest_progress` | 同上 | 同上 |
| `achievement_record` | 同上 | 同上 |
| `backpack_item` | **手动清**：`user_id NOT IN (SELECT id FROM users)` | 无外键，删 users 不会联动 |
| `shop_purchase_record` | **手动清**：同上 | 无外键，同上 |
| `audit_log` | 默认保留；`--purge-audit` 时按孤儿行清 | 日志表，后端 30 天自动清理任务兜底 |
| `guild` / `guild_boss` | 见下节「宗门处理」 | `guild.leader_id` 无外键，存在孤儿风险 |

### 宗门处理（孤儿风险）

`guild.leader_id` 没有外键：宗主账号被删会留下**孤儿宗门**（以及连带行 `guild_boss`，其 FK `guild_id → guild`）。因此脚本在删用户**之前**，把「宗主属于待删用户、且不在保留列表」的宗门按序清掉：

```
DELETE FROM guild_boss    WHERE guild_id IN (...);   -- 宗门 Boss 周状态（FK→guild，CASCADE，先显式删）
DELETE FROM guild_member  WHERE guild_id IN (...);   -- 成员关系（FK→guild，CASCADE；不显式删也会随下一步级联，显式删以求顺序明确）
DELETE FROM guild         WHERE id IN (...);         -- 宗门本体（leader_id 无 FK，必须在删用户前处理，否则无法再定位）
DELETE FROM users WHERE username REGEXP '...'        -- 之后才删用户，9 表随 CASCADE 自动清
```

`--keep-guild-ids` 列入的宗门不走上述删除，其宗主与成员账号也从用户删除范围中豁免。

### 执行顺序

1. `guild_boss`（待删宗门）→ 2. `guild_member`（待删宗门）→ 3. `guild`（待删宗门）→ 4. `users`（9 表级联）→ 5. `backpack_item` 孤儿 → 6. `shop_purchase_record` 孤儿 → 7. `audit_log` 孤儿（仅 `--purge-audit`）。`--apply` 执行完会再核查 9 张级联表的孤儿残留（应全为 0）。

## 与 backup.sh 的配合（先备份，再清理）

```bash
# Docker 部署形态：
./ops/backup/backup.sh                                        # 快照到 $BACKUP_DIR
./ops/cleanup/cleanup-dev-data.sh                             # DRY-RUN 核对
./ops/cleanup/cleanup-dev-data.sh --apply                     # 确认后执行

# 本机 dev（无 docker）：
mysqldump -uroot -p --single-transaction douluo_game > douluo_game_dev_$(date +%Y%m%d_%H%M%S).sql
bash ops/cleanup/cleanup-dev-data.sh --apply
```

恢复 = 用删除前的备份整库回灌（`gunzip < 备份.sql.gz | docker exec -i douluo-mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'`，或本机 `mysql -uroot -p < 备份.sql`）。**没有备份就没有恢复**——这一条是脚本头注释与本文共同的约定。

## 已知边界

- 级联删除不会同步 `guild.member_count` 列：若保留宗门中有成员账号被删，该计数需等宗门下次成员变动由后端刷新，或手工修正。
- 手动清理按「孤儿行」判定（`user_id NOT IN (SELECT id FROM users)`），因此也会顺带清掉此前遗留的孤儿数据——属预期收益。
- 两次 DRY-RUN 与 APPLY 之间若又有新测试账号注册，APPLY 会按同一正则**实时**重算删除范围（估计行数以 DRY-RUN 时点为准）。
- 首次交付时（2026-09-26）dev 库共 34 个用户，全部匹配清理模式、无真实用户；宗门 4 个（1/100/102/103）宗主均为测试账号。
