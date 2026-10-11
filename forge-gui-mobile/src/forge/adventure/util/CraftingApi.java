package forge.adventure.util;

import forge.adventure.player.AdventurePlayer;
import forge.card.CardRarity;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;

/**
 * EC1 / MX1: clean crafting façade over {@link AdventurePlayer} dust craft.
 * MX1's Missing list can call {@link #dustCost}, {@link #formatDustCost}, and
 * {@link #craftMissing} without reaching into Spell Smith UI.
 *
 * <p>Ascendant-gated via the underlying player APIs. No co-op wire change.
 */
public final class CraftingApi {

    private CraftingApi() {
    }

    public static AdventurePlayer player() {
        return Current.player();
    }

    /** Resolved craftable printing, or null. */
    public static PaperCard printing(String cardName) {
        AdventurePlayer p = player();
        if (p == null || cardName == null)
            return null;
        return p.craftPrinting(cardName);
    }

    public static PaperCard printing(PaperCard card) {
        AdventurePlayer p = player();
        if (p == null || card == null)
            return null;
        return p.craftPrinting(card);
    }

    /** Dust cost for one copy (0 if not craftable / not Ascendant). */
    public static int dustCost(PaperCard card) {
        AdventurePlayer p = player();
        if (p == null || card == null)
            return 0;
        return p.craftCost(card);
    }

    public static int dustCost(String cardName) {
        PaperCard pc = printing(cardName);
        return pc == null ? 0 : dustCost(pc);
    }

    /** Null when craftable; otherwise a short reason. */
    public static String problem(PaperCard card) {
        AdventurePlayer p = player();
        if (p == null)
            return "No player.";
        return p.craftProblem(card);
    }

    public static boolean canCraft(PaperCard card) {
        AdventurePlayer p = player();
        return p != null && p.canCraft(card);
    }

    public static boolean canAfford(PaperCard card) {
        AdventurePlayer p = player();
        if (p == null || card == null || !p.canCraft(card))
            return false;
        PaperCard print = p.craftPrinting(card);
        if (print == null)
            return false;
        int cost = p.craftCost(print);
        int idx = AdventurePlayer.dustIndex(print.getRarity());
        if (idx < 0 || p.getDust(idx) < cost)
            return false;
        return p.craftReagentCost(print).canAfford(p);
    }

    /** Craft one copy; returns false on failure (player notifies). */
    public static boolean craftOne(PaperCard card) {
        AdventurePlayer p = player();
        return p != null && p.craftCard(card);
    }

    /**
     * Short label for Missing-list UI: {@code "40 Common dust"} or {@code "—"}.
     */
    public static String formatDustCost(PaperCard card) {
        AdventurePlayer p = player();
        if (p == null || card == null)
            return "—";
        PaperCard print = p.craftPrinting(card);
        if (print == null)
            return "—";
        int cost = p.craftCost(print);
        if (cost <= 0)
            return "—";
        CardRarity rarity = print.getRarity();
        return cost + " " + rarity.getLongName() + " dust";
    }

    public static String formatDustCost(String cardName) {
        PaperCard pc = printing(cardName);
        return pc == null ? "—" : formatDustCost(pc);
    }

    /**
     * Craft as many missing copies as dust/reagents allow, in list order.
     * Returns one result per requested name.
     */
    public static List<CraftResult> craftMissing(List<MissingRequest> requests) {
        List<CraftResult> out = new ArrayList<>();
        if (requests == null)
            return out;
        AdventurePlayer p = player();
        if (p == null) {
            for (MissingRequest r : requests)
                out.add(CraftResult.failed(r == null ? null : r.name, 0, "No player."));
            return out;
        }
        for (MissingRequest r : requests) {
            if (r == null || r.name == null || r.name.isBlank()) {
                out.add(CraftResult.failed(null, 0, "No card."));
                continue;
            }
            int need = Math.max(0, r.wanted - Math.max(0, r.owned));
            if (need <= 0) {
                out.add(new CraftResult(r.name, 0, 0, null));
                continue;
            }
            PaperCard print = p.craftPrinting(r.name);
            if (print == null) {
                out.add(CraftResult.failed(r.name, need, "Not in craftable pool."));
                continue;
            }
            int crafted = 0;
            String lastProblem = null;
            for (int i = 0; i < need; i++) {
                String problem = p.craftProblem(print);
                if (problem != null) {
                    lastProblem = problem;
                    break;
                }
                if (!canAfford(print)) {
                    lastProblem = "Not enough dust or reagents.";
                    break;
                }
                if (!p.craftCard(print)) {
                    lastProblem = p.craftProblem(print);
                    if (lastProblem == null)
                        lastProblem = "Craft failed.";
                    break;
                }
                crafted++;
            }
            out.add(new CraftResult(r.name, need, crafted, crafted < need ? lastProblem : null));
        }
        return out;
    }

    public static final class MissingRequest {
        public final String name;
        public final int wanted;
        public final int owned;

        public MissingRequest(String name, int wanted, int owned) {
            this.name = name;
            this.wanted = wanted;
            this.owned = owned;
        }
    }

    public static final class CraftResult {
        public final String name;
        public final int requested;
        public final int crafted;
        public final String problem;

        public CraftResult(String name, int requested, int crafted, String problem) {
            this.name = name;
            this.requested = requested;
            this.crafted = crafted;
            this.problem = problem;
        }

        public static CraftResult failed(String name, int requested, String problem) {
            return new CraftResult(name, requested, 0, problem);
        }

        public boolean ok() {
            return problem == null && crafted >= requested;
        }
    }
}
