package forge.adventure;

import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.math.Rectangle;
import forge.adventure.character.EnemySprite;
import forge.adventure.character.PlayerSprite;
import forge.adventure.coop.CoopCharacterStore;
import forge.adventure.coop.CoopSession;
import forge.adventure.coop.CoopSessionRole;
import forge.adventure.data.AdventureQuestData;
import forge.adventure.data.AdventureQuestStage;
import forge.adventure.data.ConfigData;
import forge.adventure.data.PointOfInterestData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.scene.DuelScene;
import forge.adventure.scene.Scene;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.AdventureModes;
import forge.adventure.util.AdventureQuestController;
import forge.adventure.util.Config;
import forge.adventure.util.Current;
import forge.adventure.util.InteriorMapSave;
import forge.adventure.util.SaveFileData;
import forge.adventure.world.WorldSave;
import forge.card.ColorSet;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * SV1: save inside maps, restore position, pre-duel autosave, quest-safe ENTERPOI,
 * co-op .chr isolation, missing-TMX fallback, arena fee autosave.
 * Real save/load behaviour — no source greps. GL tests use {@link AdventureGlTestSupport}
 * and the Surefire {@code forge.test.userDir} isolation.
 */
public class SaveAnywhereSv1Test {

    private static final int SLOT = 91;
    private static final long WORLD_SEED = 0x515AFE01L;
    private static final String COOP_CHR_NAME = "Sv1CoopChr";

    @BeforeMethod
    public void setUp() {
        AdventureTestUserDir.requireIsolatedUserDir();
        InteriorMapSave.clearPending();
        resetCoopSession();
    }

