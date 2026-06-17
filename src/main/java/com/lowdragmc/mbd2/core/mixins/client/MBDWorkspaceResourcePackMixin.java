package com.lowdragmc.mbd2.core.mixins.client;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.utils.CustomResourcePack;
import com.lowdragmc.mbd2.MBD2;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds MBD2's workspace-backed client resource pack to Minecraft reloads.
 *
 * <p>The resource pack is injected only for {@link PackType#CLIENT_RESOURCES} and only when the
 * configured MBD2 workspace directory exists. A copied pack list is returned so the reload path
 * can keep treating the argument as immutable.</p>
 */
@Mixin(ReloadableResourceManager.class)
public abstract class MBDWorkspaceResourcePackMixin {

    @Shadow
    @Final
    private PackType type;

    /**
     * Appends the workspace resource pack to client-resource reloads.
     *
     * @param resourcePacks packs supplied by Minecraft's reload pipeline
     * @return unchanged pack list for non-client reloads or a copy with the MBD2 workspace pack
     */
    @ModifyVariable(method = "createReload", at = @At("HEAD"), index = 4, argsOnly = true)
    private List<PackResources> mbd2$injectWorkspaceResources(List<PackResources> resourcePacks) {
        if (type != PackType.CLIENT_RESOURCES) {
            return resourcePacks;
        }

        var workspace = MBD2.getLocation();
        if (!workspace.isDirectory()) {
            return resourcePacks;
        }

        var mutablePacks = new ArrayList<>(resourcePacks);
        mutablePacks.add(new CustomResourcePack(LDLib.getLDLibDir(), MBD2.MOD_ID, PackType.CLIENT_RESOURCES));
        return mutablePacks;
    }
}
