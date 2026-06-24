'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import api, { GameState, EquippedRing, EquippedBone, EquippedCore, BackpackItem } from '@/lib/api';

const YEAR_NAMES = ['百年', '千年', '万年', '十万年', '百万年'];
const QUALITY_NAMES = ['劣等', '普通', '优秀', '精良', '完美'];
const BONE_TYPE_NAMES = ['头骨', '左臂骨', '右臂骨', '躯干骨', '左腿骨', '右腿骨'];

export default function EquipmentPage() {
    const { user } = useAuth();
    const [gameState, setGameState] = useState<GameState | null>(null);
    const [selectedSlot, setSelectedSlot] = useState<string | null>(null);
    const [message, setMessage] = useState('');

    useEffect(() => {
        loadGameState();
    }, []);

    const loadGameState = async () => {
        try {
            const state = await api.getGameState();
            setGameState(state);
        } catch (err) {
            console.error('加载游戏状态失败:', err);
        }
    };

    if (!gameState) {
        return <div className="text-center py-8">加载中...</div>;
    }

    const getRingInfo = (ring: EquippedRing) => {
        return `${YEAR_NAMES[ring.yearOrdinal] || '?'} ${QUALITY_NAMES[ring.qualityOrdinal] || '?'} (${ring.percentage}年)`;
    };

    const getBoneInfo = (bone: EquippedBone) => {
        return `${YEAR_NAMES[bone.yearOrdinal] || '?'} +${bone.enhanceLevel}`;
    };

    const getCoreInfo = (core: EquippedCore) => {
        return `${core.coreName} Lv.${core.level}`;
    };

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">装备</h1>

            {/* 装备槽位 */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* 魂环槽位 */}
                <div className="bg-gray-800 rounded-lg p-4">
                    <h2 className="text-lg font-semibold mb-4 text-purple-400">魂环 (9槽位)</h2>
                    <div className="grid grid-cols-3 gap-2">
                        {Array.from({ length: 9 }).map((_, i) => {
                            const ring = gameState.equippedRings.find(r => r.slotIndex === i);
                            return (
                                <div 
                                    key={i}
                                    className={`p-2 rounded text-center text-sm cursor-pointer ${
                                        ring ? 'bg-purple-900 border border-purple-600' : 'bg-gray-700'
                                    } ${selectedSlot === `ring-${i}` ? 'ring-2 ring-yellow-400' : ''}`}
                                    onClick={() => setSelectedSlot(`ring-${i}`)}
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

                {/* 魂骨槽位 */}
                <div className="bg-gray-800 rounded-lg p-4">
                    <h2 className="text-lg font-semibold mb-4 text-blue-400">魂骨 (6槽位)</h2>
                    <div className="grid grid-cols-2 gap-2">
                        {Array.from({ length: 6 }).map((_, i) => {
                            const bone = gameState.equippedBones.find(b => b.slotIndex === i);
                            return (
                                <div 
                                    key={i}
                                    className={`p-2 rounded text-center text-sm cursor-pointer ${
                                        bone ? 'bg-blue-900 border border-blue-600' : 'bg-gray-700'
                                    } ${selectedSlot === `bone-${i}` ? 'ring-2 ring-yellow-400' : ''}`}
                                    onClick={() => setSelectedSlot(`bone-${i}`)}
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

                {/* 魂核槽位 */}
                <div className="bg-gray-800 rounded-lg p-4">
                    <h2 className="text-lg font-semibold mb-4 text-green-400">魂核 (3槽位)</h2>
                    <div className="space-y-2">
                        {['ATTACK', 'DEFENSE', 'UTILITY'].map((slotType) => {
                            const core = gameState.equippedCores.find(c => c.slotType === slotType);
                            const slotName = slotType === 'ATTACK' ? '攻击' : slotType === 'DEFENSE' ? '防御' : '辅助';
                            return (
                                <div 
                                    key={slotType}
                                    className={`p-3 rounded cursor-pointer ${
                                        core ? 'bg-green-900 border border-green-600' : 'bg-gray-700'
                                    } ${selectedSlot === `core-${slotType}` ? 'ring-2 ring-yellow-400' : ''}`}
                                    onClick={() => setSelectedSlot(`core-${slotType}`)}
                                >
                                    <div className="text-xs text-gray-400">{slotName}魂核</div>
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

            {/* 背包 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h2 className="text-lg font-semibold mb-4">背包 ({gameState.backpackItems.length}件)</h2>
                {gameState.backpackItems.length === 0 ? (
                    <div className="text-gray-500 text-center py-4">背包为空</div>
                ) : (
                    <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-2">
                        {gameState.backpackItems.map((item) => (
                            <div 
                                key={item.id}
                                className={`p-2 rounded text-sm cursor-pointer ${
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
                                <div className="text-xs text-gray-400">
                                    {item.locked ? '🔒' : ''}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* 消息提示 */}
            {message && (
                <div className="bg-gray-700 rounded-lg p-4 text-center">
                    {message}
                </div>
            )}

            {/* 装备说明 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">装备说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• 魂环：通过战斗掉落，可提升属性</li>
                    <li>• 魂骨：稀有掉落，可强化升级</li>
                    <li>• 魂核：特殊装备，提供被动技能</li>
                    <li>• 点击装备槽位查看详情</li>
                </ul>
            </div>
        </div>
    );
}