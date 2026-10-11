package forge.screens.match;

import forge.adventure.data.ConfigData;
import forge.adventure.util.Config;
import forge.localinstance.properties.ForgePreferences.FPref;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * DS1 PR1: real behaviour tests for the Arena duel skeleton — layout geometry
 * (full pairwise overlap in landscape and portrait), battlefield arrange, hand
 * fan, stack column, context labels, and preference gating. No source greps;
 * no GL; no real Forge user folder.
 */
public class ArenaDuelLayoutTest {

    @Test
    public void landscapeLeavesHaveNoPairwiseOverlap() {
        assertNoLeafOverlap(ArenaDuelLayout.compute(1280f, 720f, true), "landscape 1280x720");
        assertNoLeafOverlap(ArenaDuelLayout.compute(960f, 540f, true), "landscape 960x540");
        assertNoLeafOverlap(ArenaDuelLayout.compute(800f, 480f, true), "landscape 800x480");
    }

    @Test
    public void portraitLeavesHaveNoPairwiseOverlap() {
        assertNoLeafOverlap(ArenaDuelLayout.compute(720f, 1280f, false), "portrait 720x1280");
        assertNoLeafOverlap(ArenaDuelLayout.compute(480f, 800f, false), "portrait 480x800");
        assertNoLeafOverlap(ArenaDuelLayout.compute(360f, 640f, false), "portrait 360x640");
    }

    @Test
    public void landscapeAndPortraitExposeRequiredRegions() {
        for (final boolean landscape : new boolean[] { true, false }) {
            final ArenaDuelLayout.Result r = ArenaDuelLayout.compute(1000f, 600f, landscape);
            for (final String name : new String[] {
                    "phaseRail", "menuBar", "opponentAvatar", "opponentField", "opponentPiles",
                    "stackColumn", "combatLane", "playerField", "playerHand", "playerAvatar",
                    "playerPiles", "contextButton", "takeBackSlot", "promptBar", "passUntilEot"
            }) {
                Assert.assertNotNull(r.get(name), name + " missing in "
                        + (landscape ? "landscape" : "portrait"));
                Assert.assertTrue(r.get(name).w > 0 && r.get(name).h > 0,
                        name + " must have positive size");
            }
        }
    }

    @Test
    public void opponentIsAbovePlayerAndHandIsBelowField() {
        final ArenaDuelLayout.Result land = ArenaDuelLayout.compute(1280f, 720f, true);
        Assert.assertTrue(land.get("opponentArea").y < land.get("playerArea").y);
        Assert.assertTrue(land.get("playerField").y < land.get("playerHand").y
                || Math.abs(land.get("playerField").y + land.get("playerField").h
                - land.get("playerHand").y) < 0.5f);
        final ArenaDuelLayout.Result port = ArenaDuelLayout.compute(720f, 1280f, false);
        Assert.assertTrue(port.get("opponentArea").y < port.get("playerArea").y);
        Assert.assertTrue(port.get("stackColumn").h > 0);
    }

    @Test
    public void tunablesAffectLayoutFractions() {
        final ArenaDuelLayout.Result tightHand = ArenaDuelLayout.compute(
                1000f, 600f, true, 0.07f, 0.20f, 0.48f, 56f);
        final ArenaDuelLayout.Result tallHand = ArenaDuelLayout.compute(
                1000f, 600f, true, 0.07f, 0.38f, 0.48f, 56f);
        Assert.assertTrue(tallHand.get("playerHand").h > tightHand.get("playerHand").h);
    }

