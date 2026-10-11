package forge.screens.match.views;

import java.util.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import forge.Forge;
import forge.Graphics;
import forge.assets.FSkin;
import forge.assets.FSkinColor;
import forge.assets.FSkinColor.Colors;
import forge.assets.FSkinFont;
import forge.assets.FSkinImage;
import forge.assets.FSkinImageInterface;
import forge.game.card.CardView;
import forge.game.card.CounterEnumType;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.localinstance.skin.FSkinProp;
import forge.menu.FMenuBar;
import forge.menu.FMenuItem;
import forge.menu.FPopupMenu;
import forge.screens.match.MatchController;
import forge.screens.match.MatchScreen;
import forge.screens.match.ModernDuelScreen;
import forge.toolbox.FCardPanel;
import forge.toolbox.FContainer;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FScrollPane;
import forge.util.Utils;
import org.apache.commons.text.WordUtils;

public class VPlayerPanel extends FContainer {
    private static final FSkinFont LIFE_FONT = FSkinFont.get(18);
    private static final FSkinFont LIFE_FONT_ALT = FSkinFont.get(22);
    private static final FSkinFont INFO_FONT = FSkinFont.get(12);
    private static final FSkinFont INFO2_FONT = FSkinFont.get(14);

    private static FSkinColor getInfoForeColor() {
        if (Forge.isMobileAdventureMode)
            return FSkinColor.get(Colors.ADV_CLR_TEXT);
        return FSkinColor.get(Colors.CLR_TEXT);
    }

    private static FSkinColor getDisplayAreaBackColor() {
        if (Forge.isMobileAdventureMode)
            return FSkinColor.get(Colors.ADV_CLR_INACTIVE).alphaColor(0.5f);
        return FSkinColor.get(Colors.CLR_INACTIVE).alphaColor(0.5f);
    }

    private static FSkinColor getAltDisplayAreaBackColor() {
        if (Forge.isMobileAdventureMode)
            return FSkinColor.get(Colors.ADV_CLR_PHASE_INACTIVE_ENABLED).alphaColor(0.3f);
        return FSkinColor.get(Colors.CLR_PHASE_INACTIVE_ENABLED).alphaColor(0.3f);
    }

    private static FSkinColor getDeliriumHighlight() {
        if (Forge.isMobileAdventureMode)
            return FSkinColor.get(Colors.ADV_CLR_PHASE_ACTIVE_ENABLED).alphaColor(0.5f);
        return FSkinColor.get(Colors.CLR_PHASE_ACTIVE_ENABLED).alphaColor(0.5f);
    }

    private static final float INFO_TAB_PADDING_X = Utils.scale(2);
    private static final float INFO_TAB_PADDING_Y = Utils.scale(2);
    private static final Color ARENA_PANEL_PLATE = new Color(0.10f, 0.16f, 0.12f, 1f);
    private static final Color ARENA_HAND_SCRIM = new Color(0.16f, 0.18f, 0.22f, 1f);
    private static final Color ARENA_LIFE_BADGE_FILL = new Color(0.02f, 0.02f, 0.04f, 1f);
    private static final Color ARENA_LIFE_BADGE_BORDER = new Color(0.95f, 0.82f, 0.28f, 1f);
    private static final Color ARENA_LIFE_BADGE_INK = new Color(1f, 0.98f, 0.88f, 1f);
    private static final FSkinFont ARENA_LIFE_FONT = FSkinFont._get(28);
    private static final FSkinFont ARENA_LIFE_COUNTER_FONT = FSkinFont._get(14);

    /**
     * Zones to include in the extra zones dropdown.
     */
    private static final EnumSet<ZoneType> EXTRA_ZONES = EnumSet.of(ZoneType.Sideboard, ZoneType.PlanarDeck,
            ZoneType.SchemeDeck, ZoneType.ContraptionDeck, ZoneType.AttractionDeck, ZoneType.StickerSheets,
            ZoneType.Junkyard, ZoneType.Ante);

    private final PlayerView player;
    private final VPhaseIndicator phaseIndicator;
    private final VField field;
    private final VAvatar avatar;
    private final VZoneDisplay commandZone;
    private final LifeLabel lblLife;
    private final InfoTab tabManaPool;
    private final VFloatingMana floatingMana;
    private final Map<ZoneType, InfoTabZone> zoneTabs = new HashMap<>();
    private final InfoTabExtra extraTab;
    private final List<InfoTab> tabs = new ArrayList<>();
    private InfoTab selectedTab;
    private VField.FieldRow selectedRow;
    private float avatarHeight = VAvatar.HEIGHT;
    private float displayAreaHeightFactor = 1.0f;
    private boolean forMultiPlayer = false;
    public int adjustHeight = 1;
    private int selected = 0;
    private boolean isBottomPlayer = false;

    public VPlayerPanel(PlayerView player0, boolean showHand, int playerCount) {
        player = player0;
        phaseIndicator = add(new VPhaseIndicator());

        if (playerCount > 2) {
            forMultiPlayer = true;
            avatarHeight *= 0.5f;
            //displayAreaHeightFactor *= 0.7f;
        }
        field = add(new VField(player));
        selectedRow = field.getRow1();
        avatar = add(new VAvatar(player, avatarHeight));
        lblLife = add(new LifeLabel());
        addZoneDisplay(ZoneType.Hand);
        addZoneDisplay(ZoneType.Graveyard);
        addZoneDisplay(ZoneType.Library);
        addZoneDisplay(ZoneType.Flashback);

        VManaPool manaPool = add(new VManaPool(player));
        tabManaPool = add(new InfoTabSingleDisplay(FSkinImage.HDMANAPOOL, manaPool));
        tabs.add(tabManaPool);
        floatingMana = add(new VFloatingMana(player));

        addZoneDisplay(ZoneType.Exile);
        extraTab = add(new InfoTabExtra());
        tabs.add(extraTab);

        commandZone = add(new CommandZoneDisplay(player));

        if (showHand) {
            setSelectedZone(ZoneType.Hand);
        }
    }

    public PlayerView getPlayer() {
        return player;
    }

    public void setBottomPlayer(boolean val) {
        isBottomPlayer = val;
    }

    public void addZoneDisplay(ZoneType zoneType) {
        VZoneDisplay zoneDisplay = add(new VZoneDisplay(player, zoneType));
        InfoTabZone zoneTab = add(new InfoTabZone(zoneDisplay, zoneType));
        zoneTabs.put(zoneType, zoneTab);
        tabs.add(zoneTab);
    }

    public static FSkinImageInterface iconFromZone(ZoneType zoneType) {
        return FSkin.getImages().get(FSkinProp.iconFromZone(zoneType, Forge.hdbuttons));
    }

    public Iterable<InfoTab> getTabs() {
        return tabs;
    }

    public InfoTab getSelectedTab() {
        return selectedTab;
    }

    public void resetZoneTabs() {
        for (InfoTab tab : tabs)
            tab.reset();
    }

    public VZoneDisplay getZoneDisplay(ZoneType zoneType) {
        final InfoTabZone tab = zoneTabs.get(zoneType);
        if (tab == null || !(tab.getDisplayArea() instanceof VZoneDisplay zone)) {
            return null;
        }
        return zone;
    }

    public void setSelectedZone(ZoneType zoneType) {
        if (zoneTabs.containsKey(zoneType))
            setSelectedTab(zoneTabs.get(zoneType));
        else {
            extraTab.setActiveZone(zoneType);
            setSelectedTab(extraTab);
        }
    }

    public void hideSelectedTab() {
        if (selectedTab != null) {
            selectedTab.setDisplayVisible(false);
            selectedTab = null;
        }
    }

    public void setSelectedTab(InfoTab tab) {
        if (this.selectedTab == tab) {
            return;
        }

        hideSelectedTab();

        this.selectedTab = tab;
        selected = tabs.indexOf(tab);

        if (this.selectedTab != null) {
            this.selectedTab.setDisplayVisible(true);
        }

        if (MatchController.getView() != null) { //must revalidate entire screen so panel heights updated
            MatchController.getView().revalidate();
        }
    }

