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
import forge.assets.FSkinFont;
import forge.gamemodes.match.YieldController;
import forge.interfaces.IGameController;
import forge.menu.FMenuBar;
import forge.screens.match.views.VArenaStackColumn;
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
    private final VArenaStackColumn stackColumn;
    private final FLabel btnContext;
    private final FLabel btnPassUntilEot;
    private ArenaDuelLayout.Result lastLayout;

    public ArenaDuelScreen(final List<VPlayerPanel> playerPanels0) {
        super(playerPanels0);
        stackColumn = add(new VArenaStackColumn());
        btnContext = add(new FLabel.Builder()
                .text("Pass")
                .font(FSkinFont.get(16))
                .align(Align.center)
                .command(this::onContext)
                .build());
        btnPassUntilEot = add(new FLabel.Builder()
                .text(ArenaContextActions.passUntilEotLabel())
                .font(FSkinFont.get(12))
                .align(Align.center)
                .command(this::onPassUntilEot)
                .build());
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
        final float handF = cfg == null ? 0.28f : cfg.ds1HandFraction;
        final float stackF = cfg == null ? 0.48f : cfg.ds1StackFraction;
        final float promptH = Math.max(VPrompt.HEIGHT, Utils.scale(landscape ? 52f : 60f));

        lastLayout = ArenaDuelLayout.compute(width, height, landscape, railF, handF, stackF, promptH);

        // Menu bar across the top.
        final ArenaDuelLayout.Rect menu = lastLayout.get("menuBar");
        if (getHeader() != null && menu != null) {
            getHeader().setBounds(menu.x, menu.y, menu.w, menu.h);
            if (getHeader() instanceof FMenuBar) {
                getHeader().setRotate90(false);
            }
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
            stackColumn.refresh();
        }

        final ArenaDuelLayout.Rect ctx = lastLayout.get("contextButton");
        final ArenaDuelLayout.Rect eot = lastLayout.get("passUntilEot");
        final ArenaDuelLayout.Rect prompt = lastLayout.get("promptBar");
        final ArenaDuelLayout.Rect take = lastLayout.get("takeBackSlot");
        if (ctx != null) {
            btnContext.setBounds(ctx.x, ctx.y, ctx.w, ctx.h);
        }
        if (eot != null) {
            btnPassUntilEot.setBounds(eot.x, eot.y, eot.w, eot.h);
        }
        // Classic VPrompt still hosts OK/Cancel/Take back + message (fallback dialogs).
        // Stretch it across promptBar+takeBack so DS4 Take back stays next to the context button.
        final VPrompt bottom = getBottomPlayerPrompt();
        if (bottom != null && prompt != null && take != null) {
            final float x = take.x;
            final float w = (prompt.x + prompt.w) - take.x;
            bottom.setBounds(x, prompt.y, w, prompt.h);
        }
        final VPrompt top = getTopPlayerPrompt();
        if (top != null) {
            top.setVisible(false); // Arena is single-human layout for PR1
        }

        refreshContextLabel();
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
    }

    @Override
    protected void drawOverlay(final Graphics g) {
        super.drawOverlay(g);
        // Soft centre-lane frame so the stack column reads as its own plane.
        if (lastLayout != null) {
            final ArenaDuelLayout.Rect centre = lastLayout.get("centreLane");
            if (centre != null) {
                g.drawRect(Utils.scale(1), new Color(0.9f, 0.85f, 0.55f, 0.35f),
                        centre.x, centre.y, centre.w, centre.h);
            }
        }
        refreshContextLabel();
        if (stackColumn != null && stackColumn.isVisible()) {
            stackColumn.refresh();
        }
    }

    private void refreshContextLabel() {
        if (btnContext == null) {
            return;
        }
        final VPrompt prompt = getActivePrompt();
        final String ok = prompt == null || prompt.getBtnOk() == null ? null : prompt.getBtnOk().getText();
        final boolean stackNonEmpty = MatchController.instance != null
                && MatchController.instance.getGameView() != null
                && MatchController.instance.getGameView().getStack() != null
                && !MatchController.instance.getGameView().getStack().isEmpty();
        final boolean atk = MatchController.instance != null
                && MatchController.instance.isCombatDeclareAttackersInput();
        final boolean blk = MatchController.instance != null
                && MatchController.instance.isCombatDeclareBlockersInput();
        final ArenaContextActions.ContextKind kind =
                ArenaContextActions.resolve(ok, stackNonEmpty, atk, blk);
        btnContext.setText(ArenaContextActions.label(kind));
        final boolean enabled = prompt != null && prompt.getBtnOk() != null && prompt.getBtnOk().isEnabled();
        btnContext.setEnabled(enabled);
    }

    private void onContext(final FEvent e) {
        final IGameController gc = MatchController.instance == null
                ? null : MatchController.instance.getGameController();
        if (gc != null) {
            gc.selectButtonOk();
        }
    }

    private void onPassUntilEot(final FEvent e) {
        final IGameController gc = MatchController.instance == null
                ? null : MatchController.instance.getGameController();
        if (gc != null) {
            YieldController.endTurn(gc, MatchController.instance.getCurrentPlayer());
        }
    }

    /** Controller: map focus across Arena-only chrome (context / pass-until-EOT). */
    public FDisplayObject arenaContextControl() {
        return btnContext;
    }

    public FDisplayObject arenaPassUntilEotControl() {
        return btnPassUntilEot;
    }
}
