/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.test.immutable;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

/**
 * Checklist
 * # rewire properties
 * # ignore properties (use default or fail) ?
 * # use constants for some arguments
 * # integration tests: lombok, kotlin, scala, jackson?
 * # what if there's only all-args constructor but setters are present too?
 * # target properties can't be nested
 */
@Mapper
public interface ImmutableMapper {

    ImmutableMapper INSTANCE = Mappers.getMapper( ImmutableMapper.class );

    @Mapping(target = "settable", source = "s1")
    Target sourceToTarget(Source source);

    @Mapping(target = "settable", ignore = true)
    @Mapping(target = "notSettable", source = "s2")
    Target extendedSourceToTarget(ExtendedSource source);

    @Mappings({
        @Mapping(target = "s1", constant = "one"),
        @Mapping(target = "n", constant = "44"),
        @Mapping(target = "settable", constant = "three")
    })
    Target emptyWithConstantsToTarget(Empty e);

}
