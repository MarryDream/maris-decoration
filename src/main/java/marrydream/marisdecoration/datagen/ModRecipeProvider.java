package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModInfo;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.data.server.recipe.ShapelessRecipeJsonBuilder;
import net.minecraft.data.server.recipe.SingleItemRecipeJsonBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

public final class ModRecipeProvider extends FabricRecipeProvider {

    /**
     * 锌锭标签。用标签而不是直接引用 {@code create:zinc_ingot}，与 Create 自己的伪装板配方保持一致，
     * 这样其它把锌锭加进该标签的模组也能通用。
     */
    private static final TagKey<Item> ZINC_INGOTS =
            TagKey.of(RegistryKeys.ITEM, new Identifier("c", "zinc_ingots"));

    public ModRecipeProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate(Consumer<RecipeJsonProvider> exporter) {
        offerPlanksRecipe(exporter, ModBlock.TEAK_PLANKS,
                marrydream.marisdecoration.worldgen.ModWorldGeneration.TEAK_LOG_ITEMS, 4);
        offerBarkBlockRecipe(exporter, ModBlock.TEAK_WOOD, ModBlock.TEAK_LOG);
        offerBarkBlockRecipe(exporter, ModBlock.STRIPPED_TEAK_WOOD, ModBlock.STRIPPED_TEAK_LOG);
        offerStairs(exporter, ModBlock.TEAK_STAIRS, ModBlock.TEAK_PLANKS);
        offerStairs(exporter, ModBlock.STEEL_STAIRS, ModBlock.STEEL_BLOCK);
        offerStairs(exporter, ModBlock.CYAN_STEEL_STAIRS, ModBlock.CYAN_STEEL_BLOCK);
        offerStairs(exporter, ModBlock.BLACK_STEEL_STAIRS, ModBlock.BLACK_STEEL_BLOCK);
        offerSlab(exporter, ModBlock.TEAK_SLABS, ModBlock.TEAK_PLANKS);
        offerSlab(exporter, ModBlock.STEEL_SLABS, ModBlock.STEEL_BLOCK);
        offerSlab(exporter, ModBlock.CYAN_STEEL_SLABS, ModBlock.CYAN_STEEL_BLOCK);
        offerSlab(exporter, ModBlock.BLACK_STEEL_SLABS, ModBlock.BLACK_STEEL_BLOCK);

        // 其余木板衍生品：形状、数量、分类与分组都照抄原版（见 data/minecraft/recipes/oak_*）。
        // 原版的这几个配方由 BlockFamily 生成，自带 group，所以这里手写而不用
        // RecipeProvider 的 offerPressurePlateRecipe / offerShapelessRecipe——它们不带分组。
        offerFence(exporter, ModBlock.TEAK_FENCE, ModBlock.TEAK_PLANKS);
        offerFenceGate(exporter, ModBlock.TEAK_FENCE_GATE, ModBlock.TEAK_PLANKS);
        offerPressurePlate(exporter, ModBlock.TEAK_PRESSURE_PLATE, ModBlock.TEAK_PLANKS);
        offerButton(exporter, ModBlock.TEAK_BUTTON, ModBlock.TEAK_PLANKS);

        // 伪装护栏：与 Create 的伪装板规则一致——切石机，1 个锌锭出 4 个。
        SingleItemRecipeJsonBuilder
                .createStonecutting(Ingredient.fromTag(ZINC_INGOTS), RecipeCategory.BUILDING_BLOCKS,
                        ModBlock.COPYCAT_GUARDRAIL, 4)
                .criterion("has_zinc_ingot", conditionsFromTag(ZINC_INGOTS))
                .offerTo(exporter, ModInfo.id("copycat_guardrail_from_zinc_ingots_stonecutting"));
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

    /** 栅栏：4 木板 + 2 木棍出 3 个，与原版 {@code oak_fence} 一致。 */
    private static void offerFence(Consumer<RecipeJsonProvider> exporter, ItemConvertible result, ItemConvertible planks) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, result, 3)
                .group("wooden_fence")
                .input('W', planks).input('#', Items.STICK)
                .pattern("W#W").pattern("W#W")
                .criterion(hasItem(planks), conditionsFromItem(planks)).offerTo(exporter);
    }

    /** 栅栏门：2 木板 + 4 木棍出 1 个，与原版 {@code oak_fence_gate} 一致。 */
    private static void offerFenceGate(Consumer<RecipeJsonProvider> exporter, ItemConvertible result, ItemConvertible planks) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.REDSTONE, result, 1)
                .group("wooden_fence_gate")
                .input('#', Items.STICK).input('W', planks)
                .pattern("#W#").pattern("#W#")
                .criterion(hasItem(planks), conditionsFromItem(planks)).offerTo(exporter);
    }

    /** 压力板：2 木板出 1 个，与原版 {@code oak_pressure_plate} 一致。 */
    private static void offerPressurePlate(Consumer<RecipeJsonProvider> exporter, ItemConvertible result, ItemConvertible planks) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.REDSTONE, result, 1)
                .group("wooden_pressure_plate")
                .input('#', planks).pattern("##")
                .criterion(hasItem(planks), conditionsFromItem(planks)).offerTo(exporter);
    }

    /** 按钮：1 木板出 1 个，与原版 {@code oak_button} 一致。 */
    private static void offerButton(Consumer<RecipeJsonProvider> exporter, ItemConvertible result, ItemConvertible planks) {
        ShapelessRecipeJsonBuilder.create(RecipeCategory.REDSTONE, result)
                .input(planks).group("wooden_button")
                .criterion(hasItem(planks), conditionsFromItem(planks)).offerTo(exporter);
    }

}
