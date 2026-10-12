package forge.game;

import com.google.common.collect.Lists;
import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gui.interfaces.IGuiGame;
import forge.player.LobbyPlayerHuman;
import forge.player.PlayerControllerHuman;
import forge.util.ThreadUtil;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression: conceding off the game thread while the game thread mutates zones
 * must not throw {@link java.util.ConcurrentModificationException} from
 * {@link Game#onPlayerLost} → {@link Game#getCardsInGame}.
 *
 * <p>Fails before the fix when {@link PlayerControllerHuman#concede()} runs
 * {@code checkGameOverCondition} on the caller thread against live zone lists.
 * Passes when concede is marshalled to the game thread and/or {@code getCardsInGame}
 * snapshots zones.
 */
public class ConcedeOffGameThreadCmeTest extends AITest {

    private static final class HumanLobby extends LobbyPlayerHuman {
        HumanLobby(final String name) {
            super(name);
        }

        @Override
        public Player createIngamePlayer(final Game game, final int id) {
            final Player player = new Player(getName(), game, id);
            final PlayerControllerHuman ctrl = new PlayerControllerHuman(game, player, this);
            final IGuiGame gui = Mockito.mock(IGuiGame.class, Mockito.RETURNS_DEFAULTS);
            Mockito.when(gui.isLibgdxPort()).thenReturn(true);
            ctrl.setGui(gui);
            player.setFirstController(ctrl);
            return player;
        }
    }

    private Game createHumanVsAiGame() {
        final List<RegisteredPlayer> players = Lists.newArrayList();
        final Deck d1 = new Deck();
        players.add(new RegisteredPlayer(d1).setPlayer(new LobbyPlayerAi("ai", null)));
        players.add(new RegisteredPlayer(d1).setPlayer(new HumanLobby("human")));
        final GameRules rules = new GameRules(GameType.Constructed);
        final Match match = new Match(rules, players, "ConcedeCme");
        final Game game = new Game(players, rules, match);
        final Player human = find(game, "human");
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, human);
        game.getPhaseHandler().onStackResolved();
        return game;
    }

    private static Player find(final Game game, final String name) {
        for (final Player p : game.getRegisteredPlayers()) {
            if (name.equals(p.getName())) {
                return p;
            }
        }
        throw new IllegalStateException("missing player " + name);
    }

    @Test(timeOut = 30_000)
    public void concedeWhileGameThreadMutatesZonesDoesNotThrowCme() throws Exception {
        Assert.assertFalse(ThreadUtil.isGameThread(), "test thread must not be a Game-* pool thread");

        final Game game = createHumanVsAiGame();
        final Player human = find(game, "human");
        final PlayerControllerHuman ctrl = (PlayerControllerHuman) human.getController();

        // Enough cards that a live for-each is likely to collide with remove/add.
        for (int i = 0; i < 40; i++) {
            addCardToZone("Mountain", human, ZoneType.Library);
            addCardToZone("Mountain", human, ZoneType.Hand);
            addCardToZone("Mountain", human, ZoneType.Battlefield);
        }

        final AtomicBoolean mutate = new AtomicBoolean(true);
        final CountDownLatch mutating = new CountDownLatch(1);
        final AtomicReference<Throwable> mutatorError = new AtomicReference<>();
        final CountDownLatch mutatorDone = new CountDownLatch(1);

        ThreadUtil.invokeInGameThread(() -> {
            try {
                mutating.countDown();
                while (mutate.get()) {
                    bumpZone(human, ZoneType.Library);
                    bumpZone(human, ZoneType.Hand);
                    bumpZone(human, ZoneType.Battlefield);
                    // Hot-path style live walk (must stay allocation-free / live).
                    game.forEachCardInGame(c -> true);
                }
            } catch (final Throwable t) {
                mutatorError.set(t);
            } finally {
                mutatorDone.countDown();
            }
        });

        Assert.assertTrue(mutating.await(5, TimeUnit.SECONDS), "game-thread mutator did not start");
        // Let the mutator run before conceding off-thread.
        Thread.sleep(50);

        final AtomicReference<Throwable> concedeError = new AtomicReference<>();
        final Thread conceder = new Thread(() -> {
            try {
                for (int i = 0; i < 8; i++) {
                    if (human.hasLost() || human.conceded()) {
                        break;
                    }
                    ctrl.concede();
                    Thread.sleep(5);
                }
            } catch (final Throwable t) {
                concedeError.set(t);
            }
        }, "off-thread-concede");
        conceder.setDaemon(true);
        conceder.start();
        conceder.join(10_000);

        final long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && !human.hasLost() && !human.conceded()) {
            Thread.sleep(20);
        }
        mutate.set(false);
        Assert.assertTrue(mutatorDone.await(5, TimeUnit.SECONDS), "mutator did not stop");

        if (concedeError.get() != null) {
            throw new AssertionError("off-thread concede failed", concedeError.get());
        }
        if (mutatorError.get() != null) {
            throw new AssertionError("game-thread mutator failed", mutatorError.get());
        }
        Assert.assertTrue(human.hasLost() || human.conceded(),
                "human must lose after off-thread concede while zones mutate");
    }

    private static void bumpZone(final Player p, final ZoneType zone) {
        final List<Card> cards = new ArrayList<>(p.getZone(zone).getCards());
        if (cards.isEmpty()) {
            return;
        }
        final Card c = cards.get(0);
        p.getZone(zone).remove(c);
        p.getZone(zone).add(c);
    }
}
