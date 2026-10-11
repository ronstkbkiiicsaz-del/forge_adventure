/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.screens.match;

import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import forge.Forge;
import forge.Graphics;
import forge.adventure.data.ConfigData;
import forge.adventure.util.Config;
import forge.assets.FSkinColor;
import forge.assets.FSkinFont;
import forge.assets.FSkinTexture;
import forge.gamemodes.match.YieldController;
import forge.interfaces.IGameController;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.match.views.VArenaStackColumn;
import forge.screens.match.views.VPhaseIndicator;
import forge.screens.match.views.VPlayerPanel;
import forge.screens.match.views.VPrompt;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FEvent;
import forge.toolbox.FLabel;
import forge.toolbox.FScrollPane;
import forge.util.Utils;

/**
 * DS1 PR1: Arena-style duel screen skeleton for Ascendant. Same view/controller
 * seam as {@link MatchScreen} (GameView / selectCard / selectButtonOK); every
 * prompt without a custom view falls back to the existing Forge dialogs.
 * Gestures from the prior DS1 work (drag, peek, pad) stay available via
 * {@link ModernDuelController}.
 */
public class ArenaDuelScreen extends MatchScreen {
    private static final Color CENTRE_STROKE = new Color(0.75f, 0.70f, 0.40f, 1f);
    private static final Color BACKDROP_PLATE = new Color(0.10f, 0.16f, 0.12f, 1f);
    private static final Color PROMPT_BTN_FACE = new Color(0.28f, 0.34f, 0.44f, 1f);
    private static final Color PROMPT_BTN_BORDER = new Color(0.85f, 0.88f, 0.94f, 1f);
    private static final Color PROMPT_BTN_INK = new Color(1f, 1f, 1f, 1f);
    /** Absolute (unscaled) sizes so xvfb AVG_FINGER cannot blow captions into "…". */
    private static final FSkinFont PROMPT_BTN_FONT = FSkinFont._get(18);
    private static final FSkinFont PROMPT_MSG_FONT = FSkinFont._get(16);

    private final VArenaStackColumn stackColumn;
    private final FLabel btnContext;
    private final FLabel btnCancel;
    private final FLabel btnPassUntilEot;
    private final FDisplayObject promptChromePainter;
    private ArenaDuelLayout.Result lastLayout;
    private int lastStackSize = -1;

    public ArenaDuelScreen(final List<VPlayerPanel> playerPanels0) {
        super(playerPanels0);
        stackColumn = add(new VArenaStackColumn());
        final FSkinColor btnFace = FSkinColor.getStandardColor(new Color(0.22f, 0.28f, 0.36f, 1f));
        final FSkinColor btnFaceDim = FSkinColor.getStandardColor(new Color(0.18f, 0.22f, 0.28f, 1f));
        final FSkinColor btnInk = FSkinColor.getStandardColor(240, 240, 245);
        // Hit targets only — captions are painted by promptChromePainter above overlays
        // so FLabel theme / scale cannot collapse them to ellipsis.
        btnContext = add(new FLabel.Builder()
                .text("")
                .font(PROMPT_BTN_FONT)
                .align(Align.center)
                .opaque(true)
                .pressedColor(btnFace)
                .textColor(btnInk)
                .command(this::onContext)
                .build());
        btnCancel = add(new FLabel.Builder()
                .text("")
                .font(PROMPT_BTN_FONT)
                .align(Align.center)
                .opaque(true)
                .pressedColor(btnFace)
                .textColor(btnInk)
                .command(this::onCancel)
                .build());
        btnPassUntilEot = add(new FLabel.Builder()
                .text("")
                .font(PROMPT_BTN_FONT)
                .align(Align.center)
                .opaque(true)
                .pressedColor(btnFaceDim)
                .textColor(btnInk)
                .command(this::onPassUntilEot)
                .build());
        promptChromePainter = add(new FDisplayObject() {
            @Override
            protected boolean drawAboveOverlay() {
                return true;
            }

            @Override
            public void draw(final Graphics g) {
                paintPromptChrome(g);
            }
        });
        promptChromePainter.setBounds(0, 0, 1, 1);
        // Controller: Left Trigger still fires VPrompt OK (same selectButtonOk seam);
        // these labels are touch/mouse/primary targets and stay in the pad tab order
        // via FScreen child traversal once focused.
    }

