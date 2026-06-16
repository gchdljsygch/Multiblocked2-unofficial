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

@Mixin(ReloadableResourceManager.class)
public abstract class MBDWorkspaceResourcePackMixin {

    @Shadow
    @Final
    private PackType type;

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
