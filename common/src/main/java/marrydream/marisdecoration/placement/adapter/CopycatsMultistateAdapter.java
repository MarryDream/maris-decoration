package marrydream.marisdecoration.placement.adapter;

import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage;
import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.ArrayList;
import java.util.List;

/**
 * Copycats+ <b>multistate</b> 伪装方块的 adapter。
 *
 * <p>覆盖所有实现 {@link IMultiStateCopycatBlock} 的方块（多层薄板、多层台阶、多层墙…）。
 * 它比 {@link CopycatsOrdinaryAdapter} 更特殊，所以优先级更靠前。
 *
 * <h2>存储模型：slot 由方块自己说了算</h2>
 * multistate 的材质不是「一份」，而是<b>按 property 一份</b>，property 的名字与数量
 * <b>由每个方块自己定义</b>：{@link IMultiStateCopycatBlock#storageProperties()} 就是权威清单
 * （{@code IMultiStateCopycatBlockEntity#init()} 也用它来建
 * {@link MaterialItemStorage}）。所以这里<b>绝不硬编码</b>任何 property 名字——
 * 换了 Copycats+ 版本、或者第三方新增一个 multistate 方块，这里的代码都不用改。
 *
 * <p>由于 multistate 的占用信息来自方块状态里的多个 {@code BooleanProperty}
 * （{@link IMultiStateCopycatBlock#partExists(BlockState, String)} 是权威判据），
 * 「当前配置下这个 slot 存不存在」也由它回答，而不是靠预设里的字符串猜。
 *
 * <h2>付账语义：保持 Copycats+ 原本的语义</h2>
 * Copycats+ 的 multistate 放置（{@code IMultiStateCopycatBlock#setPlacedBy} 的字节码）是：
 * <ol>
 *   <li>对每个存在的 part，若已 {@code hasCustomMaterial(property)} 就跳过；</li>
 *   <li>{@code setMaterial(property, material)}；</li>
 *   <li>只有当 {@code getAllConsumedItems()} 里<b>还没有</b>同一种物品时才
 *       {@code setConsumedItem(property, stack)} 并扣一个。</li>
 * </ol>
 * 也就是「同一个方块上同一种材质只记一次账」。这里逐条对齐：
 * {@link PlacementContext#isPaid} 覆盖了「{@code getAllConsumedItems()} 里已有」与
 * 「本次放置前面的 slot 已经付过」两种情况。
 *
 * <h2>鲁棒性</h2>
 * {@link MaterialItemStorage#getMaterialItem(String)} 对未知 property 返回 {@code null}
 * （字节码里就是一次 {@code Map.get}），而 {@code setMaterial(property, ...)} 会直接解引用它。
 * 所以这里在写之前先确认 property 真的在存储里，避免因为版本差异或半初始化的方块实体而 NPE。
 */
public final class CopycatsMultistateAdapter implements CopycatPlacementAdapter {

    /** slot 键名前缀，完整键名为 {@code copycats.multistate.<property>}。 */
    public static final String SLOT_PREFIX = "copycats.multistate";

    private static final String LABEL_PREFIX = "maris-decoration.copycat_placer.slot.copycats_multistate.";
    private static final String GROUP_PREFIX = "maris-decoration.copycat_placer.group.part.";

    /** 某个 property 对应的 slot 键名。 */
    public static String slotKey(String property) {
        return SLOT_PREFIX + "." + property;
    }

    /** 从 slot 键名反解 property；不是本 adapter 的键时返回 {@code null}。 */
    public static String propertyOf(String slotKey) {
        String prefix = SLOT_PREFIX + ".";
        return slotKey.startsWith(prefix) ? slotKey.substring(prefix.length()) : null;
    }

    @Override
    public boolean supports(Block block) {
        return block instanceof IMultiStateCopycatBlock;
    }

    @Override
    public int priority() {
        return BuiltinAdapters.PRIORITY_COPYCATS_MULTISTATE;
    }

