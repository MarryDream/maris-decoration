# 柚木

适用于 Minecraft 1.20.1 / Fabric。新增的资源 ID 均以 `maris-decoration:` 为前缀。

## 方块与玩法

| ID | 名称 |
| --- | --- |
| `teak_log` | 柚木原木 |
| `teak_wood` | 柚木（六面树皮） |
| `stripped_teak_log` | 去皮柚木原木 |
| `stripped_teak_wood` | 去皮柚木 |
| `teak_leaves` | 柚木树叶 |
| `teak_sapling` | 柚木树苗 |

原木和木材支持斧头去皮，并保留放置轴向。四种原木类方块均可合成 4 块已有的柚木木板；2×2 原木合成 3 块对应木材。
树叶采用原版剪刀、精准采集、时运、树苗和木棍掉落规则，不掉苹果。普通树苗掉率 5%，时运 I/II/III 为 6.25% / 8.33% / 10%。
树叶离开原木会自然腐烂，玩家放置的树叶保持。树苗及树叶可堆肥；原木类方块和树苗可用作燃料。

## 自然生成与树形

| 生物群系 | 每区块生成尝试 |
| --- | --- |
| 稀疏丛林 `minecraft:sparse_jungle` | 1 次 |
| 热带草原 `minecraft:savanna` | 25% 概率尝试 1 次 |
| 热带高原 `minecraft:savanna_plateau` | 25% 概率尝试 1 次 |

生成尝试受土壤、空间和地形限制，并不保证每区块有树。旧存档仅在新生成区块自然出现。

树木约 11–16 格高、树冠约 9–13 格宽。单格粗的高直树干在上部伸出 3–5 条短枝，枝梢的圆顶叶团相互交叠，形成不规则宽展树冠。
分枝逐格相连，叶片距支撑原木不超过原版的有效范围。生成前检查树冠空间，不覆盖建筑。
一株树苗即可生长；骨粉和自然生长使用同一配置。人工种植不限制群系，遵循原版树苗的光照、土壤和空间规则。不模拟季节落叶。

设计参考：[新加坡 NParks 的 Tectona grandis](https://www.nparks.gov.sg/florafaunaweb/flora/3/1/3178)、
[Kew 柚木资料](https://powo.science.kew.org/taxon/urn%3Alsid%3Aipni.org%3Anames%3A864923-1/general-information)。
现实依据是高直树干、宽展树冠、宽大对生叶和热带干湿季生态；原版群系与频率属于游戏化映射，并非现实分布的精确模拟。

## 贴图

六张原创 PNG 位于 `src/main/resources/assets/maris-decoration/textures/block/`。
使用内置 imagegen 分别生成，再按最近邻采样整理为 16×16 游戏资源，保留树叶及树苗透明通道。
树叶原图为灰度，方块随生物群系染色；物品使用原版默认树叶色。
已有的柚木木板、活板门贴图保持原样。

实际生成提示词：

- `teak_log.png`: Create a Minecraft Java resource texture PNG: teak tree bark, flat seamless square tile, 16 by 16 pixel art enlarged with nearest-neighbor only. Gray brown bark with irregular shallow vertical dark fissures and scaly warm taupe ridges. Restrained vanilla Minecraft palette, no lighting, no perspective, no text, fills entire image edge to edge. Save output as an available local file.
- `teak_log_top.png`: Square seamless Minecraft teak log end grain texture. Orthographic flat 16x16 pixel art enlarged. Golden honey brown heartwood concentric irregular square rings, lighter thin sapwood, gray brown bark rim around all four edges. Full bleed opaque tile no text no perspective no shadow.
- `stripped_teak_log.png`: Square seamless Minecraft stripped teak log side texture. Orthographic flat 16x16 pixel art enlarged. Warm golden honey brown wood with subtle long vertical darker grain. Full bleed opaque tile, no planks or seams, no text no perspective no shadow.
- `stripped_teak_log_top.png`: Square Minecraft stripped teak log top end grain texture. Orthographic flat 16x16 pixel art enlarged. Warm golden honey brown concentric irregular square growth rings filling tile with pale golden outer sapwood, NO bark rim. Full bleed opaque tile, no text no perspective no shadow.
- `teak_leaves.png`: Minecraft teak leaves block texture seamless square tile. Flat chunky 16x16 pixel art enlarged. Overlapping large broad ovate leaves, distinct central veins, irregular tiny gaps with real transparent alpha. GRAYSCALE ONLY because game applies biome green tint; medium light gray leaf faces with dark gray edges and pale veins. Dense foliage fills edges, no border, no text, no shadows.
- `teak_sapling.png`: Minecraft teak sapling item sprite flat 16x16 pixel art enlarged, centered on genuine transparent alpha background. Single thin upright brown stem rooted exactly at bottom center, 3 opposite pairs of large broad ovate green leaves with pale midribs, largest pair lower down, small fresh green shoot at top. Sparse readable chunky pixel silhouette fills 90 percent square. No soil no pot no ground shadow no text. Vanilla Minecraft pixel art.

## 开发验证

分开执行 `gradlew runDatagen` 与 `gradlew build`：本项目的生成资源被 sourcesJar 使用，同一次调用执行两个任务会触发 Gradle 的任务依赖校验。

`gradlew runTeakTest` 运行独立的开发测试服务器，工作目录为 `build/teak-test`。首次运行应将已接受的本机 `run/eula.txt` 复制到该目录，并使用独立的 `server.properties`（建议固定种子 `20260928`、本机地址、独立端口、普通世界）。不会修改 `run/world` 或其它开发存档。

测试覆盖全部群系的白名单、配方与标签、64 种树形、树叶距离和独立连通性、自然及骨粉生长、障碍保护、采集掉落及腐烂，并在三个目标群系的真实新地形中搜索自然柚木。结果写入 `build/teak-test/teak-selftest.txt`，测试结束自动停止服务器。

客户端验证：把已停止的测试世界复制到 `build/teak-client/saves/teak-validation`，执行 `gradlew runTeakClientCheck`。
该任务自动进入世界副本，验证所有新增方块状态与物品模型，生成树木和方块样品，保存截图后退出。
报告为 `build/teak-client/teak-client-check.txt`，截图为 `build/teak-client/screenshots/teak-tree.png` 和 `teak-blocks.png`。
两个验证任务都会根据报告使 Gradle 在测试失败时退出失败。

本次实测：64 组分散随机种子下树高 11–16 格、宽 9–13 格，无无支撑树叶；服务端 28,526 项断言通过。
三个目标群系均在固定种子的新地形中找到自然柚木。客户端全部新增方块状态与物品模型无缺失贴图，已检查实机截图。
