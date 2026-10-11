package forge.adventure.util;

import forge.deck.Deck;
import forge.deck.DeckImportController;
import forge.deck.DeckRecognizer.Token;
import forge.deck.DeckRecognizer.TokenType;
import forge.deck.DeckUrlLoader;
import forge.item.PaperCard;
import forge.util.ItemPool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * MX1 helpers: Moxfield deck text, Missing-list aggregation, URL detection, Status how-to.
 * Ascendant-only behaviour is gated by callers via {@link Config#ascendant()} and ConfigData.
 */
public final class MoxfieldMx1 {
    private static final Pattern MOXFIELD_URL = Pattern.compile(
            "(?i)^(?:https?://)?(?:www\\.)?moxfield\\.com/decks/\\S+$");
    private static final Pattern ANY_DECK_URL = Pattern.compile(
            "(?i)^(?:https?://)?(?:www\\.)?(?:moxfield\\.com|archidekt\\.com|tappedout\\.net|mtggoldfish\\.com)/\\S+$");

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
        return ANY_DECK_URL.matcher(t).matches() || MOXFIELD_URL.matcher(t).matches();
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
        String when = CollectionExporter.lastExportDisplay();
        if (when == null || when.isBlank()) {
            return "Collection not exported yet (save the game to refresh exports)";
        }
        return "Collection exported " + when;
    }

    public static String moxfieldHowToBody() {
        File dir = CollectionExporter.exportDir();
        return "Moxfield upload (manual — Forge never syncs automatically):\n\n"
                + "1. Save your game (writes adventure/exports/collection.csv).\n"
                + "2. In Moxfield: Collection → Import → CSV → choose \"replace\".\n"
                + "3. Pick this file:\n   " + dir.getAbsolutePath() + File.separator + "collection.csv\n\n"
                + "Build decks on Moxfield against that collection, then paste the list or a "
                + "public Moxfield deck URL into Import here. Only cards you own are imported.\n\n"
                + "Cards Forge cannot map to Scryfall are listed in unmatched.txt and omitted from the CSV.";
    }

    /**
     * Aggregate Missing rows after {@link DeckImportController#constrainTokensToInventory()}.
     * Each entry: display name, wanted (deck-list request), owned (collection copies of that name).
     * Free basic-land fills are never listed as missing.
     */
    public static List<MissingCard> missingFromTokens(List<Token> tokens, ItemPool<PaperCard> inventory) {
        Map<String, Integer> wantedByName = new LinkedHashMap<>();
        Map<String, Boolean> hasGap = new LinkedHashMap<>();
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
                }
            }
        }
        List<MissingCard> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : wantedByName.entrySet()) {
            String name = e.getKey();
            if (!Boolean.TRUE.equals(hasGap.get(name))) {
                continue;
            }
            out.add(new MissingCard(name, e.getValue(), countOwnedByName(inventory, name)));
        }
        return out;
    }

    public static String formatMissingList(List<MissingCard> missing) {
        if (missing == null || missing.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Missing (name, wanted, owned):\n");
        for (MissingCard m : missing) {
            sb.append(String.format(Locale.ROOT, "%s — wanted %d, owned %d%n", m.name, m.wanted, m.owned));
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

        public MissingCard(String name, int wanted, int owned) {
            this.name = name;
            this.wanted = wanted;
            this.owned = owned;
        }
    }
}
