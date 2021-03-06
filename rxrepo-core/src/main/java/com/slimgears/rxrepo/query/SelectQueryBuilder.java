package com.slimgears.rxrepo.query;

import com.slimgears.rxrepo.expressions.ObjectExpression;
import com.slimgears.rxrepo.expressions.PropertyExpression;
import com.slimgears.util.autovalue.annotations.HasMetaClassWithKey;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;

@SuppressWarnings("WeakerAccess")
public abstract class SelectQueryBuilder<K, S extends HasMetaClassWithKey<K, S>>
    implements QueryBuilder<SelectQueryBuilder<K, S>, K, S> {
    public abstract SelectQueryBuilder<K, S> skip(long skip);

    public abstract <V extends Comparable<V>> SelectQueryBuilder<K, S> orderBy(PropertyExpression<S, ?, V> field, boolean ascending);

    public abstract SelectQuery<S> select();

    public abstract <T> SelectQuery<T> select(ObjectExpression<S, T> expression);

    public abstract LiveSelectQuery<S> liveSelect();

    public abstract <T> LiveSelectQuery<T> liveSelect(ObjectExpression<S, T> expression);

    public <V extends Comparable<V>> SelectQueryBuilder<K, S> orderBy(PropertyExpression<S, ?, V> field) {
        return orderBy(field, true);
    }

    public <V extends Comparable<V>> SelectQueryBuilder<K, S> orderByDescending(PropertyExpression<S, S, V> field) {
        return orderBy(field, false);
    }

    public Single<Long> count() {
        return select().count();
    }

    public Maybe<S> first() {
        return select().first();
    }

    public Observable<Long> observeCount() {
        return liveSelect().count();
    }

    public Observable<S> observeFirst() {
        return liveSelect().first();
    }

    @SafeVarargs
    public final Observable<S> retrieve(PropertyExpression<S, ?, ?>... properties) {
        return select().retrieve(properties);
    }

    @SafeVarargs
    public final Observable<Notification<S>> observe(PropertyExpression<S, ?, ?>... properties) {
        return liveSelect().observe(properties);
    }

    public Observable<S> retrieve() {
        //noinspection unchecked
        return retrieve(new PropertyExpression[0]);
    }

    public Observable<Notification<S>> observe() {
        //noinspection unchecked
        return observe(new PropertyExpression[0]);
    }
}
