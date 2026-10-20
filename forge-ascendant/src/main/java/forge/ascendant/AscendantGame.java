package forge.ascendant;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import forge.ascendant.render.TilePalette;
import forge.ascendant.render.TownMaps;
import forge.ascendant.save.SaveGame;
import forge.ascendant.world.PlaneData;
import forge.ascendant.world.Site;
import forge.ascendant.world.Terrain;
import forge.ascendant.world.WorldEdit;

import java.nio.file.Path;

public final class AscendantGame extends ApplicationAdapter {
    private final AscendantSession session;
    private OrthographicCamera camera;
    private Viewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private TownMaps townMaps;
    private Townsfolk townsfolk;
    private OrthographicCamera uiCamera;
    private final Vector3 pointer = new Vector3();
    private int aimX;
    private int aimY;
    private float editHold;

    private static final float SPEED = 7.5f; // tiles per second
    private static final float EDIT_REPEAT = 0.16f;

    public AscendantGame(AscendantSession session) {
        this.session = session;
    }

    @Override
    public void create() {
        int tile = session.profile.tileSize;
        camera = new OrthographicCamera();
        viewport = new FitViewport(40 * tile, 24 * tile, camera);
        uiCamera = new OrthographicCamera();
        uiCamera.setToOrtho(false, 1280, 720);
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        townMaps = new TownMaps(session.assetRoot);
        townsfolk = new Townsfolk();
        townsfolk.spawn(session);
        centerCamera();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        uiCamera.setToOrtho(false, width, height);
    }

    @Override
    public void render() {
        float dt = Math.min(0.05f, Gdx.graphics.getDeltaTime());
        handleInput(dt);
        if (townsfolk != null && session.layer == WorldEdit.SURFACE) {
            townsfolk.update(dt);
        }
        session.markExplored();
        if (session.statusTimer > 0) {
            session.statusTimer -= dt;
        }

        Gdx.gl.glClearColor(0.08f, 0.10f, 0.14f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (session.showFullMap) {
            drawFullMap();
        } else {
            drawWorld();
        }
        drawHud();
    }

    private void handleInput(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (session.showFullMap) {
                session.showFullMap = false;
            } else {
                Gdx.app.exit();
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            session.showFullMap = !session.showFullMap;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            save();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) {
            load();
        }
        if (session.showFullMap) {
            return;
        }

        for (int i = 0; i < 7; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)
                    || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1 + i)) {
                session.world.tool = WorldEdit.Tool.byIndex(i);
                session.toast(session.world.tool.label + " — " + session.world.tool.hint);
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            float[] pos = {session.playerX, session.playerY};
            if (session.world.tryClimb(session.plane, pos, session.layer)) {
                session.playerX = pos[0];
                session.playerY = pos[1];
                session.layer = session.layer == WorldEdit.SURFACE ? WorldEdit.UNDER : WorldEdit.SURFACE;
                session.toast(session.layer == WorldEdit.UNDER ? "Climbed down." : "Climbed up.");
            }
        }

