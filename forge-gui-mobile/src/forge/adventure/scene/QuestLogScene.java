package forge.adventure.scene;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TypingLabel;
import forge.Adventure;
import forge.Forge;
import forge.adventure.data.AdventureQuestData;
import forge.adventure.data.AdventureQuestStage;
import forge.adventure.stage.MapStage;
import forge.adventure.util.AdventureQuestController;
import forge.adventure.util.Controls;
import forge.adventure.util.Current;

import java.util.ArrayList;
import java.util.List;

public class QuestLogScene extends UIScene {
    private Table scrollContainer, detailScrollContainer;
    Window scrollWindow;
    ScrollPane scroller,detailScroller;
    Table root, detailRoot;
    TextraButton trackButton, backToListButton, abandonQuestButton;
    Scene lastGameScene;
    /** Row Track/Details buttons currently on the list (for selectable rebuild). */
    private final List<Button> listRowButtons = new ArrayList<>();

    private QuestLogScene() {
        super(Forge.isLandscapeMode() ? "ui/quests.json" : "ui/quests_portrait.json");

        scrollWindow = ui.findActor("scrollWindow");
        root = ui.findActor("questList");
        detailRoot = ui.findActor("questDetails");
        abandonQuestButton = Controls.newTextButton("Abandon Quest");
        trackButton = Controls.newTextButton("Track Quest");
        backToListButton = Controls.newTextButton("Quest List");
        ui.onButtonPress("return", QuestLogScene.this::back);
        ui.onButtonPress("status", QuestLogScene.this::status);
        ui.onButtonPress("skills", () -> Forge.switchScene(SkillsScene.instance(lastGameScene), true));
        ui.onButtonPress("achievements", () -> Forge.switchScene(AchievementsScene.instance(lastGameScene), true));
        if (!forge.adventure.util.Config.ascendant() && ui.findActor("skills") != null)
            ui.findActor("skills").setVisible(false); // skills are a Shandalar Ascendant feature
        if (ui.findActor("achievements") != null)
            ui.findActor("achievements").setVisible(forge.adventure.util.Config.ascendant());
        backToListButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                buildList();
            }
        });

        // TODO - refactor below, replace buttons in landscape
        scrollContainer = new Table(Controls.getSkin());
        scrollContainer.row();

        detailScrollContainer = new Table(Controls.getSkin());
        detailScrollContainer.row();

        detailScroller = new ScrollPane(detailScrollContainer);
        detailScroller.setScrollingDisabled(true,false);
        if (Forge.isLandscapeMode()) {
            detailRoot.add(abandonQuestButton).fillX().top().padTop(5f);
            detailRoot.add(trackButton).fillX().top().padTop(5f);
            detailRoot.add(backToListButton).fillX().top().padTop(5f);
        } else {
            detailRoot.add(abandonQuestButton).fillX().top().padTop(5f).colspan(3);
            detailRoot.row();
            detailRoot.add(trackButton).fillX().top().padTop(5f).colspan(3);
            detailRoot.row();
            detailRoot.add(backToListButton).fillX().top().padTop(5f).colspan(3);
        }

        detailRoot.row();
        detailRoot.add(detailScroller).colspan(3).expandX().fillX().expandY();
        detailRoot.row();
        scrollWindow.setTouchable(Touchable.disabled);
        detailRoot.setVisible(false);
        scroller = new ScrollPane(scrollContainer);
        root.add(scroller).colspan(3);
        root.align(Align.right);
        root.row();
        Label column0Label = new Label(Forge.getLocalizer().getMessage("lblQuestName"), Controls.getSkin());
        column0Label.setColor(Color.BLACK);
        root.add(column0Label).align(Align.bottomLeft);
        root.row();
        ScrollPane scroller = new ScrollPane(scrollContainer);
        root.add(scroller).colspan(3).fill().expand();
    }

    private static QuestLogScene object;

    public static QuestLogScene instance(Scene lastGameScene) {
        //if (object == null)
            object = new QuestLogScene();
        if (lastGameScene != null)
            object.lastGameScene=lastGameScene;
        return object;
    }

    @Override
    public void dispose() { }

    @Override
    public void enter() {
        super.enter();
        buildList();
    }

    public void buildList(){
        backToList();

        scrollContainer.clear();
        listRowButtons.clear();

        for (AdventureQuestData quest : Current.player().getQuests()) {
            String headerCode = quest.isTracked ? "{GRADIENT=CYAN;BLUE;1;1}•{ENDGRADIENT}[BLACK]" : "[BLACK]";
            TypingLabel nameLabel = Controls.newTypingLabel(headerCode + quest.getName());
            nameLabel.skipToTheEnd();
            nameLabel.setWrap(true);
            scrollContainer.add(nameLabel).align(Align.left).expandX();

            // QL1: Track reachable from the quest list (controller + mouse), not only details.
            String trackLabel = quest.isTracked
                    ? Forge.getLocalizer().getMessage("lblUntrackQuest")
                    : Forge.getLocalizer().getMessageorUseDefault("lblTrack", "Track");
            Button track = Controls.newTextButton(trackLabel);
            track.addListener(new ClickListener() {
                public void clicked(InputEvent event, float x, float y) {
                    toggleTracked(quest);
                    buildList();
                }
            });
            scrollContainer.add(track).align(Align.center).padRight(6);

            Button details = Controls.newTextButton(Forge.getLocalizer().getMessage("lblDetails"));
            details.addListener(new ClickListener() {
                public void clicked(InputEvent event, float x, float y){
                    loadDetailsPane(quest);
                }
            });
            scrollContainer.add(details).align(Align.center).padRight(10);
            scrollContainer.row().padTop(5);
            listRowButtons.add(track);
            listRowButtons.add(details);
        }
        rebuildListSelectables();
        performTouch(scrollPaneOfActor(scrollContainer)); //can use mouse wheel if available to scroll
    }

    private void backToList() {
        abandonQuestButton.setVisible(false);
        trackButton.setVisible(false);
        backToListButton.setVisible(false);

        root.setVisible(true);
        detailRoot.setVisible(false);
    }

    private void loadDetailsPane(AdventureQuestData quest) {
        if (quest == null) {
            return;
        }
        root.setVisible(false);
        detailRoot.setVisible(true);
        detailScrollContainer.clear();
        detailScrollContainer.row();
        trackButton.clearListeners();
        trackButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                toggleTracked(quest);
                updateTrackButton(quest.isTracked);
            }
        });

        abandonQuestButton.setColor(Color.RED);
        abandonQuestButton.clearListeners();
        abandonQuestButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                // Adventure scenes: use scene/MapStage dialogs — FOptionPane does not draw here.
                Dialog confirm = createGenericDialog("", Forge.getLocalizer().getMessage("lblAbandonQuestConfirm"),Forge.getLocalizer().getMessage("lblYes"),Forge.getLocalizer().getMessage("lblNo"), () -> abandonQuest(quest), null);
                showDialog(confirm);
            }
        });

        TypingLabel dNameLabel = Controls.newTypingLabel(quest.getName());
        dNameLabel.skipToTheEnd();
        dNameLabel.setWrap(true);
        dNameLabel.setColor(Color.BLACK);

        detailScrollContainer.add(dNameLabel).align(Align.left).expandX().padLeft(10);

        abandonQuestButton.setVisible(!quest.storyQuest);
        trackButton.setVisible(true);
        backToListButton.setVisible(true);

        TypingLabel dDescriptionLabel = Controls.newTypingLabel(quest.getDescription());
        dDescriptionLabel.skipToTheEnd();
        dDescriptionLabel.setWrap(true);
        dDescriptionLabel.setColor(Color.DARK_GRAY);

        detailScrollContainer.row();
        detailScrollContainer.add(dDescriptionLabel).align(Align.left).padLeft(25).width(detailRoot.getWidth() -25);

        for (AdventureQuestStage stage : quest.getCompletedStages()) {
            // Completed Stages will have Checkbox unicode
            TypingLabel completeLabel = Controls.newTypingLabel("{GRADIENT=OLIVE;LIME;1;1}\u2611 " + stage.name + "{ENDGRADIENT}[BLACK]");
            completeLabel.skipToTheEnd();
            completeLabel.setWrap(true);
            detailScrollContainer.row();
            detailScrollContainer.add(completeLabel).align(Align.left).padLeft(25);
        }

        for (AdventureQuestStage stage : quest.getActiveStages()) {
            // Active Stages will have Blank box unicode
            TypingLabel activeLabel = Controls.newTypingLabel("\u2610  " + stage.name);
            activeLabel.skipToTheEnd();
            activeLabel.setColor(Color.BLACK);
            activeLabel.setWrap(true);
            detailScrollContainer.row();
            detailScrollContainer.add(activeLabel).align(Align.left).padLeft(25);

            TypingLabel activeDescriptionLabel = Controls.newTypingLabel(stage.description);
            activeDescriptionLabel.skipToTheEnd();
            activeDescriptionLabel.setColor(Color.DARK_GRAY);
            activeDescriptionLabel.setWrap(true);

            detailScrollContainer.row();
            detailScrollContainer.add(activeDescriptionLabel).padLeft(35).width(detailRoot.getWidth() - 50);
            detailScrollContainer.row();
        }
        updateTrackButton(quest.isTracked);
        rebuildDetailsSelectables();
    }

    /** Re-add Return/Status/Skills/Achievements then list row buttons (AchievementsScene pattern). */
    private void rebuildListSelectables() {
        fillSelectables(getPossibleSelection(), navActors(), listRowButtons);
    }

    /** Clear stale list/detail selectables; nav + visible detail buttons only. */
    private void rebuildDetailsSelectables() {
        List<Actor> detailBtns = new ArrayList<>();
        detailBtns.add(trackButton);
        detailBtns.add(backToListButton);
        detailBtns.add(abandonQuestButton);
        fillSelectables(getPossibleSelection(), navActors(), detailBtns);
    }

    private Actor[] navActors() {
        return new Actor[]{
                ui.findActor("return"),
                ui.findActor("status"),
                ui.findActor("skills"),
                ui.findActor("achievements")
        };
    }

    /**
     * Rebuild the controller selectable set: visible nav buttons first, then visible content
     * buttons. Used by {@link #rebuildListSelectables}/{@link #rebuildDetailsSelectables} and
     * covered by unit tests with real Actor visibility.
     */
    public static void fillSelectables(Array<Selectable> dest, Actor[] navButtons,
                                Iterable<? extends Actor> contentButtons) {
        dest.clear();
        if (navButtons != null) {
            for (Actor a : navButtons) {
                if (a != null && a.isVisible()) {
                    dest.add(new Selectable(a));
                }
            }
        }
        if (contentButtons != null) {
            for (Actor a : contentButtons) {
                if (a != null && a.isVisible()) {
                    dest.add(new Selectable(a));
                }
            }
        }
    }

    /** Test helper: actors currently in the controller selectable set, in order. */
    List<Actor> selectableActorsForTest() {
        List<Actor> out = new ArrayList<>();
        for (Selectable s : getPossibleSelection()) {
            if (s != null && s.actor != null) {
                out.add(s.actor);
            }
        }
        return out;
    }

    private void toggleTracked(AdventureQuestData quest) {
        if (quest.isTracked) {
            quest.isTracked = false;
            AdventureQuestController.notifyTrackedQuestHud();
        } else {
            AdventureQuestController.trackQuest(quest);
        }
        updateTrackButton(quest.isTracked);
    }

    private void updateTrackButton(boolean isTracked) {
        if (isTracked) {
            trackButton.setText(Forge.getLocalizer().getMessage("lblUntrackQuest"));
        } else {
            trackButton.setText(Forge.getLocalizer().getMessage("lblTrackQuest"));
        }
    }

    private void status() {
        Forge.switchScene(PlayerStatisticScene.instance(lastGameScene),true);
    }

    @Override
    public boolean back(){
        Adventure.getInstance().renderTransitionScreen = true;
        //Needed so long as quest log and stats are separate scenes that link to each other
        Forge.switchScene(lastGameScene==null ? GameScene.instance() : lastGameScene);
        return true;
    }

    private void abandonQuest(AdventureQuestData quest) {
        AdventureQuestController.instance().abandon(quest);
        AdventureQuestController.instance().showQuestDialogs(MapStage.getInstance());
        buildList();
    }
}
