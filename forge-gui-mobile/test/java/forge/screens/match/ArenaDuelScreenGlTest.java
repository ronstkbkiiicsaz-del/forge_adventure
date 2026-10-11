package forge.screens.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import forge.Forge;
import forge.Graphics;
import forge.adventure.AdventureGlTestSupport;
import forge.adventure.data.ConfigData;
import forge.adventure.util.Config;
import forge.game.player.PlayerView;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.match.views.VPlayerPanel;
import forge.trackable.Tracker;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in GL group: construct and draw the real {@link ArenaDuelScreen} under xvfb,
 * writing landscape + portrait PNGs. Excluded from default surefire
 * ({@code -Pgl-tests} / {@code xvfb-run}).
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
            Config.installConfigDataForTest(data);
            if (FModel.getPreferences() != null) {
                FModel.getPreferences().setPref(FPref.UI_MODERN_DUEL_SCREEN, ModernDuelScreen.MODE_ALWAYS);
            }
            ModernDuelScreen.invalidate();

            renderAndSave(true, outDir.resolve("ds1-arena-duel-landscape.png"));
            renderAndSave(false, outDir.resolve("ds1-arena-duel-portrait.png"));
            return null;
        });
        Assert.assertTrue(Files.isRegularFile(outDir.resolve("ds1-arena-duel-landscape.png")));
        Assert.assertTrue(Files.isRegularFile(outDir.resolve("ds1-arena-duel-portrait.png")));
        Assert.assertTrue(Files.size(outDir.resolve("ds1-arena-duel-landscape.png")) > 1000);
        Assert.assertTrue(Files.size(outDir.resolve("ds1-arena-duel-portrait.png")) > 1000);
    }

    private static void renderAndSave(final boolean landscape, final Path file) throws Exception {
        final int w = landscape ? 1280 : 720;
        final int h = landscape ? 720 : 1280;
        Gdx.graphics.setWindowedMode(w, h);
        Forge.isPortraitMode = !landscape;

        final Tracker tracker = new Tracker();
        final PlayerView local = new PlayerView(1, tracker);
        final PlayerView foe = new PlayerView(2, tracker);
        final List<VPlayerPanel> panels = new ArrayList<>();
        panels.add(new VPlayerPanel(local, true, 2));
        panels.add(new VPlayerPanel(foe, false, 2));

        final ArenaDuelScreen screen = new ArenaDuelScreen(panels);
        Assert.assertTrue(screen.isArenaDuelScreen());
        screen.setBounds(0, 0, w, h);
        screen.revalidate();

        final Graphics g = Forge.getGraphics();
        Assert.assertNotNull(g);
        g.begin(w, h);
        try {
            screen.draw(g);
        } finally {
            g.end();
        }

        Assert.assertNotNull(screen.getLastLayout(), "Arena layout must run during revalidate/draw");
        Assert.assertNotNull(screen.getLastLayout().get("stackColumn"));
        Assert.assertNotNull(screen.getLastLayout().get("contextButton"));
        Assert.assertNotNull(screen.getArenaStackColumn());

        final byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        final Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
        PixmapIO.writePNG(Gdx.files.absolute(file.toAbsolutePath().toString()), pixmap);
        pixmap.dispose();
    }
}
