# Rocky Linux 9 部署教程

从零开始在 Rocky 9 服务器上部署「斗罗大陆·放置传说」。

## 目录

1. [服务器基础配置](#1-服务器基础配置)
2. [安装 Node.js 22](#2-安装-nodejs-22)
3. [安装 Git 并拉取项目](#3-安装-git-并拉取项目)
4. [安装 PM2 进程管理](#4-安装-pm2-进程管理)
5. [开放防火墙端口](#5-开放防火墙端口)
6. [构建并启动项目](#6-构建并启动项目)
7. [配置 Nginx 反向代理](#7-配置-nginx-反向代理)
8. [配置 SSL 证书 (HTTPS)](#8-配置-ssl-证书-https)
9. [设置开机自启](#9-设置开机自启)
10. [常用运维命令](#10-常用运维命令)

---

## 1. 服务器基础配置

### 1.1 更新系统

```bash
# 更新所有软件包
dnf update -y

# 安装基础工具
dnf install -y curl wget vim tar gzip
```

### 1.2 设置时区

```bash
timedatectl set-timezone Asia/Shanghai
timedatectl status
```

### 1.3 创建应用目录

```bash
mkdir -p /opt/app
```

---

## 2. 安装 Node.js 22

Rocky 9 默认仓库中的 Node.js 版本较旧，使用 NodeSource 官方仓库安装 v22 LTS。

```bash
# 导入 NodeSource GPG 密钥
rpm --import https://rpm.nodesource.com/gpgkey/NODESOURCE-GPG-SIGNING-KEY-EL

# 添加 NodeSource v22 仓库
curl -fsSL https://rpm.nodesource.com/setup_22.x | bash -

# 安装 Node.js
dnf install -y nodejs

# 验证安装
node --version
npm --version
```

预期输出类似：
```
v22.14.0
10.9.2
```

---

## 3. 安装 Git 并拉取项目

```bash
# 安装 Git
dnf install -y git

# 克隆项目
cd /opt/app
git clone https://github.com/SSJLYY/douluodalu.git

# 进入前端目录
cd /opt/app/douluodalu/frontend
```

---

## 4. 安装 PM2 进程管理

PM2 用于守护 Node.js 进程，提供自动重启、日志管理和开机自启。

```bash
# 全局安装 PM2
npm install -g pm2

# 验证安装
pm2 --version
```

---

## 5. 开放防火墙端口

Rocky 9 默认使用 `firewalld` 管理防火墙。

```bash
# 检查防火墙状态
systemctl status firewalld

# 开放 HTTP (80) 和 HTTPS (443) 端口
firewall-cmd --permanent --add-service=http
firewall-cmd --permanent --add-service=https

# 如果直接暴露 Next.js 端口 (3000)，也可以单独开放
# firewall-cmd --permanent --add-port=3000/tcp

# 重载防火墙使配置生效
firewall-cmd --reload

# 查看已开放规则
firewall-cmd --list-all
```

如果使用云服务器（阿里云/腾讯云等），还需在云控制台的**安全组**中放行 80、443 端口。

---

## 6. 构建并启动项目

```bash
cd /opt/app/douluodalu/frontend

# 安装依赖
npm install

# 生产构建
npm run build

# 使用 PM2 启动
pm2 start npm --name "douluo" -- start

# 保存 PM2 进程列表
pm2 save

# 查看运行状态
pm2 status

# 查看日志
pm2 logs douluo
```

应用启动后监听 `http://localhost:3000`。

---

## 7. 配置 Nginx 反向代理

### 7.1 安装 Nginx

```bash
dnf install -y nginx

# 启动并设置开机自启
systemctl enable nginx
systemctl start nginx
```

### 7.2 配置站点

```bash
vim /etc/nginx/conf.d/douluo.conf
```

写入以下配置（将 `your-domain.com` 替换为实际域名）：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 日志
    access_log /var/log/nginx/douluo_access.log;
    error_log /var/log/nginx/douluo_error.log;

    # 反向代理到 Next.js
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }

    # 静态资源缓存
    location /_next/static {
        proxy_pass http://127.0.0.1:3000;
        proxy_cache_valid 200 1d;
        add_header Cache-Control "public, max-age=86400";
    }
}
```

### 7.3 验证并重载配置

```bash
# 检查配置语法
nginx -t

# 重载 Nginx
systemctl reload nginx
```

完成后通过 `http://your-domain.com` 即可访问。

---

## 8. 配置 SSL 证书 (HTTPS)

使用 Let's Encrypt 免费证书。

### 8.1 安装 Certbot

```bash
dnf install -y epel-release
dnf install -y certbot python3-certbot-nginx
```

### 8.2 申请证书

```bash
# 将 your-domain.com 替换为实际域名
certbot --nginx -d your-domain.com

# 按提示输入邮箱并同意条款
```

Certbot 会自动修改 Nginx 配置，添加 HTTPS 重定向。

### 8.3 验证自动续期

```bash
# 测试证书自动续期（不会真正续期）
certbot renew --dry-run
```

Certbot 会通过 systemd timer 自动续期，无需手动操作。可以确认 timer 状态：

```bash
systemctl status certbot-renew.timer
```

---

## 9. 设置开机自启

```bash
# PM2 生成启动脚本
pm2 startup

# 执行上述命令输出的指令（会提示复制粘贴一条 systemd 命令）
# 例如：
# env PATH=$PATH:/usr/bin pm2 startup systemd -u root --hp /root

# 保存当前进程列表
pm2 save
```

验证：

```bash
# 查看 PM2 是否已注册为 systemd 服务
systemctl status pm2-root
```

---

## 10. 常用运维命令

### PM2 管理

```bash
pm2 status                # 查看所有应用状态
pm2 logs douluo           # 查看日志
pm2 restart douluo        # 重启应用
pm2 stop douluo           # 停止应用
pm2 delete douluo         # 删除应用

# 更新代码后重新部署
cd /opt/app/douluodalu
git pull
cd frontend
npm install
npm run build
pm2 restart douluo
```

### Nginx 管理

```bash
systemctl status nginx    # 查看状态
systemctl reload nginx    # 重载配置（不中断服务）
systemctl restart nginx   # 重启
nginx -t                  # 检查配置语法
```

### 查看日志

```bash
# Nginx 访问日志
tail -f /var/log/nginx/douluo_access.log

# Nginx 错误日志
tail -f /var/log/nginx/douluo_error.log

# PM2 应用日志
pm2 logs douluo --lines 100
```

### 数据库备份

```bash
# 备份 SQLite 数据库
cp /opt/app/douluodalu/frontend/data/app.db \
   /opt/app/douluodalu/frontend/data/app.db.$(date +%Y%m%d).bak
```

### 防火墙管理

```bash
firewall-cmd --list-all     # 查看当前规则
firewall-cmd --reload       # 重载规则
```
