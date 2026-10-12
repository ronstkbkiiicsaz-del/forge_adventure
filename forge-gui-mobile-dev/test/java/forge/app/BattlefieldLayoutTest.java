package forge.app;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Headless layout checks for the battlefield prototype. Does not touch the real user dir.
 */
public class BattlefieldLayoutTest {

    @Test
    public void boardCardsAreSixteenPercentOfHeight() {
        float h = 1080f;
        Assert.assertEquals(BattlefieldLayout.cardHeight(h), 0.16f * h, 0.001f);
        Assert.assertEquals(BattlefieldLayout.cardWidth(h),
                BattlefieldLayout.cardHeight(h) * HandLayout.CARD_ASPECT, 0.001f);
    }

    @Test
    public void creaturesSitCloserToCenterThanLands() {
        float h = 1080f;
        float mid = h / 2f;
        float youLand = BattlefieldLayout.rowCenterY(h, BattlefieldLayout.Side.YOU, BattlefieldLayout.Row.LAND);
        float youCreature = BattlefieldLayout.rowCenterY(h, BattlefieldLayout.Side.YOU, BattlefieldLayout.Row.CREATURE);
        float themLand = BattlefieldLayout.rowCenterY(h, BattlefieldLayout.Side.OPPONENT, BattlefieldLayout.Row.LAND);
        float themCreature = BattlefieldLayout.rowCenterY(h, BattlefieldLayout.Side.OPPONENT, BattlefieldLayout.Row.CREATURE);
        Assert.assertTrue(Math.abs(youCreature - mid) < Math.abs(youLand - mid));
        Assert.assertTrue(Math.abs(themCreature - mid) < Math.abs(themLand - mid));
        Assert.assertTrue(youLand > HandLayout.REST_TOP_FRAC * h);
        Assert.assertTrue(themLand > themCreature);
        Assert.assertTrue(youCreature > youLand);
    }

    @Test
    public void rowIsCentredAndOrdered() {
        float w = 1920f, h = 1080f;
        HandLayout.Pose left = BattlefieldLayout.slot(w, h, BattlefieldLayout.Side.YOU,
                BattlefieldLayout.Row.CREATURE, 0, 3, false);
        HandLayout.Pose mid = BattlefieldLayout.slot(w, h, BattlefieldLayout.Side.YOU,
                BattlefieldLayout.Row.CREATURE, 1, 3, false);
        HandLayout.Pose right = BattlefieldLayout.slot(w, h, BattlefieldLayout.Side.YOU,
                BattlefieldLayout.Row.CREATURE, 2, 3, false);
        Assert.assertEquals(mid.cx, w / 2f, 0.5f);
        Assert.assertTrue(left.cx < mid.cx && mid.cx < right.cx);
        HandLayout.Pose tapped = BattlefieldLayout.slot(w, h, BattlefieldLayout.Side.YOU,
                BattlefieldLayout.Row.LAND, 0, 1, true);
        Assert.assertEquals(Math.abs(tapped.rotationDeg), BattlefieldLayout.TAP_DEG, 0.01f);
        Assert.assertEquals(BattlefieldLayout.slot(w, h, BattlefieldLayout.Side.YOU,
                BattlefieldLayout.Row.LAND, 0, 1, false).cx, tapped.cx, 0.01f);
        float landBottom = BattlefieldLayout.rowCenterY(h, BattlefieldLayout.Side.YOU, BattlefieldLayout.Row.LAND)
                - BattlefieldLayout.cardHeight(h) / 2f;
        Assert.assertTrue(landBottom > HandLayout.REST_TOP_FRAC * h);
    }

    @Test
    public void attackZoneIsTheOpponentHalfAndSnapsNearTheirBadge() {
        float h = 1080f;
        float line = BattlefieldLayout.ATTACK_LINE_FRAC * h;
        float badgeX = 200f;
        float badgeY = line + 30f;
        Assert.assertTrue(BattlefieldLayout.declaresAttack(h, 800f, line, badgeX, badgeY));
        Assert.assertFalse(BattlefieldLayout.declaresAttack(h, 800f, line - 80f, badgeX, badgeY));
        float near = badgeY - BattlefieldLayout.SNAP_RADIUS_FRAC * h * 0.45f;
        Assert.assertTrue(near < line);
        Assert.assertTrue(BattlefieldLayout.declaresAttack(h, badgeX, near, badgeX, badgeY));
    }

