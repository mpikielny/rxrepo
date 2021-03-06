package com.slimgears.rxrepo.expressions.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.slimgears.util.autovalue.annotations.PropertyMeta;
import com.slimgears.rxrepo.expressions.NumericExpression;
import com.slimgears.rxrepo.expressions.ObjectExpression;
import com.slimgears.rxrepo.expressions.PropertyExpression;

@AutoValue
public abstract class NumericPropertyExpression<S, T, V extends Number & Comparable<V>> implements PropertyExpression<S, T, V>, NumericExpression<S, V> {
    @JsonCreator
    public static <S, T, V extends Number & Comparable<V>> NumericPropertyExpression<S, T, V> create(
            @JsonProperty("type") Type type,
            @JsonProperty("target") ObjectExpression<S, T> target,
            @JsonProperty("property") PropertyMeta<T, ? extends V> property) {
        return new AutoValue_NumericPropertyExpression<>(type, target, property);
    }
}
