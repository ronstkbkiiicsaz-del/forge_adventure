package forge.sound;

import forge.game.event.GameEvent;
import forge.gui.events.UiEvent;

/**
 * Optional playback policy registered by the client (e.g. Ascendant AU1).
 * When unset, {@link SoundSystem} keeps stock behaviour.
 */
public interface ISoundPlaybackHooks {

    /**
     * Handle a game event instead of the stock {@link EventVisualizer} path.
     * @return true if the event was fully handled (stock sound suppressed)
     */
    default boolean handleGameEvent(GameEvent evt) {
        return false;
    }

    /**
     * Handle a UI event instead of the stock visualizer path.
     * @return true if the event was fully handled
     */
    default boolean handleUiEvent(UiEvent evt) {
        return false;
    }

    /**
     * Volume scale 0..1 for a category after preferences are applied by the hooks
     * implementation. Returning a negative value means "use the stock effects volume".
     */
    default float categoryVolume(SoundCategory category) {
        return -1f;
    }

    /** When true, overworld biome / ambience music should stay silent. */
    default boolean muteAmbience() {
        return false;
    }

    /**
     * Called when a match/game ends so clients can drop per-match burst state
     * without unregistering hooks for the next Adventure duel.
     */
    default void onMatchEnded() {
    }
}
