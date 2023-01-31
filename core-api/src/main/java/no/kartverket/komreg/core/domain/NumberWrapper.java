package no.kartverket.komreg.core.domain;

import no.kartverket.komreg.core.spi.HasValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class NumberWrapper<
        Self extends NumberWrapper<Self, T>,
        T extends Number & Comparable<T>
> extends Number implements Comparable<Self>, HasValue<T> {
    @Override
    public final int intValue() {
        return getValue().intValue();
    }

    @Override
    public final long longValue() {
        return getValue().longValue();
    }

    @Override
    public final float floatValue() {
        return getValue().floatValue();
    }

    @Override
    public final double doubleValue() {
        return getValue().doubleValue();
    }

    @Override
    public final byte byteValue() {
        return getValue().byteValue();
    }

    @Override
    public final short shortValue() {
        return getValue().shortValue();
    }

    public final char toChar() {
        return (char) (getValue().intValue() & 0xFFFF);
    }

    @Override
    public final int compareTo(@NotNull Self that) {
        return getValue().compareTo(that.getValue());
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NumberWrapper<?, ?> that = (NumberWrapper<?, ?>) o;
        return getValue().equals(that.getValue());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getValue());
    }
}
