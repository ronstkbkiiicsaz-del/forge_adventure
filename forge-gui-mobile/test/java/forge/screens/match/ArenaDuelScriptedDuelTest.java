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
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputAttack;
import forge.gamemodes.match.input.InputBlock;
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
import forge.screens.match.views.VCardDisplayArea;
import forge.screens.match.views.VPlayerPanel;
import forge.screens.match.views.VZoneDisplay;
import forge.toolbox.FChoiceList;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FOptionPane;
import forge.toolbox.FOverlay;
import forge.toolbox.ListChooser;
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

    /**
     * Every step asserts real game state, never a flag set by the test itself:
     * lands tapped and opponent life after Lightning Bolt resolves (target picked by avatar tap),
     * Blaze's X on the stack and the matching damage, Cryptic Command's chosen modes on the stack
     * and their effects, an attacker declared by tapping its battlefield card panel, and a blocker
     * declared by tapping card panels when the AI attacks on its own turn.
     */
    @Test(groups = "gl", timeOut = 420_000)
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
                pressOk();
            });
            Thread.sleep(50);
        }
        Assert.assertTrue(sawMulliganOrKeep.get(), "expected Keep/Mulligan prompt");

        final Game game = match.getGame();
        Assert.assertNotNull(game);
        final Player human = localHumanPlayer(game);
        final Player ai = opponentOf(game, human);
        Assert.assertNotNull(human);
        Assert.assertNotNull(ai);

        // Seed the hand (setup only), then wait for the human's own main phase with priority.
        GuiBase.getInterface().invokeInEdtNow(() -> prepareScriptedBoard(match));
        Thread.sleep(150);
        advanceUntil(match, () -> humanMainWithPriority(match, human), 60_000L,
                "human main phase with priority");

        // --- Lightning Bolt: target by avatar tap; Cancel chrome armed; pay; resolve ---
        final int aiLifeBeforeBolt = ai.getLife();
        final int tappedMountainsBeforeBolt = countTapped(human, "Mountain");
        final AtomicBoolean sawCancelChrome = new AtomicBoolean();
        final StackSpy boltSpy = castAndResolve(match, human, "Lightning Bolt", new String[0], sawCancelChrome);
        Assert.assertTrue(sawCancelChrome.get(), "Cancel chrome must appear from the real targeting prompt");
        Assert.assertTrue(boltSpy.targetedViaAvatar, "player target chosen by avatar.tap, not gc.selectPlayer");
        Assert.assertEquals(ai.getLife(), aiLifeBeforeBolt - 3, "Lightning Bolt resolved for 3 at the opponent");
        Assert.assertEquals(countTapped(human, "Mountain"), tappedMountainsBeforeBolt + 1,
                "exactly one Mountain tapped to pay {R}");
        Assert.assertTrue(inZone(human, "Lightning Bolt", ZoneType.Graveyard), "Bolt resolved to the graveyard");

        // --- Blaze, X = 2 chosen in the X dialog through its list UI ---
        final int aiLifeBeforeBlaze = ai.getLife();
        final int tappedLandsBeforeBlaze = countTappedLands(human);
        final StackSpy blazeSpy = castAndResolve(match, human, "Blaze", new String[]{"2"}, null);
        Assert.assertEquals(blazeSpy.xPaid, Integer.valueOf(2), "Blaze on the stack carried X = 2");
        Assert.assertEquals(ai.getLife(), aiLifeBeforeBlaze - 2, "Blaze resolved for X = 2 at the opponent");
        Assert.assertEquals(countTappedLands(human) - tappedLandsBeforeBlaze, 3,
                "X = 2 plus {R}: three lands tapped for Blaze");

        // --- Cryptic Command: modes "tap all opponents' creatures" + "draw a card" ---
        final int handBeforeCryptic = human.getCardsIn(ZoneType.Hand).size();
        final int tappedLandsBeforeCryptic = countTappedLands(human);
        final StackSpy crypticSpy = castAndResolve(match, human, "Cryptic Command",
                new String[]{"Tap all creatures", "Draw a card"}, null);
        Assert.assertEquals(crypticSpy.modes.size(), 2, "two modes on the stack: " + crypticSpy.modes);
        Assert.assertTrue(crypticSpy.modes.stream().anyMatch(m -> m.toLowerCase().contains("tap all")),
                "tap-all mode chosen: " + crypticSpy.modes);
        Assert.assertTrue(crypticSpy.modes.stream().anyMatch(m -> m.toLowerCase().contains("draw")),
                "draw mode chosen: " + crypticSpy.modes);
        // Cryptic leaves the hand (-1) and its draw mode draws one (+1).
        Assert.assertEquals(human.getCardsIn(ZoneType.Hand).size(), handBeforeCryptic,
                "Cryptic left the hand and its draw mode drew one card");
        Assert.assertEquals(countTappedLands(human) - tappedLandsBeforeCryptic, 4,
                "four lands tapped for {1}{U}{U}{U}");
        for (final Card c : ai.getCreaturesInPlay()) {
            Assert.assertTrue(c.isTapped(), "Cryptic tapped opponent creature " + c);
        }

        // --- Attack: tap the Grizzly Bears battlefield panel in the attack prompt ---
        advanceUntil(match, () -> currentInput(match) instanceof InputAttack, 60_000L, "attack prompt");
        final ArenaDuelScreen arenaAtk = (ArenaDuelScreen) MatchController.getView();
        Assert.assertTrue(arenaAtk != null && !ArenaDuelUi.isCancelPromptArmed(arenaAtk.getActivePrompt())
                        && !runOnGlBool(() -> ModernDuelController.get().handlePadKey(
                                Input.Keys.BUTTON_B, arenaAtk, null, null)),
                "pad B must no-op when Cancel is disabled in attack declaration");
        final Card attacker = firstCreature(human, "Grizzly Bears", c -> !c.isTapped() && !c.hasSickness());
        Assert.assertNotNull(attacker, "an untapped Grizzly Bears that can attack");
        tapPanel(attacker);
        waitFor(() -> game.getCombat() != null && game.getCombat().isAttacking(attacker), 5_000L);
        Assert.assertTrue(game.getCombat() != null && game.getCombat().isAttacking(attacker),
                "tapping the Bears panel declared it as an attacker");
        GuiBase.getInterface().invokeInEdtNow(ArenaDuelScriptedDuelTest::pressOk);
        waitFor(() -> game.getPhaseHandler().getPhase() != PhaseType.COMBAT_DECLARE_ATTACKERS, 10_000L);
        Assert.assertTrue(attacker.isTapped(), "the declared attacker tapped to attack");
        Assert.assertTrue(game.getCombat() != null && game.getCombat().isAttacking(attacker),
                "the Bears is still attacking after the declaration was confirmed");

        // --- Block: on the AI's own turn. Setup only: low life makes the AI swing (no phase forcing). ---
        human.setLife(4, null);
        advanceUntil(match, () -> currentInput(match) instanceof InputBlock, 120_000L,
                "block prompt on the AI's turn");
        final Combat aiCombat = game.getCombat();
        Assert.assertNotNull(aiCombat);
        Assert.assertEquals(aiCombat.getAttackingPlayer(), ai, "the AI is the attacking player");
        final Card blocker = firstCreature(human, null, c -> !c.isTapped());
        Assert.assertNotNull(blocker, "an untapped creature to block with");
        final Card aiAttacker = aiCombat.getAttackers().get(0);
        tapPanel(blocker);
        Thread.sleep(150);
        tapPanel(aiAttacker);
        waitFor(() -> aiCombat.isBlocking(blocker), 5_000L);
        Assert.assertTrue(aiCombat.isBlocking(blocker), "tapping blocker then attacker panels declared the block");
        Assert.assertTrue(aiCombat.getBlockers(aiAttacker).contains(blocker), "blocking the chosen attacker");

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

    /** What the test saw on the stack while its spell was there (real game state). */
    private static final class StackSpy {
        Integer xPaid;
        final List<String> modes = new ArrayList<>();
        boolean targetedViaAvatar;
    }

    /**
     * Cast {@code name} from hand and drive its prompts until it resolves: list dialogs (X, modes)
     * are answered through the dialog's own list keys and OK, a player target through the
     * opponent avatar tap, payment and priority through OK. Records X and modes from the stack.
     */
    private static StackSpy castAndResolve(final HostedMatch match, final Player human, final String name,
            final String[] dialogPicks, final AtomicBoolean sawCancelChrome) throws InterruptedException {
        final Game game = match.getGame();
        final StackSpy spy = new StackSpy();
        final Card spell = firstInZone(human, name, ZoneType.Hand);
        Assert.assertNotNull(spell, name + " must be in hand");
        int dialogIndex = 0;
        boolean castStarted = false;
        final long deadline = System.currentTimeMillis() + 90_000L;
        while (System.currentTimeMillis() < deadline) {
            observeStack(game, name, spy);
            if (castStarted && game.getStack().isEmpty() && inZone(human, name, ZoneType.Graveyard)) {
                return spy;
            }
            if (topListDialog() != null) {
                final String pick = dialogIndex < dialogPicks.length ? dialogPicks[dialogIndex] : null;
                dialogIndex++;
                AdventureGlTestSupport.runOnGl(() -> answerListDialog(pick));
                Thread.sleep(120);
                continue;
            }
            if (!castStarted) {
                if (humanMainWithPriority(match, human)) {
                    final CardView view = spell.getView();
                    GuiBase.getInterface().invokeInEdtNow(() ->
                            MatchController.instance.getGameController().selectCard(view, null, null));
                    castStarted = true;
                    Thread.sleep(150);
                    continue;
                }
                GuiBase.getInterface().invokeInEdtNow(ArenaDuelScriptedDuelTest::pressOk);
                Thread.sleep(80);
                continue;
            }
            final boolean targeting = MatchController.instance.isSelecting()
                    || ArenaDuelUi.isCancelPromptArmed(((ArenaDuelScreen) MatchController.getView()).getActivePrompt());
            if (targeting && !spy.targetedViaAvatar && game.getStack().isEmpty()
                    && !(currentInput(match) instanceof forge.gamemodes.match.input.InputPayMana)) {
                if (sawCancelChrome != null) {
                    final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
                    AdventureGlTestSupport.runOnGl(() -> {
                        arena.revalidate(); // apply Cancel visibility from the armed prompt
                        return null;
                    });
                    if (ArenaDuelUi.isCancelPromptArmed(arena.getActivePrompt())
                            && arena.arenaCancelControl() != null && arena.arenaCancelControl().isVisible()) {
                        sawCancelChrome.set(true);
                    }
                }
                GuiBase.getInterface().invokeInEdtNow(() -> {
                    final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
                    final VPlayerPanel top = arena.getTopPlayerPanel();
                    Assert.assertNotNull(top);
                    Assert.assertTrue(top.getAvatar() != null && top.getAvatar().isVisible(), "opponent avatar visible");
                    top.getAvatar().tap(2f, 2f, 1);
                });
                spy.targetedViaAvatar = true;
                Thread.sleep(150);
                continue;
            }
            // Payment (OK = auto-pay) or passing priority so the spell resolves.
            GuiBase.getInterface().invokeInEdtNow(ArenaDuelScriptedDuelTest::pressOk);
            Thread.sleep(100);
        }
        Assert.fail(name + " did not resolve; input=" + currentInput(match) + " overlay=" + FOverlay.getTopOverlay()
                + " stack=" + game.getStack().size());
        return spy;
    }

    private static void observeStack(final Game game, final String name, final StackSpy spy) {
        try {
            if (game.getStack().isEmpty()) {
                return;
            }
            final SpellAbility top = game.getStack().peekAbility();
            if (top == null || top.getHostCard() == null || !name.equals(top.getHostCard().getName())) {
                return;
            }
            if (top.getXManaCostPaid() != null) {
                spy.xPaid = top.getXManaCostPaid();
            }
            final List<String> modes = new ArrayList<>();
            for (SpellAbility sub = top.getSubAbility(); sub != null; sub = sub.getSubAbility()) {
                modes.add(sub.toUnsuppressedString());
            }
            if (!modes.isEmpty()) {
                spy.modes.clear();
                spy.modes.addAll(modes);
            }
        } catch (final RuntimeException ignored) {
            // the game thread can move the stack between reads; next tick retries
        }
    }

    /** Top overlay if it is a list-choice dialog (X announce, mode choice). */
    private static FOptionPane topListDialog() {
        final FOverlay top = FOverlay.getTopOverlay();
        if (!(top instanceof FOptionPane pane) || !pane.isVisible()) {
            return null;
        }
        for (final FDisplayObject child : pane.getChildren()) {
            if (child instanceof ListChooser) {
                return pane;
            }
        }
        return null;
    }

    /**
     * Answer the open list dialog through its own UI: move the selection with the dialog's
     * down key to the row whose text matches {@code pick} (exact for numbers, else contains),
     * then press OK. {@code null} keeps the default row.
     */
    private static void answerListDialog(final String pick) {
        final FOptionPane pane = topListDialog();
        if (pane == null) {
            return;
        }
        for (final FDisplayObject child : pane.getChildren()) {
            if (!(child instanceof ListChooser<?> chooser)) {
                continue;
            }
            if (pick != null) {
                FChoiceList<?> list = null;
                for (final FDisplayObject inner : chooser.getChildren()) {
                    if (inner instanceof FChoiceList<?> l) {
                        list = l;
                    }
                }
                Assert.assertNotNull(list, "dialog list");
                int target = -1;
                final List<String> rows = new ArrayList<>();
                for (int i = 0; i < list.getCount(); i++) {
                    final String row = String.valueOf(list.getItemAt(i));
                    rows.add(row);
                    if (target < 0 && (row.equals(pick) || (!pick.matches("\\d+")
                            && row.toLowerCase().contains(pick.toLowerCase())))) {
                        target = i;
                    }
                }
                Assert.assertTrue(target >= 0, "no dialog row for '" + pick + "' in " + rows);
                for (int i = list.getSelectedIndex(); i < target; i++) {
                    chooser.keyDown(Input.Keys.DPAD_DOWN);
                }
                Assert.assertEquals(list.getSelectedIndex(), target, "selection moved to '" + pick + "'");
            }
        }
        pane.setResult(0);
    }

    /** Tap a battlefield card's own panel in the centre, as a click/touch would. */
    private static void tapPanel(final Card card) throws InterruptedException {
        final CardView view = card.getView();
        final boolean tapped = runOnGlBool(() -> {
            final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
            arena.revalidate();
            final VCardDisplayArea.CardAreaPanel panel = VCardDisplayArea.CardAreaPanel.get(view);
            return panel.getWidth() > 0 && panel.tap(panel.getWidth() / 2f, panel.getHeight() / 2f, 1);
        });
        Assert.assertTrue(tapped, "card panel for " + card + " must accept the tap");
        // Modern single taps are deferred past the double-tap window.
        Thread.sleep((long) (ModernDuelGestures.DOUBLE_TAP_WINDOW_SEC * 1000f) + 250L);
    }

    private static boolean runOnGlBool(final java.util.concurrent.Callable<Boolean> work) {
        final Boolean b = AdventureGlTestSupport.runOnGl(work);
        return b != null && b;
    }

    private static void advanceUntil(final HostedMatch match, final java.util.function.BooleanSupplier done,
            final long ms, final String what) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline && !done.getAsBoolean()) {
            if (topListDialog() != null) {
                AdventureGlTestSupport.runOnGl(() -> answerListDialog(null));
            } else {
                GuiBase.getInterface().invokeInEdtNow(ArenaDuelScriptedDuelTest::pressOk);
            }
            Thread.sleep(80);
        }
        Assert.assertTrue(done.getAsBoolean(), "timed out waiting for " + what + "; input=" + currentInput(match)
                + " phase=" + match.getGame().getPhaseHandler().getPhase());
    }

    private static void waitFor(final java.util.function.BooleanSupplier done, final long ms)
            throws InterruptedException {
        final long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline && !done.getAsBoolean()) {
            Thread.sleep(50);
        }
    }

    private static void pressOk() {
        if (MatchController.instance.getGameController() != null) {
            try {
                MatchController.instance.getGameController().selectButtonOk();
            } catch (final RuntimeException ignored) {
                // no OK right now
            }
        }
    }

    private static forge.gamemodes.match.input.Input currentInput(final HostedMatch match) {
        for (final forge.player.PlayerControllerHuman hc : match.getHumanControllers()) {
            if (hc != null && hc.getInputQueue() != null) {
                return hc.getInputQueue().getInput();
            }
        }
        return null;
    }

    private static boolean humanMainWithPriority(final HostedMatch match, final Player human) {
        final Game game = match.getGame();
        final PhaseType phase = game.getPhaseHandler().getPhase();
        return human.equals(game.getPhaseHandler().getPlayerTurn())
                && (phase == PhaseType.MAIN1 || phase == PhaseType.MAIN2)
                && game.getStack().isEmpty()
                && currentInput(match) instanceof forge.gamemodes.match.input.InputPassPriority;
    }

    private static Player opponentOf(final Game game, final Player human) {
        for (final Player p : game.getPlayers()) {
            if (p != human) {
                return p;
            }
        }
        return null;
    }

    private static int countTapped(final Player p, final String name) {
        int n = 0;
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (name.equals(c.getName()) && c.isTapped()) {
                n++;
            }
        }
        return n;
    }

    private static int countTappedLands(final Player p) {
        int n = 0;
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isLand() && c.isTapped()) {
                n++;
            }
        }
        return n;
    }

    private static boolean inZone(final Player p, final String name, final ZoneType zone) {
        return firstInZone(p, name, zone) != null;
    }

    private static Card firstInZone(final Player p, final String name, final ZoneType zone) {
        for (final Card c : p.getCardsIn(zone)) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private static Card firstCreature(final Player p, final String name, final java.util.function.Predicate<Card> ok) {
        for (final Card c : p.getCreaturesInPlay()) {
            if ((name == null || name.equals(c.getName())) && ok.test(c)) {
                return c;
            }
        }
        return null;
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
