package forge.adventure;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import forge.ImageKeys;
import forge.StaticData;
import forge.adventure.data.ConfigData;
import forge.adventure.player.AccountStore;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.CollectionExporter;
import forge.adventure.util.Config;
import forge.adventure.util.MoxfieldMx1;
import forge.adventure.world.WorldSave;
import forge.card.CardEdition;
import forge.deck.Deck;
import forge.deck.DeckImportController;
import forge.deck.DeckRecognizer.Token;
import forge.deck.DeckRecognizer.TokenType;
import forge.deck.DeckSection;
import forge.deck.DeckUrlLoader;
import forge.game.GameFormat;
import forge.gui.interfaces.ICheckBox;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.util.ItemPool;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MX1: Moxfield collection export (Scryfall codes) and collection-limited import.
 * Uses Surefire {@code forge.test.userDir}; never writes the real user folder.
 * URL import goes through {@link DeckUrlLoader#importTextFromMoxfieldJson} (fixture, no network).
 */
@Test(singleThreaded = true)
public class MoxfieldMx1Test {

    private static Path realUserDir;
    private static Map<String, AdventureTestUserDir.FileStamp> realUserDirSnapshot;
    private static StaticData magicDb;
    private static StaticData previousStaticData;
    private static ConfigData ascendantConfig;
    private static ConfigData stockConfig;
    private static String initError;

    @BeforeClass
    public void bootstrapConfigAndCardDb() {
        try {
            realUserDir = AdventureTestUserDir.defaultRealUserDir();
            realUserDirSnapshot = AdventureTestUserDir.snapshot(realUserDir);
            AdventureTestUserDir.requireIsolatedUserDir();
            AccountStore.setAdventureRootOverrideForTest(new File(ForgeConstants.USER_ADVENTURE_DIR));

            Path forgeGuiDir = resolveForgeGuiDir();
            Assert.assertTrue(Files.isDirectory(forgeGuiDir.resolve("res/editions")),
                    "editions dir missing under " + forgeGuiDir);

            try {
                Lang.createInstance("en-US");
                String langDir = forgeGuiDir.resolve("res/languages").toAbsolutePath().normalize()
                        + File.separator;
                Localizer.getInstance().initialize("en-US", langDir);
                ImageKeys.initializeDirs("", new HashMap<>(), "", "", "", "", "", "", "");
            } catch (Throwable ignored) {
            }

            previousStaticData = readStaticDataInstance();
            ensureFModelMagicDb();
            magicDb = FModel.getMagicDb();
            Assert.assertNotNull(magicDb, "FModel magic DB required for MX1");
            Assert.assertNotNull(magicDb.getCommonCards(), "FModel common cards required");
            pinStaticData(magicDb);

            Path cfgPath = forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/config.json");
            Assert.assertTrue(Files.isRegularFile(cfgPath), "Ascendant config missing: " + cfgPath);
            ascendantConfig = new Json().fromJson(ConfigData.class, new FileHandle(cfgPath.toFile()));
            Assert.assertNotNull(ascendantConfig);
            Assert.assertTrue(ascendantConfig.ascendantRules);
            Assert.assertTrue(ascendantConfig.mx1ScryfallExport, "Ascendant config must enable MX1 export");
            Assert.assertTrue(ascendantConfig.mx1CollectionLimitedImport);

            stockConfig = new ConfigData();
            stockConfig.ascendantRules = false;

            Config.resetInstanceForTest();
            Config.instance();
            Config.installConfigDataForTest(ascendantConfig);
            Assert.assertTrue(Config.ascendant());
        } catch (Throwable t) {
            initError = t.getClass().getSimpleName() + ": " + t.getMessage();
            t.printStackTrace();
        }
    }

    private static void ensureFModelMagicDb() {
        try {
            if (FModel.getMagicDb() != null && FModel.getMagicDb().getCommonCards() != null) {
                return;
            }
        } catch (Throwable ignored) {
        }
        FModel.initialize(null, preferences -> {
            preferences.setPref(
                    forge.localinstance.properties.ForgePreferences.FPref.LOAD_CARD_SCRIPTS_LAZILY, true);
            preferences.setPref(
                    forge.localinstance.properties.ForgePreferences.FPref.UI_LANGUAGE, "en-US");
            preferences.setPref(
                    forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY, false);
            return null;
        });
    }

    @AfterClass(alwaysRun = true)
    public void restorePinnedStateAndAssertRealUserDirUntouched() throws Exception {
        try {
            pinStaticData(previousStaticData);
            AccountStore.resetAdventureRootOverrideForTest();
            Config.resetInstanceForTest();
            CollectionExporter.clearLastExportForTest();
        } finally {
            if (realUserDirSnapshot != null) {
                AdventureTestUserDir.assertUnchanged(realUserDir, realUserDirSnapshot, "MoxfieldMx1Test");
            }
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        Assert.assertNull(initError, "MX1 BeforeClass bootstrap failed: " + initError);
        Assert.assertNotNull(magicDb, "MX1 card DB not loaded");
        pinStaticData(magicDb);
        AdventureTestUserDir.requireIsolatedUserDir();
        Config.resetInstanceForTest();
        Config.instance();
        Config.installConfigDataForTest(ascendantConfig);
        Assert.assertTrue(Config.ascendant());
        CollectionExporter.clearLastExportForTest();
        clearExportDir();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        clearExportDir();
        CollectionExporter.clearLastExportForTest();
        AdventureTestUserDir.assertUnchanged(realUserDir, realUserDirSnapshot, "MoxfieldMx1Test method");
    }

    @Test
    public void csvUsesScryfallCodesNotForgeCodeWhenTheyDiffer() throws Exception {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();

        // Conflux: Forge code CFX, Scryfall CON
        CardEdition conflux = magicDb.getEditions().get("CFX");
        Assert.assertNotNull(conflux);
        Assert.assertEquals(conflux.getScryfallCode().toLowerCase(Locale.ROOT), "con");
        Assert.assertNotEquals(conflux.getCode().toLowerCase(Locale.ROOT), "con");

        PaperCard card = firstCardWithCollectorNumber(conflux.getCode());
        Assert.assertNotNull(card, "Need a Conflux card with a collector number");
        player.addCard(card, 2);

        CollectionExporter.export(player);

        Path csv = CollectionExporter.exportDir().toPath().resolve("collection.csv");
        Assert.assertTrue(Files.isRegularFile(csv));
        String body = Files.readString(csv, StandardCharsets.UTF_8);
        Assert.assertTrue(body.startsWith(CollectionExporter.CSV_HEADER)
                        || body.lines().findFirst().orElse("").equals(CollectionExporter.CSV_HEADER),
                "CSV must use Moxfield header");
        Assert.assertTrue(body.toLowerCase(Locale.ROOT).contains(",\"con\","),
                "Edition column must be Scryfall code 'con', got:\n" + body);
        Assert.assertFalse(body.toLowerCase(Locale.ROOT).contains(",\"cfx\","),
                "Edition column must not use Forge code CFX");
        Assert.assertTrue(body.contains(card.getName()));
        Assert.assertTrue(Files.isRegularFile(
                CollectionExporter.exportDir().toPath().resolve(CollectionExporter.LAST_EXPORT_FILE)));
    }

    @Test
    public void moxfieldSampleCsvRoundTripsThroughExporterShape() throws Exception {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();

        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        Assert.assertNotNull(bolt, "M10 Lightning Bolt 146 required");
        player.addCard(bolt, 4);

        PaperCard counter = magicDb.getCommonCards().getCard("Counterspell", "MH2", "267");
        if (counter == null) {
            counter = magicDb.getCommonCards().getCard("Counterspell", "MH2");
        }
        Assert.assertNotNull(counter, "MH2 Counterspell required");
        player.addCard(counter, 2);

        CollectionExporter.export(player);
        String exported = Files.readString(
                CollectionExporter.exportDir().toPath().resolve("collection.csv"), StandardCharsets.UTF_8);
        String sample = readResource("forge/adventure/mx1/moxfield-collection-sample.csv");

        Assert.assertEquals(exported.lines().findFirst().orElse(""), sample.lines().findFirst().orElse(""),
                "Header must match Moxfield sample");
        Assert.assertTrue(exported.contains("Lightning Bolt"));
        Assert.assertTrue(exported.toLowerCase(Locale.ROOT).contains(",\"m10\","));
        Assert.assertTrue(exported.contains("\"146\""));
        // Moxfield sample Lightning Bolt row must round-trip exactly.
        String boltRow = "\"4\",\"0\",\"Lightning Bolt\",\"m10\",\"Near Mint\",\"English\",\"\",\"\",\"\",\"146\",\"False\",\"False\",\"\"";
        Assert.assertTrue(exported.replace("\r", "").contains(boltRow),
                "Exported CSV should include Moxfield sample Lightning Bolt row:\n" + exported);
        Assert.assertTrue(sample.contains(boltRow));
        if (counter != null && "267".equals(counter.getCollectorNumber())) {
            String counterRow = "\"2\",\"0\",\"Counterspell\",\"mh2\",\"Near Mint\",\"English\",\"\",\"\",\"\",\"267\",\"False\",\"False\",\"\"";
            Assert.assertTrue(exported.replace("\r", "").contains(counterRow), exported);
        }
    }

    @Test
    public void unmatchedCardsListedAndOmittedFromCsv() throws Exception {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();

        PaperCard good = firstCardWithCollectorNumber("M11");
        Assert.assertNotNull(good);
        player.addCard(good, 1);

        // Synthetic custom-edition card with no Scryfall match.
        PaperCard custom = new PaperCard(good.getRules(), CardEdition.UNKNOWN_CODE, good.getRarity());
        player.getCards().add(custom, 1);

        CollectionExporter.export(player);
        String csv = Files.readString(
                CollectionExporter.exportDir().toPath().resolve("collection.csv"), StandardCharsets.UTF_8);
        String unmatched = Files.readString(
                CollectionExporter.exportDir().toPath().resolve(CollectionExporter.UNMATCHED_FILE),
                StandardCharsets.UTF_8);

        Assert.assertTrue(csv.contains(good.getName()));
        Assert.assertTrue(unmatched.contains(custom.getName()) || unmatched.contains(CardEdition.UNKNOWN_CODE),
                "unmatched.txt should list the unknown printing:\n" + unmatched);
        Assert.assertFalse(csv.contains(",\"???\",") || csv.contains(CardEdition.UNKNOWN_CODE),
                "CSV must omit unmatched editions");
    }

    @Test
    public void pastedFourOfWhenOwningTwoImportsTwoAndListsTwoMissing() {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();

        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        Assert.assertNotNull(bolt);
        player.addCard(bolt, 2);

        DeckImportController controller = newController(player.getCards());
        List<Token> tokens = controller.parseInput("Deck\n4 Lightning Bolt (M10) 146\n");
        tokens = controller.constrainTokensToInventory();

        int legal = 0;
        int missingQty = 0;
        for (Token t : tokens) {
            if (t.getCard() == null || !"Lightning Bolt".equals(t.getCard().getName())) {
                continue;
            }
            if (t.getType() == TokenType.LEGAL_CARD || t.getType() == TokenType.LIMITED_CARD) {
                legal += t.getQuantity();
            }
            if (t.getType() == TokenType.CARD_NOT_IN_INVENTORY) {
                missingQty += t.getQuantity();
            }
        }
        Assert.assertEquals(legal, 2, "should import owned copies");
        Assert.assertEquals(missingQty, 2, "should list unowned copies as missing");

        List<MoxfieldMx1.MissingCard> missing = MoxfieldMx1.missingFromTokens(tokens, player.getCards());
        Assert.assertEquals(missing.size(), 1);
        Assert.assertEquals(missing.get(0).name, "Lightning Bolt");
        Assert.assertEquals(missing.get(0).wanted, 4);
        Assert.assertEquals(missing.get(0).owned, 2);

        Deck accepted = acceptTokens(tokens);
        Assert.assertEquals(accepted.getAllCardsInASinglePool().countAll(), 2,
                "accepted deck should keep owned copies");
    }

    @Test
    public void unownedRequestedPrintingFallsBackToOwnedPrinting() {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();

        PaperCard owned = magicDb.getCommonCards().getCard("Lightning Bolt", "M11");
        Assert.assertNotNull(owned, "M11 Lightning Bolt");
        // Prefer a printing that is not M10.
        Assert.assertNotEquals(owned.getEdition().toUpperCase(Locale.ROOT), "M10",
                "need a non-M10 owned printing for fallback");
        player.addCard(owned, 3);

        DeckImportController controller = newController(player.getCards());
        List<Token> tokens = controller.parseInput("Deck\n2 Lightning Bolt (M10) 146\n");
        tokens = controller.constrainTokensToInventory();

        int legal = 0;
        PaperCard used = null;
        for (Token t : tokens) {
            if (t.getType() == TokenType.LEGAL_CARD && "Lightning Bolt".equals(t.getCard().getName())) {
                legal += t.getQuantity();
                used = t.getCard();
            }
            Assert.assertNotEquals(t.getType(), TokenType.CARD_NOT_IN_INVENTORY,
                    "fallback should satisfy the request from owned printings");
        }
        Assert.assertEquals(legal, 2);
        Assert.assertNotNull(used);
        Assert.assertEquals(used.getEdition(), owned.getEdition());
        Assert.assertTrue(MoxfieldMx1.missingFromTokens(tokens, player.getCards()).isEmpty());
    }

    @Test
    public void urlImportParsedThroughSharedLoaderFixtureNoNetwork() throws Exception {
        String json = readResource("forge/adventure/mx1/moxfield-deck.json");
        String importText = DeckUrlLoader.importTextFromMoxfieldJson(json);
        Assert.assertTrue(importText.toLowerCase(Locale.ROOT).contains("lightning bolt"), importText);
        Assert.assertTrue(importText.toLowerCase(Locale.ROOT).contains("sideboard")
                        || importText.contains("Sideboard")
                        || importText.contains(DeckSection.Sideboard.name()),
                importText);
        Assert.assertTrue(importText.contains("[M10]") || importText.toUpperCase(Locale.ROOT).contains("[M10]"),
                "fixture should carry set code:\n" + importText);

        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();
        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        Assert.assertNotNull(bolt);
        player.addCard(bolt, 4);

        DeckImportController controller = newController(player.getCards());
        List<Token> tokens = controller.parseInput(importText);
        tokens = controller.constrainTokensToInventory();

        int bolts = 0;
        for (Token t : tokens) {
            if (t.isCardTokenForDeck() && t.getCard() != null
                    && "Lightning Bolt".equals(t.getCard().getName())) {
                bolts += t.getQuantity();
            }
        }
        Assert.assertEquals(bolts, 4, "fixture mainboard bolts should import from owned copies");
    }

    @Test
    public void moxfieldDeckTextUsesScryfallSetAndSideboard() {
        Deck deck = new Deck("Test");
        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        PaperCard negate = magicDb.getCommonCards().getCard("Negate", "M20");
        Assert.assertNotNull(bolt);
        Assert.assertNotNull(negate);
        deck.getMain().add(bolt, 1);
        deck.getOrCreate(DeckSection.Sideboard).add(negate, 2);

        String text = CollectionExporter.moxfieldDeckText(deck);
        Assert.assertTrue(text.contains("1 Lightning Bolt (M10) 146")
                        || text.matches("(?s).*1 Lightning Bolt \\(M10\\) 146.*"),
                text);
        Assert.assertTrue(text.contains("Sideboard"), text);
        Assert.assertTrue(text.contains("Negate"), text);
    }

    @Test
    public void moxfieldDeckTextIncludesCommanderSection() {
        Deck deck = new Deck("EDH");
        PaperCard commander = magicDb.getCommonCards().getCard("Omnath, Locus of Creation");
        if (commander == null) {
            commander = magicDb.getCommonCards().getCard("Atraxa, Praetors' Voice");
        }
        Assert.assertNotNull(commander, "Need a legendary creature for Commander section");
        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        Assert.assertNotNull(bolt);
        deck.getOrCreate(DeckSection.Commander).add(commander, 1);
        deck.getMain().add(bolt, 1);

        String text = CollectionExporter.moxfieldDeckText(deck);
        Assert.assertTrue(text.startsWith("Commander\n") || text.contains("Commander\n"), text);
        Assert.assertTrue(text.contains(commander.getName()), text);
        Assert.assertTrue(text.contains("Deck\n"), text);
        Assert.assertTrue(text.contains("Lightning Bolt"), text);
    }

    @Test
    public void importIntoNonEmptyDeckConfirmReplaceMergeAndCancel() {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();
        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        PaperCard negate = magicDb.getCommonCards().getCard("Negate", "M20");
        Assert.assertNotNull(bolt);
        Assert.assertNotNull(negate);
        player.addCard(bolt, 4);
        player.addCard(negate, 2);

        // Replace confirmed: accept returns imported deck (caller replaces editor contents).
        DeckImportController replace = newController(player.getCards(), true);
        replace.setImportBehavior(DeckImportController.ImportBehavior.REPLACE_CURRENT);
        replace.setConfirmResultOverrideForTest(Boolean.TRUE);
        replace.parseInput("Deck\n2 Lightning Bolt (M10) 146\n");
        replace.constrainTokensToInventory();
        Assert.assertTrue(replace.willConfirmImport());
        Deck replaced = replace.accept("Current");
        Assert.assertNotNull(replaced);
        Assert.assertEquals(replaced.getMain().countAll(), 2);
        Assert.assertFalse(replaced.getMain().contains(negate));

        // Merge confirmed: accept still returns only the import tokens (editor merges).
        DeckImportController merge = newController(player.getCards(), true);
        merge.setImportBehavior(DeckImportController.ImportBehavior.MERGE);
        merge.setConfirmResultOverrideForTest(Boolean.TRUE);
        merge.parseInput("Deck\n1 Negate\n");
        merge.constrainTokensToInventory();
        Deck merged = merge.accept("Current");
        Assert.assertNotNull(merged);
        Assert.assertEquals(merged.getMain().count(negate), 1);

        // Cancel: accept returns null and must not apply.
        DeckImportController cancel = newController(player.getCards(), true);
        cancel.setImportBehavior(DeckImportController.ImportBehavior.REPLACE_CURRENT);
        cancel.setConfirmResultOverrideForTest(Boolean.FALSE);
        cancel.parseInput("Deck\n4 Lightning Bolt (M10) 146\n");
        cancel.constrainTokensToInventory();
        Assert.assertNull(cancel.accept("Current"));
    }

    @Test
    public void unknownCardsAndDisallowedSectionsAppearInCannotImportList() {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();
        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        Assert.assertNotNull(bolt);
        player.addCard(bolt, 4);

        DeckImportController controller = newController(player.getCards());
        // Main + Sideboard only — Maybeboard / Companion are unsupported sections.
        List<Token> tokens = controller.parseInput(
                "Deck\n2 Lightning Bolt (M10) 146\n1 Not A Real Card Name Zzz\nMaybeboard\n1 Negate\n");
        tokens = controller.constrainTokensToInventory();

        String cannot = MoxfieldMx1.formatCannotImportList(tokens, controller);
        Assert.assertFalse(cannot.isBlank(), "cannot-import list should be non-empty");
        Assert.assertTrue(cannot.toLowerCase(Locale.ROOT).contains("not a real card")
                        || cannot.toLowerCase(Locale.ROOT).contains("unknown"),
                "unknown card should appear:\n" + cannot);
        Assert.assertTrue(cannot.toLowerCase(Locale.ROOT).contains("maybeboard")
                        || cannot.toLowerCase(Locale.ROOT).contains("section")
                        || cannot.toLowerCase(Locale.ROOT).contains("unsupported"),
                "disallowed section should appear:\n" + cannot);

        boolean sawUnknown = false;
        boolean sawSection = false;
        for (Token t : tokens) {
            if (t.getType() == TokenType.UNKNOWN_CARD || t.getType() == TokenType.UNSUPPORTED_CARD) {
                sawUnknown = true;
            }
            if (t.getType() == TokenType.UNSUPPORTED_DECK_SECTION) {
                sawSection = true;
            }
        }
        Assert.assertTrue(sawUnknown, "expected UNKNOWN_CARD token");
        Assert.assertTrue(sawSection, "expected UNSUPPORTED_DECK_SECTION token");
    }

    @Test
    public void limitedCardSkippedWhenBnRNotIncluded() {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();
        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        PaperCard negate = magicDb.getCommonCards().getCard("Negate", "M20");
        Assert.assertNotNull(bolt);
        Assert.assertNotNull(negate);
        player.addCard(bolt, 4);
        player.addCard(negate, 4);

        GameFormat banBolt = new GameFormat("MX1BanBolt", null, List.of("Lightning Bolt"));
        DeckImportController controller = newController(player.getCards());
        controller.setCurrentGameFormat(banBolt);
        controller.importBannedAndRestrictedCards(false);
        List<Token> tokens = controller.parseInput("Deck\n2 Lightning Bolt (M10) 146\n1 Negate\n");
        tokens = controller.constrainTokensToInventory();

        boolean sawLimited = false;
        for (Token t : tokens) {
            if (t.getType() == TokenType.LIMITED_CARD && t.getCard() != null
                    && "Lightning Bolt".equals(t.getCard().getName())) {
                sawLimited = true;
            }
        }
        Assert.assertTrue(sawLimited, "banned bolt should tokenize as LIMITED_CARD");

        controller.setConfirmResultOverrideForTest(Boolean.TRUE);
        // Empty current deck → no confirm; build path still skips LIMITED_CARD.
        Deck accepted = controller.buildDeckFromCurrentTokens("Banned");
        Assert.assertEquals(accepted.getMain().count(bolt), 0, "LIMITED_CARD must be skipped");
        Assert.assertEquals(accepted.getMain().count(negate), 1);

        controller.importBannedAndRestrictedCards(true);
        // Re-parse so forceImportBannedAndRestrictedCards applies.
        tokens = controller.parseInput("Deck\n2 Lightning Bolt (M10) 146\n1 Negate\n");
        controller.constrainTokensToInventory();
        Deck withBnR = controller.buildDeckFromCurrentTokens("Banned");
        Assert.assertEquals(withBnR.getMain().countAll(), 3,
                "with BnR included, bolt + negate should import");
    }

    @Test
    public void freeBasicLandsNotListedAsMissing() {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();
        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        Assert.assertNotNull(bolt);
        player.addCard(bolt, 1);

        DeckImportController controller = newController(player.getCards());
        List<Token> tokens = controller.parseInput("Deck\n1 Lightning Bolt (M10) 146\n4 Island\n");
        tokens = controller.constrainTokensToInventory();

        boolean sawFreeIsland = false;
        for (Token t : tokens) {
            if (t.getType() == TokenType.FREE_CARD_NOT_IN_INVENTORY && t.getCard() != null
                    && "Island".equals(t.getCard().getName())) {
                sawFreeIsland = true;
            }
            Assert.assertNotEquals(t.getType(), TokenType.CARD_NOT_IN_INVENTORY,
                    "basic lands must not be CARD_NOT_IN_INVENTORY when free-print is set");
        }
        Assert.assertTrue(sawFreeIsland, "unowned basics should be FREE_CARD_NOT_IN_INVENTORY");
        Assert.assertTrue(MoxfieldMx1.missingFromTokens(tokens, player.getCards()).isEmpty(),
                "free basics must not appear in Missing list");

        Deck accepted = acceptTokens(tokens);
        Assert.assertTrue(accepted.getMain().countAll() >= 5, accepted.getMain().toString());
    }

    @Test
    public void mainAndSideboardShareOneInventoryNoDoubleCount() {
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();
        PaperCard bolt = magicDb.getCommonCards().getCard("Lightning Bolt", "M10", "146");
        Assert.assertNotNull(bolt);
        player.addCard(bolt, 3);

        DeckImportController controller = newController(player.getCards());
        List<Token> tokens = controller.parseInput(
                "Deck\n2 Lightning Bolt (M10) 146\nSideboard\n2 Lightning Bolt (M10) 146\n");
        tokens = controller.constrainTokensToInventory();

        int legal = 0;
        int missing = 0;
        for (Token t : tokens) {
            if (t.getCard() == null || !"Lightning Bolt".equals(t.getCard().getName())) {
                continue;
            }
            if (t.isCardTokenForDeck()) {
                legal += t.getQuantity();
            }
            if (t.getType() == TokenType.CARD_NOT_IN_INVENTORY) {
                missing += t.getQuantity();
            }
        }
        Assert.assertEquals(legal, 3, "main+sideboard must share the 3 owned copies");
        Assert.assertEquals(missing, 1, "fourth requested copy is missing");

        List<MoxfieldMx1.MissingCard> gaps = MoxfieldMx1.missingFromTokens(tokens, player.getCards());
        Assert.assertEquals(gaps.size(), 1);
        Assert.assertEquals(gaps.get(0).wanted, 4);
        Assert.assertEquals(gaps.get(0).owned, 3);

        Deck accepted = acceptTokens(tokens);
        Assert.assertEquals(accepted.getAllCardsInASinglePool().countAll(), 3);
        Assert.assertEquals(accepted.getMain().countAll() + accepted.getOrCreate(DeckSection.Sideboard).countAll(), 3);
    }

    @Test
    public void stockWorldKeepsLegacyCsvBehavior() throws Exception {
        Config.installConfigDataForTest(stockConfig);
        Assert.assertFalse(Config.ascendant());

        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getCards().clear();

        CardEdition conflux = magicDb.getEditions().get("CFX");
        Assert.assertNotNull(conflux);
        PaperCard card = firstCardWithCollectorNumber(conflux.getCode());
        Assert.assertNotNull(card);
        player.addCard(card, 1);

        CollectionExporter.export(player);
        String csv = Files.readString(
                CollectionExporter.exportDir().toPath().resolve("collection.csv"), StandardCharsets.UTF_8);
        Assert.assertTrue(csv.toLowerCase(Locale.ROOT).contains(",\"cfx\","),
                "Stock export must keep Forge edition codes:\n" + csv);
        Assert.assertFalse(Files.exists(
                        CollectionExporter.exportDir().toPath().resolve(CollectionExporter.UNMATCHED_FILE)),
                "Stock export must not write unmatched.txt");
    }

    // ---- helpers ----

    private static DeckImportController newController(ItemPool<PaperCard> inventory) {
        return newController(inventory, false);
    }

    private static DeckImportController newController(ItemPool<PaperCard> inventory, boolean currentDeckNotEmpty) {
        DeckImportController c = new DeckImportController(stubCheck(false), null, null, currentDeckNotEmpty);
        c.setPlayerInventory(inventory);
        c.setImportBehavior(DeckImportController.ImportBehavior.REPLACE_CURRENT);
        c.setFreePrintConverter(pc -> pc != null && pc.isVeryBasicLand() ? pc : null);
        // Main + Sideboard only — including Commander makes DeckRecognizer place legal
        // singleton-style candidates into Commander when no section header is present.
        c.setAllowedSections(List.of(DeckSection.Main, DeckSection.Sideboard));
        return c;
    }

    private static Deck acceptTokens(List<Token> tokens) {
        Deck deck = new Deck();
        for (Token t : tokens) {
            if (!t.isTokenForDeck()) {
                continue;
            }
            if (t.getType() == TokenType.DECK_NAME) {
                deck.setName(t.getText());
                continue;
            }
            // Mirror accept()'s LIMITED_CARD skip when BnR is off (default for helpers).
            if (t.getType() == TokenType.LIMITED_CARD) {
                continue;
            }
            DeckSection section = t.getTokenSection() != null ? t.getTokenSection() : DeckSection.Main;
            deck.getOrCreate(section).add(t.getCard(), t.getQuantity());
        }
        return deck;
    }

    private static PaperCard firstCardWithCollectorNumber(String edition) {
        CardEdition ed = magicDb.getEditions().get(edition);
        if (ed == null) {
            return null;
        }
        for (PaperCard pc : magicDb.getCommonCards().getAllCards(ed)) {
            if (CollectionExporter.hasScryfallMatch(pc)) {
                return pc;
            }
        }
        for (PaperCard pc : magicDb.getCommonCards().getAllCards()) {
            if (edition.equalsIgnoreCase(pc.getEdition()) && CollectionExporter.hasScryfallMatch(pc)) {
                return pc;
            }
        }
        return null;
    }

    private static ICheckBox stubCheck(boolean selected) {
        return new ICheckBox() {
            private boolean on = selected;

            @Override
            public boolean isSelected() {
                return on;
            }

            @Override
            public void setSelected(boolean b0) {
                on = b0;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void setEnabled(boolean b0) {
            }

            @Override
            public boolean isVisible() {
                return true;
            }

            @Override
            public void setVisible(boolean b0) {
            }

            @Override
            public String getToolTipText() {
                return null;
            }

            @Override
            public void setToolTipText(String s0) {
            }
        };
    }

    private static void clearExportDir() throws Exception {
        Path dir = CollectionExporter.exportDir().toPath();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    if (!p.equals(dir)) {
                        Files.deleteIfExists(p);
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    private static String readResource(String path) throws Exception {
        try (var in = MoxfieldMx1Test.class.getClassLoader().getResourceAsStream(path)) {
            Assert.assertNotNull(in, "missing test resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path resolveForgeGuiDir() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path[] candidates = {
                cwd.resolve("forge-gui"),
                cwd.getParent() != null ? cwd.getParent().resolve("forge-gui") : null,
                Path.of("forge-gui").toAbsolutePath()
        };
        for (Path p : candidates) {
            if (p != null && Files.isDirectory(p.resolve("res/editions"))) {
                return p;
            }
        }
        return cwd.resolve("forge-gui");
    }

    private static StaticData readStaticDataInstance() {
        try {
            Field f = StaticData.class.getDeclaredField("lastInstance");
            f.setAccessible(true);
            return (StaticData) f.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static void pinStaticData(StaticData data) {
        try {
            Field f = StaticData.class.getDeclaredField("lastInstance");
            f.setAccessible(true);
            f.set(null, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
