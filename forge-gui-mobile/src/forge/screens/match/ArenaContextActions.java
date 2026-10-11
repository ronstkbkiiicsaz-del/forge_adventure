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
 * Pure label resolution for the DS1 big context button (Pass / Next / Attack /
 * Done / Resolve) and the "pass until end of turn" companion. Headless-testable.
 */
public final class ArenaContextActions {
    private ArenaContextActions() {
    }

    public enum ContextKind {
        PASS,
        NEXT,
        ATTACK,
        DONE,
        RESOLVE,
        KEEP,
        MULLIGAN,
        OK
    }

    /**
     * Pick the context label from the current prompt text / combat flags.
     * Falls back to {@link ContextKind#OK} when nothing matches — the classic
     * prompt dialogs remain the fallback for every other prompt (PR1 rule).
     */
    public static ContextKind resolve(final String okLabel, final boolean stackNonEmpty,
            final boolean declareAttackers, final boolean declareBlockers) {
        if (declareAttackers) {
            return ContextKind.ATTACK;
        }
        if (declareBlockers) {
            return ContextKind.DONE;
        }
        if (stackNonEmpty) {
            return ContextKind.RESOLVE;
        }
        if (okLabel != null) {
            final String t = okLabel.trim().toLowerCase();
            if (t.contains("keep")) {
                return ContextKind.KEEP;
            }
            if (t.contains("mulligan")) {
                return ContextKind.MULLIGAN;
            }
            if (t.contains("attack") || t.contains("all attack") || t.contains("declare attackers")) {
                return ContextKind.ATTACK;
            }
            if (t.contains("done") || t.contains("finish") || t.contains("declare blockers")) {
                return ContextKind.DONE;
            }
            if (t.contains("resolve")) {
                return ContextKind.RESOLVE;
            }
            if (t.contains("pass") || t.contains("ok") || t.isEmpty()) {
                return ContextKind.PASS;
            }
            if (t.contains("next") || t.contains("end turn") || t.contains("end of turn")) {
                return ContextKind.NEXT;
            }
        }
        return ContextKind.PASS;
    }

    public static String label(final ContextKind kind) {
        return switch (kind) {
            case PASS -> "Pass";
            case NEXT -> "Next";
            case ATTACK -> "Attack";
            case DONE -> "Done";
            case RESOLVE -> "Resolve";
            case KEEP -> "Keep";
            case MULLIGAN -> "Mulligan";
            case OK -> "OK";
        };
    }

    public static String passUntilEotLabel() {
        // Short label so the Arena prompt-end button doesn't ellipsize to "…"
        // in the landscape prompt strip.
        return "Pass EOT";
    }
}
