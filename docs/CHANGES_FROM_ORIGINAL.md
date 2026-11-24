# Shizuku-Blade · 有意改动清单（相对 1.20.1 原版）

> **用途**：记录在原版（作者 buwan 的 Shizuku-Blade）基础上做的**有意改动 / 额外修复 / 新增功能**。
> **原版更新后重新同步时，逐条核对本清单** —— 把这些改动**保留 / 重做**，避免被原版新代码覆盖，
> 或把原版本来就有的 bug 又移植回来一遍。
>
> **不在本清单**的内容：
> - **机械 API 迁移**（Forge→NeoForge、SRG→Mojmap、编译/运行报错修复、19 mixin→事件重构、网络包 等）→ 见同目录 `PORTING_GUIDE.md`（= 迁移/运行修复实录）。
> - **通用跨项目经验** → `D:\DshWorkSpace\Minecraft\WORKSPACE_MEMORY.md` 通用区（§11 Mixin、§15 资源目录/数据包、§20 渲染着色器、§21 光影、§16 反编译）。
>
> **上游基线（重移植时对照用）**
> - **已发布版本的反编译**：`.ref\decompiled\own-mods\modrinth34`(3.4.0) / `modrinth35`(3.5.0) / `modrinth36`(3.6.0) / `modrinth36fix2` / `modrinth37`(3.7.0)。
> - **作者当前源码**（Forge 1.20.1，跟踪 `upstream/master`）：工作区内的 `Shizuku-Blade-Forge-1.20.1/`。
>   该克隆**没配代理**，拉取前先 `git config http.https://github.com.proxy http://127.0.0.1:7890`。
>   写作时基线提交 = **`52f65ad`**（= PR #7 合并后的 master）。
> - 原版 jar 在 `minecraft\.minecraft\versions\1.20.1-Forge\mods\shizuku-blade-<版本>.jar`。
>
> **仓库**：本移植仓库 = `EasyRain/Shizuku-Blade`（分支 `main`，**本文件已入库**）；
> 作者仓库 = `BBuWan22/Shizuku-Blade`（`master`）。我们回馈给上游的改动见 **§E**。
>
> **文档索引**：本文件（有意偏离清单）· `PORTING_GUIDE.md`（迁移/编译修复实录）· `RELEASE_v3.8.0.md`（发布说明）。

---

## A. 有意行为改动 / 新增功能（原版更新后必须保留）

### A1. 光影（Iris / Oculus）兼容 ——【新增功能，原版没有】【上游状态：未回馈，本移植专有】
原版 1.20.1 没有光影兼容逻辑；本移植新增，**重移植时上游不会带过来，必须重做**。

- 新增类 **`client/ShaderPackCompat.java`**：`isShaderPackActive()` = `ModList.get().isLoaded("iris")||isLoaded("oculus")` + 反射 `net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()`（oculus 用 `OculusApi`）。不引硬依赖、Method 缓存、结果不缓存。
- **`client/SynthwaveShaderManager.java`**：检测到光影就**跳过**全屏后处理。原因：合成波在 `AFTER_CUTOUT_BLOCKS` 阶段渲染会**污染光影管线整屏花屏**（原版同样降级为不可见，不是我们独有的怪状态）。
- **`client/GalaxyFieldRenderer.java`**：光影下世界内自定义 RenderType 不渲染 → 改到 **GUI 阶段（`RenderGuiEvent.Post`）**用捕获的世界矩阵重绘；需 `NO_DEPTH` 变体渲染类型（屏幕帧缓冲无深度）。
  - ⚠️ 合成波的全屏后处理**不适合**改到 GUI 阶段重绘（无深度只剩纯红黑）→ 保持「光影下跳过」，别改。
  - 星河领域 GUI 重绘要 `prepareCosmicRender(opacity, fixedView=false)` 沿用玩家视角，别被物品栏的固定视角逻辑影响。
- 另：`client/ShizukuClientSetup.java` 为客户端注册入口（迁移期新增）。

