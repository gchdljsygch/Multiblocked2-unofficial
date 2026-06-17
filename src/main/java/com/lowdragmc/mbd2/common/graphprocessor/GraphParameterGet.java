package com.lowdragmc.mbd2.common.graphprocessor;

import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a graph-node field as a getter for a machine-event exposed parameter.
 *
 * <p>The annotation is retained at runtime so graph/event binding code can discover fields by reflection while an
 * event graph is being executed. It is metadata only and has no direct side effects until consumed by the graph
 * processor.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface GraphParameterGet {
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
