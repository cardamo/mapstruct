package org.mapstruct.ap.test.immutable;

public class Target {

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
