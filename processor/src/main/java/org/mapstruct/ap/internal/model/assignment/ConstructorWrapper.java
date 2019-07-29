/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.internal.model.assignment;

import org.mapstruct.ap.internal.model.common.Assignment;
import org.mapstruct.ap.internal.model.common.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the assignment in constructor call
 *
 * @author Ciaran Liedeman
 */
public class ConstructorWrapper extends AssignmentWrapper {

    private final List<Type> thrownTypesToExclude;

    public ConstructorWrapper(Assignment decoratedAssignment, List<Type> thrownTypesToExclude ) {
        super( decoratedAssignment, false );
        this.thrownTypesToExclude = thrownTypesToExclude;
    }

    @Override
    public List<Type> getThrownTypes() {
        List<Type> parentThrownTypes = super.getThrownTypes();
        List<Type> result = new ArrayList<Type>( parentThrownTypes );
        for ( Type thrownTypeToExclude : thrownTypesToExclude ) {
            for ( Type parentThrownType : parentThrownTypes ) {
                if ( parentThrownType.isAssignableTo( thrownTypeToExclude ) ) {
                    result.remove( parentThrownType );
                }
            }
        }
        return result;
    }

}
