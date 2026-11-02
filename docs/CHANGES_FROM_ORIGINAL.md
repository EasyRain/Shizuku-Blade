# Shizuku-Blade · 有意改动清单（相对 1.20.1 原版）

> **用途**：记录在原版（作者 buwan 的 Shizuku-Blade）基础上做的**有意改动 / 额外修复 / 新增功能**。
> **原版更新后重新同步时，逐条核对本清单** —— 把这些改动**保留 / 重做**，避免被原版新代码覆盖，
> 或把原版本来就有的 bug 又移植回来一遍。
>
> **不在本清单**的内容：
> - **机械 API 迁移**（Forge→NeoForge、SRG→Mojmap、编译/运行报错修复、19 mixin→事件重构、网络包 等）→ 见同目录 `PORTING_GUIDE.md`（= 迁移/运行修复实录）。
> - **通用跨项目经验** → `D:\DshWorkSpace\MCMOD\WORKSPACE_MEMORY.md` 通用区（§11 Mixin、§15 资源目录/数据包、§20 渲染着色器、§21 光影、§16 反编译）。
>
> **上游基线（反编译源码，重移植时对照用）**：`.ref\decompiled\own-mods\modrinth34`(3.4.0) / `modrinth35`(3.5.0) / `modrinth36`(3.6.0) / `modrinth36fix2` / `modrinth37`(3.7.0，**当前基线**)。
> 原版 jar 在 `minecraft\.minecraft\versions\1.20.1-Forge\mods\shizuku-blade-<版本>.jar`。
> 本项目**非 git**，本文件仅本地留存。

---

## A. 有意行为改动 / 新增功能（原版更新后必须保留）

### A1. 光影（Iris / Oculus）兼容 ——【新增功能，原版没有】
原版 1.20.1 没有光影兼容逻辑；本移植新增，**重移植时上游不会带过来，必须重做**。

- 新增类 **`client/ShaderPackCompat.java`**：`isShaderPackActive()` = `ModList.get().isLoaded("iris")||isLoaded("oculus")` + 反射 `net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()`（oculus 用 `OculusApi`）。不引硬依赖、Method 缓存、结果不缓存。
- **`client/SynthwaveShaderManager.java`**：检测到光影就**跳过**全屏后处理。原因：合成波在 `AFTER_CUTOUT_BLOCKS` 阶段渲染会**污染光影管线整屏花屏**（原版同样降级为不可见，不是我们独有的怪状态）。
- **`client/GalaxyFieldRenderer.java`**：光影下世界内自定义 RenderType 不渲染 → 改到 **GUI 阶段（`RenderGuiEvent.Post`）**用捕获的世界矩阵重绘；需 `NO_DEPTH` 变体渲染类型（屏幕帧缓冲无深度）。
  - ⚠️ 合成波的全屏后处理**不适合**改到 GUI 阶段重绘（无深度只剩纯红黑）→ 保持「光影下跳过」，别改。
  - 星河领域 GUI 重绘要 `prepareCosmicRender(opacity, fixedView=false)` 沿用玩家视角，别被物品栏的固定视角逻辑影响。
- 另：`client/ShizukuClientSetup.java` 为客户端注册入口（迁移期新增）。

### A2. 假人（dummmmmmy）兼容：领域伤害源改 `magic()` ——【修复原版 bug】
- **上游写法**（`modrinth37` `sa/GalaxyField.java:92`）：`target.hurt(target.damageSources().fellOutOfWorld(), damage)`
- **我们的写法**（`sa/GalaxyField.java` `applyDamage`）：`target.hurt(target.damageSources().magic(), damage)`
- **原因**：假人 mod 的 `hurt()` 对 `fellOutOfWorld` 来源**直接 `remove()`**（无伤害、无掉落）→ 领域开启后**假人原地消失**。
  `magic()` 同样无视护甲、不产生仇恨，且假人可正常吸收并显示伤害。
- `sa/GalaxyArc.java` 用的 `indirectMagic(null, player)` **与上游一致**，属原版写法、无需改。

### A3. 有名 SA 合成波特效加「淡入」 ——【修复上游「脚下持续闪烁」bug】
- **上游表现**：右键蓄力（有名 SA）开启合成波特效时**没有淡入**，一开就是满强度；玩家**快速连点右键**会让特效反复开关 → **在脚下持续闪烁**（上游 bug，原作者未修）。
- **我们的做法**：给合成波加 `U_FadeIn` 淡入 —— 开启后**前 0.2s 完全透明**（`FADE_DELAY_MS=200`），随后 **0.1s 内淡入**（`FADE_MS=100`）→ 快速连点不可见、不再闪。
- **改动落在 3 个文件，重移植时别只搬 Java 漏了 shader**：
  1. `client/SynthwaveShaderManager.java`：`FADE_DELAY_MS` / `FADE_MS` 常量 + 每帧算 `fade` 并 `synthwave.getUniform("U_FadeIn").set(...)`；
  2. `assets/shizuku/shaders/core/synthwave.fsh`：`uniform float U_FadeIn;` + 末行 `fragColor = vec4(synthColor, alphaMask * U_FadeIn);`（**上游是 `alphaMask`**）；
  3. `assets/shizuku/shaders/core/synthwave.json`：uniform 列表加 `{"name":"U_FadeIn","type":"float","count":1,"values":[0.0]}`。
