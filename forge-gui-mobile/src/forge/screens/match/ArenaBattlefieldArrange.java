/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.screens.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure Arena-style battlefield classification for DS1: lands in a back row
 * (identical lands stacked with a count), creatures in front, other permanents
 * to the side. Attachments stay with their host (caller skips attached panels).
 * Headless-testable — no CardView dependency; callers map views to descriptors.
 */
public final class ArenaBattlefieldArrange {
    private ArenaBattlefieldArrange() {
    }

    public enum Kind {
        LAND,
        CREATURE,
        OTHER
    }

    /** Minimal permanent descriptor used by the arranger. */
    public static final class Permanent {
        public final String id;
        public final Kind kind;
        public final String stackKey;
        public final boolean summoningSick;
        public final boolean tapped;
        public final boolean hasAttachments;

        public Permanent(final String id, final Kind kind, final String stackKey,
                final boolean summoningSick, final boolean tapped, final boolean hasAttachments) {
            this.id = Objects.requireNonNull(id, "id");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.stackKey = stackKey == null ? id : stackKey;
            this.summoningSick = summoningSick;
            this.tapped = tapped;
            this.hasAttachments = hasAttachments;
        }
    }

    /** One visible stack on a row (top permanent + stacked copies underneath). */
    public static final class Stack {
        public final Permanent top;
        public final List<Permanent> under;
        public final int count;

        Stack(final Permanent top, final List<Permanent> under) {
            this.top = top;
            this.under = Collections.unmodifiableList(under);
            this.count = 1 + under.size();
        }
    }

    public static final class Arrangement {
        public final List<Stack> lands;
        public final List<Stack> creatures;
        public final List<Stack> others;

        Arrangement(final List<Stack> lands, final List<Stack> creatures, final List<Stack> others) {
            this.lands = Collections.unmodifiableList(lands);
            this.creatures = Collections.unmodifiableList(creatures);
            this.others = Collections.unmodifiableList(others);
        }
    }

    /**
     * Classify and stack permanents. Permanents with attachments never stack
     * (host stays visible). Identical lands / tokens stack by {@code stackKey}.
     */
    public static Arrangement arrange(final List<Permanent> permanents) {
        final List<Permanent> landIn = new ArrayList<>();
        final List<Permanent> creatureIn = new ArrayList<>();
        final List<Permanent> otherIn = new ArrayList<>();
        for (final Permanent p : permanents) {
            if (p == null) {
                continue;
            }
            switch (p.kind) {
                case LAND -> landIn.add(p);
                case CREATURE -> creatureIn.add(p);
                case OTHER -> otherIn.add(p);
                default -> otherIn.add(p);
            }
        }
        return new Arrangement(stackRow(landIn, true), stackRow(creatureIn, false), stackRow(otherIn, false));
    }

    private static List<Stack> stackRow(final List<Permanent> input, final boolean stackIdentical) {
        if (!stackIdentical) {
            // Creatures / others: stack only exact stackKey matches without attachments.
            return stackByKey(input);
        }
        return stackByKey(input);
    }

    private static List<Stack> stackByKey(final List<Permanent> input) {
        final Map<String, List<Permanent>> groups = new LinkedHashMap<>();
        final List<Permanent> unstackable = new ArrayList<>();
        for (final Permanent p : input) {
            if (p.hasAttachments) {
                unstackable.add(p);
                continue;
            }
            groups.computeIfAbsent(p.stackKey, k -> new ArrayList<>()).add(p);
        }
        final List<Stack> out = new ArrayList<>();
        for (final Permanent p : unstackable) {
            out.add(new Stack(p, List.of()));
        }
        for (final List<Permanent> group : groups.values()) {
            if (group.isEmpty()) {
                continue;
            }
            final Permanent top = group.get(0);
            final List<Permanent> under = group.size() == 1
                    ? List.of()
                    : new ArrayList<>(group.subList(1, group.size()));
            out.add(new Stack(top, under));
        }
        return out;
    }

    /**
     * Side-strip share of the battlefield width for OTHER permanents (tunable default 0.22).
     */
    public static float sideStripFraction(final float configured) {
        if (configured <= 0f) {
            return 0.22f;
        }
        return Math.max(0.12f, Math.min(0.35f, configured));
    }

    /** Dim factor for summoning-sick creatures (1 = full, lower = dimmer). */
    public static float sickDimFactor(final float configured) {
        if (configured <= 0f) {
            return 0.72f;
        }
        return Math.max(0.4f, Math.min(1f, configured));
    }
}
