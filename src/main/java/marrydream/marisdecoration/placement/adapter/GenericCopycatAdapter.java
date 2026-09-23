package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * 兜底 adapter：处理所有「是伪装方块、但前面几个 adapter 都不认识」的情况。
 *
 * <h2>为什么需要它</h2>
 * {@code copycat_base} 在本 mod 的语境里几乎从不需要被主动放置，但第三方 mod 完全可以继承 Create 的
 * {@link com.simibubi.create.content.decoration.copycat.CopycatBlock} 做出新方块。
 * {@link CreateCopycatAdapter} 已经覆盖了那个分支；这里真正兜的是「Copycats+ 改了自己的类型层级 /
 * 出现一个本 mod 没适配过的伪装方块实现」——那时至少要把结构放对，只缺材质，
 * 而不是让玩家点了没反应、又说不清为什么。
 *
 * <h2>它认识什么</h2>
 * 按能力（接口）判断，不按具体方块判断：
 * <ul>
 *   <li>{@code com.simibubi.create.content.decoration.copycat.CopycatBlock} +
 *       {@code CopycatBlockEntity} → 单材质；</li>
 *   <li>{@code com.copycatsplus.copycats.foundation.copycat.ICopycatBlock} +
 *       {@code ICopycatBlockEntity} → 单材质。</li>
 * </ul>
 * 两者都通过反射以外的直接类型判断完成（Copycats+ 是 {@code modCompileOnly}，
 * 但引用它的分支只在 {@code isModLoaded} 成立时才会被装上，见 {@link BuiltinAdapters#all()}）。
 *
 * <h2>它不认识什么</h2>
 * multistate（按 property 分材质）没有办法在这里兜底——property 名字只有方块自己知道，
 * 而那正是 {@link CopycatsMultistateAdapter} 存在的原因。真的遇到未适配的 multistate 实现，
 * 这里会把结构放出来、一个材质都不铺，并在返回值里如实报告「没铺任何材质」。
 */
public final class GenericCopycatAdapter implements CopycatPlacementAdapter {

    /** 兜底槽键名前缀，完整键名带上方块注册名（不同方块的材质不应该共用一条预设）。 */
    public static final String SLOT_PREFIX = "generic.copycat";

    private static final String LABEL = "maris-decoration.copycat_placer.slot.generic_copycat";
    private static final String GROUP = "maris-decoration.copycat_placer.group.main";

    /** 某个方块的 slot 键名。 */
    public static String slotKey(Block block) {
        return SLOT_PREFIX + "." + net.minecraft.registry.Registries.BLOCK.getId(block);
    }

    @Override
    public boolean supports(Block block) {
        if (block instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock) {
            return true;
        }
        return BuiltinAdapters.copycatsLoaded()
                && block instanceof com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
    }

    @Override
    public int priority() {
        return BuiltinAdapters.PRIORITY_GENERIC;
    }

    @Override
    public String name() {
        return "generic:copycat";
    }

    @Override
    public List<AdapterSlot> slots(BlockState state, PlacementConfig config) {
        String key = slotKey(state.getBlock());
        return List.of(AdapterSlot.of(key, LABEL, GROUP, true, config.slots().get(key)));
    }

    @Override
    public void apply(PlacementContext context) {
        String key = slotKey(context.state().getBlock());
        BlockState material = context.material(key);
        if (material == null) {
            return;
        }
        if (context.world().getBlockEntity(context.pos())
                instanceof com.simibubi.create.content.decoration.copycat.CopycatBlockEntity entity) {
            if (entity.hasCustomMaterial()) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.ALREADY_CAMOUFLAGED);
                return;
            }
            if (!context.claim(material)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
                return;
            }
            entity.setMaterial(material);
            entity.setConsumedItem(new ItemStack(material.getBlock()));
            context.markApplied(key);
            return;
        }
        // Copycats+ 分支：写成独立的私有方法，避免在没装 Copycats+ 的环境里
        // 因为方法体内的类型引用而被 verify 阶段碰到。
        if (!BuiltinAdapters.copycatsLoaded()) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_BLOCK_ENTITY);
            return;
        }
        applyCopycats(context, key, material);
    }

    private static void applyCopycats(PlacementContext context, String key, BlockState material) {
        if (!(context.world().getBlockEntity(context.pos())
                instanceof com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity entity)) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_BLOCK_ENTITY);
            return;
        }
        if (entity.hasCustomMaterial()) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.ALREADY_CAMOUFLAGED);
            return;
        }
        if (!context.claim(material)) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
            return;
        }
        entity.setMaterial(material);
        entity.setConsumedItem(new ItemStack(material.getBlock()));
        context.markApplied(key);
    }
}
