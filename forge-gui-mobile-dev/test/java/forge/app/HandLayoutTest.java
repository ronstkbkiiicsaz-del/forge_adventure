package forge.app;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless layout/math checks for the hand prototype. Does not touch the real user dir.
 */
public class HandLayoutTest {

    @Test
    public void fullCardSizeUsesHeightAndAspect() {
        float h = 1080f;
        Assert.assertEquals(HandLayout.fullHeight(h), 0.28f * h, 0.001f);
        Assert.assertEquals(HandLayout.fullWidth(h), HandLayout.fullHeight(h) * (63f / 88f), 0.001f);
        Assert.assertEquals(HandLayout.restHeight(h), HandLayout.fullHeight(h) * 0.6f, 0.001f);
    }

    @Test
    public void restingHandIsTuckedAndCentred() {
        float w = 1920f, h = 1080f;
        int n = 7;
        HandLayout.Pose mid = HandLayout.restPose(w, h, n, n / 2);
        float top = mid.cy + mid.h / 2f;
        Assert.assertEquals(top, HandLayout.REST_TOP_FRAC * h, 1.5f);
        Assert.assertTrue(mid.cy < HandLayout.restHeight(h) / 2f);
        Assert.assertTrue(HandLayout.handWidth(w, h, n) <= HandLayout.MAX_HAND_WIDTH_FRAC * w + 0.5f);

        HandLayout.Pose left = HandLayout.restPose(w, h, n, 0);
        HandLayout.Pose right = HandLayout.restPose(w, h, n, n - 1);
        // HP1: left cards lean left (CCW, positive in libGDX), right cards lean right.
        Assert.assertEquals(left.rotationDeg, HandLayout.ROTATION_MAX_DEG, 0.01f);
        Assert.assertEquals(right.rotationDeg, -HandLayout.ROTATION_MAX_DEG, 0.01f);
        Assert.assertTrue(left.cy < mid.cy);
        Assert.assertTrue(right.cy < mid.cy);
    }

    /** Top-centre x of a rotated card (libGDX rotation is CCW degrees). */
    private static float topX(HandLayout.Pose p) {
        return p.cx - (p.h / 2f) * (float) Math.sin(Math.toRadians(p.rotationDeg));
    }

    private static float bottomX(HandLayout.Pose p) {
        return p.cx + (p.h / 2f) * (float) Math.sin(Math.toRadians(p.rotationDeg));
    }

    @Test
    public void restingFanPinchesBottomsAndSpreadsTops() {
        float w = 1920f, h = 1080f;
        for (int n : new int[]{3, 5, 7}) {
            for (int i = 0; i + 1 < n; i++) {
                HandLayout.Pose a = HandLayout.restPose(w, h, n, i);
                HandLayout.Pose b = HandLayout.restPose(w, h, n, i + 1);
                float bottomGap = bottomX(b) - bottomX(a);
                float topGap = topX(b) - topX(a);
                Assert.assertTrue(bottomGap > 0f, "cards stay in order at the bottom");
                Assert.assertTrue(bottomGap < topGap,
                        "n=" + n + " i=" + i + ": bottoms " + bottomGap + " must be closer than tops " + topGap);
            }
            HandLayout.Pose first = HandLayout.restPose(w, h, n, 0);
            HandLayout.Pose last = HandLayout.restPose(w, h, n, n - 1);
            Assert.assertTrue(bottomX(last) - bottomX(first) < topX(last) - topX(first),
                    "n=" + n + ": the whole fan is narrower at the bottom than at the top");
        }
    }

    @Test
    public void overlapIncreasesInsteadOfExceedingMaxWidth() {
        float w = 800f, h = 1080f;
        float step7 = HandLayout.restStep(w, h, 7);
        float ideal = HandLayout.VISIBLE_STRIP_FRAC * HandLayout.restWidth(h);
        Assert.assertTrue(step7 < ideal);
        Assert.assertTrue(HandLayout.handWidth(w, h, 7) <= HandLayout.MAX_HAND_WIDTH_FRAC * w + 0.5f);
    }