    public void setNextSelectedTab(boolean change) {
        if (change) {
            if (selectedTab != null) {
                selectedTab.setDisplayVisible(false);
                selectedTab = null;
            }
            if (MatchController.getView() != null) { //must revalidate entire screen so panel heights updated
                MatchController.getView().revalidate();
            }
        }
        if (!change)
            selected++;
        else
            hideSelectedTab();
        int numTabs = tabs.size();
        int numExtraTabs = extraTab.displayAreas.size();
        if (selected < 0 || selected >= numTabs + numExtraTabs)
            selected = 0;
        if (selected >= numTabs) {
            extraTab.setActiveZoneByIndex(selected - numTabs);
            setSelectedTab(extraTab);
        } else
            setSelectedTab(tabs.get(selected));
    }

    public void closeSelectedTab() {
        if (selectedTab != null) {
            selectedTab.setDisplayVisible(false);
            selectedTab = null;
        }
        if (MatchController.getView() != null) { //must revalidate entire screen so panel heights updated
            MatchController.getView().revalidate();
        }
        selected--;
        if (selected < -1)
            selected = -1;
    }

    public InfoTab getManaPoolTab() {
        return tabManaPool;
    }

    public boolean isFlipped() {
        return field.isFlipped();
    }

    public void setFlipped(boolean flipped0) {
        field.setFlipped(flipped0);
    }

    @Override
    public void setRotate180(boolean b0) {
        // DS1 Arena: keep both halves upright. Classic 180° on the opponent field
        // rows + clips punches FBO alpha and leaves a flat dark void over the
        // shared forest backdrop.
        if (isArenaDuelLayout()) {
            avatar.setRotate180(false);
            lblLife.setRotate180(false);
            phaseIndicator.setRotate180(false);
            for (InfoTab tab : tabs) {
                tab.setRotate180(false);
            }
            field.getRow1().setRotate180(false);
            field.getRow2().setRotate180(false);
            field.getRowSide().setRotate180(false);
            return;
        }
        //only rotate certain parts of panel
        avatar.setRotate180(b0);
        lblLife.setRotate180(b0);
        phaseIndicator.setRotate180(b0);
        for (InfoTab tab : tabs) {
            tab.setRotate180(b0);
        }
        field.getRow1().setRotate180(b0);
        field.getRow2().setRotate180(b0);
    }

    public VField getField() {
        return field;
    }

    public VField.FieldRow getSelectedRow() {
        return selectedRow;
    }

    public void switchRow() {
        if (selectedRow == field.getRow1())
            selectedRow = field.getRow2();
        else
            selectedRow = field.getRow1();
    }

    public VPhaseIndicator getPhaseIndicator() {
        return phaseIndicator;
    }

    public VAvatar getAvatar() {
        return avatar;
    }

    public VZoneDisplay getCommandZone() {
        return commandZone;
    }

    public void updateLife() {
        lblLife.update();
    }

    public void updateShards() {
        lblLife.updateShards();
    }

    public void updateManaPool() {
        tabManaPool.update();
        if (floatingMana != null) {
            floatingMana.update();
        }
    }

    public VFloatingMana getFloatingMana() {
        return floatingMana;
    }

    @SuppressWarnings("incomplete-switch")
    public void updateZone(ZoneType zoneType) {
        if (zoneType == ZoneType.Battlefield) {
            field.update(true);
        } else if (zoneType == ZoneType.Command) {
            commandZone.update();
            if (selectedTab != null && Forge.isHorizontalTabLayout())
                updateTabLayout(initW, initH);
        } else {
            if (zoneTabs.containsKey(zoneType))
                zoneTabs.get(zoneType).update();
            else if (EXTRA_ZONES.contains(zoneType)) {
                extraTab.update(zoneType);
            }

            //update flashback zone when graveyard, library, exile, or stack zones updated
            switch (zoneType) {
                case Graveyard, Library, Exile, Stack -> zoneTabs.get(ZoneType.Flashback).update();
            }
        }
    }

    @Override
    protected void doLayout(float width, float height) {
        // DS1 Arena: keep hand/field stacked (portrait-style) even in landscape so the
        // ArenaDuelLayout playerHand / playerField bands stay readable.
        if (isArenaDuelLayout()) {
            doArenaLayout(width, height);
            return;
        }
        // Restore classic chrome if we previously hid it for Arena.
        setArenaChromeVisible(true);
        if (Forge.isLandscapeMode()) {
            doLandscapeLayout(width, height);
            return;
        }

        //layout for bottom panel by default
        final float chromeH = avatarHeight;
        float x = chromeH;
        float w = width - chromeH;
        float indicatorScale = 1f;
        if (chromeH < VAvatar.HEIGHT) {
            indicatorScale = 0.6f;
        }
        phaseIndicator.setVisible(true);
        float phaseH = phaseIndicator.getPreferredHeight(w) * indicatorScale;
        phaseIndicator.setBounds(x, height - phaseH, w, phaseH);

        float y = height - chromeH;
        float displayAreaHeight = displayAreaHeightFactor * y / 3;
        y -= displayAreaHeight;
        for (InfoTab tab : tabs) {
            tab.setDisplayBounds(0, y, width, displayAreaHeight);
        }

        y = height - chromeH;
        avatar.setSize(chromeH, chromeH);
        avatar.setPosition(0, y);

        float lifeLabelWidth = LIFE_FONT.getBounds("99").width * 1.2f * indicatorScale; //make just wide enough for 2-digit life totals
        float infoLabelHeight = chromeH - (!phaseIndicator.isVisible() ? 0f : phaseIndicator.getHeight());
        if (infoLabelHeight < chromeH * 0.55f) {
            infoLabelHeight = chromeH;
        }
        lblLife.setBounds(x, y, lifeLabelWidth, infoLabelHeight);
        x += lifeLabelWidth;

        float infoTabWidth = (getWidth() - x) / Math.max(1, tabs.size());
        for (InfoTab tab : tabs) {
            tab.setBounds(x, y, infoTabWidth, infoLabelHeight);
            x += infoTabWidth;
        }

        if (selectedTab != null) {
            y -= displayAreaHeight;
        }

        //account for command zone if needed
        int commandZoneCount = commandZone.getCount();
        if (commandZoneCount > 0) {
            float commandZoneHeight = y / 2;
            float commandZoneWidth = Math.min(commandZoneCount, 2) * commandZone.getCardWidth(commandZoneHeight);
            commandZone.setBounds(width - commandZoneWidth, y - commandZoneHeight, commandZoneWidth, commandZoneHeight);

            field.setCommandZoneWidth(commandZoneWidth + 1); //ensure second row of field accounts for width of command zone and its border
        } else {
            field.setCommandZoneWidth(0);
        }

        field.setBounds(0, 0, width, y);
        layoutFloatingMana(width, y);

        if (isFlipped()) { //flip all positions across x-axis if needed
            for (FDisplayObject child : getChildren()) {
                child.setTop(height - child.getBottom());
            }
        }

        //this is used for landscape so set this to 0
        field.setFieldModifier(0);
    }

    /** Panel-local Arena slots from {@link forge.screens.match.ArenaDuelScreen}. */
    private forge.screens.match.ArenaDuelLayout.Rect arenaAvatarSlot;
    private forge.screens.match.ArenaDuelLayout.Rect arenaPilesSlot;
    private forge.screens.match.ArenaDuelLayout.Rect arenaFieldSlot;
    private forge.screens.match.ArenaDuelLayout.Rect arenaHandSlot;
    /** Full FieldScroller height — used for continuous backdrop UV mapping. */
    private float arenaScrollerHeight;

    /**
     * Apply panel-local avatar / piles / field / hand rects from ArenaDuelLayout.
     * Called after the panel is sized inside the field scroller.
     */
    public void applyArenaSlots(final forge.screens.match.ArenaDuelLayout.Rect avatarSlot,
            final forge.screens.match.ArenaDuelLayout.Rect pilesSlot,
            final forge.screens.match.ArenaDuelLayout.Rect fieldSlot,
            final forge.screens.match.ArenaDuelLayout.Rect handSlot) {
        applyArenaSlots(avatarSlot, pilesSlot, fieldSlot, handSlot, 0f);
    }

