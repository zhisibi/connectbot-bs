# SbSSH — Android VPS SSH/SFTP 管理工具

一款基于 ConnectBot 的 Android 原生 SSH/SFTP 客户端，支持本地加密存储、云同步、生物识别解锁。

## 核心功能

### 🔐 安全架构
- **主密码 + PBKDF2**：首次启动设置主密码，PBKDF2（100,000 次迭代）派生 256 位会话密钥
- **AES-GCM 字段级加密**：密码、私钥、密钥口令等敏感字段加密存储
- **生物识别解锁**：可选指纹/面部识别作为便捷解锁方式
- **SessionKeyHolder**：会话密钥仅驻留内存，不持久化明文

### 🖥️ SSH 终端
- 基于 ConnectBot terminal 内核
- 快捷键栏常驻（Ctrl、Esc、Tab、↑↓←→、Home、End、PgUp/PgDn 等）
- 终端内容自动上移避免光标被键盘遮挡
- 密码/密钥认证

### 📁 SFTP 文件管理器
- 远程文件浏览、上传、下载、删除、重命名、权限修改
- 下载进度实时显示 + 取消下载
- 下载管理器（角标 + 进度条 + 文件大小）
- Android 10+ 兼容（MediaStore.Downloads API）

### ☁️ 云同步（E2EE）
- 注册/登录云账号（FastAPI 后端）
- 端到端加密：服务器仅存密文
- 上传/下载加密备份数据
- 自动同步开关：本地增删操作自动触发同步
- Smart Sync：增量合并，云端多余数据可选择保留或删除
- 恢复时自动去重（按 host:port:username 合并，覆盖已有数据）

### 💾 备份/恢复
- 加密备份导出到 Downloads
- 从备份文件恢复（首次点击即可触发）
- 恢复自动去重：同服务器不重复添加，覆盖已有配置
- 云端下载恢复同样支持自动去重

### 🔧 其他
- 服务器列表管理（增删改查）
- 多服务器连接状态实时显示
- 语言切换（中文/英文）
- 字体大小调整
- Debug Log 查看
- 应用退出时自动断开所有 SSH 连接

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Hilt 依赖注入 |
| 数据库 | Room (SQLite) |
| SSH | JSch + ConnectBot sshlib |
| 终端 | ConnectBot termlib |
| 加密 | PBKDF2 + AES-GCM |
| 云同步后端 | Python FastAPI + SQLite + JWT |

## 构建环境

- Android Gradle Plugin: 8.6.0
- Kotlin: 2.0.21
- Compose Compiler: via `kotlin.plugin.compose`
- compileSdk: 36, targetSdk: 35, minSdk: 24
- JDK: 21
- Build Tools: 35.0.0（需离线安装，见下方说明）

### 国内构建注意事项

服务器网络无法直连 `dl.google.com`，依赖已配置阿里云 Maven 镜像：

```kotlin
// settings.gradle.kts
maven("https://maven.aliyun.com/repository/google")
maven("https://maven.aliyun.com/repository/public")
```

Build Tools 35.0.0 需要离线安装到 `/opt/android-sdk/build-tools/35.0.0/`。

### 编译

```bash
./gradlew :app:assembleDebug --no-daemon
```

Release 构建需跳过 `checkReleaseClasspath`（AGP 8.6.0 兼容问题，已配置）：

```bash
./gradlew :app:assembleRelease --no-daemon
```

## 云同步服务端

路径：`../sbssh-cloud-server/`

### 启动

```bash
cd sbssh-cloud-server
pip install -r requirements.txt
python3 server.py
```

默认端口：`9800`

### API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/api/v1/register` | 注册（username, password, encryptedSalt） |
| POST | `/api/v1/login` | 登录，返回 JWT |
| POST | `/api/v1/sync/upload` | 上传加密数据（需 Bearer Token） |
| GET | `/api/v1/sync/download` | 下载加密数据（需 Bearer Token） |
| GET | `/api/v1/user/info` | 用户信息（需 Bearer Token） |

### 数据安全

- 用户密码：PBKDF2 哈希存储
- 同步数据：客户端 AES-GCM 加密后上传，服务端仅存密文
- PBKDF2 Salt：注册时上传，新设备登录后获取可派生相同密钥
- 通信：建议部署时使用 HTTPS

## 已知问题

- SSH 终端输入体验有改进空间（IME 遮挡、光标同步等）
- 部分 deprecated API warnings（Icons.Filled、LocalClipboardManager 等，不影响功能）

## License

基于 ConnectBot（Apache License 2.0）。
