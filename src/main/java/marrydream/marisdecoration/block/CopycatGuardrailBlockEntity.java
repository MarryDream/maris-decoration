package marrydream.marisdecoration.block;

import com.simibubi.create.content.redstone.RoseQuartzLampBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import marrydream.marisdecoration.block.utils.GuardrailParts;
import marrydream.marisdecoration.init.ModBlockEntity;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code copycat_guardrail} 的方块实体。
 *
 * <p>整块有 <b>8 个互相独立的伪装材质槽</b>——4 个方向 × 柱/横梁，键名形如
 * {@code north_column} / {@code north_row}。这与 Create: Copycats+ 的
 * {@code MaterialItemStorage} 是同一套思路：以部件名为键的映射，NBT 存成一个
 * {@code material_data} 复合标签，每个部件一个子标签，子标签里放材质和「被消耗的那个物品」。
 *
 * <p><b>为什么继承 Create 的 {@link SmartBlockEntity} 而不是原版 {@code BlockEntity}</b>：
 * 伪装材质必须同步到客户端才能渲染。原版方块实体的 NBT 默认不会随方块更新发给客户端，
 * 结果就是服务端改了材质、客户端依旧渲染成旧的样子。Create 的 {@code SyncedBlockEntity}
 * 提供了 {@code toInitialChunkDataNbt()} / {@code getUpdatePacket()} / {@code sendData()} /
 * {@code notifyUpdate()} 这一整套同步路径。
 *
 * <p>还要注意：{@code SmartBlockEntity} 把 {@code writeNbt}/{@code readNbt} 标记为 final，
 * 要覆写的是带 {@code clientPacket} 参数的 {@code write}/{@code read}。
 */
public class CopycatGuardrailBlockEntity extends SmartBlockEntity implements RenderDataBlockEntity {

    private static final String KEY_MATERIAL_DATA = "material_data";
    private static final String KEY_MATERIAL = "material";
    private static final String KEY_CONSUMED_ITEM = "consumedItem";

    /** 没有材质。 */
    public static final BlockState NO_MATERIAL = Blocks.AIR.getDefaultState();

    /** 渲染线程会读，所以用并发映射；键集合固定为 8 个。 */
    private final Map<String, BlockState> materials = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> consumedItems = new ConcurrentHashMap<>();

