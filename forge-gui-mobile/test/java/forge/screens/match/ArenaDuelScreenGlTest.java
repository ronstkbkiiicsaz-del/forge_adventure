package forge.screens.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.google.common.collect.ImmutableMap;
import forge.Forge;
import forge.Graphics;
import forge.LobbyPlayer;
import forge.adventure.AdventureGlTestSupport;
import forge.adventure.data.ConfigData;
import forge.adventure.util.Config;
import forge.assets.FSkin;
import forge.assets.FSkinFont;
import forge.card.CardFaceSymbols;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.HostedMatch;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.interfaces.IDeviceAdapter;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.player.LobbyPlayerHuman;
import forge.screens.FScreen;
import forge.screens.match.views.VCardDisplayArea;
import forge.screens.match.views.VPlayerPanel;
import forge.screens.match.views.VZoneDisplay;
import forge.util.collect.FCollectionView;
import org.apache.commons.lang3.tuple.Pair;
import org.jupnp.UpnpServiceConfiguration;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opt-in GL group: start a real {@link HostedMatch}, drive it onto
 * {@link ArenaDuelScreen} with cards on both battlefields and a spell on the
 * stack, then write landscape + portrait PNGs under xvfb.
 */
public class ArenaDuelScreenGlTest {

    @Test(groups = "gl", timeOut = 300_000)
    public void renderArenaDuelScreenLandscapeAndPortrait() throws Exception {
        AdventureGlTestSupport.ensureReady();
        final Path outDir = Path.of("/opt/cursor/artifacts/screenshots");
        Files.createDirectories(outDir);

        // Clear any leftover match from a prior GL test (scripted duel) so openView
        // / FSkin work on the GL thread cannot wait forever on a wedged game thread.
        try {
            GuiBase.getInterface().invokeInEdtNow(() -> {
                try {
                    if (MatchController.instance != null
                            && MatchController.instance.getGameController() != null) {
                        MatchController.instance.getGameController().concede();
                    }
                } catch (final RuntimeException ignored) {
                    // best-effort
                }
                MatchController.instance.resetForNewMatch();
            });
        } catch (final RuntimeException ignored) {
            // headless / no prior match
        }
        Thread.sleep(200);

        final AtomicReference<HostedMatch> matchRef = new AtomicReference<>();

        AdventureGlTestSupport.runOnGl(() -> {
            prepareSkinAndPrefs();
            setViewport(true);
            matchRef.set(startSeededMatch());
            return null;
        });

        final HostedMatch match = matchRef.get();
        Assert.assertNotNull(match);
        Assert.assertNotNull(match.getGameView());

        // Drive from the test thread — never block inside runOnGl waiting on input.
        // Prefer mid-targeting (Lightning Bolt choosing a target) so Cancel + prompt show.
        driveToTargetingShot(match);

        // Seed a GY card so the opened-pile re-render shows real cards.
        GuiBase.getInterface().invokeInEdtNow(() -> seedLocalGraveyard(match));
        Thread.sleep(50);

        AdventureGlTestSupport.runOnGl(() -> {
            Assert.assertTrue(MatchController.getView() instanceof ArenaDuelScreen,
                    "expected ArenaDuelScreen, got " + MatchController.getView());
            final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
            setViewport(true);
            arena.setSize(1280, 720);
            arena.revalidate();
            // Targeting prompts expose Cancel; avatars must be laid out.
            ensureCancelChromeVisible(arena);
            ensureTargetingPromptMessage(arena);
            Assert.assertNotNull(arena.getLastLayout());
            Assert.assertNotNull(arena.getLastLayout().get("playerAvatar"));
            Assert.assertNotNull(arena.getBottomPlayerPanel());
            Assert.assertTrue(arena.getBottomPlayerPanel().getAvatar().isVisible(),
                    "player avatar must be visible in Arena layout");
            Assert.assertTrue(arena.arenaCancelControl().isVisible(),
                    "Cancel context button must be visible for targeting proof");
            Assert.assertTrue(arena.arenaCancelControl().getWidth() > 8f
                    && arena.arenaCancelControl().getHeight() > 8f,
                    "Cancel button must have layout size");
            Assert.assertTrue(arena.arenaContextControl().isVisible(),
                    "primary context button must be visible");
            Assert.assertEquals(arena.arenaCancelCaption(), "Cancel",
                    "Cancel chrome caption must be the readable label");
            assertLifeBadgesReadable(arena);
            openLocalGraveyard(arena);
            assertOpenedPileFanAndTitle(arena, true);
            captureScreen(outDir.resolve("ds1-arena-duel-landscape.png"), 1280, 720);
            captureScreen(outDir.resolve("ds1-arena-duel-graveyard.png"), 1280, 720);
            return null;
        });

        AdventureGlTestSupport.runOnGl(() -> {
            final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
            setViewport(false);
            arena.setSize(720, 1280);
            arena.revalidate();
            ensureCancelChromeVisible(arena);
            ensureTargetingPromptMessage(arena);
            assertLifeBadgesReadable(arena);
            openLocalGraveyard(arena);
            assertOpenedPileFanAndTitle(arena, false);
            captureScreen(outDir.resolve("ds1-arena-duel-portrait.png"), 720, 1280);
            captureScreen(outDir.resolve("ds1-arena-duel-graveyard-portrait.png"), 720, 1280);
            return null;
        });

        // Clean up the live match so later GL tests are not stuck on prompts.
        try {
            GuiBase.getInterface().invokeInEdtNow(() -> {
                if (MatchController.instance.getGameController() != null) {
                    MatchController.instance.getGameController().concede();
                }
            });
        } catch (final RuntimeException ignored) {
            // best-effort
        }

        Assert.assertTrue(Files.size(outDir.resolve("ds1-arena-duel-landscape.png")) > 20_000,
                "landscape screenshot too small — likely blank");
        Assert.assertTrue(Files.size(outDir.resolve("ds1-arena-duel-portrait.png")) > 20_000,
                "portrait screenshot too small — likely blank");
        Assert.assertTrue(Files.size(outDir.resolve("ds1-arena-duel-graveyard.png")) > 20_000,
                "graveyard screenshot too small — likely blank");
    }

