package forge.adventure.scene;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.data.AdventureEventData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.stage.GameHUD;
import forge.adventure.util.AdventureEventController;
import forge.adventure.util.Config;
import forge.adventure.util.Controls;
import forge.adventure.util.Current;
import forge.model.CardBlock;

/**
 * Scene for the Inn in towns
 */
public class InnScene extends UIScene {
    private static InnScene object;
    private static int localObjectId;
    private static String localPointOfInterestId;
    private static AdventureEventData localEvent;
    Scene lastGameScene;
    public static InnScene instance() {
        return instance(null, "", null, -1);
    }

    public static InnScene instance(Scene lastGameScene, String pointOfInterestId, PointOfInterestChanges localChanges, int objectId){
        if(object==null)
            object=new InnScene();

        changes = localChanges;
        localPointOfInterestId = pointOfInterestId;
        localObjectId = objectId;
        if (lastGameScene != null)
            object.lastGameScene=lastGameScene;
        initLocalEvent();

        return object;
    }


    TextraButton tempHitPointCost, sell, leave, event, travel, rest;
    Image healIcon, sellIcon, leaveIcon;
    private TextraLabel playerGold,playerShards,eventDescription;

    private InnScene() {

        super(Forge.isLandscapeMode() ? "ui/inn.json" : "ui/inn_portrait.json");
        tempHitPointCost = ui.findActor("tempHitPointCost");
        ui.onButtonPress("done", InnScene.this::done);
        ui.onButtonPress("tempHitPointCost", InnScene.this::potionOfFalseLife);
        ui.onButtonPress("sell", InnScene.this::sell);
        leave = ui.findActor("done");
        sell = ui.findActor("sell");
        playerGold = Controls.newAccountingLabel(ui.findActor("playerGold"), false);
        playerShards = Controls.newAccountingLabel(ui.findActor("playerShards"),true);

        leaveIcon = ui.findActor("leaveIcon");
        healIcon = ui.findActor("healIcon");
        sellIcon = ui.findActor("sellIcon");

        event = ui.findActor("event");
        eventDescription = ui.findActor("eventDescription");

        ui.onButtonPress("event", InnScene.this::startEvent);

        travel = ui.findActor("travel");
        if (travel != null) {
            ui.onButtonPress("travel", InnScene.this::openTravel);
        }
        // SN1: Rest until morning (Ascendant-only, code-added so stock inn.json stays unchanged).
        if (Config.ascendant()) {
            addAscendantRestButton();
        }
    }

    private void addAscendantRestButton() {
        if (ui.findActor("rest") != null) {
            rest = ui.findActor("rest");
            ui.onButtonPress("rest", InnScene.this::restUntilMorning);
            rest.setVisible(true);
            refreshRestButton();
            return;
        }
        rest = Controls.newTextButton("Rest", InnScene.this::restUntilMorning);
        rest.setName("rest");
        float[] b = restStageBounds(Forge.isLandscapeMode(), ui.getHeight());
        rest.setBounds(b[0], b[1], b[2], b[3]);
        ui.addActor(rest);
        addToSelectable(rest);
        refreshRestButton();
    }

    private void refreshRestButton() {
        if (rest == null || !Config.ascendant())
            return;
        var season = Current.player().getSeason();
        if (season == null || !season.isEnabled()) {
            rest.setVisible(false);
            return;
        }
        rest.setVisible(true);
        int cost = season.restGoldCost();
        boolean can = season.canRestToday();
        if (!can) {
            rest.setText("Rested");
            rest.setDisabled(true);
        } else {
            rest.setText(cost > 0 ? "Rest (" + cost + "g)" : "Rest");
            rest.setDisabled(false);
        }
    }

    /**
     * Stage-space (y-up) Rest button bounds for the inn layouts.
     * Landscape: left of Leave. Portrait: above Leave.
     */
    public static float[] restStageBounds(boolean landscape, float layoutH) {
        if (landscape) {
            // Right of Back (done at x=5,w=48,y=235) — json (58, 235, 56, 30).
            float jx = 58f, jy = 235f, jw = 56f, jh = 30f;
            return new float[] { jx, layoutH - jy - jh, jw, jh };
        }
        // Above Back (done at x=165,y=385), right of leaveIcon — json (165, 350, 100, 30).
        float jx = 165f, jy = 350f, jw = 100f, jh = 30f;
        return new float[] { jx, layoutH - jy - jh, jw, jh };
    }

    private void restUntilMorning() {
        if (!Config.ascendant())
            return;
        Current.player().restSeasonUntilMorning();
        refreshRestButton();
        refreshStatus();
    }



