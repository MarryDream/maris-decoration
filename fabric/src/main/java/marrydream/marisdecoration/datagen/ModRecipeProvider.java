package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModInfo;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import java.util.function.Consumer;

public final class ModRecipeProvider extends FabricRecipeProvider {

    /**
     * 锌锭标签。用标签而不是直接引用 {@code create:zinc_ingot}，与 Create 自己的伪装板配方保持一致，
     * 这样其它把锌锭加进该标签的模组也能通用。
     */
    private static final TagKey<Item> ZINC_INGOTS =
            TagKey.create(Registries.ITEM, new ResourceLocation("c", "zinc_ingots"));

    public ModRecipeProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void buildRecipes(Consumer<FinishedRecipe> exporter) {
        planksFromLogs(exporter, ModBlock.TEAK_PLANKS,
                marrydream.marisdecoration.worldgen.ModWorldGeneration.TEAK_LOG_ITEMS, 4);
        woodFromLogs(exporter, ModBlock.TEAK_WOOD, ModBlock.TEAK_LOG);
        woodFromLogs(exporter, ModBlock.STRIPPED_TEAK_WOOD, ModBlock.STRIPPED_TEAK_LOG);
        offerStairs(exporter, ModBlock.TEAK_STAIRS, ModBlock.TEAK_PLANKS);
        offerSlab(exporter, ModBlock.TEAK_SLABS, ModBlock.TEAK_PLANKS);

        // 其余木板衍生品：形状、数量、分类与分组都照抄原版（见 data/minecraft/recipes/oak_*）。
        // 原版的这几个配方由 BlockFamily 生成，自带 group，所以这里手写而不用
        // RecipeProvider 的 offerPressurePlateRecipe / offerShapelessRecipe——它们不带分组。
        offerFence(exporter, ModBlock.TEAK_FENCE, ModBlock.TEAK_PLANKS);
        offerFenceGate(exporter, ModBlock.TEAK_FENCE_GATE, ModBlock.TEAK_PLANKS);
        offerPressurePlate(exporter, ModBlock.TEAK_PRESSURE_PLATE, ModBlock.TEAK_PLANKS);
        offerButton(exporter, ModBlock.TEAK_BUTTON, ModBlock.TEAK_PLANKS);

        // 伪装护栏：与 Create 的伪装板规则一致——切石机，1 个锌锭出 4 个。
        SingleItemRecipeBuilder
                .stonecutting(Ingredient.of(ZINC_INGOTS), RecipeCategory.BUILDING_BLOCKS,
                        ModBlock.COPYCAT_GUARDRAIL, 4)
                .unlockedBy("has_zinc_ingot", has(ZINC_INGOTS))
                .save(exporter, ModInfo.id("copycat_guardrail_from_zinc_ingots_stonecutting"));
        offerCopycatLadder(exporter, ModBlock.COPYCAT_STEEL_FIXED_LADDER,
                "copycat_steel_fixed_ladder_from_zinc_ingots_stonecutting");
        offerCopycatLadder(exporter, ModBlock.COPYCAT_STEEL_VERTICAL_LADDER,
                "copycat_steel_vertical_ladder_from_zinc_ingots_stonecutting");
    }

    private static void offerCopycatLadder(Consumer<FinishedRecipe> exporter, ItemLike result, String id) {
        SingleItemRecipeBuilder
                .stonecutting(Ingredient.of(ZINC_INGOTS), RecipeCategory.BUILDING_BLOCKS, result, 6)
                .unlockedBy("has_zinc_ingot", has(ZINC_INGOTS))
                .save(exporter, ModInfo.id(id));
    }

    private static void offerStairs(Consumer<FinishedRecipe> exporter, ItemLike result, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result, 4)
                .define('#', input).pattern("#  ").pattern("## ").pattern("###")
                .unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    private static void offerSlab(Consumer<FinishedRecipe> exporter, ItemLike result, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result, 6)
                .define('#', input).pattern("###")
                .unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    /** 栅栏：4 木板 + 2 木棍出 3 个，与原版 {@code oak_fence} 一致。 */
    private static void offerFence(Consumer<FinishedRecipe> exporter, ItemLike result, ItemLike planks) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, result, 3)
                .group("wooden_fence")
                .define('W', planks).define('#', Items.STICK)
                .pattern("W#W").pattern("W#W")
                .unlockedBy(getHasName(planks), has(planks)).save(exporter);
    }

    /** 栅栏门：2 木板 + 4 木棍出 1 个，与原版 {@code oak_fence_gate} 一致。 */
    private static void offerFenceGate(Consumer<FinishedRecipe> exporter, ItemLike result, ItemLike planks) {
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, result, 1)
                .group("wooden_fence_gate")
                .define('#', Items.STICK).define('W', planks)
                .pattern("#W#").pattern("#W#")
                .unlockedBy(getHasName(planks), has(planks)).save(exporter);
    }

    /** 压力板：2 木板出 1 个，与原版 {@code oak_pressure_plate} 一致。 */
    private static void offerPressurePlate(Consumer<FinishedRecipe> exporter, ItemLike result, ItemLike planks) {
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, result, 1)
                .group("wooden_pressure_plate")
                .define('#', planks).pattern("##")
                .unlockedBy(getHasName(planks), has(planks)).save(exporter);
    }

    /** 按钮：1 木板出 1 个，与原版 {@code oak_button} 一致。 */
    private static void offerButton(Consumer<FinishedRecipe> exporter, ItemLike result, ItemLike planks) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, result)
                .requires(planks).group("wooden_button")
                .unlockedBy(getHasName(planks), has(planks)).save(exporter);
    }

}
