'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import api, { GameState, ShopItem, LimitedShopItem, BackpackItem } from '@/lib/api';

export default function ShopPage() {
    const { user } = useAuth();
    const [gameState, setGameState] = useState<GameState | null>(null);
    const [bossItems, setBossItems] = useState<ShopItem[]>([]);
    const [limitedItems, setLimitedItems] = useState<LimitedShopItem[]>([]);
    const [activeTab, setActiveTab] = useState<'boss' | 'limited'>('boss');
    const [message, setMessage] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        loadGameState();
        loadShopItems();
    }, []);

    const loadGameState = async () => {
        try {
            const state = await api.getGameState();
            setGameState(state);
        } catch (err) {
            console.error('加载游戏状态失败:', err);
        }
    };

    const loadShopItems = async () => {
        try {
            const [boss, limited] = await Promise.all([
                api.getBossShopItems(),
                api.getLimitedShopItems()
            ]);
            setBossItems(boss);
            setLimitedItems(limited);
        } catch (err) {
            console.error('加载商店物品失败:', err);
        }
    };

    const handleBuyBossItem = async (itemId: number) => {
        setLoading(true);
        try {
            await api.buyBossShopItem(itemId);
            setMessage('购买成功！');
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '购买失败');
        } finally {
            setLoading(false);
        }
    };

    const handleBuyLimitedItem = async (itemId: number) => {
        setLoading(true);
        try {
            await api.buyLimitedShopItem(itemId);
            setMessage('购买成功！');
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '购买失败');
        } finally {
            setLoading(false);
        }
    };

    if (!gameState) {
        return <div className="text-center py-8">加载中...</div>;
    }

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">商店</h1>

            {/* 货币显示 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <div className="flex justify-around">
                    <div className="text-center">
                        <div className="text-yellow-500 text-2xl">💰</div>
                        <div className="font-semibold">{gameState.profile.gold.toLocaleString()}</div>
                        <div className="text-sm text-gray-400">金币</div>
                    </div>
                    <div className="text-center">
                        <div className="text-blue-500 text-2xl">💎</div>
                        <div className="font-semibold">{gameState.profile.bossCoin.toLocaleString()}</div>
                        <div className="text-sm text-gray-400">Boss币</div>
                    </div>
                </div>
            </div>

            {/* 标签页切换 */}
            <div className="flex border-b border-gray-700">
                <button
                    className={`py-2 px-4 font-semibold ${
                        activeTab === 'boss' 
                            ? 'text-yellow-400 border-b-2 border-yellow-400' 
                            : 'text-gray-400 hover:text-white'
                    }`}
                    onClick={() => setActiveTab('boss')}
                >
                    Boss商店
                </button>
                <button
                    className={`py-2 px-4 font-semibold ${
                        activeTab === 'limited' 
                            ? 'text-yellow-400 border-b-2 border-yellow-400' 
                            : 'text-gray-400 hover:text-white'
                    }`}
                    onClick={() => setActiveTab('limited')}
                >
                    限时珍品
                </button>
            </div>

            {/* Boss商店 */}
            {activeTab === 'boss' && (
                <div className="space-y-4">
                    <h2 className="text-lg font-semibold">Boss商店</h2>
                    {bossItems.length === 0 ? (
                        <div className="text-gray-500 text-center py-4">暂无物品</div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {bossItems.map((item) => (
                                <div key={item.id} className="bg-gray-800 rounded-lg p-4">
                                    <h3 className="font-semibold text-lg">{item.name}</h3>
                                    <p className="text-gray-400 text-sm mt-1">{item.description}</p>
                                    <div className="mt-4 flex justify-between items-center">
                                        <div className="flex items-center gap-2">
                                            <span className="text-blue-500">💎</span>
                                            <span className="font-semibold">{item.price}</span>
                                        </div>
                                        <button
                                            onClick={() => handleBuyBossItem(item.id)}
                                            disabled={loading || gameState.profile.bossCoin < item.price}
                                            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 rounded text-sm"
                                        >
                                            购买
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* 限时珍品 */}
            {activeTab === 'limited' && (
                <div className="space-y-4">
                    <h2 className="text-lg font-semibold">限时珍品</h2>
                    {limitedItems.length === 0 ? (
                        <div className="text-gray-500 text-center py-4">暂无物品，请稍后再来</div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {limitedItems.map((item) => (
                                <div key={item.id} className="bg-gray-800 rounded-lg p-4">
                                    <h3 className="font-semibold text-lg">{item.name}</h3>
                                    <p className="text-gray-400 text-sm mt-1">{item.description}</p>
                                    <div className="mt-2 text-xs text-gray-500">
                                        刷新时间: {new Date(item.refreshTime).toLocaleString()}
                                    </div>
                                    <div className="mt-4 flex justify-between items-center">
                                        <div className="flex items-center gap-2">
                                            <span className="text-blue-500">💎</span>
                                            <span className="font-semibold">{item.price}</span>
                                        </div>
                                        <button
                                            onClick={() => handleBuyLimitedItem(item.id)}
                                            disabled={loading || gameState.profile.bossCoin < item.price}
                                            className="px-4 py-2 bg-purple-600 hover:bg-purple-700 disabled:bg-gray-600 rounded text-sm"
                                        >
                                            购买
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* 消息提示 */}
            {message && (
                <div className="bg-gray-700 rounded-lg p-4 text-center">
                    {message}
                </div>
            )}

            {/* 商店说明 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">商店说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• Boss商店：使用Boss币购买稀有物品</li>
                    <li>• 限时珍品：定时刷新的稀有装备</li>
                    <li>• Boss币通过击败Boss获得</li>
                    <li>• 购买前请确认有足够的货币</li>
                </ul>
            </div>
        </div>
    );
}
