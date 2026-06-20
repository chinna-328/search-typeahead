package com.typeahead;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Search Typeahead service.
 *
 * <p>The service exposes a small REST API that returns the top-k autocomplete
 * suggestions for a given prefix. Suggestions are ranked by historical query
 * frequency so that more popular terms surface first.
 */
@SpringBootApplication
public class TypeaheadApplication {

    public static void main(String[] args) {
        SpringApplication.run(TypeaheadApplication.class, args);
    }
}
