package com.lowdragmc.mbd2.client;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.compass.CompassSelectorWidget;
import com.lowdragmc.lowdraglib.gui.compass.CompassView;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Arrays;
import java.util.List;

/**
 * Builds client-only commands used by MBD development and editing tools.
 *
 * <p>The business goal is to expose local UI tooling without requiring a server
 * command round trip. Command builders are pure until executed; execution runs
 * on the client command context and mutates the local Minecraft screen/menu
 * state.</p>
 */
@OnlyIn(Dist.CLIENT)
public class ClientCommands {

    /**
     * Creates the client command tree for opening the machine editor.
     *
     * <p>Preconditions: called while Forge is registering client commands. The
     * returned command requires permission level {@code 2}; when executed,
     * Minecraft must have a local player. Side effects on execution: creates a
     * transient UI holder, initializes a {@link MachineEditor} UI, opens a
     * {@link ModularUIGuiContainer}, and replaces the player's current container
     * menu with that screen's menu.</p>
     *
     * @return immutable list of client command roots to register; currently
     * contains {@code mbd2_editor}
     */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> createClientCommands() {
        return List.of(
                Commands.literal("mbd2_editor")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> {
                            var holder = new IUIHolder() {
                                @Override
                                public ModularUI createUI(Player entityPlayer) {
                                    return null;
                                }

                                @Override
                                public boolean isInvalid() {
                                    return true;
                                }

                                @Override
                                public boolean isRemote() {
                                    return true;
                                }

                                @Override
                                public void markAsDirty() {

                                }
                            };

                            Minecraft minecraft = Minecraft.getInstance();
                            LocalPlayer entityPlayer = minecraft.player;
                            ModularUI uiTemplate = new ModularUI(holder, entityPlayer).widget(new MachineEditor());
                            uiTemplate.initWidgets();
                            ModularUIGuiContainer ModularUIGuiContainer = new ModularUIGuiContainer(uiTemplate, entityPlayer.containerMenu.containerId);
                            minecraft.setScreen(ModularUIGuiContainer);
                            entityPlayer.containerMenu = ModularUIGuiContainer.getMenu();

                            return 1;
                        })
        );
    }

}
