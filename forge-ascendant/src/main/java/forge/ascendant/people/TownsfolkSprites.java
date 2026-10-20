package forge.ascendant.people;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ninja Adventure townsfolk sheets: Walk/Idle, four directions. */
public final class TownsfolkSprites implements Disposable {
    private static final String[] FACING = {"Down", "Up", "Left", "Right"};
    private final Path dir;
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, TextureRegion[][]> walk = new HashMap<>();
    private final Map<String, TextureRegion[]> idle = new HashMap<>();
    private boolean failed;

    public TownsfolkSprites(Path mapsRoot) {
        this.dir = mapsRoot.getParent().resolve("sprites").resolve("townsfolk");
    }

    public boolean ready() {
        return !failed && Files.isDirectory(dir);
    }

    public boolean draw(SpriteBatch batch, TownsfolkSim.Person person, int tile) {
        if (!ready()) {
            return false;
        }
        TextureRegion region = frame(person);
        if (region == null) {
            return false;
        }
        float px = person.x * tile - 8f;
        float py = person.y * tile - 8f;
        batch.draw(region, px, py, 16f, 16f);
        return true;
    }

    private TextureRegion frame(TownsfolkSim.Person person) {
        ensure(person.sprite);
        int face = person.facing & 3;
        if (!person.walking) {
            TextureRegion[] idles = idle.get(person.sprite);
            if (idles != null && idles[face] != null) {
                return idles[face];
            }
        }
        TextureRegion[][] walks = walk.get(person.sprite);
        if (walks == null || walks[face] == null || walks[face].length == 0) {
            return null;
        }
        int frame = (int) (person.anim / 0.16f) % walks[face].length;
        return walks[face][frame];
    }

    private void ensure(String sprite) {
        if (failed || textures.containsKey(sprite) || walk.containsKey(sprite)) {
            return;
        }
        Path png = dir.resolve(sprite + ".png");
        Path atlas = dir.resolve(sprite + ".atlas");
        if (!Files.isRegularFile(png) || !Files.isRegularFile(atlas)) {
            walk.put(sprite, new TextureRegion[4][]);
            idle.put(sprite, new TextureRegion[4]);
            return;
        }
        try {
            Texture texture = new Texture(new FileHandle(png.toFile()));
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            textures.put(sprite, texture);
            Map<String, List<TextureRegion>> regions = parseAtlas(texture, atlas);
            TextureRegion[][] walks = new TextureRegion[4][];
            TextureRegion[] idles = new TextureRegion[4];
            for (int i = 0; i < 4; i++) {
                List<TextureRegion> w = regions.getOrDefault("Walk" + FACING[i], List.of());
                walks[i] = w.toArray(new TextureRegion[0]);
                List<TextureRegion> id = regions.getOrDefault("Idle" + FACING[i], List.of());
                idles[i] = id.isEmpty() ? null : id.get(0);
            }
            walk.put(sprite, walks);
            idle.put(sprite, idles);
        } catch (Exception e) {
            System.err.println("TownsfolkSprites: " + sprite + ": " + e.getMessage());
            failed = true;
        }
    }

    private static Map<String, List<TextureRegion>> parseAtlas(Texture texture, Path atlas) throws Exception {
        Map<String, List<TextureRegion>> regions = new HashMap<>();
        List<String> lines = Files.readAllLines(atlas);
        String name = null;
        int x = 0;
        int y = 0;
        int w = 0;
        int h = 0;
        boolean have = false;
        for (String raw : lines) {
            boolean indented = raw.startsWith(" ") || raw.startsWith("\t");
            String line = raw.trim();
            if (!indented) {
                flush(texture, regions, name, x, y, w, h, have);
                have = false;
                w = 0;
                h = 0;
                if (line.isEmpty() || line.endsWith(".png") || line.startsWith("size:")
                        || line.startsWith("format:") || line.startsWith("filter:") || line.startsWith("repeat:")) {
                    name = null;
                } else {
                    name = line;
                }
                continue;
            }
            if (line.startsWith("xy:")) {
                String[] parts = line.substring(3).trim().split(",");
                x = Integer.parseInt(parts[0].trim());
                y = Integer.parseInt(parts[1].trim());
                have = true;
            } else if (line.startsWith("size:")) {
                String[] parts = line.substring(5).trim().split(",");
                w = Integer.parseInt(parts[0].trim());
                h = Integer.parseInt(parts[1].trim());
            }
        }
        flush(texture, regions, name, x, y, w, h, have);
        return regions;
    }

    private static void flush(Texture texture, Map<String, List<TextureRegion>> regions,
                              String name, int x, int y, int w, int h, boolean have) {
        if (!have || name == null || w <= 0 || h <= 0) {
            return;
        }
        regions.computeIfAbsent(name, k -> new ArrayList<>())
                .add(new TextureRegion(texture, x, y, w, h));
    }

    @Override
    public void dispose() {
        for (Texture texture : textures.values()) {
            texture.dispose();
        }
        textures.clear();
        walk.clear();
        idle.clear();
    }
}
