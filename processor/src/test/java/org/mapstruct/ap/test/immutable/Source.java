package org.mapstruct.ap.test.immutable;

public class Source {

    final String s1;
    final Integer n;

    Source(String s1, Integer n) {
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
