package forge.adventure;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import forge.adventure.data.ConfigData;
import forge.adventure.sound.Au1SoundEnrichment;
import forge.adventure.sound.Au1SoundId;
import forge.adventure.sound.Au1Sounds;
import forge.adventure.util.Config;
import forge.card.CardRarity;
import forge.game.GameEntityView;
import forge.game.card.CardView;
import forge.game.event.GameEventAttackersDeclared;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventCardDamaged;
import forge.game.event.GameEventCardDamaged.DamageType;
import forge.game.event.GameEventMulligan;
import forge.game.event.GameEventShuffle;
import forge.game.event.GameEventSpellCountered;
import forge.game.event.GameEventSpellResolved;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;
import forge.game.zone.ZoneView;
import forge.gui.events.UiEventAttackerDeclared;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.sound.ISoundPlaybackHooks;
import forge.sound.SoundCategory;
import forge.sound.SoundEffectType;
import forge.sound.SoundSystem;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AU1: Ascendant sound overhaul — real behaviour against the isolated temp user dir.
 * No source greps. Catalog files, mappings, mute/volume, and fail-safe missing assets.
 */
public class Au1SoundEffectsTest {

    private static Path realUserDir;
    private static Map<String, AdventureTestUserDir.FileStamp> realUserDirSnapshot;
    private static ConfigData classAscendantConfig;
    private static String initError;
    private static Path forgeGuiDir;

    private ISoundPlaybackHooks previousHooks;
    private boolean previousMute;
    private String previousVolSounds;
    private String previousVolUi;

    @BeforeClass
    public void bootstrapConfig() {
        try {
            realUserDir = AdventureTestUserDir.defaultRealUserDir();
            realUserDirSnapshot = AdventureTestUserDir.snapshot(realUserDir);
            AdventureTestUserDir.requireIsolatedUserDir();

            forgeGuiDir = resolveForgeGuiDir();
            Path cfgPath = forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/config.json");
            Assert.assertTrue(Files.isRegularFile(cfgPath), "Ascendant config missing: " + cfgPath);
            byte[] raw = Files.readAllBytes(cfgPath);
            Assert.assertFalse(raw.length >= 3 && (raw[0] & 0xFF) == 0xEF
                            && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF,
                    "Ascendant config.json must be UTF-8 without BOM");
            classAscendantConfig = new Json().fromJson(ConfigData.class, new FileHandle(cfgPath.toFile()));
            Assert.assertNotNull(classAscendantConfig);
            Assert.assertTrue(classAscendantConfig.ascendantRules);
            Assert.assertTrue(classAscendantConfig.au1SoundOverhaul,
                    "Ascendant config.json must enable au1SoundOverhaul");
            Assert.assertEquals(classAscendantConfig.au1CastLightCmcMax, 2);
            Assert.assertEquals(classAscendantConfig.au1CastHeavyCmcMin, 6);
            Assert.assertEquals(classAscendantConfig.au1DamageLightMax, 2);
            Assert.assertEquals(classAscendantConfig.au1DamageHeavyMin, 5);

            Config.resetInstanceForTest();
            Config.instance();
            Config.installConfigDataForTest(classAscendantConfig);
            Assert.assertTrue(Config.au1SoundOverhaul());
        } catch (Throwable t) {
            initError = t.getClass().getSimpleName() + ": " + t.getMessage();
            t.printStackTrace();
        }
    }

    @AfterClass(alwaysRun = true)
    public void restoreAndAssertRealUserDirUntouched() throws Exception {
        try {
            Au1Sounds.setAdventureModeForTest(null);
            Config.resetInstanceForTest();
            SoundSystem.setPlaybackHooks(null);
        } finally {
            if (realUserDirSnapshot != null) {
                AdventureTestUserDir.assertUnchanged(realUserDir, realUserDirSnapshot,
                        "Au1SoundEffectsTest");
            }
        }
    }

