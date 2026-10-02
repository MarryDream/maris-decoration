package marrydream.marisdecoration.platform;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
public abstract class RenderDataBlockEntity extends SmartBlockEntity {
 public static final ModelProperty<Object> SNAPSHOT = new ModelProperty<>();
 protected RenderDataBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type,pos,state); }
 public abstract Object getRenderData();
 @Override public ModelData getModelData() { return ModelData.builder().with(SNAPSHOT, getRenderData()).build(); }
 protected void renderDataChanged() { if (level != null && level.isClientSide) requestModelDataUpdate(); }
}
