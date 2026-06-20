package com.typeahead.trie;

import java.util.HashMap;
import java.util.Map;

/**
 * A single node in the prefix tree.
 *
 * <p>We use a {@link HashMap} keyed by character instead of a fixed-size array
 * so the trie can hold the full Unicode/alphanumeric range that real search
 * queries contain (digits, accented characters, spaces) without wasting memory
 * on a 26-slot array per node.
 */
class TrieNode {

    /** Child nodes, keyed by the next character of the term. */
    final Map<Character, TrieNode> children = new HashMap<>();

    /** True when a complete term ends at this node. */
    boolean isTerminal;

    /**
     * The full term that ends here. Cached on the terminal node so we do not
     * have to rebuild the string by walking back up the tree during collection.
     */
    String term;

    /** Number of times this term has been queried. Used for ranking. */
    long frequency;
}