    @Override
    public boolean isArenaDuelScreen() {
        return true;
    }

    public ArenaDuelLayout.Result getLastLayout() {
        return lastLayout;
    }

    public VArenaStackColumn getArenaStackColumn() {
        return stackColumn;
    }

    @Override
    protected void layoutMatchChrome(final float startY, final float width, final float height) {
        final boolean landscape = Forge.isLandscapeMode();
        final ConfigData cfg = Config.instance() == null ? null : Config.instance().getConfigData();
        final float railF = cfg == null ? 0.07f : cfg.ds1PhaseRailFraction;
        float handF = cfg == null ? 0.28f : cfg.ds1HandFraction;
        // Landscape: keep a taller hand band so fanned cards stay card-aspect and fully on screen.
        if (landscape) {
            handF = Math.max(handF, 0.34f);
        }
        final float stackF = cfg == null ? 0.48f : cfg.ds1StackFraction;
        // Do not use Utils.scale / VPrompt.HEIGHT here: both bake in the GL window's
        // AVG_FINGER (often ~24px under xvfb), which collapses the Arena prompt into a
        // clipped classic widget strip. Absolute dp keeps chrome readable in captures.
        final float promptH = landscape ? 64f : 72f;

        lastLayout = ArenaDuelLayout.compute(width, height, landscape, railF, handF, stackF, promptH);

        // Classic FMenuBar in the Arena menuBand collapses to a ~36px strip of tiny
        // tick-mark tab labels (Game/Log/Dev/Stack). Hide it for Arena — Game menu
        // remains reachable via Escape / gamepad system controls.
        final ArenaDuelLayout.Rect menu = lastLayout.get("menuBar");
        if (getHeader() != null) {
            getHeader().setVisible(false);
            getHeader().setBounds(0, 0, 0, 0);
        }

        // Scroller fills opponent + player field bands; centre gap is for the stack column.
        final ArenaDuelLayout.Rect opp = lastLayout.get("opponentArea");
        final ArenaDuelLayout.Rect player = lastLayout.get("playerArea");
        final ArenaDuelLayout.Rect centre = lastLayout.get("centreLane");
        final FScrollPane scroller = getFieldScroller();
        if (opp != null && player != null && centre != null && scroller != null) {
            final float scrollerY = opp.y;
            final float scrollerH = opp.h + centre.h + player.h;
            scroller.setBounds(opp.x, scrollerY, opp.w, scrollerH);
        }

        final ArenaDuelLayout.Rect stackR = lastLayout.get("stackColumn");
        if (stackR != null) {
            stackColumn.setBounds(stackR.x, stackR.y, stackR.w, stackR.h);
            stackColumn.setVisible(true);
            refreshStackIfChanged(true);
        }

        layoutPromptButtons();
        layoutPhaseRail();
        // Keep prompt chrome above scroller / hand / phase rail in draw order.
        bringPromptChromeToFront();

        // Classic VPrompt hosts message + DS4 Take back. Context / Cancel / Pass-until-EOT
        // replace the stock OK/Cancel buttons so they don't render as a tiny clipped row.
        final VPrompt bottom = getBottomPlayerPrompt();
        final ArenaDuelLayout.Rect prompt = lastLayout.get("promptBar");
        final ArenaDuelLayout.Rect take = lastLayout.get("takeBackSlot");
        if (bottom != null && prompt != null && take != null) {
            final float x = take.x;
            final float w = (prompt.x + prompt.w) - take.x;
            bottom.setBounds(x, prompt.y, w, prompt.h);
            if (bottom.getBtnOk() != null) {
                bottom.getBtnOk().setVisible(false);
            }
            if (bottom.getBtnCancel() != null) {
                bottom.getBtnCancel().setVisible(false);
            }
        }
        // Arena: never show the second human's classic prompt strip under the menuBand
        // (MODE_ALWAYS used to park a tiny Upk/Drw-like row at y≈0–36).
        final VPrompt top = getTopPlayerPrompt();
        if (top != null) {
            top.setVisible(false);
            top.setBounds(0, 0, 0, 0);
        }

        // Do NOT force Hand here — revalidate runs after every pile tab tap
        // (setSelectedTab → revalidate) and would immediately close GY/exile/library.
        // Hand is restored on prompt change only from the draw path (not revalidate).

        // Classic VStack dropdown is replaced by VArenaStackColumn — keep it hidden so a
        // spell on the stack cannot reopen the leftover green card strip at top-left.
        if (getStack() != null) {
            getStack().hide();
        }

        refreshContextLabels(false);
    }

