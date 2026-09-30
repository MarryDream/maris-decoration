package marrydream.marisdecoration.init;

import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity;
import marrydream.marisdecoration.block.CopycatLadderBlockEntity;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlockEntity;
import marrydream.marisdecoration.block.SteelPlugDoorBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * 方块实体类型注册。
 *
 * <p>必须在 {@link ModBlock#init()} 之后调用，因为构建时要引用已注册的方块。
 */
public final class ModBlockEntity {

    public static final BlockEntityType<CopycatGuardrailBlockEntity> COPYCAT_GUARDRAIL = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            ModInfo.id("copycat_guardrail"),
            FabricBlockEntityTypeBuilder.create(CopycatGuardrailBlockEntity::new, ModBlock.COPYCAT_GUARDRAIL).build()
    );

    public static final BlockEntityType<LayeredCopycatBoardBlockEntity> LAYERED_COPYCAT_BOARD = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            ModInfo.id("layered_copycat_board"),
            FabricBlockEntityTypeBuilder.create(LayeredCopycatBoardBlockEntity::new, ModBlock.LAYERED_COPYCAT_BOARD).build()
    );

    public static final BlockEntityType<SteelPlugDoorBlockEntity> STEEL_PLUG_DOOR = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            ModInfo.id("steel_plug_door"),
            FabricBlockEntityTypeBuilder.create(SteelPlugDoorBlockEntity::new, ModBlock.STEEL_PLUG_DOOR).build()
    );

    public static final BlockEntityType<CopycatLadderBlockEntity> COPYCAT_STEEL_FIXED_LADDER = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, ModInfo.id("copycat_steel_fixed_ladder"),
            FabricBlockEntityTypeBuilder.create(CopycatLadderBlockEntity::new,
                    ModBlock.COPYCAT_STEEL_FIXED_LADDER).build());

    public static final BlockEntityType<CopycatLadderBlockEntity> COPYCAT_STEEL_VERTICAL_LADDER = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, ModInfo.id("copycat_steel_vertical_ladder"),
            FabricBlockEntityTypeBuilder.create(CopycatLadderBlockEntity::new,
                    ModBlock.COPYCAT_STEEL_VERTICAL_LADDER).build());

    private ModBlockEntity() {
    }

    public static void init() {
        // 触发类初始化完成注册
    }
}
