package com.typeahead.service;

import com.typeahead.model.Suggestion;
import com.typeahead.trie.Trie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Application-level facade over the {@link Trie}.
 *
 * <p>Reads (suggestion lookups) vastly outnumber writes (learning a new query),
 * so access is guarded by a {@link ReadWriteLock}: any number of concurrent
 * lookups are allowed, while a write briefly takes the queue exclusively. This
 * keeps the hot path cheap without giving up correctness when the index is
 * updated at runtime.
 */
@Service
public class TypeaheadService {

    private final Trie trie = new Trie();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final int maxLimit;

    public TypeaheadService(@Value("${typeahead.max-limit:10}") int maxLimit) {
        this.maxLimit = maxLimit;
    }

    /**
     * Returns the top suggestions for {@code prefix}.
     *
     * @param prefix the user's partial input
     * @param limit  requested number of suggestions; clamped to the configured max
     */
    public List<Suggestion> suggest(String prefix, int limit) {
        int effectiveLimit = clamp(limit);
        lock.readLock().lock();
        try {
            return trie.topK(prefix, effectiveLimit);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Records that {@code term} was searched, adding it to the index or bumping
     * its frequency. Called both by the seeding job and by the learn endpoint.
     */
    public void learn(String term, long weight) {
        lock.writeLock().lock();
        try {
            trie.insert(term, weight);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Number of distinct terms currently indexed. */
    public int indexedTerms() {
        lock.readLock().lock();
        try {
            return trie.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    private int clamp(int limit) {
        if (limit <= 0) {
            return 1;
        }
        return Math.min(limit, maxLimit);
    }
}
