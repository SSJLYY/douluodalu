#!/usr/bin/env bash
# ============================================================================
# 斗罗大陆 · MySQL 数据库备份脚本（docker compose 部署用）
#
# 原理：docker exec 进入 douluo-mysql 容器调用容器内 mysqldump（密码直接读
#       容器内的 $MYSQL_ROOT_PASSWORD 环境变量，不落盘、不进 ps），输出经
#       gzip 压缩为 <库名>_YYYYMMDD_HHMMSS.sql.gz，并自动清理超过保留期的旧备份。
#
# 环境变量（均可覆盖，默认值适配 docker-compose.yml）：
#   MYSQL_CONTAINER     mysql 容器名           默认 douluo-mysql
#   DB_NAME             要备份的库             默认 douluo_game
#   BACKUP_DIR          备份输出目录           默认 /opt/backup/douluo
#   BACKUP_RETAIN_DAYS  保留天数               默认 7
#
# 手动执行：
#   cd /opt/app/douluodalu && ./ops/backup/backup.sh
#
# cron 每日 03:00 自动备份（root 用户，路径按实际仓库位置调整）：
#   crontab -e
#   0 3 * * * cd /opt/app/douluodalu && ./ops/backup/backup.sh >> /var/log/douluo-backup.log 2>&1
#
# 恢复（自含 CREATE DATABASE/USE 语句，重建空库亦可）：
#   gunzip < /opt/backup/douluo/douluo_game_YYYYMMDD_HHMMSS.sql.gz | \
#     docker exec -i douluo-mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
# ============================================================================
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-douluo-mysql}"
DB_NAME="${DB_NAME:-douluo_game}"
BACKUP_DIR="${BACKUP_DIR:-/opt/backup/douluo}"
BACKUP_RETAIN_DAYS="${BACKUP_RETAIN_DAYS:-7}"

log() { echo "[backup $(date '+%F %T')] $*"; }
die() { echo "[backup $(date '+%F %T')] ERROR: $*" >&2; exit 1; }

# ---- 前置检查：docker 可用、容器在跑 ----
command -v docker >/dev/null 2>&1 || die "docker 不可用：未安装或不在 PATH"
[ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null)" = "true" ] \
  || die "容器 $MYSQL_CONTAINER 未运行（先 docker compose up -d mysql）"

mkdir -p "$BACKUP_DIR" || die "无法创建备份目录 $BACKUP_DIR（权限不足？cron 下请以 root 运行或改 BACKUP_DIR）"

OUT="$BACKUP_DIR/${DB_NAME}_$(date +%Y%m%d_%H%M%S).sql.gz"

# ---- 执行备份 ----
log "开始备份 ${DB_NAME}（容器 ${MYSQL_CONTAINER}） -> ${OUT}"
# --single-transaction: InnoDB 一致性快照，不锁表；--databases: 自含建库语句便于恢复
# 管道任一环失败（pipefail）即整体失败，避免留下截断的半截文件当有效备份
if ! docker exec -e DUMP_DB="$DB_NAME" "$MYSQL_CONTAINER" \
      sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --databases "$DUMP_DB"' \
      | gzip > "$OUT"; then
  rm -f "$OUT"
  die "mysqldump 执行失败，已删除不完整文件"
fi

[ -s "$OUT" ] || { rm -f "$OUT"; die "备份文件为空，已删除"; }
log "备份完成：${OUT}（$(du -h "$OUT" | cut -f1)）"

# ---- 清理过期备份（只删本脚本命名格式，避免误伤手工备份）----
find "$BACKUP_DIR" -maxdepth 1 -name "${DB_NAME}_*.sql.gz" -type f -mtime "+${BACKUP_RETAIN_DAYS}" \
  | while read -r f; do rm -f "$f"; log "清理过期备份：${f}"; done

TOTAL=$(find "$BACKUP_DIR" -maxdepth 1 -name "${DB_NAME}_*.sql.gz" -type f | wc -l)
log "当前保留 ${TOTAL} 份备份（策略：约 ${BACKUP_RETAIN_DAYS} 天）"
