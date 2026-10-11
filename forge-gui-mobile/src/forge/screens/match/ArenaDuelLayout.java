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

/**
 * Pure geometry for DS1 Arena-style duel screen regions. Headless-testable;
 * no libGDX / MatchController dependency. MTG Arena is a layout/feel reference
 * only — no Arena assets.
 */
public final class ArenaDuelLayout {
    private ArenaDuelLayout() {
    }

    /** Named axis-aligned rect in screen space (origin top-left of content). */
    public static final class Rect {
        public final String name;
        public final float x;
        public final float y;
        public final float w;
        public final float h;

        public Rect(final String name, final float x, final float y, final float w, final float h) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        /** Strict interior overlap; edge-touching (shared boundary) is not an overlap. */
        public boolean overlaps(final Rect o) {
            final float eps = 0.05f;
            return x + eps < o.x + o.w && x + w > o.x + eps
                    && y + eps < o.y + o.h && y + h > o.y + eps;
        }

        public boolean containsPoint(final float px, final float py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        @Override
        public String toString() {
            return name + "[" + x + "," + y + " " + w + "x" + h + "]";
        }
    }

    /**
     * Layout result. {@link #regions} is insertion-ordered; {@link #leafRegions()}
     * returns the mutually exclusive interactive bands used for overlap tests.
     */
    public static final class Result {
        private final Map<String, Rect> regions;

        Result(final Map<String, Rect> regions) {
            this.regions = Collections.unmodifiableMap(regions);
        }

        public Map<String, Rect> regions() {
            return regions;
        }

        public Rect get(final String name) {
            return regions.get(name);
        }

        /**
         * Regions that must be pairwise non-overlapping (full AC1-style check).
         * Nested/parent bands (opponentArea, playerArea, centreLane) are omitted.
         */
        public List<Rect> leafRegions() {
            final List<Rect> leaves = new ArrayList<>();
            for (final Rect r : regions.values()) {
                if (r.w <= 0 || r.h <= 0) {
                    continue;
                }
                switch (r.name) {
                    case "opponentArea":
                    case "playerArea":
                    case "centreLane":
                        continue;
                    default:
                        leaves.add(r);
                }
            }
            return leaves;
        }
    }

    /**
     * Compute Arena duel regions for a viewport.
     *
     * @param width             viewport width
     * @param height            viewport height
     * @param landscape         true for landscape orientation
     * @param phaseRailFraction share of width reserved for the phase rail (tunable)
     * @param handFraction      share of the player area height for the fanned hand
     * @param stackFraction     share of centre-lane width for the stack column
     * @param promptHeight      height of the bottom prompt / context strip
     */
    public static Result compute(final float width, final float height, final boolean landscape,
            final float phaseRailFraction, final float handFraction, final float stackFraction,
            final float promptHeight) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport must be positive");
        }
        final float railF = clamp(phaseRailFraction, 0.04f, 0.12f);
        final float handF = clamp(handFraction, 0.18f, 0.40f);
        final float stackF = clamp(stackFraction, 0.35f, 0.70f);
        final float promptH = Math.max(24f, Math.min(promptHeight, height * 0.22f));

