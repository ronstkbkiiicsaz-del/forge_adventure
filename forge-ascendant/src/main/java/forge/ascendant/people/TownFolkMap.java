package forge.ascendant.people;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads {@code npc_spot} objects and Havenbrook {@code townsfolk} template instances.
 * Does not move tiles. Y is flipped to match {@code TmxStamp} / the walk stamp
 * (TMX row 0 is north; world y grows south-to-north).
 */
public final class TownFolkMap {
    private TownFolkMap() {}

    public static List<FolkSpot> parse(Path tmx, int skipRowsTop) throws IOException {
        Document doc;
        try (InputStream in = Files.newInputStream(tmx)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            doc = factory.newDocumentBuilder().parse(in);
        } catch (Exception e) {
            throw new IOException("Failed to parse townsfolk from " + tmx + ": " + e.getMessage(), e);
        }
        Element map = doc.getDocumentElement();
        int height = Integer.parseInt(map.getAttribute("height"));
        int tileW = Integer.parseInt(map.getAttribute("tilewidth"));
        int tileH = Integer.parseInt(map.getAttribute("tileheight"));
        List<FolkSpot> spots = new ArrayList<>();
        NodeList objects = map.getElementsByTagName("object");
        for (int i = 0; i < objects.getLength(); i++) {
            Node node = objects.item(i);
            if (!(node instanceof Element object)) {
                continue;
            }
            if (object.getParentNode() instanceof Element parent && "template".equals(parent.getTagName())) {
                continue;
            }
            String type = object.getAttribute("type");
            String name = object.getAttribute("name");
            String townsfolkId = property(object, "townsfolkId");
            String roleProp = property(object, "role");
            boolean npcSpot = "npc_spot".equals(type);
            boolean townsfolk = townsfolkId != null && !townsfolkId.isBlank();
            if (!npcSpot && !townsfolk) {
                continue;
            }
            if (!object.hasAttribute("x") || !object.hasAttribute("y")) {
                continue;
            }
            int localX = (int) Math.floor(Float.parseFloat(object.getAttribute("x")) / tileW);
            int rowFromTop = (int) Math.floor(Float.parseFloat(object.getAttribute("y")) / tileH);
            if (rowFromTop < skipRowsTop || rowFromTop >= height || localX < 0) {
                continue;
            }
            int localY = height - 1 - rowFromTop;
            String id = townsfolk ? townsfolkId : (name == null || name.isBlank() ? type : name);
            String role = roleProp != null && !roleProp.isBlank()
                    ? roleProp
                    : roleFromId(id);
            spots.add(new FolkSpot(id, role, localX, localY));
        }
        return spots;
    }

    private static String roleFromId(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        int us = lower.lastIndexOf('_');
        String tail = us >= 0 ? lower.substring(us + 1) : lower;
        if (tail.startsWith("npc")) {
            tail = tail.substring(3);
        }
        return tail.isBlank() ? "villager" : tail;
    }

    private static String property(Element object, String key) {
        NodeList props = object.getElementsByTagName("property");
        for (int i = 0; i < props.getLength(); i++) {
            Node node = props.item(i);
            if (!(node instanceof Element prop)) {
                continue;
            }
            if (key.equals(prop.getAttribute("name"))) {
                return prop.getAttribute("value");
            }
        }
        return null;
    }
}
