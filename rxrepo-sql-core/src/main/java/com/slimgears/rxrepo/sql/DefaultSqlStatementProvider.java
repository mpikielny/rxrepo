package com.slimgears.rxrepo.sql;

import com.slimgears.rxrepo.expressions.ObjectExpression;
import com.slimgears.rxrepo.expressions.PropertyExpression;
import com.slimgears.rxrepo.query.provider.DeleteInfo;
import com.slimgears.rxrepo.query.provider.HasEntityMeta;
import com.slimgears.rxrepo.query.provider.HasLimit;
import com.slimgears.rxrepo.query.provider.HasMapping;
import com.slimgears.rxrepo.query.provider.HasPagination;
import com.slimgears.rxrepo.query.provider.HasPredicate;
import com.slimgears.rxrepo.query.provider.HasProperties;
import com.slimgears.rxrepo.query.provider.HasSortingInfo;
import com.slimgears.rxrepo.query.provider.QueryInfo;
import com.slimgears.rxrepo.query.provider.SortingInfo;
import com.slimgears.rxrepo.query.provider.UpdateInfo;
import com.slimgears.rxrepo.util.PropertyResolver;
import com.slimgears.util.autovalue.annotations.HasMetaClassWithKey;
import com.slimgears.util.autovalue.annotations.MetaClassWithKey;
import com.slimgears.util.autovalue.annotations.PropertyMeta;
import com.slimgears.util.reflect.TypeToken;
import com.slimgears.util.stream.Optionals;
import com.slimgears.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.slimgears.rxrepo.sql.SqlStatement.of;
import static com.slimgears.rxrepo.sql.StatementUtils.concat;
import static com.slimgears.util.generic.LazyToString.lazy;

public class DefaultSqlStatementProvider implements SqlStatementProvider {
    private final static Logger log = LoggerFactory.getLogger(DefaultSqlStatementProvider.class);

    private final SqlExpressionGenerator sqlExpressionGenerator;
    private final SqlAssignmentGenerator sqlAssignmentGenerator;
    private final SchemaProvider schemaProvider;

