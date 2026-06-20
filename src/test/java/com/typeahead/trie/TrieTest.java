package com.typeahead.trie;

import com.typeahead.model.Suggestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrieTest {

    @Test
    void returnsTermsOrderedByFrequency() {
        Trie trie = new Trie();
        trie.insert("apple", 5);
        trie.insert("application", 12);
        trie.insert("apply", 3);

        List<Suggestion> result = trie.topK("app", 10);

        assertEquals(
                List.of("application", "apple", "apply"),
                result.stream().map(Suggestion::term).toList());
    }

    @Test
    void respectsTheKLimit() {
        Trie trie = new Trie();
        trie.insert("car", 1);
        trie.insert("card", 2);
        trie.insert("care", 3);
        trie.insert("cargo", 4);

        List<Suggestion> result = trie.topK("car", 2);

        assertEquals(2, result.size());
        assertEquals(List.of("cargo", "care"), result.stream().map(Suggestion::term).toList());
    }

    @Test
    void breaksFrequencyTiesAlphabetically() {
        Trie trie = new Trie();
        trie.insert("banana", 4);
        trie.insert("band", 4);
        trie.insert("bandana", 4);

        List<Suggestion> result = trie.topK("ban", 3);

        assertEquals(
                List.of("banana", "band", "bandana"),
                result.stream().map(Suggestion::term).toList());
    }

    @Test
    void lookupIsCaseInsensitive() {
        Trie trie = new Trie();
        trie.insert("GitHub", 7);

        List<Suggestion> upper = trie.topK("GIT", 5);
        List<Suggestion> lower = trie.topK("git", 5);

        assertEquals(1, upper.size());
        assertEquals("github", upper.get(0).term());
        assertEquals(upper, lower);
    }

    @Test
    void insertingSameTermAccumulatesFrequency() {
        Trie trie = new Trie();
        trie.insert("query");
        trie.insert("query");
        trie.insert("query", 3);

        List<Suggestion> result = trie.topK("que", 1);

        assertEquals(1, trie.size());
        assertEquals(5, result.get(0).frequency());
    }

    @Test
    void unknownPrefixReturnsEmpty() {
        Trie trie = new Trie();
        trie.insert("hello");

        assertTrue(trie.topK("xyz", 5).isEmpty());
    }

    @Test
    void nonPositiveKReturnsEmpty() {
        Trie trie = new Trie();
        trie.insert("hello");

        assertTrue(trie.topK("he", 0).isEmpty());
        assertTrue(trie.topK("he", -1).isEmpty());
    }

    @Test
    void blankAndNullTermsAreIgnored() {
        Trie trie = new Trie();
        trie.insert("   ");
        trie.insert(null);
        trie.insert("ok", 0); // zero weight is ignored

        assertEquals(0, trie.size());
    }
}
