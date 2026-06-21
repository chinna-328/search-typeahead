package com.typeahead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request body for teaching the index a new (or repeated) search term.
 *
 * @param term   the search term to learn; must not be blank
 * @param weight how much to add to the term's frequency; defaults to 1
 */
public record LearnRequest(
        @NotBlank String term,
        @Positive Long weight) {

    public long weightOrDefault() {
        return weight == null ? 1L : weight;
    }
}
