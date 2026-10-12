package forge.app;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scripted battlefield window. No Forge rules, stack, mana, or AI.
 *
 * <p>Hover the hand to read it. Inside the hand, a drag reorders. Past the cast line, the card
 * leaves the pointer: a targeted spell becomes a stack-log entry (full text, then one click
 * assigns a target), and a land or creature lands on its row. Another instant can join the
 * stack before the first resolves. Resolve takes only the top spell. Click a stack entry to
 * read it while it is live. Drag a creature into the opponent's half to attack. Drag one of
 * yours onto their attacker to block. Click Activate, read the preview, then Confirm.
 * N starts a new turn. R resets. Esc cancels the top stack entry, or quits when the stack
 * is empty. 1 is your attack, 2 is their attack.
 */
public final class BattlefieldPrototype extends ApplicationAdapter {
    private static final Color TABLE = new Color(0.09f, 0.11f, 0.12f, 1f);
    private static final Color YOU_TINT = new Color(0.20f, 0.32f, 0.28f, 1f);
    private static final Color THEM_TINT = new Color(0.32f, 0.18f, 0.18f, 1f);
    private static final Color LINE_DIM = new Color(1f, 1f, 1f, 0.22f);
    private static final Color LINE_HOT = new Color(0.55f, 0.85f, 1f, 0.9f);
    private static final Color GREEN = new Color(0.45f, 0.95f, 0.45f, 1f);
    private static final Color RED = new Color(1f, 0.38f, 0.32f, 1f);
    private static final Color GOLD = new Color(1f, 0.86f, 0.45f, 1f);
    private static final Color ARROW = new Color(0.95f, 0.82f, 0.25f, 0.95f);

    private enum Phase { MAIN, ATTACKERS, BLOCKERS }
    private enum Modal { NONE, CHOOSE_TYPE, ABILITY, SHOCK }

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private BitmapFont big;
    private GlyphLayout glyphs;
    private Texture pixel;
    private final List<Texture> art = new ArrayList<>();
    private final List<Perm> board = new ArrayList<>();
    private final List<HandCard> hand = new ArrayList<>();
    private final List<StackItem> stack = new ArrayList<>();
    private final List<UiButton> buttons = new ArrayList<>();
    private UiButton youBadge = new UiButton("you-life", "", 0, 0, 1, 1);
    private UiButton themBadge = new UiButton("them-life", "", 0, 0, 1, 1);