    public void applyArenaSlots(final forge.screens.match.ArenaDuelLayout.Rect avatarSlot,
            final forge.screens.match.ArenaDuelLayout.Rect pilesSlot,
            final forge.screens.match.ArenaDuelLayout.Rect fieldSlot,
            final forge.screens.match.ArenaDuelLayout.Rect handSlot,
            final float scrollerHeight) {
        arenaAvatarSlot = avatarSlot;
        arenaPilesSlot = pilesSlot;
        arenaFieldSlot = fieldSlot;
        arenaHandSlot = handSlot;
        arenaScrollerHeight = scrollerHeight;
        // Clear any classic opponent 180° that was applied before Arena layout.
        setRotate180(false);
        if (getWidth() > 0 && getHeight() > 0) {
            doArenaLayout(getWidth(), getHeight());
        }
    }

    /**
     * DS1 Arena panel layout: place avatars/life, GY/exile/library piles, command
     * zone and floating mana into ArenaDuelLayout slots. Phase indicator is hosted
     * on the screen in the phaseRail band (not inside the panel).
     */
    private void doArenaLayout(final float width, final float height) {
        // Phase lives on ArenaDuelScreen's phaseRail — never paint classic panel chrome.
        if (!phaseIndicator.isArenaRailHosted()) {
            phaseIndicator.setVisible(false);
            phaseIndicator.setBounds(0, 0, 0, 0);
        }

        final forge.screens.match.ArenaDuelLayout.Rect av = arenaAvatarSlot;
        final forge.screens.match.ArenaDuelLayout.Rect piles = arenaPilesSlot;
        final forge.screens.match.ArenaDuelLayout.Rect fieldR = arenaFieldSlot;
        final forge.screens.match.ArenaDuelLayout.Rect handR = arenaHandSlot;

        // Avatar + Arena life badge beside it (MTG Arena-style high-contrast total).
        // Absolute dp (not Utils.scale) so xvfb AVG_FINGER cannot collapse the badge.
        if (av != null && av.w > 1f && av.h > 1f) {
            avatar.setVisible(true);
            avatar.setBounds(av.x, av.y, av.w, av.h);
            final float badgeH = Math.min(Math.max(40f, av.h * 0.55f), Math.max(40f, av.h * 0.85f));
            final float badgeW = Math.min(Math.max(52f, av.w * 0.70f), 72f);
            // Prefer overlapping the avatar's lower-right (Arena-style), clamped in-panel.
            float badgeX = av.x + Math.max(0f, av.w - badgeW * 0.85f);
            float badgeY = av.y + Math.max(0f, av.h - badgeH * 0.92f);
            if (badgeX + badgeW > width - 2f) {
                badgeX = Math.max(0f, width - badgeW - 2f);
            }
            if (badgeY + badgeH > height - 2f) {
                badgeY = Math.max(0f, height - badgeH - 2f);
            }
            lblLife.setVisible(true);
            lblLife.setBounds(badgeX, badgeY, badgeW, badgeH);
            updateLife();
        } else {
            avatar.setVisible(false);
            lblLife.setVisible(false);
        }

        // Pile tabs (graveyard / exile / library) — open on tap via InfoTab.
        final List<InfoTab> pileTabs = new ArrayList<>();
        for (final ZoneType zt : new ZoneType[]{ZoneType.Graveyard, ZoneType.Exile, ZoneType.Library}) {
            final InfoTabZone tab = zoneTabs.get(zt);
            if (tab != null) {
                pileTabs.add(tab);
            }
        }
        if (piles != null && piles.w > 1f && piles.h > 1f && !pileTabs.isEmpty()) {
            final float tabH = piles.h / pileTabs.size();
            float y = piles.y;
            for (final InfoTab tab : pileTabs) {
                tab.setVisible(true);
                tab.setBounds(piles.x, y, piles.w, tabH);
                y += tabH;
            }
        } else {
            for (final InfoTab tab : pileTabs) {
                tab.setVisible(false);
            }
        }

        // Hide non-pile chrome tabs (hand/flashback/mana/extra stay off the strip).
        for (final InfoTab tab : tabs) {
            if (!pileTabs.contains(tab)) {
                tab.setVisible(false);
            }
        }

        // Field / hand bands.
        float fieldX = 0f;
        float fieldY = 0f;
        float fieldW = width;
        float fieldH = height;
        if (fieldR != null && fieldR.w > 1f && fieldR.h > 1f) {
            fieldX = fieldR.x;
            fieldY = fieldR.y;
            fieldW = fieldR.w;
            fieldH = fieldR.h;
        } else if (!isFlipped() && handR != null) {
            fieldH = Math.max(1f, height - handR.h);
        }

        // Command zone: bottom-right of the field slot (castable commanders).
        int commandZoneCount = commandZone == null ? 0 : commandZone.getCount();
        if (commandZone != null && commandZoneCount > 0) {
            final float czH = Math.min(fieldH / 2f, Utils.scale(90));
            final float czW = Math.min(commandZoneCount, 2) * commandZone.getCardWidth(czH);
            commandZone.setVisible(true);
            commandZone.setBounds(fieldX + fieldW - czW, fieldY + fieldH - czH, czW, czH);
            field.setCommandZoneWidth(czW + 1);
        } else if (commandZone != null) {
            commandZone.setVisible(false);
            field.setCommandZoneWidth(0);
        }
        field.setFieldModifier(0);
        field.setBounds(fieldX, fieldY, fieldW, fieldH);

        // Floating mana along the field edge.
        if (floatingMana != null && ModernDuelScreen.enabled()) {
            final float barH = Utils.scale(28);
            final float barW = Math.min(fieldW * 0.55f, Utils.scale(220));
            final float mx = fieldX + (fieldW - barW) / 2f;
            final float my = isFlipped()
                    ? fieldY + Utils.scale(2)
                    : Math.max(fieldY, fieldY + fieldH - barH - Utils.scale(2));
            floatingMana.setVisible(true);
            floatingMana.setBounds(mx, my, barW, barH);
            floatingMana.update();
        } else if (floatingMana != null) {
            floatingMana.setVisible(false);
        }

        // Zone displays: hand band for the local player; opponent piles open over the field.
        for (InfoTab tab : tabs) {
            tab.setDisplayVisible(false);
        }
        if (handR != null) {
            // Local player: hand / opened pile share the hand band.
            final VZoneDisplay handZone = getZoneDisplay(ZoneType.Hand);
            for (InfoTab tab : tabs) {
                tab.setDisplayBounds(handR.x, handR.y, handR.w, handR.h);
            }
            if (selectedTab != null) {
                selectedTab.setDisplayVisible(true);
            } else if (handZone != null) {
                handZone.setVisible(true);
            }
            if (handZone != null) {
                handZone.setBounds(handR.x, handR.y, handR.w, handR.h);
                if (selectedTab == null || selectedTab.getDisplayArea() == handZone) {
                    handZone.setVisible(true);
                }
            }
            return;
        }
        // Opponent (flipped, no hand band): show an opened pile over the field slot.
        if (fieldR != null && selectedTab != null && isArenaPileTab(selectedTab)) {
            selectedTab.setDisplayBounds(fieldR.x, fieldR.y, fieldR.w, fieldR.h);
            selectedTab.setDisplayVisible(true);
        }
    }

    /** Graveyard / exile / library tabs — openable piles in Arena layout. */
    private boolean isArenaPileTab(final InfoTab tab) {
        if (!(tab instanceof InfoTabZone)) {
            return false;
        }
        final ZoneType zt = ((InfoTabZone) tab).zoneType;
        return zt == ZoneType.Graveyard || zt == ZoneType.Exile || zt == ZoneType.Library;
    }

    private void setArenaChromeVisible(final boolean visible) {
        avatar.setVisible(visible);
        lblLife.setVisible(visible);
        // Phase may be hosted on ArenaDuelScreen's rail — only toggle when still ours.
        if (!phaseIndicator.isArenaRailHosted()) {
            phaseIndicator.setVisible(visible);
        }
        for (InfoTab tab : tabs) {
            tab.setVisible(visible);
        }
    }

