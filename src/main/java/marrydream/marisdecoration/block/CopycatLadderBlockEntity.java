package marrydream.marisdecoration.block;

import com.simibubi.create.content.redstone.RoseQuartzLampBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import marrydream.marisdecoration.block.utils.CopycatLadderParts;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModBlockEntity;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CopycatLadderBlockEntity extends SmartBlockEntity implements RenderDataBlockEntity {
    private static final String MATERIAL_DATA = "material_data";
    private static final String MATERIAL = "material";
    private static final String CONSUMED = "consumedItem";
    public static final BlockState NO_MATERIAL = Blocks.AIR.getDefaultState();

    private final Map<String, BlockState> materials = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> consumedItems = new ConcurrentHashMap<>();
    private volatile RenderData renderData;

    public CopycatLadderBlockEntity(BlockPos pos, BlockState state) {
        super(state.isOf(ModBlock.COPYCAT_STEEL_FIXED_LADDER)
                ? ModBlockEntity.COPYCAT_STEEL_FIXED_LADDER
                : ModBlockEntity.COPYCAT_STEEL_VERTICAL_LADDER, pos, state);
        for (String slot : slots()) {
            materials.put(slot, NO_MATERIAL);
            consumedItems.put(slot, ItemStack.EMPTY);
        }
    }

    private List<String> slots() {
        return getCachedState().isOf(ModBlock.COPYCAT_STEEL_FIXED_LADDER)
                ? CopycatLadderParts.fixedSlots() : CopycatLadderParts.verticalSlots();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public BlockState material(String slot) {
        return materials.getOrDefault(slot, NO_MATERIAL);
    }

    public boolean hasMaterial(String slot) {
        return !material(slot).isAir();
    }

    public boolean hasMaterialBlock(Block block) {
        return materials.values().stream().anyMatch(state -> !state.isAir() && state.isOf(block));
    }

    public void setMaterial(String slot, BlockState material, @Nullable ItemStack consumed) {
        materials.put(slot, material);
        if (consumed != null && !consumed.isEmpty()) {
            consumedItems.put(slot, consumed.copyWithCount(1));
        }
        syncData();
    }

    public boolean cycleMaterial(String slot) {
        BlockState material = material(slot);
        BlockState next;
        if (material.contains(Properties.BLOCK_HALF) && material.getOrEmpty(Properties.OPEN).orElse(false)) {
            next = material.cycle(Properties.BLOCK_HALF);
        } else if (material.contains(Properties.FACING)) {
            next = material.cycle(Properties.FACING);
        } else if (material.contains(Properties.HORIZONTAL_FACING)) {
            next = material.with(Properties.HORIZONTAL_FACING,
                    material.get(Properties.HORIZONTAL_FACING).rotateYClockwise());
        } else if (material.contains(Properties.AXIS)) {
            next = material.cycle(Properties.AXIS);
        } else if (material.contains(Properties.HORIZONTAL_AXIS)) {
            next = material.cycle(Properties.HORIZONTAL_AXIS);
        } else if (material.contains(Properties.LIT)) {
            next = material.cycle(Properties.LIT);
        } else if (material.contains(RoseQuartzLampBlock.POWERING)) {
            next = material.cycle(RoseQuartzLampBlock.POWERING);
        } else {
            return false;
        }
        materials.put(slot, next);
        syncData();
        return true;
    }

    public ItemStack takeConsumedItemForRemoval(String slot) {
        ItemStack returned = consumedItems.getOrDefault(slot, ItemStack.EMPTY);
        BlockState removed = material(slot);
        materials.put(slot, NO_MATERIAL);
        consumedItems.put(slot, ItemStack.EMPTY);
        if (!returned.isEmpty() && !removed.isAir()) {
            for (String other : slots()) {
                if (!other.equals(slot) && material(other).isOf(removed.getBlock())) {
                    if (consumedItems.getOrDefault(other, ItemStack.EMPTY).isEmpty()) {
                        consumedItems.put(other, returned);
                    }
                    returned = ItemStack.EMPTY;
                    break;
                }
            }
        }
        syncData();
        return returned;
    }

    public void clearConsumedItems() {
        for (String slot : slots()) consumedItems.put(slot, ItemStack.EMPTY);
        markDirty();
    }

    public void dropAllMaterials(World world, BlockPos pos) {
        for (String slot : slots()) {
            ItemStack stack = consumedItems.getOrDefault(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) Block.dropStack(world, pos, stack.copy());
        }
    }

    private void syncData() {
        renderData = null;
        markDirty();
        if (world == null) return;
        if (!world.isClient) notifyUpdate();
        else redraw();
    }

    private void redraw() {
        if (world != null && world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
            world.getLightingProvider().checkBlock(pos);
        }
    }

    @Override
    protected void write(NbtCompound nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);
        NbtCompound data = new NbtCompound();
        for (String slot : slots()) {
            BlockState material = material(slot);
            ItemStack consumed = consumedItems.getOrDefault(slot, ItemStack.EMPTY);
            if (material.isAir() && consumed.isEmpty()) continue;
            NbtCompound entry = new NbtCompound();
            entry.put(MATERIAL, NbtHelper.fromBlockState(material));
            if (!clientPacket) entry.put(CONSUMED, consumed.writeNbt(new NbtCompound()));
            data.put(slot, entry);
        }
        nbt.put(MATERIAL_DATA, data);
    }

    @Override
    protected void read(NbtCompound nbt, boolean clientPacket) {
        Map<String, BlockState> before = Map.copyOf(materials);
        super.read(nbt, clientPacket);
        NbtCompound data = nbt.getCompound(MATERIAL_DATA);
        for (String slot : slots()) {
            NbtCompound entry = data.getCompound(slot);
            materials.put(slot, entry.contains(MATERIAL)
                    ? NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), entry.getCompound(MATERIAL))
                    : NO_MATERIAL);
            if (!clientPacket) {
                consumedItems.put(slot, entry.contains(CONSUMED)
                        ? ItemStack.fromNbt(entry.getCompound(CONSUMED)) : ItemStack.EMPTY);
            }
        }
        renderData = null;
        if (clientPacket && !before.equals(materials)) redraw();
    }

    public record RenderData(Map<String, BlockState> materials) {
    }

    @Override
    public @Nullable Object getRenderData() {
        RenderData cached = renderData;
        if (cached != null) return cached;
        RenderData built = new RenderData(Map.copyOf(materials));
        renderData = built;
        return built;
    }
}
