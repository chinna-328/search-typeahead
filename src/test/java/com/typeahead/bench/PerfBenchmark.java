package com.typeahead.bench;

import com.typeahead.trie.Trie;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Standalone latency/throughput benchmark for the trie engine.
 *
 * <p>Not a unit test — run it explicitly to reproduce the numbers in the
 * performance report:
 * <pre>
 *   mvn test-compile
 *   java -cp target/classes:target/test-classes com.typeahead.bench.PerfBenchmark
 * </pre>
 *
 * <p>It builds an index of synthetic terms (drawn from a fixed vocabulary so
 * prefixes are shared, like real query logs), then measures {@code topK}
 * latency over a large number of random prefix lookups and reports the
 * distribution.
 */
public final class PerfBenchmark {

    private static final int TERMS = 1_000_000;
    private static final int LOOKUPS = 200_000;
    private static final int K = 10;
    private static final long SEED = 42L;

    public static void main(String[] args) {
        Random rnd = new Random(SEED);
        String[] vocab = buildVocabulary(rnd);

        // ---- Build phase ----
        List<String> terms = new ArrayList<>(TERMS);
        for (int i = 0; i < TERMS; i++) {
            terms.add(randomTerm(rnd, vocab));
        }

        long usedBefore = usedHeapBytes();
        long t0 = System.nanoTime();
        Trie trie = new Trie();
        for (String term : terms) {
            trie.insert(term, 1 + rnd.nextInt(10_000));
        }
        long buildMs = (System.nanoTime() - t0) / 1_000_000;
        long usedAfter = usedHeapBytes();

        System.out.println("=== Search Typeahead — Trie benchmark ===");
        System.out.println("JVM            : " + System.getProperty("java.version"));
        System.out.println("Distinct terms : " + trie.size());
        System.out.println("Index build    : " + buildMs + " ms ("
                + (TERMS / Math.max(buildMs, 1)) + "k inserts/sec)");
        System.out.println("Index heap     : ~" + ((usedAfter - usedBefore) / (1024 * 1024)) + " MB");
        System.out.println();

        // Two regimes: broad (2-3 char) prefixes match a large subtree and are
        // the worst case for collect-then-rank; selective (>=6 char) prefixes
        // are what a user typically has on screen when they pick a suggestion.
        measure(trie, terms, rnd, "BROAD prefixes (2-3 chars, worst case)", 2, 3);
        measure(trie, terms, rnd, "SELECTIVE prefixes (6-10 chars, typical)", 6, 10);
    }

    private static void measure(Trie trie, List<String> terms, Random rnd,
                                String label, int minLen, int maxLen) {
        String[] prefixes = new String[LOOKUPS];
        for (int i = 0; i < LOOKUPS; i++) {
            String t = terms.get(rnd.nextInt(terms.size()));
            int len = Math.min(t.length(), minLen + rnd.nextInt(maxLen - minLen + 1));
            prefixes[i] = t.substring(0, len);
        }

        // Warm up the JIT for this access pattern.
        for (int i = 0; i < 20_000; i++) {
            trie.topK(prefixes[i % prefixes.length], K);
        }

        long[] latNs = new long[LOOKUPS];
        long blackhole = 0;
        long mStart = System.nanoTime();
        for (int i = 0; i < LOOKUPS; i++) {
            long s = System.nanoTime();
            blackhole += trie.topK(prefixes[i], K).size();
            latNs[i] = System.nanoTime() - s;
        }
        long totalMs = (System.nanoTime() - mStart) / 1_000_000;
        java.util.Arrays.sort(latNs);

        System.out.println("--- " + label + " ---");
        System.out.println("Lookups        : " + LOOKUPS + " (k=" + K + ")");
        System.out.println("Throughput     : " + (LOOKUPS * 1000L / Math.max(totalMs, 1))
                + " lookups/sec (single thread)");
        System.out.println("Latency  mean  : " + us(mean(latNs)) + " us");
        System.out.println("Latency  p50   : " + us(latNs[(int) (LOOKUPS * 0.50)]) + " us");
        System.out.println("Latency  p95   : " + us(latNs[(int) (LOOKUPS * 0.95)]) + " us");
        System.out.println("Latency  p99   : " + us(latNs[(int) (LOOKUPS * 0.99)]) + " us");
        System.out.println("Latency  max   : " + us(latNs[LOOKUPS - 1]) + " us");
        System.out.println("(checksum " + blackhole + ")");
        System.out.println();
    }

    private static String[] buildVocabulary(Random rnd) {
        // A few hundred word-fragments; concatenated they yield millions of
        // shared-prefix terms, mimicking the skew of a real query log.
        String[] roots = {
                "tech", "news", "buy", "best", "cheap", "online", "free", "how",
                "what", "weather", "movie", "song", "game", "phone", "laptop",
                "shoes", "flight", "hotel", "food", "recipe", "java", "python",
                "spring", "react", "node", "data", "cloud", "city", "stock",
                "price", "review", "near", "me", "today", "live", "score",
        };
        List<String> v = new ArrayList<>();
        for (String a : roots) {
            v.add(a);
            for (String b : roots) {
                v.add(a + " " + b);
            }
        }
        return v.toArray(new String[0]);
    }

    private static String randomTerm(Random rnd, String[] vocab) {
        String base = vocab[rnd.nextInt(vocab.length)];
        // Append a short suffix to inflate the distinct-term count.
        return base + " " + Integer.toString(rnd.nextInt(36 * 36), 36);
    }

    private static long usedHeapBytes() {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return rt.totalMemory() - rt.freeMemory();
    }

    private static double mean(long[] xs) {
        long sum = 0;
        for (long x : xs) {
            sum += x;
        }
        return (double) sum / xs.length;
    }

    private static String us(double ns) {
        return String.format("%.2f", ns / 1000.0);
    }

    private PerfBenchmark() {
    }
}
