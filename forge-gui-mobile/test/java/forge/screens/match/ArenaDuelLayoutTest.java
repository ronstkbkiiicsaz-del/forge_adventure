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
                    "phaseRail", "opponentAvatar", "opponentField", "opponentPiles",
                    "stackColumn", "combatLane", "playerField", "playerHand", "playerAvatar",
                    "playerPiles", "contextButton", "cancelButton", "takeBackSlot", "promptBar",
                    "passUntilEot"
            }) {
                Assert.assertNotNull(r.get(name), name + " missing in "
                        + (landscape ? "landscape" : "portrait"));
                Assert.assertTrue(r.get(name).w > 0 && r.get(name).h > 0,
                        name + " must have positive size");
            }
            // menuBar is intentionally zero-height — classic FMenuBar was a tick-mark strip.
            Assert.assertNotNull(r.get("menuBar"));
            Assert.assertEquals(r.get("menuBar").h, 0f, 0.01f,
                    "Arena menuBand must stay collapsed (no classic tab strip)");
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
    public void handFanCardsKeepAspectAndStayFullyOnScreenInBothOrientations() {
        assertFanKeepsAspectInBand("playerHand", null);
    }

    @Test
    public void openedPileFansKeepAspectAndStayFullyOnScreenInBothOrientations() {
        // Local piles share the hand band (minus title chrome); opponent piles use the field slot.
        final float chromeH = 32f;
        assertFanKeepsAspectInBand("playerHand", chromeH);
        assertFanKeepsAspectInBand("opponentField", chromeH);
        assertFanKeepsAspectInBand("playerField", chromeH);
    }

    @Test
    public void arenaPileTitlesNameOwnerZoneAndCount() {
        Assert.assertEquals(formatPileTitle(true, "graveyard", 3), "Your graveyard (3)");
        Assert.assertEquals(formatPileTitle(false, "exile", 1), "Opponent's exile (1)");
        Assert.assertEquals(formatPileTitle(true, "library", 40), "Your library (40)");
        Assert.assertEquals(formatPileTitle(false, "graveyard", 0), "Opponent's graveyard (0)");
    }

    private static String formatPileTitle(final boolean local, final String zone, final int n) {
        return (local ? "Your" : "Opponent's") + " " + zone + " (" + n + ")";
    }

    private static void assertFanKeepsAspectInBand(final String bandName, final Float chromeH) {
        for (final boolean landscape : new boolean[] { true, false }) {
            final float sw = landscape ? 1280f : 720f;
            final float sh = landscape ? 720f : 1280f;
            final ArenaDuelLayout.Result layout = ArenaDuelLayout.compute(sw, sh, landscape);
            final ArenaDuelLayout.Rect band = layout.get(bandName);
            Assert.assertNotNull(band, (landscape ? "landscape " : "portrait ") + bandName);
            Assert.assertTrue(band.x >= -0.5f && band.y >= -0.5f,
                    bandName + " origin off-screen");
            Assert.assertTrue(band.x + band.w <= sw + 0.5f && band.y + band.h <= sh + 0.5f,
                    bandName + " extends past screen");
            final float fanY = chromeH == null ? band.y : band.y + chromeH;
            final float fanH = chromeH == null ? band.h : Math.max(1f, band.h - chromeH);
            final float fanX = band.x;
            final float fanW = band.w;
            for (final int count : new int[] { 1, 2, 3, 5, 7 }) {
                final int hover = count > 1 ? 1 : -1;
                final List<ArenaHandFan.CardPose> poses =
                        ArenaHandFan.fan(count, fanW, fanH, hover, null);
                Assert.assertEquals(poses.size(), count);
                for (final ArenaHandFan.CardPose p : poses) {
                    final float aspect = p.h / p.w;
                    Assert.assertTrue(aspect > 1.2f && aspect < 1.6f,
                            "card aspect " + aspect + " count=" + count
                                    + " " + bandName + " "
                                    + (landscape ? "landscape" : "portrait"));
                    Assert.assertTrue(p.x >= -0.5f && p.y >= -0.5f,
                            "card origin outside fan band");
                    Assert.assertTrue(p.x + p.w <= fanW + 0.5f,
                            "card right edge past fan band");
                    Assert.assertTrue(p.y + p.h <= fanH + 0.5f,
                            "card bottom edge past fan band (clipped)");
                    Assert.assertTrue(fanX + p.x >= -0.5f);
                    Assert.assertTrue(fanY + p.y >= -0.5f);
                    Assert.assertTrue(fanX + p.x + p.w <= sw + 0.5f);
                    Assert.assertTrue(fanY + p.y + p.h <= sh + 0.5f);
                }
            }
        }
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
    public void stackColumnPreservesCardAspectInWideLane() {
        // Landscape centre lane is wide and short — cards must not stretch to colW.
        final List<ArenaStackColumnLayout.ItemRect> items =
                ArenaStackColumnLayout.layout(1, 600f, 110f);
        Assert.assertEquals(items.size(), 1);
        final ArenaStackColumnLayout.ItemRect top = items.get(0);
        final float aspect = top.h / top.w;
        Assert.assertTrue(aspect > 1.2f && aspect < 1.6f,
                "stack card aspect should stay near 3.5/2.5, got " + aspect);
        Assert.assertTrue(top.w < 200f, "wide lane must not stretch card width to column");
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
        Assert.assertEquals(ArenaContextActions.passUntilEotLabel(), "Pass EOT");
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
        // Unused ds1* tunables were removed (side strip / sick dim / hand fan overlap).
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

    @Test
    public void cancelButtonSitsBesideContextWithoutOverlap() {
        final ArenaDuelLayout.Result r = ArenaDuelLayout.compute(1280f, 720f, true);
        final ArenaDuelLayout.Rect ctx = r.get("contextButton");
        final ArenaDuelLayout.Rect cancel = r.get("cancelButton");
        Assert.assertNotNull(cancel);
        Assert.assertTrue(cancel.x >= ctx.x + ctx.w - 0.5f);
        Assert.assertNull(ArenaDuelLayout.firstOverlap(r.leafRegions()));
    }

    @Test
    public void padChoosesArenaContextWhenNoHandOrMana() {
        Assert.assertEquals(ModernDuelPad.chooseXTarget(false, false, true),
                ModernDuelPad.Focus.CONTEXT);
        Assert.assertEquals(ModernDuelPad.chooseXTarget(false, false, false),
                ModernDuelPad.Focus.PHASE);
        Assert.assertEquals(ModernDuelPad.cycleArenaChrome(ModernDuelPad.Focus.CONTEXT, 1),
                ModernDuelPad.Focus.CANCEL);
        Assert.assertEquals(ModernDuelPad.cycleArenaChrome(ModernDuelPad.Focus.CANCEL, 1),
                ModernDuelPad.Focus.PHASE);
    }

    @Test
    public void useArenaLayoutNeverForNetPlayGateHelper() {
        // Pure resolve still allows Always; useArenaLayout adds the net-play refuse.
        Assert.assertTrue(ModernDuelScreen.resolve("Always", false));
        ModernDuelScreen.invalidate();
        final ConfigData on = new ConfigData();
        on.ds1ArenaDuelScreen = true;
        Config.installConfigDataForTest(on);
        // Without Always pref / adventure, enabled is false → layout off.
        Assert.assertFalse(ModernDuelScreen.useArenaLayout());
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