    @Test
    public void battlefieldArrangeLandsBackCreaturesFrontOthersSide() {
        final List<ArenaBattlefieldArrange.Permanent> in = new ArrayList<>();
        in.add(new ArenaBattlefieldArrange.Permanent("f1", ArenaBattlefieldArrange.Kind.LAND,
                "Forest", false, false, false));
        in.add(new ArenaBattlefieldArrange.Permanent("f2", ArenaBattlefieldArrange.Kind.LAND,
                "Forest", false, false, false));
        in.add(new ArenaBattlefieldArrange.Permanent("f3", ArenaBattlefieldArrange.Kind.LAND,
                "Forest", false, true, false));
        in.add(new ArenaBattlefieldArrange.Permanent("b1", ArenaBattlefieldArrange.Kind.CREATURE,
                "Bear", true, false, false));
        in.add(new ArenaBattlefieldArrange.Permanent("e1", ArenaBattlefieldArrange.Kind.OTHER,
                "Enchantment", false, false, false));
        in.add(new ArenaBattlefieldArrange.Permanent("a1", ArenaBattlefieldArrange.Kind.CREATURE,
                "AuraHost", false, false, true));
        final ArenaBattlefieldArrange.Arrangement a = ArenaBattlefieldArrange.arrange(in);
        Assert.assertEquals(a.creatures.size(), 2);
        Assert.assertEquals(a.others.size(), 1);
        // Two Forests share stackKey and no attachments → one stack of 2; tapped Forest
        // shares the same key so it stacks too (caller can split by tap if desired).
        Assert.assertEquals(a.lands.size(), 1);
        Assert.assertEquals(a.lands.get(0).count, 3);
        // Attachment host never stacks away.
        Assert.assertTrue(a.creatures.stream().anyMatch(s -> s.top.hasAttachments));
    }

    @Test
    public void identicalLandsWithAttachmentsDoNotStack() {
        final List<ArenaBattlefieldArrange.Permanent> in = List.of(
                new ArenaBattlefieldArrange.Permanent("f1", ArenaBattlefieldArrange.Kind.LAND,
                        "Forest", false, false, true),
                new ArenaBattlefieldArrange.Permanent("f2", ArenaBattlefieldArrange.Kind.LAND,
                        "Forest", false, false, false));
        final ArenaBattlefieldArrange.Arrangement a = ArenaBattlefieldArrange.arrange(in);
        Assert.assertEquals(a.lands.size(), 2);
    }

    @Test
    public void handFanLiftsHoverAndMarksPlayable() {
        final boolean[] playable = { true, false, true };
        final List<ArenaHandFan.CardPose> poses =
                ArenaHandFan.fan(3, 400f, 100f, 1, playable);
        Assert.assertEquals(poses.size(), 3);
        Assert.assertTrue(poses.get(1).lifted);
        Assert.assertFalse(poses.get(0).lifted);
        Assert.assertTrue(poses.get(0).playable);
        Assert.assertFalse(poses.get(1).playable);
        // Cards spread across the band.
        Assert.assertTrue(poses.get(2).x > poses.get(0).x);
    }

    @Test
    public void stackColumnTopItemIsLargest() {
        final List<ArenaStackColumnLayout.ItemRect> items =
                ArenaStackColumnLayout.layout(4, 200f, 300f);
        Assert.assertEquals(items.size(), 4);
        Assert.assertTrue(items.get(0).scale >= items.get(1).scale);
        Assert.assertTrue(items.get(1).scale >= items.get(2).scale);
        Assert.assertTrue(items.get(0).h >= items.get(3).h);
        Assert.assertEquals(items.get(0).index, 0);
    }

    @Test
    public void contextLabelsResolveFromPromptAndCombat() {
        Assert.assertEquals(
                ArenaContextActions.resolve("OK", false, false, false),
                ArenaContextActions.ContextKind.PASS);
        Assert.assertEquals(
                ArenaContextActions.resolve("Pass", true, false, false),
                ArenaContextActions.ContextKind.RESOLVE);
        Assert.assertEquals(
                ArenaContextActions.resolve("OK", false, true, false),
                ArenaContextActions.ContextKind.ATTACK);
        Assert.assertEquals(
                ArenaContextActions.resolve("OK", false, false, true),
                ArenaContextActions.ContextKind.DONE);
        Assert.assertEquals(
                ArenaContextActions.resolve("Keep", false, false, false),
                ArenaContextActions.ContextKind.KEEP);
        Assert.assertEquals(ArenaContextActions.label(ArenaContextActions.ContextKind.RESOLVE),
                "Resolve");
        Assert.assertEquals(ArenaContextActions.passUntilEotLabel(), "Pass until EOT");
    }

