/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.test.immutable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapstruct.ap.testutil.IssueKey;
import org.mapstruct.ap.testutil.WithClasses;
import org.mapstruct.ap.testutil.compilation.annotation.CompilationResult;
import org.mapstruct.ap.testutil.compilation.annotation.ExpectedCompilationOutcome;
import org.mapstruct.ap.testutil.runner.AnnotationProcessorTestRunner;

import static org.junit.Assert.assertEquals;

@IssueKey("73")
@WithClasses({
    Source.class,
    ExtendedSource.class,
    Target.class,
    Empty.class,
    ImmutableMapper.class})
@RunWith(AnnotationProcessorTestRunner.class)
public class ImmutableMappingTest {

    @Test
    @ExpectedCompilationOutcome(value = CompilationResult.SUCCEEDED)
    public void constructorShouldBeUsed() {
        ExtendedSource source = new ExtendedSource( "a", "b", 81 );
        Target target = ImmutableMapper.INSTANCE.extendedSourceToTarget( source );

        assertEquals( source.getS1(), target.getS1() );
        assertEquals( source.getS2(), target.getS2() );
        assertEquals( source.getN().intValue(), target.getN() );
        assertEquals( "__initial_value", target.getSettable() );
        assertEquals( source.getS2(), target.getNotSettable() );
    }

}
