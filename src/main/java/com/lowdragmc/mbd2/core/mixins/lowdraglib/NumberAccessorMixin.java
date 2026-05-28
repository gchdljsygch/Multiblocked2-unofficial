package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib.gui.editor.accessors.NumberAccessor")
public abstract class NumberAccessorMixin {
    @Inject(
            method = "create(Ljava/lang/String;Ljava/util/function/Supplier;Ljava/util/function/Consumer;ZLjava/lang/reflect/Field;)Lcom/lowdragmc/lowdraglib/gui/editor/configurator/Configurator;",
            at = @At("RETURN"),
            remap = false
    )
    private void mbd2$expandPhantomSlotMaxStackRange(
            String name,
            Supplier<Number> supplier,
            Consumer<Number> consumer,
            boolean forceUpdate,
            Field field,
            CallbackInfoReturnable<Object> cir
    ) {
        if (!mbd2$isPhantomSlotMaxStackField(field)) {
            return;
        }

        Object configurator = cir.getReturnValue();
        if (configurator == null) {
            return;
        }

        try {
            Method setRange = configurator.getClass().getMethod("setRange", Number.class, Number.class);
            setRange.invoke(configurator, Integer.valueOf(0), Integer.valueOf(Integer.MAX_VALUE));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private static boolean mbd2$isPhantomSlotMaxStackField(Field field) {
        return field.getName().equals("maxStackSize")
                && field.getDeclaringClass().getName().equals("com.lowdragmc.lowdraglib.gui.widget.PhantomSlotWidget");
    }
}
