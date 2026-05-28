# Mbd2Thread

> Multiblocked2 (MBD2) 的附属模组，为 mbd2 机器系统补充**配方多线程**、**Long.MAX 等级 ForgeEnergy**、**Jade HUD 集成**、**外部能源兼容**等能力。

* Minecraft: **1.20.1**
* Mod Loader: **Forge `[47,)`**
* Mod ID: `mbd2thread`

---

## 主要功能

| 模块 | 路径 | 说明 |
|---|---|---|
| 配方线程 Trait | `trait/RecipeThreadTrait` | 让 mbd2 机器以"线程"形式并行/排队执行多份配方，可配置最大并行数、空闲超时等 |
| Long FE 能量系统 | `energy/fe/` | 支持到 `Long.MAX_VALUE` 的 ForgeEnergy 兼容容器、配方能力、Forge Energy Adapter |
| Jade HUD 集成 | `integration/jade/` | 在 Jade tooltip 显示线程状态、Long FE 能量信息 |
| 外部能源兼容 | `integration/energy/` | FluxNetworks (LongEnergy)、Mekanism (StrictEnergy) 双向适配 |
| 节点图扩展 | `graph_processor/` 等 | 为 mbd2 节点图新增事件节点、KubeJS/Java 节点 |
| 缓慢自动构筑 | `SlowAutoBuildScheduler` | 分帧自动放置多方块，避免一次性卡顿 |

详细的节点图使用方式见仓库内：

* [`NODE_GRAPH_GUIDE.zh-CN.md`](./NODE_GRAPH_GUIDE.zh-CN.md)
* [`NODE_GRAPH_TUTORIAL_EVENTS_AND_NODES.zh-CN.md`](./NODE_GRAPH_TUTORIAL_EVENTS_AND_NODES.zh-CN.md)

---

## 依赖

### 强制依赖（缺失则无法加载）

| Mod | 版本范围 | 来源 |
|---|---|---|
| Forge | `[47,)` | https://files.minecraftforge.net/ |
| Minecraft | `1.20.1` | - |
| **ldlib** | `[1.0.40,)` | https://modrinth.com/mod/ldlib |
| **mbd2 (Multiblocked2)** | `[1.0.25,)` | https://modrinth.com/mod/multiblocked2 |
| **geckolib** | `[4.0,)` | https://modrinth.com/mod/geckolib |

> 注：geckolib 是 mbd2 的**隐式字节码依赖**（mbd2 的 `MBDMachine` 字节码内嵌 `software.bernie.geckolib.*` 引用，但 mbd2 自身 `mods.toml` 未声明）。本模组对 mbd2 注入了 `@Mixin(MBDMachine.class)`，Mixin 处理时会触发 ASM 对 MBDMachine 的完整字节码重写，必然解析到 geckolib 类型，因此 mbd2thread 必须显式要求 geckolib。

### 可选集成

| Mod | 用途 |
|---|---|
| Jade | 机器 HUD 信息 |
| Flux Networks | Long FE 能量适配 |
| Mekanism | 焦耳 (Joule) ↔ FE 能量互转 |

---

## 构建

```bash
# 克隆
git clone https://github.com/<your-account>/mbd2thread.git
cd mbd2thread

# 生成 IDE 配置（任选其一）
./gradlew genEclipseRuns        # Eclipse
./gradlew genIntellijRuns       # IntelliJ IDEA

# 编译
./gradlew build

# 输出 jar
ls build/libs/mbd2thread-<version>-1.20.1.jar
```

### dev 环境运行

```bash
./gradlew runClient   # 客户端
./gradlew runServer   # 专用服务端
```

> 提示：dev 环境运行同样需要 geckolib（已在 `build.gradle` 中以 `runtimeOnly` 声明，会自动从 modrinth maven 拉取）。

---

## 协议

本项目以 **GNU General Public License v3.0** 发布，详见 [`LICENSE.txt`](./LICENSE.txt)。

之所以使用 GPL-3.0：上游 [LDLib](https://github.com/Low-Drag-MC/LDLib) 采用 GPL-3.0 协议，本模组作为其派生作品，按 GPL 的传染条款必须以同等或兼容协议发布。GPL-3.0 同时兼容上游 Multiblocked2 的 LGPL-3.0。

---

## 上游致谢

* **[LowDragMC](https://github.com/Low-Drag-MC)** 团队：本项目所依赖的 [LDLib](https://github.com/Low-Drag-MC/LDLib) 与 [Multiblocked2](https://github.com/Low-Drag-MC/Multiblocked2) 的作者
* **GeckoLib** 团队：为 mbd2 提供动画支持
* **Jade** (Snownee) 团队：HUD 集成框架

---

## 作者

Non_coffee
