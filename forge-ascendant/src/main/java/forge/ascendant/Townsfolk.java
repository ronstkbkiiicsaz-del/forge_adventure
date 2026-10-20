package forge.ascendant;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import forge.ascendant.world.Site;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Havenbrook villagers. Each walks a short loop from their TMX townsfolk marker, then idles.
 */
public final class Townsfolk implements Disposable {
    private static final Pattern PERSON = Pattern.compile(
            "x=\"(\\d+(?:\\.\\d+)?)\"\\s+y=\"(\\d+(?:\\.\\d+)?)\"[\\s\\S]{0,240}?townsfolkId\" value=\"([^\"]+)\"");
    private static final float WALK_SPEED = 1.6f;
    private static final float PAUSE = 1.4f;
    private static final int TILE = 16;

    private final List<Person> people = new ArrayList<>();
    private Texture sheet;
    private String speech = "";
    private float speechTimer;
    private float time;

    public void spawn(AscendantSession session) {
        people.clear();
        Site home = null;
        for (Site site : session.plane.sites) {
            if (site.home) {
                home = site;
                break;
            }
        }
        if (home == null) {
            return;
        }
        Path tmx = session.assetRoot.resolve(home.tmxRelative);
        if (!Files.isRegularFile(tmx)) {
            return;
        }
        try {
            Matcher m = PERSON.matcher(Files.readString(tmx));
            int i = 0;
            while (m.find()) {
                float[] at = worldCenter(home, Float.parseFloat(m.group(1)), Float.parseFloat(m.group(2)), TILE);
                people.add(Person.of(m.group(3), at[0], at[1], i));
                i++;
            }
        } catch (Exception e) {
            System.err.println("Townsfolk: " + e.getMessage());
        }
        Path png = session.assetRoot.getParent().resolve("sprites/townsfolk/villager.png");
        if (Files.isRegularFile(png)) {
            sheet = new Texture(new FileHandle(png.toFile()));
            sheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
    }

    /** TMX object y is pixels from the top, at the bottom edge of the marker. */
    public static float[] worldCenter(Site site, float objX, float objY, int tile) {
        int fileRow = (int) (objY / tile) - 1;
        int trimmed = fileRow - site.skipRowsTop;
        float wy = site.y + (site.h - 1 - trimmed) + 0.5f;
        float wx = site.x + objX / tile + 0.5f;
        return new float[] { wx, wy };
    }

    public void update(float dt) {
        time += dt;
        if (speechTimer > 0) {
            speechTimer -= dt;
        }
        for (Person p : people) {
            p.update(dt);
        }
    }

    public void click(float worldX, float worldY) {
        Person best = null;
        float bestD = 0.9f;
        for (Person p : people) {
            float d = (float) Math.hypot(p.x - worldX, p.y - worldY);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best != null) {
            speech = best.line;
            speechTimer = 3.5f;
        }
    }

    public String speech() {
        return speechTimer > 0 ? speech : "";
    }

    public int count() {
        return people.size();
    }

    public void draw(SpriteBatch batch, OrthographicCamera camera, int tile, float playerY, boolean behindPlayer) {
        if (sheet == null || people.isEmpty()) {
            return;
        }
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        int frame = ((int) (time * 6f)) & 3;
        for (Person p : people) {
            if ((p.y < playerY) != behindPlayer) {
                continue;
            }
            int row = p.walking ? frame : 4;
            TextureRegion region = new TextureRegion(sheet, p.dir * TILE, row * TILE, TILE, TILE);
            batch.draw(region, p.x * tile - 8, p.y * tile - 4, TILE, TILE);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        if (sheet != null) {
            sheet.dispose();
        }
    }

    private static String lineFor(String id) {
        return switch (id) {
            case "havenbrook_mira" -> "Mira: Morning. The mill still needs a proper sail.";
            case "havenbrook_bren" -> "Bren: Road's quiet. The market is a walk that way.";
            case "havenbrook_sela" -> "Sela: The square is the calm bit. Mind the wind.";
            case "havenbrook_ned" -> "Ned: I'm not lost. I'm exploring the square.";
            case "havenbrook_otta" -> "Otta: Anvil's cold today. Come back when it rings.";
            case "havenbrook_pell" -> "Pell: Fields are for walking, not for rushing.";
            case "havenbrook_wynn" -> "Wynn: Havenbrook keeps its gate open.";
            case "havenbrook_jorin" -> "Jorin: That wheel will turn when the sail sits right.";
            default -> "A neighbour nods as you pass.";
        };
    }

    private static final class Person {
        final String line;
        final float ax;
        final float ay;
        final float bx;
        final float by;
        float x;
        float y;
        float pause = PAUSE;
        boolean toB = true;
        boolean walking;
        int dir;

        private Person(String line, float ax, float ay, float bx, float by) {
            this.line = line;
            this.ax = ax;
            this.ay = ay;
            this.bx = bx;
            this.by = by;
            this.x = ax;
            this.y = ay;
        }

        static Person of(String id, float x, float y, int index) {
            float ox = (index % 2 == 0) ? 3.2f : -2.6f;
            float oy = (index % 3 == 0) ? 1.6f : -2.2f;
            return new Person(lineFor(id), x, y, x + ox, y + oy);
        }

        void update(float dt) {
            if (pause > 0) {
                pause -= dt;
                walking = false;
                return;
            }
            float tx = toB ? bx : ax;
            float ty = toB ? by : ay;
            float dx = tx - x;
            float dy = ty - y;
            float dist = (float) Math.hypot(dx, dy);
            if (dist < 0.05f) {
                x = tx;
                y = ty;
                toB = !toB;
                pause = PAUSE;
                walking = false;
                return;
            }
            float step = Math.min(dist, WALK_SPEED * dt);
            x += dx / dist * step;
            y += dy / dist * step;
            walking = true;
            if (Math.abs(dx) > Math.abs(dy)) {
                dir = dx > 0 ? 3 : 2;
            } else {
                dir = dy > 0 ? 1 : 0;
            }
        }
    }
}
