package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModItem;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.Models;

public final class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator generator) {
        generator.registerLog(ModBlock.TEAK_LOG).log(ModBlock.TEAK_LOG).wood(ModBlock.TEAK_WOOD);
        generator.registerLog(ModBlock.STRIPPED_TEAK_LOG).log(ModBlock.STRIPPED_TEAK_LOG).wood(ModBlock.STRIPPED_TEAK_WOOD);
        generator.registerTintableCross(ModBlock.TEAK_SAPLING, BlockStateModelGenerator.TintType.NOT_TINTED);
        var leavesModel = Models.LEAVES.upload(ModBlock.TEAK_LEAVES,
                net.minecraft.data.client.TextureMap.all(ModBlock.TEAK_LEAVES), generator.modelCollector);
        generator.blockStateCollector.accept(net.minecraft.data.client.VariantsBlockStateSupplier.create(
                ModBlock.TEAK_LEAVES, net.minecraft.data.client.BlockStateVariant.create()
                        .put(net.minecraft.data.client.VariantSettings.MODEL, leavesModel)));
        BlockStateModelGenerator.BlockTexturePool teak = generator.registerCubeAllModelTexturePool(ModBlock.TEAK_PLANKS);
        teak.stairs(ModBlock.TEAK_STAIRS);
        teak.slab(ModBlock.TEAK_SLABS);
        // 栅栏、栅栏门、压力板、按钮都走原版模板；物品模型由数据生成器补成「父级指向方块模型」，
        // 与手写方块状态（活板门、墙、屋顶）不同，它们不需要另写 models/item。
        teak.fence(ModBlock.TEAK_FENCE);
        teak.fenceGate(ModBlock.TEAK_FENCE_GATE);
        teak.pressurePlate(ModBlock.TEAK_PRESSURE_PLATE);
        teak.button(ModBlock.TEAK_BUTTON);
        // 风化柚木木板只换贴图，模型和柚木木板一样是立方体全贴图
        generator.registerCubeAllModelTexturePool(ModBlock.WEATHERED_TEAK_PLANKS);

        generator.registerCubeAllModelTexturePool(ModBlock.STEEL_BLOCK);
        generator.registerCubeAllModelTexturePool(ModBlock.CYAN_STEEL_BLOCK);
        generator.registerCubeAllModelTexturePool(ModBlock.BLACK_STEEL_BLOCK);
    }

    @Override
    public void generateItemModels(ItemModelGenerator generator) {
        generator.register(ModItem.BUBBLE_TEA, Models.GENERATED);
        generator.register(ModItem.DETAIL_CHISEL, Models.GENERATED);
        // 伪装放置器：这一阶段临时复用细工凿的贴图（纹理文件是 copycat_placer.png 的副本，
        // 见 assets/maris-decoration/textures/item/）。等正式贴图出来只需要换那个 png。
        generator.register(ModItem.COPYCAT_PLACER, Models.GENERATED);
    }
}
