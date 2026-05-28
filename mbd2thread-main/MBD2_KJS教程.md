# MBD2 KubeJS 完整开发教程

> 适用版本：Multiblocked2 1.20.1 + KubeJS 6.x（ProbeJS 已生成）
> 内容来源：源码逐文件解析（KJS 集成模块 `integration/kubejs/` + ProbeJS 类型定义 `.probe/`）
> 关联文档：节点图教程见 `MBD2_线图教程.md`

---

## 目录

1. [核心架构](#第一章-核心架构)
2. [启动注册（startup_scripts）](#第二章-启动注册startup_scripts)
3. [服务端机器事件（server_scripts）](#第三章-服务端机器事件server_scripts)
4. [客户端事件（client_scripts）](#第四章-客户端事件client_scripts)
5. [配方脚本（server_scripts/recipes）](#第五章-配方脚本server_scriptsrecipes)
6. [配方类型事件](#第六章-配方类型事件)
7. [实战案例](#第七章-实战案例八例)
8. [节点图 vs KJS 决策](#第八章-节点图-vs-kjs-决策)
9. [避坑清单](#第九章-避坑清单必读)
10. [ProbeJS 查找指南](#第十章-probejs-查找指南)

---

## 第一章 核心架构

### 1.1 集成方式

MBD2 通过实现 `KubeJSPlugin` 接入：

```Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/MBDKubeJSPlugin.java:31-46
public void registerClasses(ScriptType type, ClassFilter filter) {
    super.registerClasses(type, filter);
    filter.allow("com.lowdragmc.mbd2");
}
```

**结论**：

- 所有 `com.lowdragmc.mbd2.**` 包下的 Java 类**全部对脚本开放**，可以直接 `Java.loadClass("com.lowdragmc.mbd2....")` 或通过 ProbeJS 类型推断使用。
- 三类脚本（`startup_scripts`/`server_scripts`/`client_scripts`）的事件分发各自独立。

### 1.2 三个事件分组

| EventGroup 名 | 来源类 | 何时触发 | 脚本类型 |
|---|---|---|---|
| `MBDRegistryEvents` | `MBDStartupEvents` | KubeJS 启动阶段 | `startup_scripts` |
| `MBDMachineEvents` | `MBDMachineEvents` | 单台机器实例触发任意 `MachineEvent` 时 | `server_scripts` + `client_scripts` |
| `MBDRecipeTypeEvents` | `MBDRecipeTypeEvents` | 配方类型相关事件 | `server_scripts` + `client_scripts` |

### 1.3 全局绑定（Bindings）

脚本里**直接可用**的全局名（无需 import）：

| 名字 | 类型 | 用途 |
|---|---|---|
| `MachineState` | `com.lowdragmc.mbd2.common.machine.definition.config.MachineState` | 自定义机器状态 |
| `MBDRegistries` | `MBDRegistries` | 访问机器/配方类型注册表 |
| `Shapes` | `net.minecraft.world.phys.shapes.Shapes` | 构造碰撞箱 |
| `ConfigBlockProperties` | `ConfigBlockProperties` | 方块属性配置 |
| `IO` | `com.lowdragmc.mbd2.api.capability.recipe.IO` | NONE/IN/OUT/BOTH |
| `CapabilityIO` | `CapabilityIO` | 能力 IO 配置 |
| `ContentModifier` | `com.lowdragmc.mbd2.api.recipe.content.ContentModifier` | 配方内容修改器 |
| `CreateMachineState` | `CreateMachineState` | 仅在装了 Create 时存在 |

### 1.4 KJS 与节点图的关系

`MachineEvent.postCustomEvent()` 同时分发到：

1. **图执行器**（`MachineEventGraphProcessor`）—— 机器定义里编辑的节点图
2. **KubeJS 事件总线**（`MBDServerEvents.postMachineEvent` / `MBDClientEvents.postMachineEvent`）

**两者并行执行，互不感知**。一个事件可能同时被节点图和 KJS 脚本处理。

| 维度 | 节点图 | KubeJS |
|---|---|---|
| 编辑方式 | GUI 拖线 | 文本编程 |
| 数据持久化 | 仅 customData | customData + 全局变量 + Java 对象 |
| 跨机器协作 | 困难 | 容易（脚本里维护全局 Map） |
| 复杂分支/循环 | 受限于内置节点 | JS 完全表达力 |
| 性能 | 节点开销小（编译为执行链） | 反射调用，热路径慢 |
| 可分发 | 随机器定义打包 | 整合包级别 |
| 调试 | 编辑器内 print | console + IDE |
| 适合场景 | 简单状态机、配方修改 | 复杂逻辑、跨模组联动、动态注册 |

详见 [第八章决策表](#第八章-节点图-vs-kjs-决策)。

### 1.5 脚本路径约定

```
kubejs/
├── startup_scripts/          # 注册：机器、配方类型
│   └── mbd_machines.js
├── server_scripts/           # 服务端事件、配方
│   ├── mbd_events.js
│   └── recipes/
│       └── mbd_recipes.js
└── client_scripts/           # 客户端事件
    └── mbd_client.js
```

---

## 第二章 启动注册（startup_scripts）

### 2.1 事件清单

```Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/events/MBDStartupEvents.java:6-10
public interface MBDStartupEvents {
    EventGroup REGISTRY_EVENTS = EventGroup.of("MBDRegistryEvents");
    EventHandler MACHINE = REGISTRY_EVENTS.startup("machine", () -> MBDMachineRegistryEventJS.class);
    EventHandler RECIPE_TYPE = REGISTRY_EVENTS.startup("recipeType", () -> MBDRecipeTypeRegistryEventJS.class);
}
```

| 脚本写法 | EventJS 类 | 用途 |
|---|---|---|
| `MBDRegistryEvents.machine(e => {...})` | `MBDMachineRegistryEventJS` | 注册机器定义 |
| `MBDRegistryEvents.recipeType(e => {...})` | `MBDRecipeTypeRegistryEventJS` | 注册配方类型 |

### 2.2 注册配方类型

最简单的注册：

```javascript
// startup_scripts/mbd_machines.js
MBDRegistryEvents.recipeType(event => {
    let recipeType = event.createRecipeType('mymod:my_furnace')
    // recipeType 是 MBDRecipeType，可以链式调 setMaxLookupCount / setSearchRecipe 等
})
```

**`MBDRecipeTypeRegistryEventJS` API**：

| 方法 | 返回 | 说明 |
|---|---|---|
| `createRecipeType(id: ResourceLocation)` | `MBDRecipeType` | 创建并立即注册 |
| `getRecipeType(id)` | `MBDRecipeType?` | 查询已注册类型 |
| `removeRecipeType(id)` | `void` | 卸载已注册类型 |

源码：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/events/MBDRecipeTypeRegistryEventJS.java:10-26`

### 2.3 注册机器定义

```Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/events/MBDMachineRegistryEventJS.java:18-27
public MBDMachineDefinition.Builder create(String machineType, ResourceLocation machineID) {
    var builderCreator = BUILDERS.get(machineType);
    if (builderCreator == null) {
        throw new IllegalArgumentException("Unknown machine type: " + machineType);
    }
    var builder = builderCreator.get();
    builder.id(machineID);
    machineBuilders.put(machineID, builder);
    return builder;
}
```

| 方法 | 说明 |
|---|---|
| `create(machineType: String, machineID: ResourceLocation)` | 创建 Builder。`machineType` 必须是 `MBDMachineRegistryEventJS.BUILDERS` 里已注册的键 |
| `removeMachine(id)` | 取消注册 |
| `getMachine(id)` | 查询已注册定义 |

**`machineType` 内置值**：

- `"machine"` —— 普通机器
- `"part"` —— 多方块部件
- `"multiblock"` —— 多方块控制器
- 其他模组（GTM/Mekanism）可能注册更多类型；查 `BUILDERS` Map

> 这些键在 `CommonProxy` 里通过 `MBDMachineRegistryEventJS.BUILDERS.put(...)` 静态注册。具体可用值以你环境里的 `MBDMachineRegistryEventJS.BUILDERS.keySet()` 为准。

### 2.4 GUI 编辑器与 KJS 的关系（必读）

MBD2 提供了一个 **GUI 项目编辑器**（`MachineEditor`，在安装了 `mbd2:gadgets_item` 道具后右键打开）。其保存/加载机制与 KJS **完全独立**：

| 项 | GUI 编辑器项目文件 | KJS Startup 脚本 |
|---|---|---|
| 存储格式 | NBT 二进制（`NbtIo.write`） | 脚本运行时动态构造 |
| 文件后缀 | `.sm` / `.mb` / `.rt`（来自 `IProject.getSuffix()`） | 无 |
| 加载途径 | mod Java 代码监听 `MBDRegistryEvent.Machine`、调 `registerFromResource(class, type, "<modid>/foo.sm")` 或 `registerFromFile(type, File)` | KubeJS 在 `MBDRegistryEvents.machine` 事件中调 `event.create(...)` |
| 额外能力 | OP 命令 `/mbd2 reload_machine_projects` 热重载 | KJS reload （仅重跑脚本） |
| 能互联吗？ | **不能**。KJS 无 API 从 `.sm` 文件加载。Java.loadClass 调 Forge 事件也不可靠（mod bus） | 同左 |

**两种工作流选择**：

1. **全 KJS 流**：从 `MBDRegistryEvents.machine` 中用空白 Builder 构造机器。优点：整合包可迭代；缺点：写 Builder 链代码量大，无可视化预览。
2. **混合流**：机器本体由你的 mod（或中间 mod）用项目文件 + Forge 事件注册，KJS 仅负责事件脚本与配方。优点：可视化调 + 脚本灵活调调；缺点：需要你的 mod 集成项目文件。

> **不要期望** KJS 能读取 `.sm` 文件。代码路径：`MBDRegistryEvent.Machine` 是 Forge mod bus 事件，只在 mod 初始化期发。运行期调用 `MinecraftForge.EVENT_BUS.post(...)` 后事件会被接收，但注册表可能已 frozen（`MBDRegistry.frozen=true`）会招错。

### 2.5 注册时机（关键）

源码 `afterPosted`：

```Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/events/MBDMachineRegistryEventJS.java:40-43
@Override
protected void afterPosted(EventResult result) {
    machineBuilders.forEach((id, builder) -> MBDRegistries.MACHINE_DEFINITIONS.register(id, builder.build()));
}
```

**机器在事件结束后才统一 `build()` 注册**。这意味着：

- 你只能在事件回调里**配置 Builder**，不能在事件结束后再改
- 同一 ID 多次 `create` 会覆盖前一次的 Builder（HashMap put）
- 异常时整个事件抛错，已收集的 Builder 全部不注册

### 2.6 完整注册示例

```javascript
// startup_scripts/01_register_machines.js

MBDRegistryEvents.recipeType(event => {
    event.createRecipeType('mymod:high_furnace')
        // 链式调用 MBDRecipeType 的方法（参考 ProbeJS 类型）
})

MBDRegistryEvents.machine(event => {
    let builder = event.create('machine', 'mymod:high_furnace_machine')
    
    // builder 是 MBDMachineDefinition.Builder
    // 具体可用方法见 ProbeJS 类型定义：
    // .probe/shared/com.lowdragmc.mbd2.d.ts 搜索 "MBDMachineDefinition$Builder"
    
    // 典型配置（具体方法以 ProbeJS 推断为准）：
    // builder.blockProperties(...)
    // builder.recipeType(...)
    // builder.machineEvents(...)
})
```

> **重要澄清**：GUI 编辑器保存的是 **NBT 二进制项目文件**（后缀 `.sm`/`.mb`/`.rt`，`IProject.saveProject` 走 `NbtIo.write`），**不是 JSON**。这些项目文件只能通过 mod 自身的 Forge 事件 `MBDRegistryEvent.Machine.registerFromFile / registerFromResource` 加载，**无法被 KJS 直接消费**。详见下节。

---

## 第三章 服务端机器事件（server_scripts）

### 3.1 通用形式

所有机器事件都遵循同一结构：

```javascript
MBDMachineEvents.<eventName>('<machine_id>', e => {
    // e 是 MachineEventJS<具体事件类>
    // e.event 是底层 MachineEvent 子类
    // 通过 e.event.<字段> 访问参数
})
```

**`<machine_id>`** 是 `Extra.ID` 过滤，对应**机器定义的 ID**（不是 RecipeType ID）。源码：

```Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/events/MBDServerEvents.java:151-157
static <E extends MachineEvent> EventHandler registerMachineEvent(...) {
    var handler = MBDMachineEvents.MBD_MACHINE_EVENTS.server(name, () -> eventJSClass).extra(Extra.ID);
    machineEventHandlers.put(eventClass, event -> handler.post(eventJSFactory.apply((E) event), 
        event.machine.getDefinition().id()));
    return handler;
}
```

不传 ID = 监听**所有**机器：

```javascript
MBDMachineEvents.onTick(e => { /* 全部机器 */ })
MBDMachineEvents.onTick('mymod:my_machine', e => { /* 仅这台 */ })
```

### 3.2 22 个服务端事件签名表

源码：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/events/MBDServerEvents.java:20-145`

| 脚本事件名 | 底层 MachineEvent | 关键字段（`e.event.xxx`） | 可取消 | 说明 |
|---|---|---|---|---|
| `onLoad` | `MachineOnLoadEvent` | `machine` | ✗ | BlockEntity 加载 |
| `onPlaced` | `MachinePlacedEvent` | `machine`, `player`, `itemStack` | ✗ | 玩家放置完成 |
| `onRemoved` | `MachineRemovedEvent` | `machine` | ✗ | 移除前 |
| `onTick` | `MachineTickEvent` | `machine` | ✗ | 服务端每 tick |
| `onNeighborChanged` | `MachineNeighborChangedEvent` | `machine`, `block`, `pos` | ✗ | 邻居方块变化 |
| `onRightClick` | `MachineRightClickEvent` | `machine`, `player`, `heldItem`, `hand`, `hit`, `interactionResult` | ✓ | 右键交互（写 `interactionResult` 为 `InteractionResult.SUCCESS` 拦截原生） |
| `onOpenUI` | `MachineOpenUIEvent` | `machine`, `player` | ✓ | GUI 打开前 |
| `onUI` | `MachineUIEvent` | `machine`, `player` | ✗ | UI 构建（修改 root widget） |
| `onDrops` | `MachineDropsEvent` | `machine`, `entity`, `drops`(in/out) | ✗ | 方块掉落物 |
| `onStateChanged` | `MachineStateChangedEvent` | `machine`, `oldState`, `newState` | ✓ | 自定义状态机切换 |
| `onRecipeStatusChanged` | `MachineRecipeStatusChangedEvent` | `machine`, `oldStatus`, `newStatus` | ✗ | IDLE/WORKING/WAITING/SUSPEND 切换 |
| `onBeforeRecipeModify` | `MachineRecipeModifyEvent.Before` | `machine`, `recipe`（可读写） | ✓ | 找到配方后、开工前；写 `e.event.recipe = null` 拒绝 |
| `onAfterRecipeModify` | `MachineRecipeModifyEvent.After` | `machine`, `recipe` | ✗ | 修改后视图（观测） |
| `onFuelRecipeModify` | `MachineFuelRecipeModifyEvent` | `machine`, `recipe`（可读写） | ✓ | 燃料配方专用 modify |
| `onBeforeRecipeWorking` | `MachineBeforeRecipeWorkingEvent` | `machine`, `recipe` | ✓ | 开工前最后拦截 |
| `onRecipeWorking` | `MachineOnRecipeWorkingEvent` | `machine`, `recipe`, `progress` | ✓ | 工作中每 tick；取消可暂停推进 |
| `onRecipeWaiting` | `MachineOnRecipeWaitingEvent` | `machine`, `recipe` | ✗ | 进 WAITING（输出爆仓） |
| `onConsumeInputsAfterWorking` | `MachineOnConsumeInputsAfterWorkingEvent` | `machine`, `recipe` | ✗ | 输入消耗完毕 |
| `onRecipeFinish` | `MachineOnRecipeFinishEvent` | `machine`, `recipe` | ✗ | 配方完成 |
| `onAfterRecipeWorking` | `MachineAfterRecipeWorkingEvent` | `machine`, `recipe` | ✗ | 整体工作结束 |
| `onFuelBurningFinish` | `MachineFuelBurningFinishEvent` | `machine`, `recipe?` | ✗ | 燃料烧完 |
| `onUseCatalyst` | `MachineUseCatalystEvent` | `machine`, `catalyst`, `player`, `hand` | ✓ | 使用催化剂（多方块） |
| `onStructureFormed` | `MachineStructureFormedEvent` | `machine` | ✗ | 多方块结构成型 |
| `onStructureInvalid` | `MachineStructureInvalidEvent` | `machine` | ✗ | 多方块结构破坏 |

### 3.3 取消事件

事件基类 `MachineEvent` 继承自 Forge `Event`。可取消事件标有 `@Cancelable`。**指定取消走 Forge API**：

```javascript
MBDMachineEvents.onRightClick('mymod:locked_chest', e => {
    if (!e.event.player.isCreative()) {
        e.event.setCanceled(true)        // 拦截原生右键
    }
})
```

**或用 KJS 的 EventJS API**：

```javascript
MBDMachineEvents.onBeforeRecipeWorking('mymod:my_machine', e => {
    if (someCondition) {
        e.cancel()                       // KJS EventJS 标准方法，返回 EventResult.INTERRUPT_FALSE
    }
})
```

**两者区别**（源码 `MachineEvent.postKubeJSEvent`）：

- `e.cancel()` 仅表示本 KJS 脚本中断后续处理，**同时**最终会被 MBD2 转换为 `setCanceled(true)`（如果事件可取消）。
- `e.event.setCanceled(true)` 直接作用于 Forge 事件，其他 KJS 订阅者仍然会收到该事件。

> 不要写 `e.event.cancel = true`。`cancel` 是图层暴露的虚拟参数名，**在事件 Java 对象上不存在该字段**。

### 3.4 修改配方（Before/After Modify）

**`onBeforeRecipeModify`** 是 KJS 替代节点图最常用的入口：

```javascript
MBDMachineEvents.onBeforeRecipeModify('mymod:overclock_furnace', e => {
    let originalRecipe = e.event.recipe     // MBDRecipe，原配方
    if (originalRecipe == null) return
    
    // 1. 拒绝特定配方
    if (originalRecipe.id.toString() === 'minecraft:dangerous') {
        e.event.recipe = null
        return
    }
    
    // 2. 修改时长（超频）
    // copy(modifier, modifyDuration, io) — io=NONE 仅改 duration
    let modified = originalRecipe.copy(
        ContentModifier.multiplier(0.5),  // 乘数
        true,                              // 修改 duration
        IO.NONE                            // 不修改 input/output 内容
    )
    
    e.event.recipe = modified
})
```

**`MBDRecipe.copy` 重载表**（源码：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/api/recipe/MBDRecipe.java:97-117`）：

| 签名 | 说明 |
|---|---|
| `copy()` | 深拷贝（不修改任何值） |
| `copy(ResourceLocation id)` | 改 id 后拷贝 |
| `copy(ContentModifier m)` | 同 `copy(m, true, IO.BOTH)` |
| `copy(ContentModifier m, boolean modifyDuration)` | io 默认 BOTH |
| `copy(ContentModifier m, boolean modifyDuration, IO io)` | 全参控制 |

> **关键澄清（节点图 vs KJS 不同）**：
>
> - `MachineRecipeModifyEvent` 的 **Java 字段名叫 `recipe`**（单一字段）。
> - `recipe.in` / `recipe.out` 是**节点图暴露参数的 identity**（仅一对 Get/Set 表示同一字段的读/写口），在节点图编辑器中出现。
> - **KJS 访问的是 Java 对象本身**，读写同一字段都是 `e.event.recipe`。`['recipe.in']` / `['recipe.out']` 这种写法**不生效**（Java 对象上根本没有这个名字的字段）。

### 3.5 访问 `machine` 与世界

`e.event.machine` 是 `MBDMachine`，常用字段（详见 ProbeJS）：

```javascript
MBDMachineEvents.onTick('mymod:my_machine', e => {
    let machine = e.event.machine
    let level = machine.level                  // Level
    let pos = machine.pos                      // BlockPos
    let definition = machine.definition        // MBDMachineDefinition
    let customData = machine.getCustomData()   // CompoundTag
    let machineLevel = machine.machineLevel    // int
    
    if (level.isClientSide()) return           // 服务端事件理论上不会进，但保险
    
    // 持久化计数器
    let count = customData.getInt('tick_count') + 1
    customData.putInt('tick_count', count)
    machine.setCustomData(customData)          // 触发 CustomDataUpdateEvent
})
```

### 3.6 onDrops 改写掉落物

`MachineDropsEvent.drops` 是 `List<ItemStack>` 字段，外部调用方持有同一引用。**修改方式是改 List 内容**（`clear` + `add`），不是换引用。

```javascript
MBDMachineEvents.onDrops('mymod:explodes_on_break', e => {
    let drops = e.event.drops               // List<ItemStack>
    
    // 追加额外掉落
    drops.add(Item.of('minecraft:diamond', 3).itemStack)
    
    // 或：清空 + 重写
    // drops.clear()
    // drops.add(Item.of('minecraft:emerald').itemStack)
})
```

> **与节点图不同**：节点图的 `drops.out` 另赋为新 List 是整体替换语义（由 `gatherParameters` 负责 `clear` + 重填）。KJS 直接操作 Java List 对象，添加/清空/修改都可以。

### 3.7 onUseCatalyst 多方块催化剂

```javascript
MBDMachineEvents.onUseCatalyst('mymod:reactor', e => {
    let catalyst = e.event.catalyst            // ItemStack
    let player = e.event.player
    
    if (catalyst.id !== 'minecraft:nether_star') {
        e.event.setCanceled(true)              // 不接受其他催化剂
    }
})
```

---

## 第四章 客户端事件（client_scripts）

### 4.1 事件清单

源码：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/events/MBDClientEvents.java:25-47`

| 脚本事件名 | 底层事件 | 关键字段 | 说明 |
|---|---|---|---|
| `onClientTick` | `MachineClientTickEvent` | `machine` | 客户端每 tick（仅渲染/特效） |
| `onCustomDataUpdate` | `MachineCustomDataUpdateEvent` | `machine`, `oldValue`, `newValue` | customData 同步到客户端 |
| `onCustomKeyframe`（仅 GeckoLib 加载时） | `MachineCustomKeyframeEvent` | `machine`, `instruction` | GeckoLib 动画关键帧自定义指令 |

也可在 client 监听**配方类型事件**：

| 脚本事件名 | 底层事件 | 说明 |
|---|---|---|
| `onRecipeUI` | `RecipeUIEvent` | 配方 JEI/EMI UI 渲染 |
| `onFuelRecipeUI` | `FuelRecipeUIEvent` | 燃料配方 UI 渲染 |

### 4.2 写法

```javascript
// client_scripts/mbd_client.js

MBDMachineEvents.onClientTick('mymod:smoking_machine', e => {
    let machine = e.event.machine
    let level = machine.level
    let pos = machine.pos
    
    // 添加粒子效果（每 5 tick 一次）
    if (level.gameTime % 5 === 0) {
        // 客户端粒子调用
        // level.addParticle(...) — 参考 KJS 客户端 API
    }
})

MBDMachineEvents.onCustomDataUpdate('mymod:my_machine', e => {
    let oldData = e.event.oldValue          // CompoundTag
    let newData = e.event.newValue          // CompoundTag
    // 触发客户端 UI 刷新等
})
```

### 4.3 客户端事件的限制

- **不要在客户端事件改服务端状态**（machine.setCustomData 会被覆盖）
- 只用于**视觉表现**（粒子、声音、客户端 UI）
- 单玩家世界服务端/客户端共享内存，仍然要严格区分逻辑

---

## 第五章 配方脚本（server_scripts/recipes）

### 5.1 Schema 自动注册

源码：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/MBDKubeJSPlugin.java:56-61`

```java
public void registerRecipeSchemas(RegisterRecipeSchemasEvent event) {
    for (var recipeType : MBDRegistries.RECIPE_TYPES) {
        event.register(recipeType.getRegistryName(), MBDRecipeSchema.SCHEMA);
    }
}
```

**结论**：**所有** `MBDRecipeType`（无论 GUI 编辑器创建还是 KJS 注册）都自动获得 KJS recipe API。

### 5.2 基础写法

```javascript
// server_scripts/recipes/my_recipes.js
ServerEvents.recipes(event => {
    // 第一个参数可选：指定 recipe ID（不传则 KJS 自动生成）
    event.recipes.mymod.high_furnace('mymod:smelt_iron_ingot')
        .duration(200)
        .inputItems('minecraft:iron_ingot')
        .outputItems('3x minecraft:iron_nugget')
})
```

调用形式 `event.recipes.<modid>.<path>(...)` 由 KubeJS 根据 RecipeType 的注册 ID 自动生成（`<modid>` 来自 ID 命名空间，`<path>` 来自路径，点号会变成下划线）。

### 5.3 完整 API 表

源码：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/recipe/MBDRecipeSchema.java:60-606`

#### 5.3.1 元信息

| 方法 | 默认值 | 说明 |
|---|---|---|
| `.duration(int)` | 100 | 配方时长（tick） |
| `.priority(int)` | 0 | 优先级，高的优先匹配 |
| `.isFuel(boolean)` | false | 是否燃料配方 |
| `.isXEIHidden(boolean)` | false | 是否在 JEI/EMI 隐藏 |

#### 5.3.2 NBT 数据（任意自定义）

| 方法 | 说明 |
|---|---|
| `.addData(key: String, value: Tag)` | 写任意 NBT |
| `.addDataString(key, value)` | 字符串 |
| `.addDataNumber(key, value)` | double |
| `.addDataBoolean(key, value)` | boolean |

> `data` 字段会随配方序列化，可在 `recipe.data` 读取，配合节点图 `recipe info.data` 或 KJS 实现自定义参数。

#### 5.3.3 输入/输出（标准）

| 方法 | 类型 | 说明 |
|---|---|---|
| `.inputItems(InputItem...)` / `.outputItems(...)` | 物品 | 普通物品（标准走 `ItemRecipeCapability`） |
| `.inputItemsDurability(...)` / `.outputItemsDurability(...)` | 物品耐久 | 改耐久而不是消耗物品 |
| `.inputFluids(FluidIngredientJS...)` / `.outputFluids(...)` | 流体 | 普通流体 |
| `.inputEntities(EntityIngredientJS...)` / `.outputEntities(...)` | 实体 | 实体输入/生成 |
| `.inputFE(int)` / `.outputFE(int)` | Forge Energy | RF/FE 能量 |

#### 5.3.4 修饰器（包裹器）

```javascript
event.recipes.mymod.foo(...)
    .duration(100)
    .perTick(b => b
        .inputFE(50)                    // 每 tick 消耗 50 FE
    )
    .chance(0.5, b => b
        .outputItems('minecraft:diamond')   // 50% 概率产出
    )
    .tierChanceBoost(0.1, b => b
        .outputItems('minecraft:emerald')   // 每等级 +10% 概率
    )
    .slotName('special_slot', b => b
        .inputItems('minecraft:nether_star') // 必须放入指定 slot
    )
    .uiName('catalyst', b => b
        .inputItems('minecraft:dragon_egg')  // JEI 显示分组名
    )
```

| 修饰器 | 作用 |
|---|---|
| `.perTick(builder)` | 内含的 input/output 标记为每 tick |
| `.chance(float, builder)` | 概率执行（0.0~1.0） |
| `.tierChanceBoost(float, builder)` | 每机器等级提升概率 |
| `.slotName(string, builder)` | 指定输入/输出 slot 名（机器多 slot 场景） |
| `.uiName(string, builder)` | JEI/EMI 显示分组 |

> **嵌套支持**：修饰器可嵌套，`perTick` + `chance` 可同时生效。

#### 5.3.5 模组兼容输入输出

仅在对应模组加载时可用，否则抛 `IllegalStateException`：

| 方法 | 模组 | 说明 |
|---|---|---|
| `.inputMana(int)` / `.outputMana(int)` | Botania | 魔力 |
| `.inputAura(int)` / `.outputAura(int)` | Nature's Aura | 灵气 |
| `.inputEmber(double)` / `.outputEmber(double)` | Embers | 余烬 |
| `.inputPNCPressure(float)` / `.outputPNCPressure(float)` | PneumaticCraft | 气压 |
| `.inputPNCAir(int)` / `.outputPNCAir(int)` | PneumaticCraft | 气体量 |
| `.inputPNCHeat(double)` / `.outputPNCHeat(double)` | PneumaticCraft | 热量 |
| `.inputHeat(double)` / `.outputHeat(double)` | Mekanism | 热量 |
| `.inputEU(long)` / `.outputEU(long)` | GTCEu Modern | EU |
| `.inputStress(float)` / `.outputStress(float)` | Create | 应力 |
| `.inputRPM(float)` / `.outputRPM(float)` | Create | 转速 |
| `.inputGases(...stack)` / `.outputGases(...)` | Mekanism | 气体（字符串："Nx mod:gas"） |
| `.inputSlurries(...)` / `.outputSlurries(...)` | Mekanism | 浆料 |
| `.inputInfusions(...)` / `.outputInfusions(...)` | Mekanism | 注入类型 |
| `.inputPigments(...)` / `.outputPigments(...)` | Mekanism | 颜料 |

#### 5.3.6 通用 capability 输入

如果某 capability 没有便捷方法，用通用 API：

```javascript
const ItemRecipeCapability = Java.loadClass('com.lowdragmc.mbd2.common.capability.recipe.ItemRecipeCapability')

event.recipes.mymod.foo(...)
    .inputs(ItemRecipeCapability.CAP, 'minecraft:iron_ingot', '2x minecraft:gold_ingot')
    .removeInputs(ItemRecipeCapability.CAP)   // 清空指定 cap 的输入
```

### 5.4 配方条件（Condition）

源码：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/recipe/MBDRecipeSchema.java:457-555`

| 方法 | 说明 |
|---|---|
| `.dimension(id: ResourceLocation)` | 仅在指定维度 |
| `.biome(id: ResourceLocation)` | 仅在指定生物群系 |
| `.machineLevel(level: int)` | 机器等级 ≥ level |
| `.positionY(min, max)` | Y 坐标范围 |
| `.raining(min, max)` | 雨强度范围 0~1 → int 0~100 |
| `.thundering(min, max)` | 雷强度范围 |
| `.blocksInStructure(min, max, ...blocks)` | 多方块结构内含某种方块数量范围 |
| `.machineData(data: CompoundTag, onlyCustomData: bool)` | 匹配机器 NBT |
| `.dayTime(isDay: bool)` | 是否白天 |
| `.light(minSky, maxSky, minBlock, maxBlock, canSeeSky)` | 光照条件 |
| `.redstoneSignal(min, max)` | 红石信号强度 |
| `.addCondition(RecipeCondition)` | 加任意自定义条件 |

#### 模组兼容条件

| 方法 | 模组 |
|---|---|
| `.rotationCondition(minRPM, maxRPM, minStress, maxStress)` | Create |
| `.mekTemperatureCondition(min, max)` | Mekanism |
| `.pncTemperatureCondition(min, max)` | PneumaticCraft |
| `.pncPressureCondition(isAir, min, max)` | PneumaticCraft |

### 5.5 配方完整示例

```javascript
ServerEvents.recipes(event => {
    // 复杂配方：白天 + 雨天 + 维度 + 等级 ≥ 3 才能合成
    event.recipes.mymod.high_furnace('mymod:rain_diamond')
        .duration(400)
        .priority(10)
        .inputItems('4x minecraft:coal')
        .perTick(b => b.inputFE(100))
        .chance(0.75, b => b.outputItems('minecraft:diamond'))
        .outputItems('minecraft:slime_ball')
        .dayTime(true)
        .raining(50, 100)
        .dimension('minecraft:overworld')
        .machineLevel(3)
        .addDataString('flavor', 'lightning_made')
})
```

---

## 第六章 配方类型事件

源码：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/integration/kubejs/events/MBDRecipeTypeEvents.java:1-41`

3 个事件，按脚本类型分发：

| 事件名 | 脚本类型 | 用途 |
|---|---|---|
| `onTransferProxyRecipe` | server_scripts | JEI 一键转移配方时拦截/重定向 |
| `onRecipeUI` | client_scripts | JEI/EMI 配方 UI 渲染钩子 |
| `onFuelRecipeUI` | client_scripts | 燃料配方 UI 渲染钩子 |

形式：

```javascript
MBDRecipeTypeEvents.onTransferProxyRecipe('mymod:high_furnace', e => {
    // e.event 是 TransferProxyRecipeEvent
    // 改写源/目标库存，或 cancel 阻止转移
})
```

> 这三个事件用得相对少，绝大多数用户用不到。具体字段查 `TransferProxyRecipeEvent` / `RecipeUIEvent` / `FuelRecipeUIEvent` 源码或 ProbeJS 类型。

---

## 第七章 实战案例（八例）

### 案例 1：超频炉（duration *0.5）

最经典需求。**对比线图教程 1**，用 KJS 写法：

```javascript
// server_scripts/overclock.js
MBDMachineEvents.onBeforeRecipeModify('mymod:overclock_furnace', e => {
    let recipe = e.event.recipe
    if (recipe == null) return
    
    // copy(modifier, modifyDuration=true, io=NONE) — 仅变 duration
    e.event.recipe = recipe.copy(
        ContentModifier.multiplier(0.5), true, IO.NONE
    )
})
```

### 案例 2：黑名单配方拒绝

```javascript
// server_scripts/recipe_blacklist.js
const BLACKLIST = new Set([
    'minecraft:dangerous',
    'mymod:exploit_recipe',
])

MBDMachineEvents.onBeforeRecipeModify('mymod:safe_machine', e => {
    let recipe = e.event.recipe
    if (recipe && BLACKLIST.has(recipe.id.toString())) {
        e.event.recipe = null     // 拒绝
    }
})
```

### 案例 3：自动持久计数器（每完成一次配方 +1）

对比线图教程 5，KJS 版本：

```javascript
// 顶部预加载
const ItemEntity = Java.loadClass('net.minecraft.world.entity.item.ItemEntity')

MBDMachineEvents.onRecipeFinish('mymod:counting_machine', e => {
    let machine = e.event.machine
    let data = machine.getCustomData()
    let count = data.getInt('finishedCount') + 1
    data.putInt('finishedCount', count)
    machine.setCustomData(data)         // 写回会触发 CustomDataUpdateEvent
    
    // 每 100 次在机器顶上掉落奖励物品
    if (count % 100 === 0) {
        let level = machine.level
        let pos = machine.pos
        let itemStack = Item.of('minecraft:emerald_block')
        let entity = new ItemEntity(
            level,
            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
            itemStack
        )
        level.addFreshEntity(entity)
    }
})
```

> **不要写** `level.spawnItem(...)`——vanilla `Level` 上没有这个方法。指定用 `addFreshEntity` + `new ItemEntity(...)`。KJS 另一个快捷是 `level.getBlock(pos).popItem(itemStack)`（返回 `BlockContainerJS`）。

### 案例 4：右键给予自定义物品（一次性）

对比线图教程 6：

```javascript
// 顶部：提前加载 InteractionResult 枚举
const InteractionResult = Java.loadClass('net.minecraft.world.InteractionResult')

MBDMachineEvents.onRightClick('mymod:gift_box', e => {
    let player = e.event.player
    let machine = e.event.machine
    let data = machine.getCustomData()
    
    if (data.getBoolean('used')) {
        player.tell('§c这个礼物盒已被使用过')
        e.event.interactionResult = InteractionResult.SUCCESS    // 拦截原生
        return
    }
    
    // 给予并标记
    player.give(Item.of('minecraft:nether_star'))
    data.putBoolean('used', true)
    machine.setCustomData(data)
    
    e.event.interactionResult = InteractionResult.SUCCESS
})

// 说明：`interactionResult` 字段是 InteractionResult 枚举，不是 boolean
//   - SUCCESS 拦截原生交互并标记为“处理了”
//   - PASS    不拦截，原生交互继续
//   - FAIL    拦截但标记为失败
```

### 案例 5：每 60 tick 提示玩家附近事件

对比线图教程 8：

```javascript
// 顶部预加载
const AABB = Java.loadClass('net.minecraft.world.phys.AABB')
const Player = Java.loadClass('net.minecraft.world.entity.player.Player')

MBDMachineEvents.onTick('mymod:announcer', e => {
    let machine = e.event.machine
    let level = machine.level
    
    // 用 gameTime 直接判断，不需要 customData
    if (level.gameTime % 60 !== 0) return
    
    let pos = machine.pos
    // AABB.of 只接受 BoundingBox，从 BlockPos 起需用构造器 new AABB(pos).inflate(...)
    let aabb = new AABB(pos).inflate(8)
    // Java.loadClass(...) 返回的就是 Class 实例，直接作为 Class 参数传入（不要 .class）
    let players = level.getEntitiesOfClass(Player, aabb)
    players.forEach(p => p.tell('§e机器仍在运行...'))
})
```

> **比线图省事**：节点图需要 customData 计数 + mod 60，KJS 直接用世界 gameTime。
>
> **API 警告**：Vanilla `Level` 没有 `getEntitiesWithin(aabb)` 这种重载。只有：
> - `getEntities(EntityTypeTest, AABB, predicate?)`
> - `getEntitiesOfClass(Class<T>, AABB, predicate?)`
> - `players()`（返回全部玩家列表，带括号、不是属性）

### 案例 6：动态生成配方（KJS 独有）

KJS 可以在事件里**生成新 MBDRecipe** 注入：

```javascript
// 思路：右键时根据手持物品动态合成
MBDMachineEvents.onBeforeRecipeModify('mymod:adaptive_machine', e => {
    let recipe = e.event.recipe
    let machine = e.event.machine
    
    // 取机器上方一格的物品
    let above = machine.level.getBlockEntity(machine.pos.above())
    // ... 根据 above 的库存动态构造一个 MBDRecipe
    
    // 直接 new MBDRecipe(...) 并赋给 e.event.recipe
    // const MBDRecipe = Java.loadClass('com.lowdragmc.mbd2.api.recipe.MBDRecipe')
    // e.event.recipe = new MBDRecipe(recipeType, id, inputs, outputs, ...)
})
```

> 完整的 `MBDRecipe` 构造比较复杂（多个 Map），实际项目里更推荐：在 GUI 编辑器里准备好一组"模板配方"，KJS 根据条件 pickOne 改 `data` 字段。

### 案例 7：多方块成型送任务奖励

```javascript
// 顶部预加载
const AABB = Java.loadClass('net.minecraft.world.phys.AABB')
const Player = Java.loadClass('net.minecraft.world.entity.player.Player')

MBDMachineEvents.onStructureFormed('mymod:big_reactor', e => {
    let machine = e.event.machine
    let level = machine.level
    let aabb = new AABB(machine.pos).inflate(16)
    let players = level.getEntitiesOfClass(Player, aabb)
    
    players.forEach(p => {
        p.give(Item.of('minecraft:nether_star'))
        p.tell('§a反应堆首次成型！获得奖励')
    })
})

MBDMachineEvents.onStructureInvalid('mymod:big_reactor', e => {
    // 结构被破坏时通知本世界全部玩家。players() 是方法调用，带括号
    let machine = e.event.machine
    machine.level.players().forEach(p => p.tell('§c反应堆结构损坏！'))
})
```

### 案例 8：跨机器联动（KJS 独有的全局状态）

KJS 可以用脚本级全局变量跨机器协作，节点图办不到：

```javascript
// server_scripts/cross_machine.js

// 脚本级全局：所有运行中的机器位置
const RUNNING_MACHINES = new Set()

MBDMachineEvents.onRecipeStatusChanged('mymod:network_node', e => {
    let machine = e.event.machine
    let key = machine.pos.asLong()
    
    // newStatus 是 RecipeLogic.Status 枚举，toString() 返回 name（WORKING/IDLE/WAITING/SUSPEND）
    if (e.event.newStatus.toString() === 'WORKING') {
        RUNNING_MACHINES.add(key)
    } else {
        RUNNING_MACHINES.delete(key)
    }
})

// 在另一台机器决定是否工作时检查全局状态
MBDMachineEvents.onBeforeRecipeWorking('mymod:throttle_node', e => {
    if (RUNNING_MACHINES.size >= 5) {
        e.event.setCanceled(true)     // 全网最多 5 台同时工作
    }
})
```

> **重启失效**：`RUNNING_MACHINES` 仅在脚本运行期内有效，重启 Minecraft 重置。需要持久化的话，仍要写回每台机器的 customData，再在 `onLoad` 时重建集合。

---

## 第八章 节点图 vs KJS 决策

### 8.1 选择矩阵

| 需求 | 推荐 | 理由 |
|---|---|---|
| 单台机器内简单 if/else 逻辑 | 节点图 | GUI 友好，无脚本环境依赖 |
| 配方时长/产量倍率修改 | 节点图 | `recipe modifier` 一拖即用 |
| 配方按 ID 黑白名单 | 任意 | 都简单；KJS 写 Set 集合更易维护 |
| 跨多机器协作 | KJS | 节点图无全局状态 |
| 跨模组联动 | KJS | 直接 import 任何 Java 类 |
| 复杂数学/字符串处理 | KJS | 节点图数学库受限 |
| 动态生成新机器/配方类型 | KJS（startup） | 节点图无注册能力 |
| 配方表达式（条件、概率、perTick） | recipe schema | 节点图不能改配方结构定义 |
| GUI/UI 修改 | KJS（onUI） | 节点图改 widget 树困难 |
| 整合包级别批量调整 | KJS | 一份脚本管全部机器 |
| 模组分发（带在 mod jar 里） | 节点图 | 跟随机器定义打包；KJS 需要单独的资源包 |
| 需要给非开发者修改 | 节点图 | 可视化无门槛 |
| 性能敏感的 tick 热路径 | 节点图 | 反射调用慢于编译执行 |

### 8.2 混用模式（推荐）

实际项目里**两者并行使用**：

- **节点图**：固定不变的核心逻辑（配方修改、状态机、tick 计时器）
- **KJS**：
  - 整合包级覆盖（跨多机器统一调整）
  - 跨模组数据交换
  - 复杂动态逻辑
  - 调试期快速试验（改完不需要重新加载机器定义）

> **同事件双订阅**：节点图和 KJS 可以同时监听同一事件，**互不感知**。注意避免**冲突写入**——比如两边都改 `recipe` 字段会产生竞态。建议明确分工：节点图改 duration，KJS 改 inputs/outputs。

---

## 第九章 避坑清单（必读）

| # | 坑 | 解决 |
|---|---|---|
| 1 | `e.event['recipe.in']` / `['recipe.out']` | **错**。点号 key 是节点图 identity，与 KJS 无关。KJS 用 Java 字段名 `e.event.recipe`（读写同一字段） |
| 2 | `e.event.recipe = null` 拒绝配方 | OK，但只在 `Before` 事件生效；`After` 是观测事件写了也不生效 |
| 3 | client_scripts 改 customData 不生效 | customData 是服务端权威，客户端写入会被同步覆盖 |
| 4 | KJS Set 拒绝 ResourceLocation 对象 | `BLACKLIST.has(rl.toString())`，不要直接 `has(rl)` |
| 5 | `MBDMachineEvents.onTick(...)` 不传 ID 会监听所有机器 | 性能敏感时务必加 ID 过滤 |
| 6 | startup 阶段 `event.create(...)` 后再改其他属性无效 | 所有配置必须在事件回调内完成（`afterPosted` 才统一 build） |
| 7 | 同 ID 重复 `event.create(...)` 后者覆盖前者 | 把多份机器拆开 ID |
| 8 | 模组 capability 方法在模组未加载时直接抛 | 必须 `if (Platform.isLoaded('botania'))` 守卫 |
| 9 | recipe schema 的 `.chance(0)` 不会触发 builder | `chance` 是包裹器，0 仍执行包裹内容只是概率 0 |
| 10 | `perTick` 包裹器内的 input 仍会被加到主 inputs | 但带 perTick 标记。配方求解时按 perTick 处理 |
| 11 | `e.event.cancel = true` | **错**。cancel 不是 Java 字段。用 `e.event.setCanceled(true)`（Forge）或 `e.cancel()`（KJS） |
| 11b | `e.event.interactionResult = true` | **错**。字段是 InteractionResult 枚举。赋 `InteractionResult.SUCCESS` / `PASS` |
| 11c | `e.event.newStatus === 'WORKING'` | **错**。字段是 `RecipeLogic.Status` 枚举。用 `e.event.newStatus.toString() === 'WORKING'` |
| 12 | KJS 监听 `onCustomDataUpdate` 是 client 事件 | 不能在这里改服务端状态。需要 server 端响应改用 `onTick` 自己比对 |
| 13 | 节点图的 `MachineCustomKeyframeEvent` 需 GeckoLib | KJS `MBDClientEvents.CUSTOM_KEYFRAME` 在未装时为 null，不能监听 |
| 14 | `onPlaced.player` 是 LivingEntity 不是 Player | `const Player = Java.loadClass('net.minecraft.world.entity.player.Player'); if (Player.isInstance(player)) {...}` |
| 15 | KJS 中 `e.event.drops` 是 List 字段本身 | 直接 `drops.add(...)` / `drops.clear()` 修改，**不要**赋新 List |
| 16 | 同事件节点图 + KJS 双订阅时双方都改 recipe 字段 | 后调用方覆盖前者。建议节点图与 KJS 分别管不同事件 |
| 17 | `event.recipes.<modid>.<path>` 找不到 | 检查 RecipeType 是否注册（先 reload datapack 或重启） |
| 18 | KJS 里看不到 GUI 编辑器创建的机器 ID | 编辑器项目文件是 NBT 二进制（`.sm`），**不走 datapack**，需 mod Java 代码监听 `MBDRegistryEvent.Machine` 手动注册。KJS 无法读取 .sm 文件 |
| 19 | `e.event` 字段名拼写错 | 严格按 `MachineEvent` 子类的 `@GraphParameterGet` 注解；查 ProbeJS 推断 |
| 20 | priority 越高越优先 | 默认 0；多个配方共用输入时高 priority 先匹配 |

---

## 第十章 ProbeJS 查找指南

### 10.1 关键文件位置

| 文件 | 作用 |
|---|---|
| `.probe/shared/com.lowdragmc.mbd2.d.ts` | mbd2 全部公开类型（284KB，最完整） |
| `.probe/startup/packages/com.lowdragmc.mbd2.d.ts` | startup 阶段可用类型 |
| `.probe/server/packages/com.lowdragmc.mbd2.d.ts` | server 阶段可用类型 |
| `.probe/client/packages/com.lowdragmc.mbd2.d.ts` | client 阶段可用类型 |
| `.probe/shared/com.lowdragmc.lowdraglib.kjs.d.ts` | LDLib KJS 集成类型 |

### 10.2 查找 EventJS 字段

每个 `MachineXxxEventJS` 都通过 `getEvent()` 返回底层事件。

**在 ProbeJS 文件搜**：

- 找事件参数 → 搜 `class MachineXxxEvent` → 看字段（`@GraphParameterGet/Set` 注解的 Java 字段）
- 找 EventJS 暴露 API → 搜 `class MachineXxxEventJS`

举例（`MachineRightClickEvent`）：

```typescript
// 在 com.lowdragmc.mbd2.d.ts 里搜 MachineRightClickEvent
declare class MachineRightClickEvent extends MachineEvent {
    readonly machine: MBDMachine
    readonly player: Player
    readonly heldItem: ItemStack
    readonly hand: InteractionHand
    readonly hit: BlockHitResult
    interactionResult: InteractionResult    // 可读写，枚举类型
    // 取消走 Forge Event 接口：setCanceled(boolean) / isCanceled()
}
```

脚本中的正确写法：

- 读字段：`e.event.player`、`e.event.heldItem`、`e.event.hit`
- 写交互结果：`e.event.interactionResult = InteractionResult.SUCCESS`
- 取消事件：`e.event.setCanceled(true)`

> **避免被 ProbeJS 误导**：ProbeJS 生成的 `cancel` 字段描述是图层虚拟参数（来自 `getExposedParameters` 调用后的装饰）而非 Java 字段。**KJS 脚本中不要**写 `e.event.cancel = ...`。

### 10.3 IDE 智能提示设置

确保 KubeJS 设置里启用了 ProbeJS 自动生成：

```
.probe/probe-settings.json
{
  "auto-generate": true,
  ...
}
```

VSCode 打开 `kubejs/` 目录，TypeScript 服务会读取 `.probe` 提供完整补全。

### 10.4 当 ProbeJS 不准时

ProbeJS 通过反射生成，偶尔遗漏：

- **泛型擦除**：`List<MBDMachine>` 可能显示为 `List<any>`
- **Lombok 生成方法**：`@Getter` 的 getter 通常能识别
- **静态字段缺失**：直接 `Java.loadClass('完整类名').FIELD` 兜底

源码永远是 ground truth。ProbeJS 不准时直接读 Java 源。

---

## 附录 A：完整 KJS 脚本骨架模板

```javascript
// startup_scripts/00_mbd_setup.js
MBDRegistryEvents.recipeType(event => {
    event.createRecipeType('mymod:my_furnace')
})

MBDRegistryEvents.machine(event => {
    // 推荐：机器在 GUI 编辑器创建，这里仅做特殊场景的 KJS 注册
})
```

```javascript
// server_scripts/01_mbd_events.js
MBDMachineEvents.onBeforeRecipeModify('mymod:my_machine', e => {
    let recipe = e.event.recipe
    if (!recipe) return
    e.event.recipe = recipe.copy(
        ContentModifier.multiplier(0.5), true, IO.NONE
    )
})

MBDMachineEvents.onTick('mymod:my_machine', e => {
    let level = e.event.machine.level
    if (level.gameTime % 20 !== 0) return
    // 每秒执行
})
```

```javascript
// server_scripts/recipes/02_mbd_recipes.js
ServerEvents.recipes(event => {
    event.recipes.mymod.my_furnace('mymod:diamond_smelting')
        .duration(200)
        .inputItems('4x minecraft:coal_block')
        .perTick(b => b.inputFE(50))
        .chance(0.8, b => b.outputItems('minecraft:diamond'))
        .machineLevel(2)
})
```

```javascript
// client_scripts/03_mbd_client.js
MBDMachineEvents.onClientTick('mymod:my_machine', e => {
    // 仅渲染/特效
})
```

---

## 附录 B：KJS 调用 MBD2 Java API 速查

### 创建 ResourceLocation

```javascript
const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
let id = new ResourceLocation('mymod', 'my_machine')
// 或 KJS 标准：
let id2 = 'mymod:my_machine'    // 大多数 API 接受字符串
```

### 创建 ContentModifier

```javascript
let mul = ContentModifier.multiplier(2.0)
let add = ContentModifier.addition(10)
// 或自定义：ContentModifier.of(multiplier, addition) → f(x)=x*mul+add
```

### 访问注册表

`MBDRegistry<K, V>` 实现 `Iterable<V>`，只迭代 value。要同时拿 key + value 用 `entries()` 返回 `Set<Map.Entry<K, V>>`。

```javascript
// 仅迭代 value（不带 key）
MBDRegistries.RECIPE_TYPES.forEach(type => {
    console.log(`RecipeType: ${type.getRegistryName()}`)
})

// key + value 同时取
MBDRegistries.MACHINE_DEFINITIONS.entries().forEach(entry => {
    let id = entry.getKey()        // ResourceLocation
    let def = entry.getValue()     // MBDMachineDefinition
    console.log(`Machine: ${id}`)
})

// 其他可用方法：.values()、.keys()、.get(key)、.containKey(key)、.getKey(value)
```

> **不要写** `MACHINE_DEFINITIONS.forEach((id, def) => ...)`——`Iterable.forEach` 只接一个 `Consumer<V>`，不是 BiConsumer。上面该写法中 `id` 会拿到 value 本身，`def` 为 `undefined`。

### CompoundTag 操作（NBT）

```javascript
let tag = e.event.machine.getCustomData()
tag.putInt('count', 5)
tag.putString('name', 'foo')
tag.putBoolean('flag', true)

let count = tag.getInt('count')
let nested = tag.getCompound('sub')
```

---

**文档版本**：v1.0  
**对应代码版本**：MBD2 1.20.1（`integration/kubejs/`）  
**关联文档**：`MBD2_线图教程.md`（节点图视图）  
**适用读者**：已有 KubeJS 基础、需要给 MBD2 机器写脚本逻辑的整合包开发者
