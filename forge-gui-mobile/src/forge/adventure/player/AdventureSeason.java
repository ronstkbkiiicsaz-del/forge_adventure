package forge.adventure.player;

import forge.adventure.coop.CoopSession;
import forge.adventure.data.ConfigData;
import forge.adventure.stage.GameHUD;
import forge.adventure.util.Config;
import forge.adventure.util.SaveFileData;
import forge.card.CardEdition;
import forge.deck.CardPool;
import forge.item.PaperCard;
import forge.model.FModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * SN1: in-game calendar and seasons that drive Shandalar Standard rotation.
 * Ascendant only ({@link Config#ascendant()} + {@link ConfigData#sn1Seasons}).
 * Co-op sessions leave this path alone (host calendar is deferred until co-op thaws).
 */
public final class AdventureSeason {

    private double gameDays;
    private double seasonStartDays;
    /** Completed seasons (0 at New Game). Opening seasons use the short length. */
    private int seasonsCompleted;
    private boolean choicePending;
    private final List<String> shortlist = new ArrayList<>();
    /** Release-order option used for grace auto-pick. */
    private String releaseOrderPick;
    private double graceDeadlineDays = -1d;
    private boolean undoAvailable;
    private final List<String> undoSets = new ArrayList<>();
    private String undoRotated;
    private String undoPicked;
    private double undoSeasonStartDays;
    private int undoSeasonsCompleted;
    private boolean newestWasMasteredThisSeason;
    private String pinnedSet;
    private int seasonsSincePin = 99;
    private int planarKeys;
    private long shortlistSeed;
    private boolean initialized;

    public boolean isEnabled() {
        if (!Config.ascendant())
            return false;
        try {
            if (CoopSession.get().isActive())
                return false;
        } catch (Throwable ignored) {
            // CoopSession may be unavailable in some tests.
        }
        ConfigData cfg = cfg();
        return cfg == null || cfg.sn1Seasons;
    }

    private static ConfigData cfg() {
        try {
            return Config.instance().getConfigData();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public double getGameDays() {
        return gameDays;
    }

    public int calendarDayNumber() {
        return Math.max(1, (int) Math.floor(gameDays) + 1);
    }

    public int seasonsCompleted() {
        return seasonsCompleted;
    }

    public boolean isChoicePending() {
        return choicePending;
    }

    public boolean isUndoAvailable() {
        return undoAvailable;
    }

    public List<String> getShortlist() {
        return Collections.unmodifiableList(shortlist);
    }

    public String getReleaseOrderPick() {
        return releaseOrderPick;
    }

    public String getPinnedSet() {
        return pinnedSet;
    }

    public int getPlanarKeys() {
        return planarKeys;
    }

    public boolean isNewestMasteredThisSeason() {
        return newestWasMasteredThisSeason;
    }

    /** Display name: "The Zendikar season". */
    public String seasonDisplayName(StandardWindow window) {
        String newest = window == null ? null : window.newestSet();
        if (newest == null || newest.isEmpty())
            return "Season";
        return "The " + StandardWindow.setName(newest) + " season";
    }

    public int seasonLengthDays() {
        ConfigData c = cfg();
        int openingCount = c == null ? 2 : Math.max(0, c.sn1OpeningSeasonCount);
        int openingDays = c == null ? 7 : Math.max(1, c.sn1OpeningSeasonDays);
        int normalDays = c == null ? 14 : Math.max(1, c.sn1SeasonDays);
        return seasonsCompleted < openingCount ? openingDays : normalDays;
    }

    public double daysIntoSeason() {
        return Math.max(0d, gameDays - seasonStartDays);
    }

    public double daysRemainingInSeason() {
        return Math.max(0d, seasonLengthDays() - daysIntoSeason());
    }

    public String hudLine(StandardWindow window) {
        if (!isEnabled() || window == null || !window.isActive())
            return "";
        if (choicePending)
            return "Day " + calendarDayNumber() + " · pick next set";
        int left = (int) Math.ceil(daysRemainingInSeason());
        return "Day " + calendarDayNumber() + " · " + seasonDisplayName(window)
                + " (" + left + "d left)";
    }

    public String statusLine(StandardWindow window) {
        if (!isEnabled() || window == null || !window.isActive())
            return "";
        StringBuilder sb = new StringBuilder();
        sb.append(seasonDisplayName(window));
        sb.append(" — Day ").append(calendarDayNumber());
        if (choicePending) {
            sb.append(" — choose the next set");
        } else {
            sb.append(" — ").append((int) Math.ceil(daysRemainingInSeason())).append(" days left");
        }
        if (planarKeys > 0)
            sb.append(" — Planar Keys: ").append(planarKeys);
        return sb.toString();
    }

    /** New game: calendar day 0, opening season 1 of {@link ConfigData#sn1OpeningSeasonCount}. */
    public void initNewGame(StandardWindow window) {
        clear();
        initialized = true;
        seasonStartDays = 0d;
        gameDays = 0d;
        seasonsCompleted = 0;
        shortlistSeed = System.nanoTime();
        if (window != null && window.isActive()) {
            notifyPlayer("[GOLD]" + seasonDisplayName(window) + " begins.[]");
        }
    }

    /**
     * Old saves without season keys: derive a sensible default from the current rotation.
     * Opening seasons remaining = how many sets still fit before the window is full.
     */
    public void initFromLegacyRotation(StandardWindow window) {
        clear();
        initialized = true;
        gameDays = 0d;
        seasonStartDays = 0d;
        shortlistSeed = System.nanoTime();
        int size = window == null ? 0 : window.getSets().size();
        ConfigData c = cfg();
        int openingCount = c == null ? 2 : Math.max(0, c.sn1OpeningSeasonCount);
        int windowSize = StandardWindow.resolvedWindowSize();
        // Treat each set beyond the New Game pair as one completed opening/normal season.
        seasonsCompleted = Math.max(0, size - 2);
        if (size >= windowSize)
            seasonsCompleted = Math.max(seasonsCompleted, openingCount);
        if (window != null && window.isChoicePending()) {
            // Legacy mastery pick waiting — convert to a season shortlist pick.
            beginSeasonPick(window, false);
        }
    }

    public void clear() {
        gameDays = 0d;
        seasonStartDays = 0d;
        seasonsCompleted = 0;
        choicePending = false;
        shortlist.clear();
        releaseOrderPick = null;
        graceDeadlineDays = -1d;
        undoAvailable = false;
        undoSets.clear();
        undoRotated = null;
        undoPicked = null;
        newestWasMasteredThisSeason = false;
        pinnedSet = null;
        seasonsSincePin = 99;
        planarKeys = 0;
        shortlistSeed = 0L;
        initialized = false;
    }

    /** Advance calendar from real exploring time (WorldStage / MapStage). */
    public void tickExploring(float realSeconds, StandardWindow window, CardPool collection) {
        if (!isEnabled() || !initialized || realSeconds <= 0f)
            return;
        ConfigData c = cfg();
        float minutesPerDay = c == null ? 15f : Math.max(0.01f, c.sn1ExploreMinutesPerDay);
        gameDays += realSeconds / (minutesPerDay * 60f);
        afterTimeAdvanced(window, collection);
    }

    /** Flat duel cost in in-game hours (win or lose). */
    public void chargeDuelHours(StandardWindow window, CardPool collection) {
        if (!isEnabled() || !initialized)
            return;
        ConfigData c = cfg();
        float hours = c == null ? 2f : Math.max(0f, c.sn1DuelHours);
        gameDays += hours / 24d;
        afterTimeAdvanced(window, collection);
    }

    /** Inn rest: skip to the next morning (start of the next calendar day). */
    public void restUntilMorning(StandardWindow window, CardPool collection) {
        if (!isEnabled() || !initialized)
            return;
        double nextMorning = Math.floor(gameDays) + 1d;
        if (nextMorning <= gameDays)
            nextMorning = gameDays + 1d;
        gameDays = nextMorning;
        afterTimeAdvanced(window, collection);
        notifyPlayer("You rest until morning. Day " + calendarDayNumber() + ".");
    }

    private void afterTimeAdvanced(StandardWindow window, CardPool collection) {
        if (choicePending) {
            maybeAutoPick(window);
            return;
        }
        if (window != null && window.isActive() && daysIntoSeason() >= seasonLengthDays())
            endSeason(window, false, collection);
    }

    /**
     * Mastery of the newest set ends the season early (SN1 replaces one-unlock-per-world).
     * @return true if a season pick was opened
     */
    public boolean endByMastery(StandardWindow window, CardPool collection) {
        if (!isEnabled() || !initialized || choicePending)
            return false;
        if (window == null || !window.isActive())
            return false;
        newestWasMasteredThisSeason = true;
        endSeason(window, true, collection);
        return choicePending;
    }

    private void endSeason(StandardWindow window, boolean byMastery, CardPool collection) {
        if (choicePending)
            return;
        beginSeasonPick(window, byMastery);
        String reason = byMastery ? "Set mastered" : "The season ends";
        notifyPlayer("[GOLD]" + reason + "![]\nOpen the Skills screen to choose the next set.");
        try {
            if (byMastery && Config.ascendant())
                AdventurePlayer.current().grantMasterySurgePicks();
        } catch (Exception ignored) {
            // Player / Config may be unavailable in tests
        }
    }

    private void beginSeasonPick(StandardWindow window, boolean byMastery) {
        choicePending = true;
        undoAvailable = false;
        undoSets.clear();
        undoRotated = null;
        undoPicked = null;
        buildShortlist(window);
        ConfigData c = cfg();
        int grace = c == null ? 3 : Math.max(0, c.sn1GracePeriodDays);
        graceDeadlineDays = grace <= 0 ? -1d : gameDays + grace;
        if (!byMastery)
            newestWasMasteredThisSeason = newestWasMasteredThisSeason
                    || (window != null && isMastered(window, null));
    }

    private static boolean isMastered(StandardWindow window, CardPool collection) {
        if (window == null)
            return false;
        CardPool pool = collection;
        if (pool == null) {
            try {
                pool = AdventurePlayer.current().getCards();
            } catch (Exception e) {
                return false;
            }
        }
        int[] p = window.masteryProgress(pool);
        return p[1] > 0 && p[0] >= p[1];
    }

    public void buildShortlist(StandardWindow window) {
        shortlist.clear();
        releaseOrderPick = null;
        if (window == null)
            return;
        ConfigData c = cfg();
        int want = c == null ? 3 : Math.max(1, c.sn1ShortlistSize);
        List<CardEdition> choosable = window.choosableSets();
        if (choosable.isEmpty())
            return;

        String release = nextByReleaseOrder(window, choosable);
        releaseOrderPick = release;
        Set<String> used = new HashSet<>();
        if (release != null) {
            shortlist.add(release);
            used.add(release);
        }
        String interest = interestSet(window, choosable, used);
        if (interest != null) {
            shortlist.add(interest);
            used.add(interest);
        }
        List<String> remaining = new ArrayList<>();
        for (CardEdition ed : choosable) {
            if (!used.contains(ed.getCode()))
                remaining.add(ed.getCode());
        }
        Random rng = new Random(shortlistSeed ^ seasonsCompleted ^ Double.hashCode(gameDays));
        while (shortlist.size() < want && !remaining.isEmpty()) {
            int i = rng.nextInt(remaining.size());
            shortlist.add(remaining.remove(i));
        }
        // If interest/release collided or lists were short, fill from choosable order.
        for (CardEdition ed : choosable) {
            if (shortlist.size() >= want)
                break;
            if (!used.contains(ed.getCode()) && !shortlist.contains(ed.getCode()))
                shortlist.add(ed.getCode());
        }
    }

    /** Next booster set after the newest window set by real release date. */
    public static String nextByReleaseOrder(StandardWindow window, List<CardEdition> choosable) {
        if (window == null || choosable == null || choosable.isEmpty())
            return null;
        String newest = window.newestSet();
        Date newestDate = null;
        if (newest != null && !StandardWindow.CORE_COLLECTION.equals(newest)) {
            CardEdition ed = FModel.getMagicDb().getEditions().get(newest);
            if (ed != null)
                newestDate = ed.getDate();
        }
        CardEdition best = null;
        for (CardEdition ed : choosable) {
            if (ed == null || ed.getDate() == null)
                continue;
            if (newestDate != null && !ed.getDate().after(newestDate))
                continue;
            if (best == null || ed.getDate().before(best.getDate()))
                best = ed;
        }
        if (best != null)
            return best.getCode();
        // No later set — fall back to the oldest choosable (era wrap).
        CardEdition oldest = null;
        for (CardEdition ed : choosable) {
            if (ed == null || ed.getDate() == null)
                continue;
            if (oldest == null || ed.getDate().before(oldest.getDate()))
                oldest = ed;
        }
        return oldest == null ? choosable.get(0).getCode() : oldest.getCode();
    }

    private static String interestSet(StandardWindow window, List<CardEdition> choosable, Set<String> exclude) {
        CardPool cards;
        try {
            cards = AdventurePlayer.current().getCards();
        } catch (Exception e) {
            return null;
        }
        if (cards == null || cards.isEmpty())
            return null;
        Map<String, Integer> ownedBySet = new HashMap<>();
        for (PaperCard pc : cards.toFlatList()) {
            if (pc == null || pc.getEdition() == null)
                continue;
            String code = pc.getEdition();
            ownedBySet.merge(code, 1, Integer::sum);
        }
        String best = null;
        int bestCount = 0;
        for (CardEdition ed : choosable) {
            if (exclude.contains(ed.getCode()))
                continue;
            int n = ownedBySet.getOrDefault(ed.getCode(), 0);
            if (n > bestCount) {
                bestCount = n;
                best = ed.getCode();
            }
        }
        return bestCount > 0 ? best : null;
    }

    /** Player dismissed the pick UI — grace period keeps running. */
    public void deferChoice() {
        if (!choicePending)
            return;
        ConfigData c = cfg();
        int grace = c == null ? 3 : Math.max(0, c.sn1GracePeriodDays);
        if (graceDeadlineDays < 0 && grace > 0)
            graceDeadlineDays = gameDays + grace;
    }

    private void maybeAutoPick(StandardWindow window) {
        if (!choicePending || graceDeadlineDays < 0 || gameDays < graceDeadlineDays)
            return;
        String pick = releaseOrderPick;
        if (pick == null && !shortlist.isEmpty())
            pick = shortlist.get(0);
        if (pick == null)
            return;
        notifyPlayer("No set chosen — the world picks " + StandardWindow.setName(pick) + ".");
        applyPick(window, pick, false);
    }

    /**
     * Apply a shortlist / any-set pick. {@code chooseAny} spends gold or a Planar Key.
     * @return rotated-out set code, or null
     */
    public String applyPick(StandardWindow window, String code, boolean chooseAny) {
        if (!choicePending || window == null || code == null)
            return null;
        if (window.getSets().contains(code))
            return null;
        if (chooseAny && !payChooseAny())
            return null;

        undoSets.clear();
        undoSets.addAll(window.getSets());
        undoPicked = code;
        undoRotated = null;
        undoSeasonStartDays = seasonStartDays;
        undoSeasonsCompleted = seasonsCompleted;
        undoAvailable = true;

        String rotated = window.addSetForSeason(code, pinnedSet);
        undoRotated = rotated;
        if (rotated != null && rotated.equals(pinnedSet)) {
            // Pin consumed when it would have left; keep pin mark clear.
            pinnedSet = null;
            seasonsSincePin = 0;
        }

        finishPickBookkeeping(window, code, rotated);
        return rotated;
    }

    private boolean payChooseAny() {
        ConfigData c = cfg();
        int goldCost = c == null ? 500 : Math.max(0, c.sn1ChooseAnyGoldCost);
        try {
            AdventurePlayer player = AdventurePlayer.current();
            if (planarKeys > 0) {
                planarKeys--;
                return true;
            }
            if (goldCost <= 0)
                return true;
            if (player.getGold() < goldCost)
                return false;
            player.takeGold(goldCost);
            return true;
        } catch (Exception e) {
            return goldCost <= 0;
        }
    }

    private void finishPickBookkeeping(StandardWindow window, String code, String rotated) {
        choicePending = false;
        shortlist.clear();
        graceDeadlineDays = -1d;
        boolean mastered = newestWasMasteredThisSeason;
        newestWasMasteredThisSeason = false;
        seasonsCompleted++;
        seasonsSincePin++;
        seasonStartDays = gameDays;
        shortlistSeed = System.nanoTime();

        String msg = "New set unlocked: " + StandardWindow.setName(code) + ".";
        if (rotated != null)
            msg += "\n" + StandardWindow.setName(rotated) + " rotated out of Standard.";
        msg += "\n" + seasonDisplayName(window) + " begins.";
        notifyPlayer(msg);

        if (mastered) {
            ConfigData c = cfg();
            int keys = c == null ? 1 : Math.max(0, c.sn1SeasonMasteryPlanarKeys);
            if (keys > 0) {
                planarKeys += keys;
                notifyPlayer("Season mastered — gained " + keys + " Planar Key"
                        + (keys == 1 ? "" : "s") + ".");
            }
        }

        try {
            if (Config.ascendant()) {
                ConfigData cfg = cfg();
                if (cfg == null || cfg.masteryUnlocksSetPlane) {
                    forge.adventure.world.WorldSave save = forge.adventure.world.WorldSave.getCurrentSave();
                    if (save != null)
                        forge.adventure.world.PlanarPortalPlacer.onSetUnlocked(save, code);
                }
            }
        } catch (Exception ignored) {
            // Plane generation must not break the unlock flow.
        }
    }

    /** Free undo until a pack is opened or a fight is won with the new set. */
    public String undoPick(StandardWindow window) {
        if (!undoAvailable || window == null || undoSets.isEmpty())
            return null;
        String removed = undoPicked;
        window.restoreSets(undoSets);
        choicePending = true;
        seasonsCompleted = undoSeasonsCompleted;
        seasonStartDays = undoSeasonStartDays;
        seasonsSincePin = Math.max(0, seasonsSincePin - 1);
        undoAvailable = false;
        buildShortlist(window);
        ConfigData c = cfg();
        int grace = c == null ? 3 : Math.max(0, c.sn1GracePeriodDays);
        graceDeadlineDays = grace <= 0 ? -1d : gameDays + grace;
        undoSets.clear();
        undoPicked = null;
        undoRotated = null;
        notifyPlayer("Set choice undone" + (removed == null ? "." : ": " + StandardWindow.setName(removed) + "."));
        return removed;
    }

    public void lockUndo() {
        undoAvailable = false;
        undoSets.clear();
        undoPicked = null;
        undoRotated = null;
    }

    public boolean canPin() {
        ConfigData c = cfg();
        if (c != null && !c.sn1PinEnabled)
            return false;
        int cooldown = c == null ? 3 : Math.max(1, c.sn1PinCooldownSeasons);
        return seasonsSincePin >= cooldown;
    }

    public boolean pinSet(String code, StandardWindow window) {
        if (!canPin() || code == null || window == null)
            return false;
        if (!window.getSets().contains(code))
            return false;
        pinnedSet = code;
        seasonsSincePin = 0;
        notifyPlayer("Pinned " + StandardWindow.setName(code)
                + " — it will skip the next rotation out.");
        return true;
    }

    public void clearPin() {
        pinnedSet = null;
    }

    private static void notifyPlayer(String msg) {
        try {
            GameHUD.getInstance().addNotification(msg);
        } catch (Exception ignored) {
        }
    }

    // ---- save / load ----

    public void save(SaveFileData data) {
        if (data == null)
            return;
        data.store("sn1Initialized", initialized);
        data.store("sn1GameDays", gameDays);
        data.store("sn1SeasonStartDays", seasonStartDays);
        data.store("sn1SeasonsCompleted", seasonsCompleted);
        data.store("sn1ChoicePending", choicePending);
        data.store("sn1GraceDeadlineDays", graceDeadlineDays);
        data.store("sn1UndoAvailable", undoAvailable);
        data.store("sn1NewestMastered", newestWasMasteredThisSeason);
        data.store("sn1PlanarKeys", planarKeys);
        data.store("sn1SeasonsSincePin", seasonsSincePin);
        data.store("sn1ShortlistSeed", shortlistSeed);
        if (releaseOrderPick != null)
            data.store("sn1ReleaseOrderPick", releaseOrderPick);
        if (pinnedSet != null)
            data.store("sn1PinnedSet", pinnedSet);
        if (undoPicked != null)
            data.store("sn1UndoPicked", undoPicked);
        if (undoRotated != null)
            data.store("sn1UndoRotated", undoRotated);
        data.storeObject("sn1Shortlist", shortlist.toArray(new String[0]));
        data.storeObject("sn1UndoSets", undoSets.toArray(new String[0]));
    }

    public void load(SaveFileData data, StandardWindow window) {
        if (data == null || !data.containsKey("sn1Initialized") || !data.readBool("sn1Initialized")) {
            initFromLegacyRotation(window);
            return;
        }
        clear();
        initialized = true;
        gameDays = data.containsKey("sn1GameDays") ? Math.max(0d, data.readDouble("sn1GameDays")) : 0d;
        seasonStartDays = data.containsKey("sn1SeasonStartDays")
                ? Math.max(0d, data.readDouble("sn1SeasonStartDays")) : 0d;
        seasonsCompleted = data.containsKey("sn1SeasonsCompleted")
                ? Math.max(0, data.readInt("sn1SeasonsCompleted")) : 0;
        choicePending = data.containsKey("sn1ChoicePending") && data.readBool("sn1ChoicePending");
        graceDeadlineDays = data.containsKey("sn1GraceDeadlineDays")
                ? data.readDouble("sn1GraceDeadlineDays") : -1d;
        undoAvailable = data.containsKey("sn1UndoAvailable") && data.readBool("sn1UndoAvailable");
        newestWasMasteredThisSeason = data.containsKey("sn1NewestMastered")
                && data.readBool("sn1NewestMastered");
        planarKeys = data.containsKey("sn1PlanarKeys") ? Math.max(0, data.readInt("sn1PlanarKeys")) : 0;
        seasonsSincePin = data.containsKey("sn1SeasonsSincePin")
                ? Math.max(0, data.readInt("sn1SeasonsSincePin")) : 99;
        shortlistSeed = data.containsKey("sn1ShortlistSeed") ? data.readLong("sn1ShortlistSeed") : System.nanoTime();
        releaseOrderPick = data.containsKey("sn1ReleaseOrderPick") ? data.readString("sn1ReleaseOrderPick") : null;
        pinnedSet = data.containsKey("sn1PinnedSet") ? data.readString("sn1PinnedSet") : null;
        undoPicked = data.containsKey("sn1UndoPicked") ? data.readString("sn1UndoPicked") : null;
        undoRotated = data.containsKey("sn1UndoRotated") ? data.readString("sn1UndoRotated") : null;
        shortlist.clear();
        if (data.containsKey("sn1Shortlist")) {
            String[] sl = (String[]) data.readObject("sn1Shortlist");
            if (sl != null)
                Collections.addAll(shortlist, sl);
        }
        undoSets.clear();
        if (data.containsKey("sn1UndoSets")) {
            String[] us = (String[]) data.readObject("sn1UndoSets");
            if (us != null)
                Collections.addAll(undoSets, us);
        }
        if (choicePending && shortlist.isEmpty() && window != null)
            buildShortlist(window);
    }
}
