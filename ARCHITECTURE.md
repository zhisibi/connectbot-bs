# BoshConnect 项目架构文档

> 基于 ConnectBot 的 Android SSH/SFTP 客户端，采用 Kotlin + Jetpack Compose 构建。

---

## 1. 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构模式 | MVVM (ViewModel + StateFlow) |
| 依赖注入 | Hilt (Dagger) |
| 数据库 | Room |
| 网络 | HttpURLConnection (云同步), JSch + sshlib (SSH/SFTP) |
| 序列化 | Gson |
| 加密 | PBKDF2 + AES-GCM (字段级) |
| 终端模拟 | ConnectBot termlib |
| 生物识别 | AndroidX Biometric |
| 异步 | Kotlin Coroutines + Flow |

---

## 2. 项目结构

```
app/src/main/java/
├── com/sbssh/                    # 自研模块
│   ├── data/
│   │   ├── crypto/
│   │   │   ├── CryptoManager.kt        # 密钥派生、salt 管理
│   │   │   ├── FieldCryptoManager.kt   # AES-GCM 字段级加解密
│   │   │   └── SessionKeyHolder.kt     # 内存中持有会话密钥
│   │   └── db/
│   │       ├── AppDatabase.kt          # Room 数据库
│   │       ├── VpsDao.kt               # VPS 数据访问
│   │       └── VpsEntity.kt            # VPS 实体
│   ├── di/
│   │   └── SbsshDatabaseModule.kt      # Hilt 注入
│   ├── terminal/                        # 终端扩展
│   ├── ui/
│   │   ├── auth/                        # 主密码登录
│   │   ├── cloud/
│   │   │   └── CloudSyncApi.kt         # 云同步 HTTP 客户端
│   │   ├── navigation/
│   │   │   └── NavGraph.kt             # 路由
│   │   ├── sftp/                        # SFTP 文件管理
│   │   ├── settings/                    # 设置（含备份/恢复/云同步）
│   │   ├── terminal/                    # SSH 终端
│   │   ├── theme/                       # Material 3 主题
│   │   └── vpslist/                     # 服务器列表
│   └── util/                            # 工具类
│
├── org/connectbot/              # ConnectBot 原始代码（魔改）
│   ├── data/                    # 数据层（Host/Key/Log）
│   ├── service/                 # 核心服务（TerminalManager/Bridge）
│   ├── transport/               # 传输层（SSH/Telnet）
│   └── ui/                      # 旧版 UI（部分仍在用）
│
└── de/mud/telnet/               # 第三方库
```

---

## 3. 分层架构

```
┌─────────────────────────────────────────┐
│                  UI Layer                │
│  Compose Screens + ViewModels + Nav     │
├─────────────────────────────────────────┤
│                Domain Layer              │
│  SettingsManager · CloudSyncApi · Crypto│
├─────────────────────────────────────────┤
│                 Data Layer               │
│  Room DB  ·  ConnectBot Service/SSH     │
├─────────────────────────────────────────┤
│                Crypto Layer              │
│  SessionKeyHolder → FieldCrypto → AES   │
│  CryptoManager → PBKDF2 → salt/password │
└─────────────────────────────────────────┘
```

---

## 4. 核心模块

### 4.1 认证与加密

**当前方案（v1.0.0）：**
```
主密码 + salt → PBKDF2 (100k) → 256-bit DEK
  ├── 加密 DB 字段（密码/私钥/口令）
  └── 加密备份数据
```

**计划方案（v1.1.0）：**
```
主密码 + 服务器salt → PBKDF2 → DEK → 加密 DB 字段 + 云同步
备份密码 + 本地salt → PBKDF2 → 备份密钥 → 加密本地/GitHub 备份
```

### 4.2 SSH/SFTP 连接

```
UI → ViewModel → TerminalManager → TerminalBridge → SSH Transport → JSch
```

### 4.3 数据存储

| 数据库 | 用途 | 表 |
|--------|------|-----|
| `sbssh.db` | VPS 管理 | `vps` |
| `connectbot.db` | ConnectBot | `hosts`, `pubkeys` 等 |

### 4.4 云同步（v1.0.0）

**SbSSH Server 同步：**
- 登录 → JWT → 上传/下载加密数据
- 端到端加密，服务端只存密文

### 4.5 备份（v1.0.0）

**BackupEnvelope 格式：**
```json
{
  "format": "sbssh_backup_v1",
  "encrypted": true,
  "payload": "<AES-GCM encrypted>",
  "salt": "<base64 PBKDF2 salt>",
  "createdAt": 1712083200000
}
```

---

## 5. 备份与同步体系（v1.1.0 规划）

### 5.1 三种备份通道

| 通道 | 说明 | 加密密钥 |
|------|------|----------|
| SbSSH Server | 自建 FastAPI 服务器 | 主密码 + 服务器 salt |
| GitHub | 私有仓库，PAT 认证 | 备份密码 + 本地 salt |
| 本地文件 | 导出到 Downloads | 备份密码 + 本地 salt |

### 5.2 密钥体系

```
┌─ 主密码 ─→ 服务器 salt ─→ 云同步密钥 ─→ 加密/解密云数据
│
└─ 备份密码 ─→ 本地 salt ─→ 备份密钥 ─→ 加密/解密本地+GitHub 备份
```

**特点：**
- 两个密码独立，用户可相同可不同
- 云同步密钥确保多端互通
- 备份密码确保跨设备恢复

### 5.3 GitHub 备份流程

```
备份：
  1. 序列化 VPS 列表为 JSON
  2. 备份密码 + salt → PBKDF2 → 备份密钥
  3. AES-256-GCM 加密
  4. PUT /repos/{owner}/{repo}/contents/boshconnect/backup_YYYYMMDD_HHMMSS.enc

恢复：
  1. GET /repos/{owner}/{repo}/contents/{path}
  2. 输入备份密码
  3. 派生密钥 → 解密 → 写入本地 DB
```

---

## 6. 导航结构

```
Auth (主密码) ─→ VPS List
                   ├→ Add/Edit VPS
                   ├→ Terminal (SSH)
                   ├→ SFTP
                   └→ Settings
                        ├→ Cloud Sync (SbSSH Server)
                        ├→ GitHub Backup
                        ├→ Local Backup
                        ├→ Biometric
                        └→ Language / Font Size
```

---

## 7. 构建配置

| 项 | 值 |
|----|-----|
| AGP | 8.9.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.0.21 |
| compileSdk | 36 |
| targetSdk | 35 |
| minSdk | 24 |
| JDK | 21 |
| 签名 | keystore/boshconnect.jks |
| APK | `app/build/outputs/apk/release/boshconnect-release.apk` |

---

## 8. 安全模型

```
主密码 → PBKDF2 → DEK → AES-256-GCM → DB 字段 + 云数据
备份密码 → PBKDF2 → BackupKey → AES-256-GCM → 本地文件 + GitHub

传输：SSH v2 / HTTPS + JWT
```

---

## 9. 版本规划

| 版本 | 内容 |
|------|------|
| v1.0.0 | 基础功能：SSH/SFTP/加密/云同步/备份 |
| v1.1.0 | 独立备份密码 + GitHub 备份 + 设置页改造 |
