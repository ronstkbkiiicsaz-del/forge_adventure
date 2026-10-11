package forge.adventure;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TypingLabel;
import forge.adventure.player.AdventureSeason;
import forge.adventure.player.StandardWindow;
import forge.adventure.scene.InnScene;
import forge.adventure.scene.PlayerStatisticScene;
import forge.adventure.stage.GameHUD;
import forge.adventure.util.Config;
import forge.adventure.util.Controls;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SN1 GL screenshots (opt-in {@code gl} group under xvfb). Renders the Rest button
 * and season status/HUD labels at their production bounds in landscape and portrait.
 */
public class SeasonsSn1GlTest {

    private static final Path ARTIFACTS = Path.of("/opt/cursor/artifacts/screenshots");

    @Test(groups = "gl", timeOut = 300_000)
    public void seasonUiScreenshotsLandscapeAndPortrait() {
        AdventureGlTestSupport.runOnGl(() -> {
            Assert.assertTrue(Config.ascendant(), "GL support boots Ascendant");
            Files.createDirectories(ARTIFACTS);

            StandardWindow window = new StandardWindow();
            window.init(java.util.List.of("M11", "ZEN"));
            AdventureSeason season = new AdventureSeason();
            season.initNewGame(window);
            String hud = season.hudLine(window);
            String status = season.statusLine(window);
            Assert.assertFalse(hud.isEmpty());
            Assert.assertFalse(status.isEmpty());

            renderAndSave(true, hud, status, "sn1_season_ui_landscape.png");
            renderAndSave(false, hud, status, "sn1_season_ui_portrait.png");
        });
    }

    private static void renderAndSave(boolean landscape, String hudLine, String statusLine, String fileName) {
        float w = landscape ? 480f : 270f;
        float h = landscape ? 270f : 480f;
        Stage stage = new Stage(new ScreenViewport());
        try {
            Gdx.gl.glClearColor(0.15f, 0.16f, 0.18f, 1f);
            Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);

            // Inn Rest button
            float[] rest = InnScene.restStageBounds(landscape, h);
            TextraButton restBtn = Controls.newTextButton("Rest");
            restBtn.setBounds(rest[0], rest[1], rest[2], rest[3]);
            restBtn.setColor(Color.WHITE);
            stage.addActor(restBtn);

            // Status season line
            float[] status = PlayerStatisticScene.seasonStatusStageBounds(landscape, h);
            TypingLabel statusLbl = Controls.newTypingLabel("[%80]" + statusLine);
            statusLbl.setBounds(status[0], status[1], status[2], status[3]);
            statusLbl.setColor(Color.LIGHT_GRAY);
            statusLbl.skipToTheEnd();
            stage.addActor(statusLbl);

            // HUD season line
            float[] hud = GameHUD.seasonHudStageBounds(landscape, h);
            TypingLabel hudLbl = Controls.newTypingLabel("[%75]" + hudLine);
            hudLbl.setBounds(hud[0], hud[1], hud[2], hud[3]);
            hudLbl.setColor(Color.WHITE);
            hudLbl.skipToTheEnd();
            stage.addActor(hudLbl);

            Table caption = new Table(Controls.getSkin());
            caption.setFillParent(true);
            caption.top().pad(8);
            caption.add(Controls.newTextraLabel(landscape ? "SN1 landscape" : "SN1 portrait")).row();
            stage.addActor(caption);

            stage.getViewport().update((int) w, (int) h, true);
            stage.act(0f);
            stage.draw();

            Pixmap pm = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            try {
                // Flip Y for a right-side-up PNG.
                Pixmap flipped = new Pixmap(pm.getWidth(), pm.getHeight(), pm.getFormat());
                for (int y = 0; y < pm.getHeight(); y++) {
                    for (int x = 0; x < pm.getWidth(); x++) {
                        flipped.drawPixel(x, pm.getHeight() - 1 - y, pm.getPixel(x, y));
                    }
                }
                File out = ARTIFACTS.resolve(fileName).toFile();
                PixmapIO.writePNG(new com.badlogic.gdx.files.FileHandle(out), flipped);
                flipped.dispose();
                Assert.assertTrue(out.isFile() && out.length() > 100,
                        "screenshot must be written: " + out);
            } finally {
                pm.dispose();
            }
        } catch (Exception e) {
            throw new RuntimeException("SN1 GL screenshot failed (" + fileName + "): " + e.getMessage(), e);
        } finally {
            stage.dispose();
        }
    }
}
