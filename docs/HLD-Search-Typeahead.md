# HLD101 — Search Typeahead: High-Level Design

---

## 1. Problem statement

Design a **search typeahead** (autocomplete) system. As a user types a prefix
into a search box, the system suggests the most likely complete queries in real
time. Think of the dropdown under the Google / Amazon / YouTube search bar.

The goal of this assignment is the *design* (data structures, APIs, scaling),
backed by a working reference implementation of the core engine.

## 2. Requirements

### 2.1 Functional

- Given a prefix, return up to **k** (default 10) suggestions.
- Suggestions are **ranked** — the most popular/likely completions come first.
- Matching is on the **prefix** of the query string and is **case-insensitive**.
- The system can **learn** new and trending terms over time (a query that
  becomes popular should start appearing).
- Results must be **deterministic** for the same index state (stable ordering).

### 2.2 Non-functional

| Concern        | Target                                                            |
|----------------|-------------------------------------------------------------------|
| Latency        | p99 < ~100 ms end-to-end; the lookup itself in single-digit ms.   |
| Throughput     | Read-heavy: ~10⁵–10⁶ QPS at large scale.                          |
| Availability   | 99.9%+; stale-but-up is preferable to down (suggestions are a hint, not truth). |
| Freshness      | New/trending terms reflected within minutes, not seconds.         |
| Scale          | 10s of millions of distinct terms; billions of raw queries/day.   |

### 2.3 Explicit non-goals

- Returning the actual search *results* — we only autocomplete the query box.
- Full-text / fuzzy / typo-tolerant matching (noted as an extension in §9).
- Spelling correction and semantic search.

## 3. Back-of-the-envelope estimates

- Assume **5 billion** searches/day ≈ **60k QPS** average, with peaks ~3× → ~180k QPS.
- Each keystroke can fire a request; with debouncing assume ~4 lookups per search
  → autocomplete read QPS is several × the search QPS. Reads dominate massively.
- Distinct terms worth indexing: ~**10–50 million** after thresholding out the
  long tail of one-off queries.
- A term averaging ~20 bytes + frequency + trie overhead → on the order of a few
  GB of index. **This fits in memory on a single beefy node**, which is the key
  insight: keep the hot index in RAM and replicate it for read scale.

## 4. Core data structure: the Trie

A **trie (prefix tree)** is the natural fit because the access pattern *is*
"walk down a prefix":

```
            (root)
           /   |   \
          g    i    s
          |    |    |
          o    p    a ...
          |    |
          o    h(one)  -> "iphone"     freq 9500
          |
          g(le) -> "google"  freq 9900
             \
              ( maps ) -> "google maps" freq 6400
```

- **Insert** a term of length L: O(L).
- **Lookup**: walk L characters to the prefix node, then gather completions in
  its subtree.

### 4.1 Ranking & top-k

Each terminal node stores a **frequency**. For a prefix we must return the top-k
completions by frequency. Two strategies:

1. **Collect-then-rank (this implementation).** DFS the subtree under the prefix
   node, push every completion into a **bounded min-heap of size k**, and return
   the heap sorted. Cost: `O(M + M·log k)` where M = number of terms under the
   prefix. Simple, exact, no extra memory in the tree. Good for an assignment and
   for moderate fan-out.

2. **Precomputed top-k cache (production optimization).** Store the top-k
   completions *at each node*. A lookup then walks to the prefix node and returns
   its cached list in `O(L + k)` — independent of how many terms share the prefix.
   The cost moves to write time (updating caches up the path) and memory
   (k entries per node). This is what large systems do for the hot path.

The reference implementation uses strategy (1) and documents (2) as the scaling
path. Ties on frequency are broken **alphabetically** so output is deterministic.

### 4.2 Why not alternatives?

- **Hash map of prefix → list**: blows up storage (every prefix of every term)
  and is awkward to update.
- **Sorted array + binary search**: range for a prefix is easy, but ranking by
  frequency within the range and incremental updates are costly.
- **Ternary search tree**: more memory-efficient than a hash-child trie but more
  complex; a reasonable alternative, not chosen here for clarity.

## 5. High-level architecture

