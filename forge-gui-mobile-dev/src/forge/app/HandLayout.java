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
    public static final float ARC_RADIUS_MULT = 2.5f;
    public static final float HOVER_BOTTOM_FRAC = 0.02f;
    public static final float NEIGHBOR_SPREAD_FRAC = 0.35f;
    public static final float LEAVE_HAND_ABOVE_FRAC = 0.05f;
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

    /** Resting pose for card {@code i} of {@code n}. Origin is card centre. Y is from bottom. */
    public static Pose restPose(float windowW, float h, int n, int i) {
        float restW = restWidth(h);
        float restH = restHeight(h);
        float step = restStep(windowW, h, n);
        float handW = handWidth(windowW, h, n);
        float left = (windowW - handW) / 2f;
        float cx = left + restW / 2f + i * step;
        float t = n <= 1 ? 0.5f : i / (float) (n - 1);
        float rot = -ROTATION_MAX_DEG + 2f * ROTATION_MAX_DEG * t;
        float handCx = windowW / 2f;
        float relX = cx - handCx;
        float radius = Math.max(handW * ARC_RADIUS_MULT, 1f);
        // Outer cards sit lower on the arc; centre card top stays at REST_TOP_FRAC * H.
        float drop = radius - (float) Math.sqrt(Math.max(0.0, (double) radius * radius - (double) relX * relX));
        float topY = REST_TOP_FRAC * h - drop;
        float cy = topY - restH / 2f;
        return new Pose(cx, cy, restW, restH, rot, REST_SCALE);
    }

    /**
     * Sideways shift for neighbours of the hovered card. Immediate neighbours
     * move by {@link #NEIGHBOR_SPREAD_FRAC} of a full card width; further cards
     * fall off as 1/|distance|.
     */
    public static float neighborShiftX(float h, int cardIndex, int hoverIndex) {
        if (hoverIndex < 0 || cardIndex == hoverIndex) {
            return 0f;
        }
        int dist = cardIndex - hoverIndex;
        float mag = NEIGHBOR_SPREAD_FRAC * fullWidth(h) / Math.abs(dist);
        return Math.signum(dist) * mag;
    }

    public static Pose hoverPose(float windowW, float h, int n, int i, int hoverIndex) {
        Pose rest = restPose(windowW, h, n, i);
        float shift = neighborShiftX(h, i, hoverIndex);
        if (i == hoverIndex) {
            float fh = fullHeight(h);
            float fw = fullWidth(h);
            float cy = HOVER_BOTTOM_FRAC * h + fh / 2f;
            return new Pose(rest.cx + shift, cy, fw, fh, 0f, 1f);
        }
        return new Pose(rest.cx + shift, rest.cy, rest.w, rest.h, rest.rotationDeg, rest.scale);
    }

    /** Nearest card by centre column; used so hover does not flicker between cards. */
    public static int nearestCard(float windowW, float h, int n, float pointerX) {
        if (n <= 0) {
            return -1;
        }
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Pose p = restPose(windowW, h, n, i);
            float d = Math.abs(p.cx - pointerX);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * True while the pointer is still considered "over the hand": inside the hand's
     * horizontal span and not more than {@link #LEAVE_HAND_ABOVE_FRAC} * H above the
     * hovered card's top.
     */
    public static boolean pointerInHandArea(float windowW, float h, int n, int hoverIndex,
                                           float pointerX, float pointerY) {
        if (n <= 0 || hoverIndex < 0 || hoverIndex >= n) {
            return false;
        }
        float handW = handWidth(windowW, h, n);
        float left = (windowW - handW) / 2f;
        float pad = fullWidth(h) * 0.5f;
        if (pointerX < left - pad || pointerX > left + handW + pad) {
            return false;
        }
        Pose hover = hoverPose(windowW, h, n, hoverIndex, hoverIndex);
        float top = hover.cy + hover.h / 2f;
        return pointerY <= top + LEAVE_HAND_ABOVE_FRAC * h;
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
