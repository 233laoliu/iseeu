# 配置与使用

## 安装

**客户端和服务端都要装。** 从 [Release](https://github.com/Craft233MC/iseeu/releases/tag/nightly) 下载，放入两端 `mods/`。

## 生成密钥

Windows (PowerShell)：
```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

## 配置 iseeu-common.toml

首次启动后 `config/` 目录自动生成。**客户端也需要这个文件，`server_secret` 必须一致。**

```toml
[general]
enforce_mode = "LOG_ONLY"       # 试跑；确认无误杀后改 ENFORCE
require_hardware_fingerprint = true

[security]
server_secret = "你的密钥"
hwid_salt = "iseeu-v1-salt"
timestamp_tolerance_seconds = 60
verification_timeout_seconds = 15

[mod_list]
required_hash = ""              # 用 /iseeu hashtest 获取
allow_op_bypass = false
```

## 管理员命令

| 命令 | 说明 |
|------|------|
| `/iseeu ban <玩家名> [原因]` | 封禁 HWID |
| `/iseeu unban <hwid>` | 解封 |
| `/iseeu list [页码]` | 封禁列表 |
| `/iseeu check <玩家名>` | 验证状态 |
| `/iseeu hashtest` | Mod 列表哈希 |

封禁数据 `config/iseeu-bans.json`。

## 工作流程

```
连接 → 配置阶段（进游戏前）
  → 服务端下发 challenge
  → 客户端采集 Mod哈希 + HWID + nonce → HMAC签名
  → 服务端六重校验
  → 通过进游戏 / 失败拒绝
```

## FAQ

- **进服就断开？** 客户端装 mod + `server_secret` 一致。
- **试跑？** `enforce_mode = "LOG_ONLY"`。
- **防所有作弊？** 不能。HWID 可被 spoof，配合 GrimAC/Vulcan 使用。
