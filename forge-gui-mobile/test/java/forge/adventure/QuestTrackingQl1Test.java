package forge.adventure;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import forge.adventure.data.AdventureQuestData;
import forge.adventure.data.AdventureQuestStage;
import forge.adventure.data.BiomeData;
import forge.adventure.data.ConfigData;
import forge.adventure.data.PointOfInterestData;
import forge.adventure.data.WorldData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.util.AdventureQuestController;
import forge.adventure.util.Config;
import forge.adventure.util.QuestNavigationTarget;
import forge.adventure.util.SaveFileData;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * QL1: Track button / arrow follows the tracked quest; any-side-quest stages use the nearest
 * discovered QuestSource/Sidequest (or "no known location", never town).
 *
 * <p>Exercises the real {@link AdventureQuestController} and player save/load.
 * Uses Surefire {@code forge.test.userDir}; never writes the real user dir.
 */
public class QuestTrackingQl1Test {

    private static Path realUserDir;
    private static Map<String, AdventureTestUserDir.FileStamp> realUserDirSnapshot;
    private static ConfigData classAscendantConfig;
    private static String initError;

    private ConfigData ascendantConfig;
    private List<AdventureQuestData> questSnapshot;

    @BeforeClass
    public void bootstrapConfig() {
        try {
            realUserDir = AdventureTestUserDir.defaultRealUserDir();
            realUserDirSnapshot = AdventureTestUserDir.snapshot(realUserDir);
            AdventureTestUserDir.requireIsolatedUserDir();

            Path forgeGuiDir = resolveForgeGuiDir();
            Path cfgPath = forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/config.json");
            Assert.assertTrue(Files.isRegularFile(cfgPath), "Ascendant config missing: " + cfgPath);
            classAscendantConfig = new Json().fromJson(ConfigData.class, new FileHandle(cfgPath.toFile()));
            Assert.assertNotNull(classAscendantConfig);
            Assert.assertTrue(classAscendantConfig.ascendantRules);
            Assert.assertTrue(classAscendantConfig.ql1QuestTracking,
                    "Ascendant config.json must enable ql1QuestTracking");
            Assert.assertTrue(classAscendantConfig.ql1DiscoveredSideQuestTargetsOnly);
            Assert.assertEquals(classAscendantConfig.ql1NoKnownLocationMessage, "no known location");

            Config.resetInstanceForTest();
            Config.instance();
            Config.installConfigDataForTest(classAscendantConfig);
            Assert.assertTrue(Config.ql1QuestTracking());
        } catch (Throwable t) {
            initError = t.getClass().getSimpleName() + ": " + t.getMessage();
            t.printStackTrace();
        }
    }

    @AfterClass(alwaysRun = true)
    public void restoreAndAssertRealUserDirUntouched() {
        try {
            Config.resetInstanceForTest();
        } finally {
            if (realUserDirSnapshot != null) {
                AdventureTestUserDir.assertUnchanged(realUserDir, realUserDirSnapshot,
                        "QuestTrackingQl1Test");
            }
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        Assert.assertNull(initError, "QL1 BeforeClass bootstrap failed: " + initError);
        AdventureTestUserDir.requireIsolatedUserDir();
        AdventureTestUserDir.assertConstantsUse(
                AdventureTestUserDir.configuredTestUserDir());

        ascendantConfig = classAscendantConfig;
        Config.resetInstanceForTest();
        Config.instance();
        Config.installConfigDataForTest(ascendantConfig);
        Assert.assertTrue(Config.ql1QuestTracking());

        PointOfInterestData.clearRuntimeCacheForTests();
        AdventureQuestController.clear();

        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        questSnapshot = new ArrayList<>(player.getQuests());
        player.getQuests().clear();

        installWorldWithPois();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
            player.getQuests().clear();
            if (questSnapshot != null) {
                player.getQuests().addAll(questSnapshot);
            }
        } catch (Throwable ignored) {
        }
        PointOfInterestData.clearRuntimeCacheForTests();
        AdventureQuestController.clear();
        if (classAscendantConfig != null) {
            Config.installConfigDataForTest(classAscendantConfig);
        }
    }

