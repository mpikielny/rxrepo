package com.slimgears.rxrepo.filters;

import com.slimgears.rxrepo.expressions.BooleanExpression;
import com.slimgears.rxrepo.expressions.ObjectExpression;
import com.slimgears.util.reflect.TypeToken;

import java.util.Optional;

public interface Filter<T> {
    <S> Optional<BooleanExpression<S>> toExpression(ObjectExpression<S, T> arg);

    default <S> Optional<BooleanExpression<S>> toExpression(TypeToken<T> type) {
        return toExpression(ObjectExpression.objectArg(type));
    }

    default Filter<T> combineWith(Filter<T> filter) {
        Filter<T> self = this;
        return new Filter<T>() {
            @Override
            public <S> Optional<BooleanExpression<S>> toExpression(ObjectExpression<S, T> arg) {
                return Filters.combineExpressions(self.toExpression(arg), filter.toExpression(arg));
            }
        };
    }

    static <T> Filter<T> empty() {
        return new Filter<T>() {
            @Override
            public <S> Optional<BooleanExpression<S>> toExpression(ObjectExpression<S, T> arg) {
                return Optional.empty();
            }
        };
    }
}
