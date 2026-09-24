'use client';

import { useEffect } from 'react';

/** 全局错误边界：捕获 /game 等路由段内未处理的渲染异常（Next 16 使用 unstable_retry 重试） */
export default function GlobalError({
    error,
    unstable_retry,
}: {
    error: Error & { digest?: string };
    unstable_retry: () => void;
}) {
    useEffect(() => {
        console.error('页面渲染异常:', error);
    }, [error]);

    return (
        <div className="min-h-screen flex flex-col items-center justify-center gap-4 bg-gray-900">
            <div className="text-4xl">💥</div>
            <h2 className="text-xl font-bold text-yellow-400">出了点问题</h2>
            <p className="text-sm text-gray-400">{error.message || '服务器开小差了，请重试'}</p>
            <button
                onClick={() => unstable_retry()}
                className="px-6 py-2 bg-purple-600 hover:bg-purple-500 rounded-lg font-medium transition"
            >
                重试
            </button>
        </div>
    );
}
