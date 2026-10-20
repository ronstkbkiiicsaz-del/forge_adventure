package forge.ascendant.world;

import forge.ascendant.save.SaveGame;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class WorldEditSaveTest {
    @Test
    public void chopDigWallSurviveRoundTrip() throws Exception {
        PlaneData plane = tinyPlane();
        WorldEdit world = new WorldEdit();

        world.tool = WorldEdit.Tool.CHOP;
        String chop = world.apply(plane, WorldEdit.SURFACE, 5, 5);
        Assert.assertTrue("chop: " + chop, chop.contains("+3 wood"));
        Assert.assertEquals(3, world.wood);
        Assert.assertEquals(Terrain.STUMP, world.surfaceAt(plane, 5, 5));

        world.tool = WorldEdit.Tool.DIG;
        String dig = world.apply(plane, WorldEdit.SURFACE, 4, 5);
        Assert.assertTrue("dig: " + dig, dig.contains("+1 dirt"));
        Assert.assertEquals(Terrain.PIT, world.surfaceAt(plane, 4, 5));
        Assert.assertTrue(world.isOpenUnder(4, 5));
        Assert.assertEquals(1, world.dirt);

        world.tool = WorldEdit.Tool.WALL;
        String wall = world.apply(plane, WorldEdit.SURFACE, 6, 5);
        Assert.assertTrue("wall: " + wall, wall.toLowerCase().contains("wall"));
        Assert.assertEquals(WorldEdit.Fixture.WALL, world.fixtureAt(WorldEdit.SURFACE, 6, 5));
        Assert.assertEquals(1, world.wood);
        Assert.assertEquals(0, world.stone);

        Path tmp = Files.createTempFile("world-edit", ".json");
        SaveGame.write(tmp, new SaveGame(SaveGame.VERSION, plane.seed, "test", 8.5f, 8.5f, null,
                world.wood, world.stone, world.dirt, WorldEdit.SURFACE,
                world.copySurfaceEdits(), world.copyUnderCells(), world.copyBuilds()), world);

        WorldEdit loaded = new WorldEdit();
        SaveGame s = SaveGame.read(tmp);
        s.applyTo(loaded);

        Assert.assertEquals(1, loaded.wood);
        Assert.assertEquals(0, loaded.stone);
        Assert.assertEquals(1, loaded.dirt);
        Assert.assertEquals(Terrain.STUMP, loaded.surfaceAt(plane, 5, 5));
        Assert.assertEquals(Terrain.PIT, loaded.surfaceAt(plane, 4, 5));
        Assert.assertTrue(loaded.isOpenUnder(4, 5));
        Assert.assertEquals(WorldEdit.Fixture.WALL, loaded.fixtureAt(WorldEdit.SURFACE, 6, 5));
        Files.deleteIfExists(tmp);
    }

    @Test
    public void oldSaveMissingEditsLoadsEmpty() throws Exception {
        Path tmp = Files.createTempFile("old-save", ".json");
        Files.writeString(tmp, "{\"version\":1,\"seed\":1,\"profileId\":\"t\",\"playerX\":1.5,\"playerY\":2.5}");
        SaveGame s = SaveGame.read(tmp);
        Assert.assertEquals(0, s.wood);
        Assert.assertEquals(0, s.stone);
        Assert.assertEquals(0, s.dirt);
        Assert.assertEquals(WorldEdit.SURFACE, s.layer);
        WorldEdit world = new WorldEdit();
        s.applyTo(world);
        Assert.assertEquals(0, world.wood);
        Assert.assertTrue(world.copySurfaceEdits().isEmpty());
        Assert.assertTrue(world.copyUnderCells().isEmpty());
        Assert.assertTrue(world.copyBuilds().isEmpty());
        Files.deleteIfExists(tmp);
    }

    private static PlaneData tinyPlane() throws Exception {
        String json = """
                {"id":"t","name":"t","size":16,"chunk":8,"tileSize":16,
                 "layout":{},"home_town":{},"plains_town":{},"terrain":{},"sea":{},
                 "biomes":{"white":{"density":1}},"roads":{"WIDTH":2}}
                """;
        Path p = Files.createTempFile("tiny-profile", ".json");
        Files.writeString(p, json);
        PlaneProfile profile = PlaneProfile.loadFile(p);
        Files.deleteIfExists(p);
        byte[] terrain = new byte[16 * 16];
        byte[] biome = new byte[16 * 16];
        terrain[5 * 16 + 5] = Terrain.TREE;
        return new PlaneData(1L, profile, terrain, biome, List.of(), 8, 8);
    }
}
