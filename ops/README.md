# ops/ —— 监控与备份运维资产

Docker 部署形态下的监控栈（Prometheus + Grafana）与数据库备份脚本。指标来源：后端 `/actuator/prometheus`（micrometer-registry-prometheus，非 prod 匿名可抓，prod 默认关闭，详见 `DEPLOY.md`）。

## 目录结构

| 文件 | 用途 |
|------|------|
| `prometheus/prometheus.yml` | Prometheus 抓取配置：抓 compose 内网 `backend:8080/actuator/prometheus`，15s 间隔；`rule_files` 加载告警规则 |
| `prometheus/alerts.yml` | 告警规则（InstanceDown / HighErrorRate / HighLatencyP99 / JvmMemoryPressure / DbPoolExhausted），每条含 severity 与阈值理由 |
| `grafana/douluo-dashboard.json` | 总览仪表盘（11 面板：概览 / 业务 / 资源三行），由 provisioning 自动导入，无需手工 import |
| `grafana/provisioning/datasources/prometheus.yml` | 自动注册 Prometheus 数据源（指向 `http://prometheus:9090`） |
| `grafana/provisioning/dashboards/douluo.yml` | 仪表盘文件提供者（扫描 `/var/lib/grafana/dashboards`） |
| `backup/backup.sh` | MySQL 备份脚本：容器内 mysqldump + gzip + 时间戳文件名 + 保留 N 天（`BACKUP_RETAIN_DAYS`，默认 7），用法见脚本头注释 |

## 启用监控栈

监控服务用 compose profile `monitoring` 隔离，默认 `up` 不启动：

```bash
docker compose --profile monitoring up -d        # 与业务容器一起启动
# 或只启动监控栈（后端须已在跑）：
docker compose --profile monitoring up -d prometheus grafana
```

- Prometheus：`http://127.0.0.1:9090`（默认只绑本机回环，公网暴露请走 Nginx 反代 + ACL）
- Grafana：`http://127.0.0.1:3001`，默认账号 admin / admin（首次登录强制改密；可用 `.env` 中 `GRAFANA_ADMIN_PASSWORD` 覆盖），数据源与「斗罗大陆 · 放置传说 总览」面板已自动注入
- 停止：`docker compose --profile monitoring down`（数据卷 `prometheus-data` / `grafana-data` 保留）

## 业务指标名契约（后端 Micrometer 计数器，逐字使用）

Micrometer 的 `douluo.battle.total` 等点分名会被 Prometheus 自动转成下划线形式，Grafana/告警一律使用**下划线名**：

| 指标名（Prometheus 形式） | tag | 含义 |
|------|-----|------|
| `douluo_battle_total` | `outcome=win\|lose` | 战斗次数（按胜负分） |
| `douluo_checkin_total` | — | 每日签到次数 |
| `douluo_checkin_makeup_total` | — | 补签次数 |
| `douluo_awaken_total` | `kind=first\|reawaken` | 觉醒次数（首次/再觉醒） |
| `douluo_prestige_total` | — | 转生次数 |
| `douluo_school_choose_total` | — | 流派选择次数 |
| `douluo_quest_claim_total` | `quest_id` | 任务领取次数（按任务分） |

标准 micrometer/actuator 指标同样可用：`jvm_memory_used_bytes` / `jvm_memory_max_bytes`（tag `area`、`id`）、`jvm_gc_pause_seconds`、`hikaricp_connections_active/idle/pending/max`、`http_server_requests_seconds_count/bucket/sum`（tag `uri`、`status`、`outcome`）、`process_cpu_usage`、`system_cpu_usage`。修改业务指标名前先同步本表、alerts.yml 与 douluo-dashboard.json。

## 数据库备份

```bash
# 手动备份（默认输出 /opt/backup/douluo，保留 7 天）
./ops/backup/backup.sh

# cron 每日 03:00（详见脚本头注释）
0 3 * * * cd /opt/app/douluodalu && ./ops/backup/backup.sh >> /var/log/douluo-backup.log 2>&1
```

恢复命令见 `ops/backup/backup.sh` 头注释；PM2（非 Docker）部署形态的备份见 `DEPLOY.md` 第 12 节。
