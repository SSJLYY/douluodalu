'use client';

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import api, { UserInfo } from '@/lib/api';
import { useRouter } from 'next/navigation';

interface AuthContextType {
    user: UserInfo | null;
    isLoading: boolean;
    login: (username: string, password: string) => Promise<void>;
    register: (username: string, password: string, nickname: string) => Promise<void>;
    logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<UserInfo | null>(null);
    const [isLoading, setIsLoading] = useState(() => typeof window !== 'undefined' && Boolean(localStorage.getItem('token')));
    const router = useRouter();

    useEffect(() => {
        const token = localStorage.getItem('token');
        if (token) {
            api.getMe()
                .then(setUser)
                .catch(() => {
                    api.setToken(null);
                })
                .finally(() => setIsLoading(false));
        }
    }, []);

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
        api.setToken(null);
        setUser(null);
        router.push('/');
    };

    return (
        <AuthContext.Provider value={{ user, isLoading, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used within AuthProvider');
    return ctx;
}