    public CopycatGuardrailBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntity.COPYCAT_GUARDRAIL, pos, state);
        for (String key : GuardrailParts.allKeys()) {
            materials.put(key, NO_MATERIAL);
            consumedItems.put(key, ItemStack.EMPTY);
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 不需要任何 Create 行为组件
    }

    // ---------------------------------------------------------------- 材质读写

    public BlockState material(String key) {
        return materials.getOrDefault(key, NO_MATERIAL);
    }

    public boolean hasMaterial(String key) {
        return !material(key).isAir();
    }

    public boolean hasAnyMaterial() {
        return materials.values().stream().anyMatch(state -> !state.isAir());
    }

    public ItemStack consumedItem(String key) {
        return consumedItems.getOrDefault(key, ItemStack.EMPTY);
    }

    /**
     * 是否已经有槽位为同种物品付过账。
     *
     * <p>沿用 Create 的规则：整块里只要有任何一个部件已经消耗过同种物品，之后再用同种物品
     * 伪装其它部件就不再扣物品。
     */
    public boolean alreadyPaidWith(ItemStack stack) {
        return consumedItems.values().stream()
                .anyMatch(paid -> !paid.isEmpty() && paid.getItem() == stack.getItem());
    }

    /**
     * 给某个槽位附着伪装材质；{@code consumed} 是被消耗的那个物品堆。
     *
     * <p>沿用 Create 的 {@code CopycatBlockEntity#setMaterial} 与 Create: Copycats+ 的
     * {@code setMaterial(property, state)} 里的「邻居继承」：如果紧挨着的同类伪装方块在同一个
     * 槽位上已经是同一种方块，就直接沿用邻居的完整状态。这样一排护栏摆在一起时材质朝向自动一致，
     * 不会出现相邻两块原木纹理方向互不相同的情况。
     */
    public void setMaterial(String key, BlockState material, ItemStack consumed) {
        BlockState applied = inheritFromNeighbour(key, material);
        materials.put(key, applied);
        if (!consumed.isEmpty()) {
            consumedItems.put(key, consumed.copyWithCount(1));
        }
        sync();
    }

    /**
     * 邻居继承。返回应当真正使用的材质状态。
     *
     * <p>判定条件与上游一致：邻居必须与本方块<b>状态完全相同</b>（同一个 BlockState 实例，
     * 即方块与全部属性都一致），且它在同一个槽位上已经是同一种方块，才沿用它的状态。
     */
    private BlockState inheritFromNeighbour(String key, BlockState material) {
        if (world == null || material(key).isOf(material.getBlock())) {
            return material;
        }
        BlockState ownState = getCachedState();
        for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.offset(side);
            if (world.getBlockState(neighbour) != ownState) {
                continue;
            }
            if (!(world.getBlockEntity(neighbour) instanceof CopycatGuardrailBlockEntity other)) {
                continue;
            }
            BlockState otherMaterial = other.material(key);
            if (!otherMaterial.isOf(material.getBlock())) {
                continue;
            }
            return otherMaterial;
        }
        return material;
    }

    /**
     * 拆掉某个槽位时决定实际要返还的物品。
     *
     * <p>沿用 Create: Copycats+ 的做法：整块里同一种材质只会在「第一个」槽位上留下被消耗物品的
     * 记录（后续槽位因为已经付过账，记录为空）。所以拆一个槽位时，先把这条记录<b>迁移</b>到另一个
     * 材质相同、记录为空的槽位上——迁移成功就说明这块里还有同材质的部件，本次不返还；
     * 只有拆掉最后一个同材质槽位、记录无处可迁时，才真的把物品还回去。
     */
    public ItemStack takeConsumedItemForRemoval(String key) {
        ItemStack returned = consumedItem(key);
        if (!returned.isEmpty()) {
            Block block = material(key).getBlock();
            for (String other : GuardrailParts.allKeys()) {
                if (other.equals(key)) {
                    continue;
                }
                if (material(other).getBlock() != block || !consumedItem(other).isEmpty()) {
                    continue;
                }
                consumedItems.put(other, returned);
                returned = ItemStack.EMPTY;
                break;
            }
        }
        materials.put(key, NO_MATERIAL);
        consumedItems.put(key, ItemStack.EMPTY);
        sync();
        return returned;
    }

    /**
     * 旋转某个槽位的伪装贴图朝向。
     *
     * <p>照搬 Create 的 {@code CopycatBlockEntity#cycleMaterial()} 与 Create: Copycats+ 的
     * {@code IMultiStateCopycatBlockEntity#cycleMaterial(property)}，级联顺序和特例都一致。
     * 返回 false 表示这个材质没有任何可旋转的属性，调用方应当把操作交还给原版。
     */
    public boolean cycleMaterial(String key) {
        BlockState material = material(key);

        if (material.contains(Properties.BLOCK_HALF) && material.getOrEmpty(Properties.OPEN).orElse(false)) {
            setMaterialState(key, material.cycle(Properties.BLOCK_HALF));
        } else if (material.contains(Properties.FACING)) {
            setMaterialState(key, material.cycle(Properties.FACING));
        } else if (material.contains(Properties.HORIZONTAL_FACING)) {
            // 这里不能用 cycle()：Direction 的枚举顺序是 下/上/北/南/西/东，
            // 对水平朝向会转出「北→南」这种跳法。显式取顺时针才是 北→东→南→西。
            setMaterialState(key,
                    material.with(Properties.HORIZONTAL_FACING,
                            material.get(Properties.HORIZONTAL_FACING).rotateYClockwise()));
        } else if (material.contains(Properties.AXIS)) {
            setMaterialState(key, material.cycle(Properties.AXIS));
        } else if (material.contains(Properties.HORIZONTAL_AXIS)) {
            setMaterialState(key, material.cycle(Properties.HORIZONTAL_AXIS));
        } else if (material.contains(Properties.LIT)) {
            setMaterialState(key, material.cycle(Properties.LIT));
        } else if (material.contains(RoseQuartzLampBlock.POWERING)) {
            setMaterialState(key, material.cycle(RoseQuartzLampBlock.POWERING));
        } else {
            return false;
        }
        return true;
    }

    /** 只替换材质本身，保留该槽位记录的被消耗物品（旋转不该改变「付过账」的记录）。 */
    private void setMaterialState(String key, BlockState material) {
        materials.put(key, material);
        sync();
    }

    /** 创造模式破坏时用：清掉所有「被消耗物品」记录，这样什么都不会掉。 */
    public void clearConsumedItems() {
        for (String key : GuardrailParts.allKeys()) {
            consumedItems.put(key, ItemStack.EMPTY);
        }
        markDirty();
    }

    /** 整块被破坏时把所有已消耗的物品掉出来。 */
    public void dropAllMaterials(World world, BlockPos pos) {
        for (String key : GuardrailParts.allKeys()) {
            ItemStack stack = consumedItem(key);
            if (!stack.isEmpty()) {
                Block.dropStack(world, pos, stack.copy());
            }
        }
    }

    /** 扳手拆除时把已消耗的物品直接塞进玩家背包（Create 的原生行为）。 */
    public void giveMaterialsTo(PlayerEntity player) {
        for (String key : GuardrailParts.allKeys()) {
            ItemStack stack = consumedItem(key);
            if (!stack.isEmpty()) {
                player.getInventory().offerOrDrop(stack.copy());
            }
        }
    }

    private void sync() {
        markDirty();
        if (world == null) {
            return;
        }
        if (!world.isClient) {
            // Create 的同步入口：把方块实体数据推给客户端
            notifyUpdate();
            return;
        }
        // 客户端：本地改动（例如右键旋转材质，这个动作两端都会执行）必须自己重烘焙。
        // 只等同步包是不行的——客户端的映射已经先被本地改动更新过，read() 里的变化检测
        // 会因此判定「没变化」而跳过重绘，表现为「材质变了但画面不动，直到方块状态变化」。
        redraw();
    }

    /**
     * 让本方块所在的区块重新烘焙。
     *
     * <p>客户端渲染时才去读 {@code getRenderData()}，而区块网格是烘焙后缓存起来的。
     * 只把新材质同步过来、不主动让区块重烘焙的话，画面要等到下一次方块状态变化才会更新。
     * {@code ClientWorld.updateListeners} 内部就是 {@code worldRenderer.updateBlock(...)}，
     * 本身会触发重绘，所以用通用 API 就够了，不需要碰客户端类。
     */
    private void redraw() {
        if (world == null || !world.isClient) {
            return;
        }
        world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        world.getLightingProvider().checkBlock(pos);
    }

    // ---------------------------------------------------------------- 持久化

    @Override
    protected void write(NbtCompound nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);
        NbtCompound data = new NbtCompound();
        for (String key : GuardrailParts.allKeys()) {
            NbtCompound entry = new NbtCompound();
            entry.put(KEY_MATERIAL, NbtHelper.fromBlockState(material(key)));
            if (!clientPacket) {
                // 被消耗的物品只在服务端保存，没必要发给客户端
                entry.put(KEY_CONSUMED_ITEM, consumedItem(key).writeNbt(new NbtCompound()));
            }
            data.put(key, entry);
        }
        nbt.put(KEY_MATERIAL_DATA, data);
    }

    @Override
    protected void read(NbtCompound nbt, boolean clientPacket) {
        Map<String, BlockState> previous = Map.copyOf(materials);
        super.read(nbt, clientPacket);
        NbtCompound data = nbt.getCompound(KEY_MATERIAL_DATA);
        for (String key : GuardrailParts.allKeys()) {
            NbtCompound entry = data.getCompound(key);
            materials.put(key, readMaterial(entry, KEY_MATERIAL));
            if (!clientPacket) {
                consumedItems.put(key, readStack(entry, KEY_CONSUMED_ITEM));
            }
        }
        // 客户端拿到新材质后必须主动重烘焙，否则要等下次方块状态变化才刷新外观
        if (clientPacket && !previous.equals(materials)) {
            redraw();
        }
    }

    private static BlockState readMaterial(NbtCompound nbt, String key) {
        if (!nbt.contains(key)) {
            return NO_MATERIAL;
        }
        BlockState state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt.getCompound(key));
        return state == null ? NO_MATERIAL : state;
    }

    private static ItemStack readStack(NbtCompound nbt, String key) {
        if (!nbt.contains(key)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.fromNbt(nbt.getCompound(key));
    }

    // ---------------------------------------------------------------- 渲染数据

    /**
     * 交给渲染层的部件→材质映射。键名与 {@link GuardrailParts#key} 一致，
     * 生成脚本按同样的键名给每个几何体分配了占位贴图。
     */
    @Override
    public @Nullable Object getRenderData() {
        return materials;
    }
}