        updateAim();
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && townsfolk != null
                && session.layer == WorldEdit.SURFACE) {
            townsfolk.click(pointer.x / session.profile.tileSize, pointer.y / session.profile.tileSize);
        }
        boolean held = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) || (held && editHold <= 0f)) {
            String msg = session.world.apply(session.plane, session.layer, aimX, aimY);
            session.toast(msg);
            editHold = EDIT_REPEAT;
        }
        if (held) {
            editHold -= dt;
        } else {
            editHold = 0f;
        }

        float dx = 0;
        float dy = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            dy += 1;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            dy -= 1;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            dx -= 1;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            dx += 1;
        }
        if (dx != 0 || dy != 0) {
            float len = (float) Math.hypot(dx, dy);
            dx = dx / len * SPEED * dt;
            dy = dy / len * SPEED * dt;
            tryMove(dx, 0);
            tryMove(0, dy);
            easeCamera(dt);
        } else {
            easeCamera(dt);
        }
    }

    private void updateAim() {
        pointer.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(pointer);
        int tile = session.profile.tileSize;
        int mx = MathUtils.floor(pointer.x / tile);
        int my = MathUtils.floor(pointer.y / tile);
        int[] a = WorldEdit.aimTile(session.playerX, session.playerY, mx, my);
        aimX = a[0];
        aimY = a[1];
    }

    private void tryMove(float dx, float dy) {
        float nx = session.playerX + dx;
        float ny = session.playerY + dy;
        // small foot box
        float[][] feet = {
                {nx - 0.2f, ny - 0.15f},
                {nx + 0.2f, ny - 0.15f},
                {nx - 0.2f, ny + 0.15f},
                {nx + 0.2f, ny + 0.15f}
        };
        PlaneData plane = session.plane;
        for (float[] p : feet) {
            int tx = MathUtils.floor(p[0]);
            int ty = MathUtils.floor(p[1]);
            if (session.world.blocks(plane, session.layer, tx, ty)) {
                return;
            }
        }
        session.playerX = MathUtils.clamp(nx, 1, plane.size - 2);
        session.playerY = MathUtils.clamp(ny, 1, plane.size - 2);
    }

    private void easeCamera(float dt) {
        int tile = session.profile.tileSize;
        float tx = session.playerX * tile;
        float ty = session.playerY * tile;
        camera.position.x += (tx - camera.position.x) * Math.min(1f, 8f * dt);
        camera.position.y += (ty - camera.position.y) * Math.min(1f, 8f * dt);
        camera.update();
    }

    private void centerCamera() {
        int tile = session.profile.tileSize;
        camera.position.set(session.playerX * tile, session.playerY * tile, 0);
        camera.update();
    }

    private void drawWorld() {
        viewport.apply();
        camera.update();
        int tile = session.profile.tileSize;
        PlaneData plane = session.plane;
        boolean under = session.layer == WorldEdit.UNDER;

        int minX = Math.max(0, MathUtils.floor((camera.position.x - camera.viewportWidth * 0.5f) / tile) - 1);
        int maxX = Math.min(plane.size - 1, MathUtils.ceil((camera.position.x + camera.viewportWidth * 0.5f) / tile) + 1);
        int minY = Math.max(0, MathUtils.floor((camera.position.y - camera.viewportHeight * 0.5f) / tile) - 1);
        int maxY = Math.min(plane.size - 1, MathUtils.ceil((camera.position.y + camera.viewportHeight * 0.5f) / tile) + 1);

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (under) {
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    float light = session.world.lightAt(plane, WorldEdit.UNDER, x, y);
                    if (session.world.isOpenUnder(x, y)) {
                        shapes.setColor(0.38f * light, 0.24f * light, 0.13f * light, 1f);
                    } else {
                        float d = 0.04f + 0.06f * light;
                        shapes.setColor(d, d * 0.85f, d * 0.9f, 1f);
                    }
                    shapes.rect(x * tile, y * tile, tile, tile);
                }
            }
        } else {
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    byte t = session.world.surfaceAt(plane, x, y);
                    if (t == Terrain.PIT) {
                        shapes.setColor(0.07f, 0.05f, 0.04f, 1f);
                    } else if (t == Terrain.STUMP) {
                        shapes.setColor(0.34f, 0.24f, 0.12f, 1f);
                    } else {
                        shapes.setColor(TilePalette.color(t, plane.biomeAt(x, y)));
                    }
                    shapes.rect(x * tile, y * tile, tile, tile);
                }
            }
        }
        shapes.end();

        if (!under) {
            for (Site site : plane.sites) {
                if (site.x > maxX || site.y > maxY || site.x + site.w < minX || site.y + site.h < minY) {
                    continue;
                }
                drawTown(site, tile);
            }
        }

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawBuilds(plane, minX, maxX, minY, maxY, tile, under);
        drawAim(tile);
        shapes.end();

        if (!under && townsfolk != null) {
            townsfolk.draw(batch, camera, tile, session.playerY, true);
        }

        // Player
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.WHITE);
        shapes.rect(session.playerX * tile - 6, session.playerY * tile - 4, 12, 16);
        shapes.setColor(0.2f, 0.5f, 0.95f, 1f);
        shapes.rect(session.playerX * tile - 4, session.playerY * tile, 8, 8);
        shapes.end();

        if (!under && townsfolk != null) {
            townsfolk.draw(batch, camera, tile, session.playerY, false);
        }
    }

    private void drawBuilds(PlaneData plane, int minX, int maxX, int minY, int maxY, int tile, boolean under) {
        int layer = session.layer;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float light = under ? session.world.lightAt(plane, layer, x, y) : 1f;
                if (session.world.floorAt(layer, x, y) == WorldEdit.Fixture.FLOOR) {
                    shapes.setColor(0.50f * light, 0.34f * light, 0.18f * light, 1f);
                    shapes.rect(x * tile + 1, y * tile + 1, tile - 2, tile - 2);
                }
                WorldEdit.Fixture fix = session.world.fixtureAt(layer, x, y);
                if (fix == null) {
                    continue;
                }
                float px = x * tile;
                float py = y * tile;
                switch (fix) {
                    case WALL -> {
                        shapes.setColor(0.46f * light, 0.42f * light, 0.38f * light, 1f);
                        shapes.rect(px + 1, py + 1, tile - 2, tile - 2);
                    }
                    case DOOR -> {
                        shapes.setColor(0.38f * light, 0.22f * light, 0.10f * light, 1f);
                        shapes.rect(px + 4, py + 1, tile - 8, tile - 2);
                    }
                    case TORCH -> {
                        shapes.setColor(1f * Math.max(0.35f, light), 0.62f * Math.max(0.35f, light), 0.12f, 1f);
                        shapes.rect(px + tile * 0.38f, py + tile * 0.32f, tile * 0.24f, tile * 0.48f);
                    }
                    case LADDER -> {
                        shapes.setColor(0.62f * light, 0.42f * light, 0.16f * light, 1f);
                        shapes.rect(px + 3, py + 2, 3, tile - 4);
                        shapes.rect(px + tile - 6, py + 2, 3, tile - 4);
                        shapes.rect(px + 3, py + tile * 0.28f, tile - 6, 2);
                        shapes.rect(px + 3, py + tile * 0.55f, tile - 6, 2);
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private void drawAim(int tile) {
        float x = aimX * tile;
        float y = aimY * tile;
        float t = 2f;
        shapes.setColor(1f, 0.92f, 0.25f, 1f);
        shapes.rect(x, y, tile, t);
        shapes.rect(x, y + tile - t, tile, t);
        shapes.rect(x, y, t, tile);
        shapes.rect(x + tile - t, y, t, tile);
    }

    private void drawTown(Site site, int tile) {
        try {
            OrthogonalTiledMapRenderer renderer = townMaps.rendererFor(site);
            TiledMap map = townMaps.mapFor(site);
            // Default flipY=true: getCell(x,0) = TMX south / bottom = site.y.
            // Draw cell-by-cell (no camera hijack) so rows stay on the 16px grid with tile offsets.
            renderer.getBatch().setProjectionMatrix(camera.combined);
            renderer.getBatch().begin();
            for (com.badlogic.gdx.maps.MapLayer layer : map.getLayers()) {
                if (!(layer instanceof TiledMapTileLayer tileLayer) || !layer.isVisible()) {
                    continue;
                }
                int maxY = Math.min(site.h, tileLayer.getHeight());
                for (int cellY = 0; cellY < maxY; cellY++) {
                    float worldY = (site.y + cellY) * tile;
                    for (int x = 0; x < tileLayer.getWidth() && x < site.w; x++) {
                        TiledMapTileLayer.Cell cell = tileLayer.getCell(x, cellY);
                        if (cell == null || cell.getTile() == null) {
                            continue;
                        }
                        var tileObj = cell.getTile();
                        var region = tileObj.getTextureRegion();
                        float rw = region.getRegionWidth();
                        float rh = region.getRegionHeight();
                        float dx = (site.x + x) * tile + tileObj.getOffsetX();
                        float dy = worldY + tileObj.getOffsetY();
                        // Tall regions still foot on the cell (same as Adventure map sprites).
                        if (rh > tile) {
                            dy -= (rh - tile);
                        }
                        renderer.getBatch().draw(region, dx, dy, rw, rh);
                    }
                }
            }
            renderer.getBatch().end();
        } catch (Exception e) {
            System.err.println("TownMaps: Failed to draw " + site.name + ": " + e);
            e.printStackTrace(System.err);
        }
    }

    private void drawFullMap() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, w, h);
        uiCamera.setToOrtho(false, w, h);
        uiCamera.update();
        PlaneData plane = session.plane;
        float scale = Math.min(w / (float) plane.size, h / (float) plane.size);
        float ox = (w - plane.size * scale) * 0.5f;
        float oy = (h - plane.size * scale) * 0.5f;

        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.05f, 0.06f, 0.08f, 1f);
        shapes.rect(0, 0, w, h);
        // Always draw a dim world silhouette so scale is readable; brighten explored chunks.
        int step = Math.max(1, plane.size / 700);
        int chunks = (plane.size + session.profile.chunk - 1) / session.profile.chunk;
        for (int y = 0; y < plane.size; y += step) {
            for (int x = 0; x < plane.size; x += step) {
                Color c = TilePalette.color(plane.terrainAt(x, y), plane.biomeAt(x, y));
                int cx = x / session.profile.chunk;
                int cy = y / session.profile.chunk;
                boolean seen = session.exploredChunks[cy * chunks + cx];
                if (!seen) {
                    c = new Color(c.r * 0.35f, c.g * 0.35f, c.b * 0.35f, 1f);
                }
                shapes.setColor(c);
                shapes.rect(ox + x * scale, oy + y * scale, scale * step + 0.5f, scale * step + 0.5f);
            }
        }
        // sites
        for (Site s : plane.sites) {
            shapes.setColor(1f, 0.85f, 0.2f, 1f);
            shapes.rect(ox + s.x * scale, oy + s.y * scale, s.w * scale, s.h * scale);
        }
        shapes.setColor(Color.WHITE);
        shapes.rect(ox + session.playerX * scale - 2, oy + session.playerY * scale - 2, 4, 4);
        shapes.end();
    }

    private void drawHud() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, w, h);
        uiCamera.setToOrtho(false, w, h);
        uiCamera.update();
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        PlaneData plane = session.plane;
        Site here = nearestSite(plane, (int) session.playerX, (int) session.playerY, 6);
        String loc = here != null ? here.name : BiomeIdName(plane.biomeAt((int) session.playerX, (int) session.playerY));
        String layerName = session.layer == WorldEdit.UNDER ? "under" : "surface";
        font.draw(batch, String.format("Shandalar Ascendant Next  |  %s  |  (%.0f, %.0f)  |  seed %d  |  %s",
                loc, session.playerX, session.playerY, plane.seed, layerName), 12, Gdx.graphics.getHeight() - 12);
        font.draw(batch, "WASD move  |  1-7 tools  |  click/hold use  |  E/Space climb  |  M map  |  F5 save  |  F9 load",
                12, Gdx.graphics.getHeight() - 32);
        WorldEdit.Tool tool = session.world.tool;
        font.setColor(1f, 0.92f, 0.55f, 1f);
        font.draw(batch, tool.label + " — " + tool.hint, 12, Gdx.graphics.getHeight() - 52);
        font.setColor(0.85f, 0.82f, 0.72f, 1f);
        font.draw(batch, String.format("wood %d   stone %d   dirt %d   |   %s",
                session.world.wood, session.world.stone, session.world.dirt, layerName),
                12, Gdx.graphics.getHeight() - 72);
        int px = MathUtils.floor(session.playerX);
        int py = MathUtils.floor(session.playerY);
        String climb = session.world.climbHint(px, py, session.layer);
        if (!climb.isEmpty()) {
            font.setColor(0.55f, 0.95f, 0.65f, 1f);
            font.draw(batch, climb, 12, Gdx.graphics.getHeight() - 92);
        }
        if (session.statusTimer > 0) {
            font.setColor(Color.GOLD);
            font.draw(batch, session.statusMessage, 12, 28);
        }
        if (townsfolk != null && session.layer == WorldEdit.SURFACE && !townsfolk.speech().isEmpty()) {
            font.setColor(Color.GOLD);
            font.draw(batch, townsfolk.speech(), 12, 52);
        }
        // minimap
        drawMinimap(batch);
        batch.end();
    }

    private void drawMinimap(SpriteBatch batchIgnored) {
        batch.end();
        int mm = 160;
        int pad = 12;
        int x0 = Gdx.graphics.getWidth() - mm - pad;
        int y0 = pad;
        PlaneData plane = session.plane;
        float scale = mm / (float) plane.size;
        shapes.setProjectionMatrix(uiCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0, 0, 0, 0.55f);
        shapes.rect(x0 - 4, y0 - 4, mm + 8, mm + 8);
        int step = Math.max(1, plane.size / mm);
        for (int y = 0; y < plane.size; y += step) {
            for (int x = 0; x < plane.size; x += step) {
                shapes.setColor(TilePalette.color(plane.terrainAt(x, y), plane.biomeAt(x, y)));
                shapes.rect(x0 + x * scale, y0 + y * scale, scale * step + 0.5f, scale * step + 0.5f);
            }
        }
        shapes.setColor(Color.WHITE);
        shapes.rect(x0 + session.playerX * scale - 1.5f, y0 + session.playerY * scale - 1.5f, 3, 3);
        shapes.end();
        batch.begin();
    }

    private static String BiomeIdName(byte b) {
        return forge.ascendant.world.BiomeId.NAMES[b & 0xff];
    }

    private static Site nearestSite(PlaneData plane, int x, int y, int radius) {
        Site best = plane.siteAt(x, y);
        if (best != null) {
            return best;
        }
        for (Site s : plane.sites) {
            int cx = s.x + s.w / 2;
            int cy = s.y + s.h / 2;
            if (Math.abs(cx - x) <= s.w / 2 + radius && Math.abs(cy - y) <= s.h / 2 + radius) {
                return s;
            }
        }
        return null;
    }

    private void save() {
        try {
            Path path = session.saveDir.resolve("slot1.json");
            byte[] bits = new byte[(session.exploredChunks.length + 7) / 8];
            for (int i = 0; i < session.exploredChunks.length; i++) {
                if (session.exploredChunks[i]) {
                    bits[i / 8] |= (byte) (1 << (i % 8));
                }
            }
            SaveGame snapshot = new SaveGame(SaveGame.VERSION, session.plane.seed, session.profile.id,
                    session.playerX, session.playerY, bits,
                    session.world.wood, session.world.stone, session.world.dirt, session.layer,
                    session.world.copySurfaceEdits(), session.world.copyUnderCells(), session.world.copyBuilds());
            SaveGame.write(path, snapshot, session.world);
            session.toast("Saved " + path.getFileName());
        } catch (Exception e) {
            session.toast("Save failed: " + e.getMessage());
        }
    }

    private void load() {
        try {
            Path path = session.saveDir.resolve("slot1.json");
            SaveGame s = SaveGame.read(path);
            if (s.seed != session.plane.seed) {
                session.toast("Save seed differs — restart with that seed to load");
                return;
            }
            session.playerX = s.playerX;
            session.playerY = s.playerY;
            session.layer = s.layer;
            session.world = new WorldEdit();
            s.applyTo(session.world);
            if (s.explored != null) {
                for (int i = 0; i < session.exploredChunks.length; i++) {
                    session.exploredChunks[i] = ((s.explored[i / 8] >> (i % 8)) & 1) == 1;
                }
            }
            centerCamera();
            session.toast("Loaded " + path.getFileName());
        } catch (Exception e) {
            session.toast("Load failed: " + e.getMessage());
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        townMaps.dispose();
        if (townsfolk != null) {
            townsfolk.dispose();
        }
    }
}
