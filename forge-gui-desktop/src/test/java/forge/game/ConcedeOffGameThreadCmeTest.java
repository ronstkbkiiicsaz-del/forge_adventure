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
 * Concede must run on a game thread and must not walk live zone lists while
 * another thread still holds {@link Game}'s mutation lock. Hot-path
 * {@link Game#forEachCardInGame} stays a live walk (no zone copies).
 */
public class ConcedeOffGameThreadCmeTest extends AITest {

    private static final class HumanLobby extends LobbyPlayerHuman {
        private final AtomicReference<String> concedeThread = new AtomicReference<>();

        HumanLobby(final String name) {
            super(name);
        }

        AtomicReference<String> concedeThread() {
            return concedeThread;
        }

        @Override
        public Player createIngamePlayer(final Game game, final int id) {
            final Player player = new Player(getName(), game, id);
            final PlayerControllerHuman ctrl = new PlayerControllerHuman(game, player, this) {
                @Override
                public void concede() {
                    if (ThreadUtil.isGameThread()) {
                        concedeThread.compareAndSet(null, Thread.currentThread().getName());
                    }
                    super.concede();
                }
            };
            final IGuiGame gui = Mockito.mock(IGuiGame.class, Mockito.RETURNS_DEFAULTS);
            Mockito.when(gui.isLibgdxPort()).thenReturn(true);
            ctrl.setGui(gui);
            player.setFirstController(ctrl);
            return player;
        }
    }

    private Game createHumanVsAiGame() {
        final List<RegisteredPlayer> players = Lists.newArrayList();
        final Deck deck = new Deck();
        final HumanLobby humanLobby = new HumanLobby("human");
        players.add(new RegisteredPlayer(deck).setPlayer(new LobbyPlayerAi("ai", null)));
        players.add(new RegisteredPlayer(deck).setPlayer(humanLobby));
        final GameRules rules = new GameRules(GameType.Constructed);
        final Match match = new Match(rules, players, "ConcedeCme");
        final Game game = new Game(players, rules, match);
        final Player human = find(game, "human");
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, human);
        game.getPhaseHandler().onStackResolved();
        return game;
    }

    private static HumanLobby humanLobby(final Game game) {
        return (HumanLobby) find(game, "human").getLobbyPlayer();
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
    public void forEachCardInGameWalksLiveZoneLists() {
        final Game game = createHumanVsAiGame();
        final Player human = find(game, "human");
        // Three cards: removing the current element from a two-card ArrayList
        // makes the iterator's cursor land on the new size, so hasNext() stops
        // without a fail-fast check. A third card forces next() to notice.
        addCardToZone("Mountain", human, ZoneType.Hand);
        addCardToZone("Mountain", human, ZoneType.Hand);
        addCardToZone("Mountain", human, ZoneType.Hand);

        try {
            game.forEachCardInGame(c -> {
                if (c.getZone() != null && c.getZone().is(ZoneType.Hand)) {
                    c.getZone().remove(c);
                }
                return true;
            });
            Assert.fail("forEachCardInGame must iterate the live zone list");
        } catch (final java.util.ConcurrentModificationException expected) {
            // ArrayList fail-fast: a snapshot walk would have finished instead.
        }
    }

    @Test(timeOut = 30_000)
    public void concedeWaitsForMutationLockAndRunsOnGameThread() throws Exception {
        Assert.assertFalse(ThreadUtil.isGameThread(), "caller must not already be a game thread");

        final Game game = createHumanVsAiGame();
        final Player human = find(game, "human");
        final HumanLobby lobby = humanLobby(game);
        final PlayerControllerHuman ctrl = (PlayerControllerHuman) human.getController();
        addCardToZone("Mountain", human, ZoneType.Library);
        addCardToZone("Mountain", human, ZoneType.Hand);
        addCardToZone("Mountain", human, ZoneType.Battlefield);

        final AtomicBoolean release = new AtomicBoolean();
        final CountDownLatch holding = new CountDownLatch(1);
        final AtomicReference<Throwable> holderError = new AtomicReference<>();
        final Thread holder = new Thread(() -> {
            game.acquireMutationLock();
            try {
                holding.countDown();
                while (!release.get()) {
                    bumpZone(human, ZoneType.Library);
                    game.forEachCardInGame(c -> true);
                    Thread.sleep(5);
                }
            } catch (final Throwable t) {
                holderError.set(t);
            } finally {
                release.set(true);
                game.releaseMutationLock();
            }
        }, "mutation-holder");
        holder.setDaemon(true);
        holder.start();
        Assert.assertTrue(holding.await(5, TimeUnit.SECONDS), "holder did not acquire the mutation lock");

        final AtomicReference<Throwable> concedeError = new AtomicReference<>();
        final Thread conceder = new Thread(() -> {
            try {
                ctrl.concede();
            } catch (final Throwable t) {
                concedeError.set(t);
            }
        }, "off-thread-concede");
        conceder.setDaemon(true);
        conceder.start();

        final long deadline = System.currentTimeMillis() + 5_000;
        while (lobby.concedeThread().get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        final String startedOn = lobby.concedeThread().get();
        Assert.assertNotNull(startedOn, "concede body never reached a game thread");
        Assert.assertTrue(startedOn.startsWith("Game"),
                "concede must run on a game thread, was " + startedOn);
        Assert.assertNotEquals(startedOn, Thread.currentThread().getName());
        Assert.assertFalse(human.hasLost() || human.conceded(),
                "concede must not apply while the mutation lock is still held");
        Assert.assertTrue(conceder.isAlive(), "concede returned before the mutation lock was released");

        release.set(true);
        conceder.join(10_000);
        holder.join(5_000);

        if (holderError.get() != null) {
            throw new AssertionError("zone walk while holding the mutation lock failed", holderError.get());
        }
        if (concedeError.get() != null) {
            throw new AssertionError("off-thread concede failed", concedeError.get());
        }
        Assert.assertFalse(conceder.isAlive(), "concede did not finish after the mutation lock was released");
        Assert.assertTrue(human.hasLost() || human.conceded(), "human must lose once concede runs");
        Assert.assertTrue(game.isGameOver(), "caller must observe game-over after concede returns");
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
