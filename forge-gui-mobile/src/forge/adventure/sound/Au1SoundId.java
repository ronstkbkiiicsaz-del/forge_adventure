package forge.adventure.sound;

import forge.sound.SoundCategory;

/**
 * AU1 catalog of Ascendant sound keys. Filenames live under
 * {@code res/adventure/Shandalar Ascendant/sound/} (preferred) or
 * {@code res/adventure/common/sound/}. Stock {@code res/sound/} is never overwritten.
 *
 * <p>DS1's later animation PR should call {@link Au1Sounds} helpers that map to these ids.
 * {@code synced} mirrors stock {@link forge.sound.SoundEffectType#isSynced()} so rapid
 * identical clips do not stack.
 */
public enum Au1SoundId {
    // Duel
    DRAW("au1_draw", SoundCategory.EFFECTS, true),
    LAND_W("au1_land_w", SoundCategory.EFFECTS, false),
    LAND_U("au1_land_u", SoundCategory.EFFECTS, false),
    LAND_B("au1_land_b", SoundCategory.EFFECTS, false),
    LAND_R("au1_land_r", SoundCategory.EFFECTS, false),
    LAND_G("au1_land_g", SoundCategory.EFFECTS, false),
    LAND_C("au1_land_c", SoundCategory.EFFECTS, false),
    CAST_CREATURE_LIGHT("au1_cast_creature_light", SoundCategory.EFFECTS, false),
    CAST_CREATURE_MEDIUM("au1_cast_creature_medium", SoundCategory.EFFECTS, false),
    CAST_CREATURE_HEAVY("au1_cast_creature_heavy", SoundCategory.EFFECTS, false),
    CAST_INSTANT_LIGHT("au1_cast_instant_light", SoundCategory.EFFECTS, false),
    CAST_INSTANT_MEDIUM("au1_cast_instant_medium", SoundCategory.EFFECTS, false),
    CAST_INSTANT_HEAVY("au1_cast_instant_heavy", SoundCategory.EFFECTS, false),
    CAST_SORCERY_LIGHT("au1_cast_sorcery_light", SoundCategory.EFFECTS, false),
    CAST_SORCERY_MEDIUM("au1_cast_sorcery_medium", SoundCategory.EFFECTS, false),
    CAST_SORCERY_HEAVY("au1_cast_sorcery_heavy", SoundCategory.EFFECTS, false),
    CAST_ARTIFACT("au1_cast_artifact", SoundCategory.EFFECTS, false),
    CAST_ENCHANTMENT("au1_cast_enchantment", SoundCategory.EFFECTS, false),
    CAST_PLANESWALKER("au1_cast_planeswalker", SoundCategory.EFFECTS, false),
    CAST_OTHER("au1_cast_other", SoundCategory.EFFECTS, false),
    RESOLVE("au1_resolve", SoundCategory.EFFECTS, false),
    COUNTER("au1_counter", SoundCategory.EFFECTS, false),
    ATTACK("au1_attack", SoundCategory.EFFECTS, true),
    BLOCK("au1_block", SoundCategory.EFFECTS, false),
    COMBAT_DAMAGE_LIGHT("au1_combat_damage_light", SoundCategory.EFFECTS, true),
    COMBAT_DAMAGE_MEDIUM("au1_combat_damage_medium", SoundCategory.EFFECTS, true),
    COMBAT_DAMAGE_HEAVY("au1_combat_damage_heavy", SoundCategory.EFFECTS, true),
    CREATURE_DIES("au1_creature_dies", SoundCategory.EFFECTS, true),
    EXILE("au1_exile", SoundCategory.EFFECTS, true),
    TOKEN("au1_token", SoundCategory.EFFECTS, true),
    LIFE_GAIN("au1_life_gain", SoundCategory.EFFECTS, true),
    LIFE_LOSS("au1_life_loss", SoundCategory.EFFECTS, true),
    MULLIGAN("au1_mulligan", SoundCategory.EFFECTS, false),
    SHUFFLE("au1_shuffle", SoundCategory.EFFECTS, false),
    WIN("au1_win", SoundCategory.EFFECTS, false),
    LOSE("au1_lose", SoundCategory.EFFECTS, false),

    // Overworld / maps
    FOOTSTEP_GRASS("au1_footstep_grass", SoundCategory.EFFECTS, false),
    FOOTSTEP_DIRT("au1_footstep_dirt", SoundCategory.EFFECTS, false),
    FOOTSTEP_STONE("au1_footstep_stone", SoundCategory.EFFECTS, false),
    FOOTSTEP_WOOD("au1_footstep_wood", SoundCategory.EFFECTS, false),
    FOOTSTEP_WATER("au1_footstep_water", SoundCategory.EFFECTS, false),
    NODE_DEPLETED("au1_node_depleted", SoundCategory.EFFECTS, false),
    ITEM_PICKUP("au1_item_pickup", SoundCategory.EFFECTS, false),
    DOOR_OPEN("au1_door_open", SoundCategory.EFFECTS, false),
    PORTAL("au1_portal", SoundCategory.EFFECTS, false),
    SHOP_BUY("au1_shop_buy", SoundCategory.EFFECTS, false),
    SHOP_SELL("au1_shop_sell", SoundCategory.EFFECTS, false),
    LEVEL_UP("au1_level_up", SoundCategory.EFFECTS, false),
    SKILL_UP("au1_skill_up", SoundCategory.EFFECTS, false),
    QUEST_ACCEPT("au1_quest_accept", SoundCategory.EFFECTS, false),
    QUEST_COMPLETE("au1_quest_complete", SoundCategory.EFFECTS, false),
    ENEMY_NOTICE("au1_enemy_notice", SoundCategory.EFFECTS, false),
    CHEST_OPEN("au1_chest_open", SoundCategory.EFFECTS, false),
    DIALOG_BLIP("au1_dialog_blip", SoundCategory.UI, false),

    // UI
    UI_HOVER("au1_ui_hover", SoundCategory.UI, false),
    UI_CONFIRM("au1_ui_confirm", SoundCategory.UI, true),
    PACK_RIP("au1_pack_rip", SoundCategory.UI, false),
    CARD_FLIP("au1_card_flip", SoundCategory.UI, false),
    RARE_REVEAL("au1_rare_reveal", SoundCategory.UI, false),
    REWARD_REVEAL("au1_reward_reveal", SoundCategory.UI, false),
    ACHIEVEMENT("au1_achievement", SoundCategory.UI, false);

    private final String resourceFileName;
    private final SoundCategory category;
    private final boolean synced;

    Au1SoundId(final String resourceFileName, final SoundCategory category, final boolean synced) {
        this.resourceFileName = resourceFileName;
        this.category = category;
        this.synced = synced;
    }

    public String getResourceFileName() {
        return resourceFileName;
    }

    public SoundCategory getCategory() {
        return category;
    }

    /** When true, only one instance of this clip plays at a time (stock {@code isSynced}). */
    public boolean isSynced() {
        return synced;
    }
}
