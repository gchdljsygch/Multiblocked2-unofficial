package com.lowdragmc.mbd2.common;

import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Builds server-side administrative commands for project-backed MBD content.
 *
 * <p>The business goal is to let operators reload machine and recipe type
 * project files without restarting the game. Command builders are pure until
 * execution; command execution mutates in-memory project definitions and sends
 * feedback messages through the command source.</p>
 */
public class ServerCommands {
    /**
     * Creates the root {@code /mbd2} command and its reload subcommands.
     *
     * <p>Preconditions: called while Forge is registering server commands. The
     * returned root requires permission level {@code 2}. Side effects on command
     * execution: {@code reload_machine_projects} clears catalyst candidates and
     * reloads machine definitions that came from project files;
     * {@code reload_recipe_type_projects} reloads recipe types that came from
     * project files and emits status messages.</p>
     *
     * @return immutable list of server command roots to register; currently
     * contains {@code mbd2}
     */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> createServerCommands() {
        return List.of(
                Commands.literal("mbd2")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reload_machine_projects")
                                .executes(context -> {
                                    // clear up the catalyst candidates
                                    MultiblockMachineDefinition.CATALYST_CANDIDATES.clear();
                                    // reload all machine definitions
                                    for (var definition : MBDRegistries.MACHINE_DEFINITIONS) {
                                        if (definition.isCreatedFromProjectFile()) {
                                            definition.reloadFromProjectFile();
                                            context.getSource().sendSystemMessage(Component.literal(definition.id().toString()).append(Component.translatable("project.reload")));
                                        }
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("reload_recipe_type_projects")
                                .executes(context -> {
                                    // reload all recipe types
                                    for (var recipeType : MBDRegistries.RECIPE_TYPES) {
                                        if (recipeType.isCreatedFromProjectFile()) {
                                            recipeType.reloadFromProjectFile();
                                            context.getSource().sendSystemMessage(Component.literal(recipeType.getRegistryName().toString()).append(Component.translatable("project.reload")));
                                            context.getSource().sendSystemMessage(Component.translatable("project.reload.recipe"));
                                        }
                                    }
                                    return 1;
                                })
                        )
        );
    }
}
