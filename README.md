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

**云同步**（SbSSH Server）
- 注册/登录 → JWT 认证
- 端到端加密：服务端只存密文
- 自动同步开关（增删服务器自动触发）
- Smart Sync：增量合并，云端多余数据可选保留或删除
- 恢复自动去重（按 host:port:username 合并覆盖）

**备份与恢复**
- 本地加密备份导出 & 恢复（备份密码独立于云同步密码）
- 支持多设备恢复（备份文件内嵌 salt）
- ⏳ v1.1.0: GitHub 私有仓库备份（PAT 认证）

**设置**
- 语言切换（中文 / 英文）
- 字体大小
- 生物识别开关
- 云同步管理
- 备份密码设置
- Debug Log

## 技术栈

- **客户端**：Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Room · JSch · ConnectBot sshlib · PBKDF2 · AES-GCM
- **服务端**：Python FastAPI · SQLite · JWT（详见 [cloud-server/](cloud-server/)）

## 构建

```bash
# Debug
./gradlew :app:assembleDebug --no-daemon

# Release（需要 keystore/boshconnect.jks）
./gradlew :app:assembleRelease --no-daemon
```

**环境要求**：JDK 21 · compileSdk 36 · Build Tools 35.0.0

**APK 输出**：`app/build/outputs/apk/release/boshconnect-release.apk`

## 云同步服务端

服务端代码位于 [cloud-server/](cloud-server/) 目录。

```bash
cd cloud-server
pip install -r requirements.txt
python3 server.py   # :9800
```

### API 接口

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/register` | POST | 用户注册 |
| `/api/v1/login` | POST | 登录 → JWT |
| `/api/v1/sync/salt` | GET | 获取 salt（多端同步） |
| `/api/v1/sync/upload` | POST | 上传加密数据 |
| `/api/v1/sync/download` | GET | 下载加密数据 |
| `/health` | GET | 健康检查 |

### 相关文件

| 文件 | 说明 |
|------|------|
| [cloud-server/server.py](cloud-server/server.py) | FastAPI 服务端主程序 |
| [cloud-server/requirements.txt](cloud-server/requirements.txt) | Python 依赖 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 项目架构文档 |

## License

Apache 2.0（基于 [ConnectBot](https://github.com/connectbot/connectbot)）
