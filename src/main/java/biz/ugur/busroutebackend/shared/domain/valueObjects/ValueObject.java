package biz.ugur.busroutebackend.shared.domain.valueObjects;

import java.io.Serializable;

public abstract class ValueObject implements Serializable {

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();
}