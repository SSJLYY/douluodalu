'use client';

import { useCallback, useEffect, useState } from 'react';
import api, { ShopItem } from '@/lib/api';
import { useGameData } from '@/contexts/GameDataContext';
import { BootState, ErrorPanel, EmptyPanel, SkeletonCards } from '@/components/StateViews';
import { useAsync } from '@/lib/hooks';
import { killingAttrCost, killingTitleAttrs } from '@/lib/killing';

export default function ShopPage() {
    const { gameState, message, setMessage, actionLoading: loading, loadError, refresh, runAction } = useGameData();
    const [normalItems, setNormalItems] = useState<ShopItem[]>([]);
    const [bossItems, setBossItems] = useState<ShopItem[]>([]);
    const [limitedItems, setLimitedItems] = useState<ShopItem[]>([]);
    const [activeTab, setActiveTab] = useState<'normal' | 'boss' | 'limited'>('normal');
    // 任务#27：商店商品三态——首载骨架 / 失败重试 / 空态（此前只有 console.error + 「暂无物品」误导）
    const [shopLoading, setShopLoading] = useState(true);
    const [shopError, setShopError] = useState('');
    // 第二十九轮杀气商店（设计文档 §7.4）：面板数据独立局部加载（useAsync），失败/重试
    // 不波及上方金币/Boss币三页签；购买成功后 runAction 已自动刷新 gameState，此处再
    // killing.refresh() 局部 refetch，让 owned/价格与扣款保持同一快照
    const killing = useAsync(() => api.getKillingShop());

    const loadShopItems = useCallback(async () => {
        setShopLoading(true);
        setShopError('');
        try {
            const [normal, boss, limited] = await Promise.all([
                api.getNormalShopItems(),
                api.getBossShopItems(),
                api.getLimitedShopItems()
            ]);
            setNormalItems(normal);
            setBossItems(boss);
            setLimitedItems(limited);
        } catch (err) {
            setShopError(err instanceof Error && err.message ? err.message : '加载商店物品失败');
        } finally {
            setShopLoading(false);
        }
    }, []);

    useEffect(() => {
        queueMicrotask(loadShopItems);
    }, [loadShopItems]);

    const handleBuyNormalItem = (itemId: number) => runAction(
        () => api.buyNormalShopItem(itemId),
        (result) => setMessage((result.item as { reward?: string })?.reward || '购买成功！'),
        '购买失败',
    );

    const handleBuyBossItem = (itemId: number) => runAction(
        () => api.buyBossShopItem(itemId),
        (result) => setMessage((result.item as { reward?: string })?.reward || '购买成功！'),
        '购买失败',
    );

    const handleBuyLimitedItem = (itemId: number) => runAction(
        () => api.buyLimitedShopItem(itemId),
        (result) => setMessage(`${result.message}！`),
        '购买失败',
    );

    // ==================== 杀气商店（第二十九轮，设计文档 §7.4） ====================
    // 业务失败（杀气不足/已拥有）→ 后端 400 ApiError.message → runAction 落 message toast；
    // 成功回调里局部 refetch（owned/价格与扣款同快照），余额条随 killing.data 更新
    const handleBuyKillingTitle = (titleId: string) => runAction(
        () => api.buyKillingTitle(titleId),
        (result) => {
            setMessage(result.message);
            void killing.refresh();
        },
        '兑换失败',
    );

    const handleBuyKillingAttr = (stat: 'hp' | 'atk') => runAction(
        () => api.buyKillingAttr(stat),
        (result) => {
            setMessage(result.message);
            void killing.refresh();
        },
        '购买失败',
    );

    if (!gameState) {
        return (
            <div className="space-y-6">
                <h1 className="text-2xl font-bold text-yellow-400">商店</h1>
                <BootState error={loadError} onRetry={refresh} rows={5} />
            </div>
        );
    }

    const canAfford = (item: ShopItem) => item.currencyType === 'GOLD'
        ? gameState.profile.gold >= item.price
        : gameState.profile.bossCoin >= item.price;

    const currencyLabel = (item: ShopItem) => item.currencyType === 'GOLD' ? '金币' : 'Boss币';

    // 第二十九轮：杀气面板快照提为局部 const——闭包（map 回调）内 TS 收窄不失效，免去非空断言
    const killShop = killing.data;

    /** 单个页签的商品列表：骨架 / 错误重试 / 空态 / 卡片网格（三页签同构，收敛于此避免复制粘贴） */
    const renderShopList = (opts: {
        tab: string;
        title: string;
        items: ShopItem[];
        buyCls: string;
        emptyText: string;
        onBuy: (id: number) => void;
        showMeta?: boolean;
    }) => {
        const { tab, title, items, buyCls, emptyText, onBuy, showMeta } = opts;
        let body;
        if (shopLoading && items.length === 0) {
            body = <SkeletonCards count={6} testId={`shop-skeleton-${tab}`} />;
        } else if (shopError && items.length === 0) {
            body = <ErrorPanel message={shopError} onRetry={loadShopItems} testId={`shop-error-${tab}`} />;
        } else if (items.length === 0) {
            body = <EmptyPanel message={emptyText} testId={`shop-empty-${tab}`} />;
        } else {
            body = (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
                    {items.map((item) => (
                        <div key={item.id} className="bg-surface border border-line rounded-lg p-4 hover:shadow-lg transition-shadow">
                            <h3 className="font-semibold text-lg break-words">{item.name}</h3>
                            <p className="text-gray-400 text-sm mt-1">{item.description}</p>
                            {showMeta && (
                                <div className="mt-2 text-xs text-gray-500">
                                    {item.requiresLevel > 1 && <span className="mr-3">需 {item.requiresLevel} 级</span>}
                                    {item.stock >= 0 ? `限购 ${item.stock} 件` : '不限购'}
                                </div>
                            )}
                            <div className="mt-4 flex justify-between items-center gap-3">
                                <div className="flex items-center gap-2 min-w-0">
                                    <span className="font-semibold tabular-nums">{item.price} {currencyLabel(item)}</span>
                                </div>
                                <button
                                    onClick={() => onBuy(item.id)}
                                    disabled={loading || !canAfford(item)}
                                    className={`shrink-0 px-4 min-h-11 ${buyCls} disabled:bg-gray-600 rounded text-sm`}
                                >
                                    购买
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            );
        }
        return (
            <div className="space-y-4">
                <h2 className="text-lg font-semibold">{title}</h2>
                {/* 刷新失败但已有旧数据：顶部横幅提示，不打断浏览 */}
                {shopError && items.length > 0 && (
                    <div role="alert" className="dl-shake px-4 py-2 bg-red-500/20 border border-red-500/50 rounded-lg text-red-300 text-sm">
                        {shopError}，正在显示上次数据
                    </div>
                )}
                {body}
            </div>
        );
    };

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400 dl-fade-up">商店</h1>

            {/* 货币显示 */}
            <div className="dl-fade-up [animation-delay:60ms] bg-surface border border-line rounded-lg p-4">
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
            <div className="flex border-b border-gray-700" role="tablist">
                {([
                    { id: 'normal', name: '普通商店' },
                    { id: 'boss', name: 'Boss商店' },
                    { id: 'limited', name: '限时珍品' },
                ] as const).map((tab) => (
                    <button
                        key={tab.id}
                        type="button"
                        role="tab"
                        aria-selected={activeTab === tab.id}
                        className={`min-h-11 py-2 px-4 font-semibold transition-colors ${
                            activeTab === tab.id
                                ? 'text-yellow-400 border-b-2 border-yellow-400'
                                : 'text-gray-400 hover:text-foreground'
                        }`}
                        onClick={() => setActiveTab(tab.id)}
                    >
                        {tab.name}
                    </button>
                ))}
            </div>

            {activeTab === 'normal' && renderShopList({
                tab: 'normal',
                title: '普通商店',
                items: normalItems,
                buyCls: 'bg-green-700 hover:bg-green-600',
                emptyText: '暂无物品',
                onBuy: handleBuyNormalItem,
            })}

            {activeTab === 'boss' && renderShopList({
                tab: 'boss',
                title: 'Boss商店',
                items: bossItems,
                buyCls: 'bg-blue-600 hover:bg-blue-700',
                emptyText: '暂无物品',
                onBuy: handleBuyBossItem,
            })}

            {activeTab === 'limited' && renderShopList({
                tab: 'limited',
                title: '限时珍品',
                items: limitedItems,
                buyCls: 'bg-purple-600 hover:bg-purple-700',
                emptyText: '暂无物品，请稍后再来',
                onBuy: handleBuyLimitedItem,
                showMeta: true,
            })}

            {/* ==================== 杀气商店（第二十九轮，设计文档 §7.4） ====================
                独立区段（金币/Boss币商店之后），不走 renderShopList：数据源（useAsync 局部
                快照）、货币（杀气）与禁用规则（已拥有不可回购）都与三页签不同。
                杀气余额颜色照塔页（杀戮之都）杀戮值展示（text-orange-500）；🗡️ 图标取杀气
                主题（与战力明细称号行同款）。禁用态的余额预判是展示层优化，后端仍为唯一
                校验点（杀气不足 → 400 → message toast）。 */}
            <section className="space-y-4" aria-labelledby="killing-shop-title" data-testid="killing-shop-section">
                <div className="flex items-center justify-between gap-3 flex-wrap">
                    <h2 id="killing-shop-title" className="text-lg font-semibold">🗡️ 杀气商店</h2>
                    {killShop && (
                        <div className="flex items-baseline gap-2 min-w-0">
                            <span aria-hidden className="text-xl">🗡️</span>
                            <span className="text-xl font-bold text-orange-500 tabular-nums">
                                {killShop.killingIntent.toLocaleString()}
                            </span>
                            <span className="text-sm text-gray-400">杀气（塔挑战获得）</span>
                        </div>
                    )}
                </div>
                {killShop ? (
                    <>
                        {/* 称号兑换：8 卡；购买即永久拥有、全部已拥有称号属性叠加生效（无「佩戴」概念）。
                            已拥有 → 按钮变「已拥有」禁用；余额不足 → 禁用。属性文案由 lib/killing.ts
                            纯函数格式化（零字段省略，title_1/2/3 不出现「+0%」噪音） */}
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
                            {killShop.titles.map((title) => (
                                <div key={title.id} className="bg-surface border border-line rounded-lg p-4 flex flex-col">
                                    <div className="flex justify-between items-center gap-2">
                                        <h3 className="font-semibold text-lg break-words">{title.name}</h3>
                                        {title.owned && (
                                            <span className="shrink-0 text-xs px-2 py-1 rounded bg-green-700/40 text-green-400">已拥有</span>
                                        )}
                                    </div>
                                    <p className="text-gray-400 text-sm mt-1 min-h-5 break-words">
                                        {killingTitleAttrs(title).join('　') || '永久称号'}
                                    </p>
                                    <div className="mt-4 flex justify-between items-center gap-3">
                                        <span className="font-semibold tabular-nums text-orange-400">{title.cost.toLocaleString()} 杀气</span>
                                        <button
                                            onClick={() => handleBuyKillingTitle(title.id)}
                                            disabled={loading || title.owned || killShop.killingIntent < title.cost}
                                            className="shrink-0 px-4 min-h-11 bg-red-700 hover:bg-red-600 disabled:bg-gray-600 rounded text-sm"
                                        >
                                            {title.owned ? '已拥有' : '兑换'}
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                        {/* 属性购买：HP/ATK 两商品各自独立计数，价格 = 100×2^已购次数（后端 nextCost
                            为唯一数值源；killingAttrCost 前端镜像仅用于「再下一档」价格预告）。无限次购买 */}
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                            {killShop.attrs.map((attr) => (
                                <div key={attr.stat} className="bg-surface border border-line rounded-lg p-4 flex flex-col">
                                    <h3 className="font-semibold text-lg break-words">{attr.displayName}</h3>
                                    <p className="text-gray-400 text-sm mt-1">
                                        每次 +{attr.effectPerBuy} {attr.stat === 'hp' ? 'HP' : 'ATK'}（基值固定）·已购 {attr.buys} 次
                                    </p>
                                    <p className="text-gray-500 text-xs mt-1">
                                        再下一档 {killingAttrCost(attr.buys + 1).toLocaleString()} 杀气
                                    </p>
                                    <div className="mt-4 flex justify-between items-center gap-3">
                                        <span className="font-semibold tabular-nums text-orange-400">{attr.nextCost.toLocaleString()} 杀气</span>
                                        <button
                                            onClick={() => handleBuyKillingAttr(attr.stat === 'atk' ? 'atk' : 'hp')}
                                            disabled={loading || killShop.killingIntent < attr.nextCost}
                                            className="shrink-0 px-4 min-h-11 bg-red-700 hover:bg-red-600 disabled:bg-gray-600 rounded text-sm"
                                        >
                                            购买
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </>
                ) : killing.error ? (
                    <ErrorPanel message={killing.error} onRetry={() => void killing.refresh()} testId="killing-shop-error" />
                ) : (
                    <SkeletonCards count={3} testId="killing-shop-skeleton" />
                )}
            </section>

            {/* 消息提示 */}
            {message && (
                <div key={message} className="dl-slide-in bg-gray-700 rounded-lg p-4 text-center">
                    {message}
                </div>
            )}

            {/* 商店说明 */}
            <div className="bg-surface border border-line rounded-lg p-4">
                <h3 className="font-semibold mb-2">商店说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• Boss商店：使用Boss币购买稀有物品</li>
                    <li>• 限时珍品：限量抢购的稀有装备，售完即止</li>
                    <li>• Boss币通过击败Boss获得</li>
                    <li>• 杀气商店：塔（杀戮之都）挑战获得杀气，可兑换永久称号或购买 HP/ATK 提升</li>
                    <li>• 购买前请确认有足够的货币</li>
                </ul>
            </div>
        </div>
    );
}
