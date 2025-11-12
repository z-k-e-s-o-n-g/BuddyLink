package com.example.BuddyLink.Model;

import java.util.Comparator;
import java.util.List;

public final class UserSorter {

    private UserSorter() {}

    public enum Mode { RECENT, ALPHA, RELEVANCE, BEST_MATCH }

    public interface UserView {
        int getUserId();
        String getName();
        boolean[] getSubjects();
        Long getLastContactTs();
    }

 
    public static <T extends UserView> void sort(List<T> users, Mode mode, T currentUser) {
        if (users == null || users.size() <= 1) return;

        Comparator<T> cmp = switch (mode) {
            case RECENT -> mostRecentComparator();
            case ALPHA  -> alphaComparator();
            case RELEVANCE -> {
                Comparator<T> rel = relevanceComparator(currentUser == null ? null : currentUser.getSubjects());
                yield rel.thenComparing(mostRecentComparator()).thenComparing(alphaComparator());
            }
            case BEST_MATCH -> {
                boolean[] me = (currentUser == null) ? null : currentUser.getSubjects();
                Comparator<T> points = Comparator
                        .comparingInt((T u) -> pointsFor(me, u.getSubjects()))
                        .reversed();
                yield points.thenComparing(mostRecentComparator()).thenComparing(alphaComparator());
            }
        };

        users.sort(cmp);
    }


    public static int pointsFor(boolean[] me, boolean[] other) {
        if (me == null || other == null) return 0;
        int n = Math.min(me.length, other.length), p = 0;
        for (int i = 0; i < n; i++) if (me[i] && other[i]) p++;
        return p;
    }

    private static <T extends UserView> Comparator<T> mostRecentComparator() {
        return Comparator
                .comparing((T u) -> u.getLastContactTs(), Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing((T u) -> safeName(u), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(UserView::getUserId);
    }

    private static <T extends UserView> Comparator<T> alphaComparator() {
        return Comparator
                .comparing((T u) -> safeName(u), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(UserView::getUserId);
    }

    private static <T extends UserView> Comparator<T> relevanceComparator(boolean[] meSubjects) {
        return (a, b) -> Integer.compare(
                pointsFor(meSubjects, b.getSubjects()),
                pointsFor(meSubjects, a.getSubjects())
        );
    }

    private static String safeName(UserView u) {
        String n = u.getName();
        return (n == null) ? "" : n;
    }

    public static final class UserLiteAdapter implements UserView {
        private final int id; private final String name;
        private final boolean[] subjects; private final Long lastContactTs;
        public UserLiteAdapter(int id, String name, boolean[] subjects, Long lastContactTs) {
            this.id = id; this.name = name; this.subjects = subjects; this.lastContactTs = lastContactTs;
        }
        public int getUserId() { return id; }
        public String getName() { return name; }
        public boolean[] getSubjects() { return subjects; }
        public Long getLastContactTs() { return lastContactTs; }
    }
}
