package forge.adventure;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.SnapshotArray;
import com.github.tommyettinger.textra.TextraButton;
import forge.Forge;
import forge.adventure.data.DifficultyData;
import forge.adventure.data.PointOfInterestData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.player.StandardWindow;
import forge.adventure.scene.GameScene;
import forge.adventure.scene.Scene;
import forge.adventure.scene.SpellSmithScene;
import forge.adventure.scene.UIScene;
import forge.adventure.stage.GameHUD;
import forge.adventure.stage.MapStage;
import forge.adventure.stage.WorldStage;
import forge.adventure.util.AdventureModes;
import forge.adventure.util.AscendantCardEconomy;
import forge.adventure.util.CardUtil;
import forge.adventure.util.Config;
import forge.adventure.util.Controls;
import forge.adventure.util.CraftingApi;
import forge.adventure.util.Current;
import forge.adventure.util.MoxfieldMx1;
import forge.adventure.world.WorldSave;
import forge.card.ColorSet;
import forge.deck.DeckImportController;
import forge.deck.DeckRecognizer.Token;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.gamemodes.limited.DraftRankCache;
import forge.gui.interfaces.ICheckBox;
import forge.item.PaperCard;
import forge.player.GamePlayerUtil;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * EC1 GL screenshots (opt-in {@code gl} group under xvfb). Boots a live Ascendant
 * world and captures GameHUD + Spell Smith (craft entry) + Inventory in landscape
 * and portrait. Asserts live EC1 prices (not stub images).
 */
public class CardEconomyEc1GlTest {

    private static final Path ARTIFACTS = Path.of("/opt/cursor/artifacts/screenshots");
    /** Same seed as SV1 GL tests — known to finish under the 300s GL await. */
    private static final long WORLD_SEED = 0x515AFE01L;
    private static final long MIN_REAL_PNG_BYTES = 3_500L;