- **触发逻辑没改**：`client/YmsaChargeHandler.java`（主手拔刀剑 + `player.isUsingItem()` + `getTicksUsingItem() > 0` + 该刀 SA = `SASlashArtRegistry.Ymsa`）**与上游逐行一致**，改的只是渲染淡入。

---

## B. 迁移期确立的行为约定（重移植时核对，别改回去）

- **B1. 伤害公式：不要手动叠加锋利/力量/精炼。** 与上游一致，统一走 `util/DamageCalculator.calculateRefinedDamage()`
  = `comboRatio × ATTACK_DAMAGE + AttackHelper.getRankBonus(caster)`，再 `× AttackManager.getSlashBladeDamageScale(caster) × SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER`。
  （我们曾在 3.6.0fix2 手动叠过附魔加成，3.7.0 已回归上游；**别再次叠加**。）
- **B2. 领域 / AoE 伤害源一律用 `magic()` 或 `indirectMagic(null, player)`，禁止 `fellOutOfWorld()`**（见 A2 的假人原因）。
- **B3. 不移植上游 `agent\*` 与 `core\*` 的 ASM / java-agent 血量体系**：上游有 `agent/AgentBootstrap`、`AgentHealthTransformer`、`core/HealthLockManager`、`HealthModify`、`AllReturnPlugin` 等（ASM 改血量/返回值），1.21.1 不适用 → 本移植用公共 API 重写（`core/UltimateEntityRemover` 等）。**重移植时不要照搬这些类。**
- **B4. 抹除（ymsa）体系不照搬上游 19 个 mixin**：改用事件 API —— `discard()` + `ServerChunkCache.removeEntity(entity)`；防重生 `EntityJoinLevelEvent`；tick 抑制 `EntityTickEvent.Pre`；掉落清理 `LivingDropsEvent`；周期净化 `ServerTickEvent.Post`；客户端 `ClientLevel.removeEntity(id, RemovalReason)`（public）+ `YmsaForceRemovePacket` + `NoopRenderer` 换渲染器。仅保留 1 个 mixin（`LivingEntityAccessor` 暴露 `DATA_HEALTH_ID`）。**细节见 `PORTING_GUIDE.md`。**
- **B5. `PhantomSlashPacket` / `PhantomSlashInput` 是作者取消左键幻影斩后的遗留死代码，不注册**（不是漏注册，别"补"回去）。
- **B6. 星河刀（mosmicblade）Tooltip 视觉 = 原版设计，必须照原版**：**全透明背景（不画底板）+ 纯白边框 + `ColorHelper.ShizukuBlade` 调色板流动渐变文字**（`client/tooltip/ShizukuTooltipRenderer.java` 的 `renderCosmicTooltipBackground` / `ColorHelper.ShizukuBlade`）。
  ⚠️ **迁移时曾搞错**（改成 Cosmic 调色板 + 加半透明底板），最后才纠正回原版 —— 重移植时**不要用 Cosmic 调色板、不要加半透明底板**。

---

## C. 数据包 / 资源格式（1.21.1 硬性要求，原版更新后**每次都要重做**）

上游是 1.20.1 格式，每次同步上游资源都要重来一遍。**完整清单见 `WORKSPACE_MEMORY.md` §15**，此处只列本 mod 涉及的：

- 目录单复数：`recipes/`→`recipe/`、`advancements/`→`advancement/`、`loot_tables/`→`loot_table/`（放错**静默失效**）。
- 配方：`"result":{"item":x}` → `{"id":x}`（`ItemStack.STRICT_CODEC` 只认 `id`）。
- 标签命名空间：`forge:` → `c:`（特殊改名 `forge:leather`→`c:leathers` 等）。
- 进度 ItemPredicate：`{"tag":"x"}` → `{"items":"#x"}`。
- 命名刀 `slashblade:blade` 配料：`{"item":"..."}` → `{"items":["slashblade:slashblade"],"request":{...}}`。
- `ymsa_kill` 伤害类型：1.21.1 damage_type JSON **没有内联 `tags` 数组**，bypass 标签放 `data/minecraft/tags/damage_type/*.json`。

---

## D. 已知现象（非 bug）/ 未实现（别当 bug 修）

- **有名刀 `attack_base: 3.4e38` 打假人不显示伤害**：假人伤害显示有 `Math.abs(实际-记录) > 1e-4` 校验，天文数字在 float 精度下必失配 → 不显示。**属假人机制，不是本 mod 的 bug，不用改。**
- 上游 tooltip 原版 switch 里的其它刀型分支是不可达死代码；本移植特殊信息框**只作用于星河刀（mosmicblade）**，无铭「有名」由 `ShizukuTooltipRenderer` 负责，其余刀回落到原版信息框。

---

## 维护说明

- 每次「同步上游新版本」或自行加改动后，**当轮追加到本清单**（写清：改了什么 / 哪个文件 / 为什么 / 上游原本怎么写）。
- 追加前先确认它确实**偏离了上游**（拿 `.ref\decompiled\own-mods\modrinthXX` 对照）；纯 API 适配写进 `PORTING_GUIDE.md`，别混进来。
- 本项目非 git，本文件是本地档案，不进任何仓库。
