package forge.game;

import com.google.common.collect.Lists;
import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.player.LobbyPlayerHuman;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression: a seat that concedes after {@link Match#createGame()} but before
 * {@link Match#startGame(Game)} must not throw {@link IndexOutOfBoundsException}
 * in {@code prepareAllZones} (registered seats stay fixed; {@code getPlayers()}
 * shrinks when {@link Game#onPlayerLost} runs).
 */
public class MatchPrepareAllZonesEarlyConcedeTest extends AITest {

    @Test(timeOut = 60_000)
    public void prepareAllZonesIndexesRegisteredPlayersAfterPreStartConcede() throws Exception {
        final Deck deck = minimalDeck();
        final List<RegisteredPlayer> seats = Lists.newArrayList();
        seats.add(new RegisteredPlayer(deck).setPlayer(new LobbyPlayerHuman("host")).setTeamNumber(0));
        seats.add(new RegisteredPlayer(new Deck(deck)).setPlayer(new LobbyPlayerHuman("guest")).setTeamNumber(0));
        seats.add(new RegisteredPlayer(new Deck(deck)).setPlayer(new LobbyPlayerAi("enemy", null)).setTeamNumber(1));

        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setGamesPerMatch(1);
        final Match match = new Match(rules, seats, "EarlyConcede");
        final Game game = match.createGame();

        Assert.assertEquals(game.getRegisteredPlayers().size(), 3);
        Assert.assertEquals(game.getPlayers().size(), 3);

        final Player guest = find(game, "guest");
        guest.concede();
        game.getAction().checkGameOverCondition();

        Assert.assertTrue(guest.hasLost() || guest.conceded(), "guest seat conceded before start");
        Assert.assertEquals(game.getRegisteredPlayers().size(), 3, "registered seats stay fixed");
        Assert.assertEquals(game.getPlayers().size(), 2,
                "ingame players shrink after onPlayerLost — the bug indexed this list");

        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread t = new Thread(() -> {
            try {
                match.startGame(game, () -> {
                    for (final Player p : new ArrayList<>(game.getRegisteredPlayers())) {
                        if (!p.hasLost()) {
                            p.concede();
                        }
                    }
                    game.getAction().checkGameOverCondition();
                });
            } catch (final Throwable e) {
                error.set(e);
            } finally {
                finished.countDown();
            }
        }, "early-concede-start");
        t.setDaemon(true);
        t.start();

        Assert.assertTrue(finished.await(45, TimeUnit.SECONDS), "startGame timed out");
        if (error.get() != null) {
            throw new AssertionError(
                    "prepareAllZones must index registered players when a seat conceded pre-start",
                    error.get());
        }
        Assert.assertTrue(game.isGameOver());
    }

    private static Player find(final Game game, final String name) {
        for (final Player p : game.getRegisteredPlayers()) {
            if (name.equals(p.getName())) {
                return p;
            }
        }
        throw new IllegalStateException("missing " + name);
    }

    private static Deck minimalDeck() {
        final Deck deck = new Deck("EarlyConcede");
        PaperCard mountain = FModel.getMagicDb().getCommonCards().getCard("Mountain");
        if (mountain == null) {
            forge.StaticData.instance().attemptToLoadCard("Mountain");
            mountain = FModel.getMagicDb().getCommonCards().getCard("Mountain");
        }
        Assert.assertNotNull(mountain, "Mountain must resolve from card DB");
        for (int i = 0; i < 40; i++) {
            deck.getMain().add(mountain);
        }
        return deck;
    }
}
