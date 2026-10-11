package forge.adventure;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.SnapshotArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TypingLabel;
import forge.Forge;
import forge.adventure.data.AdventureQuestData;
import forge.adventure.data.DifficultyData;
import forge.adventure.data.PointOfInterestData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.player.AdventureSeason;
import forge.adventure.player.StandardWindow;
import forge.adventure.scene.GameScene;
import forge.adventure.scene.InnScene;
import forge.adventure.scene.PlayerStatisticScene;
import forge.adventure.scene.Scene;
import forge.adventure.scene.SkillsScene;
import forge.adventure.scene.UIScene;
import forge.adventure.stage.GameHUD;
import forge.adventure.stage.MapStage;
import forge.adventure.stage.WorldStage;
import forge.adventure.util.AdventureModes;
import forge.adventure.util.AdventureQuestController;
import forge.adventure.util.Config;
import forge.adventure.util.Current;
import forge.adventure.world.WorldSave;
import forge.card.ColorSet;
import forge.deck.CardPool;
import forge.player.GamePlayerUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SN1 GL screenshots (opt-in {@code gl} group under xvfb). Boots a live Ascendant
 * world and captures GameHUD (overworld + season line), Inn Rest, Status season,
 * and the Skills shortlist picker in landscape and portrait.
 */
public class SeasonsSn1GlTest {

    private static final Path ARTIFACTS = Path.of("/opt/cursor/artifacts/screenshots");
    private static final long WORLD_SEED = 0x534E3147L; // "SN1G"
    /** Caption-only stubs were ~1.5–2 KB; real scene PNGs are far larger. */
    private static final long MIN_REAL_PNG_BYTES = 8_000L;

