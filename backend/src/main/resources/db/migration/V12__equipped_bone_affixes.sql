-- ============================================================
-- V12: equipped_bone 增加魂骨词缀字段（affixes_json）
--
-- 背景：魂骨词缀（第二十八轮）在掉落侧 roll——GameService.rollBackpackDrop 的
-- BONE 分支在全部既有掷点之后追加（5 类型不重复抽取、条数 = qualityOrdinal+1、
-- 数值按品质 0→4 线性插值，表定义见 GameBalance「魂骨词缀」区块），以 JSON 串
-- 持久化：[{"type":"CRIT_RATE","value":5},...]。
--
-- 装备/卸装是「属性拷贝」模式（equip 删背包行建 equipped 属性拷贝行、unequip 按
-- 属性拷贝重建背包行、bone_id 只是留念）——词缀 JSON 必须随件搬运：equipBone 把
-- 背包行词缀拷入 equipped 行（被换下的旧骨词缀随件回背包）、unequipBone 重建背包
-- 行时带回、prestige 转生卸装同款拷贝（GameService 三处注释留档），否则换装即丢词缀。
--
-- DEFAULT NULL：历史骨行无词缀（消费侧 EquipmentPowerService.boneAffixBonus 按
-- 空词缀宽容处理，与 V12 前行为逐位一致）。
-- backpack_item.affixes_json 列 V1 已存在（此前恒 null，本版本起 BONE 掉落写入）。
-- ============================================================

ALTER TABLE equipped_bone
    ADD COLUMN affixes_json JSON DEFAULT NULL COMMENT '魂骨词缀';
