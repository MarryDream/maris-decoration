package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.init.ModBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import java.util.Set;

public final class ModLootTableProvider extends FabricBlockLootTableProvider {
    public ModLootTableProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate() {
        Set<Block> specialDrops = Set.of(
                ModBlock.TEAK_LEAVES,
                ModBlock.TEAK_SLABS, ModBlock.STEEL_PLUG_DOOR,
                ModBlock.COPYCAT_GUARDRAIL,
                ModBlock.LAYERED_COPYCAT_BOARD
        );
        for (Block block : ModBlock.registeredBlocks()) {
            if (!specialDrops.contains(block)) {
                dropSelf(block);
            }
        }
        add(ModBlock.TEAK_LEAVES, createLeavesDrops(ModBlock.TEAK_LEAVES, ModBlock.TEAK_SAPLING,
                0.05F, 0.0625F, 0.083333336F, 0.1F));
        add(ModBlock.TEAK_SLABS, createSlabItemTable(ModBlock.TEAK_SLABS));
        add(ModBlock.STEEL_PLUG_DOOR, createDoorTable(ModBlock.STEEL_PLUG_DOOR));

        // 伪装护栏：每个已存在的面各掉一个，所以四个方向各一条掉落池。
        // 走战利品表而不是在代码里发掉落，好处是原版会自动处理「创造模式不掉落」。
        // 两个伪装材质不在这里，它们由 CopycatGuardrailBlockEntity 掉落。
        add(ModBlock.COPYCAT_GUARDRAIL, LootTable.lootTable()
                .withPool(facePool(CopycatGuardrailBlock.NORTH))
                .withPool(facePool(CopycatGuardrailBlock.EAST))
                .withPool(facePool(CopycatGuardrailBlock.SOUTH))
                .withPool(facePool(CopycatGuardrailBlock.WEST)));

        // 分层伪装薄板刻意<b>没有</b>战利品表：它的 12 个 1px 槽位住在方块实体里，
        // 方块状态根本表达不出来，所以落多少由代码按占用掩码计算
        // （见 LayeredCopycatBoardBlock#onStateReplaced）。这里只是把严格校验豁免掉，
        // 免得 datagen 因为「这个方块没有 loot table」而报错。
        excludeFromStrictValidation(ModBlock.LAYERED_COPYCAT_BOARD);
    }

    /** 该方向存在时掉一个伪装护栏。 */
    private LootPool.Builder facePool(BooleanProperty face) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(ExplosionCondition.survivesExplosion())
                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(ModBlock.COPYCAT_GUARDRAIL)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(face, true)))
                .add(LootItem.lootTableItem(ModBlock.COPYCAT_GUARDRAIL));
    }
}
