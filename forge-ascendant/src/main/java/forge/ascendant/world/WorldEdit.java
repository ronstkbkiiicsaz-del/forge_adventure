package forge.ascendant.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sparse player edits on top of generated {@link PlaneData}.
 * Surface deltas + one underground layer (same XY). Grows later via signed layer index.
 */
public final class WorldEdit {
    public static final int SURFACE = 0;
    public static final int UNDER = -1;
    public static final byte UNDER_EMPTY = 1;
    public static final int REACH = 2;
    public static final float TORCH_RADIUS = 6.5f;
    public static final float PIT_LIGHT = 3.2f;

    public enum Tool {
        CHOP("CHOP", "Chop trees, break rocks, remove builds"),
        DIG("DIG", "Dig a pit or excavate underground"),
        FLOOR("FLOOR", "Place a floor"),
        WALL("WALL", "Place a wall"),
        DOOR("DOOR", "Place a door"),
        TORCH("TORCH", "Place a torch"),
        LADDER("LADDER", "Place a ladder on a pit");

        public final String label;
        public final String hint;

        Tool(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        public static Tool byIndex(int i) {
            Tool[] all = values();
            int n = all.length;
            return all[Math.floorMod(i, n)];
        }
    }

    public enum Fixture {
        FLOOR, WALL, DOOR, TORCH, LADDER
    }

    public int wood;
    public int stone;
    public int dirt;
    public Tool tool = Tool.CHOP;

    private final Map<Long, Byte> surface = new LinkedHashMap<>();
    private final Map<Long, Byte> under = new LinkedHashMap<>();
    private final Map<Long, Fixture> floors = new LinkedHashMap<>();
    private final Map<Long, Fixture> fixtures = new LinkedHashMap<>();

    public byte surfaceAt(PlaneData plane, int x, int y) {
        Byte d = surface.get(xy(x, y));
        return d != null ? d : plane.terrainAt(x, y);
    }

    public Byte surfaceDelta(int x, int y) {
        return surface.get(xy(x, y));
    }

    public boolean isOpenUnder(int x, int y) {
        Byte d = under.get(xy(x, y));
        return d != null && d == UNDER_EMPTY;
    }

    public Fixture floorAt(int layer, int x, int y) {
        return floors.get(zxy(layer, x, y));
    }

    public Fixture fixtureAt(int layer, int x, int y) {
        return fixtures.get(zxy(layer, x, y));
    }

    public boolean hasLadder(int x, int y) {
        return fixtureAt(SURFACE, x, y) == Fixture.LADDER
                || fixtureAt(UNDER, x, y) == Fixture.LADDER;
    }

    public boolean inTown(PlaneData plane, int x, int y) {
        return plane.siteAt(x, y) != null;
    }

    public boolean blocks(PlaneData plane, int layer, int x, int y) {
        Fixture fix = fixtureAt(layer, x, y);
        if (fix == Fixture.WALL) {
            return true;
        }
        if (fix == Fixture.DOOR) {
            return false;
        }
        if (layer == UNDER) {
            return !isOpenUnder(x, y);
        }
        return Terrain.blocksMovement(surfaceAt(plane, x, y));
    }

