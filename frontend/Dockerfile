# 构建上下文是仓库根目录，所以这里的 COPY 路径都带 frontend/ 前缀。
# docker-compose 里通过 build.context = . 和 build.dockerfile 指定。

# ---- 构建阶段：把 Vue 打包成静态文件 ----
FROM node:22-alpine AS builder
WORKDIR /build

# 先只复制依赖清单，利用 Docker 层缓存，改业务代码时不必重装依赖
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ---- 运行阶段：Nginx 托管静态文件并反代接口 ----
FROM nginx:1.27-alpine
COPY --from=builder /build/dist /usr/share/nginx/html
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
