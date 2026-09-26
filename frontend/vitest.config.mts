import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tsconfigPaths from 'vite-tsconfig-paths';

export default defineConfig({
    // tsconfigPaths：解析 tsconfig 的 @/* 别名；react：与 next build 一致的 JSX/快速刷新无关的 Babel 转换
    plugins: [tsconfigPaths(), react()],
    test: {
        environment: 'jsdom',
        // globals: true 让 @testing-library/react 自动注册 afterEach(cleanup)，避免用例间 DOM 串扰
        globals: true,
        // api.ts 的 API_BASE 在模块加载时固化（读 process.env.NEXT_PUBLIC_API_URL）；
        // 此处固定为兜底默认值，与本地开发/CI 的 Next 运行时注入解耦，URL 断言确定可复现
        env: {
            NEXT_PUBLIC_API_URL: 'http://localhost:8080',
        },
    },
});