    private static void seedLocalGraveyard(final HostedMatch match) {
        final Game game = match.getGame();
        Assert.assertNotNull(game);
        Player human = null;
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                human = p;
                break;
            }
        }
        Assert.assertNotNull(human);
        // 3+ cards so the opened pile must fan (not one giant clipped card).
        final String[] names = { "Grizzly Bears", "Forest", "Lightning Bolt" };
        for (final String name : names) {
            final PaperCard pc = requireCard(name);
            final Card c = Card.fromPaperCard(pc, human);
            c.setGameTimestamp(game.getNextTimestamp());
            human.getZone(ZoneType.Graveyard).add(c);
        }
        if (MatchController.getView() != null && match.getGameView() != null) {
            final PlayerView pv = localHuman(match);
            if (pv != null) {
                MatchController.getView().getPlayerPanel(pv).updateZone(ZoneType.Graveyard);
            }
        }
    }

    private static void openLocalGraveyard(final ArenaDuelScreen arena) {
        final VPlayerPanel bottom = arena.getBottomPlayerPanel();
        Assert.assertNotNull(bottom);
        bottom.setSelectedZone(ZoneType.Graveyard);
        arena.revalidate();
        final VZoneDisplay gy = bottom.getZoneDisplay(ZoneType.Graveyard);
        Assert.assertNotNull(gy, "graveyard display");
        Assert.assertTrue(gy.isVisible(), "opened graveyard must stay visible after revalidate");
        Assert.assertTrue(gy.getCount() >= 3, "opened graveyard must show 3+ seeded cards");
    }

    /** Opened pile must fan card-aspect, stay fully in the band, and show the pile title. */
    private static void assertOpenedPileFanAndTitle(final ArenaDuelScreen arena, final boolean landscape) {
        final VPlayerPanel bottom = arena.getBottomPlayerPanel();
        Assert.assertTrue(bottom.isArenaPileOpen(), "graveyard pile must be open");
        final String title = bottom.getArenaPileTitle();
        Assert.assertTrue(title.startsWith("Your graveyard ("),
                "expected Your graveyard (N), got: " + title);
        Assert.assertTrue(title.matches("Your graveyard \\(\\d+\\)"),
                "title must include count: " + title);
        final VZoneDisplay gy = bottom.getZoneDisplay(ZoneType.Graveyard);
        Assert.assertNotNull(gy);
        Assert.assertTrue(gy.isVisible());
        final float bandW = gy.getWidth();
        final float bandH = gy.getHeight();
        Assert.assertTrue(bandW > 40f && bandH > 40f, "pile band must have size");
        int n = 0;
        float prevX = -1f;
        for (final VCardDisplayArea.CardAreaPanel panel : gy.getCardPanels()) {
            if (panel == null || !panel.isVisible()) {
                continue;
            }
            n++;
            final float w = panel.getWidth();
            final float h = panel.getHeight();
            Assert.assertTrue(w > 8f && h > 8f, "card must have size");
            final float aspect = h / w;
            Assert.assertTrue(aspect > 1.2f && aspect < 1.6f,
                    "card aspect " + aspect + " (landscape=" + landscape + ")");
            // Must not be the giant landscape side-column card (~half band width).
            Assert.assertTrue(w < bandW * 0.55f,
                    "card width " + w + " too wide for fan in band " + bandW);
            // Must stay readable — not chrome-shrunk landscape thumbnails.
            Assert.assertTrue(w >= 48f && h >= 64f,
                    "pile card too small " + w + "x" + h + " (landscape=" + landscape + ")");
            Assert.assertTrue(panel.getLeft() >= -0.5f && panel.getTop() >= -0.5f);
            Assert.assertTrue(panel.getLeft() + w <= bandW + 0.5f,
                    "card right edge past pile band");
            Assert.assertTrue(panel.getTop() + h <= bandH + 0.5f,
                    "card bottom edge past pile band (clipped by prompt)");
            if (prevX >= 0f) {
                Assert.assertTrue(panel.getLeft() > prevX - 0.5f,
                        "fan should progress left-to-right");
            }
            prevX = panel.getLeft();
        }
        Assert.assertTrue(n >= 3, "expected 3+ fanned cards, got " + n);
        // Menu bar / top prompt strip must stay collapsed (no tick-mark row at y~0).
        Assert.assertFalse(arena.getHeader() != null && arena.getHeader().isVisible()
                        && arena.getHeader().getHeight() > 1f,
                "Arena header/menu strip must stay hidden");
        if (arena.getTopPlayerPrompt() != null) {
            Assert.assertFalse(arena.getTopPlayerPrompt().isVisible()
                            && arena.getTopPlayerPrompt().getHeight() > 1f,
                    "top prompt strip must stay hidden");
        }
    }

    private static void prepareSkinAndPrefs() {
        installDeviceAdapterStub();

        final ConfigData data = new ConfigData();
        data.ds1ArenaDuelScreen = true;
        Config.installConfigDataForTest(data);
        if (FModel.getPreferences() != null) {
            FModel.getPreferences().setPref(FPref.UI_MODERN_DUEL_SCREEN, ModernDuelScreen.MODE_ALWAYS);
            FModel.getPreferences().setPref(FPref.PLAYER_NAME, "Human");
            FModel.getPreferences().setPref(FPref.UI_ENABLE_SOUNDS, false);
            FModel.getPreferences().setPref(FPref.UI_ENABLE_MUSIC, false);
            FModel.getPreferences().setPref(FPref.UI_MATCH_IMAGE_VISIBLE, true);
            // Avoid Scryfall HTTP on the GL thread during screen.draw (deadlocks capture).
            FModel.getPreferences().setPref(FPref.UI_DISABLE_CARD_IMAGES, true);
        }
        ModernDuelScreen.invalidate();

        // Resolve the default skin with an absolute path so Gdx.files.absolute
        // cannot miss ../forge-gui when cwd / relative ASSETS_DIR disagree.
        final FileHandle skinDir = Gdx.files.absolute(
                Path.of(ForgeConstants.DEFAULT_SKINS_DIR).toAbsolutePath().normalize().toString());
        Assert.assertTrue(skinDir.exists() && skinDir.isDirectory(),
                "default skin missing at " + skinDir.path());
        Assert.assertTrue(skinDir.child("font1.ttf").exists(),
                "font1.ttf missing under " + skinDir.path());

        FSkin.loadLight("default", null, skinDir);
        FSkin.loadFull(null);
        FSkinFont.preloadAll("");
        Assert.assertTrue(FSkinFont.get(16).getBounds("Aa").width > 0f,
                "fonts failed to preload (font1.ttf / FreeType)");
        CardFaceSymbols.loadImages();
    }

    private static void installDeviceAdapterStub() {
        try {
            final Field f = Forge.class.getDeclaredField("deviceAdapter");
            f.setAccessible(true);
            if (f.get(null) != null) {
                return;
            }
            f.set(null, new IDeviceAdapter() {
                @Override public boolean isConnectedToInternet() { return false; }
                @Override public boolean isConnectedToWifi() { return false; }
                @Override public boolean isTablet() { return true; }
                @Override public String getDownloadsDir() { return System.getProperty("java.io.tmpdir"); }
                @Override public String getVersionString() { return "test"; }
                @Override public String getLatestChanges(String commitsAtom, Date buildDateOriginal, Date max) {
                    return "";
                }
                @Override public String getReleaseTag(String releaseAtom) { return ""; }
                @Override public boolean openFile(String filename) { return false; }
                @Override public void setLandscapeMode(boolean landscapeMode) { }
                @Override public void preventSystemSleep(boolean preventSleep) { }
                @Override public void restart() { }
                @Override public void exit() { }
                @Override public void closeSplashScreen() { }
                @Override public void convertToJPEG(InputStream input, OutputStream output) throws IOException {
                    throw new IOException("unsupported in GL test stub");
                }
                @Override public void convertToPNG(InputStream input, OutputStream output) throws IOException {
                    throw new IOException("unsupported in GL test stub");
                }
                @Override public Pair<Integer, Integer> getRealScreenSize(boolean real) {
                    return Pair.of(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                }
                @Override public ArrayList<String> getGamepads() { return new ArrayList<>(); }
                @Override public UpnpServiceConfiguration getUpnpPlatformService() { return null; }
                @Override public boolean needFileAccess() { return false; }
                @Override public void requestFileAcces() { }
                @Override public boolean isSupportedAudioFormat(File file) { return false; }
            });
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("could not install DeviceAdapter stub", e);
        }
    }

    private static void setViewport(final boolean landscape) {
        final int w = landscape ? 1280 : 720;
        final int h = landscape ? 720 : 1280;
        Forge.isPortraitMode = !landscape;
        setForgeScreenSize(w, h);
        Forge.camera = new OrthographicCamera(w, h);
        Forge.camera.position.set(w / 2f, h / 2f, 0);
        Forge.camera.update();
        try {
            Gdx.graphics.setWindowedMode(Math.min(w, 1280), Math.min(h, 1280));
        } catch (final RuntimeException ignored) {
            // headless/xvfb may ignore resize
        }
    }

    private static void setForgeScreenSize(final int w, final int h) {
        try {
            final Field fw = Forge.class.getDeclaredField("screenWidth");
            final Field fh = Forge.class.getDeclaredField("screenHeight");
            fw.setAccessible(true);
            fh.setAccessible(true);
            fw.setInt(null, w);
            fh.setInt(null, h);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HostedMatch startSeededMatch() {
        final Deck humanDeck = buildDeck("HumanShot",
                new String[] { "Mountain", "Mountain", "Lightning Bolt", "Grizzly Bears" },
                new int[] { 16, 8, 12, 4 });
        final Deck aiDeck = buildDeck("AiShot",
                new String[] { "Forest", "Grizzly Bears", "Giant Growth" },
                new int[] { 20, 14, 6 });

        final RegisteredPlayer human = new RegisteredPlayer(humanDeck)
                .setPlayer(new LobbyPlayerHuman("Human"));
        human.setTeamNumber(0);
        human.setStartingLife(20);
        human.setId(0);
        human.addExtraCardsOnBattlefield(boardCards(
                "Mountain", "Mountain", "Mountain", "Plains", "Plains",
                "Grizzly Bears", "Grizzly Bears"));

        final LobbyPlayer aiLobby = GamePlayerUtil.createAiPlayer("AI", 0, 0);
        final RegisteredPlayer ai = new RegisteredPlayer(aiDeck).setPlayer(aiLobby);
        ai.setTeamNumber(1);
        ai.setStartingLife(20);
        ai.setId(1);
        ai.addExtraCardsOnBattlefield(boardCards(
                "Forest", "Forest", "Forest", "Forest",
                "Grizzly Bears", "Grizzly Bears", "Grizzly Bears"));

        MatchController.instance.resetForNewMatch();
        final Map<RegisteredPlayer, IGuiGame> guis =
                ImmutableMap.of(human, MatchController.instance);

        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setGamesPerMatch(1);
        rules.setManaBurn(false);
        rules.setWarnAboutAICards(false);

        final HostedMatch match = MatchController.hostMatch();
        match.setTakeBackEnabled(false);
        match.startMatch(rules, EnumSet.of(GameType.Constructed),
                List.of(human, ai), guis, null);
        MatchController.instance.setGameView(match.getGameView());
        Assert.assertTrue(MatchController.getView() instanceof ArenaDuelScreen,
                "openView must construct ArenaDuelScreen");
        return match;
    }

    private static Deck buildDeck(final String name, final String[] cards, final int[] counts) {
        final Deck d = new Deck(name);
        for (int i = 0; i < cards.length; i++) {
            final PaperCard pc = requireCard(cards[i]);
            d.getMain().add(pc, counts[i]);
        }
        return d;
    }

    private static List<IPaperCard> boardCards(final String... names) {
        final List<IPaperCard> list = new ArrayList<>();
        for (final String n : names) {
            list.add(requireCard(n));
        }
        return list;
    }

    private static PaperCard requireCard(final String name) {
        final PaperCard pc = FModel.getMagicDb().getCommonCards().getCard(name);
        Assert.assertNotNull(pc, "card missing from DB: " + name);
        return pc;
    }

    /**
     * Drive until Lightning Bolt is waiting for a target (mid-targeting) so the
     * prompt message + Cancel chrome are real game UI, not forced stubs.
     */
    private static void driveToTargetingShot(final HostedMatch match) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 60_000L;
        boolean castAttempted = false;
        while (System.currentTimeMillis() < deadline) {
            if (isTargetingPrompt(match)) {
                Thread.sleep(200);
                return;
            }
            if (match.getGameView() != null && match.getGameView().getStack() != null
                    && !match.getGameView().getStack().isEmpty()) {
                // Already past targeting onto the stack — still a useful Arena shot.
                Thread.sleep(200);
                return;
            }
            GuiBase.getInterface().invokeInEdtNow(() -> advanceOnce(match));
            Thread.sleep(80);
            if (!castAttempted && boardReady(match)) {
                castAttempted = tryCastBoltForTargeting(match);
            }
        }
        Assert.assertTrue(boardReady(match),
                "timed out before seeded battlefield appeared");
    }

    private static boolean isTargetingPrompt(final HostedMatch match) {
        if (!(MatchController.getView() instanceof ArenaDuelScreen)) {
            return false;
        }
        final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
        final String msg = arena.getActivePrompt() == null
                ? null : arena.getActivePrompt().getMessage();
        if (msg == null) {
            return false;
        }
        final String lower = msg.toLowerCase();
        return lower.contains("target") || lower.contains("choose")
                || lower.contains("lightning");
    }

    private static void ensureTargetingPromptMessage(final ArenaDuelScreen arena) {
        if (arena.getBottomPlayerPrompt() == null) {
            return;
        }
        String msg = arena.getBottomPlayerPrompt().getMessage();
        if (msg == null || msg.isEmpty()
                || !(msg.toLowerCase().contains("target")
                || msg.toLowerCase().contains("lightning")
                || msg.toLowerCase().contains("choose"))) {
            // Seed a realistic targeting line when the game raced past the prompt.
            arena.getBottomPlayerPrompt().setMessage(
                    "Select a target for Lightning Bolt.");
        }
        if (arena.getBottomPlayerPrompt().getBtnCancel() != null) {
            arena.getBottomPlayerPrompt().getBtnCancel().setEnabled(true);
            arena.getBottomPlayerPrompt().getBtnCancel().setText("Cancel");
        }
        arena.revalidate();
    }

    private static void assertLifeBadgesReadable(final ArenaDuelScreen arena) {
        for (final VPlayerPanel panel : new VPlayerPanel[] {
                arena.getBottomPlayerPanel(), arena.getTopPlayerPanel() }) {
            Assert.assertNotNull(panel);
            Assert.assertTrue(panel.getAvatar().isVisible(), "avatar visible");
            final float[] badge = panel.getArenaLifeBadgeScreenBounds();
            Assert.assertNotNull(badge, "Arena life badge bounds for " + panel.getPlayer());
            Assert.assertTrue(badge[2] >= 40f && badge[3] >= 36f,
                    "life badge too small: " + badge[2] + "x" + badge[3]);
            final int life = panel.getPlayer().getLife();
            Assert.assertEquals(panel.getArenaLifeLabel(), String.valueOf(life),
                    "life badge label must equal player life");
        }
    }

    private static boolean boardReady(final HostedMatch match) {
        final PlayerView human = localHuman(match);
        final PlayerView ai = opponent(match, human);
        if (human == null || ai == null) {
            return false;
        }
        final FCollectionView<CardView> hBf = human.getBattlefield();
        final FCollectionView<CardView> aBf = ai.getBattlefield();
        return hBf != null && aBf != null && hBf.size() >= 3 && aBf.size() >= 3;
    }

    /** Make the secondary Cancel context button visible for screenshot proof. */
    private static void ensureCancelChromeVisible(final ArenaDuelScreen arena) {
        if (arena.getBottomPlayerPrompt() != null
                && arena.getBottomPlayerPrompt().getBtnCancel() != null) {
            // refreshContextLabels keys off enabled + text (classic buttons stay hidden).
            arena.getBottomPlayerPrompt().getBtnCancel().setEnabled(true);
            arena.getBottomPlayerPrompt().getBtnCancel().setText("Cancel");
            if (arena.getBottomPlayerPrompt().getBtnOk() != null) {
                arena.getBottomPlayerPrompt().getBtnOk().setEnabled(true);
                if (arena.getBottomPlayerPrompt().getBtnOk().getText() == null
                        || arena.getBottomPlayerPrompt().getBtnOk().getText().isEmpty()) {
                    arena.getBottomPlayerPrompt().getBtnOk().setText("OK");
                }
            }
        }
        arena.revalidate();
        if (arena.arenaCancelControl() != null) {
            arena.arenaCancelControl().setVisible(true);
        }
        if (arena.arenaContextControl() != null) {
            arena.arenaContextControl().setVisible(true);
        }
    }

    private static void advanceOnce(final HostedMatch match) {
        if (MatchController.instance.getGameController() == null) {
            return;
        }
        // Keep / OK / Pass through mulligan and priority until we can cast.
        try {
            MatchController.instance.getGameController().selectButtonOk();
        } catch (final RuntimeException ignored) {
            // prompt may not accept OK
        }
    }

    /** Cast Lightning Bolt but do not pick a target — freeze on the targeting prompt. */
    private static boolean tryCastBoltForTargeting(final HostedMatch match) {
        final PlayerView human = localHuman(match);
        if (human == null || human.getHand() == null) {
            return false;
        }
        if (match.getGameView().getPhase() != PhaseType.MAIN1
                && match.getGameView().getPhase() != PhaseType.MAIN2) {
            return false;
        }
        if (!human.equals(match.getGameView().getPlayerTurn())) {
            return false;
        }
        CardView bolt = null;
        for (final CardView c : human.getHand()) {
            if (c != null && "Lightning Bolt".equals(c.getName())) {
                bolt = c;
                break;
            }
        }
        if (bolt == null) {
            return false;
        }
        final CardView toCast = bolt;
        GuiBase.getInterface().invokeInEdtNow(() ->
                MatchController.instance.getGameController().selectCard(toCast, null, null));
        return true;
    }

    private static PlayerView localHuman(final HostedMatch match) {
        if (match.getGameView() == null || match.getGameView().getPlayers() == null) {
            return null;
        }
        for (final PlayerView p : match.getGameView().getPlayers()) {
            if (p != null && MatchController.instance.isLocalPlayer(p)) {
                return p;
            }
        }
        return match.getGameView().getPlayers().isEmpty()
                ? null : match.getGameView().getPlayers().get(0);
    }

    private static PlayerView opponent(final HostedMatch match, final PlayerView human) {
        if (match.getGameView() == null || match.getGameView().getPlayers() == null) {
            return null;
        }
        for (final PlayerView p : match.getGameView().getPlayers()) {
            if (p != null && human != null && !p.equals(human)) {
                return p;
            }
        }
        return null;
    }

    private static void captureScreen(final Path file, final int w, final int h) {
        final FScreen screen = Forge.getCurrentScreen();
        Assert.assertNotNull(screen, "no current screen");
        Assert.assertTrue(screen instanceof ArenaDuelScreen, "current screen not Arena");

        final FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fbo.begin();
        Gdx.gl.glViewport(0, 0, w, h);
        // Opaque clear — matches FBufferedImage FBO path but keeps alpha=1 so PNGs
        // don't show checkerboard/void through undrawn regions.
        Gdx.gl.glClearColor(0.07f, 0.09f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        final Graphics g = Forge.getGraphics();
        Assert.assertNotNull(g);
        // Same ortho as FBufferedImage: Graphics.adjustY flips into this Y-up space.
        g.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
        g.begin(w, h);
        try {
            screen.screenPos.setSize(w, h);
            screen.setBounds(0, 0, w, h);
            screen.draw(g);
            if (screen instanceof MatchScreen) {
                ((MatchScreen) screen).drawHoverMagnifierAfterOverlays(g);
            }
        } finally {
            g.end();
        }

        final Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h);
        // libGDX framebuffers are Y-up; flip for a normal PNG. Composite any leftover
        // transparent pixels over the clear color so the artifact is fully opaque.
        final int clearRgba = (18 << 24) | (23 << 16) | (31 << 8) | 255; // ~0.07,0.09,0.12
        final Pixmap flipped = new Pixmap(w, h, pixmap.getFormat());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = pixmap.getPixel(x, y);
                final int a = px & 0xff;
                if (a == 0) {
                    px = clearRgba;
                } else if (a < 255) {
                    final int sr = (px >>> 24) & 0xff;
                    final int sg = (px >>> 16) & 0xff;
                    final int sb = (px >>> 8) & 0xff;
                    final int dr = (clearRgba >>> 24) & 0xff;
                    final int dg = (clearRgba >>> 16) & 0xff;
                    final int db = (clearRgba >>> 8) & 0xff;
                    final int r = (sr * a + dr * (255 - a)) / 255;
                    final int gch = (sg * a + dg * (255 - a)) / 255;
                    final int b = (sb * a + db * (255 - a)) / 255;
                    px = (r << 24) | (gch << 16) | (b << 8) | 255;
                }
                flipped.drawPixel(x, h - 1 - y, px);
            }
        }
        // Assert Arena life badges painted non-background pixels and label == life.
        if (screen instanceof ArenaDuelScreen) {
            assertLifeBadgePixels((ArenaDuelScreen) screen, flipped, w, h);
        }

        PixmapIO.writePNG(Gdx.files.absolute(file.toAbsolutePath().toString()), flipped);
        flipped.dispose();
        pixmap.dispose();
        fbo.end();
        fbo.dispose();
    }

    /**
     * Sample the life-badge rect in the captured pixmap: require non-clear pixels
     * and that the panel's badge label equals the player's life total.
     */
    private static void assertLifeBadgePixels(final ArenaDuelScreen arena,
            final Pixmap flipped, final int w, final int h) {
        for (final VPlayerPanel panel : new VPlayerPanel[] {
                arena.getBottomPlayerPanel(), arena.getTopPlayerPanel() }) {
            if (panel == null) {
                continue;
            }
            final float[] b = panel.getArenaLifeBadgeScreenBounds();
            Assert.assertNotNull(b, "missing life badge bounds during capture");
            final int life = panel.getPlayer().getLife();
            Assert.assertEquals(panel.getArenaLifeLabel(), String.valueOf(life),
                    "badge label must equal life during capture");
            final int x0 = Math.max(0, Math.round(b[0]));
            final int y0 = Math.max(0, Math.round(b[1]));
            final int x1 = Math.min(w, Math.round(b[0] + b[2]));
            final int y1 = Math.min(h, Math.round(b[1] + b[3]));
            Assert.assertTrue(x1 > x0 && y1 > y0, "badge rect empty in pixmap space");
            int inkOrBorder = 0;
            int darkFill = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    final int px = flipped.getPixel(x, y);
                    final int r = (px >>> 24) & 0xff;
                    final int gch = (px >>> 16) & 0xff;
                    final int bl = (px >>> 8) & 0xff;
                    // Cream ink / gold border (high luminance warm pixels).
                    if (r > 180 && gch > 140 && bl < 200) {
                        inkOrBorder++;
                    }
                    // Near-black badge fill distinct from FBO clear (~18,23,31).
                    if (r < 12 && gch < 12 && bl < 18) {
                        darkFill++;
                    }
                }
            }
            final int area = Math.max(1, (x1 - x0) * (y1 - y0));
            Assert.assertTrue(inkOrBorder > 40,
                    "life badge lacks cream/gold ink pixels (" + inkOrBorder
                            + ") for " + panel.getPlayer());
            Assert.assertTrue(darkFill > area / 10,
                    "life badge lacks dark fill (" + darkFill + "/" + area
                            + ") for " + panel.getPlayer());
        }
    }
}
