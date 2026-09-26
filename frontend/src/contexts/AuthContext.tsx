'use client';

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import api, { UserInfo, UNAUTHORIZED_EVENT } from '@/lib/api';
import { useRouter } from 'next/navigation';

interface AuthContextType {
    user: UserInfo | null;
    isLoading: boolean;
    /** localStorage token 存在性校验是否已完成（hydration 安全：SSR 与首帧客户端渲染同为 false） */
    authChecked: boolean;
    login: (username: string, password: string) => Promise<void>;
    register: (username: string, password: string, nickname: string) => Promise<void>;
    logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<UserInfo | null>(null);
    // isLoading 初值必须与 SSR 一致（false）：token 只存在于 localStorage，若在 useState 惰性初始化里读取，
    // 带 token 的客户端首帧会渲染「加载中...」而服务端渲染登录表单 → hydration mismatch（React 19 报错）。
    // token 校验挪到 effect 内置位 isLoading，首帧后仅多一帧表单闪现，换来零 hydration 错误。
    const [isLoading, setIsLoading] = useState(false);
    // token 存在性校验完成标记：/game 布局用它区分「还没查完 token」与「确实没登录」，
    // 避免修复 isLoading 初值后已登录用户在 getMe 返回前被误判未登录而踢回登录页。
    const [authChecked, setAuthChecked] = useState(false);
    const router = useRouter();

    useEffect(() => {
        // localStorage 读取与置位放入微任务：react-hooks/set-state-in-effect 禁止在 effect 同步段内
        // setState；微任务先于下一次绘制排空，用户无感知差异（带 token 访登录页仅多一帧表单闪现）。
        Promise.resolve().then(() => {
            const token = localStorage.getItem('token');
            if (!token) {
                setAuthChecked(true);
                return;
            }
            setIsLoading(true);
            api.getMe()
                .then(setUser)
                .catch(() => {
                    api.setToken(null);
                })
                .finally(() => {
                    setIsLoading(false);
                    setAuthChecked(true);
                });
        });
    }, []);

    // api.ts 检测到 401/403 时派发该事件，这里统一做软跳转（替代 window.location 硬跳）
    useEffect(() => {
        const onUnauthorized = () => {
            setUser(null);
            router.push('/');
        };
        window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
        return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    }, [router]);

    const login = async (username: string, password: string) => {
        const res = await api.login(username, password);
        api.setToken(res.token);
        setUser({ userId: res.userId, username: res.username, nickname: res.nickname, avatarUrl: null });
        router.push('/game');
    };

    const register = async (username: string, password: string, nickname: string) => {
        const res = await api.register(username, password, nickname);
        api.setToken(res.token);
        setUser({ userId: res.userId, username: res.username, nickname: res.nickname, avatarUrl: null });
        router.push('/game');
    };

    const logout = () => {
        // 通知后端拉黑 token 并记录登出时间（离线收益起算点），失败不阻塞本地登出
        api.logout().catch(() => undefined);
        api.setToken(null);
        setUser(null);
        router.push('/');
    };

    return (
        <AuthContext.Provider value={{ user, isLoading, authChecked, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used within AuthProvider');
    return ctx;
}
