package com.slimgears.rxrepo.expressions.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.slimgears.util.autovalue.annotations.PropertyMeta;
import com.slimgears.rxrepo.expressions.CollectionExpression;
import com.slimgears.rxrepo.expressions.ObjectExpression;
import com.slimgears.rxrepo.expressions.PropertyExpression;

import java.util.Collection;

@AutoValue
public abstract class CollectionPropertyExpression<S, T, E> implements PropertyExpression<S, T, Collection<E>>, CollectionExpression<S, E> {
    @JsonCreator
    public static <S, T, E> CollectionPropertyExpression<S, T, E> create(
            @JsonProperty("type") Type type,
            @JsonProperty("target") ObjectExpression<S, T> target,
            @JsonProperty("property") PropertyMeta<T, ? extends Collection<E>> property) {
        return new AutoValue_CollectionPropertyExpression<>(type, target, property);
    }
}
