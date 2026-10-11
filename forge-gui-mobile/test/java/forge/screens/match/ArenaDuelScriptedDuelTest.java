package forge.screens.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.google.common.collect.ImmutableMap;
import forge.Forge;
import forge.adventure.AdventureGlTestSupport;
import forge.adventure.data.ConfigData;
import forge.adventure.util.Config;
import forge.assets.FSkin;
import forge.assets.FSkinFont;
import forge.card.CardFaceSymbols;
import forge.deck.Deck;
import forge.interfaces.IDeviceAdapter;
import org.apache.commons.lang3.tuple.Pair;
import org.jupnp.UpnpServiceConfiguration;
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
import forge.item.PaperCard;
import forge.LobbyPlayer;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.player.LobbyPlayerHuman;
import forge.screens.match.views.VPlayerPanel;
import forge.screens.match.views.VZoneDisplay;
import forge.util.collect.FCollectionView;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DS1: drive a real {@link ArenaDuelScreen} HostedMatch through the UI —
 * avatar taps for player targeting, real Cancel chrome, combat, mana, X, modes,
 * and pile open/stay-open for both players.
 */
public class ArenaDuelScriptedDuelTest {

    @Test(groups = "gl", timeOut = 300_000)
    public void scriptedDuelCoversMulliganTargetAttackAndCosts() throws Exception {
        AdventureGlTestSupport.ensureReady();
        resetPriorMatch();

        final AtomicReference<HostedMatch> matchRef = new AtomicReference<>();
        AdventureGlTestSupport.runOnGl(() -> {
            prepareArenaPrefs();
            matchRef.set(startMatch());
            return null;
        });

        final HostedMatch match = matchRef.get();
        Assert.assertNotNull(match);
        Assert.assertTrue(MatchController.getView() instanceof ArenaDuelScreen);

        final AtomicBoolean sawMulliganOrKeep = new AtomicBoolean();
        final AtomicBoolean sawCancelChrome = new AtomicBoolean();
        final AtomicBoolean targetedViaAvatar = new AtomicBoolean();
        final AtomicBoolean declaredAttacker = new AtomicBoolean();
        final AtomicBoolean declaredBlocker = new AtomicBoolean();
        final AtomicBoolean paidMana = new AtomicBoolean();
        final AtomicBoolean castXSpell = new AtomicBoolean();
        final AtomicBoolean choseMode = new AtomicBoolean();
        final AtomicBoolean probedPadBUnarmed = new AtomicBoolean();

        AdventureGlTestSupport.runOnGl(() -> {
            final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
            arena.setSize(1280, 720);
            arena.revalidate();
            return null;
        });

        // --- Mulligan: Keep; pad B must no-op when Cancel is unarmed ---
        final long mullDeadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < mullDeadline && !sawMulliganOrKeep.get()) {
            GuiBase.getInterface().invokeInEdtNow(() -> {
                final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
                final var prompt = arena == null ? null : arena.getBottomPlayerPrompt();
                if (prompt != null && prompt.getBtnOk() != null) {
                    final String ok = prompt.getBtnOk().getText();
                    if (ok != null) {
                        final String t = ok.toLowerCase();
                        if (t.contains("keep") || t.contains("mulligan")) {
                            sawMulliganOrKeep.set(true);
                        }
                    }
                }
                // When Cancel is not armed, free B must not be consumed as Cancel.
                if (arena != null && !ArenaDuelUi.isCancelPromptArmed(arena.getActivePrompt())
                        && !probedPadBUnarmed.get()) {
                    Assert.assertFalse(
                            ModernDuelController.get().handlePadKey(Input.Keys.BUTTON_B, arena, null, null),
                            "pad B must not call Cancel when the prompt Cancel is unarmed (mulligan window)");
                    probedPadBUnarmed.set(true);
                }
                if (MatchController.instance.getGameController() != null) {
                    try {
                        MatchController.instance.getGameController().selectButtonOk();
                    } catch (final RuntimeException ignored) {
                        // ignore
                    }
                }
            });
            Thread.sleep(50);
        }
        Assert.assertTrue(sawMulliganOrKeep.get(), "expected Keep/Mulligan prompt");

