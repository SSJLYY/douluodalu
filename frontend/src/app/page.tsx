'use client';

import { useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import ThemeToggle from '@/components/ThemeToggle';

export default function LoginPage() {
    const { login, register, isLoading } = useAuth();
    const [isRegister, setIsRegister] = useState(false);
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [nickname, setNickname] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            if (isRegister) {
                await register(username, password, nickname || username);
            } else {
                await login(username, password);
            }
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : '操作失败');
        } finally {
            setLoading(false);
        }
    };

    if (isLoading) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <div className="text-xl animate-pulse">加载中...</div>
            </div>
        );
    }

    return (
        <div className="relative min-h-screen flex items-center justify-center overflow-hidden bg-gradient-to-b from-gray-900 via-purple-950 to-gray-900">
            {/* 氛围光斑（纯装饰，blur 圆形，两主题各自的彩阶变量自动适配） */}
            <div aria-hidden className="pointer-events-none absolute -top-24 -left-24 h-96 w-96 rounded-full bg-purple-500/20 blur-3xl dl-fade-in" />
            <div aria-hidden className="pointer-events-none absolute -bottom-32 -right-16 h-[26rem] w-[26rem] rounded-full bg-yellow-500/10 blur-3xl dl-fade-in [animation-delay:200ms]" />

            {/* 主题切换：右上角 */}
            <div className="absolute right-4 top-4 z-10 dl-fade-up">
                <ThemeToggle />
            </div>

            <div className="w-full max-w-md p-8 relative">
                <div className="text-center mb-8 dl-fade-up">
                    <h1 className="text-4xl font-bold bg-gradient-to-r from-yellow-400 via-orange-500 to-red-500 bg-clip-text text-transparent">
                        斗罗大陆
                    </h1>
                    <p className="text-gray-400 mt-2">放置传说 · Web版</p>
                </div>

                {/* 玻璃态卡片：半透明表面 + backdrop-blur + 渐变高光描边 */}
                <div className="dl-fade-up [animation-delay:120ms] relative rounded-2xl p-[1px] bg-gradient-to-b from-white/20 to-white/5 shadow-2xl">
                    <div className="bg-surface/70 dark:bg-surface/60 backdrop-blur-xl rounded-2xl p-8 border border-white/10">
                        <div className="flex mb-6 bg-surface-soft/80 rounded-lg p-1">
                            <button
                                onClick={() => setIsRegister(false)}
                                className={`flex-1 py-2 rounded-md text-sm font-medium transition-all duration-200 ${
                                    !isRegister ? 'bg-purple-600 shadow-md' : 'text-gray-400 hover:text-foreground'
                                }`}
                            >
                                登录
                            </button>
                            <button
                                onClick={() => setIsRegister(true)}
                                className={`flex-1 py-2 rounded-md text-sm font-medium transition-all duration-200 ${
                                    isRegister ? 'bg-purple-600 shadow-md' : 'text-gray-400 hover:text-foreground'
                                }`}
                            >
                                注册
                            </button>
                        </div>

                        {error && (
                            <div key={error} role="alert" className="mb-4 p-3 bg-red-500/20 border border-red-500/50 rounded-lg text-red-300 text-sm dl-shake">
                                {error}
                            </div>
                        )}

                        <form onSubmit={handleSubmit} className="space-y-4">
                            <div>
                                <label className="block text-sm text-gray-400 mb-1">用户名</label>
                                <input
                                    type="text"
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    className="w-full px-4 py-3 bg-gray-700/70 border border-gray-600 rounded-lg focus:outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/30 transition-shadow text-foreground"
                                    placeholder="请输入用户名"
                                    required
                                    minLength={3}
                                />
                            </div>

                            <div>
                                <label className="block text-sm text-gray-400 mb-1">密码</label>
                                <input
                                    type="password"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    className="w-full px-4 py-3 bg-gray-700/70 border border-gray-600 rounded-lg focus:outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/30 transition-shadow text-foreground"
                                    placeholder="请输入密码"
                                    required
                                    minLength={6}
                                />
                            </div>

                            {isRegister && (
                                <div className="dl-fade-up">
                                    <label className="block text-sm text-gray-400 mb-1">昵称</label>
                                    <input
                                        type="text"
                                        value={nickname}
                                        onChange={(e) => setNickname(e.target.value)}
                                        className="w-full px-4 py-3 bg-gray-700/70 border border-gray-600 rounded-lg focus:outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/30 transition-shadow text-foreground"
                                        placeholder="游戏内显示名称"
                                        minLength={2}
                                    />
                                </div>
                            )}

                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full py-3 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 rounded-lg font-medium transition disabled:opacity-60 disabled:cursor-not-allowed active:scale-[0.99]"
                            >
                                {loading ? (
                                    <span className="inline-flex items-center gap-2">
                                        <span aria-hidden className="dl-spinner" />
                                        处理中...
                                    </span>
                                ) : isRegister ? '注册并开始游戏' : '进入游戏'}
                            </button>
                        </form>
                    </div>
                </div>

                <p className="dl-fade-up [animation-delay:240ms] text-center text-gray-500 text-xs mt-6">
                    斗罗大陆·放置传说 Web版 v1.0
                </p>
            </div>
        </div>
    );
}
