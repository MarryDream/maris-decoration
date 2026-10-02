package marrydream.marisdecoration.client;

import marrydream.marisdecoration.block.*;
import marrydream.marisdecoration.block.utils.*;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModItem;
import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.PlacementConfigs;
import marrydream.marisdecoration.placement.client.PlacerClientBridge;
import marrydream.marisdecoration.platform.ClientPlatform;
import marrydream.marisdecoration.platform.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Opt-in world fixture verifies actual BE synchronization and chunk model data. */
public final class ForgeWorldCheck {
    private static boolean requested;
    private static int ticks;
    private static boolean packetSent;
    private static final PlacementConfig NETWORK_CONFIG = new PlacementConfig(
            ModBlock.COPYCAT_STEEL_FIXED_LADDER.defaultBlockState(), Map.of(),
            Map.of(CopycatLadderParts.MATERIAL, Blocks.STONE.defaultBlockState()));
    private static final List<BlockPos> POSITIONS=List.of(new BlockPos(-4,201,7),new BlockPos(-2,201,7),
            new BlockPos(0,201,7),new BlockPos(2,201,7),new BlockPos(4,202,7));
    public static void register() {
        ClientPlatform.onEndTick(client -> {
            if(client.level==null || client.player==null || client.getSingleplayerServer()==null) return;
            if(!requested) {
                requested=true;
                client.getSingleplayerServer().execute(()-> {
                    var level=client.getSingleplayerServer().overworld();
                    var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItem.COPYCAT_PLACER));
                    player.containerMenu.sendAllDataToRemote();
                    for(BlockPos pos:POSITIONS) {
                        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState());
                    }
                    var guard=ModBlock.COPYCAT_GUARDRAIL.defaultBlockState();
                    for(var property:CopycatGuardrailBlock.PROPERTY_BY_DIRECTION.values()) guard=guard.setValue(property,true);
                    level.setBlockAndUpdate(POSITIONS.get(0),guard);
                    var rail=(CopycatGuardrailBlockEntity)level.getBlockEntity(POSITIONS.get(0));
                    for(String key:GuardrailParts.allKeys()) rail.setMaterial(key,Blocks.OAK_LOG.defaultBlockState(),ItemStack.EMPTY);
                    level.setBlockAndUpdate(POSITIONS.get(1),ModBlock.LAYERED_COPYCAT_BOARD.defaultBlockState());
                    var board=(LayeredCopycatBoardBlockEntity)level.getBlockEntity(POSITIONS.get(1));
                    board.setOccupancy(4095);
                    for(String key:LayeredBoardSlots.allMaterialKeys()) board.setMaterial(key,Blocks.GLASS.defaultBlockState(),ItemStack.EMPTY);
                    level.setBlockAndUpdate(POSITIONS.get(2),ModBlock.COPYCAT_STEEL_FIXED_LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH));
                    ((CopycatLadderBlockEntity)level.getBlockEntity(POSITIONS.get(2))).setMaterial(CopycatLadderParts.MATERIAL,Blocks.OAK_LOG.defaultBlockState(),ItemStack.EMPTY);
                    level.setBlockAndUpdate(POSITIONS.get(3),ModBlock.COPYCAT_STEEL_VERTICAL_LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH));
                    var ladder=(CopycatLadderBlockEntity)level.getBlockEntity(POSITIONS.get(3));
                    ladder.setMaterial(CopycatLadderParts.SUPPORT,Blocks.STONE.defaultBlockState(),ItemStack.EMPTY);
                    ladder.setMaterial(CopycatLadderParts.RUNG,Blocks.OAK_LOG.defaultBlockState(),ItemStack.EMPTY);
                    var lower=ModBlock.STEEL_PLUG_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH);
                    level.setBlockAndUpdate(POSITIONS.get(4).below(),lower);
                    level.setBlockAndUpdate(POSITIONS.get(4),lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
                    ((SteelPlugDoorBlockEntity)level.getBlockEntity(POSITIONS.get(4))).insertRoof();
                });
            }
            ++ticks;
            if(!packetSent && ticks>=40 && client.player.getMainHandItem().is(ModItem.COPYCAT_PLACER)) {
                packetSent=true;
                // Exercise the same bridge, SimpleChannel and server validator as the GUI.
                // Do not optimistically write client NBT: observing it proves server echo.
                PlacerClientBridge.sendConfig(NETWORK_CONFIG);
            }
            if(ticks!=220) return;
            try {
                if(!packetSent || !PlacementConfigs.read(client.player.getMainHandItem()).toNbt().equals(NETWORK_CONFIG.toNbt()))
                    throw new AssertionError("Placer C2S config / inventory NBT echo failed");
                for(BlockPos pos:POSITIONS) {
                    if(!(client.level.getBlockEntity(pos) instanceof RenderDataBlockEntity entity)) throw new AssertionError("Missing client BE: "+pos);
                    var cached=client.level.getModelDataManager().getAt(pos);
                    if(cached==null || cached.get(RenderDataBlockEntity.SNAPSHOT)==null) throw new AssertionError("Missing chunk model data: "+pos);
                    Object snapshot=cached.get(RenderDataBlockEntity.SNAPSHOT);
                    if(snapshot instanceof LayeredCopycatBoardBlockEntity.RenderData d && d.occupancy()==0) throw new AssertionError("Occupancy sync failed: "+pos);
                }
                Files.writeString(Path.of("forge-world-check.txt"),"PASS: actual client BE synchronization and chunk ModelData for guardrail, board, both ladders and door roof; Placer C2S config and server inventory NBT echo.\n");
            } catch(Throwable error) {
                error.printStackTrace();
                try { Files.writeString(Path.of("forge-world-check.txt"),"FAIL: "+error); } catch(Exception ignored) {}
            }
        });
    }
}