    private void layoutPromptButtons() {
        if (lastLayout == null) {
            return;
        }
        final ArenaDuelLayout.Rect ctx = lastLayout.get("contextButton");
        final ArenaDuelLayout.Rect cancel = lastLayout.get("cancelButton");
        final ArenaDuelLayout.Rect eot = lastLayout.get("passUntilEot");
        if (ctx != null) {
            btnContext.setBounds(ctx.x, ctx.y, ctx.w, ctx.h);
            btnContext.setVisible(true);
        }
        if (cancel != null) {
            btnCancel.setBounds(cancel.x, cancel.y, cancel.w, cancel.h);
        }
        if (eot != null) {
            btnPassUntilEot.setBounds(eot.x, eot.y, eot.w, eot.h);
            btnPassUntilEot.setVisible(true);
        }
    }

    private void bringPromptChromeToFront() {
        if (btnContext != null) {
            remove(btnContext);
            add(btnContext);
        }
        if (btnCancel != null) {
            remove(btnCancel);
            add(btnCancel);
        }
        if (btnPassUntilEot != null) {
            remove(btnPassUntilEot);
            add(btnPassUntilEot);
        }
        // Painter last + drawAboveOverlay so captions win over any earlier overlay flush.
        if (promptChromePainter != null) {
            remove(promptChromePainter);
            add(promptChromePainter);
            promptChromePainter.setBounds(0, 0, getWidth(), getHeight());
        }
    }

    /**
     * Host the local player's phase indicator across the full Arena phaseRail band.
     * One full-height rail keeps UP/DR/M1… captions readable; the opponent's classic
     * strip stays suppressed via {@link VPhaseIndicator#setArenaRailHosted}.
     */
    private void layoutPhaseRail() {
        if (lastLayout == null) {
            return;
        }
        final ArenaDuelLayout.Rect rail = lastLayout.get("phaseRail");
        final VPlayerPanel top = getTopPlayerPanel();
        final VPlayerPanel bottom = getBottomPlayerPanel();
        if (rail == null || top == null || bottom == null) {
            return;
        }
        // One full rail for the local (bottom) player — readable UP/DR/M1… labels.
        suppressPhaseRail(top.getPhaseIndicator());
        hostPhaseInRail(bottom.getPhaseIndicator(), rail);
    }

    private void suppressPhaseRail(final VPhaseIndicator phase) {
        if (phase == null) {
            return;
        }
        phase.setVisible(false);
        phase.setBounds(0, 0, 0, 0);
        // Keep hosted=false so arenaPhaseChromeSuppressed hides any leftover strip.
        if (phase.isArenaRailHosted()) {
            remove(phase);
            phase.setArenaRailHosted(false);
        }
    }

    private void hostPhaseInRail(final VPhaseIndicator phase, final ArenaDuelLayout.Rect rail) {
        if (phase == null) {
            return;
        }
        if (!phase.isArenaRailHosted()) {
            final VPlayerPanel top = getTopPlayerPanel();
            final VPlayerPanel bottom = getBottomPlayerPanel();
            if (top != null) {
                top.remove(phase);
            }
            if (bottom != null) {
                bottom.remove(phase);
            }
            if (indexOf(phase) < 0) {
                add(phase);
            }
            phase.setArenaRailHosted(true);
        }
        phase.setVisible(true);
        phase.resetFont();
        phase.setBounds(rail.x, rail.y, rail.w, rail.h);
    }

