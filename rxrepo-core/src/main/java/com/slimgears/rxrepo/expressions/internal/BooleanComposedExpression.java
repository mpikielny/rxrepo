package com.slimgears.rxrepo.expressions.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.slimgears.rxrepo.expressions.BooleanExpression;
import com.slimgears.rxrepo.expressions.ComposedExpression;
import com.slimgears.rxrepo.expressions.ObjectExpression;

@AutoValue
public abstract class BooleanComposedExpression<S, T> implements ComposedExpression<S, T, Boolean>, BooleanExpression<S> {
    @JsonCreator
    public static <S, T> BooleanComposedExpression<S, T> create(
            @JsonProperty("type") Type type,
            @JsonProperty("source") ObjectExpression<S, T> source,
            @JsonProperty("expression") ObjectExpression<T, Boolean> expression) {
        return new AutoValue_BooleanComposedExpression<>(type, source, expression);
    }
}