    @Test
    public void modernGateStillAutoOnlyForAscendantAdventure() {
        Assert.assertTrue(ModernDuelScreen.resolve("Auto", true));
        Assert.assertFalse(ModernDuelScreen.resolve("Auto", false));
        Assert.assertFalse(ModernDuelScreen.resolve("Never", true));
        Assert.assertTrue(ModernDuelScreen.resolve("Always", false));
        Assert.assertEquals(FPref.UI_MODERN_DUEL_SCREEN.getDefault(), "Auto");
    }

    @Test
    public void configDataDs1DefaultsMatchAscendantJsonKeys() {
        final ConfigData d = new ConfigData();
        Assert.assertTrue(d.ds1ArenaDuelScreen);
        Assert.assertEquals(d.ds1PhaseRailFraction, 0.07f, 0.0001f);
        Assert.assertEquals(d.ds1HandFraction, 0.28f, 0.0001f);
        Assert.assertEquals(d.ds1StackFraction, 0.48f, 0.0001f);
        Assert.assertEquals(d.ds1DragSlopPx, 9f, 0.0001f);
        Assert.assertEquals(ModernDuelScreen.DRAG_SLOP_PX, 9f, 0.0001f);
    }

    @Test
    public void useArenaLayoutRequiresEnabledAndConfigFlag() {
        ModernDuelScreen.invalidate();
        final ConfigData off = new ConfigData();
        off.ds1ArenaDuelScreen = false;
        Config.installConfigDataForTest(off);
        // Headless: Auto + not adventure → enabled false → useArenaLayout false.
        Assert.assertFalse(ModernDuelScreen.useArenaLayout());

        final ConfigData on = new ConfigData();
        on.ds1ArenaDuelScreen = true;
        Config.installConfigDataForTest(on);
        ModernDuelScreen.invalidate();
        // Still false without adventure+ascendant match context.
        Assert.assertFalse(ModernDuelScreen.enabled());
        Assert.assertFalse(ModernDuelScreen.useArenaLayout());
        // Flag alone does not force Always.
        Assert.assertTrue(on.ds1ArenaDuelScreen);
        Assert.assertFalse(ModernDuelScreen.resolve("Never", true));
    }

    @Test
    public void sickDimAndSideStripClamp() {
        Assert.assertEquals(ArenaBattlefieldArrange.sideStripFraction(0f), 0.22f, 0.0001f);
        Assert.assertEquals(ArenaBattlefieldArrange.sideStripFraction(0.5f), 0.35f, 0.0001f);
        Assert.assertEquals(ArenaBattlefieldArrange.sickDimFactor(0f), 0.72f, 0.0001f);
        Assert.assertTrue(ArenaBattlefieldArrange.sickDimFactor(0.5f) < 1f);
    }

    private static void assertNoLeafOverlap(final ArenaDuelLayout.Result result, final String label) {
        final List<ArenaDuelLayout.Rect> leaves = result.leafRegions();
        Assert.assertTrue(leaves.size() >= 10, label + " expected many leaf regions, got " + leaves.size());
        // Full pairwise — every leaf vs every other leaf.
        final String conflict = ArenaDuelLayout.firstOverlap(leaves);
        Assert.assertNull(conflict, label + ": " + conflict);
        // Also assert every leaf sits inside the viewport implied by menu+body+prompt.
        float maxX = 0, maxY = 0;
        for (final ArenaDuelLayout.Rect r : result.regions().values()) {
            maxX = Math.max(maxX, r.x + r.w);
            maxY = Math.max(maxY, r.y + r.h);
        }
        for (final ArenaDuelLayout.Rect r : leaves) {
            Assert.assertTrue(r.x >= -0.01f && r.y >= -0.01f,
                    label + " " + r + " outside origin");
            Assert.assertTrue(r.x + r.w <= maxX + 0.5f && r.y + r.h <= maxY + 0.5f,
                    label + " " + r + " outside viewport " + maxX + "x" + maxY);
        }
    }
}