    @Override
    protected void layoutPlayerPanelsInScroller(final float visibleWidth, final float totalHeight) {
        final VPlayerPanel top = getTopPlayerPanel();
        final VPlayerPanel bottom = getBottomPlayerPanel();
        if (top == null || bottom == null || lastLayout == null) {
            super.layoutPlayerPanelsInScroller(visibleWidth, totalHeight);
            return;
        }
        final ArenaDuelLayout.Rect opp = lastLayout.get("opponentArea");
        final ArenaDuelLayout.Rect player = lastLayout.get("playerArea");
        final ArenaDuelLayout.Rect centre = lastLayout.get("centreLane");
        if (opp == null || player == null || centre == null) {
            super.layoutPlayerPanelsInScroller(visibleWidth, totalHeight);
            return;
        }
        // Panels are positioned relative to the scroller (opp.x/opp.y is screen-space).
        top.setBounds(0, 0, visibleWidth, opp.h);
        // Leave the centre band empty inside the scroller (stack column draws above it).
        bottom.setBounds(0, opp.h + centre.h, visibleWidth, player.h);
        // Draw opponent first, local player last. Classic MatchScreen adds bottom then top,
        // so the flipped opponent panel painted after the local panel and wiped the
        // player-field plate into FBO alpha holes (flat dark void in landscape).
        final FScrollPane scroller = getFieldScroller();
        if (scroller != null && scroller.indexOf(bottom) >= 0 && scroller.indexOf(top) >= 0
                && scroller.indexOf(bottom) < scroller.indexOf(top)) {
            scroller.remove(bottom);
            scroller.add(bottom);
        }

        // Panel-local slots: convert screen-space Arena rects into panel space.
        final float scrollerH = opp.h + centre.h + player.h;
        applyPanelSlots(top, opp,
                lastLayout.get("opponentAvatar"), lastLayout.get("opponentPiles"),
                lastLayout.get("opponentField"), null, scrollerH);
        applyPanelSlots(bottom, player,
                lastLayout.get("playerAvatar"), lastLayout.get("playerPiles"),
                lastLayout.get("playerField"), lastLayout.get("playerHand"), scrollerH);
    }

    private static void applyPanelSlots(final VPlayerPanel panel, final ArenaDuelLayout.Rect area,
            final ArenaDuelLayout.Rect avatar, final ArenaDuelLayout.Rect piles,
            final ArenaDuelLayout.Rect field, final ArenaDuelLayout.Rect hand,
            final float scrollerH) {
        if (panel == null || area == null) {
            return;
        }
        panel.applyArenaSlots(
                toLocal(avatar, area),
                toLocal(piles, area),
                toLocal(field, area),
                hand == null ? null : toLocal(hand, area),
                scrollerH);
    }

    private static ArenaDuelLayout.Rect toLocal(final ArenaDuelLayout.Rect screen,
            final ArenaDuelLayout.Rect area) {
        if (screen == null) {
            return null;
        }
        return new ArenaDuelLayout.Rect(screen.name,
                screen.x - area.x, screen.y - area.y, screen.w, screen.h);
    }

    @Override
    protected void drawBackground(final Graphics g) {
        // Full-bleed match backdrop in both orientations. Skip FScreen's BG_TEXTURE
        // path. Prefer the same source MatchScreen.getBG() uses so adventure / day /
        // night stay consistent with portrait. Avoid a translucent fillRect for the
        // darken pass — batched rects with a<1 leave broken framebuffer alpha that
        // later opaque children wipe into a flat void in landscape captures.
        final float w = getWidth();
        final float h = getHeight();
        g.resetAlphaComposite();
        // Full-bleed forest-biased plate (same UV as FieldScroller / panel plates).
        g.fillRect(BACKDROP_PLATE, 0, 0, w, h);
        final FSkinTexture matchBG = resolveMatchBackground();
        if (matchBG.getWidth() > 0) {
            g.drawImage(matchBG, 0, -h * 0.08f, w, h / 0.42f);
        }
        // No setAlphaComposite darken: multiplicative alpha punches FBO holes that the
        // GL capture composites to a flat void (centre lane / player field in landscape).
        g.resetAlphaComposite();
    }

    /** Prefer ADV_BG_MATCH for readable contrast in both orientations. */
    private FSkinTexture resolveMatchBackground() {
        if (FSkinTexture.ADV_BG_MATCH.getWidth() > 0) {
            return FSkinTexture.ADV_BG_MATCH;
        }
        if (Forge.isMobileAdventureMode) {
            return getBG();
        }
        if (MatchController.instance != null && MatchController.instance.getDayTime() != null) {
            return "Day".equals(MatchController.instance.getDayTime())
                    ? FSkinTexture.BG_MATCH_DAY : FSkinTexture.BG_MATCH_NIGHT;
        }
        return FSkinTexture.BG_MATCH;
    }