    @Test
    public void blockSnapsToTheNearestAttackerInsideAGenerousRadius() {
        float h = 1080f;
        float[] xs = {400f, 700f};
        float[] ys = {700f, 700f};
        float radius = BattlefieldLayout.SNAP_RADIUS_FRAC * h;
        Assert.assertEquals(BattlefieldLayout.snapBlocker(h, 400f + radius * 0.4f, 700f, xs, ys), 0);
        Assert.assertEquals(BattlefieldLayout.snapBlocker(h, 690f, 710f, xs, ys), 1);
        Assert.assertEquals(BattlefieldLayout.snapBlocker(h, 400f, 700f + radius + 8f, xs, ys), -1);
        Assert.assertTrue(BattlefieldLayout.ignoredJitter(11f));
        Assert.assertFalse(BattlefieldLayout.ignoredJitter(HandLayout.DRAG_THRESHOLD_PX));
    }

    @Test
    public void summoningSicknessAndTapBlockAttackAndActivate() {
        Assert.assertTrue(BattlefieldLayout.canAttack(BattlefieldLayout.Kind.CREATURE, false, false));
        Assert.assertFalse(BattlefieldLayout.canAttack(BattlefieldLayout.Kind.CREATURE, false, true));
        Assert.assertFalse(BattlefieldLayout.canAttack(BattlefieldLayout.Kind.CREATURE, true, false));
        Assert.assertFalse(BattlefieldLayout.canAttack(BattlefieldLayout.Kind.LAND, false, false));
        Assert.assertFalse(BattlefieldLayout.canActivate(BattlefieldLayout.Kind.CREATURE, false, true));
        Assert.assertTrue(BattlefieldLayout.canActivate(BattlefieldLayout.Kind.CREATURE, false, false));
    }

    @Test
    public void choiceStaysPrintedAndNumbersShowBuffsDebuffsAndCounters() {
        Assert.assertEquals(BattlefieldLayout.printedChoice(null), "Choose a creature type");
        Assert.assertEquals(BattlefieldLayout.printedChoice("  "), "Choose a creature type");
        Assert.assertEquals(BattlefieldLayout.printedChoice("Dragon"), "Dragon");
        Assert.assertEquals(BattlefieldLayout.shownPower(2, 2, 0, 0), 4);
        Assert.assertEquals(BattlefieldLayout.shownToughness(4, 0, 2, 0), 2);
        Assert.assertEquals(BattlefieldLayout.shownPower(1, 0, 0, 3), 4);
        Assert.assertEquals(BattlefieldLayout.shownPower(1, 0, 0, 4), 5);
    }

    @Test
    public void previewsDescribeTheOutcomeBeforeItResolves() {
        BattlefieldLayout.CombatPreview trade = BattlefieldLayout.previewBlock(3, 3, 3, 3);
        Assert.assertTrue(trade.attackerDies && trade.blockerDies);
        Assert.assertEquals(trade.lifeLoss, 0);
        BattlefieldLayout.CombatPreview chump = BattlefieldLayout.previewBlock(3, 3, 2, 2);
        Assert.assertFalse(chump.attackerDies);
        Assert.assertTrue(chump.blockerDies);
        BattlefieldLayout.CombatPreview open = BattlefieldLayout.previewUnblocked("Them", 4);
        Assert.assertEquals(open.lifeLoss, 4);
        Assert.assertTrue(open.summary.contains("4"));
        Assert.assertEquals(BattlefieldLayout.abilityPreview("Veteran", "Hero", 4),
                "Veteran taps. Hero will show 4 +1/+1.");
        Assert.assertTrue(BattlefieldLayout.shockOnCreature("Sprite", 1, 0, 2).contains("will die"));
        Assert.assertTrue(BattlefieldLayout.shockOnCreature("Ogre", 3, 0, 2).contains("survives"));
        Assert.assertEquals(BattlefieldLayout.lifeChange("Them", 20, 2), "Them 20 -> 18");
    }
}
