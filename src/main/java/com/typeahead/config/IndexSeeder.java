package com.typeahead.config;

import com.typeahead.service.TypeaheadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Warms up the index at startup from {@code resources/seed-terms.tsv}.
 *
 * <p>Each line is {@code <term>\t<frequency>}; lines starting with {@code #}
 * and blank lines are skipped. In production this seed would be replaced by a
 * batch load from the query-log aggregation pipeline (see the HLD document),
 * but a static file keeps the assignment self-contained and reproducible.
 */
@Component
public class IndexSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexSeeder.class);
    private static final String SEED_FILE = "seed-terms.tsv";

    private final TypeaheadService service;

    public IndexSeeder(TypeaheadService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) throws Exception {
        ClassPathResource resource = new ClassPathResource(SEED_FILE);
        if (!resource.exists()) {
            log.warn("Seed file '{}' not found; starting with an empty index", SEED_FILE);
            return;
        }

        int loaded = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (loadLine(line)) {
                    loaded++;
                }
            }
        } catch (IOException e) {
            log.error("Failed to read seed file '{}'", SEED_FILE, e);
            throw e;
        }

        log.info("Seeded typeahead index with {} terms ({} distinct)", loaded, service.indexedTerms());
    }

    private boolean loadLine(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return false;
        }
        String[] parts = line.split("\t");
        String term = parts[0].trim();
        if (term.isEmpty()) {
            return false;
        }
        long weight = 1;
        if (parts.length > 1) {
            try {
                weight = Long.parseLong(parts[1].trim());
            } catch (NumberFormatException e) {
                log.warn("Bad frequency on line '{}', defaulting to 1", rawLine);
            }
        }
        service.learn(term, weight);
        return true;
    }
}
