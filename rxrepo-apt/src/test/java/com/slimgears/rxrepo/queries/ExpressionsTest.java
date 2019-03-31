package com.slimgears.rxrepo.queries;

import com.slimgears.rxrepo.expressions.ObjectExpression;
import com.slimgears.rxrepo.util.Expressions;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.function.Function;

public class ExpressionsTest {
    private final TestEntity testEntity1 = TestEntity.builder()
            .number(3)
            .text("Entity 1")
            .refEntity(TestRefEntity
                    .builder()
                    .text("Description 1")
                    .id(10)
                    .build())
            .keyName("Key 1")
            .refEntities(Collections.emptyList())
            .build();

    private final TestEntity testEntity2 = TestEntity.builder()
            .number(8)
            .text("Entity 2")
            .refEntity(TestRefEntity
                    .builder()
                    .text("Description 2")
                    .id(10)
                    .build())
            .keyName("Key 2")
            .refEntities(Collections.emptyList())
            .build();

    @Test
    public void testPropertyExpressionCompile() {
        String description = Expressions.compile(TestEntity.$.text).apply(testEntity1);
        Assert.assertEquals(testEntity1.text(), description);
    }

    @Test
    public void testReferencePropertyExpressionCompile() {
        ObjectExpression<TestEntity, String> exp = TestEntity.$.refEntity.text;
        String description = Expressions.compile(exp).apply(testEntity1);
        Assert.assertEquals(description, testEntity1.refEntity().text());
    }

    @Test
    public void testComparisonExpression() {
        Function<TestEntity, Boolean> exp = Expressions.compile(TestEntity.$.text.length().eq(TestEntity.$.number));
        Assert.assertFalse(exp.apply(testEntity1));
        Assert.assertTrue(exp.apply(testEntity2));
    }

    @Test
    public void testMathExpression() {
        Function<TestEntity, Integer> exp = Expressions
                .compile(TestEntity.$.number
                        .add(TestEntity.$.text.length())
                        .add(TestEntity.$.refEntity.id)
                        .mul(100)
                        .div(5));

        Assert.assertEquals(Integer.valueOf(420), exp.apply(testEntity1));
        Assert.assertEquals(Integer.valueOf(520), exp.apply(testEntity2));
    }
}