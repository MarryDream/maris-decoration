package marrydream.marisdecoration.init;

import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity;
import marrydream.marisdecoration.block.CopycatLadderBlockEntity;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlockEntity;
import marrydream.marisdecoration.block.SteelPlugDoorBlockEntity;
import marrydream.marisdecoration.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 方块实体类型注册。
 *
 * <p>必须在 {@link ModBlock#init()} 之后调用，因为构建时要引用已注册的方块。
 */
public final class ModBlockEntity {

    public static final BlockEntityType<CopycatGuardrailBlockEntity> COPYCAT_GUARDRAIL = Platform.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ModInfo.id("copycat_guardrail"),
            BlockEntityType.Builder.of(CopycatGuardrailBlockEntity::new, ModBlock.COPYCAT_GUARDRAIL).build(null)
    );

    public static final BlockEntityType<LayeredCopycatBoardBlockEntity> LAYERED_COPYCAT_BOARD = Platform.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ModInfo.id("layered_copycat_board"),
            BlockEntityType.Builder.of(LayeredCopycatBoardBlockEntity::new, ModBlock.LAYERED_COPYCAT_BOARD).build(null)
    );

    public static final BlockEntityType<SteelPlugDoorBlockEntity> STEEL_PLUG_DOOR = Platform.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ModInfo.id("steel_plug_door"),
            BlockEntityType.Builder.of(SteelPlugDoorBlockEntity::new, ModBlock.STEEL_PLUG_DOOR).build(null)
    );

    public static final BlockEntityType<CopycatLadderBlockEntity> COPYCAT_STEEL_FIXED_LADDER = Platform.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, ModInfo.id("copycat_steel_fixed_ladder"),
            BlockEntityType.Builder.of(CopycatLadderBlockEntity::new,
                    ModBlock.COPYCAT_STEEL_FIXED_LADDER).build(null));

    public static final BlockEntityType<CopycatLadderBlockEntity> COPYCAT_STEEL_VERTICAL_LADDER = Platform.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, ModInfo.id("copycat_steel_vertical_ladder"),
            BlockEntityType.Builder.of(CopycatLadderBlockEntity::new,
                    ModBlock.COPYCAT_STEEL_VERTICAL_LADDER).build(null));

    private ModBlockEntity() {
    }

    public static void init() {
        // 触发类初始化完成注册
    }
}
