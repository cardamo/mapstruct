/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.test.nestedsourceproperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapstruct.ap.test.nestedsourceproperties._target.AdderUsageObserver;
import org.mapstruct.ap.test.nestedsourceproperties._target.ChartEntry;
import org.mapstruct.ap.test.nestedsourceproperties._target.ChartPositions;
import org.mapstruct.ap.test.nestedsourceproperties.source.Artist;
import org.mapstruct.ap.test.nestedsourceproperties.source.Chart;
import org.mapstruct.ap.test.nestedsourceproperties.source.Label;
import org.mapstruct.ap.test.nestedsourceproperties.source.Song;
import org.mapstruct.ap.test.nestedsourceproperties.source.Studio;
import org.mapstruct.ap.testutil.IssueKey;
import org.mapstruct.ap.testutil.WithClasses;
import org.mapstruct.ap.testutil.compilation.annotation.CompilationResult;
import org.mapstruct.ap.testutil.compilation.annotation.Diagnostic;
import org.mapstruct.ap.testutil.compilation.annotation.ExpectedCompilationOutcome;
import org.mapstruct.ap.testutil.runner.AnnotationProcessorTestRunner;
import org.mapstruct.ap.testutil.runner.GeneratedSource;

/**
 * @author Sjaak Derksen
 */
@WithClasses({ Song.class, Artist.class, Chart.class, Label.class, Studio.class, ChartEntry.class })
@IssueKey("65")
@RunWith(AnnotationProcessorTestRunner.class)
public class NestedSourcePropertiesTest {

    @Rule
    public final GeneratedSource generatedSource = new GeneratedSource();

    @Test
    @WithClasses({ ArtistToChartEntry.class })
    public void shouldGenerateImplementationForPropertyNamesOnly() {
        generatedSource.addComparisonToFixtureFor( ArtistToChartEntry.class );

        Studio studio = new Studio();
        studio.setName( "Abbey Road" );
        studio.setCity( "London" );

        Label label = new Label();
        label.setStudio( studio );
        label.setName( "EMY" );

        Artist artist = new Artist();
        artist.setName( "The Beatles" );
        artist.setLabel( label );

        Song song = new Song();
        song.setArtist( artist );
        song.setTitle( "A Hard Day's Night" );

        ChartEntry chartEntry = ArtistToChartEntry.MAPPER.map( song );

        assertThat( chartEntry ).isNotNull();
        assertThat( chartEntry.getArtistName() ).isEqualTo( "The Beatles" );
        assertThat( chartEntry.getChartName() ).isNull();
        assertThat( chartEntry.getCity() ).isEqualTo( "London" );
        assertThat( chartEntry.getPosition() ).isEqualTo( 0 );
        assertThat( chartEntry.getRecordedAt() ).isEqualTo( "Abbey Road" );
        assertThat( chartEntry.getSongTitle() ).isEqualTo( "A Hard Day's Night" );
    }

    @Test
    @WithClasses({ ArtistToChartEntry.class })
    public void shouldGenerateImplementationForMultipleParam() {

        Studio studio = new Studio();
        studio.setName( "Abbey Road" );
        studio.setCity( "London" );

        Label label = new Label();
        label.setStudio( studio );
        label.setName( "EMY" );

        Artist artist = new Artist();
        artist.setName( "The Beatles" );
        artist.setLabel( label );

        Song song = new Song();
        song.setArtist( artist );
        song.setTitle( "A Hard Day's Night" );

        Chart chart = new Chart();
        chart.setName( "Billboard" );
        chart.setType( "record-sales" );

        ChartEntry chartEntry = ArtistToChartEntry.MAPPER.map( chart, song, 1 );

        assertThat( chartEntry ).isNotNull();
        assertThat( chartEntry.getArtistName() ).isEqualTo( "The Beatles" );
        assertThat( chartEntry.getChartName() ).isEqualTo( "Billboard" );
        assertThat( chartEntry.getCity() ).isEqualTo( "London" );
        assertThat( chartEntry.getPosition() ).isEqualTo( 1 );
        assertThat( chartEntry.getRecordedAt() ).isEqualTo( "Abbey Road" );
        assertThat( chartEntry.getSongTitle() ).isEqualTo( "A Hard Day's Night" );
    }

    @Test
    @WithClasses({ ArtistToChartEntry.class })
    public void shouldPickPropertyNameOverParameterName() {

        Chart chart = new Chart();
        chart.setName( "Billboard" );
        chart.setType( "record-sales" );

        ChartEntry chartEntry = ArtistToChartEntry.MAPPER.map( chart );

        assertThat( chartEntry ).isNotNull();
        assertThat( chartEntry.getArtistName() ).isNull();
        assertThat( chartEntry.getChartName() ).isEqualTo( "Billboard" );
        assertThat( chartEntry.getCity() ).isNull();
        assertThat( chartEntry.getPosition() ).isEqualTo( 0 );
        assertThat( chartEntry.getRecordedAt() ).isNull();
        assertThat( chartEntry.getSongTitle() ).isNull();
    }

    @Test
    @WithClasses({ ArtistToChartEntryAdder.class, ChartPositions.class, AdderUsageObserver.class })
    public void shouldUseAddAsTargetAccessor() {

        AdderUsageObserver.setUsed( false );
        Song song = new Song();
        song.setPositions( Arrays.asList( 3, 5 ) );

        Chart chart = new Chart();
        chart.setSong( song );

        ChartPositions positions = ArtistToChartEntryAdder.MAPPER.map( chart );
        assertThat( positions ).isNotNull();
        assertThat( positions.getPositions() ).containsExactly( 3L, 5L );

        assertTrue( AdderUsageObserver.isUsed() );
    }

    @Test
    @WithClasses({ ArtistToChartEntryGetter.class, ChartPositions.class, AdderUsageObserver.class })
    public void shouldUseGetAsTargetAccessor() {

        AdderUsageObserver.setUsed( false );
        Song song = new Song();
        song.setPositions( Arrays.asList( 3, 5 ) );

        Chart chart = new Chart();
        chart.setSong( song );

        ChartPositions positions = ArtistToChartEntryGetter.MAPPER.map( chart );
        assertThat( positions ).isNotNull();
        assertThat( positions.getPositions() ).containsExactly( 3L, 5L );

        assertFalse( AdderUsageObserver.isUsed() );
    }

    @Test
    @IssueKey( "838" )
    @ExpectedCompilationOutcome(
             value = CompilationResult.FAILED,
            diagnostics = {
                @Diagnostic( type = ArtistToChartEntryErroneous.class,
                        kind = javax.tools.Diagnostic.Kind.ERROR,
                        line = 34,
                        messageRegExp = "java.lang.Integer does not have an accessible parameterless constructor." )
            }
    )
    @WithClasses({ ArtistToChartEntryErroneous.class })
    public void inverseShouldRaiseErrorForEmptyContructor() {
    }
}
