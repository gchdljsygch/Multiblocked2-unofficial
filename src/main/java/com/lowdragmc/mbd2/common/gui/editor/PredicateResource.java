package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.lowdragmc.mbd2.common.gui.editor.PredicateResource.RESOURCE_NAME;


@LDLRegister(name = RESOURCE_NAME, group = "resource")
public class PredicateResource extends Resource<SimplePredicate> {
    public final static String RESOURCE_NAME = "mbd2.gui.editor.group.predicate";

    public PredicateResource() {
        super(new File(LDLib.getLDLibDir(), "assets/resources/predicates"));
        addBuiltinResource("any", SimplePredicate.ANY);
        addBuiltinResource("air", SimplePredicate.AIR);
    }

    @Override
    public String name() {
        return RESOURCE_NAME;
    }

    @Override
    public ResourceContainer<SimplePredicate, ? extends Widget> createContainer(ResourcePanel resourcePanel) {
        return new PredicateResourceContainer(this, resourcePanel);
    }

    @Nullable
    @Override
    public Tag serialize(SimplePredicate predicate) {
        return SimplePredicate.serializeWrapper(predicate);
    }

    @Override
    public SimplePredicate deserialize(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            return SimplePredicate.deserializeWrapper(compoundTag);
        }
        return SimplePredicate.ANY;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        getBuiltinResources().clear();
        addBuiltinResource("any", SimplePredicate.ANY);
        addBuiltinResource("air", SimplePredicate.AIR);
        for (String key : nbt.getAllKeys()) {
            addBuiltinResource(key, deserialize(nbt.get(key)));
        }
    }
}
