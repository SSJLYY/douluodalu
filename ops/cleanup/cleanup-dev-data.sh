#!/usr/bin/env bash
# ============================================================================
# 斗罗大陆 · dev 数据治理：清理 E2E/冒烟测试残留账号（第二十五轮交付）
#
# 【适用环境】仅限本机 dev MySQL8（root/123456，库 douluo_game）。
#   这是真实 DELETE 的危险脚本：误用于共享/生产环境 = 不可逆数据丢失。
#
# 【恢复方式】没有"撤销"，只有备份。删除前务必先备份：
#   · Docker 部署形态： ./ops/backup/backup.sh
#   · 本机 dev（无 docker）： mysqldump -uroot -p douluo_game > douluo_game_dev_$(date +%Y%m%d_%H%M%S).sql
#   恢复 = 用备份整库回灌；无备份 = 不恢复。
#
# 【清理对象】users.username REGEXP '^(e2e|r1[3-9]|r2[0-4])'
#   即第 1~24 轮 E2E/冒烟残留账号（e2e% 与 r13%~r24% 前缀）。
#   外键核查结论（2026-09-26，information_schema 实测，见 ops/cleanup/README.md）：
#     · 随 users 行 ON DELETE CASCADE 自动清（9 表）：player_profile /
#       player_talent / guild_member / equipped_ring / equipped_bone /
#       equipped_core / check_in_record / daily_quest_progress / achievement_record
#     · 无外键、必须手动清（2 表）：backpack_item / shop_purchase_record
#     · audit_log：审计日志，默认保留（后端 30 天自动清理任务会收），
#       加 --purge-audit 立即清掉孤儿行
#
# 【宗门孤儿风险】guild.leader_id 没有外键——宗主账号被删会留下孤儿 guild
#   （连带 guild_boss 行，guild_boss FK→guild）。本脚本在删用户之前先处理
#   这些宗门（不在保留列表的）：DELETE guild_boss → DELETE guild_member →
#   DELETE guild。guild_member 即使不显式删，也会随 guild 行 CASCADE。
#
# 【用法】
#   ./ops/cleanup/cleanup-dev-data.sh                          # 默认 DRY-RUN，只打印
#   ./ops/cleanup/cleanup-dev-data.sh --keep-guild-ids 100,102 # 预览：保留回归宗门
#   ./ops/cleanup/cleanup-dev-data.sh --apply                  # 真删（交互终端需输 yes）
#   ./ops/cleanup/cleanup-dev-data.sh --apply --yes --purge-audit  # 非交互全量清理
#
# 【参数】
#   （缺省）              DRY-RUN：只打印将执行的 DELETE 与影响行数估计（SELECT COUNT），不改任何数据
#   --apply               真正执行删除（交互终端要求输入 yes 二次确认）
#   --keep-guild-ids LIST 逗号分隔宗门 ID（如 100,102）：这些宗门连同其宗主、成员用户一并豁免
#   --purge-audit         连带删除 audit_log 中不在 users 里的孤儿审计行（默认保留）
#   --yes                 --apply 时跳过交互确认（cron/非交互环境必须加）
#   -h, --help            显示帮助
#
# 【环境变量】（默认值适配本机 dev MySQL8）
#   DB_HOST=127.0.0.1  DB_PORT=3306  DB_NAME=douluo_game
#   DB_USER=root       DB_PASSWORD=123456
#   USERNAME_PATTERN='^(e2e|r1[3-9]|r2[0-4])'
#
# 【删除顺序】① 待删宗门的 guild_boss / guild_member / guild
#             ② users（9 表随 CASCADE 自动清）
#             ③ 无外键表孤儿清理：backpack_item / shop_purchase_record（/ audit_log）
# ============================================================================
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-douluo_game}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-123456}"
USERNAME_PATTERN="${USERNAME_PATTERN:-^(e2e|r1[3-9]|r2[0-4])}"

APPLY=0
PURGE_AUDIT=0
ASSUME_YES=0
KEEP_GUILD_IDS=""

log() { echo "[cleanup $(date '+%F %T')] $*"; }
die() { echo "[cleanup $(date '+%F %T')] ERROR: $*" >&2; exit 1; }

