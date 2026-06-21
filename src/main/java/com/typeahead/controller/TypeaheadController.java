package com.typeahead.controller;

import com.typeahead.dto.LearnRequest;
import com.typeahead.dto.SuggestionResponse;
import com.typeahead.model.Suggestion;
import com.typeahead.service.TypeaheadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for the typeahead service.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/suggestions?q=&limit=} &mdash; fetch ranked suggestions.</li>
 *   <li>{@code POST /api/v1/terms} &mdash; teach the index a term (online learning).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class TypeaheadController {

    private final TypeaheadService service;

    public TypeaheadController(TypeaheadService service) {
        this.service = service;
    }

    @GetMapping("/suggestions")
    public SuggestionResponse suggestions(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        List<Suggestion> suggestions = service.suggest(query, limit);
        return SuggestionResponse.of(query.trim().toLowerCase(), suggestions);
    }

    @PostMapping("/terms")
    public ResponseEntity<Void> learn(@Valid @RequestBody LearnRequest request) {
        service.learn(request.term(), request.weightOrDefault());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
