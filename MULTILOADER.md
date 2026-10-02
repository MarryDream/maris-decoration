# 多版本开发

Canonical implementation / 默认 active target：**1.20.1-fabric**。
1.20.1 Fabric 与 Forge 已实现；1.21.1 两个节点仍未完成，不发布空壳 jar。

## 支持矩阵与依赖

节点属性的唯一配置位置为 `versions/<node>/gradle.properties`。
全部目标使用 Mojmap；Fabric 原 Yarn `1.20.1+build.10` 已通过 Loom 机械转换。

| 节点 | Java | Loader | Create | Copycats+ | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| 1.20.1-fabric | 17 | Fabric 0.17.2 / API 0.92.12+1.20.1 | Fabric 6.0.8.1+build.1744-mc1.20.1 | 3.0.10+mc.1.20.1-fabric | implemented / canonical |
| 1.20.1-forge | 17 | Forge 47.4.10 | 6.0.8-291 (Maven slim) | 3.0.10+mc.1.20.1-forge | implemented |
| 1.21.1-forge | 21 | Forge 52.1.0 | unavailable | unavailable | blocked / unavailable dependency |
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
Forge 开发环境显式使用 Registrate MC1.20-1.3.3、Flywheel 1.0.6-beta-266、Ponder 1.0.91、
MixinExtras 0.4.1，均与 Create 6.0.8-291 发布提交一致。普通实例使用正常的 Create 发布包。
`MOD_ID = maris_decoration` 用于 Forge loader / metadata / 事件；`NAMESPACE = maris-decoration`
用于 registry、ResourceLocation、资源和存档标识。Fabric loader mod id 仍为 `maris-decoration`。

## 工程与 Java

Stonecutter 0.9.8 / Gradle 9.7.1；Fabric Loom 1.17.21；Forge 1.20.1 与 NeoForge 的
toolkit 入口为 ModDevGradle 2.0.140。不使用 Architectury runtime 或 Architectury Loom。
LegacyForge 不支持 Forge 1.21.1，该节点未套用错误的插件。

`common/` 放共享业务、注册目录、测试、客户端计算/屏幕/门 renderer 和资源；`fabric/`、`forge/`
提供入口、注册生命周期、网络、事件和模型接口。datagen providers 保留 Fabric bootstrap。
共享源码通过 source inclusion 编译，不生成 common mod jar；非 active 共享源码由 Stonecutter 生成，避免重复索引。
`gradle/stonecutter-sources.gradle` 将标准 Stonecutter prepare/generate/merge 任务连接到实际源码根；
版本 conditional 用于 common 中少量 MC API 差异，Loader 差异由独立目录负责。
共享生成资源目前是 1.20.1 格式，1.21.1 的数据格式适配留给后续阶段。

安装 JDK 17 与 21。**Gradle / IntelliJ Gradle JVM 使用 21**；Fabric compile release、toolchain、
Forge 与 Fabric 游戏 launcher 固定 17；1.21.1 toolchain/release 为 21。
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
gradlew.bat stonecutterSwitchTo1.20.1-forge
gradlew.bat :1.20.1-forge:runClient
gradlew.bat :1.20.1-forge:runServer
gradlew.bat :1.20.1-forge:build
gradlew.bat buildActive
gradlew.bat buildAll
```

切其它节点使用相同 `stonecutterSwitchTo<node>` 命令，或 IntelliJ 的 Stonecutter 切换操作。
切换后重新 Gradle sync；IDE 选择对应节点的运行配置。
这些配置通过 Gradle 启动，使用节点的 Java launcher，避免 IDEA 模块名转换引起 classpath 错误。
提交/交付前切回 canonical；不要手改 controller 的 active 字符串。
根目录 `build`、`runClient`、`runServer` 等兼容命令仅委托给 active 节点。
scaffold 的 build/run 明确失败；`buildAll` 严格包含全部节点，因此本阶段也会失败。
不需要旧版 `chiseledBuild` 任务。

Fabric jar 在 `versions/1.20.1-fabric/build/libs/`，游戏目录在 `run/1.20.1-fabric/`。
正式 Forge jar：`versions/1.20.1-forge/build/libs/maris-decoration-1.20.1-forge-1.0.6.jar`。
Forge 的 `build/devlibs/` 是未重混淆开发产物；普通实例使用 `build/libs/` 的正式 jar。
Forge 游戏目录为 `run/1.20.1-forge/`，metadata 与共享资源直接打包，不含 Fabric metadata。
原有 `run/` 的世界和配置没有迁移或覆盖；新服务器首次启动需要接受 Minecraft EULA。

```text
gradlew.bat :1.20.1-fabric:runTeakTest
gradlew.bat :1.20.1-fabric:runTeakClientCheck
powershell -ExecutionPolicy Bypass -File tools/run-harness.ps1 -Loader fabric
powershell -ExecutionPolicy Bypass -File tools/run-harness.ps1 -Loader forge
powershell -ExecutionPolicy Bypass -File tools/run-harness.ps1 -Loader forge -WithCopycats
gradlew.bat :1.20.1-forge:runTeakTest
gradlew.bat :1.20.1-forge:runClient -PforgeClientCheck
gradlew.bat :1.20.1-forge:runTeakClientCheck
```

`-WithCopycats` 只给验证运行追加 Copycats+ runtime；正常运行不加载它。
Forge `runDatagen` / `checkDatagenFresh` 委托 canonical Fabric target，共享资源只生成一份。
`-PforgeClientCheck` 在真实主菜单检查动态模型并截图、自动退出；不影响正常运行。
`runTeakClientCheck` 使用 `versions/1.20.1-fabric/build/teak-client/saves/teak-validation` 世界。
首次运行前，将已停止的 `runTeakTest` 的 `versions/1.20.1-fabric/build/teak-test/world` 复制至该目录。
Forge 使用相应的 `versions/1.20.1-forge/build/teak-client/saves/teak-validation` 隔离世界，
也可复制已停止的该节点测试世界；会生成客户端验证报告和 screenshots。
资源辅助生成脚本仍从仓库根运行：`node tools/gen-copycat-guardrail.mjs`、
`node tools/gen-layered-copycat-board.mjs`。

共享 textures/models/lang/recipes/loot/tags 和 datagen 输入只保留一份。
`common/src/main/generated/` 是跟踪的 canonical datagen 产物；其 `.cache/` 忽略。
`versions/*/gradle.properties` 跟踪，build/run/Gradle cache/预处理生成源码忽略。
