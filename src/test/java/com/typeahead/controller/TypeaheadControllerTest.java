package com.typeahead.controller;

import com.typeahead.service.TypeaheadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TypeaheadControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TypeaheadService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        // The index is already seeded from seed-terms.tsv at startup.
    }

    @Test
    void returnsRankedSuggestionsForPrefix() throws Exception {
        mockMvc.perform(get("/api/v1/suggestions").param("q", "goog").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query", is("goog")))
                .andExpect(jsonPath("$.suggestions", hasSize(3)))
                // "google" (9900) outranks "google maps" (6400)
                .andExpect(jsonPath("$.suggestions[0].term", is("google")));
    }

    @Test
    void clampsLimitToMax() throws Exception {
        mockMvc.perform(get("/api/v1/suggestions").param("q", "i").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(10)));
    }

    @Test
    void unknownPrefixReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/suggestions").param("q", "zzzzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(0)));
    }

    @Test
    void missingQueryParamIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/suggestions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("missing required parameter: q")));
    }

    @Test
    void learnEndpointAddsTermAndItBecomesSearchable() throws Exception {
        String body = "{\"term\":\"zebra crossing\",\"weight\":42}";
        mockMvc.perform(post("/api/v1/terms").contentType("application/json").content(body))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/suggestions").param("q", "zebra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].term", is("zebra crossing")))
                .andExpect(jsonPath("$.suggestions[0].score", is(42)));
    }

    @Test
    void learnRejectsBlankTerm() throws Exception {
        String body = "{\"term\":\"   \"}";
        mockMvc.perform(post("/api/v1/terms").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }
}
