/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.internal.util.accessor;

import org.mapstruct.ap.internal.model.common.Parameter;

import javax.lang.model.element.ExecutableElement;

public class InstantiatorAccessor extends ExecutableElementAccessor {

    private final Parameter parameter;

    public InstantiatorAccessor(ExecutableElement element, Parameter parameter) {
        super( element, parameter.getType().getTypeMirror(), AccessorType.INSTANTIATOR );

        this.parameter = parameter;
    }

    public Parameter getParameter() {
        return parameter;
    }
}