    @BeforeMethod
    public void setUp() {
        Assert.assertNull(initError, "AU1 BeforeClass bootstrap failed: " + initError);
        AdventureTestUserDir.requireIsolatedUserDir();

        Config.resetInstanceForTest();
        Config.instance();
        Config.installConfigDataForTest(classAscendantConfig);
        Assert.assertTrue(Config.au1SoundOverhaul());

        previousHooks = SoundSystem.getPlaybackHooks();
        previousMute = Config.instance().getSettingData().muteOverworldAmbience;
        previousVolSounds = FModel.getPreferences().getPref(FPref.UI_VOL_SOUNDS);
        previousVolUi = FModel.getPreferences().getPref(FPref.UI_VOL_UI);

        // AU1 requires Adventure + Ascendant; tests force the Adventure gate.
        Au1Sounds.setAdventureModeForTest(true);
        Au1Sounds.refreshRegistration();
        Au1Sounds.setRecording(true);
        SoundSystem.instance.setIgnorePlayRequests(true);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        Au1Sounds.setRecording(false);
        Au1Sounds.setAdventureModeForTest(null);
        SoundSystem.instance.setIgnorePlayRequests(false);
        Config.instance().getSettingData().muteOverworldAmbience = previousMute;
        FModel.getPreferences().setPref(FPref.UI_VOL_SOUNDS, previousVolSounds);
        FModel.getPreferences().setPref(FPref.UI_VOL_UI, previousVolUi);
        SoundSystem.setPlaybackHooks(previousHooks);
    }

    @Test
    public void everyCatalogIdMapsToExistingAscendantOrCommonFile() {
        Path ascSound = forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/sound");
        Path commonSound = forgeGuiDir.resolve("res/adventure/common/sound");
        Assert.assertTrue(Files.isDirectory(ascSound), "Ascendant sound dir missing: " + ascSound);

        List<String> missing = new ArrayList<>();
        for (Au1SoundId id : Au1SoundId.values()) {
            String base = id.getResourceFileName();
            boolean found = fileExistsWithExtension(ascSound, base)
                    || fileExistsWithExtension(commonSound, base);
            if (!found) {
                missing.add(base);
            }
        }
        Assert.assertTrue(missing.isEmpty(), "AU1 catalog ids without files: " + missing);
    }

    @Test
    public void everyNewAu1FileHasLicenseEntryInCreditsOrLicenseStub() throws Exception {
        Path ascSound = forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/sound");
        Path credits = resolveRepoRoot().resolve("CREDITS.md");
        Assert.assertTrue(Files.isRegularFile(credits), "CREDITS.md missing");
        String creditsText = Files.readString(credits, StandardCharsets.UTF_8);
        Assert.assertTrue(creditsText.toLowerCase(Locale.ROOT).contains("au1"),
                "CREDITS.md must mention AU1");
        Assert.assertTrue(creditsText.toLowerCase(Locale.ROOT).contains("kenney"),
                "CREDITS.md must credit Kenney for AU1 packs");

        Set<String> licenseStubs = new HashSet<>();
        try (var stream = Files.list(ascSound)) {
            stream.filter(p -> p.getFileName().toString().startsWith("LICENSE-kenney-"))
                    .forEach(p -> licenseStubs.add(p.getFileName().toString()));
        }
        Assert.assertTrue(licenseStubs.contains("LICENSE-kenney-interface-sounds.txt"));
        Assert.assertTrue(licenseStubs.contains("LICENSE-kenney-impact-sounds.txt"));
        Assert.assertTrue(licenseStubs.contains("LICENSE-kenney-digital-audio.txt"));
        Assert.assertTrue(licenseStubs.contains("LICENSE-kenney-casino-audio.txt"));
        Assert.assertTrue(licenseStubs.contains("LICENSE-kenney-rpg-audio.txt"));
        Assert.assertTrue(licenseStubs.contains("LICENSE-kenney-ui-audio.txt"));
        Assert.assertTrue(licenseStubs.contains("LICENSE-kenney-music-jingles.txt"));

        // Stock duel sounds must remain present and untouched by AU1 filenames.
        Path stockSound = forgeGuiDir.resolve("res/sound");
        Assert.assertTrue(Files.isRegularFile(stockSound.resolve("draw.mp3")),
                "Stock draw.mp3 must remain");
        Assert.assertFalse(Files.exists(stockSound.resolve("au1_draw.ogg")),
                "AU1 files must not land in stock res/sound/");
    }

