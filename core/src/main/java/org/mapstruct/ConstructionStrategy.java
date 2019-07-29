/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct;

/**
 * @since 1.4
 */
public enum ConstructionStrategy {

    /**
     * Use only default no-args constructor.
     */
    NO_ARGS_CONSTRUCTOR,

    /**
     * Assume that target bean is immutable and all properties are passed through a constructor.
     * If any writing accessor is detected a compile error will occur.
     */
    NO_ACCESSORS,

    /**
     * Use up all of the accessors as if it's NO_ARGS_CONSTRUCTOR but also
     */
    PREFER_ACCESSORS,

    /**
     *
     */
    PREFER_CONSTRUCTOR;

}