    @Test
    public void hoverRaisesAndStraightensHoveredCard() {
        float w = 1920f, h = 1080f;
        int n = 7, hover = 3;
        HandLayout.Pose raised = HandLayout.hoverPose(w, h, n, hover, hover);
        Assert.assertEquals(raised.rotationDeg, 0f, 0.001f);
        Assert.assertEquals(raised.scale, 1f, 0.001f);
        Assert.assertEquals(raised.h, HandLayout.fullHeight(h), 0.001f);
        Assert.assertEquals(raised.cy - raised.h / 2f, HandLayout.HOVER_BOTTOM_FRAC * h, 0.5f);
    }

    /** Horizontal extent of a rotated card (axis-aligned bounds, conservative). */
    private static float[] span(HandLayout.Pose p) {
        double r = Math.toRadians(p.rotationDeg);
        float half = (float) (p.w * Math.abs(Math.cos(r)) + p.h * Math.abs(Math.sin(r))) / 2f;
        return new float[]{p.cx - half, p.cx + half};
    }

    /** Width of card {@code i}'s span that no other card's span covers. */
    private static float uncoveredWidth(HandLayout.Pose[] poses, int i) {
        float[] own = span(poses[i]);
        List<float[]> cover = new ArrayList<>();
        for (int k = 0; k < poses.length; k++) {
            if (k == i) {
                continue;
            }
            float[] o = span(poses[k]);
            float a = Math.max(own[0], o[0]);
            float b = Math.min(own[1], o[1]);
            if (b > a) {
                cover.add(new float[]{a, b});
            }
        }
        cover.sort((x, y) -> Float.compare(x[0], y[0]));
        float covered = 0f;
        float end = own[0];
        for (float[] c : cover) {
            float a = Math.max(c[0], end);
            if (c[1] > a) {
                covered += c[1] - a;
                end = c[1];
            }
        }
        return (own[1] - own[0]) - covered;
    }

    @Test
    public void hoverScalesWholeHandAndSpreadsSoNoCardIsCovered() {
        for (float[] size : new float[][]{{1920f, 1080f}, {1280f, 720f}}) {
            float w = size[0], h = size[1];
            int n = 7;
            Assert.assertTrue(HandLayout.hoverHandScale(w, h, n) > 1f, "hovered hand scales up");
            for (int hover = 0; hover < n; hover++) {
                HandLayout.Pose[] poses = new HandLayout.Pose[n];
                for (int i = 0; i < n; i++) {
                    poses[i] = HandLayout.hoverPose(w, h, n, i, hover);
                    if (i != hover) {
                        HandLayout.Pose rest = HandLayout.restPose(w, h, n, i);
                        Assert.assertTrue(poses[i].w > rest.w && poses[i].scale > rest.scale,
                                "non-hovered card " + i + " grows with the hand");
                    }
                }
                for (int i = 0; i < n; i++) {
                    float visible = uncoveredWidth(poses, i);
                    float minVisible = (i == hover ? 0.95f : 0.85f) * poses[i].w;
                    Assert.assertTrue(visible >= minVisible, w + "x" + h + " hover=" + hover + " card " + i
                            + ": uncovered width " + visible + " < " + minVisible);
                }
                Assert.assertTrue(HandLayout.hoverHandWidth(w, h, n, hover)
                        <= HandLayout.HOVER_MAX_WIDTH_FRAC * w + 0.5f, "spread hand fits the window");
            }
        }
    }