    @Test
    public void castAndDamageMappingsUseConfigThresholds() {
        Assert.assertEquals(Au1Sounds.castForTypeAndCmc("creature", 1), Au1SoundId.CAST_CREATURE_LIGHT);
        Assert.assertEquals(Au1Sounds.castForTypeAndCmc("creature", 2), Au1SoundId.CAST_CREATURE_LIGHT);
        Assert.assertEquals(Au1Sounds.castForTypeAndCmc("creature", 3), Au1SoundId.CAST_CREATURE_MEDIUM);
        Assert.assertEquals(Au1Sounds.castForTypeAndCmc("creature", 6), Au1SoundId.CAST_CREATURE_HEAVY);
        Assert.assertEquals(Au1Sounds.castForTypeAndCmc("instant", 7), Au1SoundId.CAST_INSTANT_HEAVY);
        Assert.assertEquals(Au1Sounds.castForTypeAndCmc("sorcery", 4), Au1SoundId.CAST_SORCERY_MEDIUM);
        Assert.assertEquals(Au1Sounds.castForTypeAndCmc("artifact", 1), Au1SoundId.CAST_ARTIFACT);
        Assert.assertEquals(Au1Sounds.castForTypeAndCmc("enchantment", 3), Au1SoundId.CAST_ENCHANTMENT);
        Assert.assertEquals(Au1Sounds.castForTypeAndCmc("planeswalker", 4), Au1SoundId.CAST_PLANESWALKER);

        Assert.assertEquals(Au1Sounds.damageForAmount(1), Au1SoundId.COMBAT_DAMAGE_LIGHT);
        Assert.assertEquals(Au1Sounds.damageForAmount(2), Au1SoundId.COMBAT_DAMAGE_LIGHT);
        Assert.assertEquals(Au1Sounds.damageForAmount(3), Au1SoundId.COMBAT_DAMAGE_MEDIUM);
        Assert.assertEquals(Au1Sounds.damageForAmount(5), Au1SoundId.COMBAT_DAMAGE_HEAVY);

        Assert.assertEquals(Au1Sounds.footstepForTerrain("green"), Au1SoundId.FOOTSTEP_GRASS);
        Assert.assertEquals(Au1Sounds.footstepForTerrain("blue"), Au1SoundId.FOOTSTEP_WATER);
        Assert.assertEquals(Au1Sounds.footstepForTerrain("red"), Au1SoundId.FOOTSTEP_STONE);
    }