    @Override
    public String name() {
        return "copycats:multistate";
    }

    /**
     * 枚举槽位。
     *
     * <p>这里<b>刻意</b>用一个内部提升出来的 {@code preview} 状态来判断「哪些 part 可以配」：
     * Copycats+ 的 multistate 方块出厂时一个 part 都没有（比如 {@code copycat_board} 六个面全 false），
     * 如果按 config 的真实状态枚举，玩家会在界面上看到一整列「结构不存在」，根本无从下手。
     *
     * <p><b>preview 只用于这里（发现潜在槽位）</b>。config 的真实状态才是唯一事实来源——
     * GUI 显示属性、点击修改、NBT 保存、最终放置全都走 config，绝不走 preview。
     * 早先的 bug 就是把 preview 当成了展示状态返回给 GUI，于是「界面全 true、实际全 false」分裂，
     * 放下去的就是看不见的幽灵方块。
     */
    @Override
    public List<AdapterSlot> slots(BlockState state, PlacementConfig config) {
        if (!(state.getBlock() instanceof IMultiStateCopycatBlock block)) {
            return List.of();
        }
        BlockState preview = discoverState(state, block);
        String group = GROUP_PREFIX + BuiltInRegistries.BLOCK.getKey(state.getBlock());
        List<AdapterSlot> slots = new ArrayList<>();
        for (String property : block.storageProperties()) {
            String key = slotKey(property);
            slots.add(AdapterSlot.of(key, LABEL_PREFIX + property,
                    PropertySpec.humanize(property),
                    group, block.partExists(preview, property), config.slots().get(key)));
        }
        return slots;
    }

    // ---------------------------------------------------------------- 默认状态与校验

    /**
     * 默认配置：找出这个方块的<b>最小有效状态</b>（至少一个可见 part）。
     *
     * <p>不能直接用 {@code getDefaultState()}：Copycats+ 里有些方块的默认状态虽然合法，
     * 但代表「零个部件」。实测 {@code copycat_half_layer} 就是
     * {@code negative_layers=0 / positive_layers=0}——这种状态能真的放进世界，
     * 但完全没有可见结构，占着格子却什么都看不见，再想放别的东西还会提示「这里放不下」。
     *
     * <p>算法见 {@link #minimalValidState}。
     */
    @Override
    public PlacementConfig defaultConfig(Block block) {
        if (!(block instanceof IMultiStateCopycatBlock multiState)) {
            return PlacementConfig.of(block.defaultBlockState());
        }
        return PlacementConfig.of(minimalValidState(block.defaultBlockState(), multiState));
    }

    /** 至少一个 part 存在，否则就是零部件的幽灵方块。 */
    @Override
    public String validateStructure(BlockState state, PlacementConfig config) {
        if (!(state.getBlock() instanceof IMultiStateCopycatBlock block)) {
            // 不是 multistate（理论上不会被派到这里），交给默认实现放行
            return null;
        }
        if (!hasAnyPart(state, block)) {
            return "multistate_no_part";
        }
        return null;
    }

    /**
     * 材质准入：直接问 Copycats+ 自己。
     *
     * <p>{@code getAcceptedBlockState(property, world, pos, stack, face)} 就是它平铺材质时用的
     * 那一个判据，这里原样复用，不自己维护白名单/黑名单。property 用<b>当前槽推导出来的属性</b>，
     * world/pos 传<b>真实世界</b>：判据把「轮廓必须是完整立方体」写在 {@code world != null} 里面，
     * 传 null 会让它整段被跳过（半砖、玻璃板就会混进来）。
     */
    @Override
    public boolean acceptsMaterial(BlockState material, String slotKey, PlacementConfig config, BlockGetter world, BlockPos pos) {
        if (material.isAir()) {
            return false;
        }
        String property = propertyOf(slotKey);
        if (property == null) {
            return false;
        }
        var block = config.block();
        if (!(block instanceof IMultiStateCopycatBlock multiState)) {
            return false;
        }
        BlockState accepted = multiState.getAcceptedBlockState(property, CopycatPlacementAdapter.castWorld(world), pos,
                new ItemStack(material.getBlock()), null);
        return accepted != null;
    }

