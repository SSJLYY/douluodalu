'use client';

import { useState } from 'react';
import api, { BackpackItem } from '@/lib/api';
import { useGameData } from '@/lib/hooks';

const YEAR_NAMES = ['百年', '千年', '万年', '十万年', '百万年'];
const QUALITY_NAMES = ['劣等', '普通', '优秀', '精良', '完美'];
const BONE_TYPE_NAMES = ['头骨', '左臂骨', '右臂骨', '躯干骨', '左腿骨', '右腿骨'];
// 后端魂核槽位：slotIndex 0=LEFT(左) 1=RIGHT(右)
const CORE_SLOTS = [
    { slotIndex: 0, slotType: 'LEFT', name: '左魂核' },
    { slotIndex: 1, slotType: 'RIGHT', name: '右魂核' },
];

export default function EquipmentPage() {
    const { gameState, message, setMessage, refresh } = useGameData();
    const [selectedSlot, setSelectedSlot] = useState<string | null>(null);

    if (!gameState) {
        return <div className="text-center py-8">加载中...</div>;
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
                    <h2 className="text-lg font-semibold mb-4 text-purple-400">魂环 (9槽位)</h2>
                    <div className="grid grid-cols-3 gap-2">
                        {Array.from({ length: 9 }).map((_, i) => {
                            const ring = gameState.equippedRings.find(r => r.slotIndex === i);
                            const slotKey = `ring-${i}`;
                            return (
                                <div
                                    key={i}
                                    className={`p-2 rounded text-center text-sm cursor-pointer ${
                                        ring ? 'bg-purple-900 border border-purple-600' : 'bg-gray-700 dl-slot-empty'
                                    } ${selectedSlot === slotKey ? 'ring-2 ring-yellow-400' : ''}`}
                                    onClick={() => handleSlotClick(slotKey, Boolean(ring))}
                                >
                                    <div className="text-xs text-gray-400">槽位 {i + 1}</div>
                                    {ring ? (
                                        <div className="text-purple-300">{getRingInfo(ring)}</div>
                                    ) : (
                                        <div className="text-gray-500">空</div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>

                <div className="bg-gray-800 rounded-lg p-4">
                    <h2 className="text-lg font-semibold mb-4 text-blue-400">魂骨 (6槽位)</h2>
                    <div className="grid grid-cols-2 gap-2">
                        {Array.from({ length: 6 }).map((_, i) => {
                            const bone = gameState.equippedBones.find(b => b.slotIndex === i);
                            const slotKey = `bone-${i}`;
                            return (
                                <div
                                    key={i}
                                    className={`p-2 rounded text-center text-sm cursor-pointer ${
                                        bone ? 'bg-blue-900 border border-blue-600' : 'bg-gray-700 dl-slot-empty'
                                    } ${selectedSlot === slotKey ? 'ring-2 ring-yellow-400' : ''}`}
                                    onClick={() => handleSlotClick(slotKey, Boolean(bone))}
                                >
                                    <div className="text-xs text-gray-400">{BONE_TYPE_NAMES[i]}</div>
                                    {bone ? (
                                        <div className="text-blue-300">{getBoneInfo(bone)}</div>
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
                    <div className="space-y-2">
                        {CORE_SLOTS.map((slot) => {
                            const core = gameState.equippedCores.find(c => c.slotType === slot.slotType);
                            const slotKey = `core-${slot.slotIndex}`;
                            return (
                                <div
                                    key={slot.slotType}
                                    className={`p-3 rounded cursor-pointer ${
                                        core ? 'bg-green-900 border border-green-600' : 'bg-gray-700 dl-slot-empty'
                                    } ${selectedSlot === slotKey ? 'ring-2 ring-yellow-400' : ''}`}
                                    onClick={() => handleSlotClick(slotKey, Boolean(core))}
                                >
                                    <div className="text-xs text-gray-400">{slot.name}</div>
                                    {core ? (
                                        <div className="text-green-300">{getCoreInfo(core)}</div>
                                    ) : (
                                        <div className="text-gray-500">空</div>
                                    )}
                                </div>
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
                        className="px-3 py-1 text-sm bg-gray-700 hover:bg-gray-600 border border-gray-600 rounded transition"
                    >
                        扩容背包（消耗金币）
                    </button>
                </div>
                {gameState.backpackItems.length === 0 ? (
                    <div className="text-gray-500 text-center py-4">背包为空</div>
                ) : (
                    <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-2">
                        {gameState.backpackItems.map((item) => (
                            <div
                                key={item.id}
                                onClick={() => handleEquip(item)}
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
                                <button
                                    onClick={(e) => { e.stopPropagation(); if (!item.locked) handleSell(item); }}
                                    disabled={item.locked}
                                    className="mt-1 w-full px-2 py-0.5 text-xs bg-red-900/60 hover:bg-red-800 disabled:opacity-40 border border-red-700 rounded"
                                >
                                    出售
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">装备说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>点击空槽位选中它，再点击背包物品即可装备</li>
                    <li>点击已装备的槽位可卸下装备放回背包</li>
                    <li>魂环：通过战斗掉落，提升攻击属性</li>
                    <li>魂骨：稀有掉落，可强化升级</li>
                    <li>魂核：特殊装备，提供被动技能</li>
                </ul>
            </div>
        </div>
    );
}
