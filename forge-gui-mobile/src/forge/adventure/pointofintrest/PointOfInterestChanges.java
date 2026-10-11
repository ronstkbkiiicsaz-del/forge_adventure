package forge.adventure.pointofintrest;

import forge.adventure.util.Current;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

/**
 * Class to save point of interest changes, like sold cards and dead enemies.
 * EC1 also stores per-town card market supply (optional keys; old saves load empty).
 */
public class PointOfInterestChanges implements SaveFileContent  {
    private final HashSet<Integer> deletedObjects=new HashSet<>();
    private final HashMap<Integer, HashSet<Integer>> cardsBought = new HashMap<>();
    private final java.util.Map<String, Byte> mapFlags = new HashMap<>();
    private final java.util.Map<Integer, Long> shopSeeds = new HashMap<>();
    //private final java.util.Map<Integer, Float> shopModifiers = new HashMap<>();
    private final java.util.Map<Integer, Integer> reputation = new HashMap<>();
    /** EC1: card name → supply pressure (positive = cheaper here). */
    private final java.util.Map<String, Float> marketSupply = new HashMap<>();
    /** EC1: last game-day when market drift was applied. */
    private double marketDriftDay;
    /** EC1: town color lean (W/U/B/R/G or biome name); null = none. */
    private String marketColorLean;
    private Boolean isBookmarked;
    private Boolean isVisited;

    public static class Map extends HashMap<String,PointOfInterestChanges> implements SaveFileContent {
        @Override
        public void load(SaveFileData data) {
            this.clear();
            if(data==null || !data.containsKey("keys")) return;

            String[] keys= (String[]) data.readObject("keys");
            for(int i=0;i<keys.length;i++) {
                SaveFileData elementData = data.readSubData("value_"+i);
                PointOfInterestChanges newChanges=new PointOfInterestChanges();
                newChanges.load(elementData);
                this.put(keys[i],newChanges);
            }
        }

        @Override
        public SaveFileData save() {
            SaveFileData data=new SaveFileData();
            ArrayList<String> keys=new ArrayList<>();
            ArrayList<PointOfInterestChanges> items=new ArrayList<>();
            for (java.util.Map.Entry<String,PointOfInterestChanges> entry : this.entrySet()) {
                keys.add(entry.getKey());
                items.add(entry.getValue());
            }
            data.storeObject("keys",keys.toArray(new String[0]));
            for(int i=0;i<items.size();i++)
                data.store("value_"+i,items.get(i).save());
            return data;
        }
    }

    @Override
    public void load(SaveFileData data) {
        deletedObjects.clear();
        deletedObjects.addAll((HashSet<Integer>) data.readObject("deletedObjects"));
        cardsBought.clear();
        cardsBought.putAll((HashMap<Integer, HashSet<Integer>>) data.readObject("cardsBought"));
        shopSeeds.clear();
        shopSeeds.putAll((java.util.Map<Integer, Long>) data.readObject("shopSeeds"));
        mapFlags.clear();
        mapFlags.putAll((java.util.Map<String, Byte>) data.readObject("mapFlags"));
        reputation.clear();
        if (data.containsKey("reputation")) {
            reputation.putAll((java.util.Map<Integer, Integer>) data.readObject("reputation"));
        }
        marketSupply.clear();
        marketDriftDay = 0;
        marketColorLean = null;
        if (data.containsKey("ec1MarketNames") && data.containsKey("ec1MarketSupply")) {
            Object rawNames = data.readObject("ec1MarketNames");
            Object rawSupply = data.readObject("ec1MarketSupply");
            if (rawNames instanceof String[] names && rawSupply instanceof float[] supply) {
                int n = Math.min(names.length, supply.length);
                for (int i = 0; i < n; i++) {
                    if (names[i] != null && !names[i].isEmpty())
                        marketSupply.put(names[i], supply[i]);
                }
            }
        }
        if (data.containsKey("ec1MarketDriftDay"))
            marketDriftDay = data.readFloat("ec1MarketDriftDay");
        if (data.containsKey("ec1MarketColorLean"))
            marketColorLean = data.readString("ec1MarketColorLean");
        isBookmarked = (Boolean) data.readObject("isBookmarked");
        isVisited = (Boolean) data.readObject("isVisited");
    }

    @Override
    public SaveFileData save() {
        SaveFileData data=new SaveFileData();
        data.storeObject("deletedObjects",deletedObjects);
        data.storeObject("cardsBought",cardsBought);
        data.storeObject("mapFlags", mapFlags);
        data.storeObject("shopSeeds", shopSeeds);
        data.storeObject("reputation", reputation);
        if (!marketSupply.isEmpty()) {
            String[] names = new String[marketSupply.size()];
            float[] supply = new float[marketSupply.size()];
            int i = 0;
            for (java.util.Map.Entry<String, Float> e : marketSupply.entrySet()) {
                names[i] = e.getKey();
                supply[i] = e.getValue() == null ? 0f : e.getValue();
                i++;
            }
            data.storeObject("ec1MarketNames", names);
            data.storeObject("ec1MarketSupply", supply);
        }
        data.store("ec1MarketDriftDay", (float) marketDriftDay);
        if (marketColorLean != null)
            data.store("ec1MarketColorLean", marketColorLean);
        data.storeObject("isBookmarked", isBookmarked);
        data.storeObject("isVisited", isVisited);
        return data;
    }

    public boolean isObjectDeleted(int objectID) { return deletedObjects.contains(objectID); }
    public boolean deleteObject(int objectID)    { return deletedObjects.add(objectID); }

    public java.util.Map<String, Byte> getMapFlags() {
        return mapFlags;
    }

