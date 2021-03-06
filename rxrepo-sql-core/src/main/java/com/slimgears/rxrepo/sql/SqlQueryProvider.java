package com.slimgears.rxrepo.sql;

import com.slimgears.rxrepo.expressions.Aggregator;
import com.slimgears.rxrepo.expressions.CollectionExpression;
import com.slimgears.rxrepo.expressions.ObjectExpression;
import com.slimgears.rxrepo.expressions.PropertyExpression;
import com.slimgears.rxrepo.query.Notification;
import com.slimgears.rxrepo.query.provider.DeleteInfo;
import com.slimgears.rxrepo.query.provider.HasMapping;
import com.slimgears.rxrepo.query.provider.QueryInfo;
import com.slimgears.rxrepo.query.provider.QueryProvider;
import com.slimgears.rxrepo.query.provider.UpdateInfo;
import com.slimgears.rxrepo.util.PropertyResolver;
import com.slimgears.util.autovalue.annotations.HasMetaClassWithKey;
import com.slimgears.util.autovalue.annotations.MetaClassWithKey;
import com.slimgears.util.reflect.TypeToken;
import com.slimgears.util.stream.Streams;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.slimgears.util.stream.Streams.ofType;

public class SqlQueryProvider implements QueryProvider {
    private final static Logger log = LoggerFactory.getLogger(SqlQueryProvider.class);
    private final static String aggregationField = "__aggregation";
    private final SqlStatementProvider statementProvider;
    private final SqlStatementExecutor statementExecutor;
    private final SchemaProvider schemaProvider;
    private final ReferenceResolver referenceResolver;

