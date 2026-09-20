package marrydream.marisdecoration.init;

import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity;
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

    private ModBlockEntity() {
    }

    public static void init() {
        // 触发类初始化完成注册
    }
}
