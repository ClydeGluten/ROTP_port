package com.github.standobyte.jojo.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Pure domain logic for non-duplicate Stand awakening generations.
 * Allocations cycle through all playable Stands before advancing to the next generation.
 */
public final class StandAllocation {
    public static final List<String> DEFAULT_CATALOG = List.of(
            StandProgress.STAR_PLATINUM,
            StandProgress.THE_WORLD
    );

    public record StandCycle(int generation, List<String> reserved) {
        public static final StandCycle INITIAL = new StandCycle(1, List.of());

        public StandCycle {
            generation = Math.max(1, generation);
            reserved = reserved == null ? List.of() : List.copyOf(reserved);
        }
    }

    public record AllocationResult(String standId, StandCycle newCycle, boolean newGenerationStarted) {
        public AllocationResult {
            Objects.requireNonNull(standId, "standId");
            Objects.requireNonNull(newCycle, "newCycle");
        }
    }

    public static int randomProvisional(int catalogSize, RandomGenerator random) {
        int bound = Math.max(1, catalogSize);
        return random.nextInt(bound) + 1;
    }

    public static int randomProvisional(int catalogSize) {
        return randomProvisional(catalogSize, java.util.concurrent.ThreadLocalRandom.current());
    }

    public static int randomProvisional() {
        return randomProvisional(DEFAULT_CATALOG.size());
    }

    public static AllocationResult allocate(StandCycle current, int provisionalIndex) {
        return allocate(current, provisionalIndex, DEFAULT_CATALOG, java.util.concurrent.ThreadLocalRandom.current());
    }

    public static AllocationResult allocate(StandCycle current, int provisionalIndex, List<String> catalog, RandomGenerator random) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(random, "random");
        List<String> distinctCatalog = sanitizeCatalog(catalog);
        if (distinctCatalog.isEmpty()) {
            throw new IllegalArgumentException("Catalog cannot be empty");
        }

        StandCycle cycle = current == null ? StandCycle.INITIAL : current;
        int generation = Math.max(1, cycle.generation());
        LinkedHashSet<String> validReserved = sanitizeReserved(cycle.reserved(), distinctCatalog);

        boolean newGenerationStarted = false;
        if (validReserved.containsAll(distinctCatalog)) {
            generation = Math.min(Integer.MAX_VALUE - 1, generation) + 1;
            validReserved.clear();
            newGenerationStarted = true;
        }

        List<String> available = new ArrayList<>();
        for (String standId : distinctCatalog) {
            if (!validReserved.contains(standId)) {
                available.add(standId);
            }
        }

        String chosen;
        if (provisionalIndex >= 1 && provisionalIndex <= distinctCatalog.size()) {
            String preferred = distinctCatalog.get(provisionalIndex - 1);
            if (available.contains(preferred)) {
                chosen = preferred;
            } else {
                chosen = available.get(random.nextInt(available.size()));
            }
        } else {
            chosen = available.get(random.nextInt(available.size()));
        }

        validReserved.add(chosen);
        StandCycle newCycle = new StandCycle(generation, List.copyOf(validReserved));
        return new AllocationResult(chosen, newCycle, newGenerationStarted);
    }

    public static StandCycle seed(StandCycle current, String standId) {
        return seed(current, standId, DEFAULT_CATALOG);
    }

    public static StandCycle seed(StandCycle current, String standId, List<String> catalog) {
        if (standId == null || standId.isEmpty() || catalog == null || !catalog.contains(standId)) {
            return current == null ? StandCycle.INITIAL : current;
        }
        List<String> distinctCatalog = sanitizeCatalog(catalog);
        StandCycle cycle = current == null ? StandCycle.INITIAL : current;
        int generation = Math.max(1, cycle.generation());
        LinkedHashSet<String> validReserved = sanitizeReserved(cycle.reserved(), distinctCatalog);

        if (validReserved.contains(standId) || validReserved.containsAll(distinctCatalog)) {
            return new StandCycle(generation, List.copyOf(validReserved));
        }

        validReserved.add(standId);
        return new StandCycle(generation, List.copyOf(validReserved));
    }

    private static List<String> sanitizeCatalog(List<String> catalog) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String id : catalog) {
            if (id != null && !id.isEmpty()) {
                distinct.add(id);
            }
        }
        return List.copyOf(distinct);
    }

    private static LinkedHashSet<String> sanitizeReserved(List<String> reserved, List<String> distinctCatalog) {
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        if (reserved != null) {
            for (String id : reserved) {
                if (id != null && distinctCatalog.contains(id)) {
                    valid.add(id);
                }
            }
        }
        return valid;
    }

    private StandAllocation() { }
}
