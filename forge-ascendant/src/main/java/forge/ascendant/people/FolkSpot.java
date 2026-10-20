package forge.ascendant.people;

/** One townsfolk anchor in TMX tile space, y measured up from the south edge of the full map. */
public final class FolkSpot {
    public final String id;
    public final String role;
    public final int localX;
    public final int localY;

    public FolkSpot(String id, String role, int localX, int localY) {
        this.id = id;
        this.role = role;
        this.localX = localX;
        this.localY = localY;
    }
}
