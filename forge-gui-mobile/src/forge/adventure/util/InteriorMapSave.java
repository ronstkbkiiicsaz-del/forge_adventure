package forge.adventure.util;

import forge.adventure.character.PlayerSprite;
import forge.adventure.coop.CoopSession;
import forge.adventure.data.ConfigData;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.scene.GameScene;
import forge.adventure.scene.Scene;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;
import forge.adventure.world.WorldSave;

/**
 * SV1: Ascendant save/load of the player's position inside a POI map (dungeon/town),
 * plus the shared pre-duel autosave hook.
 *
 * <p>Interior keys are written only into the WorldSave player blob (see
 * {@link WorldSave#save} / {@link WorldSave#load}), never into co-op {@code .chr}
 * files that call {@link forge.adventure.player.AdventurePlayer#save()} directly.
 *
 * <p>Old saves without the interior keys load on the world map as before.
 * Stock modes never write or restore these fields.
 */
public final class InteriorMapSave {
    public static final String KEY_POI_ID = "interiorPoiId";
    public static final String KEY_MAP_PATH = "interiorMapPath";
    public static final String KEY_ENTRANCE_ID = "interiorEntranceId";
    public static final String KEY_POS_X = "interiorPosX";
    public static final String KEY_POS_Y = "interiorPosY";
    public static final String KEY_MUST_CLEAR = "interiorMustClearOnExit";

    private static String pendingPoiId;
    private static String pendingMapPath;
    private static int pendingEntranceId;
    private static float pendingPosX;
    private static float pendingPosY;
    private static boolean pendingMustClear;
    private static boolean pending;

    private InteriorMapSave() {
    }

    public static boolean saveAnywhereEnabled() {
        if (!Config.ascendant()) {
            return false;
        }
        ConfigData cfg = Config.instance().getConfigData();
        return cfg == null || cfg.sv1SaveAnywhere;
    }

    public static boolean autosaveBeforeFightEnabled() {
        if (!Config.ascendant()) {
            return false;
        }
        ConfigData cfg = Config.instance().getConfigData();
        return cfg == null || cfg.sv1AutosaveBeforeFight;
    }

    /**
     * Manual save is allowed on Ascendant everywhere except during a dialog
     * (and shops/duels, which are other scenes — the pause menu is not open there).
     * Stock modes stay world-map only.
     */
    public static boolean allowsManualSave() {
        MapStage map = MapStage.instance;
        if (!saveAnywhereEnabled()) {
            return map == null || !map.isInMap();
        }
        return map == null || !map.isDialogOnlyInput();
    }

    /**
     * Persist interior location into the WorldSave player blob when in a map.
     * Must only be called from {@link WorldSave#save}, never from co-op {@code .chr} paths.
     */
    public static void write(SaveFileData data) {
        if (data == null || !saveAnywhereEnabled()) {
            return;
        }
        // Use the live instance only — never construct MapStage from a headless save.
        MapStage map = MapStage.instance;
        if (map == null || !map.isInMap()) {
            return;
        }
        TileMapScene scene = TileMapScene.instance();
        if (scene.rootPoint == null) {
            return;
        }
        PlayerSprite player = map.getPlayerSprite();
        if (player == null) {
            return;
        }
        data.store(KEY_POI_ID, scene.rootPoint.getID());
        String mapPath = map.getLoadedMapPath();
        if (mapPath == null || mapPath.isEmpty()) {
            mapPath = scene.getCurrentMapPath();
        }
        data.store(KEY_MAP_PATH, mapPath != null ? mapPath : "");
        data.store(KEY_ENTRANCE_ID, map.getLastSpawnTargetId());
        data.store(KEY_POS_X, player.getX());
        data.store(KEY_POS_Y, player.getY());
        data.store(KEY_MUST_CLEAR, map.isMustClearOnExit());
    }