usage() {
  sed -n '/^# 【用法】/,/^# 【删除顺序】/p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

# ---- 参数解析 ----
while [ $# -gt 0 ]; do
  case "$1" in
    --apply)       APPLY=1 ;;
    --purge-audit) PURGE_AUDIT=1 ;;
    --yes)         ASSUME_YES=1 ;;
    --keep-guild-ids)
      shift
      KEEP_GUILD_IDS="${1:-}"
      [ -n "$KEEP_GUILD_IDS" ] || die "--keep-guild-ids 需要参数：逗号分隔的宗门 ID（如 100,102）"
      ;;
    -h|--help) usage ;;
    *) die "未知参数: $1（--help 查看用法）" ;;
  esac
  shift
done

if [ -n "$KEEP_GUILD_IDS" ]; then
  echo "$KEEP_GUILD_IDS" | grep -Eq '^[0-9]+(,[0-9]+)*$' \
    || die "--keep-guild-ids 格式错误：只允许数字与逗号（如 100,102），收到: $KEEP_GUILD_IDS"
fi

# ---- 前置检查 ----
MYSQL_BIN="$(command -v mysql 2>/dev/null || true)"
[ -n "$MYSQL_BIN" ] || die "mysql 客户端不可用（未安装或不在 PATH）"

# 独立短会话执行 SQL；-N 去表头、-B 用 tab 分隔（密码走 MYSQL_PWD 环境变量，不进 ps）
sql() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
    --default-character-set=utf8mb4 "$DB_NAME" -N -B -e "$1"
}

log "连接 ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME} ..."
if ! sql "SELECT 1;" >/dev/null 2>&1; then
  die "无法连接 MySQL（检查 DB_HOST/DB_PORT/DB_USER/DB_PASSWORD，以及库 ${DB_NAME} 是否存在）"
fi

# SQL 字符串里的单引号翻倍，防止模式注入 SQL 文本
PAT_SQL="${USERNAME_PATTERN//\'/\'\'}"
TGT_PRED="username REGEXP '${PAT_SQL}'"

# ---- 解析保护名单（保留宗门的宗主 + 成员，且本身匹配清理模式）----
PROTECTED=""
if [ -n "$KEEP_GUILD_IDS" ]; then
  PROTECTED="$(sql "SET SESSION group_concat_max_len=1000000;
    SELECT IFNULL(GROUP_CONCAT(DISTINCT t.uid ORDER BY t.uid), '')
    FROM (
      SELECT g.leader_id AS uid FROM guild g WHERE g.id IN (${KEEP_GUILD_IDS})
      UNION
      SELECT gm.user_id  AS uid FROM guild_member gm WHERE gm.guild_id IN (${KEEP_GUILD_IDS})
    ) t
    JOIN users u ON u.id = t.uid
    WHERE u.${TGT_PRED};")"
fi

NOT_PROTECTED_SQL=""
if [ -n "$PROTECTED" ]; then
  NOT_PROTECTED_SQL=" AND id NOT IN (${PROTECTED})"
fi

# ---- 解析待删宗门（宗主为目标用户、且不在保留列表）----
KEEP_GUILD_SQL=""
if [ -n "$KEEP_GUILD_IDS" ]; then
  KEEP_GUILD_SQL=" AND g.id NOT IN (${KEEP_GUILD_IDS})"
fi

DOOMED_GUILDS="$(sql "SET SESSION group_concat_max_len=1000000;
  SELECT IFNULL(GROUP_CONCAT(g.id ORDER BY g.id), '')
  FROM guild g
  WHERE g.leader_id IN (SELECT id FROM users WHERE ${TGT_PRED}${NOT_PROTECTED_SQL})
  ${KEEP_GUILD_SQL};")"

G_IN=""
if [ -n "$DOOMED_GUILDS" ]; then
  G_IN="IN (${DOOMED_GUILDS})"
fi

# ---- 统计 ----
TARGET_CNT="$(sql "SELECT COUNT(*) FROM users WHERE ${TGT_PRED};")"
DOOMED_CNT="$(sql "SELECT COUNT(*) FROM users WHERE ${TGT_PRED}${NOT_PROTECTED_SQL};")"

