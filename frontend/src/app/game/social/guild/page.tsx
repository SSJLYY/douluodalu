'use client';

import { useCallback, useEffect, useState } from 'react';
import api, { GuildBossResult, GuildSummary, ShopItem } from '@/lib/api';
import { useGameData } from '@/lib/hooks';
import { BootState, ErrorPanel, EmptyPanel } from '@/components/StateViews';

export default function GuildPage() {
    const { gameState, message, setMessage, actionLoading: loading, loadError, refresh, runAction } = useGameData();
    const [guilds, setGuilds] = useState<GuildSummary[]>([]);
    const [myGuild, setMyGuild] = useState<GuildSummary | null>(null);
    const [showCreateForm, setShowCreateForm] = useState(false);
    const [newGuildName, setNewGuildName] = useState('');
    const [donationAmount, setDonationAmount] = useState(500);
    const [guildShopItems, setGuildShopItems] = useState<ShopItem[]>([]);
    const [bossResult, setBossResult] = useState<GuildBossResult | null>(null);
    // 任务#27：宗门列表/商店各自三态（此前失败只 console.error，页面永远空白或假空态）
    const [listLoading, setListLoading] = useState(true);
    const [listError, setListError] = useState('');
    const [shopLoading, setShopLoading] = useState(true);
    const [shopError, setShopError] = useState('');

    const loadGuilds = useCallback(async () => {
        setListLoading(true);
        setListError('');
        try {
            setGuilds(await api.getGuildList());
        } catch (err) {
            setListError(err instanceof Error && err.message ? err.message : '加载宗门列表失败');
        }
        try {
            // 后端 /guild/my 返回 {joined, guild}，未加入时 guild 为 null
            const res = await api.getMyGuild();
            setMyGuild(res.joined ? res.guild : null);
        } catch (err) {
            setListError(err instanceof Error && err.message ? err.message : '加载我的宗门失败');
            setMyGuild(null);
        }
        setListLoading(false);
    }, []);

    const loadGuildShop = useCallback(async () => {
        setShopLoading(true);
        setShopError('');
        try {
            setGuildShopItems(await api.getGuildShopItems());
        } catch (err) {
            setShopError(err instanceof Error && err.message ? err.message : '加载宗门商店失败');
        } finally {
            setShopLoading(false);
        }
    }, []);

    useEffect(() => {
        queueMicrotask(() => {
            loadGuilds();
            loadGuildShop();
        });
    }, [loadGuilds, loadGuildShop]);

    const handleCreateGuild = () => runAction(
        () => api.createGuild(newGuildName),
        (resp) => {
            if (resp.guild) setMyGuild(resp.guild);
            setMessage('宗门创建成功！');
            setShowCreateForm(false);
            setNewGuildName('');
        },
        '创建失败',
    ).then(loadGuilds);

    const handleJoinGuild = (guildId: number) => {
        if (myGuild) {
            setMessage('请先退出当前宗门，再加入其他宗门');
            return Promise.resolve();
        }
        return runAction(
            () => api.joinGuild(guildId),
            () => setMessage('加入宗门成功！'),
            '加入失败',
        ).then(loadGuilds);
    };

    const handleLeaveGuild = () => runAction(
        () => api.leaveGuild(),
        () => {
            setMessage('已退出宗门');
            setMyGuild(null);
        },
        '退出失败',
    ).then(loadGuilds);

    const handleDonate = () => runAction(
        () => api.donateGuild(donationAmount),
        (result) => setMessage(result.message),
        '捐献失败',
    ).then(loadGuilds);

    const handleChallengeBoss = () => runAction(
        () => api.challengeGuildBoss(),
        (result) => {
            setBossResult(result);
            setMessage(`${result.message}，获得 ${result.goldGained} 金币、${result.bossCoinGained} Boss币和 1 件装备`);
        },
        '挑战失败',
    ).then(loadGuilds);

    const handleBuyGuildItem = (itemId: number) => runAction(
        () => api.buyGuildShopItem(itemId),
        (result) => setMessage(result.message),
        '购买失败',
    );

    if (!gameState) {
        return (
            <div className="space-y-6">
                <h1 className="text-2xl font-bold text-yellow-400">宗门</h1>
                <BootState error={loadError} onRetry={refresh} rows={5} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">宗门</h1>

            {/* 我的宗门 */}
            <div className="bg-gray-800 rounded-lg p-6 border border-gray-600">
                <h2 className="text-lg font-semibold mb-4">我的宗门</h2>
                {myGuild ? (
                    <div>
                        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-4">
                            <div className="min-w-0">
                                <h3 className="text-xl font-bold break-words">{myGuild.name}</h3>
                                <p className="text-gray-200">等级 {myGuild.level}</p>
                            </div>
                            <div className="text-right shrink-0">
                                <div className="text-sm text-gray-200">成员</div>
                                <div className="font-semibold tabular-nums">{myGuild.memberCount}/{myGuild.maxMembers}</div>
                            </div>
                        </div>
                        {myGuild.notice && (
                            <div className="bg-gray-700 rounded p-3 text-sm break-words">
                                <span className="text-gray-200">公告：</span>
                                {myGuild.notice}
                            </div>
                        )}
                        <button
                            onClick={handleLeaveGuild}
                            disabled={loading}
                            className="mt-4 px-4 min-h-11 bg-red-600 hover:bg-red-700 disabled:bg-gray-600 rounded text-sm font-medium"
                        >
                            退出宗门
                        </button>
                    </div>
                ) : listLoading ? (
                    <div aria-hidden data-testid="guild-my-skeleton" className="space-y-3">
                        <div className="dl-skeleton w-36 h-6" />
                        <div className="dl-skeleton w-24 h-4" />
                        <div className="dl-skeleton w-28 h-9 rounded-lg" />
                    </div>
                ) : (
                    <div className="text-center py-4">
                        <p className="text-gray-200 mb-4">你还没有加入任何宗门</p>
                        <button
                            onClick={() => setShowCreateForm(true)}
                            className="px-6 min-h-11 bg-yellow-600 hover:bg-yellow-700 rounded"
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
                                className="w-full px-4 py-2 bg-gray-700 placeholder:text-gray-300 rounded border border-gray-500 focus:border-yellow-400 focus:outline-none"
                                maxLength={20}
                            />
                        </div>
                        <div className="flex flex-wrap gap-2">
                            <button
                                onClick={handleCreateGuild}
                                disabled={loading}
                                className="px-6 min-h-11 bg-yellow-600 hover:bg-yellow-700 disabled:bg-gray-600 rounded"
                            >
                                {loading ? '创建中...' : '确认创建'}
                            </button>
                            <button
                                onClick={() => setShowCreateForm(false)}
                                className="px-6 min-h-11 bg-gray-600 hover:bg-gray-500 rounded"
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
                                className="w-full min-w-0 px-3 min-h-11 bg-gray-700 border border-gray-500 rounded"
                            />
                            <button
                                onClick={handleDonate}
                                disabled={loading || gameState.profile.gold < donationAmount}
                                className="px-4 min-h-11 bg-yellow-600 hover:bg-yellow-700 disabled:bg-gray-600 rounded font-medium shrink-0"
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
                            className="w-full px-4 min-h-11 bg-red-600 hover:bg-red-700 disabled:bg-gray-600 rounded font-medium"
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
                        {shopLoading && guildShopItems.length === 0 ? (
                            <div aria-hidden data-testid="guild-shop-skeleton" className="space-y-3">
                                {Array.from({ length: 2 }, (_, i) => (
                                    <div key={i} className="bg-gray-700 rounded p-3 border border-gray-600">
                                        <div className="dl-skeleton w-24 h-4" />
                                        <div className="dl-skeleton w-32 h-3 mt-2" />
                                        <div className="flex justify-between items-center mt-3">
                                            <div className="dl-skeleton w-14 h-4" />
                                            <div className="dl-skeleton w-12 h-8 rounded" />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : shopError && guildShopItems.length === 0 ? (
                            <ErrorPanel message={shopError} onRetry={loadGuildShop} testId="guild-shop-error" />
                        ) : guildShopItems.length === 0 ? (
                            <EmptyPanel message="暂无宗门商品" testId="guild-shop-empty" />
                        ) : (
                            <div className="space-y-3">
                                {shopError && (
                                    <div role="alert" className="dl-shake px-3 py-2 bg-red-500/20 border border-red-500/50 rounded text-red-300 text-sm">
                                        {shopError}，正在显示上次数据
                                    </div>
                                )}
                                {guildShopItems.map((item) => (
                                    <div key={`${item.id}-${item.name}`} className="bg-gray-700 rounded p-3 border border-gray-600">
                                        <div className="font-semibold break-words">{item.name}</div>
                                        <p className="text-sm text-gray-200">{item.description}</p>
                                        <div className="mt-2 flex items-center justify-between gap-2 text-sm">
                                            <span className="tabular-nums">{item.price} 金币</span>
                                            <button
                                                onClick={() => handleBuyGuildItem(item.id)}
                                                disabled={loading || gameState.profile.gold < item.price}
                                                className="px-3 min-h-11 bg-green-600 hover:bg-green-700 disabled:bg-gray-600 rounded shrink-0"
                                            >
                                                购买
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* 宗门列表 */}
            <div className="bg-gray-800 rounded-lg p-6 border border-gray-600">
                <div className="flex items-center justify-between gap-3 mb-4">
                    <h2 className="text-lg font-semibold">宗门列表</h2>
                    {listError && guilds.length > 0 && (
                        <button type="button" onClick={loadGuilds} className="px-3 min-h-11 text-sm border border-line bg-surface-soft/80 hover:bg-surface-soft rounded-lg">
                            重试刷新
                        </button>
                    )}
                </div>
                {listLoading && guilds.length === 0 ? (
                    <div data-testid="guild-list-skeleton" aria-hidden className="space-y-3">
                        {Array.from({ length: 3 }, (_, i) => (
                            <div key={i} className="bg-gray-700 rounded-lg p-4 border border-gray-600">
                                <div className="dl-skeleton w-28 h-5" />
                                <div className="dl-skeleton w-40 h-4 mt-2" />
                            </div>
                        ))}
                    </div>
                ) : listError && guilds.length === 0 ? (
                    <ErrorPanel message={listError} onRetry={loadGuilds} testId="guild-list-error" />
                ) : guilds.length === 0 ? (
                    <EmptyPanel message="暂无宗门，快来创建第一个吧！" testId="guild-list-empty" />
                ) : (
                    <div className="space-y-4">
                        {listError && (
                            <div role="alert" className="dl-shake px-4 py-2 bg-red-500/20 border border-red-500/50 rounded-lg text-red-300 text-sm">
                                {listError}，正在显示上次数据
                            </div>
                        )}
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
                                <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
                                    <div className="min-w-0">
                                        <h3 className="font-semibold text-lg break-words">{guild.name}</h3>
                                        <p className="text-gray-200 text-sm tabular-nums">
                                            等级 {guild.level} | 成员 {guild.memberCount}/{guild.maxMembers}
                                        </p>
                                    </div>
                                    <button
                                        onClick={() => handleJoinGuild(guild.id)}
                                        disabled={cannotJoin}
                                        className="px-4 min-h-11 bg-green-600 hover:bg-green-700 disabled:bg-gray-600 disabled:text-gray-200 rounded text-sm font-medium shrink-0"
                                    >
                                        {buttonText}
                                    </button>
                                </div>
                                {guild.notice && (
                                    <p className="text-gray-200 text-sm mt-2 break-words">{guild.notice}</p>
                                )}
                            </div>
                            );
                        })}
                    </div>
                )}
            </div>

            {/* 消息提示 */}
            {message && (
                <div className="bg-gray-700 rounded-lg p-4 text-center border border-gray-500">
                    {message}
                </div>
            )}

            {/* 宗门说明 */}
            <div className="bg-gray-800 rounded-lg p-4 border border-gray-600">
                <h3 className="font-semibold mb-2">宗门说明</h3>
                <ul className="text-sm text-gray-100 space-y-1">
                    <li>• 创建宗门需要达到 30 级并消耗 10000 金币</li>
                    <li>• 宗门成员可以一起参与宗门活动</li>
                    <li>• 宗门等级越高，可容纳成员越多</li>
                    <li>• 宗门捐献可提升宗门等级</li>
                </ul>
            </div>
        </div>
    );
}
