package forge.ascendant.people;

import forge.ascendant.world.PlaneData;
import forge.ascendant.world.Site;
import forge.ascendant.world.Terrain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Havenbrook (and the plains market, when that town is stamped) townsfolk.
 * They walk a short loop, pause, and idle. One or two travellers use the road
 * only when both towns and a carved road are present.
 */
public final class TownsfolkSim {
    private static final float SPEED = 1.5f;
    private static final String[] ROLE_ORDER = {
            "innkeeper", "shopkeep", "smith", "priest", "child", "coachman",
            "elder", "miller", "stablehand", "farmer", "merchant"
    };
    private static final String[] EXTRA_NAMES = {"Ned", "Pia", "Cal", "Lise", "Otto", "Nell", "Ivo", "Mara"};
    private static final int[][] EXTRA_OFFSETS = {
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 1}, {-3, 1}, {2, -2}, {-1, 3}, {4, 0}, {-4, 1}
    };

    public final List<Person> people;

    public TownsfolkSim(List<Person> people) {
        this.people = people;
    }

    public static TownsfolkSim place(PlaneData plane, Path mapsRoot) throws IOException {
        Site home = null;
        Site plains = null;
        for (Site site : plane.sites) {
            if (site.home) {
                home = site;
            } else if ("town".equals(site.kind) && plains == null) {
                plains = site;
            }
        }
        List<Person> people = new ArrayList<>();
        if (home != null) {
            List<FolkSpot> spots = TownFolkMap.parse(mapsRoot.resolve(home.tmxRelative), home.skipRowsTop);
            spots = fillToward(plane, home, spots, 8);
            addTown(people, plane, home, cap(spots, 10));
        }
        if (plains != null) {
            List<FolkSpot> spots = TownFolkMap.parse(mapsRoot.resolve(plains.tmxRelative), plains.skipRowsTop);
            boolean hasNpcSpots = !spots.isEmpty();
            if (hasNpcSpots) {
                addTown(people, plane, plains, prefer(spots, 8));
            }
        }
        if (home != null && plains != null) {
            addTravellers(people, plane, home, plains);
        }
        return new TownsfolkSim(people);
    }

    public void update(float dt, PlaneData plane) {
        for (Person person : people) {
            if (person.bubble > 0) {
                person.bubble -= dt;
            }
            if (person.pause > 0) {
                person.pause -= dt;
                person.walking = false;
                continue;
            }
            if (person.wx.length < 2) {
                person.walking = false;
                continue;
            }
            float tx = person.wx[person.waypoint];
            float ty = person.wy[person.waypoint];
            float dx = tx - person.x;
            float dy = ty - person.y;
            float dist = (float) Math.hypot(dx, dy);
            if (dist < 0.08f) {
                person.x = tx;
                person.y = ty;
                person.waypoint = (person.waypoint + 1) % person.wx.length;
                person.pause = 0.9f + (person.name.hashCode() & 7) * 0.18f;
                person.walking = false;
                continue;
            }
            person.walking = true;
            person.facing = facing(dx, dy);
            person.anim += dt;
            float step = Math.min(dist, SPEED * dt);
            float nx = person.x + dx / dist * step;
            float ny = person.y + dy / dist * step;
            if (blocked(plane, nx, ny)) {
                person.waypoint = (person.waypoint + 1) % person.wx.length;
                person.pause = 0.7f;
                person.walking = false;
                continue;
            }
            person.x = nx;
            person.y = ny;
        }
    }

    /** One-line bubble when the click lands on someone. Returns that person, or null. */
    public Person click(float worldX, float worldY) {
        Person best = null;
        float bestD = 1.15f;
        for (Person person : people) {
            float d = (float) Math.hypot(person.x - worldX, person.y - worldY);
            if (d <= bestD) {
                bestD = d;
                best = person;
            }
        }
        if (best != null) {
            best.bubble = 3.2f;
        }
        return best;
    }

    private static void addTown(List<Person> people, PlaneData plane, Site site, List<FolkSpot> spots) {
        for (int i = 0; i < spots.size(); i++) {
            FolkSpot spot = spots.get(i);
            float[] route = route(plane, site, spots, i);
            if (route == null) {
                continue;
            }
            String name = displayName(spot, i);
            String sprite = spriteFor(spot.role, i);
            people.add(new Person(name, lineFor(spot), sprite, false, route));
        }
    }

    private static float[] route(PlaneData plane, Site site, List<FolkSpot> spots, int index) {
        FolkSpot home = spots.get(index);
        float hx = site.x + home.localX + 0.5f;
        float hy = site.y + home.localY + 0.5f;
        if (blocked(plane, hx, hy)) {
            float[] nudged = nudge(plane, hx, hy);
            if (nudged == null) {
                return null;
            }
            hx = nudged[0];
            hy = nudged[1];
        }
        List<Float> xs = new ArrayList<>();
        List<Float> ys = new ArrayList<>();
        xs.add(hx);
        ys.add(hy);
        for (int j = 0; j < spots.size() && xs.size() < 3; j++) {
            if (j == index) {
                continue;
            }
            FolkSpot other = spots.get(j);
            float ox = site.x + other.localX + 0.5f;
            float oy = site.y + other.localY + 0.5f;
            float dist = (float) Math.hypot(ox - hx, oy - hy);
            if (dist < 1.2f || dist > 18f || blocked(plane, ox, oy)) {
                continue;
            }
            xs.add(ox);
            ys.add(oy);
        }
        if (xs.size() == 1) {
            float[] step = stepAway(plane, hx, hy);
            if (step != null) {
                xs.add(step[0]);
                ys.add(step[1]);
            }
        }
        float[] packed = new float[xs.size() * 2];
        for (int i = 0; i < xs.size(); i++) {
            packed[i * 2] = xs.get(i);
            packed[i * 2 + 1] = ys.get(i);
        }
        return packed;
    }

    private static void addTravellers(List<Person> people, PlaneData plane, Site home, Site plains) {
        int x0 = home.x + home.w / 2;
        int y0 = Math.max(1, home.y - 1);
        int x1 = plains.x + plains.w / 2;
        int y1 = plains.y + plains.h / 2;
        List<int[]> road = new ArrayList<>();
        int x = x0;
        int y = y0;
        while (true) {
            byte t = plane.terrainAt(x, y);
            if (t == Terrain.ROAD || t == Terrain.BRIDGE) {
                if (road.isEmpty() || road.get(road.size() - 1)[0] != x || road.get(road.size() - 1)[1] != y) {
                    road.add(new int[]{x, y});
                }
            }
            if (x == x1 && y == y1) {
                break;
            }
            if (x < x1) {
                x++;
            } else if (x > x1) {
                x--;
            }
            if (y < y1) {
                y++;
            } else if (y > y1) {
                y--;
            }
        }
        if (road.size() < 8) {
            return;
        }
        int stride = Math.max(1, road.size() / 14);
        List<Integer> picks = new ArrayList<>();
        for (int i = 0; i < road.size(); i += stride) {
            picks.add(i);
        }
        if (picks.get(picks.size() - 1) != road.size() - 1) {
            picks.add(road.size() - 1);
        }
        float[] forward = pack(road, picks);
        people.add(new Person("Jory", "Long road. Havenbrook one way, market the other.",
                "hunter", true, forward));
        if (picks.size() >= 4) {
            float[] back = new float[forward.length];
            for (int i = 0; i < picks.size(); i++) {
                int src = picks.size() - 1 - i;
                back[i * 2] = forward[src * 2];
                back[i * 2 + 1] = forward[src * 2 + 1];
            }
            Person second = new Person("Ansel", "I walk this road most days.", "inspector", true, back);
            second.waypoint = 0;
            people.add(second);
        }
    }

    private static float[] pack(List<int[]> road, List<Integer> picks) {
        float[] packed = new float[picks.size() * 2];
        for (int i = 0; i < picks.size(); i++) {
            int[] cell = road.get(picks.get(i));
            packed[i * 2] = cell[0] + 0.5f;
            packed[i * 2 + 1] = cell[1] + 0.5f;
        }
        return packed;
    }

    private static List<FolkSpot> fillToward(PlaneData plane, Site site, List<FolkSpot> spots, int target) {
        if (spots.size() >= 6) {
            return spots;
        }
        List<FolkSpot> out = new ArrayList<>(spots);
        int extra = 0;
        for (FolkSpot anchor : spots) {
            for (int[] off : EXTRA_OFFSETS) {
                if (out.size() >= target) {
                    return out;
                }
                int lx = anchor.localX + off[0];
                int ly = anchor.localY + off[1];
                if (lx < 0 || ly < 0 || lx >= site.w || ly >= site.h) {
                    continue;
                }
                if (occupied(out, lx, ly)) {
                    continue;
                }
                float wx = site.x + lx + 0.5f;
                float wy = site.y + ly + 0.5f;
                if (blocked(plane, wx, wy)) {
                    continue;
                }
                out.add(new FolkSpot("extra_" + extra, extraRole(extra), lx, ly));
                extra++;
            }
        }
        return out;
    }

    private static boolean occupied(List<FolkSpot> spots, int x, int y) {
        for (FolkSpot spot : spots) {
            if (Math.abs(spot.localX - x) + Math.abs(spot.localY - y) < 2) {
                return true;
            }
        }
        return false;
    }

    private static String extraRole(int i) {
        return switch (i % 4) {
            case 0 -> "merchant";
            case 1 -> "child";
            case 2 -> "farmer";
            default -> "elder";
        };
    }

    private static List<FolkSpot> prefer(List<FolkSpot> spots, int cap) {
        List<FolkSpot> picked = new ArrayList<>();
        boolean[] used = new boolean[spots.size()];
        for (String role : ROLE_ORDER) {
            for (int i = 0; i < spots.size() && picked.size() < cap; i++) {
                if (!used[i] && role.equals(spots.get(i).role)) {
                    used[i] = true;
                    picked.add(spots.get(i));
                    break;
                }
            }
        }
        for (int i = 0; i < spots.size() && picked.size() < cap; i++) {
            if (!used[i]) {
                picked.add(spots.get(i));
            }
        }
        return picked;
    }

    private static List<FolkSpot> cap(List<FolkSpot> spots, int max) {
        if (spots.size() <= max) {
            return spots;
        }
        return new ArrayList<>(spots.subList(0, max));
    }

    private static String displayName(FolkSpot spot, int index) {
        String id = spot.id.toLowerCase(Locale.ROOT);
        if (id.contains("mira")) {
            return "Mira";
        }
        if (id.contains("bren")) {
            return "Bren";
        }
        if (id.contains("sela")) {
            return "Sela";
        }
        if (id.startsWith("extra_")) {
            int n = 0;
            try {
                n = Integer.parseInt(id.substring("extra_".length()));
            } catch (NumberFormatException ignored) {
                n = index;
            }
            return EXTRA_NAMES[Math.floorMod(n, EXTRA_NAMES.length)];
        }
        String role = spot.role;
        if (role == null || role.isBlank()) {
            return "Villager";
        }
        return Character.toUpperCase(role.charAt(0)) + role.substring(1);
    }

    private static String lineFor(FolkSpot spot) {
        String id = spot.id.toLowerCase(Locale.ROOT);
        if (id.contains("mira")) {
            return "The mill is up the lane. Mind the carts.";
        }
        if (id.contains("bren")) {
            return "South road takes you to the market.";
        }
        if (id.contains("sela")) {
            return "The square is the place to stand and talk.";
        }
        return switch (spot.role) {
            case "innkeeper" -> "Rooms if you need them. Stew is on.";
            case "shopkeep" -> "Looking, or buying?";
            case "smith" -> "Hot iron. Don't lean on the bench.";
            case "farmer" -> "The fields are quiet. That's a good day.";
            case "priest" -> "The bell keeps its own time.";
            case "child" -> "Race you to the well.";
            case "coachman" -> "The coach leaves when the road is clear.";
            case "merchant" -> "Plains grain, if your purse is open.";
            case "elder" -> "We have always kept the market here.";
            case "miller" -> "Wind is up today.";
            case "stablehand" -> "The horses stay. You can walk.";
            default -> "Fine day for a walk.";
        };
    }

    static String spriteFor(String role, int index) {
        return switch (role) {
            case "innkeeper" -> "oldwoman";
            case "shopkeep" -> "villager2";
            case "smith" -> "villager";
            case "farmer" -> "villager3";
            case "priest" -> "monk";
            case "child" -> "child";
            case "coachman" -> "hunter";
            case "merchant" -> "noble";
            case "elder" -> "oldman";
            case "miller" -> "villager4";
            case "stablehand" -> "knight";
            case "mira" -> "woman";
            case "bren" -> "villager5";
            case "sela" -> "princess";
            default -> switch (index % 4) {
                case 0 -> "woman";
                case 1 -> "boy";
                case 2 -> "villager6";
                default -> "oldman2";
            };
        };
    }

    private static float[] nudge(PlaneData plane, float x, float y) {
        int[][] offs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {-1, 1}};
        for (int[] off : offs) {
            float nx = x + off[0];
            float ny = y + off[1];
            if (!blocked(plane, nx, ny)) {
                return new float[]{nx, ny};
            }
        }
        return null;
    }

    private static float[] stepAway(PlaneData plane, float x, float y) {
        int[][] offs = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 0}, {0, 3}};
        for (int[] off : offs) {
            float nx = x + off[0];
            float ny = y + off[1];
            if (!blocked(plane, nx, ny)) {
                return new float[]{nx, ny};
            }
        }
        return null;
    }

    private static boolean blocked(PlaneData plane, float x, float y) {
        return plane.blocks((int) Math.floor(x), (int) Math.floor(y));
    }

    private static int facing(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx < 0 ? 2 : 3;
        }
        return dy < 0 ? 0 : 1;
    }

    public static final class Person {
        public final String name;
        public final String line;
        public final String sprite;
        public final boolean traveller;
        final float[] wx;
        final float[] wy;
        public float x;
        public float y;
        int waypoint;
        float pause;
        public boolean walking;
        /** 0 down, 1 up, 2 left, 3 right. */
        public int facing;
        public float anim;
        public float bubble;

        Person(String name, String line, String sprite, boolean traveller, float[] packed) {
            this.name = name;
            this.line = line;
            this.sprite = sprite;
            this.traveller = traveller;
            int n = packed.length / 2;
            this.wx = new float[n];
            this.wy = new float[n];
            for (int i = 0; i < n; i++) {
                wx[i] = packed[i * 2];
                wy[i] = packed[i * 2 + 1];
            }
            this.x = wx[0];
            this.y = wy[0];
            this.pause = 0.4f + (name.hashCode() & 5) * 0.15f;
        }
    }
}
