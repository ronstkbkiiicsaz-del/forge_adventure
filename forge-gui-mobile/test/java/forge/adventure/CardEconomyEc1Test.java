package forge.adventure;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import forge.ImageKeys;
import forge.StaticData;
import forge.adventure.data.ConfigData;
import forge.adventure.data.DifficultyData;
import forge.adventure.player.AccountStore;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.util.AscendantCardEconomy;
import forge.adventure.util.CardUtil;
import forge.adventure.util.Config;
import forge.adventure.util.CraftingApi;
import forge.adventure.util.Current;
import forge.adventure.util.SaveFileData;
import forge.adventure.world.WorldSave;
import forge.card.CardRarity;
import forge.gamemodes.limited.DraftRankCache;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * EC1 behaviour: Ascendant game prices (rarity + draft rank + rotation + local markets),
 * shared worth for craft/salvage, old-save gold/cards/dust preserved, stock unchanged.
 *
 * <p>Real behaviour against the isolated temp user dir — no source greps.
 */
@Test(singleThreaded = true)
public class CardEconomyEc1Test {

    private static final ReentrantLock LOCK = new ReentrantLock();

    private static Path realUserDir;
    private static java.util.Map<String, AdventureTestUserDir.FileStamp> realUserDirSnapshot;
    private static StaticData magicDb;
    private static StaticData previousStaticData;
    private static ConfigData ascendantConfig;
    private static ConfigData stockConfig;
    private static String initError;

    private int goldSnapshot;
    private int dustCommonSnapshot;
    private int dustRareSnapshot;
    private PointOfInterestChanges townA;
    private PointOfInterestChanges townB;
    private PointOfInterestChanges previousLocation;

    @BeforeClass
    public void bootstrapConfigAndCardDb() {
        try {
            realUserDir = AdventureTestUserDir.defaultRealUserDir();
            realUserDirSnapshot = AdventureTestUserDir.snapshot(realUserDir);
            AdventureTestUserDir.requireIsolatedUserDir();
            AccountStore.setAdventureRootOverrideForTest(new File(ForgeConstants.USER_ADVENTURE_DIR));

            Path forgeGuiDir = resolveForgeGuiDir();
            Assert.assertTrue(Files.isDirectory(forgeGuiDir.resolve("res/editions")));
            Assert.assertTrue(Files.isDirectory(forgeGuiDir.resolve("res/draft/rankings")));

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
            Assert.assertNotNull(magicDb);
            Assert.assertNotNull(magicDb.getCommonCards());
            Assert.assertNotNull(magicDb.getEditions().get("ZEN"), "ZEN required for rank tests");
            pinStaticData(magicDb);

            Path cfgPath = forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/config.json");
            ascendantConfig = new Json().fromJson(ConfigData.class, new FileHandle(cfgPath.toFile()));
            Assert.assertNotNull(ascendantConfig);
            Assert.assertTrue(ascendantConfig.ascendantRules);
            Assert.assertTrue(ascendantConfig.ec1GamePrices, "Ascendant config must enable EC1");
            ascendantConfig.allowedEditions = null;

            stockConfig = new ConfigData();
            stockConfig.ascendantRules = false;
            stockConfig.usePriceListPrices = true;
            stockConfig.ec1GamePrices = true; // unused when not Ascendant

            Config.resetInstanceForTest();
            Config.instance();
            Config.installConfigDataForTest(ascendantConfig);
            Assert.assertTrue(Config.ascendant());
            Assert.assertTrue(AscendantCardEconomy.enabled());

            Assert.assertNotNull(DraftRankCache.getRanking("Day of Judgment", "ZEN"));
            PaperCard badlands = magicDb.getCommonCards().getCard("Badlands", "LEB");
            if (badlands == null)
                badlands = magicDb.getCommonCards().getUniqueByName("Badlands");
            Assert.assertNotNull(badlands, "Badlands must resolve for jackpot test");
        } catch (Throwable t) {
            initError = t.getClass().getSimpleName() + ": " + t.getMessage();
            t.printStackTrace();
        }
    }

