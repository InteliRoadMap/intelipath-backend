package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The retrieval filter is a privacy boundary: transcripts of every student share one
 * vector store, so an unfiltered search would feed one student's grades into another
 * student's answer. A broken boundary here returns more data rather than an error, so
 * it cannot be caught by hand.
 */
class VirtualMentorRetrievalFilterTest {

    private static User userWithId(UUID id) {
        return User.builder().userId(id).build();
    }

    @Test
    void retrievesGlobalKnowledgeAndTheCallersOwnDocumentsOnly() {
        UUID userId = UUID.randomUUID();

        Filter.Expression expression = VirtualMentorServiceImpl.retrievableBy(userWithId(userId));

        assertEquals(Filter.ExpressionType.OR, expression.type());

        Filter.Expression global = (Filter.Expression) expression.left();
        assertEquals(Filter.ExpressionType.EQ, global.type());
        assertEquals("scope", ((Filter.Key) global.left()).key());
        assertEquals("GLOBAL", ((Filter.Value) global.right()).value());

        Filter.Expression own = (Filter.Expression) expression.right();
        assertEquals(Filter.ExpressionType.EQ, own.type());
        assertEquals("userId", ((Filter.Key) own.left()).key());
        assertEquals(userId.toString(), ((Filter.Value) own.right()).value());
    }

    @Test
    void scopesTheFilterToTheCallerSoOneStudentCannotMatchAnother() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        Filter.Expression forAlice = VirtualMentorServiceImpl.retrievableBy(userWithId(alice));
        Filter.Expression forBob = VirtualMentorServiceImpl.retrievableBy(userWithId(bob));

        assertNotEquals(forAlice, forBob);
        assertEquals(alice.toString(), ((Filter.Value) ((Filter.Expression) forAlice.right()).right()).value());
        assertEquals(bob.toString(), ((Filter.Value) ((Filter.Expression) forBob.right()).right()).value());
    }
}
