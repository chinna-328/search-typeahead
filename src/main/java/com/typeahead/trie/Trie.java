package com.typeahead.trie;

import com.typeahead.model.Suggestion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A prefix tree (trie) that backs the autocomplete index.
 *
 * <p>Terms are stored lower-cased so that lookups are case-insensitive. Each
 * terminal node keeps a frequency counter; {@link #topK(String, int)} returns
 * the most frequent terms under a prefix, breaking ties alphabetically so the
 * output is deterministic.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>insert: O(L) where L is the length of the term.</li>
 *   <li>topK: O(P + M log k) where P is the prefix length and M is the number
 *       of terms sharing that prefix.</li>
 * </ul>
 *
 * <p>This class is <strong>not</strong> thread-safe on its own. The service
 * layer guards concurrent access (see {@code TypeaheadService}); see the HLD
 * document for the production sharding/locking strategy.
 */
public class Trie {

    private final TrieNode root = new TrieNode();
    private int size;

    /**
     * Inserts a term, or increases its frequency by {@code weight} if it is
     * already present.
     *
     * @param rawTerm the term to index; blank terms are ignored
     * @param weight  how much to add to the frequency (must be positive)
     */
    public void insert(String rawTerm, long weight) {
        if (rawTerm == null) {
            return;
        }
        String term = normalize(rawTerm);
        if (term.isEmpty() || weight <= 0) {
            return;
        }

        TrieNode node = root;
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        if (!node.isTerminal) {
            node.isTerminal = true;
            node.term = term;
            size++;
        }
        node.frequency += weight;
    }

    /** Inserts a term with the default weight of one. */
    public void insert(String term) {
        insert(term, 1);
    }

    /**
     * Returns up to {@code k} suggestions for the given prefix, ordered by
     * descending frequency and then ascending term.
     *
     * @param rawPrefix the prefix typed by the user
     * @param k         the maximum number of suggestions to return
     * @return an ordered list of suggestions; empty if nothing matches
     */
    public List<Suggestion> topK(String rawPrefix, int k) {
        if (k <= 0) {
            return List.of();
        }
        String prefix = normalize(rawPrefix);

        TrieNode node = root;
        for (int i = 0; i < prefix.length(); i++) {
            node = node.children.get(prefix.charAt(i));
            if (node == null) {
                return List.of();
            }
        }

        // A min-heap of size k keeps memory bounded even when many terms share
        // the prefix: we only ever hold the current best k candidates.
        Comparator<Suggestion> worstFirst = Comparator
                .comparingLong(Suggestion::frequency)
                .thenComparing(Suggestion::term, Comparator.reverseOrder());
        PriorityQueue<Suggestion> heap = new PriorityQueue<>(worstFirst);

        collect(node, heap, k);

        List<Suggestion> result = new ArrayList<>(heap);
        result.sort(worstFirst.reversed());
        return result;
    }

    /** Depth-first walk that feeds terminal nodes into the bounded heap. */
    private void collect(TrieNode node, PriorityQueue<Suggestion> heap, int k) {
        if (node.isTerminal) {
            offer(heap, new Suggestion(node.term, node.frequency), k);
        }
        for (TrieNode child : node.children.values()) {
            collect(child, heap, k);
        }
    }

    private void offer(PriorityQueue<Suggestion> heap, Suggestion candidate, int k) {
        if (heap.size() < k) {
            heap.offer(candidate);
        } else if (heap.peek() != null && isBetter(candidate, heap.peek())) {
            heap.poll();
            heap.offer(candidate);
        }
    }

    private boolean isBetter(Suggestion a, Suggestion b) {
        if (a.frequency() != b.frequency()) {
            return a.frequency() > b.frequency();
        }
        // Lower (alphabetically earlier) term wins on a tie.
        return a.term().compareTo(b.term()) < 0;
    }

    /** Number of distinct terms currently indexed. */
    public int size() {
        return size;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase();
    }
}
