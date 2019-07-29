package org.mapstruct.ap.test.constructors;

import org.mapstruct.Mapper;

@Mapper
public interface CopyingMapper {

    ImmutableN copyImmutable(ImmutableN source);

    MutableN copyMutable(MutableN source);

    class ImmutableN {

        private final int n;

        public ImmutableN(int n) {
            this.n = n;
        }

        public int getN() {
            return n;
        }
    }

    class MutableN {

        private int n;

        public MutableN(int n) {
            this.n = n;
        }

        public int getN() {
            return n;
        }

        public void setN(int n) {
            throw new RuntimeException( "Yes there's a setter, but single-arg constructor must be preferred" );
        }
    }
}