# 目标用户的行数（级联预估用）
TGT_SUBQ="(SELECT id FROM users WHERE ${TGT_PRED}${NOT_PROTECTED_SQL})"
count_by_target() { sql "SELECT COUNT(*) FROM $1 WHERE user_id IN ${TGT_SUBQ};"; }
# 无外键表的预估谓词：目标用户的行 + 既有孤儿行（干跑时 users 还没删，孤儿谓词
# 查不到"将因删用户而产生"的孤儿，必须把目标用户行合并计入才真实）
MANUAL_PRED="user_id IN ${TGT_SUBQ} OR user_id NOT IN (SELECT id FROM users)"

# ---- 步骤定义：说明 / DELETE SQL / 影响行数估计（SELECT COUNT）----
STEPS_DESC=(); STEPS_DEL=(); STEPS_CNT=()
add_step() { STEPS_DESC+=("$1"); STEPS_DEL+=("$2"); STEPS_CNT+=("$3"); }

if [ -n "$DOOMED_GUILDS" ]; then
  add_step "清理待删宗门的 Boss 周状态（guild_boss，FK→guild ON DELETE CASCADE）" \
    "DELETE FROM guild_boss WHERE guild_id ${G_IN};" \
    "SELECT COUNT(*) FROM guild_boss WHERE guild_id ${G_IN};"
  add_step "清理待删宗门的成员关系（guild_member；即使不显式删也会随 guild 行 CASCADE，显式删以求顺序明确）" \
    "DELETE FROM guild_member WHERE guild_id ${G_IN};" \
    "SELECT COUNT(*) FROM guild_member WHERE guild_id ${G_IN};"
  add_step "删除宗门本体（guild.leader_id 无外键，不先删会留孤儿宗门）" \
    "DELETE FROM guild WHERE id ${G_IN};" \
    "SELECT COUNT(*) FROM guild WHERE id ${G_IN};"
fi

add_step "删除目标用户（下面 9 张子表随 ON DELETE CASCADE 自动清空）" \
  "DELETE FROM users WHERE ${TGT_PRED}${NOT_PROTECTED_SQL};" \
  "SELECT COUNT(*) FROM users WHERE ${TGT_PRED}${NOT_PROTECTED_SQL};"

add_step "清理无外键残留：backpack_item（删 users 后的孤儿行；预估值=目标用户行+既有孤儿行）" \
  "DELETE FROM backpack_item WHERE user_id NOT IN (SELECT id FROM users);" \
  "SELECT (SELECT COUNT(*) FROM backpack_item WHERE user_id IN (SELECT id FROM users WHERE ${TGT_PRED}${NOT_PROTECTED_SQL})) + (SELECT COUNT(*) FROM backpack_item WHERE user_id NOT IN (SELECT id FROM users));"

add_step "清理无外键残留：shop_purchase_record（删 users 后的孤儿行，同上）" \
  "DELETE FROM shop_purchase_record WHERE user_id NOT IN (SELECT id FROM users);" \
  "SELECT (SELECT COUNT(*) FROM shop_purchase_record WHERE user_id IN (SELECT id FROM users WHERE ${TGT_PRED}${NOT_PROTECTED_SQL})) + (SELECT COUNT(*) FROM shop_purchase_record WHERE user_id NOT IN (SELECT id FROM users));"

if [ "$PURGE_AUDIT" = 1 ]; then
  add_step "清理审计日志：audit_log 孤儿行（--purge-audit）" \
    "DELETE FROM audit_log WHERE user_id NOT IN (SELECT id FROM users);" \
    "SELECT COUNT(*) FROM audit_log WHERE user_id NOT IN (SELECT id FROM users);"
fi

# ---- 打印执行计划 ----
MODE="DRY-RUN（只预览，不修改任何数据）"
if [ "$APPLY" = 1 ]; then MODE="APPLY（将真实删除！）"; fi
AUDIT_DESC="保留（后端 30 天自动清理任务会收；加 --purge-audit 立即清）"
if [ "$PURGE_AUDIT" = 1 ]; then AUDIT_DESC="一并删除（--purge-audit）"; fi

echo "============================================================"
echo " 斗罗大陆 dev 数据清理 · ${MODE}"
echo " 目标库    : ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo " 匹配模式  : username REGEXP '${USERNAME_PATTERN}'"
echo " 目标用户  : 匹配 ${TARGET_CNT} 个，待删 ${DOOMED_CNT} 个"
echo " 保留宗门  : ${KEEP_GUILD_IDS:-（无）}（其宗主与成员用户一并豁免）"
if [ -n "$PROTECTED" ]; then
  echo " 豁免用户  : id = ${PROTECTED}"