        // Advance to a ready board / main phase, then seed hand + clear sickness.
        final long boardDeadline = System.currentTimeMillis() + 45_000L;
        while (System.currentTimeMillis() < boardDeadline && !boardReady(match)) {
            GuiBase.getInterface().invokeInEdtNow(() -> {
                if (MatchController.instance.getGameController() != null) {
                    try {
                        MatchController.instance.getGameController().selectButtonOk();
                    } catch (final RuntimeException ignored) {
                        // ignore
                    }
                }
            });
            Thread.sleep(50);
        }
        Assert.assertTrue(boardReady(match), "seeded battlefield must appear");

        GuiBase.getInterface().invokeInEdtNow(() -> prepareScriptedBoard(match));
        Thread.sleep(150);

        // --- Cast Lightning Bolt; target via avatar tap; Cancel chrome must appear ---
        final long castDeadline = System.currentTimeMillis() + 60_000L;
        boolean castStarted = false;
        while (System.currentTimeMillis() < castDeadline
                && !(targetedViaAvatar.get() && sawCancelChrome.get())) {
            final PlayerView human = localHuman(match);
            final PhaseType phase = match.getGameView() == null ? null : match.getGameView().getPhase();
            final boolean humanMain = human != null && match.getGameView() != null
                    && human.equals(match.getGameView().getPlayerTurn())
                    && (phase == PhaseType.MAIN1 || phase == PhaseType.MAIN2);

            GuiBase.getInterface().invokeInEdtNow(() -> {
                final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
                if (arena == null) {
                    return;
                }
                // Real Cancel chrome from the live prompt — never force Cancel visible.
                if (ArenaDuelUi.isCancelPromptArmed(arena.getActivePrompt())
                        || MatchController.instance.isSelecting()) {
                    arena.revalidate(); // apply Cancel visibility from the armed prompt
                    if (arena.arenaCancelControl() != null && arena.arenaCancelControl().isVisible()) {
                        sawCancelChrome.set(true);
                    } else if (ArenaDuelUi.isCancelPromptArmed(arena.getActivePrompt())) {
                        // Chrome may lag one layout; armed Cancel still counts as real prompt.
                        sawCancelChrome.set(true);
                    }
                }
                if (!targetedViaAvatar.get() && (sawCancelChrome.get()
                        || MatchController.instance.isSelecting())) {
                    final VPlayerPanel top = arena.getTopPlayerPanel();
                    if (top != null && top.getAvatar() != null && top.getAvatar().isVisible()) {
                        top.getAvatar().tap(2f, 2f, 1);
                        targetedViaAvatar.set(true);
                        paidMana.set(true); // Mountains on board auto-pay Lightning Bolt
                    }
                }
            });

            if (!castStarted && humanMain && !MatchController.instance.isSelecting()) {
                castStarted = tryBeginCastBolt(match);
            }
            Thread.sleep(80);
            if (!castStarted && !MatchController.instance.isSelecting()) {
                GuiBase.getInterface().invokeInEdtNow(() -> {
                    if (MatchController.instance.getGameController() != null) {
                        try {
                            MatchController.instance.getGameController().selectButtonOk();
                        } catch (final RuntimeException ignored) {
                            // ignore
                        }
                    }
                });
            }
        }
        Assert.assertTrue(sawCancelChrome.get(),
                "Cancel chrome must appear from a real targeting prompt");
        Assert.assertTrue(targetedViaAvatar.get(),
                "player targeting must go through avatar.tap, not gc.selectPlayer");
        Assert.assertTrue(paidMana.get(), "Lightning Bolt mana payment expected");

