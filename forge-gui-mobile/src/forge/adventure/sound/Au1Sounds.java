package forge.adventure.sound;

import forge.adventure.data.ConfigData;
import forge.adventure.util.Config;
import forge.card.ColorSet;
import forge.card.mana.ManaCost;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.sound.ISoundPlaybackHooks;
import forge.sound.SoundCategory;
import forge.sound.SoundSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Ascendant AU1 sound API. Safe to call from DS1 animation code, adventure scenes,
 * and duel event enrichment. No-ops when AU1 is off; missing assets fail safe.
 *
 * <p>Co-op: audio is local presentation only (each client plays its own clips). No wire change.
 */
public final class Au1Sounds {
    private static final List<String> recordedPlays = new ArrayList<>();
    private static boolean recording;

    private Au1Sounds() {
    }

    /** True when Ascendant rules and {@code au1SoundOverhaul} are on. */
    public static boolean enabled() {
        if (!Config.ascendant()) {
            return false;
        }
        ConfigData data = Config.instance().getConfigData();
        return data != null && data.au1SoundOverhaul;
    }

    /** Register or clear {@link SoundSystem} hooks from the current config. */
    public static void refreshRegistration() {
        if (enabled()) {
            SoundSystem.setPlaybackHooks(Au1SoundEnrichment.INSTANCE);
        } else {
            ISoundPlaybackHooks current = SoundSystem.getPlaybackHooks();
            if (current instanceof Au1SoundEnrichment) {
                SoundSystem.setPlaybackHooks(null);
            }
        }
    }

    // ---- Test helpers (real behaviour; no source greps) ----

    public static void setRecording(boolean on) {
        recording = on;
        recordedPlays.clear();
    }

    public static List<String> drainRecordedPlays() {
        List<String> out = new ArrayList<>(recordedPlays);
        recordedPlays.clear();
        return out;
    }

    public static List<String> peekRecordedPlays() {
        return Collections.unmodifiableList(recordedPlays);
    }

    // ---- Core play ----

    public static void play(Au1SoundId id) {
        if (id == null || !enabled()) {
            return;
        }
        if (recording) {
            recordedPlays.add(id.getResourceFileName());
        }
        try {
            SoundSystem.instance.play(id.getResourceFileName(), false, id.getCategory());
        } catch (Exception e) {
            System.err.println("AU1: failed to play " + id + ": " + e.getMessage());
        }
    }

    /** Resolve the on-disk file for a catalog id (adventure dirs first). Null if missing. */
    public static File resolveFile(Au1SoundId id) {
        if (id == null) {
            return null;
        }
        return SoundSystem.instance.getSoundResource(id.getResourceFileName());
    }

    public static float categoryVolume(SoundCategory category) {
        if (category == SoundCategory.UI) {
            return FModel.getPreferences().getPrefInt(FPref.UI_VOL_UI) / 100f;
        }
        if (category == SoundCategory.AMBIENCE) {
            if (muteOverworldAmbience()) {
                return 0f;
            }
            return FModel.getPreferences().getPrefInt(FPref.UI_VOL_MUSIC) / 100f;
        }
        return FModel.getPreferences().getPrefInt(FPref.UI_VOL_SOUNDS) / 100f;
    }

    public static boolean muteOverworldAmbience() {
        if (!enabled()) {
            return false;
        }
        if (Config.instance().getSettingData() != null
                && Config.instance().getSettingData().muteOverworldAmbience) {
            return true;
        }
        ConfigData data = Config.instance().getConfigData();
        return data != null && data.au1MuteOverworldAmbienceDefault;
    }

    // ---- DS1 / duel hooks (animation PR can call these) ----

    public static void playDraw() {
        play(Au1SoundId.DRAW);
    }

    public static void playLand(ColorSet colors) {
        play(landForColors(colors));
    }

    public static void playCast(CardView card) {
        play(castForCard(card));
    }

    public static void playCast(String typeKey, int cmc) {
        play(castForTypeAndCmc(typeKey, cmc));
    }

    public static void playResolve() {
        play(Au1SoundId.RESOLVE);
    }