    /**
     * Paint this panel's slice of the continuous Arena scroller backdrop.
     * Uses scroller-relative Y so opponent and local halves share one forest-
     * biased ADV_BG_MATCH mapping (no per-panel full-image stretch).
     */
    private void paintArenaPanelBackdrop(final Graphics g) {
        forge.assets.FSkinTexture plate = forge.assets.FSkinTexture.ADV_BG_MATCH;
        if (plate.getWidth() <= 0) {
            plate = forge.assets.FSkinTexture.BG_MATCH;
        }
        final float w = getWidth();
        final float h = getHeight();
        // Scroller-relative top: opponent panel at 0, local below centre gap.
        final float scrollerY = getTop();
        final float fullH = arenaScrollerHeight > 1f
                ? arenaScrollerHeight : Math.max(h, scrollerY + h);
        g.resetAlphaComposite();
        g.startClip(0, 0, w, h);
        try {
            g.fillRect(ARENA_PANEL_PLATE, 0, 0, w, h);
            if (plate.getWidth() > 0 && fullH > 1f) {
                // Skip the texture's dark top band; stretch the forest/meadow slice
                // across the full scroller so opponent and local halves match.
                final float imgH = fullH / 0.42f;
                final float imgY = -scrollerY - fullH * 0.08f;
                g.drawImage(plate, 0, imgY, w, imgH);
            }
        } finally {
            g.endClip();
        }
        g.resetAlphaComposite();
    }

    private void layoutFloatingMana(float panelWidth, float fieldBottom) {
        if (floatingMana == null || !ModernDuelScreen.enabled()) {
            if (floatingMana != null) {
                floatingMana.setVisible(false);
            }
            return;
        }
        final float barH = Utils.scale(28);
        final float barW = Math.min(panelWidth * 0.55f, Utils.scale(220));
        final float x = (panelWidth - barW) / 2f;
        final float y = isFlipped() ? Utils.scale(2) : Math.max(0, fieldBottom - barH - Utils.scale(2));
        floatingMana.setBounds(x, y, barW, barH);
        floatingMana.update();
    }

    private void layoutFloatingManaLandscape() {
        if (floatingMana == null || !ModernDuelScreen.enabled()) {
            if (floatingMana != null) {
                floatingMana.setVisible(false);
            }
            return;
        }
        final float barH = Utils.scale(28);
        final float barW = Math.min(field.getWidth() * 0.45f, Utils.scale(220));
        final float x = field.getLeft() + (field.getWidth() - barW) / 2f;
        final float y = isFlipped()
                ? field.getTop() + Utils.scale(2)
                : field.getBottom() - barH - Utils.scale(2);
        floatingMana.setBounds(x, y, barW, barH);
        floatingMana.update();
    }

    private float initW, initH, commandZoneWidth, commandZoneCount, avatarWidth, prefWidth;
    private final float mod = 2.4f;

    /** True when the active match view is the DS1 Arena skeleton (hand stays bottom-stacked). */
    private static boolean isArenaDuelLayout() {
        return forge.screens.match.ArenaDuelUi.isActive();
    }

    private static float arenaHandFraction() {
        try {
            if (forge.adventure.util.Config.instance() != null
                    && forge.adventure.util.Config.instance().getConfigData() != null) {
                return forge.adventure.util.Config.instance().getConfigData().ds1HandFraction;
            }
        } catch (final RuntimeException ignored) {
            // Config may be unavailable in some headless paths
        }
        return 0.28f;
    }

    private void doLandscapeLayout(float width, float height) {
        initW = width;
        initH = height;
        float x = 0;
        float y = 0;
        float yAlt = 0;
        avatarWidth = Forge.altZoneTabs ? avatar.getWidth() : 0;
        avatar.setPosition(x, y);
        y += avatar.getHeight();

        lblLife.setBounds(x, (Forge.altPlayerLayout && !Forge.altZoneTabs) ? 0 : y, avatar.getWidth(), (Forge.altPlayerLayout && !Forge.altZoneTabs) ? INFO_FONT.getLineHeight() : Forge.altZoneTabs ? LIFE_FONT_ALT.getLineHeight() : LIFE_FONT.getLineHeight());
        if (Forge.altPlayerLayout && !Forge.altZoneTabs) {
            if (adjustHeight > 2)
                y += INFO_FONT.getLineHeight() / 2;
        } else
            y += lblLife.getHeight();

        float infoTabWidth = avatar.getWidth();
        int tabSize = !Forge.altZoneTabs ? tabs.size() : tabs.size() - 4;
        float infoTabHeight = (height - y) / tabSize;
        float infoTabHeightAlt = (height - yAlt) / 4;

        for (InfoTab tab : tabs) {
            if (!Forge.altZoneTabs) {
                tab.setBounds(x, y, infoTabWidth, infoTabHeight);
                y += infoTabHeight;
            } else {
                if (!tab.isAlignedRightForAltDisplay()) {
                    tab.setBounds(x, y, infoTabWidth, infoTabHeight);
                    y += infoTabHeight;
                } else {
                    tab.setBounds(x + width - avatarWidth, yAlt, avatarWidth, infoTabHeightAlt);
                    yAlt += infoTabHeightAlt;
                }
            }
        }
        updateTabLayout(width, height);
    }

    private void updateTabLayout(float width, float height) {
        // Arena keeps portrait-stacked field/hand; never re-apply classic landscape chrome.
        if (isArenaDuelLayout()) {
            doArenaLayout(width, height);
            return;
        }
        float x = avatar.getRight();
        phaseIndicator.resetFont();
        phaseIndicator.setBounds(x, 0, avatar.getWidth() * 0.6f, height);
        x += phaseIndicator.getWidth();

        float fieldWidth = width - x - avatarWidth;
        float displayAreaWidth = height / FCardPanel.ASPECT_RATIO;
        if (selectedTab != null) {
            fieldWidth -= displayAreaWidth;
        }

        //account for command zone if needed
        commandZoneWidth = 0f;
        commandZoneCount = commandZone.getCount();
        if (commandZoneCount > 0) {
            float commandZoneHeight = height / 2;
            float minCommandCards = Forge.isHorizontalTabLayout() ? 5 : 2;
            commandZoneWidth = Math.min(commandZoneCount, minCommandCards) * commandZone.getCardWidth(commandZoneHeight);
            float x2 = x + fieldWidth - commandZoneWidth;
            float y2 = height - commandZoneHeight;
            if (Forge.isHorizontalTabLayout()) {
                x2 = width - avatarWidth - commandZoneWidth;
                y2 = 0;
            }
            commandZone.setBounds(x2, y2, commandZoneWidth, commandZoneHeight);
            if (isFlipped()) { //flip across x-axis if needed
                commandZone.setTop(height - commandZone.getBottom());
            }

            field.setCommandZoneWidth(commandZoneWidth + 1); //ensure second row of field accounts for width of command zone and its border
        } else {
            field.setCommandZoneWidth(0);
        }
        prefWidth = width / mod;
        if (Forge.isHorizontalTabLayout()) {
            field.setBounds(x, 0, width - avatarWidth, height);
            field.getRow1().setWidth(width - (commandZoneCount > 0 ? commandZone.getWidth() + (avatarWidth * commandZoneCount) : avatarWidth));
            field.getRow2().setWidth(width - (avatarWidth / 4f) - (selectedTab == null ? 0 : selectedTab.getIdealWidth(prefWidth) + 1) - avatarWidth * mod);
        } else
            field.setBounds(x, 0, fieldWidth, height);
        layoutFloatingManaLandscape();

        x = width - displayAreaWidth - avatarWidth;
        for (InfoTab tab : tabs) {
            if (Forge.isHorizontalTabLayout()) {
                float w = tab.getIdealWidth(prefWidth);
                float h = height / 2f;
                tab.setDisplayBounds(width - w - avatarWidth, isBottomPlayer ? h : 0, w, h);
            } else {
                tab.setDisplayBounds(x, 0, displayAreaWidth, height);
            }
        }

        if (!Forge.altZoneTabs) {
            field.setFieldModifier(0);
        } else {
            if (!"Horizontal".equalsIgnoreCase(Forge.altZoneTabMode))
                field.setFieldModifier(avatarWidth / 16);
        }
    }

