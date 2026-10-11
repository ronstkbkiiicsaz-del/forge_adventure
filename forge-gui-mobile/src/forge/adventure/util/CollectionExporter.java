package forge.adventure.util;

import forge.StaticData;
import forge.adventure.player.AdventurePlayer;
import forge.card.CardEdition;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the Adventure collection and decks to plain files on every save, so they can be loaded
 * into deck-building sites (Moxfield, Archidekt, ManaBox, ...) with "only cards I own" filtering.
 * Decks built there can be pasted back through the deck editor's Import.
 *
 * <p>Output folder: Forge user folder / adventure / exports
 * <ul>
 *   <li>{@code collection.csv} — Moxfield collection CSV layout (re-import with "replace")</li>
 *   <li>{@code collection.txt} — {@code 4 Lightning Bolt (M10) 146} lines</li>
 *   <li>{@code decks/NN - name.txt} — Arena-style deck lists</li>
 *   <li>{@code unmatched.txt} — Ascendant MX1 only: cards with no Scryfall match</li>
 *   <li>{@code last_export.txt} — Ascendant MX1 only: export timestamp for Status UI</li>
 * </ul>
 */
public final class CollectionExporter {
    public static final String CSV_HEADER =
            "\"Count\",\"Tradelist Count\",\"Name\",\"Edition\",\"Condition\",\"Language\",\"Foil\",\"Tags\",\"Last Modified\",\"Collector Number\",\"Alter\",\"Proxy\",\"Purchase Price\"";
    public static final String LAST_EXPORT_FILE = "last_export.txt";
    public static final String UNMATCHED_FILE = "unmatched.txt";

    private static volatile long lastExportMillis;
    private static volatile String lastExportDisplay = "";

    private CollectionExporter() {
    }

    public static File exportDir() {
        return new File(ForgeConstants.USER_DIR, "adventure" + File.separator + "exports");
    }

    /** Epoch millis of the last successful export in this process (0 if none). */
    public static long lastExportMillis() {
        return lastExportMillis;
    }

    /** Short local-time string for Status UI, or empty when never exported this session / on disk. */
    public static String lastExportDisplay() {
        if (lastExportDisplay != null && !lastExportDisplay.isBlank()) {
            return lastExportDisplay;
        }
        loadLastExportFromDisk();
        return lastExportDisplay == null ? "" : lastExportDisplay;
    }

