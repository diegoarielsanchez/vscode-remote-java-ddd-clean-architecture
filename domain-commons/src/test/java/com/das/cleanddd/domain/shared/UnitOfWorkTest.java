package com.das.cleanddd.domain.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

class UnitOfWorkTest {

    @Test
    void immediateRunsTheWorkAndReturnsItsResult() throws DomainException {
        assertEquals(42, UnitOfWork.immediate().execute(() -> 42));
    }

    @Test
    void runExecutesVoidWorkAndPropagatesDomainExceptions() throws DomainException {
        List<String> calls = new ArrayList<>();
        UnitOfWork.immediate().run(() -> calls.add("ran"));
        assertEquals(List.of("ran"), calls);
        assertThrows(DomainException.class, () -> UnitOfWork.immediate().run(() -> {
            throw new DomainException("boom");
        }));
    }
}
