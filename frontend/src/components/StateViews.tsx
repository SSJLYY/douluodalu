'use client';

/**
 * 任务#27：游戏内页面「首载骨架 / 错误重试 / 空态」三态复用组件。
 * 视觉基准取自第十轮打磨的排行榜页（shimmer 骨架、dl-pop 错误面板 + 重试按钮、灰字空态），
 * 这里刻意只收敛成三个无状态小组件 + 一个 useGameData 首屏门控（BootState），
 * 不做数据层/配置对象抽象——各页面结构同构但行内容不同，用 props 传内容与回调即可，
 * 避免「每页复制一份骨架/错误 JSX」的漂移，也不至于抽象到失去页面定制能力。
 */

/** 列表类骨架：与排行榜/宗门列表行同构（头像块 + 两行文字 + 右侧数值块） */
export function SkeletonRows({ rows = 8, testId = 'skeleton-rows' }: { rows?: number; testId?: string }) {
    return (
        <div data-testid={testId} aria-hidden className="divide-y divide-gray-700">
            {Array.from({ length: rows }, (_, i) => (
                <div key={i} className="p-4 flex items-center justify-between gap-4">
                    <div className="flex items-center gap-4 min-w-0 flex-1">
                        <div className="dl-skeleton w-8 h-8 shrink-0" />
                        <div className="min-w-0 flex-1">
                            <div className="dl-skeleton w-28 h-4" />
                            <div className="dl-skeleton w-16 h-3 mt-2" />
                        </div>
                    </div>
                    <div className="dl-skeleton w-16 h-5 shrink-0" />
                </div>
            ))}
        </div>
    );
}

/** 卡片类骨架：与商店/天赋等 1/2/3 列网格卡片同构 */
export function SkeletonCards({ count = 6, testId = 'skeleton-cards' }: { count?: number; testId?: string }) {
    return (
        <div data-testid={testId} aria-hidden className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
            {Array.from({ length: count }, (_, i) => (
                <div key={i} className="bg-surface border border-line rounded-lg p-4">
                    <div className="dl-skeleton w-24 h-5" />
                    <div className="dl-skeleton w-full h-3 mt-3" />
                    <div className="flex items-center justify-between gap-3 mt-4">
                        <div className="dl-skeleton w-16 h-4" />
                        <div className="dl-skeleton w-16 h-8 rounded" />
                    </div>
                </div>
            ))}
        </div>
    );
}

/** 错误 + 重试面板（排行榜 rank-error 同款：dl-pop 入场、红字文案、紫底重试钮） */
export function ErrorPanel({
    message,
    onRetry,
    testId = 'error-panel',
    retryTestId,
}: {
    message: string;
    onRetry: () => void;
    testId?: string;
    retryTestId?: string;
}) {
    return (
        <div data-testid={testId} className="dl-pop text-center py-10 px-4">
            <div className="text-red-400 mb-4 break-words">{message}</div>
            <button
                type="button"
                data-testid={retryTestId ?? `${testId}-retry`}
                onClick={onRetry}
                className="px-5 min-h-11 bg-purple-600 hover:bg-purple-500 rounded-lg text-sm transition-colors"
            >
                重试
            </button>
        </div>
    );
}

/** 空态占位（背包为空 / 暂无商品 / 未加入宗门等统一样式） */
export function EmptyPanel({ message, testId = 'empty-state' }: { message: string; testId?: string }) {
    return (
        <div data-testid={testId} className="text-center py-8 px-4 text-gray-400">
            {message}
        </div>
    );
}

/**
 * useGameData 页面的统一首屏门控：state 未落地时——
 * 有 loadError → 错误重试面板（重试走 refresh 重取 /api/game/state）；否则列表骨架。
 */
export function BootState({ error, onRetry, rows = 7 }: { error: string; onRetry: () => void; rows?: number }) {
    return error ? (
        <ErrorPanel message={error} onRetry={onRetry} testId="boot-error" />
    ) : (
        <div className="bg-gray-800 rounded-lg overflow-hidden">
            <SkeletonRows rows={rows} testId="boot-skeleton" />
        </div>
    );
}
