# 雫拔刀剑 · Shizuku-Blade

[SlashBlade: Resharped](https://github.com/flammpfeil/SlashBlade-Refabricated)（拔刀剑）的附属 mod ——
17 把刀 + 18 个 SA。原作者为 [buwan](https://github.com/BBuWan22/Shizuku-Blade)。

**本仓库是经作者授权的多平台维护分支**（已获授权，负责下列版本；1.20.1 Forge 仍由原作者维护），
把作者面向 Minecraft 1.20.1 / Forge 的源码维护到：

| Minecraft | 加载器 | 说明 |
| --- | --- | --- |
| 1.21.1 | **NeoForge** | |
| 1.21.1 | **Fabric** | |
| 1.20.1 | **Fabric** | |
| 26.1.2 | **Fabric** | 26.1 渲染架构大改，见文档 A4 |

> 角色 / 主刀名「**猫羽雫**」（ねこはしづく / Nekoha Shizuku，画师 甘城なつき）。
> 本仓库只负责上面这几个版本；1.20.1 Forge 版仍由原作者维护，本仓库**不发布**。

## 下载

见 [Releases](https://github.com/EasyRain/Shizuku-Blade/releases/latest)——每个平台一个 jar：

| 平台 | jar | 必需前置（实测版本） |
| --- | --- | --- |
| 1.21.1 · NeoForge | `shizuku-blade-1.21.1-neoforge-*.jar` | SlashBlade 2.0.5-1.21.1 以上 |
| 1.21.1 · Fabric | `shizuku-blade-1.21.1-fabric-*.jar` | Fabric API + SlashBlade-Refabricated `1.5.1-Resharped-2.0.5` + Forge Config API Port |
| 1.20.1 · Fabric | `shizuku-blade-1.20.1-fabric-*.jar` | Fabric API + SlashBlade-Refabricated `1.4.0-Resharped-1.9.65` + Forge Config API Port |
| 26.1.2 · Fabric | `shizuku-blade-26.1.2-fabric-*.jar` | Fabric API + SlashBlade-Refabricated `1.5.1-Resharped-2.0.5` + Forge Config API Port |

**Java**：1.20.1 → 17+；1.21.1 → 21+；26.1.2 → 25+。

## 安装

- 把 jar 放进 `mods/`，同时装好上表的必需前置。
- **每个实例只放一个 shizuku 包** —— 新旧 jar 共存会因 `module.ResolutionException` 崩端。

## 文档

| 文件 | 内容 |
| --- | --- |
| [`docs/CHANGES_FROM_ORIGINAL.md`](docs/CHANGES_FROM_ORIGINAL.md) | 相对 1.20.1 原版的**有意改动清单**、行为约定、已知限制、**回馈上游的 PR 记录** |
| [`docs/PORTING_GUIDE.md`](docs/PORTING_GUIDE.md) | Forge→NeoForge/Fabric 的迁移与编译修复实录（SRG→Mojmap 速查等） |
| [`docs/RELEASE_v3.8.0.md`](docs/RELEASE_v3.8.0.md) | 当前版本的发布说明 |

## 已知限制

- **开光影（Iris shaderpack）时**，时停球 / 囚笼球在**天空背景处**会被光影的后期天空重绘稀释到约 5%
  （背后是实心方块处正常）。成因在光影自身的后期 pass，本 mod 侧无法用渲染状态规避。
- 26.1.2 端**没有阴间 boss 类模组**可对照，击杀相关分支未做实机验证（代码与 1.20.1 同源且有兜底）。

## 构建

需要 JDK 21（1.21.1）/ JDK 25（26.1.2）与 Gradle 8.x / 9.x：

```bash
# 以 NeoForge 1.21.1 为例
JAVA_HOME=<zulu-21> GRADLE_USER_HOME=<gradle-home> \
  gradle -p Shizuku-Blade-NeoForge-1.21.1 build --console=plain --offline
```

产物在 `build/libs/shizuku-blade-<mcversion>-<loader>-<version>.jar`。

## 致谢与许可

- 原作者 **buwan**（[BBuWan22/Shizuku-Blade](https://github.com/BBuWan22/Shizuku-Blade)）与前置
  **SlashBlade: Resharped** 的作者 —— 本仓库只是移植，玩法与素材版权归原作者。
- 许可：**GNU LGPL 3.0**（见 [LICENSE](LICENSE)）。
