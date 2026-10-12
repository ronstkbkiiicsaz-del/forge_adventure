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
    /** HP1: on hover the whole hand grows to this multiple of its resting size. */
    public static final float HOVER_HAND_SCALE = 1.35f;
    /** HP1: hovered hand's card tops sit this high (fraction of H); the hovered card still lifts higher. */
    public static final float HOVER_HAND_TOP_FRAC = 0.20f;
    /** HP1: hovered hand fan is flatter than the resting fan so spread cards stay clear of each other. */
    public static final float HOVER_ROTATION_MAX_DEG = 4f;
    /** HP1: clear gap between spread cards on hover, as a fraction of a full card width. */
    public static final float HOVER_GAP_FRAC = 0.05f;
    /** HP1: the spread hand may use at most this fraction of the window width (scale shrinks to fit). */
    public static final float HOVER_MAX_WIDTH_FRAC = 0.92f;
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

    /**
     * HP1 hover scale: the whole hand grows to {@link #HOVER_HAND_SCALE} times its resting size,
     * shrunk only if the spread hand would not fit in {@link #HOVER_MAX_WIDTH_FRAC} of the window.
     * Never below 1.
     */
    public static float hoverHandScale(float windowW, float h, int n) {
        if (n <= 0) {
            return HOVER_HAND_SCALE;
        }
        float restW = restWidth(h);
        float gap = HOVER_GAP_FRAC * fullWidth(h);
        float room = HOVER_MAX_WIDTH_FRAC * windowW - fullWidth(h) - gap * (n - 1);
        float fit = n <= 1 ? HOVER_HAND_SCALE : room / ((n - 1) * restW);
        return Math.max(1f, Math.min(HOVER_HAND_SCALE, fit));
    }

    private static float hoverCardWidth(float windowW, float h, int n, int i, int hoverIndex) {
        return i == hoverIndex ? fullWidth(h) : restWidth(h) * hoverHandScale(windowW, h, n);
    }

    /** Total width of the spread hand while card {@code hoverIndex} is hovered. */
    public static float hoverHandWidth(float windowW, float h, int n, int hoverIndex) {
        float total = 0f;
        for (int i = 0; i < n; i++) {
            total += hoverCardWidth(windowW, h, n, i, hoverIndex);
        }
        return total + HOVER_GAP_FRAC * fullWidth(h) * Math.max(0, n - 1);
    }

    /**
     * HP1 hover pose: the whole hand scales up (see {@link #hoverHandScale}) and spreads into
     * side-by-side slots with a small gap, so no card covers another. The hovered card is full
     * size, upright and lifted (bottom at {@link #HOVER_BOTTOM_FRAC} * H); the others keep a
     * flatter version of the same bottoms-pinched fan.
     */
    public static Pose hoverPose(float windowW, float h, int n, int i, int hoverIndex) {
        if (hoverIndex < 0 || hoverIndex >= n) {
            return restPose(windowW, h, n, i);
        }
        float gap = HOVER_GAP_FRAC * fullWidth(h);
        float x = (windowW - hoverHandWidth(windowW, h, n, hoverIndex)) / 2f;
        for (int k = 0; k < i; k++) {
            x += hoverCardWidth(windowW, h, n, k, hoverIndex) + gap;
        }
        float cw = hoverCardWidth(windowW, h, n, i, hoverIndex);
        float cx = x + cw / 2f;
        if (i == hoverIndex) {
            float fh = fullHeight(h);
            return new Pose(cx, HOVER_BOTTOM_FRAC * h + fh / 2f, cw, fh, 0f, 1f);
        }
        float scale = hoverHandScale(windowW, h, n);
        float ch = restHeight(h) * scale;
        float theta = fanAngleDeg(n, i, HOVER_ROTATION_MAX_DEG);
        // Outer cards dip slightly, matching the resting arc.
        float dip = (float) (1.0 - Math.cos(Math.toRadians(theta))) * ch;
        float cy = HOVER_HAND_TOP_FRAC * h - ch / 2f - dip;
        return new Pose(cx, cy, cw, ch, -theta, REST_SCALE * scale);
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
