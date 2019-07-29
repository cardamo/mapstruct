/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.test.constructors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapstruct.ap.test.constructors.CopyingMapper.ImmutableN;
import org.mapstruct.ap.test.constructors.CopyingMapper.MutableN;
import org.mapstruct.ap.testutil.IssueKey;
import org.mapstruct.ap.testutil.WithClasses;
import org.mapstruct.ap.testutil.compilation.annotation.CompilationResult;
import org.mapstruct.ap.testutil.compilation.annotation.ExpectedCompilationOutcome;
import org.mapstruct.ap.testutil.runner.AnnotationProcessorTestRunner;
import org.mapstruct.ap.testutil.runner.Compiler;
import org.mapstruct.ap.testutil.runner.WithSingleCompiler;
import org.mapstruct.factory.Mappers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

@IssueKey("73")
@WithClasses(CopyingMapper.class)
@RunWith(AnnotationProcessorTestRunner.class)
@ExpectedCompilationOutcome(value = CompilationResult.SUCCEEDED)
@WithSingleCompiler( Compiler.JDK ) // TODO: delete
public class ImmutableCopyingTest {

    static CopyingMapper MAPPER = Mappers.getMapper( CopyingMapper.class );

    @Test
    public void shouldCopyImmutableBeanUsingConstructor() {
        ImmutableN source = new ImmutableN( 99 );
        ImmutableN copy = MAPPER.copyImmutable( source );

        assertNotSame( source, copy );
        assertEquals( 99, copy.getN() );
    }


    @Test
    public void shouldCopyMutableWithoutSetters() {
        MutableN source = new MutableN( 98 );
        MutableN copy = MAPPER.copyMutable( source );

        assertNotSame( source, copy );
        assertEquals( 98, copy.getN() );
    }


}
