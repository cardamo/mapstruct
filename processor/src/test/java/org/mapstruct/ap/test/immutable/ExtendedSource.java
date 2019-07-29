package org.mapstruct.ap.test.immutable;

public class ExtendedSource extends Source {

    final String s2;

    ExtendedSource(String s1, String s2, Integer n) {
        super( s1, n );
        this.s2 = s2;
    }

    public String getS2() {
        return s2;
    }
}