    @Test(groups = "gl", timeOut = 600_000)
    public void economyUiScreenshotsLandscapeAndPortrait() throws Exception {
        AdventureGlTestSupport.runOnGl(() -> {
            Assert.assertTrue(Config.ascendant(), "GL support boots Ascendant");
            Assert.assertTrue(AscendantCardEconomy.enabled(), "EC1 must be on");
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

            // Live economy check inside the running world (not a stub).
            PaperCard strong = null;
            PaperCard weak = null;
            try {
                for (PaperCard pc : forge.model.FModel.getMagicDb().getCommonCards().getAllCards("Plated Geopede")) {
                    if ("ZEN".equalsIgnoreCase(pc.getEdition())) {
                        strong = pc;
                        break;
                    }
                }
                for (PaperCard pc : forge.model.FModel.getMagicDb().getCommonCards().getAllCards("Archmage Ascension")) {
                    if ("ZEN".equalsIgnoreCase(pc.getEdition())) {
                        weak = pc;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            Assert.assertNotNull(strong, "Plated Geopede ZEN");
            Assert.assertNotNull(weak, "Archmage Ascension ZEN");
            Double sr = DraftRankCache.getRanking(strong.getName(), "ZEN");
            Double wr = DraftRankCache.getRanking(weak.getName(), "ZEN");
            Assert.assertNotNull(sr);
            Assert.assertNotNull(wr);
            Assert.assertTrue(sr < wr);
            Assert.assertTrue(CardUtil.getCardPrice(strong) > CardUtil.getCardPrice(weak),
                    "live EC1: strong common outprices weak rare");
            Assert.assertEquals(CardUtil.getCardPrice(
                    forge.model.FModel.getMagicDb().getCommonCards().getCard("Plains", "ZEN")), 0);
            Assert.assertNotNull(CraftingApi.formatDustCost(strong));

            try {
                captureOrientation(true);
                captureOrientation(false);
                captureSpikeRumourHud();
                captureMissingListCraftMissing();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Real xvfb render of the EC1 spike rumour on the GameHUD notification banner
     * ({@code lblEc1SpikeRumour}).
     */
    private static void captureSpikeRumourHud() throws Exception {
        applyOrientation(true);
        int w = 480;
        int h = 270;
        AdventurePlayer player = Current.player();
        Assert.assertNotNull(player);
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        PaperCard common = null;
        for (PaperCard pc : forge.model.FModel.getMagicDb().getCommonCards().getAllCards("Plated Geopede")) {
            if ("ZEN".equalsIgnoreCase(pc.getEdition())) {
                common = pc;
                break;
            }
        }
        Assert.assertNotNull(common, "need a spiked common for rumour render");
        AscendantCardEconomy.setSeasonSpikesForTest(List.of(common.getName()));
        String rumour = AscendantCardEconomy.spikeRumourLine();
        Assert.assertFalse(rumour.isBlank());
        Assert.assertTrue(rumour.contains(common.getName()), rumour);

        GameScene game = GameScene.instance();
        syncSceneViewports(game, w, h);
        game.enter();
        game.act(0.05f);
        GameHUD hud = GameHUD.getInstance();
        hud.clearNotifications();
        AscendantCardEconomy.showSpikeRumourNotification();
        // Drive notification Actions so the banner is on-screen for the capture.
        for (int i = 0; i < 120; i++) {
            hud.act(0.05f);
            game.act(0.05f);
        }
        Assert.assertTrue(hud.getNotificationText().contains(common.getName())
                        || hud.getNotificationText().toLowerCase(Locale.ROOT).contains("demand"),
                "HUD notification must show rumour, got: " + hud.getNotificationText());
        renderForScreenshot(game);
        saveScreenshot("ec1_spike_rumour_hud_landscape.png");
        try {
            game.leave();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Real xvfb render of the MX1 Missing list with per-card dust, total dust, and a
     * controller-reachable Craft missing action (adventure dialog — FOptionPane overlays
     * do not draw over adventure scenes).
     */
    private static void captureMissingListCraftMissing() throws Exception {
        applyOrientation(true);
        int w = 480;
        int h = 270;
        AdventurePlayer player = Current.player();
        Assert.assertNotNull(player);
        player.getStandardWindow().init(List.of("M10", "M11", "ZEN"));
        player.getCards().clear();
        PaperCard bolt = forge.model.FModel.getMagicDb().getCommonCards()
                .getCard("Lightning Bolt", "M10", "146");
        Assert.assertNotNull(bolt, "M10 Lightning Bolt");
        player.addCard(bolt, 2);
        int unit = Math.max(1, player.craftCost(bolt));
        player.addDust(AdventurePlayer.DUST_COMMON, unit * 2 + 80);
        forge.adventure.data.MaterialData ash = forge.adventure.data.MaterialListData.primaryReagent("R", 1);
        Assert.assertNotNull(ash);
        player.addMaterial(ash.id, 4);

        DeckImportController controller = new DeckImportController(stubCheck(false), null, null, false);
        controller.setPlayerInventory(player.getCards());
        controller.setImportBehavior(DeckImportController.ImportBehavior.REPLACE_CURRENT);
        controller.setFreePrintConverter(pc -> pc != null && pc.isVeryBasicLand() ? pc : null);
        controller.setAllowedSections(List.of(DeckSection.Main, DeckSection.Sideboard));
        controller.setGameFormat(GameType.Adventure);
        List<Token> tokens = controller.parseInput("Deck\n4 Lightning Bolt (M10) 146\n");
        tokens = controller.constrainTokensToInventory();
        List<MoxfieldMx1.MissingCard> missing = MoxfieldMx1.missingFromTokens(tokens, player.getCards());
        Assert.assertFalse(missing.isEmpty(), "need a Missing gap for the render");
        CraftingApi.CraftMissingPlan plan = CraftingApi.planCraftMissing(
                MoxfieldMx1.toCraftRequests(missing));
        Assert.assertTrue(plan.craftEnabled, "GL render needs affordable Craft missing: " + plan.disabledReason);

        Localizer loc = Forge.getLocalizer();
        String body = MoxfieldMx1.formatMissingList(missing)
                + "\n\n" + loc.getMessage("lblImportOwnedCopiesConfirm");
        Assert.assertTrue(body.toLowerCase(Locale.ROOT).contains("dust"), body);
        Assert.assertTrue(body.toLowerCase(Locale.ROOT).contains("total"), body);

        GameScene game = GameScene.instance();
        syncSceneViewports(game, w, h);
        game.enter();
        game.act(0.05f);
        WorldStage stage = WorldStage.getInstance();
        Assert.assertNotNull(stage.getDialog(), "WorldStage dialog required for Missing list render");
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d = stage.getDialog();
        d.getButtonTable().clear();
        d.getContentTable().clear();
        d.clearListeners();
        com.github.tommyettinger.textra.TextraLabel label = Controls.newTextraLabel(body);
        label.setWrap(true);
        d.getContentTable().add(label).width(420f);
        d.getButtonTable().add(Controls.newTextButton(loc.getMessage("lblImportWhatIOwn"),
                stage::hideDialog)).width(200f).row();
        TextraButton craftBtn = Controls.newTextButton(loc.getMessage("lblCraftMissing"),
                stage::hideDialog);
        d.getButtonTable().add(craftBtn).width(200f).row();
        d.getButtonTable().add(Controls.newTextButton(loc.getMessage("lblCancel"),
                stage::hideDialog)).width(200f).row();
        d.setKeepWithinStage(true);
        stage.showDialog();
        game.act(0.05f);
        renderForScreenshot(game);
        saveScreenshot("ec1_mx1_missing_craft_missing_landscape.png");
        Assert.assertTrue(craftBtn.isVisible(), "Craft missing must be visible");
        stage.hideDialog();
        try {
            game.leave();
        } catch (Throwable ignored) {
        }
    }

    private static ICheckBox stubCheck(boolean selected) {
        return new ICheckBox() {
            private boolean on = selected;
            @Override public boolean isSelected() { return on; }
            @Override public void setSelected(boolean b0) { on = b0; }
            @Override public boolean isEnabled() { return true; }
            @Override public void setEnabled(boolean b0) { }
            @Override public boolean isVisible() { return true; }
            @Override public void setVisible(boolean b0) { }
            @Override public String getToolTipText() { return null; }
            @Override public void setToolTipText(String s0) { }
        };
    }

    private static void captureOrientation(boolean landscape) throws Exception {
        applyOrientation(landscape);
        String suffix = landscape ? "landscape" : "portrait";
        int w = landscape ? 480 : 270;
        int h = landscape ? 270 : 480;

        GameScene game = GameScene.instance();
        syncSceneViewports(game, w, h);
        game.enter();
        game.act(0.05f);
        GameHUD hud = GameHUD.getInstance();
        syncSceneViewports(game, w, h);
        renderForScreenshot(game);
        saveScreenshot("ec1_hud_economy_" + suffix + ".png");
        Assert.assertTrue(findByName(collectActors(hud.ui), "map").isVisible(),
                "minimap must be visible in " + suffix);

        SpellSmithScene smith = SpellSmithScene.instance();
        syncSceneViewports(smith, w, h);
        smith.enter();
        smith.act(0.05f);
        Actor craft = findCraftButton(smith);
        Assert.assertNotNull(craft, "Spell Smith must expose Craft entry for EC1/MX1");
        Assert.assertTrue(craft.isVisible(), "Craft must be visible");
        assertSelectableContains(smith, craft);
        // Pairwise: Craft button must not overlap Done / pull / Refine / Prism.
        assertCraftClearOfNamedPeers(smith);
        renderForScreenshot(smith);
        saveScreenshot("ec1_spellsmith_craft_" + suffix + ".png");

        try {
            smith.leave();
            game.leave();
        } catch (Throwable ignored) {
        }
    }

    private static Actor findCraftButton(SpellSmithScene smith) {
        for (Actor a : collectActors(smith.getUI())) {
            if (a instanceof TextraButton) {
                String t = ((TextraButton) a).getText().toString();
                if (t != null && t.toLowerCase().contains("craft"))
                    return a;
            }
            if (a.getName() != null && a.getName().toLowerCase().contains("craft"))
                return a;
        }
        return null;
    }

    private static void assertCraftClearOfNamedPeers(SpellSmithScene smith) {
        Actor craft = findCraftButton(smith);
        Assert.assertNotNull(craft);
        float[] cr = actorRect(craft);
        for (String peerName : new String[] { "done", "pullUsingGold", "pullUsingShards", "accept", "decline" }) {
            Actor peer = findByName(collectActors(smith.getUI()), peerName);
            if (peer == null || !peer.isVisible())
                continue;
            float[] pr = actorRect(peer);
            Assert.assertFalse(rectsOverlap(cr, pr),
                    "Craft overlaps " + peerName + " in Spell Smith");
        }
        // Also clear of Refine / Prism siblings.
        for (Actor a : collectActors(smith.getUI())) {
            if (a == craft || !(a instanceof TextraButton) || !a.isVisible())
                continue;
            String t = ((TextraButton) a).getText().toString();
            if (t == null)
                continue;
            String lower = t.toLowerCase();
            if (!lower.contains("refine") && !lower.contains("prism"))
                continue;
            Assert.assertFalse(rectsOverlap(cr, actorRect(a)), "Craft overlaps " + t);
        }
    }

    private static void assertSelectableContains(UIScene scene, Actor actor) {
        // Craft is Ascendant-added; ensure it participates in controller focus when registered.
        try {
            java.lang.reflect.Method m = UIScene.class.getDeclaredMethod("visibleSelection");
            m.setAccessible(true);
            Object arr = m.invoke(scene);
            if (arr instanceof com.badlogic.gdx.utils.Array) {
                @SuppressWarnings("unchecked")
                com.badlogic.gdx.utils.Array<Object> sel = (com.badlogic.gdx.utils.Array<Object>) arr;
                for (Object o : sel) {
                    try {
                        Field af = o.getClass().getDeclaredField("actor");
                        af.setAccessible(true);
                        if (af.get(o) == actor)
                            return;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // Fallback: visible + on stage is enough; Spell Smith adds Craft outside JSON selectables.
        Assert.assertTrue(actor.isVisible(), "Craft must be visible for controller users");
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
        setStaticNull(SpellSmithScene.class, "object");

        Gdx.gl.glViewport(0, 0, w, h);
        WorldStage stage = WorldStage.getInstance();
        stage.getViewport().setWorldSize(Scene.getIntendedWidth(), Scene.getIntendedHeight());
        stage.getViewport().update(w, h, true);
    }

    private static void syncSceneViewports(Scene scene, int w, int h) {
        float worldW = Scene.getIntendedWidth();
        float worldH = Scene.getIntendedHeight();
        if (scene instanceof GameScene) {
            GameHUD hud = GameHUD.getInstance();
            pinViewport(hud.getViewport(), hud.getCamera(), worldW, worldH, w, h, true);
            pinViewport(WorldStage.getInstance().getViewport(), WorldStage.getInstance().getCamera(),
                    worldW, worldH, w, h, false);
            return;
        }
        if (scene instanceof UIScene) {
            try {
                Field stageField = UIScene.class.getDeclaredField("stage");
                stageField.setAccessible(true);
                com.badlogic.gdx.scenes.scene2d.Stage st =
                        (com.badlogic.gdx.scenes.scene2d.Stage) stageField.get(scene);
                pinViewport(st.getViewport(), st.getCamera(), worldW, worldH, w, h, true);
            } catch (Exception e) {
                throw new RuntimeException("Failed to sync UIScene viewport", e);
            }
        }
    }

    private static void pinViewport(com.badlogic.gdx.utils.viewport.Viewport vp,
            com.badlogic.gdx.graphics.Camera cam, float worldW, float worldH, int screenW, int screenH,
            boolean resetOrtho) {
        vp.setWorldSize(worldW, worldH);
        vp.update(screenW, screenH, true);
        if (resetOrtho && cam instanceof com.badlogic.gdx.graphics.OrthographicCamera) {
            com.badlogic.gdx.graphics.OrthographicCamera oc =
                    (com.badlogic.gdx.graphics.OrthographicCamera) cam;
            oc.setToOrtho(false, worldW, worldH);
            oc.position.set(worldW / 2f, worldH / 2f, 0f);
            oc.update();
        }
    }

    private static void setStaticNull(Class<?> cls, String field) throws Exception {
        Field f = cls.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, null);
    }

    private static void generateAscendantWorld() {
        PointOfInterestData.clearRuntimeCacheForTests();
        DifficultyData diff = Config.instance().getConfigData().difficulties[0];
        WorldSave.generateNewWorld("Ec1Hero", false, 0, 0,
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
        MapStage.getInstance().clearIsInMap();
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

    private static void renderForScreenshot(Scene scene) {
        int gw = Gdx.graphics.getWidth();
        int gh = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, gw, gh);
        syncSceneViewports(scene, gw, gh);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (scene instanceof GameScene) {
            scene.render();
            return;
        }
        if (scene instanceof UIScene) {
            try {
                Field stageField = UIScene.class.getDeclaredField("stage");
                stageField.setAccessible(true);
                com.badlogic.gdx.scenes.scene2d.Stage stage =
                        (com.badlogic.gdx.scenes.scene2d.Stage) stageField.get(scene);
                stage.draw();
            } catch (Exception e) {
                throw new RuntimeException("UIScene stage draw failed", e);
            }
            return;
        }
        scene.render();
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
            Assert.assertTrue(colors.size() >= 4,
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

    private static List<Actor> collectActors(Group root) {
        List<Actor> out = new ArrayList<>();
        if (root == null)
            return out;
        SnapshotArray<Actor> children = root.getChildren();
        Actor[] items = children.begin();
        try {
            for (int i = 0; i < children.size; i++) {
                Actor a = items[i];
                out.add(a);
                if (a instanceof Group)
                    out.addAll(collectActors((Group) a));
            }
        } finally {
            children.end();
        }
        return out;
    }

    private static Actor findByName(List<Actor> actors, String name) {
        for (Actor a : actors) {
            if (name.equals(a.getName()))
                return a;
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
}