### A2. 假人（dummmmmmy）兼容：领域伤害源改 `magic()` ——【修复原版 bug】【上游状态：未回馈】（任何 PR 都没碰过 `sa/GalaxyField.java`）
- **上游写法**（`modrinth37` `sa/GalaxyField.java:92`）：`target.hurt(target.damageSources().fellOutOfWorld(), damage)`
- **我们的写法**（`sa/GalaxyField.java` `applyDamage`）：`target.hurt(target.damageSources().magic(), damage)`
- **原因**：假人 mod 的 `hurt()` 对 `fellOutOfWorld` 来源**直接 `remove()`**（无伤害、无掉落）→ 领域开启后**假人原地消失**。
  `magic()` 同样无视护甲、不产生仇恨，且假人可正常吸收并显示伤害。
- `sa/GalaxyArc.java` 用的 `indirectMagic(null, player)` **与上游一致**，属原版写法、无需改。

### A3. 有名 SA 合成波特效加「淡入」 ——【修复上游「脚下持续闪烁」bug】【上游状态：**已回馈 —— PR #6 已合并**，但两边实现不同，见下】
- **上游表现**：右键蓄力（有名 SA）开启合成波特效时**没有淡入**，一开就是满强度；玩家**快速连点右键**会让特效反复开关 → **在脚下持续闪烁**（上游 bug，原作者未修）。
- **我们的做法**：给合成波加 `U_FadeIn` 淡入 —— 开启后**前 0.2s 完全透明**（`FADE_DELAY_MS=200`），随后 **0.1s 内淡入**（`FADE_MS=100`）→ 快速连点不可见、不再闪。
- **改动落在 3 个文件，重移植时别只搬 Java 漏了 shader**：
  1. `client/SynthwaveShaderManager.java`：`FADE_DELAY_MS` / `FADE_MS` 常量 + 每帧算 `fade` 并 `synthwave.getUniform("U_FadeIn").set(...)`；
  2. `assets/shizuku/shaders/core/synthwave.fsh`：`uniform float U_FadeIn;` + 末行 `fragColor = vec4(synthColor, alphaMask * U_FadeIn);`（**上游是 `alphaMask`**）；
  3. `assets/shizuku/shaders/core/synthwave.json`：uniform 列表加 `{"name":"U_FadeIn","type":"float","count":1,"values":[0.0]}`。
- **触发逻辑没改**：`client/YmsaChargeHandler.java`（主手拔刀剑 + `player.isUsingItem()` + `getTicksUsingItem() > 0` + 该刀 SA = `SASlashArtRegistry.Ymsa`）**与上游逐行一致**，改的只是渲染淡入。

