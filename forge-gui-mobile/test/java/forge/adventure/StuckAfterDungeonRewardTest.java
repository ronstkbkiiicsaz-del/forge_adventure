package forge.adventure;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controllers;
import forge.Forge;
import forge.adventure.character.EnemySprite;
import forge.adventure.character.PlayerSprite;
import forge.adventure.data.AdventureQuestData;
import forge.adventure.data.AdventureQuestStage;
import forge.adventure.data.DialogData;
import forge.adventure.data.DifficultyData;
import forge.adventure.data.PointOfInterestData;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.scene.RewardScene;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.GameStage;
import forge.adventure.stage.MapStage;
import forge.adventure.stage.WorldStage;
import forge.adventure.util.AdventureModes;
import forge.adventure.util.AdventureQuestController;
import forge.adventure.util.Config;
import forge.adventure.util.Current;
import forge.adventure.util.InteriorMapSave;
import forge.adventure.world.WorldSave;
import forge.card.ColorSet;
import forge.interfaces.IDeviceAdapter;
import org.apache.commons.lang3.tuple.Pair;
import org.jupnp.UpnpServiceConfiguration;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.Queue;

import static forge.adventure.util.AdventureQuestController.QuestStatus.COMPLETE;

/**
 * Regression for Steve's play bug: after a dungeon fight the reward screen shows,
 * then the player cannot move (no animation, no mouse/keyboard) while enemies still
 * animate. Root cause was {@link AdventureQuestController#displayNextDialog} calling
 * {@code showDialog()} before {@code activate()}; a failed-condition quest dialog left
 * {@code dialogOnlyInput} stuck. {@code STUCK:} would report {@code dialogOnlyInput=true}.
 */
public class StuckAfterDungeonRewardTest {

    private static final long WORLD_SEED = 0x57AC4D01L;

    @BeforeMethod
    public void setUp() {
        AdventureTestUserDir.requireIsolatedUserDir();
        InteriorMapSave.clearPending();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        InteriorMapSave.clearPending();
        clearQuestDialogState();
        try {
            MapStage map = MapStage.instance;
            if (map != null) {
                setDialogOnlyInput(map, false);
                setAnimationTimeout(map, -1f);
                map.clearIsInMap();
            }
        } catch (Exception ignored) {
        }
        Forge.advFreezePlayerControls = false;
    }