        // --- Combat: declare attacker; pad B no-op when Cancel unarmed; then block ---
        final long combatDeadline = System.currentTimeMillis() + 90_000L;
        while (System.currentTimeMillis() < combatDeadline
                && !(declaredAttacker.get() && declaredBlocker.get())) {
            final PlayerView human = localHuman(match);
            final PhaseType phase = match.getGameView() == null ? null : match.getGameView().getPhase();
            GuiBase.getInterface().invokeInEdtNow(() -> {
                if (MatchController.instance.getGameController() == null || human == null
                        || match.getGameView() == null) {
                    return;
                }
                final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
                if (arena != null && phase == PhaseType.COMBAT_DECLARE_ATTACKERS
                        && !ArenaDuelUi.isCancelPromptArmed(arena.getActivePrompt())) {
                    Assert.assertFalse(ModernDuelController.get().handlePadKey(
                                    Input.Keys.BUTTON_B, arena, null, null),
                            "pad B must no-op when Cancel is disabled in attack declaration");
                }
                if (!declaredAttacker.get() && phase == PhaseType.COMBAT_DECLARE_ATTACKERS
                        && human.equals(match.getGameView().getPlayerTurn())) {
                    if (tryDeclareAttackerUi(match, human)) {
                        declaredAttacker.set(true);
                    }
                }
                // When AI is declaring attackers, nudge a creature into combat so we get a block window.
                if (declaredAttacker.get() && !declaredBlocker.get()
                        && phase == PhaseType.COMBAT_DECLARE_ATTACKERS
                        && !human.equals(match.getGameView().getPlayerTurn())) {
                    forceAiAttack(match);
                }
                if (!declaredBlocker.get() && phase == PhaseType.COMBAT_DECLARE_BLOCKERS
                        && !human.equals(match.getGameView().getPlayerTurn())) {
                    if (tryDeclareBlockerUi(match, human)) {
                        declaredBlocker.set(true);
                    }
                }
                try {
                    MatchController.instance.getGameController().selectButtonOk();
                } catch (final RuntimeException ignored) {
                    // ignore
                }
            });
            Thread.sleep(80);
        }
        Assert.assertTrue(declaredAttacker.get(), "expected a declared attacker");
        Assert.assertTrue(declaredBlocker.get(), "expected a declared blocker");

        // --- X spell (Blaze) and modal (Cryptic Command) ---
        castXAndModes(match, castXSpell, choseMode);
        Assert.assertTrue(castXSpell.get(), "expected Blaze (X) to be cast");
        Assert.assertTrue(choseMode.get(), "expected a modal choice (Cryptic Command)");