- ⚠️ **同一功能、两边实现不同（同步上游时整套换，别混用）**

  | | 延迟 | 淡入时长 | uniform 名 |
  | --- | --- | --- | --- |
  | **本移植四端** | `FADE_DELAY_MS = 200` | `FADE_MS = 100` | **`U_FadeIn`**（Java 设置处 + `synthwave.fsh` + `synthwave.json` 三处一致） |
  | **上游（PR #6 合并后）** | 隐式（延迟期内 `fade` 保持 0） | `FADE_MS = 150` | **`U_Fade`** |

  ⇒ 两边改的是同一处，但**常量与 uniform 名都不同**：同步上游那版时必须 Java + `.fsh` + `.json` **一起换**；
  只换一半会**静默失效**（uniform 名不匹配不会报错，只是特效不淡入）。

  **为什么会不一样（2026-11-24 查证过的历史，别再当 bug 查）**：
  移植端这套是 **2026-11-02** 移植期就自己写的（`b5b589e` / `7c09b8b` / `034f57c`）—— 当时基线 3.7.0 的
  synthwave **完全没有淡入机制**（`modrinth37` 的 `synthwave.fsh` 末行就是 `fragColor = vec4(synthColor, alphaMask)`），
  所以一开就是满强度、连点就闪。
  而提给作者的两次都在 **2026-11-22**、且是在**作者的 Forge 代码里重新实现**的：
  PR #3（`77380cb`）引入 `U_Fade` + 200ms 延迟 + 250ms 淡入，PR #6（`3889df1`）把它调成 200ms + 150ms。
  ⇒ **不是"我们的 PR 被改了"，而是同一个修复做了两遍、两处各自命名与取参数、之后没互相同步**。
  - **决定（2026-11-24）**：统一方向 = **以上游那份为准** ⇒ `U_FadeIn` → **`U_Fade`**、
    `FADE_MS` `100` → **`150`**（延迟仍保持我们现有的显式 `FADE_DELAY_MS = 200`，行为等价）。
    **但用户要求「不重新构建」**，代码改动**留到下次发版一起做**（差异只有 50ms 淡入手感，不值得单独出包）。
  - ✅ **代码已改完**（2026-11-24，四端共 **16 个文件 / 35 处**：31×`U_FadeIn` + 4×`FADE_MS`）。
    **26.1.2 已单独构建并实测通过**（`fade = clamp((elapsed-200)/150, 0, 1)`，包内 `U_Fade` 已生效）；
    **其余三端按用户要求不重新构建/部署**，留到下次发版自然带上。
  - ⚠️ **发布状态**：已发布的 **v3.8.0 四个包仍是旧参数**（`U_FadeIn` / 100ms），
    源码 `main` 与发布资产**在这一处不一致** —— 这是**有意为之**（用户 2026-11-24 决定：差异只有 50ms 淡入、
    基本感觉不出来，不值得单独重发）。**下次发版时说明里提一句即可。**
  - **本条目存在的意义**：用户原话 —— 同步只是为了**免得以后又忘了"为什么我们和上游不一样"**；
    行为本身无需对齐，其余部分也不用同步。所以这里保留的是「差异 + 原因 + 现状」的记录，而不是待办。
  - 改动清单（留档，便于核对是否改全 —— **各端资源命名空间不一致，最容易漏**）：
    | 端口 | 文件 |
    | --- | --- |
    | NeoForge 1.21.1 | `client/SynthwaveShaderManager.java` + `assets/shizuku/shaders/core/synthwave.fsh` + `…/synthwave.json` |
    | Fabric 1.21.1 | 同上 3 个 **+ `assets/minecraft/shaders/core/synthwave.{fsh,json}`**（同名额外的 minecraft 命名空间副本） |
    | Fabric 1.20.1 | `SynthwaveShaderManager.java` + `assets/shizuku/shaders/core/synthwave.fsh` + `assets/minecraft/shaders/core/synthwave.{fsh,json}`（**该端没有 shizuku 命名空间的 .json**） |
    | Fabric 26.1.2 | `SynthwaveShaderManager.java` + `assets/shizuku/shaders/core/synthwave.{fsh,json}` + **`assets/shizuku/shaders/post/synthwave.fsh`**（26.1 的后处理变体） |
    校验口径：`U_FadeIn` 残留数必须为 **0**，且 **uniform 名在 Java + 所有 `.fsh`/`.json` 里一致**，否则**静默失效**（不报错、只是不淡入）。

