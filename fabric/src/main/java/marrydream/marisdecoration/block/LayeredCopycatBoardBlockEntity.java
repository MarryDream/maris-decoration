package marrydream.marisdecoration.block;

import com.simibubi.create.content.redstone.RoseQuartzLampBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import marrydream.marisdecoration.block.utils.LayeredBoardParts;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardArea;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import marrydream.marisdecoration.init.ModBlockEntity;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code layered_copycat_board} 的方块实体。
 *
 * <p>所有「看不见的状态」都住在这里，方块状态只留一个 {@code WATERLOGGED}：
 * <ul>
 *   <li>{@link #occupancy} —— <b>12 个 1px 槽位</b>的存在与否，一个 12 位掩码
 *       （6 面 × 2 层，位序见 {@link LayeredBoardSlots#slotBit}）；</li>
 *   <li>{@link #windows} —— 6 个面的窗开关，一个 6 位掩码；</li>
 *   <li>{@link #junctionOwners} —— 细工凿在物理交汇点上选定的显示材质槽（键是候选集合，值是槽名）；</li>
 *   <li>{@link #materials} —— 66 个材质槽（每面 2 层 × 5 区域 + 1 份窗），键名见
 *       {@link LayeredBoardSlots#allMaterialKeys()}。</li>
 * </ul>
 *
 * <p>伪装材质那套完全沿用 {@code CopycatGuardrailBlockEntity}：按部件名独立付账、稀疏 NBT
 * （空槽整条不写）、{@code RenderData} 快照、{@code SmartBlockEntity} 的同步路径。
 * 唯一的差别是付账规则里加了一条<b>引用计数</b>——同一个方块位置上同一种材质只在第一次
 * 使用时消耗一个物品，最后一个引用被移除时才返还。
 */
public class LayeredCopycatBoardBlockEntity extends SmartBlockEntity implements RenderDataBlockEntity {

    private static final String KEY_OCCUPANCY = "occupancy";
    private static final String KEY_WINDOWS = "windows";
    private static final String KEY_JUNCTION_OWNERS = "junction_owners";
    private static final String KEY_MATERIAL_DATA = "material_data";
    private static final String KEY_MATERIAL = "material";
    private static final String KEY_PAID_MATERIALS = "paid_materials";

    /** 没有材质。 */
    public static final BlockState NO_MATERIAL = Blocks.AIR.defaultBlockState();

    /** 12 个槽位的存在与否。 */
    private int occupancy;
    /** 6 个面的窗开关。 */
    private int windows;
    /**
     * 细工凿在物理交汇点上选定的显示材质槽。
     *
     * <p>键是 Junction 的稳定 identity（候选槽名的有序集合），值是选中的材质槽名。
     * <b>不存「候选列表第几个」</b>——occupancy 一变候选列表就会变，下标会指到另一条边上。
     * 存槽名之后，如果那条边已经因为拆结构不存在了，查不到就自动回退默认 owner。
     *
     * <p>一个方块上通常只有几个交汇点，所以这份状态很小。
     */
    private final Map<String, String> junctionOwners = new ConcurrentHashMap<>();

    /** 渲染线程会读，所以用并发映射；键集合固定为 66 个。 */
    private final Map<String, BlockState> materials = new ConcurrentHashMap<>();

    /**
     * 这个方块位置上<b>已经付过账</b>的伪装方块种类 → 被消耗的那一份物品。
     *
     * <p>规则是「同一个 BlockPos 上同一种方块只在<b>第一次</b>使用时消耗一个，
     * <b>最后一个引用</b>被移除时才返还一个」，所以付账记录按<b>方块种类</b>记，
     * 而不是按材质槽记。按槽记会有一个致命问题：第一份记录所在的那个槽一旦被拆掉，
     * 记录就没了，剩下那些「因为已经付过账所以没记账」的同材质槽就再也还不出东西。
     */
    private final Map<Item, ItemStack> paidMaterials = new ConcurrentHashMap<>();

    /**
     * 当前占用掩码对应的选取箱。
     *
     * <p>按掩码缓存：形状只跟占用有关（窗是填充不是洞），而占用变化的频率远低于渲染读取，
     * 所以每次读取都重拼一遍是浪费。掩码一变就置空，下次读取时重建。
     */
    private @Nullable VoxelShape cachedShape;
    private int cachedShapeMask = -1;

    public LayeredCopycatBoardBlockEntity( BlockPos pos, BlockState state ) {
        this( ModBlockEntity.LAYERED_COPYCAT_BOARD, pos, state );
    }

    protected LayeredCopycatBoardBlockEntity( BlockEntityType<?> type, BlockPos pos, BlockState state ) {
        super( type, pos, state );
        for ( String key : LayeredBoardSlots.allMaterialKeys() ) {
            materials.put( key, NO_MATERIAL );
        }
    }

    @Override
    public void addBehaviours( List<BlockEntityBehaviour> behaviours ) {
        // 不需要任何 Create 行为组件
    }

    // ---------------------------------------------------------------- 槽位

    public int occupancy( ) {
        return occupancy;
    }

    public boolean hasSlot( FaceDir face, BoardLayer layer ) {
        return LayeredBoardSlots.hasSlot( occupancy, face, layer );
    }

    public int slotCount( ) {
        return Integer.bitCount( occupancy );
    }

    /** 占用状态变了：形状缓存作废、快照作废、同步。 */
    public void setOccupancy( int value ) {
        int masked = sanitizeOccupancy( value & LayeredBoardSlots.FULL_OCCUPANCY );
        if ( masked == occupancy ) {
            return;
        }
        occupancy = masked;
        cachedShape = null;
        // 结构变了：候选里已经有一块板不在场的 override 再也匹配不上，顺手清掉，不留下幽灵材质。
        // 即使不清，解析时也会自动回退默认 owner——清理只是不让这份状态无限长大。
        for ( String stale : LayeredBoardParts.staleJunctionOwners( junctionOwners, masked ) ) {
            junctionOwners.remove( stale );
        }
        sync();
    }

    /** Lets embedded single-layer users constrain the shared occupancy model. */
    protected int sanitizeOccupancy( int value ) {
        return value;
    }

    public void addSlot( FaceDir face, BoardLayer layer ) {
        setOccupancy( LayeredBoardSlots.withSlot( occupancy, face, layer ) );
    }

    public void removeSlot( FaceDir face, BoardLayer layer ) {
        setOccupancy( LayeredBoardSlots.withoutSlot( occupancy, face, layer ) );
    }

    /** 按占用掩码缓存的选取箱。 */
    public VoxelShape shape( ) {
        if ( cachedShape == null || cachedShapeMask != occupancy ) {
            cachedShape = LayeredBoardParts.shape( occupancy );
            cachedShapeMask = occupancy;
        }
        return cachedShape;
    }

    // ---------------------------------------------------------------- 窗

    public int windows( ) {
        return windows;
    }

    public boolean hasWindow( FaceDir face ) {
        return LayeredBoardSlots.hasWindow( windows, face );
    }

    /**
     * 切换一个面的窗。
     *
     * <p>只改「这块面开不开窗」，窗材质、被消耗物品记录、角的归属<b>一概不动</b>——
     * 关掉再开，之前贴好的窗材质原样回来。
     */
    public boolean toggleWindow( FaceDir face ) {
        int next = LayeredBoardSlots.toggleWindow( windows, face );
        if ( next == windows ) {
            return false;
        }
        windows = next;
        sync();
        return true;
    }

    /** 把所有面的窗恢复成「关」，不改材质、不返还、不动角归属。 */
    public boolean clearWindows( ) {
        if ( windows == 0 ) {
            return false;
        }
        windows = 0;
        sync();
        return true;
    }

    // ---------------------------------------------------------------- 交汇点归属

    /** Junction identity → 玩家选中的材质槽名。渲染与命中都读这一份。 */
    public Map<String, String> junctionOwners( ) {
        return junctionOwners;
    }

    /**
     * 把某个交汇点当前显示的材质槽换成 {@code slot}（细工凿循环时调）。
     */
    public void setJunctionOwner( String junctionKey, String slot ) {
        String previous = junctionOwners.put( junctionKey, slot );
        if ( slot.equals( previous ) ) {
            return;
        }
        sync();
    }

    // ---------------------------------------------------------------- 材质读写

    public BlockState material( String key ) {
        return materials.getOrDefault( key, NO_MATERIAL );
    }

    public boolean hasMaterial( String key ) {
        return !material( key ).isAir();
    }

    public boolean hasAnyMaterial( ) {
        return materials.values().stream().anyMatch( state -> !state.isAir() );
    }

    /**
     * 这个方块位置上是否已经为「同一种方块」付过账——按方块种类判断，不看方块状态。
     *
     * <p>同一 BlockPos 同一种材质只在第一次使用时消耗一个物品：第二处无论朝向是否一致都不再扣。
     */
    public boolean hasPaidFor( BlockState material ) {
        return paidMaterials.containsKey( material.getBlock( ).asItem( ) );
    }

    /** 已经付过账的全部方块种类（整块破坏时按这个掉，同一材质只掉一份）。 */
    public Map<Item, ItemStack> paidMaterials( ) {
        return paidMaterials;
    }

    /**
     * 给某个材质槽附着伪装材质。
     *
     * <p>沿用 Create 的 {@code CopycatBlockEntity#setMaterial} 与 Create: Copycats+ 的
     * {@code setMaterial(property, state)} 里的「邻居继承」：如果紧挨着的同类伪装方块在同一个
     * 材质槽上已经是同一种方块，就直接沿用邻居的完整状态，这样一排薄板的贴图朝向自动一致。
     *
     * @param consumed 真正被消耗掉的那个物品堆；为 {@code null} 或空表示这次不用付账
     */
    public void setMaterial( String key, BlockState material, @Nullable ItemStack consumed ) {
        BlockState applied = inheritFromNeighbour( key, material );
        materials.put( key, applied );
        if ( consumed != null && !consumed.isEmpty() ) {
            // putIfAbsent：同一种方块只记第一份，之后复用同一材质的槽位不再记账
            paidMaterials.putIfAbsent( applied.getBlock().asItem(), consumed.copyWithCount( 1 ) );
        }
        sync();
    }

    /**
     * 邻居继承。判定条件与护栏 / 上游一致：邻居必须与本方块<b>状态完全相同</b>（同一个
     * BlockState 实例，即方块与全部属性都一致），且它在同一个材质槽上已经是同一种方块。
     */
    private BlockState inheritFromNeighbour( String key, BlockState material ) {
        if ( level == null || material( key ).is( material.getBlock() ) ) {
            return material;
        }
        BlockState ownState = getBlockState();
        for ( Direction side : Direction.values() ) {
            BlockPos neighbour = worldPosition.relative( side );
            if ( level.getBlockState( neighbour ) != ownState ) {
                continue;
            }
            if ( !( level.getBlockEntity( neighbour ) instanceof LayeredCopycatBoardBlockEntity other ) ) {
                continue;
            }
            BlockState otherMaterial = other.material( key );
            if ( !otherMaterial.is( material.getBlock() ) ) {
                continue;
            }
            return otherMaterial;
        }
        return material;
    }

    /**
     * 拆掉某个材质槽，并决定要不要返还那一份被消耗的物品。
     *
     * <p>规则是「同一个 BlockPos 上同一种方块只在第一次使用时消耗一个，<b>最后一个引用</b>被移除时
     * 才返还一个」。所以这里先看<b>还有没有别的槽引用着同一种方块</b>：还有就把记录留着、
     * 这次什么都不还；没有别的引用了才把那一份还回去。
     */
    public ItemStack takeConsumedItemForRemoval( String key ) {
        BlockState removed = material( key );
        materials.put( key, NO_MATERIAL );
        if ( removed.isAir( ) ) {
            sync();
            return ItemStack.EMPTY;
        }
        for ( BlockState other : materials.values() ) {
            if ( !other.isAir() && other.is( removed.getBlock() ) ) {
                // 还有别的槽在用同一种方块，记录留着
                sync();
                return ItemStack.EMPTY;
            }
        }
        ItemStack paid = paidMaterials.remove( removed.getBlock().asItem() );
        sync();
        return paid == null ? ItemStack.EMPTY : paid;
    }

    /**
     * 旋转某个材质槽的伪装贴图朝向。
     *
     * <p>照搬 Create 的 {@code CopycatBlockEntity#cycleMaterial()} 与 Create: Copycats+ 的
     * {@code IMultiStateCopycatBlockEntity#cycleMaterial(property)}，级联顺序和特例都一致。
     */
    public boolean cycleMaterial( String key ) {
        BlockState material = material( key );

        if ( material.hasProperty( BlockStateProperties.HALF ) && material.getOptionalValue( BlockStateProperties.OPEN ).orElse( false ) ) {
            setMaterialState( key, material.cycle( BlockStateProperties.HALF ) );
        } else if ( material.hasProperty( BlockStateProperties.FACING ) ) {
            setMaterialState( key, material.cycle( BlockStateProperties.FACING ) );
        } else if ( material.hasProperty( BlockStateProperties.HORIZONTAL_FACING ) ) {
            // 不能用 cycle()：Direction 的枚举顺序对水平朝向会转出「北→南」这种跳法
            setMaterialState( key, material.setValue( BlockStateProperties.HORIZONTAL_FACING,
                    material.getValue( BlockStateProperties.HORIZONTAL_FACING ).getClockWise() ) );
        } else if ( material.hasProperty( BlockStateProperties.AXIS ) ) {
            setMaterialState( key, material.cycle( BlockStateProperties.AXIS ) );
        } else if ( material.hasProperty( BlockStateProperties.HORIZONTAL_AXIS ) ) {
            setMaterialState( key, material.cycle( BlockStateProperties.HORIZONTAL_AXIS ) );
        } else if ( material.hasProperty( BlockStateProperties.LIT ) ) {
            setMaterialState( key, material.cycle( BlockStateProperties.LIT ) );
        } else if ( material.hasProperty( RoseQuartzLampBlock.POWERING ) ) {
            setMaterialState( key, material.cycle( RoseQuartzLampBlock.POWERING ) );
        } else {
            return false;
        }
        return true;
    }

    /** 只替换材质本身，保留该槽位「付过账」的记录（旋转不该改变付账状态）。 */
    private void setMaterialState( String key, BlockState material ) {
        materials.put( key, material );
        sync();
    }

    /** 创造模式破坏时用：清掉所有付账记录，这样什么都不会掉。 */
    public void clearConsumedItems( ) {
        paidMaterials.clear();
        setChanged();
    }

    /**
     * 整块被破坏时把已付账的材质掉出来。
     *
     * <p>板材本体由方块自己按占用掩码掉落（见 {@code LayeredCopycatBoardBlock#onStateReplaced}），
     * 这里只负责伪装材质。付账记录按方块种类记，所以同一材质天然只会掉一份。
     */
    public void dropAllMaterials( Level world, BlockPos pos ) {
        for ( ItemStack stack : paidMaterials.values() ) {
            if ( !stack.isEmpty() ) {
                Block.popResource( world, pos, stack.copy() );
            }
        }
        paidMaterials.clear();
    }

    // ---------------------------------------------------------------- 同步

    private void sync( ) {
        // 走到这里就说明占用 / 窗 / 角归属 / 材质刚变过，快照作废。
        // 放在最前面：world 为空时下面会提前 return，但失效一样不能漏。
        invalidateRenderData();
        setChanged();
        if ( level == null ) {
            return;
        }
        if ( !level.isClientSide ) {
            notifyUpdate();
            return;
        }
        // 客户端：本地改动（右键切换窗、细工凿改角归属等动作两端都会执行）必须自己重烘焙。
        redraw();
    }

    /**
     * 让本方块所在的区块重新烘焙。
     *
     * <p>与护栏同样的理由：客户端渲染时读的是 {@code getRenderData()}，而区块网格是烘焙后
     * 缓存起来的，只把新数据同步过来并不会让画面更新。
     */
    private void redraw( ) {
        if ( level == null || !level.isClientSide ) {
            return;
        }
        level.sendBlockUpdated( worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS );
        level.getLightEngine().checkBlock( worldPosition );
    }

    // ---------------------------------------------------------------- 持久化

    @Override
    protected void write( CompoundTag nbt, boolean clientPacket ) {
        super.write( nbt, clientPacket );
        nbt.putInt( KEY_OCCUPANCY, occupancy );
        nbt.putInt( KEY_WINDOWS, windows );

        CompoundTag junctions = new CompoundTag();
        junctionOwners.forEach( junctions::putString );
        nbt.put( KEY_JUNCTION_OWNERS, junctions );

        CompoundTag data = new CompoundTag();
        for ( String key : LayeredBoardSlots.allMaterialKeys() ) {
            BlockState material = material( key );
            // 空槽整条跳过：66 个固定槽位里通常只用了其中几个，全写进去大半是「空气」。
            // 读取端按「键缺失 = 空槽」处理，所以可以放心省。
            if ( material.isAir() ) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.put( KEY_MATERIAL, NbtUtils.writeBlockState( material ) );
            data.put( key, entry );
        }
        nbt.put( KEY_MATERIAL_DATA, data );

        // 付账记录按方块种类记（每种一份），只在服务端保存——客户端不需要知道谁付过账。
        if ( !clientPacket ) {
            CompoundTag paid = new CompoundTag();
            paidMaterials.forEach( ( item, stack ) -> paid.put(
                    BuiltInRegistries.ITEM.getKey( item ).toString(), stack.save( new CompoundTag() ) ) );
            nbt.put( KEY_PAID_MATERIALS, paid );
        }
    }

    @Override
    protected void read( CompoundTag nbt, boolean clientPacket ) {
        int previousOccupancy = occupancy;
        int previousWindows = windows;
        Map<String, String> previousJunctions = Map.copyOf( junctionOwners );
        Map<String, BlockState> previousMaterials = Map.copyOf( materials );

        super.read( nbt, clientPacket );
        occupancy = sanitizeOccupancy( nbt.getInt( KEY_OCCUPANCY ) & LayeredBoardSlots.FULL_OCCUPANCY );
        windows = nbt.getInt( KEY_WINDOWS ) & ( ( 1 << FaceDir.values().length ) - 1 );
        junctionOwners.clear();
        CompoundTag junctions = nbt.getCompound( KEY_JUNCTION_OWNERS );
        for ( String key : junctions.getAllKeys() ) {
            junctionOwners.put( key, junctions.getString( key ) );
        }
        cachedShape = null;

        CompoundTag data = nbt.getCompound( KEY_MATERIAL_DATA );
        for ( String key : LayeredBoardSlots.allMaterialKeys() ) {
            CompoundTag entry = data.getCompound( key );
            materials.put( key, readMaterial( entry, KEY_MATERIAL ) );
        }

        paidMaterials.clear();
        if ( !clientPacket ) {
            CompoundTag paid = nbt.getCompound( KEY_PAID_MATERIALS );
            for ( String id : paid.getAllKeys() ) {
                ResourceLocation identifier = ResourceLocation.tryParse( id );
                if ( identifier == null ) {
                    continue;
                }
                paidMaterials.put( BuiltInRegistries.ITEM.get( identifier ), ItemStack.of( paid.getCompound( id ) ) );
            }
        }

        // read 不走 sync()，所以这里必须自己作废快照，否则收包之后画面还停在旧数据上
        invalidateRenderData();
        boolean changed = previousOccupancy != occupancy
                || previousWindows != windows
                || !previousJunctions.equals( junctionOwners )
                || !previousMaterials.equals( materials );
        if ( clientPacket && changed ) {
            redraw();
        }
    }

    private static BlockState readMaterial( CompoundTag nbt, String key ) {
        if ( !nbt.contains( key ) ) {
            return NO_MATERIAL;
        }
        BlockState state = NbtUtils.readBlockState( BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound( key ) );
        return state == null ? NO_MATERIAL : state;
    }

    private static ItemStack readStack( CompoundTag nbt, String key ) {
        if ( !nbt.contains( key ) ) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of( nbt.getCompound( key ) );
    }


    // ---------------------------------------------------------------- 渲染数据

    /**
     * 交给渲染层的快照。
     *
     * <p>几何由 {@code occupancy / windows} 决定，交汇点由 {@code junctionOwners} 决定显示哪条边，
     * 材质再决定每个面的贴图，所以这三份状态必须一起给。
     */
    public record RenderData( int occupancy, int windows, Map<String, String> junctionOwners,
                              Map<String, BlockState> materials ) {
    }

    /** {@code volatile}：区块网格可以并行烘焙，快照会被渲染线程读，写入发生在交互 / sync 线程。 */
    private volatile RenderData renderData;

    private void invalidateRenderData( ) {
        renderData = null;
    }

    @Override
    public @Nullable Object getRenderData( ) {
        RenderData cached = renderData;
        if ( cached != null ) {
            return cached;
        }
        // 并发烘焙时可能有两个线程同时错过缓存、各建一份，内容相同，最后写入的胜出，
        // 不会读到半成品（Map.copyOf 出来的映射不可变，volatile 保证安全发布）。
        RenderData built = new RenderData( occupancy, windows, Map.copyOf( junctionOwners ),
                Map.copyOf( materials ) );
        renderData = built;
        return built;
    }

    /** 供未同步时兜底：渲染层拿不到快照就按空板处理。 */
    public static RenderData emptyRenderData( ) {
        return new RenderData( 0, 0, Map.of(), Map.of() );
    }
}
