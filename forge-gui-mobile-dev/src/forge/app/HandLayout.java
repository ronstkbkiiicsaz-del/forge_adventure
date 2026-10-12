package forge.app;

/**
 * Pure layout/math for {@link HandPrototype}. No libGDX, no Forge game state.
 * All sizes are fractions of window height H unless noted.
 */
public final class HandLayout {
    public static final int CARD_COUNT = 7;
    public static final float CARD_ASPECT = 63f / 88f;
    public static final float FULL_HEIGHT_FRAC = 0.28f;
    public static final float REST_SCALE = 0.60f;
    public static final float REST_TOP_FRAC = 0.14f;
    public static final float VISIBLE_STRIP_FRAC = 0.45f;
    public static final float MAX_HAND_WIDTH_FRAC = 0.55f;
    public static final float ROTATION_MAX_DEG = 12f;
    public static final float HOVER_BOTTOM_FRAC = 0.02f;
    /** Opened-hand cards, other than the one under the cursor. Tall enough to mostly read the title. */
    public static final float HOVER_OPEN_HEIGHT_FRAC = 0.42f;
    /** Card under the cursor. A little taller than the opened hand, still short of the 0.75H zoom. */
    public static final float HOVER_RAISED_HEIGHT_FRAC = 0.48f;
    /** Tops of the opened hand, just above the middle of the window. */
    public static final float HOVER_HAND_TOP_FRAC = 0.55f;
    /** Top fraction of a card treated as the title band. */
    public static final float TITLE_BAND_FRAC = 0.16f;
    /**
     * Fraction of each card's width that should stay visible across the title band.
     * The row overlaps below that band when seven cards would not fit side by side.
     */
    public static final float TITLE_VISIBLE_FRAC = 0.55f;
    /** Opened hand keeps a fan so the lower part can overlap while the titles stay apart. */
    public static final float HOVER_ROTATION_MAX_DEG = 12f;
    /** Gap used when the opened cards fit side by side, as a fraction of an opened card width. */
    public static final float HOVER_GAP_FRAC = 0.04f;
    public static final float LEAVE_HAND_ABOVE_FRAC = 0.05f;
    /**
     * Extra hit margin around each card when deciding whether to open or keep the hand open,
     * as a fraction of window height. ~0.02H so empty space above/beside the fan does not open it.
     */
    public static final float OPEN_HIT_PAD_FRAC = 0.02f;
    public static final float EASE_RATE = 14f;
    public static final float CAST_LINE_FRAC = 0.45f;
    public static final float DRAG_TILT_MAX_DEG = 8f;
    public static final float CAST_FLY_SEC = 0.15f;
    public static final float CAST_FADE_SEC = 0.25f;
    public static final float CAST_END_SCALE = 0.40f;
    public static final float CANCEL_SNAP_SEC = 0.20f;
    public static final float DRAG_THRESHOLD_PX = 12f;
    public static final float TAP_MAX_SEC = 0.25f;
    public static final float ZOOM_HEIGHT_FRAC = 0.75f;

    private HandLayout() {
    }

    public static float fullHeight(float h) {
        return FULL_HEIGHT_FRAC * h;
    }

    public static float fullWidth(float h) {
        return fullHeight(h) * CARD_ASPECT;
    }

    public static float restHeight(float h) {
        return fullHeight(h) * REST_SCALE;
    }

    public static float restWidth(float h) {
        return fullWidth(h) * REST_SCALE;
    }

    public static float castLineY(float h) {
        return CAST_LINE_FRAC * h;
    }

    /** Exponential approach used for all motion. */
    public static float easeToward(float value, float target, float deltaSec) {
        if (deltaSec <= 0f) {
            return value;
        }
        float t = 1f - (float) Math.exp(-deltaSec * EASE_RATE);
        return value + (target - value) * t;
    }

    public static boolean isTap(float movePx, float holdSec) {
        return movePx < DRAG_THRESHOLD_PX && holdSec < TAP_MAX_SEC;
    }

