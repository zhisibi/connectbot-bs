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
├── com/sbssh/                    # 新增模块（自研代码）
│   ├── data/
│   │   ├── crypto/               # 加密核心
│   │   │   ├── CryptoManager.kt        # 主密码管理、PBKDF2 密钥派生、salt 管理
│   │   │   ├── FieldCryptoManager.kt   # AES-GCM 字段级加解密
│   │   │   └── SessionKeyHolder.kt     # 内存中持有当前会话密钥（单例）
│   │   └── db/
│   │       ├── AppDatabase.kt          # Room 数据库（VPS 服务器表）
│   │       ├── VpsDao.kt               # VPS 数据访问
│   │       └── VpsEntity.kt            # VPS 实体定义
│   ├── di/
│   │   └── SbsshDatabaseModule.kt      # Hilt 数据库注入模块
│   ├── terminal/                        # 终端相关扩展
│   ├── ui/
│   │   ├── auth/                        # 主密码登录界面
│   │   ├── cloud/
│   │   │   └── CloudSyncApi.kt         # 云端同步 HTTP 客户端
│   │   ├── components/                  # 通用 UI 组件
│   │   ├── navigation/
│   │   │   └── NavGraph.kt             # Compose Navigation 路由图
│   │   ├── sftp/
│   │   │   └── SftpScreen.kt           # SFTP 文件管理界面
│   │   ├── settings/
│   │   │   ├── SettingsManager.kt      # SharedPreferences 设置管理
│   │   │   ├── SettingsScreen.kt       # 设置界面
│   │   │   └── SettingsViewModel.kt    # 设置逻辑（含备份/恢复/云同步）
│   │   ├── terminal/
│   │   │   └── TerminalScreen.kt       # SSH 终端界面
│   │   ├── theme/                       # Material 3 主题
│   │   └── vpslist/
│   │       ├── VpsListScreen.kt        # 服务器列表
│   │       └── AddEditVpsScreen.kt     # 添加/编辑服务器
│   └── util/
│       ├── AppLogger.kt                # 本地日志
│       └── BiometricHelper.kt          # 生物识别工具
│
├── org/connectbot/              # ConnectBot 原始代码（魔改）
│   ├── data/
│   │   ├── dao/                        # Room DAO（Host, Pubkey 等）
│   │   ├── entity/                     # 数据实体
│   │   ├── ConnectBotDatabase.kt       # ConnectBot 数据库
│   │   ├── HostRepository.kt           # Host 数据仓库
│   │   └── migration/                  # 数据库迁移
│   ├── service/
│   │   ├── TerminalManager.kt          # 终端管理器（核心服务）
│   │   ├── TerminalBridge.kt           # SSH 连接桥
│   │   ├── TerminalKeyListener.kt      # 按键处理
│   │   ├── Relay.kt                    # 数据中继
│   │   └── ConnectionNotifier.kt       # 通知管理
│   ├── transport/
│   │   ├── SSH.kt                      # SSH 传输层
│   │   ├── Telnet.kt                   # Telnet 传输
│   │   └── TransportFactory.kt         # 传输工厂
│   ├── ui/
│   │   ├── screens/console/            # 旧版终端界面（仍在使用）
│   │   ├── screens/hostlist/           # 主机列表（旧版）
│   │   └── navigation/                 # 旧版导航
│   └── util/                           # 工具类
│
├── de/mud/telnet/               # Telnet 库（第三方）
├── org/apache/harmony/niochar/  # 字符编码（第三方）
└── org/keyczar/jce/             # JCE 提供者（第三方）
```

---

## 3. 分层架构

```
┌─────────────────────────────────────────────────┐
│                  UI Layer                        │
│  Compose Screens + ViewModels + Navigation      │
│  ┌──────────┬──────────┬──────────┬───────────┐ │
│  │ Auth     │ VPS List │ Terminal │ SFTP      │ │
│  │ Screen   │ Screen   │ Screen   │ Screen    │ │
│  └──────────┴──────────┴──────────┴───────────┘ │
├─────────────────────────────────────────────────┤
│                Domain Layer                      │
│  SettingsManager · CloudSyncApi · CryptoManager │
├─────────────────────────────────────────────────┤
│                 Data Layer                       │
│  ┌──────────────┐  ┌─────────────────────────┐  │
│  │ Room DB      │  │ ConnectBot Service       │  │
│  │ AppDatabase  │  │ TerminalManager          │  │
│  │ VpsDao       │  │ TerminalBridge           │  │
│  │ ConnectBotDB │  │ SSH Transport            │  │
│  └──────────────┘  └─────────────────────────┘  │
├─────────────────────────────────────────────────┤
│                Crypto Layer                      │
│  SessionKeyHolder → FieldCryptoManager → AES    │
│  CryptoManager → PBKDF2 → salt + password       │
└─────────────────────────────────────────────────┘
```

---

## 4. 核心模块详解

### 4.1 认证与加密

**密钥派生流程：**
```
用户密码 + salt (32B) 
    → PBKDF2WithHmacSHA256 (100k iterations)
    → 256-bit 密钥
    → 存入 SessionKeyHolder（内存）
