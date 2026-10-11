package forge.adventure.sound;

import forge.sound.SoundCategory;

/**
 * AU1 catalog of Ascendant sound keys. Filenames live under
 * {@code res/adventure/Shandalar Ascendant/sound/} (preferred) or
 * {@code res/adventure/common/sound/}. Stock {@code res/sound/} is never overwritten.
 *
 * <p>DS1's later animation PR should call {@link Au1Sounds} helpers that map to these ids.
 */
public enum Au1SoundId {
    // Duel
    DRAW("au1_draw", SoundCategory.EFFECTS),
    LAND_W("au1_land_w", SoundCategory.EFFECTS),
    LAND_U("au1_land_u", SoundCategory.EFFECTS),
    LAND_B("au1_land_b", SoundCategory.EFFECTS),
    LAND_R("au1_land_r", SoundCategory.EFFECTS),
    LAND_G("au1_land_g", SoundCategory.EFFECTS),
    LAND_C("au1_land_c", SoundCategory.EFFECTS),
    CAST_CREATURE_LIGHT("au1_cast_creature_light", SoundCategory.EFFECTS),
    CAST_CREATURE_MEDIUM("au1_cast_creature_medium", SoundCategory.EFFECTS),
    CAST_CREATURE_HEAVY("au1_cast_creature_heavy", SoundCategory.EFFECTS),
    CAST_INSTANT_LIGHT("au1_cast_instant_light", SoundCategory.EFFECTS),
    CAST_INSTANT_MEDIUM("au1_cast_instant_medium", SoundCategory.EFFECTS),
    CAST_INSTANT_HEAVY("au1_cast_instant_heavy", SoundCategory.EFFECTS),
    CAST_SORCERY_LIGHT("au1_cast_sorcery_light", SoundCategory.EFFECTS),
    CAST_SORCERY_MEDIUM("au1_cast_sorcery_medium", SoundCategory.EFFECTS),
    CAST_SORCERY_HEAVY("au1_cast_sorcery_heavy", SoundCategory.EFFECTS),
    CAST_ARTIFACT("au1_cast_artifact", SoundCategory.EFFECTS),
    CAST_ENCHANTMENT("au1_cast_enchantment", SoundCategory.EFFECTS),
    CAST_PLANESWALKER("au1_cast_planeswalker", SoundCategory.EFFECTS),
    CAST_OTHER("au1_cast_other", SoundCategory.EFFECTS),
    RESOLVE("au1_resolve", SoundCategory.EFFECTS),
    COUNTER("au1_counter", SoundCategory.EFFECTS),
    ATTACK("au1_attack", SoundCategory.EFFECTS),
    BLOCK("au1_block", SoundCategory.EFFECTS),
    COMBAT_DAMAGE_LIGHT("au1_combat_damage_light", SoundCategory.EFFECTS),
    COMBAT_DAMAGE_MEDIUM("au1_combat_damage_medium", SoundCategory.EFFECTS),
    COMBAT_DAMAGE_HEAVY("au1_combat_damage_heavy", SoundCategory.EFFECTS),
    CREATURE_DIES("au1_creature_dies", SoundCategory.EFFECTS),
    EXILE("au1_exile", SoundCategory.EFFECTS),
    TOKEN("au1_token", SoundCategory.EFFECTS),
    LIFE_GAIN("au1_life_gain", SoundCategory.EFFECTS),
    LIFE_LOSS("au1_life_loss", SoundCategory.EFFECTS),
    MULLIGAN("au1_mulligan", SoundCategory.EFFECTS),
    SHUFFLE("au1_shuffle", SoundCategory.EFFECTS),
    WIN("au1_win", SoundCategory.EFFECTS),
    LOSE("au1_lose", SoundCategory.EFFECTS),

    // Overworld / maps
    FOOTSTEP_GRASS("au1_footstep_grass", SoundCategory.EFFECTS),
    FOOTSTEP_DIRT("au1_footstep_dirt", SoundCategory.EFFECTS),
    FOOTSTEP_STONE("au1_footstep_stone", SoundCategory.EFFECTS),
    FOOTSTEP_WOOD("au1_footstep_wood", SoundCategory.EFFECTS),
    FOOTSTEP_WATER("au1_footstep_water", SoundCategory.EFFECTS),
    NODE_DEPLETED("au1_node_depleted", SoundCategory.EFFECTS),
    ITEM_PICKUP("au1_item_pickup", SoundCategory.EFFECTS),
    DOOR_OPEN("au1_door_open", SoundCategory.EFFECTS),
    PORTAL("au1_portal", SoundCategory.EFFECTS),
    SHOP_BUY("au1_shop_buy", SoundCategory.EFFECTS),
    SHOP_SELL("au1_shop_sell", SoundCategory.EFFECTS),
    LEVEL_UP("au1_level_up", SoundCategory.EFFECTS),
    SKILL_UP("au1_skill_up", SoundCategory.EFFECTS),
    QUEST_ACCEPT("au1_quest_accept", SoundCategory.EFFECTS),
    QUEST_COMPLETE("au1_quest_complete", SoundCategory.EFFECTS),
    ENEMY_NOTICE("au1_enemy_notice", SoundCategory.EFFECTS),
    CHEST_OPEN("au1_chest_open", SoundCategory.EFFECTS),
    DIALOG_BLIP("au1_dialog_blip", SoundCategory.UI),

    // UI
    UI_HOVER("au1_ui_hover", SoundCategory.UI),
    UI_CONFIRM("au1_ui_confirm", SoundCategory.UI),
    PACK_RIP("au1_pack_rip", SoundCategory.UI),
    CARD_FLIP("au1_card_flip", SoundCategory.UI),
    RARE_REVEAL("au1_rare_reveal", SoundCategory.UI),
    REWARD_REVEAL("au1_reward_reveal", SoundCategory.UI),
    ACHIEVEMENT("au1_achievement", SoundCategory.UI);

    private final String resourceFileName;
    private final SoundCategory category;

    Au1SoundId(final String resourceFileName, final SoundCategory category) {
        this.resourceFileName = resourceFileName;
        this.category = category;
    }

    public String getResourceFileName() {
        return resourceFileName;
    }

    public SoundCategory getCategory() {
        return category;
    }
}