### A4. 26.1.2 审判三段的世界几何渲染挂点 ——【26.1 渲染架构强制偏离，重移植到 26.x 时必须保留】【上游状态：不适用（26.1 才有）】
- **上游（1.20.1/1.21.1）**：域球 / 裂缝 / 落剑全部画在 `RenderLevelStageEvent.AFTER_ENTITIES`（单层 main 目标，先画的东西已在 main 的颜色+深度里）。
- **26.1.2 的差异**：main pass 变成分层帧图 —— 实体/物品/粒子/云/天气各有独立 target，由 `minecraft:transparency` 合成链按**每像素深度排序**混合，且只把**颜色**写回 main（深度留在各层）。把几何画在层内（`AFTER_TRANSLUCENT_TERRAIN`/`END_MAIN`）会被卷进分层目标的预乘合成，观感是「被天空盖住、只剩 20~30% 不透明」。
- **我们的做法（2026-11-24，删掉阶段一的全屏后处理方案后确立）**：
  1. **域球 + 裂缝** = `client/JudgmentWorldPass.java`：在透明合成链**之后**开独立 FramePass（挂点 `mixin/LevelRendererSynthwaveMixin` 的 `addLateDebugPass` WrapOperation，在其**之前**声明），直写 main 的 color/depth override；几何（16×40 球面 / 单位四边形）与 `judgment_domain`/`judgment_rift` 着色器算法逐行照原版，每帧 uniform（Atmosphere/Phase）经顶点色携带（`shader/JudgmentShaders.java`）。
  2. **落剑** = `JudgmentVisuals.submitSword` 经 Fabric `LevelRenderEvents.COLLECT_SUBMITS` 走**原版物品提交节点**（`ItemStackRenderState` + `ItemModelResolver#updateForTopItem` + `submit(pose, collector, FULL_BRIGHT, NO_OVERLAY, 0)`），画进 item_entity 分层、由合成链正确合成。
  3. **遮挡次序**：画球前把 item_entity 深度 `copyDepthFrom` 回 main（主通道在画物品层前已先把 main 深度拷进去，所以该层深度 = 地形+实体+物品）⇒ 域球用 LEQUAL 测试时被地形/实体/落剑正确遮挡 = 上游「剑先画写深度、球再测深度」的次序语义。
  4. 相机旋转**不要再推**：`renderLevel` 建帧图前已把 modelViewMatrix push 进全局 `RenderSystem.getModelViewStack()`，帧通道里只建 fresh `PoseStack`。
  5. **面板预览裂缝**（`client/JudgmentRiftPreview.java`）：上游 1.20.1/1.21.1 是在 GUI 里**直接跑真着色器**（`renderCut` 用世界同一个 RIFT RenderType + `graphics.bufferSource()`，旧 GUI 允许任意 RenderType 混入）；26.1 的 GUI 改为渲染状态提取、没有自定义着色器通道 ⇒ 改为**世界帧通道里把裂缝四边形画进离屏 `TextureTarget`**（同一份 GLSL：模型栈推相机逆旋转 + 逆投影 NDC 四角铺满 + `JudgmentShaders.riftOffscreen()` 无混合直写），GUI 只 blit 颜色视图。三个已实测的坑：① 渲染目标 v=0 在底、GUI 贴图 v=0 在顶，blit 必须 v 翻转（v0=1,v1=0），否则垂直镜像；② 无混合直写的写掩码必须 `WRITE_ALL`（`WRITE_COLOR`=7 不含 alpha，目标 alpha 恒 0 = 预览隐形）；③ 离屏分辨率逐帧对齐预览区**物理**像素（rect × `getGuiScale()`，1:1 blit），固定分辨率上采样会发糊。
- **已删除**：`client/JudgmentPostEffect.java` + `assets/shizuku/shaders/post/judgment_domain.fsh`（阶段一的全屏后处理域球，用户要求单/多人观感统一为世界几何后移除）。
- **保持不变**：屏幕红染 `shade`（fillGradient）与面板斩击预览 `renderCut`。

---

## B. 迁移期确立的行为约定（重移植时核对，别改回去）

- **B1. 伤害公式：不要手动叠加锋利/力量/精炼。** 与上游一致，统一走 `util/DamageCalculator.calculateRefinedDamage()`
  = `comboRatio × ATTACK_DAMAGE + AttackHelper.getRankBonus(caster)`，再 `× AttackManager.getSlashBladeDamageScale(caster) × SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER`。
  （我们曾在 3.6.0fix2 手动叠过附魔加成，3.7.0 已回归上游；**别再次叠加**。）