    public void done() {
        GameHUD.getInstance().getTouchpad().setVisible(false);
        Forge.switchScene(lastGameScene==null?GameScene.instance():lastGameScene);
    }

    public void potionOfFalseLife() {
        if (Current.player().potionOfFalseLife()){
            refreshStatus();
        }
    }

    @Override
    public void act(float delta) {
        stage.act(delta);
    }


    @Override
    public void render() {
        super.render();
    }

    int tempHealthCost = 0;
    static PointOfInterestChanges changes;

    @Override
    public void enter() {
        super.enter();
        refreshRestButton();
        refreshStatus();
        GameHUD.getInstance().updateBGM();
    }

    private void refreshStatus(){
        float townPriceModifier = changes == null ? 1f : changes.getTownPriceModifier();
        tempHealthCost = Math.round(Current.player().falseLifeCost() * townPriceModifier);
        boolean purchaseable = Current.player().getMaxLife() == Current.player().getLife() &&
                tempHealthCost <= Current.player().getGold();

        tempHitPointCost.setDisabled(!purchaseable);
        tempHitPointCost.setText("[+GoldCoin] " + tempHealthCost);

        if (travel != null) {
            boolean showTravel = Config.ascendant();
            travel.setVisible(showTravel);
            if (showTravel) {
                boolean unlocked = Current.player().getSkills().canUseWaypointTravel();
                travel.setDisabled(!unlocked);
                travel.setText(unlocked ? "Travel" : "Travel (Exp "
                        + Config.instance().getConfigData().waypointTravelUnlockLevel + ")");
            }
        }

        initLocalEvent();
        if (localEvent == null){
            eventDescription.setText("[GREY]No events at this time");
            event.setDisabled(true);
        }
        else{
            event.setDisabled(false);
            switch (localEvent.eventStatus){
                case Available:
                    eventDescription.setText(localEvent.format.toString() + " available");
                    break;
                case Entered:
                    eventDescription.setText(localEvent.format.toString() + " [GREEN]entered");
                    break;
                case Ready:
                    eventDescription.setText(localEvent.format.toString() + " [GREEN]ready");
                    break;
                case Started:
                    eventDescription.setText(localEvent.format.toString() + " [GREEN]in progress");
                    break;
                case Completed:
                    eventDescription.setText(localEvent.format.toString() + " [GREEN]rewards available");
                    break;
                case Awarded:
                    eventDescription.setText(localEvent.format.toString() + " complete");
                    break;
                case Abandoned:
                    eventDescription.setText(localEvent.format.toString() + " [RED]abandoned");
                    event.setDisabled(true);
                    break;
            }
        }
    }

    private void openTravel() {
        if (!Config.ascendant())
            return;
        if (Current.player() != null && Current.player().isOverloaded()) {
            try {
                forge.adventure.stage.GameHUD.getInstance().addNotification(
                        "Overloaded — clear Overflow before waypoint travel.");
            } catch (Exception ignored) {
                // HUD may be unavailable
            }
            return;
        }
        Forge.switchScene(WaypointTravelScene.instance().prepare(localPointOfInterestId));
    }

    private void sell() {
        ShopScene scene = ShopScene.instance(getUIBackground());
        scene.loadChanges(changes);
        Forge.switchScene(scene);
    }

    private static void initLocalEvent() {
        localEvent = null;
        for (AdventureEventData data :  AdventurePlayer.current().getEvents()){
            if (data.sourceID.equals(localPointOfInterestId) && data.eventOrigin == localObjectId){
                localEvent = data;
                return;
            }
        }
        AdventureEventController controller = AdventureEventController.instance();
        localEvent = controller.createEvent(localPointOfInterestId);
        if(localEvent != null)
            controller.initializeEvent(localEvent, localPointOfInterestId, localObjectId, changes);
    }

    public static void replaceLocalEvent(AdventureEventController.EventFormat format, CardBlock cardBlock) {
        AdventurePlayer.current().getEvents().removeIf((data) -> data.sourceID.equals(localPointOfInterestId) && data.eventOrigin == localObjectId);
        AdventureEventController controller = AdventureEventController.instance();
        localEvent = controller.createEvent(format, cardBlock, localPointOfInterestId);
        if(localEvent != null)
            controller.initializeEvent(localEvent, localPointOfInterestId, localObjectId, changes);
    }

    private void startEvent(){

        Forge.switchScene(EventScene.instance(this, localEvent, changes), true);

    }

}