    /**
     * Read interior keys from a WorldSave player blob after a successful load.
     * Missing keys mean world-map spawn. Must only be called from {@link WorldSave#load}.
     */
    public static void readPending(SaveFileData data) {
        clearPending();
        if (data == null || !data.containsKey(KEY_POI_ID)) {
            return;
        }
        String poiId = data.readString(KEY_POI_ID);
        if (poiId == null || poiId.isEmpty()) {
            return;
        }
        pendingPoiId = poiId;
        pendingMapPath = data.containsKey(KEY_MAP_PATH) ? data.readString(KEY_MAP_PATH) : "";
        pendingEntranceId = data.containsKey(KEY_ENTRANCE_ID) ? data.readInt(KEY_ENTRANCE_ID) : 0;
        pendingPosX = data.containsKey(KEY_POS_X) ? data.readFloat(KEY_POS_X) : Float.NaN;
        pendingPosY = data.containsKey(KEY_POS_Y) ? data.readFloat(KEY_POS_Y) : Float.NaN;
        pendingMustClear = data.containsKey(KEY_MUST_CLEAR) && data.readBool(KEY_MUST_CLEAR);
        pending = true;
    }

    public static void clearPending() {
        pending = false;
        pendingPoiId = null;
        pendingMapPath = null;
        pendingEntranceId = 0;
        pendingPosX = Float.NaN;
        pendingPosY = Float.NaN;
        pendingMustClear = false;
    }

    public static boolean hasPending() {
        return pending;
    }

    /**
     * After a successful {@link WorldSave#load}, return the scene to enter:
     * the restored map when interior fields are present, otherwise the overworld.
     */
    public static Scene sceneAfterLoad() {
        if (prepareRestoredMap()) {
            return TileMapScene.instance();
        }
        return GameScene.instance();
    }

    /**
     * Load the saved POI/map into {@link TileMapScene} and place the player.
     * Does not switch scenes — caller should {@code Forge.switchScene} the result of
     * {@link #sceneAfterLoad()}. Quest ENTERPOI fires once from {@link TileMapScene#enter()}.
     *
     * <p>If the saved TMX is missing or fails to load, retries the POI's root map at its
     * entrance. If that also fails, clears pending and returns false (overworld).
     *
     * @return true when the map was prepared
     */
    public static boolean prepareRestoredMap() {
        if (!pending || !saveAnywhereEnabled()) {
            clearPending();
            return false;
        }
        PointOfInterest poi = findPoiById(pendingPoiId);
        if (poi == null || poi.getData() == null) {
            clearPending();
            return false;
        }
        String mapPath = pendingMapPath;
        int entranceId = pendingEntranceId;
        float posX = pendingPosX;
        float posY = pendingPosY;
        boolean mustClear = pendingMustClear;
        clearPending();

        TileMapScene scene = TileMapScene.instance();
        try {
            scene.loadForRestore(poi, mapPath, entranceId);
            MapStage stage = MapStage.getInstance();
            stage.setMustClearOnExit(mustClear);
            stage.tryRestorePlayerPosition(posX, posY);
            return true;
        } catch (Exception e) {
            System.err.println("SV1: failed to restore map \"" + mapPath + "\": " + e.getMessage());
            try {
                String rootMap = poi.getData().map;
                scene.loadForRestore(poi, rootMap, 0);
                MapStage.getInstance().setMustClearOnExit(mustClear);
                // Leave the player at the root entrance (do not apply the saved position).
                return true;
            } catch (Exception e2) {
                System.err.println("SV1: root map also failed: " + e2.getMessage());
                try {
                    MapStage.getInstance().clearIsInMap();
                } catch (Exception ignored) {
                }
                return false;
            }
        }
    }

    /**
     * SV1 autosave into the rotating auto slot right before a duel starts.
     * Ascendant only; skipped during any active co-op session. Does not change
     * the co-op guest/host save path itself.
     */
    public static void maybeAutosaveBeforeDuel() {
        if (!autosaveBeforeFightEnabled()) {
            return;
        }
        if (CoopSession.get().isActive()) {
            return;
        }
        WorldSave current = WorldSave.getCurrentSave();
        if (current == null) {
            return;
        }
        current.autoSave();
    }

    static PointOfInterest findPoiById(String poiId) {
        if (poiId == null || poiId.isEmpty()) {
            return null;
        }
        WorldSave save = WorldSave.getCurrentSave();
        if (save == null || save.getWorld() == null) {
            return null;
        }
        for (PointOfInterest poi : save.getWorld().getAllPointOfInterest()) {
            if (poi != null && poiId.equals(poi.getID())) {
                return poi;
            }
        }
        return null;
    }

    /** Test helper: expose pending POI id after {@link #readPending}. */
    public static String pendingPoiIdForTest() {
        return pendingPoiId;
    }
}
