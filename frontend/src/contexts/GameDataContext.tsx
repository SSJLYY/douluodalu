'use client';

import { createContext, useContext, type ReactNode } from 'react';
import { makeRunMessageAction, useGameData as useGameDataStore } from '@/lib/hooks';

/**
 * 页面拿到的完整数据面：useGameData 实例（轮询/WS/状态/actionLoading/runAction…）
 * + runMessageAction 简写（成功路径统一透传后端 message，收敛 9 页重复写法）。
 */
export type GameData = ReturnType<typeof useGameDataStore> & {
    runMessageAction: <T extends { message: string }>(action: () => Promise<T>, fallbackError?: string) => Promise<void>;
};

const GameDataContext = createContext<GameData | null>(null);

/**
 * 游戏主数据单实例 Provider：由 game/layout 挂载（布局顶栏自身也消费同一实例）。
 * 此前 layout + 每个 game 页面各自调用 useGameData，各起一份 12s 轮询、WS 订阅和
 * 首屏 /api/game/state 拉取；收敛到 Context 后整个 /game 树只有一份订阅与一条轮询。
 */
export function GameDataProvider({ children }: { children: ReactNode }) {
    const base = useGameDataStore();
    const value: GameData = {
        ...base,
        runMessageAction: makeRunMessageAction(base.runAction, base.setMessage),
    };
    return <GameDataContext.Provider value={value}>{children}</GameDataContext.Provider>;
}

/** 页面侧消费入口：拿到与布局顶栏同一份 gameData（仅在 GameDataProvider 内可用） */
export function useGameData(): GameData {
    const ctx = useContext(GameDataContext);
    if (!ctx) {
        throw new Error('useGameData 必须在 <GameDataProvider> 内使用（game/layout 已包裹）');
    }
    return ctx;
}
