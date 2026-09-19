package marrydream.marisdecoration;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModItem;
import marrydream.marisdecoration.init.ModItemGroup;
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
        ModItemGroup.init();
        LOGGER.info("Initialized Maris' Decoration");
    }
}