    @Override
    protected void drawOverlay(Graphics g) {
        if (Forge.isHorizontalTabLayout()) {
            InfoTab infoTab = selectedTab;
            if (infoTab != null) {
                VDisplayArea selectedDisplayArea = infoTab.getDisplayArea();
                if (selectedDisplayArea != null && selectedDisplayArea.getCount() > 0) {
                    float scale = avatarWidth / 2f;
                    float x = selectedDisplayArea.getLeft();
                    float y = selectedDisplayArea.getBottom() - scale;
                    g.fillRect(getAltDisplayAreaBackColor(), x, y, scale, scale);
                    infoTab.icon.draw(g, x, y, scale, scale);
                }
            }
        }
        super.drawOverlay(g);
    }

    @Override
    public void draw(Graphics g) {
        // Arena: scissor the whole panel so field-row / zone clips cannot paint into the
        // other half and zero FBO alpha (reads as a flat dark void in captures).
        if (isArenaDuelLayout()) {
            g.startClip(0, 0, getWidth(), getHeight());
            try {
                super.draw(g);
            } finally {
                g.endClip();
            }
            return;
        }
        super.draw(g);
    }

    @Override
    public void drawBackground(Graphics g) {
        float y;
        InfoTab infoTab = selectedTab;
        float pad = Forge.isHorizontalTabLayout() ? avatarWidth / 16f : 0f;
        final VDisplayArea selectedDisplayArea = infoTab == null ? null : infoTab.getDisplayArea();
        // Arena: opaque scroller-relative forest plate so child alpha cannot punch
        // FBO voids; same UV mapping as FieldScroller so both halves match.
        if (isArenaDuelLayout()) {
            g.resetAlphaComposite();
            paintArenaPanelBackdrop(g);
            final VZoneDisplay handZone = getZoneDisplay(ZoneType.Hand);
            if (handZone != null && handZone.isVisible() && handZone.getHeight() > 1f) {
                g.fillRect(ARENA_HAND_SCRIM,
                        handZone.getLeft(), handZone.getTop(), handZone.getWidth(), handZone.getHeight());
            }
            g.resetAlphaComposite();
            return; // no classic zone-tab borders in Arena
        }
        if (infoTab != null) { //draw background and border for selected zone if needed
            float x = selectedDisplayArea == null ? 0 : selectedDisplayArea.getLeft();
            float w = selectedDisplayArea == null ? 0 : selectedDisplayArea.getWidth();
            float top = selectedDisplayArea == null ? 0 : selectedDisplayArea.getTop();
            float h = selectedDisplayArea == null ? 0 : selectedDisplayArea.getHeight();
            float bottom = selectedDisplayArea == null ? 0 : selectedDisplayArea.getBottom();
            g.fillRect(Forge.isHorizontalTabLayout() ? getAltDisplayAreaBackColor() : getDisplayAreaBackColor(), x - pad, top, w + pad, h + pad);
            if (Forge.isHorizontalTabLayout())
                g.drawLine(1, MatchScreen.getBorderColor(), x, isFlipped() ? bottom : top, x + w, isFlipped() ? bottom : top);

            if (Forge.isLandscapeMode()) {
                g.drawLine(1, MatchScreen.getBorderColor(), x, top, x, bottom);
            } else {
                y = isFlipped() ? top + 1 : bottom;
                //don't know why infotab gets null here, either way don't crash the gui..
                float left = infoTab == null ? 0 : infoTab.getLeft();
                float right = infoTab == null ? 0 : infoTab.getRight();
                //leave gap at selected zone tab
                g.drawLine(1, MatchScreen.getBorderColor(), x, y, left, y);
                g.drawLine(1, MatchScreen.getBorderColor(), right, y, w, y);
            }
        }
        if (commandZone != null && commandZone.isVisible()) { //draw border for command zone if needed
            float x = commandZone.getLeft();
            y = commandZone.getTop();
            g.drawLine(1, MatchScreen.getBorderColor(), x, y, x, y + commandZone.getHeight());
            /*if (Forge.isHorizontalTabLayout())
                g.fillRect(getAltDisplayAreaBackColor(), x - pad, y, commandZoneWidth + pad, commandZone.getHeight() + pad);*/
            if (isFlipped()) {
                y += commandZone.getHeight();
            }
            g.drawLine(1, MatchScreen.getBorderColor(), x, y, x + commandZone.getWidth(), y);
        }
    }

    public Iterable<FScrollPane> getAllScrollPanes() {
        //Used to catalog scroll positions before resizing UI.
        ArrayList<FScrollPane> out = new ArrayList<>();
        out.add(field.getRow1());
        out.add(field.getRow2());
        for (InfoTabZone tab : zoneTabs.values())
            out.add(tab.displayArea);
        out.add(commandZone);
        out.addAll(extraTab.displayAreas.values());
        return out;
    }

    private class LifeLabel extends FDisplayObject {
        private int life = player.getLife();
        private int poisonCounters = player.getCounters(CounterEnumType.POISON);
        private int energyCounters = player.getCounters(CounterEnumType.ENERGY);
        private int experienceCounters = player.getCounters(CounterEnumType.EXPERIENCE);
        private int ticketCounters = player.getCounters(CounterEnumType.TICKET);
        private int radCounters = player.getCounters(CounterEnumType.RAD);
        private int manaShards = player.getNumManaShards();
        private String lifeStr = String.valueOf(life);

        private LifeLabel() {
        }

        private void update() {
            int delta = player.getLife() - life;
            player.setAvatarLifeDifference(player.getAvatarLifeDifference() + delta);
            if (delta != 0) {
                life = player.getLife();
                lifeStr = String.valueOf(life);
            }

            delta = player.getCounters(CounterEnumType.POISON) - poisonCounters;
            if (delta != 0) {
                //TODO: Show animation on avatar for gaining poison counters
                poisonCounters = player.getCounters(CounterEnumType.POISON);
            }

            energyCounters = player.getCounters(CounterEnumType.ENERGY);
            experienceCounters = player.getCounters(CounterEnumType.EXPERIENCE);
            ticketCounters = player.getCounters(CounterEnumType.TICKET);
            radCounters = player.getCounters(CounterEnumType.RAD);
            manaShards = player.getNumManaShards();
        }

        private void updateShards() {
            manaShards = player.getNumManaShards();
        }

        @Override
        public boolean tap(float x, float y, int count) {
            MatchController.instance.getGameController().selectPlayer(player, null); //treat tapping on life the same as tapping on the avatar
            return true;
        }