        final Map<String, Rect> m = new LinkedHashMap<>();
        if (landscape) {
            layoutLandscape(m, width, height, railF, handF, stackF, promptH);
        } else {
            layoutPortrait(m, width, height, railF, handF, stackF, promptH);
        }
        return new Result(m);
    }

    /** Defaults matching Ascendant {@code config.json} DS1 tunables. */
    public static Result compute(final float width, final float height, final boolean landscape) {
        return compute(width, height, landscape, 0.07f, 0.28f, 0.48f,
                landscape ? 56f : 64f);
    }

    private static void layoutLandscape(final Map<String, Rect> m, final float width, final float height,
            final float railF, final float handF, final float stackF, final float promptH) {
        final float railW = Math.max(36f, width * railF);
        final float contentX = railW;
        final float contentW = width - railW;
        final float menuH = Math.max(28f, height * 0.05f);
        final float bodyY = menuH;
        final float bodyH = height - menuH - promptH;

        m.put("phaseRail", new Rect("phaseRail", 0, bodyY, railW, bodyH));
        m.put("menuBar", new Rect("menuBar", 0, 0, width, menuH));

        // Opponent ~34%, centre stack/combat ~18%, player field+hand ~48% of body.
        final float oppH = bodyH * 0.34f;
        final float centreH = bodyH * 0.18f;
        final float playerH = bodyH - oppH - centreH;

        m.put("opponentArea", new Rect("opponentArea", contentX, bodyY, contentW, oppH));
        final float avatar = Math.min(oppH * 0.72f, contentW * 0.10f);
        m.put("opponentAvatar", new Rect("opponentAvatar", contentX, bodyY, avatar, avatar));
        final float pileW = Math.min(contentW * 0.12f, avatar * 1.1f);
        final float oppFieldX = contentX + avatar;
        final float pilesX = contentX + contentW - pileW;
        m.put("opponentField", new Rect("opponentField",
                oppFieldX, bodyY, pilesX - oppFieldX, oppH));
        m.put("opponentPiles", new Rect("opponentPiles", pilesX, bodyY, pileW, oppH));

        final float centreY = bodyY + oppH;
        m.put("centreLane", new Rect("centreLane", contentX, centreY, contentW, centreH));
        final float stackW = contentW * stackF;
        final float stackX = contentX + (contentW - stackW) / 2f;
        m.put("stackColumn", new Rect("stackColumn", stackX, centreY, stackW, centreH));
        // Combat strip: leftover bands left/right of the stack (used for arrows / declare).
        m.put("combatLane", new Rect("combatLane", contentX, centreY, contentW, centreH));

        final float playerY = centreY + centreH;
        m.put("playerArea", new Rect("playerArea", contentX, playerY, contentW, playerH));
        final float handH = playerH * handF;
        final float fieldH = playerH - handH;
        final float playerFieldX = contentX + avatar;
        m.put("playerField", new Rect("playerField",
                playerFieldX, playerY, pilesX - playerFieldX, fieldH));
        m.put("playerAvatar", new Rect("playerAvatar",
                contentX, playerY + fieldH - avatar, avatar, avatar));
        m.put("playerPiles", new Rect("playerPiles", pilesX, playerY, pileW, fieldH));
        m.put("playerHand", new Rect("playerHand",
                contentX, playerY + fieldH, contentW, handH));

        // Prompt strip: OK context, Cancel/secondary, take-back, message, pass-until-EOT.
        // Keep Cancel/OK wide enough for absolute 18px captions (no ellipsis).
        final float promptY = height - promptH;
        final float ctxW = Math.max(96f, Math.min(contentW * 0.16f, promptH * 2.6f));
        final float cancelW = Math.max(110f, Math.min(contentW * 0.16f, promptH * 2.6f));
        final float takeW = Math.min(contentW * 0.10f, promptH * 1.4f);
        final float eotW = Math.max(96f, Math.min(contentW * 0.16f, promptH * 2.2f));
        m.put("contextButton", new Rect("contextButton", 0, promptY, ctxW, promptH));
        m.put("cancelButton", new Rect("cancelButton", ctxW, promptY, cancelW, promptH));
        m.put("takeBackSlot", new Rect("takeBackSlot", ctxW + cancelW, promptY, takeW, promptH));
        m.put("promptBar", new Rect("promptBar",
                ctxW + cancelW + takeW, promptY, width - ctxW - cancelW - takeW - eotW, promptH));
        m.put("passUntilEot", new Rect("passUntilEot",
                width - eotW, promptY, eotW, promptH));
    }

    private static void layoutPortrait(final Map<String, Rect> m, final float width, final float height,
            final float railF, final float handF, final float stackF, final float promptH) {
        final float menuH = Math.max(28f, height * 0.045f);
        // Portrait: phase rail is a horizontal strip above the prompt (pad-reachable).
        final float railH = Math.max(28f, height * Math.max(railF, 0.05f));
        final float bodyY = menuH;
        final float bodyH = height - menuH - promptH - railH;

        m.put("menuBar", new Rect("menuBar", 0, 0, width, menuH));
        m.put("phaseRail", new Rect("phaseRail", 0, height - promptH - railH, width, railH));

        final float oppH = bodyH * 0.30f;
        final float centreH = bodyH * 0.16f;
        final float playerH = bodyH - oppH - centreH;
        final float contentX = 0;
        final float contentW = width;

        m.put("opponentArea", new Rect("opponentArea", contentX, bodyY, contentW, oppH));
        final float avatar = Math.min(oppH * 0.65f, contentW * 0.18f);
        final float pileW = Math.min(contentW * 0.16f, avatar * 1.15f);
        m.put("opponentAvatar", new Rect("opponentAvatar", contentX, bodyY, avatar, avatar));
        final float oppFieldX = contentX + avatar;
        final float pilesX = contentX + contentW - pileW;
        m.put("opponentField", new Rect("opponentField",
                oppFieldX, bodyY, pilesX - oppFieldX, oppH));
        m.put("opponentPiles", new Rect("opponentPiles", pilesX, bodyY, pileW, oppH));

        final float centreY = bodyY + oppH;
        m.put("centreLane", new Rect("centreLane", contentX, centreY, contentW, centreH));
        final float stackW = contentW * stackF;
        m.put("stackColumn", new Rect("stackColumn",
                contentX + (contentW - stackW) / 2f, centreY, stackW, centreH));
        m.put("combatLane", new Rect("combatLane", contentX, centreY, contentW, centreH));

        final float playerY = centreY + centreH;
        m.put("playerArea", new Rect("playerArea", contentX, playerY, contentW, playerH));
        final float handH = playerH * handF;
        final float fieldH = playerH - handH;
        final float playerFieldX = contentX + avatar;
        m.put("playerField", new Rect("playerField",
                playerFieldX, playerY, pilesX - playerFieldX, fieldH));
        m.put("playerAvatar", new Rect("playerAvatar",
                contentX, playerY + fieldH - avatar, avatar, avatar));
        m.put("playerPiles", new Rect("playerPiles", pilesX, playerY, pileW, fieldH));
        m.put("playerHand", new Rect("playerHand",
                contentX, playerY + fieldH, contentW, handH));

        final float promptY = height - promptH;
        final float ctxW = Math.max(96f, Math.min(width * 0.20f, promptH * 2.4f));
        final float cancelW = Math.max(110f, Math.min(width * 0.20f, promptH * 2.4f));
        final float takeW = Math.min(width * 0.12f, promptH * 1.3f);
        final float eotW = Math.max(96f, Math.min(width * 0.18f, promptH * 2.0f));
        m.put("contextButton", new Rect("contextButton", 0, promptY, ctxW, promptH));
        m.put("cancelButton", new Rect("cancelButton", ctxW, promptY, cancelW, promptH));
        m.put("takeBackSlot", new Rect("takeBackSlot", ctxW + cancelW, promptY, takeW, promptH));
        m.put("promptBar", new Rect("promptBar",
                ctxW + cancelW + takeW, promptY, width - ctxW - cancelW - takeW - eotW, promptH));
        m.put("passUntilEot", new Rect("passUntilEot",
                width - eotW, promptY, eotW, promptH));
    }

    private static float clamp(final float v, final float lo, final float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Full pairwise overlap check; returns the first conflicting pair or null. */
    public static String firstOverlap(final List<Rect> leaves) {
        for (int i = 0; i < leaves.size(); i++) {
            for (int j = i + 1; j < leaves.size(); j++) {
                final Rect a = leaves.get(i);
                final Rect b = leaves.get(j);
                // combatLane intentionally coincides with stackColumn's vertical band;
                // treat stack as the leaf and skip combat vs stack.
                if (("combatLane".equals(a.name) && "stackColumn".equals(b.name))
                        || ("stackColumn".equals(a.name) && "combatLane".equals(b.name))) {
                    continue;
                }
                if (a.overlaps(b)) {
                    return a + " overlaps " + b;
                }
            }
        }
        return null;
    }
}
