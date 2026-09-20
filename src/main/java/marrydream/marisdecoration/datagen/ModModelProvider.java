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
        BlockStateModelGenerator.BlockTexturePool teak = generator.registerCubeAllModelTexturePool(ModBlock.TEAK_PLANKS);
        teak.stairs(ModBlock.TEAK_STAIRS);
        teak.slab(ModBlock.TEAK_SLABS);

        BlockStateModelGenerator.BlockTexturePool steel = generator.registerCubeAllModelTexturePool(ModBlock.STEEL_BLOCK);
        steel.stairs(ModBlock.STEEL_STAIRS);
        steel.slab(ModBlock.STEEL_SLABS);

        BlockStateModelGenerator.BlockTexturePool cyanSteel = generator.registerCubeAllModelTexturePool(ModBlock.CYAN_STEEL_BLOCK);
        cyanSteel.stairs(ModBlock.CYAN_STEEL_STAIRS);
        cyanSteel.slab(ModBlock.CYAN_STEEL_SLABS);

        BlockStateModelGenerator.BlockTexturePool blackSteel = generator.registerCubeAllModelTexturePool(ModBlock.BLACK_STEEL_BLOCK);
        blackSteel.stairs(ModBlock.BLACK_STEEL_STAIRS);
        blackSteel.slab(ModBlock.BLACK_STEEL_SLABS);
    }

    @Override
    public void generateItemModels(ItemModelGenerator generator) {
        generator.register(ModItem.BUBBLE_TEA, Models.GENERATED);
        generator.register(ModItem.DETAIL_CHISEL, Models.GENERATED);
        generator.register(ModItem.STEEL_HAMMER, Models.GENERATED);
        generator.register(ModItem.REBAR, Models.GENERATED);
    }
}
