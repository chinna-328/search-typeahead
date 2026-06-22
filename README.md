# Search Typeahead

A small autocomplete ("search-as-you-type") service built for the **HLD101 –
Search Typeahead** assignment. It returns the top-_k_ most relevant suggestions
for a prefix, ranked by historical query frequency.

The repo contains both deliverables:

- **Working code** – a Spring Boot service backed by an in-memory trie.
- **Design document** – [`docs/HLD-Search-Typeahead.md`](docs/HLD-Search-Typeahead.md),
  covering requirements, architecture, data model, APIs and scaling.
- **Project report (PDF)** – [`docs/Project-Report.pdf`](docs/Project-Report.pdf):
  architecture, dataset, API docs, design trade-offs and a measured performance report.

---

## What it does

- Indexes a corpus of search terms, each with a frequency/weight.
- Serves `GET /api/v1/suggestions?q=<prefix>` returning the best matches,
  ordered by frequency (ties broken alphabetically for determinism).
- Learns new terms at runtime via `POST /api/v1/terms` (online updates).
- Seeds itself at startup from `src/main/resources/seed-terms.tsv`.

## Tech stack

- Java 17, Spring Boot 3.2
- Maven build
- JUnit 5 + Spring MockMvc for tests

## Prerequisites

You need a JDK (17+) and Maven on your `PATH`. They are not currently installed
on this machine:

```bash
# Arch Linux
sudo pacman -S jdk17-openjdk maven
```

## Build & run

```bash
mvn clean test        # compile and run the test suite (18 tests)
mvn spring-boot:run   # start the service on http://localhost:8080
```

## Reproduce the performance numbers

```bash
mvn test-compile
java -Xmx2g -cp target/classes:target/test-classes \
     com.typeahead.bench.PerfBenchmark
```

See [`docs/Project-Report.pdf`](docs/Project-Report.pdf) §5 for the results.

## Try it

```bash
# Top 5 suggestions for "goo"
curl "http://localhost:8080/api/v1/suggestions?q=goo&limit=5"

# Teach the index a new term
curl -X POST http://localhost:8080/api/v1/terms \
     -H 'Content-Type: application/json' \
     -d '{"term":"spring boot actuator","weight":50}'

# Health check
curl http://localhost:8080/actuator/health
```

Example response:

```json
{
  "query": "goo",
  "suggestions": [
    { "term": "google", "score": 9900 },
    { "term": "google maps", "score": 6400 },
    { "term": "google translate", "score": 3800 }
  ]
}
```

## Project layout

```
src/main/java/com/typeahead
├── TypeaheadApplication.java      # Spring Boot entry point
├── trie/                          # Trie + TrieNode (core data structure)
├── model/Suggestion.java          # term + frequency value object
├── service/TypeaheadService.java  # read/write-locked facade over the trie
├── dto/                           # request/response payloads
├── controller/                    # REST endpoints + error handling
└── config/IndexSeeder.java        # loads seed-terms.tsv at startup
```

## Design notes

The interesting decisions (why a trie, how top-_k_ is computed, how this scales
to billions of queries, sharding, caching, and personalization) are written up
in [`docs/HLD-Search-Typeahead.md`](docs/HLD-Search-Typeahead.md).