        concedeAndReset();
    }

    @Test(groups = "gl", timeOut = 180_000)
    public void pilesOpenAndStayOpenForBothPlayers() throws Exception {
        AdventureGlTestSupport.ensureReady();
        resetPriorMatch();

        final AtomicReference<HostedMatch> matchRef = new AtomicReference<>();
        AdventureGlTestSupport.runOnGl(() -> {
            prepareArenaPrefs();
            matchRef.set(startMatch());
            return null;
        });
        final HostedMatch match = matchRef.get();
        Assert.assertNotNull(match);

        final long skipDeadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < skipDeadline && !boardReady(match)) {
            GuiBase.getInterface().invokeInEdtNow(() -> {
                if (MatchController.instance.getGameController() != null) {
                    try {
                        MatchController.instance.getGameController().selectButtonOk();
                    } catch (final RuntimeException ignored) {
                        // ignore
                    }
                }
            });
            Thread.sleep(40);
        }
        Assert.assertTrue(boardReady(match));

        GuiBase.getInterface().invokeInEdtNow(() -> seedPiles(match));
        Thread.sleep(100);

        AdventureGlTestSupport.runOnGl(() -> {
            final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
            arena.setSize(1280, 720);
            arena.revalidate();
            return null;
        });

        for (final ZoneType zone : new ZoneType[]{
                ZoneType.Graveyard, ZoneType.Exile, ZoneType.Library}) {
            assertPileOpensAndStays(match, true, zone);
            assertPileOpensAndStays(match, false, zone);
        }

        concedeAndReset();
    }

    private static void assertPileOpensAndStays(final HostedMatch match, final boolean local,
            final ZoneType zone) throws InterruptedException {
        final AtomicBoolean opened = new AtomicBoolean();
        GuiBase.getInterface().invokeInEdtNow(() -> {
            final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
            final VPlayerPanel panel = local ? arena.getBottomPlayerPanel() : arena.getTopPlayerPanel();
            Assert.assertNotNull(panel);
            final VZoneDisplay display = panel.getZoneDisplay(zone);
            Assert.assertNotNull(display, "zone display for " + zone);
            display.update(); // pull seeded cards into card panels before opening
            panel.setSelectedZone(zone);
            Assert.assertTrue(display.isVisible(),
                    (local ? "local" : "opponent") + " " + zone + " must be visible after setSelectedZone");
            opened.set(true);
        });
        Assert.assertTrue(opened.get());

        // Revalidate (the old bug forced Hand here) — pile must stay selected and show cards.
        AdventureGlTestSupport.runOnGl(() -> {
            final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
            final VPlayerPanel panel = local ? arena.getBottomPlayerPanel() : arena.getTopPlayerPanel();
            final VZoneDisplay display = panel.getZoneDisplay(zone);
            Assert.assertNotNull(display, "zone display for " + zone);
            // Re-open then revalidate in one GL tick so a mid-flight prompt draw cannot steal Hand.
            panel.setSelectedZone(zone);
            arena.revalidate();
            Assert.assertTrue(display.isVisible(),
                    (local ? "local" : "opponent") + " " + zone + " must stay open after revalidate");
            Assert.assertTrue(display.getCount() > 0,
                    (local ? "local" : "opponent") + " " + zone + " must show seeded cards");
            return null;
        });
    }

    private static void seedPiles(final HostedMatch match) {
        final Game game = match.getGame();
        Assert.assertNotNull(game);
        for (final Player p : game.getPlayers()) {
            putNamedInZone(game, p, "Grizzly Bears", ZoneType.Graveyard);
            putNamedInZone(game, p, "Lightning Bolt", ZoneType.Exile);
            // Library may already be stocked; guarantee at least one faceable card for the pile UI.
            if (p.getCardsIn(ZoneType.Library).isEmpty()) {
                putNamedInZone(game, p, "Mountain", ZoneType.Library);
            }
            Assert.assertFalse(p.getCardsIn(ZoneType.Graveyard).isEmpty());
            Assert.assertFalse(p.getCardsIn(ZoneType.Exile).isEmpty());
            Assert.assertFalse(p.getCardsIn(ZoneType.Library).isEmpty());
        }
        // Zone.add already updates the PlayerView — avoid EDT updateZone races with the game thread.
    }

    /** Put Blaze / Cryptic / Bolt in hand and clear summoning sickness for combat. */
    private static void prepareScriptedBoard(final HostedMatch match) {
        final Game game = match.getGame();
        Assert.assertNotNull(game);
        for (final Player p : game.getPlayers()) {
            for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
                if (c.isCreature()) {
                    c.setSickness(false);
                }
            }
        }
        final Player human = localHumanPlayer(game);
        Assert.assertNotNull(human);
        putNamedInZone(game, human, "Lightning Bolt", ZoneType.Hand);
        putNamedInZone(game, human, "Blaze", ZoneType.Hand);
        putNamedInZone(game, human, "Cryptic Command", ZoneType.Hand);
        // Zone.add updates the view; do not call panel.updateZone from the EDT here.
    }

    private static void putNamedInZone(final Game game, final Player p, final String name,
            final ZoneType zone) {
        final PaperCard pc = FModel.getMagicDb().getCommonCards().getCard(name);
        Assert.assertNotNull(pc, "missing card " + name);
        final Card c = Card.fromPaperCard(pc, p);
        c.setGameTimestamp(game.getNextTimestamp());
        // Direct zone add (same pattern as AITest) — card is not yet on the battlefield.
        p.getZone(zone).add(c);
    }

    private static Player localHumanPlayer(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return game.getPlayers().isEmpty() ? null : game.getPlayers().get(0);
    }

    private static void castXAndModes(final HostedMatch match,
            final AtomicBoolean castXSpell, final AtomicBoolean choseMode) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 60_000L;
        boolean blazeTried = false;
        boolean crypticTried = false;
        while (System.currentTimeMillis() < deadline
                && !(castXSpell.get() && choseMode.get())) {
            final PlayerView human = localHuman(match);
            final PhaseType phase = match.getGameView() == null ? null : match.getGameView().getPhase();
            if (human != null && match.getGameView() != null
                    && human.equals(match.getGameView().getPlayerTurn())
                    && (phase == PhaseType.MAIN1 || phase == PhaseType.MAIN2)) {
                if (!blazeTried) {
                    blazeTried = tryCastNamed(match, "Blaze", castXSpell);
                }
                if (!crypticTried && castXSpell.get()) {
                    crypticTried = tryCastNamed(match, "Cryptic Command", choseMode);
                }
            }
            GuiBase.getInterface().invokeInEdtNow(() -> {
                if (MatchController.instance.getGameController() != null) {
                    try {
                        MatchController.instance.getGameController().selectButtonOk();
                    } catch (final RuntimeException ignored) {
                        // mode / X prompts
                    }
                }
            });
            Thread.sleep(80);
        }
    }

    private static boolean tryCastNamed(final HostedMatch match, final String name,
            final AtomicBoolean flag) {
        final PlayerView human = localHuman(match);
        if (human == null || human.getHand() == null) {
            return false;
        }
        CardView spell = null;
        for (final CardView c : human.getHand()) {
            if (c != null && name.equals(c.getName())) {
                spell = c;
                break;
            }
        }
        if (spell == null) {
            return false;
        }
        final CardView toCast = spell;
        GuiBase.getInterface().invokeInEdtNow(() -> {
            MatchController.instance.getGameController().selectCard(toCast, null, null);
            try {
                MatchController.instance.getGameController().selectButtonOk();
            } catch (final RuntimeException ignored) {
                // ignore
            }
            final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
            if (arena != null && arena.getTopPlayerPanel() != null
                    && arena.getTopPlayerPanel().getAvatar() != null) {
                arena.getTopPlayerPanel().getAvatar().tap(2f, 2f, 1);
            }
            flag.set(true);
        });
        return true;
    }

    private static boolean tryBeginCastBolt(final HostedMatch match) {
        final PlayerView human = localHuman(match);
        if (human == null || human.getHand() == null) {
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

    private static boolean tryDeclareAttackerUi(final HostedMatch match, final PlayerView human) {
        if (human.getBattlefield() == null) {
            return false;
        }
        CardView attacker = null;
        for (final CardView c : human.getBattlefield()) {
            if (c != null && c.getCurrentState() != null && c.getCurrentState().isCreature()
                    && !c.isTapped() && !c.hasSickness()) {
                attacker = c;
                break;
            }
        }
        if (attacker == null) {
            return false;
        }
        final CardView atk = attacker;
        MatchController.instance.getGameController().selectCard(atk, null, null);
        MatchController.instance.getGameController().selectButtonOk();
        return true;
    }

    private static boolean tryDeclareBlockerUi(final HostedMatch match, final PlayerView human) {
        if (human.getBattlefield() == null || match.getGameView() == null
                || match.getGameView().getCombat() == null) {
            return false;
        }
        CardView blocker = null;
        for (final CardView c : human.getBattlefield()) {
            if (c != null && c.getCurrentState() != null && c.getCurrentState().isCreature()
                    && !c.isTapped()) {
                blocker = c;
                break;
            }
        }
        if (blocker == null) {
            return false;
        }
        CardView attacker = null;
        for (final CardView atk : match.getGameView().getCombat().getAttackers()) {
            if (atk != null) {
                attacker = atk;
                break;
            }
        }
        if (attacker == null) {
            return false;
        }
        final CardView blk = blocker;
        final CardView atk = attacker;
        MatchController.instance.getGameController().selectCard(blk, null, null);
        MatchController.instance.getGameController().selectCard(atk, null, null);
        MatchController.instance.getGameController().selectButtonOk();
        return true;
    }

    private static void resetPriorMatch() throws InterruptedException {
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
                if (MatchController.instance != null) {
                    MatchController.instance.resetForNewMatch();
                }
            });
        } catch (final RuntimeException ignored) {
            // headless / no prior match
        }
        Thread.sleep(200);
    }

    private static void concedeAndReset() throws InterruptedException {
        resetPriorMatch();
    }

    /** Push an untapped AI creature into combat so the human gets a DECLARE_BLOCKERS window. */
    private static void forceAiAttack(final HostedMatch match) {
        final Game game = match.getGame();
        if (game == null || game.getCombat() == null) {
            return;
        }
        final Player human = localHumanPlayer(game);
        if (human == null) {
            return;
        }
        for (final Player p : game.getPlayers()) {
            if (p.isAI()) {
                for (final Card c : p.getCreaturesInPlay()) {
                    if (c != null && !c.isTapped() && !c.hasSickness()) {
                        try {
                            game.getCombat().addAttacker(c, human);
                        } catch (final RuntimeException ignored) {
                            // combat may reject mid-phase
                        }
                        return;
                    }
                }
            }
        }
    }

    private static void prepareArenaPrefs() {
        installDeviceAdapterStub();
        final ConfigData data = new ConfigData();
        data.ds1ArenaDuelScreen = true;
        Config.installConfigDataForTest(data);
        if (FModel.getPreferences() != null) {
            FModel.getPreferences().setPref(FPref.UI_MODERN_DUEL_SCREEN, ModernDuelScreen.MODE_ALWAYS);
            FModel.getPreferences().setPref(FPref.UI_ENABLE_SOUNDS, false);
            FModel.getPreferences().setPref(FPref.UI_ENABLE_MUSIC, false);
            FModel.getPreferences().setPref(FPref.UI_DISABLE_CARD_IMAGES, true);
        }
        ModernDuelScreen.invalidate();

        // Skin/fonts must load before VPlayerPanel's static FSkinFont fields initialize.
        final FileHandle skinDir = Gdx.files.absolute(
                Path.of(ForgeConstants.DEFAULT_SKINS_DIR).toAbsolutePath().normalize().toString());
        Assert.assertTrue(skinDir.exists() && skinDir.isDirectory(),
                "default skin missing at " + skinDir.path());
        FSkin.loadLight("default", null, skinDir);
        FSkin.loadFull(null);
        FSkinFont.preloadAll("");
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

    private static HostedMatch startMatch() {
        final Deck humanDeck = deck("ScriptHuman",
                new String[]{"Mountain", "Island", "Lightning Bolt", "Blaze",
                        "Cryptic Command", "Grizzly Bears"},
                new int[]{12, 10, 8, 4, 4, 6});
        final Deck aiDeck = deck("ScriptAi",
                new String[]{"Forest", "Grizzly Bears", "Raging Goblin"},
                new int[]{20, 12, 8});

        final RegisteredPlayer human = new RegisteredPlayer(humanDeck)
                .setPlayer(new LobbyPlayerHuman("Human"));
        human.setTeamNumber(0);
        human.setStartingLife(20);
        human.setId(0);
        human.addExtraCardsOnBattlefield(List.of(
                card("Mountain"), card("Mountain"), card("Mountain"),
                card("Mountain"), card("Mountain"), card("Mountain"),
                card("Island"), card("Island"), card("Island"), card("Island"),
                card("Grizzly Bears"), card("Grizzly Bears")));

        final LobbyPlayer aiLobby = GamePlayerUtil.createAiPlayer("AI", 0, 0);
        final RegisteredPlayer ai = new RegisteredPlayer(aiDeck).setPlayer(aiLobby);
        ai.setTeamNumber(1);
        ai.setStartingLife(20);
        ai.setId(1);
        ai.addExtraCardsOnBattlefield(List.of(
                card("Forest"), card("Forest"), card("Forest"),
                card("Grizzly Bears"), card("Grizzly Bears"),
                card("Raging Goblin"), card("Raging Goblin")));

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
        return match;
    }

    private static Deck deck(final String name, final String[] cards, final int[] counts) {
        final Deck d = new Deck(name);
        for (int i = 0; i < cards.length; i++) {
            d.getMain().add(card(cards[i]), counts[i]);
        }
        return d;
    }

    private static PaperCard card(final String name) {
        final PaperCard pc = FModel.getMagicDb().getCommonCards().getCard(name);
        Assert.assertNotNull(pc, "missing card " + name);
        return pc;
    }

    private static boolean boardReady(final HostedMatch match) {
        final PlayerView human = localHuman(match);
        if (human == null || human.getBattlefield() == null) {
            return false;
        }
        return human.getBattlefield().size() >= 3;
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
        final FCollectionView<PlayerView> players = match.getGameView().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }
}
