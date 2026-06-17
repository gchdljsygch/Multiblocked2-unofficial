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

/**
 * Expands LDLib's numeric editor range for phantom slot max-stack-size fields.
 *
 * <p>The target accessor builds configurators reflectively from annotated fields. This pseudo
 * mixin detects LDLib's {@code PhantomSlotWidget.maxStackSize} configurator after creation and
 * widens its range to {@link Integer#MAX_VALUE}, matching MBD's oversized phantom stack support.</p>
 */
@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib.gui.editor.accessors.NumberAccessor")
public abstract class NumberAccessorMixin {
    /**
     * Updates the returned configurator range when it edits a phantom-slot stack limit.
     *
     * @param name        configurator name
     * @param supplier    value supplier
     * @param consumer    update consumer
     * @param forceUpdate whether LDLib should force updates
     * @param field       reflected field being configured
     * @param cir         callback containing the created configurator
     */
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

    /**
     * Checks for LDLib's phantom slot max-stack field by declaring class and name.
     *
     * @param field reflected field supplied to the number accessor
     * @return {@code true} when this field controls phantom item count range
     */
    @Unique
    private static boolean mbd2$isPhantomSlotMaxStackField(Field field) {
        return field.getName().equals("maxStackSize")
                && field.getDeclaringClass().getName().equals("com.lowdragmc.lowdraglib.gui.widget.PhantomSlotWidget");
    }
}
