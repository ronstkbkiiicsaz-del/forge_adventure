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
 *
 * <p>Every pose keeps MTG card aspect ({@code h/w ≈ 3.5/2.5}) and axis-aligned
 * bounds fully inside the hand band (including the hovered/lifted card).
 */
public final class ArenaHandFan {
    /** Same ratio as {@code FCardPanel.ASPECT_RATIO} (height / width). */
    public static final float CARD_ASPECT = 3.5f / 2.5f;

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
     * @param liftPx     pixels to raise the hovered card (clamped so the card stays in-band)
     */
    public static List<CardPose> fan(final int count, final float bandW, final float bandH,
            final int hoverIndex, final boolean[] playable, final float overlap,
            final float maxTiltDeg, final float liftPx) {
        if (count <= 0 || bandW <= 0 || bandH <= 0) {
            return List.of();
        }
        final float ov = Math.max(0f, Math.min(0.85f, overlap));
        // Prefer height-driven sizing, then shrink so the fan fits the band width.
        float cardH = bandH * 0.92f;
        float cardW = cardH / CARD_ASPECT;
        float step = count == 1 ? 0f : cardW * (1f - ov);
        float totalW = cardW + step * Math.max(0, count - 1);
        if (totalW > bandW && count > 0) {
            final float scale = bandW / totalW;
            cardW *= scale;
            cardH = cardW * CARD_ASPECT;
            // Keep a little vertical margin so lift still has room.
            if (cardH > bandH * 0.92f) {
                cardH = bandH * 0.92f;
                cardW = cardH / CARD_ASPECT;
            }
            step = count == 1 ? 0f : cardW * (1f - ov);
            totalW = cardW + step * Math.max(0, count - 1);
        }
        final float startX = Math.max(0f, (bandW - totalW) / 2f);
        final float maxLift = Math.max(0f, bandH - cardH);
        final float lift = Math.min(Math.max(0f, liftPx), maxLift);
        // Resting Y leaves room above for the lift; clamp so y+h <= bandH.
        final float baseY = Math.max(0f, Math.min(maxLift, (bandH - cardH) * 0.55f + lift * 0.15f));
        final List<CardPose> poses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final float t = count == 1 ? 0f : (i / (float) (count - 1)) * 2f - 1f; // -1..1
            final float rot = t * maxTiltDeg;
            final boolean isLift = i == hoverIndex;
            float y = baseY - (isLift ? lift : 0f);
            if (y < 0f) {
                y = 0f;
            }
            if (y + cardH > bandH) {
                y = bandH - cardH;
            }
            final boolean canPlay = playable != null && i < playable.length && playable[i];
            poses.add(new CardPose(i, startX + i * step, y, cardW, cardH, rot, isLift, canPlay));
        }
        return Collections.unmodifiableList(poses);
    }

    public static List<CardPose> fan(final int count, final float bandW, final float bandH,
            final int hoverIndex, final boolean[] playable) {
        return fan(count, bandW, bandH, hoverIndex, playable, 0.55f, 12f, bandH * 0.18f);
    }
}