    SqlQueryProvider(SqlStatementProvider statementProvider,
                     SqlStatementExecutor statementExecutor,
                     SchemaProvider schemaProvider,
                     ReferenceResolver referenceResolver) {
        this.statementProvider = statementProvider;
        this.statementExecutor = statementExecutor;
        this.schemaProvider = schemaProvider;
        this.referenceResolver = referenceResolver;
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>> Single<S> insertOrUpdate(S entity) {
        return insertOrUpdate(entity.metaClass(), PropertyResolver.fromObject(entity));
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>> Maybe<S> insertOrUpdate(MetaClassWithKey<K, S> metaClass, K key, Function<Maybe<S>, Maybe<S>> entityUpdater) {
        SqlStatement statement = statementProvider.forQuery(QueryInfo
                .<K, S, S>builder()
                .metaClass(metaClass)
                .predicate(PropertyExpression.ofObject(metaClass.keyProperty()).eq(key))
                .limit(1L)
                .build());

        return statementExecutor
                .executeQuery(statement)
                .firstElement()
                .flatMap((PropertyResolver pr) -> {
                    S oldObj = pr.toObject(metaClass);
                    return entityUpdater
                            .apply(Maybe.just(oldObj))
                            .filter(newObj -> !newObj.equals(oldObj))
                            .map(newObj -> pr.mergeWith(PropertyResolver.fromObject(newObj)))
                            .filter(newPr -> !pr.equals(newPr))
                            .flatMap(newPr -> insertOrUpdate(metaClass, newPr).toMaybe())
                            .switchIfEmpty(Maybe.just(oldObj));
                })
                .switchIfEmpty(Maybe.defer(() -> entityUpdater
                        .apply(Maybe.empty())
                        .flatMap(e -> insert(e).toMaybe())));
    }


    private <K, S extends HasMetaClassWithKey<K, S>> Single<S> insertOrUpdate(MetaClassWithKey<K, S> metaClass, PropertyResolver propertyResolver) {
        SqlStatement statement = statementProvider.forInsertOrUpdate(metaClass, propertyResolver, referenceResolver);
        return schemaProvider.createOrUpdate(metaClass)
                .doOnSubscribe(d -> log.trace("Ensuring class {}", metaClass.simpleName()))
                .doOnError(e -> log.trace("Error when updating class: {}", metaClass.simpleName(), e))
                .doOnComplete(() -> log.trace("Class updated {}", metaClass.simpleName()))
                .andThen(updateReferences(metaClass, propertyResolver))
                .andThen(statementExecutor
                        .executeCommandReturnEntries(statement)
                        .map(pr -> pr.toObject(metaClass))
                        .doOnSubscribe(d -> log.trace("Executing statement: {}", statement.statement()))
                        .doOnError(e -> log.trace("Failed to execute statement: {}", statement.statement(), e))
                        .doOnComplete(() -> log.trace("Execution complete: {}", statement.statement()))
                        .doOnNext(obj -> log.trace("Updated {}", obj))
                        .take(1)
                        .singleOrError());
    }

    private <K, S extends HasMetaClassWithKey<K, S>> Single<S> insert(S entity) {
        MetaClassWithKey<K, S> metaClass = entity.metaClass();
        SqlStatement statement = statementProvider.forInsert(entity, referenceResolver);
        return schemaProvider.createOrUpdate(metaClass)
                .doOnSubscribe(d -> log.trace("Ensuring class {}", metaClass.simpleName()))
                .doOnError(e -> log.trace("Error when updating class: {}", metaClass.simpleName(), e))
                .doOnComplete(() -> log.trace("Class updated {}", metaClass.simpleName()))
                .andThen(updateReferences(entity))
                .andThen(statementExecutor
                        .executeCommandReturnEntries(statement)
                        .map(pr -> pr.toObject(metaClass))
                        .doOnSubscribe(d -> log.trace("Executing statement: {}", statement.statement()))
                        .doOnError(e -> log.trace("Failed to execute statement: {}", statement.statement(), e))
                        .doOnComplete(() -> log.trace("Execution complete: {}", statement.statement()))
                        .doOnNext(obj -> log.trace("Updated {}", obj))
                        .take(1)
                        .singleOrError());
    }

    private <K, S extends HasMetaClassWithKey<K, S>> Completable updateReferences(S entity) {
        return updateReferences(entity.metaClass(), PropertyResolver.fromObject(entity));
    }

    @SuppressWarnings("unchecked")
    private <K, S extends HasMetaClassWithKey<K, S>> Completable updateReferences(MetaClassWithKey<K, S> metaClass, PropertyResolver propertyResolver) {
        List<HasMetaClassWithKey> references = Streams.fromIterable(metaClass.properties())
                .filter(PropertyMetas::isReference)
                .flatMap(prop -> Optional
                        .ofNullable(propertyResolver.getProperty(prop))
                        .map(Stream::of)
                        .orElseGet(Stream::empty))
                .flatMap(ofType(HasMetaClassWithKey.class))
                .collect(Collectors.toList());

        return Observable.fromIterable(references)
                .concatMapSingle(this::insertOrUpdate)
                .doOnSubscribe(d -> log.trace("Updating {} {} references", references.size(), metaClass.simpleName()))
                .doOnNext(obj -> log.trace("Updated object {} (referenced from {})", obj, metaClass.simpleName()))
                .doOnError(e -> log.trace("Failed to update object {} references: {}", metaClass.simpleName(), e))
                .doOnComplete(() -> log.trace("All {} {} references were updated", references.size(), metaClass.simpleName()))
                .ignoreElements();
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>, T> Observable<T> query(QueryInfo<K, S, T> query) {
        TypeToken<? extends T> objectType = HasMapping.objectType(query);
        return schemaProvider.createOrUpdate(query.metaClass()).andThen(statementExecutor
                .executeQuery(statementProvider.forQuery(query))
                .map(pr -> pr.toObject(objectType)));
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>, T> Observable<Notification<T>> liveQuery(QueryInfo<K, S, T> query) {
        TypeToken<? extends T> objectType = HasMapping.objectType(query);
        return schemaProvider.createOrUpdate(query.metaClass()).andThen(statementExecutor
                .executeLiveQuery(statementProvider.forQuery(query))
                .map(notification -> notification.map(pr -> pr.toObject(objectType))));
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>, T, R> Single<R> aggregate(QueryInfo<K, S, T> query, Aggregator<T, T, R, ?> aggregator) {
        TypeToken<? extends T> elementType = HasMapping.objectType(query);
        ObjectExpression<T, R> aggregation = aggregator.apply(CollectionExpression.indirectArg(elementType));
        TypeToken<? extends R> resultType = aggregation.objectType();
        return schemaProvider.createOrUpdate(query.metaClass()).andThen(statementExecutor
                .executeQuery(statementProvider.forAggregation(query, aggregation, aggregationField))
                .map(pr -> {
                    Object obj = pr.getProperty(aggregationField, resultType.asClass());
                    //noinspection unchecked
                    return (obj instanceof PropertyResolver)
                            ? ((PropertyResolver)obj).toObject(resultType)
                            : (R)obj;
                })
                .singleOrError());
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>> Observable<S> update(UpdateInfo<K, S> update) {
        return schemaProvider.createOrUpdate(update.metaClass()).andThen(statementExecutor
                .executeCommandReturnEntries(statementProvider.forUpdate(update))
                .map(pr -> pr.toObject(update.metaClass())));
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>> Single<Integer> delete(DeleteInfo<K, S> deleteInfo) {
        return schemaProvider.createOrUpdate(deleteInfo.metaClass()).andThen(statementExecutor
               .executeCommandReturnCount(statementProvider.forDelete(deleteInfo)));
    }
}
