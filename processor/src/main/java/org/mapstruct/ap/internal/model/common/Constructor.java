/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.internal.model.common;

import org.mapstruct.ap.internal.util.accessor.InstantiatorAccessor;

import javax.lang.model.element.ExecutableElement;
import java.util.Map;

public class Constructor {

    private final Map<String, Parameter> properties;
    private final ExecutableElement element;

    public Constructor(Map<String, Parameter> properties, ExecutableElement element) {
        this.properties = properties;
        this.element = element;
    }

    public Map<String, Parameter> getProperties() {
        return properties;
    }

    public ExecutableElement getElement() {
        return element;
    }

    public InstantiatorAccessor asWriteAccessorOf(Parameter parameter) {
        return new InstantiatorAccessor( element, parameter );
    }
}
