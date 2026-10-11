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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinFont;
import forge.assets.FSkinImage;
import forge.card.CardRenderer;
import forge.card.CardRenderer.CardStackPosition;
import forge.card.CardZoom;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.YieldUpdate;
import forge.interfaces.IGameController;
import forge.menu.FCheckBoxMenuItem;
import forge.menu.FMenuItem;
import forge.menu.FPopupMenu;
import forge.player.AutoYieldStore.TriggerDecision;
import forge.screens.match.ArenaStackColumnLayout;
import forge.screens.match.MatchController;
import forge.toolbox.FContainer;
import forge.toolbox.FDisplayObject;
import forge.util.Utils;
import forge.util.collect.FCollectionView;

/**
 * DS1 always-visible centre stack column (top item largest). Complements the
 * classic {@link VStack} dropdown; prompts without a custom view still fall
 * back to Forge dialogs. Tap opens the same auto-yield / always yes-no / yield
 * menus as {@link VStack}.
 */
public class VArenaStackColumn extends FContainer {
    private static final FSkinFont LABEL_FONT = FSkinFont.get(11);
    private static final Color FRAME = new Color(0.85f, 0.75f, 0.35f, 1f);
    private static final Color PLACEHOLDER = new Color(0.15f, 0.18f, 0.22f, 0.95f);

    private final List<StackCard> cards = new ArrayList<>();
    private StackItemView activeStackInstance;
    private Map<PlayerView, Object> playersWithValidTargets;
    private int lastLaidOutSize = -1;

    public void refresh() {
        clear();
        cards.clear();
        if (MatchController.instance == null || MatchController.instance.getGameView() == null) {
            lastLaidOutSize = 0;
            restoreOldZones();
            activeStackInstance = null;
            return;
        }
        final FCollectionView<StackItemView> stack = MatchController.instance.getGameView().getStack();
        if (stack == null || stack.isEmpty()) {
            lastLaidOutSize = 0;
            restoreOldZones();
            activeStackInstance = null;
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
        lastLaidOutSize = items.size();
        if (activeStackInstance == null || !items.contains(activeStackInstance)) {
            activeStackInstance = items.get(0);
            restoreOldZones();
            revealTargetZones();
        }
    }

    /** Size of the last built column; used by ArenaDuelScreen to skip per-frame rebuilds. */
    public int getLaidOutSize() {
        return lastLaidOutSize;
    }

    private void revealTargetZones() {
        if (activeStackInstance == null) {
            return;
        }
        final PlayerView player = MatchController.instance.getCurrentPlayer();
        final Set<ZoneType> zones = new HashSet<>();
        playersWithValidTargets = new HashMap<>();
        final FCollectionView<CardView> targetCards = activeStackInstance.getTargetCards();
        if (targetCards != null) {
            for (final CardView c : targetCards) {
                if (c.getZone() != null) {
                    zones.add(c.getZone());
                    playersWithValidTargets.put(c.getController(), c);
                }
            }
        }
        if (zones.isEmpty() || playersWithValidTargets.isEmpty()) {
            return;
        }
        MatchController.instance.openZones(player, zones, playersWithValidTargets);
    }

    private void restoreOldZones() {
        if (playersWithValidTargets == null) {
            return;
        }
        MatchController.instance.restoreOldZones(playersWithValidTargets);
        playersWithValidTargets = null;
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
        // Frame only — no fill. The FieldScroller centre-gap seal paints the match
        // backdrop behind this column; a solid plate read as a black void in landscape.
        g.resetAlphaComposite();
        g.drawRect(Utils.scale(1), FRAME, 0, 0, getWidth(), getHeight());
    }

    private final class StackCard extends FDisplayObject {
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
                g.fillRect(PLACEHOLDER, 0, 0, getWidth(), getHeight());
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
            if (activeStackInstance != item) {
                activeStackInstance = item;
                restoreOldZones();
                revealTargetZones();
                return true;
            }
            final IGameController controller = MatchController.instance.getGameController();
            final PlayerView player = MatchController.instance.getCurrentPlayer();
            final CardView sourceCard = item.getSourceCard();
            // Tap on the left art band → zoom; elsewhere → classic VStack menus.
            final boolean onArt = x < getWidth() * 0.42f;
            if (onArt && sourceCard != null) {
                CardZoom.show(sourceCard);
                return true;
            }
            if (player == null) {
                if (sourceCard != null) {
                    CardZoom.show(sourceCard);
                }
                return true;
            }
            final FPopupMenu menu = new FPopupMenu() {
                @Override
                protected void buildMenu() {
                    if (item.isAbility()) {
                        final String key = item.getKey();
                        final boolean autoYield = controller.shouldAutoYield(key);
                        addItem(new FCheckBoxMenuItem(Forge.getLocalizer().getMessage("cbpAutoYieldMode"), autoYield,
                                e -> {
                                    boolean abilityScope = controller.getYieldController().isAbilityScope();
                                    controller.setShouldAutoYield(key, !autoYield, abilityScope);
                                }));
                        if (item.isOptionalTrigger() && item.getActivatingPlayer().equals(player)) {
                            if (!key.isEmpty()) {
                                final boolean abilityScope = controller.getYieldController().isAbilityScope();
                                addItem(new FCheckBoxMenuItem(Forge.getLocalizer().getMessage("lblAlwaysYes"),
                                        controller.getTriggerDecision(key) == TriggerDecision.ACCEPT,
                                        e -> controller.setTriggerDecision(key,
                                                controller.getTriggerDecision(key) == TriggerDecision.ACCEPT
                                                        ? TriggerDecision.ASK : TriggerDecision.ACCEPT,
                                                abilityScope)));
                                addItem(new FCheckBoxMenuItem(Forge.getLocalizer().getMessage("lblAlwaysNo"),
                                        controller.getTriggerDecision(key) == TriggerDecision.DECLINE,
                                        e -> controller.setTriggerDecision(key,
                                                controller.getTriggerDecision(key) == TriggerDecision.DECLINE
                                                        ? TriggerDecision.ASK : TriggerDecision.DECLINE,
                                                abilityScope)));
                            }
                        }
                    }
                    addItem(new FMenuItem(Forge.getLocalizer().getMessage("lblYieldToStack"),
                            Forge.hdbuttons ? FSkinImage.HDYIELD : FSkinImage.WARNING,
                            e -> controller.sendYieldUpdate(new YieldUpdate.StackYield(player, true, true))));
                    addItem(new FMenuItem(Forge.getLocalizer().getMessage("lblYieldToEntireStack"),
                            Forge.hdbuttons ? FSkinImage.HDYIELD : FSkinImage.WARNING,
                            e -> controller.sendYieldUpdate(new YieldUpdate.StackYield(player, true, false))));
                    addItem(new FMenuItem(Forge.getLocalizer().getMessage("lblZoomOrDetails"),
                            e -> CardZoom.show(item.getSourceCard())));
                }
            };
            menu.show(this, x, y);
            return true;
        }

        @Override
        public boolean longPress(final float x, final float y) {
            final CardView source = item.getSourceCard();
            if (source != null) {
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