```

**字段级加密：**
- 密码、私钥、口令等敏感字段单独 AES-GCM 加密
- 存储在 Room 数据库的 `VpsEntity` 中
- `FieldCryptoManager.encrypt()/decrypt()` 处理

**多端密钥一致：**
- 注册时 salt 上传到服务器（base64）
- 登录时从 `/api/v1/sync/salt` 获取 salt
- 所有设备使用同一个 salt → 同一个密钥

### 4.2 SSH/SFTP 连接

**连接链路：**
```
UI (TerminalScreen)
  → ViewModel
    → TerminalManager (Service)
      → TerminalBridge
        → SSH Transport (connectbot sshlib)
          → JSch 底层
```

**SFTP 实现：**
- 使用 JSch `ChannelSftp` 直接操作
- 独立于 ConnectBot 的终端通道
- 支持上传、下载、删除、重命名、权限修改

### 4.3 数据存储

**双数据库架构：**

| 数据库 | 用途 | 表 |
|--------|------|-----|
| `sbssh.db` (AppDatabase) | VPS 服务器管理 | `vps` |
| `connectbot.db` | ConnectBot Host/Key/Log | `hosts`, `pubkeys`, `hostcolor`, `knownhosts` |

**VpsEntity 字段：**
```kotlin
id, alias, host, port, username, authType,
encryptedPassword,    // AES-GCM 加密
encryptedKeyContent,  // AES-GCM 加密
encryptedKeyPassphrase, // AES-GCM 加密
createdAt, updatedAt
```

### 4.4 云同步

**同步流程（Upload）：**
```
1. 读取本地 VPS 列表
2. 序列化为 JSON
3. 用 SessionKey AES-GCM 加密
4. 包装为 BackupEnvelope（含 salt）
5. POST /api/v1/sync/upload
```

**同步流程（Download）：**
```
1. GET /api/v1/sync/download
2. 解析 BackupEnvelope
3. 用 SessionKey 解密
4. 反序列化为 BackupItem 列表
5. 按 host:port:username 去重合并
6. 写入本地数据库
```

**Smart Sync：**
- 先下载云端数据，对比本地
- 如云端有多余服务器，询问用户
- 增量合并或全量替换

### 4.5 备份恢复

**BackupEnvelope 格式：**
```json
{
  "format": "sbssh_backup_v1",
  "encrypted": true,
  "payload": "<AES-GCM encrypted JSON>",
  "salt": "<base64 PBKDF2 salt>",
  "createdAt": 1712083200000
}
```

**跨设备恢复：**
- 备份文件包含 salt
- 恢复时检测 salt 差异
- 弹出密码输入框，用备份 salt + 密码派生密钥
- 解密并写入数据库

---

## 5. 导航结构

```
Auth (主密码)
  └→ VPS List (服务器列表)
       ├→ Add/Edit VPS
       ├→ Terminal (SSH 终端)
       │    └→ Console (ConnectBot 旧终端)
       ├→ SFTP (文件管理)
       └→ Settings (设置)
            ├→ Language / Font Size
            ├→ Biometric
            ├→ Cloud Sync (注册/登录/同步)
            ├→ Backup / Restore
            ├→ Change Password
            └→ Log Viewer
