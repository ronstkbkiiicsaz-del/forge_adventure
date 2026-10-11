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
 * Pure stack-column geometry for DS1: a vertical column where the top (resolving)
 * item is largest. Index 0 is the top of the stack.
 */
public final class ArenaStackColumnLayout {
    private ArenaStackColumnLayout() {
    }

    public static final class ItemRect {
        public final int index;
        public final float x;
        public final float y;
        public final float w;
        public final float h;
        public final float scale;

        ItemRect(final int index, final float x, final float y, final float w, final float h, final float scale) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.scale = scale;
        }
    }

    /**
     * Lay out {@code count} stack items in a column. Top item (index 0) is largest;
     * each deeper item shrinks by {@code shrinkPerLevel}.
     */
    public static List<ItemRect> layout(final int count, final float colW, final float colH,
            final float shrinkPerLevel) {
        if (count <= 0 || colW <= 0 || colH <= 0) {
            return List.of();
        }
        final float shrink = Math.max(0.05f, Math.min(0.35f, shrinkPerLevel));
        // Reserve vertical slots; top gets more height.
        float weightSum = 0f;
        final float[] weights = new float[count];
        for (int i = 0; i < count; i++) {
            weights[i] = (float) Math.pow(1f - shrink, i);
            weightSum += weights[i];
        }
        final List<ItemRect> out = new ArrayList<>(count);
        float y = 0f;
        final float pad = Math.min(6f, colH * 0.04f);
        final float usableH = Math.max(1f, colH - pad * (count + 1));
        // Match FCardPanel.ASPECT_RATIO (h/w = 3.5/2.5) so wide centre lanes don't
        // squash stack cards into unreadable horizontal bars.
        final float cardAspect = 3.5f / 2.5f;
        for (int i = 0; i < count; i++) {
            final float h = usableH * (weights[i] / weightSum);
            final float scale = weights[i];
            final float maxW = colW * (0.55f + 0.45f * scale);
            final float aspectW = h / cardAspect;
            final float w = Math.min(maxW, aspectW);
            final float x = (colW - w) / 2f;
            y += pad;
            out.add(new ItemRect(i, x, y, w, h, scale));
            y += h;
        }
        return Collections.unmodifiableList(out);
    }

    public static List<ItemRect> layout(final int count, final float colW, final float colH) {
        return layout(count, colW, colH, 0.18f);
    }
}
