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

import forge.Forge;
import forge.screens.match.views.VPrompt;

/**
 * Single Arena-detection check for DS1. Prefer this over repeating
 * {@code instanceof ArenaDuelScreen} / {@code isArenaDuelScreen()} pairs.
 * Also hosts Cancel-arming helpers that must stay loadable without GL fonts.
 */
public final class ArenaDuelUi {
    private ArenaDuelUi() {
    }

    /** True when the active match view is the DS1 Arena skeleton. */
    public static boolean isActive() {
        if (Forge.getCurrentScreen() instanceof ArenaDuelScreen) {
            return true;
        }
        final MatchScreen view = MatchController.getView();
        return view != null && view.isArenaDuelScreen();
    }

    /**
     * Same arming test as the Arena Cancel chrome / pad B: classic Cancel enabled
     * with a non-empty label. Classic buttons stay hidden in Arena layout.
     */
    public static boolean isCancelPromptArmed(final VPrompt prompt) {
        if (prompt == null || prompt.getBtnCancel() == null) {
            return false;
        }
        return isCancelPromptArmed(prompt.getBtnCancel().isEnabled(), prompt.getBtnCancel().getText());
    }

    /** Headless-friendly form of {@link #isCancelPromptArmed(VPrompt)}. */
    public static boolean isCancelPromptArmed(final boolean cancelEnabled, final String cancelText) {
        return cancelEnabled && cancelText != null && !cancelText.isEmpty();
    }
}
