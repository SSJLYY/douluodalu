'use client';

import { AuthProvider } from '@/contexts/AuthContext';
import './globals.css';

/**
 * 防闪烁主题初始化：首屏 HTML 解析时立即根据 localStorage/系统偏好给 <html> 挂 .dark，
 * 早于任何绘制与 hydration，避免亮/暗主题闪屏（与 ThemeToggle 的 readStoredMode 逻辑一致）。
 * 支持 ?theme=dark|light|system 临时覆盖（不持久化，便于截图验证与调试）。
 */
const THEME_INIT_SCRIPT = `(function(){try{var q=new URLSearchParams(location.search).get('theme');var t=q==='dark'||q==='light'||q==='system'?q:(localStorage.getItem('dl-theme')||'dark');var d=t==='dark'||(t==='system'&&window.matchMedia('(prefers-color-scheme: dark)').matches);var e=document.documentElement;e.classList.toggle('dark',d);e.style.colorScheme=d?'dark':'light';}catch(err){}})();`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
    return (
        <html lang="zh-CN" suppressHydrationWarning>
            <head>
                <title>斗罗大陆·放置传说</title>
                <meta name="description" content="斗罗大陆放置类RPG网页游戏" />
                <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
            </head>
            <body className="bg-background text-foreground min-h-screen">
                <AuthProvider>
                    {children}
                </AuthProvider>
            </body>
        </html>
    );
}
