package forge.adventure;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import forge.ImageKeys;
import forge.StaticData;
import forge.adventure.data.ConfigData;
import forge.adventure.data.UIData;
import forge.adventure.player.AdventureSeason;
import forge.adventure.player.StandardWindow;
import forge.adventure.scene.InnScene;
import forge.adventure.scene.PlayerStatisticScene;
import forge.adventure.stage.GameHUD;
import forge.adventure.util.Config;
import forge.adventure.util.SaveFileData;
import forge.card.CardEdition;
import forge.deck.CardPool;
import forge.model.FModel;
import forge.util.Lang;
import forge.util.Localizer;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.OrderedMap;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SN1 seasons: 4-set Standard, calendar, shortlist, grace auto-pick, undo, old-save default.
 * Real behaviour against the isolated temp user dir — no source greps.
 */
public class SeasonsSn1Test {

    private static Path realUserDir;
    private static Map<String, AdventureTestUserDir.FileStamp> realUserDirSnapshot;
    private static StaticData magicDb;
    private static StaticData previousStaticData;
    private static ConfigData classAscendantConfig;
    private static String initError;

    private ConfigData ascendantConfig;
    private ConfigData stockConfig;

    @BeforeClass
    public void bootstrapConfigAndCardDb() {
        try {
            realUserDir = AdventureTestUserDir.defaultRealUserDir();
            realUserDirSnapshot = AdventureTestUserDir.snapshot(realUserDir);
            AdventureTestUserDir.requireIsolatedUserDir();

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
            Assert.assertNotNull(magicDb, "FModel magic DB required for SN1");
            pinStaticData(magicDb);

            Path cfgPath = forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/config.json");
            Assert.assertTrue(Files.isRegularFile(cfgPath), "Ascendant config missing: " + cfgPath);
            // UTF-8 no BOM
            byte[] raw = Files.readAllBytes(cfgPath);
            Assert.assertFalse(raw.length >= 3 && (raw[0] & 0xff) == 0xef
                            && (raw[1] & 0xff) == 0xbb && (raw[2] & 0xff) == 0xbf,
                    "Ascendant config.json must not have a UTF-8 BOM");
            String text = Files.readString(cfgPath, StandardCharsets.UTF_8);
            Assert.assertTrue(text.contains("\"sn1Seasons\""), "SN1 block missing from Ascendant config.json");
            Assert.assertTrue(text.contains("\"sn1WindowSize\""), "sn1WindowSize missing from config.json");

            classAscendantConfig = new Json().fromJson(ConfigData.class, new FileHandle(cfgPath.toFile()));
            Assert.assertNotNull(classAscendantConfig);
            Assert.assertTrue(classAscendantConfig.ascendantRules);
            Assert.assertTrue(classAscendantConfig.sn1Seasons);
            Assert.assertEquals(classAscendantConfig.sn1WindowSize, 4);
            Assert.assertEquals(classAscendantConfig.sn1SeasonDays, 14);
            Assert.assertEquals(classAscendantConfig.sn1OpeningSeasonDays, 7);
            Assert.assertEquals(classAscendantConfig.sn1RestGoldCost, 25,
                    "Inn Rest gold cost must be present and tunable");

            Config.resetInstanceForTest();
            Config.instance();
            Config.installConfigDataForTest(classAscendantConfig);
            Assert.assertTrue(Config.ascendant());
        } catch (Throwable t) {
            initError = t.getClass().getSimpleName() + ": " + t.getMessage();
            t.printStackTrace();
        }
    }

    @AfterClass(alwaysRun = true)
    public void restorePinnedStateAndAssertRealUserDirUntouched() throws Exception {
        try {
            pinStaticData(previousStaticData);
            Config.resetInstanceForTest();
        } finally {
            if (realUserDirSnapshot != null) {
                AdventureTestUserDir.assertUnchanged(realUserDir, realUserDirSnapshot, "SeasonsSn1Test");
            }
        }
    }

    @BeforeMethod
    public void setUp() {
        Assert.assertNull(initError, "SN1 BeforeClass bootstrap failed: " + initError);
        pinStaticData(magicDb);
        AdventureTestUserDir.requireIsolatedUserDir();
        ascendantConfig = classAscendantConfig;
        Config.installConfigDataForTest(ascendantConfig);
        stockConfig = new ConfigData();
        stockConfig.ascendantRules = false;
    }

    @AfterMethod
    public void tearDown() {
        Config.installConfigDataForTest(ascendantConfig);
    }

    @Test
    public void windowHoldsFourAndRotatesOldestOnFifth() {
        Assert.assertEquals(StandardWindow.resolvedWindowSize(), 4);
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12", "ZEN", "ROE"));
        Assert.assertEquals(window.getSets().size(), 4);
        Assert.assertEquals(window.addSet("WWK"), "M11");
        Assert.assertEquals(window.getSets(), List.of("M12", "ZEN", "ROE", "WWK"));
        Assert.assertEquals(window.newestSet(), "WWK");
        // Historic: rotated set is no longer in the window.
        Assert.assertFalse(window.getSets().contains("M11"));
    }

