package forge.ascendant;

import forge.ascendant.world.Site;
import org.junit.Assert;
import org.junit.Test;

public class TownsfolkPlacementTest {
    @Test
    public void miraStandsOnTheHavenbrookSquare() {
        Site home = new Site("town", "Havenbrook", 100, 200, 90, 52,
                "map/ascendant/havenbrook.tmx", 8, true);
        float[] at = Townsfolk.worldCenter(home, 640, 672, 16);
        Assert.assertEquals(140.5f, at[0], 0.01f);
        Assert.assertEquals(218.5f, at[1], 0.01f);
    }
}
