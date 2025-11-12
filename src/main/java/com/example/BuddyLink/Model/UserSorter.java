package com.example.BuddyLink.Model;

import java.util.Comparator;
import java.util.List;

/**
 * Sort users by:
 * 1) Most recently contacted
 * 2) Alphabetical (case-insensitive)
 * 3) Relevance (shared liked subjects)
 */
public final class UserSorter {

    private UserSorter() {}

    public enum Mode { RECENT, ALPHA, RELEVANCE }

    public interface UserView {
        int getUserId();
        String getName();
        boolean[] getSubjects();
        Long getLastContactTs(); // nullable epoch millis
    }

    /** Sorts in-place by the given mode. */
    public static <T extends UserView> void sort(List<T> users, Mode mode, T currentUser) {
        if (users == null || users.size() <= 1) return;

        Comparator<T> cmp = switch (mode) {
            case RECENT -> mostRecentComparator();
            case ALPHA -> alphaComparator();
            case RELEVANCE -> {
                Comparator<T> rel = relevanceComparator(currentUser == null ? null : currentUser.getSubjects());
                // after relevance, fallback to recent then alpha for stable ordering
                yield rel.thenComparing(mostRecentComparator()).thenComparing(alphaComparator());
            }
        };

        users.sort(cmp);
    }

    // ----------------- COMPARATORS -----------------

    /** Newest first; null timestamps go last; tie-break by name A→Z then id. */
    private static <T extends UserView> Comparator<T> mostRecentComparator() {
        return Comparator
                .comparing((T u) -> u.getLastContactTs(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing((T u) -> safeName(u), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(u -> u.getUserId());
    }

    /** Alphabetical by case-insensitive name; tie-break by id. */
    private static <T extends UserView> Comparator<T> alphaComparator() {
        return Comparator
                .comparing((T u) -> safeName(u), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(u -> u.getUserId());
    }

    /** Relevance = number of shared true subjects (desc). */
    private static <T extends UserView> Comparator<T> relevanceComparator(boolean[] meSubjects) {
        return (a, b) -> {
            int ca = commonSubjectsCount(meSubjects, a.getSubjects());
            int cb = commonSubjectsCount(meSubjects, b.getSubjects());
            return Integer.compare(cb, ca); // descending
        };
    }

    // ----------------- HELPERS -----------------

    private static String safeName(UserView u) {
        String n = u.getName();
        return (n == null) ? "" : n;
    }

    private static int commonSubjectsCount(boolean[] me, boolean[] other) {
        if (me == null || other == null) return 0;
        int n = Math.min(me.length, other.length);
        int c = 0;
        for (int i = 0; i < n; i++) if (me[i] && other[i]) c++;
        return c;
    }

    // ----------------- OPTIONAL ADAPTER -----------------

    public static final class UserLiteAdapter implements UserView {
        private final int id;
        private final String name;
        private final boolean[] subjects;
        private final Long lastContactTs;

        public UserLiteAdapter(int id, String name, boolean[] subjects, Long lastContactTs) {
            this.id = id;
            this.name = name;
            this.subjects = subjects;
            this.lastContactTs = lastContactTs;
        }
        public int getUserId() { return id; }
        public String getName() { return name; }
        public boolean[] getSubjects() { return subjects; }
        public Long getLastContactTs() { return lastContactTs; }
    }
}