    public static void export(AdventurePlayer player) {
        if (player == null) {
            return;
        }
        try {
            File dir = exportDir();
            File decksDir = new File(dir, "decks");
            decksDir.mkdirs();

            final boolean mx1 = Config.ascendant() && Config.instance().getConfigData() != null
                    && Config.instance().getConfigData().mx1ScryfallExport;

            CardPool cards = player.getCards();
            List<String> unmatched = new ArrayList<>();
            try (PrintWriter csv = new PrintWriter(new File(dir, "collection.csv"), StandardCharsets.UTF_8);
                 PrintWriter txt = new PrintWriter(new File(dir, "collection.txt"), StandardCharsets.UTF_8)) {
                csv.println(CSV_HEADER);
                for (Map.Entry<PaperCard, Integer> e : cards) {
                    PaperCard pc = e.getKey();
                    if (mx1 && !hasScryfallMatch(pc)) {
                        unmatched.add(unmatchedLine(e.getValue(), pc));
                        continue;
                    }
                    String editionCol = mx1 ? scryfallEdition(pc) : pc.getEdition().toLowerCase(Locale.ROOT);
                    String collector = collectorNumberOrEmpty(pc);
                    csv.println(String.join(",", q(String.valueOf(e.getValue())), q("0"), q(pc.getName()),
                            q(editionCol), q("Near Mint"), q("English"), q(pc.isFoil() ? "foil" : ""),
                            q(""), q(""), q(collector),
                            q("False"), q("False"), q("")));
                    txt.println(line(e.getValue(), pc, mx1));
                }
            }

            File unmatchedFile = new File(dir, UNMATCHED_FILE);
            if (mx1) {
                try (PrintWriter out = new PrintWriter(unmatchedFile, StandardCharsets.UTF_8)) {
                    out.println("# Cards omitted from collection.csv (no Scryfall match)");
                    for (String row : unmatched) {
                        out.println(row);
                    }
                }
                writeLastExportMarker(dir);
            } else if (unmatchedFile.exists()) {
                // Stock export must not leave Ascendant-only artefacts from a prior mode.
                //noinspection ResultOfMethodCallIgnored
                unmatchedFile.delete();
            }

            File[] old = decksDir.listFiles((d, n) -> n.endsWith(".txt"));
            if (old != null) {
                for (File f : old) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
            for (int i = 0; i < player.getDeckCount(); i++) {
                Deck deck = player.getDeck(i);
                if (deck == null || deck.getMain().isEmpty()) {
                    continue;
                }
                String name = deck.getName() == null || deck.getName().isBlank() ? "Deck" : deck.getName();
                File f = new File(decksDir, String.format("%02d - %s.txt", i + 1, name.replaceAll("[\\\\/:*?\"<>|]", "_")));
                try (PrintWriter out = new PrintWriter(f, StandardCharsets.UTF_8)) {
                    if (deck.has(DeckSection.Commander)) {
                        out.println("Commander");
                        for (Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Commander)) {
                            out.println(line(e.getValue(), e.getKey(), mx1));
                        }
                        out.println();
                    }
                    out.println("Deck");
                    for (Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                        out.println(line(e.getValue(), e.getKey(), mx1));
                    }
                    if (deck.has(DeckSection.Sideboard) && !deck.get(DeckSection.Sideboard).isEmpty()) {
                        out.println();
                        out.println("Sideboard");
                        for (Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Sideboard)) {
                            out.println(line(e.getValue(), e.getKey(), mx1));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Collection export failed: " + e);
        }
    }

    /**
     * Moxfield / Arena paste format: {@code 1 Name (SET) 123}, with Commander, Deck and
     * Sideboard section headers as needed. Uses Scryfall set codes when available.
     */
    public static String moxfieldDeckText(Deck deck) {
        if (deck == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean needBlank = false;
        if (deck.has(DeckSection.Commander) && !deck.get(DeckSection.Commander).isEmpty()) {
            sb.append("Commander\n");
            appendMoxfieldSection(sb, deck.get(DeckSection.Commander));
            needBlank = true;
        }
        if (!deck.getMain().isEmpty()) {
            if (needBlank) {
                sb.append('\n');
            }
            sb.append("Deck\n");
            appendMoxfieldSection(sb, deck.getMain());
            needBlank = true;
        }
        if (deck.has(DeckSection.Sideboard) && !deck.get(DeckSection.Sideboard).isEmpty()) {
            if (needBlank) {
                sb.append('\n');
            }
            sb.append("Sideboard\n");
            appendMoxfieldSection(sb, deck.get(DeckSection.Sideboard));
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "" : out + "\n";
    }

    /**
     * Open a directory in the OS file manager when running on desktop.
     * Uses reflection so Android/mobile never links {@code java.awt.Desktop}.
     * @return true if the folder was opened
     */
    public static boolean openDirectory(File dir) {
        if (dir == null) {
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try {
            if (forge.gui.GuiBase.getInterface() != null
                    && !forge.gui.GuiBase.getInterface().isRunningOnDesktop()) {
                return false;
            }
            Class<?> desktopClass = Class.forName("java.awt.Desktop");
            Object isSupported = desktopClass.getMethod("isDesktopSupported").invoke(null);
            if (!(isSupported instanceof Boolean) || !((Boolean) isSupported)) {
                return false;
            }
            Object desktop = desktopClass.getMethod("getDesktop").invoke(null);
            desktopClass.getMethod("open", File.class).invoke(desktop, dir);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when the card can be listed in a Moxfield CSV (Scryfall set code + collector number). */
    public static boolean hasScryfallMatch(PaperCard pc) {
        if (pc == null) {
            return false;
        }
        CardEdition ed = resolveEdition(pc);
        if (ed == null || ed == CardEdition.UNKNOWN) {
            return false;
        }
        if (ed.getType() == CardEdition.Type.CUSTOM_SET || ed.getType() == CardEdition.Type.UNKNOWN) {
            return false;
        }
        String code = ed.getScryfallCode();
        if (code == null || code.isBlank() || CardEdition.UNKNOWN_CODE.equals(code)) {
            return false;
        }
        String cn = pc.getCollectorNumber();
        return cn != null && !cn.isBlank() && !IPaperCard.NO_COLLECTOR_NUMBER.equals(cn);
    }

    static String scryfallEdition(PaperCard pc) {
        CardEdition ed = resolveEdition(pc);
        if (ed == null) {
            return pc.getEdition() == null ? "" : pc.getEdition().toLowerCase(Locale.ROOT);
        }
        return ed.getScryfallCode().toLowerCase(Locale.ROOT);
    }

    static String scryfallEditionDisplay(PaperCard pc) {
        return scryfallEdition(pc).toUpperCase(Locale.ROOT);
    }

    private static void appendMoxfieldSection(StringBuilder sb, CardPool pool) {
        for (Map.Entry<PaperCard, Integer> e : pool) {
            sb.append(moxfieldLine(e.getValue(), e.getKey())).append('\n');
        }
    }

    static String moxfieldLine(int count, PaperCard pc) {
        String cn = collectorNumberOrEmpty(pc);
        String set = scryfallEditionDisplay(pc);
        if (set.isBlank()) {
            set = pc.getEdition() == null ? "" : pc.getEdition().toUpperCase(Locale.ROOT);
        }
        String line = count + " " + pc.getName() + " (" + set + ")";
        if (!cn.isBlank()) {
            line += " " + cn;
        }
        return line;
    }

    private static String line(int count, PaperCard pc, boolean mx1) {
        if (mx1) {
            return moxfieldLine(count, pc);
        }
        String cn = pc.getCollectorNumber();
        return count + " " + pc.getName() + " (" + pc.getEdition() + ")"
                + (cn == null || cn.isBlank() || IPaperCard.NO_COLLECTOR_NUMBER.equals(cn) ? "" : " " + cn);
    }

    private static String unmatchedLine(int count, PaperCard pc) {
        String cn = pc.getCollectorNumber();
        return count + "\t" + pc.getName() + "\t" + pc.getEdition()
                + "\t" + (cn == null ? "" : cn);
    }

    private static String collectorNumberOrEmpty(PaperCard pc) {
        String cn = pc.getCollectorNumber();
        if (cn == null || cn.isBlank() || IPaperCard.NO_COLLECTOR_NUMBER.equals(cn)) {
            return "";
        }
        return cn;
    }

    private static CardEdition resolveEdition(PaperCard pc) {
        if (pc == null || pc.getEdition() == null) {
            return null;
        }
        StaticData data = StaticData.instance();
        if (data == null || data.getEditions() == null) {
            return null;
        }
        return data.getEditions().get(pc.getEdition());
    }

    private static void writeLastExportMarker(File dir) {
        long now = System.currentTimeMillis();
        String display = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(now));
        lastExportMillis = now;
        lastExportDisplay = display;
        try {
            Files.writeString(new File(dir, LAST_EXPORT_FILE).toPath(),
                    now + "\n" + display + "\n", StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // UI can still use in-memory values for this session.
        }
    }

    private static void loadLastExportFromDisk() {
        try {
            File f = new File(exportDir(), LAST_EXPORT_FILE);
            if (!f.isFile()) {
                return;
            }
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return;
            }
            try {
                lastExportMillis = Long.parseLong(lines.get(0).trim());
            } catch (NumberFormatException ignored) {
                lastExportMillis = f.lastModified();
            }
            if (lines.size() > 1 && !lines.get(1).isBlank()) {
                lastExportDisplay = lines.get(1).trim();
            } else {
                lastExportDisplay = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(lastExportMillis));
            }
        } catch (Exception ignored) {
            // leave blank
        }
    }

    /** Test helper: clear in-memory export timestamp between cases. */
    public static void clearLastExportForTest() {
        lastExportMillis = 0L;
        lastExportDisplay = "";
    }

    private static String q(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