    @Override
    protected void drawOverlay(final Graphics g) {
        super.drawOverlay(g);
        // Soft centre-lane frame so the stack column reads as its own plane.
        // Keep the stroke opaque (a=1) — translucent drawRect punches FBO alpha.
        if (lastLayout != null) {
            final ArenaDuelLayout.Rect centre = lastLayout.get("centreLane");
            if (centre != null) {
                g.drawRect(Utils.scale(1), CENTRE_STROKE, centre.x, centre.y, centre.w, centre.h);
            }
        }
        refreshContextLabels();
        refreshStackIfChanged(false);
    }

    /** Opaque faces + captions for Arena prompt chrome (also used by promptChromePainter). */
    private void paintPromptChrome(final Graphics g) {
        refreshContextLabels();
        paintPromptChromeFace(g, btnContext, contextCaption);
        paintPromptChromeFace(g, btnCancel, cancelCaption);
        paintPromptChromeFace(g, btnPassUntilEot, ArenaContextActions.passUntilEotLabel());
        paintPromptMessage(g);
    }

    private void paintPromptChromeFace(final Graphics g, final FLabel btn, final String caption) {
        if (btn == null || !btn.isVisible() || btn.getWidth() < 2f || btn.getHeight() < 2f) {
            return;
        }
        final float x = btn.getLeft();
        final float y = btn.getTop();
        final float w = btn.getWidth();
        final float h = btn.getHeight();
        g.resetAlphaComposite();
        g.fillRect(PROMPT_BTN_FACE, x, y, w, h);
        g.drawRect(2f, PROMPT_BTN_BORDER, x, y, w, h);
        String text = caption;
        if (text == null || text.isEmpty() || "…".equals(text) || "...".equals(text)) {
            text = btn == btnCancel ? "Cancel"
                    : btn == btnPassUntilEot ? ArenaContextActions.passUntilEotLabel()
                    : "OK";
        }
        g.drawText(text, PROMPT_BTN_FONT, PROMPT_BTN_INK,
                x + 4f, y, w - 8f, h, false, Align.center, true);
    }

    private void paintPromptMessage(final Graphics g) {
        if (lastLayout == null) {
            return;
        }
        final ArenaDuelLayout.Rect prompt = lastLayout.get("promptBar");
        if (prompt == null || prompt.w < 8f || prompt.h < 8f) {
            return;
        }
        final VPrompt vp = getActivePrompt();
        String msg = vp == null ? null : vp.getMessage();
        if (msg == null || msg.isEmpty()) {
            return;
        }
        // Strip mana braces for the chrome line so fitText stays readable.
        msg = msg.replace("{", "").replace("}", "");
        g.resetAlphaComposite();
        g.drawText(msg, PROMPT_MSG_FONT, PROMPT_BTN_INK,
                prompt.x + 4f, prompt.y, prompt.w - 8f, prompt.h, true, Align.center, true);
    }

    private String contextCaption = "OK";
    private String cancelCaption = "Cancel";
    /** Last prompt message seen — used to restore Hand only on prompt change. */
    private String lastPromptMessageForHand;

    private void refreshStackIfChanged(final boolean force) {
        if (stackColumn == null || !stackColumn.isVisible()) {
            return;
        }
        int size = 0;
        if (MatchController.instance != null
                && MatchController.instance.getGameView() != null
                && MatchController.instance.getGameView().getStack() != null) {
            size = MatchController.instance.getGameView().getStack().size();
        }
        if (force || size != lastStackSize) {
            lastStackSize = size;
            stackColumn.refresh();
        }
    }

    private void refreshContextLabels() {
        refreshContextLabels(true);
    }

