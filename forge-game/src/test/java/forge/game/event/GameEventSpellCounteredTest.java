package forge.game.event;

import forge.game.spellability.SpellAbilityView;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Engine contract for AU1: successfully countered spells raise {@link GameEventSpellCountered},
 * visited through {@link IGameEventVisitor}.
 */
public class GameEventSpellCounteredTest {

    @Test
    public void visitorReceivesSpellCounteredEvent() {
        SpellAbilityView none = null;
        GameEventSpellCountered event = new GameEventSpellCountered(none, none);
        AtomicReference<GameEventSpellCountered> seen = new AtomicReference<>();
        event.visit(new IGameEventVisitor.Base<Void>() {
            @Override
            public Void visit(GameEventSpellCountered e) {
                seen.set(e);
                return null;
            }
        });
        Assert.assertSame(seen.get(), event);
        Assert.assertTrue(event.toString().contains("Countered"));
    }
}
