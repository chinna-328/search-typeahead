package com.typeahead.service;

import com.typeahead.model.Suggestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeaheadServiceTest {

    private TypeaheadService newService(int maxLimit) {
        return new TypeaheadService(maxLimit);
    }

    @Test
    void clampsLimitToConfiguredMaximum() {
        TypeaheadService service = newService(2);
        service.learn("alpha", 5);
        service.learn("alphabet", 4);
        service.learn("alpine", 3);

        List<Suggestion> result = service.suggest("al", 50);

        assertEquals(2, result.size(), "limit should be clamped to max-limit");
    }

    @Test
    void nonPositiveLimitFallsBackToOne() {
        TypeaheadService service = newService(10);
        service.learn("alpha", 5);
        service.learn("alphabet", 4);

        assertEquals(1, service.suggest("al", 0).size());
        assertEquals(1, service.suggest("al", -3).size());
    }

    @Test
    void learnAddsAndRanksTerms() {
        TypeaheadService service = newService(10);
        service.learn("google", 10);
        service.learn("gmail", 20);

        List<Suggestion> result = service.suggest("g", 10);

        assertEquals(List.of("gmail", "google"), result.stream().map(Suggestion::term).toList());
        assertEquals(2, service.indexedTerms());
    }

    @Test
    void unknownPrefixReturnsEmptyList() {
        TypeaheadService service = newService(10);
        service.learn("hello", 1);

        assertTrue(service.suggest("zzz", 5).isEmpty());
    }
}
