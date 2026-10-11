package forge.adventure.util;

import forge.adventure.data.ConfigData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.player.AdventureSeason;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.card.CardRarity;
import forge.card.MagicColor;
import forge.gamemodes.limited.DraftRankCache;
import forge.item.PaperCard;

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
 */
public final class AscendantCardEconomy {

    /** Set code → season index when it left Standard (for post-rotation age). */
    private static final Map<String, Integer> rotatedAtSeason = new HashMap<>();
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
     * Bind to the live SN1 calendar (idempotent). Replaces reflection / static fallback.
     */
    public static void bindSeasonCalendar(AdventureSeason season) {
        if (boundSeason == season)
            return;
        if (boundSeason != null)
            boundSeason.removeSeasonChangeListener(SEASON_LISTENER);
        boundSeason = season;
        if (season != null)
            season.addSeasonChangeListener(SEASON_LISTENER);
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

    /** Current season index ({@link AdventureSeason#seasonIndex()}). */
    public static int currentSeasonIndex() {
        ensureBound();
        try {
            AdventurePlayer p = Current.player();
            if (p != null && p.getSeason() != null)
                return p.getSeason().seasonIndex();
        } catch (Exception ignored) {
        }
        return boundSeason != null ? boundSeason.seasonIndex() : 0;
    }

    /** Record that {@code setCode} left Standard at {@code seasonIndex}. */
    public static void noteSetRotated(String setCode, int seasonIndex) {
        if (setCode == null || setCode.isBlank())
            return;
        rotatedAtSeason.put(setCode.toUpperCase(Locale.ROOT), Math.max(0, seasonIndex));
    }

    /** Test hook: clear rotation age / spikes. */
    public static void resetLifecycleStateForTest() {
        rotatedAtSeason.clear();
        seasonSpikes.clear();
        spikesForSeason = -1;
    }

    /** Test hook: mark a set rotated at a season boundary. */
    public static void noteSetRotatedForTest(String setCode, int seasonIndex) {
        noteSetRotated(setCode, seasonIndex);
    }

    private static void onSeasonChanged(int seasonIndex, String newSet, String rotatedSet) {
        if (rotatedSet != null && !rotatedSet.isBlank())
            noteSetRotated(rotatedSet, seasonIndex);
        rollSeasonSpikes(seasonIndex);
    }

    /** Seasons since this card's edition left Standard (0 while still legal). */
    public static int seasonsOutOfStandard(PaperCard card) {
        if (card == null || isStandardLegal(card))
            return 0;
        String ed = card.getEdition();
        if (ed == null)
            return 0;
        Integer at = rotatedAtSeason.get(ed.toUpperCase(Locale.ROOT));
        if (at == null)
            return 0;
        return Math.max(0, currentSeasonIndex() - at);
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
        float base = rarityBaseGold(card, c) * rankMultiplier(card, c.ec1RankMultMin, c.ec1RankMultMax);
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

    /** Unitless relative worth vs an average unranked Standard card (for craft/salvage). */
    public static float relativeWorth(PaperCard card) {
        ConfigData c = cfg();
        if (c == null || card == null || !enabled())
            return 1f;
        float gold = lifeCycleGold(card, c);
        float avg = Math.max(1, c.ec1BaseCommon);
        return Math.max(0.05f, gold / avg);
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
        Random rng = new Random(0xEC15A1CEL ^ (((long) seasonIndex) * 0x9E3779B97F4A7C15L));
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
                forge.card.CardEdition ed = forge.model.FModel.getMagicDb().getEditions().get(code);
                if (ed == null)
                    continue;
                for (PaperCard pc : forge.model.FModel.getMagicDb().getCommonCards().getAllCards(ed)) {
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
        ensureSpikesForCurrentSeason();
        return seasonSpikes.contains(cardName);
    }

    private static void ensureSpikesForCurrentSeason() {
        int idx = currentSeasonIndex();
        if (spikesForSeason != idx)
            rollSeasonSpikes(idx);
    }

    public static List<String> currentSpikeNames() {
        ensureSpikesForCurrentSeason();
        return List.copyOf(seasonSpikes);
    }

    /** Town rumour line, or empty when nothing is spiked. */
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
            return match ? leanMult : 1f;
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
}
