package forge.app;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone hand-feel prototype (Arena / Slay the Spire). No Forge game state.
 * Run via {@code hand-prototype.cmd}.
 */
public final class HandPrototype extends ApplicationAdapter {
    private static final Color TABLE = new Color(0.10f, 0.11f, 0.13f, 1f);
    private static final Color LINE_DIM = new Color(1f, 1f, 1f, 0.18f);
    private static final Color LINE_HOT = new Color(0.55f, 0.85f, 1f, 0.85f);
    private static final Color GLOW = new Color(0.45f, 0.80f, 1f, 0.55f);
    private static final Color PLACEHOLDER = new Color(0.35f, 0.36f, 0.40f, 1f);

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private GlyphLayout glyphs;
    private Texture whitePixel;
    private final List<Card> cards = new ArrayList<>();
    private int hover = -1, castCount, pressIndex = -1, zoomIndex = -1;
    private boolean zoomOpen, dragging;
    private float pressX, pressY, pressTime, dragDist, dragTilt, prevDragX, castT;
    private Card casting;
    private String captureDir;
    private float captureAge;
    private int captureStep;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration.useGlfwAsync();
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Hand prototype");
        cfg.setWindowedMode(1920, 1080);
        cfg.setResizable(true);
        cfg.useVsync(true);
        new Lwjgl3Application(new HandPrototype(), cfg);
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(1.4f);
        glyphs = new GlyphLayout();
        captureDir = System.getProperty("hand.prototype.captureDir");
        loadCards();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int keycode) {
                if (zoomOpen) {
                    if (keycode == Input.Keys.ESCAPE) zoomOpen = false;
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) { Gdx.app.exit(); return true; }
                if (keycode == Input.Keys.R) { resetHand(); return true; }
                List<Card> live = live();
                if (keycode == Input.Keys.LEFT && !live.isEmpty()) {
                    hover = hover <= 0 ? 0 : hover - 1; return true;
                }
                if (keycode == Input.Keys.RIGHT && !live.isEmpty()) {
                    hover = hover < 0 ? 0 : Math.min(live.size() - 1, hover + 1); return true;
                }
                if (keycode == Input.Keys.SPACE && hover >= 0) { openZoom(hover); return true; }
                if (keycode == Input.Keys.ENTER && hover >= 0 && casting == null) {
                    beginCast(live.get(hover)); return true;
                }
                return false;
            }
            @Override public boolean touchDown(int sx, int sy, int p, int button) {
                float x = sx, y = Gdx.graphics.getHeight() - sy;
                if (zoomOpen) { zoomOpen = false; return true; }
                if (button == Input.Buttons.RIGHT) {
                    int i = pick(x, y); if (i >= 0) openZoom(i); return true;
                }
                if (button != Input.Buttons.LEFT || casting != null) return false;
                int i = pick(x, y); if (i < 0) return false;
                pressIndex = i; pressX = x; pressY = y; pressTime = 0; dragDist = 0;
                dragging = false; prevDragX = x; hover = i; return true;
            }
            @Override public boolean touchDragged(int sx, int sy, int p) {
                if (pressIndex < 0 || zoomOpen || casting != null) return false;
                float x = sx, y = Gdx.graphics.getHeight() - sy;
                dragDist = Math.max(dragDist, dist(pressX, pressY, x, y));
                if (HandLayout.isDrag(dragDist)) {
                    dragging = true;
                    dragTilt = clamp((x - prevDragX) * 0.8f, -HandLayout.DRAG_TILT_MAX_DEG, HandLayout.DRAG_TILT_MAX_DEG);
                    prevDragX = x;
                    Card c = live().get(pressIndex);
                    float h = Gdx.graphics.getHeight();
                    c.targetCx = x; c.targetCy = y;
                    c.targetW = HandLayout.fullWidth(h); c.targetH = HandLayout.fullHeight(h);
                    c.targetRot = dragTilt; c.targetScale = 1f;
                }
                return true;
            }
            @Override public boolean touchUp(int sx, int sy, int p, int button) {
                if (button != Input.Buttons.LEFT || pressIndex < 0) return false;
                float x = sx, y = Gdx.graphics.getHeight() - sy;
                dragDist = Math.max(dragDist, dist(pressX, pressY, x, y));
                if (HandLayout.isTap(dragDist, pressTime)) openZoom(pressIndex);
                else if (dragging) {
                    Card c = live().get(pressIndex);
                    if (c.cy > HandLayout.castLineY(Gdx.graphics.getHeight())) beginCast(c);
                }
                dragging = false; pressIndex = -1; dragTilt = 0; return true;
            }
            @Override public boolean mouseMoved(int sx, int sy) {
                if (zoomOpen || dragging || casting != null || pressIndex >= 0) return false;
                float x = sx, y = Gdx.graphics.getHeight() - sy;
                List<Card> live = live();
                int n = live.size();
                if (n == 0) { hover = -1; return false; }
                float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
                hover = HandLayout.hoverIndexFor(w, h, n, hover, x, y);
                return false;
            }
        });
    }

    private List<Card> live() {
        List<Card> out = new ArrayList<>();
        for (Card c : cards) if (c.alive) out.add(c);
        return out;
    }

    private void loadCards() {
        List<Path> images = findCardImages(HandLayout.CARD_COUNT);
        for (int i = 0; i < HandLayout.CARD_COUNT; i++) {
            Card c = new Card("Card " + (i + 1));
            if (i < images.size()) {
                try {
                    c.texture = new Texture(new FileHandle(images.get(i).toFile()));
                    c.label = images.get(i).getFileName().toString();
                } catch (Exception ignored) { /* placeholder */ }
            }
            cards.add(c);
        }
        layoutTargets(0f);
        for (Card c : cards) c.snapToTarget();
    }

    static List<Path> findCardImages(int limit) {
        List<Path> roots = new ArrayList<>();
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank())
            roots.add(Paths.get(local, "Forge", "Cache", "pics", "cards"));
        String home = System.getProperty("user.home");
        if (home != null) {
            roots.add(Paths.get(home, "AppData", "Local", "Forge", "Cache", "pics", "cards"));
            roots.add(Paths.get(home, ".forge", "Cache", "pics", "cards"));
        }
        List<Path> found = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String n = file.getFileName().toString().toLowerCase();
                        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) {
                            found.add(file);
                            if (found.size() >= limit) return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) { /* skip */ }
            if (found.size() >= limit) break;
        }
        return found;
    }

    private void resetHand() {
        casting = null; castT = 0; zoomOpen = false; dragging = false;
        pressIndex = -1; castCount = 0; hover = -1;
        for (Card c : cards) { c.alive = true; c.alpha = 1f; }
        layoutTargets(0f);
        for (Card c : cards) c.snapToTarget();
    }

    private void openZoom(int liveIndex) {
        if (liveIndex < 0 || liveIndex >= live().size()) return;
        zoomOpen = true; zoomIndex = liveIndex; dragging = false; pressIndex = -1;
    }

    private void beginCast(Card c) {
        casting = c; castT = 0; dragging = false; pressIndex = -1;
        List<Card> live = live();
        int idx = live.indexOf(c);
        if (idx >= 0 && hover == idx) hover = -1;
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        c.targetCx = w / 2f; c.targetCy = h / 2f;
        c.targetW = HandLayout.fullWidth(h) * HandLayout.CAST_END_SCALE;
        c.targetH = HandLayout.fullHeight(h) * HandLayout.CAST_END_SCALE;
        c.targetRot = 0f; c.targetScale = HandLayout.CAST_END_SCALE;
    }

    private int pick(float x, float y) {
        List<Card> live = live();
        int n = live.size();
        if (n == 0) return -1;
        for (int i = n - 1; i >= 0; i--) {
            Card c = live.get(i);
            if (Math.abs(x - c.cx) <= c.w / 2f && Math.abs(y - c.cy) <= c.h / 2f) return i;
        }
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        return HandLayout.hoverIndexFor(w, h, n, hover, x, y);
    }

    private void layoutTargets(float dt) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        List<Card> live = live();
        int n = live.size();
        for (int i = 0; i < n; i++) {
            Card c = live.get(i);
            if (c == casting) continue;
            if (dragging && i == pressIndex) continue;
            HandLayout.Pose pose = hover >= 0 ? HandLayout.hoverPose(w, h, n, i, hover)
                    : HandLayout.restPose(w, h, n, i);
            c.targetCx = pose.cx; c.targetCy = pose.cy; c.targetW = pose.w; c.targetH = pose.h;
            c.targetRot = pose.rotationDeg; c.targetScale = pose.scale;
            if (dt > 0f) easeCard(c, dt);
        }
        if (casting != null) {
            Card c = casting;
            castT += dt;
            easeCard(c, dt);
            if (castT >= HandLayout.CAST_FLY_SEC) {
                c.alpha = Math.max(0f, 1f - (castT - HandLayout.CAST_FLY_SEC) / HandLayout.CAST_FADE_SEC);
            }
            if (castT >= HandLayout.CAST_FLY_SEC + HandLayout.CAST_FADE_SEC) {
                c.alive = false; c.alpha = 0f; casting = null; castCount++;
                if (hover >= live().size()) hover = live().size() - 1;
            }
        }
        if (dragging && pressIndex >= 0 && pressIndex < live.size()) {
            Card c = live.get(pressIndex);
            easeCard(c, dt);
            dragTilt = HandLayout.easeToward(dragTilt, 0f, dt);
            c.targetRot = dragTilt;
        }
        if (pressIndex >= 0) pressTime += dt;
    }

    private static void easeCard(Card c, float dt) {
        c.cx = HandLayout.easeToward(c.cx, c.targetCx, dt);
        c.cy = HandLayout.easeToward(c.cy, c.targetCy, dt);
        c.w = HandLayout.easeToward(c.w, c.targetW, dt);
        c.h = HandLayout.easeToward(c.h, c.targetH, dt);
        c.rotation = HandLayout.easeToward(c.rotation, c.targetRot, dt);
        c.scale = HandLayout.easeToward(c.scale, c.targetScale, dt);
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        advanceCapture(dt);
        if (!zoomOpen) layoutTargets(dt);

        Gdx.gl.glClearColor(TABLE.r, TABLE.g, TABLE.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        List<Card> live = live();
        boolean willCast = dragging && pressIndex >= 0 && pressIndex < live.size()
                && live.get(pressIndex).cy > HandLayout.castLineY(h);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(willCast ? LINE_HOT : LINE_DIM);
        shapes.rect(0, HandLayout.castLineY(h) - 2f, w, 4f);
        shapes.end();

        batch.begin();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < live.size(); i++) {
                boolean top = (dragging && i == pressIndex) || (!dragging && i == hover) || live.get(i) == casting;
                if ((pass == 0) == top) continue;
                drawCard(live.get(i), willCast && i == pressIndex);
            }
        }
        font.setColor(Color.WHITE);
        font.draw(batch, "Cast: " + castCount, 24, h - 28);
        if (zoomOpen && zoomIndex >= 0 && zoomIndex < live.size()) {
            batch.setColor(0, 0, 0, 0.55f);
            batch.draw(pixel(), 0, 0, w, h);
            batch.setColor(Color.WHITE);
            Card z = live.get(zoomIndex);
            float zh = HandLayout.ZOOM_HEIGHT_FRAC * h, zw = zh * HandLayout.CARD_ASPECT;
            drawCardAt(z, w / 2f, h / 2f, zw, zh, 0f, 1f, false);
        }
        batch.end();
        maybeWriteCapture();
    }

    private Texture pixel() {
        if (whitePixel == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE); pm.fill();
            whitePixel = new Texture(pm); pm.dispose();
        }
        return whitePixel;
    }

    private void drawCard(Card c, boolean glow) {
        drawCardAt(c, c.cx, c.cy, c.w, c.h, c.rotation, c.alpha, glow);
    }

    private void drawCardAt(Card c, float cx, float cy, float cw, float ch, float rot, float alpha, boolean glow) {
        if (glow) {
            batch.setColor(GLOW.r, GLOW.g, GLOW.b, GLOW.a * alpha);
            drawTex(pixel(), cx - cw / 2f - 8, cy - ch / 2f - 8, cw / 2f + 8, ch / 2f + 8, cw + 16, ch + 16, rot);
        }
        batch.setColor(1, 1, 1, alpha);
        if (c.texture != null) {
            drawTex(c.texture, cx - cw / 2f, cy - ch / 2f, cw / 2f, ch / 2f, cw, ch, rot);
        } else {
            batch.setColor(PLACEHOLDER.r, PLACEHOLDER.g, PLACEHOLDER.b, alpha);
            drawTex(pixel(), cx - cw / 2f, cy - ch / 2f, cw / 2f, ch / 2f, cw, ch, rot);
            batch.setColor(1, 1, 1, alpha);
            glyphs.setText(font, c.label);
            // Label near the top so it stays readable when the hand is tucked.
            font.draw(batch, c.label, cx - glyphs.width / 2f, cy + ch * 0.35f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawTex(Texture t, float x, float y, float ox, float oy, float tw, float th, float rot) {
        batch.draw(t, x, y, ox, oy, tw, th, 1f, 1f, rot, 0, 0, t.getWidth(), t.getHeight(), false, false);
    }

    private void advanceCapture(float dt) {
        if (captureDir == null || captureDir.isBlank()) return;
        captureAge += dt;
        List<Card> live = live();
        if (captureStep == 0 && captureAge > 0.5f) { hover = -1; captureStep = 1; pendingShot = "01_resting_hand.png"; }
        else if (captureStep == 2 && captureAge > 0.05f) {
            hover = live.size() / 2; layoutTargets(1f); for (Card c : live) c.snapToTarget();
            captureStep = 3; captureAge = 0;
        } else if (captureStep == 3 && captureAge > 0.4f) { captureStep = 4; pendingShot = "02_hover_raise.png"; }
        else if (captureStep == 5 && captureAge > 0.05f) {
            pressIndex = Math.max(0, hover); hover = pressIndex; dragging = true;
            if (!live.isEmpty()) {
                Card c = live.get(pressIndex); float h = Gdx.graphics.getHeight();
                c.targetCx = Gdx.graphics.getWidth() / 2f; c.targetCy = HandLayout.castLineY(h) + 80;
                c.targetW = HandLayout.fullWidth(h); c.targetH = HandLayout.fullHeight(h);
                c.targetRot = 0; c.snapToTarget();
            }
            captureStep = 6; captureAge = 0;
        } else if (captureStep == 6 && captureAge > 0.4f) { captureStep = 7; pendingShot = "03_drag_will_cast.png"; }
        else if (captureStep == 8 && captureAge > 0.05f) {
            dragging = false; pressIndex = -1;
            if (!live.isEmpty()) openZoom(Math.min(2, live.size() - 1));
            captureStep = 9; captureAge = 0;
        } else if (captureStep == 9 && captureAge > 0.4f) { captureStep = 10; pendingShot = "04_zoom_inspect.png"; }
    }

    private String pendingShot;

    private void maybeWriteCapture() {
        if (pendingShot == null) return;
        try {
            Path dir = Paths.get(captureDir);
            Files.createDirectories(dir);
            int ww = Gdx.graphics.getWidth(), hh = Gdx.graphics.getHeight();
            byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, ww, hh, true);
            Pixmap pm = new Pixmap(ww, hh, Pixmap.Format.RGBA8888);
            BufferUtils.copy(pixels, 0, pm.getPixels(), pixels.length);
            PixmapIO.writePNG(new FileHandle(dir.resolve(pendingShot).toFile()), pm);
            pm.dispose();
        } catch (Exception e) { e.printStackTrace(); }
        pendingShot = null;
        captureAge = 0;
        if (captureStep == 1) captureStep = 2;
        else if (captureStep == 4) captureStep = 5;
        else if (captureStep == 7) captureStep = 8;
        else if (captureStep == 10) Gdx.app.exit();
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        if (whitePixel != null) whitePixel.dispose();
        for (Card c : cards) if (c.texture != null) c.texture.dispose();
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0; return (float) Math.sqrt(dx * dx + dy * dy);
    }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static final class Card {
        String label; Texture texture; boolean alive = true;
        float cx, cy, w, h, rotation, scale = 1f, alpha = 1f;
        float targetCx, targetCy, targetW, targetH, targetRot, targetScale = 1f;
        Card(String label) { this.label = label; }
        void snapToTarget() {
            cx = targetCx; cy = targetCy; w = targetW; h = targetH;
            rotation = targetRot; scale = targetScale; alpha = 1f;
        }
    }
}