        @Override
        public void draw(Graphics g) {
            adjustHeight = 1;
            float divider = Gdx.app.getGraphics().getHeight() > 900 ? 1.2f : 2f;
            if (Forge.altPlayerLayout && !Forge.altZoneTabs && Forge.isLandscapeMode()) {
                if (poisonCounters == 0 && energyCounters == 0 && experienceCounters == 0 && ticketCounters == 0 && radCounters == 0 && manaShards == 0) {
                    g.fillRect(Color.DARK_GRAY, 0, 0, INFO2_FONT.getBounds(lifeStr).width + 1, INFO2_FONT.getBounds(lifeStr).height + 1);
                    g.drawText(lifeStr, INFO2_FONT, getInfoForeColor().getColor(), 0, 0, getWidth(), getHeight(), false, Align.left, false);
                } else {
                    float halfHeight = getHeight() / divider;
                    float textStart = halfHeight + Utils.scale(1);
                    float textWidth = getWidth() - textStart;
                    int mod = 1;
                    g.fillRect(Color.DARK_GRAY, 0, 0, INFO_FONT.getBounds(lifeStr).width + halfHeight + 1, INFO_FONT.getBounds(lifeStr).height + 1);
                    g.drawImage(FSkinImage.QUEST_LIFE, 0, 0, halfHeight, halfHeight);
                    g.drawText(lifeStr, INFO_FONT, getInfoForeColor().getColor(), textStart, 0, textWidth, halfHeight, false, Align.left, false);
                    if (poisonCounters > 0) {
                        g.fillRect(Color.DARK_GRAY, 0, halfHeight + 2, INFO_FONT.getBounds(String.valueOf(poisonCounters)).width + halfHeight + 1, INFO_FONT.getBounds(String.valueOf(poisonCounters)).height + 1);
                        g.drawImage(FSkinImage.POISON, 0, halfHeight + 2, halfHeight, halfHeight);
                        g.drawText(String.valueOf(poisonCounters), INFO_FONT, getInfoForeColor().getColor(), textStart, halfHeight + 2, textWidth, halfHeight, false, Align.left, false);
                        mod += 1;
                    }
                    if (energyCounters > 0) {
                        g.fillRect(Color.DARK_GRAY, 0, (halfHeight * mod) + 2, INFO_FONT.getBounds(String.valueOf(energyCounters)).width + halfHeight + 1, INFO_FONT.getBounds(String.valueOf(energyCounters)).height + 1);
                        g.drawImage(FSkinImage.ENERGY, 0, (halfHeight * mod) + 2, halfHeight, halfHeight);
                        g.drawText(String.valueOf(energyCounters), INFO_FONT, getInfoForeColor().getColor(), textStart, (halfHeight * mod) + 2, textWidth, halfHeight, false, Align.left, false);
                        mod += 1;
                    }
                    if (experienceCounters > 0) {
                        g.fillRect(Color.DARK_GRAY, 0, (halfHeight * mod) + 2, INFO_FONT.getBounds(String.valueOf(experienceCounters)).width + halfHeight + 1, INFO_FONT.getBounds(String.valueOf(experienceCounters)).height + 1);
                        g.drawImage(FSkinImage.COMMANDER, 0, (halfHeight * mod) + 2, halfHeight, halfHeight);
                        g.drawText(String.valueOf(experienceCounters), INFO_FONT, getInfoForeColor().getColor(), textStart, (halfHeight * mod) + 2, textWidth, halfHeight, false, Align.left, false);
                        mod += 1;
                    }
                    if (radCounters > 0) {
                        g.fillRect(Color.DARK_GRAY, 0, (halfHeight * mod) + 2, INFO_FONT.getBounds(String.valueOf(radCounters)).width + halfHeight + 1, INFO_FONT.getBounds(String.valueOf(radCounters)).height + 1);
                        g.drawImage(FSkinImage.RAD, 0, (halfHeight * mod) + 2, halfHeight, halfHeight);
                        g.drawText(String.valueOf(radCounters), INFO_FONT, getInfoForeColor().getColor(), textStart, (halfHeight * mod) + 2, textWidth, halfHeight, false, Align.left, false);
                        mod += 1;
                    }
                    if (ticketCounters > 0) {
                        g.fillRect(Color.DARK_GRAY, 0, (halfHeight * mod) + 2, INFO_FONT.getBounds(String.valueOf(ticketCounters)).width + halfHeight + 1, INFO_FONT.getBounds(String.valueOf(ticketCounters)).height + 1);
                        g.drawImage(FSkinImage.TICKET, 0, (halfHeight * mod) + 2, halfHeight, halfHeight);
                        g.drawText(String.valueOf(ticketCounters), INFO_FONT, getInfoForeColor().getColor(), textStart, (halfHeight * mod) + 2, textWidth, halfHeight, false, Align.left, false);
                        mod += 1;
                    }
                    if (manaShards > 0) {
                        g.fillRect(Color.DARK_GRAY, 0, (halfHeight * mod) + 2, INFO_FONT.getBounds(String.valueOf(manaShards)).width + halfHeight + 1, INFO_FONT.getBounds(String.valueOf(manaShards)).height + 1);
                        g.drawImage(FSkinImage.AETHER_SHARD, 0, (halfHeight * mod) + 2, halfHeight, halfHeight);
                        g.drawText(String.valueOf(manaShards), INFO_FONT, getInfoForeColor().getColor(), textStart, (halfHeight * mod) + 2, textWidth, halfHeight, false, Align.left, false);
                        mod += 1;
                    }
                    adjustHeight = (mod > 2) && (avatar.getHeight() < halfHeight * mod) ? mod : 1;
                }
            } else if (isArenaDuelLayout()) {
                paintArenaLifeBadge(g);
            } else {
                if (poisonCounters == 0 && energyCounters == 0 && manaShards == 0) {
                    g.drawText(lifeStr, Forge.altZoneTabs ? LIFE_FONT_ALT : LIFE_FONT, getInfoForeColor(), 0, 0, getWidth(), getHeight(), false, Align.center, true);
                } else {
                    float halfHeight = getHeight() / 2;
                    float textStart = halfHeight + Utils.scale(1);
                    float textWidth = getWidth() - textStart;
                    g.drawImage(FSkinImage.QUEST_LIFE, 0, 0, halfHeight, halfHeight);
                    g.drawText(lifeStr, INFO_FONT, getInfoForeColor(), textStart, 0, textWidth, halfHeight, false, Align.center, true);
                    if (poisonCounters > 0) { //prioritize showing poison counters over energy counters
                        g.drawImage(FSkinImage.POISON, 0, halfHeight, halfHeight, halfHeight);
                        g.drawText(String.valueOf(poisonCounters), INFO_FONT, getInfoForeColor(), textStart, halfHeight, textWidth, halfHeight, false, Align.center, true);
                    } else if (energyCounters > 0) { //prioritize showing energy counters over mana shards
                        g.drawImage(FSkinImage.ENERGY, 0, halfHeight, halfHeight, halfHeight);
                        g.drawText(String.valueOf(energyCounters), INFO_FONT, getInfoForeColor(), textStart, halfHeight, textWidth, halfHeight, false, Align.center, true);
                    } else {
                        g.drawImage(FSkinImage.MANASHARD, 0, halfHeight, halfHeight, halfHeight);
                        g.drawText(String.valueOf(manaShards), INFO_FONT, getInfoForeColor(), textStart, halfHeight, textWidth, halfHeight, false, Align.center, true);
                    }
                }
            }
        }

        /** Arena-style opaque life badge: large cream total + poison/energy when non-zero. */
        private void paintArenaLifeBadge(final Graphics g) {
            final float w = getWidth();
            final float h = getHeight();
            g.resetAlphaComposite();
            g.fillRect(ARENA_LIFE_BADGE_FILL, 0, 0, w, h);
            g.drawRect(3f, ARENA_LIFE_BADGE_BORDER, 0, 0, w, h);
            final boolean showCounters = poisonCounters > 0 || energyCounters > 0;
            final float lifeH = showCounters ? h * 0.62f : h;
            // Outlined cream digits so the total stays readable on any backdrop.
            g.drawOutlinedText(lifeStr, ARENA_LIFE_FONT, ARENA_LIFE_BADGE_INK, Color.BLACK,
                    2f, 1f, w - 4f, lifeH, false, Align.center, true);
            if (showCounters) {
                final float cy = lifeH;
                final float ch = h - lifeH;
                final float icon = Math.min(ch - 2f, 14f);
                float x = 3f;
                if (poisonCounters > 0) {
                    g.drawImage(FSkinImage.POISON, x, cy + (ch - icon) / 2f, icon, icon);
                    g.drawText(String.valueOf(poisonCounters), ARENA_LIFE_COUNTER_FONT, ARENA_LIFE_BADGE_INK,
                            x + icon + 1f, cy, w - x - icon - 2f, ch, false, Align.left, true);
                    x += icon + ARENA_LIFE_COUNTER_FONT.getBounds(String.valueOf(poisonCounters)).width + Utils.scale(6);
                }
                if (energyCounters > 0) {
                    g.drawImage(FSkinImage.ENERGY, x, cy + (ch - icon) / 2f, icon, icon);
                    g.drawText(String.valueOf(energyCounters), ARENA_LIFE_COUNTER_FONT, ARENA_LIFE_BADGE_INK,
                            x + icon + 1f, cy, w - x - icon - 2f, ch, false, Align.left, true);
                }
            }
        }

        /** Life total shown on the Arena badge (for GL pixel/text asserts). */
        String getArenaLifeLabel() {
            return lifeStr;
        }
    }

