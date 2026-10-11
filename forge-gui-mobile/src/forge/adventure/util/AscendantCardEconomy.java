package forge.adventure.util;

import forge.adventure.data.ConfigData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.player.AdventureSeason;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.stage.GameHUD;
import forge.adventure.world.WorldSave;
import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.MagicColor;
import forge.gamemodes.limited.DraftRankCache;
import forge.item.PaperCard;
import forge.model.FModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * EC1: Ascendant life-cycle card value (rarity/power in Standard, post-rotation
 * appreciation toward pedigree ceilings, seasonal spikes, living town markets).
 * Stock worlds keep {@link CardUtil#getCardPrice} rarity / price-list behaviour.
 *
 * <p><b>Co-op:</b> no wire change. Market state lives on host
 * {@link PointOfInterestChanges}; guests see host shop prices when shopping in the
 * host world. Solo-only build still owns this path.
 *
 * <p>Rotation age / spikes are <b>per save</b>: persisted on the player blob, cleared
 * on new game and load, and rebound on every load (the season instance is final).
 */
public final class AscendantCardEconomy {

    /** Set code → season index when it left Standard (for post-rotation age). */
    private static final Map<String, Integer> rotatedAtSeason = new HashMap<>();
    /** Snapshot of Standard window codes at last bind / season change (to detect rotations). */
    private static final LinkedHashSet<String> lastWindowSets = new LinkedHashSet<>();
    /** Current season's spiked common/uncommon names (town rumour). */
    private static final LinkedHashSet<String> seasonSpikes = new LinkedHashSet<>();
    private static int spikesForSeason = -1;
    private static AdventureSeason boundSeason;
    private static final AdventureSeason.SeasonChangeListener SEASON_LISTENER =
            AscendantCardEconomy::onSeasonChanged;

    private AscendantCardEconomy() {
    }

    public static boolean enabled() {
        if (!Config.ascendant())
            return false;
        ConfigData cfg = cfg();
        return cfg == null || cfg.ec1GamePrices;
    }

    private static ConfigData cfg() {
        try {
            return Config.instance().getConfigData();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Bind to the live SN1 calendar. Always (re)registers the season-change listener —
     * {@link AdventurePlayer}'s season field is final, so the early same-instance return
     * would skip rebind after load.
     */
    public static void bindSeasonCalendar(AdventureSeason season) {
        if (boundSeason != null && boundSeason != season)
            boundSeason.removeSeasonChangeListener(SEASON_LISTENER);
        if (season != null)
            season.removeSeasonChangeListener(SEASON_LISTENER);
        boundSeason = season;
        if (season != null) {
            season.addSeasonChangeListener(SEASON_LISTENER);
            snapshotWindowSets();
            // Seed spikes for the current season once at bind; later rolls are listener-only.
            if (spikesForSeason != season.getSeasonIndex())
                rollSeasonSpikes(season.getSeasonIndex());
        }
    }

    private static void ensureBound() {
        if (boundSeason != null)
            return;
        try {
            AdventurePlayer p = Current.player();
            if (p != null)
                bindSeasonCalendar(p.getSeason());
        } catch (Exception ignored) {
        }
    }

    /** In-game days for market drift — SN1 {@link AdventureSeason#getGameDays()} directly. */
    public static double currentGameDays() {
        ensureBound();
        try {
            AdventurePlayer p = Current.player();
            if (p != null && p.getSeason() != null)
                return p.getSeason().getGameDays();
        } catch (Exception ignored) {
        }
        return boundSeason != null ? boundSeason.getGameDays() : 0d;
    }

    /** Current season index ({@link AdventureSeason#getSeasonIndex()}). */
    public static int currentSeasonIndex() {
        ensureBound();
        try {
            AdventurePlayer p = Current.player();
            if (p != null && p.getSeason() != null)
                return p.getSeason().getSeasonIndex();
        } catch (Exception ignored) {
        }
        return boundSeason != null ? boundSeason.getSeasonIndex() : 0;
    }

    /** Record that {@code setCode} left Standard at {@code seasonIndex}. */
    public static void noteSetRotated(String setCode, int seasonIndex) {
        if (setCode == null || setCode.isBlank())
            return;
        rotatedAtSeason.put(setCode.toUpperCase(Locale.ROOT), Math.max(0, seasonIndex));
    }

    /** Clear per-save life-cycle state (new game / before load). */
    public static void resetLifecycleState() {
        rotatedAtSeason.clear();
        lastWindowSets.clear();
        seasonSpikes.clear();
        spikesForSeason = -1;
        if (boundSeason != null) {
            boundSeason.removeSeasonChangeListener(SEASON_LISTENER);
            boundSeason = null;
        }
    }

    /** Test hook: clear rotation age / spikes. */
    public static void resetLifecycleStateForTest() {
        resetLifecycleState();
    }

    /** Test hook: mark a set rotated at a season boundary. */
    public static void noteSetRotatedForTest(String setCode, int seasonIndex) {
        noteSetRotated(setCode, seasonIndex);
    }

    /** Persist rotation age + spike rumour into the player save blob. */
    public static void save(SaveFileData data) {
        if (data == null)
            return;
        String[] keys = rotatedAtSeason.keySet().toArray(new String[0]);
        Integer[] vals = new Integer[keys.length];
        for (int i = 0; i < keys.length; i++)
            vals[i] = rotatedAtSeason.get(keys[i]);
        data.storeObject("ec1RotatedAtSeasonKeys", keys);
        data.storeObject("ec1RotatedAtSeasonVals", vals);
        data.storeObject("ec1LastWindowSets", lastWindowSets.toArray(new String[0]));
        data.storeObject("ec1SeasonSpikes", seasonSpikes.toArray(new String[0]));
        data.store("ec1SpikesForSeason", spikesForSeason);
    }

    /** Restore rotation age + spike rumour from the player save blob (after {@link #resetLifecycleState}). */
    public static void load(SaveFileData data) {
        if (data == null)
            return;
        rotatedAtSeason.clear();
        lastWindowSets.clear();
        seasonSpikes.clear();
        spikesForSeason = -1;
        if (data.containsKey("ec1RotatedAtSeasonKeys") && data.containsKey("ec1RotatedAtSeasonVals")) {
            String[] keys = (String[]) data.readObject("ec1RotatedAtSeasonKeys");
            Integer[] vals = (Integer[]) data.readObject("ec1RotatedAtSeasonVals");
            if (keys != null && vals != null) {
                int n = Math.min(keys.length, vals.length);
                for (int i = 0; i < n; i++) {
                    if (keys[i] != null && vals[i] != null)
                        rotatedAtSeason.put(keys[i].toUpperCase(Locale.ROOT), Math.max(0, vals[i]));
                }
            }
        }
        if (data.containsKey("ec1LastWindowSets")) {
            String[] win = (String[]) data.readObject("ec1LastWindowSets");
            if (win != null) {
                for (String s : win) {
                    if (s != null && !s.isBlank())
                        lastWindowSets.add(s);
                }
            }
        }
        if (data.containsKey("ec1SeasonSpikes")) {
            String[] spikes = (String[]) data.readObject("ec1SeasonSpikes");
            if (spikes != null) {
                for (String s : spikes) {
                    if (s != null && !s.isBlank())
                        seasonSpikes.add(s);
                }
            }
        }
        if (data.containsKey("ec1SpikesForSeason"))
            spikesForSeason = data.readInt("ec1SpikesForSeason");
    }

    /** SN1 {@link AdventureSeason.SeasonChangeListener}: appreciate rotations + roll spikes. */
    private static void onSeasonChanged(AdventureSeason season) {
        if (season == null)
            return;
        int seasonIndex = season.getSeasonIndex();
        noteRotationsFromWindowDiff(seasonIndex);
        rollSeasonSpikes(seasonIndex);
        showSpikeRumourNotification();
    }

    private static void snapshotWindowSets() {
        // Prefer live window; keep prior snapshot only when the player/window is unavailable.
        LinkedHashSet<String> now = new LinkedHashSet<>();
        try {
            AdventurePlayer p = Current.player();
            if (p != null && p.getStandardWindow() != null)
                now.addAll(p.getStandardWindow().getSets());
        } catch (Exception ignored) {
        }
        if (!now.isEmpty() || lastWindowSets.isEmpty()) {
            lastWindowSets.clear();
            lastWindowSets.addAll(now);
        }
    }

    /** Any set that left the Standard window since the last snapshot rotated this season. */
    private static void noteRotationsFromWindowDiff(int seasonIndex) {
        LinkedHashSet<String> now = new LinkedHashSet<>();
        try {
            AdventurePlayer p = Current.player();
            if (p != null && p.getStandardWindow() != null)
                now.addAll(p.getStandardWindow().getSets());
        } catch (Exception ignored) {
        }
        if (!lastWindowSets.isEmpty()) {
            for (String prev : lastWindowSets) {
                if (prev != null && !now.contains(prev))
                    noteSetRotated(prev, seasonIndex);
            }
        }
        lastWindowSets.clear();
        lastWindowSets.addAll(now);
    }

    /**
     * Seasons since this card's edition left Standard (0 while still legal).
     * Sets that were never in the window (Badlands / LEB, etc.) are treated as having
     * rotated at season 0 (release-order history before the campaign), so pedigree cards
     * climb with real season changes without a test hook.
     */
    public static int seasonsOutOfStandard(PaperCard card) {
        if (card == null || isStandardLegal(card))
            return 0;
        String ed = card.getEdition();
        if (ed == null)
            return 0;
        Integer at = rotatedAtSeason.get(ed.toUpperCase(Locale.ROOT));
        if (at != null)
            return Math.max(0, currentSeasonIndex() - at);
        // Never recorded: pre-campaign / never-window → age = seasons completed since NG.
        return Math.max(0, currentSeasonIndex());
    }

    /** True when this edition was never recorded leaving the live Standard window. */
    private static boolean isNeverWindowEdition(PaperCard card) {
        if (card == null || card.getEdition() == null)
            return false;
        return !rotatedAtSeason.containsKey(card.getEdition().toUpperCase(Locale.ROOT));
    }

    public static boolean isStandardLegal(PaperCard card) {
        if (card == null)
            return false;
        try {
            AdventurePlayer p = Current.player();
            if (p != null && p.getStandardWindow() != null && p.getStandardWindow().isActive())
                return p.isStandardLegal(card);
        } catch (Exception ignored) {
        }
        return true; // no window → treat as current (no post-rotation premium yet)
    }

    /**
     * Base shop / display value in gold before shop skill modifiers.
     * Basic lands are worth nothing. Uses the shop's own market when {@code market} is non-null.
     */
    public static int cardValue(PaperCard card, PointOfInterestChanges market) {
        if (!enabled() || card == null)
            return 0;
        if (isPricelessBasic(card))
            return 0;
        ConfigData c = cfg();
        if (c == null)
            return 0;

        float gold = lifeCycleGold(card, c);
        if (market != null) {
            market.driftMarketsTowardBase(currentGameDays(), c.ec1MarketDriftPerDay);
            gold *= market.marketPriceMultiplier(card.getName(), c.ec1MarketBandMin, c.ec1MarketBandMax,
                    c.ec1MarketPressureScale);
            gold *= colorLeanMultiplier(card, market.getMarketColorLean(), c.ec1ColorLeanMult);
        }
        return Math.max(0, Math.round(gold));
    }

    /**
     * Life-cycle gold before local market: Standard = rarity×power; after rotation,
     * appreciate each season (rares/mythics faster), approaching pedigree ceiling from
     * {@code cardprices.txt} over many seasons — never as an immediate price.
     */
    public static float lifeCycleGold(PaperCard card, ConfigData c) {
        // Steve: a live / never-window Badlands is an ordinary ~300 rare — not a draft-rank jackpot.
        // Power rank applies to cards that are (or were) in the Standard window; never-window
        // pedigree cards start at rarity base and climb via the life-cycle curve only.
        float base = standardBaselineGold(card, c);
        if (isSpiked(card.getName()))
            base *= Math.max(1f, c.ec1SeasonSpikeMult);

        if (isStandardLegal(card))
            return base;

        int seasonsOut = seasonsOutOfStandard(card);
        if (seasonsOut <= 0)
            return base;

        float rate = postRotationRate(card, c) * powerAppreciateScale(card, c);
        float appreciated = base * (float) Math.pow(1.0 + rate, seasonsOut);

        Integer list = pedigreeListPrice(card.getName());
        if (list == null || list <= 0)
            return appreciated;

        float ceiling = list * Math.max(0f, c.ec1PedigreeCeilingShare);
        if (ceiling <= base)
            return Math.max(appreciated, base);

        // Ease from Standard base toward the pedigree ceiling over many seasons.
        int toCeiling = Math.max(1, c.ec1PedigreeSeasonsToCeiling);
        float t = Math.min(1f, seasonsOut / (float) toCeiling);
        // Smoothstep: slow start, approaches ceiling; never jumps to list price day-one.
        float ease = t * t * (3f - 2f * t);
        float towardCeiling = base + (ceiling - base) * ease;
        // Also allow the compound path for non-ceiling growth, but never exceed the ceiling.
        return Math.min(ceiling, Math.max(appreciated, towardCeiling));
    }

    private static float postRotationRate(PaperCard card, ConfigData c) {
        return switch (effectiveRarity(card)) {
            case Common -> Math.max(0f, c.ec1PostRotationAppreciateCommon);
            case Uncommon -> Math.max(0f, c.ec1PostRotationAppreciateUncommon);
            case Rare, Special -> Math.max(0f, c.ec1PostRotationAppreciateRare);
            case MythicRare -> Math.max(0f, c.ec1PostRotationAppreciateMythic);
            default -> Math.max(0f, c.ec1PostRotationAppreciateUncommon);
        };
    }

    /** Stronger cards (better draft rank) appreciate a bit faster after rotation. */
    private static float powerAppreciateScale(PaperCard card, ConfigData c) {
        float rank = rankMultiplier(card, c.ec1RankMultMin, c.ec1RankMultMax);
        float mid = 0.5f * (c.ec1RankMultMin + c.ec1RankMultMax);
        if (mid <= 0f)
            return 1f;
        return Math.max(0.5f, Math.min(1.75f, rank / mid));
    }

    private static Integer pedigreeListPrice(String name) {
        if (name == null)
            return null;
        try {
            AdventureReadPriceList.PriceData data = AdventureReadPriceList.loadPrices();
            if (data == null || data.prices == null)
                return null;
            return data.prices.get(name);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Craft/salvage worth multiplier: {@code lifeCycleGold / standardBaselineGold}.
     * The baseline is that rarity's Standard gold (rarity base × power rank) <b>before</b>
     * spikes / rotation / pedigree — so an in-Standard card (no spike) is exactly
     * {@code 1.0} and dust costs match the original pre-EC1 {@code craftCost} /
     * {@code salvageDust} of that rarity's dust. Only life-cycle (appreciation,
     * pedigree, spikes) moves the multiplier; Historic ×factor stays on the craft path.
     */
    public static float relativeWorth(PaperCard card) {
        ConfigData c = cfg();
        if (c == null || card == null || !enabled())
            return 1f;
        float gold = lifeCycleGold(card, c);
        float baseline = standardBaselineGold(card, c);
        return Math.max(0.05f, gold / baseline);
    }

    /**
     * In-Standard gold before spikes / rotation / pedigree (rarity base × power).
     * Never-window editions use rank 1 so Badlands starts as an ordinary rare.
     */
    public static float standardBaselineGold(PaperCard card, ConfigData c) {
        if (card == null || c == null)
            return 1f;
        float rank = rankMultiplier(card, c.ec1RankMultMin, c.ec1RankMultMax);
        if (!isStandardLegal(card) && isNeverWindowEdition(card))
            rank = 1f;
        return Math.max(1f, rarityBaseGold(card, c) * rank);
    }

    public static float rankMultiplier(PaperCard card, float min, float max) {
        if (card == null)
            return 1f;
        Double relative = null;
        try {
            relative = DraftRankCache.getRanking(card.getName(), card.getEdition());
        } catch (Exception ignored) {
        }
        if (relative == null || relative.isNaN() || relative <= 0)
            return 1f;
        float t = (float) Math.min(1.0, Math.max(0.0, relative));
        return max + t * (min - max);
    }

    public static int rarityBaseGold(PaperCard card, ConfigData c) {
        CardRarity r = effectiveRarity(card);
        if (r == CardRarity.BasicLand)
            return Math.max(0, c.ec1BaseBasicLand);
        return switch (r) {
            case Common -> Math.max(0, c.ec1BaseCommon);
            case Uncommon -> Math.max(0, c.ec1BaseUncommon);
            case Rare -> Math.max(0, c.ec1BaseRare);
            case MythicRare -> Math.max(0, c.ec1BaseMythic);
            case Special -> Math.max(0, c.ec1BaseSpecial);
            default -> Math.max(0, c.ec1BaseSpecial);
        };
    }

    public static CardRarity effectiveRarity(PaperCard card) {
        if (card == null)
            return CardRarity.Special;
        CardRarity r = card.getRarity();
        if (r == CardRarity.BasicLand
                && !card.isVeryBasicLand()
                && !MagicColor.Constant.SNOW_LANDS.contains(card.getName())
                && !"Wastes".equals(card.getName())) {
            return CardRarity.Common;
        }
        return r;
    }

    public static boolean isPricelessBasic(PaperCard card) {
        if (card == null)
            return true;
        if (card.getRarity() == CardRarity.BasicLand)
            return true;
        return card.getRules() != null && card.getRules().getType().isBasicLand();
    }

    /**
     * Dust cost scale from the shared life-cycle worth. Historic cards also keep
     * {@link ConfigData#historicCraftFactor} on the craft path (not dead).
     */
    public static float craftWorthFactor(PaperCard card) {
        if (!enabled() || card == null)
            return 1f;
        return relativeWorth(card);
    }

    public static float salvageWorthFactor(PaperCard card) {
        if (!enabled() || card == null)
            return 1f;
        ConfigData c = cfg();
        float worth = relativeWorth(card);
        if (c != null)
            worth *= Math.max(0f, c.ec1SalvageWorthFactor);
        return worth;
    }

    public static float saleShare() {
        ConfigData c = cfg();
        if (c == null)
            return 0.3f;
        return Math.max(0f, Math.min(1f, c.ec1SaleShare));
    }

    /** Cap on skill sell multiplier so pumped markets cannot be looped. */
    public static float sellSkillCap() {
        ConfigData c = cfg();
        if (c == null)
            return 1.35f;
        return Math.max(1f, c.ec1SellSkillCap);
    }

    public static void noteSold(PointOfInterestChanges market, PaperCard card, int copies) {
        if (!enabled() || market == null || card == null || copies < 1 || isPricelessBasic(card))
            return;
        ConfigData c = cfg();
        float delta = c == null ? 0.15f : c.ec1MarketSellDelta;
        market.addMarketSupply(card.getName(), copies * delta);
    }

    public static void noteBought(PointOfInterestChanges market, PaperCard card, int copies) {
        if (!enabled() || market == null || card == null || copies < 1 || isPricelessBasic(card))
            return;
        ConfigData c = cfg();
        float delta = c == null ? 0.15f : c.ec1MarketBuyDelta;
        market.addMarketSupply(card.getName(), -copies * delta);
    }

    // ---- Seasonal spikes / rumour ---------------------------------------------------------------

    public static void rollSeasonSpikes(int seasonIndex) {
        if (!enabled())
            return;
        if (spikesForSeason == seasonIndex && !seasonSpikes.isEmpty())
            return;
        seasonSpikes.clear();
        spikesForSeason = seasonIndex;
        ConfigData c = cfg();
        int count = c == null ? 3 : Math.max(0, c.ec1SeasonSpikeCount);
        if (count <= 0)
            return;
        List<PaperCard> pool = spikeCandidates();
        if (pool.isEmpty())
            return;
        long worldSeed = 0L;
        try {
            WorldSave save = WorldSave.getCurrentSave();
            if (save != null && save.getWorld() != null)
                worldSeed = save.getWorld().getSeed();
        } catch (Exception ignored) {
        }
        Random rng = new Random(0xEC15A1CEL
                ^ (((long) seasonIndex) * 0x9E3779B97F4A7C15L)
                ^ worldSeed);
        Collections.shuffle(pool, rng);
        for (int i = 0; i < pool.size() && seasonSpikes.size() < count; i++) {
            seasonSpikes.add(pool.get(i).getName());
        }
    }

    /** Test hook: force the current rumour list. */
    public static void setSeasonSpikesForTest(List<String> names) {
        seasonSpikes.clear();
        if (names != null)
            seasonSpikes.addAll(names);
        spikesForSeason = currentSeasonIndex();
    }

    private static List<PaperCard> spikeCandidates() {
        List<PaperCard> out = new ArrayList<>();
        try {
            AdventurePlayer p = Current.player();
            if (p == null || p.getStandardWindow() == null || !p.getStandardWindow().isActive())
                return out;
            Set<String> codes = new LinkedHashSet<>(p.getStandardWindow().expandedCodes());
            for (String code : codes) {
                CardEdition ed = FModel.getMagicDb().getEditions().get(code);
                if (ed == null)
                    continue;
                for (PaperCard pc : FModel.getMagicDb().getCommonCards().getAllCards(ed)) {
                    if (pc == null)
                        continue;
                    CardRarity r = pc.getRarity();
                    if (r != CardRarity.Common && r != CardRarity.Uncommon)
                        continue;
                    if (isPricelessBasic(pc))
                        continue;
                    out.add(pc);
                    if (out.size() > 400)
                        return out;
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean isSpiked(String cardName) {
        if (cardName == null || seasonSpikes.isEmpty())
            return false;
        return seasonSpikes.contains(cardName);
    }

    public static List<String> currentSpikeNames() {
        return List.copyOf(seasonSpikes);
    }

    /** Town / inn / HUD rumour line, or empty when nothing is spiked. */
    public static String spikeRumourLine() {
        List<String> names = currentSpikeNames();
        if (names.isEmpty())
            return "";
        try {
            return forge.util.Localizer.getInstance().getMessage("lblEc1SpikeRumour",
                    String.join(", ", names));
        } catch (Exception e) {
            return names.get(0) + " is in demand.";
        }
    }

    /** Push the current spike rumour to the HUD (season change / inn enter). */
    public static void showSpikeRumourNotification() {
        String line = spikeRumourLine();
        if (line == null || line.isBlank())
            return;
        try {
            GameHUD.getInstance().addNotification(line);
        } catch (Exception ignored) {
        }
    }

    public static float colorLeanMultiplier(PaperCard card, String lean, float leanMult) {
        if (card == null || lean == null || lean.isEmpty() || leanMult == 1f)
            return 1f;
        String color = normalizeLean(lean);
        if (color == null)
            return 1f;
        try {
            byte identity = card.getRules() != null ? card.getRules().getColorIdentity().getColor() : 0;
            boolean match = switch (color) {
                case "W" -> (identity & MagicColor.WHITE) != 0;
                case "U" -> (identity & MagicColor.BLUE) != 0;
                case "B" -> (identity & MagicColor.BLACK) != 0;
                case "R" -> (identity & MagicColor.RED) != 0;
                case "G" -> (identity & MagicColor.GREEN) != 0;
                default -> false;
            };
            return match ? Math.max(1f, leanMult) : 1f;
        } catch (Exception e) {
            return 1f;
        }
    }

    public static String normalizeLean(String lean) {
        if (lean == null || lean.isEmpty())
            return null;
        String s = lean.trim().toLowerCase(Locale.ROOT);
        if (s.length() == 1) {
            char c = Character.toUpperCase(s.charAt(0));
            if ("WUBRG".indexOf(c) >= 0)
                return String.valueOf(c);
            return null;
        }
        if (s.contains("white") || s.equals("plains"))
            return "W";
        if (s.contains("blue") || s.equals("island"))
            return "U";
        if (s.contains("black") || s.equals("swamp"))
            return "B";
        if (s.contains("red") || s.equals("mountain"))
            return "R";
        if (s.contains("green") || s.equals("forest"))
            return "G";
        return null;
    }

    /** Test / debug: copy of the rotation map. */
    public static Map<String, Integer> rotatedAtSeasonSnapshotForTest() {
        return Map.copyOf(rotatedAtSeason);
    }
}