```

---

## 6. 依赖关系

```
┌─────────────┐
│    Hilt     │ ← 依赖注入
└──────┬──────┘
       │
┌──────▼──────┐     ┌──────────────┐
│ AppDatabase │────→│ VpsDao       │
└──────┬──────┘     └──────────────┘
       │
┌──────▼──────────┐  ┌────────────────┐
│ TerminalManager │─→│ SSH Transport  │
└──────┬──────────┘  └────────────────┘
       │
┌──────▼──────────┐  ┌────────────────┐
│ SettingsManager │─→│ CloudSyncApi   │
└─────────────────┘  └────────────────┘

┌─────────────────┐
│ CryptoManager   │ ← 被多个模块依赖
│ FieldCrypto     │
│ SessionKeyHolder│
└─────────────────┘
```

---

## 7. 构建配置

| 项 | 值 |
|----|-----|
| AGP | 8.9.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| compileSdk | 36 |
| targetSdk | 35 |
| minSdk | 24 |
| JDK | 21 |
| APK 输出 | `app/build/outputs/apk/debug/boshconnect-debug.apk` |

**⚠️ 注意：** 不要升级 Kotlin 到 2.3.0+，termlib 不兼容。

---

## 8. 外部服务

| 服务 | 协议 | 说明 |
|------|------|------|
| SSH Server | SSH v2 | 用户自己的 VPS |
| Cloud Sync | HTTP REST | FastAPI + SQLite，端口 9800 |
| Cloud API | JWT Bearer | 30 天有效期 |

**云同步 API 端点：**
- `POST /api/v1/register` — 注册
- `POST /api/v1/login` — 登录
- `GET  /api/v1/sync/salt?username=` — 获取 salt
- `POST /api/v1/sync/upload` — 上传
- `GET  /api/v1/sync/download` — 下载
- `GET  /health` — 健康检查

---

## 9. 安全模型

```
┌─────────────────────────────────────────┐
│ Master Password                         │
│   ↓ PBKDF2 (100k) + salt               │
│ 256-bit DEK (Data Encryption Key)       │
│   ↓ AES-256-GCM                         │
│ 密码字段 · 私钥 · 口令 · 备份数据        │
├─────────────────────────────────────────┤
│ 传输层：SSH v2 加密通道                   │
│ 云同步：HTTPS + JWT + E2EE              │
│ 生物识别：仅作 UI 解锁门控               │
└─────────────────────────────────────────┘
```

**密钥生命周期：**
1. 首次启动 → 生成 salt → 设置主密码 → 派生 DEK
2. 解锁 → 验证密码 → 派生 DEK → 存入 SessionKeyHolder（内存）
3. 云登录 → 获取服务器 salt → 重新派生 DEK
4. 退出 → 清除 SessionKeyHolder

---

## 10. 待改进方向

- [ ] 终端从 ConnectBot 旧 UI 迁移到 Compose TerminalScreen
- [ ] Room 数据库合并（AppDatabase + ConnectBotDatabase）
- [ ] 云同步改为 Retrofit + OkHttp（更好的错误处理）
- [ ] 支持 WebDAV / OneDrive 同步
- [ ] 端到端加密改为 Signal Protocol 或 age
- [ ] 支持 SFTP 断点续传
- [ ] 多语言完善（中/英）
