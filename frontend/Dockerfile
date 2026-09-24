# syntax=docker/dockerfile:1
# ==================== Stage 1: build ====================
FROM node:22-alpine AS build
WORKDIR /app

# 依赖层缓存：lockfile 不变则不重装
COPY package.json package-lock.json ./
RUN npm ci

# NEXT_PUBLIC_* 是构建时内联变量（见 README/DEPLOY.md），必须在 build 前注入
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

COPY . .
# package.json build 已固定 `next build --webpack`，勿改回 Turbopack
RUN npm run build

# ==================== Stage 2: runtime ====================
# next.config.ts 已开启 output:"standalone"，只拷自包含产物，无需 node_modules
FROM node:22-alpine
ENV NODE_ENV=production \
    HOSTNAME=0.0.0.0 \
    PORT=3000
WORKDIR /app

COPY --from=build /app/.next/standalone ./
# standalone 不自动包含 static 与 public，需手动补齐（server.js 会自动服务它们）
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

# node 镜像自带非 root 的 node 用户
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD wget -qO /dev/null http://127.0.0.1:3000/ || exit 1

CMD ["node", "server.js"]