- **B2. 领域 / AoE 伤害源一律用 `magic()` 或 `indirectMagic(null, player)`，禁止 `fellOutOfWorld()`**（见 A2 的假人原因）。
- **B3. 不移植上游 `agent\*` 与 `core\*` 的 ASM / java-agent 血量体系**：上游有 `agent/AgentBootstrap`、`AgentHealthTransformer`、`core/HealthLockManager`、`HealthModify`、`AllReturnPlugin` 等（ASM 改血量/返回值），1.21.1 不适用 → 本移植用公共 API 重写（`core/UltimateEntityRemover` 等）。**重移植时不要照搬这些类。**
- **B4. 抹除（ymsa）体系不照搬上游 19 个 mixin**：改用事件 API —— `discard()` + `ServerChunkCache.removeEntity(entity)`；防重生 `EntityJoinLevelEvent`；tick 抑制 `EntityTickEvent.Pre`；掉落清理 `LivingDropsEvent`；周期净化 `ServerTickEvent.Post`；客户端 `ClientLevel.removeEntity(id, RemovalReason)`（public）+ `YmsaForceRemovePacket` + `NoopRenderer` 换渲染器。仅保留 1 个 mixin（`LivingEntityAccessor` 暴露 `DATA_HEALTH_ID`）。**细节见 `PORTING_GUIDE.md`。**
- **B5. `PhantomSlashPacket` / `PhantomSlashInput` 是作者取消左键幻影斩后的遗留死代码，不注册、不接线**（不是漏注册，别"补"回去）。
  - 依据：作者 Forge 1.20.1 与 **3.7.0 反编译**里 `PhantomSlashInput` 都从未被引用、`PhantomSlashPacket` 都没注册
    （`.ref/decompiled/own-mods/modrinth37`）；NeoForge 1.21.1 的 `PacketHandler` 里也留了同样的注释。
  - ⚠️ 2026-11-24 **统一**：三个 Fabric 端此前自己接了这条线（`PhantomSlashPacket` 注册 + 接收器 +
    `PhantomSlashInput.tick()`），与 B5 和作者原版不符，**已全部移除**（四个端现在一致）。
  - ⚠️ 排查教训：做「缺失接线」审计时**参照必须是作者原版**，不能拿自家移植端当参照 ——
    自家端自己就可能是错的（本次先拿 1.21.1 Fabric 当参照，误判 `PhantomSlashInput.tick()` 为"缺失"）。
- **B6. 星河刀（mosmicblade）Tooltip 视觉 = 原版设计，必须照原版**：**全透明背景（不画底板）+ 纯白边框 + `ColorHelper.ShizukuBlade` 调色板流动渐变文字**（`client/tooltip/ShizukuTooltipRenderer.java` 的 `renderCosmicTooltipBackground` / `ColorHelper.ShizukuBlade`）。
  ⚠️ **迁移时曾搞错**（改成 Cosmic 调色板 + 加半透明底板），最后才纠正回原版 —— 重移植时**不要用 Cosmic 调色板、不要加半透明底板**。

- **B7. 时停的屏幕滤镜 = 只画颜色层，不画暗角**（`client/TimeStopScreenEffect.java`）。
  - 颜色层：原版挂 `RenderGuiOverlayEvent.Pre`，该事件**按 GUI 图层逐个触发**（Forge 注册了
    `VanillaGuiOverlay` 全部 26 个且不过滤）⇒ 原版实际每帧叠加 26 次，满强度≈74%。我们按
    累积等效公式 `a_eff = 1-(1-a)^N` 单次绘制，**N 取 13（原版一半）**，见 `OVERLAY_PASSES`。
  - 暗角：**停用**（`VIGNETTE_ENABLED = false`，方法保留）。原版暗角实际也没生效（边缘累加≈99% 被颜色层盖住），
    且我们单次绘制后水平渐变只能用阶梯色柱逼近、画得不规范 ⇒ 用户 2026-11-24 决定不用。
  - ⚠️ 客户端必须每 tick 调 `TimeStopScreenEffect.getInstance().tick()`（驱动 `updateStrength()`）；
    漏掉则 `currentStrength` 恒为 0，滤镜与雾色弱染全部不显示（26.1.2 曾漏，2026-11-24 补）。

