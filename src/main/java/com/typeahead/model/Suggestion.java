package com.typeahead.model;

/**
 * A single autocomplete suggestion together with the frequency that earned it
 * a place in the ranking.
 */
public record Suggestion(String term, long frequency) {
}
