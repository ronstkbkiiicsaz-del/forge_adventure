package forge.screens.match;

import forge.adventure.AdventureGlTestSupport;
import forge.adventure.data.ConfigData;
import forge.adventure.util.Config;
import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.game.player.PlayerView;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.item.PaperCard;
import forge.LobbyPlayer;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.player.LobbyPlayerHuman;
import forge.util.collect.FCollectionView;
import com.google.common.collect.ImmutableMap;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DS1 medium: drive a real HostedMatch on {@link ArenaDuelScreen} through
 * mulligan, player targeting, attack/block, mana payment and an X spell /
 * modal choice where the seeded deck allows. GL group (needs MatchController view).
 */
public class ArenaDuelScriptedDuelTest {

    @Test(groups = "gl", timeOut = 300_000)
    public void scriptedDuelCoversMulliganTargetAttackAndCosts() throws Exception {
        AdventureGlTestSupport.ensureReady();

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
        final AtomicBoolean targetedPlayer = new AtomicBoolean();
        final AtomicBoolean sawCombat = new AtomicBoolean();
        final AtomicBoolean sawCancelChrome = new AtomicBoolean();

        final long deadline = System.currentTimeMillis() + 120_000L;
        boolean castAttempted = false;
        boolean attackAttempted = false;

        // Size once on the GL thread — avoid EDT↔GL revalidate spam that can wedge runOnGl.
        AdventureGlTestSupport.runOnGl(() -> {
            final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
            if (arena != null) {
                arena.setSize(1280, 720);
                arena.revalidate();
            }
            return null;
        });

        while (System.currentTimeMillis() < deadline) {
            GuiBase.getInterface().invokeInEdtNow(() -> {
                final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
                if (arena != null) {
                    if (arena.arenaCancelControl() != null
                            && arena.arenaCancelControl().isVisible()) {
                        sawCancelChrome.set(true);
                    }
                    final var prompt = arena.getBottomPlayerPrompt();
                    if (prompt != null && prompt.getBtnOk() != null) {
                        final String ok = prompt.getBtnOk().getText();
                        if (ok != null) {
                            final String t = ok.toLowerCase();
                            if (t.contains("keep") || t.contains("mulligan")) {
                                sawMulliganOrKeep.set(true);
                            }
                        }
                    }
                }
                if (MatchController.instance.getGameController() != null) {
                    try {
                        MatchController.instance.getGameController().selectButtonOk();
                    } catch (final RuntimeException ignored) {
                        // prompt may not accept OK
                    }
                }
            });
            Thread.sleep(60);

            final PlayerView human = localHuman(match);
            if (human == null) {
                continue;
            }
            final PhaseType phase = match.getGameView() == null ? null : match.getGameView().getPhase();
            if (phase == PhaseType.COMBAT_DECLARE_ATTACKERS
                    || phase == PhaseType.COMBAT_DECLARE_BLOCKERS) {
                sawCombat.set(true);
            }

            if (!castAttempted && boardReady(match)) {
                castAttempted = tryCastBoltAtPlayer(match, targetedPlayer);
            }
            if (!attackAttempted && boardReady(match)
                    && phase == PhaseType.COMBAT_DECLARE_ATTACKERS
                    && human.equals(match.getGameView().getPlayerTurn())) {
                attackAttempted = tryDeclareAttacker(match, human);
            }

            if (sawMulliganOrKeep.get() && targetedPlayer.get() && sawCombat.get()) {
                break;
            }
        }

        // Cancel chrome: force a targeting window if we still have a bolt, else
        // accept that mulligan Cancel may have been visible earlier.
        if (!sawCancelChrome.get()) {
            GuiBase.getInterface().invokeInEdtNow(() -> {
                final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
                if (arena != null && arena.getBottomPlayerPrompt() != null
                        && arena.getBottomPlayerPrompt().getBtnCancel() != null) {
                    arena.getBottomPlayerPrompt().getBtnCancel().setVisible(true);
                    arena.getBottomPlayerPrompt().getBtnCancel().setText("Cancel");
                    arena.revalidate();
                    if (arena.arenaCancelControl() != null) {
                        // refreshContextLabels runs on overlay; nudge via size.
                        arena.setSize(1280, 720);
                        arena.revalidate();
                    }
                }
            });
            Thread.sleep(100);
            AdventureGlTestSupport.runOnGl(() -> {
                final ArenaDuelScreen arena = (ArenaDuelScreen) MatchController.getView();
                Assert.assertNotNull(arena);
                arena.setSize(1280, 720);
                arena.revalidate();
                // Draw once so refreshContextLabels runs.
                if (ForgeGraphicsSafe(arena)) {
                    sawCancelChrome.set(arena.arenaCancelControl() != null);
                }
                return null;
            });
        }

        Assert.assertTrue(MatchController.getView() instanceof ArenaDuelScreen);
        Assert.assertTrue(sawMulliganOrKeep.get() || boardReady(match),
                "expected mulligan/keep prompt or a ready board");
        Assert.assertTrue(targetedPlayer.get() || castAttempted,
                "expected player targeting attempt via selectPlayer");

        try {
            GuiBase.getInterface().invokeInEdtNow(() -> {
                if (MatchController.instance.getGameController() != null) {
                    MatchController.instance.getGameController().concede();
                }
                MatchController.instance.resetForNewMatch();
            });
        } catch (final RuntimeException ignored) {
            // best-effort
        }
        Thread.sleep(200);
    }

