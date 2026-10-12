package forge.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout and scripted-combat math for {@link BattlefieldPrototype}.
 * No libGDX and no Forge rules. Sizes are fractions of window height unless noted.
 */
public final class BattlefieldLayout {
    public static final float CARD_HEIGHT_FRAC = 0.16f;
    public static final float OPPONENT_LAND_Y = 0.86f;
    public static final float OPPONENT_CREATURE_Y = 0.67f;
    public static final float PLAYER_CREATURE_Y = 0.42f;
    public static final float PLAYER_LAND_Y = 0.26f;
    /** Pointer at or above this fraction of H is in the opponent's half. */
    public static final float ATTACK_LINE_FRAC = 0.55f;
    /** Generous snap radius for tremor-friendly drops, as a fraction of H. */
    public static final float SNAP_RADIUS_FRAC = 0.14f;
    public static final float ATTACK_SLIDE_FRAC = 0.05f;
    public static final float ROW_WIDTH_FRAC = 0.50f;
    public static final float TAP_DEG = 90f;
    public static final int SHOCK_DAMAGE = 2;
    /** Side log, clear of the centred rows. Fractions of window width or height. */
    public static final float STACK_COLUMN_X_FRAC = 0.115f;
    public static final float STACK_COLUMN_W_FRAC = 0.20f;
    public static final float STACK_TOP_FRAC = 0.73f;
    public static final float STACK_ROW_FRAC = 0.155f;
    public static final float STACK_ROW_EXAMINED_FRAC = 0.36f;
    /** Thumbnail height as a fraction of a battlefield card. Small enough that the board stays clickable. */
    public static final float STACK_THUMB_FRAC = 0.42f;
    public static final String TARGET_PROMPT = "Choose a target.";

    public enum Side { YOU, OPPONENT }
    public enum Row { LAND, CREATURE }
    public enum Kind { LAND, CREATURE, SPELL }
    /** Instants may be cast onto a nonempty stack. Permanents and sorceries wait. */
    public enum Speed { INSTANT, SORCERY }

    private BattlefieldLayout() {
    }

    public static float cardHeight(float windowH) {
        return CARD_HEIGHT_FRAC * windowH;
    }

    public static float cardWidth(float windowH) {
        return cardHeight(windowH) * HandLayout.CARD_ASPECT;
    }

    public static float rowCenterY(float windowH, Side side, Row row) {
        float frac;
        if (side == Side.OPPONENT) {
            frac = row == Row.LAND ? OPPONENT_LAND_Y : OPPONENT_CREATURE_Y;
        } else {
            frac = row == Row.LAND ? PLAYER_LAND_Y : PLAYER_CREATURE_Y;
        }
        return frac * windowH;
    }

    /**
     * Slot {@code i} of {@code n} in a centred row. A tapped card rotates in place;
     * the row does not reflow when it taps.
     */
    public static HandLayout.Pose slot(float windowW, float windowH, Side side, Row row,
                                        int i, int n, boolean tapped) {
        float ch = cardHeight(windowH);
        float cw = cardWidth(windowH);
        float step = 0f;
        if (n > 1) {
            float span = Math.min(ROW_WIDTH_FRAC * windowW, n * cw * 1.25f);
            step = (span - cw) / (n - 1);
        }
        float cx = windowW / 2f + (i - (n - 1) / 2f) * step;
        float cy = rowCenterY(windowH, side, row);
        float rot = 0f;
        if (tapped) {
            rot = side == Side.YOU ? -TAP_DEG : TAP_DEG;
        }
        return new HandLayout.Pose(cx, cy, cw, ch, rot, 1f);
    }

    /**
     * libGDX reports pointer Y downward from the top of the window.
     * Layout space grows upward from the bottom, so one subtraction is the conversion
     * for drawing, hit-testing, and the targeting-arrow tip.
     */
    public static float screenYToLayoutY(float windowH, float screenY) {
        return windowH - screenY;
    }

    /**
     * Index of the rectangle that contains the pointer, testing from the last entry
     * (drawn on top) toward the first. A nearby rectangle that does not contain the
     * point is not a hit, so an empty board does not stick to the nearest card.
     */
    public static int stickIndexIfInside(float px, float py, float[] cx, float[] cy, float[] w, float[] h) {
        if (cx == null || cy == null || w == null || h == null) {
            return -1;
        }
        int n = Math.min(Math.min(cx.length, cy.length), Math.min(w.length, h.length));
        for (int i = n - 1; i >= 0; i--) {
            if (Math.abs(px - cx[i]) <= w[i] / 2f && Math.abs(py - cy[i]) <= h[i] / 2f) {
                return i;
            }
        }
        return -1;
    }

    public static boolean inOpponentHalf(float windowH, float pointerY) {
        return pointerY >= ATTACK_LINE_FRAC * windowH;
    }