    /**
     * Direct gate: a queued quest dialog whose conditions fail must not leave
     * {@code dialogOnlyInput} stuck (fail-before / pass-after for the soft-lock).
     */
    @Test(groups = "gl", timeOut = 300_000)
    public void failedConditionQuestDialog_doesNotStickDialogOnlyInput() {
        AdventureGlTestSupport.runOnGl(() -> {
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            MapStage map = MapStage.getInstance();
            Assert.assertTrue(map.isInMap());
            setDialogOnlyInput(map, false);
            setAnimationTimeout(map, -1f);
            Forge.advFreezePlayerControls = false;

            try {
                clearQuestDialogState();
                enqueueDialog(failingConditionDialog());
                AdventureQuestController.instance().displayNextDialog(map);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // displayNextDialog schedules activate after 0.25s on the GL Timer.
        waitMs(500);

        AdventureGlTestSupport.runOnGl(() -> {
            MapStage map = MapStage.getInstance();
            Assert.assertFalse(map.isDialogOnlyInput(),
                    "Failed-condition quest dialog must not leave dialogOnlyInput stuck "
                            + "(STUCK gate: dialogOnlyInput / mapDialogOnlyInput)");
            Assert.assertFalse(map.isPaused(),
                    "Spurious playerIdle pause from a failed dialog must not remain");
            Assert.assertFalse(Forge.advFreezePlayerControls);
            assertPlayerCanStep(map);
        });
    }

    /**
     * Dungeon win bookkeeping → RewardScene → showQuestDialogs (same order as
     * {@link MapStage#setWinner}'s death-pause callback) → dismiss reward → assert
     * movement/input. Uses the real reward + quest-dialog path; Forge.switchScene is
     * avoided when FSkin is not booted (GL tests), matching other adventure GL tests.
     */
    @Test(groups = "gl", timeOut = 300_000)
    public void dungeonWinRewardPath_restoresMovementAndInput() {
        AdventureGlTestSupport.runOnGl(() -> {
            preferStubControllers();
            PointOfInterest dungeon = generateWorldAndPickDungeon();
            TileMapScene.instance().load(dungeon);
            MapStage map = MapStage.getInstance();
            Assert.assertFalse(map.getEnemies().isEmpty());

            try {
                installFailedConditionEpilogueQuest();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            EnemySprite mob = map.getEnemies().get(0);
            setCurrentMob(map, mob);
            Forge.advFreezePlayerControls = true; // beginDuel leaves this set until afterGameEnd
            setDialogOnlyInput(map, false);
            setAnimationTimeout(map, -1f);
            ensureDeviceAdapter();

            // SV1 pre-duel autosave (same hook initDuels uses).
            InteriorMapSave.maybeAutosaveBeforeDuel();

            // afterGameEnd clears freeze before setWinner; then the win callback does:
            // getReward → updateQuestsWin → showQuestDialogs.
            Forge.advFreezePlayerControls = false;
            Current.player().getSkills().onDuelFinished(true, mob);
            try {
                Current.player().win(mob.getData() != null && mob.getData().boss);
            } catch (Exception ignored) {
                // Audio / haptic are optional under GL.
            }

            RewardScene.instance().loadRewards(mob.getRewards(), RewardScene.Type.Loot, null);
            // Scene stack: TileMap under Reward so quitScene → switchToLast returns to the map.
            setForgeSceneStack(TileMapScene.instance(), RewardScene.instance());
            try {
                RewardScene.instance().enter();
            } catch (Exception ignored) {
            }
            try {
                Field stageField = Class.forName("forge.adventure.scene.UIScene")
                        .getDeclaredField("stage");
                stageField.setAccessible(true);
                Object rewardStage = stageField.get(RewardScene.instance());
                if (rewardStage instanceof com.badlogic.gdx.InputProcessor) {
                    Gdx.input.setInputProcessor((com.badlogic.gdx.InputProcessor) rewardStage);
                }
            } catch (Exception ignored) {
            }

            // Same post-reward ordering as MapStage.setWinner's pause runnable.
            AdventureQuestController.instance().updateQuestsWin(mob,
                    new java.util.ArrayList<>(map.getEnemies()));
            AdventureQuestController.instance().showQuestDialogs(map);
            if (mob.defeatDialog == null) {
                mob.remove();
                map.getEnemies().remove(mob);
            }
            setCurrentMob(map, null);
        });

        // Quest dialog Timer (0.25s) fires while the reward screen is up — the buggy window.
        waitMs(500);

        AdventureGlTestSupport.runOnGl(() -> {
            Assert.assertTrue(Forge.getCurrentScene() instanceof RewardScene);
            // quitScene clears advFreeze and switchToLast → TileMapScene.enter (input restore).
            try {
                java.lang.reflect.Method done = RewardScene.class.getDeclaredMethod("done", boolean.class);
                done.setAccessible(true);
                done.invoke(RewardScene.instance(), true);
            } catch (Exception e) {
                // FSkin/FrameRate can still NPE inside switchToLast; finish the return by hand.
                Forge.advFreezePlayerControls = false;
                setForgeCurrentScene(TileMapScene.instance());
                try {
                    TileMapScene.instance().enter();
                } catch (Exception ignored) {
                    Gdx.input.setInputProcessor(TileMapScene.instance());
                }
            }
        });

        waitMs(200);

        AdventureGlTestSupport.runOnGl(() -> {
            MapStage map = MapStage.getInstance();
            Assert.assertTrue(map.isInMap());
            Assert.assertFalse(map.isDialogOnlyInput(),
                    "After reward, dialogOnlyInput must be clear (STUCK: dialogOnlyInput=true)");
            Assert.assertFalse(Forge.advFreezePlayerControls,
                    "After reward, advFreezePlayerControls must be clear");
            // Failed-condition dialogs must not call showDialog/playerIdle (1s pause).
            Assert.assertFalse(map.isPaused(),
                    "After reward, animationTimeout pause must not remain");
            Object processor = Gdx.input.getInputProcessor();
            Assert.assertNotNull(processor, "Input processor must be restored after reward");
            Assert.assertFalse(processor instanceof RewardScene,
                    "Input must leave RewardScene after Done");
            assertPlayerCanStep(map);

            // Overworld freezes when MapStage.dialogOnlyInput is stuck (WorldStage.onActing).
            Assert.assertFalse(MapStage.getInstance().isDialogOnlyInput());
            Assert.assertNotNull(WorldStage.getInstance());
        });
    }

    private static void assertPlayerCanStep(MapStage map) {
        PlayerSprite player = map.getPlayerSprite();
        float x0 = player.getX();
        float y0 = player.getY();
        player.getMovementDirection().set(1f, 0f);
        player.setMoveModifier(2f);
        player.act(0.2f);
        boolean moved = Math.abs(player.getX() - x0) > 0.01f || Math.abs(player.getY() - y0) > 0.01f;
        player.stop();
        Assert.assertTrue(moved, "Player sprite must be able to move after the reward path");
    }

    private static DialogData failingConditionDialog() {
        DialogData root = new DialogData();
        root.text = "Should never appear";
        DialogData.ConditionData needItem = new DialogData.ConditionData();
        needItem.item = "__stuck_test_missing_item__";
        root.condition = new DialogData.ConditionData[]{needItem};
        DialogData ok = new DialogData();
        ok.name = "OK";
        root.options = new DialogData[]{ok};
        return root;
    }

    private static void installFailedConditionEpilogueQuest() throws Exception {
        AdventureQuestData quest = new AdventureQuestData();
        quest.name = "StuckRewardEpilogue";
        quest.isTemplate = false;
        AdventureQuestStage stage = new AdventureQuestStage();
        stage.name = "KillThenEpilogue";
        Field status = AdventureQuestStage.class.getDeclaredField("status");
        status.setAccessible(true);
        status.set(stage, COMPLETE);
        stage.epilogueDisplayed = false;
        stage.epilogue = failingConditionDialog();
        quest.stages = new AdventureQuestStage[]{stage};
        Current.player().getQuests().clear();
        Current.player().getQuests().add(quest);
    }

    private static void enqueueDialog(DialogData data) throws Exception {
        Field q = AdventureQuestController.class.getDeclaredField("dialogQueue");
        q.setAccessible(true);
        @SuppressWarnings("unchecked")
        Queue<DialogData> queue = (Queue<DialogData>) q.get(AdventureQuestController.instance());
        queue.clear();
        queue.add(data);
        Field active = AdventureQuestController.class.getDeclaredField("activeDialog");
        active.setAccessible(true);
        active.set(AdventureQuestController.instance(), null);
    }

    private static void clearQuestDialogState() throws Exception {
        AdventureQuestController c = AdventureQuestController.instance();
        Field q = AdventureQuestController.class.getDeclaredField("dialogQueue");
        q.setAccessible(true);
        ((Queue<?>) q.get(c)).clear();
        Field active = AdventureQuestController.class.getDeclaredField("activeDialog");
        active.setAccessible(true);
        active.set(c, null);
    }

    private static void setCurrentMob(MapStage map, EnemySprite mob) {
        try {
            Field f = MapStage.class.getDeclaredField("currentMob");
            f.setAccessible(true);
            f.set(map, mob);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setDialogOnlyInput(GameStage stage, boolean value) {
        try {
            Field f = GameStage.class.getDeclaredField("dialogOnlyInput");
            f.setAccessible(true);
            f.setBoolean(stage, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setAnimationTimeout(GameStage stage, float value) {
        try {
            Field f = GameStage.class.getDeclaredField("animationTimeout");
            f.setAccessible(true);
            f.setFloat(stage, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setForgeCurrentScene(forge.adventure.scene.Scene scene) {
        try {
            Field f = Forge.class.getDeclaredField("currentScene");
            f.setAccessible(true);
            f.set(null, scene);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Put {@code under} on the last-scene stack and make {@code top} current. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setForgeSceneStack(forge.adventure.scene.Scene under,
                                           forge.adventure.scene.Scene top) {
        try {
            Field last = Forge.class.getDeclaredField("lastScene");
            last.setAccessible(true);
            com.badlogic.gdx.utils.Array stack = (com.badlogic.gdx.utils.Array) last.get(null);
            stack.clear();
            stack.add(under);
            setForgeCurrentScene(top);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** AdventurePlayer.win() / SoundSystem need a non-null adapter under GL tests. */
    private static void ensureDeviceAdapter() {
        if (Forge.getDeviceAdapter() != null) {
            return;
        }
        try {
            Field f = Forge.class.getDeclaredField("deviceAdapter");
            f.setAccessible(true);
            f.set(null, new IDeviceAdapter() {
                @Override public boolean isConnectedToInternet() { return false; }
                @Override public boolean isConnectedToWifi() { return false; }
                @Override public boolean isTablet() { return false; }
                @Override public String getDownloadsDir() { return System.getProperty("java.io.tmpdir"); }
                @Override public String getVersionString() { return "test"; }
                @Override public String getLatestChanges(String commitsAtom, Date buildDateOriginal, Date maxDate) { return ""; }
                @Override public String getReleaseTag(String releaseAtom) { return ""; }
                @Override public boolean openFile(String filename) { return false; }
                @Override public void setLandscapeMode(boolean landscapeMode) { }
                @Override public void preventSystemSleep(boolean preventSleep) { }
                @Override public void restart() { }
                @Override public void exit() { }
                @Override public void closeSplashScreen() { }
                @Override public void convertToJPEG(InputStream input, OutputStream output) { }
                @Override public void convertToPNG(InputStream input, OutputStream output) { }
                @Override public Pair<Integer, Integer> getRealScreenSize(boolean real) { return Pair.of(64, 64); }
                @Override public ArrayList<String> getGamepads() { return new ArrayList<>(); }
                @Override public UpnpServiceConfiguration getUpnpPlatformService() { return null; }
                @Override public boolean needFileAccess() { return false; }
                @Override public void requestFileAcces() { }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void waitMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

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

    private static PointOfInterest generateWorldAndPickDungeon() {
        preferStubControllers();
        PointOfInterestData.clearRuntimeCacheForTests();
        DifficultyData diff = Config.instance().getConfigData().difficulties[0];
        WorldSave.generateNewWorld("StuckHero", false, 0, 0,
                ColorSet.W, diff, AdventureModes.Standard, 0, null, WORLD_SEED);
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
}