    public static void playCounter() {
        play(Au1SoundId.COUNTER);
    }

    public static void playAttackDeclared() {
        play(Au1SoundId.ATTACK);
    }

    public static void playBlock() {
        play(Au1SoundId.BLOCK);
    }

    public static void playCombatDamage(int amount) {
        play(damageForAmount(amount));
    }

    public static void playCreatureDies() {
        play(Au1SoundId.CREATURE_DIES);
    }

    public static void playExile() {
        play(Au1SoundId.EXILE);
    }

    public static void playToken() {
        play(Au1SoundId.TOKEN);
    }

    public static void playLifeGain() {
        play(Au1SoundId.LIFE_GAIN);
    }

    public static void playLifeLoss() {
        play(Au1SoundId.LIFE_LOSS);
    }

    public static void playMulligan() {
        play(Au1SoundId.MULLIGAN);
    }

    public static void playShuffle() {
        play(Au1SoundId.SHUFFLE);
    }

    public static void playWin() {
        play(Au1SoundId.WIN);
    }

    public static void playLose() {
        play(Au1SoundId.LOSE);
    }

    // ---- Overworld / UI hooks ----

    public static void playFootstep(String terrainKey) {
        play(footstepForTerrain(terrainKey));
    }

    public static void playNodeDepleted() {
        play(Au1SoundId.NODE_DEPLETED);
    }

    public static void playItemPickup() {
        play(Au1SoundId.ITEM_PICKUP);
    }

    public static void playDoorOpen() {
        play(Au1SoundId.DOOR_OPEN);
    }

    public static void playPortal() {
        play(Au1SoundId.PORTAL);
    }

    public static void playShopBuy() {
        play(Au1SoundId.SHOP_BUY);
    }

    public static void playShopSell() {
        play(Au1SoundId.SHOP_SELL);
    }

    public static void playLevelUp() {
        play(Au1SoundId.LEVEL_UP);
    }

    public static void playSkillUp() {
        play(Au1SoundId.SKILL_UP);
    }

    public static void playQuestAccept() {
        play(Au1SoundId.QUEST_ACCEPT);
    }

    public static void playQuestComplete() {
        play(Au1SoundId.QUEST_COMPLETE);
    }

    public static void playEnemyNotice() {
        play(Au1SoundId.ENEMY_NOTICE);
    }

    public static void playChestOpen() {
        play(Au1SoundId.CHEST_OPEN);
    }

    public static void playDialogBlip() {
        play(Au1SoundId.DIALOG_BLIP);
    }

    public static void playUiHover() {
        play(Au1SoundId.UI_HOVER);
    }

    public static void playUiConfirm() {
        play(Au1SoundId.UI_CONFIRM);
    }

    public static void playPackRip() {
        play(Au1SoundId.PACK_RIP);
    }

    public static void playCardFlip() {
        play(Au1SoundId.CARD_FLIP);
    }

    public static void playRareReveal() {
        play(Au1SoundId.RARE_REVEAL);
    }

    public static void playRewardReveal() {
        play(Au1SoundId.REWARD_REVEAL);
    }

    public static void playAchievement() {
        play(Au1SoundId.ACHIEVEMENT);
    }

    // ---- Mapping helpers (also used by tests) ----

    public static Au1SoundId landForColors(ColorSet colors) {
        if (colors == null || colors.isColorless()) {
            return Au1SoundId.LAND_C;
        }
        if (colors.isMonoColor()) {
            if (colors.hasWhite()) {
                return Au1SoundId.LAND_W;
            }
            if (colors.hasBlue()) {
                return Au1SoundId.LAND_U;
            }
            if (colors.hasBlack()) {
                return Au1SoundId.LAND_B;
            }
            if (colors.hasRed()) {
                return Au1SoundId.LAND_R;
            }
            if (colors.hasGreen()) {
                return Au1SoundId.LAND_G;
            }
        }
        return Au1SoundId.LAND_C;
    }