    @Test(groups = "gl", timeOut = 600_000)
    public void seasonUiScreenshotsLandscapeAndPortrait() throws Exception {
        AdventureGlTestSupport.runOnGl(() -> {
            Assert.assertTrue(Config.ascendant(), "GL support boots Ascendant");
            try {
                Files.createDirectories(ARTIFACTS);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
            clearCoopLeftovers();
            preferStubControllers();
            ensureStubDeviceAdapter();
            generateAscendantWorld();

            AdventurePlayer player = Current.player();
            Assert.assertNotNull(player);
            Assert.assertTrue(player.getSeason().isConfigured(), "SN1 configured after world gen");
            Assert.assertFalse(player.getSeason().hudLine(player.getStandardWindow()).isEmpty());

            AdventureQuestController.ensureTrackedQuest();
            if (AdventureQuestController.getTrackedQuest() == null) {
                seedTrackedQuest(player);
            }

            try {
                captureOrientation(true);
                captureOrientation(false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void captureOrientation(boolean landscape) throws Exception {
        applyOrientation(landscape);
        String suffix = landscape ? "landscape" : "portrait";

        GameScene game = GameScene.instance();
        game.enter();
        game.act(0.05f);
        GameHUD hud = GameHUD.getInstance();
        hud.updateSeasonHud();
        hud.updateTrackedQuestHud();
        assertSeasonHudClearOfLiveHud(hud, landscape);
        game.render();
        saveScreenshot("sn1_hud_season_" + suffix + ".png");
        Actor seasonHudActor = findByName(collectActors(hud.ui), "seasonHud");
        Assert.assertNotNull(seasonHudActor, "seasonHud missing");
        Assert.assertTrue(seasonHudActor.isVisible(), "seasonHud must be visible");
        String hudLine = Current.player().getSeason().hudLine(Current.player().getStandardWindow());
        Assert.assertTrue(hudLine.contains("Day"), "hudLine must include Day: " + hudLine);

        InnScene inn = InnScene.instance(game, "sn1-test-inn", null, -1);
        inn.enter();
        inn.act(0.05f);
        Actor rest = inn.getUI().findActor("rest");
        Assert.assertNotNull(rest, "Rest button must exist on Inn");
        Assert.assertTrue(rest.isVisible(), "Rest must be visible");
        Assert.assertTrue(rest instanceof TextraButton, "Rest must be a button");
        assertSelectableContains(inn, rest);
        inn.render();
        saveScreenshot("sn1_inn_rest_" + suffix + ".png");

        PlayerStatisticScene status = PlayerStatisticScene.instance(game);
        status.enter();
        status.act(0.05f);
        Actor seasonStatus = status.getUI().findActor("seasonStatus");
        Assert.assertNotNull(seasonStatus, "seasonStatus must exist on Status");
        Assert.assertTrue(seasonStatus.isVisible(), "seasonStatus must be visible");
        Assert.assertTrue(seasonStatus instanceof TypingLabel);
        String statusLine = Current.player().getSeason().statusLine(Current.player().getStandardWindow());
        Assert.assertTrue(statusLine.contains("Day") || statusLine.contains("season"),
                "statusLine must be meaningful: " + statusLine);
        status.render();
        saveScreenshot("sn1_status_season_" + suffix + ".png");

        StandardWindow window = Current.player().getStandardWindow();
        AdventureSeason season = Current.player().getSeason();
        if (!season.isChoicePending()) {
            Assert.assertTrue(season.endByMastery(window, new CardPool()),
                    "endByMastery must open a season pick");
        }
        Assert.assertTrue(season.isChoicePending());
        Assert.assertFalse(season.getShortlist().isEmpty(), "shortlist must be non-empty");

        SkillsScene skills = SkillsScene.instance(game);
        skills.enter();
        skills.act(0.05f);
        assertSelectableTextContains(skills, "Unlock");
        assertSelectableTextContains(skills, "Later");
        skills.render();
        saveScreenshot("sn1_shortlist_" + suffix + ".png");

        try {
            skills.leave();
            status.leave();
            inn.leave();
            game.leave();
        } catch (Throwable ignored) {
        }
    }

    private static void assertSeasonHudClearOfLiveHud(GameHUD hud, boolean landscape) {
        List<Actor> actors = collectActors(hud.ui);
        Actor season = findByName(actors, "seasonHud");
        Assert.assertNotNull(season, "seasonHud actor missing");
        Assert.assertTrue(season.isVisible(), "seasonHud must be visible for collision check");

        float[] seasonR = actorRect(season);
        Actor map = findByName(actors, "map");
        Assert.assertNotNull(map, "minimap missing");
        Assert.assertFalse(rectsOverlap(seasonR, actorRect(map)),
                "seasonHud overlaps minimap in " + (landscape ? "landscape" : "portrait")
                        + ": season=" + Arrays.toString(seasonR)
                        + " map=" + Arrays.toString(actorRect(map)));

        Actor quest = findTrackedQuestHud(hud);
        if (quest != null && quest.isVisible()) {
            Assert.assertFalse(rectsOverlap(seasonR, actorRect(quest)),
                    "seasonHud overlaps QL1 trackedQuestHud in "
                            + (landscape ? "landscape" : "portrait")
                            + ": season=" + Arrays.toString(seasonR)
                            + " quest=" + Arrays.toString(actorRect(quest)));
        }

        // MX1 "Collection exported" line is Status/Inventory (roadmap), not GameHUD.
        Assert.assertNull(findByName(actors, "exportHud"),
                "unexpected MX1 export HUD actor on GameHUD");
        Assert.assertNull(findByName(actors, "mx1Export"),
                "unexpected MX1 export HUD actor on GameHUD");
    }

    private static Actor findTrackedQuestHud(GameHUD hud) {
        try {
            Field f = GameHUD.class.getDeclaredField("trackedQuestHud");
            f.setAccessible(true);
            return (Actor) f.get(hud);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Actor> collectActors(Group root) {
        List<Actor> out = new ArrayList<>();
        collectActors(root, out);
        return out;
    }

    private static void collectActors(Group root, List<Actor> out) {
        SnapshotArray<Actor> children = root.getChildren();
        Actor[] items = children.begin();
        for (int i = 0, n = children.size; i < n; i++) {
            Actor a = items[i];
            out.add(a);
            if (a instanceof Group) {
                collectActors((Group) a, out);
            }
        }
        children.end();
    }

    private static Actor findByName(List<Actor> actors, String name) {
        for (Actor a : actors) {
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }

    private static float[] actorRect(Actor a) {
        return new float[] { a.getX(), a.getY(), a.getWidth(), a.getHeight() };
    }

    private static boolean rectsOverlap(float[] a, float[] b) {
        return a[0] < b[0] + b[2] && a[0] + a[2] > b[0]
                && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
    }

    private static void assertSelectableContains(UIScene scene, Actor actor) {
        boolean found = false;
        for (UIScene.Selectable sel : scene.getPossibleSelection()) {
            if (sel.actor == actor) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found, "controller selectables must include " + actor.getName());
    }

    private static void assertSelectableTextContains(UIScene scene, String labelFragment) {
        boolean found = false;
        for (UIScene.Selectable sel : scene.getPossibleSelection()) {
            Actor a = sel.actor;
            String text = "";
            if (a instanceof TextraButton) {
                text = ((TextraButton) a).getText();
            } else if (a.getName() != null) {
                text = a.getName();
            }
            if (text != null && text.contains(labelFragment)) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found, "controller selectables must include control with '"
                + labelFragment + "'");
    }

    private static void applyOrientation(boolean landscape) throws Exception {
        int w = landscape ? 480 : 270;
        int h = landscape ? 270 : 480;
        Gdx.graphics.setWindowedMode(w, h);
        Forge.isPortraitMode = !landscape;
        Field sw = Forge.class.getDeclaredField("screenWidth");
        sw.setAccessible(true);
        sw.setInt(null, w);
        Field sh = Forge.class.getDeclaredField("screenHeight");
        sh.setAccessible(true);
        sh.setInt(null, h);

        GameHUD.instance = null;
        setStaticNull(GameScene.class, "object");
        setStaticNull(InnScene.class, "object");
        setStaticNull(PlayerStatisticScene.class, "object");
        setStaticNull(SkillsScene.class, "object");

        WorldStage stage = WorldStage.getInstance();
        stage.getViewport().setWorldSize(Scene.getIntendedWidth(), Scene.getIntendedHeight());
        stage.getViewport().update(w, h, true);
    }

    private static void setStaticNull(Class<?> cls, String field) throws Exception {
        Field f = cls.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, null);
    }

    private static void generateAscendantWorld() {
        PointOfInterestData.clearRuntimeCacheForTests();
        DifficultyData diff = Config.instance().getConfigData().difficulties[0];
        // Standard avoids Sealed's giveGold() sound path (needs DeviceAdapter). Seed the
        // Standard window + SN1 season the same way createSealedStart would under Ascendant.
        WorldSave.generateNewWorld("Sn1Hero", false, 0, 0,
                ColorSet.W, diff,
                AdventureModes.Standard, 0, null, WORLD_SEED);
        WorldSave.getCurrentSave().getPlayer().clearAnnounceFantasy();
        WorldSave.getCurrentSave().getPlayer().clearAnnounceCustom();
        GamePlayerUtil.getGuiPlayer().setName(WorldSave.getCurrentSave().getPlayer().getName());

        String[] cores = Config.instance().getConfigData().coreSets;
        String[] starters = Config.instance().starterEditions();
        String core = cores != null && cores.length > 0 ? cores[0] : "M11";
        String set = starters != null && starters.length > 0 ? starters[0] : "ZEN";
        StandardWindow window = Current.player().getStandardWindow();
        window.init(java.util.List.of(core, set));
        Current.player().getSeason().initNewGame(window);

        Assert.assertTrue(window.isActive(), "Standard window must be active for SN1 HUD");
        Assert.assertTrue(Current.player().getSeason().isConfigured(),
                "SN1 must be configured after initNewGame");
        MapStage.getInstance().clearIsInMap();
    }

    private static void seedTrackedQuest(AdventurePlayer player) {
        List<AdventureQuestData> quests = player.getQuests();
        if (quests == null || quests.isEmpty()) {
            AdventureQuestData q = new AdventureQuestData();
            q.name = "SN1 Track Probe";
            q.isTracked = true;
            player.getQuests().add(q);
            return;
        }
        AdventureQuestController.trackQuest(player, quests.get(0));
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

    /** GameHUD BGM / gold SFX need a DeviceAdapter; GL boot never installs one. */
    private static void ensureStubDeviceAdapter() {
        try {
            if (Forge.getDeviceAdapter() != null) {
                return;
            }
            Field f = Forge.class.getDeclaredField("deviceAdapter");
            f.setAccessible(true);
            f.set(null, new forge.interfaces.IDeviceAdapter() {
                @Override public boolean isConnectedToInternet() { return false; }
                @Override public boolean isConnectedToWifi() { return false; }
                @Override public boolean isTablet() { return false; }
                @Override public String getDownloadsDir() {
                    return System.getProperty("java.io.tmpdir") + File.separator;
                }
                @Override public String getVersionString() { return "test"; }
                @Override public String getLatestChanges(String commitsAtom, java.util.Date buildDateOriginal, java.util.Date maxDate) {
                    return "";
                }
                @Override public String getReleaseTag(String releaseAtom) { return ""; }
                @Override public boolean openFile(String filename) { return false; }
                @Override public void setLandscapeMode(boolean landscapeMode) { }
                @Override public void preventSystemSleep(boolean preventSleep) { }
                @Override public void restart() { }
                @Override public void exit() { }
                @Override public void closeSplashScreen() { }
                @Override public void convertToJPEG(java.io.InputStream input, java.io.OutputStream output) { }
                @Override public void convertToPNG(java.io.InputStream input, java.io.OutputStream output) { }
                @Override public org.apache.commons.lang3.tuple.Pair<Integer, Integer> getRealScreenSize(boolean real) {
                    return org.apache.commons.lang3.tuple.Pair.of(480, 270);
                }
                @Override public java.util.ArrayList<String> getGamepads() {
                    return new java.util.ArrayList<>();
                }
                @Override public org.jupnp.UpnpServiceConfiguration getUpnpPlatformService() { return null; }
                @Override public boolean needFileAccess() { return false; }
                @Override public void requestFileAcces() { }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to install stub DeviceAdapter", e);
        }
    }

    private static void clearCoopLeftovers() {
        try {
            forge.adventure.coop.CoopSession.get().testClearGuestPlaneFollow();
            forge.adventure.coop.CoopSession.get().testClearHostingForHello();
        } catch (Throwable ignored) {
        }
    }

    private static void saveScreenshot(String fileName) {
        int gw = Gdx.graphics.getWidth();
        int gh = Gdx.graphics.getHeight();
        Assert.assertTrue(gw >= 270 && gh >= 270, "window must be full UI size, got " + gw + "x" + gh);
        Pixmap pm = ScreenUtils.getFrameBufferPixmap(0, 0, gw, gh);
        try {
            Pixmap flipped = new Pixmap(pm.getWidth(), pm.getHeight(), pm.getFormat());
            for (int y = 0; y < pm.getHeight(); y++) {
                for (int x = 0; x < pm.getWidth(); x++) {
                    flipped.drawPixel(x, pm.getHeight() - 1 - y, pm.getPixel(x, y));
                }
            }
            Set<Integer> colors = new HashSet<>();
            int step = Math.max(1, flipped.getWidth() / 40);
            for (int y = 0; y < flipped.getHeight(); y += step) {
                for (int x = 0; x < flipped.getWidth(); x += step) {
                    colors.add(flipped.getPixel(x, y));
                }
            }
            Assert.assertTrue(colors.size() >= 8,
                    fileName + " looks too uniform (" + colors.size()
                            + " colors) — not a real scene render");

            File out = ARTIFACTS.resolve(fileName).toFile();
            PixmapIO.writePNG(new com.badlogic.gdx.files.FileHandle(out), flipped);
            flipped.dispose();
            Assert.assertTrue(out.isFile() && out.length() >= MIN_REAL_PNG_BYTES,
                    "real screenshot required (>= " + MIN_REAL_PNG_BYTES + " bytes): "
                            + out + " size=" + (out.isFile() ? out.length() : -1));
        } finally {
            pm.dispose();
        }
    }
}