    /** Screen-space bounds of the Arena life badge, or null when hidden. */
    public float[] getArenaLifeBadgeScreenBounds() {
        if (!isArenaDuelLayout() || lblLife == null || !lblLife.isVisible()
                || lblLife.getWidth() < 2f || lblLife.getHeight() < 2f) {
            return null;
        }
        return new float[] {
                lblLife.screenPos.x, lblLife.screenPos.y,
                lblLife.getWidth(), lblLife.getHeight()
        };
    }

    /** Label text currently painted on the Arena life badge. */
    public String getArenaLifeLabel() {
        return lblLife == null ? null : lblLife.getArenaLifeLabel();
    }

    /**
     * A tab in the player panel, which toggles the visibility of the player's zones or mana pool.
     */
    public abstract class InfoTab extends FDisplayObject {
        protected String value = "0";
        protected FSkinImageInterface icon;

        protected InfoTab(FSkinImageInterface icon) {
            this.icon = icon;
        }

        public FSkinImageInterface getIcon() {
            return icon;
        }

        public abstract VDisplayArea getDisplayArea();

        public abstract void setDisplayVisible(boolean visible);

        public abstract void setDisplayBounds(float x, float y, float width, float height);

        public abstract void setRotate180(boolean rotate180);

        public abstract void update();

        public abstract void reset();

        public abstract float getIdealWidth(float pref);

        protected boolean isSelected() {
            return selectedTab == this;
        }

        protected FSkinColor getSelectedBackgroundColor() {
            return getDisplayAreaBackColor();
        }

        protected boolean isAlignedRightForAltDisplay() {
            return false;
        }

        @Override
        public void draw(Graphics g) {
            float x, y, w, h;
            boolean drawOverlay = MatchController.getView().selectedPlayerPanel().getPlayer() == player && Forge.hasGamepad();
            if (Forge.altZoneTabs && this.isAlignedRightForAltDisplay()) {
                //draw extra
                g.fillRect(FSkinColor.get(Forge.isMobileAdventureMode ? Colors.ADV_CLR_THEME2 : Colors.CLR_THEME2), 0, 0, getWidth(), getHeight());
                if (isSelected()) {
                    if (drawOverlay)
                        g.fillRect(FSkinColor.getStandardColor(50, 200, 150).alphaColor(0.3f), 0, isFlipped() ? INFO_TAB_PADDING_Y : 0, getWidth(), getHeight() - INFO_TAB_PADDING_Y);
                    g.fillRect(getDisplayAreaBackColor(), 0, isFlipped() ? INFO_TAB_PADDING_Y : 0, getWidth(), getHeight() - INFO_TAB_PADDING_Y);
                }
            }
            if (isSelected()) {
                y = 0;
                w = getWidth();
                h = getHeight();
                float yAcross;
                if (isFlipped()) {
                    y += INFO_TAB_PADDING_Y;
                    yAcross = y;
                    y--;
                    h++;
                } else {
                    h -= INFO_TAB_PADDING_Y;
                    yAcross = h;
                    y--;
                    h += 2;
                }
                if (drawOverlay)
                    g.fillRect(FSkinColor.getStandardColor(50, 200, 150).alphaColor(0.3f), 0, isFlipped() ? INFO_TAB_PADDING_Y : 0, w, getHeight() - INFO_TAB_PADDING_Y);
                //change the graveyard tab selection color to active phase color to indicate the player has delirium
                g.fillRect(this.getSelectedBackgroundColor(), 0, isFlipped() ? INFO_TAB_PADDING_Y : 0, w, getHeight() - INFO_TAB_PADDING_Y);
                if (!Forge.isLandscapeMode()) {
                    if (isFlipped()) { //use clip to ensure all corners connect
                        g.startClip(-1, y, w + 2, h);
                    } else {
                        g.startClip(-1, y, w + 2, yAcross - y);
                    }
                    if (forMultiPlayer) {
                        g.drawLine(1, MatchScreen.getBorderColor(), 0, yAcross, w, yAcross);
                        g.drawLine(1, MatchScreen.getBorderColor(), 0, y, 0, h);
                        g.drawLine(1, MatchScreen.getBorderColor(), w, y, w, h);
                    }
                    g.endClip();
                }
            }

            FSkinImageInterface icon = this.getIcon();

            //show image left of text if wider than tall
            if (getWidth() > getHeight()) {
                float maxImageWidth = getWidth() - INFO_FONT.getBounds("0").width - 3 * INFO_TAB_PADDING_X;
                w = icon.getNearestHQWidth(maxImageWidth);
                if (w > maxImageWidth) {
                    w /= 2;
                }
                h = icon.getHeight() * w / icon.getWidth();
                float maxImageHeight = getHeight() - 2 * INFO_TAB_PADDING_Y;
                if (h > maxImageHeight) {
                    h = icon.getNearestHQHeight(maxImageHeight);
                    if (h > maxImageWidth) {
                        h /= 2;
                    }
                    w = icon.getWidth() * h / icon.getHeight();
                }
                x = INFO_TAB_PADDING_X + (maxImageWidth - w) / 2;
                y = (getHeight() - h) / 2;
                if (lblLife.getRotate180()) {
                    g.startRotateTransform(x + w / 2, y + h / 2, 180);
                }
                float mod = isHovered() ? w / 8f : 0;
                g.drawImage(icon, x - mod / 2, y - mod / 2, w + mod, h + mod);
                if (lblLife.getRotate180()) {
                    g.endTransform();
                }

                x += w + INFO_TAB_PADDING_X;
                int alignX = Align.left;
                if (lblLife.getRotate180()) {
                    g.startRotateTransform(x + (getWidth() - x + 1) / 2, getHeight() / 2, 180);
                    alignX = Align.right;
                }
                g.drawText(value, INFO_FONT, getInfoForeColor(), x, 0, getWidth() - x + 1, getHeight(), false, alignX, true);
                if (lblLife.getRotate180()) {
                    g.endTransform();
                }
            } else { //show image above text if taller than wide
                if (lblLife.getRotate180()) {
                    g.startRotateTransform(getWidth() / 2, getHeight() / 2, 180);
                }
                float maxImageWidth = getWidth() - 2 * INFO_TAB_PADDING_X;
                w = icon.getNearestHQWidth(maxImageWidth);
                if (w > maxImageWidth) {
                    w /= 2;
                }
                h = icon.getHeight() * w / icon.getWidth();
                x = (getWidth() - w) / 2;
                y = INFO_TAB_PADDING_Y;
                float mod = isHovered() ? w / 8f : 0;
                g.drawImage(icon, x - mod / 2, y - mod / 2, w + mod, h + mod);

                y += h + INFO_TAB_PADDING_Y;
                g.drawText(value, INFO_FONT, getInfoForeColor(), 0, y, getWidth(), getHeight() - y + 1, false, Align.center, false);
                if (lblLife.getRotate180()) {
                    g.endTransform();
                }
            }
        }
    }

    /**
     * Player panel tab linked to a single display area - usually either the mana pool or a zone via InfoTabZone.
     */
    public class InfoTabSingleDisplay extends InfoTab {
        protected final VDisplayArea displayArea;

        private InfoTabSingleDisplay(FSkinImageInterface icon, VDisplayArea displayArea) {
            super(icon);
            this.displayArea = displayArea;
        }

        public VDisplayArea getDisplayArea() {
            return displayArea;
        }

        public void setDisplayVisible(boolean visible) {
            this.displayArea.setVisible(visible);
        }

        public void setDisplayBounds(float x, float y, float width, float height) {
            this.displayArea.setBounds(x, y, width, height);
        }

        public void setRotate180(boolean rotate180) {
            this.displayArea.setRotate180(rotate180);
        }

        @Override
        public boolean tap(float x, float y, int count) {
            if (isSelected())
                setSelectedTab(null);
            else
                setSelectedTab(this);
            return true;
        }

        public void update() {
            displayArea.update();
            value = String.valueOf(displayArea.getCount());
        }

        @Override
        public void reset() {
        } //Mana Display does not get cleared.

        @Override
        public float getIdealWidth(float pref) {
            return pref;
        }
    }

    /**
     * Player panel tab for a single typical card zone, such as the hand.
     */
    public class InfoTabZone extends InfoTabSingleDisplay {
        public final ZoneType zoneType;

