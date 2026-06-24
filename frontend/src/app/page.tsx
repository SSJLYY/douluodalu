'use client';

import { useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';

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
        <div className="min-h-screen flex items-center justify-center bg-gradient-to-b from-gray-900 via-purple-950 to-gray-900">
            <div className="w-full max-w-md p-8">
                <div className="text-center mb-8">
                    <h1 className="text-4xl font-bold bg-gradient-to-r from-yellow-400 via-orange-500 to-red-500 bg-clip-text text-transparent">
                        斗罗大陆
                    </h1>
                    <p className="text-gray-400 mt-2">放置传说 · Web版</p>
                </div>

                <div className="bg-gray-800/80 backdrop-blur rounded-2xl p-8 shadow-2xl border border-gray-700">
                    <div className="flex mb-6 bg-gray-700 rounded-lg p-1">
                        <button
                            onClick={() => setIsRegister(false)}
                            className={`flex-1 py-2 rounded-md text-sm font-medium transition ${
                                !isRegister ? 'bg-purple-600 text-white' : 'text-gray-400'
                            }`}
                        >
                            登录
                        </button>
                        <button
                            onClick={() => setIsRegister(true)}
                            className={`flex-1 py-2 rounded-md text-sm font-medium transition ${
                                isRegister ? 'bg-purple-600 text-white' : 'text-gray-400'
                            }`}
                        >
                            注册
                        </button>
                    </div>

                    {error && (
                        <div className="mb-4 p-3 bg-red-500/20 border border-red-500/50 rounded-lg text-red-300 text-sm">
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
                                className="w-full px-4 py-3 bg-gray-700 border border-gray-600 rounded-lg focus:outline-none focus:border-purple-500 text-white"
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
                                className="w-full px-4 py-3 bg-gray-700 border border-gray-600 rounded-lg focus:outline-none focus:border-purple-500 text-white"
                                placeholder="请输入密码"
                                required
                                minLength={6}
                            />
                        </div>

                        {isRegister && (
                            <div>
                                <label className="block text-sm text-gray-400 mb-1">昵称</label>
                                <input
                                    type="text"
                                    value={nickname}
                                    onChange={(e) => setNickname(e.target.value)}
                                    className="w-full px-4 py-3 bg-gray-700 border border-gray-600 rounded-lg focus:outline-none focus:border-purple-500 text-white"
                                    placeholder="游戏内显示名称"
                                    minLength={2}
                                />
                            </div>
                        )}

                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full py-3 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 rounded-lg font-medium transition disabled:opacity-50"
                        >
                            {loading ? '处理中...' : isRegister ? '注册并开始游戏' : '进入游戏'}
                        </button>
                    </form>
                </div>

                <p className="text-center text-gray-500 text-xs mt-6">
                    斗罗大陆·放置传说 Web版 v1.0
                </p>
            </div>
        </div>
    );
}
