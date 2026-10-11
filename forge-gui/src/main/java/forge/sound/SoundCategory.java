package forge.sound;

/**
 * Playback categories for volume routing. Music stays on {@code UI_VOL_MUSIC};
 * duel/overworld SFX use EFFECTS; menus and toasts use UI; overworld biome loops
 * are treated as AMBIENCE (can be muted independently).
 */
public enum SoundCategory {
    EFFECTS,
    UI,
    AMBIENCE
}