    private Phase phase = Phase.MAIN;
    private Modal modal = Modal.NONE;
    private int yourLife = 20, theirLife = 20, yourGy, theirGy, artCursor;
    private int hover = -1, pressHand = -1, examining = -1;
    private boolean draggingHand, dragInHand;
    private float pressX, pressY, pressTime, dragDist;
    private Perm pressedPerm;
    private boolean draggingBoard;
    private Perm selected;
    private HandCard choiceHand;
    private Perm choicePerm;
    private Perm shockHover, shockLock;
    private boolean shockHoverYou, shockHoverThem, shockLockYou, shockLockThem;
    private String abilityLine = "", note = "";
    private float stackBottomY;
    private float mx, my;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration.useGlfwAsync();
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Battlefield prototype");
        cfg.setWindowedMode(1920, 1080);
        cfg.setResizable(true);
        cfg.useVsync(true);
        new Lwjgl3Application(new BattlefieldPrototype(), cfg);
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(1.15f);
        big = new BitmapFont();
        big.getData().setScale(1.8f);
        glyphs = new GlyphLayout();
        loadArt();
        resetAll();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    if (modal != Modal.NONE) cancelModal();
                    else if (!stack.isEmpty()) cancelTop();
                    else Gdx.app.exit();
                    return true;
                }
                if (keycode == Input.Keys.R) { resetAll(); return true; }
                if (keycode == Input.Keys.N) { newTurn(); return true; }
                if (keycode == Input.Keys.NUM_1 || keycode == Input.Keys.NUMPAD_1) { yourAttack(); return true; }
                if (keycode == Input.Keys.NUM_2 || keycode == Input.Keys.NUMPAD_2) { theirAttack(); return true; }
                if (keycode == Input.Keys.ENTER) {
                    if (modal == Modal.ABILITY) confirmModal();
                    else if (topHasTarget()) resolveShock();
                    else if (modal == Modal.NONE && examining < 0 && hover >= 0) play(live().get(hover));
                    return true;
                }
                return false;
            }
            @Override public boolean touchDown(int sx, int sy, int pointer, int button) {
                if (button != Input.Buttons.LEFT) {
                    if (modal != Modal.NONE) cancelModal();
                    else if (!stack.isEmpty()) cancelTop();
                    return true;
                }
                readPointer(sx, sy);
                UiButton hit = hitButton(mx, my);
                if (hit != null) { onButton(hit.id); return true; }
                if (hitStack(mx, my)) return true;
                int handIndex = pickHand(mx, my);
                if (handIndex >= 0 && modal == Modal.NONE) {
                    if (examining >= 0) examining = -1;
                    pressHand = handIndex;
                    pressedPerm = null;
                    pressX = mx; pressY = my; pressTime = 0; dragDist = 0;
                    draggingHand = false;
                    dragInHand = false;
                    hover = handIndex;
                    return true;
                }
                if (examining >= 0) {
                    examining = -1;
                    note = targeting() ? BattlefieldLayout.TARGET_PROMPT : note;
                    return true;
                }
                if (targeting()) { clickShockTarget(); return true; }
                if (modal != Modal.NONE) return true;
                Perm perm = hitBoard(mx, my);
                if (perm != null) {
                    pressedPerm = perm;
                    pressHand = -1;
                    pressX = mx; pressY = my; pressTime = 0; dragDist = 0;
                    draggingBoard = false;
                    return true;
                }
                if (phase == Phase.ATTACKERS && selected != null && themBadge.contains(mx, my)) {
                    tryAttack(selected);
                }
                return true;
            }
            @Override public boolean touchDragged(int sx, int sy, int pointer) {
                readPointer(sx, sy);
                dragDist = Math.max(dragDist, dist(pressX, pressY, mx, my));
                if (!HandLayout.isDrag(dragDist)) return true;
                if (pressHand >= 0 && modal == Modal.NONE) {
                    if (!draggingHand) dragInHand = true;
                    draggingHand = true;
                    List<HandCard> live = live();
                    float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
                    dragInHand = HandLayout.pointerInHand(w, h, live.size(), hover, dragInHand, mx, my);
                    if (pressHand < live.size()) {
                        HandCard c = live.get(pressHand);
                        c.tcx = mx; c.tcy = my;
                        c.tw = HandLayout.hoverRaisedWidth(h);
                        c.th = HandLayout.hoverRaisedHeight(h);
                        c.trot = 0f;
                    }
                } else if (pressedPerm != null && modal == Modal.NONE) {
                    draggingBoard = true;
                }
                return true;
            }
            @Override public boolean touchUp(int sx, int sy, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;
                readPointer(sx, sy);
                dragDist = Math.max(dragDist, dist(pressX, pressY, mx, my));
                if (pressHand >= 0) {
                    List<HandCard> live = live();
                    if (draggingHand && pressHand < live.size()) {
                        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
                        boolean inHand = HandLayout.pointerInHand(w, h, live.size(), hover, dragInHand, mx, my);
                        if (inHand) {
                            int drop = HandLayout.reorderIndex(w, h, live.size(), pressHand, mx);
                            reorderLive(pressHand, drop);
                            hover = drop;
                        } else {
                            HandCard c = live.get(pressHand);
                            if (c.cy > HandLayout.castLineY(h)) play(c);
                        }
                    }
                } else if (pressedPerm != null) {
                    if (HandLayout.isTap(dragDist, pressTime)) clickBoard(pressedPerm);
                    else if (draggingBoard) releaseBoardDrag(pressedPerm);
                }
                draggingHand = false;
                dragInHand = false;
                draggingBoard = false;
                pressHand = -1;
                pressedPerm = null;
                return true;
            }
            @Override public boolean mouseMoved(int sx, int sy) {
                readPointer(sx, sy);
                if (draggingHand || pressHand >= 0 || modal == Modal.ABILITY) return false;
                List<HandCard> live = live();
                float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
                hover = HandLayout.hoverIndexFor(w, h, live.size(), hover, mx, my);
                return false;
            }
        });
    }

    private void resetAll() {
        board.clear();
        hand.clear();
        stack.clear();
        phase = Phase.MAIN;
        modal = Modal.NONE;
        examining = -1;
        yourLife = 20;
        theirLife = 20;
        yourGy = 0;
        theirGy = 0;
        hover = -1;
        pressHand = -1;
        draggingHand = false;
        dragInHand = false;
        draggingBoard = false;
        pressedPerm = null;
        selected = null;
        choiceHand = null;
        choicePerm = null;
        clearShockLock();
        shockHover = null;
        note = "Main. The board stays put until you play, tap, attack, or activate.";
        artCursor = 0;
        addLand(false, "Mountain");
        addLand(false, "Swamp");
        addCreature(false, "Ogre", 3, 3, false, 0, 0, null, null, false);
        addCreature(false, "Sprite", 1, 1, false, 0, 0, null, null, false);
        addLand(true, "Plains");
        addLand(true, "Island");
        Perm cavern = addLand(true, "Cavern of Souls");
        cavern.choiceCard = true;
        cavern.choice = null;
        addCreature(true, "Bear Cub", 2, 2, true, 0, 0, null, null, false);
        addCreature(true, "Growth Wolf", 2, 2, false, 2, 0, "+2/+2 Giant Growth", null, false);
        addCreature(true, "Cursed Knight", 4, 4, false, 0, 2, null, "-2/-2", false);
        addCreature(true, "Hero", 1, 1, false, 0, 0, null, null, false).counters = 3;
        addCreature(true, "Veteran", 3, 3, false, 0, 0, null, null, true);
        addHand("Forest", BattlefieldLayout.Kind.LAND, false, 0, 0, false);
        addHand("Grizzly", BattlefieldLayout.Kind.CREATURE, false, 2, 2, false);
        addHand("Cavern of Souls", BattlefieldLayout.Kind.LAND, true, 0, 0, false);
        addHand("Shock", BattlefieldLayout.Kind.SPELL, false, 0, 0, true);
        addHand("Shock", BattlefieldLayout.Kind.SPELL, false, 0, 0, true);
        layout(0f);
    }

    private Perm addLand(boolean you, String name) {
        Perm p = new Perm();
        p.you = you;
        p.name = name;
        p.kind = BattlefieldLayout.Kind.LAND;
        p.tex = nextArt();
        board.add(p);
        return p;
    }

    private Perm addCreature(boolean you, String name, int power, int toughness, boolean sick,
                              int buff, int debuff, String buffLine, String debuffLine, boolean ability) {
        Perm p = new Perm();
        p.you = you;
        p.name = name;
        p.kind = BattlefieldLayout.Kind.CREATURE;
        p.power = power;
        p.toughness = toughness;
        p.sick = sick;
        p.buff = buff;
        p.debuff = debuff;
        p.buffLine = buffLine;
        p.debuffLine = debuffLine;
        p.hasAbility = ability;
        p.tex = nextArt();
        board.add(p);
        return p;
    }

    private void addHand(String name, BattlefieldLayout.Kind kind, boolean choice, int power, int toughness,
                          boolean instant) {
        HandCard c = new HandCard();
        c.name = name;
        c.kind = kind;
        c.choice = choice;
        c.instant = instant;
        c.power = power;
        c.toughness = toughness;
        c.tex = nextArt();
        hand.add(c);
    }

    private Texture nextArt() {
        if (art.isEmpty()) return null;
        Texture t = art.get(Math.floorMod(artCursor, art.size()));
        artCursor++;
        return t;
    }

    private void loadArt() {
        for (Path path : HandPrototype.findCardImages(14)) {
            try {
                art.add(loadRounded(path));
            } catch (RuntimeException ignored) {
                /* grey card with a name is enough */
            }
        }
    }

    private static Texture loadRounded(Path file) {
        Pixmap src = new Pixmap(new FileHandle(file.toFile()));
        Pixmap pm = src;
        if (src.getFormat() != Pixmap.Format.RGBA8888) {
            pm = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
            pm.setBlending(Pixmap.Blending.None);
            pm.drawPixmap(src, 0, 0);
            src.dispose();
        }
        HandPrototype.roundCardCorners(pm);
        Texture tex = new Texture(pm, true);
        tex.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        pm.dispose();
        return tex;
    }

    private void newTurn() {
        cancelModal();
        while (!stack.isEmpty()) cancelTop();
        phase = Phase.MAIN;
        for (Perm p : board) {
            if (!p.alive) continue;
            p.tapped = false;
            p.sick = false;
            p.attacking = false;
            p.blocking = false;
            p.damage = 0;
        }
        note = "New turn. Summoning sickness cleared, permanents untapped.";
    }

    private void yourAttack() {
        if (modal != Modal.NONE) return;
        phase = Phase.ATTACKERS;
        for (Perm p : board) {
            p.blocking = false;
            if (!p.you) p.attacking = false;
        }
        note = "Your attack. Legal creatures are highlighted.";
    }

    private void theirAttack() {
        if (modal != Modal.NONE) return;
        Perm ogre = find("Ogre");
        if (ogre == null) {
            note = "Ogre is not on the board.";
            return;
        }
        phase = Phase.BLOCKERS;
        for (Perm p : board) {
            p.attacking = false;
            p.blocking = false;
        }
        ogre.attacking = true;
        note = "Their attack. Drag one creature onto the Ogre to block.";
    }

    private void play(HandCard c) {
        if (c == null || !c.alive || modal != Modal.NONE) return;
        draggingHand = false;
        dragInHand = false;
        pressHand = -1;
        hover = -1;
        BattlefieldLayout.Speed speed = c.instant ? BattlefieldLayout.Speed.INSTANT : BattlefieldLayout.Speed.SORCERY;
        if (!BattlefieldLayout.mayCast(speed, stack.size())) {
            note = c.name + " waits until the stack is empty.";
            return;
        }
        if (c.kind == BattlefieldLayout.Kind.SPELL || c.choice) {
            queue(c);
            return;
        }
        spawn(c, null);
    }

    /** The card leaves the pointer and becomes the top line of the stack log. */
    private void queue(HandCard c) {
        boolean needsTarget = c.kind == BattlefieldLayout.Kind.SPELL;
        BattlefieldLayout.StackEntry entry = BattlefieldLayout.castSpell(c.name, needsTarget);
        StackItem item = new StackItem();
        item.card = c;
        item.name = entry.name;
        item.fullText = entry.fullText;
        item.needsTarget = entry.needsTarget;
        item.needsChoice = c.choice && !needsTarget;
        c.alive = false;
        c.parked = false;
        stack.add(item);
        examining = -1;
        clearShockLock();
        shockHover = null;
        if (item.needsTarget) note = BattlefieldLayout.TARGET_PROMPT;
        else if (item.needsChoice) note = "Choose a creature type. It stays printed on the card.";
    }

    private void spawn(HandCard c, String type) {
        Perm p = new Perm();
        p.you = true;
        p.name = c.name;
        p.kind = c.kind == BattlefieldLayout.Kind.SPELL ? BattlefieldLayout.Kind.CREATURE : c.kind;
        p.power = c.power;
        p.toughness = c.toughness;
        p.sick = c.kind == BattlefieldLayout.Kind.CREATURE;
        p.choiceCard = c.choice;
        p.choice = type;
        p.tex = c.tex;
        p.cx = c.cx;
        p.cy = c.cy;
        p.w = c.w;
        p.h = c.h;
        p.thunk = 1f;
        board.add(p);
        c.alive = false;
        c.parked = false;
        note = p.sick ? "Played " + p.name + ". Summoning sickness: it cannot attack yet."
                : "Played " + p.name + ".";
    }

    private void choose(String type) {
        StackItem top = top();
        if (top != null && top.needsChoice && top.card != null) {
            String name = top.card.name;
            spawn(top.card, type);
            stack.remove(stack.size() - 1);
            if (examining >= stack.size()) examining = -1;
            note = type + " stays on " + name + ".";
            return;
        }
        if (choiceHand != null) {
            spawn(choiceHand, type);
            note = type + " stays on " + choiceHand.name + ".";
            choiceHand = null;
        } else if (choicePerm != null) {
            choicePerm.choice = type;
            note = type + " stays on " + choicePerm.name + ".";
            choicePerm = null;
        }
        modal = Modal.NONE;
    }

    private void clickBoard(Perm p) {
        if (!p.alive) return;
        if (phase == Phase.ATTACKERS && !p.you && selected != null && selected.you) {
            tryAttack(selected);
            return;
        }
        if (phase == Phase.BLOCKERS && !p.you && p.attacking && selected != null && selected.you
                && selected.kind == BattlefieldLayout.Kind.CREATURE) {
            assignBlock(selected);
            return;
        }
        selected = p;
        if (phase == Phase.ATTACKERS && p.you) {
            tryAttackToggle(p);
            return;
        }
        if (phase == Phase.BLOCKERS && p.you && p.kind == BattlefieldLayout.Kind.CREATURE) {
            if (p.blocking) {
                p.blocking = false;
                note = "Block removed.";
            } else {
                note = "Now click the attacking creature.";
            }
            return;
        }
        if (p.choiceCard && (p.choice == null || p.choice.isBlank())) {
            choicePerm = p;
            choiceHand = null;
            modal = Modal.CHOOSE_TYPE;
            return;
        }
        p.tapped = !p.tapped;
        note = p.name + (p.tapped ? " tapped." : " untapped.");
    }

    private void releaseBoardDrag(Perm p) {
        if (!p.alive) return;
        selected = p;
        float h = Gdx.graphics.getHeight();
        if (phase == Phase.ATTACKERS && p.you) {
            if (!legalAttack(p)) return;
            if (BattlefieldLayout.declaresAttack(h, mx, my, themBadge.x, themBadge.y)) {
                p.attacking = true;
                note = p.name + " is attacking.";
            } else {
                note = "Release in their half, or near their life, to attack.";
            }
            return;
        }
        if (phase == Phase.BLOCKERS && p.you && p.kind == BattlefieldLayout.Kind.CREATURE) {
            if (snapAttacker(mx, my) != null) assignBlock(p);
            else note = "Release closer to their attacker to block.";
        }
    }

    private void tryAttackToggle(Perm p) {
        if (!legalAttack(p)) return;
        p.attacking = !p.attacking;
        note = p.attacking ? p.name + " is attacking." : p.name + " pulled back.";
    }

    private void tryAttack(Perm p) {
        if (p == null || !p.you) return;
        if (!legalAttack(p)) return;
        p.attacking = true;
        note = p.name + " is attacking.";
    }

    private boolean legalAttack(Perm p) {
        if (BattlefieldLayout.canAttack(p.kind, p.tapped, p.sick)) return true;
        note = p.sick ? p.name + " has summoning sickness and cannot attack yet."
                : p.name + " cannot attack.";
        return false;
    }

    private void assignBlock(Perm blocker) {
        if (blocker == null || !blocker.you || blocker.kind != BattlefieldLayout.Kind.CREATURE) return;
        if (opponentAttacker() == null) {
            note = "Nothing is attacking.";
            return;
        }
        for (Perm o : board) if (o.you) o.blocking = false;
        blocker.blocking = true;
        note = blocker.name + " blocks " + opponentAttacker().name + ".";
    }

    private void confirmAttack() {
        int damage = 0;
        int before = theirLife;
        for (Perm p : board) {
            if (!p.alive || !p.you || !p.attacking) continue;
            damage += power(p);
            p.tapped = true;
            p.attacking = false;
        }
        if (damage == 0) {
            note = "No attackers.";
            return;
        }
        theirLife -= damage;
        phase = Phase.MAIN;
        note = BattlefieldLayout.lifeChange("Them", before, damage);
    }

    private void confirmBlocks() {
        Perm atk = opponentAttacker();
        if (atk == null) {
            phase = Phase.MAIN;
            note = "No attacker.";
            return;
        }
        Perm blk = yourBlocker();
        if (blk == null) {
            int before = yourLife;
            int damage = power(atk);
            yourLife -= damage;
            atk.attacking = false;
            atk.tapped = true;
            phase = Phase.MAIN;
            note = BattlefieldLayout.lifeChange("You", before, damage);
            return;
        }
        BattlefieldLayout.CombatPreview preview = BattlefieldLayout.previewBlock(
                power(atk), toughness(atk), power(blk), toughness(blk));
        if (preview.attackerDies) kill(atk);
        else {
            atk.attacking = false;
            atk.tapped = true;
        }
        if (preview.blockerDies) kill(blk);
        else blk.blocking = false;
        phase = Phase.MAIN;
        note = preview.summary;
    }

    private void activate() {
        Perm veteran = find("Veteran");
        Perm hero = find("Hero");
        if (veteran == null || !BattlefieldLayout.canActivate(veteran.kind, veteran.tapped, veteran.sick)) {
            note = "Veteran cannot activate (tapped or summoning sick).";
            return;
        }
        if (hero == null) {
            note = "Hero is not on the board.";
            return;
        }
        abilityLine = BattlefieldLayout.abilityPreview("Veteran", "Hero", hero.counters + 1);
        modal = Modal.ABILITY;
    }

    private void confirmModal() {
        if (modal == Modal.ABILITY) {
            Perm veteran = find("Veteran");
            Perm hero = find("Hero");
            if (veteran != null && hero != null) {
                veteran.tapped = true;
                hero.counters++;
                note = "Hero now shows " + hero.counters + " +1/+1.";
            }
            modal = Modal.NONE;
            return;
        }
    }

    private void resolveShock() {
        StackItem top = top();
        if (top == null || !top.needsTarget) return;
        if (top.targetPerm != null) {
            Perm target = top.targetPerm;
            int printed = BattlefieldLayout.shownToughness(target.toughness, target.buff,
                    target.debuff, target.counters);
            String line = BattlefieldLayout.shockOnCreature(target.name, printed,
                    target.damage, BattlefieldLayout.SHOCK_DAMAGE);
            target.damage += BattlefieldLayout.SHOCK_DAMAGE;
            if (target.damage >= printed) kill(target);
            note = line;
        } else if (top.targetYou) {
            int before = yourLife;
            yourLife -= BattlefieldLayout.SHOCK_DAMAGE;
            note = BattlefieldLayout.lifeChange("You", before, BattlefieldLayout.SHOCK_DAMAGE);
        } else if (top.targetThem) {
            int before = theirLife;
            theirLife -= BattlefieldLayout.SHOCK_DAMAGE;
            note = BattlefieldLayout.lifeChange("Them", before, BattlefieldLayout.SHOCK_DAMAGE);
        } else {
            return;
        }
        if (top.card != null) {
            top.card.alive = false;
            top.card.parked = false;
        }
        yourGy++;
        stack.remove(stack.size() - 1);
        if (examining >= stack.size()) examining = -1;
        applyLockFromTop();
        StackItem next = top();
        if (next != null && BattlefieldLayout.waitingForTarget(
                new BattlefieldLayout.StackEntry(next.name, next.fullText, next.needsTarget, next.targetLabel))) {
            note = note + "  " + BattlefieldLayout.TARGET_PROMPT;
        }
    }

    private void clickShockTarget() {
        if (!targeting() || examining >= 0) return;
        updateShockHover();
        StackItem top = top();
        if (top == null) return;
        if (shockHover != null) {
            BattlefieldLayout.TargetAssignment assigned = BattlefieldLayout.clickTarget(
                    BattlefieldLayout.castSpell(top.name, true), shockHover.name);
            top.targetLabel = assigned.entry.target;
            top.targetPerm = shockHover;
            top.targetYou = false;
            top.targetThem = false;
            shockLock = shockHover;
            shockLockYou = false;
            shockLockThem = false;
            top.preview = BattlefieldLayout.stackOutcome(top.name, assigned.entry.target);
        } else if (shockHoverYou) {
            BattlefieldLayout.TargetAssignment assigned = BattlefieldLayout.clickTarget(
                    BattlefieldLayout.castSpell(top.name, true), "You");
            top.targetLabel = assigned.entry.target;
            top.targetPerm = null;
            top.targetYou = true;
            top.targetThem = false;
            shockLock = null;
            shockLockYou = true;
            shockLockThem = false;
            top.preview = BattlefieldLayout.stackOutcome(top.name, assigned.entry.target);
        } else if (shockHoverThem) {
            BattlefieldLayout.TargetAssignment assigned = BattlefieldLayout.clickTarget(
                    BattlefieldLayout.castSpell(top.name, true), "Them");
            top.targetLabel = assigned.entry.target;
            top.targetPerm = null;
            top.targetYou = false;
            top.targetThem = true;
            shockLock = null;
            shockLockYou = false;
            shockLockThem = true;
            top.preview = BattlefieldLayout.stackOutcome(top.name, assigned.entry.target);
        }
    }

    private void updateShockHover() {
        shockHover = null;
        shockHoverYou = false;
        shockHoverThem = false;
        List<Perm> creatures = new ArrayList<>();
        for (Perm p : board) {
            if (p.alive && p.kind == BattlefieldLayout.Kind.CREATURE) creatures.add(p);
        }
        int n = creatures.size();
        float[] cx = new float[n];
        float[] cy = new float[n];
        float[] ww = new float[n];
        float[] hh = new float[n];
        for (int i = 0; i < n; i++) {
            Perm p = creatures.get(i);
            cx[i] = p.cx;
            cy[i] = p.cy;
            ww[i] = p.w;
            hh[i] = p.h;
        }
        int hit = BattlefieldLayout.stickIndexIfInside(mx, my, cx, cy, ww, hh);
        if (hit >= 0) {
            shockHover = creatures.get(hit);
            return;
        }
        if (youBadge.contains(mx, my)) {
            shockHoverYou = true;
            return;
        }
        if (themBadge.contains(mx, my)) {
            shockHoverThem = true;
        }
    }

    private void clearShockLock() {
        shockLock = null;
        shockLockYou = false;
        shockLockThem = false;
    }

    /** The arrow follows the top spell's target after that spell becomes the top. */
    private void applyLockFromTop() {
        clearShockLock();
        shockHover = null;
        StackItem top = top();
        if (top == null) return;
        shockLock = top.targetPerm;
        shockLockYou = top.targetYou;
        shockLockThem = top.targetThem;
    }

    private void cancelModal() {
        if (choiceHand != null) choiceHand.parked = false;
        choiceHand = null;
        choicePerm = null;
        modal = Modal.NONE;
        note = "Cancelled.";
    }

    private void cancelTop() {
        if (stack.isEmpty()) return;
        StackItem item = stack.remove(stack.size() - 1);
        if (item.card != null) {
            item.card.alive = true;
            item.card.parked = false;
        }
        if (examining >= stack.size()) examining = -1;
        applyLockFromTop();
        note = item.name + " is back in your hand.";
        StackItem next = top();
        if (next != null && next.needsTarget && (next.targetLabel == null || next.targetLabel.isBlank())) {
            note = item.name + " is back in your hand. " + BattlefieldLayout.TARGET_PROMPT;
        }
    }

    private void kill(Perm p) {
        if (!p.alive) return;
        p.alive = false;
        p.attacking = false;
        p.blocking = false;
        if (p.you) yourGy++;
        else theirGy++;
    }

    private int power(Perm p) {
        return BattlefieldLayout.shownPower(p.power, p.buff, p.debuff, p.counters);
    }

    private int toughness(Perm p) {
        return BattlefieldLayout.shownToughness(p.toughness, p.buff, p.debuff, p.counters) - p.damage;
    }

    private Perm find(String name) {
        for (Perm p : board) if (p.alive && name.equals(p.name)) return p;
        return null;
    }

    private Perm opponentAttacker() {
        for (Perm p : board) if (p.alive && !p.you && p.attacking) return p;
        return null;
    }

    private Perm yourBlocker() {
        for (Perm p : board) if (p.alive && p.you && p.blocking) return p;
        return null;
    }

    private List<Perm> row(boolean you, BattlefieldLayout.Row which) {
        List<Perm> out = new ArrayList<>();
        for (Perm p : board) {
            if (!p.alive || p.you != you) continue;
            boolean land = p.kind == BattlefieldLayout.Kind.LAND;
            if (which == BattlefieldLayout.Row.LAND ? land : !land) out.add(p);
        }
        return out;
    }

    private void layout(float dt) {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        if (w < 1f || h < 1f) return;
        placeRow(w, h, false, BattlefieldLayout.Row.LAND);
        placeRow(w, h, false, BattlefieldLayout.Row.CREATURE);
        placeRow(w, h, true, BattlefieldLayout.Row.LAND);
        placeRow(w, h, true, BattlefieldLayout.Row.CREATURE);
        Perm atk = opponentAttacker();
        for (Perm p : board) {
            if (p.alive && p.blocking && atk != null) {
                p.tx = atk.tx;
                p.ty = atk.ty - BattlefieldLayout.cardHeight(h) * 0.95f;
            }
        }
        for (Perm p : board) {
            if (!p.alive) {
                if (dt > 0f) p.alpha = Math.max(0f, p.alpha - dt / 0.4f);
                continue;
            }
            if (dt <= 0f) {
                p.cx = p.tx; p.cy = p.ty; p.w = p.tw; p.h = p.th; p.rot = p.trot;
            } else {
                p.cx = HandLayout.easeToward(p.cx, p.tx, dt);
                p.cy = HandLayout.easeToward(p.cy, p.ty, dt);
                p.w = HandLayout.easeToward(p.w, p.tw, dt);
                p.h = HandLayout.easeToward(p.h, p.th, dt);
                p.rot = HandLayout.easeToward(p.rot, p.trot, dt);
                if (p.thunk > 0f) p.thunk = Math.max(0f, p.thunk - dt / 0.22f);
            }
        }
        layoutHand(w, h, dt);
        layoutStack(w, h);
        layoutChrome(w, h);
    }

    private void placeRow(float w, float h, boolean you, BattlefieldLayout.Row which) {
        List<Perm> cards = row(you, which);
        BattlefieldLayout.Side side = you ? BattlefieldLayout.Side.YOU : BattlefieldLayout.Side.OPPONENT;
        for (int i = 0; i < cards.size(); i++) {
            Perm p = cards.get(i);
            HandLayout.Pose pose = BattlefieldLayout.slot(w, h, side, which, i, cards.size(), p.tapped);
            p.tx = pose.cx;
            p.ty = pose.cy;
            p.tw = pose.w;
            p.th = pose.h;
            p.trot = pose.rotationDeg;
            if (p.attacking) {
                float slide = BattlefieldLayout.ATTACK_SLIDE_FRAC * h;
                p.ty += p.you ? slide : -slide;
            }
        }
    }

    private void layoutHand(float w, float h, float dt) {
        List<HandCard> live = live();
        int n = live.size();
        for (int i = 0; i < n; i++) {
            HandCard c = live.get(i);
            if (draggingHand && i == pressHand) {
                if (dt > 0f) easeHand(c, dt);
                continue;
            }
            HandLayout.Pose pose = draggingHand
                    ? HandLayout.dragLayoutPose(w, h, n, i, hover, dragInHand, pressHand, mx, my)
                    : HandLayout.layoutPose(w, h, n, i, hover, false);
            c.tcx = pose.cx; c.tcy = pose.cy; c.tw = pose.w; c.th = pose.h; c.trot = pose.rotationDeg;
            if (dt <= 0f) snapHand(c);
            else easeHand(c, dt);
        }
        if (pressHand >= 0) pressTime += dt;
    }

    private void layoutStack(float w, float h) {
        float colW = BattlefieldLayout.stackColumnWidth(w);
        float cx = BattlefieldLayout.stackColumnCenterX(w);
        HandLayout.Pose thumb = BattlefieldLayout.stackThumbnail(w, h);
        float y = BattlefieldLayout.STACK_TOP_FRAC * h;
        stackBottomY = y;
        for (int visual = 0; visual < stack.size(); visual++) {
            int i = stack.size() - 1 - visual;
            StackItem item = stack.get(i);
            boolean examined = i == examining;
            float rh = BattlefieldLayout.stackRowHeight(h, examined);
            item.rowW = colW;
            item.rowH = rh;
            item.rowX = cx;
            item.rowY = y - rh / 2f;
            item.thumbW = thumb.w;
            item.thumbH = thumb.h;
            item.thumbX = cx - colW / 2f + thumb.w / 2f + 8f;
            item.thumbY = item.rowY;
            y -= rh + 0.008f * h;
            stackBottomY = y;
        }
    }

    private void layoutChrome(float w, float h) {
        buttons.clear();
        youBadge = badge(0.08f * w, 0.33f * h, 0.12f * w, 0.07f * h, "you-life", "You  " + yourLife);
        themBadge = badge(0.08f * w, 0.78f * h, 0.12f * w, 0.07f * h, "them-life", "Them  " + theirLife);
        float x = 0.90f * w;
        float bw = 0.16f * w;
        float bh = 0.042f * h;
        float y = 0.62f * h;
        y = stack(x, y, bw, bh, "your-attack", "Your attack");
        y = stack(x, y, bw, bh, "their-attack", "Their attack");
        y = stack(x, y, bw, bh, "new-turn", "New turn");
        if (phase == Phase.ATTACKERS && modal == Modal.NONE) {
            y = stack(x, y, bw, bh, "all-attack", "All attack");
            y = stack(x, y, bw, bh, "confirm-attack", "Attack");
        }
        if (phase == Phase.BLOCKERS && modal == Modal.NONE) {
            y = stack(x, y, bw, bh, "no-blocks", "No blocks");
            y = stack(x, y, bw, bh, "confirm-blocks", "Confirm blocks");
        }
        Perm veteran = find("Veteran");
        if (modal == Modal.NONE && veteran != null
                && BattlefieldLayout.canActivate(veteran.kind, veteran.tapped, veteran.sick)) {
            y = stack(x, y, bw, bh, "activate", "Activate");
        }
        if (modal == Modal.NONE && hover >= 0 && hover < live().size()) {
            stack(x, y, bw, bh, "play", "Play");
        }
        if (modal == Modal.ABILITY) {
            stack(0.50f * w - 0.08f * w, 0.57f * h, 0.12f * w, bh, "confirm", "Confirm");
            stack(0.50f * w + 0.08f * w, 0.57f * h, 0.12f * w, bh, "cancel", "Cancel");
        }
        boolean choosing = modal == Modal.CHOOSE_TYPE || (top() != null && top().needsChoice);
        float choiceY = choosing ? Math.min(stackBottomY - bh * 0.2f, 0.50f * h) : 0.50f * h;
        if (choosing && !stack.isEmpty() && top().needsChoice) {
            float bx = BattlefieldLayout.stackColumnCenterX(w);
            stack(bx, choiceY, bw * 0.7f, bh, "type-dragon", "Dragon");
            stack(bx, choiceY - bh * 1.25f, bw * 0.7f, bh, "type-elf", "Elf");
            stack(bx, choiceY - bh * 2.5f, bw * 0.7f, bh, "type-goblin", "Goblin");
            stack(bx, choiceY - bh * 3.75f, bw * 0.7f, bh, "cancel", "Cancel");
        } else if (modal == Modal.CHOOSE_TYPE) {
            stack(0.38f * w, 0.57f * h, 0.12f * w, bh, "type-dragon", "Dragon");
            stack(0.52f * w, 0.57f * h, 0.12f * w, bh, "type-elf", "Elf");
            stack(0.66f * w, 0.57f * h, 0.12f * w, bh, "type-goblin", "Goblin");
        }
        if (modal != Modal.ABILITY && !stack.isEmpty() && (top() == null || !top().needsChoice)) {
            float bx = BattlefieldLayout.stackColumnCenterX(w);
            float by = Math.min(stackBottomY - bh * 0.15f, 0.52f * h);
            if (topHasTarget()) {
                stack(bx, by, bw * 0.75f, bh, "resolve", "Resolve");
                stack(bx, by - bh * 1.3f, bw * 0.75f, bh, "cancel", "Cancel");
            } else {
                stack(bx, by, bw * 0.75f, bh, "cancel", "Cancel");
            }
        }
    }

    private float stack(float x, float y, float w, float h, String id, String label) {
        buttons.add(new UiButton(id, label, x, y, w, h));
        return y - h * 1.35f;
    }

    private static UiButton badge(float x, float y, float w, float h, String id, String label) {
        return new UiButton(id, label, x, y, w, h);
    }

    private void onButton(String id) {
        switch (id) {
            case "your-attack" -> yourAttack();
            case "their-attack" -> theirAttack();
            case "new-turn" -> newTurn();
            case "all-attack" -> allAttack();
            case "confirm-attack" -> confirmAttack();
            case "no-blocks" -> clearBlocks();
            case "confirm-blocks" -> confirmBlocks();
            case "activate" -> activate();
            case "confirm" -> confirmModal();
            case "resolve" -> { if (topHasTarget()) resolveShock(); }
            case "cancel" -> {
                if (modal != Modal.NONE) cancelModal();
                else cancelTop();
            }
            case "play" -> { if (hover >= 0 && hover < live().size()) play(live().get(hover)); }
            case "type-dragon" -> choose("Dragon");
            case "type-elf" -> choose("Elf");
            case "type-goblin" -> choose("Goblin");
            default -> { }
        }
    }

    private void allAttack() {
        int n = 0;
        for (Perm p : board) {
            if (p.alive && p.you && BattlefieldLayout.canAttack(p.kind, p.tapped, p.sick)) {
                p.attacking = true;
                n++;
            }
        }
        note = n == 0 ? "Nothing can attack." : "All " + n + " legal creatures are attacking.";
    }

    private void clearBlocks() {
        for (Perm p : board) p.blocking = false;
        note = "No blocks.";
    }

    private List<HandCard> live() {
        List<HandCard> out = new ArrayList<>();
        for (HandCard c : hand) if (c.alive && !c.parked) out.add(c);
        return out;
    }

    private int pickHand(float x, float y) {
        List<HandCard> live = live();
        for (int i = live.size() - 1; i >= 0; i--) {
            HandCard c = live.get(i);
            if (Math.abs(x - c.cx) <= c.w / 2f && Math.abs(y - c.cy) <= c.h / 2f) return i;
        }
        return -1;
    }

    private Perm hitBoard(float x, float y) {
        float pad = 0.01f * Gdx.graphics.getHeight();
        for (int i = board.size() - 1; i >= 0; i--) {
            Perm p = board.get(i);
            if (!p.alive) continue;
            HandLayout.Pose pose = new HandLayout.Pose(p.cx, p.cy, p.w, p.h, p.rot, 1f);
            if (HandLayout.pointHitsPose(pose, x, y, pad)) return p;
        }
        return null;
    }

    private Perm snapAttacker(float x, float y) {
        List<Perm> attackers = new ArrayList<>();
        for (Perm p : board) if (p.alive && !p.you && p.attacking) attackers.add(p);
        float[] xs = new float[attackers.size()];
        float[] ys = new float[attackers.size()];
        for (int i = 0; i < attackers.size(); i++) {
            xs[i] = attackers.get(i).cx;
            ys[i] = attackers.get(i).cy;
        }
        int idx = BattlefieldLayout.snapBlocker(Gdx.graphics.getHeight(), x, y, xs, ys);
        return idx < 0 ? null : attackers.get(idx);
    }

    private UiButton hitButton(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            if (buttons.get(i).contains(x, y)) return buttons.get(i);
        }
        return null;
    }

    private String bannerText() {
        if (examining >= 0 && examining < stack.size()) {
            return "Reading the stack. Click the entry again, or click the board, to go back to targeting.";
        }
        StackItem top = top();
        if (top != null && top.needsChoice) return "Choose a creature type. It stays printed on the card.";
        if (top != null && top.needsTarget) {
            if (top.preview != null && !top.preview.isBlank()) return top.preview;
            if (note != null && !note.isBlank()) return note;
            return BattlefieldLayout.TARGET_PROMPT;
        }
        if (modal == Modal.CHOOSE_TYPE) return "Choose a creature type. It stays printed on the card.";
        if (modal == Modal.ABILITY) return abilityLine + "  Confirm to resolve.";
        if (phase == Phase.BLOCKERS) {
            Perm atk = opponentAttacker();
            Perm blk = yourBlocker();
            if (atk == null) return "Nothing is attacking.";
            if (blk == null) return BattlefieldLayout.previewUnblocked("You", power(atk)).summary + " Confirm or assign a blocker.";
            return blk.name + " blocks " + atk.name + ". "
                    + BattlefieldLayout.previewBlock(power(atk), toughness(atk), power(blk), toughness(blk)).summary;
        }
        if (phase == Phase.ATTACKERS) return "Drag a creature into their half. The arrow snaps. Then press Attack.";
        return note;
    }

    private boolean markedDead(Perm p) {
        if (phase != Phase.BLOCKERS || modal != Modal.NONE) return false;
        Perm atk = opponentAttacker();
        Perm blk = yourBlocker();
        if (atk == null || blk == null) return false;
        BattlefieldLayout.CombatPreview preview = BattlefieldLayout.previewBlock(
                power(atk), toughness(atk), power(blk), toughness(blk));
        if (p == atk) return preview.attackerDies;
        return p == blk && preview.blockerDies;
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        readPointer(Gdx.input.getX(), Gdx.input.getY());
        if (targeting() && examining < 0) updateShockHover();
        else {
            shockHover = null;
            shockHoverYou = false;
            shockHoverThem = false;
        }
        layout(dt);
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        shapes.setProjectionMatrix(batch.getProjectionMatrix());
        Gdx.gl.glClearColor(TABLE.r, TABLE.g, TABLE.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        List<HandCard> live = live();
        boolean willCast = draggingHand && !dragInHand && pressHand >= 0 && pressHand < live.size()
                && live.get(pressHand).cy > HandLayout.castLineY(h);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(YOU_TINT.r, YOU_TINT.g, YOU_TINT.b, 0.35f);
        shapes.rect(0, 0, w, h * 0.50f);
        shapes.setColor(THEM_TINT.r, THEM_TINT.g, THEM_TINT.b, 0.28f);
        shapes.rect(0, h * 0.50f, w, h * 0.50f);
        shapes.setColor(willCast ? LINE_HOT : LINE_DIM);
        shapes.rect(0, HandLayout.castLineY(h) - 2f, w, 4f);
        drawBadge(youBadge, new Color(0.15f, 0.35f, 0.28f, 0.95f));
        drawBadge(themBadge, shockHoverThem || shockLockThem
                ? new Color(0.85f, 0.35f, 0.2f, 1f) : new Color(0.40f, 0.16f, 0.16f, 0.95f));
        for (UiButton b : buttons) drawBadge(b, new Color(0.16f, 0.18f, 0.22f, 0.92f));
        for (Perm p : board) {
            if (p.alpha <= 0.02f) continue;
            Color glow = null;
            if (p == shockHover || p == shockLock) glow = ARROW;
            else if (markedDead(p)) glow = RED;
            else if (p.attacking) glow = RED;
            else if (phase == Phase.ATTACKERS && p.you && BattlefieldLayout.canAttack(p.kind, p.tapped, p.sick))
                glow = GOLD;
            else if (p == selected) glow = Color.WHITE;
            if (glow != null) {
                shapes.setColor(glow.r, glow.g, glow.b, 0.45f);
                shapes.rect(p.cx - p.w / 2f - 8f, p.cy - p.h / 2f - 8f, p.w + 16f, p.h + 16f);
            }
        }
        for (StackItem item : stack) {
            boolean active = item == top();
            boolean examined = stack.indexOf(item) == examining;
            shapes.setColor(examined ? 0.24f : 0.10f, examined ? 0.18f : 0.10f, 0.08f, 0.94f);
            shapes.rect(item.rowX - item.rowW / 2f, item.rowY - item.rowH / 2f, item.rowW, item.rowH);
            shapes.setColor(active ? GOLD.r : 0.45f, active ? GOLD.g : 0.40f, active ? GOLD.b : 0.28f, active ? 1f : 0.75f);
            float left = item.rowX - item.rowW / 2f;
            float bottom = item.rowY - item.rowH / 2f;
            shapes.rect(left, bottom, item.rowW, 3f);
            shapes.rect(left, bottom + item.rowH - 3f, item.rowW, 3f);
            shapes.rect(left, bottom, 3f, item.rowH);
            shapes.rect(left + item.rowW - 3f, bottom, 3f, item.rowH);
        }
        shapes.end();

        batch.begin();
        for (Perm p : board) if (p.alpha > 0.02f) drawPerm(p);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < live.size(); i++) {
                boolean top = (draggingHand && i == pressHand) || (!draggingHand && i == hover);
                if ((pass == 0) == top) continue;
                drawHand(live.get(i), willCast && i == pressHand);
            }
        }
        font.setColor(Color.WHITE);
        big.setColor(Color.WHITE);
        drawBig(youBadge.label, youBadge.x, youBadge.y + 8f);
        drawBig(themBadge.label, themBadge.x, themBadge.y + 8f);
        for (UiButton b : buttons) drawCentered(b.label, b.x, b.y + 6f, Color.WHITE);
        drawCentered("Library 52", 0.93f * w, 0.90f * h, Color.WHITE);
        drawCentered("Their GY " + theirGy, 0.93f * w, 0.84f * h, Color.WHITE);
        drawCentered("Your GY " + yourGy, 0.93f * w, 0.22f * h, Color.WHITE);
        String phaseLabel = switch (phase) {
            case MAIN -> "Main";
            case ATTACKERS -> "Combat: attackers";
            case BLOCKERS -> "Combat: blockers";
        };
        drawBig(phaseLabel, 0.62f * w, 0.52f * h);
        font.draw(batch, "Drag out of the hand to queue a spell. One click targets the top spell. Click the log to read it.", 24f, h - 18f);
        font.draw(batch, "Cast another instant onto the stack. Resolve takes only the top. Esc cancels the top spell.", 24f, h - 40f);
        drawCentered(bannerText(), w / 2f, 0.505f * h, GOLD);
        drawStackLog();
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(ARROW);
        drawArrows(w, h);
        shapes.end();
    }

    private void drawArrows(float w, float h) {
        StackItem top = top();
        if (top != null && top.needsTarget && examining < 0) {
            float x0 = top.thumbX;
            float y0 = top.thumbY;
            float x1 = mx, y1 = my;
            if (top.targetPerm != null) { x1 = top.targetPerm.cx; y1 = top.targetPerm.cy; }
            else if (top.targetThem || shockHoverThem) { x1 = themBadge.x; y1 = themBadge.y; }
            else if (top.targetYou || shockHoverYou) { x1 = youBadge.x; y1 = youBadge.y; }
            else if (shockHover != null) { x1 = shockHover.cx; y1 = shockHover.cy; }
            curveArrow(x0, y0, x1, y1, 3.5f);
        }
        for (Perm p : board) {
            if (!p.alive || !p.attacking) continue;
            float x1 = p.you ? themBadge.x : youBadge.x;
            float y1 = p.you ? themBadge.y : youBadge.y;
            if (draggingBoard && p == pressedPerm && phase == Phase.ATTACKERS) {
                x1 = mx; y1 = my;
                if (BattlefieldLayout.declaresAttack(h, mx, my, themBadge.x, themBadge.y)) {
                    x1 = themBadge.x; y1 = themBadge.y;
                }
            }
            curveArrow(p.cx, p.cy, x1, y1);
        }
        if (draggingBoard && pressedPerm != null && phase == Phase.BLOCKERS && !pressedPerm.attacking) {
            Perm snap = snapAttacker(mx, my);
            float x1 = snap != null ? snap.cx : mx;
            float y1 = snap != null ? snap.cy : my;
            curveArrow(pressedPerm.cx, pressedPerm.cy, x1, y1);
        }
        Perm blk = yourBlocker();
        Perm atk = opponentAttacker();
        if (blk != null && atk != null && phase == Phase.BLOCKERS) {
            shapes.rectLine(blk.cx, blk.cy, atk.cx, atk.cy, 8f);
        }
    }

    private void curveArrow(float x0, float y0, float x1, float y1) {
        curveArrow(x0, y0, x1, y1, 12f);
    }

    private void curveArrow(float x0, float y0, float x1, float y1, float width) {
        float cx = (x0 + x1) / 2f;
        float cy = (y0 + y1) / 2f + 40f;
        float px = x0, py = y0;
        int n = 14;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            float u = 1f - t;
            float x = u * u * x0 + 2f * u * t * cx + t * t * x1;
            float y = u * u * y0 + 2f * u * t * cy + t * t * y1;
            shapes.rectLine(px, py, x, y, width);
            if (i == n) arrowHead(px, py, x, y, width);
            px = x;
            py = y;
        }
    }

    private void arrowHead(float x0, float y0, float x1, float y1) {
        arrowHead(x0, y0, x1, y1, 12f);
    }

    private void arrowHead(float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        dx /= len; dy /= len;
        float reach = Math.max(10f, width * 2.2f);
        float wing = Math.max(5f, width * 1.2f);
        float bx = x1 - dx * reach, by = y1 - dy * reach;
        shapes.triangle(x1, y1, bx + -dy * wing, by + dx * wing, bx - -dy * wing, by - dx * wing);
    }

    private void drawStackLog() {
        if (stack.isEmpty()) return;
        float scale = font.getData().scaleX;
        for (int i = stack.size() - 1; i >= 0; i--) {
            StackItem item = stack.get(i);
            boolean examined = i == examining;
            float textScale = examined ? 1.55f : 1.15f;
            font.getData().setScale(textScale);
            float textX = item.thumbX + item.thumbW / 2f + 8f;
            float textW = item.rowX + item.rowW / 2f - 8f - textX;
            float textTop = item.rowY + item.rowH / 2f - 8f;
            if (item.card != null && item.card.tex != null) {
                batch.setColor(Color.WHITE);
                drawTex(item.card.tex, item.thumbX, item.thumbY, item.thumbW, item.thumbH, 0f);
            } else {
                batch.setColor(0.35f, 0.36f, 0.4f, 1f);
                drawTex(pixel(), item.thumbX, item.thumbY, item.thumbW, item.thumbH, 0f);
                batch.setColor(Color.WHITE);
            }
            font.setColor(GOLD);
            font.draw(batch, item.name, textX, textTop, textW, Align.left, true);
            glyphs.setText(font, item.name);
            float y = textTop - glyphs.height - 4f;
            font.setColor(Color.WHITE);
            font.draw(batch, item.fullText, textX, y, textW, Align.left, true);
            glyphs.setText(font, item.fullText, Color.WHITE, textW, Align.left, true);
            y -= glyphs.height + 6f;
            font.setColor(GOLD);
            String extra = item.needsTarget
                    ? (item.preview == null || item.preview.isBlank() ? BattlefieldLayout.TARGET_PROMPT : item.preview)
                    : (item.needsChoice ? "Choose a creature type." : "");
            if (!extra.isEmpty()) font.draw(batch, extra, textX, y, textW, Align.left, true);
        }
        font.getData().setScale(scale);
        font.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);
    }

    private boolean hitStack(float x, float y) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            StackItem item = stack.get(i);
            if (Math.abs(x - item.rowX) <= item.rowW / 2f && Math.abs(y - item.rowY) <= item.rowH / 2f) {
                examining = examining == i ? -1 : i;
                return true;
            }
        }
        return false;
    }

    private StackItem top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    private boolean targeting() {
        StackItem top = top();
        return modal == Modal.NONE && top != null && top.needsTarget;
    }

    private boolean topHasTarget() {
        StackItem top = top();
        return top != null && top.needsTarget && top.targetLabel != null && !top.targetLabel.isBlank();
    }

    /** Insert the gripped live card at {@code to}. Cards already on the stack keep their places. */
    private void reorderLive(int from, int to) {
        List<HandCard> cards = live();
        int n = cards.size();
        if (from < 0 || from >= n) return;
        int[] order = HandLayout.movedOrder(n, from, to);
        HandCard[] next = new HandCard[n];
        for (int i = 0; i < n; i++) next[i] = cards.get(order[i]);
        int li = 0;
        for (int i = 0; i < hand.size(); i++) {
            HandCard c = hand.get(i);
            if (c.alive && !c.parked) hand.set(i, next[li++]);
        }
    }

    private void drawBadge(UiButton b, Color color) {
        shapes.setColor(color);
        shapes.rect(b.x - b.w / 2f, b.y - b.h / 2f, b.w, b.h);
    }

    private void drawPerm(Perm p) {
        float scale = 1f;
        if (p.thunk > 0f) scale = 1f + 0.08f * (float) Math.sin(p.thunk * Math.PI);
        float cw = p.w * scale, ch = p.h * scale;
        boolean sick = p.sick && p.alive;
        if (p.tex != null) {
            if (sick) batch.setColor(0.42f, 0.46f, 0.55f, p.alpha);
            else batch.setColor(1f, 1f, 1f, p.alpha);
            drawTex(p.tex, p.cx, p.cy, cw, ch, p.rot);
        } else {
            batch.setColor(sick ? 0.25f : 0.34f, 0.35f, 0.38f, p.alpha);
            drawTex(pixel(), p.cx, p.cy, cw, ch, p.rot);
        }
        if (p.alpha < 0.25f) return;
        drawTag(p.name, p.cx, p.cy + ch * 0.22f, Color.WHITE);
        if (p.choiceCard) drawTag(BattlefieldLayout.printedChoice(p.choice), p.cx, p.cy + ch * 0.02f, GOLD);
        if (p.buffLine != null) drawTag(p.buffLine, p.cx, p.cy - ch * 0.12f, GREEN);
        if (p.debuffLine != null) drawTag(p.debuffLine, p.cx, p.cy - ch * 0.12f, RED);
        if (p.kind == BattlefieldLayout.Kind.CREATURE) {
            Color pt = Color.WHITE;
            if (p.debuff > 0 && p.buff == 0 && p.counters == 0) pt = RED;
            else if (p.buff > 0 || p.counters > 0) pt = GREEN;
            drawTag(power(p) + "/" + toughness(p), p.cx + cw * 0.18f, p.cy - ch * 0.32f, pt);
        }
        if (p.counters > 0) drawTag(p.counters + " +1/+1", p.cx, p.cy + ch * 0.40f, GREEN);
        if (sick) drawTag("Sick", p.cx - cw * 0.18f, p.cy - ch * 0.32f, new Color(0.75f, 0.82f, 0.95f, 1f));
        if (markedDead(p)) drawTag("dies", p.cx, p.cy, RED);
        batch.setColor(Color.WHITE);
    }

    private void drawHand(HandCard c, boolean glow) {
        if (glow) {
            batch.setColor(0.45f, 0.8f, 1f, 0.45f);
            drawTex(pixel(), c.cx, c.cy, c.w + 14f, c.h + 14f, c.rotation);
        }
        drawHandAt(c, c.cx, c.cy, c.w, c.h, c.rotation);
    }

    private void drawHandAt(HandCard c, float cx, float cy, float cw, float ch, float rot) {
        if (c.tex != null) {
            batch.setColor(Color.WHITE);
            drawTex(c.tex, cx, cy, cw, ch, rot);
        } else {
            batch.setColor(0.35f, 0.36f, 0.4f, 1f);
            drawTex(pixel(), cx, cy, cw, ch, rot);
        }
        drawTag(c.name, cx, cy + ch * 0.28f, Color.WHITE);
    }

    private void drawTag(String text, float x, float y, Color color) {
        glyphs.setText(font, text);
        float pad = 3f;
        batch.setColor(0f, 0f, 0f, 0.66f);
        batch.draw(pixel(), x - glyphs.width / 2f - pad, y - glyphs.height, glyphs.width + pad * 2f, glyphs.height + pad * 2f);
        batch.setColor(Color.WHITE);
        font.setColor(color);
        font.draw(batch, text, x - glyphs.width / 2f, y);
    }

    private void drawCentered(String text, float x, float y, Color color) {
        glyphs.setText(font, text);
        batch.setColor(Color.WHITE);
        font.setColor(color);
        font.draw(batch, text, x - glyphs.width / 2f, y);
    }

    private void drawBig(String text, float x, float y) {
        glyphs.setText(big, text);
        batch.setColor(Color.WHITE);
        big.setColor(Color.WHITE);
        big.draw(batch, text, x - glyphs.width / 2f, y);
    }

    private void drawTex(Texture t, float cx, float cy, float w, float h, float rot) {
        batch.draw(t, cx - w / 2f, cy - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, rot,
                0, 0, t.getWidth(), t.getHeight(), false, false);
    }

    private Texture pixel() {
        if (pixel == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE);
            pm.fill();
            pixel = new Texture(pm);
            pm.dispose();
        }
        return pixel;
    }

    private void readPointer(int sx, int sy) {
        mx = sx;
        my = BattlefieldLayout.screenYToLayoutY(Gdx.graphics.getHeight(), sy);
    }

    private static void easeHand(HandCard c, float dt) {
        c.cx = HandLayout.easeToward(c.cx, c.tcx, dt);
        c.cy = HandLayout.easeToward(c.cy, c.tcy, dt);
        c.w = HandLayout.easeToward(c.w, c.tw, dt);
        c.h = HandLayout.easeToward(c.h, c.th, dt);
        c.rotation = HandLayout.easeToward(c.rotation, c.trot, dt);
    }

    private static void snapHand(HandCard c) {
        c.cx = c.tcx; c.cy = c.tcy; c.w = c.tw; c.h = c.th; c.rotation = c.trot;
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        if (big != null) big.dispose();
        if (pixel != null) pixel.dispose();
        for (Texture t : art) t.dispose();
    }

    private static final class Perm {
        String name;
        BattlefieldLayout.Kind kind = BattlefieldLayout.Kind.LAND;
        boolean you, tapped, sick, attacking, blocking, choiceCard, hasAbility, alive = true;
        int power, toughness, buff, debuff, counters, damage;
        String buffLine, debuffLine, choice;
        Texture tex;
        float cx, cy, w, h, rot, alpha = 1f, thunk;
        float tx, ty, tw, th, trot;
    }

    private static final class StackItem {
        HandCard card;
        String name = "";
        String fullText = "";
        String targetLabel;
        String preview = "";
        Perm targetPerm;
        boolean needsTarget, needsChoice, targetYou, targetThem;
        float rowX, rowY, rowW, rowH;
        float thumbX, thumbY, thumbW, thumbH;
    }

    private static final class HandCard {
        String name;
        BattlefieldLayout.Kind kind;
        boolean choice, instant, alive = true, parked;
        int power, toughness;
        Texture tex;
        float cx, cy, w, h, rotation;
        float tcx, tcy, tw, th, trot;
    }

    private static final class UiButton {
        final String id;
        final String label;
        final float x, y, w, h;

        UiButton(String id, String label, float x, float y, float w, float h) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(float px, float py) {
            return Math.abs(px - x) <= w / 2f && Math.abs(py - y) <= h / 2f;
        }
    }
}
