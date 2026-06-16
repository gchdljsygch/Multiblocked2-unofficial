package com.lowdragmc.mbd2.common;

import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.MultiblockWorldSavedData;
import com.lowdragmc.mbd2.common.item.MBDGadgetsItem;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-bus server/common event hooks for commands, multiblock interaction, and
 * world shutdown cleanup.
 *
 * <p>The business goal is to connect player actions and server lifecycle events
 * to MBD's runtime multiblock state. Handlers are invoked by Forge on the
 * appropriate game/server thread and may mutate interaction results, world
 * blocks, saved multiblock mappings, and async executor state.</p>
 */
@Mod.EventBusSubscriber(modid = MBD2.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEventListener {

    /**
     * Registers server-side MBD commands with Brigadier.
     *
     * <p>Preconditions: called by Forge while command dispatchers are being
     * built. Side effects: adds every command returned by
     * {@link ServerCommands#createServerCommands()} to the active dispatcher.</p>
     *
     * @param event command registration event
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ServerCommands.createServerCommands().forEach(event.getDispatcher()::register);
    }

    /**
     * Handles right-clicks that should open multiblock UIs or transform catalyst
     * candidate blocks into controllers.
     *
     * <p>Business goal: allow players to interact with the visible structure
     * instead of only the controller, and allow catalyst items to form a
     * multiblock from a valid controller candidate. Preconditions: called by
     * Forge for a block right-click. Side effects may deny normal block use,
     * open a machine UI, replace the clicked block with a controller, invoke
     * catalyst callbacks, update saved multiblock mappings, or roll back the
     * block when formation fails.</p>
     *
     * @param event right-click interaction event; may be canceled or have use
     *              results changed
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (MachineInteractionHelper.shouldBypassMachineUI(event.getItemStack())) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.ALLOW);
            return;
        }
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            var pos = event.getPos();
            if (!event.getEntity().isCrouching() &&
                    !(event.getEntity().getItemInHand(event.getHand()).getItem() instanceof MBDGadgetsItem)) {
                // on multiblock ui click
                for (var state : MultiblockWorldSavedData.getOrCreate(serverLevel).getControllerInPos(pos)) {
                    LongSet openUIMask = state.getMatchContext().getOrDefault("openUIMask", LongSets.EMPTY_SET);
                    if (state.getController() instanceof MBDMultiblockMachine machine) {
                        if (machine.getDefinition().machineSettings().hasUI() &&
                                machine.getDefinition().multiblockSettings().showUIWhenClickStructure() &&
                                openUIMask.contains(pos.asLong())) {
                            machine.openUI(event.getEntity());
                            event.setUseBlock(Event.Result.ALLOW);
                            event.setUseItem(Event.Result.DENY);
                            // event.setCanceled(true);
                            return;
                        }
                    }
                }
            }
            // on multiblock catalyst candidates
            var originalState = serverLevel.getBlockState(pos);
            var hitBlock = originalState.getBlock();
            if (MultiblockMachineDefinition.CATALYST_CANDIDATES.containsKey(hitBlock)) {
                var held = event.getItemStack();
                var definitions = MultiblockMachineDefinition.CATALYST_CANDIDATES.get(hitBlock);
                for (var definition : definitions) {
                    if (definition.multiblockSettings().catalyst().test(held)) {
                        // check structure
                        var multiblockState = new MultiblockState(serverLevel, pos);
                        for (Direction facing : new Direction[]{Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST}) {
                            if (definition.blockPatternFactory().apply(null).checkPatternAtWithoutController(multiblockState, facing)) {
                                // can be formed, replace with the real controller
                                var controllerState = definition.block().defaultBlockState();
                                if (definition.blockProperties().rotationState().property.isPresent()) {
                                    controllerState = controllerState.setValue(definition.blockProperties().rotationState().property.get(), facing);
                                }
                                serverLevel.setBlockAndUpdate(pos, controllerState);
                                // notify formed
                                if (!IMultiController.ofController(serverLevel, pos).map(controller -> {
                                    if (controller instanceof MBDMultiblockMachine machine && machine.checkPatternWithLock()) {
                                        var success = machine.onCatalystUsed(event.getEntity(), event.getHand(), held);
                                        if (success) {
                                            machine.onStructureFormed();
                                            var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                                            mwsd.addMapping(machine.getMultiblockState());
                                            mwsd.removeAsyncLogic(machine);
                                            machine.setOriginalBlock(originalState);
                                            return true;
                                        }
                                    }
                                    return false;
                                }).orElse(false)) {
                                    // rollback to the original state
                                    serverLevel.setBlockAndUpdate(pos, originalState);
                                } else {
                                    // success
                                    event.setCanceled(true);
                                    return;
                                }
                            }
                        }

                    }
                }
            }
        }

    }

    /**
     * Releases per-world multiblock async resources when a server level unloads.
     *
     * <p>Preconditions: called by Forge for level unload. Client levels are
     * ignored. Side effects: releases executor services owned by the level's
     * {@link MultiblockWorldSavedData}.</p>
     *
     * @param event level unload event
     */
    @SubscribeEvent
    public static void onWorldUnLoad(LevelEvent.Unload event) {
        LevelAccessor world = event.getLevel();
        if (!world.isClientSide() && world instanceof ServerLevel serverLevel) {
            MultiblockWorldSavedData.getOrCreate(serverLevel).releaseExecutorService();
        }
    }

    /**
     * Releases multiblock async resources for every server level during server
     * shutdown.
     *
     * <p>Side effects: iterates loaded server levels and releases each
     * {@link MultiblockWorldSavedData} executor service.</p>
     *
     * @param event server stopping event
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        var levels = event.getServer().getAllLevels();
        for (var level : levels) {
            if (!level.isClientSide()) {
                MultiblockWorldSavedData.getOrCreate(level).releaseExecutorService();
            }
        }
    }

}
