package forge.adventure.util;

import forge.adventure.pointofintrest.PointOfInterest;

/**
 * QL1: result of resolving where the navigation arrow / map marker / HUD line should point
 * for the currently tracked quest.
 */
public final class QuestNavigationTarget {
    public final PointOfInterest poi;
    /** True when Ascendant "any side quest" has no discovered QuestSource/Sidequest. */
    public final boolean noKnownLocation;
    public final String hudLine;

    private QuestNavigationTarget(PointOfInterest poi, boolean noKnownLocation, String hudLine) {
        this.poi = poi;
        this.noKnownLocation = noKnownLocation;
        this.hudLine = hudLine == null ? "" : hudLine;
    }

    public static QuestNavigationTarget ofPoi(PointOfInterest poi, String questName) {
        String name = poi != null ? poi.getDisplayName() : "";
        String line = questName == null || questName.isEmpty()
                ? name
                : (name == null || name.isEmpty() ? questName : questName + " — " + name);
        return new QuestNavigationTarget(poi, false, line);
    }

    public static QuestNavigationTarget noKnownLocation(String questName, String message) {
        String msg = message == null || message.isEmpty() ? "no known location" : message;
        String line = questName == null || questName.isEmpty() ? msg : questName + " — " + msg;
        return new QuestNavigationTarget(null, true, line);
    }

    public static QuestNavigationTarget none() {
        return new QuestNavigationTarget(null, false, "");
    }

    public boolean hasPoi() {
        return poi != null;
    }
}