    public void buyCard(int objectID, int cardIndex) {
        if( !cardsBought.containsKey(objectID)) {
            cardsBought.put(objectID,new HashSet<>());
        }
        cardsBought.get(objectID).add(cardIndex);
    }
    public boolean wasCardBought(int objectID, int cardIndex) {
        if( !cardsBought.containsKey(objectID)) {
            return false;
        }
        return cardsBought.get(objectID).contains(cardIndex);
    }

    public long getShopSeed(int objectID){
        if (!shopSeeds.containsKey(objectID))
        {
            generateNewShopSeed(objectID);
        }
        return shopSeeds.get(objectID);
    }

    public void generateNewShopSeed(int objectID){
        shopSeeds.put(objectID, shopSeeds.containsKey(objectID)? new Random(shopSeeds.get(objectID)).nextLong() : Current.world().getRandom().nextLong());
        cardsBought.put(objectID, new HashSet<>()); //Allows cards to appear in slots of previous purchases
    }

    public void setRotatingShopSeed(int objectID, long seed){
        if (shopSeeds.containsKey(objectID) && shopSeeds.get(objectID) != seed) {
            cardsBought.put(objectID, new HashSet<>()); //Allows cards to appear in slots of previous purchases
        }
        shopSeeds.put(objectID, seed);
    }

    public float getShopPriceModifier(int objectID){
        int shopRep = reputation.getOrDefault(objectID, 0);

        shopRep = Integer.min(maxRepToApply, (Integer.max(-maxRepToApply, shopRep)));

        return 1.0f + (shopRep * priceModifierPerRep);
    }

    int maxRepToApply = 20;
    float priceModifierPerRep = 0.005f;

    public float getTownPriceModifier(){
        int townRep = reputation.getOrDefault(0, 0);

        townRep = Integer.min(maxRepToApply, (Integer.max(-maxRepToApply, townRep)));

        return 1.0f - Math.round((priceModifierPerRep * townRep) * 1000)/1000f;
    }

    public void addMapReputation(int delta)
    {
        addObjectReputation(0, delta);
    }

    public void addObjectReputation(int id, int delta)
    {
        reputation.merge(id, delta, Integer::sum);
    }

    public int getMapReputation()
    {
        return getObjectReputation(0);
    }

    public int getObjectReputation(int id)
    {
        return reputation.getOrDefault(id, 0);
    }
    public boolean hasDeletedObjects() {
        return deletedObjects != null && !deletedObjects.isEmpty();
    }
    public boolean isBookmarked() {
        if (isBookmarked == null)
            return false;
        return isBookmarked;
    }
    public void setIsBookmarked(boolean val) {
        isBookmarked = val;
    }

    public void clearDeletedObjects() {
        // reset map when assigning as a quest target that needs enemies
        deletedObjects.clear();
    }
    public boolean isVisited() {
        if (isVisited ==null)
            return false;
        return isVisited;
    }
    public void visit() {
        boolean was = isVisited != null && isVisited;
        isVisited = true;
        if (!was) {
            try {
                forge.adventure.world.WorldSave.getCurrentSave().notePoiDiscovered();
            } catch (Exception ignored) {
                // Headless / no live save.
            }
            try {
                forge.adventure.stage.GameHUD hud = forge.adventure.stage.GameHUD.instance;
                if (hud != null) {
                    hud.updateTrackedQuestHud();
                }
            } catch (Exception ignored) {
                // HUD not built in tests.
            }
        }
    }

    // ---- EC1 living local markets ---------------------------------------------------------------

    public void setMarketColorLean(String lean) {
        this.marketColorLean = lean;
    }

    public String getMarketColorLean() {
        return marketColorLean;
    }

    public float getMarketSupply(String cardName) {
        if (cardName == null)
            return 0f;
        return marketSupply.getOrDefault(cardName, 0f);
    }

    public void addMarketSupply(String cardName, float delta) {
        if (cardName == null || delta == 0f)
            return;
        float next = marketSupply.getOrDefault(cardName, 0f) + delta;
        if (Math.abs(next) < 0.0001f)
            marketSupply.remove(cardName);
        else
            marketSupply.put(cardName, next);
    }

    public void clearMarketSupply() {
        marketSupply.clear();
        marketDriftDay = 0;
    }

    /**
     * Price multiplier from local supply. Positive supply → cheaper; negative → pricier.
     * Bounded to {@code [bandMin, bandMax]} around 1.0.
     */
    public float marketPriceMultiplier(String cardName, float bandMin, float bandMax, float pressureScale) {
        float supply = getMarketSupply(cardName);
        float raw = 1f / (1f + supply * Math.max(0.0001f, pressureScale));
        float min = Math.min(bandMin, bandMax);
        float max = Math.max(bandMin, bandMax);
        return Math.max(min, Math.min(max, raw));
    }

    /**
     * Drift each card's supply toward 0 over in-game days.
     * {@code driftPerDay} is the fraction of remaining pressure removed per day (0–1).
     */
    public void driftMarketsTowardBase(double currentGameDays, float driftPerDay) {
        if (marketSupply.isEmpty() || driftPerDay <= 0f)
            return;
        double days = currentGameDays - marketDriftDay;
        if (days <= 0) {
            marketDriftDay = currentGameDays;
            return;
        }
        float keep = (float) Math.pow(1.0 - Math.min(0.99, driftPerDay), days);
        java.util.Iterator<java.util.Map.Entry<String, Float>> it = marketSupply.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, Float> e = it.next();
            float next = e.getValue() * keep;
            if (Math.abs(next) < 0.0001f)
                it.remove();
            else
                e.setValue(next);
        }
        marketDriftDay = currentGameDays;
    }
}
