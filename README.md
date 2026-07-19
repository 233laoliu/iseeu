# IseeU Guard

**NeoForge 1.21.1 客户端+服务端反作弊模组。** 在玩家进入游戏世界之前完成握手校验，Mod 列表不匹配、硬件被封禁的客户端直接拒绝连接。

## 功能

- **Mod 列表校验** — 客户端 mod 列表 SHA-256 哈希与服务端配置比对，版本不一致拒绝入服
- **硬件指纹封禁** — CPU + 磁盘 + MAC 加盐 SHA-256 生成 HWID，换号不换机一样封
- **配置阶段拒绝** — 握手在进游戏前完成，作弊者看不到游戏世界
- **防篡改防重放** — HMAC-SHA256 签名 + challenge/nonce/时间戳三重防重放
- **零第三方依赖** — 纯 NeoForge 原生网络 API，不依赖任何整合包库

## 快速开始

1. [下载最新 Nightly Build](https://github.com/Craft233MC/iseeu/releases/tag/nightly)
2. 客户端和服务端都放入 `mods/`
3. 按 [Wiki 配置指南](https://github.com/Craft233MC/iseeu/wiki) 填写 `config/iseeu-common.toml`
4. 先用 `LOG_ONLY` 模式试跑，确认无误杀后切 `ENFORCE`

## 文档

完整配置说明、命令手册、常见问题见 [Wiki](https://github.com/Craft233MC/iseeu/wiki)。

## 安全模型

**客户端永远不可信。** HWID 是客户端自报的，理论可被虚拟机/hardware spoof 欺骗。本模组提高作弊门槛，建议配合服务端行为反作弊（GrimAC/Vulcan）使用。

`server_secret` 是 HMAC 签名密钥，泄露 = 整个握手体系作废，务必保密。