        private InfoTabZone(VDisplayArea displayArea, ZoneType zoneType) {
            super(iconFromZone(zoneType), displayArea);
            this.zoneType = zoneType;
        }

        private final EnumSet<ZoneType> altDisplayZones = EnumSet.of(ZoneType.Hand, ZoneType.Library, ZoneType.Graveyard, ZoneType.Exile);

        public boolean isAlignedRightForAltDisplay() {
            return altDisplayZones.contains(this.zoneType);
        }

        @Override
        protected FSkinColor getSelectedBackgroundColor() {
            if ((this.zoneType == ZoneType.Graveyard) && player.hasDelirium())
                return getDeliriumHighlight();
            return super.getSelectedBackgroundColor();
        }

        @Override
        public void reset() {
            displayArea.clear();
        }

        @Override
        public float getIdealWidth(float pref) {
            if (displayArea instanceof VCardDisplayArea vCardDisplayArea) {
                float cardWidth = vCardDisplayArea.getCardWidth(vCardDisplayArea.getHeight());
                float size = vCardDisplayArea.getCount();
                return Math.min(cardWidth * size, pref);
            }
            return pref;
        }

        @Override
        public void update() {
            super.update();
            if (selectedTab != null && Forge.isHorizontalTabLayout())
                updateTabLayout(initW, initH);
        }
    }

    /**
     * Player panel tab that can contain several extra rarely-used zones. Displays a dropdown when clicked, letting the
     * player pick which one to show.
     */
    public class InfoTabExtra extends InfoTab {
        private static final FSkinImageInterface DEFAULT_ICON = FSkinImage.HDSTAR_OUTLINE;

        private final EnumMap<ZoneType, VDisplayArea> displayAreas;
        private ZoneType activeZone;
        private boolean hasCardsInExtraZone = false;

        private InfoTabExtra() {
            super(DEFAULT_ICON);
            this.displayAreas = new EnumMap<>(ZoneType.class);
            for (ZoneType zoneType : EXTRA_ZONES) {
                if (player.getCards(zoneType).isEmpty())
                    continue;
                createZoneIfMissing(zoneType);
                hasCardsInExtraZone = true;
            }
            VZoneDisplay sb = VPlayerPanel.this.add(new VZoneDisplay(player, ZoneType.Sideboard));
            this.displayAreas.put(ZoneType.Sideboard, sb);
            this.activeZone = ZoneType.Sideboard;
            this.updateTab();
        }

        public void createZoneIfMissing(ZoneType zone) {
            if (this.displayAreas.containsKey(zone))
                return;
            VZoneDisplay display = VPlayerPanel.this.add(new VZoneDisplay(player, zone));
            this.displayAreas.put(zone, display);
            this.hasCardsInExtraZone = true;
            if (zone == ZoneType.AttractionDeck || zone == ZoneType.ContraptionDeck)
                createZoneIfMissing(ZoneType.Junkyard); //If the game uses one, it uses both.
        }

        public void setActiveZone(ZoneType zone) {
            if (this.activeZone == zone)
                return;
            createZoneIfMissing(zone);
            getDisplayArea().setVisible(false);
            this.activeZone = zone;
            if (isSelected())
                getDisplayArea().setVisible(true);
            updateTab();
        }

        public void setActiveZoneByIndex(int index) {
            List<ZoneType> keyList = List.copyOf(displayAreas.keySet());
            setActiveZone(keyList.get(index % keyList.size()));
        }

        private void updateTab() {
            if (!hasCardsInExtraZone)
                this.value = "";
            else if (!getDisplayArea().isVisible())
                this.value = "+";
            else
                this.value = String.valueOf(displayAreas.get(this.activeZone).getCount());
            if (getDisplayArea().isVisible())
                this.icon = iconFromZone(this.activeZone);
            else
                this.icon = DEFAULT_ICON;
        }


        @Override
        public VDisplayArea getDisplayArea() {
            return displayAreas.get(activeZone);
        }

        @Override
        public void setDisplayVisible(boolean visible) {
            if (!visible)
                displayAreas.values().forEach(d -> d.setVisible(false));
            else
                getDisplayArea().setVisible(true);
            updateTab();
        }

        @Override
        public void setDisplayBounds(float x, float y, float width, float height) {
            displayAreas.values().forEach(d -> d.setBounds(x, y, width, height));
        }

        @Override
        public void setRotate180(boolean rotate180) {
            displayAreas.values().forEach(d -> d.setRotate180(rotate180));
        }

        @Override
        public void update() {
            displayAreas.values().forEach(VDisplayArea::update);
            updateTab();
        }

        public void update(ZoneType zoneType) {
            if (!displayAreas.containsKey(zoneType)) {
                if (!EXTRA_ZONES.contains(zoneType))
                    return;
                if (player.getCards(zoneType).isEmpty())
                    return;
                createZoneIfMissing(zoneType);
            }
            displayAreas.get(zoneType).update();
            updateTab();
        }

        @Override
        public void reset() {
            Iterator<Map.Entry<ZoneType, VDisplayArea>> iterator = displayAreas.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ZoneType, VDisplayArea> e = iterator.next();
                VDisplayArea display = e.getValue();
                display.clear();
                //Remove all zones besides sideboard, as this tab is in its initial state.
                //Seems appropriate when resetting, but this code path only gets visited from resetFields in
                //MatchScreen.java, which only gets called when initiating an online game. I'm not sure why zones are
                //specifically cleared at that point, but I'm not really able to test this part right now. Feel free to
                //uncomment and see what happens, or delete entirely if unnecessary.
                //if (e.getKey() == ZoneType.Sideboard)
                //    continue;
                //VPlayerPanel.this.remove(display);
                //iterator.remove();
            }
            activeZone = ZoneType.Sideboard;
        }

        @Override
        public float getIdealWidth(float pref) {
            if (getDisplayArea() instanceof VCardDisplayArea vCardDisplayArea) {
                float cardWidth = vCardDisplayArea.getCardWidth(vCardDisplayArea.getHeight());
                float size = vCardDisplayArea.getCount();
                return Math.min(cardWidth * size, pref);
            }
            return pref;
        }

        @Override
        public boolean tap(float x, float y, int count) {
            if (this.displayAreas.isEmpty())
                return false;
            if (count >= 2) {
                onClickZone(this.activeZone);
                return true;
            }
            FPopupMenu menu = new FPopupMenu() {
                @Override
                protected void buildMenu() {
                    for (ZoneType zone : displayAreas.keySet()) {
                        String label = WordUtils.capitalize(zone.getTranslatedName());
                        addItem(new FMenuItem(label, iconFromZone(zone), (e) -> onClickZone(zone)));
                    }
                }
            };
            menu.show(this, this.getWidth(), 0);
            return true;
        }

        public void onClickZone(ZoneType zone) {
            if (activeZone == zone && this.isSelected()) {
                setSelectedTab(null);
                return;
            }
            setActiveZone(zone);
            setSelectedTab(this);
        }
    }

    private class CommandZoneDisplay extends VZoneDisplay {
        private CommandZoneDisplay(PlayerView player0) {
            super(player0, ZoneType.Command);
        }

        @Override
        protected void refreshCardPanels(Iterable<CardView> model) {
            int oldCount = getCount();
            super.refreshCardPanels(model);
            int newCount = getCount();
            if (newCount != oldCount) {
                setVisible(newCount > 0);
                VPlayerPanel.this.revalidate(); //need to revalidated entire panel when command zone size changes
            }
        }

        @Override
        protected boolean layoutVerticallyForLandscapeMode() {
            return false;
        }
    }

    @Override
    public boolean keyDown(int keyCode) {
        if (MatchController.getView().selectedPlayerPanel() == this && !((FMenuBar) MatchController.getView().getHeader()).isShowingMenu(true)) {
            if (keyCode == Input.Keys.BUTTON_B) {
                MatchScreen.nullPotentialListener();
                closeSelectedTab();
                return true;
            }
            if (keyCode == Input.Keys.BUTTON_R1) {
                setNextSelectedTab(false);
                return true;
            }
        }
        return super.keyDown(keyCode);
    }
}
