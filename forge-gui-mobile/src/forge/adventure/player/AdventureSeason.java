package forge.adventure.player;

import forge.adventure.coop.CoopSession;
import forge.adventure.data.ConfigData;
import forge.adventure.data.RewardData;
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SN1: in-game calendar and seasons that drive Shandalar Standard rotation.
 * Ascendant only ({@link Config#ascendant()} + {@link ConfigData#sn1Seasons}).
 * Co-op sessions leave this path alone (host calendar is deferred until co-op thaws).
 *
 * <p>Public calendar API for EC1 and other packages (call directly — no reflection):
 * <ul>
 *   <li>{@link #getGameDays()} / {@link #calendarDayNumber()} — current in-game day</li>
 *   <li>{@link #getSeasonIndex()} / {@link #seasonsCompleted()} — seasons finished (0 at NG)</li>
 *   <li>{@link #addSeasonChangeListener(SeasonChangeListener)} /
 *       {@link #removeSeasonChangeListener(SeasonChangeListener)}
 *       — notified when a season ends (pick applied, empty end, or auto-pick)</li>
 * </ul>
 */
public final class AdventureSeason {

    /** Fired after a season completes (pick applied, auto-pick, or empty choosable end). */
    @FunctionalInterface
    public interface SeasonChangeListener {
        void onSeasonChanged(AdventureSeason season);
    }

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
    private final List<String> undoShortlist = new ArrayList<>();
    private long undoShortlistSeed;
    private String undoReleaseOrderPick;
    private boolean undoChoseAny;
    private boolean undoPaidWithKey;
    private int undoGoldPaid;
    private boolean undoOpenedPlane;
    private boolean undoFirstUnlock;
    private boolean undoGrantedMasteryRewards;
    private String undoMasteryRewardSet;
    /** Pin state before the pick (restored on undo when the skip path consumed it). */
    private String undoPinnedSet;
    private int undoSeasonsSincePin = 99;
    private boolean newestWasMasteredThisSeason;
    private String pinnedSet;
    private int seasonsSincePin = 99;
    private int planarKeys;
    private long shortlistSeed;
    private boolean initialized;
    /** Calendar day number (1-based) of the last successful Inn Rest; -1 = never. */
    private int lastRestDay = -1;
    /** Sets that already granted season-mastery Surge / Planar Key / cosmetic. */
    private final Set<String> masteryRewardedSets = new HashSet<>();
    private boolean newestEligibleForMasteryReward = true;
    private String lastPickError;
    private final List<SeasonChangeListener> seasonChangeListeners = new CopyOnWriteArrayList<>();

    /** Ascendant + sn1Seasons — true even in co-op (display / status). */
    public boolean isConfigured() {
        if (!Config.ascendant())
            return false;
        ConfigData cfg = cfg();
        return cfg == null || cfg.sn1Seasons;
    }

    /**
     * Season mutations (calendar ticks, picks) run only outside co-op sessions.
     * Co-op is frozen; display still uses {@link #isConfigured()}.
     */
    public boolean isEnabled() {
        if (!isConfigured())
            return false;
        try {
            if (CoopSession.get().isActive())
                return false;
        } catch (Throwable ignored) {
            // CoopSession may be unavailable in some tests.
        }
        return true;
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

    /** Fractional in-game days since New Game (0 at start). EC1 calendar day source. */
    public double getGameDays() {
        return gameDays;
    }

    /** 1-based calendar day number shown on the HUD. */
    public int calendarDayNumber() {
        return Math.max(1, (int) Math.floor(gameDays) + 1);
    }

    /** Seasons fully completed (0 at New Game). Alias for EC1 "season index". */
    public int getSeasonIndex() {
        return seasonsCompleted;
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

    /** Test / debug: grant Planar Keys (boss/achievement sources are a documented gap). */
    public void addPlanarKeys(int n) {
        if (n > 0)
            planarKeys += n;
    }

    public boolean isNewestMasteredThisSeason() {
        return newestWasMasteredThisSeason;
    }

    /** Last applyPick / rest failure message, or null. */
    public String getLastPickError() {
        return lastPickError;
    }

    public void addSeasonChangeListener(SeasonChangeListener listener) {
        if (listener != null)
            seasonChangeListeners.add(listener);
    }

    public void removeSeasonChangeListener(SeasonChangeListener listener) {
        seasonChangeListeners.remove(listener);
    }

    private void fireSeasonChanged() {
        for (SeasonChangeListener l : seasonChangeListeners) {
            try {
                l.onSeasonChanged(this);
            } catch (Exception ignored) {
            }
        }
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
        if (!isConfigured() || !initialized || window == null || !window.isActive())
            return "";
        // ASCII hyphen — adventure HUD fonts often lack middot glyphs.
        if (choicePending)
            return "Day " + calendarDayNumber() + " - pick next set";
        int left = (int) Math.ceil(daysRemainingInSeason());
        return "Day " + calendarDayNumber() + " - " + seasonDisplayName(window)
                + " (" + left + "d left)";
    }

    public String statusLine(StandardWindow window) {
        if (!isConfigured() || !initialized || window == null || !window.isActive())
            return "";
        StringBuilder sb = new StringBuilder();
        sb.append(seasonDisplayName(window));
        // ASCII separators — adventure UI fonts often lack em-dash glyphs.
        sb.append(" - Day ").append(calendarDayNumber());
        if (choicePending) {
            sb.append(" - choose the next set");
        } else {
            sb.append(" - ").append((int) Math.ceil(daysRemainingInSeason())).append(" days left");
        }
        if (planarKeys > 0)
            sb.append(" - Planar Keys: ").append(planarKeys);
        return sb.toString();
    }

    /** New game: calendar day 0, opening season 1 of {@link ConfigData#sn1OpeningSeasonCount}. */
    public void initNewGame(StandardWindow window) {
        clear();
        initialized = true;
        seasonStartDays = 0d;
        gameDays = 0d;
        seasonsCompleted = 0;
        // One random seed at NG; later seasons advance it deterministically (no nanoTime reseed).
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
        undoSeasonStartDays = 0d;
        undoSeasonsCompleted = 0;
        undoShortlist.clear();
        undoShortlistSeed = 0L;
        undoReleaseOrderPick = null;
        undoChoseAny = false;
        undoPaidWithKey = false;
        undoGoldPaid = 0;
        undoOpenedPlane = false;
        undoFirstUnlock = false;
        undoGrantedMasteryRewards = false;
        undoMasteryRewardSet = null;
        undoPinnedSet = null;
        undoSeasonsSincePin = 99;
        newestWasMasteredThisSeason = false;
        pinnedSet = null;
        seasonsSincePin = 99;
        planarKeys = 0;
        shortlistSeed = 0L;
        lastRestDay = -1;
        masteryRewardedSets.clear();
        newestEligibleForMasteryReward = true;
        lastPickError = null;
        initialized = false;
    }

    /**
     * Advance calendar from real exploring time (WorldStage / MapStage).
     * Callers must pass only moving/exploring time — idle does not count (SN1 spec).
     */
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

    /** Gold cost for Inn Rest (0 = free). */
    public int restGoldCost() {
        ConfigData c = cfg();
        return c == null ? 25 : Math.max(0, c.sn1RestGoldCost);
    }

    /** True when Rest is available today (not already used this calendar day). */
    public boolean canRestToday() {
        if (!isEnabled() || !initialized)
            return false;
        return lastRestDay != calendarDayNumber();
    }

    /**
     * Inn rest: pay the tunable gold cost (once per in-game day) and skip to the next morning.
     * @return true when rest succeeded
     */
    public boolean restUntilMorning(StandardWindow window, CardPool collection) {
        lastPickError = null;
        if (!isEnabled() || !initialized)
            return false;
        if (!canRestToday()) {
            lastPickError = "Already rested today.";
            notifyPlayer(lastPickError);
            return false;
        }
        int cost = restGoldCost();
        if (cost > 0) {
            try {
                AdventurePlayer player = AdventurePlayer.current();
                if (player.getGold() < cost) {
                    lastPickError = "Need " + cost + " gold to rest (have " + player.getGold() + ").";
                    notifyPlayer(lastPickError);
                    return false;
                }
                player.takeGold(cost);
            } catch (Exception e) {
                lastPickError = "Cannot rest right now.";
                return false;
            }
        }
        // Record the day being rested (tonight), not the morning after — otherwise
        // canRestToday stays false for the whole next calendar day.
        int dayRested = calendarDayNumber();
        double nextMorning = Math.floor(gameDays) + 1d;
        if (nextMorning <= gameDays)
            nextMorning = gameDays + 1d;
        gameDays = nextMorning;
        lastRestDay = dayRested;
        afterTimeAdvanced(window, collection);
        notifyPlayer("You rest until morning. Day " + calendarDayNumber() + "."
                + (cost > 0 ? " (-" + cost + " gold)" : ""));
        return true;
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
     * @return true if a season pick was opened (or the season ended with no sets left)
     */
    public boolean endByMastery(StandardWindow window, CardPool collection) {
        if (!isEnabled() || !initialized || choicePending)
            return false;
        if (window == null || !window.isActive())
            return false;
        newestWasMasteredThisSeason = true;
        endSeason(window, true, collection);
        return true;
    }

    private void endSeason(StandardWindow window, boolean byMastery, CardPool collection) {
        if (choicePending)
            return;
        String newest = window == null ? null : window.newestSet();
        boolean grantRewards = byMastery && newest != null && !masteryRewardedSets.contains(newest);
        beginSeasonPick(window, byMastery);
        if (!choicePending) {
            // Empty choosable — season already closed in beginSeasonPick.
            if (grantRewards) {
                try {
                    if (Config.ascendant())
                        AdventurePlayer.current().grantMasterySurgePicks();
                } catch (Exception ignored) {
                }
                grantSeasonMasteryRewards(newest);
            }
            return;
        }
        String reason = byMastery ? "Set mastered" : "The season ends";
        notifyPlayer("[GOLD]" + reason + "![]\nOpen the Skills screen to choose the next set.");
        if (grantRewards) {
            try {
                if (Config.ascendant())
                    AdventurePlayer.current().grantMasterySurgePicks();
            } catch (Exception ignored) {
                // Player / Config may be unavailable in tests
            }
            // Keys + cosmetic land when the pick finishes (finishPickBookkeeping).
            newestEligibleForMasteryReward = true;
        } else {
            newestEligibleForMasteryReward = false;
        }
    }

    private void beginSeasonPick(StandardWindow window, boolean byMastery) {
        undoAvailable = false;
        undoSets.clear();
        undoRotated = null;
        undoPicked = null;
        undoShortlist.clear();
        List<CardEdition> choosable = window == null ? List.of() : window.choosableSets();
        if (choosable.isEmpty()) {
            // No sets left — end the season with no pick and say so.
            choicePending = false;
            shortlist.clear();
            releaseOrderPick = null;
            graceDeadlineDays = -1d;
            seasonsCompleted++;
            seasonsSincePin++;
            seasonStartDays = gameDays;
            advanceShortlistSeed();
            notifyPlayer("The season ends — no sets left to choose. "
                    + seasonDisplayName(window) + " continues with the current Standard.");
            fireSeasonChanged();
            return;
        }
        choicePending = true;
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
        if (pick == null) {
            // Shortlist empty somehow — close with no pick.
            beginSeasonPick(window, false);
            return;
        }
        notifyPlayer("No set chosen — the world picks " + StandardWindow.setName(pick) + ".");
        applyPick(window, pick, false);
        try {
            RewardData.invalidateCardPool();
        } catch (Exception ignored) {
        }
    }

    /**
     * Apply a shortlist / any-set pick. {@code chooseAny} spends gold or a Planar Key.
     * @return rotated-out set code, or null (also null on failure — see {@link #getLastPickError()})
     */
    public String applyPick(StandardWindow window, String code, boolean chooseAny) {
        lastPickError = null;
        if (!choicePending || window == null || code == null) {
            lastPickError = "No season pick is waiting.";
            return null;
        }
        if (window.getSets().contains(code)) {
            lastPickError = StandardWindow.setName(code) + " is already in Standard.";
            return null;
        }
        if (!chooseAny && !shortlist.contains(code)) {
            lastPickError = StandardWindow.setName(code) + " is not on the shortlist.";
            notifyPlayer(lastPickError);
            return null;
        }
        if (chooseAny && !payChooseAny()) {
            // payChooseAny sets lastPickError
            notifyPlayer(lastPickError != null ? lastPickError : "Cannot afford Choose any set.");
            return null;
        }

        boolean firstUnlock = !window.isEverUnlocked(code);

        undoSets.clear();
        undoSets.addAll(window.getSets());
        undoPicked = code;
        undoRotated = null;
        undoSeasonStartDays = seasonStartDays;
        undoSeasonsCompleted = seasonsCompleted;
        undoShortlist.clear();
        undoShortlist.addAll(shortlist);
        undoShortlistSeed = shortlistSeed;
        undoReleaseOrderPick = releaseOrderPick;
        undoChoseAny = chooseAny;
        // undoGoldPaid / undoPaidWithKey already set inside payChooseAny when chooseAny
        if (!chooseAny) {
            undoPaidWithKey = false;
            undoGoldPaid = 0;
        }
        undoOpenedPlane = false;
        undoFirstUnlock = firstUnlock;
        undoGrantedMasteryRewards = false;
        undoMasteryRewardSet = null;
        undoPinnedSet = pinnedSet;
        undoSeasonsSincePin = seasonsSincePin;
        undoAvailable = true;

        String rotated = window.addSetForSeason(code, pinnedSet);
        undoRotated = rotated;
        if (window.didLastAddConsumePin() || (rotated != null && rotated.equals(pinnedSet))) {
            // Pin expires after it skips (or would have left). Restored on undo.
            pinnedSet = null;
            seasonsSincePin = 0;
        }

        finishPickBookkeeping(window, code, rotated, firstUnlock);
        return rotated;
    }

    private boolean payChooseAny() {
        undoPaidWithKey = false;
        undoGoldPaid = 0;
        ConfigData c = cfg();
        int goldCost = c == null ? 500 : Math.max(0, c.sn1ChooseAnyGoldCost);
        try {
            AdventurePlayer player = AdventurePlayer.current();
            if (planarKeys > 0) {
                planarKeys--;
                undoPaidWithKey = true;
                return true;
            }
            if (goldCost <= 0)
                return true;
            if (player.getGold() < goldCost) {
                lastPickError = "Need " + goldCost + " gold to choose any set (have "
                        + player.getGold() + ").";
                return false;
            }
            player.takeGold(goldCost);
            undoGoldPaid = goldCost;
            return true;
        } catch (Exception e) {
            if (goldCost <= 0)
                return true;
            lastPickError = "Cannot spend gold for Choose any set.";
            return false;
        }
    }

    private void finishPickBookkeeping(StandardWindow window, String code, String rotated,
                                       boolean firstUnlock) {
        choicePending = false;
        shortlist.clear();
        graceDeadlineDays = -1d;
        boolean mastered = newestWasMasteredThisSeason;
        newestWasMasteredThisSeason = false;
        seasonsCompleted++;
        seasonsSincePin++;
        seasonStartDays = gameDays;
        advanceShortlistSeed();

        String msg = "New set unlocked: " + StandardWindow.setName(code) + ".";
        if (rotated != null)
            msg += "\n" + StandardWindow.setName(rotated) + " rotated out of Standard.";
        msg += "\n" + seasonDisplayName(window) + " begins.";
        notifyPlayer(msg);

        // Mastery rewards for the season that just ended (newest that was mastered).
        // Re-picking an already-mastered rotated-out set must not farm Surge / Keys / cosmetic.
        if (mastered && newestEligibleForMasteryReward) {
            // The mastered set is the previous newest — window's second-to-last after add,
            // or the set that was newest before this pick. Track via mastery of prior newest:
            // rewards were gated at endSeason; mark the set that earned them.
            String rewarded = undoSets.isEmpty() ? null : undoSets.get(undoSets.size() - 1);
            if (rewarded != null && !masteryRewardedSets.contains(rewarded)) {
                grantSeasonMasteryRewards(rewarded);
                undoGrantedMasteryRewards = true;
                undoMasteryRewardSet = rewarded;
            }
        }
        newestEligibleForMasteryReward = true;

        try {
            if (Config.ascendant() && firstUnlock) {
                ConfigData cfg = cfg();
                if (cfg == null || cfg.masteryUnlocksSetPlane) {
                    forge.adventure.world.WorldSave save = forge.adventure.world.WorldSave.getCurrentSave();
                    if (save != null) {
                        forge.adventure.world.PlanarPortalPlacer.onSetUnlocked(save, code);
                        undoOpenedPlane = true;
                    }
                }
            }
        } catch (Exception ignored) {
            // Plane generation must not break the unlock flow.
        }

        fireSeasonChanged();
    }

    private void grantSeasonMasteryRewards(String setCode) {
        if (setCode == null || masteryRewardedSets.contains(setCode))
            return;
        masteryRewardedSets.add(setCode);
        ConfigData c = cfg();
        int keys = c == null ? 1 : Math.max(0, c.sn1SeasonMasteryPlanarKeys);
        if (keys > 0) {
            planarKeys += keys;
            notifyPlayer("Season mastered — gained " + keys + " Planar Key"
                    + (keys == 1 ? "" : "s") + ".");
        }
        // Spec: cosmetic (card style or title). CS1 card styles are not applied yet —
        // grant a season title now; card-style season cosmetics remain a documented gap.
        try {
            String title = "Season: " + StandardWindow.setName(setCode);
            AchievementService.get().getProgress().addTitle(title);
            notifyPlayer("Season title unlocked: " + title + ".");
        } catch (Exception ignored) {
        }
    }

    /** Deterministic seed advance — never nanoTime (undo must not reroll the shortlist). */
    private void advanceShortlistSeed() {
        shortlistSeed = shortlistSeed * 0x5DEECE66DL + 0xBL
                + seasonsCompleted + Double.hashCode(gameDays);
    }

    /** Free undo until a pack is opened or a fight is won with the new set. */
    public String undoPick(StandardWindow window) {
        if (!undoAvailable || window == null || undoSets.isEmpty())
            return null;
        String removed = undoPicked;

        // Revert window sets.
        window.restoreSets(undoSets);
        // Only erase history when this pick was the first unlock — re-adding a
        // previously rotated-out set must keep the real unlock on undo.
        if (removed != null && undoFirstUnlock)
            window.removeFromHistory(removed);

        // Refund Choose any cost.
        if (undoChoseAny) {
            if (undoPaidWithKey)
                planarKeys++;
            if (undoGoldPaid > 0) {
                try {
                    AdventurePlayer.current().giveGold(undoGoldPaid);
                } catch (Exception ignored) {
                }
            }
        }

        // Close / revert opened plane + portal (live home or inactive home blob).
        if (undoOpenedPlane && removed != null) {
            try {
                forge.adventure.world.WorldSave save = forge.adventure.world.WorldSave.getCurrentSave();
                if (save != null)
                    forge.adventure.world.PlanarPortalPlacer.onSetUnlockUndone(save, removed);
            } catch (Exception ignored) {
            }
        }

        // Revert mastery rewards granted at pick finish (keys + title bookkeeping).
        if (undoGrantedMasteryRewards && undoMasteryRewardSet != null) {
            masteryRewardedSets.remove(undoMasteryRewardSet);
            ConfigData c = cfg();
            int keys = c == null ? 1 : Math.max(0, c.sn1SeasonMasteryPlanarKeys);
            planarKeys = Math.max(0, planarKeys - keys);
            // Title stays (cosmetic unlocks are not re-locked); keys refunded.
        }

        choicePending = true;
        seasonsCompleted = undoSeasonsCompleted;
        seasonStartDays = undoSeasonStartDays;
        // Restore pin + cooldown from before the pick (including a consumed pin).
        pinnedSet = undoPinnedSet;
        seasonsSincePin = undoSeasonsSincePin;
        // Restore the exact shortlist — do not rebuild (no reseed / nanoTime).
        shortlistSeed = undoShortlistSeed;
        shortlist.clear();
        shortlist.addAll(undoShortlist);
        releaseOrderPick = undoReleaseOrderPick;
        if (shortlist.isEmpty())
            buildShortlist(window);
        ConfigData c = cfg();
        int grace = c == null ? 3 : Math.max(0, c.sn1GracePeriodDays);
        graceDeadlineDays = grace <= 0 ? -1d : gameDays + grace;
        undoAvailable = false;
        undoSets.clear();
        undoPicked = null;
        undoRotated = null;
        undoShortlist.clear();
        undoChoseAny = false;
        undoPaidWithKey = false;
        undoGoldPaid = 0;
        undoOpenedPlane = false;
        undoFirstUnlock = false;
        undoGrantedMasteryRewards = false;
        undoMasteryRewardSet = null;
        undoPinnedSet = null;
        undoSeasonsSincePin = 99;
        notifyPlayer("Set choice undone" + (removed == null ? "." : ": " + StandardWindow.setName(removed) + "."));
        return removed;
    }

    public void lockUndo() {
        undoAvailable = false;
        undoSets.clear();
        undoPicked = null;
        undoRotated = null;
        undoShortlist.clear();
        undoChoseAny = false;
        undoPaidWithKey = false;
        undoGoldPaid = 0;
        undoOpenedPlane = false;
        undoFirstUnlock = false;
        undoGrantedMasteryRewards = false;
        undoMasteryRewardSet = null;
        undoPinnedSet = null;
        undoSeasonsSincePin = 99;
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
        data.store("sn1LastRestDay", lastRestDay);
        data.store("sn1UndoSeasonStartDays", undoSeasonStartDays);
        data.store("sn1UndoSeasonsCompleted", undoSeasonsCompleted);
        data.store("sn1UndoShortlistSeed", undoShortlistSeed);
        data.store("sn1UndoChoseAny", undoChoseAny);
        data.store("sn1UndoPaidWithKey", undoPaidWithKey);
        data.store("sn1UndoGoldPaid", undoGoldPaid);
        data.store("sn1UndoOpenedPlane", undoOpenedPlane);
        data.store("sn1UndoFirstUnlock", undoFirstUnlock);
        data.store("sn1UndoGrantedMasteryRewards", undoGrantedMasteryRewards);
        data.store("sn1UndoSeasonsSincePin", undoSeasonsSincePin);
        if (releaseOrderPick != null)
            data.store("sn1ReleaseOrderPick", releaseOrderPick);
        if (pinnedSet != null)
            data.store("sn1PinnedSet", pinnedSet);
        if (undoPicked != null)
            data.store("sn1UndoPicked", undoPicked);
        if (undoRotated != null)
            data.store("sn1UndoRotated", undoRotated);
        if (undoReleaseOrderPick != null)
            data.store("sn1UndoReleaseOrderPick", undoReleaseOrderPick);
        if (undoMasteryRewardSet != null)
            data.store("sn1UndoMasteryRewardSet", undoMasteryRewardSet);
        if (undoPinnedSet != null)
            data.store("sn1UndoPinnedSet", undoPinnedSet);
        data.storeObject("sn1Shortlist", shortlist.toArray(new String[0]));
        data.storeObject("sn1UndoSets", undoSets.toArray(new String[0]));
        data.storeObject("sn1UndoShortlist", undoShortlist.toArray(new String[0]));
        data.storeObject("sn1MasteryRewardedSets", masteryRewardedSets.toArray(new String[0]));
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
        lastRestDay = data.containsKey("sn1LastRestDay") ? data.readInt("sn1LastRestDay") : -1;
        undoSeasonStartDays = data.containsKey("sn1UndoSeasonStartDays")
                ? data.readDouble("sn1UndoSeasonStartDays") : 0d;
        undoSeasonsCompleted = data.containsKey("sn1UndoSeasonsCompleted")
                ? data.readInt("sn1UndoSeasonsCompleted") : 0;
        undoShortlistSeed = data.containsKey("sn1UndoShortlistSeed")
                ? data.readLong("sn1UndoShortlistSeed") : 0L;
        undoChoseAny = data.containsKey("sn1UndoChoseAny") && data.readBool("sn1UndoChoseAny");
        undoPaidWithKey = data.containsKey("sn1UndoPaidWithKey") && data.readBool("sn1UndoPaidWithKey");
        undoGoldPaid = data.containsKey("sn1UndoGoldPaid") ? Math.max(0, data.readInt("sn1UndoGoldPaid")) : 0;
        undoOpenedPlane = data.containsKey("sn1UndoOpenedPlane") && data.readBool("sn1UndoOpenedPlane");
        undoFirstUnlock = data.containsKey("sn1UndoFirstUnlock") && data.readBool("sn1UndoFirstUnlock");
        undoGrantedMasteryRewards = data.containsKey("sn1UndoGrantedMasteryRewards")
                && data.readBool("sn1UndoGrantedMasteryRewards");
        undoSeasonsSincePin = data.containsKey("sn1UndoSeasonsSincePin")
                ? Math.max(0, data.readInt("sn1UndoSeasonsSincePin")) : 99;
        releaseOrderPick = data.containsKey("sn1ReleaseOrderPick") ? data.readString("sn1ReleaseOrderPick") : null;
        pinnedSet = data.containsKey("sn1PinnedSet") ? data.readString("sn1PinnedSet") : null;
        undoPicked = data.containsKey("sn1UndoPicked") ? data.readString("sn1UndoPicked") : null;
        undoRotated = data.containsKey("sn1UndoRotated") ? data.readString("sn1UndoRotated") : null;
        undoReleaseOrderPick = data.containsKey("sn1UndoReleaseOrderPick")
                ? data.readString("sn1UndoReleaseOrderPick") : null;
        undoMasteryRewardSet = data.containsKey("sn1UndoMasteryRewardSet")
                ? data.readString("sn1UndoMasteryRewardSet") : null;
        undoPinnedSet = data.containsKey("sn1UndoPinnedSet") ? data.readString("sn1UndoPinnedSet") : null;
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
        undoShortlist.clear();
        if (data.containsKey("sn1UndoShortlist")) {
            String[] usl = (String[]) data.readObject("sn1UndoShortlist");
            if (usl != null)
                Collections.addAll(undoShortlist, usl);
        }
        masteryRewardedSets.clear();
        if (data.containsKey("sn1MasteryRewardedSets")) {
            String[] mrs = (String[]) data.readObject("sn1MasteryRewardedSets");
            if (mrs != null)
                Collections.addAll(masteryRewardedSets, mrs);
        }
        if (choicePending && shortlist.isEmpty() && window != null)
            buildShortlist(window);
    }
}
