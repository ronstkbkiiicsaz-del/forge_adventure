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

/**
 * Pure helpers for DS1 controller focus modes. Keeps headless tests free of
 * libGDX / Localizer / PhaseType class-init.
 */
public final class ModernDuelPad {
    public enum Focus {
        NONE,
        PEEK,
        MANA,
        PHASE,
        /** Arena primary context button (prompt OK). */
        CONTEXT,
        /** Arena secondary context button (prompt Cancel). */
        CANCEL
    }

    /**
     * What X should enter when not already in a focus mode.
     *
     * @param handCardFocused true if the focus cursor is on a hand card
     * @param manaAvailable   floating mana bar is showing (paying / pool non-empty)
     * @param arenaScreen     true when the Arena duel screen hosts context buttons
     */
    public static Focus chooseXTarget(final boolean handCardFocused, final boolean manaAvailable) {
        return chooseXTarget(handCardFocused, manaAvailable, false);
    }

    public static Focus chooseXTarget(final boolean handCardFocused, final boolean manaAvailable,
            final boolean arenaScreen) {
        if (handCardFocused) {
            return Focus.PEEK;
        }
        if (manaAvailable) {
            return Focus.MANA;
        }
        // Arena: prefer the big context button so pad can reach OK/Cancel chrome.
        if (arenaScreen) {
            return Focus.CONTEXT;
        }
        return Focus.PHASE;
    }

    /** Cycle Arena chrome focus: CONTEXT ↔ CANCEL ↔ PHASE. */
    public static Focus cycleArenaChrome(final Focus current, final int delta) {
        final Focus[] order = {Focus.CONTEXT, Focus.CANCEL, Focus.PHASE};
        int idx = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                idx = i;
                break;
            }
        }
        return order[cycle(idx, order.length, delta)];
    }

    /** Cycle an index in {@code [0, size)} by {@code delta} (-1 or +1). */
    public static int cycle(final int index, final int size, final int delta) {
        if (size <= 0) {
            return 0;
        }
        int i = index + delta;
        if (i < 0) {
            i = size - 1;
        } else if (i >= size) {
            i = 0;
        }
        return i;
    }

    /**
     * Hand reorder index when dropping a held hand card onto another hand card
     * (controller path — no screen X).
     *
     * @param heldIndex   current index of the held card, or -1
     * @param targetIndex index of the focused drop target in the hand
     * @param handSize    number of cards in hand
     * @return index for {@code reorderHand}, or -1 if no change
     */
    public static int reorderIndex(final int heldIndex, final int targetIndex, final int handSize) {
        if (heldIndex < 0 || targetIndex < 0 || handSize <= 0) {
            return -1;
        }
        if (targetIndex == heldIndex) {
            return -1;
        }
        // Zone.reorder expects the final left-count for the moved card.
        return Math.min(targetIndex, handSize - 1);
    }

    private ModernDuelPad() {
    }
}
