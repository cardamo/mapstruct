/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.internal.model;

import org.mapstruct.ap.internal.model.common.Constructor;
import org.mapstruct.ap.internal.model.common.ModelElement;
import org.mapstruct.ap.internal.model.common.Parameter;
import org.mapstruct.ap.internal.model.common.Type;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConstructorMapping extends ModelElement {

    private final Constructor constructor;
    private final List<PropertyMapping> propertyMappings;

    public ConstructorMapping(Constructor constructor, List<PropertyMapping> propertyMappings) {
        this.constructor = constructor;
        this.propertyMappings = propertyMappings;
    }

    public Constructor getConstructor() {
        return constructor;
    }

    public Collection<PropertyMapping> getProperties() {
        return propertyMappings;
    }

    @Override
    public Set<Type> getImportTypes() {
        return constructor.getProperties().values().stream()
            .map( Parameter::getType )
            .collect( Collectors.toSet());
    }
}