- **B8. Sodium 会 `@Overwrite` 掉 `LevelRenderer.renderSectionLayer` 且不再调用 `RenderType.setupRenderState()`
  ⇒ 画世界几何前必须**自己显式声明深度状态**，不要继承环境状态。**
  （`client/WorldRenderCompat.bindMainTargetWithDepthTest()`；时停球 / 囚笼球 / 星河领域三处调用。）
  - 现象：NeoForge 端时停球 / 囚笼球没有遮挡（透墙可见）；26.1.2 与作者原版正常。
  - **根因（已反编译 Sodium 0.8.13 证实）**：`sodium-common.mixins.json` 里的
    `core.render.world.LevelRendererMixin.renderSectionLayer(...)` 带 **`@Overwrite`**，
    方法体被换成：
    `RenderDevice.enterManagedCode()` → `setupTerrain(...)` / `drawChunkLayer(...)` →
    `exitManagedCode()` → `PlatformLevelRenderHooks.runChunkLayerEvents(type, ...)`。
    - **原版的 `p_294513_.setupRenderState()` 没了** ⇒ `LEQUAL_DEPTH_TEST.setupState()`
      （它会 `enableDepthTest()`）**不再执行**，我们继承到的是 Sodium 画完区块后的状态。
    - NeoForge 的事件仍在派发：`runChunkLayerEvents` 在 `exitManagedCode()` **之后**调用，
      NeoForge 平台实现用它把 RenderType 映射成 Stage 并 post 事件 —— 所以球照画，只是丢深度。
  - **版本差异正好解释各端表现**：
    | 端 | Sodium | 是否 overwrite `renderSectionLayer` | 球的画法 | 结果 |
    | --- | --- | --- | --- | --- |
    | 1.20.1 Fabric | 0.5.13 | ❌ 无该 mixin（老架构） | 裸绘制 | ✅ 环境状态仍是原版的 |
    | 1.21.1 NeoForge | 0.8.13 | ✅ | 裸绘制 | ❌ 曾无遮挡 |
    | 1.21.1 Fabric | 0.8.13 | ✅ | 裸绘制 | ✅ 用户实测**未复现**，但机理相同 ⇒ 已同样补上显式声明（`c2b650e`） |
    | 26.1.2 Fabric | 0.9.2 | ✅ | `RenderTypes.debugQuads().draw(...)` | ✅ 自己走 RenderType，免疫 |
  - 关键对照（用户实测）：**星河领域的圆盘一直正常** —— 它是三者中唯一用**自定义 RenderType**
    绘制的（`COSMIC_RENDER_TYPE` 显式 `setDepthTestState(LEQUAL_DEPTH_TEST)`）。
  - 修法：绘制前 `getMainRenderTarget().bindWrite(false)` + 显式 `RenderSystem.enableDepthTest()` /
    `depthFunc(GL11.GL_LEQUAL)`。**用户实测已恢复遮挡**（高品质与流畅画质均正常）。
  - ⚠️ 我曾把根因误判为「fabulous 的 translucentTarget」并写进文档，**已更正**：该路径需要
    `Minecraft.useShaderTransparency()`，而它要求 `graphicsMode >= FABULOUS`（`Minecraft.java:2311`），
    用户是高品质(Fancy)，不成立。`bindWrite(main)` 保留为对该陷阱的防御（NeoForge 的 javadoc 亦警告
    `AFTER_TRANSLUCENT_BLOCKS` *"is called within a fabulous graphics target"*）。
  - **教训**：装了 Sodium/Iris 时，**原版 `RenderType` 的状态语义可能整个不成立**；
    世界阶段画任何东西都要自己声明状态。诊断这类问题必须去看 Sodium 的 mixin，而不是只读 vanilla 源码。
  - ✅ **光影下已实测**（2026-11-24）：用户确认 NeoForge 端两个球在 shaderpack 开启时显示正常
    ⇒ `bindWrite(主目标)` 在光影下也合适，**保持不动**。若日后光影下出现异常，再考虑改成
    「仅在无光影时执行」（`ShaderPackCompat.isShaderPackActive()` 现成可用）。

