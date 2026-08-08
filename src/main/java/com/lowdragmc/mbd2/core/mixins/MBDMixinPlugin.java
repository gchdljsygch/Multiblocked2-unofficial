package com.lowdragmc.mbd2.core.mixins;

import com.lowdragmc.lowdraglib.core.mixins.MixinPluginShared;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin configuration plugin that disables optional integration mixins when their target
 * mods are not loaded.
 *
 * <p>The plugin is evaluated during mixin discovery. Mixins outside optional integration
 * packages are applied normally. Optional checks use Forge's loading mod list or class-file
 * resources for integrations that do not have a simple mod-file lookup. Resource lookups are
 * deliberately used instead of class loading because defining a mixin target before Mixin has
 * prepared it prevents that mixin from being applied.</p>
 */
public class MBDMixinPlugin implements IMixinConfigPlugin, MixinPluginShared {
    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.create")) {
            return isClassFilePresent("com.simibubi.create.compat.Mods");
        }
        if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.arsnouveau")) {
            return LoadingModList.get().getModFileById("ars_nouveau") != null;
        }
        if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.manaandartifice")) {
            return LoadingModList.get().getModFileById("mna") != null;
        }
        if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.ae2")) {
            return LoadingModList.get().getModFileById("ae2") != null
                    && isClassFilePresent("appeng.helpers.InterfaceLogic")
                    && isClassFilePresent("appeng.helpers.MultiCraftingTracker");
        }
        if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.fusion")) {
            return LoadingModList.get().getModFileById("fusion") != null;
        }
        if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.jei")) {
            return LoadingModList.get().getModFileById("jei") != null;
        }
//        if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.kjs") || mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.rhino")) {
//            return MixinPluginShared.isClassFound("dev.latvian.mods.kubejs.KubeJSPlugin");
//        } else if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.create")) {
//            return MixinPluginShared.isClassFound("com.simibubi.create.compat.Mods");
//        } else if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.rei")) {
//            return MixinPluginShared.isClassFound("me.shedaniel.rei.api.common.plugins.REIPlugin");
//        } else if (mixinClassName.contains("com.lowdragmc.mbd2.fabric.core.mixins.kjs")) {
//            return MixinPluginShared.isClassFound("dev.latvian.mods.kubejs.fabric.KubeJSFabric");
//        } else if (mixinClassName.contains("com.lowdragmc.mbd2.forge.core.mixins.kjs")) {
//            return MixinPluginShared.isClassFound("dev.latvian.mods.kubejs.forge.KubeJSForge");
//        } else if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.top")) {
//            return MixinPluginShared.isClassFound("mcjty.theoneprobe.api.ITheOneProbe");
//        } else if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.jei")) {
//            return MixinPluginShared.isClassFound("mezz.jei.api.IModPlugin");
//        } else if (mixinClassName.contains("com.lowdragmc.mbd2.core.mixins.emi")) {
//            return MixinPluginShared.isClassFound("dev.emi.emi.api.EmiPlugin");
//        }
        return true;
    }

    /**
     * Checks whether a class file is visible without defining the class.
     *
     * <p>{@code Class.forName}, including with initialization disabled, defines the class. That
     * is unsafe while Mixin is still preparing configurations because the queried class can be a
     * mixin target. A resource lookup keeps this compatibility check side-effect free.</p>
     *
     * @param className binary name of the class to look up
     * @return whether a matching class file is visible to either active class loader
     */
    private static boolean isClassFilePresent(String className) {
        String resourceName = className.replace('.', '/') + ".class";
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null && contextClassLoader.getResource(resourceName) != null) {
            return true;
        }

        ClassLoader pluginClassLoader = MBDMixinPlugin.class.getClassLoader();
        return pluginClassLoader != null
                && pluginClassLoader != contextClassLoader
                && pluginClassLoader.getResource(resourceName) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
