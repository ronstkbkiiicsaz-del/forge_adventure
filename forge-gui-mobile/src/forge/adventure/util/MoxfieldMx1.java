package forge.adventure.util;

import forge.deck.Deck;
import forge.deck.DeckImportController;
import forge.deck.DeckRecognizer.Token;
import forge.deck.DeckRecognizer.TokenType;
import forge.deck.DeckUrlLoader;
import forge.item.PaperCard;
import forge.util.ItemPool;
import forge.util.Localizer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * MX1 helpers: Moxfield deck text, Missing-list aggregation, URL detection, Status how-to.
 * Ascendant-only behaviour is gated by callers via {@link Config#ascendant()} and ConfigData.
 *
 * <p>URL import accepts public deck pages from Moxfield plus the other providers already
 * wired in {@link DeckUrlLoader} (Archidekt, TappedOut, MTGGoldfish). There is no automatic
 * collection sync and no site automation beyond those public read APIs.
 */
public final class MoxfieldMx1 {
    private static final Pattern ANY_DECK_URL = Pattern.compile(
            "(?i)^(?:https?://)?(?:www\\.)?(?:moxfield\\.com|archidekt\\.com|tappedout\\.net|mtggoldfish\\.com)/\\S+$");

    /**
     * Stock "cannot be imported" token types for MX1, excluding
     * {@link TokenType#CARD_NOT_IN_INVENTORY} (those belong in the Missing list).
     * Includes disallowed deck sections.
     */
    public static final EnumSet<TokenType> CANNOT_IMPORT_TOKENS = EnumSet.of(
            TokenType.CARD_FROM_NOT_ALLOWED_SET,
            TokenType.CARD_FROM_INVALID_SET,
            TokenType.UNKNOWN_CARD,
            TokenType.UNSUPPORTED_CARD,
            TokenType.WARNING_MESSAGE,
            TokenType.UNSUPPORTED_DECK_SECTION);

    private MoxfieldMx1() {
    }

    public static boolean enabled() {
        return Config.ascendant() && Config.instance().getConfigData() != null
                && Config.instance().getConfigData().mx1CollectionLimitedImport;
    }

    public static boolean exportActionsEnabled() {
        return Config.ascendant() && Config.instance().getConfigData() != null
                && Config.instance().getConfigData().mx1DeckEditorExportActions;
    }

    public static boolean statusUiEnabled() {
        return Config.ascendant() && Config.instance().getConfigData() != null
                && Config.instance().getConfigData().mx1ExportStatusUi;
    }

    public static boolean looksLikeDeckUrl(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        if (t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0) {
            return false;
        }
        return ANY_DECK_URL.matcher(t).matches();
    }

    /**
     * If {@code text} is a single deck URL, load Arena-like import text through
     * {@link DeckUrlLoader} (network). Otherwise return the original text.
     */
    public static String resolveImportText(String text) throws IOException {
        if (!looksLikeDeckUrl(text)) {
            return text == null ? "" : text;
        }
        return DeckUrlLoader.loadImportText(text.trim());
    }

    /** Fixture path: parse Moxfield API JSON without network (shared loader). */
    public static String importTextFromMoxfieldJson(String json) throws IOException {
        return DeckUrlLoader.importTextFromMoxfieldJson(json);
    }

    public static String copyDeckForMoxfield(Deck deck) {
        return CollectionExporter.moxfieldDeckText(deck);
    }

    public static String exportStatusLine() {
        Localizer loc = Localizer.getInstance();
        String when = CollectionExporter.lastExportDisplay();
        if (when == null || when.isBlank()) {
            return loc.getMessage("lblCollectionNotExportedYet");
        }
        return loc.getMessage("lblCollectionExported", when);
    }

    public static String moxfieldHowToBody() {
        File dir = CollectionExporter.exportDir();
        return Localizer.getInstance().getMessage("lblMoxfieldHowToBody",
                dir.getAbsolutePath() + File.separator + "collection.csv");
    }

    /**
     * Aggregate Missing rows after {@link DeckImportController#constrainTokensToInventory()}.
     * Each entry: display name, wanted (deck-list request), owned (collection copies of that name).
     * Free basic-land fills are never listed as missing.
     */
    public static List<MissingCard> missingFromTokens(List<Token> tokens, ItemPool<PaperCard> inventory) {
        Map<String, Integer> wantedByName = new LinkedHashMap<>();
        Map<String, Boolean> hasGap = new LinkedHashMap<>();
        Map<String, PaperCard> preferredByName = new LinkedHashMap<>();
        if (tokens != null) {
            for (Token t : tokens) {
                if (t == null || !t.isCardToken()) {
                    continue;
                }
                PaperCard card = t.getCard();
                if (card == null) {
                    continue;
                }
                if (t.getType() == TokenType.FREE_CARD_NOT_IN_INVENTORY) {
                    continue;
                }
                String name = card.getName();
                wantedByName.merge(name, t.getQuantity(), Integer::sum);
                if (t.getType() == TokenType.CARD_NOT_IN_INVENTORY) {
                    hasGap.put(name, Boolean.TRUE);
                    // Keep the first requested source printing for Craft missing.
                    preferredByName.putIfAbsent(name, card);
                }
            }
        }
        List<MissingCard> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : wantedByName.entrySet()) {
            String name = e.getKey();
            if (!Boolean.TRUE.equals(hasGap.get(name))) {
                continue;
            }
            out.add(new MissingCard(name, e.getValue(), countOwnedByName(inventory, name),
                    preferredByName.get(name)));
        }
        return out;
    }

    public static String formatMissingList(List<MissingCard> missing) {
        return formatMissingList(missing, true);
    }

    /**
     * Missing list body. When {@code includeDust} and EC1 crafting is available, each row
     * includes the dust cost for the gap and a total line is appended.
     */
    public static String formatMissingList(List<MissingCard> missing, boolean includeDust) {
        if (missing == null || missing.isEmpty()) {
            return "";
        }
        Localizer loc = Localizer.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(loc.getMessage("lblMissingListHeader")).append('\n');
        boolean showDust = includeDust && Config.ascendant() && AscendantCardEconomy.enabled();
        CraftingApi.CraftMissingPlan plan = showDust
                ? CraftingApi.planCraftMissing(toCraftRequests(missing)) : null;
        for (MissingCard m : missing) {
            String base = loc.getMessage("lblMissingWantedOwned", m.wanted, m.owned);
            if (showDust) {
                int gap = Math.max(0, m.wanted - Math.max(0, m.owned));
                PaperCard pref = m.preferred;
                String dust = gap <= 0 ? "—" : formatGapDust(pref, m.name, gap);
                sb.append(String.format(Locale.ROOT, "%s — %s — %s%n", m.name, base, dust));
            } else {
                sb.append(String.format(Locale.ROOT, "%s — %s%n", m.name, base));
            }
        }
        if (showDust && plan != null && plan.totalCopies > 0 && !plan.totalDustLine().isEmpty()) {
            sb.append('\n').append(loc.getMessage("lblMissingDustTotal", plan.totalDustLine()));
            if (!plan.craftEnabled && plan.disabledReason != null && !plan.disabledReason.isBlank()) {
                sb.append('\n').append(loc.getMessage("lblCraftMissingUnavailable", plan.disabledReason));
            }
        }
        return sb.toString().trim();
    }

    private static String formatGapDust(PaperCard preferred, String name, int gap) {
        PaperCard print = CraftingApi.printing(name, preferred);
        if (print == null)
            return "—";
        int unit = CraftingApi.dustCost(print);
        if (unit <= 0)
            return "—";
        return (unit * gap) + " " + print.getRarity().getLongName() + " dust";
    }

    /** Build {@link CraftingApi.MissingRequest}s with preferred source printings. */
    public static List<CraftingApi.MissingRequest> toCraftRequests(List<MissingCard> missing) {
        List<CraftingApi.MissingRequest> out = new ArrayList<>();
        if (missing == null)
            return out;
        for (MissingCard m : missing) {
            if (m == null)
                continue;
            out.add(new CraftingApi.MissingRequest(m.name, m.wanted, m.owned, m.preferred));
        }
        return out;
    }

    /**
     * True when the parsed token list contains at least one known card token
     * (legal, free basic, inventory gap, limited, etc.). Unknown-only paste → false.
     */
    public static boolean hasKnownCardTokens(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }
        for (Token t : tokens) {
            if (t != null && t.isCardToken() && t.getCard() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build the stock-style "cannot be imported" body for MX1 (unknown cards, disallowed
     * sections, warnings). Inventory gaps are omitted — they belong in {@link #formatMissingList}.
     */
    public static String formatCannotImportList(List<Token> tokens, DeckImportController controller) {
        if (tokens == null || tokens.isEmpty() || controller == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            if (token == null || !CANNOT_IMPORT_TOKENS.contains(token.getType())) {
                continue;
            }
            if (sb.length() != 0) {
                sb.append('\n');
            }
            String message = controller.getTokenMessage(token);
            String statusMessage = controller.getTokenStatusMessage(token);
            if (statusMessage != null && !statusMessage.isBlank()) {
                sb.append(String.format("%s - (%s)", message, statusMessage));
            } else if (message != null && !message.isBlank()) {
                sb.append(message);
            } else if (statusMessage != null) {
                sb.append(statusMessage);
            }
        }
        return sb.toString().trim();
    }

    private static int countOwnedByName(ItemPool<PaperCard> inventory, String name) {
        if (inventory == null || name == null) {
            return 0;
        }
        int n = 0;
        for (Map.Entry<PaperCard, Integer> e : inventory) {
            if (name.equals(e.getKey().getName())) {
                n += e.getValue();
            }
        }
        return n;
    }

    public static final class MissingCard {
        public final String name;
        public final int wanted;
        public final int owned;
        /** First requested source printing from the deck list (may be null). */
        public final PaperCard preferred;

        public MissingCard(String name, int wanted, int owned) {
            this(name, wanted, owned, null);
        }

        public MissingCard(String name, int wanted, int owned, PaperCard preferred) {
            this.name = name;
            this.wanted = wanted;
            this.owned = owned;
            this.preferred = preferred;
        }
    }
}
