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
        Assert.assertEquals(left.rotationDeg, -HandLayout.ROTATION_MAX_DEG, 0.01f);
        Assert.assertEquals(right.rotationDeg, HandLayout.ROTATION_MAX_DEG, 0.01f);
        Assert.assertTrue(left.cy < mid.cy);
        Assert.assertTrue(right.cy < mid.cy);
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
    public void hoverRaisesAndStraightensWhileNeighboursSpread() {
        float w = 1920f, h = 1080f;
        int n = 7, hover = 3;
        HandLayout.Pose raised = HandLayout.hoverPose(w, h, n, hover, hover);
        Assert.assertEquals(raised.rotationDeg, 0f, 0.001f);
        Assert.assertEquals(raised.scale, 1f, 0.001f);
        Assert.assertEquals(raised.cy - raised.h / 2f, HandLayout.HOVER_BOTTOM_FRAC * h, 0.5f);

        HandLayout.Pose left = HandLayout.hoverPose(w, h, n, hover - 1, hover);
        HandLayout.Pose restLeft = HandLayout.restPose(w, h, n, hover - 1);
        Assert.assertTrue(left.cx < restLeft.cx);
        HandLayout.Pose right = HandLayout.hoverPose(w, h, n, hover + 1, hover);
        HandLayout.Pose restRight = HandLayout.restPose(w, h, n, hover + 1);
        Assert.assertTrue(right.cx > restRight.cx);
        float near = Math.abs(HandLayout.neighborShiftX(h, hover + 1, hover));
        float far = Math.abs(HandLayout.neighborShiftX(h, hover + 2, hover));
        Assert.assertTrue(near > far);
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
                top + HandLayout.LEAVE_HAND_ABOVE_FRAC * h + 1f));
        Assert.assertFalse(HandLayout.pointerInHandArea(w, h, n, hover, -100f, raised.cy));
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