    /**
     * 求「最小有效状态」：从方块默认状态出发，逐个属性尝试最小的非默认取值，
     * <b>一旦某个 part 存在就立刻停手</b>。
     *
     * <p>与「把所有属性都置到最大」相对：后者虽然也能得到可见结构，但会一次开出所有部件
     * （六个面全开、所有层全占），玩家每次都得先把多余的删掉，而且对 half_layer 这种
     * 层数属性「最大」意味着十几层板叠在一起。这里要的是<b>刚好可见</b>。
     *
     * <p>取值策略（全靠方块自己声明的属性类型，不按方块 id 特判）：
     * <ul>
     *   <li>布尔：false → true；</li>
     *   <li>整数：0 → 最小的正有效值（通常是 1）。负值也会被考虑，取绝对值最小的那个；</li>
     *   <li>枚举：按属性自己的取值顺序逐个试。</li>
     * </ul>
     */
    public static BlockState minimalValidState(BlockState state, IMultiStateCopycatBlock block) {
        if (hasAnyPart(state, block)) {
            return state;
        }
        BlockState candidate = state;
        for (String name : block.storageProperties()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            if (property == null || !candidate.hasProperty(property)) {
                continue;
            }
            for (Object value : candidateValues(property)) {
                BlockState attempt = withUnchecked(candidate, property, value);
                if (attempt == null) {
                    continue;
                }
                candidate = attempt;
                if (hasAnyPart(candidate, block)) {
                    return candidate;
                }
            }
        }
        // 怎么试都没有 part：如实返回原状态，让 validateStructure 去挡住这次放置
        return state;
    }

