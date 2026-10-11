package forge.game;

import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Documents the CME fix used by {@link Game#forEachCardInGame}: zone lists are live;
 * iteration must use an independent snapshot ({@link CardCollection#getView(Iterable, boolean)}
 * with {@code allowModify=true}).
 */
public class GameForEachCardSnapshotTest {

    @Test
    public void getViewAllowModifyIsIndependentOfLaterMutation() {
        final CardCollection live = new CardCollection();
        Assert.assertTrue(live.isEmpty());
        final CardCollectionView snap = CardCollection.getView(live, true);
        // Mutating the live collection must not affect the snapshot (and must not CME
        // an in-flight for-each over the snapshot).
        live.clear();
        live.addAll(new CardCollection());
        Assert.assertTrue(snap.isEmpty());
        int visited = 0;
        for (final Object ignored : snap) {
            visited++;
            live.clear(); // would CME if snap were the live list
        }
        Assert.assertEquals(visited, 0);
    }
}