- **B9. 光影(Iris)下的自定义 RenderType / 管线：1.21.1 无解，26.1.2 可以注册。**
  - **1.21.1（Iris 1.8.14）：公开 API 做不到。** 反编译 `iris-neoforge-1.8.14` 的
    `net.irisshaders.iris.api.v0.IrisApi`，只有
    `isShaderPackInUse()` / `isRenderingShadowPass()` / `getConfig()` / `getSunPathRotation()` 等，
    **没有**任何「把自定义 RenderType/管线注册给 Iris」的方法 ⇒ 自定义 `ShaderInstance` 的
    RenderType 在世界阶段会被光影管线跳过 ⇒ 只能用 **GUI 阶段回退**（星河领域就是这么做的，
    代价是屏幕帧缓冲无深度，代码注释已记）。
  - **26.1.2（Iris 1.11.4）：可以。** 同一接口已新增
    `assignPipeline(RenderPipeline, IrisProgram)` 与 `assignPipelineShadow(RenderPipeline, IrisShadowProgram)`
    ⇒ 用 26.1 的 `RenderPipeline`（不是 RenderType）+ `IrisProgram` 注册后，自定义管线就能被光影
    正常处理（参与光照/阴影/后处理）。需要把 iris 作为 `compileOnly` 依赖。**未做，等需要时再上。**
  - **不依赖 Iris API 的替代方案**：改用**原版核心着色器**（Iris 认识的，如 `position_color`）
    承载几何、把自定义效果换成顶点属性/纹理表达 ⇒ 光影下天然可见。
    这正是时停球 / 囚笼球在光影下正常的原因（它们走 `GameRenderer.getPositionColorShader()`），
    代价是失去自定义 shader 效果。
  - 结论：**「自定义 shader + 自定义 RenderType」在 1.21.1 光影下不可能可见**（除非换成原版核心着色器）；
    26.1.2 才有官方注册途径。

- **B10. 已知限制：光影(shaderpack)下，时停球 / 囚笼球在「天空背景处」被稀释到约 5%。**
  - 现象（用户实测）：**不是不显示**，而是球体凡是背后是天空的部分都变得极淡（约 5% 不透明度）；
    背后有实心方块的部分完全正常。**只在 shaderpack 生效时出现**，多个端一致。
  - 机制：光影在**后期重新绘制天空**（天空区域在 gbuffer 里深度 = 1.0），把该区域的像素按高不透明度
    重画一遍 ⇒ 我们画在天空区域的那部分球被稀释；方块背景处被深度挡住、不参与重绘 ⇒ 正常。
  - **与云无关**（用户确认：很多光影自己接管云）。我最初推断是「挂在云那一段被云覆盖」，
    **已证伪并撤回**（当时还把两个球的挂载点从 `AFTER_TRANSLUCENT` 改到 `AFTER_ENTITIES` 做验证，
    现已还原 —— 该改动无依据，且会改变水/玻璃的叠放顺序）。
  - 从我们这边**无法用 GL 状态规避**（问题发生在光影自己的后期 pass，不在我们的绘制点）。
  - **可选的正规修法（未做）**：把这两个球改成走 **一个光影认识的 RenderType**（例如
    `RenderType.entityTranslucentEmissive(minecraft:textures/misc/white.png)` 这类，
    原版就有 1×1 白图，不需要新增素材）—— 光影会把它当实体/地形半透明层处理并**写入深度**，
    后期天空重绘（以 depth==1.0 为掩码）就不会再稀释它，同时遮挡语义也保持。
    代价：两个渲染器改成 `MultiBufferSource` 顶点写入（每端约 30 行），四端都要改。
  - 用户态度（2026-11-24）：**可接受，先留着**（"光影下面很多特效不显示都是社区共识"）。

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

## E. 上游回馈记录（PR）

我们把一部分修复回馈给了作者（`BBuWan22/Shizuku-Blade`）。**维护时照此表判断「这条改动上游有没有」**。

> ⚠️ 这些 PR 都是提在**作者的 1.20.1 Forge 代码**上的（开发分支在对照工程 `Shizuku-Blade-Forge-1.20.1/` 里），
> **不等于我们四个移植端已有同样实现** —— 「上游有了」与「我们端有了」要分开核对。
> 作者 `master` 基线（写作时）= **`52f65ad`**。查询：
> `gh pr list --repo BBuWan22/Shizuku-Blade --state all`（需 `GH_TOKEN` + `HTTPS_PROXY=http://127.0.0.1:7890`）。

