package forge.screens.match.views;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinColor;
import forge.assets.FSkinColor.Colors;
import forge.assets.FSkinFont;
import forge.game.phase.PhaseType;
import forge.screens.match.ModernDuelController;
import forge.screens.match.ModernDuelScreen;
import forge.toolbox.FContainer;
import forge.toolbox.FDisplayObject;
import forge.util.TextBounds;
import forge.util.Utils;

public class VPhaseIndicator extends FContainer {
    public static final FSkinFont BASE_FONT = FSkinFont.get(11);
    public static final float PADDING_X = Utils.scale(1);
    public static final float PADDING_Y = Utils.scale(2);

    /** True when ArenaDuelScreen hosts this indicator in the phaseRail band. */
    private boolean arenaRailHosted;

    public boolean isArenaRailHosted() {
        return arenaRailHosted;
    }

    public void setArenaRailHosted(final boolean hosted) {
        arenaRailHosted = hosted;
    }

    private boolean arenaPhaseChromeSuppressed() {
        // Skip classic leftover strip when Arena is active but this indicator is
        // still zero-sized inside a player panel (not yet / never hosted on the rail).
        return forge.screens.match.ArenaDuelUi.isActive()
                && !arenaRailHosted
                && (getWidth() < 1f || getHeight() < 1f);
    }

    @Override
    public void draw(final Graphics g) {
        if (arenaPhaseChromeSuppressed()) {
            return;
        }
        super.draw(g);
    }

    private static final Color YIELD_MARKER_COLOR = new Color(0xFFA528FF);
    private static final Color PAD_FOCUS_BORDER = new Color(1f, 1f, 1f, 0.95f);
    private static final Color ARENA_LABEL_INK = new Color(0.96f, 0.97f, 0.99f, 1f);
    private static final Color ARENA_ACTIVE_FILL = new Color(0.92f, 0.72f, 0.28f, 1f);
    private static final Color ARENA_ACTIVE_BORDER = new Color(1f, 0.92f, 0.55f, 1f);
    private static final Color ARENA_STOP_FILL = new Color(0.28f, 0.55f, 0.72f, 1f);
    private static final Color ARENA_IDLE_FILL = new Color(0.22f, 0.24f, 0.28f, 1f);

    /** Rail order for controller cycling (matches visual left-to-right / top-to-bottom). */
    public static final PhaseType[] PHASE_ORDER = {
            PhaseType.UPKEEP, PhaseType.DRAW, PhaseType.MAIN1,
            PhaseType.COMBAT_BEGIN, PhaseType.COMBAT_DECLARE_ATTACKERS, PhaseType.COMBAT_DECLARE_BLOCKERS,
            PhaseType.COMBAT_FIRST_STRIKE_DAMAGE, PhaseType.COMBAT_DAMAGE, PhaseType.COMBAT_END,
            PhaseType.MAIN2, PhaseType.END_OF_TURN, PhaseType.CLEANUP
    };

    private final Map<PhaseType, PhaseLabel> phaseLabels = new HashMap<>();
    private FSkinFont font;
    private int padFocusIndex = -1;

    public VPhaseIndicator() {
        resetFont();
        addPhaseLabel("UP", PhaseType.UPKEEP);
        addPhaseLabel("DR", PhaseType.DRAW);
        addPhaseLabel("M1", PhaseType.MAIN1);
        addPhaseLabel("BC", PhaseType.COMBAT_BEGIN);
        addPhaseLabel("DA", PhaseType.COMBAT_DECLARE_ATTACKERS);
        addPhaseLabel("DB", PhaseType.COMBAT_DECLARE_BLOCKERS);
        addPhaseLabel("FS", PhaseType.COMBAT_FIRST_STRIKE_DAMAGE);
        addPhaseLabel("CD", PhaseType.COMBAT_DAMAGE);
        addPhaseLabel("EC", PhaseType.COMBAT_END);
        addPhaseLabel("M2", PhaseType.MAIN2);
        addPhaseLabel("ET", PhaseType.END_OF_TURN);
        addPhaseLabel("CL", PhaseType.CLEANUP);
    }