    @Test
    public void trackBWhileAActive_arrowTargetsB() {
        PointOfInterest poiA = findPoi("QuestTownNear");
        PointOfInterest poiB = findPoi("QuestTownFar");
        Assert.assertNotNull(poiA);
        Assert.assertNotNull(poiB);

        AdventureQuestData questA = travelQuest("Quest A", poiA);
        AdventureQuestData questB = travelQuest("Quest B", poiB);
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getQuests().add(questA);
        player.getQuests().add(questB);
        AdventureQuestController.trackQuest(questA);
        Assert.assertTrue(questA.isTracked);
        Assert.assertFalse(questB.isTracked);

        AdventureQuestController.trackQuest(questB);
        Assert.assertFalse(questA.isTracked, "only one quest tracked at a time");
        Assert.assertTrue(questB.isTracked);
        Assert.assertSame(AdventureQuestController.getTrackedQuest(), questB);

        Vector2 from = new Vector2(0, 0);
        QuestNavigationTarget nav = AdventureQuestController.resolveTrackedNavigation(from);
        Assert.assertTrue(nav.hasPoi());
        Assert.assertEquals(nav.poi.getID(), poiB.getID(), "arrow must target tracked quest B");
        Assert.assertFalse(nav.noKnownLocation);
    }

    @Test
    public void saveLoadKeepsTrackedQuest() {
        PointOfInterest poiA = findPoi("QuestTownNear");
        PointOfInterest poiB = findPoi("QuestTownFar");
        AdventureQuestData questA = travelQuest("Save A", poiA);
        AdventureQuestData questB = travelQuest("Save B", poiB);
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getQuests().add(questA);
        player.getQuests().add(questB);
        AdventureQuestController.trackQuest(questB);

        SaveFileData saved = player.save();
        player.getQuests().clear();
        player.load(saved);

        AdventureQuestData tracked = AdventureQuestController.getTrackedQuest();
        Assert.assertNotNull(tracked, "tracking must survive save/load");
        Assert.assertEquals(tracked.getName(), "Save B");
        QuestNavigationTarget nav = tracked.resolveNavigationTarget(new Vector2(0, 0));
        Assert.assertTrue(nav.hasPoi());
        Assert.assertEquals(nav.poi.getID(), poiB.getID());
    }

    @Test
    public void oldSaveWithoutTracking_tracksFirstActive() {
        PointOfInterest poiA = findPoi("QuestTownNear");
        PointOfInterest poiB = findPoi("QuestTownFar");
        AdventureQuestData questA = travelQuest("First", poiA);
        AdventureQuestData questB = travelQuest("Second", poiB);
        questA.isTracked = false;
        questB.isTracked = false;
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getQuests().add(questA);
        player.getQuests().add(questB);

        AdventureQuestController.ensureTrackedQuest();
        Assert.assertTrue(questA.isTracked);
        Assert.assertFalse(questB.isTracked);
    }

    @Test
    public void finishingTrackedQuest_movesTrackingToNext() {
        PointOfInterest poiA = findPoi("QuestTownNear");
        PointOfInterest poiB = findPoi("QuestTownFar");
        AdventureQuestData questA = travelQuest("Finish A", poiA);
        AdventureQuestData questB = travelQuest("Finish B", poiB);
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getQuests().add(questA);
        player.getQuests().add(questB);
        AdventureQuestController.trackQuest(questA);

        player.removeQuest(questA);
        Assert.assertFalse(player.getQuests().contains(questA));
        Assert.assertTrue(questB.isTracked, "finishing tracked quest must track the next active one");
        Assert.assertSame(AdventureQuestController.getTrackedQuest(), questB);
    }

    @Test
    public void newQuestDoesNotStealTracking() {
        PointOfInterest poiA = findPoi("QuestTownNear");
        PointOfInterest poiB = findPoi("QuestTownFar");
        AdventureQuestData questA = travelQuest("Keep A", poiA);
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getQuests().add(questA);
        AdventureQuestController.trackQuest(questA);

        AdventureQuestData questB = travelQuest("New B", poiB);
        questB.autoTrack = true;
        // Use addQuest path (QL1: autoTrack must not steal).
        player.addQuest(questB, true);
        Assert.assertTrue(questA.isTracked, "new quest must not steal tracking");
        Assert.assertFalse(questB.isTracked);
    }

