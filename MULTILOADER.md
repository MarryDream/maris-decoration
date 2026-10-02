# 多版本开发

Canonical implementation / 默认 active target：**1.20.1-fabric**。
三个节点均已实现；1.21.1 NeoForge 等待普通实例人工验收。

## 支持矩阵与依赖

节点属性的唯一配置位置为 `versions/<node>/gradle.properties`。
全部目标使用 Mojmap；Fabric 原 Yarn `1.20.1+build.10` 已通过 Loom 机械转换。

| 节点 | Java | Loader | Create | Copycats+ | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| 1.20.1-fabric | 17 | Fabric 0.17.2 / API 0.92.12+1.20.1 | Fabric 6.0.8.1+build.1744-mc1.20.1 | 3.0.10+mc.1.20.1-fabric | implemented / canonical |
| 1.20.1-forge | 17 | Forge 47.4.10 / NeoForge 47.1.106（同一 jar） | 6.0.8-291 (Maven slim) | 3.0.10+mc.1.20.1-forge | implemented / 两个 Loader 普通实例实测通过 |
| 1.21.1-neoforge | 21 | NeoForge 21.1.252 | 6.0.10-280 (Maven slim) | 3.0.9+mc.1.21.1-neoforge | implemented / 待人工验收 |

2026-10-02 核实来源：[Forge](https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json)、
[NeoForge Maven](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)、
[Create Maven](https://maven.createmod.net/com/simibubi/create/)、
[Create 发布](https://api.modrinth.com/v2/project/create/version)、
[Copycats+ 发布](https://api.modrinth.com/v2/project/copycats/version)。
Forge 1.21.1 明确不开发；Fabric 1.21.1 当前没有开发目标。
1.20.1 NeoForge 使用 Forge 节点的正式产物，不设独立节点或源码。

Fabric 保留 `fuzs.forgeconfigapiport:forgeconfigapiport-fabric:8.0.0`。
Create 是必需依赖；Copycats+ 保持 compile-only 可选兼容，不嵌入 jar。
Forge 开发环境显式使用 Registrate MC1.20-1.3.3、Flywheel 1.0.6-beta-266、Ponder 1.0.91、
MixinExtras 0.4.1，均与 Create 6.0.8-291 发布提交一致。普通实例使用正常的 Create 发布包。
NeoForge 1.21.1 固定 Registrate MC1.21-1.3.0+67、Flywheel 1.0.6、Ponder 1.0.82+mc1.21.1，
与 [Create 6.0.10 正式发布](https://github.com/Creators-of-Create/Create/releases/tag/mc1.21.1-6.0.10)
的内嵌依赖一致；MixinExtras 0.5.3 由 NeoForge 提供。普通实例使用 Create 6.0.10 发布包。
`MOD_ID = maris_decoration` 用于 Forge loader / metadata / 事件；`NAMESPACE = maris-decoration`
用于 registry、ResourceLocation、资源和存档标识。Fabric loader mod id 仍为 `maris-decoration`。

## 工程与 Java

Stonecutter 0.9.8 / Gradle 9.7.1；Fabric Loom 1.17.21；Forge 1.20.1 与 NeoForge 的
toolkit 入口为 ModDevGradle 2.0.140。不使用 Architectury runtime 或 Architectury Loom。

`common/` 放共享业务、注册目录、测试、客户端计算/屏幕/门 renderer 和资源；`fabric/`、`forge/`、`neoforge/`
提供入口、注册生命周期、网络、事件和模型接口。datagen providers 保留 Fabric bootstrap。
共享源码通过 source inclusion 编译，不生成 common mod jar；非 active 共享源码由 Stonecutter 生成，避免重复索引。
`gradle/stonecutter-sources.gradle` 将标准 Stonecutter prepare/generate/merge 任务连接到实际源码根；
版本 conditional 用于 common 中少量 MC API 差异，Loader 差异由独立目录负责。
canonical datagen 仍生成一份 1.20.1 数据；`gradle/neoforge-resources.gradle` 将它投影为 1.21.1
目录、配方、战利品谓词、标签和世界生成格式，输出只位于 NeoForge build 目录。
两个版本的 freshness 均检查。textures/models/lang 等视觉资源直接共享。

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
gradlew.bat stonecutterSwitchTo1.21.1-neoforge
gradlew.bat :1.21.1-neoforge:runClient
gradlew.bat :1.21.1-neoforge:runServer
gradlew.bat :1.21.1-neoforge:runDatagen
gradlew.bat :1.21.1-neoforge:checkDatagenFresh
gradlew.bat :1.21.1-neoforge:build
gradlew.bat buildActive
gradlew.bat buildAll
```

切其它节点使用相同 `stonecutterSwitchTo<node>` 命令，或 IntelliJ 的 Stonecutter 切换操作。
切换后重新 Gradle sync；IDE 选择对应节点的运行配置。
这些配置通过 Gradle 启动，使用节点的 Java launcher，避免 IDEA 模块名转换引起 classpath 错误。
NeoForge 的共享 Gradle 运行配置位于 `.run/`；优先选择 `Minecraft Client/Server (:1.21.1-neoforge)`。
提交/交付前切回 canonical；不要手改 controller 的 active 字符串。
根目录 `build`、`runClient`、`runServer` 等兼容命令仅委托给 active 节点。
`buildAll` 构建全部三个正式节点；`buildActive` 只构建当前 active 节点。
不需要旧版 `chiseledBuild` 任务。

Fabric jar 在 `versions/1.20.1-fabric/build/libs/`，游戏目录在 `run/1.20.1-fabric/`。
正式 Forge jar：`versions/1.20.1-forge/build/libs/maris-decoration-1.20.1-forge-1.0.6.jar`。
同一文件用于 Forge 1.20.1-47.4.10 和 NeoForge 1.20.1-47.1.106；metadata 的 Forge 范围为 `[47.1.106,48)`。
Forge 的 `build/devlibs/` 是未重混淆开发产物；普通实例使用 `build/libs/` 的正式 jar。
Forge 游戏目录为 `run/1.20.1-forge/`，metadata 与共享资源直接打包，不含 Fabric metadata。
NeoForge 1.21.1 正式 jar：`versions/1.21.1-neoforge/build/libs/maris-decoration-1.21.1-neoforge-1.0.6.jar`。
游戏目录为 `run/1.21.1-neoforge/`；现代 NeoForge 使用 Mojmap 生产命名，不执行 Legacy Forge SRG 重混淆。
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
powershell -ExecutionPolicy Bypass -File tools/run-harness.ps1 -Loader neoforge
powershell -ExecutionPolicy Bypass -File tools/run-harness.ps1 -Loader neoforge -WithCopycats
gradlew.bat :1.21.1-neoforge:runTeakTest
gradlew.bat :1.21.1-neoforge:runClient -PneoforgeClientCheck
gradlew.bat :1.21.1-neoforge:runTeakClientCheck
```

`-WithCopycats` 只给验证运行追加 Copycats+ runtime；正常运行不加载它。
Forge `runDatagen` / `checkDatagenFresh` 委托 canonical Fabric target，共享资源只生成一份。
`-PforgeClientCheck` 在真实主菜单检查动态模型并截图、自动退出；不影响正常运行。
`runTeakClientCheck` 使用 `versions/1.20.1-fabric/build/teak-client/saves/teak-validation` 世界。
首次运行前，将已停止的 `runTeakTest` 的 `versions/1.20.1-fabric/build/teak-test/world` 复制至该目录。
Forge 使用相应的 `versions/1.20.1-forge/build/teak-client/saves/teak-validation` 隔离世界，
也可复制已停止的该节点测试世界；会生成客户端验证报告和 screenshots。
NeoForge 使用同样的节点内隔离目录；首次客户端测试可在该目录的 `options.txt` 设置
`onboardAccessibility:false`，避免停在首次引导页面。`-PwithCopycats` 启用可选运行依赖。
资源辅助生成脚本仍从仓库根运行：`node tools/gen-copycat-guardrail.mjs`、
`node tools/gen-layered-copycat-board.mjs`。

共享 textures/models/lang/recipes/loot/tags 和 datagen 输入只保留一份。
`common/src/main/generated/` 是跟踪的 canonical datagen 产物；其 `.cache/` 忽略。
`versions/*/gradle.properties` 跟踪，build/run/Gradle cache/预处理生成源码忽略。
