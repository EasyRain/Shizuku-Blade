# Shizuku-Blade 3.4.0 → 1.21.1 移植指南（子代理必读）

## 目标与目录
- 反编译源码（1.20.1，Forge，SRG 混淆名）：`D:\DshWorkSpace\MCMOD\.ref\decompiled\own-mods\modrinth34\src\com\buwan\shizuku\`
- 移植目标工程（NeoForge 21.1.228 / Minecraft 1.21.1 / Java 21 / **Mojang 映射名**）：`D:\DshWorkSpace\MCMOD\Shizuku-Blade\Shizuku-Blade-NeoForge-1.21.1\src\main\java\com\buwan\shizuku\`
- 原版+NeoForge 1.21.1 源码参考：`D:\DshWorkSpace\MCMOD\.ref\decompiled\minecraft\vanilla\`（部分已解压）；完整源码在 jar 内：
  `D:\DshWorkSpace\MCMOD\Shizuku-Blade\Shizuku-Blade-NeoForge-1.21.1\build\moddev\artifacts\neoforge-21.1.228-minecraft-sources.jar`
  解压命令：`"C:\Program Files\Zulu\zulu-21\bin\jar.exe" xf <那个jar> net/...` （在工作目录里解压后阅读，勿提交到 src）
- 拔刀剑 1.21.1 API 源码参考：`D:\DshWorkSpace\MCMOD\SlashBlade_Resharped\src\main\java\mods\flammpfeil\slashblade\`（我们的 fork，`1.21.1` 分支）

## 硬性规则
1. 只写目标工程目录，不得改动 `SlashBlade_Resharped`（我们的 fork，1.21.1 分支）与 `.ref` 下的文件。
2. 所有代码使用 Mojang 映射名（原反编译代码里的 m_xxx/f_xxx 必须翻译）。
3. 不确定的 SRG 名：用 `javap` 或解压源码 jar 对照 1.21.1 源码确定语义后翻译；禁止保留任何 m_/f_ 符号。
4. 客户端专用类加 `@OnlyIn(Dist.CLIENT)`（`net.neoforged.api.distmarker.Dist/OnlyIn`）。
5. 公共代码不得直接引用客户端类（见"客户端安全模式"）。
6. 编译目标：`javac` 兼容即可，我会统一编译修复。
7. 保持包结构与原名；删掉不再需要的类时在回复里说明。

## Forge 1.20.1 → NeoForge 1.21.1 通用映射
- `net.minecraftforge.eventbus.api.IEventBus/SubscribeEvent` → `net.neoforged.bus.api.IEventBus/SubscribeEvent`
- `net.minecraftforge.common.MinecraftForge.EVENT_BUS` → `net.neoforged.neoforge.common.NeoForge.EVENT_BUS`
- `net.minecraftforge.fml.common.Mod.EventBusSubscriber` → `net.neoforged.fml.common.EventBusSubscriber`（建议写 `@EventBusSubscriber(modid = "shizuku")`；默认 Game 总线；MOD 总线用 `bus = EventBusSubscriber.Bus.MOD`）
- `net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext` → 已不存在；主类由我负责，子代理不要改 shizuku.java
- `net.minecraftforge.registries.DeferredRegister/RegistryObject` → `net.neoforged.neoforge.registries.DeferredRegister/DeferredHolder`
- `ForgeRegistries.SOUND_EVENTS` → `BuiltInRegistries.SOUND_EVENT`；`ForgeRegistries.ENTITY_TYPES.getKey(type)` → `BuiltInRegistries.ENTITY_TYPE.getKey(type)`
- 事件：`LivingDeathEvent`→`net.neoforged.neoforge.event.entity.living.LivingDeathEvent`；`EntityJoinLevelEvent`→`net.neoforged.neoforge.event.entity.EntityJoinLevelEvent`；`PlayerChangedDimensionEvent/PlayerLoggedOutEvent`→`net.neoforged.neoforge.event.entity.player.PlayerEvent.*`；`ServerTickEvent`→`net.neoforged.neoforge.event.tick.ServerTickEvent`（Post，getServer()）；`TickEvent` 系列同理
- `net.minecraftforge.event.TickEvent` → `net.neoforged.neoforge.event.tick.*`
- `FMLEnvironment.dist.isClient()` → `net.neoforged.fml.loading.FMLEnvironment.dist.isClient()`
- `DistExecutor.unsafeRunWhenOn` → 不使用；见"客户端安全模式"
- 网络：`net.minecraftforge.network.simple.SimpleChannel` → `net.neoforged.neoforge.network.simple.SimpleChannel`；
  构建方式：
  ```java
  SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder
      .named(ResourceLocation.fromNamespaceAndPath("shizuku", "main"))
      .networkProtocolVersion(1)
      .clientAcceptedVersions((s, v) -> true)
      .serverAcceptedVersions((s, v) -> true)
      .simpleChannel();
  ```
  注意 21.1.228 的 `networkProtocolVersion(int)` 是否可用——若不可用改用无参 `networkProtocolVersion()`；以编译为准。
  消息注册 `messageBuilder(cls, id, PlayDirection.PLAY_TO_CLIENT)`（`net.neoforged.neoforge.network.PlayDirection`）→ `.encoder/.decoder/.consumerMainThread/.add()`；decoder 参数类型为 `net.minecraft.network.RegistryFriendlyByteBuf`；handle 签名 `void handle(IPayloadContext)`？——21.1.228 的 consumer 回调：`consumerMainThread((msg, ctx) -> {...})`，ctx 为 `SimpleChannel.MessageContext`，用 `ctx.getSender()` 而非旧 ctx；写完以编译为准。
- 音效/注册：`SoundEvent.createVariableRangeEvent` 保留（1.21.1 仍存在）
- `entity.spawnAtLocation(ItemStack)` 保留

## 拔刀剑 1.20.1 → 1.21.1 API 差异（重要）
- `stack.getCapability(ItemSlashBlade.BLADESTATE)` → `BladeStateAccess.of(stack)`，返回 `Optional<ISlashBladeState>`
  （`mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess` / `ISlashBladeState`）
- `SBItems.slashblade` → `SlashBladeItems.SLASHBLADE.get()`（`mods.flammpfeil.slashblade.registry.SlashBladeItems`）
- `ConcentrationRankCapabilityProvider.RANK_POINT` → `CapabilityConcentrationRank.RANK_POINT`（`mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank`），
  用法：`entity.getData(CapabilityConcentrationRank.RANK_POINT.get()).getRankLevel(level.getGameTime())` 返回 float
- `SlashBlade.RegistryEvents.JudgementCut` → `mods.flammpfeil.slashblade.RegistryEvents.JudgementCut`（同名字段还有 SlashEffect、Drive）
- `KnockBacks` 现为枚举 `mods.flammpfeil.slashblade.util.KnockBacks`，`KnockBacks.cancel` 不变
- `SakuraEnd.doSlash(player, roll, colorCode, Vec3 offset, mute, critical, damage, KnockBacks)` 签名不变
- `AttackManager.doSlash(player, roll, Vec3, mute, critical, double comboRatio)`、`playQuickSheathSoundAction` 不变
- `Drive.doSlash(player, roll, lifetime, Vec3 offset, critical, damage, speed)` 不变
- `EntityJudgementCut(EntityType<? extends Projectile>, Level)` 不变；setColor(int)/setRank(float)/setSilent 可用
- `EntityDrive` 无变化；`drive.setSilent(true)` 可用
- `StunManager.setStun(LivingEntity target, LivingEntity attacker)` 静态方法不变
- `ComboState.Builder`（startAndEnd/priority/motionLoc/next/nextOfTimeout/addTickAction/addHitEffect/releaseAction）、`ComboState.TimeoutNext.buildFromFrame`、`TimeLineTickAction.getBuilder().put(tick, Consumer).build()` 均不变
- `SlashArts` 构造 `new SlashArts(Function<LivingEntity, ResourceLocation>)` 不变；`SlashArts.REGISTRY_KEY` 不变
- `SpecialEffect(int requestLevel)`、`SpecialEffect.isEffective(ResourceLocation id, int level)`、`SpecialEffect.REGISTRY_KEY` 不变
- `SlashBladeEvent.UpdateEvent`（getEntity()/getSlashBladeState()）、`HitEvent`（getUser()）、`DoSlashEvent` 不变
- `SlashBlade.prefix(String)` 不变；`DefaultResources.ExMotionLocation` 不变
- 刀状态读写方法（setTranslationKey/setSlashArtsKey/setComboRoot/addSpecialEffect/hasSpecialEffect/getColorCode/setColorCode/setModel/setTexture/getAttackAmplifier 等）通过 `BladeStateAccess.of(stack).ifPresent/…` 使用，签名不变

## 客户端安全模式（防专用服务器崩溃）
公共类里绝不直接引用客户端类（含我们自己的 @OnlyIn 类）。需要客户端行为时：
```java
if (FMLEnvironment.dist.isClient()) {
    com.buwan.shizuku.client.SomeClientProxy.doX(...);
}
```
其中 SomeClientProxy 标注 `@OnlyIn(Dist.CLIENT)`。不要写 DistExecutor。

## 常用 SRG → Mojang（1.20.1→1.21.1）速查
- m_9236_() → level()
- m_21153_(f) → setHealth(f)；m_21223_() → getHealth()；m_21233_() → getMaxHealth()
- m_6469_(src, f) → hurt(src, f)
- m_269291_() → damageSources()；m_269264_() → genericKill()；m_269425_() → fellOutOfWorld()；m_269341_() → outOfWorld()；m_269075_(att) → playerAttack(att)
- m_142687_(reason) → discard()（1.21.1 的 RemovalReason.KILLED → RemovalReason.KILLED 同名保留；DISCARDED 保留）
- m_6074_() → kill()；m_146870_() → discard()（旧 kill→discard 语义按上下文判断）
- m_20148_() → getUUID()；m_19879_() → getId()；m_20185_/86_/89_() → getX/Y/Z()；m_6021_(x,y,z) → setPos；m_20182_() → position()；m_20270_(e) → distanceTo(e)
- m_7755_() → getName()；m_6095_() → getType()；m_213877_() → isRemoved()；m_20147_() → isInvulnerable()
- m_217043_() → getRandom()；m_188501_() → nextFloat()；m_19998_/m_19983_(stack) → spawnAtLocation(stack)
- m_21205_() → getMainHandItem()；m_21206_() → getOffhandItem()；m_20158_() → getHandSlots()
- m_41763_() → isDamageableItem()；m_41773_() → getDamageValue()；m_41721_(i) → setDamageValue(i)；m_41782_() → hasTag()；m_41783_() → getOrCreateTag()；m_41751_ → setTag；m_41619_() → isEmpty()
- m_128379_ → putBoolean；m_128405_ → putInt；m_128473_ → remove；m_128441_ → contains；m_128356_ → putLong
- m_21187_() → tickCount；m_21219_() → setInvulnerable(true)；m_20331_(b) → setGlowingTag(b)? 请按语义核对（m_20331_ 为 setSharedFlag；疑似 setSharedFlagOnFire? 以 1.21.1 源码为准）
- m_20011_(aabb) → setBoundingBox(aabb)；m_213846_(c) → sendSystemMessage(c)；m_237115_ → literal；m_237113_ → translatable
- m_20194_() → getServer()；m_6846_() → getPlayerList()；m_11314_() → getPlayers()；m_129785_() → getAllLevels()；m_129921_() → getTickCount()
- m_8583_() → getAllEntities()（Level）；m_45976_(cls, aabb) → getEntitiesOfClass(cls, aabb)
- m_7654_() → getServer()；m_109153_() → getCameraEntity()；m_90583_() → getPosition()
- m_85850_() → last()；m_252922_() → pose()（Matrix4f）
- m_173350_ → setSampler；m_173348_ → getUniform；m_7971_/m_5889_/m_5985_/m_5679_ → set；m_173362_ → apply()
- m_85913_() → Tesselator.getInstance()；m_85915_() → getBuilder()；m_166779_(mode, fmt) → begin；m_5483_ → vertex；m_5752_ → uv(0,0)；m_231175_ → build()（BufferBuilder.RenderedBuffer）；BufferUploader.m_231202_ → BufferUploader.drawWithShader
- m_130077_/m_130259_ → writeUUID/readUUID
- f_46443_ → isClientSide；f_82478_ → Vec3.ZERO；f_82479_/80_/81_ → x/y/z；f_85814_ → DefaultVertexFormat.POSITION_TEX? 以源码核对（渲染全屏四边形用 POSITION_TEX）
- f_91073_ → level（Minecraft）；f_91074_ → player；f_91063_ → gameRenderer；m_91385_() → getMainRenderTarget()；m_83980_ → getDepthTextureId()
- f_20911_（LivingEntity.health 反射）→ 1.21.1 无此私有字段 hack；用 setHealth()
- 其余未列出的：对照 1.21.1 源码语义翻译。

## 明确禁止
- 不移植 `com.buwan.shizuku.agent.*`（java agent/ASM，1.21.1 不适用）。
- 不用 sun.misc.Unsafe、不用 SRG 反射 hack（getDeclaredField("m_xxx")/f_xxx 全部删除或改为公共 API）。
- 不修改 build.gradle / mods.toml / 资源（由主控负责）。

## 验证
子代理无法编译时，允许用以下命令做语法级自查（工作目录 `Shizuku-Blade\Shizuku-Blade-NeoForge-1.21.1`）：
```
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'; .\gradlew.bat compileJava
```
（依赖已就绪；gradlew 需用 JDK21；若 wrapper 无法用则跳过，把疑似错误留给主控。）

## 1.2.0 编译/运行修复实录（第二批）
- `Level.getAllEntities()` 不存在（仅 `ServerLevel.getAllEntities()`）；`Level` 只有 protected `getEntities()` → Helper 改为 `instanceof ServerLevel` 分支。
- `Entity.setSecondsOnFire(int)` 不存在 → `setRemainingFireTicks(60)`（1.21.1 只有 remainingFireTicks）。
- `AttributeModifier` 是 record：用 `amount()` / `id()` / `operation()`，无 getAmount()/getId()。
- `Player.connection` 不存在（移到 `LocalPlayer`/`ServerPlayer`）→ 客户端用 `Minecraft.getConnection()`（@Nullable ClientPacketListener，`sendUnsignedCommand` 仍是 public）。
- `ItemStack.getTag()/getOrCreateTag()` 已移除 → `stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()`；写回用 `stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))`。
- `ModAttributes.getSlashBladeDamage()` 返回裸 `Attribute` → 用 `ModAttributes.SLASHBLADE_DAMAGE`（DeferredHolder = Holder<Attribute>）。
- `getExperienceReward()` → `getExperienceReward(ServerLevel, @Nullable Entity)`（public final，LivingEntity）。
- `LivingEntity.getLootTable()` 返回 `ResourceKey<LootTable>`（非 Optional，非 ResourceLocation）。
- Mixin 陷阱：`ItemStack.hurtAndBreak` 有 3 个重载（ServerPlayer 版 / LivingEntity 版 / LivingEntity+EquipmentSlot 版），`@Inject(method="hurtAndBreak")` 按名匹配会命中 ServerPlayer 重载 → 描述符不匹配直接 FATAL 崩溃。必须写完整描述符 `hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V`。
- Resharped 1.21.1 的 `ItemSlashBlade.hurtEnemy/mineBlock` 调用 4 参 `hurtAndBreak(int, ServerLevel, LivingEntity, Consumer)` → ItemSlashBladeMixin 的 INVOKE 目标用该描述符即可（require=0 容错）。
- `@EventBusSubscriber(bus = Bus.MOD)` 已弃用（removal 警告）→ 删掉 bus 参数，自动探测（RegisterShadersEvent 仍走 MOD 总线）。
- DataComponents.CUSTOM_DATA 是 `DataComponentType<CustomData>`（包装类型），不是 CompoundTag。
- **双端陷阱（重要）**：Forge 1.20.1 的 `PlayerTickEvent` 有 `event.side`（原版守卫 `!event.side.isClient()`）；NeoForge 1.21.1 的 `PlayerTickEvent.Pre/Post` **没有 side 字段，双端都触发**。单人游戏中客户端与集成服务器共享同一份静态状态，客户端 tick 也会命中 SA 数据 → 必须用 `if (!(player.level() instanceof ServerLevel)) return;` 做服务端过滤（客户端 LocalPlayer.level() 是 ClientLevel，强转 ServerLevel 会 ClassCastException 崩游戏）。所有 SA 的 onPlayerTick 都要加此守卫。
- **网络包陷阱（重要）**：`PacketDistributor.sendToPlayersNear(level, excluded, x, y, z, radius, payload)` 第二参数是 **excluded**！原版 Forge 的 `PacketDistributor.NEAR`（发给追踪施法者的玩家）**包含施法者本人**；移植时若把 player 填进 excluded，单人游戏里包发给零人，客户端特效全部不可见。正确写法：excluded 传 `null`。
- **BufferBuilder 陷阱（重要）**：1.21.1 的 `BufferBuilder.build()/buildOrThrow()` 会把 building 状态置 false，继续 addVertex 会抛 `IllegalStateException("Not building!")`。多层绘制（球体分层/线框）必须每层重新 `Tesselator.getInstance().begin(mode, format)`，不能共用一个 buffer 连续 build 多次。且对空 buffer 调 buildOrThrow 会抛 "BufferBuilder was empty"。
- **玩家传送陷阱**：服务器端对 Player 用 `setPos()` 只改服务端坐标，客户端移动包会在下一 tick 把位置"回滚"；且 Resharped 1.21.1 的 `AttackManager.areaAttack` 伤害框以**玩家当前坐标**为中心（不是斩击特效位置）。需要真实传送时（如瞬空斩）必须用 `ServerPlayer.teleportTo(ServerLevel, x, y, z, yaw, pitch)`（发 ClientboundPlayerPositionPacket 并锁定位置确认）。

## 3.5.0 移植实录（雫工作台 + 抹除体系）
- **反编译**：`shizuku-blade-3.5.0.jar` → `.ref\decompiled\own-mods\modrinth35\src`；差异分析：新增雫工作台子系统（block/tile/menu/container/slot/crafting/JEI + 资源）、mixin 抹除体系（19 mixin + YmsaForceRemovePacket/YmsaLogic/YmsaClientErase/YmsaRenderKiller）、`sa/Ymsa.java` 整体重写；其余共享类与资源 MD5 完全一致。lang 新增 4 键（工作台名、JEI 分类、ymsa_kill 死亡信息×2）。
- **自定义配方（1.21.1 新范式）**：`RecipeSerializer<T>` 改为接口，需实现 `MapCodec<T> codec()` + `StreamCodec<RegistryFriendlyByteBuf,T> streamCodec()`；`Recipe.matches(RecipeInput, Level)` / `assemble(RecipeInput, HolderLookup.Provider)`；配方 JSON 的 `type` 字段映射 **RECIPE_SERIALIZER** 注册表（不是 RecipeType）；自定义 RecipeType 用 `RecipeType.simple(id)` 并（可选）注册进 `Registries.RECIPE_TYPE`。
- **Codec 陷阱（重要）**：解析 pattern key 用 **`ExtraCodecs.strictUnboundedMap(Codec.STRING, Ingredient.CODEC_NONEMPTY)`**（原版 ShapedRecipePattern 同款）。`Codec.unboundedMap` 会**静默丢弃解析失败的条目**（实测 blade 配料条目全部丢失，只剩普通物品条目，报"符号未定义"）。
- **SlashBlade 1.21.1 配料 JSON 格式变了**：`{"type":"slashblade:blade","item":"..."}` → `{"type":"slashblade:blade","items":["slashblade:slashblade"],"request":{"name":"..."}}`（item 单数 → items 数组）。
- **blade 产出**：`access.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).getOrThrow(key)` 返回 **Holder.Reference<T>**，需 `.value().getBlade(access)`。
- **菜单**：`IForgeMenuType.create` → `IMenuTypeExtension.create(factory)`（IContainerFactory: `(int, Inventory, RegistryFriendlyByteBuf)`）；`NetworkHooks.openScreen` → `serverPlayer.openMenu(provider, pos)`；屏幕注册走 `RegisterMenuScreensEvent`（MOD 总线，client）。
- **ItemStackHandler**（NeoForge 1.21.1）：`serializeNBT(HolderLookup.Provider)` / `deserializeNBT(HolderLookup.Provider, CompoundTag)`（多了参数）。
- **1.21.1 无 RecipeHolder 菜单接口**：ResultSlot 的 awardUsedRecipes 逻辑（1.21.2+）不存在；`getRecipeFor` 返回 `Optional<RecipeHolder<T>>`，需 `.value()`；`getRemainingItemsFor(type, craftingContainer.asCraftInput(), level)`。
- **TransientCraftingContainer 直接 implements CraftingContainer**（不继承 SimpleContainer）；`CraftingContainer.asCraftInput()` 显式转换 RecipeInput。
- **ymsa_kill 伤害类型**：1.21.1 的 damage_type JSON **没有内联 "tags" 数组**；bypass 标签需放 `data/minecraft/tags/damage_type/*.json`（replace:false 追加）。伤害源用 `level.damageSources().source(ResourceKey<DamageType>, attacker, attacker)`（1.21.1 是 public，无需 accessor）。
- **抹除体系 1.21.1 化**：19 个 mixin 缩减为 1 个（`LivingEntityAccessor` 暴露私有静态 `DATA_HEALTH_ID`，接口静态方法 @Accessor）；服务器实体管理器直删 → `discard()` + `ServerChunkCache.removeEntity(entity)`；防重生 → `EntityJoinLevelEvent.setCanceled(true)`；tick 抑制 → `EntityTickEvent.Pre`（cancellable）取消；掉落后清理 → `LivingDropsEvent`；周期净化 → `ServerTickEvent.Post`；客户端抹除 → `ClientLevel.removeEntity(id, RemovalReason)`（public）+ `YmsaForceRemovePacket` + `NoopRenderer` 替换渲染器（公共 API，无渲染 mixin）。
- **JEI 19.44**：`AbstractRecipeCategory<T>(RecipeType<T>, Component, IDrawable, w, h)`（`getIcon()` 是 final）；布局方法名 `setRecipe(IRecipeLayoutBuilder, T, IFocusGroup)`（不再是 createRecipeLayout）；`RecipeType.create(namespace, uid, class)`；transfer 便捷重载 `addRecipeTransferHandler(Class, MenuType, RecipeType, int, int, int, int)` 仍在。
- **`Entity.getParts()` 返回 `PartEntity<?>[]`**（NeoForge），非 EnderDragonPart[]；`DataComponents.UNBREAKABLE` 的值类型是 `Unbreakable(boolean showInTooltip)` 记录。
- **工作台合成失效的两连坑**：① `TransientCraftingContainer(menu, w, h)` 的 w/h 在 1.21.1 会被 `asCraftInput()` 用来构造 CraftingInput——1.20.1 传 `(size,size)=(81,81)` 的写法在 1.21.1 会产生 81×81 维度（越界/永不匹配），必须传 `(9,9)`；② `TransientCraftingContainer.getItems()` 返回其**内部**列表，若子类把读写委托给外部 ItemStackHandler，必须同时覆盖 `getItems()`（asCraftInput 依赖它），否则配方永远匹配不到任何物品。
- **薙草之稻光 = el 命名刀**（el.json 的 name 字段是 `shizuku:engulfing_lightning`）；主类创造栏特制刀在 3.5.0 是**无名无铭测试刀**（只设 comboRoot + 喵喵 SE），不要给它设 translationKey/model，否则会造出第二把"薙草之稻光"。