    public static boolean isDrag(float movePx) {
        return movePx >= DRAG_THRESHOLD_PX;
    }

    /**
     * Horizontal step between resting card centres. Increases overlap when the
     * ideal fan would exceed {@link #MAX_HAND_WIDTH_FRAC} of window width.
     */
    public static float restStep(float windowW, float h, int n) {
        if (n <= 1) {
            return 0f;
        }
        float restW = restWidth(h);
        float ideal = VISIBLE_STRIP_FRAC * restW;
        float maxHand = MAX_HAND_WIDTH_FRAC * windowW;
        float idealHand = (n - 1) * ideal + restW;
        if (idealHand <= maxHand) {
            return ideal;
        }
        return Math.max(1f, (maxHand - restW) / (n - 1));
    }

    public static float handWidth(float windowW, float h, int n) {
        if (n <= 0) {
            return 0f;
        }
        return (n - 1) * restStep(windowW, h, n) + restWidth(h);
    }

    /** Fan angle (degrees from vertical, negative = leaning left) for card {@code i} of {@code n}. */
    static float fanAngleDeg(int n, int i, float maxDeg) {
        float t = n <= 1 ? 0.5f : i / (float) (n - 1);
        return -maxDeg + 2f * maxDeg * t;
    }

    /**
     * Resting pose for card {@code i} of {@code n}. Origin is card centre. Y is from bottom.
     * HP1: held like a real hand. Every card rotates about one pivot below the hand, so the
     * card bottoms are pinched together and the tops fan outward along an arc (left cards lean
     * left, right cards lean right). The arc radius is chosen so the centre-to-centre spacing
     * matches {@link #restStep}, keeping the 45% visible strip and 55% width cap.
     */
    public static Pose restPose(float windowW, float h, int n, int i) {
        float restW = restWidth(h);
        float restH = restHeight(h);
        float step = restStep(windowW, h, n);
        float handCx = windowW / 2f;
        float theta = fanAngleDeg(n, i, ROTATION_MAX_DEG);
        float stepRad = n <= 1 ? 0f : (float) Math.toRadians(2f * ROTATION_MAX_DEG / (n - 1));
        float radius = stepRad <= 0f ? 0f : step / stepRad;
        // Centre card's top stays at REST_TOP_FRAC * H; the pivot sits radius below its centre.
        float pivotY = REST_TOP_FRAC * h - restH / 2f - radius;
        double rad = Math.toRadians(theta);
        float cx = handCx + radius * (float) Math.sin(rad);
        float cy = pivotY + radius * (float) Math.cos(rad);
        // libGDX rotation is counter-clockwise; a card leaning left (theta < 0) turns CCW.
        return new Pose(cx, cy, restW, restH, -theta, REST_SCALE);
    }

    public static float hoverOpenHeight(float h) {
        return HOVER_OPEN_HEIGHT_FRAC * h;
    }

    public static float hoverOpenWidth(float h) {
        return hoverOpenHeight(h) * CARD_ASPECT;
    }

    public static float hoverRaisedHeight(float h) {
        return HOVER_RAISED_HEIGHT_FRAC * h;
    }

    public static float hoverRaisedWidth(float h) {
        return hoverRaisedHeight(h) * CARD_ASPECT;
    }

    /**
     * How many times taller the opened hand is than the tucked resting hand.
     * Card size does not shrink to fit a narrow window; the lower part overlaps instead.
     */
    public static float hoverHandScale(float windowW, float h, int n) {
        // Width and count do not shrink the cards. A tight window overlaps the lower part instead.
        if (n < 0 || windowW < 0f) {
            return 1f;
        }
        float restH = restHeight(h);
        if (restH <= 0f) {
            return 1f;
        }
        return hoverOpenHeight(h) / restH;
    }

