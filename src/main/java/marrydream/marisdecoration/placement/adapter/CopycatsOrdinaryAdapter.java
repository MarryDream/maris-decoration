package marrydream.marisdecoration.placement.adapter;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Copycats+ <b>普通（单材质）</b>伪装方块的 adapter。
 *
 * <p>覆盖 {@code ICopycatBlock} 的实现，但<b>不</b>覆盖 multistate（那是
 * {@link CopycatsMultistateAdapter} 的事，它的优先级更靠前）。典型成员：
 * {@code copycat_slab} / {@code copycat_stairs} / {@code copycat_vertical_slice} 等。
 *
 * <h2>存储模型</h2>
 * {@link ICopycatBlockEntity} 上只有一份 {@code getMaterial()} / {@code getConsumedItem()}
 * （{@code CCCopycatBlockEntity} 的两个字段），所以只有一个 material slot。
 * 但 Copycats+ 单材质方块的身份<b>更细致</b>：同一种方块在不同形态下是不同的 block
 * （例如竖直半砖与水平半砖是两个 block），所以 slot 键里带上方块注册名，
 * 避免以后同一份预设被套到两个不同模型上时材质串味。
 *
 * <h2>付账语义：保持 Copycats+ 原本的语义</h2>
 * 与 Create 一样：{@code setMaterial(BlockState)} + {@code setConsumedItem(ItemStack)}，
 * 已经被 {@link ICopycatBlockEntity#hasCustomMaterial()} 命中的方块实体跳过不覆盖。
 * {@code ICopycatBlockEntity#setMaterial} 自带「状态相同的邻居沿用其材质」的继承规则，
 * 这里直接复用，不重复实现。
 */
public final class CopycatsOrdinaryAdapter implements CopycatPlacementAdapter {

    /** 单材质槽的键名前缀，完整键名由 {@link #slotKey} 生成。 */
    public static final String SLOT_PREFIX = "copycats.ordinary";

    private static final String LABEL = "maris-decoration.copycat_placer.slot.copycats_ordinary";
    private static final String GROUP = "maris-decoration.copycat_placer.group.main";

    /** 某个方块的 slot 键名。 */
    public static String slotKey(Block block) {
        return SLOT_PREFIX + "." + net.minecraft.registry.Registries.BLOCK.getId(block);
    }

    @Override
    public boolean supports(Block block) {
        // multistate 也是 ICopycatBlock 的子接口，但它的 adapter 优先级更高，会先被问到；
        // 这里的否定判断只是让"谁能处理"这件事在单看这个类时也是自洽的。
        return block instanceof ICopycatBlock && !(block instanceof com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock);
    }

    @Override
    public int priority() {
        return BuiltinAdapters.PRIORITY_COPYCATS_ORDINARY;
    }

    @Override
    public String name() {
        return "copycats:ordinary";
    }

    @Override
    public List<AdapterSlot> slots(BlockState state, PlacementConfig config) {
        String key = slotKey(state.getBlock());
        return List.of(AdapterSlot.of(key, LABEL, GROUP, true, config.slots().get(key)));
    }

    /**
     * 材质准入：直接问 Copycats+ 自己（{@link ICopycatBlock#getAcceptedBlockState}）。
     *
     * <p>world/pos 传的是<b>真实世界</b>：判据把「轮廓必须是完整立方体」写在 {@code world != null}
     * 里面，传 null 会让它整段被跳过（半砖、玻璃板就会混进来）。
     */
    @Override
    public boolean acceptsMaterial(BlockState material, String slotKey, PlacementConfig config, BlockView world, BlockPos pos) {
        if (material.isAir() || !(config.block() instanceof ICopycatBlock copycat)) {
            return false;
        }
        return copycat.getAcceptedBlockState(CopycatPlacementAdapter.castWorld(world), pos, new ItemStack(material.getBlock()), null) != null;
    }

    @Override
    public void apply(PlacementContext context) {
        if (!(context.world().getBlockEntity(context.pos()) instanceof ICopycatBlockEntity blockEntity)) {
            return;
        }
        String key = slotKey(context.state().getBlock());
        BlockState material = context.material(key);
        if (material == null) {
            return;
        }
        // 已经有材质就不动它，与 Copycats+ 自己的 use() 行为一致
        if (blockEntity.hasCustomMaterial()) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.ALREADY_CAMOUFLAGED);
            return;
        }
        // 服务端准入校验：不合法的材质留空，不阻止放置（客户端过滤只是 UX）
        if (!context.acceptsMaterial(material, key)) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.REJECTED_MATERIAL);
            return;
        }
        // 拿不出这个材质就留空，不阻止放置
        if (!context.claim(material)) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
            return;
        }

        blockEntity.setMaterial(material);
        blockEntity.setConsumedItem(new ItemStack(material.getBlock()));
        context.markApplied(key);
    }
}
