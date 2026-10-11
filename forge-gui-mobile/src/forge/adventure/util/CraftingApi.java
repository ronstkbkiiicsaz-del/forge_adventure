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
        // Full-count reagent check: temporarily reserve materials for every copy.
        List<Map.Entry<String, Integer>> reserved = new ArrayList<>();
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
                if (!cost.canAfford(p)) {
                    List<String> missingMats = cost.missing(p);
                    reagentBlock = missingMats.isEmpty()
                            ? "Missing reagents for " + r.name + " (" + need + " copies)."
                            : missingMats.get(0);
                    break outer;
                }
                Map<String, Integer> pay = cost.resolvePayment(p);
                if (pay == null || pay.isEmpty()) {
                    reagentBlock = "Missing reagents for " + r.name + ".";
                    break outer;
                }
                for (Map.Entry<String, Integer> e : pay.entrySet()) {
                    if (!p.takeMaterial(e.getKey(), e.getValue())) {
                        reagentBlock = "Missing reagents for " + r.name + ".";
                        break outer;
                    }
                    reserved.add(Map.entry(e.getKey(), e.getValue()));
                }
            }
        }
        for (int i = reserved.size() - 1; i >= 0; i--) {
            Map.Entry<String, Integer> e = reserved.get(i);
            p.addMaterial(e.getKey(), e.getValue());
        }
        if (reagentBlock != null) {
            return new CraftMissingPlan(normalized, needByRarity, totalCopies, false, reagentBlock);
        }
        return new CraftMissingPlan(normalized, needByRarity, totalCopies, true, null);
    }

    /**
     * All-or-nothing craft of every missing copy (source printings). If the plan is not
     * affordable, nothing is crafted. Uses each request's preferred printing when unlocked.
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
        // Plan already verified full dust + reagents for every copy — craft all or nothing.
        for (MissingRequest r : plan.requests) {
            int need = r.gap();
            PaperCard print = r.preferred != null
                    ? p.craftPrintingPreferring(r.preferred)
                    : p.craftPrinting(r.name);
            if (print == null) {
                out.add(CraftResult.failed(r.name, need, "Not in craftable pool."));
                return failRemaining(out, plan.requests, r);
            }
            for (int i = 0; i < need; i++) {
                if (!p.craftCard(print)) {
                    out.add(CraftResult.failed(r.name, need, "Craft failed mid-batch."));
                    return failRemaining(out, plan.requests, r);
                }
            }
            out.add(new CraftResult(r.name, need, need, null));
        }
        return out;
    }

    private static List<CraftResult> failRemaining(List<CraftResult> out,
            List<MissingRequest> requests, MissingRequest failed) {
        boolean past = false;
        for (MissingRequest r : requests) {
            if (r == failed) {
                past = true;
                continue;
            }
            if (!past)
                continue;
            out.add(CraftResult.failed(r.name, r.gap(), "Craft missing aborted (all-or-nothing)."));
        }
        return out;
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
