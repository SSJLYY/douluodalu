/**
 * useModalEscape（弹窗 Escape 关闭 + 聚焦收敛）与 makeRunMessageAction（runAction 成功路径
 * 透传后端 message 的简写）测试。两者均为注入式、不依赖 GameDataProvider/AuthContext/WS，
 * 故与 useAutoBattle 同款直接测本体（照 hooks.test.tsx 先例，避免拉起 useGameData 重依赖链）。
 */
import { useRef } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { makeRunMessageAction, useModalEscape, type RunActionFn } from '@/lib/hooks';

function EscapeHost({ open, onClose }: { open: boolean; onClose: () => void }) {
    const cancelRef = useRef<HTMLButtonElement>(null);
    useModalEscape(open, onClose, cancelRef);
    return <button ref={cancelRef} type="button">取消</button>;
}

describe('useModalEscape（弹窗 Escape + 聚焦）', () => {
    it('打开时聚焦取消钮，Escape 触发 close；关闭后监听移除', () => {
        const onClose = vi.fn();
        const { rerender } = render(<EscapeHost open={false} onClose={onClose} />);
        const cancel = screen.getByRole('button', { name: '取消' });
        expect(document.activeElement).not.toBe(cancel);

        rerender(<EscapeHost open={true} onClose={onClose} />);
        expect(document.activeElement).toBe(cancel);

        fireEvent.keyDown(window, { key: 'Escape' });
        expect(onClose).toHaveBeenCalledTimes(1);

        // 关闭后监听卸载：再次 Escape 不触发
        rerender(<EscapeHost open={false} onClose={onClose} />);
        fireEvent.keyDown(window, { key: 'Escape' });
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('close 回调换引用不重跑 effect：open 不变时不重复聚焦', () => {
        const { rerender } = render(<EscapeHost open={true} onClose={vi.fn()} />);
        const cancel = screen.getByRole('button', { name: '取消' });
        expect(document.activeElement).toBe(cancel);

        cancel.blur();
        expect(document.activeElement).not.toBe(cancel);

        // 仅换 onClose 引用（模拟 actionLoading 翻转等重渲染）→ 不应重新聚焦抢焦点
        rerender(<EscapeHost open={true} onClose={vi.fn()} />);
        expect(document.activeElement).not.toBe(cancel);
    });
});

describe('makeRunMessageAction（runAction 成功路径简写）', () => {
    it('成功：动作 resolve 后把响应 message 透传给 setMessage，并原样转发 fallbackError', async () => {
        const setMessage = vi.fn();
        let fallbackSeen: string | undefined;
        const runAction: RunActionFn = async (action, onSuccess, fallbackError) => {
            fallbackSeen = fallbackError;
            onSuccess(await action());
        };
        const run = makeRunMessageAction(runAction, setMessage);

        await run(async () => ({ message: '升级成功' }), '升级失败');
        expect(setMessage).toHaveBeenCalledWith('升级成功');
        expect(fallbackSeen).toBe('升级失败');
    });

    it('缺省 fallbackError 为「操作失败」', async () => {
        const setMessage = vi.fn();
        let fallbackSeen: string | undefined = 'sentinel';
        const runAction: RunActionFn = async (action, onSuccess, fallbackError) => {
            fallbackSeen = fallbackError;
            onSuccess(await action());
        };
        const run = makeRunMessageAction(runAction, setMessage);

        await run(async () => ({ message: 'ok' }));
        expect(fallbackSeen).toBe('操作失败');
        expect(setMessage).toHaveBeenCalledWith('ok');
    });
});
