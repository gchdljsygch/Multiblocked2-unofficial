# MBD2 节点图（线图）完整使用教程

> 适用版本：Multiblocked2 1.20.1 + LDLib-MultiLoader 1.20.1  
> 内容来源：源码逐文件解析（MBD2 13 个节点 + 27 个事件 + LDLib ~70 个通用节点）  
> 目录定位：
> - MBD2 节点：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/common/graphprocessor/`
> - 事件类：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/common/machine/definition/config/event/`
> - LDLib 节点：`LDLib-MultiLoader-1.20.1/common/src/main/java/com/lowdragmc/lowdraglib/gui/graphprocessor/nodes/`

---

## 目录

1. [核心概念](#第一章-核心概念)
2. [MBD2 节点参考](#第二章-mbd2-节点参考13-个)
3. [LDLib 通用节点参考](#第三章-ldlib-通用节点参考)
4. [MachineEvent 事件清单](#第四章-machineevent-事件清单27-个)
5. [暴露参数命名表](#第五章-暴露参数命名表连图必查)
6. [实战教程（七例）](#第六章-实战教程七个完整范例)
7. [高级技巧](#第七章-高级技巧)
8. [避坑清单](#第八章-避坑清单必读)
9. [参考定位](#第九章-参考定位)

---

## 第一章 核心概念

### 1.1 节点图是"事件触发器"

每张图绑定一个 `MachineEvent` 子类。游戏中触发事件 → 引擎把事件字段写入图的"暴露参数" → 按深度优先执行节点 → 把图执行结果回写事件字段。

执行入口（`MachineEventGraphProcessor.postEvent`）：

```java
event.bindParameters(graph.exposedParameters); // 事件→图
run();                                          // 执行图
event.gatherParameters(graph.exposedParameters);// 图→事件
```

### 1.2 三类节点

| 类型 | 基类 | 行为 |
|---|---|---|
| **数据节点** | `BaseNode` | 被下游索取时按需求值，可重复求值 |
| **触发节点** | `TriggerNode` / `LinearTriggerNode` | 必须挂在 trigger（红色三角）链上才执行；用于副作用操作 |
| **流控节点** | `TriggerNode` 派生 | 改变触发流（if/for/break） |

### 1.3 端口类型

- **数据端口**（蓝/绿/黄圆点）：传值，按类型着色，自动类型适配
- **触发端口**（红色三角，类型 `TriggerLink`）：传执行流，决定触发节点执行顺序

### 1.4 双填值约定（重要）

很多节点同名提供 InputPort + `internalXxx` Configurable：

- 端口未连线 → 用 internal（GUI 直填）
- 端口连线 → 端口值覆盖 internal，编辑器自动隐藏 internal

涉及节点：`recipe create`(id)、`modify recipe`(content side)、`recipe modifier`(multiplier/addition)、几乎所有 `value` 节点等。

### 1.5 图的生命周期

- **保存位置**：图作为数据存储于 `MachineDefinition.machineEvents()`，每个事件 class 一张图
- **构建时机**：机器加载时根据事件 class 实例化 `MachineEventGraphProcessor`
- **执行时机**：游戏中 `MachineEvent.postCustomEvent()` 触发对应处理器
- **同步**：图本身定义随机器定义同步到客户端；执行只在事件源端发生（服务端事件→服务端执行）
- **错误隔离**：单个节点抛异常不会熔断整图（LDLib 处理器层有保护），但建议用 `print`/`console log` 排错

---

## 第二章 MBD2 节点参考（13 个）

### 2.1 信息读取（数据节点）

#### `machine info`（机器信息）- 机器信息读取节点
**组**：`graph_processor.node.mbd2.machine`

| 端口 | 方向 | 类型 | 说明 |
|---|---|---|---|
| `machine`（机器） | In | MBDMachine | 机器实例 |
| `level`（世界） | Out | Level | 所在世界 |
| `xyz`（坐标） | Out | Vector3f | 方块坐标（已转浮点） |
| `front`（正面朝向） | Out | Direction | 正面朝向（无值返回 NORTH） |
| `status`（状态） | Out | String | 自定义状态机的 state name |
| `recipe status`（配方状态） | Out | String | IDLE/WORKING/WAITING/SUSPEND |
| `custom data`（自定义数据） | Out | CompoundTag | 持久化 NBT |
| `machine level`（机器等级） | Out | int | 机器等级 |

#### `multiblock info`（多方块信息）
| 端口 | 方向 | 类型 | 说明 |
|---|---|---|---|
| `machine`（机器） | In | MBDMachine | 必须是 MBDMultiblockMachine |
| `is formed`（已成型） | Out | boolean | 结构是否成型 |
| `parts`（部件列表） | Out | List<MBDMachine> | 已识别的部件列表 |

#### `part info`（部件信息）
| 端口 | 方向 | 类型 | 说明 |
|---|---|---|---|
| `machine`（机器） | In | MBDMachine | 必须是 MBDPartMachine |
| `is formed`（已成型） | Out | boolean | 该部件是否属于已成型多方块 |
| `controllers`（控制器列表） | Out | List<MBDMultiblockMachine> | 反查所有控制器 |

#### `recipe info`（配方信息）
**组**：`graph_processor.node.mbd2.machine.recipe`

| 端口 | 方向 | 类型 |
|---|---|---|
| `recipe`（配方） | In | MBDRecipe |
| `recipe id`（配方 ID） | Out | String |
| `duration`（耗时） | Out | int |
| `priority`（优先级） | Out | int |
| `is fuel recipe`（是否燃料配方） | Out | boolean |
| `data`（数据） | Out | CompoundTag |

### 2.2 配方流水线（数据节点）

#### `recipe create`（创建配方）- 运行时构造配方
| 端口/配置 | 类型 | 说明 |
|---|---|---|
| `in`（输入） | In Object | NBT/JsonObject/CharSequence 自动 parse |
| `id`（配方 ID） | In String | 运行时 id；非合法 ResourceLocation 整节点放弃 |
| `internalID`（内部 ID） | 配置 | 默认 `mbd2:recipe_on_the_fly` |
| `out`（输出） | Out MBDRecipe | 生成的配方 |

#### `recipe deserialize`（配方反序列化）- 配方→NBT
| 端口 | 类型 |
|---|---|
| `in`（输入配方） | In MBDRecipe |
| `id`（配方 ID） | Out String |
| `recipe`（配方 NBT） | Out CompoundTag（序列化后的 NBT） |

#### `recipe modifier`（配方修改器）- 构造内容修改器
| 端口/配置 | 类型 | 说明 |
|---|---|---|
| `multiplier`（乘数） | In Float | 默认 internalMul=1（≥0） |
| `addition`（加数） | In Float | 默认 internalAdd=0 |
| `modifier`（修改器） | Out ContentModifier | `f(x)=x*mul+add` |

> 当 `mul==1 && add==0` 时为 identity，下游会跳过 copy 优化性能。

#### `modify recipe`（修改配方）- 应用修改器
| 端口/配置 | 类型 | 说明 |
|---|---|---|
| `in`（输入配方） | In MBDRecipe | 原配方 |
| `content modifier`（内容修改器） | In ContentModifier | 应用到输入/输出内容数量 |
| `content side`（作用侧） | In IO | NONE/IN/OUT/BOTH |
| `duration modifier`（耗时修改器） | In ContentModifier | 应用到 duration |
| `internalContentIO`（内部作用侧） | 配置 IO | 默认 BOTH，端口未连时用 |
| `out`（输出配方） | Out MBDRecipe | 修改后的新配方（identity 时透传） |

### 2.3 状态写入（触发节点 LinearTriggerNode）

| 节点 | 输入 | 调用 |
|---|---|---|
| `set machine status`（设置机器状态） | machine, status(String) | `setMachineState(status)` |
| `set machine front`（设置正面朝向） | machine, front(Direction) | `setFrontFacing` |
| `set machine level`（设置机器等级） | machine, machine level(int) | `setMachineLevel` |
| `set custom data`（设置自定义数据） | machine, data(CompoundTag) | `setCustomData`（触发 CustomDataUpdateEvent） |
| `set machine signal`（设置红石信号） | machine, side, signal(int), mode | 见下表 |

`set machine signal` 的 `mode`：
- **SIGNAL**：弱红石信号（普通输出）
- **DIRECT_SIGNAL**：强红石信号（中继器/比较器穿透）
- **ANALOG**：模拟比较器输出（侧面无关）

---

## 第三章 LDLib 通用节点参考

### 3.1 值节点（`graph_processor.node.value`）

| 节点 | 输出 | 说明 |
|---|---|---|
| `bool`（布尔值） | boolean | in 端口 Object 自动适配；可设 internalValue |
| `number`（数字） | float | int/float/double 都走这个 |
| `string`（字符串） | String | 字符串常量 |
| `color`（颜色） | int | ARGB 颜色整数 |
| `xyz`（三维坐标） | Vector3f | 同时拆分 outX/outY/outZ；inX/inY/inZ 单分量入 |
| `null`（空值） | Object | null 常量（用于显式拒配方等） |

### 3.2 数学（`graph_processor.node.math`）

**多输入聚合（基于 ListMergeNode，端口数量自适应）**：
- `adder`（加法器，求和，默认 0） / `multiplier`（乘法器，求积，默认 1）
- `min`（最小值，默认 Float.MAX_VALUE） / `max`（最大值，默认 -Float.MIN_VALUE）

**二元**：
- `sub`（减法，a-b）、`div`（除法，a/b）
- `mod`（取模，a%b，**整数取模，输入 int**）

**取整**：`ceil`（向上取整）、`floor`（向下取整）、`round`（四舍五入，返回 int）

**三角**：`sin`（正弦）、`cos`（余弦）、`tan`（正切）（弧度制 float→float）

**随机**：`random`（随机数，min, max → float），每次 process 重新随机

### 3.3 逻辑（`graph_processor.node.logic`）

| 节点 | 端口 | 说明 |
|---|---|---|
| `and`（与） / `or`（或） | inputs(List<Boolean>) → out | 多输入逻辑，端口自适应 |
| `xor`（异或） | a, b → out | 异或 |
| `equal`（相等判定） | a, b (Object) → out | `Objects.equals` |
| `comparator`（比较器） | a, b (float), type 配置 → out | 6 种比较：EQUAL/NOT_EQUAL/GREATER/GREATER_EQUAL/LESS/LESS_EQUAL |
| `regex`（正则匹配） | in, regex → out(boolean) | 正则匹配 |
| `select`（选择器） | true, false, condition → out | `c ? true : false` |
| `switch`（分支选择） | index, inputs(List) → out | `inputs[index]` |
| `if else`（条件分支） | condition → True 触发, Else 触发 | 触发流分支 |
| `for loop`（循环） | start, end → loop body 触发, loop completed 触发, index | for 循环 |
| `break`（中断） | (无) | 中断 for loop |
| `loop start`（循环起点） | — | 内部循环起点（一般不直接用） |

### 3.4 工具（`graph_processor.node.utils`）

| 节点 | 说明 |
|---|---|
| `print`（聊天输出） | 触发节点，把 in 转字符串显示在玩家聊天 |
| `console log`（控制台日志） | 触发节点，写控制台日志（debug 用） |
| `cache`（缓存） | 数据节点，缓存上次值（节点字段持久于本图实例） |

**列表（`.utils.list`）**：
| 节点 | 端口 |
|---|---|
| `pack list`（打包列表） | 任意输入 → out(List<Object>)，N 路打包 |
| `list reader`（列表读取器） | in(List), index → out, size |
| `list merge`（列表合并） | a, b → out（拼接） |

### 3.5 Minecraft 通用（`graph_processor.node.minecraft`）

| 节点 | 端口 | 说明 |
|---|---|---|
| `command`（执行命令） | command(String), level, xyz → output(String) | **触发节点；以 OP 权限执行** |
| `level info`（世界信息） | level → height, day time, rain level, thunder level, is day | 世界信息 |

### 3.6 方块（`.minecraft.block`）

| 节点 | 端口 |
|---|---|
| `block`（方块） | in → out(Block)；可选 internal Block |
| `blockstate`（方块状态） | in → out(BlockState) |
| `blockstate info`（方块状态信息） | in(BlockState), property(String) → value(String) |
| `get block`（获取方块） | level, xyz → blockstate, blockentity |
| `blockentity info`（方块实体信息） | in → level, pos(BlockPos), blockstate, tag(NBT) |
| `place block`（放置方块） | 触发；level, xyz, blockstate |
| `remove block`（移除方块） | 触发；level, xyz |

### 3.7 数据/NBT（`.minecraft.data`）

| 节点 | 端口 |
|---|---|
| `compound`（复合标签） | in → out(CompoundTag) |
| `compound merge`（标签合并） | a, b → out（深合并） |
| `compound reader`（标签读取器） | tag, key → out(Object)；type 配置决定反序列化目标类型 |
| `compound writer`（标签写入器） | 触发；tag, key, value → out（写入并返回新 tag） |
| `direction`（方向） | in → out(Direction)；internal 默认值 |
| `direction info`（方向信息） | in → ordinal(int), xyz(Vector3f), clockwise, opposite |

### 3.8 实体（`.minecraft.entity`）

| 节点 | 端口 |
|---|---|
| `entity type`（实体类型） | in → out(EntityType) |
| `entity info`（实体信息） | in → entity type, is alive, xyz, level, tag |
| `player info`（玩家信息） | in(Player) → inventory(IItemTransfer), is crouching, name, xp |
| `get entities`（获取实体列表） | level, from(xyz), to(xyz), entity type → entities(List<Entity>)，AABB 范围扫描 |
| `spawn entity`（生成实体） | 触发；level, xyz, entity type, count, tag |
| `entity action`（实体动作） | 触发；entity, xyz, action(KILL/MOVE/FIRE) |

`entity action` 的 action：
- **KILL**：`entity.kill()`
- **FIRE**：`setSecondsOnFire(5)`
- **MOVE**：`teleportTo(xyz)`

### 3.9 物品（`.minecraft.item`）

| 节点 | 端口 |
|---|---|
| `item`（物品） | in → out(Item) |
| `itemstack`（物品堆） | in / item / count / tag → out(ItemStack) |
| `itemstack info`（物品堆信息） | in → item, count, nbt |
| `item transfer get`（获取物品仓储） | level, xyz, direction → itemTransfer(IItemTransfer) |
| `item transfer info`（物品仓储信息） | itemTransfer, slot index → slots, itemstack, slot limit |
| `item insert`（插入物品） | 触发；itemTransfer, itemstack, slot, simulate → remaining |
| `item extract`（抽取物品） | 触发；itemTransfer, amount, slot, simulate → extracted |
| `give player item`（给予玩家物品） | 触发；target(Player), itemstack, preferredSlot；塞不下则掉落 |
| `drop item`（掉落物品） | 触发；level, xyz, itemStack, direction（从哪面弹出） |

### 3.10 流体（`.minecraft.fluid`）

| 节点 | 端口 |
|---|---|
| `fluid`（流体） | in → out(Fluid) |
| `fluidstack`（流体堆） | in / fluid / amount / tag → out(FluidStack) |
| `fluidstack info`（流体堆信息） | in → out(Fluid), amount, nbt |
| `fluid transfer get`（获取流体仓储） | level, xyz, direction → fluidTransfer(IFluidTransfer) |
| `fluid transfer info`（流体仓储信息） | fluidTransfer, tank index → tanks, fluidstack, capacity |
| `fluid fill`（注入流体） | 触发；fluidTransfer, fluidstack, simulate → filled |
| `fluid drain`（抽取流体） | 触发；fluidTransfer, fluidstack, simulate → drained |

---

## 第四章 MachineEvent 事件清单（27 个）

> **加粗** = 可取消，写 `cancel=true` 即拦截默认行为。

### 4.1 生命周期

| 事件 | Get 参数 | 说明 |
|---|---|---|
| `MachineOnLoadEvent`（机器加载） | — | BlockEntity 加载 |
| `MachinePlacedEvent`（机器放置） | player(LivingEntity), itemStack | 放置完成 |
| `MachineRemovedEvent`（机器移除） | — | 移除前 |
| **`MachineTickEvent`（服务端 tick）** | — | 服务端每 tick |
| `MachineClientTickEvent`（客户端 tick） | — | 客户端每 tick（仅渲染/特效） |

### 4.2 玩家交互

| 事件 | Get | Set | 说明 |
|---|---|---|---|
| `MachineRightClickEvent`（机器右键） | player, heldItem, hand, hit(BlockHitResult) | interactionResult(Boolean→true=SUCCESS) | 右键。Set true 拦截原生交互 |
| **`MachineOpenUIEvent`（打开 GUI）** | player | (cancel) | 打开 GUI 前 |
| `MachineUIEvent`（UI 构建） | player | — | UI 构建（图里改 root 困难，建议 KubeJS） |
| `MachineNeighborChangedEvent`（邻居方块变化） | block, fromPos(BlockPos) | — | 邻居方块变化 |
| `MachineDropsEvent`（机器掉落物） | entity, drops.in(List<ItemStack>) | drops.out | 写 drops.out 重写整组掉落 |

### 4.3 状态变化

| 事件 | Get | 说明 |
|---|---|---|
| **`MachineStateChangedEvent`（机器状态变更）** | oldState, newState(String) | 自定义状态机切换 |
| `MachineRecipeStatusChangedEvent`（配方状态变更） | oldStatus, newStatus(String) | IDLE/WORKING/WAITING/SUSPEND |
| `MachineCustomDataUpdateEvent`（自定义数据更新） | oldValue, newValue(CompoundTag) | customData 变更 |

### 4.4 配方流水线（核心）

| 事件 | Get | Set | 说明 |
|---|---|---|---|
| **`MachineRecipeModifyEvent.Before`（配方修改前）** | recipe.in | recipe.out | 找到配方后、开工前；写 null 拒绝该配方 |
| `MachineRecipeModifyEvent.After`（配方修改后） | recipe.in | recipe.out | 修改后视图（观测） |
| **`MachineFuelRecipeModifyEvent`（燃料配方修改）** | recipe.in | recipe.out | 燃料配方专用 modify |
| **`MachineBeforeRecipeWorkingEvent`（配方开工前）** | recipe | (cancel) | 开工前最后拦截 |
| **`MachineOnRecipeWorkingEvent`（配方工作中）** | recipe, progress(int) | (cancel) | 工作中每 tick；取消可暂停推进 |
| `MachineOnRecipeWaitingEvent`（配方等待中） | recipe | — | 进 WAITING（输出爆仓等） |
| `MachineOnRecipeFinishEvent`（配方完成） | recipe | — | 配方完成 |
| `MachineOnConsumeInputsAfterWorkingEvent`（输入消耗后） | recipe | — | 输入消耗完毕 |
| `MachineAfterRecipeWorkingEvent`（配方工作后） | recipe | — | 整体工作结束 |
| `MachineFuelBurningFinishEvent`（燃料燃烧完毕） | recipe(可空) | — | 燃料烧完 |

### 4.5 多方块（`MachineEvent.Multiblock`）

| 事件 | Get | 说明 |
|---|---|---|
| `MachineStructureFormedEvent`（结构成型） | — | 结构成型 |
| `MachineStructureInvalidEvent`（结构破坏） | — | 结构破坏 |
| **`MachineUseCatalystEvent`（使用催化剂）** | catalyst, player, hand | 使用催化剂 |

### 4.6 集成

- `MachineCustomKeyframeEvent`（自定义关键帧，modID=geckolib）：Get instruction(String)；GeckoLib 动画 keyframe 自定义指令响应。

---

## 第五章 暴露参数命名表（连图必查）

| 事件 | 参数 key | 注意 |
|---|---|---|
| 所有事件 | `machine`（机器） | 基类提供 |
| `@Cancelable` | `cancel`（取消，Set Boolean） | 自动注入 |
| Recipe Modify 系列 | **`recipe.in`**（输入配方） / **`recipe.out`**（输出配方） | 带点号 |
| Drops | **`drops.in`**（输入掉落） / **`drops.out`**（输出掉落） | 带点号 |
| RightClick | `interactionResult`（交互结果） | Boolean 类型 |
| RecipeStatusChanged | `oldStatus`（旧状态） / `newStatus`（新状态） | 已 toString，String 类型 |
| StateChanged | `oldState`（旧状态） / `newState`（新状态） | String |
| CustomDataUpdate | `oldValue`（旧值） / `newValue`（新值） | CompoundTag |
| NeighborChanged | `block`（邻居方块） / `fromPos`（来源位置） | 显示名为 pos |
| OnRecipeWorking | `recipe`（配方） / `progress`（进度，int） | progress 单位 tick |
| RightClick | `player`（玩家） / `heldItem`（手持物品） / `hand`（手） / `hit`（击中结果） | hit 是 BlockHitResult |
| Placed | `player`（玩家，LivingEntity） / `itemStack`（物品堆） | 注意是 LivingEntity 不是 Player |
| UseCatalyst | `catalyst`（催化剂，ItemStack） / `player`（玩家） / `hand`（手） | — |

---

## 第六章 实战教程（七个完整范例）

### 教程 1：超频机器（duration *0.5）

**目标**：所有配方时间减半。

**步骤**：
1. 机器定义编辑器 → Events 选项卡 → 添加 `MachineRecipeModifyEvent.Before` 图
2. 左侧暴露参数自动包含：`machine`(Get)、`recipe.in`(Get)、`recipe.out`(Set)、`cancel`(Set)
3. 拖入节点：
   - `recipe modifier`：`multiplier=0.5`, `addition=0`
   - `modify recipe`
4. 连线：
   - `recipe.in` → `modify recipe.in`
   - `recipe modifier.modifier` → `modify recipe.duration modifier`
   - `modify recipe.out` → `recipe.out`
   - **不接** content modifier（保持 identity 跳过 copy，性能友好）

### 教程 2：产量翻倍（仅输出侧）

事件同上。

- `recipe modifier`(mul=2) → `modify recipe.content modifier`
- `recipe.in` → `modify recipe.in`
- `modify recipe.out` → `recipe.out`
- `internalContentIO` 配置项设为 `OUT`（content side 不连线）
- duration modifier 不连（保持 identity）

### 教程 3：拒绝特定配方

事件 `MachineRecipeModifyEvent.Before`：

1. `recipe.in` → `recipe info`
2. `recipe info.recipe id` → `equal.a`
3. `string` 节点设置目标 id（如 `mymod:dangerous_recipe`） → `equal.b`
4. `equal.out` → `select.condition`
5. `null` 节点 → `select.true`，`recipe.in` → `select.false`
6. `select.out` → `recipe.out`

效果：命中黑名单 id 的配方被吞为 null，机器跳过。

### 教程 4：配方进度比较器输出（红石）

事件 `MachineOnRecipeWorkingEvent`：

1. `recipe` → `recipe info` 取 `duration`
2. `progress` 与 `15` 通过 `multiplier` 相乘 → 临时值
3. 临时值 → `div.a`，`recipe info.duration` → `div.b`
4. `div.out` → `round` → 整数 0~15
5. 触发链：事件触发 → `set machine signal`(mode=ANALOG, signal 接 round.out)

> 想要每个面分别输出？把 mode 改 SIGNAL，连 side 输入。

### 教程 5：自动持久计数器

事件 `MachineOnRecipeFinishEvent`：

1. `machine` → `machine info` 取 `customData`
2. `compound reader.tag` 接 customData，`key="finishedCount"`，`type=Integer` → 取出当前次数
3. `adder`：当前次数 + 1
4. `compound writer.tag`(原 customData), `key`("finishedCount"), `value`(累加结果) → 输出新 tag
5. 触发链：事件触发 → `compound writer` → `set custom data`(把新 tag 写回 machine)

> **关键**：图本身无状态，跨 tick 数据**只能靠 customData 持久化**。

### 教程 6：右键拦截 + 给予物品

事件 `MachineRightClickEvent`：

1. `heldItem` → `itemstack info` → 取 `item`
2. `item` → `equal` 与某个目标 `item` 节点比较
3. 命中分支：触发链 → `give player item`(target=`player`, itemstack=新构造) + `set custom data` 写"已使用"标记
4. `equal.out` → `interactionResult`（true=SUCCESS=拦截原生右键）

### 教程 7：多方块部件联动 tick

事件（部件机器的）`MachineTickEvent`：

1. `machine` → `part info` → `controllers` 列表
2. `for loop` 触发节点：start=0, end=`controllers.size`（用 `list reader.size`）
3. loop body 每次触发：
   - `list reader.in=controllers`, `index=for loop.index` → 取出 controller
   - 对 controller 做读写（`multiblock info`、`set custom data`）

### 教程 8（额外）：每 60 tick 给玩家发消息

事件 `MachineTickEvent`：

1. `machine` → `machine info.custom data`
2. `compound reader`(key=tickCounter, Integer) → 当前计数
3. `adder` +1 → 新计数
4. `mod` 新计数 % 60 → 余数
5. `equal` 余数 == 0
6. 触发链：事件触发 → `if else`(condition=equal.out)
   - True 分支：`get entities`(找半径内玩家) → 遍历 → `print` 信息
   - 任意分支末尾：`compound writer` 写回新计数 → `set custom data`

---

## 第七章 高级技巧

### 7.1 动态配方

`recipe create` 节点接受 NBT/JSON 字符串。结合 `compound writer` 节点拼出配方 NBT，可让机器在某事件中临时生成配方。配合 `MachineRecipeModifyEvent.Before`，可实现"根据手持物动态生成配方"。

### 7.2 自定义暴露参数（图内全局变量）

左侧面板可创建自定义 ExposedParameter（不止事件参数）。Get/Set 节点（LDLib `parameters` 组）可读写它们。**这是同一图内的全局变量**，但**不跨执行**（每次事件触发都重置，要持久还得 customData）。

### 7.3 cache 节点

`cache` 节点保留上次执行值，可减少重计算。注意：图每次执行都重新 process，cache 是节点字段保留语义（节点实例本身在多次执行间复用）。

### 7.4 错误观测

`print` / `console log` 节点排错。重要节点（如 `set custom data`）失败不抛异常，只是静默不执行——通过 `print` 注入观测点确认执行链。

### 7.5 类型适配器

LDLib 内置 `TypeAdapter`：`Object → bool/number/string` 等基础转换自动完成。所以 `bool`/`number` 节点的 in 端口是 Object 也能正常工作。`MinecraftTypeAdapters` 注册了 Vector3f↔BlockPos、Direction↔int 等。

### 7.6 多输入聚合节点的扩展

`adder`/`multiplier`/`min`/`max`/`and`/`or`/`pack list` 都基于 `ListMergeNode`。当你连一根线后会自动多生一个 in 端口，可无限扩展。

### 7.7 触发节点的并行 vs 顺序

触发链严格顺序执行（按连线顺序）。多输出触发端口（如 `if else` 的 True/Else）是互斥分支。`for loop` 的 loopBody 在每次 index 递增时都会触发一遍下游。

### 7.8 数据节点的求值时机

数据节点是惰性的：被某个触发节点（或下游）读取时才计算。如果一个数据节点的输出没被任何端口连接，它**不会执行**。这意味着纯观测节点（如只接到 `print`）必须挂在触发链上才会输出。

---

## 第八章 避坑清单（必读）

| # | 坑 | 解决 |
|---|---|---|
| 1 | 触发节点不挂 trigger 链不执行 | `Set*` / `place block` / `give player item` 等必须从事件 trigger 入口连过来 |
| 2 | `recipe.in` 不连到 `recipe.out` → 配方变 null 被吞 | 透传也要显式连一根线 |
| 3 | 图无状态，无法记忆上次值 | 用 `set custom data` + `compound reader` 持久化 |
| 4 | Cancelable 写 false 不能强制不取消 | cancel 信号是"或"语义 |
| 5 | RecipeStatus 是 String 不是枚举 | bind 时已 toString，比较用字符串 |
| 6 | 客户端事件不能改服务端状态 | ClientTick 只用于渲染/特效 |
| 7 | identity Modifier 不会触发 copy | 性能优化，但意味"什么都不改"的图无效果 |
| 8 | `mod` 节点是整数取模 | 浮点取模需要自己拼 a - floor(a/b)*b |
| 9 | `for loop` 的 index 在 loop completed 后保留最大值 | 完成分支谨慎使用 index |
| 10 | `command` 节点以 OP 权限运行 | 不要把玩家输入直接拼进 command（注入风险） |
| 11 | `random` 每次 process 重新随机 | 要稳定值就 cache 或写入 customData |
| 12 | 图保存在 MachineDefinition 里，热重载需要重开 | 编辑后保存→加载机器才生效 |
| 13 | 不同事件类型必须建独立图 | `MachineEventGraphProcessor` 严格 class 校验 |
| 14 | `MachinePlacedEvent.player` 是 LivingEntity 不是 Player | 需要 Player 时要做类型判断 |
| 15 | `MachineDropsEvent` 写 `drops.out` 是**整体替换**不是追加 | 想追加要先读 `drops.in` 再 merge |
| 16 | KubeJS 集成里事件双发：图触发 + KJS 触发 | 不要在两边重复实现同一逻辑 |
| 17 | `place block` 不会触发邻居 update | 需要的话用 `command` 节点跑 `/setblock` |
| 18 | NBT 类型混淆 | `compound reader` 的 type 配置必须匹配实际存的类型，否则返回 null |
| 19 | 图里没有 wait/delay 节点 | 用 customData 存 tick 计数 + Tick 事件实现 |
| 20 | `select` 节点两个分支都会被求值（数据节点惰性但已连接的就求值） | 性能敏感场景考虑用 `if else` 触发分支 |

---

## 第九章 参考定位

### 源码路径

- **节点处理器入口**：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/common/graphprocessor/MachineEventGraphProcessor.java`
- **图视图**：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/common/graphprocessor/MachineEventGraphView.java`
- **事件基类**：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/common/machine/definition/config/event/MachineEvent.java`
- **MBD2 节点目录**：`Multiblocked2-1.20.1/src/main/java/com/lowdragmc/mbd2/common/graphprocessor/node/`
- **LDLib 节点目录**：`LDLib-MultiLoader-1.20.1/common/src/main/java/com/lowdragmc/lowdraglib/gui/graphprocessor/nodes/`
- **图视图 Widget**：`LDLib-MultiLoader-1.20.1/common/src/main/java/com/lowdragmc/lowdraglib/gui/graphprocessor/widget/GraphViewWidget.java`
- **底层 BaseNode/BaseGraph**：`LDLib-MultiLoader-1.20.1/common/src/main/java/com/lowdragmc/lowdraglib/gui/graphprocessor/data/`

### 关键类

| 类 | 作用 |
|---|---|
| `BaseGraph` | 图数据模型 |
| `BaseNode` | 节点基类（数据节点） |
| `TriggerNode` / `LinearTriggerNode` | 触发节点基类 |
| `ListMergeNode<T>` | 多输入聚合节点基类 |
| `ListInputNode<I>` | 多输入收集节点基类 |
| `ExposedParameter<T>` | 暴露参数（事件↔图桥接） |
| `TriggerProcessor` | 触发式图执行器（MBD2 处理器的父类） |
| `ContentModifier` | 配方内容/duration 修改函数 |
| `IO` | NONE/IN/OUT/BOTH 枚举 |
| `MBDRecipe` | MBD2 配方对象 |
| `MBDRecipeSerializer` | 配方序列化器（NBT/JSON 互转） |

### 注解

| 注解 | 作用 |
|---|---|
| `@LDLRegister(name, group, modID)` | 节点/事件注册到编辑器，按 group 分组 |
| `@InputPort(name, tips)` | 节点输入端口 |
| `@OutputPort(name, tips, priority)` | 节点输出端口 |
| `@Configurable(name, showName, canCollapse)` | 节点 GUI 配置项（不参与连线） |
| `@NumberRange(range)` | 数值配置范围限制 |
| `@CustomPortBehavior(field)` | 自定义端口生成逻辑（如多输入自适应） |
| `@CustomPortInput(field)` | 自定义端口输入处理逻辑 |
| `@GraphParameterGet/Set(identity, displayName, type, tips)` | 事件字段→图参数映射 |
| `@Cancelable`（Forge 原生） | 标记事件可取消，自动注入 cancel 出参 |

---

## 附录 A：图执行流程伪代码

```
on MachineEvent.postCustomEvent():
    machineEvents.postGraphEvent(this):
        processor = lookup(this.class)  # 按 class 严格匹配
        if processor != null:
            processor.postEvent(this):
                event.bindParameters(graph.exposedParameters)
                # 深度优先执行所有节点
                for trigger in graph.entryPoints:
                    trigger.execute()  # 沿触发链推进
                event.gatherParameters(graph.exposedParameters)
    postKubeJSEvent()  # 同时分发到 KubeJS（如安装）
```

## 附录 B：建议的图开发流程

1. **明确事件**：先确定要响应哪个 MachineEvent，查第四章表
2. **明确暴露参数**：查第五章表确认 key 命名
3. **从输出倒推**：先放最终的 Set 节点（`recipe.out` / `cancel` / `set custom data`），再倒推中间逻辑
4. **触发链优先于数据链**：先把触发流串起来，再连数据
5. **加观测点**：关键节点接 `print`，运行验证后再删
6. **保存导出**：图保存在 MachineDefinition，可与机器 JSON 一起导出共享

---

**文档版本**：v1.0  
**对应代码版本**：MBD2 1.20.1 主分支  
**适用场景**：自定义机器逻辑、配方修改、多方块联动、UI 联动、KubeJS 协作开发
