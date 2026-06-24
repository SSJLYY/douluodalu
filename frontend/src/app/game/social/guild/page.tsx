'use client';

import { useEffect, useState } from 'react';
import api, { GameState, GuildBossResult, ShopItem } from '@/lib/api';

interface Guild {
    id: number;
    name: string;
    leaderId: number;
    level: number;
    exp: number;
    memberCount: number;
    maxMembers: number;
    notice: string | null;
    createdAt: string;
}

export default function GuildPage() {
    const [gameState, setGameState] = useState<GameState | null>(null);
    const [guilds, setGuilds] = useState<Guild[]>([]);
    const [myGuild, setMyGuild] = useState<Guild | null>(null);
    const [message, setMessage] = useState('');
    const [loading, setLoading] = useState(false);
    const [showCreateForm, setShowCreateForm] = useState(false);
    const [newGuildName, setNewGuildName] = useState('');
    const [donationAmount, setDonationAmount] = useState(500);
    const [guildShopItems, setGuildShopItems] = useState<ShopItem[]>([]);
    const [bossResult, setBossResult] = useState<GuildBossResult | null>(null);

    async function loadGameState() {
        try {
            const state = await api.getGameState();
            setGameState(state);
        } catch (err) {
            console.error('加载游戏状态失败:', err);
        }
    }

    async function loadGuilds() {
        try {
            const guildList = await api.getGuildList();
            setGuilds(guildList);
            try {
                const myGuildData = await api.getMyGuild();
                if (myGuildData && typeof myGuildData === 'object' && 'id' in myGuildData) {
                    setMyGuild(myGuildData as unknown as Guild);
                } else {
                    setMyGuild(null);
                }
            } catch {
                setMyGuild(null);
            }
        } catch (err) {
            console.error('加载宗门列表失败:', err);
        }
    }

    async function loadGuildShop() {
        try {
            setGuildShopItems(await api.getGuildShopItems());
        } catch (err) {
            console.error('加载宗门商店失败:', err);
        }
    }

    useEffect(() => {
        queueMicrotask(() => {
            loadGameState();
            loadGuilds();
            loadGuildShop();
        });
    }, []);

    const handleCreateGuild = async () => {
        if (!newGuildName.trim()) {
            setMessage('请输入宗门名称');
            return;
        }

        setLoading(true);
        try {
            const resp = await api.createGuild(newGuildName);
            if (resp && resp.guild) {
                setMyGuild(resp.guild as unknown as Guild);
            }
            setMessage('宗门创建成功！');
            setShowCreateForm(false);
            setNewGuildName('');
            await loadGuilds();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '创建失败');
        } finally {
            setLoading(false);
        }
    };

    const handleJoinGuild = async (guildId: number) => {
        if (myGuild) {
            setMessage('请先退出当前宗门，再加入其他宗门');
            return;
        }

        setLoading(true);
        try {
            await api.joinGuild(guildId);
            setMessage('加入宗门成功！');
            await loadGuilds();
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '加入失败');
        } finally {
            setLoading(false);
        }
    };

    const handleLeaveGuild = async () => {
        setLoading(true);
        try {
            await api.leaveGuild();
            setMessage('已退出宗门');
            setMyGuild(null);
            await loadGuilds();
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '退出失败');
        } finally {
            setLoading(false);
        }
    };

    const handleDonate = async () => {
        setLoading(true);
        try {
            const result = await api.donateGuild(donationAmount);
            setMessage(result.message);
            await loadGuilds();
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '捐献失败');
        } finally {
            setLoading(false);
        }
    };

    const handleChallengeBoss = async () => {
        setLoading(true);
        try {
            const result = await api.challengeGuildBoss();
            setBossResult(result);
            setMessage(`${result.message}，获得 ${result.goldGained} 金币、${result.bossCoinGained} Boss币和 1 件装备`);
            await loadGuilds();
            await loadGameState();
        } catch (err: unknown) {
            setMessage(err instanceof Error ? err.message : '挑战失败');
        } finally {
            setLoading(false);
        }
    };

    const handleBuyGuildItem = async (itemId: number) => {
        setLoading(true);
        try {
            const result = await api.buyGuildShopItem(itemId);
            setMessage(result.message);
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
            <h1 className="text-2xl font-bold text-yellow-400">宗门</h1>

            {/* 我的宗门 */}
            <div className="bg-gray-800 rounded-lg p-6 border border-gray-600">
                <h2 className="text-lg font-semibold mb-4">我的宗门</h2>
                {myGuild ? (
                    <div>
                        <div className="flex justify-between items-center mb-4">
                            <div>
                                <h3 className="text-xl font-bold">{myGuild.name}</h3>
                                <p className="text-gray-200">等级 {myGuild.level}</p>
                            </div>
                            <div className="text-right">
                                <div className="text-sm text-gray-200">成员</div>
                                <div className="font-semibold">{myGuild.memberCount}/{myGuild.maxMembers}</div>
                            </div>
                        </div>
                        {myGuild.notice && (
                            <div className="bg-gray-700 rounded p-3 text-sm">
                                <span className="text-gray-200">公告：</span>
                                {myGuild.notice}
                            </div>
                        )}
                        <button
                            onClick={handleLeaveGuild}
                            disabled={loading}
                            className="mt-4 px-4 py-2 bg-red-600 hover:bg-red-700 disabled:bg-gray-600 rounded text-sm font-medium"
                        >
                            退出宗门
                        </button>
                    </div>
                ) : (
                    <div className="text-center py-4">
                        <p className="text-gray-200 mb-4">你还没有加入任何宗门</p>
                        <button
                            onClick={() => setShowCreateForm(true)}
                            className="px-6 py-2 bg-yellow-600 hover:bg-yellow-700 rounded"
                        >
                            创建宗门
                        </button>
                    </div>
                )}
            </div>

            {/* 创建宗门表单 */}
            {showCreateForm && (
                <div className="bg-gray-800 rounded-lg p-6 border border-gray-600">
                    <h2 className="text-lg font-semibold mb-4">创建宗门</h2>
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm text-gray-200 mb-1">宗门名称</label>
                            <input
                                type="text"
                                value={newGuildName}
                                onChange={(e) => setNewGuildName(e.target.value)}
                                placeholder="请输入宗门名称"
                                className="w-full px-4 py-2 bg-gray-700 text-white placeholder:text-gray-300 rounded border border-gray-500 focus:border-yellow-400 focus:outline-none"
                                maxLength={20}
                            />
                        </div>
                        <div className="flex gap-2">
                            <button
                                onClick={handleCreateGuild}
                                disabled={loading}
                                className="px-6 py-2 bg-yellow-600 hover:bg-yellow-700 disabled:bg-gray-600 rounded"
                            >
                                {loading ? '创建中...' : '确认创建'}
                            </button>
                            <button
                                onClick={() => setShowCreateForm(false)}
                                className="px-6 py-2 bg-gray-600 hover:bg-gray-500 rounded"
                            >
                                取消
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {myGuild && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                    <div className="bg-gray-800 rounded-lg p-5 border border-gray-600">
                        <h2 className="text-lg font-semibold mb-3 text-yellow-400">宗门捐献</h2>
                        <p className="text-sm text-gray-200 mb-4">消耗金币提升宗门经验，宗门升级后人数上限提高。</p>
                        <div className="flex gap-2">
                            <input
                                type="number"
                                min={100}
                                max={10000}
                                step={100}
                                value={donationAmount}
                                onChange={(event) => setDonationAmount(Number(event.target.value))}
                                className="w-full px-3 py-2 bg-gray-700 border border-gray-500 rounded"
                            />
                            <button
                                onClick={handleDonate}
                                disabled={loading || gameState.profile.gold < donationAmount}
                                className="px-4 py-2 bg-yellow-600 hover:bg-yellow-700 disabled:bg-gray-600 rounded font-medium"
                            >
                                捐献
                            </button>
                        </div>
                    </div>

                    <div className="bg-gray-800 rounded-lg p-5 border border-gray-600">
                        <h2 className="text-lg font-semibold mb-3 text-red-400">宗门 Boss</h2>
                        <p className="text-sm text-gray-200 mb-4">挑战宗门 Boss，可获得金币、Boss币和装备掉落。</p>
                        <button
                            onClick={handleChallengeBoss}
                            disabled={loading}
                            className="w-full px-4 py-2 bg-red-600 hover:bg-red-700 disabled:bg-gray-600 rounded font-medium"
                        >
                            挑战 Boss
                        </button>
                        {bossResult && (
                            <div className="mt-3 text-sm text-gray-100">
                                伤害 {bossResult.damage} / Boss生命 {bossResult.bossHp}
                            </div>
                        )}
                    </div>

                    <div className="bg-gray-800 rounded-lg p-5 border border-gray-600">
                        <h2 className="text-lg font-semibold mb-3 text-green-400">宗门商店</h2>
                        <div className="space-y-3">
                            {guildShopItems.map((item) => (
                                <div key={item.id} className="bg-gray-700 rounded p-3 border border-gray-600">
                                    <div className="font-semibold">{item.name}</div>
                                    <p className="text-sm text-gray-200">{item.description}</p>
                                    <div className="mt-2 flex items-center justify-between text-sm">
                                        <span>{item.price} 金币</span>
                                        <button
                                            onClick={() => handleBuyGuildItem(item.id)}
                                            disabled={loading || gameState.profile.gold < item.price}
                                            className="px-3 py-1 bg-green-600 hover:bg-green-700 disabled:bg-gray-600 rounded"
                                        >
                                            购买
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* 宗门列表 */}
            <div className="bg-gray-800 rounded-lg p-6 border border-gray-600">
                <h2 className="text-lg font-semibold mb-4">宗门列表</h2>
                {guilds.length === 0 ? (
                    <div className="text-center py-4 text-gray-200">
                        暂无宗门，快来创建第一个吧！
                    </div>
                ) : (
                    <div className="space-y-4">
                        {guilds.map((guild) => {
                            const isCurrentGuild = myGuild?.id === guild.id;
                            const cannotJoin = loading || Boolean(myGuild) || guild.memberCount >= guild.maxMembers;
                            const buttonText = isCurrentGuild
                                ? '已加入'
                                : myGuild
                                    ? '先退出当前宗门'
                                    : guild.memberCount >= guild.maxMembers
                                        ? '已满'
                                        : '加入';

                            return (
                            <div key={guild.id} className="bg-gray-700 rounded-lg p-4 border border-gray-600">
                                <div className="flex justify-between items-center">
                                    <div>
                                        <h3 className="font-semibold text-lg">{guild.name}</h3>
                                        <p className="text-gray-200 text-sm">
                                            等级 {guild.level} | 成员 {guild.memberCount}/{guild.maxMembers}
                                        </p>
                                    </div>
                                    <button
                                        onClick={() => handleJoinGuild(guild.id)}
                                        disabled={cannotJoin}
                                        className="px-4 py-2 bg-green-600 hover:bg-green-700 disabled:bg-gray-600 disabled:text-gray-200 rounded text-sm font-medium"
                                    >
                                        {buttonText}
                                    </button>
                                </div>
                                {guild.notice && (
                                    <p className="text-gray-200 text-sm mt-2">{guild.notice}</p>
                                )}
                            </div>
                            );
                        })}
                    </div>
                )}
            </div>

            {/* 消息提示 */}
            {message && (
                <div className="bg-gray-700 rounded-lg p-4 text-center border border-gray-500 text-white">
                    {message}
                </div>
            )}

            {/* 宗门说明 */}
            <div className="bg-gray-800 rounded-lg p-4 border border-gray-600">
                <h3 className="font-semibold mb-2">宗门说明</h3>
                <ul className="text-sm text-gray-100 space-y-1">
                    <li>• 创建宗门需要消耗金币</li>
                    <li>• 宗门成员可以一起参与宗门活动</li>
                    <li>• 宗门等级越高，可容纳成员越多</li>
                    <li>• 宗门捐献可提升宗门等级</li>
                </ul>
            </div>
        </div>
    );
}
