package forge.app;

import org.testng.Assert;
import org.testng.annotations.Test;

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
        Assert.assertEquals(raised.h, HandLayout.hoverRaisedHeight(h), 0.001f);
        Assert.assertTrue(raised.h > HandLayout.hoverOpenHeight(h));
        Assert.assertTrue(raised.h < HandLayout.ZOOM_HEIGHT_FRAC * h);
        Assert.assertEquals(raised.cy - raised.h / 2f, HandLayout.HOVER_BOTTOM_FRAC * h, 0.5f);
    }

    @Test
    public void hoverLiftsHandTowardMidScreenAtReadableSize() {
        for (float[] size : new float[][]{{1920f, 1080f}, {1280f, 720f}, {800f, 1080f}}) {
            float w = size[0], h = size[1];
            int n = 7;
            Assert.assertTrue(HandLayout.hoverHandScale(w, h, n) > 2f, "opened hand is much taller than rest");
            for (int hover = 0; hover < n; hover++) {
                for (int i = 0; i < n; i++) {
                    HandLayout.Pose pose = HandLayout.hoverPose(w, h, n, i, hover);
                    if (i == hover) {
                        Assert.assertEquals(pose.h, HandLayout.hoverRaisedHeight(h), 0.01f);
                    } else {
                        Assert.assertEquals(pose.h, HandLayout.hoverOpenHeight(h), 0.01f,
                                "narrow windows overlap instead of shrinking the cards");
                        float top = pose.cy + pose.h / 2f;
                        Assert.assertTrue(top <= HandLayout.HOVER_HAND_TOP_FRAC * h + 1f,
                                "opened card tops stay at or under the mid-screen line");
                        Assert.assertTrue(top > 0.40f * h, "opened cards rise toward the middle");
                    }
                }
                Assert.assertTrue(HandLayout.hoverHandWidth(w, h, n, hover) <= w + 2f,
                        "opened hand stays on screen");
            }
        }
        float w = 1920f, h = 1080f;
        int n = 7;
        int hover = 3;
        HandLayout.Pose[] poses = new HandLayout.Pose[n];
        for (int i = 0; i < n; i++) {
            poses[i] = HandLayout.hoverPose(w, h, n, i, hover);
        }
        for (int i = 0; i < n; i++) {
            float clear = titleClearFraction(poses, i, hover);
            Assert.assertTrue(clear >= 0.50f, "card " + i + " title band clear fraction " + clear);
        }
    }

    /** Fraction of the title band of card {@code i} that no later-drawn card covers. */
    private static float titleClearFraction(HandLayout.Pose[] poses, int i, int hover) {
        HandLayout.Pose p = poses[i];
        int samples = 21;
        int clear = 0;
        float yLocal = p.h / 2f - p.h * HandLayout.TITLE_BAND_FRAC * 0.5f;
        for (int s = 0; s < samples; s++) {
            float lx = -p.w / 2f + p.w * s / (samples - 1);
            float[] pt = worldOnPose(p, lx, yLocal);
            boolean covered = false;
            for (int j = 0; j < poses.length; j++) {
                if (j == i) {
                    continue;
                }
                boolean drawnLater = j == hover || (j > i && j != hover);
                if (drawnLater && HandLayout.pointHitsPose(poses[j], pt[0], pt[1], 0f)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                clear++;
            }
        }
        return clear / (float) samples;
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
    public void draggingCollapsesOtherCardsToRest() {
        float w = 1920f, h = 1080f;
        int n = 7, drag = 3;
        HandLayout.Pose inside = HandLayout.hoverPose(w, h, n, 1, drag);
        Assert.assertTrue(HandLayout.pointerInHand(w, h, n, drag, true, inside.cx, inside.cy));
        for (int i = 0; i < n; i++) {
            if (i == drag) {
                continue;
            }
            HandLayout.Pose heldOpen = HandLayout.dragLayoutPose(w, h, n, i, drag, true, drag, inside.cx, inside.cy);
            Assert.assertEquals(heldOpen.h, HandLayout.hoverOpenHeight(h), 0.01f,
                    "dragging inside the hand keeps the others opened");
            Assert.assertTrue(heldOpen.h > HandLayout.restHeight(h));
        }
        // Above the opened row: tuck. The fan tops sit higher than the raised card, and past the cast line.
        HandLayout.Pose raisedTop = HandLayout.hoverPose(w, h, n, drag, drag);
        float rowTop = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            HandLayout.Pose p = HandLayout.hoverPose(w, h, n, i, drag);
            float hw = p.w / 2f, hh = p.h / 2f;
            for (float lx : new float[]{-hw, hw}) {
                for (float ly : new float[]{-hh, hh}) {
                    rowTop = Math.max(rowTop, worldOnPose(p, lx, ly)[1]);
                }
            }
        }
        float aboveY = rowTop + HandLayout.OPEN_HIT_PAD_FRAC * h + 2f;
        Assert.assertTrue(aboveY > HandLayout.castLineY(h));
        Assert.assertFalse(HandLayout.pointerInHand(w, h, n, drag, true, raisedTop.cx, aboveY));
        for (int i = 0; i < n; i++) {
            if (i == drag) {
                continue;
            }
            HandLayout.Pose collapsed = HandLayout.dragLayoutPose(w, h, n, i, drag, true, drag, raisedTop.cx, aboveY);
            HandLayout.Pose rest = HandLayout.restPose(w, h, n, i);
            Assert.assertEquals(collapsed.cx, rest.cx, 0.001f);
            Assert.assertEquals(collapsed.cy, rest.cy, 0.001f);
            Assert.assertEquals(collapsed.w, rest.w, 0.001f);
            Assert.assertEquals(collapsed.h, rest.h, 0.001f);
            Assert.assertEquals(collapsed.rotationDeg, rest.rotationDeg, 0.001f);
            Assert.assertTrue(collapsed.h < HandLayout.hoverOpenHeight(h),
                    "leaving the hand tucks the rest of the hand");
        }
        // Title band of an opened card is above the cast line and still in the hand.
        float[] title = worldOnPose(raisedTop, 0f, raisedTop.h / 2f - raisedTop.h * HandLayout.TITLE_BAND_FRAC * 0.5f);
        if (title[1] > HandLayout.castLineY(h)) {
            Assert.assertTrue(HandLayout.pointerInHand(w, h, n, drag, true, title[0], title[1]));
            HandLayout.Pose stillOpen = HandLayout.dragLayoutPose(w, h, n, 1, drag, true, drag, title[0], title[1]);
            Assert.assertEquals(stillOpen.h, HandLayout.hoverOpenHeight(h), 0.01f);
        }
        HandLayout.Pose hoverOpen = HandLayout.layoutPose(w, h, n, 0, drag, false);
        Assert.assertEquals(hoverOpen.h, HandLayout.hoverOpenHeight(h), 0.01f);
        HandLayout.Pose raised = HandLayout.layoutPose(w, h, n, drag, drag, false);
        Assert.assertEquals(raised.h, HandLayout.hoverRaisedHeight(h), 0.01f);
    }

    @Test
    public void reorderIndexIsStableAtTheMidpoint() {
        float w = 1920f, h = 1080f;
        int n = 7, grip = 3;
        float left = HandLayout.slotCenterX(w, h, n, grip - 1);
        float right = HandLayout.slotCenterX(w, h, n, grip + 1);
        float mid = (left + right) / 2f;
        int at = HandLayout.reorderIndex(w, h, n, grip, mid);
        Assert.assertEquals(HandLayout.reorderIndex(w, h, n, grip, mid - 1f), at);
        Assert.assertEquals(HandLayout.reorderIndex(w, h, n, grip, mid + 1f), at);
        Assert.assertEquals(at, grip, "the gap stays put across the midpoint");
        float onCentre = HandLayout.slotCenterX(w, h, n, grip + 1);
        Assert.assertEquals(HandLayout.reorderIndex(w, h, n, grip, onCentre),
                HandLayout.reorderIndex(w, h, n, grip, onCentre - 0.25f));
        Assert.assertTrue(HandLayout.reorderIndex(w, h, n, grip, onCentre + 0.25f)
                > HandLayout.reorderIndex(w, h, n, grip, onCentre));
        int prev = 0;
        for (float x = 0f; x <= w; x += 3f) {
            int idx = HandLayout.reorderIndex(w, h, n, grip, x);
            Assert.assertTrue(idx >= prev && idx < n);
            prev = idx;
        }
    }

    @Test
    public void reorderLeavesASingleGapAndDoesNotRepeatTheGrip() {
        int n = 7, grip = 3, drop = 5;
        boolean[] used = new boolean[n];
        for (int i = 0; i < n; i++) {
            int slot = HandLayout.reorderSlot(n, i, grip, drop);
            if (i == grip) {
                Assert.assertEquals(slot, -1);
            } else {
                Assert.assertTrue(slot >= 0 && slot < n);
                Assert.assertFalse(used[slot], "slot " + slot + " used twice");
                used[slot] = true;
                HandLayout.Pose pose = HandLayout.reorderPose(1920f, 1080f, n, i, grip, drop);
                Assert.assertEquals(pose.h, HandLayout.hoverOpenHeight(1080f), 0.01f);
            }
        }
        Assert.assertFalse(used[drop], "the drop slot stays empty");
        int filled = 0;
        for (boolean u : used) {
            if (u) filled++;
        }
        Assert.assertEquals(filled, n - 1);
    }

    @Test
    public void releaseInsideHandReordersAndReleaseOutsideDoesNot() {
        float w = 1920f, h = 1080f;
        int n = 7, grip = 3;
        float past = HandLayout.slotCenterX(w, h, n, 5) + 1f;
        HandLayout.Pose row = HandLayout.hoverPose(w, h, n, 1, grip);
        Assert.assertTrue(HandLayout.pointerInHand(w, h, n, grip, true, past, row.cy));
        int[] moved = HandLayout.orderAfterRelease(w, h, n, grip, grip, true, past, row.cy);
        int drop = HandLayout.reorderIndex(w, h, n, grip, past);
        Assert.assertTrue(drop != grip);
        Assert.assertEquals(moved, HandLayout.movedOrder(n, grip, drop));
        Assert.assertEquals(moved[drop], grip);

        HandLayout.Pose top = HandLayout.hoverPose(w, h, n, grip, grip);
        float above = top.cy + top.h / 2f + HandLayout.OPEN_HIT_PAD_FRAC * h + 4f;
        int[] same = HandLayout.orderAfterRelease(w, h, n, grip, grip, true, top.cx, above);
        for (int i = 0; i < n; i++) {
            Assert.assertEquals(same[i], i);
        }
        float beside = -40f;
        int[] besideSame = HandLayout.orderAfterRelease(w, h, n, grip, grip, true, beside, row.cy);
        for (int i = 0; i < n; i++) {
            Assert.assertEquals(besideSame[i], i);
        }
    }

    @Test
    public void returningToTheRestingHandOpensReorderAgain() {
        float w = 1920f, h = 1080f;
        int n = 7, grip = 2;
        HandLayout.Pose rest = HandLayout.restPose(w, h, n, 4);
        Assert.assertTrue(HandLayout.pointerInHand(w, h, n, grip, false, rest.cx, rest.cy));
        HandLayout.Pose reopened = HandLayout.dragLayoutPose(w, h, n, 0, grip, false, grip, rest.cx, rest.cy);
        Assert.assertEquals(reopened.h, HandLayout.hoverOpenHeight(h), 0.01f);
        HandLayout.Pose open = HandLayout.hoverPose(w, h, n, 3, grip);
        float high = open.cy + open.h / 2f;
        Assert.assertFalse(HandLayout.pointerInHand(w, h, n, grip, false, open.cx, high),
                "while tucked, the old opened row is not the hand");
        HandLayout.Pose stayTucked = HandLayout.dragLayoutPose(w, h, n, 0, grip, false, grip, open.cx, high);
        HandLayout.Pose rest0 = HandLayout.restPose(w, h, n, 0);
        Assert.assertEquals(stayTucked.h, rest0.h, 0.01f);
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
