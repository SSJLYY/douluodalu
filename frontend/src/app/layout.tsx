'use client';

import { AuthProvider } from '@/contexts/AuthContext';
import './globals.css';

export default function RootLayout({ children }: { children: React.ReactNode }) {
    return (
        <html lang="zh-CN">
            <head>
                <title>斗罗大陆·放置传说</title>
                <meta name="description" content="斗罗大陆放置类RPG网页游戏" />
            </head>
            <body className="bg-gray-900 text-white min-h-screen">
                <AuthProvider>
                    {children}
                </AuthProvider>
            </body>
        </html>
    );
}