| PR | 状态 | 标题 | 改动文件（节选） | 与本清单的关系 |
| --- | --- | --- | --- | --- |
| [#2](https://github.com/BBuWan22/Shizuku-Blade/pull/2) | ✅ 已合并 | 还原 3.7.0 源码：从已发布 jar 反编译 + 中文注释 | 100 个文件（整车源码） | 工程性，无行为改动 |
| [#3](https://github.com/BBuWan22/Shizuku-Blade/pull/3) | ✅ 已合并 | 修复：图标跟随视角闪烁 / 合成波触发 / 注入鲁棒性 / 线程安全 / JEI 与配方 id | `SynthwaveShaderManager` / `YmsaChargeHandler` / `CosmicBladeRenderer` / `DriveRendererMixin` / `EntityAbstractSummonedSwordMixin` / JEI | 与 **A3**、「召唤物兜底」重叠 |
| [#4](https://github.com/BBuWan22/Shizuku-Blade/pull/4) | ✅ 已合并 | i18n：`en_us.json` 里 37 条中文补成英文 | `assets/shizuku/lang/en_us.json` | §C 资源/语言相关（见发布约定 §4） |
| [#5](https://github.com/BBuWan22/Shizuku-Blade/pull/5) | ✅ 已合并 | 清理调试输出/死代码 + 游戏内配置页（含 Mods 列表「配置」按钮与可调喵石概率） | `client/screen/JudgmentScreen` / `CosmicShaders` / `core/*` 等 22 个 | 新增功能（上游独有，未移植回四端） |
| [#6](https://github.com/BBuWan22/Shizuku-Blade/pull/6) | ✅ 已合并 | 合成波补 200ms 延迟，替掉「按住 10 tick 才出」的阈值 | `SynthwaveShaderManager` / `YmsaChargeHandler` | **A3**（两边实现不同，见 A3 的表） |
| [#7](https://github.com/BBuWan22/Shizuku-Blade/pull/7) | ✅ 已合并 | 落剑朝向在施法瞬间锁定（不再跟着鼠标跑） | `network/JudgmentImpactPacket` / `sa/Ymsa` | 审判落剑（本清单 A4 是 26.1 渲染挂点，不同事） |
| [#8](https://github.com/BBuWan22/Shizuku-Blade/pull/8) | 🕒 待合并 | #7 的朝向锁定没生效（工厂方法丢参数）+ 施法位置一起锁死 | 同上两个文件 | 同上；分支 `fix/judgment-locked-origin`(`671dd24`) |
| [#9](https://github.com/BBuWan22/Shizuku-Blade/pull/9) | 🕒 待合并 | 模组 boss 走正常死亡 + 按 Shift 才抹除 + 假人点名收尾 | `sa/BossCompat`(新) / `sa/Ymsa` / `YmsaRenderKiller` / `mixin/LivingEntityAccessor` / `JudgmentImpactPacket` | 抹除（ymsa）体系；分支 `feat/judgment-normal-death`(`369999f`) |

**结论（写作时）**：
- **A1（光影兼容）/ A2（假人 magic）/ A4（26.1 审判挂点）/ B8（Sodium 深度状态）/ B9、B10** —— 任何 PR 都没碰过
  （已核对 `sa/GalaxyField.java`、`client/ShaderPackCompat.java`、`client/WorldRenderCompat.java` 均不在任何 PR 里）
  ⇒ **这些是上游没有的，重移植时必须自己重做**。
- **A3 是唯一「上游已合并、但两边实现不同」的**（合流时对齐 PR #6）⇒ 见 A3 表。
- #8 / #9 合并后，把对应条目改成 `【已回馈 PR #x】`，并回来更新本表的状态列。

---

## 维护说明

- 每次「同步上游新版本」或自行加改动后，**当轮追加到本清单**（写清：改了什么 / 哪个文件 / 为什么 / 上游原本怎么写）。
- 追加前先确认它确实**偏离了上游**（拿 `.ref\decompiled\own-mods\modrinthXX` 对照）；纯 API 适配写进 `PORTING_GUIDE.md`，别混进来。
- 本文件**已入库**（`EasyRain/Shizuku-Blade` 的 `docs/CHANGES_FROM_ORIGINAL.md`）；`PROJECT_MEMORY.md` 才是本地档案（已 gitignore，不进仓库）。
- **回馈上游**：凡是我们先修好、又提给作者的改动，合并后要回来在对应条目上标 `【上游状态：已回馈 PR #x】`，并同步 §E 的 PR 表 —— 否则下次同步上游会重复劳动，或把上游早就有的东西当成「我们的偏离」。
