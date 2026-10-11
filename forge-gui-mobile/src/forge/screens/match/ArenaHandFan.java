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
import java.util.List;

/**
 * Pure fanned-hand geometry for DS1. Cards arc across the hand band; hover lifts
 * a card; playable glow is a flag the view draws. Headless-testable.
 */
public final class ArenaHandFan {
    private ArenaHandFan() {
    }

    public static final class CardPose {
        public final int index;
        public final float x;
        public final float y;
        public final float w;
        public final float h;
        public final float rotationDeg;
        public final boolean lifted;
        public final boolean playable;

        CardPose(final int index, final float x, final float y, final float w, final float h,
                final float rotationDeg, final boolean lifted, final boolean playable) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.rotationDeg = rotationDeg;
            this.lifted = lifted;
            this.playable = playable;
        }
    }

    /**
     * Fan {@code count} cards into {@code bandW x bandH}.
     *
     * @param hoverIndex index to lift, or -1
     * @param playable   length {@code count}; null treats none as playable
     * @param overlap    0..1 how much consecutive cards overlap (higher = tighter fan)
     * @param maxTiltDeg max absolute rotation at the fan edges
     * @param liftPx     pixels to raise the hovered card
     */
    public static List<CardPose> fan(final int count, final float bandW, final float bandH,
            final int hoverIndex, final boolean[] playable, final float overlap,
            final float maxTiltDeg, final float liftPx) {
        if (count <= 0 || bandW <= 0 || bandH <= 0) {
            return List.of();
        }
        final float ov = Math.max(0f, Math.min(0.85f, overlap));
        final float cardH = bandH * 0.92f;
        final float cardW = cardH / 1.4f; // ~MTG aspect without depending on FCardPanel
        final float step = count == 1 ? 0f : cardW * (1f - ov);
        final float totalW = cardW + step * (count - 1);
        final float startX = Math.max(0f, (bandW - totalW) / 2f);
        final float baseY = (bandH - cardH) * 0.55f;
        final List<CardPose> poses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final float t = count == 1 ? 0f : (i / (float) (count - 1)) * 2f - 1f; // -1..1
            final float rot = t * maxTiltDeg;
            final boolean lift = i == hoverIndex;
            final float y = baseY - (lift ? Math.max(0f, liftPx) : 0f);
            final boolean canPlay = playable != null && i < playable.length && playable[i];
            poses.add(new CardPose(i, startX + i * step, y, cardW, cardH, rot, lift, canPlay));
        }
        return Collections.unmodifiableList(poses);
    }

    public static List<CardPose> fan(final int count, final float bandW, final float bandH,
            final int hoverIndex, final boolean[] playable) {
        return fan(count, bandW, bandH, hoverIndex, playable, 0.55f, 12f, bandH * 0.18f);
    }
}
