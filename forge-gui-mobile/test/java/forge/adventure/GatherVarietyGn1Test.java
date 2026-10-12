package forge.adventure;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import forge.adventure.data.ConfigData;
import forge.adventure.data.MaterialData;
import forge.adventure.data.MaterialListData;
import forge.adventure.stage.WorldStage;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * GN1: gather-node family variety. Real data (materials.json + Ascendant config.json),
 * seeded RNG, isolated forge.test.userDir. Drives {@link WorldStage#chooseGatherFamily}, the
 * same picker the overworld spawner calls.
 */
public class GatherVarietyGn1Test {

    private static final int ROLLS = 20_000;
    private static final String[] BIOMES = {"plains", "island", "swamp", "wastes", "mountain", "forest"};

    private static Path realUserDir;
    private static Map<String, AdventureTestUserDir.FileStamp> realUserDirSnapshot;
    private static Path forgeGuiDir;
    private static Array<MaterialData> materials;
    private static Map<String, String> homes;

    @BeforeClass
    public void loadData() throws Exception {
        realUserDir = AdventureTestUserDir.defaultRealUserDir();
        realUserDirSnapshot = AdventureTestUserDir.snapshot(realUserDir);
        AdventureTestUserDir.requireIsolatedUserDir();
        forgeGuiDir = resolveForgeGuiDir();
        Path mats = forgeGuiDir.resolve("res/adventure/common/world/materials.json");
        Assert.assertTrue(Files.isRegularFile(mats), "materials.json missing: " + mats);
        @SuppressWarnings("unchecked")
        Array<MaterialData> loaded = new Json().fromJson(Array.class, MaterialData.class,
                new FileHandle(mats.toFile()));
        materials = loaded;
        homes = MaterialListData.gatherFamilyHomeBiomes(new Array.ArrayIterator<>(materials));
    }

    @AfterClass(alwaysRun = true)
    public void realUserDirUntouched() throws Exception {
        if (realUserDirSnapshot != null)
            AdventureTestUserDir.assertUnchanged(realUserDir, realUserDirSnapshot, "GatherVarietyGn1Test");
    }

    private static ConfigData ascendantConfig() {
        Path cfgPath = forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/config.json");
        return new Json().fromJson(ConfigData.class, new FileHandle(cfgPath.toFile()));
    }

    @Test
    public void familyNamesExistInData() {
        // Every family the GN1 config uses is a real gatherable family with a home biome.
        Assert.assertEquals(homes.get("logs"), "forest");
        Assert.assertEquals(homes.get("plants"), "forest");
        Assert.assertEquals(homes.get("sacred_stone"), "plains");
        Assert.assertEquals(homes.get("waters"), "island");
        Assert.assertEquals(homes.get("dead"), "swamp");
        Assert.assertEquals(homes.get("scrap"), "wastes");
        Assert.assertEquals(homes.get("ore"), "mountain");
        Assert.assertEquals(homes.get("ash"), "mountain");
        Assert.assertEquals(homes.size(), 8, "gatherable families: " + homes);
        // crystal / pearls / gems exist only as rare finds (no node skill / type), so they cannot be
        // node families; they keep dropping from waters (crystal, pearls) and ore (gems) nodes.
        for (String rareOnly : new String[] {"crystal", "pearls", "gems"}) {
            Assert.assertFalse(homes.containsKey(rareOnly), rareOnly + " must not be a node family");
            boolean exists = false;
            for (MaterialData m : new Array.ArrayIterator<>(materials))
                exists |= rareOnly.equals(m.family);
            Assert.assertTrue(exists, rareOnly + " should still exist as a material family");
        }
        ConfigData cfg = ascendantConfig();
        for (ObjectMap.Entry<String, String> e : new ObjectMap.Entries<>(cfg.gn1PrimaryFamily))
            Assert.assertTrue(homes.containsKey(e.value), "unknown primary family " + e.value);
        for (ObjectMap.Entry<String, ObjectMap<String, Float>> e : new ObjectMap.Entries<>(cfg.gn1SecondaryWeights)) {
            for (ObjectMap.Entry<String, Float> w : new ObjectMap.Entries<>(e.value))
                Assert.assertTrue(homes.containsKey(w.key), "unknown secondary family " + w.key + " in " + e.key);
        }
        // Each family has gatherables on its home biome, so borrowing always finds materials.
        for (Map.Entry<String, String> e : homes.entrySet()) {
            int n = 0;
            for (MaterialData m : new Array.ArrayIterator<>(materials)) {
                if (m.isGatherNode() && e.getKey().equals(m.family) && e.getValue().equals(m.biome))
                    n++;
            }
            Assert.assertTrue(n > 0, "no gatherables for " + e);
        }
    }

    @Test
    public void configBlockHasDefaultsAndNoBom() throws Exception {
        Path cfgPath = forgeGuiDir.resolve("res/adventure/Shandalar Ascendant/config.json");
        byte[] raw = Files.readAllBytes(cfgPath);
        Assert.assertFalse(raw.length >= 3 && (raw[0] & 0xff) == 0xef && (raw[1] & 0xff) == 0xbb
                && (raw[2] & 0xff) == 0xbf, "config.json must not have a UTF-8 BOM");
        ConfigData cfg = ascendantConfig();
        Assert.assertTrue(cfg.ascendantRules);
        Assert.assertTrue(cfg.gn1GatherVariety);
        Assert.assertEquals(cfg.gn1PrimaryShare, 0.5f, 1e-6f);
        Assert.assertEquals(cfg.gn1LogsShare, 0.2f, 1e-6f);
        Assert.assertEquals(cfg.gn1SecondaryShare, 0.3f, 1e-6f);
        Assert.assertEquals(cfg.gn1OtherFamilyWeight, 1f, 1e-6f);
        for (String biome : BIOMES) {
            Assert.assertNotNull(cfg.gn1PrimaryFamily.get(biome), "primary for " + biome);
            Assert.assertNotNull(cfg.gn1SecondaryWeights.get(biome), "secondary weights for " + biome);
        }
        // Code defaults match the config numbers; switch is off unless a world turns it on.
        ConfigData defaults = new ConfigData();
        Assert.assertFalse(defaults.gn1GatherVariety);
        Assert.assertEquals(defaults.gn1PrimaryShare, 0.5f, 1e-6f);
        Assert.assertEquals(defaults.gn1LogsShare, 0.2f, 1e-6f);
        Assert.assertEquals(defaults.gn1SecondaryShare, 0.3f, 1e-6f);
    }

    @Test
    public void everyBiomeYieldsPrimaryMostLogsAndListedSecondaries() {
        ConfigData cfg = ascendantConfig();
        StringBuilder table = new StringBuilder("GN1 distribution (" + ROLLS + " rolls, seed 4242):\n");
        for (String biome : BIOMES) {
            Map<String, Integer> counts = roll(biome, cfg, true, 4242L + biome.hashCode());
            table.append(biome).append(": ").append(percentages(counts)).append('\n');
            String primary = cfg.gn1PrimaryFamily.get(biome);
            int primaryCount = counts.getOrDefault(primary, 0);
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (!e.getKey().equals(primary))
                    Assert.assertTrue(primaryCount > e.getValue(), biome + ": primary not most common " + counts);
            }
            Assert.assertEquals(share(counts, primary), 0.5, 0.02, biome + " primary share " + counts);
            Assert.assertEquals(share(counts, "logs"), 0.2, 0.02, biome + " logs share " + counts);
            Assert.assertFalse(counts.containsKey(null), biome + ": GN1 always names a family");
            ObjectMap<String, Float> listed = cfg.gn1SecondaryWeights.get(biome);
            int minListed = Integer.MAX_VALUE;
            for (ObjectMap.Entry<String, Float> w : new ObjectMap.Entries<>(listed)) {
                int n = counts.getOrDefault(w.key, 0);
                Assert.assertTrue(n > 0, biome + ": listed secondary " + w.key + " never appeared " + counts);
                minListed = Math.min(minListed, n);
            }
            // Favoured secondaries beat every unlisted family.
            for (String family : homes.keySet()) {
                if (family.equals(primary) || family.equals("logs") || listed.containsKey(family))
                    continue;
                Assert.assertTrue(counts.getOrDefault(family, 0) < minListed,
                        biome + ": unlisted " + family + " outweighs a favoured secondary " + counts);
            }
            // The rest of the map is still reachable: every family shows up somewhere in 20k rolls.
            Assert.assertEquals(counts.keySet(), homes.keySet(), biome + " families " + counts);
        }
        System.out.println(table);
    }

    @Test
    public void sharesAndWeightsFollowTheConfig() {
        ConfigData cfg = ascendantConfig();
        cfg.gn1PrimaryShare = 0.8f;
        cfg.gn1LogsShare = 0.05f;
        cfg.gn1SecondaryShare = 0.15f;
        Map<String, Integer> counts = roll("wastes", cfg, true, 7L);
        Assert.assertEquals(share(counts, "scrap"), 0.8, 0.02, counts.toString());
        Assert.assertEquals(share(counts, "logs"), 0.05, 0.01, counts.toString());

        // Re-weight wastes so ore dominates the secondary mix.
        cfg = ascendantConfig();
        cfg.gn1SecondaryWeights.get("wastes").put("ore", 20f);
        counts = roll("wastes", cfg, true, 8L);
        for (String f : new String[] {"sacred_stone", "ash", "plants", "waters", "dead"})
            Assert.assertTrue(counts.get("ore") > counts.getOrDefault(f, 0), "ore should lead secondaries " + counts);
        Assert.assertEquals(share(counts, "ore"), 0.3 * 20 / 31.0, 0.015, counts.toString());

        // Zero logs share: trees stop spawning in that config.
        cfg = ascendantConfig();
        cfg.gn1LogsShare = 0f;
        counts = roll("plains", cfg, true, 9L);
        Assert.assertFalse(counts.containsKey("logs"), counts.toString());
        Assert.assertEquals(share(counts, "sacred_stone"), 0.5 / 0.8, 0.02, counts.toString());

        // Primary override in config.
        cfg = ascendantConfig();
        cfg.gn1PrimaryFamily.put("mountain", "ash");
        counts = roll("mountain", cfg, true, 10L);
        Assert.assertEquals(share(counts, "ash"), 0.5, 0.02, counts.toString());
    }

    @Test
    public void stockAndSwitchedOffKeepTheLegacySplit() throws Exception {
        ConfigData stock = new Json().fromJson(ConfigData.class,
                new FileHandle(forgeGuiDir.resolve("res/adventure/common/config.json").toFile()));
        Assert.assertFalse(stock.ascendantRules);
        Assert.assertFalse(stock.gn1GatherVariety, "stock config must not opt into GN1");
        ConfigData ascOff = ascendantConfig();
        ascOff.gn1GatherVariety = false;
        ConfigData ascCfgStockWorld = ascendantConfig();
        for (String biome : BIOMES) {
            for (Object[] c : new Object[][] {{stock, false}, {stock, true}, {ascOff, true}, {ascCfgStockWorld, false}}) {
                Random a = new Random(99L + biome.hashCode());
                Random b = new Random(99L + biome.hashCode());
                for (int i = 0; i < 5_000; i++) {
                    Assert.assertEquals(WorldStage.chooseGatherFamily(biome, (ConfigData) c[0], (Boolean) c[1], homes, a),
                            legacy(biome, b), biome + " roll " + i);
                }
            }
        }
        // Legacy wastes: only logs or null (scrap), no other families.
        Map<String, Integer> counts = roll("wastes", stock, false, 5L);
        Assert.assertEquals(counts.keySet(), new java.util.HashSet<>(java.util.Arrays.asList("logs", null)));
    }

    /** Copy of the pre-GN1 WorldStage.pickBiomeFamily, so stock is pinned to the old behaviour. */
    private static String legacy(String materialBiome, Random rand) {
        if ("forest".equalsIgnoreCase(materialBiome))
            return rand.nextBoolean() ? "logs" : "plants";
        if (rand.nextFloat() < 0.3f)
            return "logs";
        if ("mountain".equalsIgnoreCase(materialBiome))
            return rand.nextBoolean() ? "ore" : "ash";
        return null;
    }

    private static Map<String, Integer> roll(String biome, ConfigData cfg, boolean ascendant, long seed) {
        Random rand = new Random(seed);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < ROLLS; i++)
            counts.merge(WorldStage.chooseGatherFamily(biome, cfg, ascendant, homes, rand), 1, Integer::sum);
        return counts;
    }

    private static double share(Map<String, Integer> counts, String family) {
        return counts.getOrDefault(family, 0) / (double) ROLLS;
    }

    private static String percentages(Map<String, Integer> counts) {
        Map<String, Integer> sorted = new TreeMap<>((x, y) -> {
            int c = Integer.compare(counts.get(y), counts.get(x));
            return c != 0 ? c : x.compareTo(y);
        });
        sorted.putAll(counts);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : sorted.entrySet())
            sb.append(String.format("%s %.1f%%  ", e.getKey(), 100.0 * e.getValue() / ROLLS));
        return sb.toString().trim();
    }

    private static Path resolveForgeGuiDir() {
        for (String rel : List.of("forge-gui", "../forge-gui")) {
            Path p = Path.of(rel);
            if (Files.isDirectory(p.resolve("res/adventure")))
                return p.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("forge-gui not found");
    }
}
