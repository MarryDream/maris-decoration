package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.init.ModBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.block.Block;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.BlockStatePropertyLootCondition;
import net.minecraft.loot.condition.SurvivesExplosionLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.predicate.StatePredicate;
import net.minecraft.state.property.BooleanProperty;

import java.util.Set;

public final class ModLootTableProvider extends FabricBlockLootTableProvider {
    public ModLootTableProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate() {
        Set<Block> specialDrops = Set.of(
                ModBlock.TEAK_SLABS, ModBlock.STEEL_SLABS, ModBlock.CYAN_STEEL_SLABS, ModBlock.BLACK_STEEL_SLABS,
                ModBlock.STEEL_PLUG_DOOR, ModBlock.STEEL_PLUG_DOOR_WITH_ROOF,
                ModBlock.TEAK_STEEL_PLUG_DOOR_WITH_ROOF, ModBlock.CYAN_STEEL_PLUG_DOOR_WITH_ROOF,
                ModBlock.BLACK_STEEL_PLUG_DOOR_WITH_ROOF,
                ModBlock.COPYCAT_GUARDRAIL
        );
        for (Block block : ModBlock.registeredBlocks()) {
            if (!specialDrops.contains(block)) {
                addDrop(block);
            }
        }
        addDrop(ModBlock.TEAK_SLABS, slabDrops(ModBlock.TEAK_SLABS));
        addDrop(ModBlock.STEEL_SLABS, slabDrops(ModBlock.STEEL_SLABS));
        addDrop(ModBlock.CYAN_STEEL_SLABS, slabDrops(ModBlock.CYAN_STEEL_SLABS));
        addDrop(ModBlock.BLACK_STEEL_SLABS, slabDrops(ModBlock.BLACK_STEEL_SLABS));
        addDrop(ModBlock.STEEL_PLUG_DOOR, doorDrops(ModBlock.STEEL_PLUG_DOOR));
        addDrop(ModBlock.STEEL_PLUG_DOOR_WITH_ROOF, doorDrops(ModBlock.STEEL_PLUG_DOOR_WITH_ROOF));
        addDrop(ModBlock.TEAK_STEEL_PLUG_DOOR_WITH_ROOF, doorDrops(ModBlock.TEAK_STEEL_PLUG_DOOR_WITH_ROOF));
        addDrop(ModBlock.CYAN_STEEL_PLUG_DOOR_WITH_ROOF, doorDrops(ModBlock.CYAN_STEEL_PLUG_DOOR_WITH_ROOF));
        addDrop(ModBlock.BLACK_STEEL_PLUG_DOOR_WITH_ROOF, doorDrops(ModBlock.BLACK_STEEL_PLUG_DOOR_WITH_ROOF));

        // 伪装护栏：每个已存在的面各掉一个，所以四个方向各一条掉落池。
        // 走战利品表而不是在代码里发掉落，好处是原版会自动处理「创造模式不掉落」。
        // 两个伪装材质不在这里，它们由 CopycatGuardrailBlockEntity 掉落。
        addDrop(ModBlock.COPYCAT_GUARDRAIL, LootTable.builder()
                .pool(facePool(CopycatGuardrailBlock.NORTH))
                .pool(facePool(CopycatGuardrailBlock.EAST))
                .pool(facePool(CopycatGuardrailBlock.SOUTH))
                .pool(facePool(CopycatGuardrailBlock.WEST)));
    }

    /** 该方向存在时掉一个伪装护栏。 */
    private LootPool.Builder facePool(BooleanProperty face) {
        return LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1.0F))
                .conditionally(SurvivesExplosionLootCondition.builder())
                .conditionally(BlockStatePropertyLootCondition.builder(ModBlock.COPYCAT_GUARDRAIL)
                        .properties(StatePredicate.Builder.create().exactMatch(face, true)))
                .with(ItemEntry.builder(ModBlock.COPYCAT_GUARDRAIL));
    }
}