    /**
     * Centre-to-centre step of the opened hand. Prefers a step that leaves
     * {@link #TITLE_VISIBLE_FRAC} of each card's width clear at the title, and tightens
     * (overlapping the lower part) only so the fan still fits in the window.
     */
    public static float hoverStep(float windowW, float h, int n) {
        if (n <= 1) {
            return 0f;
        }
        float cw = hoverOpenWidth(h);
        float ch = hoverOpenHeight(h);
        // Extra half-width of the raised card eats into the neighbour title, so the step
        // has to give that back or the title band slides under the card in front.
        // A card between two others is covered from both sides, so the step has to leave
        // the title band clear of both neighbours: 2*step - width >= TITLE_VISIBLE_FRAC * width.
        float titleStep = cw * (1f + TITLE_VISIBLE_FRAC) / 2f + (hoverRaisedWidth(h) - cw) / 2f;
        float sideBySide = cw * (1f + HOVER_GAP_FRAC);
        float ideal = Math.min(sideBySide, Math.max(cw * 0.35f, titleStep));
        float overhang = ch * (float) Math.sin(Math.toRadians(HOVER_ROTATION_MAX_DEG));
        float fitStep = (windowW - cw - overhang) / (n - 1);
        if ((n - 1) * ideal + cw + overhang <= windowW) {
            return ideal;
        }
        return Math.max(1f, fitStep);
    }

    /** Axis-aligned width of the opened hand, including the raised card. */
    public static float hoverHandWidth(float windowW, float h, int n, int hoverIndex) {
        if (n <= 0 || hoverIndex < 0 || hoverIndex >= n) {
            return 0f;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Pose p = hoverPose(windowW, h, n, i, hoverIndex);
            double r = Math.toRadians(p.rotationDeg);
            float half = (float) (p.w * Math.abs(Math.cos(r)) + p.h * Math.abs(Math.sin(r))) / 2f;
            min = Math.min(min, p.cx - half);
            max = Math.max(max, p.cx + half);
        }
        return max - min;
    }

    /**
     * Opened-hand pose. Every card grows to {@link #HOVER_OPEN_HEIGHT_FRAC} of the window
     * (the one under the cursor to {@link #HOVER_RAISED_HEIGHT_FRAC}) and the tops sit near
     * {@link #HOVER_HAND_TOP_FRAC}. Neighbours overlap below the title band when the row
     * would not fit. The hovered card is upright with its bottom at {@link #HOVER_BOTTOM_FRAC}.
     */
    public static Pose hoverPose(float windowW, float h, int n, int i, int hoverIndex) {
        if (hoverIndex < 0 || hoverIndex >= n) {
            return restPose(windowW, h, n, i);
        }
        float openW = hoverOpenWidth(h);
        float openH = hoverOpenHeight(h);
        float step = hoverStep(windowW, h, n);
        float theta = fanAngleDeg(n, i, HOVER_ROTATION_MAX_DEG);
        float stepRad = n <= 1 ? 0f : (float) Math.toRadians(2f * HOVER_ROTATION_MAX_DEG / (n - 1));
        float radius = stepRad <= 0f ? 0f : step / stepRad;
        float pivotY = HOVER_HAND_TOP_FRAC * h - openH / 2f - radius;
        double rad = Math.toRadians(theta);
        float cx = windowW / 2f + radius * (float) Math.sin(rad);
        if (i == hoverIndex) {
            float rh = hoverRaisedHeight(h);
            float rw = hoverRaisedWidth(h);
            return new Pose(cx, HOVER_BOTTOM_FRAC * h + rh / 2f, rw, rh, 0f, rh / fullHeight(h));
        }
        float cy = pivotY + radius * (float) Math.cos(rad);
        return new Pose(cx, cy, openW, openH, -theta, openH / fullHeight(h));
    }

