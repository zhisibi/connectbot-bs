# BoshConnect

> 基于 ConnectBot 的 Android SSH/SFTP 客户端。本地加密存储，云端 E2EE 同步。

## 功能一览

**SSH 终端**
- 快捷键栏常驻（Ctrl / Esc / Tab / 方向键 / 功能键）
- 密码 & 密钥认证，光标自动避让键盘
- 退出时自动断开所有连接

**SFTP 文件管理**
- 远程目录浏览、上传、下载、删除、重命名、权限修改
- 下载管理器：进度条 + 实时字节 + 取消下载
- Android 10+ 分区存储兼容（MediaStore API）

**安全**
- 主密码 + PBKDF2（100k 迭代）→ 256 位密钥
- 敏感字段 AES-GCM 字段级加密（密码、私钥、口令）
- 生物识别便捷解锁
- 密钥仅驻留内存，不落盘明文

**云同步**
- 注册/登录 → JWT 认证
- 端到端加密：服务端只存密文
- 自动同步开关（增删服务器自动触发）
- Smart Sync：增量合并，云端多余数据可选保留或删除
- 恢复自动去重（按 host:port:username 合并覆盖）

**备份/恢复**
- 加密备份导出 & 恢复（首次点击即触发）
- 本地恢复 & 云端下载均支持自动去重覆盖

**设置**
- 语言切换（中文 / 英文）
- 字体大小
- 生物识别开关
- 云同步管理
- Debug Log

## 技术栈

Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Room · JSch · ConnectBot sshlib · PBKDF2 · AES-GCM

云同步后端：Python FastAPI · SQLite · JWT

## 构建

```bash
# Debug
./gradlew :app:assembleDebug --no-daemon

# Release
./gradlew :app:assembleRelease --no-daemon
```

**环境要求**：JDK 21 · compileSdk 36 · Build Tools 35.0.0

**APK 输出**：`app/build/outputs/apk/debug/boshconnect-debug.apk`

## 云同步服务端

```bash
cd sbssh-cloud-server
pip install -r requirements.txt
python3 server.py   # 启动 :9800
```

| 端点 | 说明 |
|------|------|
| `POST /api/v1/register` | 注册 |
| `POST /api/v1/login` | 登录 → JWT |
| `POST /api/v1/sync/upload` | 上传加密数据 |
| `GET  /api/v1/sync/download` | 下载加密数据 |
| `GET  /health` | 健康检查 |

## License

Apache 2.0（基于 ConnectBot）
