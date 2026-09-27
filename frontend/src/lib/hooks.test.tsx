/**
 * useAutoBattle 自动战斗循环测试（jsdom fake timers）。
 * 直接测 hook：它不依赖 useGameData/AuthContext（回调经参数注入），api.battle/breakthrough
 * 用 vi.spyOn 打在 api 单例上——hooks.ts 引用同一单例，无需整模块 mock。
 * useGameData 依赖链重（AuthProvider/WS/轮询），故循环调度核心收敛在 useAutoBattle 内独立可测。
 */
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import api, { ApiError } from '@/lib/api';
import type { BattleResult } from '@/lib/api';
import { AUTO_BATTLE_INTERVAL_MS, AUTO_BATTLE_RETRY_PAUSE_MS, breakthroughCostFor, useAutoBattle } from '@/lib/hooks';

/** BattleResult 合法桩：只覆盖循环消费的字段（playerSoulPower/playerLevel），其余给默认值 */
function battleResult(overrides: Partial<BattleResult> = {}): BattleResult {
    return {
        won: true,
        rounds: 3,
        monsterName: '天青牛蟒',
        expGained: 10,
        goldGained: 20,
        drops: [],
        playerHp: 200,
        playerLevel: 5,
        playerGold: 500,
        playerSoulPower: 100,
        ...overrides,
    };
}

describe('useAutoBattle（自动战斗循环）', () => {
    let battleSpy: ReturnType<typeof vi.spyOn>;
    let breakthroughSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        vi.useFakeTimers();
        battleSpy = vi.spyOn(api, 'battle').mockResolvedValue(battleResult());
        breakthroughSpy = vi.spyOn(api, 'breakthrough').mockResolvedValue({ success: true, newLevel: 6, message: '突破成功' });
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('enabled 翻转启停：false 不发请求；true 每 2s 一次；翻回 false 停止', async () => {
        const onResult = vi.fn();
        const { rerender } = renderHook(({ enabled }) => useAutoBattle(enabled, false, onResult), {
            initialProps: { enabled: false },
        });

        await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
        expect(battleSpy).not.toHaveBeenCalled();
        expect(onResult).not.toHaveBeenCalled();

        rerender({ enabled: true });
        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(battleSpy).toHaveBeenCalledTimes(1);
        expect(onResult).toHaveBeenNthCalledWith(1, battleResult());

        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS * 2); });
        expect(battleSpy).toHaveBeenCalledTimes(3);

        rerender({ enabled: false });
        await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
        expect(battleSpy).toHaveBeenCalledTimes(3);
    });

    it('429 后暂停 60s：窗口内 tick 静默跳过，超时后自动恢复', async () => {
        battleSpy.mockRejectedValueOnce(new ApiError('请求过于频繁，请稍后再试', 429))
            .mockResolvedValue(battleResult());
        renderHook(() => useAutoBattle(true, false, vi.fn()));

        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(battleSpy).toHaveBeenCalledTimes(1);

        // 退避窗口内（59s 处）不再发起请求
        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_RETRY_PAUSE_MS - 2000); });
        expect(battleSpy).toHaveBeenCalledTimes(1);

        // 窗口过后恢复：62s、64s 两个 tick
        await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
        expect(battleSpy).toHaveBeenCalledTimes(3);
    });

    it('非 429 错误不暂停：下一 tick 照常重试', async () => {
        battleSpy.mockRejectedValueOnce(new ApiError('网络抖动', 500))
            .mockResolvedValue(battleResult());
        renderHook(() => useAutoBattle(true, false, vi.fn()));

        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(battleSpy).toHaveBeenCalledTimes(1);

        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(battleSpy).toHaveBeenCalledTimes(2);
    });

    it('document.hidden=true 跳过 tick，恢复可见后继续', async () => {
        let hidden = true;
        Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
        renderHook(() => useAutoBattle(true, false, vi.fn()));

        await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
        expect(battleSpy).not.toHaveBeenCalled();

        hidden = false;
        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(battleSpy).toHaveBeenCalledTimes(1);

        // 还原 jsdom 默认（document.hidden=false），避免泄漏到同文件其他用例
        Object.defineProperty(document, 'hidden', { configurable: true, get: () => false });
    });

    it('上一场 battle 未返回时跳过后续 tick（防请求堆积）', async () => {
        let resolveBattle: (r: BattleResult) => void = () => undefined;
        battleSpy.mockImplementation(() => new Promise<BattleResult>((res) => { resolveBattle = res; }));
        const onResult = vi.fn();
        renderHook(() => useAutoBattle(true, false, onResult));

        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(battleSpy).toHaveBeenCalledTimes(1);

        // 挂起期间多个 tick 全部跳过
        await act(async () => { await vi.advanceTimersByTimeAsync(8000); });
        expect(battleSpy).toHaveBeenCalledTimes(1);

        await act(async () => { resolveBattle(battleResult()); });
        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(battleSpy).toHaveBeenCalledTimes(2);
        expect(onResult).toHaveBeenCalledTimes(1);
    });

    it('autoBreakthrough 开启且魂力 ≥ 120·Lv^1.55 → 战斗后顺手突破；不够或关闭则不调', async () => {
        const level = 5;
        // 恰好等于消耗（后端 getBreakthroughCost 截断取整同源）→ 触发
        battleSpy.mockResolvedValue(battleResult({ playerLevel: level, playerSoulPower: breakthroughCostFor(level) }));
        const { rerender } = renderHook(({ enabled, autoBreakthrough }) => useAutoBattle(enabled, autoBreakthrough, vi.fn()), {
            initialProps: { enabled: true, autoBreakthrough: true },
        });
        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(breakthroughSpy).toHaveBeenCalledTimes(1);

        // 魂力差 1 → 不触发
        breakthroughSpy.mockClear();
        battleSpy.mockResolvedValue(battleResult({ playerLevel: level, playerSoulPower: breakthroughCostFor(level) - 1 }));
        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(breakthroughSpy).not.toHaveBeenCalled();

        // autoBreakthrough 关闭 → 不触发（开关经 ref 热更新，不重启定时器）
        rerender({ enabled: true, autoBreakthrough: false });
        battleSpy.mockResolvedValue(battleResult({ playerLevel: level, playerSoulPower: breakthroughCostFor(level) }));
        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS); });
        expect(breakthroughSpy).not.toHaveBeenCalled();
    });

    it('自动突破失败（success=false / 抛错）静默，不打断战斗循环', async () => {
        breakthroughSpy.mockRejectedValue(new ApiError('魂力不足', 400));
        battleSpy.mockResolvedValue(battleResult({ playerLevel: 5, playerSoulPower: 999999 }));
        const onResult = vi.fn();
        renderHook(() => useAutoBattle(true, true, onResult));

        await act(async () => { await vi.advanceTimersByTimeAsync(AUTO_BATTLE_INTERVAL_MS * 3); });
        expect(battleSpy).toHaveBeenCalledTimes(3);
        expect(onResult).toHaveBeenCalledTimes(3);
        expect(breakthroughSpy).toHaveBeenCalledTimes(3);
    });
});
