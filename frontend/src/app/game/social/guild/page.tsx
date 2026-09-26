'use client';

import { useCallback, useEffect, useState } from 'react';
import api, { GuildBossRank, GuildBossResult, GuildMemberInfo, GuildSummary, ShopItem } from '@/lib/api';
import { useGameData } from '@/lib/hooks';
import { useAuth } from '@/contexts/AuthContext';
import { BootState, ErrorPanel, EmptyPanel } from '@/components/StateViews';
import BossRankPanel from '@/components/BossRankPanel';

/** ISO-8601 → 本地化「月-日 时:分」；解析失败原样截断，不让坏数据炸渲染 */
function formatJoinedAt(iso: string): string {
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return iso.slice(0, 16).replace('T', ' ');
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function GuildPage() {
    const { user } = useAuth();
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
    // 任务#28：成员列表（已加入视图）。membersError 与 listError 分开——成员查失败
    // 不应把整个「我的宗门」卡打成错误态，降级为卡内错误+重试即可
    const [members, setMembers] = useState<GuildMemberInfo[]>([]);
    const [membersError, setMembersError] = useState('');
    // 宗门 Boss 周榜：null = 未加载/加载失败（整块静默不渲染，纯展示功能不阻塞挑战主流程）
    const [bossRank, setBossRank] = useState<GuildBossRank | null>(null);

    const loadMembers = useCallback(async (joined: boolean) => {
        if (!joined) {
            setMembers([]);
            setMembersError('');
            return;
        }
        try {
            setMembers(await api.getGuildMembers());
            setMembersError('');
        } catch (err) {
            setMembers([]);
            setMembersError(err instanceof Error && err.message ? err.message : '加载成员列表失败');
        }
    }, []);

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
            await loadMembers(res.joined && Boolean(res.guild));
        } catch (err) {
            setListError(err instanceof Error && err.message ? err.message : '加载我的宗门失败');
            setMyGuild(null);
        }
        setListLoading(false);
    }, [loadMembers]);

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

    // 周榜拉取失败静默（置 null 不渲染），不打红字/不重试打扰——Boss 挑战主功能不受影响
    const loadBossRank = useCallback(async () => {
        try {
            setBossRank(await api.guildBossRank());
        } catch {
            setBossRank(null);
        }
    }, []);

    useEffect(() => {
        queueMicrotask(() => {
            loadGuilds();
            loadGuildShop();
        });
    }, [loadGuilds, loadGuildShop]);

    // 周榜拉取时机①：进入宗门页且确认在宗门内（myGuild 就绪）后拉一次；退出/解散（id 变 null）清空不渲染。
    // 依赖 id 而非对象引用：loadGuilds 重建 myGuild 对象（捐献/踢人等）不会重复拉榜；
    // setState 全部走微任务回调（照本页 loadGuilds 惯例），避免 effect 体内同步 setState
    const myGuildId = myGuild?.id ?? null;
    useEffect(() => {
        queueMicrotask(() => {
            if (myGuildId === null) {
                setBossRank(null);
                return;
            }
            void loadBossRank();
        });
    }, [myGuildId, loadBossRank]);

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

    // 任务：leave 响应升级（{message, disbanded?, transferredTo?}）——
    // disbanded=true 宗门已随退出解散；transferredTo 为继任者昵称；旧后端仅 {message} 走兜底文案
    const handleLeaveGuild = () => runAction(
        () => api.leaveGuild(),
        (result) => {
            if (result.disbanded) setMessage('宗门已解散');
            else if (result.transferredTo) setMessage(`已退出，宗主转让给 ${result.transferredTo}`);
            else setMessage(result.message || '已退出宗门');
            setMyGuild(null);
        },
        '退出失败',
    ).then(loadGuilds);

    // 任务#28：管理按钮权限完全以服务端 members 行推导——
    // isLeader 取 guild.leader_id 事实源；user.userId 未就绪时按钮一律不渲染（安全缺省）
    const currentMember = members.find((m) => m.userId === user?.userId) ?? null;
    const isLeader = Boolean(currentMember?.isLeader);
    // 解散仅宗主且只剩自己时可用（与服务端 disband 校验同口径），否则禁用态给 title 说明
    const canDisband = isLeader && members.length === 1;

    const handleKickMember = (targetUserId: number) => runAction(
        () => api.kickGuildMember(targetUserId),
        (result) => setMessage(result.message),
        '踢出失败',
    ).then(loadGuilds);

    const handleTransferLeader = (targetUserId: number) => runAction(
        () => api.transferGuildLeader(targetUserId),
        (result) => setMessage(result.message),
        '转让失败',
    ).then(loadGuilds);

    const handleDisbandGuild = () => runAction(
        () => api.disbandGuild(),
        (result) => setMessage(result.message),
        '解散失败',
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
            // 周榜拉取时机②：挑战成功后立即刷新，本次伤害即时入榜（失败静默，不影响结果展示）
            void loadBossRank();
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
                        {/* 任务#28：解散入口仅宗主可见；启用条件与服务端一致（只剩自己），
                            禁用态用 title 说明原因（hover/长按可读到），避免用户盲点报错 */}
                        {isLeader && (
                            <button
                                type="button"
                                onClick={handleDisbandGuild}
                                disabled={loading || !canDisband}
                                title={canDisband ? '解散后宗门与成员记录将被清除' : '仅宗主可解散，且需宗门内只剩自己'}
                                className="ml-2 mt-4 px-4 min-h-11 bg-gray-700 hover:bg-red-900/60 disabled:bg-gray-600 disabled:text-gray-300 disabled:hover:bg-gray-600 border border-red-800 disabled:border-gray-500 rounded text-sm font-medium"
                            >
                                解散宗门
                            </button>
                        )}
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

            {/* 任务#28：成员列表卡（仅已加入时展示）。行内容 = 昵称/加入时间/贡献 + 宗主徽章；
                宗主视角下其他成员行带「踢出/转让」，自己的行无管理按钮 */}
            {myGuild && (
                <div data-testid="guild-members-card" className="bg-gray-800 rounded-lg p-6 border border-gray-600">
                    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-4">
                        <h2 className="text-lg font-semibold">成员列表</h2>
                        <span className="text-sm text-gray-200 tabular-nums">共 {members.length} 人</span>
                    </div>
                    {listLoading && members.length === 0 ? (
                        <div aria-hidden data-testid="guild-members-skeleton" className="space-y-3">
                            {Array.from({ length: Math.min(Math.max(myGuild.memberCount, 1), 5) }, (_, i) => (
                                <div key={i} className="bg-gray-700 rounded-lg p-4 border border-gray-600">
                                    <div className="dl-skeleton w-24 h-4" />
                                    <div className="dl-skeleton w-40 h-3 mt-2" />
                                </div>
                            ))}
                        </div>
                    ) : membersError ? (
                        <ErrorPanel message={membersError} onRetry={loadGuilds} testId="guild-members-error" />
                    ) : members.length === 0 ? (
                        <EmptyPanel message="暂无成员数据" testId="guild-members-empty" />
                    ) : (
                        <ul className="space-y-3">
                            {members.map((member) => {
                                const isSelf = member.userId === user?.userId;
                                return (
                                    <li
                                        key={member.userId}
                                        data-testid={`guild-member-row-${member.userId}`}
                                        className={`bg-gray-700 rounded-lg p-4 border ${isSelf ? 'border-yellow-600' : 'border-gray-600'}`}
                                    >
                                        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
                                            <div className="min-w-0">
                                                <div className="flex flex-wrap items-center gap-2">
                                                    <span className="font-semibold break-words">{member.nickname}</span>
                                                    {member.isLeader && (
                                                        <span className="px-2 py-0.5 rounded bg-yellow-600/20 border border-yellow-600 text-yellow-400 text-xs shrink-0">
                                                            宗主
                                                        </span>
                                                    )}
                                                    {isSelf && (
                                                        <span className="px-2 py-0.5 rounded bg-gray-600/40 border border-gray-500 text-gray-200 text-xs shrink-0">
                                                            我
                                                        </span>
                                                    )}
                                                </div>
                                                <p className="text-gray-200 text-sm mt-1 tabular-nums break-words">
                                                    加入 {formatJoinedAt(member.joinedAt)} | 贡献 {member.contribution}
                                                </p>
                                            </div>
                                            {isLeader && !isSelf && (
                                                <div className="flex gap-2 shrink-0">
                                                    <button
                                                        type="button"
                                                        onClick={() => handleTransferLeader(member.userId)}
                                                        disabled={loading}
                                                        className="px-3 min-h-11 bg-purple-700 hover:bg-purple-600 disabled:bg-gray-600 rounded text-sm font-medium"
                                                    >
                                                        转让
                                                    </button>
                                                    <button
                                                        type="button"
                                                        onClick={() => handleKickMember(member.userId)}
                                                        disabled={loading}
                                                        className="px-3 min-h-11 bg-red-700 hover:bg-red-600 disabled:bg-gray-600 rounded text-sm font-medium"
                                                    >
                                                        踢出
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </div>
            )}

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
                        {/* 本周伤害榜：仅Boss卡（=已在宗门内）渲染；数据未就绪/拉取失败静默整块隐藏 */}
                        {bossRank && (
                            <BossRankPanel rank={bossRank} myUserId={user?.userId ?? null} />
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
                    <li>• 宗主可踢出成员或转让宗主；宗门内只剩自己时可解散（创建费不退还）</li>
                </ul>
            </div>
        </div>
    );
}
