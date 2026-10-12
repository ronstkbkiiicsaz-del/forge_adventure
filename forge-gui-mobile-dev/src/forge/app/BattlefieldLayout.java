package forge.app;

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

    public enum Side { YOU, OPPONENT }
    public enum Row { LAND, CREATURE }
    public enum Kind { LAND, CREATURE, SPELL }

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
