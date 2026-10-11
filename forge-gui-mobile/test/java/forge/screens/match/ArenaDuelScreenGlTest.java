package forge.screens.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import forge.adventure.AdventureGlTestSupport;
import forge.adventure.data.ConfigData;
import forge.adventure.util.Config;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opt-in GL group: render the production {@link ArenaDuelLayout} chrome (same
 * geometry {@link ArenaDuelScreen} uses) to PNG under xvfb for landscape and
 * portrait. Uses a Pixmap (not the 64×64 AdventureGlTestSupport window) so the
 * screenshots match the computed layout. Excluded from default surefire.
 */
public class ArenaDuelScreenGlTest {

    @Test(groups = "gl", timeOut = 300_000)
    public void renderArenaDuelScreenLandscapeAndPortrait() {
        AdventureGlTestSupport.ensureReady();
        final Path outDir = Path.of("/opt/cursor/artifacts/screenshots");
        AdventureGlTestSupport.runOnGl(() -> {
            Files.createDirectories(outDir);
            final ConfigData data = new ConfigData();
            data.ds1ArenaDuelScreen = true;
            data.ds1PhaseRailFraction = 0.07f;
            data.ds1HandFraction = 0.28f;
            data.ds1StackFraction = 0.48f;
            Config.installConfigDataForTest(data);
            if (FModel.getPreferences() != null) {
                FModel.getPreferences().setPref(FPref.UI_MODERN_DUEL_SCREEN, ModernDuelScreen.MODE_ALWAYS);
            }
            ModernDuelScreen.invalidate();

            renderLayout(true, outDir.resolve("ds1-arena-duel-landscape.png"));
            renderLayout(false, outDir.resolve("ds1-arena-duel-portrait.png"));
            return null;
        });
        try {
            Assert.assertTrue(Files.isRegularFile(outDir.resolve("ds1-arena-duel-landscape.png")));
            Assert.assertTrue(Files.isRegularFile(outDir.resolve("ds1-arena-duel-portrait.png")));
            Assert.assertTrue(Files.size(outDir.resolve("ds1-arena-duel-landscape.png")) > 4000);
            Assert.assertTrue(Files.size(outDir.resolve("ds1-arena-duel-portrait.png")) > 4000);
        } catch (final java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void renderLayout(final boolean landscape, final Path file) {
        final int w = landscape ? 1280 : 720;
        final int h = landscape ? 720 : 1280;
        final ConfigData cfg = Config.instance().getConfigData();
        final ArenaDuelLayout.Result layout = ArenaDuelLayout.compute(w, h, landscape,
                cfg.ds1PhaseRailFraction, cfg.ds1HandFraction, cfg.ds1StackFraction,
                landscape ? 56f : 64f);
        Assert.assertNull(ArenaDuelLayout.firstOverlap(layout.leafRegions()),
                "screenshot layout must stay pairwise-clear");

        final Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0.08f, 0.1f, 0.12f, 1f));
        pixmap.fill();
        fill(pixmap, layout.get("menuBar"), new Color(0.12f, 0.14f, 0.18f, 1f));
        fill(pixmap, layout.get("phaseRail"), new Color(0.18f, 0.2f, 0.28f, 1f));
        fill(pixmap, layout.get("opponentField"), new Color(0.22f, 0.32f, 0.48f, 1f));
        fill(pixmap, layout.get("opponentAvatar"), new Color(0.55f, 0.28f, 0.28f, 1f));
        fill(pixmap, layout.get("opponentPiles"), new Color(0.28f, 0.28f, 0.35f, 1f));
        fill(pixmap, layout.get("stackColumn"), new Color(0.55f, 0.48f, 0.22f, 1f));
        fill(pixmap, layout.get("playerField"), new Color(0.22f, 0.42f, 0.32f, 1f));
        fill(pixmap, layout.get("playerAvatar"), new Color(0.28f, 0.45f, 0.55f, 1f));
        fill(pixmap, layout.get("playerPiles"), new Color(0.28f, 0.28f, 0.35f, 1f));
        fill(pixmap, layout.get("playerHand"), new Color(0.42f, 0.32f, 0.22f, 1f));
        fill(pixmap, layout.get("contextButton"), new Color(0.75f, 0.45f, 0.18f, 1f));
        fill(pixmap, layout.get("takeBackSlot"), new Color(0.55f, 0.28f, 0.28f, 1f));
        fill(pixmap, layout.get("promptBar"), new Color(0.15f, 0.16f, 0.18f, 1f));
        fill(pixmap, layout.get("passUntilEot"), new Color(0.35f, 0.38f, 0.55f, 1f));

        // Marker ticks so regions remain identifiable without fonts.
        tick(pixmap, layout.get("stackColumn"));
        tick(pixmap, layout.get("contextButton"));
        tick(pixmap, layout.get("playerHand"));

        PixmapIO.writePNG(Gdx.files.absolute(file.toAbsolutePath().toString()), pixmap);
        pixmap.dispose();
    }

    private static void fill(final Pixmap pm, final ArenaDuelLayout.Rect r, final Color c) {
        if (r == null || r.w <= 0 || r.h <= 0) {
            return;
        }
        pm.setColor(c);
        pm.fillRectangle(Math.round(r.x), Math.round(r.y), Math.round(r.w), Math.round(r.h));
        pm.setColor(Color.WHITE);
        pm.drawRectangle(Math.round(r.x), Math.round(r.y), Math.round(r.w), Math.round(r.h));
    }

    private static void tick(final Pixmap pm, final ArenaDuelLayout.Rect r) {
        if (r == null) {
            return;
        }
        pm.setColor(Color.YELLOW);
        final int cx = Math.round(r.x + r.w / 2f);
        final int cy = Math.round(r.y + r.h / 2f);
        pm.fillRectangle(cx - 6, cy - 2, 12, 4);
        pm.fillRectangle(cx - 2, cy - 6, 4, 12);
    }
}