    public DefaultSqlStatementProvider(SqlExpressionGenerator sqlExpressionGenerator,
                                       SqlAssignmentGenerator sqlAssignmentGenerator,
                                       SchemaProvider schemaProvider) {
        this.sqlExpressionGenerator = sqlExpressionGenerator;
        this.sqlAssignmentGenerator = sqlAssignmentGenerator;
        this.schemaProvider = schemaProvider;
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>, T> SqlStatement forQuery(QueryInfo<K, S, T> queryInfo) {
        return statement(() -> of(
                selectClause(queryInfo),
                fromClause(queryInfo),
                whereClause(queryInfo),
                orderClause(queryInfo),
                limitClause(queryInfo),
                skipClause(queryInfo)));
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>, T, R> SqlStatement forAggregation(QueryInfo<K, S, T> queryInfo, ObjectExpression<T, R> aggregation, String projectedName) {
        return statement(() -> of(
                selectClause(queryInfo, aggregation, projectedName),
                fromClause(queryInfo),
                whereClause(queryInfo)));
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>> SqlStatement forUpdate(UpdateInfo<K, S> updateInfo) {
        return statement(() -> of(
                "update",
                schemaProvider.tableName(updateInfo.metaClass()),
                "set",
                updateInfo.propertyUpdates()
                        .stream()
                        .map(pu -> concat(sqlExpressionGenerator.toSqlExpression(pu.property()), "=", sqlExpressionGenerator.toSqlExpression(pu.updater())))
                        .collect(Collectors.joining(", ")),
                "return after",
                whereClause(updateInfo),
                limitClause(updateInfo)));
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>> SqlStatement forDelete(DeleteInfo<K, S> deleteInfo) {
        return statement(() -> of(
                "delete",
                fromClause(deleteInfo),
                whereClause(deleteInfo),
                limitClause(deleteInfo)));
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>> SqlStatement forInsertOrUpdate(MetaClassWithKey<K, S> metaClass, PropertyResolver propertyResolver, ReferenceResolver resolver) {
        PropertyMeta<S, K> keyProperty = metaClass.keyProperty();

        return statement(() -> of(
                        "update",
                        schemaProvider.tableName(metaClass),
                        "set",
                        Streams
                                .fromIterable(propertyResolver.propertyNames())
                                .flatMap(sqlAssignmentGenerator.toAssignment(metaClass, propertyResolver, resolver))
                                .collect(Collectors.joining(", ")),
                        "upsert",
                        "return after",
                        "where",
                        toConditionClause(PropertyExpression.ofObject(keyProperty).eq(propertyResolver.getProperty(keyProperty)))
                ));
    }

    @Override
    public <K, S extends HasMetaClassWithKey<K, S>> SqlStatement forInsert(MetaClassWithKey<K, S> metaClass, PropertyResolver propertyResolver, ReferenceResolver resolver) {
        return statement(() -> of(
                        "insert",
                        "into",
                        schemaProvider.tableName(metaClass),
                        "set",
                        Streams
                                .fromIterable(propertyResolver.propertyNames())
                                .flatMap(sqlAssignmentGenerator.toAssignment(metaClass, propertyResolver, resolver))
                                .collect(Collectors.joining(", "))
                ));
    }

    private SqlStatement statement(Supplier<SqlStatement> statementSupplier) {
        List<Object> params = new ArrayList<>();
        SqlStatement statement = sqlExpressionGenerator.withParams(params, statementSupplier::get);
        return statement.withArgs(params.toArray());
    }

    @SuppressWarnings("unchecked")
    private <K, S extends HasMetaClassWithKey<K, S>, T, Q extends HasMapping<S, T> & HasEntityMeta<K, S> & HasProperties<T>> String selectClause(Q queryInfo) {
        ObjectExpression<S, T> expression = Optional
                .ofNullable(queryInfo.mapping())
                .orElse(ObjectExpression.arg((TypeToken)queryInfo.metaClass().objectClass()));

        return Optional.of(toMappingClause(expression, queryInfo.properties()))
                .filter(exp -> !exp.isEmpty())
                .map(exp -> "select " + exp)
                .orElse("select");
    }

    private <K, S extends HasMetaClassWithKey<K, S>, T, R, Q extends HasMapping<S, T> & HasEntityMeta<K, S> & HasProperties<T>> String selectClause(Q statement, ObjectExpression<T, R> aggregation, String projectedName) {
        return concat(
                "select",
                Optional.ofNullable(statement.mapping())
                        .map(exp -> sqlExpressionGenerator.toSqlExpression(aggregation, exp))
                        .orElseGet(() -> sqlExpressionGenerator.toSqlExpression(aggregation)),
                "as",
                projectedName);
    }

    private <S, T> String toMappingClause(ObjectExpression<S, T> expression, Collection<PropertyExpression<T, ?, ?>> properties) {
        return Optionals.or(
                () -> Optional.ofNullable(properties)
                        .filter(p -> !p.isEmpty())
                        .map(this::eliminateRedundantProperties)
                        .map(p -> p.stream()
                                .map(prop -> sqlExpressionGenerator.toSqlExpression(prop, expression))
                                .collect(Collectors.joining(", "))),
                () -> Optional
                        .of(sqlExpressionGenerator.toSqlExpression(expression))
                        .filter(e -> !e.isEmpty()))
                .orElse("");
    }

    private <K, S extends HasMetaClassWithKey<K, S>, Q extends HasEntityMeta<K, S>> String fromClause(Q statement) {
        return "from " + schemaProvider.tableName(statement.metaClass());
    }

    private <S, Q extends HasPredicate<S>> String whereClause(Q statement) {
        return Optional
                .ofNullable(statement.predicate())
                .map(this::toConditionClause)
                .map(cond -> "where " + cond)
                .orElse("");
    }

    private <Q extends HasLimit> String limitClause(Q statement) {
        return Optional.ofNullable(statement.limit())
                .map(count -> "limit " + count)
                .orElse("");
    }

    private <Q extends HasPagination> String skipClause(Q statement) {
        return Optional.ofNullable(statement.skip())
                .map(count -> "skip " + count)
                .orElse("");
    }

    private <T, Q extends HasSortingInfo<T>> String orderClause(Q statement) {
        if (statement.sorting().isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        SortingInfo<T, ?, ?> first = statement.sorting().get(0);
        builder.append("order by ");
        builder.append(toOrder(first));
        statement.sorting().stream().skip(1)
                .forEach(si -> {
                    builder.append(" then by ");
                    builder.append(toOrder(si));
                });
        return builder.toString();
    }

    private String toOrder(SortingInfo<?, ?, ?> sortingInfo) {
        return sqlExpressionGenerator.toSqlExpression(sortingInfo.property()) + (sortingInfo.ascending() ? " asc" : " desc");
    }

    private <S> String toConditionClause(ObjectExpression<S, Boolean> condition) {
        return sqlExpressionGenerator.toSqlExpression(condition);
    }

    private <T> Collection<PropertyExpression<T, ?, ?>> eliminateRedundantProperties(Collection<PropertyExpression<T, ?, ?>> properties) {
        log.trace("Requested properties: {}", lazy(() -> properties.stream()
                .map(sqlExpressionGenerator::toSqlExpression)
                .collect(Collectors.joining(", "))));

        Map<PropertyMeta<?, ?>, PropertyExpression<T, ?, ?>> propertyMap = properties
                .stream()
                .collect(Collectors.toMap(PropertyExpression::property, p -> p, (a, b) -> a, LinkedHashMap::new));

        properties.forEach(p -> eliminateParents(propertyMap, p));

        log.trace("Filtered properties: {}", lazy(() -> propertyMap.values().stream()
                .map(sqlExpressionGenerator::toSqlExpression)
                .collect(Collectors.joining(", "))));

        return propertyMap.values();
    }

    private static <S, T, V> void eliminateParents(Map<PropertyMeta<?, ?>, PropertyExpression<S, ?, ?>> properties, PropertyExpression<S, T, V> property) {
        if (property.target() instanceof PropertyExpression) {
            PropertyExpression<S, ?, ?> parentProperty = (PropertyExpression<S, ?, ?>)property.target();
            properties.remove(parentProperty.property());
            eliminateParents(properties, parentProperty);
        }
    }
}