    @Test
    public void anySideQuest_picksNearestDiscoveredTarget() {
        PointOfInterest near = findPoi("QuestTownNear");
        PointOfInterest far = findPoi("QuestTownFar");
        PointOfInterest haven = findPoi("HavenTown");
        Assert.assertNotNull(near);
        Assert.assertNotNull(far);
        Assert.assertNotNull(haven);

        // Discover near and far boards; Havenbrook-like town stays undiscovered as a source.
        WorldSave.getCurrentSave().getPointOfInterestChanges(near.getID()).visit();
        WorldSave.getCurrentSave().getPointOfInterestChanges(far.getID()).visit();

        AdventureQuestData side = anySideQuest("Do Side Quests");
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getQuests().add(side);
        AdventureQuestController.trackQuest(side);

        QuestNavigationTarget nav = side.resolveNavigationTarget(new Vector2(0, 0));
        Assert.assertTrue(nav.hasPoi(), "should point at a discovered QuestSource");
        Assert.assertEquals(nav.poi.getID(), near.getID(), "nearest discovered QuestSource");
        Assert.assertNotEquals(nav.poi.getID(), haven.getID(), "must not point at non-QuestSource town");
        Assert.assertFalse(nav.noKnownLocation);
        Assert.assertTrue(nav.hudLine.contains(near.getDisplayName()));
    }

    @Test
    public void anySideQuest_noneDiscovered_noKnownLocationNotTown() {
        PointOfInterest near = findPoi("QuestTownNear");
        PointOfInterest haven = findPoi("HavenTown");
        // Leave every QuestSource unvisited.
        Assert.assertFalse(WorldSave.getCurrentSave().getPointOfInterestChanges(near.getID()).isVisited());

        AdventureQuestData side = anySideQuest("Do Side Quests");
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        player.getQuests().add(side);
        AdventureQuestController.trackQuest(side);

        QuestNavigationTarget nav = AdventureQuestController.resolveTrackedNavigation(new Vector2(0, 0));
        Assert.assertFalse(nav.hasPoi(), "no discovered board → no POI target");
        Assert.assertTrue(nav.noKnownLocation);
        Assert.assertTrue(nav.hudLine.toLowerCase().contains("no known location"),
                "HUD must say no known location, got: " + nav.hudLine);
        // Root-cause regression: must not resolve to the nearest town (Havenbrook stand-in).
        Assert.assertTrue(nav.poi == null || !nav.poi.getID().equals(haven.getID()),
                "must not point at town when nothing is discovered");
    }

    @Test
    public void emptyTagFallback_doesNotPointAtAllPoisUnderQl1() {
        // Root cause: setTargetPOI used to replace an empty tag match with ALL POIs, so the
        // nearest town won. Under QL1 the list stays empty.
        AdventureQuestStage stage = new AdventureQuestStage();
        stage.name = "Broken tags";
        stage.objective = AdventureQuestController.ObjectiveTypes.Travel;
        stage.anyPOI = false;
        stage.POITags = new ArrayList<>(Collections.singletonList("TagThatMatchesNothing"));
        stage.initialize();
        stage.setTargetPOI(new java.util.Hashtable<>(), "Broken");
        stage.checkPrerequisites(Collections.emptyList());

        List<PointOfInterest> valid = stage.getValidPOIs();
        Assert.assertTrue(valid == null || valid.isEmpty(),
                "QL1 must not fall back to all POIs (that pointed at town)");
    }

    // ---- helpers ----

    private static void installWorldWithPois() {
        World world = WorldSave.getCurrentSave().getWorld();
        WorldData data = tinyWorld();
        world.installTestWorldGrid(data, 42L);

        PointOfInterestData haven = poiDef("HavenTown", "town",
                new String[]{"Town", "StarterTown", "BiomeColorless"});
        PointOfInterestData near = poiDef("QuestTownNear", "town",
                new String[]{"Town", "QuestSource", "Sidequest", "BiomeColorless"});
        PointOfInterestData far = poiDef("QuestTownFar", "town",
                new String[]{"Town", "QuestSource", "Sidequest", "BiomeColorless"});
        PointOfInterestData.registerRuntime(haven);
        PointOfInterestData.registerRuntime(near);
        PointOfInterestData.registerRuntime(far);

        world.addPointOfInterest(poiAt(haven, 32f, 32f));
        world.addPointOfInterest(poiAt(near, 128f, 32f));
        world.addPointOfInterest(poiAt(far, 400f, 32f));
    }