    public void setPadFocusIndex(final int index) {
        padFocusIndex = index;
        for (int i = 0; i < PHASE_ORDER.length; i++) {
            final PhaseLabel lbl = phaseLabels.get(PHASE_ORDER[i]);
            if (lbl != null) {
                lbl.setPadFocused(i == index);
            }
        }
    }

    public int getPadFocusIndex() {
        return padFocusIndex;
    }

    /** Toggle stop on the pad-focused phase label. */
    public boolean togglePadFocusedStop() {
        if (padFocusIndex < 0 || padFocusIndex >= PHASE_ORDER.length) {
            return false;
        }
        final PhaseLabel lbl = phaseLabels.get(PHASE_ORDER[padFocusIndex]);
        if (lbl == null) {
            return false;
        }
        lbl.tap(0, 0, 1);
        return true;
    }

    private void addPhaseLabel(String caption, PhaseType phaseType) {
        phaseLabels.put(phaseType, add(new PhaseLabel(caption)));
    }

    public PhaseLabel getLabel(PhaseType phaseType) {
        return phaseLabels.get(phaseType);
    }

    public Iterable<PhaseLabel> allLabels() {
        return phaseLabels.values();
    }

    public void resetPhaseButtons() {
        for (PhaseLabel lbl : phaseLabels.values()) {
            lbl.setActive(false);
        }
    }

    public void resetFont() {
        font = BASE_FONT;
    }

    public float getPreferredHeight(float width) {
        //build string to use to determine ideal font
        float w = width / phaseLabels.size();
        w -= 2 * PADDING_X;
        resetFont();
        return _getPreferredHeight(w);
    }
    private float _getPreferredHeight(float w) {
        TextBounds bounds = null;
        for (PhaseLabel lbl : phaseLabels.values()) {
            bounds = font.getBounds(lbl.caption);
            if (bounds.width > w) {
                if (font.canShrink()) {
                    font = font.shrink();
                    return _getPreferredHeight(w);
                }
                break;
            }
        }
        return bounds.height + 2 * PADDING_Y;
    }

    @Override
    protected void doLayout(float width, float height) {
        if (width > height) {
            float x = 0;
            float w = width / phaseLabels.size();
            float h = height;

            for (FDisplayObject lbl : getChildren()) {
                lbl.setBounds(x, 0, w, h);
                x += w;
            }
        }
        else {
            float padding = Utils.scale(1);
            float y = 0;
            float w = width - 2 * padding;
            float h = height / phaseLabels.size();

            for (FDisplayObject lbl : getChildren()) {
                lbl.setBounds(padding, y + padding, w, h - 2 * padding);
                y += h;
            }
        }
    }

    public class PhaseLabel extends FDisplayObject {
        private final String caption;
        private boolean stopAtPhase = false;
        private boolean active = false;
        private boolean yieldMarked = false;
        private boolean padFocused = false;
        private Runnable onToggled;
        private Runnable onLongPress;

        public PhaseLabel(String caption0) {
            caption = caption0;
        }

        public void setPadFocused(final boolean v) {
            padFocused = v;
        }

        public boolean getActive() {
            return active;
        }
        public void setActive(boolean active0) {
            active = active0;
        }

        public boolean getStopAtPhase() {
            return stopAtPhase;
        }
        public void setStopAtPhase(boolean stopAtPhase0) {
            stopAtPhase = stopAtPhase0;
        }

        public boolean isYieldMarked() {
            return yieldMarked;
        }
        public void setYieldMarked(boolean v) {
            this.yieldMarked = v;
        }

        /** Fires after the user toggles this label by tapping. */
        public void setOnToggled(Runnable r) {
            onToggled = r;
        }

        /** Fires when the user long-presses this label. */
        public void setOnLongPress(Runnable r) {
            onLongPress = r;
        }

        @Override
        public boolean tap(float x, float y, int count) {
            // Taps are touch — keep pad/touch arrow mode consistent with modern duel.
            if (ModernDuelScreen.enabled()) {
                ModernDuelController.get().markTouchInput();
            }
            stopAtPhase = !stopAtPhase;
            if (onToggled != null) onToggled.run();
            return true;
        }

