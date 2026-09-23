package marrydream.marisdecoration;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModBlockEntity;
import marrydream.marisdecoration.init.ModItem;
import marrydream.marisdecoration.init.ModItemGroup;
import marrydream.marisdecoration.placement.adapter.PlacementAdapters;
import marrydream.marisdecoration.placement.harness.CopycatPlacerCommand;
import marrydream.marisdecoration.placement.network.ServerPlacerConfigHandler;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static marrydream.marisdecoration.init.ModInfo.MOD_ID;

public class MarisDecoration implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger( MOD_ID );

    @Override
    public void onInitialize( ) {
        ModItem.init();
        ModBlock.init();
        // 必须在 ModBlock.init() 之后：方块实体类型要引用已注册的方块
        ModBlockEntity.init();
        ModItemGroup.init();
        // 伪装放置器：装 adapter 表。必须在方块注册之后——adapter 的判据要拿方块实例来比对。
        // 这一步不做任何世界交互，只是把「谁处理哪种方块」这件事定下来。
        PlacementAdapters.init();
        // C2S：接收客户端在配置界面里改好的预设，校验后写回物品 NBT
        ServerPlacerConfigHandler.register();
        // 只注册一个服务端调试命令；GUI 的其它部分在客户端源集
        CopycatPlacerCommand.register();
        LOGGER.info("Initialized Maris' Decoration");
    }
}