    /**
     * Attack declaration. The whole opponent half counts, and a release near their
     * life badge still counts when the pointer stops short of the line.
     */
    public static boolean declaresAttack(float windowH, float pointerX, float pointerY,
                                          float badgeX, float badgeY) {
        if (inOpponentHalf(windowH, pointerY)) {
            return true;
        }
        return within(pointerX, pointerY, badgeX, badgeY, SNAP_RADIUS_FRAC * windowH);
    }

    /** Index of the nearest point within {@code radius}, or -1. */
    public static int snapNearest(float px, float py, float[] xs, float[] ys, float radius) {
        if (xs == null || ys == null || xs.length == 0 || xs.length != ys.length || radius < 0f) {
            return -1;
        }
        int best = -1;
        float bestD = radius;
        for (int i = 0; i < xs.length; i++) {
            float d = dist(px, py, xs[i], ys[i]);
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public static int snapBlocker(float windowH, float px, float py, float[] attackerX, float[] attackerY) {
        return snapNearest(px, py, attackerX, attackerY, SNAP_RADIUS_FRAC * windowH);
    }

    /** Movement below the hand prototype's drag threshold is tremor, not a drag. */
    public static boolean ignoredJitter(float movePx) {
        return movePx < HandLayout.DRAG_THRESHOLD_PX;
    }

    public static boolean canAttack(Kind kind, boolean tapped, boolean summoningSick) {
        return kind == Kind.CREATURE && !tapped && !summoningSick;
    }

    public static boolean canActivate(Kind kind, boolean tapped, boolean summoningSick) {
        return kind == Kind.CREATURE && !tapped && !summoningSick;
    }

    /** A blank choice still prompts; a chosen type stays printed as that word. */
    public static String printedChoice(String chosenType) {
        if (chosenType == null || chosenType.isBlank()) {
            return "Choose a creature type";
        }
        return chosenType;
    }

    public static int shownPower(int base, int buff, int debuff, int plusCounters) {
        return base + buff + plusCounters - debuff;
    }

    public static int shownToughness(int base, int buff, int debuff, int plusCounters) {
        return shownPower(base, buff, debuff, plusCounters);
    }

    public static String abilityPreview(String source, String target, int countersAfter) {
        return source + " taps. " + target + " will show " + countersAfter + " +1/+1.";
    }

    public static String shockOnCreature(String name, int toughness, int damageAlready, int incoming) {
        int total = damageAlready + incoming;
        if (total >= toughness) {
            return name + " takes " + incoming + " and will die.";
        }
        return name + " takes " + incoming + " and survives.";
    }

    public static String lifeChange(String who, int before, int damage) {
        return who + " " + before + " -> " + (before - damage);
    }

    /** Oracle-style line for a scripted card. Never blank. */
    public static String fullTextFor(String cardName) {
        if ("Shock".equals(cardName)) {
            return "Shock deals 2 damage to any target.";
        }
        if ("Cavern of Souls".equals(cardName)) {
            return "As Cavern of Souls enters the battlefield, choose a creature type. "
                    + "{T}: Add {C}. {T}: Add one mana of any color. Spend this mana only to cast a creature spell "
                    + "of the chosen type, and that spell can't be countered.";
        }
        if (cardName == null || cardName.isBlank()) {
            return "No rules text.";
        }
        return cardName;
    }

    /** A cast spell is a log entry. It does not take a card-sized pose on the board. */
    public static StackEntry castSpell(String cardName, boolean needsTarget) {
        return new StackEntry(cardName, fullTextFor(cardName), needsTarget, null);
    }

    public static StackEntry chooseTarget(StackEntry entry, String target) {
        String name = entry == null ? "" : entry.name;
        String text = entry == null ? "" : entry.fullText;
        boolean needs = entry != null && entry.needsTarget;
        return new StackEntry(name, text, needs, target);
    }

    /**
     * One click assigns the target. There is no confirm flag to lock it, and the click
     * does not resolve the spell.
     */
    public static TargetAssignment clickTarget(StackEntry entry, String target) {
        return new TargetAssignment(chooseTarget(entry, target), false);
    }

    /** Outcome line once a target is chosen, for example "Shock deals 2 to Grizzly". */
    public static String stackOutcome(String spell, String target) {
        String name = spell == null || spell.isBlank() ? "Spell" : spell;
        String who = target == null || target.isBlank() ? "a target" : target;
        return name + " deals " + SHOCK_DAMAGE + " to " + who;
    }

    /** A permanent or sorcery joins only an empty stack. An instant may always join. */
    public static boolean mayCast(Speed speed, int stackSize) {
        int n = Math.max(0, stackSize);
        return speed == Speed.INSTANT || n == 0;
    }

    /** Push a spell. A refused cast leaves the stack unchanged. The new spell is the top. */
    public static List<StackEntry> castOnto(List<StackEntry> stack, Speed speed,
                                             String cardName, boolean needsTarget) {
        List<StackEntry> base = stack == null ? List.of() : stack;
        if (!mayCast(speed, base.size())) return List.copyOf(base);
        ArrayList<StackEntry> next = new ArrayList<>(base);
        next.add(castSpell(cardName, needsTarget));
        return List.copyOf(next);
    }

    /**
     * One click writes the target onto the top spell and does not resolve it.
     * A later click on another legal target replaces that choice.
     */
    public static List<StackEntry> assignTopTarget(List<StackEntry> stack, String target) {
        if (stack == null || stack.isEmpty()) return List.of();
        StackEntry top = stack.get(stack.size() - 1);
        if (!top.needsTarget) return List.copyOf(stack);
        ArrayList<StackEntry> next = new ArrayList<>(stack.subList(0, stack.size() - 1));
        next.add(clickTarget(top, target).entry);
        return List.copyOf(next);
    }

    /** Resolve only the top object. The spell under it stays on the stack. */
    public static List<StackEntry> resolveTop(List<StackEntry> stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        return List.copyOf(stack.subList(0, stack.size() - 1));
    }

    /** Stack art is the thumbnail. A full card must not sit over the table. */
    public static boolean stackArtCoversBoard(float windowW, float windowH) {
        return occupiesFullCardOverBoard(stackThumbnail(windowW, windowH), windowW, windowH);
    }

    public static boolean waitingForTarget(StackEntry entry) {
        return entry != null && entry.needsTarget && (entry.target == null || entry.target.isBlank());
    }

    public static float stackColumnCenterX(float windowW) {
        return STACK_COLUMN_X_FRAC * windowW;
    }

    public static float stackColumnWidth(float windowW) {
        return STACK_COLUMN_W_FRAC * windowW;
    }

    public static float stackRowHeight(float windowH, boolean examined) {
        return (examined ? STACK_ROW_EXAMINED_FRAC : STACK_ROW_FRAC) * windowH;
    }

    /** Small art beside the log text. Not a full card over the table. */
    public static HandLayout.Pose stackThumbnail(float windowW, float windowH) {
        float ch = cardHeight(windowH) * STACK_THUMB_FRAC;
        float cw = ch * HandLayout.CARD_ASPECT;
        return new HandLayout.Pose(stackColumnCenterX(windowW), STACK_TOP_FRAC * windowH, cw, ch, 0f, 1f);
    }

    /** True when a pose is a full battlefield card sitting over the centred table. */
    public static boolean occupiesFullCardOverBoard(HandLayout.Pose pose, float windowW, float windowH) {
        if (pose == null) {
            return false;
        }
        boolean fullSize = pose.h >= cardHeight(windowH) * 0.90f;
        boolean overTable = pose.cx > windowW * 0.25f && pose.cx < windowW * 0.75f
                && pose.cy > windowH * 0.18f && pose.cy < windowH * 0.90f;
        return fullSize && overTable;
    }

    /** One click's target. {@code confirmRequired} stays false: the click does not lock a confirm step. */
    public static final class TargetAssignment {
        public final StackEntry entry;
        public final boolean confirmRequired;

        public TargetAssignment(StackEntry entry, boolean confirmRequired) {
            this.entry = entry;
            this.confirmRequired = confirmRequired;
        }
    }

    /** One line in the stack log. {@code target} is null until the player chooses. */
    public static final class StackEntry {
        public final String name;
        public final String fullText;
        public final boolean needsTarget;
        public final String target;

        public StackEntry(String name, String fullText, boolean needsTarget, String target) {
            this.name = name == null ? "" : name;
            this.fullText = fullText == null ? "" : fullText;
            this.needsTarget = needsTarget;
            this.target = target;
        }
    }

    public static final class CombatPreview {
        public final boolean attackerDies;
        public final boolean blockerDies;
        public final int lifeLoss;
        public final String summary;

        public CombatPreview(boolean attackerDies, boolean blockerDies, int lifeLoss, String summary) {
            this.attackerDies = attackerDies;
            this.blockerDies = blockerDies;
            this.lifeLoss = lifeLoss;
            this.summary = summary;
        }
    }

    public static CombatPreview previewBlock(int atkPower, int atkToughness, int blkPower, int blkToughness) {
        boolean attackerDies = blkPower >= atkToughness;
        boolean blockerDies = atkPower >= blkToughness;
        String summary = (attackerDies ? "Attacker dies. " : "Attacker lives. ")
                + (blockerDies ? "Blocker dies." : "Blocker lives.");
        return new CombatPreview(attackerDies, blockerDies, 0, summary);
    }

    public static CombatPreview previewUnblocked(String defender, int power) {
        int loss = Math.max(0, power);
        return new CombatPreview(false, false, loss, defender + " loses " + loss + " life.");
    }

    private static boolean within(float x0, float y0, float x1, float y1, float radius) {
        return dist(x0, y0, x1, y1) <= radius;
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
