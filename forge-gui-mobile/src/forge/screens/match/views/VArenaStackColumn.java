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
package forge.screens.match.views;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import forge.Graphics;
import forge.assets.FSkinFont;
import forge.card.CardRenderer;
import forge.card.CardRenderer.CardStackPosition;
import forge.card.CardZoom;
import forge.game.card.CardView;
import forge.game.spellability.StackItemView;
import forge.screens.match.ArenaStackColumnLayout;
import forge.screens.match.MatchController;
import forge.toolbox.FContainer;
import forge.toolbox.FDisplayObject;
import forge.util.Utils;
import forge.util.collect.FCollectionView;

/**
 * DS1 always-visible centre stack column (top item largest). Complements the
 * classic {@link VStack} dropdown; prompts without a custom view still fall
 * back to Forge dialogs.
 */
public class VArenaStackColumn extends FContainer {
    private static final FSkinFont LABEL_FONT = FSkinFont.get(11);
    private final List<StackCard> cards = new ArrayList<>();

    public void refresh() {
        clear();
        cards.clear();
        if (MatchController.instance == null || MatchController.instance.getGameView() == null) {
            return;
        }
        final FCollectionView<StackItemView> stack = MatchController.instance.getGameView().getStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        final List<StackItemView> items = new ArrayList<>();
        for (final StackItemView si : stack) {
            items.add(si);
        }
        // Engine stack: index 0 is bottom; Arena column wants top (resolving) first.
        java.util.Collections.reverse(items);
        final List<ArenaStackColumnLayout.ItemRect> poses =
                ArenaStackColumnLayout.layout(items.size(), getWidth(), getHeight());
        for (int i = 0; i < items.size() && i < poses.size(); i++) {
            final StackItemView si = items.get(i);
            final ArenaStackColumnLayout.ItemRect pose = poses.get(i);
            final StackCard card = add(new StackCard(si));
            card.setBounds(pose.x, pose.y, pose.w, pose.h);
            cards.add(card);
        }
    }

    @Override
    protected void doLayout(final float width, final float height) {
        refresh();
    }

    @Override
    protected void drawBackground(final Graphics g) {
        if (cards.isEmpty()) {
            return;
        }
        // Opaque plate — translucent fills punch FBO alpha and leave a black void
        // in landscape GL captures where the centre lane should show the match BG.
        g.resetAlphaComposite();
        g.fillRect(new Color(0.06f, 0.08f, 0.11f, 1f), 0, 0, getWidth(), getHeight());
        g.drawRect(Utils.scale(1), new Color(0.85f, 0.75f, 0.35f, 1f), 0, 0, getWidth(), getHeight());
    }

    private static final class StackCard extends FDisplayObject {
        private final StackItemView item;

        StackCard(final StackItemView item) {
            this.item = item;
        }

        @Override
        public void draw(final Graphics g) {
            final CardView source = item.getSourceCard();
            if (source != null) {
                CardRenderer.drawCard(g, source, 0, 0, getWidth(), getHeight(), CardStackPosition.Top, false);
            } else {
                g.fillRect(new Color(0.15f, 0.18f, 0.22f, 0.95f), 0, 0, getWidth(), getHeight());
            }
            final String text = item.getText() == null ? "" : item.getText();
            final String shortText = text.length() > 48 ? text.substring(0, 45) + "…" : text;
            g.drawText(shortText, LABEL_FONT, Color.WHITE,
                    Utils.scale(3), getHeight() - LABEL_FONT.getLineHeight() - Utils.scale(2),
                    getWidth() - Utils.scale(6), LABEL_FONT.getLineHeight(),
                    false, Align.left, false);
        }

        @Override
        public boolean tap(final float x, final float y, final int count) {
            final CardView source = item.getSourceCard();
            if (source != null) {
                // Tap opens zoom (peek). Right-click stays no-op here (DS3: CardZoom
                // only on card panels). Activation still goes through prompts / OK.
                CardZoom.show(source);
            }
            return true;
        }

        @Override
        public boolean rightClick(final float x, final float y) {
            // Right-click opens CardZoom only on card panels (DS3). Stack items
            // are not card panels — do not open zoom here.
            return false;
        }
    }
}
