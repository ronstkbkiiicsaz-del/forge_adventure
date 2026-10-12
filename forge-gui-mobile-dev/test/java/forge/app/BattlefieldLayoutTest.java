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

    @Test
    public void castSpellIsAStackEntryAndDoesNotCoverTheBoard() {
        BattlefieldLayout.StackEntry shock = BattlefieldLayout.castSpell("Shock", true);
        Assert.assertEquals(shock.name, "Shock");
        Assert.assertTrue(shock.needsTarget);
        float w = 1920f, h = 1080f;
        HandLayout.Pose thumb = BattlefieldLayout.stackThumbnail(w, h);
        Assert.assertFalse(BattlefieldLayout.occupiesFullCardOverBoard(thumb, w, h));
        HandLayout.Pose boardCard = BattlefieldLayout.slot(w, h, BattlefieldLayout.Side.YOU,
                BattlefieldLayout.Row.CREATURE, 0, 1, false);
        Assert.assertTrue(BattlefieldLayout.occupiesFullCardOverBoard(boardCard, w, h));
    }

    @Test
    public void targetedSpellWaitsUntilATargetIsChosen() {
        BattlefieldLayout.StackEntry shock = BattlefieldLayout.castSpell("Shock", true);
        Assert.assertTrue(BattlefieldLayout.waitingForTarget(shock));
        Assert.assertEquals(BattlefieldLayout.TARGET_PROMPT, "Choose a target.");
        BattlefieldLayout.StackEntry chosen = BattlefieldLayout.chooseTarget(shock, "Sprite");
        Assert.assertFalse(BattlefieldLayout.waitingForTarget(chosen));
        Assert.assertEquals(chosen.target, "Sprite");
    }

    @Test
    public void oneClickAssignsATargetWithNoConfirm() {
        BattlefieldLayout.StackEntry shock = BattlefieldLayout.castSpell("Shock", true);
        BattlefieldLayout.TargetAssignment assigned = BattlefieldLayout.clickTarget(shock, "Grizzly");
        Assert.assertFalse(assigned.confirmRequired);
        Assert.assertEquals(assigned.entry.target, "Grizzly");
        Assert.assertFalse(BattlefieldLayout.waitingForTarget(assigned.entry));
        Assert.assertEquals(BattlefieldLayout.stackOutcome("Shock", "Grizzly"), "Shock deals 2 to Grizzly");
        java.util.List<BattlefieldLayout.StackEntry> stacked = java.util.List.of(shock);
        java.util.List<BattlefieldLayout.StackEntry> aimed = BattlefieldLayout.assignTopTarget(stacked, "Grizzly");
        Assert.assertEquals(aimed.size(), stacked.size());
        Assert.assertEquals(aimed.get(0).target, "Grizzly");
    }

    @Test
    public void twoSpellsStayOnTheStackUntilResolvePopsTheTop() {
        java.util.List<BattlefieldLayout.StackEntry> stack = java.util.List.of();
        Assert.assertTrue(BattlefieldLayout.mayCast(BattlefieldLayout.Speed.SORCERY, stack.size()));
        stack = BattlefieldLayout.castOnto(stack, BattlefieldLayout.Speed.INSTANT, "Shock", true);
        Assert.assertEquals(stack.size(), 1);
        Assert.assertFalse(BattlefieldLayout.mayCast(BattlefieldLayout.Speed.SORCERY, stack.size()));
        java.util.List<BattlefieldLayout.StackEntry> refused = BattlefieldLayout.castOnto(
                stack, BattlefieldLayout.Speed.SORCERY, "Grizzly", false);
        Assert.assertEquals(refused.size(), 1);
        Assert.assertTrue(BattlefieldLayout.mayCast(BattlefieldLayout.Speed.INSTANT, stack.size()));
        stack = BattlefieldLayout.castOnto(stack, BattlefieldLayout.Speed.INSTANT, "Shock", true);
        Assert.assertEquals(stack.size(), 2);
        Assert.assertEquals(stack.get(1).name, "Shock");
        Assert.assertTrue(stack.get(0).fullText.contains("any target"));
        Assert.assertTrue(stack.get(1).fullText.contains("any target"));
        Assert.assertFalse(BattlefieldLayout.stackArtCoversBoard(1920f, 1080f));
        HandLayout.Pose full = BattlefieldLayout.slot(1920f, 1080f, BattlefieldLayout.Side.YOU,
                BattlefieldLayout.Row.CREATURE, 0, 1, false);
        Assert.assertTrue(BattlefieldLayout.occupiesFullCardOverBoard(full, 1920f, 1080f));
        Assert.assertFalse(BattlefieldLayout.occupiesFullCardOverBoard(
                BattlefieldLayout.stackThumbnail(1920f, 1080f), 1920f, 1080f));
        stack = BattlefieldLayout.assignTopTarget(stack, "Grizzly");
        Assert.assertEquals(stack.get(1).target, "Grizzly");
        Assert.assertTrue(BattlefieldLayout.waitingForTarget(stack.get(0)));
        Assert.assertFalse(BattlefieldLayout.clickTarget(stack.get(1), "Grizzly").confirmRequired);
        stack = BattlefieldLayout.resolveTop(stack);
        Assert.assertEquals(stack.size(), 1);
        Assert.assertTrue(BattlefieldLayout.waitingForTarget(stack.get(0)));
        stack = BattlefieldLayout.assignTopTarget(stack, "Sprite");
        stack = BattlefieldLayout.resolveTop(stack);
        Assert.assertTrue(stack.isEmpty());
    }

    @Test
    public void stackEntryKeepsAReadableFullText() {
        BattlefieldLayout.StackEntry shock = BattlefieldLayout.castSpell("Shock", true);
        Assert.assertNotNull(shock.fullText);
        Assert.assertFalse(shock.fullText.isBlank());
        Assert.assertTrue(shock.fullText.contains("any target"));
        BattlefieldLayout.StackEntry cavern = BattlefieldLayout.castSpell("Cavern of Souls", false);
        Assert.assertFalse(cavern.fullText.isBlank());
        Assert.assertFalse(BattlefieldLayout.waitingForTarget(cavern));
    }

    @Test
    public void screenYMovesUpWhenThePointerMovesUp() {
        float windowH = 1080f;
        float lowOnScreen = BattlefieldLayout.screenYToLayoutY(windowH, 800f);
        float higherOnScreen = BattlefieldLayout.screenYToLayoutY(windowH, 200f);
        Assert.assertTrue(higherOnScreen > lowOnScreen);
        Assert.assertEquals(BattlefieldLayout.screenYToLayoutY(windowH, 0f), windowH, 0.001f);
        Assert.assertEquals(BattlefieldLayout.screenYToLayoutY(windowH, windowH), 0f, 0.001f);
        Assert.assertEquals(higherOnScreen - lowOnScreen, 600f, 0.001f);
    }

    @Test
    public void arrowSticksOnlyWhenThePointerIsInsideARectangle() {
        float[] cx = {200f, 500f};
        float[] cy = {300f, 700f};
        float[] w = {80f, 80f};
        float[] h = {110f, 110f};
        Assert.assertEquals(BattlefieldLayout.stickIndexIfInside(200f, 300f, cx, cy, w, h), 0);
        Assert.assertEquals(BattlefieldLayout.stickIndexIfInside(530f, 740f, cx, cy, w, h), 1);
        float outsideX = 200f + 41f;
        Assert.assertEquals(BattlefieldLayout.stickIndexIfInside(outsideX, 300f, cx, cy, w, h), -1);
        Assert.assertEquals(BattlefieldLayout.snapNearest(outsideX, 300f, cx, cy, 80f), 0);
        Assert.assertEquals(BattlefieldLayout.stickIndexIfInside(350f, 500f, cx, cy, w, h), -1);
        Assert.assertEquals(BattlefieldLayout.stickIndexIfInside(510f, 640f, cx, cy, w, h), -1);
    }
}
