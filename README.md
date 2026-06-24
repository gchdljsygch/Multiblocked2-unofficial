Multiblocked2-unofficial
========================

Languages: [中文](#中文) | [English](#english)

中文
----

### 关于本项目

Multiblocked2-unofficial fork 自 Multiblocked 2 的 Minecraft 1.20.1 版本，继承了原模组的所有功能，合并了 mbd2thread，修复了部分 bug，并添加了一些更有利于魔改的方法和事件。后续预计会兼容更多模组。

### 项目信息

- Minecraft: 1.20.1
- Forge: 47.4.10
- Java: 17
- Mod ID: `mbd2`
- License: GPL-3.0-only

### 协议说明

上游 Multiblocked2 以 LGPL-3.0 发布。本 fork 合并了以 GPL-3.0 发布的 mbd2thread 代码，因此当前组合发布物整体按 GPL-3.0-only 发布。

### 构建

```powershell
.\gradlew.bat build
```

### 上游项目

原项目可以在这里找到：[Multiblocked](https://www.curseforge.com/minecraft/mc-mods/multiblocked "multiblocked")

- Discord: [https://discord.com/invite/sDdf2yD9bh](https://discord.com/invite/sDdf2yD9bh)
- GitHub: [https://github.com/Low-Drag-MC/Multiblocked2](https://github.com/Low-Drag-MC/Multiblocked2)
- CurseForge: [https://www.curseforge.com/minecraft/mc-mods/multiblocked2](https://www.curseforge.com/minecraft/mc-mods/multiblocked2)
- Modrinth: [https://modrinth.com/mod/multiblocked2](https://modrinth.com/mod/multiblocked2)
- QQ: 827533873

### Gradle 依赖示例

```gradle
repositories {
    maven {
        name "firstdarkdev"
        url "https://maven.firstdark.dev/snapshots"
    }
}

dependencies {
    implementation fg.deobf("com.lowdragmc.ldlib:ldlib-forge-{minecraft_version}:{latest_version}") { transitive = false }
    implementation fg.deobf("com.lowdragmc.multiblocked2:Multiblocked2:{minecraft_version}-{latest_version}") { transitive = false }
}
```

### 上游功能简介

Multiblocked2 (mbd2) 是继 mbd1 (Multiblocked) 之后更强大的可视化自定义机器与多方块结构模组。它非常灵活，同时保持接近原版的体验，并吸收了 MultiblockTweaker 与 Modular Machinery 的部分设计思路。

#### Demo

![demo](https://i.ibb.co/2ZLFgFb/demo.gif "demo")

Demo：火焰基座会燃烧煤炭生成 EMI fire，并照亮周围环境。无需编写任何代码，也可以创建这样一台机器。

#### 新编辑器

![editor](https://i.ibb.co/XVFC0mm/editor.png "editor")

新的编辑器更强大、更现代，也更易使用。mbd2 提供了类似 Unity 的游戏设计引擎，让机器自定义流程更高效。

#### 逻辑节点图

![node graph](https://i.ibb.co/7bqL3j6/nodegraph.png "node graph")

节点图是一种全新的事件逻辑配置方式。你不再需要使用 KubeJS 来定义复杂功能。如果你熟悉 Blender 的节点系统、Unreal 的蓝图或 Unity 的 Shader Graph，这个功能会很容易上手。

MBD2 允许你通过节点图监听各种机器事件，并配置丰富的执行逻辑，为自定义机器提供更强的创作能力。

English
-------

### About This Project

Multiblocked2-unofficial is forked from the Minecraft 1.20.1 version of Multiblocked 2. It keeps all features from the original mod, merges mbd2thread, fixes several bugs, and adds methods and events that are more useful for modpack customization. More mod compatibility is planned for future updates.

### Project Information

- Minecraft: 1.20.1
- Forge: 47.4.10
- Java: 17
- Mod ID: `mbd2`
- License: GPL-3.0-only

### License Notes

Upstream Multiblocked2 is licensed under LGPL-3.0. This fork includes code from mbd2thread, which is licensed under GPL-3.0, so the combined distribution is released under GPL-3.0-only.

### Build

```powershell
.\gradlew.bat build
```

### Upstream Project

The original project can be found here: [Multiblocked](https://www.curseforge.com/minecraft/mc-mods/multiblocked "multiblocked")

- Discord: [https://discord.com/invite/sDdf2yD9bh](https://discord.com/invite/sDdf2yD9bh)
- GitHub: [https://github.com/Low-Drag-MC/Multiblocked2](https://github.com/Low-Drag-MC/Multiblocked2)
- CurseForge: [https://www.curseforge.com/minecraft/mc-mods/multiblocked2](https://www.curseforge.com/minecraft/mc-mods/multiblocked2)
- Modrinth: [https://modrinth.com/mod/multiblocked2](https://modrinth.com/mod/multiblocked2)
- QQ: 827533873

### Gradle Dependency Example

```gradle
repositories {
    maven {
        name "firstdarkdev"
        url "https://maven.firstdark.dev/snapshots"
    }
}

dependencies {
    implementation fg.deobf("com.lowdragmc.ldlib:ldlib-forge-{minecraft_version}:{latest_version}") { transitive = false }
    implementation fg.deobf("com.lowdragmc.multiblocked2:Multiblocked2:{minecraft_version}-{latest_version}") { transitive = false }
}
```

### Upstream Feature Overview

Multiblocked2 (mbd2) is the more powerful visual custom machine and multiblock structure mod after mbd1 (Multiblocked). Mbd2 is an extremely flexible yet vanilla-esque multiblock mod that embraces aspects of MultiblockTweaker and Modular Machinery.

#### Demo

![demo](https://i.ibb.co/2ZLFgFb/demo.gif "demo")

Demo: A fire pedestal burns coal into EMI fire and lights the surroundings. Imagine creating such a machine without writing a single line of code.

#### New Editor

![editor](https://i.ibb.co/XVFC0mm/editor.png "editor")

The new editor is more powerful, more modern, and easier to use. mbd2 helps you work without code and provides a Unity-like game design engine for more efficient machine customization.

#### Logic Node Graph

![node graph](https://i.ibb.co/7bqL3j6/nodegraph.png "node graph")

The node graph is a new way to configure event logic. You no longer need KubeJS to define complex functionality. If you use Blender's node system, Unreal's Blueprints, or Unity's Shader Graph, this feature should feel familiar.

MBD2 allows you to listen to various machine events through the node graph and configure a wide range of execution logic, giving you extensive creative power.
