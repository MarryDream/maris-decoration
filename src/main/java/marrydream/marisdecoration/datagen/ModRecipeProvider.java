package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.init.ModBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.item.ItemConvertible;
import net.minecraft.recipe.book.RecipeCategory;

import java.util.function.Consumer;

public final class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate(Consumer<RecipeJsonProvider> exporter) {
        offerStairs(exporter, ModBlock.TEAK_STAIRS, ModBlock.TEAK_PLANKS);
        offerStairs(exporter, ModBlock.STEEL_STAIRS, ModBlock.STEEL_BLOCK);
        offerStairs(exporter, ModBlock.CYAN_STEEL_STAIRS, ModBlock.CYAN_STEEL_BLOCK);
        offerStairs(exporter, ModBlock.BLACK_STEEL_STAIRS, ModBlock.BLACK_STEEL_BLOCK);
        offerSlab(exporter, ModBlock.TEAK_SLABS, ModBlock.TEAK_PLANKS);
        offerSlab(exporter, ModBlock.STEEL_SLABS, ModBlock.STEEL_BLOCK);
        offerSlab(exporter, ModBlock.CYAN_STEEL_SLABS, ModBlock.CYAN_STEEL_BLOCK);
        offerSlab(exporter, ModBlock.BLACK_STEEL_SLABS, ModBlock.BLACK_STEEL_BLOCK);
    }

    private static void offerStairs(Consumer<RecipeJsonProvider> exporter, ItemConvertible result, ItemConvertible input) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, result, 4)
                .input('#', input).pattern("#  ").pattern("## ").pattern("###")
                .criterion(hasItem(input), conditionsFromItem(input)).offerTo(exporter);
    }

    private static void offerSlab(Consumer<RecipeJsonProvider> exporter, ItemConvertible result, ItemConvertible input) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, result, 6)
                .input('#', input).pattern("###")
                .criterion(hasItem(input), conditionsFromItem(input)).offerTo(exporter);
    }

}
