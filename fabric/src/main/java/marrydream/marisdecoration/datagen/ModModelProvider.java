package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModItem;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.resources.ResourceLocation;

public final class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generator) {
        generator.woodProvider(ModBlock.TEAK_LOG).logWithHorizontal(ModBlock.TEAK_LOG).wood(ModBlock.TEAK_WOOD);
        generator.woodProvider(ModBlock.STRIPPED_TEAK_LOG).logWithHorizontal(ModBlock.STRIPPED_TEAK_LOG).wood(ModBlock.STRIPPED_TEAK_WOOD);
        generator.createCrossBlockWithDefaultItem(ModBlock.TEAK_SAPLING, BlockModelGenerators.TintState.NOT_TINTED);
        var leavesModel = ModelTemplates.LEAVES.create(ModBlock.TEAK_LEAVES,
                net.minecraft.data.models.model.TextureMapping.cube(ModBlock.TEAK_LEAVES), generator.modelOutput);
        generator.blockStateOutput.accept(net.minecraft.data.models.blockstates.MultiVariantGenerator.multiVariant(
                ModBlock.TEAK_LEAVES, net.minecraft.data.models.blockstates.Variant.variant()
                        .with(net.minecraft.data.models.blockstates.VariantProperties.MODEL, leavesModel)));
        BlockModelGenerators.BlockFamilyProvider teak = generator.family(ModBlock.TEAK_PLANKS);
        teak.stairs(ModBlock.TEAK_STAIRS);
        teak.slab(ModBlock.TEAK_SLABS);
        // 栅栏、栅栏门、压力板、按钮都走原版模板；物品模型由数据生成器补成「父级指向方块模型」，
        // 与手写方块状态（活板门、墙、屋顶）不同，它们不需要另写 models/item。
        teak.fence(ModBlock.TEAK_FENCE);
        teak.fenceGate(ModBlock.TEAK_FENCE_GATE);
        teak.pressurePlate(ModBlock.TEAK_PRESSURE_PLATE);
        teak.button(ModBlock.TEAK_BUTTON);
        // 风化柚木木板只换贴图，模型和柚木木板一样是立方体全贴图
        generator.family(ModBlock.WEATHERED_TEAK_PLANKS);

        generator.family(ModBlock.STEEL_BLOCK);
        generator.family(ModBlock.CYAN_STEEL_BLOCK);
        generator.family(ModBlock.BLACK_STEEL_BLOCK);
    }

    @Override
    public void generateItemModels(ItemModelGenerators generator) {
        generator.generateFlatItem(ModItem.BUBBLE_TEA, ModelTemplates.FLAT_ITEM);
        generator.generateFlatItem(ModItem.DETAIL_CHISEL, ModelTemplates.FLAT_ITEM);
        // 伪装放置器：这一阶段临时复用细工凿的贴图（纹理文件是 copycat_placer.png 的副本，
        // 见 assets/maris-decoration/textures/item/）。等正式贴图出来只需要换那个 png。
        generator.generateFlatItem(ModItem.COPYCAT_PLACER, ModelTemplates.FLAT_ITEM);
    }
}
