# 多版本开发

Canonical implementation / 默认 active target：**1.20.1-fabric**。
本轮只有此节点可以构建和运行；其它三个节点为 scaffold，不发布空壳 jar。

## 支持矩阵与依赖

节点属性的唯一配置位置为 `versions/<node>/gradle.properties`。
全部目标使用 Mojmap；Fabric 原 Yarn `1.20.1+build.10` 已通过 Loom 机械转换。

| 节点 | Java | Loader | Create | Copycats+ | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| 1.20.1-fabric | 17 | Fabric 0.17.2 / API 0.92.12+1.20.1 | Fabric 6.0.8.1+build.1744-mc1.20.1 | 3.0.10+mc.1.20.1-fabric | canonical |
| 1.20.1-forge | 17 | Forge 47.4.10 | 6.0.8-291 (Maven slim) | 3.0.10+mc.1.20.1-forge | scaffold，业务未移植 |
| 1.21.1-forge | 21 | Forge 52.1.0 | unavailable | unavailable | scaffold，依赖阻塞 |
| 1.21.1-neoforge | 21 | NeoForge 21.1.252 | 6.0.11-312 (Maven jar) | 3.0.9+mc.1.21.1-neoforge | scaffold，业务未移植 |

2026-10-02 核实来源：[Forge](https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json)、
[NeoForge Maven](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)、
[Create Maven](https://maven.createmod.net/com/simibubi/create/)、
[Create 发布](https://api.modrinth.com/v2/project/create/version)、
[Copycats+ 发布](https://api.modrinth.com/v2/project/copycats/version)。
两个项目的发布 API 都没有 Forge 1.21.1 artifact，所以四节点是计划矩阵，不能宣称四目标已经可用。
Fabric 1.21.1 不在矩阵中。

Fabric 保留 `fuzs.forgeconfigapiport:forgeconfigapiport-fabric:8.0.0`。
Create 是必需依赖；Copycats+ 保持 `modCompileOnly` 可选兼容，不嵌入 jar。
其它节点的已验证坐标是后续移植的配置入口，尚未启用依赖解析或补齐运行时传递依赖。
Forge-family metadata 使用合法 Loader mod id `maris_decoration`；未来注册资源仍必须保持
`maris-decoration` namespace。此 scaffold metadata 不用于发布。

## 工程与 Java

Stonecutter 0.9.8 / Gradle 9.7.1；Fabric Loom 1.17.21；Forge 1.20.1 与 NeoForge 的
toolkit 入口为 ModDevGradle 2.0.140。不使用 Architectury runtime 或 Architectury Loom。
LegacyForge 不支持 Forge 1.21.1，该节点未套用错误的插件。

`common/` 放共享资源和无直接平台依赖的源码，`fabric/` 放当前平台入口、注册、网络、
Fabric datagen providers 和客户端实现。共享源码通过 source inclusion 编译，不生成 common mod jar。
部分 common 类仍引用尚未抽象的注册/平台实现，本阶段没有完成业务解耦。
`gradle/stonecutter-sources.gradle` 将标准 Stonecutter prepare/generate/merge 任务连接到实际源码根；
版本 conditional 用于 common 中少量 MC API 差异，Loader 差异由独立目录负责。
共享生成资源目前是 1.20.1 格式，1.21.1 的数据格式适配留给后续阶段。

安装 JDK 17 与 21。**Gradle / IntelliJ Gradle JVM 使用 21**；Fabric compile release、toolchain、
游戏 launcher 固定 17；1.21.1 toolchain/release 为 21。
没有写入机器专属 JDK 路径。可在个人 Gradle 属性中配置 `org.gradle.java.installations.paths`。

## 常用命令

Windows 使用 `gradlew.bat`；其它系统使用 `./gradlew`。

```text
gradlew.bat stonecutterSwitchTo1.20.1-fabric
gradlew.bat :1.20.1-fabric:runClient
gradlew.bat :1.20.1-fabric:runServer
gradlew.bat :1.20.1-fabric:runDatagen
gradlew.bat :1.20.1-fabric:checkDatagenFresh
gradlew.bat :1.20.1-fabric:build
gradlew.bat buildActive
gradlew.bat buildAll
```

切其它节点使用相同 `stonecutterSwitchTo<node>` 命令，或 IntelliJ 的 Stonecutter 切换操作。
切换后重新 Gradle sync；IDE 选择带 `(:1.20.1-fabric)` 后缀的新运行配置。
这些配置通过 Gradle 启动，使用节点的 Java launcher，避免 IDEA 模块名转换引起 classpath 错误。
提交/交付前切回 canonical；不要手改 controller 的 active 字符串。
根目录 `build`、`runClient`、`runServer` 等兼容命令仅委托给 active 节点。
scaffold 的 build/run 明确失败；`buildAll` 严格包含全部节点，因此本阶段也会失败。
不需要旧版 `chiseledBuild` 任务。

Fabric jar 在 `versions/1.20.1-fabric/build/libs/`，游戏目录在 `run/1.20.1-fabric/`。
原有 `run/` 的世界和配置没有迁移或覆盖；新服务器首次启动需要接受 Minecraft EULA。

```text
gradlew.bat :1.20.1-fabric:runTeakTest
gradlew.bat :1.20.1-fabric:runTeakClientCheck
powershell -File tools/run-harness.ps1
powershell -File tools/run-harness.ps1 -WithCopycats
```

`-WithCopycats` 只给验证运行追加 Copycats+ runtime；正常运行不加载它。
`runTeakClientCheck` 使用 `versions/1.20.1-fabric/build/teak-client/saves/teak-validation` 世界。
首次运行前，将已停止的 `runTeakTest` 的 `versions/1.20.1-fabric/build/teak-test/world` 复制至该目录。
资源辅助生成脚本仍从仓库根运行：`node tools/gen-copycat-guardrail.mjs`、
`node tools/gen-layered-copycat-board.mjs`。

共享 textures/models/lang/recipes/loot/tags 和 datagen 输入只保留一份。
`common/src/main/generated/` 是跟踪的 canonical datagen 产物；其 `.cache/` 忽略。
`versions/*/gradle.properties` 跟踪，build/run/Gradle cache/预处理生成源码忽略。
