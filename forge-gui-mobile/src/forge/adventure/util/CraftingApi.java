package forge.adventure.util;

import forge.adventure.player.AdventurePlayer;
import forge.card.CardRarity;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * EC1 / MX1: clean crafting façade over {@link AdventurePlayer} dust craft.
 * MX1's Missing list calls {@link #dustCost}, {@link #formatDustCost},
 * {@link #planCraftMissing} and {@link #craftMissing}.
 *
 * <p>Ascendant-gated via the underlying player APIs. No co-op wire change.
 */
public final class CraftingApi {

    private CraftingApi() {
    }

    public static AdventurePlayer player() {
        return Current.player();
    }

    /** Resolved craftable printing, or null. Prefers {@code preferred} when unlocked. */
    public static PaperCard printing(String cardName) {
        return printing(cardName, null);
    }

    public static PaperCard printing(String cardName, PaperCard preferred) {
        AdventurePlayer p = player();
        if (p == null || cardName == null)
            return null;
        return p.craftPrintingPreferring(preferred != null ? preferred : p.craftPrinting(cardName));
    }

    public static PaperCard printing(PaperCard card) {
        AdventurePlayer p = player();
        if (p == null || card == null)
            return null;
        return p.craftPrintingPreferring(card);
    }

    /** Dust cost for one copy (0 if not craftable / not Ascendant). */
    public static int dustCost(PaperCard card) {
        AdventurePlayer p = player();
        if (p == null || card == null)
            return 0;
        PaperCard print = p.craftPrintingPreferring(card);
        return print == null ? 0 : p.craftCost(print);
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
        if (p == null || card == null)
            return false;
        PaperCard print = p.craftPrintingPreferring(card);
        if (print == null || p.craftProblem(print) != null)
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
        if (p == null || card == null)
            return false;
        PaperCard print = p.craftPrintingPreferring(card);
        return print != null && p.craftCard(print);
    }

    /**
     * Short label for Missing-list UI: {@code "40 Common dust"} or {@code "—"}.
     */
    public static String formatDustCost(PaperCard card) {
        AdventurePlayer p = player();
        if (p == null || card == null)
            return "—";
        PaperCard print = p.craftPrintingPreferring(card);
        if (print == null)
            return "—";
        int cost = p.craftCost(print);
        if (cost <= 0)
            return "—";
        return cost + " " + print.getRarity().getLongName() + " dust";
    }

    public static String formatDustCost(String cardName) {
        PaperCard pc = printing(cardName);
        return pc == null ? "—" : formatDustCost(pc);
    }

    /**
     * Plan crafting every missing copy. {@link CraftMissingPlan#craftEnabled} is false
     * with {@link CraftMissingPlan#disabledReason} when dust/reagents are short or any
     * card is not craftable. Dust totals are still filled when computable so the Missing
     * list can show them even when Craft missing is disabled.
     */
    public static CraftMissingPlan planCraftMissing(List<MissingRequest> requests) {
        AdventurePlayer p = player();
        if (p == null || !Config.ascendant()) {
            return CraftMissingPlan.disabled(requests, "Crafting is not available.");
        }
        if (requests == null || requests.isEmpty()) {
            return CraftMissingPlan.disabled(requests, "Nothing to craft.");
        }
        List<MissingRequest> normalized = new ArrayList<>();
        Map<CardRarity, Integer> needByRarity = new LinkedHashMap<>();
        int totalCopies = 0;
        String hardBlock = null;
        for (MissingRequest r : requests) {
            if (r == null || r.name == null || r.name.isBlank())
                continue;
            int need = Math.max(0, r.wanted - Math.max(0, r.owned));
            if (need <= 0)
                continue;
            PaperCard preferred = r.preferred;
            PaperCard print = preferred != null
                    ? p.craftPrintingPreferring(preferred)
                    : p.craftPrinting(r.name);
            if (print == null) {
                hardBlock = r.name + " is not in your craftable pool.";
                break;
            }
            String problem = p.craftProblem(print);
            if (problem != null) {
                hardBlock = problem;
                break;
            }
            int unit = p.craftCost(print);
            int idx = AdventurePlayer.dustIndex(print.getRarity());
            if (idx < 0 || unit <= 0) {
                hardBlock = r.name + " cannot be crafted.";
                break;
            }
            needByRarity.merge(print.getRarity(), unit * need, Integer::sum);
            totalCopies += need;
            normalized.add(new MissingRequest(r.name, r.wanted, r.owned, print));
        }
        if (hardBlock != null) {
            return new CraftMissingPlan(normalized, needByRarity, totalCopies, false, hardBlock);
        }
        if (totalCopies <= 0) {
            return CraftMissingPlan.disabled(requests, "Nothing to craft.");
        }
        for (Map.Entry<CardRarity, Integer> e : needByRarity.entrySet()) {
            int have = p.getDust(e.getKey());
            if (have < e.getValue()) {
                return new CraftMissingPlan(normalized, needByRarity, totalCopies, false,
                        "Not enough " + e.getKey().getLongName() + " dust (need "
                                + e.getValue() + ", have " + have + ").");
            }
        }
        // Full-count reagent check: reserve in a virtual map (no take / re-add).
        Map<String, Integer> reserved = new LinkedHashMap<>();
        String reagentBlock = null;
        outer:
        for (MissingRequest r : normalized) {
            PaperCard print = r.preferred != null ? r.preferred : p.craftPrinting(r.name);
            if (print == null)
                continue;
            int need = r.gap();
            for (int i = 0; i < need; i++) {
                var cost = p.craftReagentCost(print);
                if (cost.isEmpty())
                    continue;
                if (!cost.canAfford(p, reserved)) {
                    List<String> missingMats = cost.missing(p);
                    reagentBlock = missingMats.isEmpty()
                            ? "Missing reagents for " + r.name + " (" + need + " copies)."
                            : missingMats.get(0);
                    break outer;
                }
                Map<String, Integer> delta = cost.resolvePayment(p, reserved);
                if (!cost.isEmpty() && (delta == null || delta.isEmpty())) {
                    reagentBlock = "Missing reagents for " + r.name + ".";
                    break outer;
                }
                for (Map.Entry<String, Integer> e : delta.entrySet())
                    reserved.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        if (reagentBlock != null) {
            return new CraftMissingPlan(normalized, needByRarity, totalCopies, false, reagentBlock);
        }
        return new CraftMissingPlan(normalized, needByRarity, totalCopies, true, null);
    }

    /**
     * All-or-nothing craft of every missing copy (source printings). If the plan is not
     * affordable, nothing is crafted. Mid-batch failure rolls back every earlier copy.
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
        CraftMissingPlan plan = planCraftMissing(requests);
        if (!plan.craftEnabled) {
            List<MissingRequest> src = !plan.requests.isEmpty() ? plan.requests : requests;
            for (MissingRequest r : src) {
                if (r == null)
                    continue;
                out.add(CraftResult.failed(r.name, r.gap(),
                        plan.disabledReason != null ? plan.disabledReason : "Cannot craft."));
            }
            return out;
        }
        // Track successful crafts so a mid-batch failure can roll everything back.
        List<PaperCard> crafted = new ArrayList<>();
        List<Integer> dustIdx = new ArrayList<>();
        List<Integer> dustSpent = new ArrayList<>();
        List<Map<String, Integer>> matSpent = new ArrayList<>();
        for (MissingRequest r : plan.requests) {
            int need = r.gap();
            PaperCard print = r.preferred != null
                    ? p.craftPrintingPreferring(r.preferred)
                    : p.craftPrinting(r.name);
            if (print == null) {
                rollbackCrafts(p, crafted, dustIdx, dustSpent, matSpent);
                return allFailed(plan.requests, "Not in craftable pool.");
            }
            int unitCost = p.craftCost(print);
            int idx = AdventurePlayer.dustIndex(print.getRarity());
            for (int i = 0; i < need; i++) {
                // Snapshot the same payment craftCard will take (before inventory changes).
                var cost = p.craftReagentCost(print);
                Map<String, Integer> pay = cost.isEmpty()
                        ? Map.of()
                        : Map.copyOf(cost.resolvePayment(p));
                if (!p.craftCard(print)) {
                    rollbackCrafts(p, crafted, dustIdx, dustSpent, matSpent);
                    return allFailed(plan.requests, "Craft failed mid-batch.");
                }
                crafted.add(print);
                dustIdx.add(idx);
                dustSpent.add(unitCost);
                matSpent.add(pay);
            }
            out.add(new CraftResult(r.name, need, need, null));
        }
        return out;
    }

    private static List<CraftResult> allFailed(List<MissingRequest> requests, String reason) {
        List<CraftResult> out = new ArrayList<>();
        for (MissingRequest r : requests) {
            if (r != null)
                out.add(CraftResult.failed(r.name, r.gap(), reason));
        }
        return out;
    }

    /** Undo every successful craft in this batch (cards, dust, reagents). */
    private static void rollbackCrafts(AdventurePlayer p, List<PaperCard> crafted,
            List<Integer> dustIdx, List<Integer> dustSpent, List<Map<String, Integer>> matSpent) {
        for (int i = crafted.size() - 1; i >= 0; i--) {
            PaperCard print = crafted.get(i);
            if (print != null && p.getCards().count(print) > 0)
                p.getCards().remove(print, 1);
            int idx = dustIdx.get(i);
            int cost = dustSpent.get(i);
            if (idx >= 0 && cost > 0)
                p.addDust(idx, cost);
            Map<String, Integer> mats = matSpent.get(i);
            if (mats != null) {
                for (Map.Entry<String, Integer> e : mats.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                        p.addMaterial(e.getKey(), e.getValue());
                }
            }
        }
    }

    public static final class MissingRequest {
        public final String name;
        public final int wanted;
        public final int owned;
        /** Preferred source printing from the deck list (may be null). */
        public final PaperCard preferred;

        public MissingRequest(String name, int wanted, int owned) {
            this(name, wanted, owned, null);
        }

        public MissingRequest(String name, int wanted, int owned, PaperCard preferred) {
            this.name = name;
            this.wanted = wanted;
            this.owned = owned;
            this.preferred = preferred;
        }

        public int gap() {
            return Math.max(0, wanted - Math.max(0, owned));
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

    /** Snapshot of whether Craft missing can run and why not. */
    public static final class CraftMissingPlan {
        public final List<MissingRequest> requests;
        public final Map<CardRarity, Integer> dustNeeded;
        public final int totalCopies;
        public final boolean craftEnabled;
        public final String disabledReason;

        public CraftMissingPlan(List<MissingRequest> requests, Map<CardRarity, Integer> dustNeeded,
                int totalCopies, boolean craftEnabled, String disabledReason) {
            this.requests = requests == null ? List.of() : List.copyOf(requests);
            this.dustNeeded = dustNeeded == null ? Map.of() : Map.copyOf(dustNeeded);
            this.totalCopies = totalCopies;
            this.craftEnabled = craftEnabled;
            this.disabledReason = disabledReason;
        }

        public static CraftMissingPlan disabled(List<MissingRequest> requests, String reason) {
            return new CraftMissingPlan(requests, Map.of(), 0, false, reason);
        }

        public String totalDustLine() {
            if (dustNeeded.isEmpty())
                return "";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<CardRarity, Integer> e : dustNeeded.entrySet()) {
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(e.getValue()).append(' ').append(e.getKey().getLongName());
            }
            sb.append(" dust");
            return sb.toString();
        }

        public String confirmBody() {
            return String.format(Locale.ROOT,
                    "Craft %d missing %s for %s?",
                    totalCopies,
                    totalCopies == 1 ? "card" : "cards",
                    totalDustLine());
        }
    }
}
