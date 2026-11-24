**雫拔刀剑**（Nekoha Shizuku Blade）—— SlashBlade: Resharped 附属的多平台移植，刀名沿用原版的日式命名。本 Release 收录四个平台的包。

## 下载

| 平台 | jar | 必需前置（实测版本） |
| --- | --- | --- |
| Minecraft **1.21.1 · NeoForge** | `shizuku-blade-1.21.1-neoforge-3.8.0.jar` | SlashBlade 2.0.5-1.21.1 以上 |
| Minecraft **1.21.1 · Fabric** | `shizuku-blade-1.21.1-fabric-3.8.0.jar` | Fabric API + SlashBlade-Refabricated `1.5.1-Resharped-2.0.5` + Forge Config API Port |
| Minecraft **1.20.1 · Fabric** | `shizuku-blade-1.20.1-fabric-3.8.0.jar` | Fabric API + SlashBlade-Refabricated `1.4.0-Resharped-1.9.65` + Forge Config API Port |
| Minecraft **26.1.2 · Fabric** | `shizuku-blade-26.1.2-fabric-3.8.0.jar` | Fabric API + SlashBlade-Refabricated `1.5.1-Resharped-2.0.5` + Forge Config API Port |

> 1.20.1 Forge 版由原作者维护，本 Release 不包含。

**Java 要求**：1.20.1 → 17+；1.21.1 → 21+；26.1.2 → 25+。

## 本次更新（自 v3.7.0 起）

### 审判系统补完（26.1.2）
- 域球 / 裂缝 / 落剑三段**世界几何回归原版**实现（独立帧通道；裂缝用 GPU 离屏真 shader 替代 CPU 逐行移植）
- 补回**审判持刀姿势动画**（26.1 的 `setupAnim` 改成渲染状态快照，用渲染状态桥接把实体数据带进去）
- 面板预览裂缝按**物理像素 1:1** 离屏，修掉 v 翻转 / 写掩码 / 分辨率发糊

### 时停 / 囚笼
- **恢复时停的黄色全屏滤镜**：原版把颜色层挂在 `RenderGuiOverlayEvent.Pre` 上，而该事件**逐 GUI 图层触发**（26 个）⇒ 实际是同一层每帧叠加 26 次（满强度≈74%）。本移植按累积等效公式单次绘制，取**原版的一半（13 次）**
- **恢复时停球 / 囚笼球的深度遮挡**（NeoForge 与两个 Fabric 端）
- 雾色沉浸：审判域球 + 时停，三端行为统一

### 粒子
- 全量粒子审计并**还原原版**（四端共 96 处：灵魂火 / 熔岩 / 横扫攻击 / 龙息 / 白星光点 等）

### 其他修复
- 第二层皮肤（袖子 / 外套 / 帽子）错位 —— 26.1 下不应复制父节点姿态
- 「收刀一下就回去」：姿势计时改由自己的时间轴驱动
- 服务端停机未清空 SA 静态状态
- SA 数值对齐 3.8.0（固定 `DAMAGE_SCALE`）

## 已知限制

- **开光影（Iris shaderpack）时**，时停球 / 囚笼球在**天空背景处**会被光影的后期天空重绘稀释到约 5%（背后是实心方块处完全正常）。成因在光影自身的后期 pass，本 mod 侧无法用状态规避。
- 26.1.2 端没有阴间 boss 类模组，击杀相关分支无法实机验证（代码与 1.20.1 同源且有兜底）。

## 安装

- 每个实例**只放一个** shizuku 包；同 mod 的新旧 jar 共存会因 `module.ResolutionException` 崩端。
- 前置 SlashBlade-Refabricated 必须与游戏 / 加载器版本对应（见上表）。