    @AfterMethod
    public void tearDown() {
        InteriorMapSave.clearPending();
        resetCoopSession();
        deleteSlot(SLOT);
        deleteSlot(WorldSave.AUTO_SAVE_SLOT);
        try {
            File chr = CoopCharacterStore.characterFile(COOP_CHR_NAME);
            if (chr.isFile()) {
                Assert.assertTrue(chr.delete());
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    public void oldSaveWithoutInteriorFieldDoesNotPendRestore() {
        SaveFileData data = new SaveFileData();
        data.store("worldPosX", 10f);
        data.store("worldPosY", 20f);
        InteriorMapSave.readPending(data);
        Assert.assertFalse(InteriorMapSave.hasPending(),
                "Old saves without interior keys must load on the world map");
    }

    @Test
    public void interiorKeysRoundTripThroughSaveFileData() {
        SaveFileData data = new SaveFileData();
        data.store(InteriorMapSave.KEY_POI_ID, "poi-test-1");
        data.store(InteriorMapSave.KEY_MAP_PATH, "../common/maps/map/cave/cave_bear.tmx");
        data.store(InteriorMapSave.KEY_ENTRANCE_ID, 3);
        data.store(InteriorMapSave.KEY_POS_X, 120.5f);
        data.store(InteriorMapSave.KEY_POS_Y, 80.25f);
        data.store(InteriorMapSave.KEY_MUST_CLEAR, true);

        InteriorMapSave.readPending(data);
        Assert.assertTrue(InteriorMapSave.hasPending());
        Assert.assertEquals(InteriorMapSave.pendingPoiIdForTest(), "poi-test-1");
        InteriorMapSave.clearPending();
        Assert.assertFalse(InteriorMapSave.hasPending());
    }

    @Test
    public void stockModeDoesNotWriteInteriorKeys() {
        ConfigData stock = new ConfigData();
        stock.ascendantRules = false;
        Config.installConfigDataForTest(stock);
        try {
            SaveFileData data = new SaveFileData();
            InteriorMapSave.write(data);
            Assert.assertFalse(data.containsKey(InteriorMapSave.KEY_POI_ID),
                    "Stock modes must not write SV1 interior fields");
        } finally {
            Config.resetInstanceForTest();
        }
    }

    @Test
    public void worldMapOnlySaveMessageIsStockOnly() {
        // Ascendant: never show lblGameNotSaved for in-map saves.
        assumeAscendantConfig();
        Assert.assertTrue(InteriorMapSave.saveAnywhereEnabled());
        Assert.assertFalse(InteriorMapSave.shouldShowWorldMapOnlySaveMessage(),
                "Ascendant must not use the world-map-only save message");

        ConfigData stock = new ConfigData();
        stock.ascendantRules = false;
        Config.installConfigDataForTest(stock);
        try {
            Assert.assertFalse(InteriorMapSave.saveAnywhereEnabled());
            // Without an in-map MapStage the stock message stays off; the in-map case is
            // covered by the GL test below.
            Assert.assertFalse(InteriorMapSave.shouldShowWorldMapOnlySaveMessage());
        } finally {
            Config.resetInstanceForTest();
            assumeAscendantConfig();
        }
    }

    @Test
    public void autosaveBeforeDuelSkippedDuringCoopSession() throws Exception {
        assumeAscendantConfig();
        setCoopActiveHost();
        Assert.assertTrue(CoopSession.get().isActive());
        File auto = new File(WorldSave.getSaveFile(WorldSave.AUTO_SAVE_SLOT));
        if (auto.isFile()) {
            Assert.assertTrue(auto.delete());
        }
        InteriorMapSave.maybeAutosaveBeforeDuel();
        Assert.assertFalse(auto.isFile(),
                "Autosave must not run during an active co-op session");
    }

    /**
     * Co-op {@code .chr} uses {@link AdventurePlayer#save()} / {@code load()} only —
     * interior keys must not appear, and load must not leave restore pending.
     */
    @Test
    public void coopChrRoundTripHasNoInteriorKeysAndLeavesNothingPending() throws Exception {
        assumeAscendantConfig();
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        String originalName = player.getName();
        Object originalMode = getPlayerField(player, "adventureMode");
        try {
            // Headless default player may lack adventureMode; seed fields savePlayer needs.
            setPlayerField(player, "name", COOP_CHR_NAME);
            if (originalMode == null) {
                setPlayerField(player, "adventureMode", AdventureModes.Chaos);
            }

            CoopCharacterStore.savePlayer(player);
            SaveFileData raw = readRawChr(COOP_CHR_NAME);
            Assert.assertNotNull(raw);
            Assert.assertFalse(raw.containsKey(InteriorMapSave.KEY_POI_ID),
                    "co-op .chr must not carry WorldSave interior keys");

            // Even a hand-crafted .chr with interior keys must not set pending via loadPlayer.
            raw.store(InteriorMapSave.KEY_POI_ID, "should-not-pend");
            raw.store(InteriorMapSave.KEY_MAP_PATH, "../common/maps/map/cave/cave_bear.tmx");
            writeRawChr(COOP_CHR_NAME, raw);
            InteriorMapSave.clearPending();
            Assert.assertTrue(CoopCharacterStore.loadPlayer(player, COOP_CHR_NAME));
            Assert.assertFalse(InteriorMapSave.hasPending(),
                    "CoopCharacterStore.loadPlayer must not set interior restore pending");
        } finally {
            try {
                setPlayerField(player, "name", originalName);
                setPlayerField(player, "adventureMode", originalMode);
            } catch (Exception ignored) {
            }
        }
    }

    @Test(groups = "gl", timeOut = 300_000)
    public void saveLoadInsideDungeonKeepsPositionAndDeletedEnemy() {
        AdventureGlTestSupport.runOnGl(() -> {
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            MapStage map = MapStage.getInstance();
            Assert.assertTrue(map.isInMap());
            Assert.assertFalse(map.getEnemies().isEmpty(), "Test dungeon must have enemies");

            EnemySprite victim = map.getEnemies().get(0);
            int victimId = victim.getId();
            map.getChanges().deleteObject(victimId);
            victim.remove();
            map.getEnemies().remove(victim);
            map.setMustClearOnExit(true);

            PlayerSprite player = map.getPlayerSprite();
            float savedX = player.getX() + 8f;
            float savedY = player.getY() + 4f;
            if (!map.tryRestorePlayerPosition(savedX, savedY)) {
                savedX = player.getX();
                savedY = player.getY();
            }

            Assert.assertTrue(WorldSave.getCurrentSave().save("sv1-dungeon", SLOT));
            MapStage.getInstance().clearIsInMap();
            MapStage.getInstance().setMustClearOnExit(false);
            InteriorMapSave.clearPending();

            Assert.assertTrue(WorldSave.load(SLOT));
            Assert.assertTrue(InteriorMapSave.hasPending(), "Interior fields must be pending after WorldSave.load");
            Scene scene = InteriorMapSave.sceneAfterLoad();
            Assert.assertTrue(scene instanceof TileMapScene);
            scene.enter();
            MapStage restored = MapStage.getInstance();
            Assert.assertTrue(restored.isInMap());
            Assert.assertEquals(TileMapScene.instance().rootPoint.getID(), dungeon.getID());
            Assert.assertEquals(restored.getPlayerSprite().getX(), savedX, 0.01f);
            Assert.assertEquals(restored.getPlayerSprite().getY(), savedY, 0.01f);
            Assert.assertTrue(restored.isMustClearOnExit(), "mustClearOnExit must persist");
            Assert.assertTrue(restored.getChanges().isObjectDeleted(victimId),
                    "Killed enemy must stay deleted via PointOfInterestChanges");
            Assert.assertNull(restored.getEnemyByID(victimId),
                    "Deleted enemy must not respawn on the map");
        });
    }

    @Test(groups = "gl", timeOut = 300_000)
    public void blockedSavedPositionFallsBackToEntrance() {
        AdventureGlTestSupport.runOnGl(() -> {
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            MapStage map = MapStage.getInstance();
            PlayerSprite player = map.getPlayerSprite();
            float entranceX = player.getX();
            float entranceY = player.getY();

            float blockedX = entranceX + 64f;
            float blockedY = entranceY + 64f;
            map.collisionRect.add(new Rectangle(blockedX - 2f, blockedY - 2f, 40f, 40f));

            Assert.assertFalse(map.tryRestorePlayerPosition(blockedX, blockedY),
                    "Blocked position must be rejected");
            Assert.assertEquals(player.getX(), entranceX, 0.01f);
            Assert.assertEquals(player.getY(), entranceY, 0.01f);
        });
    }

    @Test(groups = "gl", timeOut = 300_000)
    public void missingTmxFallsBackToRootMapEntrance() {
        AdventureGlTestSupport.runOnGl(() -> {
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            Assert.assertTrue(MapStage.getInstance().isInMap());

            SaveFileData fake = new SaveFileData();
            fake.store(InteriorMapSave.KEY_POI_ID, dungeon.getID());
            fake.store(InteriorMapSave.KEY_MAP_PATH, "../common/maps/map/does_not_exist_sv1.tmx");
            fake.store(InteriorMapSave.KEY_ENTRANCE_ID, 0);
            fake.store(InteriorMapSave.KEY_POS_X, 9999f);
            fake.store(InteriorMapSave.KEY_POS_Y, 9999f);
            InteriorMapSave.readPending(fake);

            Assert.assertTrue(InteriorMapSave.prepareRestoredMap(),
                    "Missing TMX should fall back to the POI root map");
            Assert.assertFalse(InteriorMapSave.hasPending(), "pending must be cleared after restore attempt");
            Assert.assertTrue(MapStage.getInstance().isInMap());
            Assert.assertEquals(TileMapScene.instance().rootPoint.getID(), dungeon.getID());
            String loaded = MapStage.getInstance().getLoadedMapPath();
            Assert.assertEquals(loaded, dungeon.getData().map,
                    "Fallback must load the POI root map, not the missing path");
        });
    }

    @Test(groups = "gl", timeOut = 300_000)
    public void overworldFallbackResetsWorldSeedLikeNormalOverworld() {
        AdventureGlTestSupport.runOnGl(() -> {
            assumeAscendantConfig();
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            Assert.assertTrue(MapStage.getInstance().isInMap());
            // load() applied the POI seed offset; capture a draw from that sequence for contrast.
            forge.adventure.world.World world = WorldSave.getCurrentSave().getWorld();
            long poiOffsetDraw = world.getRandom().nextLong();

            String originalMap = dungeon.getData().map;
            dungeon.getData().map = "../common/maps/map/does_not_exist_sv1_root.tmx";
            try {
                SaveFileData fake = new SaveFileData();
                fake.store(InteriorMapSave.KEY_POI_ID, dungeon.getID());
                fake.store(InteriorMapSave.KEY_MAP_PATH, "../common/maps/map/does_not_exist_sv1.tmx");
                fake.store(InteriorMapSave.KEY_ENTRANCE_ID, 0);
                fake.store(InteriorMapSave.KEY_POS_X, 1f);
                fake.store(InteriorMapSave.KEY_POS_Y, 1f);
                InteriorMapSave.readPending(fake);

                Assert.assertFalse(InteriorMapSave.prepareRestoredMap(),
                        "Both nested and root TMX missing → overworld fallback");
                Assert.assertFalse(MapStage.getInstance().isInMap());

                long afterFallback = world.getRandom().nextLong();
                world.setSeed(0);
                long expectedOverworld = world.getRandom().nextLong();
                Assert.assertEquals(afterFallback, expectedOverworld,
                        "Overworld fallback must reset RNG like setSeed(0) / generateNew");
                Assert.assertNotEquals(poiOffsetDraw, afterFallback,
                        "Fallback RNG must not stay on the POI-offset sequence");
            } finally {
                dungeon.getData().map = originalMap;
            }
        });
    }

    @Test(groups = "gl", timeOut = 300_000)
    public void worldMapOnlySaveMessage_stockInMap_ascendantInMap() {
        AdventureGlTestSupport.runOnGl(() -> {
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            Assert.assertTrue(MapStage.getInstance().isInMap());

            assumeAscendantConfig();
            Assert.assertFalse(InteriorMapSave.shouldShowWorldMapOnlySaveMessage(),
                    "Ascendant in a map must not show the world-map-only save message");

            ConfigData stock = new ConfigData();
            stock.ascendantRules = false;
            Config.installConfigDataForTest(stock);
            try {
                Assert.assertTrue(MapStage.getInstance().isInMap());
                Assert.assertTrue(InteriorMapSave.shouldShowWorldMapOnlySaveMessage(),
                        "Stock mode in a map must still show the world-map-only save message");
            } finally {
                Config.resetInstanceForTest();
                assumeAscendantConfig();
            }
        });
    }

    @Test(groups = "gl", timeOut = 300_000)
    public void f8QuickloadBlockedWhileDialogOpen() {
        AdventureGlTestSupport.runOnGl(() -> {
            assumeAscendantConfig();
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            Assert.assertTrue(MapStage.getInstance().isInMap());

            try {
                setPlayerField(Current.player(), "gold", 100);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Assert.assertTrue(WorldSave.getCurrentSave().quickSave());
            try {
                setPlayerField(Current.player(), "gold", 50);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Assert.assertEquals(Current.player().getGold(), 50);

            MapStage map = MapStage.getInstance();
            try {
                Field dialogFlag = forge.adventure.stage.GameStage.class
                        .getDeclaredField("dialogOnlyInput");
                dialogFlag.setAccessible(true);
                dialogFlag.setBoolean(map, true);
                Assert.assertTrue(map.isDialogOnlyInput());

                Assert.assertTrue(map.keyDown(com.badlogic.gdx.Input.Keys.F8));
                Assert.assertEquals(Current.player().getGold(), 50,
                        "F8 must not quickload while a dialog is open");

                // Clear dialog and press F8. quickLoad runs before switchScene; scene swap
                // may NPE under GL (FSkin) — gold restore is the behavior under test.
                dialogFlag.setBoolean(map, false);
                Assert.assertFalse(map.isDialogOnlyInput());
                try {
                    map.keyDown(com.badlogic.gdx.Input.Keys.F8);
                } catch (Exception switchSceneEx) {
                    Throwable root = switchSceneEx;
                    while (root.getCause() != null && root.getCause() != root) {
                        root = root.getCause();
                    }
                    if (!(root instanceof NullPointerException)
                            || root.getMessage() == null
                            || !root.getMessage().contains("FSkin")) {
                        throw switchSceneEx;
                    }
                }
                Assert.assertEquals(Current.player().getGold(), 100,
                        "F8 quickload must restore the quicksave when no dialog is open");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    Field dialogFlag = forge.adventure.stage.GameStage.class
                            .getDeclaredField("dialogOnlyInput");
                    dialogFlag.setAccessible(true);
                    dialogFlag.setBoolean(map, false);
                } catch (Exception ignored) {
                }
                deleteSlot(WorldSave.QUICK_SAVE_SLOT);
            }
        });
    }

    @Test(groups = "gl", timeOut = 300_000)
    public void autosaveWrittenAtDuelStartNotDuringCoop() {
        AdventureGlTestSupport.runOnGl(() -> {
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            Assert.assertFalse(MapStage.getInstance().getEnemies().isEmpty());

            File auto = new File(WorldSave.getSaveFile(WorldSave.AUTO_SAVE_SLOT));
            if (auto.isFile()) {
                Assert.assertTrue(auto.delete());
            }

            PlayerSprite player = MapStage.getInstance().getPlayerSprite();
            EnemySprite enemy = MapStage.getInstance().getEnemies().get(0);
            DuelScene.instance().initDuels(player, enemy);

            Assert.assertTrue(auto.isFile(), "SV1 must write the rotating autosave at duel start");
            long written = auto.lastModified();

            try {
                setCoopActiveHost();
                Thread.sleep(20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            InteriorMapSave.maybeAutosaveBeforeDuel();
            Assert.assertEquals(auto.lastModified(), written,
                    "Autosave must not rewrite during an active co-op session");
        });
    }

    /**
     * Arena: fee is taken before rounds; autosave once before the fee, never per round via initDuels.
     */
    @Test(groups = "gl", timeOut = 300_000)
    public void arenaAutosavesBeforeFeeNotPerRound() {
        AdventureGlTestSupport.runOnGl(() -> {
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            Assert.assertFalse(MapStage.getInstance().getEnemies().isEmpty());

            File auto = new File(WorldSave.getSaveFile(WorldSave.AUTO_SAVE_SLOT));
            if (auto.isFile()) {
                Assert.assertTrue(auto.delete());
            }

            PlayerSprite player = MapStage.getInstance().getPlayerSprite();
            EnemySprite enemy = MapStage.getInstance().getEnemies().get(0);

            // Per-round path: isArena=true must not write the auto slot.
            DuelScene.instance().initDuels(player, enemy, true, null);
            Assert.assertFalse(auto.isFile(), "Arena rounds must not autosave via initDuels");

            // startArena order: autosave, then take fee. Load must restore pre-fee gold.
            // Set gold via field — takeGold() plays CoinsDrop and needs a DeviceAdapter.
            int goldBefore = Math.max(Current.player().getGold(), 50);
            try {
                setPlayerField(Current.player(), "gold", goldBefore);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            InteriorMapSave.maybeAutosaveBeforeDuel();
            Assert.assertTrue(auto.isFile(), "Arena entry must autosave before the fee");
            try {
                setPlayerField(Current.player(), "gold", goldBefore - 5);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Assert.assertEquals(Current.player().getGold(), goldBefore - 5);

            InteriorMapSave.clearPending();
            Assert.assertTrue(WorldSave.load(WorldSave.AUTO_SAVE_SLOT));
            Assert.assertEquals(Current.player().getGold(), goldBefore,
                    "Loading the pre-fee autosave must restore gold");
        });
    }

    @Test(groups = "gl", timeOut = 300_000)
    public void loadInsideQuestDungeonDoesNotDoubleCompleteTravelStage() {
        AdventureGlTestSupport.runOnGl(() -> {
            PointOfInterest dungeon = generateWorldAndPickDungeon();

            AdventureQuestStage stage = new AdventureQuestStage();
            stage.id = 1;
            stage.objective = AdventureQuestController.ObjectiveTypes.Travel;
            stage.count3 = 1;
            stage.setTargetPOI(dungeon);
            stage.checkPrerequisites(Collections.emptyList());
            Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.ACTIVE);

            AdventureQuestData quest = new AdventureQuestData();
            quest.name = "SV1 Quest Safety";
            quest.stages = new AdventureQuestStage[]{stage};
            WorldSave.getCurrentSave().getPlayer().addQuest(quest, false);

            // Setup: complete Travel without showQuestDialogs so the quest remains on the save.
            // Restore path under test is sceneAfterLoad → enter (not this shortcut).
            TileMapScene.instance().load(dungeon);
            AdventureQuestController.instance().updateEnteredPOI(dungeon);
            Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.COMPLETE);

            Assert.assertTrue(WorldSave.getCurrentSave().save("sv1-quest", SLOT));
            MapStage.getInstance().clearIsInMap();
            InteriorMapSave.clearPending();

            Assert.assertTrue(WorldSave.load(SLOT));
            AdventureQuestData beforeEnter = findQuest("SV1 Quest Safety");
            Assert.assertNotNull(beforeEnter, "Quest must persist in the WorldSave");
            Assert.assertEquals(beforeEnter.stages[0].getStatus(),
                    AdventureQuestController.QuestStatus.COMPLETE);

            // Avoid Chaos/custom deck-award path (needs CardRenderer fonts under GL tests).
            Current.player().clearAnnounceFantasy();
            Current.player().clearAnnounceCustom();
            Scene scene = InteriorMapSave.sceneAfterLoad();
            Assert.assertTrue(scene instanceof TileMapScene);
            scene.enter();

            // enter() → showQuestDialogs may retire a finished quest; never reset to ACTIVE.
            AdventureQuestData afterEnter = findQuest("SV1 Quest Safety");
            if (afterEnter != null) {
                Assert.assertEquals(afterEnter.stages[0].getStatus(),
                        AdventureQuestController.QuestStatus.COMPLETE,
                        "Done Travel stage must not reset on interior enter");
            }
        });
    }

    @Test(groups = "gl", timeOut = 300_000)
    public void loadInsideQuestDungeonCompletesPendingTravelStageOnce() {
        AdventureGlTestSupport.runOnGl(() -> {
            PointOfInterest dungeon = generateWorldAndPickDungeon();

            AdventureQuestStage stage = new AdventureQuestStage();
            stage.id = 2;
            stage.objective = AdventureQuestController.ObjectiveTypes.Travel;
            stage.count3 = 1;
            stage.setTargetPOI(dungeon);
            stage.checkPrerequisites(Collections.emptyList());
            Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.ACTIVE);

            AdventureQuestData quest = new AdventureQuestData();
            quest.name = "SV1 Pending Travel";
            quest.stages = new AdventureQuestStage[]{stage};
            WorldSave.getCurrentSave().getPlayer().addQuest(quest, false);

            // Save inside without firing ENTERPOI yet.
            TileMapScene.instance().load(dungeon);
            Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.ACTIVE);
            Assert.assertTrue(WorldSave.getCurrentSave().save("sv1-pending-quest", SLOT));
            MapStage.getInstance().clearIsInMap();
            InteriorMapSave.clearPending();

            Assert.assertTrue(WorldSave.load(SLOT));
            AdventureQuestData beforeEnter = findQuest("SV1 Pending Travel");
            Assert.assertNotNull(beforeEnter);
            Assert.assertEquals(beforeEnter.stages[0].getStatus(),
                    AdventureQuestController.QuestStatus.ACTIVE,
                    "Pending Travel must still be ACTIVE after load, before enter");

            Current.player().clearAnnounceFantasy();
            Current.player().clearAnnounceCustom();
            Scene scene = InteriorMapSave.sceneAfterLoad();
            Assert.assertTrue(scene instanceof TileMapScene);
            scene.enter();

            // ENTERPOI via enter() completes the stage; showQuestDialogs may then retire it.
            AdventureQuestData afterEnter = findQuest("SV1 Pending Travel");
            if (afterEnter != null) {
                Assert.assertEquals(afterEnter.stages[0].getStatus(),
                        AdventureQuestController.QuestStatus.COMPLETE,
                        "Pending reach stage must complete on TileMapScene.enter after restore");
            } else {
                // Retired as finished — still proves ENTERPOI ran once to completion.
                Assert.assertTrue(beforeEnter.stages[0].getStatus()
                                == AdventureQuestController.QuestStatus.COMPLETE
                                || beforeEnter.completed,
                        "Pending Travel must complete via enter before quest retirement");
            }
        });
    }

    /**
     * forge-gui-mobile test classpath has controllers-core only (desktop manager lives in
     * forge-gui-mobile-dev). Prefer the stub so TileMapScene/GameHUD can construct under GL.
     */
    private static void preferStubControllers() {
        Controllers.preferredManager = "com.badlogic.gdx.controllers.ControllerManagerStub";
        try {
            Field listener = Class.forName("forge.adventure.scene.Scene")
                    .getDeclaredField("listener");
            listener.setAccessible(true);
            listener.set(null, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Build a Standard-mode world (avoids Chaos announceFantasy on TileMapScene.enter)
     * and return the first dungeon/cave whose TMX actually spawns enemies.
     */
    private static PointOfInterest generateWorldAndPickDungeon() {
        preferStubControllers();
        PointOfInterestData.clearRuntimeCacheForTests();
        forge.adventure.data.DifficultyData diff =
                Config.instance().getConfigData().difficulties[0];
        WorldSave.generateNewWorld("Sv1Hero", false, 0, 0,
                ColorSet.W, diff,
                AdventureModes.Standard, 0, null, WORLD_SEED);
        // New worlds can still flag announce bits; clear so enter() hits initializeDialogs.
        WorldSave.getCurrentSave().getPlayer().clearAnnounceFantasy();
        WorldSave.getCurrentSave().getPlayer().clearAnnounceCustom();

        WorldSave save = WorldSave.getCurrentSave();
        PointOfInterest fallback = null;
        for (PointOfInterest poi : save.getWorld().getAllPointOfInterest()) {
            if (poi == null || poi.getData() == null) {
                continue;
            }
            String type = poi.getData().type;
            if (!"dungeon".equals(type) && !"cave".equals(type)) {
                continue;
            }
            String map = poi.getData().map;
            if (map != null && map.contains("cave/")) {
                TileMapScene.instance().load(poi);
                if (!MapStage.getInstance().getEnemies().isEmpty()) {
                    MapStage.getInstance().clearIsInMap();
                    return poi;
                }
                MapStage.getInstance().clearIsInMap();
            }
            if (fallback == null) {
                fallback = poi;
            }
        }
        if (fallback != null) {
            TileMapScene.instance().load(fallback);
            if (!MapStage.getInstance().getEnemies().isEmpty()) {
                MapStage.getInstance().clearIsInMap();
                return fallback;
            }
            MapStage.getInstance().clearIsInMap();
        }
        Assert.fail("Generated world must include a dungeon/cave POI with enemies");
        return null;
    }

    private static AdventureQuestData findQuest(String name) {
        List<AdventureQuestData> quests = WorldSave.getCurrentSave().getPlayer().getQuests();
        for (AdventureQuestData q : quests) {
            if (name.equals(q.name)) {
                return q;
            }
        }
        return null;
    }

    private static void assumeAscendantConfig() {
        if (!Config.ascendant()) {
            ConfigData cfg = new ConfigData();
            cfg.ascendantRules = true;
            cfg.sv1SaveAnywhere = true;
            cfg.sv1AutosaveBeforeFight = true;
            Config.installConfigDataForTest(cfg);
        }
    }

    private static void setCoopActiveHost() throws Exception {
        CoopSession session = CoopSession.get();
        Field role = CoopSession.class.getDeclaredField("role");
        role.setAccessible(true);
        role.set(session, CoopSessionRole.HOST);
        Field state = CoopSession.class.getDeclaredField("state");
        state.setAccessible(true);
        state.set(session, CoopSession.State.READY);
        Assert.assertTrue(session.isActive());
    }

    private static void resetCoopSession() {
        try {
            CoopSession session = CoopSession.get();
            Field role = CoopSession.class.getDeclaredField("role");
            role.setAccessible(true);
            role.set(session, CoopSessionRole.NONE);
            Field state = CoopSession.class.getDeclaredField("state");
            state.setAccessible(true);
            state.set(session, CoopSession.State.IDLE);
        } catch (Exception ignored) {
        }
    }

    private static Object getPlayerField(AdventurePlayer player, String field) throws Exception {
        Field f = AdventurePlayer.class.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(player);
    }

    private static void setPlayerField(AdventurePlayer player, String field, Object value)
            throws Exception {
        Field f = AdventurePlayer.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(player, value);
    }

    private static SaveFileData readRawChr(String name) throws Exception {
        File file = CoopCharacterStore.characterFile(name);
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InflaterInputStream inf = new InflaterInputStream(fis);
             ObjectInputStream ois = new ObjectInputStream(inf)) {
            return (SaveFileData) ois.readObject();
        }
    }

    private static void writeRawChr(String name, SaveFileData data) throws Exception {
        File file = CoopCharacterStore.characterFile(name);
        File parent = file.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file);
             DeflaterOutputStream def = new DeflaterOutputStream(fos);
             ObjectOutputStream oos = new ObjectOutputStream(def)) {
            oos.writeObject(data);
        }
    }

    private static void deleteSlot(int slot) {
        try {
            Path p = Path.of(WorldSave.getSaveFile(slot));
            Files.deleteIfExists(p);
            Files.deleteIfExists(Path.of(p.toString().replace(".sav", ".old")));
        } catch (Exception ignored) {
        }
    }
}