    public static Au1SoundId castForCard(CardView card) {
        if (card == null) {
            return Au1SoundId.CAST_OTHER;
        }
        CardStateView state = card.getCurrentState();
        int cmc = 0;
        ManaCost cost = state != null ? state.getManaCost() : null;
        if (cost != null) {
            cmc = cost.getCMC();
        }
        String typeKey = "other";
        if (state != null) {
            if (state.isCreature()) {
                typeKey = "creature";
            } else if (state.isInstant()) {
                typeKey = "instant";
            } else if (state.isSorcery()) {
                typeKey = "sorcery";
            } else if (state.isArtifact()) {
                typeKey = "artifact";
            } else if (state.isEnchantment()) {
                typeKey = "enchantment";
            } else if (state.isPlaneswalker()) {
                typeKey = "planeswalker";
            }
        }
        return castForTypeAndCmc(typeKey, cmc);
    }

    public static Au1SoundId castForTypeAndCmc(String typeKey, int cmc) {
        ConfigData cfg = Config.instance().getConfigData();
        int lightMax = cfg != null ? cfg.au1CastLightCmcMax : 2;
        int heavyMin = cfg != null ? cfg.au1CastHeavyCmcMin : 6;
        String weight = cmc <= lightMax ? "light" : (cmc >= heavyMin ? "heavy" : "medium");
        String key = typeKey == null ? "other" : typeKey.toLowerCase(Locale.ROOT);
        switch (key) {
            case "creature":
                return weight.equals("light") ? Au1SoundId.CAST_CREATURE_LIGHT
                        : weight.equals("heavy") ? Au1SoundId.CAST_CREATURE_HEAVY
                        : Au1SoundId.CAST_CREATURE_MEDIUM;
            case "instant":
                return weight.equals("light") ? Au1SoundId.CAST_INSTANT_LIGHT
                        : weight.equals("heavy") ? Au1SoundId.CAST_INSTANT_HEAVY
                        : Au1SoundId.CAST_INSTANT_MEDIUM;
            case "sorcery":
                return weight.equals("light") ? Au1SoundId.CAST_SORCERY_LIGHT
                        : weight.equals("heavy") ? Au1SoundId.CAST_SORCERY_HEAVY
                        : Au1SoundId.CAST_SORCERY_MEDIUM;
            case "artifact":
                return Au1SoundId.CAST_ARTIFACT;
            case "enchantment":
                return Au1SoundId.CAST_ENCHANTMENT;
            case "planeswalker":
                return Au1SoundId.CAST_PLANESWALKER;
            default:
                return Au1SoundId.CAST_OTHER;
        }
    }

    public static Au1SoundId damageForAmount(int amount) {
        ConfigData cfg = Config.instance().getConfigData();
        int lightMax = cfg != null ? cfg.au1DamageLightMax : 2;
        int heavyMin = cfg != null ? cfg.au1DamageHeavyMin : 5;
        if (amount <= lightMax) {
            return Au1SoundId.COMBAT_DAMAGE_LIGHT;
        }
        if (amount >= heavyMin) {
            return Au1SoundId.COMBAT_DAMAGE_HEAVY;
        }
        return Au1SoundId.COMBAT_DAMAGE_MEDIUM;
    }

    public static Au1SoundId footstepForTerrain(String terrainKey) {
        if (terrainKey == null) {
            return Au1SoundId.FOOTSTEP_DIRT;
        }
        switch (terrainKey.toLowerCase(Locale.ROOT)) {
            case "green":
            case "forest":
            case "grass":
                return Au1SoundId.FOOTSTEP_GRASS;
            case "white":
            case "plains":
            case "dirt":
            case "waste":
            case "colorless":
                return Au1SoundId.FOOTSTEP_DIRT;
            case "red":
            case "mountain":
            case "stone":
            case "cave":
            case "dungeon":
            case "castle":
                return Au1SoundId.FOOTSTEP_STONE;
            case "black":
            case "swamp":
            case "town":
            case "capital":
            case "wood":
                return Au1SoundId.FOOTSTEP_WOOD;
            case "blue":
            case "island":
            case "water":
                return Au1SoundId.FOOTSTEP_WATER;
            default:
                return Au1SoundId.FOOTSTEP_DIRT;
        }
    }
}