    /**
     * @param restoreHandOnPromptChange when false (layout/revalidate), only refresh
     *        Cancel/OK chrome — never yank an open pile closed via setSelectedTab→revalidate.
     */
    private void refreshContextLabels(final boolean restoreHandOnPromptChange) {
        if (btnContext == null) {
            return;
        }
        final VPrompt prompt = getActivePrompt();
        final String ok = prompt == null || prompt.getBtnOk() == null ? null : prompt.getBtnOk().getText();
        final String cancel = prompt == null || prompt.getBtnCancel() == null
                ? null : prompt.getBtnCancel().getText();
        // Captions live on fields; FLabel text stays empty so theme draw cannot ellipsize.
        contextCaption = sanitizeCaption(ok, "OK");
        final boolean okEnabled = prompt != null && prompt.getBtnOk() != null && prompt.getBtnOk().isEnabled();
        btnContext.setEnabled(okEnabled);

        // Classic OK/Cancel are force-hidden for Arena layout, so do not trust
        // isVisible() — the game enables Cancel when mulligan/target/mana/etc. need it.
        final boolean cancelWanted = ArenaDuelUi.isCancelPromptArmed(prompt);
        btnCancel.setVisible(cancelWanted);
        if (cancelWanted) {
            cancelCaption = sanitizeCaption(cancel, "Cancel");
            btnCancel.setEnabled(true);
        }

        if (restoreHandOnPromptChange) {
            maybeRestoreHandOnPromptChange(prompt);
        }
    }

    /** @deprecated use {@link ArenaDuelUi#isCancelPromptArmed(VPrompt)} */
    public static boolean isCancelPromptArmed(final VPrompt prompt) {
        return ArenaDuelUi.isCancelPromptArmed(prompt);
    }

    /** @deprecated use {@link ArenaDuelUi#isCancelPromptArmed(boolean, String)} */
    public static boolean isCancelPromptArmed(final boolean cancelEnabled, final String cancelText) {
        return ArenaDuelUi.isCancelPromptArmed(cancelEnabled, cancelText);
    }

    /** Restore the local hand band when the prompt message changes (new decision). */
    private void maybeRestoreHandOnPromptChange(final VPrompt prompt) {
        final String msg = prompt == null ? null : prompt.getMessage();
        final String key = msg == null ? "" : msg;
        if (lastPromptMessageForHand == null) {
            // First observation only — never yank a pile open via setSelectedTab→revalidate.
            lastPromptMessageForHand = key;
            return;
        }
        if (key.equals(lastPromptMessageForHand)) {
            return;
        }
        lastPromptMessageForHand = key;
        final VPlayerPanel bottomPanel = getBottomPlayerPanel();
        if (bottomPanel != null) {
            bottomPanel.setSelectedZone(forge.game.zone.ZoneType.Hand);
        }
    }

    private static String sanitizeCaption(final String raw, final String fallback) {
        if (raw == null || raw.isEmpty() || "…".equals(raw) || "...".equals(raw)
                || "··".equals(raw) || "• • •".equals(raw)) {
            return fallback;
        }
        return raw;
    }

    /** Caption currently painted on the Cancel chrome (for GL / screenshot asserts). */
    public String arenaCancelCaption() {
        return cancelCaption;
    }

    /** Caption currently painted on the primary context chrome. */
    public String arenaContextCaption() {
        return contextCaption;
    }

    private void onContext(final FEvent e) {
        final IGameController gc = MatchController.instance == null
                ? null : MatchController.instance.getGameController();
        if (gc != null) {
            gc.selectButtonOk();
        }
    }

    private void onCancel(final FEvent e) {
        final IGameController gc = MatchController.instance == null
                ? null : MatchController.instance.getGameController();
        if (gc != null) {
            gc.selectButtonCancel();
        }
    }

    private void onPassUntilEot(final FEvent e) {
        final IGameController gc = MatchController.instance == null
                ? null : MatchController.instance.getGameController();
        if (gc != null) {
            YieldController.endTurn(gc, MatchController.instance.getCurrentPlayer());
        }
    }

    private static boolean isAlwaysModernMode() {
        if (FModel.getPreferences() == null) {
            return false;
        }
        final String mode = FModel.getPreferences().getPref(FPref.UI_MODERN_DUEL_SCREEN);
        return ModernDuelScreen.MODE_ALWAYS.equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    /** Controller: map focus across Arena-only chrome (context / cancel / pass-until-EOT). */
    public FDisplayObject arenaContextControl() {
        return btnContext;
    }

    public FDisplayObject arenaCancelControl() {
        return btnCancel;
    }

    public FDisplayObject arenaPassUntilEotControl() {
        return btnPassUntilEot;
    }
}