        @Override
        public boolean longPress(float x, float y) {
            if (onLongPress == null) {
                return false;
            }
            onLongPress.run();
            return true;
        }

        @Override
        public void draw(final Graphics g) {
            if (arenaPhaseChromeSuppressed()) {
                return;
            }
            if (font == null) {
                resetFont();
            }
            float x = PADDING_X;
            float w = getWidth() - 2 * PADDING_X;
            float h = getHeight();
            final boolean arenaRail = arenaRailHosted;

            //determine back color according to skip or active state of label
            if (yieldMarked) {
                g.fillRect(YIELD_MARKER_COLOR, x, 0, w, h);
                drawChevron(g, x, w, h);
                // Skip the caption when marked — chevron replaces the phase abbreviation.
            } else if (arenaRail) {
                // High-contrast Arena rail: idle / stop / active with white captions.
                final Color fill;
                if (active) {
                    fill = ARENA_ACTIVE_FILL;
                } else if (stopAtPhase) {
                    fill = ARENA_STOP_FILL;
                } else {
                    fill = ARENA_IDLE_FILL;
                }
                g.fillRect(isHovered() || padFocused ? brighter(fill) : fill, x, 0, w, h);
                if (active) {
                    g.drawRect(Math.max(2f, Utils.scale(2)), ARENA_ACTIVE_BORDER, x, 0, w, h);
                }
                final FSkinFont labelFont = h >= Utils.scale(18) ? FSkinFont.get(12) : FSkinFont.get(10);
                g.drawText(caption, labelFont, ARENA_LABEL_INK, x, 0, w, h, false, Align.center, true);
            } else {
                FSkinColor backColor;
                if (active && stopAtPhase) {
                    backColor = Forge.isMobileAdventureMode ? FSkinColor.get(Colors.ADV_CLR_PHASE_ACTIVE_ENABLED) : FSkinColor.get(Colors.CLR_PHASE_ACTIVE_ENABLED);
                }
                else if (!active && stopAtPhase) {
                    backColor = Forge.isMobileAdventureMode ? FSkinColor.get(Colors.ADV_CLR_PHASE_INACTIVE_ENABLED) : FSkinColor.get(Colors.CLR_PHASE_INACTIVE_ENABLED);
                }
                else if (active && !stopAtPhase) {
                    backColor = Forge.isMobileAdventureMode ? FSkinColor.get(Colors.ADV_CLR_PHASE_ACTIVE_DISABLED) : FSkinColor.get(Colors.CLR_PHASE_ACTIVE_DISABLED);
                }
                else {
                    backColor = Forge.isMobileAdventureMode ? FSkinColor.get(Colors.ADV_CLR_PHASE_INACTIVE_DISABLED) : FSkinColor.get(Colors.CLR_PHASE_INACTIVE_DISABLED);
                }
                g.fillRect(isHovered() || padFocused ? backColor.brighter() : backColor, x, 0, w, h);
                g.drawText(caption, isHovered() && font.canIncrease() ? font.increase() : font, Color.BLACK, x, 0, w, h, false, Align.center, true);
            }
            if (padFocused) {
                g.drawRect(Utils.scale(2), PAD_FOCUS_BORDER, x, 0, w, h);
            }
        }

        private static Color brighter(final Color c) {
            return new Color(Math.min(1f, c.r * 1.15f), Math.min(1f, c.g * 1.15f),
                    Math.min(1f, c.b * 1.15f), c.a);
        }

        private void drawChevron(final Graphics g, float x, float w, float h) {
            // Two back-to-back triangles centered in the cell
            float size = Math.max(Utils.scale(6f), h * 0.55f);
            float cx = x + (w - size) / 2f;
            float cy = (h - size) / 2f;
            g.fillTriangle(Color.BLACK, cx,            cy,            cx + size / 2f, cy + size / 2f, cx,            cy + size);
            g.fillTriangle(Color.BLACK, cx + size / 2f, cy,            cx + size,      cy + size / 2f, cx + size / 2f, cy + size);
        }
    }
}