    /** Nearest card by centre column in the current layout (resting, or spread around {@code hoverIndex}). */
    public static int nearestCard(float windowW, float h, int n, float pointerX, int hoverIndex) {
        if (n <= 0) {
            return -1;
        }
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Pose p = hoverIndex >= 0 ? hoverPose(windowW, h, n, i, hoverIndex) : restPose(windowW, h, n, i);
            float d = Math.abs(p.cx - pointerX);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** Nearest card by resting centre column. */
    public static int nearestCard(float windowW, float h, int n, float pointerX) {
        return nearestCard(windowW, h, n, pointerX, -1);
    }

    /**
     * True if {@code (px, py)} lies inside the rotated card rectangle, expanded by {@code pad}
     * on every side. libGDX rotation is counter-clockwise degrees about the card centre.
     */
    public static boolean pointHitsPose(Pose pose, float px, float py, float pad) {
        float dx = px - pose.cx;
        float dy = py - pose.cy;
        double rad = Math.toRadians(pose.rotationDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float lx = dx * cos + dy * sin;
        float ly = -dx * sin + dy * cos;
        return Math.abs(lx) <= pose.w / 2f + pad && Math.abs(ly) <= pose.h / 2f + pad;
    }

    /**
     * Resting-hand hit: the topmost card whose rotated quad (minus parts covered by cards drawn
     * later / higher index) contains the pointer. Equivalent to testing full quads from front to
     * back and taking the first hit. {@code pad} expands each quad.
     */
    public static int restingVisibleHit(float windowW, float h, int n, float pointerX, float pointerY,
                                        float pad) {
        if (n <= 0) {
            return -1;
        }
        for (int i = n - 1; i >= 0; i--) {
            if (pointHitsPose(restPose(windowW, h, n, i), pointerX, pointerY, pad)) {
                return i;
            }
        }
        return -1;
    }

    /** True while the pointer is over any card of the opened (spread) hand, plus {@code pad}. */
    public static boolean pointerOverOpenedCards(float windowW, float h, int n, int hoverIndex,
                                                 float pointerX, float pointerY, float pad) {
        if (n <= 0 || hoverIndex < 0 || hoverIndex >= n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (pointHitsPose(hoverPose(windowW, h, n, i, hoverIndex), pointerX, pointerY, pad)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pose the hand eases toward when nothing is being reordered.
     * {@code dragging} means the gripped card has already left the hand, so every slot
     * returns to {@link #restPose}. Callers that still have the pointer inside the hand
     * use {@link #dragLayoutPose} instead. The gripped card is not moved by this; the
     * caller keeps it on the cursor at the raised size.
     */
    public static Pose layoutPose(float windowW, float h, int n, int i, int hoverIndex, boolean dragging) {
        if (dragging || hoverIndex < 0 || hoverIndex >= n) {
            return restPose(windowW, h, n, i);
        }
        return hoverPose(windowW, h, n, i, hoverIndex);
    }

    /**
     * True while the pointer is in the hand Steve sees. {@code opened} uses the opened fan
     * around {@code hoverIndex}; otherwise the resting fan. The zone is that row's bounds
     * plus {@link #OPEN_HIT_PAD_FRAC}, not the whole screen.
     * The hand is left when the pointer is above that top, past the cast line and clear of
     * the row, or outside the row horizontally. The opened row crosses the cast line, so a
     * pointer still on those cards is in the hand.
     */
    public static boolean pointerInHand(float windowW, float h, int n, int hoverIndex, boolean opened,
                                         float pointerX, float pointerY) {
        if (n <= 0) {
            return false;
        }
        boolean useOpen = opened && hoverIndex >= 0 && hoverIndex < n;
        float pad = OPEN_HIT_PAD_FRAC * h;
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            Pose p = useOpen ? hoverPose(windowW, h, n, i, hoverIndex) : restPose(windowW, h, n, i);
            float[] box = axisBounds(p);
            minX = Math.min(minX, box[0]);
            maxX = Math.max(maxX, box[1]);
            minY = Math.min(minY, box[2]);
            maxY = Math.max(maxY, box[3]);
        }
        boolean outsideX = pointerX < minX - pad || pointerX > maxX + pad;
        boolean aboveHand = pointerY > maxY + pad;
        boolean belowHand = pointerY < minY - pad;
        boolean pastCastLine = pointerY > castLineY(h) && aboveHand;
        return !outsideX && !aboveHand && !belowHand && !pastCastLine;
    }

    /** Axis-aligned bounds of a rotated card: minX, maxX, minY, maxY. */
    private static float[] axisBounds(Pose p) {
        double rad = Math.toRadians(p.rotationDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float hw = p.w / 2f;
        float hh = p.h / 2f;
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (float lx : new float[]{-hw, hw}) {
            for (float ly : new float[]{-hh, hh}) {
                float x = p.cx + lx * cos - ly * sin;
                float y = p.cy + lx * sin + ly * cos;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        return new float[]{minX, maxX, minY, maxY};
    }

    /**
     * Opened-fan centre x of slot {@code i}. Shared by every hover index, so reorder
     * thresholds do not move when the gap shifts.
     */
    public static float slotCenterX(float windowW, float h, int n, int i) {
        int reference = (n > 1 && i == 0) ? 1 : 0;
        return hoverPose(windowW, h, n, i, reference).cx;
    }

    /**
     * Where the gripped card would insert, from 0 through {@code n - 1}.
     * Counts other cards whose slot centre is strictly left of the pointer.
     * Sitting on a centre stays before that card, so the midpoint between two
     * centres does not flicker.
     */
    public static int reorderIndex(float windowW, float h, int n, int gripIndex, float pointerX) {
        if (n <= 1) {
            return 0;
        }
        int drop = 0;
        for (int i = 0; i < n; i++) {
            if (i == gripIndex) {
                continue;
            }
            if (pointerX > slotCenterX(windowW, h, n, i)) {
                drop++;
            }
        }
        return drop;
    }

    /**
     * Fan slot for card {@code cardIndex} while {@code gripIndex} is held and the gap
     * is {@code dropIndex}. The gripped card returns -1 so it is not drawn in the fan.
     */
    public static int reorderSlot(int n, int cardIndex, int gripIndex, int dropIndex) {
        if (n <= 0 || cardIndex < 0 || cardIndex >= n || cardIndex == gripIndex) {
            return -1;
        }
        int clampedDrop = Math.max(0, Math.min(n - 1, dropIndex));
        int rank = cardIndex < gripIndex ? cardIndex : cardIndex - 1;
        return rank < clampedDrop ? rank : rank + 1;
    }

    /**
     * Opened-hand pose for a card that is not the one under the cursor. Same fan as
     * {@link #hoverPose} without the raised card.
     */
    private static Pose openFanPose(float windowW, float h, int n, int slot) {
        if (n <= 1) {
            return hoverPose(windowW, h, 1, 0, 0);
        }
        int other = slot == 0 ? 1 : 0;
        return hoverPose(windowW, h, n, slot, other);
    }

    /** Opened pose of a non-gripped card, shifted so {@code dropIndex} is an empty gap. */
    public static Pose reorderPose(float windowW, float h, int n, int cardIndex, int gripIndex, int dropIndex) {
        int slot = reorderSlot(n, cardIndex, gripIndex, dropIndex);
        if (slot < 0) {
            return restPose(windowW, h, n, Math.max(0, Math.min(n - 1, cardIndex)));
        }
        return openFanPose(windowW, h, n, slot);
    }

    /**
     * Pose for every card except the one on the cursor. Inside the hand, the others stay
     * opened and shift to leave a gap. Once the pointer has left, they use the resting fan.
     * {@code opened} is the layout currently on screen (opened while reordering, resting while tucked).
     */
    public static Pose dragLayoutPose(float windowW, float h, int n, int i, int hoverIndex, boolean opened,
                                       int gripIndex, float pointerX, float pointerY) {
        if (i < 0 || i >= n) {
            return restPose(windowW, h, Math.max(n, 1), 0);
        }
        if (!pointerInHand(windowW, h, n, hoverIndex, opened, pointerX, pointerY) || i == gripIndex) {
            return restPose(windowW, h, n, i);
        }
        return reorderPose(windowW, h, n, i, gripIndex, reorderIndex(windowW, h, n, gripIndex, pointerX));
    }

    /** Identity order with {@code from} removed and inserted at {@code to}. */
    public static int[] movedOrder(int n, int from, int to) {
        int[] order = new int[n];
        if (n <= 0) {
            return order;
        }
        if (from < 0 || from >= n) {
            for (int i = 0; i < n; i++) {
                order[i] = i;
            }
            return order;
        }
        int dest = Math.max(0, Math.min(n - 1, to));
        int[] rest = new int[n - 1];
        int r = 0;
        for (int i = 0; i < n; i++) {
            if (i != from) {
                rest[r++] = i;
            }
        }
        r = 0;
        for (int i = 0; i < n; i++) {
            order[i] = i == dest ? from : rest[r++];
        }
        return order;
    }

    /**
     * Card indices after release. Inside the hand, the gripped card inserts at
     * {@link #reorderIndex}. Outside, the order is unchanged (that release is a cast or a cancel).
     */
    public static int[] orderAfterRelease(float windowW, float h, int n, int gripIndex, int hoverIndex,
                                           boolean opened, float pointerX, float pointerY) {
        int[] same = new int[n];
        for (int i = 0; i < n; i++) {
            same[i] = i;
        }
        if (gripIndex < 0 || gripIndex >= n
                || !pointerInHand(windowW, h, n, hoverIndex, opened, pointerX, pointerY)) {
            return same;
        }
        return movedOrder(n, gripIndex, reorderIndex(windowW, h, n, gripIndex, pointerX));
    }

    /**
     * Hover target for a pointer. Opens only when the pointer is over a resting card's visible
     * area (rotated quad minus neighbour coverage), padded by {@link #OPEN_HIT_PAD_FRAC} * H.
     * Once open, stays open while the pointer is over any opened-hand card plus the same pad,
     * and picks the card by centre-column hysteresis so the hand does not flicker. Returns -1
     * when the pointer leaves.
     */
    public static int hoverIndexFor(float windowW, float h, int n, int currentHover, float pointerX, float pointerY) {
        if (n <= 0) {
            return -1;
        }
        float pad = OPEN_HIT_PAD_FRAC * h;
        if (currentHover >= 0 && currentHover < n) {
            if (!pointerOverOpenedCards(windowW, h, n, currentHover, pointerX, pointerY, pad)) {
                return -1;
            }
            Pose cur = hoverPose(windowW, h, n, currentHover, currentHover);
            if (Math.abs(pointerX - cur.cx) <= cur.w / 2f) {
                return currentHover;
            }
            return nearestCard(windowW, h, n, pointerX, currentHover);
        }
        return restingVisibleHit(windowW, h, n, pointerX, pointerY, pad);
    }

    /**
     * True while the pointer is still considered "over the opened hand": over any spread card
     * plus {@link #OPEN_HIT_PAD_FRAC} * H. Kept for callers that already hold a hover index.
     */
    public static boolean pointerInHandArea(float windowW, float h, int n, int hoverIndex,
                                           float pointerX, float pointerY) {
        return pointerOverOpenedCards(windowW, h, n, hoverIndex, pointerX, pointerY, OPEN_HIT_PAD_FRAC * h);
    }

    public static final class Pose {
        public final float cx;
        public final float cy;
        public final float w;
        public final float h;
        public final float rotationDeg;
        public final float scale;

        public Pose(float cx, float cy, float w, float h, float rotationDeg, float scale) {
            this.cx = cx;
            this.cy = cy;
            this.w = w;
            this.h = h;
            this.rotationDeg = rotationDeg;
            this.scale = scale;
        }
    }
}