    @AfterClass
    public void teardownClass() {
        try {
            Config.resetInstanceForTest();
        } catch (Throwable ignored) {
        }
        try {
            pinStaticData(previousStaticData);
        } catch (Throwable ignored) {
        }
        try {
            AccountStore.setAdventureRootOverrideForTest(null);
        } catch (Throwable ignored) {
        }
        if (realUserDir != null && realUserDirSnapshot != null) {
            try {
                AdventureTestUserDir.assertUnchanged(realUserDir, realUserDirSnapshot, "EC1");
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @BeforeMethod
    public void setUp() {
        LOCK.lock();
        Assert.assertNull(initError, "bootstrap failed: " + initError);
        pinStaticData(magicDb);
        AdventureTestUserDir.requireIsolatedUserDir();
        Config.installConfigDataForTest(ascendantConfig);
        AscendantCardEconomy.resetLifecycleStateForTest();
        CardUtil.clearPriceCache();

        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        Assert.assertNotNull(player);
        previousLocation = player.getCurrentLocationChanges();
        goldSnapshot = player.getGold();
        dustCommonSnapshot = player.getDust(AdventurePlayer.DUST_COMMON);
        dustRareSnapshot = player.getDust(AdventurePlayer.DUST_RARE);

        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        player.getSeason().initNewGame(player.getStandardWindow());
        AscendantCardEconomy.bindSeasonCalendar(player.getSeason());
        player.setActiveShopMarket(null);

        townA = new PointOfInterestChanges();
        townB = new PointOfInterestChanges();
        townA.setMarketColorLean("R");
        townB.setMarketColorLean("U");
        player.loadChanges(townA);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
            player.setActiveShopMarket(null);
            player.loadChanges(previousLocation);
            AscendantCardEconomy.resetLifecycleStateForTest();
            int goldNow = player.getGold();
            if (goldNow > goldSnapshot)
                player.takeGold(goldNow - goldSnapshot);
            else if (goldNow < goldSnapshot)
                player.giveGold(goldSnapshot - goldNow);
            int dustC = player.getDust(AdventurePlayer.DUST_COMMON);
            if (dustC != dustCommonSnapshot)
                player.addDust(AdventurePlayer.DUST_COMMON, dustCommonSnapshot - dustC);
            int dustR = player.getDust(AdventurePlayer.DUST_RARE);
            if (dustR != dustRareSnapshot)
                player.addDust(AdventurePlayer.DUST_RARE, dustRareSnapshot - dustR);
            AscendantCardEconomy.resetLifecycleStateForTest();
            Config.installConfigDataForTest(ascendantConfig);
            pinStaticData(magicDb);
        } finally {
            LOCK.unlock();
        }
    }

    @Test
    public void zenCommonOutpricesWeakZenRareByRanking() {
        PaperCard topCommon = pickZenByRarity(CardRarity.Common, true);
        PaperCard weakRare = pickZenByRarity(CardRarity.Rare, false);
        Assert.assertNotNull(topCommon, "need a ranked ZEN common");
        Assert.assertNotNull(weakRare, "need a ranked ZEN rare");

        Double commonRank = DraftRankCache.getRanking(topCommon.getName(), "ZEN");
        Double rareRank = DraftRankCache.getRanking(weakRare.getName(), "ZEN");
        Assert.assertNotNull(commonRank);
        Assert.assertNotNull(rareRank);
        Assert.assertTrue(commonRank < rareRank,
                "common relative rank must be better (lower) than weak rare");

        int commonPrice = AscendantCardEconomy.cardValue(topCommon, null);
        int rarePrice = AscendantCardEconomy.cardValue(weakRare, null);
        Assert.assertTrue(commonPrice > rarePrice,
                "strong ZEN common (" + topCommon.getName() + "=" + commonPrice
                        + ") must outprice weak ZEN rare (" + weakRare.getName() + "=" + rarePrice + ")");
    }

    @Test
    public void badlandsPricedByRarityNotPriceListJackpot() {
        PaperCard badlands = card("Badlands", "LEB");
        if (badlands == null)
            badlands = card("Badlands", null);
        Assert.assertNotNull(badlands);

        int gamePrice = CardUtil.getCardPrice(badlands);
        Assert.assertTrue(gamePrice > 0);
        Assert.assertTrue(gamePrice < 1000,
                "EC1 Badlands must not be a real-world jackpot, got " + gamePrice);
        Assert.assertNotEquals(gamePrice, 3000);

        Config.installConfigDataForTest(stockConfig);
        CardUtil.clearPriceCache();
        int stockPrice = CardUtil.getCardPrice(badlands);
        Assert.assertEquals(stockPrice, 3000, "stock world still uses cardprices.txt Badlands=3000");
        Config.installConfigDataForTest(ascendantConfig);
        CardUtil.clearPriceCache();
    }

    @Test
    public void basicLandsWorthNothing() {
        PaperCard plains = card("Plains", "ZEN");
        Assert.assertNotNull(plains);
        Assert.assertEquals(AscendantCardEconomy.cardValue(plains, null), 0);
        Assert.assertEquals(CardUtil.getCardPrice(plains), 0);
    }

    @Test
    public void saleShareAppliedOnSellPrice() {
        AdventurePlayer player = Current.player();
        PaperCard c = pickZenByRarity(CardRarity.Common, true);
        Assert.assertNotNull(c);
        player.setActiveShopMarket(townA);
        int local = AscendantCardEconomy.cardValue(c, townA);
        int sell = player.cardSellPrice(c);
        int expected = Math.round(local * AscendantCardEconomy.saleShare());
        Assert.assertEquals(sell, expected, "sell = local × saleShare (neutral town/skill mods)");
        Assert.assertEquals(AscendantCardEconomy.saleShare(), 0.30f, 0.0001f);
        player.setActiveShopMarket(null);
    }

    @Test
    public void sellingFiveCopiesLowersThatTownOnly() {
        AdventurePlayer player = Current.player();
        PaperCard c = pickZenByRarity(CardRarity.Common, true);
        Assert.assertNotNull(c);
        player.addCard(c, 10);

        player.setActiveShopMarket(townA);
        int beforeA = AscendantCardEconomy.cardValue(c, townA);
        int beforeB = AscendantCardEconomy.cardValue(c, townB);

        Assert.assertEquals(player.sellCard(c, 5), 5);
        int afterA = AscendantCardEconomy.cardValue(c, townA);
        int afterB = AscendantCardEconomy.cardValue(c, townB);

        Assert.assertTrue(afterA < beforeA, "town A price must drop after selling five");
        Assert.assertEquals(afterB, beforeB, "town B price must be unchanged");
        Assert.assertTrue(townA.getMarketSupply(c.getName()) > 0);
        Assert.assertEquals(townB.getMarketSupply(c.getName()), 0f, 0.0001f);
        player.setActiveShopMarket(null);
    }

    @Test
    public void marketDriftsBackOverGameDays() {
        PaperCard c = pickZenByRarity(CardRarity.Uncommon, true);
        Assert.assertNotNull(c);
        townA.addMarketSupply(c.getName(), 2.0f);
        float supply0 = townA.getMarketSupply(c.getName());
        int price0 = AscendantCardEconomy.cardValue(c, townA);

        // Drive drift through the real SN1 calendar (not a static fallback).
        AdventurePlayer player = Current.player();
        player.getSeason().initNewGame(player.getStandardWindow());
        AscendantCardEconomy.bindSeasonCalendar(player.getSeason());
        player.getSeason().advanceGameDaysForTest(10);
        int price1 = AscendantCardEconomy.cardValue(c, townA);
        float supply1 = townA.getMarketSupply(c.getName());

        Assert.assertTrue(supply1 < supply0, "supply drifts toward 0");
        Assert.assertTrue(price1 > price0, "price drifts back up as supply fades");
    }

    @Test
    public void craftAndSalvageReadSharedWorth() {
        PaperCard strong = pickZenByRarity(CardRarity.Common, true);
        PaperCard weak = pickZenByRarity(CardRarity.Common, false);
        Assert.assertNotNull(strong);
        Assert.assertNotNull(weak);
        // Shop gold still follows rank; dust worth is 1.0 for in-Standard (life-cycle only).
        Assert.assertTrue(AscendantCardEconomy.cardValue(strong, null)
                        > AscendantCardEconomy.cardValue(weak, null),
                "strong common shop gold > weak common");
        Assert.assertEquals(AscendantCardEconomy.relativeWorth(strong), 1f, 0.001f);
        Assert.assertEquals(AscendantCardEconomy.relativeWorth(weak), 1f, 0.001f);

        AdventurePlayer player = Current.player();
        Assert.assertEquals(player.salvageYield(strong), ascendantConfig.salvageDust);
        Assert.assertEquals(player.salvageYield(weak), ascendantConfig.salvageDust);
        Assert.assertEquals(player.craftCost(strong), ascendantConfig.craftCost);

        Assert.assertNotNull(CraftingApi.formatDustCost(strong));
        Assert.assertEquals(CraftingApi.dustCost("_____nonexistent_card_ec1_____"), 0);
    }

    @Test
    public void craftingApiCraftMissingReportsGaps() {
        List<CraftingApi.CraftResult> results = CraftingApi.craftMissing(List.of(
                new CraftingApi.MissingRequest("Plains", 2, 0),
                new CraftingApi.MissingRequest("_____no_such_card_____", 1, 0)
        ));
        Assert.assertEquals(results.size(), 2);
        Assert.assertFalse(results.get(0).ok());
        Assert.assertFalse(results.get(1).ok());
    }

    @Test
    public void oldSaveRoundTripKeepsGoldCardsAndDust() {
        AdventurePlayer player = Current.player();
        PaperCard c = pickZenByRarity(CardRarity.Common, true);
        Assert.assertNotNull(c);

        // Ensure required string fields are non-null for SaveFileData (test bootstrap
        // WorldSave players can have a null name before New Game finishes).
        ensurePlayerSaveable(player);

        player.giveGold(1234);
        player.addCard(c, 3);
        player.addDust(AdventurePlayer.DUST_COMMON, 17);
        player.addDust(AdventurePlayer.DUST_RARE, 4);

        int gold = player.getGold();
        int dustC = player.getDust(AdventurePlayer.DUST_COMMON);
        int dustR = player.getDust(AdventurePlayer.DUST_RARE);
        int copies = player.getCards().count(c);
        Assert.assertTrue(copies >= 3);

        // Round-trip the live player blob — gold/cards/dust present, no EC1 market keys.
        SaveFileData saved = player.save();
        Assert.assertFalse(saved.containsKey("ec1MarketNames"));

        player.load(saved);
        Assert.assertEquals(player.getGold(), gold, "gold must survive old-save load");
        Assert.assertEquals(player.getDust(AdventurePlayer.DUST_COMMON), dustC);
        Assert.assertEquals(player.getDust(AdventurePlayer.DUST_RARE), dustR);
        Assert.assertEquals(player.getCards().count(c), copies, "cards must survive old-save load");

        // POI changes without EC1 keys load with empty markets.
        PointOfInterestChanges reloaded = new PointOfInterestChanges();
        SaveFileData full = new PointOfInterestChanges().save();
        SaveFileData minimal = new SaveFileData();
        minimal.storeObject("deletedObjects", full.readObject("deletedObjects"));
        minimal.storeObject("cardsBought", full.readObject("cardsBought"));
        minimal.storeObject("mapFlags", full.readObject("mapFlags"));
        minimal.storeObject("shopSeeds", full.readObject("shopSeeds"));
        minimal.storeObject("reputation", full.readObject("reputation"));
        minimal.storeObject("isBookmarked", full.readObject("isBookmarked"));
        minimal.storeObject("isVisited", full.readObject("isVisited"));
        reloaded.load(minimal);
        Assert.assertEquals(reloaded.getMarketSupply("Anything"), 0f, 0.0001f);
        Assert.assertNull(reloaded.getMarketColorLean());
    }

    @Test
    public void stockWorldIgnoresEc1() {
        Config.installConfigDataForTest(stockConfig);
        CardUtil.clearPriceCache();
        Assert.assertFalse(Config.ascendant());
        Assert.assertFalse(AscendantCardEconomy.enabled());
        PaperCard c = card("Lightning Bolt", "M10");
        if (c == null)
            c = card("Lightning Bolt", null);
        Assert.assertNotNull(c);
        Assert.assertTrue(CardUtil.getCardPrice(c) > 0);
        Config.installConfigDataForTest(ascendantConfig);
        CardUtil.clearPriceCache();
    }

    @Test
    public void colorLeanRaisesMatchingCardPrice() {
        PaperCard red = null;
        for (String name : List.of("Goblin Guide", "Ball Lightning", "Lightning Bolt", "Burst Lightning")) {
            red = card(name, "ZEN");
            if (red == null)
                red = card(name, null);
            if (red != null && AscendantCardEconomy.colorLeanMultiplier(red, "R", 1.15f) > 1f)
                break;
            red = null;
        }
        Assert.assertNotNull(red, "need a red card for lean test");
        townA.setMarketColorLean("R");
        townB.setMarketColorLean("U");
        int inRedTown = AscendantCardEconomy.cardValue(red, townA);
        int inBlueTown = AscendantCardEconomy.cardValue(red, townB);
        Assert.assertTrue(inRedTown > inBlueTown, "red town pays more for red cards");
    }


    @Test
    public void standardCardPricedNearRarityBase() {
        AdventurePlayer player = Current.player();
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        PaperCard bolt = card("Lightning Bolt", "M10");
        Assert.assertNotNull(bolt);
        Assert.assertTrue(AscendantCardEconomy.isStandardLegal(bolt));
        int price = AscendantCardEconomy.cardValue(bolt, null);
        // rarity base 50 × rank band — stay in a Standard-ish band, not a jackpot.
        Assert.assertTrue(price >= 20 && price <= 200, "Standard bolt near rarity/power base, got " + price);
    }

    @Test
    public void rotatedCardRisesAfterNSeasons() {
        AdventurePlayer player = Current.player();
        // Window without LEB — Badlands is Historic-only by name.
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        PaperCard badlands = card("Badlands", "LEB");
        if (badlands == null)
            badlands = card("Badlands", null);
        Assert.assertNotNull(badlands);
        Assert.assertFalse(AscendantCardEconomy.isStandardLegal(badlands), "Badlands must be Historic");
        AscendantCardEconomy.noteSetRotatedForTest(badlands.getEdition(), 1);
        player.getSeason().setCalendarForTest(0, 1);
        int atRotate = AscendantCardEconomy.cardValue(badlands, null);
        player.getSeason().setCalendarForTest(100, 8);
        int later = AscendantCardEconomy.cardValue(badlands, null);
        Assert.assertTrue(later > atRotate,
                "post-rotation card must rise after N seasons: " + atRotate + " -> " + later);
    }

    @Test
    public void badlandsStartsLowAndClimbsTowardListPrice() {
        AdventurePlayer player = Current.player();
        // Keep Badlands Historic (not in Standard window).
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        PaperCard badlands = card("Badlands", "LEB");
        if (badlands == null)
            badlands = card("Badlands", null);
        Assert.assertNotNull(badlands);
        AscendantCardEconomy.noteSetRotatedForTest(badlands.getEdition(), 1);
        player.getSeason().setCalendarForTest(0, 1);
        int early = AscendantCardEconomy.cardValue(badlands, null);
        Assert.assertTrue(early > 0 && early < 1000,
                "Badlands starts near rarity base, got " + early);
        player.getSeason().setCalendarForTest(400, 24);
        int late = AscendantCardEconomy.cardValue(badlands, null);
        Assert.assertTrue(late > early, "Badlands climbs over many seasons: " + early + " -> " + late);
        Assert.assertTrue(late <= 3000, "must not exceed pedigree list ceiling, got " + late);
        Assert.assertTrue(late > 800, "should approach list price materially after 24 seasons, got " + late);
    }

    @Test
    public void spikedCommonShowsRumour() {
        PaperCard common = pickZenByRarity(CardRarity.Common, true);
        Assert.assertNotNull(common);
        AscendantCardEconomy.setSeasonSpikesForTest(List.of(common.getName()));
        Assert.assertTrue(AscendantCardEconomy.isSpiked(common.getName()));
        String rumour = AscendantCardEconomy.spikeRumourLine();
        Assert.assertTrue(rumour.toLowerCase().contains("demand")
                        || rumour.contains(common.getName()), rumour);
        int spiked = AscendantCardEconomy.cardValue(common, null);
        AscendantCardEconomy.setSeasonSpikesForTest(List.of());
        int normal = AscendantCardEconomy.cardValue(common, null);
        Assert.assertTrue(spiked > normal, "spike mult must raise price");
    }

    @Test
    public void historicCraftFactorAppliedOnCraftCost() {
        AdventurePlayer player = Current.player();
        // Unlock ZEN then rotate it out of the legal window (history keeps craft access).
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        PaperCard geopede = card("Plated Geopede", "ZEN");
        Assert.assertNotNull(geopede);
        player.getStandardWindow().restoreSets(List.of("M10", "M11", "M12"));
        Assert.assertFalse(player.isStandardLegal(geopede), "rotated ZEN card must be Historic");
        Assert.assertNotNull(player.craftPrintingPreferring(geopede), "still craftable from history");
        int withFactor = player.craftCost(geopede);
        float saved = ascendantConfig.historicCraftFactor;
        try {
            ascendantConfig.historicCraftFactor = 1f;
            Config.installConfigDataForTest(ascendantConfig);
            int without = player.craftCost(geopede);
            Assert.assertTrue(withFactor > without,
                    "historicCraftFactor must raise Historic craft cost: "
                            + withFactor + " vs " + without);
        } finally {
            ascendantConfig.historicCraftFactor = saved;
            Config.installConfigDataForTest(ascendantConfig);
        }
    }

    @Test
    public void shopPricesAndRecordsFromOwnMarketOnly() {
        AdventurePlayer player = Current.player();
        PaperCard c = pickZenByRarity(CardRarity.Common, true);
        Assert.assertNotNull(c);
        player.addCard(c, 6);

        // Shop A prices from townA; buy recording hits townA only.
        player.setActiveShopMarket(townA);
        int priceA0 = CardUtil.getCardPrice(c);
        AscendantCardEconomy.noteBought(townA, c, 3);
        Assert.assertTrue(townA.getMarketSupply(c.getName()) < 0);
        Assert.assertEquals(townB.getMarketSupply(c.getName()), 0f, 0.0001f);
        int priceA1 = CardUtil.getCardPrice(c);
        Assert.assertTrue(priceA1 > priceA0, "buying in A raises A's local price");

        // Map shop B uses its own POI — A's buy pressure must not inflate B.
        player.setActiveShopMarket(townB);
        int priceB = CardUtil.getCardPrice(c);
        Assert.assertTrue(priceB < priceA1, "no cross-town arbitrage: B stays below pumped A");
        Assert.assertEquals(priceB, priceA0, "B matches the pre-buy baseline");

        // Selling outside town moves no market.
        float supplyA = townA.getMarketSupply(c.getName());
        float supplyB = townB.getMarketSupply(c.getName());
        player.setActiveShopMarket(null);
        Assert.assertEquals(player.sellCard(c, 2), 2);
        Assert.assertEquals(townA.getMarketSupply(c.getName()), supplyA, 0.0001f);
        Assert.assertEquals(townB.getMarketSupply(c.getName()), supplyB, 0.0001f);
    }

    @Test
    public void sellSkillCapPreventsMarketLoop() {
        AdventurePlayer player = Current.player();
        PaperCard c = pickZenByRarity(CardRarity.Common, true);
        Assert.assertNotNull(c);
        player.setActiveShopMarket(townA);
        // Max Salvaging skill would otherwise push sellPriceFactor well above the cap.
        while (player.getSkills().getLevel(forge.adventure.player.PlayerSkills.Skill.SALVAGING) < 99) {
            player.getSkills().addXp(forge.adventure.player.PlayerSkills.Skill.SALVAGING, 10_000);
            if (player.getSkills().getLevel(forge.adventure.player.PlayerSkills.Skill.SALVAGING) >= 99)
                break;
        }
        float uncapped = player.getSkills().sellPriceFactor();
        Assert.assertTrue(uncapped > AscendantCardEconomy.sellSkillCap(),
                "need uncapped skill above cap for this test, got " + uncapped);
        int local = AscendantCardEconomy.cardValue(c, townA);
        int sell = player.cardSellPrice(c);
        float uncappedSell = local * AscendantCardEconomy.saleShare()
                * (2f - townA.getTownPriceModifier()) * uncapped;
        int cappedExpected = Math.round(local * AscendantCardEconomy.saleShare()
                * (2f - townA.getTownPriceModifier()) * AscendantCardEconomy.sellSkillCap());
        Assert.assertTrue(sell <= cappedExpected + 1, "sell skill must be capped, got " + sell);
        Assert.assertTrue(sell < uncappedSell - 1, "capped sell must be below uncapped skill payout");
        player.setActiveShopMarket(null);
    }

    @Test
    public void craftMissingReagentsFullCountAllOrNothing() {
        AdventurePlayer player = Current.player();
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        PaperCard bolt = card("Lightning Bolt", "M10");
        Assert.assertNotNull(bolt);
        player.getCards().clear();
        // Enough dust for 3 copies but only 1 ash → plan disabled (full-count reagents).
        int unit = Math.max(1, player.craftCost(bolt));
        player.addDust(AdventurePlayer.DUST_COMMON, unit * 3 + 20);
        forge.adventure.data.MaterialData ash = forge.adventure.data.MaterialListData.primaryReagent("R", 1);
        Assert.assertNotNull(ash);
        int ashHave = player.getMaterial(ash.id);
        if (ashHave > 0)
            player.takeMaterial(ash.id, ashHave);
        player.addMaterial(ash.id, 1);
        int ashBeforePlan = player.getMaterial(ash.id);
        CraftingApi.CraftMissingPlan shortPlan = CraftingApi.planCraftMissing(List.of(
                new CraftingApi.MissingRequest("Lightning Bolt", 3, 0, bolt)));
        Assert.assertFalse(shortPlan.craftEnabled, shortPlan.disabledReason);
        Assert.assertEquals(player.getMaterial(ash.id), ashBeforePlan,
                "planCraftMissing must reserve without taking materials");
        Assert.assertTrue(shortPlan.disabledReason != null
                        && shortPlan.disabledReason.toLowerCase().contains("reagent")
                        || shortPlan.disabledReason.toLowerCase().contains("need"),
                shortPlan.disabledReason);
        List<CraftingApi.CraftResult> none = CraftingApi.craftMissing(List.of(
                new CraftingApi.MissingRequest("Lightning Bolt", 3, 0, bolt)));
        Assert.assertEquals(player.getCards().count(bolt), 0, "all-or-nothing: no partial craft");
        Assert.assertFalse(none.isEmpty());
        Assert.assertFalse(none.get(0).ok());

        // Enough reagents for full count → crafts all three.
        player.addMaterial(ash.id, 5);
        CraftingApi.CraftMissingPlan ok = CraftingApi.planCraftMissing(List.of(
                new CraftingApi.MissingRequest("Lightning Bolt", 3, 0, bolt)));
        Assert.assertTrue(ok.craftEnabled, ok.disabledReason);
        List<CraftingApi.CraftResult> done = CraftingApi.craftMissing(ok.requests);
        Assert.assertTrue(done.get(0).ok());
        Assert.assertEquals(done.get(0).crafted, 3);
        Assert.assertEquals(player.getCards().count(bolt), 3);
    }

    @Test
    public void rotationAgeSurvivesSaveLoadAndDoesNotLeakBetweenSaves() {
        AdventurePlayer player = Current.player();
        ensurePlayerSaveable(player);
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        player.getSeason().initNewGame(player.getStandardWindow());
        AscendantCardEconomy.bindSeasonCalendar(player.getSeason());
        AscendantCardEconomy.noteSetRotated("ZEN", 2);
        AscendantCardEconomy.noteSetRotated("M10", 3);
        Assert.assertEquals(AscendantCardEconomy.rotatedAtSeasonSnapshotForTest().get("ZEN"), Integer.valueOf(2));

        SaveFileData saveA = player.save();
        Assert.assertTrue(saveA.containsKey("ec1RotatedAtSeasonKeys"), "rotation age must be persisted");

        // Simulate save B with different rotation data.
        AscendantCardEconomy.resetLifecycleState();
        AscendantCardEconomy.noteSetRotated("M11", 9);
        SaveFileData saveB = player.save();

        // Load A after B — must not carry B's M11=9.
        AscendantCardEconomy.resetLifecycleState();
        player.load(saveA);
        AscendantCardEconomy.bindSeasonCalendar(player.getSeason());
        Map<String, Integer> afterA = AscendantCardEconomy.rotatedAtSeasonSnapshotForTest();
        Assert.assertEquals(afterA.get("ZEN"), Integer.valueOf(2));
        Assert.assertEquals(afterA.get("M10"), Integer.valueOf(3));
        Assert.assertNull(afterA.get("M11"), "load A must not keep B's rotation entries");

        // Load B — only B's data.
        player.load(saveB);
        AscendantCardEconomy.bindSeasonCalendar(player.getSeason());
        Map<String, Integer> afterB = AscendantCardEconomy.rotatedAtSeasonSnapshotForTest();
        Assert.assertEquals(afterB.get("M11"), Integer.valueOf(9));
        Assert.assertNull(afterB.get("ZEN"), "load B must not keep A's rotation entries");

        // Next season change marks the right sets from the rebound window snapshot.
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        AscendantCardEconomy.bindSeasonCalendar(player.getSeason());
        player.getSeason().endByMastery(player.getStandardWindow(), new forge.deck.CardPool());
        String pick = player.getSeason().getShortlist().isEmpty()
                ? null : player.getSeason().getShortlist().get(0);
        if (pick != null) {
            String rotated = player.getSeason().applyPick(player.getStandardWindow(), pick, false);
            if (rotated != null) {
                Assert.assertEquals(
                        AscendantCardEconomy.rotatedAtSeasonSnapshotForTest().get(rotated.toUpperCase()),
                        Integer.valueOf(player.getSeason().getSeasonIndex()),
                        "season change must record the rotated set at the new season index");
            }
        }
    }

    @Test
    public void badlandsAppreciatesAfterRealSeasonChangesWithoutTestHook() {
        AdventurePlayer player = Current.player();
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        player.getSeason().initNewGame(player.getStandardWindow());
        AscendantCardEconomy.bindSeasonCalendar(player.getSeason());
        // No noteSetRotatedForTest — never-window sets must climb from real season changes.
        PaperCard badlands = card("Badlands", "LEB");
        if (badlands == null)
            badlands = card("Badlands", null);
        Assert.assertNotNull(badlands);
        Assert.assertFalse(AscendantCardEconomy.isStandardLegal(badlands));
        int early = AscendantCardEconomy.cardValue(badlands, null);
        Assert.assertTrue(early > 0 && early < 1000, "Badlands starts near rarity base, got " + early);

        int seasons = 0;
        for (int i = 0; i < 12; i++) {
            if (!advanceOneSeason(player))
                break;
            seasons++;
        }
        Assert.assertTrue(seasons >= 6, "need several real season changes, got " + seasons);
        int late = AscendantCardEconomy.cardValue(badlands, null);
        Assert.assertTrue(late > early,
                "Badlands must rise after real seasons without noteSetRotatedForTest: "
                        + early + " -> " + late + " (seasons=" + seasons + ")");
    }

    @Test
    public void spikeRumourLineUsesLocalizerKey() {
        PaperCard common = pickZenByRarity(CardRarity.Common, true);
        Assert.assertNotNull(common);
        AscendantCardEconomy.setSeasonSpikesForTest(List.of(common.getName()));
        String rumour = AscendantCardEconomy.spikeRumourLine();
        Assert.assertFalse(rumour.isBlank());
        Assert.assertTrue(rumour.contains(common.getName()), rumour);
        String expected = Localizer.getInstance().getMessage("lblEc1SpikeRumour", common.getName());
        Assert.assertEquals(rumour, expected);
        // Season-change path also surfaces the rumour (HUD may be unavailable in unit tests).
        AscendantCardEconomy.showSpikeRumourNotification();
    }

    @Test
    public void inStandardDustCostsMatchPreEc1PerRarityAmounts() {
        AdventurePlayer player = Current.player();
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        AscendantCardEconomy.setSeasonSpikesForTest(List.of()); // no spike mult
        // Pre-EC1 (feature/set-start): craftCost=50 and salvageDust=10 for every rarity;
        // rarity-split dust chooses which bucket (C/U/R/M), not a different amount.
        int origCraft = ascendantConfig.craftCost;
        int origSalvage = ascendantConfig.salvageDust;
        Assert.assertEquals(origCraft, 50);
        Assert.assertEquals(origSalvage, 10);

        PaperCard common = pickZenByRarity(CardRarity.Common, true);
        PaperCard uncommon = pickZenByRarity(CardRarity.Uncommon, true);
        PaperCard rare = card("Day of Judgment", "ZEN");
        if (rare == null)
            rare = pickZenByRarity(CardRarity.Rare, true);
        PaperCard mythic = null;
        for (PaperCard pc : FModel.getMagicDb().getCommonCards().getAllCards()) {
            if (pc != null && "ZEN".equals(pc.getEdition()) && pc.getRarity() == CardRarity.MythicRare
                    && !AscendantCardEconomy.isPricelessBasic(pc)
                    && AscendantCardEconomy.isStandardLegal(pc)) {
                mythic = pc;
                break;
            }
        }
        Assert.assertNotNull(common);
        Assert.assertNotNull(uncommon);
        Assert.assertNotNull(rare);
        for (PaperCard pc : List.of(common, uncommon, rare)) {
            Assert.assertTrue(AscendantCardEconomy.isStandardLegal(pc), pc.getName());
            Assert.assertEquals(AscendantCardEconomy.relativeWorth(pc), 1f, 0.001f,
                    "in-Standard worth must be 1.0 (life cycle only), " + pc.getName());
            Assert.assertEquals(player.craftCost(pc), origCraft,
                    "in-Standard craft must equal pre-EC1 craftCost for " + pc.getRarity());
            Assert.assertEquals(player.salvageYield(pc), origSalvage,
                    "in-Standard salvage must equal pre-EC1 salvageDust for " + pc.getRarity());
        }
        if (mythic != null) {
            Assert.assertEquals(AscendantCardEconomy.relativeWorth(mythic), 1f, 0.001f);
            Assert.assertEquals(player.craftCost(mythic), origCraft);
            Assert.assertEquals(player.salvageYield(mythic), origSalvage);
        }
    }

    @Test
    public void lifeCycleMovesDustAbovePreEc1Baseline() {
        AdventurePlayer player = Current.player();
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        AscendantCardEconomy.setSeasonSpikesForTest(List.of());
        // Rotated rare after 4 seasons: worth > 1, Historic ×factor on craft.
        PaperCard geopede = card("Plated Geopede", "ZEN");
        Assert.assertNotNull(geopede);
        player.getStandardWindow().restoreSets(List.of("M10", "M11", "M12"));
        AscendantCardEconomy.noteSetRotatedForTest("ZEN", 1);
        player.getSeason().setCalendarForTest(40, 5); // 4 seasons out
        Assert.assertFalse(player.isStandardLegal(geopede));
        float worth = AscendantCardEconomy.relativeWorth(geopede);
        Assert.assertTrue(worth > 1f, "post-rotation worth must rise, got " + worth);
        int craft = player.craftCost(geopede);
        int expectedCraft = Math.max(1, Math.round(ascendantConfig.craftCost * worth
                * ascendantConfig.historicCraftFactor));
        Assert.assertEquals(craft, expectedCraft, "Historic factor applies on top of life-cycle worth");

        // Badlands after 24 seasons: salvage bounded, above baseline.
        PaperCard badlands = card("Badlands", "LEB");
        if (badlands == null)
            badlands = card("Badlands", null);
        Assert.assertNotNull(badlands);
        player.getSeason().setCalendarForTest(400, 24);
        int salvage = player.salvageYield(badlands);
        Assert.assertTrue(salvage > ascendantConfig.salvageDust,
                "late pedigree salvage above baseline, got " + salvage);
        Assert.assertTrue(salvage <= 120, "late pedigree salvage bounded, got " + salvage);
    }

    @Test
    public void planCraftMissingDoesNotMutateMaterials() {
        AdventurePlayer player = Current.player();
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        PaperCard bolt = card("Lightning Bolt", "M10");
        Assert.assertNotNull(bolt);
        int unit = Math.max(1, player.craftCost(bolt));
        player.addDust(AdventurePlayer.DUST_COMMON, unit * 2 + 10);
        forge.adventure.data.MaterialData ash = forge.adventure.data.MaterialListData.primaryReagent("R", 1);
        Assert.assertNotNull(ash);
        int before = player.getMaterial(ash.id);
        player.addMaterial(ash.id, 4);
        int afterAdd = player.getMaterial(ash.id);
        CraftingApi.CraftMissingPlan plan = CraftingApi.planCraftMissing(List.of(
                new CraftingApi.MissingRequest("Lightning Bolt", 2, 0, bolt)));
        Assert.assertTrue(plan.craftEnabled, plan.disabledReason);
        Assert.assertEquals(player.getMaterial(ash.id), afterAdd,
                "planning must not take or re-add materials");
        Assert.assertTrue(player.getMaterial(ash.id) >= before + 4);
    }


    // ---- helpers -------------------------------------------------------------------------------

    /** Drive one real SN1 season end + shortlist pick. Returns false when no pick is possible. */
    private static boolean advanceOneSeason(AdventurePlayer player) {
        var season = player.getSeason();
        var window = player.getStandardWindow();
        if (!season.endByMastery(window, new forge.deck.CardPool()))
            return false;
        if (!season.isChoicePending() || season.getShortlist().isEmpty())
            return false;
        String pick = season.getShortlist().get(0);
        season.applyPick(window, pick, false);
        return true;
    }


    private static void ensurePlayerSaveable(AdventurePlayer player) {
        try {
            Field name = AdventurePlayer.class.getDeclaredField("name");
            name.setAccessible(true);
            if (name.get(player) == null)
                name.set(player, "Ec1Hero");
            Field diff = AdventurePlayer.class.getDeclaredField("difficultyData");
            diff.setAccessible(true);
            DifficultyData d = (DifficultyData) diff.get(player);
            if (d != null && (d.name == null || d.name.isEmpty()))
                d.name = "Normal";
            Field mode = AdventurePlayer.class.getDeclaredField("adventureMode");
            mode.setAccessible(true);
            if (mode.get(player) == null)
                mode.set(player, forge.adventure.util.AdventureModes.Standard);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void ensureFModelMagicDb() {
        try {
            if (FModel.getMagicDb() != null && FModel.getMagicDb().getCommonCards() != null)
                return;
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

    private static PaperCard card(String name, String set) {
        try {
            if (set != null)
                return magicDb.getCommonCards().getCard(name, set);
            return magicDb.getCommonCards().getUniqueByName(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static PaperCard pickZenByRarity(CardRarity rarity, boolean best) {
        PaperCard pick = null;
        double bestRank = best ? Double.MAX_VALUE : -1;
        char want = switch (rarity) {
            case Common -> 'C';
            case Uncommon -> 'U';
            case Rare -> 'R';
            case MythicRare -> 'M';
            default -> '?';
        };
        Path rnk = resolveForgeGuiDir().resolve("res/draft/rankings/zen.rnk");
        try {
            for (String line : Files.readAllLines(rnk)) {
                if (line == null || line.isEmpty() || line.startsWith("//") || line.startsWith("#") && !line.contains("|"))
                    continue;
                // Format: #rank|Name|Rarity|Set  (comment lines use //)
                String raw = line.startsWith("#") ? line.substring(1) : line;
                String[] parts = raw.split("\\|");
                if (parts.length < 4)
                    continue;
                if (parts[2].trim().isEmpty() || parts[2].trim().charAt(0) != want)
                    continue;
                if (!"ZEN".equalsIgnoreCase(parts[3].trim()))
                    continue;
                String name = parts[1].trim();
                PaperCard pc = magicDb.getCommonCards().getCard(name, "ZEN");
                if (pc == null) {
                    for (PaperCard cand : magicDb.getCommonCards().getAllCards(name)) {
                        if ("ZEN".equalsIgnoreCase(cand.getEdition()) && cand.getRarity() == rarity) {
                            pc = cand;
                            break;
                        }
                    }
                }
                if (pc == null || pc.getRarity() != rarity)
                    continue;
                Double r = DraftRankCache.getRanking(name, "ZEN");
                if (r == null)
                    continue;
                if (best && r < bestRank) {
                    bestRank = r;
                    pick = pc;
                } else if (!best && r > bestRank) {
                    bestRank = r;
                    pick = pc;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return pick;
    }

    private static Path resolveForgeGuiDir() {
        for (String rel : new String[] { "forge-gui", "../forge-gui" }) {
            Path p = Path.of(rel).toAbsolutePath().normalize();
            if (Files.isDirectory(p.resolve("res/editions")))
                return p;
        }
        throw new IllegalStateException("forge-gui not found");
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