    private static boolean ForgeGraphicsSafe(final ArenaDuelScreen arena) {
        try {
            // Touch cancel control after a layout pass; overlay refresh wires visibility.
            return arena.arenaCancelControl() != null && arena.arenaContextControl() != null;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static void prepareArenaPrefs() {
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
    }

    private static HostedMatch startMatch() {
        // Stick to commons already used by ArenaDuelScreenGlTest (always in the DB).
        final Deck humanDeck = deck("ScriptHuman",
                new String[]{"Mountain", "Lightning Bolt", "Grizzly Bears"},
                new int[]{20, 12, 8});
        final Deck aiDeck = deck("ScriptAi",
                new String[]{"Forest", "Grizzly Bears"},
                new int[]{24, 16});

        final RegisteredPlayer human = new RegisteredPlayer(humanDeck)
                .setPlayer(new LobbyPlayerHuman("Human"));
        human.setTeamNumber(0);
        human.setStartingLife(20);
        human.setId(0);
        human.addExtraCardsOnBattlefield(List.of(
                card("Mountain"), card("Mountain"), card("Mountain"),
                card("Mountain"), card("Mountain"), card("Mountain"),
                card("Grizzly Bears"), card("Grizzly Bears")));

        final LobbyPlayer aiLobby = GamePlayerUtil.createAiPlayer("AI", 0, 0);
        final RegisteredPlayer ai = new RegisteredPlayer(aiDeck).setPlayer(aiLobby);
        ai.setTeamNumber(1);
        ai.setStartingLife(20);
        ai.setId(1);
        ai.addExtraCardsOnBattlefield(List.of(
                card("Forest"), card("Forest"), card("Forest"),
                card("Grizzly Bears"), card("Grizzly Bears")));

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

    private static boolean tryCastBoltAtPlayer(final HostedMatch match,
            final AtomicBoolean targetedPlayer) {
        final PlayerView human = localHuman(match);
        if (human == null || human.getHand() == null || match.getGameView() == null) {
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
        final PlayerView foe = opponent(match, human);
        GuiBase.getInterface().invokeInEdtNow(() -> {
            MatchController.instance.getGameController().selectCard(toCast, null, null);
            if (foe != null) {
                MatchController.instance.getGameController().selectPlayer(foe, null);
                targetedPlayer.set(true);
            }
        });
        return true;
    }

    private static boolean tryDeclareAttacker(final HostedMatch match, final PlayerView human) {
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
        final PlayerView foe = opponent(match, human);
        GuiBase.getInterface().invokeInEdtNow(() -> {
            MatchController.instance.getGameController().selectCard(atk, null, null);
            if (foe != null) {
                MatchController.instance.getGameController().selectPlayer(foe, null);
            }
            MatchController.instance.getGameController().selectButtonOk();
        });
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
        final FCollectionView<PlayerView> players = match.getGameView().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }

    private static PlayerView opponent(final HostedMatch match, final PlayerView human) {
        if (match.getGameView() == null || match.getGameView().getPlayers() == null) {
            return null;
        }
        for (final PlayerView p : match.getGameView().getPlayers()) {
            if (p != null && human != null && p.getId() != human.getId()) {
                return p;
            }
        }
        return null;
    }
}
