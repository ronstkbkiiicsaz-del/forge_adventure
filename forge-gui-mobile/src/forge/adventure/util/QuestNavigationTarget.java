package forge.adventure.util;

import forge.Forge;
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

    /** Localized "no known location" line for the HUD / map. */
    public static QuestNavigationTarget noKnownLocation(String questName) {
        return noKnownLocation(questName, localizedNoKnownLocation());
    }

    public static QuestNavigationTarget noKnownLocation(String questName, String message) {
        String msg = message == null || message.isEmpty() ? localizedNoKnownLocation() : message;
        String line = questName == null || questName.isEmpty() ? msg : questName + " — " + msg;
        return new QuestNavigationTarget(null, true, line);
    }

    public static String localizedNoKnownLocation() {
        String fallback = "no known location";
        try {
            if (Config.instance() != null && Config.instance().getConfigData() != null
                    && Config.instance().getConfigData().ql1NoKnownLocationMessage != null
                    && !Config.instance().getConfigData().ql1NoKnownLocationMessage.isEmpty()
                    && !"no known location".equals(Config.instance().getConfigData().ql1NoKnownLocationMessage)) {
                // Explicit non-default config override (tests / tunables).
                return Config.instance().getConfigData().ql1NoKnownLocationMessage;
            }
        } catch (Exception ignored) {
            // Config not ready.
        }
        try {
            return Forge.getLocalizer().getMessageorUseDefault("lblNoKnownLocation", fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static QuestNavigationTarget none() {
        return new QuestNavigationTarget(null, false, "");
    }

    public boolean hasPoi() {
        return poi != null;
    }
}
