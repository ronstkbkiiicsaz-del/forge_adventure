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
import java.util.function.Consumer;

/**
 * SN1 GL screenshots (opt-in {@code gl} group under xvfb). Renders Rest, season
 * status, and HUD labels at their production bounds in landscape and portrait.
 */
public class SeasonsSn1GlTest {

    private static final Path ARTIFACTS = Path.of("/opt/cursor/artifacts/screenshots");

    @Test(groups = "gl", timeOut = 300_000)
    public void seasonUiScreenshotsLandscapeAndPortrait() throws Exception {
        AdventureGlTestSupport.runOnGl(() -> {
            Assert.assertTrue(Config.ascendant(), "GL support boots Ascendant");
            try {
                Files.createDirectories(ARTIFACTS);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
            try {
                forge.adventure.coop.CoopSession.get().testClearGuestPlaneFollow();
                forge.adventure.coop.CoopSession.get().testClearHostingForHello();
            } catch (Throwable ignored) {
            }
            StandardWindow window = new StandardWindow();
            window.init(java.util.List.of("M11", "ZEN"));
            AdventureSeason season = new AdventureSeason();
            season.initNewGame(window);
            Assert.assertTrue(season.isConfigured(), "SN1 must be configured under Ascendant GL boot");
            String hud = season.hudLine(window);
            String status = season.statusLine(window);
            Assert.assertFalse(hud == null || hud.isEmpty(), "hudLine empty");
            Assert.assertFalse(status == null || status.isEmpty(), "statusLine empty");

            // One screen per capture so Rest / Status / HUD are not stacked.
            renderAndSave(true, "sn1_inn_rest_landscape.png", stage -> {
                float[] rest = InnScene.restStageBounds(true, 270f);
                TextraButton restBtn = Controls.newTextButton("Rest");
                restBtn.setBounds(rest[0], rest[1], rest[2], rest[3]);
                stage.addActor(restBtn);
                addCaption(stage, "SN1 Inn Rest — landscape");
            });
            renderAndSave(false, "sn1_inn_rest_portrait.png", stage -> {
                float[] rest = InnScene.restStageBounds(false, 480f);
                TextraButton restBtn = Controls.newTextButton("Rest");
                restBtn.setBounds(rest[0], rest[1], rest[2], rest[3]);
                stage.addActor(restBtn);
                addCaption(stage, "SN1 Inn Rest — portrait");
            });
            renderAndSave(true, "sn1_status_season_landscape.png", stage -> {
                float[] b = PlayerStatisticScene.seasonStatusStageBounds(true, 270f);
                TypingLabel lbl = Controls.newTypingLabel("[%80]" + status);
                lbl.setBounds(b[0], b[1], b[2], b[3]);
                lbl.setColor(Color.LIGHT_GRAY);
                lbl.skipToTheEnd();
                stage.addActor(lbl);
                addCaption(stage, "SN1 Status season — landscape");
            });
            renderAndSave(false, "sn1_status_season_portrait.png", stage -> {
                float[] b = PlayerStatisticScene.seasonStatusStageBounds(false, 480f);
                TypingLabel lbl = Controls.newTypingLabel("[%80]" + status);
                lbl.setBounds(b[0], b[1], b[2], b[3]);
                lbl.setColor(Color.LIGHT_GRAY);
                lbl.skipToTheEnd();
                stage.addActor(lbl);
                addCaption(stage, "SN1 Status season — portrait");
            });
            renderAndSave(true, "sn1_hud_season_landscape.png", stage -> {
                float[] b = GameHUD.seasonHudStageBounds(true, 270f);
                TypingLabel lbl = Controls.newTypingLabel("[%75]" + hud);
                lbl.setBounds(b[0], b[1], b[2], b[3]);
                lbl.setColor(Color.WHITE);
                lbl.skipToTheEnd();
                stage.addActor(lbl);
                addCaption(stage, "SN1 HUD season — landscape");
            });
            renderAndSave(false, "sn1_hud_season_portrait.png", stage -> {
                float[] b = GameHUD.seasonHudStageBounds(false, 480f);
                TypingLabel lbl = Controls.newTypingLabel("[%75]" + hud);
                lbl.setBounds(b[0], b[1], b[2], b[3]);
                lbl.setColor(Color.WHITE);
                lbl.skipToTheEnd();
                stage.addActor(lbl);
                addCaption(stage, "SN1 HUD season — portrait");
            });
        });
    }

    private static void addCaption(Stage stage, String text) {
        Table caption = new Table(Controls.getSkin());
        caption.setFillParent(true);
        caption.top().pad(8);
        caption.add(Controls.newTextraLabel(text)).row();
        stage.addActor(caption);
    }

    private static void renderAndSave(boolean landscape, String fileName, Consumer<Stage> build) {
        int w = landscape ? 480 : 270;
        int h = landscape ? 270 : 480;
        Gdx.graphics.setWindowedMode(w, h);
        Stage stage = new Stage(new ScreenViewport());
        try {
            Gdx.gl.glClearColor(0.15f, 0.16f, 0.18f, 1f);
            Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
            build.accept(stage);
            stage.getViewport().update(w, h, true);
            stage.act(0f);
            stage.draw();

            int gw = Gdx.graphics.getWidth();
            int gh = Gdx.graphics.getHeight();
            Pixmap pm = ScreenUtils.getFrameBufferPixmap(0, 0, gw, gh);
            try {
                Pixmap flipped = new Pixmap(pm.getWidth(), pm.getHeight(), pm.getFormat());
                for (int y = 0; y < pm.getHeight(); y++) {
                    for (int x = 0; x < pm.getWidth(); x++) {
                        flipped.drawPixel(x, pm.getHeight() - 1 - y, pm.getPixel(x, y));
                    }
                }
                File out = ARTIFACTS.resolve(fileName).toFile();
                PixmapIO.writePNG(new com.badlogic.gdx.files.FileHandle(out), flipped);
                flipped.dispose();
                Assert.assertTrue(out.isFile() && out.length() > 500,
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