    public float lightAt(PlaneData plane, int layer, int x, int y) {
        if (layer == SURFACE) {
            return 1f;
        }
        float light = 0f;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int sx = x + dx;
                int sy = y + dy;
                if (surfaceAt(plane, sx, sy) == Terrain.PIT) {
                    float d = (float) Math.hypot(dx, dy);
                    light = Math.max(light, (1f - d / PIT_LIGHT) * 0.55f);
                }
            }
        }
        for (Map.Entry<Long, Fixture> e : fixtures.entrySet()) {
            if (e.getValue() != Fixture.TORCH) {
                continue;
            }
            int[] p = unpackZxy(e.getKey());
            if (p[0] != UNDER && p[0] != SURFACE) {
                continue;
            }
            if (p[0] == SURFACE && surfaceAt(plane, p[1], p[2]) != Terrain.PIT) {
                continue;
            }
            float d = (float) Math.hypot(p[1] - x, p[2] - y);
            if (d <= TORCH_RADIUS) {
                light = Math.max(light, 1f - d / TORCH_RADIUS);
            }
        }
        return Math.max(0f, Math.min(1f, light));
    }

    public String apply(PlaneData plane, int layer, int tx, int ty) {
        if (inTown(plane, tx, ty)) {
            return "Village stays as it is — build in the wild.";
        }
        return switch (tool) {
            case CHOP -> chop(plane, layer, tx, ty);
            case DIG -> dig(plane, layer, tx, ty);
            case FLOOR -> placeFloor(plane, layer, tx, ty);
            case WALL -> placeFixture(plane, layer, tx, ty, Fixture.WALL, 2, 2);
            case DOOR -> placeFixture(plane, layer, tx, ty, Fixture.DOOR, 2, 0);
            case TORCH -> placeFixture(plane, layer, tx, ty, Fixture.TORCH, 1, 0);
            case LADDER -> placeLadder(plane, layer, tx, ty);
        };
    }

    public boolean tryClimb(PlaneData plane, float[] pos, int layer) {
        int px = (int) Math.floor(pos[0]);
        int py = (int) Math.floor(pos[1]);
        int dest = layer == SURFACE ? UNDER : SURFACE;
        int[][] spots = {
                {px, py}, {px + 1, py}, {px - 1, py}, {px, py + 1}, {px, py - 1}
        };
        for (int[] s : spots) {
            if (!hasLadder(s[0], s[1])) {
                continue;
            }
            if (dest == UNDER && !isOpenUnder(s[0], s[1])) {
                continue;
            }
            if (dest == SURFACE && blocks(plane, SURFACE, s[0], s[1])
                    && surfaceAt(plane, s[0], s[1]) != Terrain.PIT) {
                continue;
            }
            if (dest == SURFACE && fixtureAt(SURFACE, s[0], s[1]) == Fixture.WALL) {
                continue;
            }
            pos[0] = s[0] + 0.5f;
            pos[1] = s[1] + 0.5f;
            return true;
        }
        return false;
    }

    public String climbHint(int x, int y, int layer) {
        if (!hasLadder(x, y) && !hasLadder(x + 1, y) && !hasLadder(x - 1, y)
                && !hasLadder(x, y + 1) && !hasLadder(x, y - 1)) {
            return "";
        }
        return layer == SURFACE ? "E / Space: climb down" : "E / Space: climb up";
    }

    public void setSurface(int x, int y, byte terrain) {
        surface.put(xy(x, y), terrain);
    }

    public void setUnderEmpty(int x, int y) {
        under.put(xy(x, y), UNDER_EMPTY);
    }

    public void setFloor(int layer, int x, int y, Fixture floor) {
        if (floor == null) {
            floors.remove(zxy(layer, x, y));
        } else {
            floors.put(zxy(layer, x, y), floor);
        }
    }

    public void setFixture(int layer, int x, int y, Fixture fixture) {
        if (fixture == null) {
            fixtures.remove(zxy(layer, x, y));
        } else {
            fixtures.put(zxy(layer, x, y), fixture);
        }
    }

    public List<int[]> copySurfaceEdits() {
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<Long, Byte> e : surface.entrySet()) {
            int[] p = unpackXy(e.getKey());
            out.add(new int[] {p[0], p[1], e.getValue() & 0xff});
        }
        return out;
    }

    public List<int[]> copyUnderCells() {
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<Long, Byte> e : under.entrySet()) {
            int[] p = unpackXy(e.getKey());
            out.add(new int[] {p[0], p[1], e.getValue() & 0xff});
        }
        return out;
    }

    public List<int[]> copyBuilds() {
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<Long, Fixture> e : floors.entrySet()) {
            int[] p = unpackZxy(e.getKey());
            out.add(new int[] {p[0], p[1], p[2], e.getValue().ordinal()});
        }
        for (Map.Entry<Long, Fixture> e : fixtures.entrySet()) {
            int[] p = unpackZxy(e.getKey());
            out.add(new int[] {p[0], p[1], p[2], e.getValue().ordinal()});
        }
        return out;
    }

    public void loadSurfaceEdit(int x, int y, int terrain) {
        surface.put(xy(x, y), (byte) terrain);
    }

    public void loadUnderCell(int x, int y, int kind) {
        under.put(xy(x, y), (byte) kind);
    }

    public void loadBuild(int layer, int x, int y, int kindOrdinal) {
        Fixture f = Fixture.values()[kindOrdinal];
        if (f == Fixture.FLOOR) {
            floors.put(zxy(layer, x, y), f);
        } else {
            fixtures.put(zxy(layer, x, y), f);
        }
    }

    public static int[] aimTile(float playerX, float playerY, int mouseX, int mouseY) {
        int px = (int) Math.floor(playerX);
        int py = (int) Math.floor(playerY);
        int dx = mouseX - px;
        int dy = mouseY - py;
        if (Math.max(Math.abs(dx), Math.abs(dy)) > REACH) {
            dx = clamp(dx, -REACH, REACH);
            dy = clamp(dy, -REACH, REACH);
        }
        return new int[] {px + dx, py + dy};
    }

    private String chop(PlaneData plane, int layer, int tx, int ty) {
        Fixture fix = fixtureAt(layer, tx, ty);
        if (fix != null) {
            fixtures.remove(zxy(layer, tx, ty));
            if (fix == Fixture.LADDER) {
                fixtures.remove(zxy(SURFACE, tx, ty));
                fixtures.remove(zxy(UNDER, tx, ty));
            }
            refund(fix);
            return "Removed " + fix.name().toLowerCase() + refundText(fix);
        }
        Fixture floor = floorAt(layer, tx, ty);
        if (floor != null) {
            floors.remove(zxy(layer, tx, ty));
            dirt += 1;
            return "Pulled up a floor  +1 dirt";
        }
        if (layer == UNDER) {
            return "Nothing to chop here.";
        }
        byte t = surfaceAt(plane, tx, ty);
        if (t == Terrain.TREE) {
            setSurface(tx, ty, Terrain.STUMP);
            wood += 3;
            return "Chopped a tree  +3 wood";
        }
        if (t == Terrain.STUMP) {
            setSurface(tx, ty, Terrain.GRASS);
            wood += 1;
            return "Cleared a stump  +1 wood";
        }
        if (t == Terrain.ROCK || t == Terrain.RIDGE) {
            setSurface(tx, ty, Terrain.GRASS);
            stone += 2;
            return "Broke rock  +2 stone";
        }
        return "Nothing to harvest.";
    }

    private String dig(PlaneData plane, int layer, int tx, int ty) {
        if (layer == SURFACE) {
            byte t = surfaceAt(plane, tx, ty);
            if (t == Terrain.PIT) {
                setUnderEmpty(tx, ty);
                return "Pit already open below.";
            }
            if (Terrain.isHarvestable(t) && t != Terrain.STUMP) {
                return "Chop that first.";
            }
            if (!Terrain.canDigSurface(t)) {
                return "Can't dig here.";
            }
            if (fixtureAt(SURFACE, tx, ty) == Fixture.WALL) {
                return "Remove the wall first.";
            }
            setSurface(tx, ty, Terrain.PIT);
            setUnderEmpty(tx, ty);
            dirt += 1;
            return "Dug a pit  +1 dirt";
        }
        if (isOpenUnder(tx, ty)) {
            return "Already excavated.";
        }
        if (!canExpandUnder(tx, ty, plane)) {
            return "Dig next to an open hole.";
        }
        setUnderEmpty(tx, ty);
        dirt += 1;
        return "Excavated  +1 dirt";
    }

    private boolean canExpandUnder(int tx, int ty, PlaneData plane) {
        int[][] n = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : n) {
            int x = tx + d[0];
            int y = ty + d[1];
            if (isOpenUnder(x, y) || surfaceAt(plane, x, y) == Terrain.PIT) {
                return true;
            }
        }
        return false;
    }

    private String placeFloor(PlaneData plane, int layer, int tx, int ty) {
        if (!canStand(plane, layer, tx, ty)) {
            return layer == UNDER ? "Excavate first." : "Need open ground.";
        }
        if (floorAt(layer, tx, ty) != null) {
            return "Floor already here.";
        }
        if (wood < 1 && dirt < 1) {
            return "Need 1 wood or 1 dirt.";
        }
        if (dirt >= 1 && (layer == UNDER || wood < 1)) {
            dirt -= 1;
        } else {
            wood -= 1;
        }
        setFloor(layer, tx, ty, Fixture.FLOOR);
        return "Placed a floor.";
    }

    private String placeFixture(PlaneData plane, int layer, int tx, int ty, Fixture kind,
                                int woodCost, int stoneCost) {
        if (!canStand(plane, layer, tx, ty) && kind != Fixture.WALL) {
            return layer == UNDER ? "Excavate first." : "Need open ground.";
        }
        if (kind == Fixture.WALL && layer == UNDER && !isOpenUnder(tx, ty)
                && !canExpandUnder(tx, ty, plane)) {
            return "Excavate first.";
        }
        if (kind == Fixture.WALL && layer == SURFACE && Terrain.blocksMovement(surfaceAt(plane, tx, ty))) {
            return "Clear this tile first.";
        }
        Fixture existing = fixtureAt(layer, tx, ty);
        if (existing != null) {
            return "Already built here. Chop to remove.";
        }
        if (kind == Fixture.WALL && isOpenUnder(tx, ty) && layer == UNDER) {
            // placing a wall in an open cell is fine
        }
        if (wood < woodCost && (stoneCost == 0 || stone < stoneCost)) {
            if (stoneCost > 0) {
                return "Need " + woodCost + " wood or " + stoneCost + " stone.";
            }
            return "Need " + woodCost + " wood.";
        }
        if (wood >= woodCost) {
            wood -= woodCost;
        } else {
            stone -= stoneCost;
        }
        if (kind == Fixture.WALL && layer == UNDER && !isOpenUnder(tx, ty)) {
            setUnderEmpty(tx, ty);
        }
        setFixture(layer, tx, ty, kind);
        return "Placed a " + kind.name().toLowerCase() + ".";
    }

    private String placeLadder(PlaneData plane, int layer, int tx, int ty) {
        if (hasLadder(tx, ty)) {
            return "Ladder already here.";
        }
        if (wood < 2) {
            return "Need 2 wood for a ladder.";
        }
        if (layer == SURFACE) {
            byte t = surfaceAt(plane, tx, ty);
            if (t != Terrain.PIT) {
                return "Dig a pit first, then place the ladder.";
            }
        } else if (!isOpenUnder(tx, ty)) {
            return "Excavate first.";
        }
        wood -= 2;
        setFixture(SURFACE, tx, ty, Fixture.LADDER);
        setFixture(UNDER, tx, ty, Fixture.LADDER);
        if (surfaceAt(plane, tx, ty) != Terrain.PIT) {
            setSurface(tx, ty, Terrain.PIT);
        }
        setUnderEmpty(tx, ty);
        return "Placed a ladder. Stand on it and press E.";
    }

    private boolean canStand(PlaneData plane, int layer, int tx, int ty) {
        if (layer == UNDER) {
            return isOpenUnder(tx, ty);
        }
        byte t = surfaceAt(plane, tx, ty);
        return !Terrain.blocksMovement(t) || t == Terrain.PIT;
    }

    private void refund(Fixture fix) {
        switch (fix) {
            case WALL -> wood += 2;
            case DOOR, LADDER -> wood += 2;
            case TORCH -> wood += 1;
            case FLOOR -> dirt += 1;
        }
    }

    private static String refundText(Fixture fix) {
        return switch (fix) {
            case WALL, DOOR, LADDER -> "  +2 wood";
            case TORCH -> "  +1 wood";
            case FLOOR -> "  +1 dirt";
        };
    }

    public static long xy(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    public static long zxy(int layer, int x, int y) {
        return ((long) (layer + 8) << 48) | ((long) (x & 0xffffff) << 24) | (y & 0xffffff);
    }

    public static int[] unpackXy(long k) {
        return new int[] {(int) (k >> 32), (int) k};
    }

    public static int[] unpackZxy(long k) {
        int layer = (int) (k >> 48) - 8;
        int x = (int) ((k >> 24) & 0xffffff);
        int y = (int) (k & 0xffffff);
        return new int[] {layer, x, y};
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
