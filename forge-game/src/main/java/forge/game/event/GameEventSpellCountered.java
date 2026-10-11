package forge.game.event;

import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;

/**
 * Fired when a spell on the stack is successfully countered (not merely removed
 * by resolving or fizzling). Distinct from {@link GameEventSpellRemovedFromStack},
 * which also fires for normal resolution.
 */
public record GameEventSpellCountered(SpellAbilityView countered, SpellAbilityView cause) implements GameEvent {

    public GameEventSpellCountered(SpellAbility countered, SpellAbility cause) {
        this(SpellAbilityView.get(countered), SpellAbilityView.get(cause));
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return "Countered " + countered + (cause != null ? " (by " + cause + ")" : "");
    }
}