    @Test
    public void stockWorldLeavesSn1DefaultsUnused() {
        Config.installConfigDataForTest(stockConfig);
        Assert.assertFalse(Config.ascendant());
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(new StandardWindow());
        Assert.assertFalse(season.isEnabled(), "stock must not run the season calendar");
    }

    @Test
    public void calendarEndsSeasonAfterNDays() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "ZEN"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        Assert.assertFalse(season.isChoicePending());
        // Opening season = 7 days. 15 explore minutes = 1 day → 7*15*60 seconds.
        float seconds = 7f * 15f * 60f;
        season.tickExploring(seconds + 1f, window, new CardPool());
        Assert.assertTrue(season.isChoicePending(), "calendar must end the opening season");
        Assert.assertFalse(season.getShortlist().isEmpty(), "shortlist must be built");
    }

    @Test
    public void duelHoursAdvanceCalendar() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "ZEN"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        double before = season.getGameDays();
        season.chargeDuelHours(window, new CardPool());
        Assert.assertEquals(season.getGameDays(), before + 2d / 24d, 1e-6);
    }

    @Test
    public void masteryEndsSeasonEarly() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "ZEN"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        Assert.assertFalse(season.isChoicePending());
        Assert.assertTrue(season.endByMastery(window, new CardPool()));
        Assert.assertTrue(season.isChoicePending());
        Assert.assertTrue(season.isNewestMasteredThisSeason());
        Assert.assertFalse(season.getShortlist().isEmpty());
        // Calendar must not also be required — season ended on day 0.
        Assert.assertTrue(season.daysIntoSeason() < season.seasonLengthDays());
    }

    @Test
    public void shortlistHasThreeDistinctSetsIncludingReleaseOrder() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        season.endByMastery(window, new CardPool());
        List<String> shortlist = season.getShortlist();
        Assert.assertFalse(shortlist.isEmpty(), "shortlist must not be empty with a live card DB");
        Assert.assertTrue(shortlist.size() <= ascendantConfig.sn1ShortlistSize);
        Set<String> distinct = new HashSet<>(shortlist);
        Assert.assertEquals(distinct.size(), shortlist.size(), "shortlist entries must be distinct");
        for (String code : shortlist) {
            Assert.assertFalse(window.getSets().contains(code), "shortlist must exclude window sets");
        }
        String release = season.getReleaseOrderPick();
        Assert.assertNotNull(release, "release-order pick required");
        Assert.assertTrue(shortlist.contains(release), "shortlist must include release-order option");
        // Cross-check against the static helper.
        List<CardEdition> choosable = window.choosableSets();
        Assert.assertFalse(choosable.isEmpty());
        String expected = AdventureSeason.nextByReleaseOrder(window, choosable);
        Assert.assertEquals(release, expected);
    }

    @Test
    public void skippingChoiceAutoPicksAfterGracePeriod() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        season.endByMastery(window, new CardPool());
        Assert.assertTrue(season.isChoicePending());
        String auto = season.getReleaseOrderPick();
        Assert.assertNotNull(auto);
        season.deferChoice();
        // Advance past grace (3 days) via exploring.
        float graceSeconds = ascendantConfig.sn1GracePeriodDays * 15f * 60f + 5f;
        List<String> before = new ArrayList<>(window.getSets());
        season.tickExploring(graceSeconds, window, new CardPool());
        Assert.assertFalse(season.isChoicePending(), "grace must auto-pick");
        Assert.assertTrue(window.getSets().contains(auto), "auto-pick must add release-order set");
        Assert.assertEquals(window.getSets().size(), before.size() + 1);
    }

    @Test
    public void undoWorksUntilLocked() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        season.endByMastery(window, new CardPool());
        String pick = season.getShortlist().get(0);
        List<String> before = new ArrayList<>(window.getSets());
        season.applyPick(window, pick, false);
        Assert.assertTrue(season.isUndoAvailable());
        Assert.assertTrue(window.getSets().contains(pick));
        season.undoPick(window);
        Assert.assertEquals(window.getSets(), before);
        Assert.assertTrue(season.isChoicePending());
        Assert.assertFalse(season.isUndoAvailable());

        // Re-pick then lock.
        season.applyPick(window, pick, false);
        Assert.assertTrue(season.isUndoAvailable());
        season.lockUndo();
        Assert.assertFalse(season.isUndoAvailable());
        Assert.assertNull(season.undoPick(window));
        Assert.assertTrue(window.getSets().contains(pick));
    }

    @Test
    public void oldSaveWithoutSeasonDataGetsDefaultFromRotation() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12", "ZEN"));
        SaveFileData data = new SaveFileData();
        // No sn1* keys — legacy save.
        AdventureSeason season = new AdventureSeason();
        season.load(data, window);
        Assert.assertTrue(season.isInitialized());
        Assert.assertTrue(season.isEnabled());
        Assert.assertEquals(season.seasonsCompleted(), 1,
                "3-set legacy window ⇒ one completed opening season");
        Assert.assertFalse(season.isChoicePending());
        Assert.assertEquals(season.seasonLengthDays(), ascendantConfig.sn1OpeningSeasonDays,
                "one opening season still remaining");
        // Window keeps its 3 sets and has room for a 4th.
        Assert.assertEquals(window.getSets().size(), 3);
        Assert.assertTrue(StandardWindow.resolvedWindowSize() >= 4);
    }

    @Test
    public void oldSaveRoundTripSaveLoadKeepsSeason() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M10", "M11", "M12"));
        AdventureSeason season = new AdventureSeason();
        season.initFromLegacyRotation(window);
        // Advance a bit and save.
        season.tickExploring(60f, window, new CardPool());
        SaveFileData data = new SaveFileData();
        data.storeObject("standardSets", window.saveSets());
        season.save(data);

        StandardWindow loadedWindow = new StandardWindow();
        loadedWindow.load(window.saveSets(), false, false, window.saveHistory());
        AdventureSeason loaded = new AdventureSeason();
        loaded.load(data, loadedWindow);
        Assert.assertTrue(loaded.isInitialized());
        Assert.assertEquals(loaded.seasonsCompleted(), season.seasonsCompleted());
        Assert.assertEquals(loaded.getGameDays(), season.getGameDays(), 1e-6);
        Assert.assertEquals(loadedWindow.getSets(), window.getSets());

        // Same blob with SN1 keys stripped still loads a sensible default.
        data.remove("sn1Initialized");
        AdventureSeason legacy = new AdventureSeason();
        legacy.load(data, loadedWindow);
        Assert.assertTrue(legacy.isInitialized());
        Assert.assertEquals(legacy.seasonsCompleted(), 1);
    }

    @Test
    public void pinSkipsPinnedSetOnRotation() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12", "ZEN", "ROE"));
        Assert.assertEquals(window.addSetForSeason("WWK", "M11"), "M12",
                "pinned oldest must be skipped; next-oldest rotates");
        Assert.assertTrue(window.getSets().contains("M11"));
        Assert.assertFalse(window.getSets().contains("M12"));
        Assert.assertTrue(window.didLastAddConsumePin(), "pin skip must be reported to the season");
    }

    @Test
    public void pinExpiresAfterSkipPath() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12", "ZEN", "ROE"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        Assert.assertTrue(season.pinSet("M11", window));
        Assert.assertEquals(season.getPinnedSet(), "M11");
        season.endByMastery(window, new CardPool());
        String pick = season.getShortlist().get(0);
        season.applyPick(window, pick, false);
        Assert.assertNull(season.getPinnedSet(), "pin must expire after the skip path consumes it");
        Assert.assertTrue(window.getSets().contains("M11"), "pinned set stays in the window");
        Assert.assertFalse(window.getSets().contains("M12"), "next-oldest rotates instead");
    }

    @Test
    public void undoRemovesHistoryAndRestoresShortlistWithoutReseed() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        season.endByMastery(window, new CardPool());
        List<String> shortlistBefore = new ArrayList<>(season.getShortlist());
        String pick = shortlistBefore.get(0);
        Assert.assertFalse(window.getUnlockedHistory().contains(pick)
                        || window.getSets().contains(pick));
        season.applyPick(window, pick, false);
        Assert.assertTrue(window.getUnlockedHistory().contains(pick));
        Assert.assertTrue(season.isUndoAvailable());
        season.undoPick(window);
        Assert.assertFalse(window.getUnlockedHistory().contains(pick),
                "undo must remove the set from unlockedHistory when it was a first unlock");
        Assert.assertEquals(season.getShortlist(), shortlistBefore,
                "undo must restore the same shortlist (no nanoTime reseed)");
        Assert.assertTrue(season.isChoicePending());
    }

    @Test
    public void undoKeepsHistoryWhenReAddingPreviouslyRotatedSet() {
        StandardWindow window = new StandardWindow();
        // Full window; history includes M11 which will rotate out then come back.
        window.init(List.of("M11", "M12", "ZEN", "ROE"));
        Assert.assertTrue(window.getUnlockedHistory().contains("M11"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        // Rotate M11 out by picking a new set.
        season.endByMastery(window, new CardPool());
        String firstPick = season.getShortlist().get(0);
        season.applyPick(window, firstPick, false);
        season.lockUndo();
        Assert.assertFalse(window.getSets().contains("M11"), "M11 rotated out");
        Assert.assertTrue(window.getUnlockedHistory().contains("M11"), "history keeps M11");

        // Season end again; force-pick M11 back via chooseAny (Planar Key).
        season.endByMastery(window, new CardPool());
        season.addPlanarKeys(1);
        Assert.assertFalse(window.getSets().contains("M11"));
        season.applyPick(window, "M11", true);
        Assert.assertTrue(window.getSets().contains("M11"));
        Assert.assertTrue(window.getUnlockedHistory().contains("M11"));
        season.undoPick(window);
        Assert.assertTrue(window.getUnlockedHistory().contains("M11"),
                "undo must not erase a real prior unlock when re-adding a rotated-out set");
        Assert.assertFalse(window.getSets().contains("M11"));
    }

    @Test
    public void undoRefundsChooseAnyPlanarKey() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        // Calendar end (not mastery) so mastery rewards do not mask the Choose-any spend.
        season.tickExploring(7f * 15f * 60f + 1f, window, new CardPool());
        Assert.assertTrue(season.isChoicePending());
        season.addPlanarKeys(1);
        int keysBefore = season.getPlanarKeys();
        String any = null;
        for (CardEdition ed : window.choosableSets()) {
            if (!season.getShortlist().contains(ed.getCode())) {
                any = ed.getCode();
                break;
            }
        }
        if (any == null)
            any = season.getShortlist().get(0);
        season.applyPick(window, any, true);
        Assert.assertNull(season.getLastPickError(), "Choose any with a Planar Key must succeed");
        Assert.assertFalse(season.isChoicePending());
        Assert.assertTrue(window.getSets().contains(any));
        Assert.assertEquals(season.getPlanarKeys(), keysBefore - 1, "Choose any spends a Planar Key");
        Assert.assertTrue(season.isUndoAvailable());
        season.undoPick(window);
        Assert.assertEquals(season.getPlanarKeys(), keysBefore, "undo must refund the Planar Key");
        Assert.assertFalse(window.getUnlockedHistory().contains(any));
    }

    @Test
    public void undoSeasonCountersSurviveSaveLoad() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        season.endByMastery(window, new CardPool());
        String pick = season.getShortlist().get(0);
        double startBefore = season.daysIntoSeason();
        season.applyPick(window, pick, false);
        Assert.assertTrue(season.isUndoAvailable());
        SaveFileData data = new SaveFileData();
        season.save(data);
        Assert.assertTrue(data.containsKey("sn1UndoSeasonStartDays"));
        Assert.assertTrue(data.containsKey("sn1UndoSeasonsCompleted"));

        AdventureSeason loaded = new AdventureSeason();
        StandardWindow loadedWindow = new StandardWindow();
        loadedWindow.load(window.saveSets(), true, false, window.saveHistory());
        loaded.load(data, loadedWindow);
        Assert.assertTrue(loaded.isUndoAvailable());
        String removed = loaded.undoPick(loadedWindow);
        Assert.assertEquals(removed, pick);
        Assert.assertEquals(loaded.seasonsCompleted(), 0);
        Assert.assertEquals(loadedWindow.getSets(), List.of("M11", "M12"));
        Assert.assertEquals(startBefore, loaded.daysIntoSeason(), 1e-6);
    }

    @Test
    public void shortlistPickRejectedWhenNotOnList() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        season.endByMastery(window, new CardPool());
        Assert.assertNull(season.applyPick(window, "ZZZ_NOT_A_SET", false));
        Assert.assertTrue(season.isChoicePending());
        Assert.assertNotNull(season.getLastPickError());
    }

    @Test
    public void emptyChoosableSetsEndsSeasonWithNoPick() {
        StandardWindow window = new StandardWindow();
        List<String> all = new ArrayList<>();
        for (CardEdition ed : StandardWindow.boosterSets())
            all.add(ed.getCode());
        Assert.assertFalse(all.isEmpty(), "need a live card DB");
        window.init(all);
        Assert.assertTrue(window.choosableSets().isEmpty());
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        AtomicInteger fires = new AtomicInteger();
        season.addSeasonChangeListener(s -> fires.incrementAndGet());
        Assert.assertTrue(season.endByMastery(window, new CardPool()));
        Assert.assertFalse(season.isChoicePending(), "empty choosable must not leave pick pending");
        Assert.assertEquals(season.seasonsCompleted(), 1);
        Assert.assertEquals(fires.get(), 1, "season-change listener must fire on empty end");
    }

    @Test
    public void remasteringAlreadyRewardedSetDoesNotFarmKeys() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        Assert.assertTrue(season.endByMastery(window, new CardPool()));
        String pick1 = season.getShortlist().get(0);
        int keysBefore = season.getPlanarKeys();
        season.applyPick(window, pick1, false);
        int keysAfterFirst = season.getPlanarKeys();
        Assert.assertTrue(keysAfterFirst > keysBefore, "first mastery grants Planar Keys");

        // Put M12 back as newest; it was already rewarded — must not farm again.
        window.restoreSets(List.of("M11", "M12"));
        Assert.assertEquals(window.newestSet(), "M12");
        Assert.assertTrue(season.endByMastery(window, new CardPool()));
        String pick2 = season.getShortlist().get(0);
        season.applyPick(window, pick2, false);
        Assert.assertEquals(season.getPlanarKeys(), keysAfterFirst,
                "re-picking/remastering an already-rewarded set must not farm Planar Keys");
    }

    @Test
    public void innRestIsOncePerDayAndCannotBeChainedAfterWaking() {
        ConfigData freeRest = new Json().fromJson(ConfigData.class,
                new FileHandle(resolveForgeGuiDir()
                        .resolve("res/adventure/Shandalar Ascendant/config.json").toFile()));
        freeRest.sn1RestGoldCost = 0; // exercise once-per-day without a live gold wallet
        Config.installConfigDataForTest(freeRest);
        float secondsPerDay = Math.max(0.01f, freeRest.sn1ExploreMinutesPerDay) * 60f;

        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "ZEN"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        Assert.assertEquals(season.restGoldCost(), 0);
        int dayBefore = season.calendarDayNumber();
        Assert.assertTrue(season.canRestToday());
        Assert.assertTrue(season.restUntilMorning(window, new CardPool()));
        Assert.assertEquals(season.calendarDayNumber(), dayBefore + 1, "Rest advances to next morning");

        // Woke on day N+1: no second rest straight away (that chained paid rests before).
        Assert.assertFalse(season.canRestToday(), "cannot rest again on the morning you woke up");
        Assert.assertFalse(season.restUntilMorning(window, new CardPool()), "chained rest must be refused");
        Assert.assertEquals(season.calendarDayNumber(), dayBefore + 1, "refused rest does not advance time");

        // Half a day of exploring is still the same day.
        season.tickExploring(secondsPerDay * 0.5f, window, new CardPool());
        Assert.assertEquals(season.calendarDayNumber(), dayBefore + 1);
        Assert.assertFalse(season.canRestToday(), "still the day you woke up");

        // After living through that full day (now day N+2), one rest is allowed again.
        season.tickExploring(secondsPerDay * 0.5f + 1f, window, new CardPool());
        Assert.assertEquals(season.calendarDayNumber(), dayBefore + 2);
        Assert.assertTrue(season.canRestToday(), "a full day after waking, Rest opens again");
        Assert.assertTrue(season.restUntilMorning(window, new CardPool()));
        Assert.assertEquals(season.calendarDayNumber(), dayBefore + 3);
        Assert.assertFalse(season.canRestToday(), "and the rule repeats");
    }

    @Test
    public void undoRestoresConsumedPin() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12", "ZEN", "ROE"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        Assert.assertTrue(season.pinSet("M11", window));
        Assert.assertEquals(season.getPinnedSet(), "M11");
        season.endByMastery(window, new CardPool());
        String pick = season.getShortlist().get(0);
        season.applyPick(window, pick, false);
        Assert.assertNull(season.getPinnedSet(), "pin consumed by skip path");
        season.undoPick(window);
        Assert.assertEquals(season.getPinnedSet(), "M11", "undo must restore the consumed pin");
    }

    @Test
    public void undoRemovesGateFromInactiveHomeBlob() throws Exception {
        forge.adventure.data.PointOfInterestData.clearRuntimeCacheForTests();
        forge.adventure.world.SetPlaneGenerator.ensurePlanarGateRegistered();
        forge.adventure.data.PointOfInterestData gateDef =
                forge.adventure.data.PointOfInterestData.getPointOfInterest("PlanarGate");
        Assert.assertNotNull(gateDef, "PlanarGate template required");

        forge.adventure.world.World home = new forge.adventure.world.World();
        forge.adventure.data.WorldData data = new forge.adventure.data.WorldData();
        data.width = 64;
        data.height = 64;
        data.tileSize = 16;
        data.playerStartPosX = 0.5f;
        data.playerStartPosY = 0.5f;
        home.installTestWorldGrid(data, 11L);
        String planeId = forge.adventure.world.SetPlaneGenerator.planeIdForSet("ZEN");
        forge.adventure.pointofintrest.PointOfInterest gate =
                new forge.adventure.pointofintrest.PointOfInterest(
                        gateDef, new com.badlogic.gdx.math.Vector2(48f, 48f), new java.util.Random(1L));
        gate.setTargetPlane(planeId);
        home.addPointOfInterest(gate);
        Assert.assertTrue(forge.adventure.world.PlanarPortalPlacer.existingPortalTargets(home)
                .contains(planeId));

        forge.adventure.world.MultiverseState multi = new forge.adventure.world.MultiverseState();
        multi.initHomeFromLive(11L, 0f, 0f);
        forge.adventure.world.PlaneMeta homeMeta = multi.getMeta(forge.adventure.world.PlaneMeta.HOME_ID);
        // Hashable test save carries mapPoiIds without requiring a full pixmap world.
        forge.adventure.util.SaveFileData worldSave = home.saveHashableStateForTest();
        forge.adventure.util.SaveFileData blob = forge.adventure.world.PlaneBlob.pack(
                worldSave, new forge.adventure.util.SaveFileData(),
                new forge.adventure.util.SaveFileData(), homeMeta);
        multi.writeInactiveBlob(forge.adventure.world.PlaneMeta.HOME_ID, blob);
        forge.Forge.safeDispose(home);

        Assert.assertTrue(forge.adventure.world.PlanarPortalPlacer
                .removeGateFromInactiveHomeBlob(multi, planeId));

        forge.adventure.util.SaveFileData updated = multi.readInactiveBlob(forge.adventure.world.PlaneMeta.HOME_ID);
        Assert.assertNotNull(updated);
        forge.adventure.util.SaveFileData worldData = forge.adventure.world.PlaneBlob.world(updated);
        Assert.assertNotNull(worldData);
        forge.adventure.pointofintrest.PointOfInterestMap map =
                new forge.adventure.pointofintrest.PointOfInterestMap(16, 16, 4, 4);
        map.load(worldData.readSubData("mapPoiIds"));
        boolean stillPresent = false;
        for (forge.adventure.pointofintrest.PointOfInterest poi : map.getAllPointOfInterest()) {
            if (poi != null && planeId.equals(poi.getTargetPlane())) {
                stillPresent = true;
                break;
            }
        }
        Assert.assertFalse(stillPresent, "gate must be gone from the inactive home blob after undo");
    }

    @Test
    public void calendarApiExposesDaySeasonIndexAndListener() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "ZEN"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        Assert.assertEquals(season.getGameDays(), 0d, 1e-9);
        Assert.assertEquals(season.calendarDayNumber(), 1);
        Assert.assertEquals(season.getSeasonIndex(), 0);
        AtomicInteger fires = new AtomicInteger();
        season.addSeasonChangeListener(s -> {
            fires.incrementAndGet();
            Assert.assertSame(s, season);
        });
        season.endByMastery(window, new CardPool());
        season.applyPick(window, season.getShortlist().get(0), false);
        Assert.assertEquals(fires.get(), 1);
        Assert.assertEquals(season.getSeasonIndex(), 1);
        Assert.assertTrue(season.getGameDays() >= 0d);
    }

    @Test
    public void legacyChoicePendingClearedOnNewGamePlus() {
        // Use stock config so isChoicePending reads the legacy flag (not a live SN1 season).
        Config.installConfigDataForTest(stockConfig);
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "M12"));
        window.setLegacyChoicePending(true);
        Assert.assertTrue(window.isChoicePending(), "legacy pending visible before NG+ clear");
        window.startNewWorld();
        Assert.assertFalse(window.isSetUnlockedThisWorld());
        Assert.assertFalse(window.isChoicePending(),
                "legacy standardChoicePending must be cleared on NG+");
    }

    @Test
    public void pendingSetPlaneRemovedOnUnlockUndo() {
        forge.adventure.world.MultiverseState multi = new forge.adventure.world.MultiverseState();
        multi.initHomeFromLive(1L, 0f, 0f);
        String planeId = "set_zen";
        multi.registerSetPlane(planeId, 1L, "world/set_plane_world.json", "Zendikar");
        multi.rememberPendingHomeGate(planeId);
        Assert.assertTrue(multi.hasPlane(planeId));
        Assert.assertTrue(multi.removePendingSetPlane(planeId));
        Assert.assertFalse(multi.hasPlane(planeId));
        Assert.assertFalse(multi.getPendingHomeGatePlaneIds().contains(planeId));
    }

    @Test
    public void openingSeasonsUseShortLengthThenNormal() {
        StandardWindow window = new StandardWindow();
        window.init(List.of("M11", "ZEN"));
        AdventureSeason season = new AdventureSeason();
        season.initNewGame(window);
        Assert.assertEquals(season.seasonLengthDays(), 7);
        season.endByMastery(window, new CardPool());
        String a = season.getShortlist().get(0);
        season.applyPick(window, a, false);
        Assert.assertEquals(season.seasonsCompleted(), 1);
        Assert.assertEquals(season.seasonLengthDays(), 7, "second opening season still short");
        season.endByMastery(window, new CardPool());
        String b = season.getShortlist().get(0);
        season.applyPick(window, b, false);
        Assert.assertEquals(season.seasonsCompleted(), 2);
        Assert.assertEquals(season.seasonLengthDays(), 14, "normal seasons after opening count");
    }

    @Test
    public void innRestAndHudSeasonBoundsClearInBothLayouts() throws Exception {
        assertRestClearOfLayout(resolveUi("inn.json"), true);
        assertRestClearOfLayout(resolveUi("inn_portrait.json"), false);
        assertSeasonStatusClearOfLayout(resolveUi("statistic.json"), true);
        assertSeasonStatusClearOfLayout(resolveUi("statistic_portrait.json"), false);
        // HUD season line vs stock HUD actors.
        assertSeasonHudClearOfLayout(resolveUi("hud.json"), true);
        assertSeasonHudClearOfLayout(resolveUi("hud_portrait.json"), false);
    }

    /**
     * Season HUD must not collide with QL1 tracking line or the minimap in either
     * orientation. MX1's "Collection exported" line is Status/Inventory (not GameHUD).
     */
    @Test
    public void seasonHudClearOfQl1TrackingAndMinimapBothOrientations() throws Exception {
        assertSeasonHudClearOfQl1AndMinimap(resolveUi("hud.json"), true, 480f, 270f);
        assertSeasonHudClearOfQl1AndMinimap(resolveUi("hud_portrait.json"), false, 270f, 480f);
    }

    private static void assertSeasonHudClearOfQl1AndMinimap(Path layoutFile, boolean landscape,
            float layoutW, float layoutH) {
        UIData data = new Json().fromJson(UIData.class, new FileHandle(layoutFile.toFile()));
        Assert.assertEquals(data.width, layoutW, 0.01f);
        Assert.assertEquals(data.height, layoutH, 0.01f);
        float[] season = GameHUD.seasonHudStageBounds(landscape, layoutH);
        float[] quest = trackedQuestHudBounds(landscape, layoutW, layoutH);
        float[] map = minimapStageBounds(data, layoutH);
        Assert.assertFalse(rectsOverlap(season, quest),
                "seasonHud " + Arrays.toString(season) + " overlaps QL1 trackedQuestHud "
                        + Arrays.toString(quest) + " in " + layoutFile.getFileName());
        Assert.assertFalse(rectsOverlap(season, map),
                "seasonHud " + Arrays.toString(season) + " overlaps minimap "
                        + Arrays.toString(map) + " in " + layoutFile.getFileName());
        // Party status sits above the quest line; season is below — still assert clearance.
        float[] party = partyStatusHudBounds(landscape, layoutH);
        Assert.assertFalse(rectsOverlap(season, party),
                "seasonHud " + Arrays.toString(season) + " overlaps partyStatus "
                        + Arrays.toString(party) + " in " + layoutFile.getFileName());
    }

    /** Mirrors GameHUD trackedQuestHud placement (width/height for a single HUD line). */
    private static float[] trackedQuestHudBounds(boolean landscape, float layoutW, float layoutH) {
        float x = 5f;
        float y = landscape ? 78f : layoutH - 145f;
        float w = Math.min(220f, layoutW * 0.45f);
        float h = 18f;
        return new float[] { x, y, w, h };
    }

    private static float[] partyStatusHudBounds(boolean landscape, float layoutH) {
        float x = 5f;
        float y = landscape ? 102f : layoutH - 120f;
        return new float[] { x, y, 160f, 20f };
    }

    private static float[] minimapStageBounds(UIData data, float layoutH) {
        for (OrderedMap<String, String> el : data.elements) {
            if (el == null)
                continue;
            String name = null;
            Float x = null, y = null, w = null, h = null;
            for (ObjectMap.Entry property : new OrderedMap.OrderedMapEntries<>(el)) {
                String key = property.key == null ? null : property.key.toString();
                Object val = property.value;
                if ("name".equals(key))
                    name = val == null ? null : val.toString();
                else if ("x".equals(key))
                    x = parseFloat(val);
                else if ("y".equals(key))
                    y = parseFloat(val);
                else if ("width".equals(key))
                    w = parseFloat(val);
                else if ("height".equals(key))
                    h = parseFloat(val);
            }
            if (!"map".equals(name) || x == null || y == null || w == null || h == null)
                continue;
            return new float[] { x, layoutH - y - h, w, h };
        }
        throw new IllegalStateException("minimap 'map' actor missing from HUD layout");
    }

    @Test
    public void stockStatisticAndHudJsonUnchangedNoSeasonActors() throws Exception {
        for (String file : new String[] {
                "statistic.json", "statistic_portrait.json", "hud.json", "hud_portrait.json",
                "inn.json", "inn_portrait.json"
        }) {
            Path p = resolveUi(file);
            String text = Files.readString(p, StandardCharsets.UTF_8);
            Assert.assertFalse(text.contains("\"name\": \"seasonStatus\""),
                    "stock UI must not embed seasonStatus: " + p);
            Assert.assertFalse(text.contains("\"name\": \"seasonHud\""),
                    "stock UI must not embed seasonHud: " + p);
            Assert.assertFalse(text.contains("\"name\": \"rest\""),
                    "stock UI must not embed rest: " + p);
        }
    }

    // ---- layout helpers (AC1-style pairwise overlap) ----

    private static void assertRestClearOfLayout(Path layoutFile, boolean landscape) {
        UIData data = new Json().fromJson(UIData.class, new FileHandle(layoutFile.toFile()));
        float layoutH = data.height;
        float layoutW = data.width;
        List<float[]> others = layoutRects(data);
        float[] rest = InnScene.restStageBounds(landscape, layoutH);
        Assert.assertTrue(inside(rest, layoutW, layoutH),
                "Rest must sit inside " + layoutFile.getFileName() + ": " + Arrays.toString(rest));
        for (float[] o : others) {
            Assert.assertFalse(rectsOverlap(rest, o),
                    "Rest " + Arrays.toString(rest) + " overlaps " + Arrays.toString(o)
                            + " in " + layoutFile.getFileName());
        }
    }

    private static void assertSeasonStatusClearOfLayout(Path layoutFile, boolean landscape) {
        UIData data = new Json().fromJson(UIData.class, new FileHandle(layoutFile.toFile()));
        float layoutH = data.height;
        float layoutW = data.width;
        List<float[]> others = layoutRects(data, !landscape, java.util.Set.of("lastScreen"));
        float[] season = PlayerStatisticScene.seasonStatusStageBounds(landscape, layoutH);
        Assert.assertTrue(inside(season, layoutW, layoutH),
                "seasonStatus must sit inside " + layoutFile.getFileName());
        for (float[] o : others) {
            Assert.assertFalse(rectsOverlap(season, o),
                    "seasonStatus " + Arrays.toString(season) + " overlaps " + Arrays.toString(o)
                            + " in " + layoutFile.getFileName());
        }
    }

    private static void assertSeasonHudClearOfLayout(Path layoutFile, boolean landscape) {
        UIData data = new Json().fromJson(UIData.class, new FileHandle(layoutFile.toFile()));
        float layoutH = data.height;
        float layoutW = data.width;
        List<float[]> others = layoutRects(data);
        float[] season = GameHUD.seasonHudStageBounds(landscape, layoutH);
        Assert.assertTrue(inside(season, layoutW, layoutH),
                "seasonHud must sit inside " + layoutFile.getFileName());
        for (float[] o : others) {
            // Map occupies the top-left; season is bottom-left — still assert no overlap.
            Assert.assertFalse(rectsOverlap(season, o),
                    "seasonHud " + Arrays.toString(season) + " overlaps " + Arrays.toString(o)
                            + " in " + layoutFile.getFileName());
        }
    }

    private static List<float[]> layoutRects(UIData data) {
        return layoutRects(data, false, java.util.Set.of());
    }

    /**
     * @param trimPortraitAwardsBand when true, shrink portrait {@code scrollWindow}/{@code enemies}
     *        from the bottom by {@link PlayerStatisticScene#PORTRAIT_AWARDS_BAND} the same way
     *        production does before placing Awards / seasonStatus.
     * @param skipNames actor names to ignore (e.g. paper {@code stats} the season line sits on)
     */
    private static List<float[]> layoutRects(UIData data, boolean trimPortraitAwardsBand) {
        return layoutRects(data, trimPortraitAwardsBand, java.util.Set.of());
    }

    private static List<float[]> layoutRects(UIData data, boolean trimPortraitAwardsBand,
            java.util.Set<String> skipNames) {
        List<float[]> others = new ArrayList<>();
        float layoutH = data.height;
        for (OrderedMap<String, String> el : data.elements) {
            if (el == null)
                continue;
            String name = null;
            Float x = null, y = null, w = null, h = null;
            for (ObjectMap.Entry property : new OrderedMap.OrderedMapEntries<>(el)) {
                String key = property.key == null ? null : property.key.toString();
                Object val = property.value;
                if ("name".equals(key))
                    name = val == null ? null : val.toString();
                else if ("x".equals(key))
                    x = parseFloat(val);
                else if ("y".equals(key))
                    y = parseFloat(val);
                else if ("width".equals(key))
                    w = parseFloat(val);
                else if ("height".equals(key))
                    h = parseFloat(val);
            }
            if (name == null || name.isEmpty() || "lastScreen".equals(name))
                continue;
            if (skipNames != null && skipNames.contains(name))
                continue;
            if (x == null || y == null || w == null || h == null || w <= 0 || h <= 0)
                continue;
            if (w >= data.width - 1 && h >= data.height - 1)
                continue;
            float stageY = layoutH - y - h;
            float stageH = h;
            if (trimPortraitAwardsBand
                    && ("scrollWindow".equals(name) || "enemies".equals(name))) {
                float band = PlayerStatisticScene.PORTRAIT_AWARDS_BAND;
                stageY += band;
                stageH -= band;
            }
            if (stageH <= 0)
                continue;
            others.add(new float[] { x, stageY, w, stageH });
        }
        return others;
    }

    private static boolean inside(float[] r, float layoutW, float layoutH) {
        return r[0] >= -0.01f && r[1] >= -0.01f
                && r[0] + r[2] <= layoutW + 0.01f
                && r[1] + r[3] <= layoutH + 0.01f;
    }

    private static boolean rectsOverlap(float[] a, float[] b) {
        return a[0] < b[0] + b[2] && a[0] + a[2] > b[0]
                && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
    }

    private static Float parseFloat(Object v) {
        if (v == null)
            return null;
        if (v instanceof Number)
            return ((Number) v).floatValue();
        try {
            return Float.parseFloat(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Path resolveUi(String fileName) throws Exception {
        for (String rel : new String[] {
                "forge-gui/res/adventure/common/ui/" + fileName,
                "../forge-gui/res/adventure/common/ui/" + fileName
        }) {
            Path p = Path.of(rel);
            if (Files.isRegularFile(p))
                return p;
        }
        throw new IllegalStateException("UI layout not found: " + fileName);
    }

    private static Path resolveForgeGuiDir() {
        for (String rel : new String[] { "forge-gui", "../forge-gui" }) {
            Path p = Path.of(rel);
            if (Files.isDirectory(p.resolve("res/editions")))
                return p.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("forge-gui not found");
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

    private static StaticData readStaticDataInstance() {
        try {
            java.lang.reflect.Field f = StaticData.class.getDeclaredField("lastInstance");
            f.setAccessible(true);
            return (StaticData) f.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void pinStaticData(StaticData data) {
        try {
            java.lang.reflect.Field f = StaticData.class.getDeclaredField("lastInstance");
            f.setAccessible(true);
            f.set(null, data);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