    private static PointOfInterest findPoi(String name) {
        for (PointOfInterest poi : WorldSave.getCurrentSave().getWorld().getAllPointOfInterest()) {
            if (poi != null && poi.getData() != null && name.equals(poi.getData().name)) {
                return poi;
            }
        }
        return null;
    }

    private static AdventureQuestData travelQuest(String name, PointOfInterest target) {
        AdventureQuestStage stage = new AdventureQuestStage();
        stage.id = 1;
        stage.name = "Go to " + target.getDisplayName();
        stage.objective = AdventureQuestController.ObjectiveTypes.Travel;
        stage.anyPOI = false;
        stage.initialize();
        stage.setTargetPOI(target);
        stage.checkPrerequisites(Collections.emptyList());
        Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.ACTIVE);

        AdventureQuestData q = new AdventureQuestData();
        q.name = name;
        q.description = name;
        q.stages = new AdventureQuestStage[]{stage};
        q.isTracked = false;
        return q;
    }

    private static AdventureQuestData anySideQuest(String name) {
        AdventureQuestStage stage = new AdventureQuestStage();
        stage.id = 1;
        stage.name = "Do Side Quests";
        stage.description = "Complete side quests";
        stage.objective = AdventureQuestController.ObjectiveTypes.CompleteQuest;
        stage.anyPOI = true;
        stage.count3 = 3;
        stage.POITags = new ArrayList<>(Collections.singletonList("QuestSource"));
        stage.initialize();
        stage.checkPrerequisites(Collections.emptyList());
        Assert.assertTrue(stage.isAnySideQuestObjective());
        Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.ACTIVE);

        AdventureQuestData q = new AdventureQuestData();
        q.name = name;
        q.description = name;
        q.storyQuest = true;
        q.stages = new AdventureQuestStage[]{stage};
        q.isTracked = false;
        return q;
    }

    private static WorldData tinyWorld() {
        WorldData d = new WorldData();
        d.width = 48;
        d.height = 48;
        d.tileSize = 16;
        d.playerStartPosX = 0.1f;
        d.playerStartPosY = 0.1f;
        d.noiseZoomBiome = 30;
        d.biomesNames = new String[0];
        List<BiomeData> biomes = new ArrayList<>();
        BiomeData b = new BiomeData();
        b.name = "colorless";
        b.width = 1f;
        b.height = 1f;
        b.color = "ffffff";
        b.pointsOfInterest = new String[]{"HavenTown", "QuestTownNear", "QuestTownFar"};
        biomes.add(b);
        d.replaceBiomes(biomes);
        return d;
    }

    private static PointOfInterestData poiDef(String name, String type, String[] tags) {
        PointOfInterestData d = new PointOfInterestData();
        d.name = name;
        d.displayName = name;
        d.type = type;
        d.count = 1;
        d.spriteAtlas = "../common/maps/tileset/buildings.atlas";
        d.sprite = "WasteTown";
        d.map = "../common/maps/map/towns/waste_town_generic.tmx";
        d.radiusFactor = 0.5f;
        d.active = true;
        d.questTags = tags;
        return d;
    }

    private static PointOfInterest poiAt(PointOfInterestData def, float x, float y) {
        PointOfInterest poi = new PointOfInterest();
        SaveFileData saved = new SaveFileData();
        saved.store("name", def.name);
        saved.store("position", new Vector2(x, y));
        saved.store("rectangle", new Rectangle(x, y, 16f, 16f));
        saved.store("spriteIndex", 0);
        saved.store("active", true);
        saved.store("displayName", def.displayName);
        poi.load(saved);
        return poi;
    }

    private static Path resolveForgeGuiDir() {
        for (String p : new String[]{"forge-gui", "../forge-gui"}) {
            Path path = Paths.get(p);
            if (Files.isDirectory(path.resolve("res/adventure"))) {
                return path.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("forge-gui not found");
    }
}
