package com.typeahead.dto;

import com.typeahead.model.Suggestion;

import java.util.List;

/**
 * Response body for a suggestion lookup.
 *
 * @param query       the (normalized) prefix that was queried
 * @param suggestions ordered list of matching terms
 */
public record SuggestionResponse(String query, List<Item> suggestions) {

    /** A single ranked entry returned to the client. */
    public record Item(String term, long score) {
        static Item from(Suggestion s) {
            return new Item(s.term(), s.frequency());
        }
    }

    public static SuggestionResponse of(String query, List<Suggestion> suggestions) {
        List<Item> items = suggestions.stream().map(Item::from).toList();
        return new SuggestionResponse(query, items);
    }
}
