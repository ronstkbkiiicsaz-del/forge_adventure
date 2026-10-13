package forge.ascendant.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight TMX reader for stamping towns into the plane.
 * Any non-zero gid on Foreground / AboveSprites counts as HOUSE (blocked);
 * Ground/Background non-zero becomes TOWN floor.
 */
public final class TmxStamp {
    public final int width;
    public final int height;
    public final byte[] terrain; // row-major, length width*height

    private TmxStamp(int width, int height, byte[] terrain) {
        this.width = width;
        this.height = height;
        this.terrain = terrain;
    }

    public static TmxStamp load(Path tmxPath, int skipRowsTop) throws IOException {
        String xml = Files.readString(tmxPath, StandardCharsets.UTF_8);
        int width = attrInt(xml, "width");
        int height = attrInt(xml, "height");
        byte[] out = new byte[width * height];

        List<Layer> layers = parseLayers(xml, width, height);
        for (Layer layer : layers) {
            boolean blocking = "Foreground".equals(layer.name) || "AboveSprites".equals(layer.name);
            for (int i = 0; i < layer.gids.length; i++) {
                if (layer.gids[i] == 0) {
                    continue;
                }
                out[i] = blocking ? Terrain.HOUSE : Terrain.TOWN;
            }
        }

        if (skipRowsTop > 0) {
            int newH = height - skipRowsTop;
            byte[] trimmed = new byte[width * newH];
            System.arraycopy(out, skipRowsTop * width, trimmed, 0, trimmed.length);
            return new TmxStamp(width, newH, trimmed);
        }
        return new TmxStamp(width, height, out);
    }

    private static int attrInt(String xml, String attr) {
        Matcher m = Pattern.compile("<map[^>]*\\s" + attr + "=\"(\\d+)\"").matcher(xml);
        if (!m.find()) {
            throw new IllegalArgumentException("TMX missing " + attr);
        }
        return Integer.parseInt(m.group(1));
    }

    private static List<Layer> parseLayers(String xml, int width, int height) {
        List<Layer> layers = new ArrayList<>();
        Pattern layerPat = Pattern.compile(
                "<layer[^>]*name=\"([^\"]+)\"[^>]*>\\s*<data encoding=\"csv\">(.*?)</data>",
                Pattern.DOTALL);
        Matcher m = layerPat.matcher(xml);
        while (m.find()) {
            String name = m.group(1);
            String csv = m.group(2).trim();
            int[] gids = new int[width * height];
            int idx = 0;
            for (String part : csv.split("[,\\s]+")) {
                if (part.isEmpty()) {
                    continue;
                }
                if (idx < gids.length) {
                    // clear flip flags
                    gids[idx++] = (int) (Long.parseLong(part) & 0x1FFFFFFFL);
                }
            }
            layers.add(new Layer(name, gids));
        }
        return layers;
    }

    private static final class Layer {
        final String name;
        final int[] gids;

        Layer(String name, int[] gids) {
            this.name = name;
            this.gids = gids;
        }
    }
}
