package forge.adventure.util;

import forge.adventure.data.ConfigData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.card.CardRarity;
import forge.card.MagicColor;
import forge.gamemodes.limited.DraftRankCache;
import forge.item.PaperCard;

/**
 * EC1: Ascendant game-based card value (rarity + draft rank + rotation + local market).
 * Stock worlds keep {@link CardUtil#getCardPrice} rarity / price-list behaviour.
 *
 * <p><b>Co-op:</b> no wire change. Market state lives on host
 * {@link PointOfInterestChanges}; guests see host shop prices when shopping in the
 * host world. Solo-only build still owns this path.
 */
public final class AscendantCardEconomy {

    /** Fallback calendar when SN1 is not present; advanced by {@link #advanceGameDays}. */
    private static double fallbackGameDays;

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

    /** In-game days for market drift (SN1 calendar when available, else fallback). */
    public static double currentGameDays() {
        try {
            AdventurePlayer p = Current.player();
            if (p != null) {
                try {
                    Object season = p.getClass().getMethod("getSeason").invoke(p);
                    if (season != null) {
                        Object days = season.getClass().getMethod("getGameDays").invoke(season);
                        if (days instanceof Number)
                            return ((Number) days).doubleValue();
                    }
                } catch (ReflectiveOperationException ignored) {
                    // SN1 not on this branch.
                }
            }
        } catch (Exception ignored) {
            // No live player.
        }
        return fallbackGameDays;
    }

    /** Test / pre-SN1 hook: advance the fallback calendar used for market drift. */
    public static void advanceGameDays(double days) {
        if (days > 0)
            fallbackGameDays += days;
    }

    /** Test hook: set the fallback calendar. */
    public static void setFallbackGameDays(double days) {
        fallbackGameDays = Math.max(0, days);
    }

    public static void resetFallbackGameDays() {
        fallbackGameDays = 0;
    }

    /**
     * Base shop / display value in gold before shop skill modifiers.
     * Basic lands are worth nothing. Uses the current town market when {@code market} is non-null.
     */
    public static int cardValue(PaperCard card, PointOfInterestChanges market) {
        if (!enabled() || card == null)
            return 0;
        if (isPricelessBasic(card))
            return 0;
        ConfigData c = cfg();
        if (c == null)
            return 0;

        float worth = relativeWorth(card, c);
        float gold = rarityBaseGold(card, c) * worth;
        if (market != null) {
            market.driftMarketsTowardBase(currentGameDays(), c.ec1MarketDriftPerDay);
            gold *= market.marketPriceMultiplier(card.getName(), c.ec1MarketBandMin, c.ec1MarketBandMax,
                    c.ec1MarketPressureScale);
            gold *= colorLeanMultiplier(card, market.getMarketColorLean(), c.ec1ColorLeanMult);
        }
        return Math.max(0, Math.round(gold));
    }

    /** Unitless relative worth: rank × rotation (1.0 = average unranked Standard card). */
    public static float relativeWorth(PaperCard card) {
        ConfigData c = cfg();
        if (c == null || card == null)
            return 1f;
        return relativeWorth(card, c);
    }

    private static float relativeWorth(PaperCard card, ConfigData c) {
        float worth = rankMultiplier(card, c.ec1RankMultMin, c.ec1RankMultMax);
        worth *= rotationMultiplier(card, c.ec1StandardMult, c.ec1HistoricMult);
        return worth;
    }

    public static float rankMultiplier(PaperCard card, float min, float max) {
        if (card == null)
            return 1f;
        Double relative = null;
        try {
            relative = DraftRankCache.getRanking(card.getName(), card.getEdition());
        } catch (Exception ignored) {
            // Rankings folder missing in some headless setups.
        }
        if (relative == null || relative.isNaN() || relative <= 0)
            return 1f;
        float t = (float) Math.min(1.0, Math.max(0.0, relative));
        // Lower relative rank (near 0) = stronger = closer to max.
        return max + t * (min - max);
    }

    public static float rotationMultiplier(PaperCard card, float standardMult, float historicMult) {
        if (card == null)
            return 1f;
        try {
            AdventurePlayer p = Current.player();
            if (p != null && p.getStandardWindow() != null && p.getStandardWindow().isActive()) {
                return p.isStandardLegal(card) ? standardMult : historicMult;
            }
        } catch (Exception ignored) {
            // Headless / no player.
        }
        // No active Standard window → neutral (neither demand nor rotated).
        return 1f;
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
     * Dust cost scale from the shared worth function (rarity dust type stays on the card).
     * Average unranked Standard card → ~1.0 × configured craftCost.
     */
    public static float craftWorthFactor(PaperCard card) {
        if (!enabled() || card == null)
            return 1f;
        return relativeWorth(card);
    }

    /** Salvage dust scale from the shared worth function. */
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

    /**
     * Color lean: towns pay/charge more for cards of their color.
     * {@code lean} is a biome/color name or single letter (W/U/B/R/G).
     */
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
        String s = lean.trim().toLowerCase();
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
