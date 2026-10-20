package forge.ascendant.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import forge.ascendant.world.WorldEdit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SaveGame {
    public static final int VERSION = 1;

    public final int version;
    public final long seed;
    public final String profileId;
    public final float playerX;
    public final float playerY;
    public final byte[] explored; // bitset, optional
    public final int wood;
    public final int stone;
    public final int dirt;
    public final int layer;
    public final List<int[]> surfaceEdits;
    public final List<int[]> underCells;
    public final List<int[]> builds;

    public SaveGame(int version, long seed, String profileId, float playerX, float playerY, byte[] explored) {
        this(version, seed, profileId, playerX, playerY, explored, 0, 0, 0, WorldEdit.SURFACE,
                List.of(), List.of(), List.of());
    }

    public SaveGame(int version, long seed, String profileId, float playerX, float playerY, byte[] explored,
                    int wood, int stone, int dirt, int layer,
                    List<int[]> surfaceEdits, List<int[]> underCells, List<int[]> builds) {
        this.version = version;
        this.seed = seed;
        this.profileId = profileId;
        this.playerX = playerX;
        this.playerY = playerY;
        this.explored = explored;
        this.wood = wood;
        this.stone = stone;
        this.dirt = dirt;
        this.layer = layer;
        this.surfaceEdits = surfaceEdits == null ? List.of() : List.copyOf(surfaceEdits);
        this.underCells = underCells == null ? List.of() : List.copyOf(underCells);
        this.builds = builds == null ? List.of() : List.copyOf(builds);
    }

    public static void write(Path path, SaveGame save) throws IOException {
        write(path, save, null);
    }

    public static void write(Path path, SaveGame save, WorldEdit world) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("version", save.version);
        o.addProperty("seed", save.seed);
        o.addProperty("profileId", save.profileId);
        o.addProperty("playerX", save.playerX);
        o.addProperty("playerY", save.playerY);
        if (save.explored != null) {
            o.addProperty("exploredB64", java.util.Base64.getEncoder().encodeToString(save.explored));
        }
        int wood = world != null ? world.wood : save.wood;
        int stone = world != null ? world.stone : save.stone;
        int dirt = world != null ? world.dirt : save.dirt;
        List<int[]> surface = world != null ? world.copySurfaceEdits() : save.surfaceEdits;
        List<int[]> under = world != null ? world.copyUnderCells() : save.underCells;
        List<int[]> builds = world != null ? world.copyBuilds() : save.builds;
        o.addProperty("wood", wood);
        o.addProperty("stone", stone);
        o.addProperty("dirt", dirt);
        o.addProperty("layer", save.layer);
        o.add("surface", ints(surface, 3));
        o.add("under", ints(under, 3));
        o.add("builds", ints(builds, 4));
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
    }

    public static SaveGame read(Path path) throws IOException {
        JsonObject o = new Gson().fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
        byte[] explored = null;
        if (o.has("exploredB64")) {
            explored = java.util.Base64.getDecoder().decode(o.get("exploredB64").getAsString());
        }
        return new SaveGame(
                o.get("version").getAsInt(),
                o.get("seed").getAsLong(),
                o.get("profileId").getAsString(),
                o.get("playerX").getAsFloat(),
                o.get("playerY").getAsFloat(),
                explored,
                intOr(o, "wood", 0),
                intOr(o, "stone", 0),
                intOr(o, "dirt", 0),
                intOr(o, "layer", WorldEdit.SURFACE),
                readInts(o, "surface", 3),
                readInts(o, "under", 3),
                readInts(o, "builds", 4));
    }

    public void applyTo(WorldEdit world) {
        world.wood = wood;
        world.stone = stone;
        world.dirt = dirt;
        for (int[] row : surfaceEdits) {
            world.loadSurfaceEdit(row[0], row[1], row[2]);
        }
        for (int[] row : underCells) {
            world.loadUnderCell(row[0], row[1], row[2]);
        }
        for (int[] row : builds) {
            world.loadBuild(row[0], row[1], row[2], row[3]);
        }
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static JsonArray ints(List<int[]> rows, int n) {
        JsonArray arr = new JsonArray();
        if (rows == null) {
            return arr;
        }
        for (int[] row : rows) {
            JsonArray inner = new JsonArray();
            for (int i = 0; i < n && i < row.length; i++) {
                inner.add(row[i]);
            }
            arr.add(inner);
        }
        return arr;
    }

    private static List<int[]> readInts(JsonObject o, String key, int n) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return List.of();
        }
        List<int[]> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray(key)) {
            if (!e.isJsonArray()) {
                continue;
            }
            JsonArray inner = e.getAsJsonArray();
            int[] row = new int[n];
            for (int i = 0; i < n && i < inner.size(); i++) {
                row[i] = inner.get(i).getAsInt();
            }
            out.add(row);
        }
        return out;
    }
}
