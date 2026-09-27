'use client';

import { useState } from 'react';
import api, { BackpackItem } from '@/lib/api';
import { useGameData } from '@/lib/hooks';
import { boneEnhanceCost, boneEnhanceMaxed } from '@/lib/equipment';
import { BootState, EmptyPanel } from '@/components/StateViews';

const YEAR_NAMES = ['百年', '千年', '万年', '十万年', '百万年'];
const QUALITY_NAMES = ['劣等', '普通', '优秀', '精良', '完美'];
const BONE_TYPE_NAMES = ['头骨', '左臂骨', '右臂骨', '躯干骨', '左腿骨', '右腿骨'];
// 后端魂核槽位：slotIndex 0=LEFT(左) 1=RIGHT(右)
const CORE_SLOTS = [
    { slotIndex: 0, slotType: 'LEFT', name: '左魂核' },
    { slotIndex: 1, slotType: 'RIGHT', name: '右魂核' },
];

export default function EquipmentPage() {
    const { gameState, message, setMessage, refresh, loadError, runAction } = useGameData();
    const [selectedSlot, setSelectedSlot] = useState<string | null>(null);

    if (!gameState) {
        return (
            <div className="space-y-6">
                <h1 className="text-2xl font-bold text-yellow-400">装备</h1>
                <BootState error={loadError} onRetry={refresh} rows={5} />
            </div>
        );
    }

    function showMessage(text: string) {
        setMessage(text);
        setTimeout(() => setMessage(''), 3000);
    }

    /**
     * 后端按「同类型子列表中的下标」定位背包物品（GameService 按创建时间排序），
     * 因此这里以 /api/game/state 返回的 backpackItems 顺序为准计算索引。
     */
    function indexOfBackpackType(itemType: string, itemId: number): number {
        return gameState!.backpackItems.filter((i) => i.itemType === itemType).findIndex((i) => i.id === itemId);
    }

    async function handleEquip(item: BackpackItem) {
        if (!selectedSlot) {
            showMessage('请先点击上方装备槽位选择目标位置');
            return;
        }
        const [slotType, ...rest] = selectedSlot.split('-');
        const slotValue = Number(rest.join('-'));
        if (slotType === 'ring' && item.itemType !== 'RING') {
            showMessage('该槽位只能装备魂环');
            return;
        }
        if (slotType === 'bone' && item.itemType !== 'BONE') {
            showMessage('该槽位只能装备魂骨');
            return;
        }
        if (slotType === 'core' && item.itemType !== 'CORE') {
            showMessage('该槽位只能装备魂核');
            return;
        }
        try {
            if (slotType === 'ring') {
                await api.equipRing(indexOfBackpackType('RING', item.id), slotValue);
            } else if (slotType === 'bone') {
                await api.equipBone(indexOfBackpackType('BONE', item.id), slotValue);
            } else {
                await api.equipCore(indexOfBackpackType('CORE', item.id), slotValue);
            }
            showMessage('装备成功');
            setSelectedSlot(null);
            await refresh();
        } catch (err: unknown) {
            showMessage(err instanceof Error ? err.message : '装备失败');
        }
    }

    async function handleUnequip(slotKey: string) {
        const [type, ...rest] = slotKey.split('-');
        const slotValue = Number(rest.join('-'));
        try {
            if (type === 'ring') {
                await api.unequipRing(slotValue);
            } else if (type === 'bone') {
                await api.unequipBone(slotValue);
            } else if (type === 'core') {
                await api.unequipCore(slotValue);
            }
            showMessage('已卸下装备，放回背包');
            setSelectedSlot(null);
            await refresh();
        } catch (err: unknown) {
            showMessage(err instanceof Error ? err.message : '卸下失败');
        }
    }

    function handleSlotClick(slotKey: string, isEquipped: boolean) {
        if (isEquipped) {
            handleUnequip(slotKey);
        } else {
            setSelectedSlot(selectedSlot === slotKey ? null : slotKey);
        }
    }

    /** 出售背包物品：itemIndex 为全背包列表（与 /api/game/state 返回顺序一致）下标 */
    async function handleSell(item: BackpackItem) {
        const itemIndex = gameState!.backpackItems.findIndex((i) => i.id === item.id);
        try {
            await api.sellBackpackItem(itemIndex);
            showMessage('出售成功，金币已入账');
            await refresh();
        } catch (err: unknown) {
            showMessage(err instanceof Error ? err.message : '出售失败');
        }
    }

    /**
     * 强化背包魂骨：itemIndex 走 BONE 子列表下标（与 equipBone 的 boneIndex 同口径）。
     * 金币不足仍发请求（按钮不禁用）→ 后端 400 文案（如「金币不足（需要 X）」）经 runAction 落到 message 条；
     * 成功/失败都由 runAction 自动 refresh 拉新 enhanceLevel 与金币数。
     */
    async function handleEnhanceItem(item: BackpackItem) {
        const itemIndex = indexOfBackpackType('BONE', item.id);
        if (itemIndex < 0) return;
        await runAction(
            () => api.enhanceBone({ itemIndex }),
            (r) => setMessage(r.message),
            '强化失败',
        );
    }

    /** 强化已装备魂骨：slotIndex 0-5（与 unequipBone 同口径），同样交 runAction 成功回调 + 自动 refresh */
    async function handleEnhanceSlot(slotIndex: number) {
        await runAction(
            () => api.enhanceBone({ slotIndex }),
            (r) => setMessage(r.message),
            '强化失败',
        );
    }

    async function handleExpand() {
        try {
            await api.expandBackpack();
            showMessage('背包扩容成功');
            await refresh();
        } catch (err: unknown) {
            showMessage(err instanceof Error ? err.message : '扩容失败');
        }
    }

    const getRingInfo = (ring: { yearOrdinal: number; qualityOrdinal: number; percentage: number }) => {
        return `${YEAR_NAMES[ring.yearOrdinal] || '?'} ${QUALITY_NAMES[ring.qualityOrdinal] || '?'} (${ring.percentage}年)`;
    };

    const getBoneInfo = (bone: { yearOrdinal: number; enhanceLevel: number }) => {
        return `${YEAR_NAMES[bone.yearOrdinal] || '?'} +${bone.enhanceLevel}`;
    };

    const getCoreInfo = (core: { coreName: string; value: number; level: number }) => {
        return `${core.coreName} +${core.value} Lv.${core.level}`;
    };

    // ======== 任务#21：魂环负荷 ========
    const ringLoad = gameState.ringLoad;
    const capacity = gameState.capacity;
    const loadRatio = capacity > 0 ? ringLoad / capacity : 0;
    const loadWarn = loadRatio > 0.8;

    /**
     * 背包装配「装上后」的负荷预览：
     * 已选中魂环槽位时按「替换该槽旧环（先卸后装）」折算，否则按纯追加折算。
     * 与后端 GameService.equipRing 的校验口径一致（currentLoad − 旧环负荷 + 新环负荷 ≤ 容量）。
     */
    function ringLoadPreview(item: BackpackItem): { projected: number; fits: boolean } {
        const released =
            selectedSlot && selectedSlot.startsWith('ring-')
                ? gameState!.equippedRings.find((r) => r.slotIndex === Number(selectedSlot.slice(5)))?.load ?? 0
                : 0;
        const projected = ringLoad - released + item.load;
        return { projected, fits: projected <= capacity };
    }

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">装备</h1>

            {message && (
                <div className="bg-yellow-900 border border-yellow-600 text-yellow-200 px-4 py-2 rounded text-center">
                    {message}
                </div>
            )}

            {selectedSlot && (
                <div className="bg-gray-800 border border-yellow-600 rounded-lg p-3 text-center text-yellow-300">
                    已选择槽位，点击下方背包物品即可装备
                    <button onClick={() => setSelectedSlot(null)} className="ml-3 text-sm text-gray-400 underline">取消选择</button>
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="bg-gray-800 rounded-lg p-4">
                    <h2 className="text-lg font-semibold mb-2 text-purple-400">魂环 (9槽位)</h2>
                    {/* 负荷进度条：超 80% 变黄红警示（亮暗主题均走调色板变量） */}
                    <div className="mb-3">
                        <div className="flex justify-between text-xs mb-1">
                            <span className="text-gray-400">负荷</span>
                            <span className={loadWarn ? 'text-red-400 font-semibold' : 'text-gray-300'}>
                                {ringLoad.toLocaleString()}/{capacity.toLocaleString()}（{Math.round(loadRatio * 100)}%）
                            </span>
                        </div>
                        <div className="h-2 bg-gray-700 rounded-full overflow-hidden" role="progressbar"
                             aria-valuemin={0} aria-valuemax={capacity} aria-valuenow={ringLoad}
                             title={loadWarn ? '负荷接近上限：提升等级/装备可增根骨→扩容吸收容量' : undefined}>
                            <div
                                className={`h-full rounded-full transition-all duration-700 ease-out ${
                                    loadWarn ? 'bg-red-500' : loadRatio > 0.5 ? 'bg-yellow-500' : 'bg-purple-500'
                                }`}
                                style={{ width: `${Math.min(100, Math.round(loadRatio * 100))}%` }}
                            />
                        </div>
                    </div>
                    <div className="grid grid-cols-3 gap-3 sm:gap-2">
                        {Array.from({ length: 9 }).map((_, i) => {
                            const ring = gameState.equippedRings.find(r => r.slotIndex === i);
                            const slotKey = `ring-${i}`;
                            return (
                                <button
                                    type="button"
                                    key={i}
                                    aria-label={`魂环槽位 ${i + 1}${ring ? '，点击卸下' : '，空，点击选中'}`}
                                    className={`block w-full p-2 rounded text-center text-sm cursor-pointer min-h-11 ${
                                        ring ? 'bg-purple-900 border border-purple-600' : 'bg-gray-700 dl-slot-empty'
                                    } ${selectedSlot === slotKey ? 'ring-2 ring-yellow-400' : ''}`}
                                    onClick={() => handleSlotClick(slotKey, Boolean(ring))}
                                >
                                    <div className="text-xs text-gray-400">槽位 {i + 1}</div>
                                    {ring ? (
                                        <>
                                            <div className="text-purple-300 break-all leading-snug">{getRingInfo(ring)}</div>
                                            <div className="text-xs text-gray-400 tabular-nums">负荷 {(ring.load ?? 0).toLocaleString()}</div>
                                        </>
                                    ) : (
                                        <div className="text-gray-500">空</div>
                                    )}
                                </button>
                            );
                        })}
                    </div>
                </div>

                <div className="bg-gray-800 rounded-lg p-4">
                    <h2 className="text-lg font-semibold mb-4 text-blue-400">魂骨 (6槽位)</h2>
                    <div className="grid grid-cols-2 gap-3 sm:gap-2">
                        {Array.from({ length: 6 }).map((_, i) => {
                            const bone = gameState.equippedBones.find(b => b.slotIndex === i);
                            const slotKey = `bone-${i}`;
                            const boneMaxed = bone != null && boneEnhanceMaxed(bone.enhanceLevel);
                            return (
                                // 已装备槽内嵌强化小按钮：<button> 里不能再套 <button>（HTML 禁止交互元素嵌套，
                                // SSR 解析会把外层标签提前闭合 → hydration 报错），故骨槽降级为 div[role=button]
                                // 承载「点槽=卸下」语义（含 Enter/Space 键盘等价），强化按钮是真正的 <button>。
                                // 键盘防双触发：外层只在事件源是自身时响应（内层按键 target ≠ currentTarget 直接跳过），
                                // 内层点击 stopPropagation 防止冒泡触发卸下。
                                <div
                                    key={i}
                                    role="button"
                                    tabIndex={0}
                                    aria-label={`魂骨槽位 ${BONE_TYPE_NAMES[i]}${bone ? '，点击卸下' : '，空，点击选中'}`}
                                    className={`block w-full p-2 rounded text-center text-sm cursor-pointer min-h-11 ${
                                        bone ? 'bg-blue-900 border border-blue-600' : 'bg-gray-700 dl-slot-empty'
                                    } ${selectedSlot === slotKey ? 'ring-2 ring-yellow-400' : ''}`}
                                    onClick={() => handleSlotClick(slotKey, Boolean(bone))}
                                    onKeyDown={(e) => {
                                        if (e.target !== e.currentTarget) return;
                                        if (e.key === 'Enter' || e.key === ' ') {
                                            e.preventDefault();
                                            handleSlotClick(slotKey, Boolean(bone));
                                        }
                                    }}
                                >
                                    <div className="text-xs text-gray-400">{BONE_TYPE_NAMES[i]}</div>
                                    {bone ? (
                                        <>
                                            <div className="text-blue-300 break-all leading-snug">{getBoneInfo(bone)}</div>
                                            {/* 槽内强化：EquippedBoneDto 未下发 qualityOrdinal（背包 BONE 有）→
                                                槽内不预览费用（避免报错数），实际花费以后端成功/400 文案为准 */}
                                            <button
                                                type="button"
                                                data-testid={`enhance-btn-slot-${i}`}
                                                aria-label={`强化${BONE_TYPE_NAMES[i]}魂骨，当前 +${bone.enhanceLevel}${boneMaxed ? '，已达上限' : ''}`}
                                                title={boneMaxed ? '已达强化上限（+15）' : `强化到 +${bone.enhanceLevel + 1}`}
                                                disabled={boneMaxed}
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    if (!boneMaxed) void handleEnhanceSlot(i);
                                                }}
                                                className="mt-1 w-full px-1.5 py-0.5 text-[11px] bg-yellow-900/60 hover:bg-yellow-800 disabled:hover:bg-yellow-900/60 disabled:opacity-40 border border-yellow-700 rounded"
                                            >
                                                {boneMaxed ? '已满级' : '🔨 强化'}
                                            </button>
                                        </>
                                    ) : (
                                        <div className="text-gray-500">空</div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>

                <div className="bg-gray-800 rounded-lg p-4">
                    <h2 className="text-lg font-semibold mb-4 text-green-400">魂核 (2槽位)</h2>
                    <div className="space-y-3">
                        {CORE_SLOTS.map((slot) => {
                            const core = gameState.equippedCores.find(c => c.slotType === slot.slotType);
                            const slotKey = `core-${slot.slotIndex}`;
                            return (
                                <button
                                    type="button"
                                    key={slot.slotType}
                                    aria-label={`${slot.name}${core ? '，点击卸下' : '，空，点击选中'}`}
                                    className={`block w-full p-3 rounded text-left cursor-pointer min-h-11 ${
                                        core ? 'bg-green-900 border border-green-600' : 'bg-gray-700 dl-slot-empty'
                                    } ${selectedSlot === slotKey ? 'ring-2 ring-yellow-400' : ''}`}
                                    onClick={() => handleSlotClick(slotKey, Boolean(core))}
                                >
                                    <div className="text-xs text-gray-400">{slot.name}</div>
                                    {core ? (
                                        <div className="text-green-300 break-all leading-snug">{getCoreInfo(core)}</div>
                                    ) : (
                                        <div className="text-gray-500">空</div>
                                    )}
                                </button>
                            );
                        })}
                    </div>
                </div>
            </div>

            <div className="bg-gray-800 rounded-lg p-4">
                <div className="flex items-center justify-between mb-4">
                    <h2 className="text-lg font-semibold">背包 ({gameState.backpackItems.length}件)</h2>
                    <button
                        onClick={handleExpand}
                        className="px-3 py-2 max-sm:min-h-11 text-sm bg-gray-700 hover:bg-gray-600 border border-gray-600 rounded transition shrink-0"
                    >
                        扩容背包（消耗金币）
                    </button>
                </div>
                {gameState.backpackItems.length === 0 ? (
                    <EmptyPanel message="背包为空，去战斗掉落装备吧" testId="backpack-empty" />
                ) : (
                    <div className="grid grid-cols-2 lg:grid-cols-4 xl:grid-cols-6 gap-3 sm:gap-2">
                        {gameState.backpackItems.map((item) => {
                            const isRing = item.itemType === 'RING';
                            // hover(title)/选中时可见的负荷预览：装上后 X/Y（装得下/装不下）
                            const preview = isRing ? ringLoadPreview(item) : null;
                            const cannotFit = preview != null && !preview.fits;
                            // 魂骨强化（仅 BONE 卡显示第三钮）：费用实时按 lib/equipment.ts 同源公式预览，
                            // 已满级禁用；金币不足不禁用（后端 400 文案经 runAction 落 message 条）
                            const isBone = item.itemType === 'BONE';
                            const boneItemIndex = isBone ? indexOfBackpackType('BONE', item.id) : -1;
                            const boneMaxed = isBone && boneEnhanceMaxed(item.enhanceLevel);
                            const boneCost = isBone
                                ? boneEnhanceCost(item.yearOrdinal, item.qualityOrdinal, item.enhanceLevel)
                                : 0;
                            return (
                                <div
                                    key={item.id}
                                    onClick={() => handleEquip(item)}
                                    title={preview
                                        ? `装上后负荷 +${item.load.toLocaleString()} → ${preview.projected.toLocaleString()}/${capacity.toLocaleString()}（${preview.fits ? '装得下' : '装不下'}）`
                                        : undefined}
                                    className={`p-2 rounded text-sm cursor-pointer hover:brightness-110 ${
                                        item.itemType === 'RING' ? 'bg-purple-900' :
                                        item.itemType === 'BONE' ? 'bg-blue-900' : 'bg-green-900'
                                    } ${item.locked ? 'opacity-50' : ''}`}
                                >
                                    <div className="text-xs text-gray-400">
                                        {item.itemType === 'RING' ? '魂环' :
                                         item.itemType === 'BONE' ? '魂骨' : '魂核'}
                                    </div>
                                    <div className="font-semibold">
                                        {item.itemType === 'RING' && `${YEAR_NAMES[item.yearOrdinal]} ${QUALITY_NAMES[item.qualityOrdinal]}`}
                                        {item.itemType === 'BONE' && `${YEAR_NAMES[item.yearOrdinal]} ${BONE_TYPE_NAMES[item.boneTypeOrdinal || 0]}`}
                                        {item.itemType === 'CORE' && item.coreName}
                                    </div>
                                    {preview && (
                                        <div className={`text-xs ${cannotFit ? 'text-red-400 font-semibold' : 'text-gray-300'}`}>
                                            负荷 +{item.load.toLocaleString()} → {preview.projected.toLocaleString()}/{capacity.toLocaleString()}
                                            <span className="ml-1">{cannotFit ? '装不下' : '装得下'}</span>
                                        </div>
                                    )}
                                    {/* flex-wrap：375px 下三钮放不下时「强化（X金币）」换行占整行，不溢出 */}
                                    <div className="mt-1 flex flex-wrap gap-1">
                                        <button
                                            onClick={(e) => { e.stopPropagation(); void handleEquip(item); }}
                                            aria-disabled={cannotFit || item.locked}
                                            className={`flex-1 px-2 py-1.5 max-sm:min-h-11 text-xs rounded border transition ${
                                                cannotFit
                                                    ? 'bg-gray-700 border-gray-600 text-gray-500 opacity-50 cursor-not-allowed'
                                                    : 'bg-purple-600 hover:bg-purple-500 border-purple-500'
                                            }`}
                                        >
                                            装备
                                        </button>
                                        <button
                                            onClick={(e) => { e.stopPropagation(); if (!item.locked) handleSell(item); }}
                                            disabled={item.locked}
                                            className="flex-1 px-2 py-1.5 max-sm:min-h-11 text-xs bg-red-900/60 hover:bg-red-800 disabled:opacity-40 border border-red-700 rounded"
                                        >
                                            出售
                                        </button>
                                        {isBone && (
                                            <button
                                                type="button"
                                                data-testid={`enhance-btn-item-${boneItemIndex}`}
                                                disabled={boneMaxed}
                                                aria-label={`强化魂骨，当前 +${item.enhanceLevel}，${boneMaxed ? '已达上限' : `花费 ${boneCost.toLocaleString()} 金币`}`}
                                                title={boneMaxed ? '已达强化上限（+15）' : `强化到 +${item.enhanceLevel + 1}，花费 ${boneCost.toLocaleString()} 金币`}
                                                onClick={(e) => { e.stopPropagation(); if (!boneMaxed) void handleEnhanceItem(item); }}
                                                className="flex-1 px-2 py-1.5 max-sm:min-h-11 text-xs bg-yellow-900/60 hover:bg-yellow-800 disabled:hover:bg-yellow-900/60 disabled:opacity-40 border border-yellow-700 rounded"
                                            >
                                                {boneMaxed ? '已满级' : `强化（${boneCost.toLocaleString()}金币）`}
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>

            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">装备说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>点击空槽位选中它，再点击背包物品即可装备</li>
                    <li>点击已装备的槽位可卸下装备放回背包</li>
                    <li>魂环：通过战斗掉落，提升攻击属性</li>
                    <li>魂环负荷 = 等效年份（品质越高、成熟度越高负荷越大），总负荷不能超过吸收容量（=根骨×6）</li>
                    <li>吸收容量不足时无法装环：先提升等级/换装增根骨，再吸收更高年份魂环</li>
                    <li>魂骨：稀有掉落，可强化升级（不占负荷）</li>
                    <li>魂核：特殊装备，提供被动技能（不占负荷）</li>
                </ul>
            </div>
        </div>
    );
}