```
        ┌────────────┐    debounced GET /suggestions?q=
 Client │  Browser   │ ───────────────────────────────►  ┌──────────────┐
        └────────────┘                                    │  API Gateway  │
                                                          │  / LB + CDN   │
                                                          └──────┬───────┘
                                                                 │ (fan-out by shard)
                                              ┌──────────────────┼──────────────────┐
                                              ▼                  ▼                  ▼
                                       ┌────────────┐    ┌────────────┐     ┌────────────┐
                                       │ Suggest svc│    │ Suggest svc│ ... │ Suggest svc│
                                       │ (in-mem    │    │  replica   │     │  shard N   │
                                       │  trie)     │    └────────────┘     └────────────┘
                                       └─────┬──────┘
                                             │ periodic load of immutable index snapshot
                                             ▼
                                    ┌───────────────────┐
                                    │  Index snapshots  │  (object store: S3/GCS)
                                    └─────────▲─────────┘
                                              │ build new snapshot every few minutes
                                    ┌─────────┴─────────┐
                                    │  Index builder    │  (batch/stream job)
                                    └─────────▲─────────┘
                                              │ aggregated (term, count)
                                    ┌─────────┴─────────┐
                                    │ Query-log pipeline│  (Kafka → aggregation)
                                    └─────────▲─────────┘
                                              │ raw query events
                                        every user search
```

**Read path (hot, synchronous):** Client debounces keystrokes (~150 ms) and
calls the suggestion service through a CDN/gateway. The service answers from its
**in-memory trie** — no database on the request path.

**Write/learn path (cold, asynchronous):** Every search emits an event to a log
pipeline (e.g. Kafka). A batch/stream **index builder** aggregates counts,
applies thresholds/decay, builds a fresh **immutable snapshot**, and publishes it
to object storage. Suggestion nodes periodically pull and hot-swap the new
snapshot. This separates the read path (must be fast) from freshness updates
(can lag by minutes) — eventual consistency is acceptable for autocomplete.

The reference implementation collapses this into one process: the `IndexSeeder`
plays the role of "load a snapshot" and `POST /api/v1/terms` plays the role of
"learn an event," so the same engine demonstrates both paths.

## 6. API design

### `GET /api/v1/suggestions`

| Param   | Type   | Default | Notes                                   |
|---------|--------|---------|-----------------------------------------|
| `q`     | string | —       | Required. The prefix.                   |
| `limit` | int    | 10      | Clamped to `typeahead.max-limit` (10).  |

```json
200 OK
{
  "query": "goo",
  "suggestions": [
    { "term": "google", "score": 9900 },
    { "term": "google maps", "score": 6400 }
  ]
}
```

### `POST /api/v1/terms`  (online learning)

```json
{ "term": "spring boot actuator", "weight": 50 }
// 202 Accepted
```

Errors use a consistent body: `400` for a missing `q` or a blank `term`.

## 7. Scaling strategy

- **Replicate for reads.** The index is read-mostly and fits in RAM, so run many
  stateless replicas behind a load balancer. Reads scale horizontally and
  linearly.
- **Shard for size.** If the term set outgrows one box, shard by **first
  character / prefix range** (e.g. a–c on shard 1). The gateway routes a query to
  the owning shard by its leading characters. Hot shards (e.g. common letters)
  get more replicas.
- **Cache the hottest prefixes.** A CDN / edge cache on `(q, limit)` absorbs the
  enormous skew — a tiny set of prefixes accounts for most traffic. Short TTL
  (seconds–minutes) keeps it fresh enough.
- **Immutable snapshots** make replica scaling trivial: a new node just downloads
  the latest snapshot and serves; no live coordination needed.

## 8. Data lifecycle: trending & decay

- Aggregate raw query counts in the pipeline; **threshold** out the long tail so
  the index stays at "interesting" terms.
- Apply **time decay** (e.g. exponentially weighted counts) so yesterday's spike
  fades and genuinely trending terms rise. This is why frequency is a *weight*,
  not a raw lifetime count.
- **Safety filtering**: block disallowed/offensive completions in the builder,
  before they ever reach a snapshot.

## 9. Trade-offs & future extensions

- **Exact prefix only.** Typo tolerance / fuzzy matching would need edit-distance
  search (e.g. a Levenshtein automaton over the trie) — deliberately out of scope.
- **Personalization & context** (location, language, user history) would move
  ranking from a single global frequency to a scoring function; the API shape
  stays the same.
- **Top-k node cache** (§4.1.2) is the first optimization to add if collect-then-
  rank fan-out becomes the bottleneck.
- **Concurrency**: the reference uses a `ReadWriteLock` for live updates. The
  production design sidesteps locks entirely by swapping *immutable* snapshots —
  readers never block.

## 10. Summary

A trie in memory, replicated for reads and sharded for size, fed by an
asynchronous query-log pipeline that publishes immutable snapshots, with a thin
CDN cache over the hottest prefixes. The request path touches only RAM, giving
single-digit-millisecond lookups; freshness is handled off the hot path where a
few minutes of lag is acceptable. The code in this repo implements the core
engine (trie, ranking, top-k, REST API, online learning) and the tests pin down
the ranking and API contract.