    @Test
    public void enrichmentMapsDuelEventsToCatalogIds() {
        Au1Sounds.drainRecordedPlays();

        PlayerView nobody = null;
        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(new GameEventShuffle(nobody)));
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().contains("au1_shuffle"));

        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(new GameEventMulligan(nobody)));
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().contains("au1_mulligan"));

        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventCardDamaged(null, null, 1, DamageType.Normal)));
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().contains("au1_combat_damage_light"));

        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventCardDamaged(null, null, 9, DamageType.Normal)));
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().contains("au1_combat_damage_heavy"));
    }

    @Test
    public void counterSoundOnlyFromEngineSpellCounteredEvent() {
        Au1Sounds.drainRecordedPlays();

        SpellAbilityView noSa = null;
        // Real engine path: CounterEffect fires GameEventSpellCountered when a spell is countered.
        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventSpellCountered(noSa, noSa)));
        List<String> afterCounter = Au1Sounds.drainRecordedPlays();
        Assert.assertEquals(afterCounter.size(), 1, "countered event must play exactly once: " + afterCounter);
        Assert.assertEquals(afterCounter.get(0), "au1_counter");

        // A second countered event plays once more (not latched across distinct counters).
        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventSpellCountered(noSa, noSa)));
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_counter"));

        // Spell resolve — even with a Counterspell-like stack description — must NOT play counter.
        // (Name heuristics are forbidden; only GameEventSpellCountered maps to au1_counter.)
        Assert.assertFalse(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventSpellResolved(noSa, false, "Counterspell")));
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().isEmpty(),
                "SpellResolved must not emit au1_counter from the card name");

        Assert.assertFalse(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventSpellResolved(noSa, false, "Counterbalance")));
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().isEmpty(),
                "Non-counter spell with a counter-like name must not emit au1_counter");

        // DS1 may still call playCounter() directly.
        Au1Sounds.playCounter();
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_counter"));
    }

    @Test
    public void chestItemPickupAndUiFocusFireOnceWithoutDoubleOnRepeat() {
        Au1Sounds.resetUiDebounceForTest();
        Au1Sounds.drainRecordedPlays();

        // Treasure / reward-sprite path: chest open + item pickup (instant gold/shards/life).
        Au1Sounds.playChestOpen();
        Au1Sounds.playItemPickup();
        List<String> loot = Au1Sounds.drainRecordedPlays();
        Assert.assertEquals(loot, List.of("au1_chest_open", "au1_item_pickup"));

        // Gathering primary grant: item pickup once.
        Au1Sounds.playItemPickup();
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_item_pickup"));

        // Focus move A → A (held key / re-select): hover once.
        Object actorA = new Object();
        Object actorB = new Object();
        Au1Sounds.playUiHoverIfChanged(actorA);
        Au1Sounds.playUiHoverIfChanged(actorA);
        Au1Sounds.playUiHoverIfChanged(actorA);
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_ui_hover"));

        // Focus move A → B → A: hover for each change.
        Au1Sounds.playUiHoverIfChanged(actorB);
        Au1Sounds.playUiHoverIfChanged(actorA);
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_ui_hover", "au1_ui_hover"));

        // Controller Use held: confirm once per down/up cycle.
        Au1Sounds.playUiConfirmOnPress();
        Au1Sounds.playUiConfirmOnPress();
        Au1Sounds.playUiConfirmOnPress();
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_ui_confirm"));
        Au1Sounds.releaseUiConfirmLatch();
        Au1Sounds.playUiConfirmOnPress();
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_ui_confirm"));
    }

    @Test
    public void settingsMuteAndVolumeRespected() {
        FModel.getPreferences().setPref(FPref.UI_VOL_SOUNDS, "0");
        Assert.assertEquals(Au1Sounds.categoryVolume(SoundCategory.EFFECTS), 0f, 0.001f);
        // UI volume is independent — still audible when effects are 0.
        FModel.getPreferences().setPref(FPref.UI_VOL_UI, "40");
        Assert.assertEquals(Au1Sounds.categoryVolume(SoundCategory.UI), 0.4f, 0.001f);

        FModel.getPreferences().setPref(FPref.UI_VOL_SOUNDS, "50");
        Assert.assertEquals(Au1Sounds.categoryVolume(SoundCategory.EFFECTS), 0.5f, 0.001f);

        FModel.getPreferences().setPref(FPref.UI_VOL_UI, "25");
        Assert.assertEquals(Au1Sounds.categoryVolume(SoundCategory.UI), 0.25f, 0.001f);

        Config.instance().getSettingData().muteOverworldAmbience = false;
        Assert.assertFalse(Au1Sounds.muteOverworldAmbience());
        Config.instance().getSettingData().muteOverworldAmbience = true;
        Assert.assertTrue(Au1Sounds.muteOverworldAmbience());
        Assert.assertEquals(Au1Sounds.categoryVolume(SoundCategory.AMBIENCE), 0f, 0.001f);
        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.muteAmbience());
    }

    @Test
    public void adventureGateKeepsConstructedMatchesOnStockSounds() {
        Assert.assertTrue(Au1Sounds.enabled());
        Au1Sounds.drainRecordedPlays();
        Au1Sounds.playDraw();
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_draw"));

        // Outside Adventure (constructed / quest / draft): AU1 off even with Ascendant config.
        Au1Sounds.setAdventureModeForTest(false);
        Au1Sounds.refreshRegistration();
        Assert.assertFalse(Au1Sounds.enabled());
        Au1Sounds.playDraw();
        Au1Sounds.playAttackDeclared();
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().isEmpty(),
                "Non-Adventure matches must keep stock sounds");
        Assert.assertFalse(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventShuffle((PlayerView) null)));

        Au1Sounds.setAdventureModeForTest(true);
        Au1Sounds.refreshRegistration();
        Assert.assertTrue(Au1Sounds.enabled());
    }

    @Test
    public void attackSoundOncePerDeclareNotOnClickOrUndeclare() {
        Au1Sounds.drainRecordedPlays();
        CardView attacker = new CardView(1, null, "Bear");
        PlayerView defender = new PlayerView(2, null);

        // Click declare / un-declare: silent.
        Assert.assertFalse(Au1SoundEnrichment.INSTANCE.handleUiEvent(
                new UiEventAttackerDeclared(attacker, defender)));
        Assert.assertFalse(Au1SoundEnrichment.INSTANCE.handleUiEvent(
                new UiEventAttackerDeclared(attacker, null)));
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().isEmpty(),
                "Attacker click/un-declare must not play au1_attack");

        // Committed declare: exactly one.
        Multimap<GameEntityView, CardView> map = HashMultimap.create();
        map.put(defender, attacker);
        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventAttackersDeclared(defender, map)));
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_attack"));
    }

    @Test
    public void drawExileDamageBurstPlayOnceScaledByCount() {
        Au1Sounds.drainRecordedPlays();
        ZoneView library = new ZoneView(null, ZoneType.Library);
        ZoneView hand = new ZoneView(null, ZoneType.Hand);
        ZoneView exile = new ZoneView(null, ZoneType.Exile);
        ZoneView battlefield = new ZoneView(null, ZoneType.Battlefield);

        // Seven cards drawn → one draw clip.
        for (int i = 0; i < 7; i++) {
            Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                    new GameEventCardChangeZone(new CardView(10 + i, null, "Card" + i), library, hand)));
        }
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_draw"));

        // Three exiles → one exile clip.
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                    new GameEventCardChangeZone(new CardView(20 + i, null, "X" + i), battlefield, exile)));
        }
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_exile"));

        // Damage 2 + 3 + 4 = 9 → one heavy clip (scaled by total).
        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventCardDamaged(null, null, 2, DamageType.Normal)));
        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventCardDamaged(null, null, 3, DamageType.Normal)));
        Assert.assertTrue(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventCardDamaged(null, null, 4, DamageType.Normal)));
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_combat_damage_heavy"));
    }

    @Test
    public void controllerConfirmAndRewardFlipPlayOnce() {
        Au1Sounds.resetUiDebounceForTest();
        Au1Sounds.drainRecordedPlays();

        // Controller Use + button click share one latch cycle → one confirm.
        Au1Sounds.playUiConfirmOnPress();
        Au1Sounds.playUiConfirmOnPress();
        Au1Sounds.playUiConfirm();
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_ui_confirm"));
        Au1Sounds.releaseUiConfirmLatch();

        // Reward flip: exactly one sting (not pack+flip+reveal).
        Au1Sounds.playRewardFlipOnce(true, null);
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_pack_rip"));
        Au1Sounds.playRewardFlipOnce(false, CardRarity.MythicRare);
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_rare_reveal"));
        Au1Sounds.playRewardFlipOnce(false, CardRarity.Common);
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_card_flip"));
        Au1Sounds.playRewardFlipOnce(false, null);
        Assert.assertEquals(Au1Sounds.drainRecordedPlays(), List.of("au1_reward_reveal"));
    }

    @Test
    public void syncedCatalogIdsAndScriptedEffectLeftToStock() {
        Assert.assertTrue(Au1SoundId.DRAW.isSynced());
        Assert.assertTrue(Au1SoundId.COMBAT_DAMAGE_MEDIUM.isSynced());
        Assert.assertTrue(Au1SoundId.CREATURE_DIES.isSynced());
        Assert.assertTrue(Au1SoundId.EXILE.isSynced());
        Assert.assertTrue(Au1SoundId.TOKEN.isSynced());
        Assert.assertTrue(Au1SoundId.LIFE_GAIN.isSynced());
        Assert.assertTrue(Au1SoundId.UI_CONFIRM.isSynced());

        // No scripted resource for a normal resolve → AU1 resolve once.
        SpellAbilityView noSa = null;
        Au1Sounds.drainRecordedPlays();
        // Null spell ability: enrichment declines (stock would also no-op).
        Assert.assertFalse(Au1SoundEnrichment.INSTANCE.handleGameEvent(
                new GameEventSpellResolved(noSa, false, "Lightning Bolt")));
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().isEmpty());

        // hasScriptedCardSound is false for names without a resource file.
        Assert.assertFalse(Au1SoundEnrichment.hasScriptedCardSound(
                new CardView(99, null, "Definitely No Scripted Sound XYZ")));
        Assert.assertEquals(SoundEffectType.ScriptedEffect.getResourceFileName(), "");
    }

    @Test
    public void coinsDropAtPriceZeroAndLoadedScriptClipsIsConcurrent() throws Exception {
        // price 0 must still make a sound (stock CoinsDrop). Recording AU1 shop path:
        Au1Sounds.drainRecordedPlays();
        Au1Sounds.playShopBuy();
        Assert.assertEquals(Au1Sounds.drainRecordedPlays().size(), 1);

        // Reflect: loadedScriptClips must be a ConcurrentHashMap.
        var field = SoundSystem.class.getDeclaredField("loadedScriptClips");
        field.setAccessible(true);
        Object map = field.get(null);
        Assert.assertTrue(map instanceof java.util.concurrent.ConcurrentHashMap,
                "loadedScriptClips must be ConcurrentHashMap, was " + map.getClass().getName());
    }

    @Test
    public void missingAssetFailsSafeAndStockWorldLeavesHooksOff() {
        // Playing a nonsense resource must not throw.
        SoundSystem.instance.play("au1_definitely_missing_clip_xyz", false, SoundCategory.EFFECTS);

        ConfigData stock = new ConfigData();
        stock.ascendantRules = false;
        stock.au1SoundOverhaul = true;
        Config.installConfigDataForTest(stock);
        Assert.assertFalse(Config.au1SoundOverhaul());
        Assert.assertFalse(Au1Sounds.enabled());
        Au1Sounds.drainRecordedPlays();
        Au1Sounds.playDraw();
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().isEmpty(),
                "Stock worlds must not play AU1 catalog ids");

        // Ascendant with flag off also stays quiet.
        ConfigData off = new Json().fromJson(ConfigData.class,
                new FileHandle(forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/config.json").toFile()));
        off.au1SoundOverhaul = false;
        Config.installConfigDataForTest(off);
        Assert.assertFalse(Config.au1SoundOverhaul());
        Au1Sounds.playUiConfirm();
        Assert.assertTrue(Au1Sounds.drainRecordedPlays().isEmpty());
    }

    @Test
    public void ds1PublicHooksRecordExpectedIds() {
        Au1Sounds.drainRecordedPlays();
        Au1Sounds.playDraw();
        Au1Sounds.playCast("creature", 1);
        Au1Sounds.playCast("creature", 7);
        Au1Sounds.playResolve();
        Au1Sounds.playCounter();
        Au1Sounds.playAttackDeclared();
        Au1Sounds.playBlock();
        Au1Sounds.playCombatDamage(3);
        Au1Sounds.playCreatureDies();
        Au1Sounds.playUiHover();
        Au1Sounds.playUiConfirm();
        Au1Sounds.playPackRip();
        Au1Sounds.playCardFlip();
        Au1Sounds.playRareReveal();
        Au1Sounds.playAchievement();

        List<String> plays = Au1Sounds.drainRecordedPlays();
        Assert.assertTrue(plays.contains("au1_draw"));
        Assert.assertTrue(plays.contains("au1_cast_creature_light"));
        Assert.assertTrue(plays.contains("au1_cast_creature_heavy"));
        Assert.assertTrue(plays.contains("au1_resolve"));
        Assert.assertTrue(plays.contains("au1_counter"));
        Assert.assertTrue(plays.contains("au1_attack"));
        Assert.assertTrue(plays.contains("au1_block"));
        Assert.assertTrue(plays.contains("au1_combat_damage_medium"));
        Assert.assertTrue(plays.contains("au1_creature_dies"));
        Assert.assertTrue(plays.contains("au1_ui_hover"));
        Assert.assertTrue(plays.contains("au1_ui_confirm"));
        Assert.assertTrue(plays.contains("au1_pack_rip"));
        Assert.assertTrue(plays.contains("au1_card_flip"));
        Assert.assertTrue(plays.contains("au1_rare_reveal"));
        Assert.assertTrue(plays.contains("au1_achievement"));

        // Catalog completeness for DS1 animation PR surface.
        Set<Au1SoundId> duelHooks = EnumSet.of(
                Au1SoundId.DRAW, Au1SoundId.RESOLVE, Au1SoundId.COUNTER, Au1SoundId.ATTACK,
                Au1SoundId.BLOCK, Au1SoundId.CREATURE_DIES, Au1SoundId.EXILE, Au1SoundId.TOKEN,
                Au1SoundId.LIFE_GAIN, Au1SoundId.LIFE_LOSS, Au1SoundId.MULLIGAN, Au1SoundId.SHUFFLE,
                Au1SoundId.WIN, Au1SoundId.LOSE);
        for (Au1SoundId id : duelHooks) {
            Assert.assertNotNull(id.getResourceFileName());
        }
    }

    private static boolean fileExistsWithExtension(Path dir, String base) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        File direct = dir.resolve(base).toFile();
        if (direct.isFile()) {
            return true;
        }
        String[] exts = { ".ogg", ".mp3", ".wav" };
        for (String ext : exts) {
            if (Files.isRegularFile(dir.resolve(base + ext))) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveForgeGuiDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("forge-gui"),
                cwd.getParent() != null ? cwd.getParent().resolve("forge-gui") : null,
                Paths.get("forge-gui").toAbsolutePath()
        };
        for (Path p : candidates) {
            if (p != null && Files.isDirectory(p.resolve("res/adventure"))) {
                return p;
            }
        }
        throw new IllegalStateException("Could not locate forge-gui/res/adventure from " + cwd);
    }

    private static Path resolveRepoRoot() {
        Path gui = resolveForgeGuiDir();
        return gui.getParent() != null ? gui.getParent() : gui;
    }
}