    @Test
    public void hoverDoesNotFlickerAtCardEdges() {
        float w = 1920f, h = 1080f;
        int n = 7;
        HandLayout.Pose raised = HandLayout.hoverPose(w, h, n, 3, 3);
        float y = raised.cy;
        // Just inside the hovered card's right edge: stays on 3.
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, 3, raised.cx + raised.w / 2f - 1f, y), 3);
        // Just past it: moves to 4, and once 4 is hovered the same point keeps 4.
        float past = raised.cx + raised.w / 2f + 1f;
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, 3, past, y), 4);
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, 4, past, y), 4);
        // Leaving above the opened card (past the open-hit pad) closes the hand.
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, 3, raised.cx,
                raised.cy + raised.h / 2f + HandLayout.OPEN_HIT_PAD_FRAC * h + 1f), -1);
    }

    @Test
    public void nearestCardUsesCentreColumn() {
        float w = 1920f, h = 1080f;
        int n = 7;
        HandLayout.Pose a = HandLayout.restPose(w, h, n, 2);
        HandLayout.Pose b = HandLayout.restPose(w, h, n, 3);
        float mid = (a.cx + b.cx) / 2f;
        Assert.assertEquals(HandLayout.nearestCard(w, h, n, mid - 1f), 2);
        Assert.assertEquals(HandLayout.nearestCard(w, h, n, mid + 1f), 3);
    }

    @Test
    public void leaveHandAreaAboveHoveredTop() {
        float w = 1920f, h = 1080f;
        int n = 7, hover = 3;
        HandLayout.Pose raised = HandLayout.hoverPose(w, h, n, hover, hover);
        float top = raised.cy + raised.h / 2f;
        Assert.assertTrue(HandLayout.pointerInHandArea(w, h, n, hover, raised.cx, top));
        Assert.assertFalse(HandLayout.pointerInHandArea(w, h, n, hover, raised.cx,
                top + HandLayout.OPEN_HIT_PAD_FRAC * h + 1f));
        Assert.assertFalse(HandLayout.pointerInHandArea(w, h, n, hover, -100f, raised.cy));
    }

    /** Local (lx, ly) on a rotated pose → world (x, y). libGDX CCW about centre. */
    private static float[] worldOnPose(HandLayout.Pose p, float lx, float ly) {
        double rad = Math.toRadians(p.rotationDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new float[]{p.cx + lx * cos - ly * sin, p.cy + lx * sin + ly * cos};
    }

    /** Max world-Y of any resting card's padded corners (above this is empty space). */
    private static float restingPaddedMaxY(float w, float h, int n, float pad) {
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            HandLayout.Pose p = HandLayout.restPose(w, h, n, i);
            float hw = p.w / 2f + pad, hh = p.h / 2f + pad;
            for (float lx : new float[]{-hw, hw}) {
                for (float ly : new float[]{-hh, hh}) {
                    maxY = Math.max(maxY, worldOnPose(p, lx, ly)[1]);
                }
            }
        }
        return maxY;
    }

    /** Min world-X of any resting card's padded corners (left of this is empty space). */
    private static float restingPaddedMinX(float w, float h, int n, float pad) {
        float minX = Float.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            HandLayout.Pose p = HandLayout.restPose(w, h, n, i);
            float hw = p.w / 2f + pad, hh = p.h / 2f + pad;
            for (float lx : new float[]{-hw, hw}) {
                for (float ly : new float[]{-hh, hh}) {
                    minX = Math.min(minX, worldOnPose(p, lx, ly)[0]);
                }
            }
        }
        return minX;
    }

    @Test
    public void emptySpaceAboveOrBesideFanDoesNotOpenHand() {
        float w = 1920f, h = 1080f;
        int n = 7;
        float pad = HandLayout.OPEN_HIT_PAD_FRAC * h;
        // Just above the fan, outside every resting card's padded quad.
        float aboveY = restingPaddedMaxY(w, h, n, pad) + 2f;
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, -1, w / 2f, aboveY), -1);
        // Beside the fan, outside every resting card's padded quad.
        float besideX = restingPaddedMinX(w, h, n, pad) - 2f;
        HandLayout.Pose left = HandLayout.restPose(w, h, n, 0);
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, -1, besideX, left.cy + left.h / 4f), -1);
        // Mid-table point the old eager bounding box used to treat as "in hand".
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, -1, w / 2f, 0.35f * h), -1);
    }

    @Test
    public void visibleCardEdgeWithinPadOpensHand() {
        float w = 1920f, h = 1080f;
        int n = 7;
        float pad = HandLayout.OPEN_HIT_PAD_FRAC * h;
        // Top edge of the rightmost card is uncovered (nothing drawn after it).
        HandLayout.Pose right = HandLayout.restPose(w, h, n, n - 1);
        float[] onEdge = worldOnPose(right, 0f, right.h / 2f);
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, -1, onEdge[0], onEdge[1]), n - 1);
        // Just outside that top edge but still inside the pad.
        float[] inPad = worldOnPose(right, 0f, right.h / 2f + pad * 0.5f);
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, -1, inPad[0], inPad[1]), n - 1);
        // Leftmost card's outer (left) edge is uncovered — opens on that card.
        HandLayout.Pose left = HandLayout.restPose(w, h, n, 0);
        float[] leftEdge = worldOnPose(left, -left.w / 2f, left.h / 4f);
        Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, -1, leftEdge[0], leftEdge[1]), 0);
    }

    /**
     * Find a point on card {@code under} that also lies on {@code over} and on no higher-index card.
     */
    private static float[] exclusiveOverlap(float w, float h, int n, int underIdx, int overIdx) {
        HandLayout.Pose under = HandLayout.restPose(w, h, n, underIdx);
        HandLayout.Pose over = HandLayout.restPose(w, h, n, overIdx);
        for (int sx = -20; sx <= 20; sx++) {
            for (int sy = -20; sy <= 20; sy++) {
                float[] pt = worldOnPose(under, under.w * sx / 40f, under.h * sy / 40f);
                if (!HandLayout.pointHitsPose(under, pt[0], pt[1], 0f)) {
                    continue;
                }
                if (!HandLayout.pointHitsPose(over, pt[0], pt[1], 0f)) {
                    continue;
                }
                boolean higher = false;
                for (int k = overIdx + 1; k < n; k++) {
                    if (HandLayout.pointHitsPose(HandLayout.restPose(w, h, n, k), pt[0], pt[1], 0f)) {
                        higher = true;
                        break;
                    }
                }
                if (!higher) {
                    return pt;
                }
            }
        }
        return null;
    }

    @Test
    public void coveredPartOfCardHitsCardOnTop() {
        float w = 1920f, h = 1080f;
        int n = 7;
        // Cards are drawn left→right; higher index sits on top. A point in the overlap of
        // card i and i+1 (and not under a still-higher card) must hit i+1.
        for (int i = 0; i + 1 < n; i++) {
            float[] covered = exclusiveOverlap(w, h, n, i, i + 1);
            Assert.assertNotNull(covered, "no exclusive overlap for i=" + i);
            Assert.assertEquals(HandLayout.restingVisibleHit(w, h, n, covered[0], covered[1], 0f), i + 1);
            Assert.assertEquals(HandLayout.hoverIndexFor(w, h, n, -1, covered[0], covered[1]), i + 1);
        }
    }

    @Test
    public void dragAndTapThresholds() {
        Assert.assertTrue(HandLayout.isTap(0f, 0.1f));
        Assert.assertTrue(HandLayout.isTap(11.9f, 0.24f));
        Assert.assertFalse(HandLayout.isTap(12f, 0.1f));
        Assert.assertFalse(HandLayout.isTap(0f, 0.25f));
        Assert.assertTrue(HandLayout.isDrag(12f));
        Assert.assertFalse(HandLayout.isDrag(11.9f));
    }

    @Test
    public void easeTowardApproachesTarget() {
        float v = HandLayout.easeToward(0f, 100f, 0.12f);
        Assert.assertTrue(v > 50f && v < 100f);
        float done = 0f;
        for (int i = 0; i < 40; i++) {
            done = HandLayout.easeToward(done, 100f, 1f / 60f);
        }
        Assert.assertEquals(done, 100f, 0.5f);
    }

    @Test
    public void castLineAndZoomFractions() {
        float h = 1080f;
        Assert.assertEquals(HandLayout.castLineY(h), 0.45f * h, 0.001f);
        Assert.assertEquals(HandLayout.ZOOM_HEIGHT_FRAC * h, 0.75f * h, 0.001f);
    }
}