    /**
     * 一个属性要尝试的取值顺序（跳过当前值）。
     *
     * <p>整数优先试「绝对值最小的正数」——{@code negative_layers} 这类属性在 0 时表示没有部件，
     * 1 才是最小可见结构；同时负值也是一个合法的层方向，所以也试一遍负数。
     */
    private static List<Object> candidateValues(Property<?> property) {
        List<Object> values = new ArrayList<>();
        if (property instanceof BooleanProperty) {
            values.add(Boolean.TRUE);
            return values;
        }
        if (property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty intProperty) {
            int min = intProperty.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0);
            int max = intProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
            // 从 1 开始向外扩：1, -1, 2, -2, ...
            for (int step = 1; step <= Math.max(Math.abs(min), Math.abs(max)); step++) {
                if (step <= max && step != 0) {
                    values.add(step);
                }
                if (-step >= min && -step != 0) {
                    values.add(-step);
                }
            }
            return values;
        }
        // 枚举与其它：按属性自己的取值顺序逐个试
        values.addAll(property.getPossibleValues());
        return values;
    }

    /** 把属性值写进状态；类型对不上时返回 null 而不是抛异常。 */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState withUnchecked(BlockState state, Property<?> property,
                                                                      Object value) {
        if (!(value instanceof Comparable<?> comparable)) {
            return null;
        }
        try {
            return state.setValue((Property<T>) property, (T) comparable);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 用来「发现潜在槽位」的状态：把 {@code storageProperties()} 里的布尔属性全部置 true，
     * 让 GUI 知道这个方块理论上支持哪些 slot。
     *
     * <p>只影响 {@link #slots} 报出的「这个槽能不能配」，<b>不影响</b>任何真实配置。
     * 非布尔属性（层数那种）不动——它们置「最大」会让人以为默认要铺十几层。
     */
    private static BlockState discoverState(BlockState state, IMultiStateCopycatBlock block) {
        if (hasAnyPart(state, block)) {
            return state;
        }
        BlockState candidate = state;
        boolean applied = false;
        for (String name : block.storageProperties()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            if (property instanceof BooleanProperty booleanProperty && candidate.hasProperty(booleanProperty)) {
                candidate = candidate.setValue(booleanProperty, true);
                applied = true;
            }
        }
        return applied && hasAnyPart(candidate, block) ? candidate : state;
    }

    /** 这个状态下有没有至少一个 part 存在——全部结构校验与默认状态搜索都靠它。 */
    private static boolean hasAnyPart(BlockState state, IMultiStateCopycatBlock block) {
        for (String property : block.storageProperties()) {
            if (block.partExists(state, property)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void apply(PlacementContext context) {
        if (!(context.world().getBlockEntity(context.pos()) instanceof IMultiStateCopycatBlockEntity blockEntity)) {
            return;
        }
        if (!(context.state().getBlock() instanceof IMultiStateCopycatBlock block)) {
            return;
        }
        MaterialItemStorage storage = blockEntity.getMaterialItemStorage();
        if (storage == null) {
            return;
        }

        // 遍历顺序取 storageProperties()：它是 Set，顺序不保证，但同一次运行内一致，
        // 且付账判断是逐条累积的（isPaid），与顺序无关。
        for (String property : block.storageProperties()) {
            String key = slotKey(property);
            BlockState material = context.material(key);
            if (material == null) {
                continue;
            }
            // 这个 part 在当前形态下不存在，写了也不显示——直接跳过，与 Copycats+ 的 setPlacedBy 一致
            if (!block.partExists(context.state(), property)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.STRUCTURE_ABSENT);
                continue;
            }
            if (storage.getMaterialItem(property) == null) {
                // 存储里没有这个 property（版本差异 / 半初始化）：不冒险，也不静默吞掉
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_BLOCK_ENTITY);
                continue;
            }
            // 这个 slot 已经有材质就不覆盖，与 Copycats+ 的 use() 一致
            if (storage.hasCustomMaterial(property)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.ALREADY_CAMOUFLAGED);
                continue;
            }
            // 服务端准入校验：按当前 property 问 Copycats+ 自己。
            // 客户端材质选择界面用的是同一个判据，这一步挡的主要是「改过包的客户端」。
            if (!context.acceptsMaterial(material, key)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.REJECTED_MATERIAL);
                continue;
            }
            // 拿不出这个材质就留空，不阻止放置
            if (!context.affordable(material)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
                continue;
            }
            // 先判断"这个材质是不是已经记过账"（对齐 Copycats+ 的 getAllConsumedItems 判断），
            // 再 claim：claim 会把材质挂到本次放置的账本上，之后自己就算"已经记过账"了。
            boolean alreadyAccounted = isAlreadyAccounted(context, storage, material);
            if (!context.claim(material)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
                continue;
            }

            blockEntity.setMaterial(property, material);
            if (!alreadyAccounted) {
                blockEntity.setConsumedItem(property, new ItemStack(material.getBlock()));
            }
            context.markApplied(key);
        }
    }

    /**
     * 这个材质是不是<b>已经记过账</b>了：本次放置前面的 slot 付过，或者这个方块上已经存在
     * 同一种物品的付账记录（对齐 Copycats+ 的 {@code getAllConsumedItems().stream().anyMatch(...)}）。
     *
     * <p>用方块种类而不是 {@code ItemStack.matches} 判断前半条，是因为玩家当初付掉的那一个物品
     * 可能带着 NBT；用完整堆比较会在这种情况下判成「没记过账」，于是又往方块实体里记一份，
     * 拆掉时就会多掉出一个物品。
     */
    private static boolean isAlreadyAccounted(PlacementContext context, MaterialItemStorage storage,
                                              BlockState material) {
        if (context.paid().contains(material)) {
            return true;
        }
        ItemStack probe = new ItemStack(material.getBlock());
        for (ItemStack existing : storage.getAllConsumedItems()) {
            if (marrydream.marisdecoration.platform.StackData.same(existing, probe)) {
                return true;
            }
        }
        return false;
    }
}
