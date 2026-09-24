import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  allowedDevOrigins: ["*.monkeycode-ai.online"],
  // Docker 部署用：生成 .next/standalone 自包含产物（见 frontend/Dockerfile）
  output: "standalone",
  // 上方 turbopack.root 被设为盘符根（Windows 兼容），会让 standalone 文件追踪
  // 以盘符为根导致产物嵌套一层目录；这里显式把追踪根钉在项目目录。
  outputFileTracingRoot: __dirname,
  turbopack: {
    root: path.parse(path.resolve(__dirname)).root,
  },
};

export default nextConfig;