fi
echo " 待删宗门  : ${DOOMED_GUILDS:-（无）}"
echo " 审计日志  : ${AUDIT_DESC}"
echo "============================================================"

log "待删用户明细（共 ${DOOMED_CNT} 个）:"
if [ "$DOOMED_CNT" -gt 0 ]; then
  sql "SELECT CONCAT('  id=', LPAD(id, 4, ' '), '  ', username) FROM users WHERE ${TGT_PRED}${NOT_PROTECTED_SQL} ORDER BY id;"
fi

if [ -n "$DOOMED_GUILDS" ]; then
  log "待删宗门明细:"
  sql "SELECT CONCAT('  guild id=', g.id, '  name=', g.name,
                    '  宗主=', u.username, '(id=', g.leader_id, ')',
                    '  成员数=', (SELECT COUNT(*) FROM guild_member gm WHERE gm.guild_id = g.id))
       FROM guild g JOIN users u ON u.id = g.leader_id
       WHERE g.leader_id IN (SELECT id FROM users WHERE ${TGT_PRED}${NOT_PROTECTED_SQL})
       ${KEEP_GUILD_SQL}
       ORDER BY g.id;"
fi

echo
echo "--- 将执行以下步骤（顺序即依赖序）---"
for i in "${!STEPS_DESC[@]}"; do
  n="$(sql "${STEPS_CNT[$i]}")"
  printf "\n[步骤 %d] %s\n    SQL: %s\n    预计影响 %s 行\n" \
    "$((i + 1))" "${STEPS_DESC[$i]}" "${STEPS_DEL[$i]}" "$n"
done

# ---- APPLY 二次确认 ----
if [ "$APPLY" = 1 ] && [ "$ASSUME_YES" != 1 ]; then
  if [ -t 0 ]; then
    printf "以上操作将真实删除数据，且不可通过本脚本撤销。输入 yes 继续（其他任意输入取消）: "
    IFS= read -r REPLY
    if [ "$REPLY" != "yes" ]; then
      die "已取消，未做任何修改"
    fi
  else
    die "--apply 在非交互环境执行需要显式 --yes（请先人工核对 DRY-RUN 输出并完成备份）"
  fi
fi

# ---- 执行 ----
if [ "$APPLY" = 1 ]; then
  echo
  for i in "${!STEPS_DESC[@]}"; do
    n="$(sql "${STEPS_DEL[$i]}; SELECT ROW_COUNT();")"
    log "步骤 $((i + 1)) 完成: 已删除 ${n} 行 —— ${STEPS_DESC[$i]}"
  done

  # 级联残留核查：9 张 CASCADE 子表里不该再有任何 user_id 不在 users 中的行
  echo
  log "级联残留核查（应全为 0）:"
  ORPHAN_FOUND=0
  for t in player_profile player_talent guild_member equipped_ring equipped_bone \
           equipped_core check_in_record daily_quest_progress achievement_record; do
    n="$(sql "SELECT COUNT(*) FROM ${t} WHERE user_id NOT IN (SELECT id FROM users);")"
    printf "    %-22s %s\n" "$t" "$n"
    if [ "$n" != "0" ]; then
      ORPHAN_FOUND=1
      echo "    ^^ 警告: ${t} 仍有孤儿行，需人工排查（外键可能曾被关闭写入）"
    fi
  done

  if [ -n "$KEEP_GUILD_IDS" ]; then
    log "提示: 保留宗门(${KEEP_GUILD_IDS})若有成员账号被删，guild.member_count 列不会随级联自动递减，"
    echo "      需等该宗门下次成员变动由后端刷新，或手工核对修正。"
  fi
  if [ "$ORPHAN_FOUND" = 0 ]; then
    log "清理完成，无级联残留。如需回滚请用删除前的备份整库恢复（无备份不可恢复）。"
  fi
else
  echo
  log "DRY-RUN 结束，未修改任何数据。"
  log "下一步: ① 先备份 —— Docker 形态 ./ops/backup/backup.sh；本机 dev 用 mysqldump -uroot -p ${DB_NAME} > 备份文件.sql"
  log "       ② 核对无误后加 --apply 执行（交互终端会要求输入 yes；非交互再加 --yes）"
fi
