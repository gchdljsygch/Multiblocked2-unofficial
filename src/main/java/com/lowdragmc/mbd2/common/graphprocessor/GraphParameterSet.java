package com.lowdragmc.mbd2.common.graphprocessor;

import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a graph-node field as a setter for a machine-event exposed parameter.
 *
 * <p>The annotation is retained at runtime so event graph execution can gather modified field values back into the
 * event's exposed parameter map. It is intended for mutable graph state and should only be used on fields whose value
 * type matches the declared parameter type.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface GraphParameterSet {
    /**
     * The identity name of the parameter
     */
    String identity() default "";

    /**
     * The display name of the parameter
     */
    String displayName() default "";

    /**
     * The type of the parameter
     */
    Class type() default ExposedParameter.class;

    /**
     * The description of the parameter
     */
    String[] tips() default {};
}
