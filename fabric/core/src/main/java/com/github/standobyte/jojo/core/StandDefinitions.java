package com.github.standobyte.jojo.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Active ModStandsInit definitions. Known source content and integrated gameplay are separate facts. */
public final class StandDefinitions {
    public enum Kind { ENTITY, NO_SUMMON }
    public enum Acquisition { ARROW, NPC_ENCOUNTER, ANIMAL }

    public record Definition(String id, StandStats stats, Kind kind, Acquisition acquisition,
                             int color, int sourceWeight, int timeStopUnlock, boolean playable) {
        public boolean canStopTime() { return timeStopUnlock >= 0; }
        public boolean naturallyAssignable() { return playable && acquisition == Acquisition.ARROW; }
        public String path() { return id.substring(id.indexOf(':') + 1); }
        public String translationKey() { return "stand.jojo." + path(); }
    }

    // Enable a definition only alongside its complete gameplay, presentation and integration.
    // Source weights remain metadata: the user's generation allocation intentionally uses uniform integers.
    private static final List<Definition> ALL = List.of(
            new Definition("jojo:star_platinum", StandStats.STAR_PLATINUM, Kind.ENTITY, Acquisition.ARROW, 0x8E45FF, 1, 4, true),
            new Definition("jojo:the_world", StandStats.THE_WORLD, Kind.ENTITY, Acquisition.ARROW, 0xFFD800, 1, 2, true),
            new Definition("jojo:hierophant_green", StandStats.HIEROPHANT_GREEN, Kind.ENTITY, Acquisition.ARROW, 0x00B319, 2, -1, true),
            new Definition("jojo:silver_chariot", StandStats.SILVER_CHARIOT, Kind.ENTITY, Acquisition.ARROW, 0xBEC8D6, 2, -1, true),
            new Definition("jojo:magicians_red", StandStats.MAGICIANS_RED, Kind.ENTITY, Acquisition.ARROW, 0xDE203A, 2, -1, true),
            new Definition("jojo:crazy_diamond", StandStats.CRAZY_DIAMOND, Kind.ENTITY, Acquisition.ARROW, 0x4BEDE6, 2, -1, false),
            new Definition("jojo:gold_experience", StandStats.GOLD_EXPERIENCE, Kind.ENTITY, Acquisition.ARROW, 0xD7933B, 2, -1, false),
            new Definition("jojo:boy_ii_man", StandStats.EMPTY, Kind.NO_SUMMON, Acquisition.NPC_ENCOUNTER, 0x749FA5, 2, -1, false),
            new Definition("jojo:mr_president", StandStats.MR_PRESIDENT, Kind.NO_SUMMON, Acquisition.ANIMAL, 0x00AFAF, 2, -1, false));
    private static final Map<String, Definition> BY_ID = ALL.stream().collect(Collectors.toUnmodifiableMap(Definition::id, Function.identity()));

    public static List<Definition> all() { return ALL; }
    public static Optional<Definition> find(String id) { return Optional.ofNullable(BY_ID.get(id)); }
    public static Definition definition(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown Stand: " + id));
    }
    public static List<String> naturalPool() {
        return ALL.stream().filter(Definition::naturallyAssignable).map(Definition::id).toList();
    }
    private StandDefinitions() { }
}
