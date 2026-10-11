package forge.screens.match;

import forge.adventure.data.ConfigData;
import forge.adventure.util.Config;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * DS1 low: classic {@code VCardDisplayArea}/{@code VPlayerPanel} paths stay off
 * when Arena is inactive. Headless — no GL, no real Forge user folder.
 */
public class ArenaClassicUnchangedTest {

    @Test
    public void arenaUiInactiveMeansClassicLayoutGatesStayClosed() {
        ModernDuelScreen.invalidate();
        final ConfigData data = new ConfigData();
        data.ds1ArenaDuelScreen = true;
        Config.installConfigDataForTest(data);
        if (FModel.getPreferences() != null) {
            FModel.getPreferences().setPref(FPref.UI_MODERN_DUEL_SCREEN, ModernDuelScreen.MODE_NEVER);
        }
        ModernDuelScreen.invalidate();

        Assert.assertFalse(ModernDuelScreen.enabled(),
                "Never must keep modern/Arena off for classic MatchScreen");
        Assert.assertFalse(ModernDuelScreen.useArenaLayout());
        Assert.assertFalse(ArenaDuelUi.isActive(),
                "headless classic path must not report Arena active "
                        + "(VCardDisplayArea fan / VPlayerPanel doArenaLayout stay closed)");
    }

    @Test
    public void autoModeWithoutAdventureKeepsClassicScreen() {
        ModernDuelScreen.invalidate();
        if (FModel.getPreferences() != null) {
            FModel.getPreferences().setPref(FPref.UI_MODERN_DUEL_SCREEN, ModernDuelScreen.MODE_AUTO);
        }
        ModernDuelScreen.invalidate();
        Assert.assertFalse(ModernDuelScreen.resolve("Auto", false));
        Assert.assertFalse(ModernDuelScreen.useArenaLayout());
        Assert.assertFalse(ArenaDuelUi.isActive());
    }
}
