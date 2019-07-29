/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.test.constructors;

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

    class Source {

        final String s1;
        final Integer n;

        public Source(String s1, Integer n) {
            this.s1 = s1;
            this.n = n;
        }

        public String getS1() {
            return s1;
        }


        public Integer getN() {
            return n;
        }
    }

    class Target {

        private final String s1;
        private final String s2;
        private final int n;

        private String settable;
        private String notSettable;

        public Target(String s1, String s2, int n, String notSettable) {
            this.s1 = s1;
            this.s2 = s2;
            this.n = n;
            this.settable = "__initial_value";
            this.notSettable = notSettable;
        }

        public Target(String s1, int n) {
            this( s1, "__default_s2", n, "__constant_value" );
        }

        public String getS1() {
            return s1;
        }

        public String getS2() {
            return s2;
        }

        public int getN() {
            return n;
        }

        public String getSettable() {
            return settable;
        }

        public void setSettable(String settable) {
            this.settable = settable;
        }

        public String getNotSettable() {
            return notSettable;
        }
    }

    class Empty {
    }

    class ExtendedSource extends Source {

        final String s2;

        ExtendedSource(String s1, String s2, Integer n) {
            super( s1, n );
            this.s2 = s2;
        }

        public String getS2() {
            return s2;
        }
    }
}
