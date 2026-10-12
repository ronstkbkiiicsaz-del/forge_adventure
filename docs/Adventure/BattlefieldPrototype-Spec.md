# Battlefield and combat prototype (spec for Grok, draft 2026-10-11)

**Status: approved by Steve, 2026-10-12** (drafted by Vex from Steve's ask; drag-first and the targeting arrow added
at Steve's direction). Ready for Grok.

**Purpose.** The hand prototype proved the hand feel (`HandPrototype-Spec.md`). This one proves the **battlefield**:
how played cards land, how the board is laid out, and above all how **attacking and blocking** feel. Same approach:
a small standalone libGDX window, fake scripted state, no Forge rules. Steve plays it for a few minutes and says yes
or no. If yes, it becomes the battlefield component of the new duel screen.

**Reference.** MTG Arena does this well, not perfectly. Take what works (clear rows, arrows,
satisfying landings) and fix what doesn't (see "Where we beat Arena").

## Drag first, forgiving (Steve, 2026-10-12)
**Dragging is the primary interaction**: Steve finds it more visual and natural than clicking. Drag a card to play
it, drag a creature toward the opponent to attack, drag your creature onto an attacker to block.
Steve and Tiny both have hand tremors, so **drags must be forgiving**:
- **Generous drop zones that snap.** You don't have to release precisely: anywhere over the opponent's half counts as
  "attack," anywhere near an attacker snaps the blocker to it, and the target highlights before you let go.
- **Small jitter is ignored** (the 12 px rule from the hand prototype applies everywhere).
- **Clicking still works as a backup** for every action (click creature then click target), for anyone who prefers it.
- Nothing timing-based, and every combat choice can be undone until you press Confirm.

## Deliverable
- `forge.app.BattlefieldPrototype` in `forge-gui-mobile-dev` with its own `main`, plus `battlefield-prototype.cmd`.
  1920x1080, resizable, everything scales from window height H.
- **Reuse the hand component** from the hand prototype at the bottom (with the tightened hover from round 2), so
  playing from hand onto the board can be felt end to end.
- Card art from the local Forge image cache (as in the hand prototype), labelled grey rectangles as fallback.
- Numbers in one constants block so they're easy to tune.

## Layout
- **Opponent's side on top, yours on the bottom**, mirrored.
- On each side, two rows: **creatures in front** (toward the middle of the table), **lands at the back** (toward
  that player's edge). Other permanents (artifacts, enchantments) in a small group at the right end of the creature row.
- Battlefield cards at about **0.16H tall**. Rows centre themselves and **reflow smoothly** when cards arrive or leave.
- Each player has a **life total badge** and a portrait spot at the left; **library, graveyard and exile** piles at
  the right edge, with counts.
- A slim **phase strip** at the middle-right showing the current step (Main, Combat: attackers, blockers, damage, End).
- **Power/toughness badges** on creatures, large enough to read at a glance.

## 1. Playing cards onto the board
- Casting from the hand (drag above the cast line, or click a card then click "Play", or Enter) sends the card **into
  its row**: it flies to its slot, lands with a small overshoot "thunk" (scale 1.08 then settle), and a brief glow.
- Lands go to the land row; creatures to the creature row; others to the right group.
- **Tapping:** click your land to tap it (rotates 90°, eased, about 0.15 s); click again to untap (prototype only).

## 1b. Targeting arrow (Steve, 2026-10-12: "a big indicator arrow like Arena")
- When you play a card **that needs a target**, a **big, bold arrow** appears, curving from the card to your cursor.
  Arena-style: thick, bright, with a clear arrowhead, impossible to miss.
- **Valid targets light up**; everything else dims slightly. The arrow **snaps** to the nearest valid target as you
  get close, and that target highlights strongly before you commit (forgiving, same as the combat drags).
- Drag-play flow: drag the card up past the cast line, and the card parks above the hand while the arrow follows
  your cursor; **release over a target** (or click it) to choose. Release over nothing, or right-click / Esc, cancels
  and the card returns to your hand.
- Once chosen, the arrow **stays drawn** from the card to its target for a moment while the spell resolves, so it's
  clear what hit what.
- **The same arrow style is used for attack arrows**, so the whole board speaks one visual language.
- Fake cards for the prototype: one burn spell that can target a creature or a player, and one that can only target
  a creature (so "valid vs invalid" is visible).

## 2. Attacking (scripted scenario "Your attack")
- In the **Declare attackers** step, every creature that can attack gets a soft highlight.
- **Drag a creature toward the opponent** (anywhere over their half) **to attack**; clicking it also toggles it. It slides forward toward the opponent (about 0.05H), gets a red
  edge, and an **arrow** points from it to the opponent (or a planeswalker, if the scenario has one).
- An **"All attack"** button selects everything; clicking an attacker again removes it.
- A big **"Attack"** confirm button (bottom right). Nothing happens until it's pressed.

## 3. Blocking (scripted scenario "Their attack")
- The opponent's attackers are slid forward with arrows pointing at you.
- **Drag one of your creatures onto an attacker** to block (it snaps to the nearest attacker, which highlights before you let go); clicking your creature then the attacker also works. Your creature slides up to face
  that attacker and a **clear line** connects them.
- Several blockers on one attacker are shown side by side, with their **damage order numbered**.
- Drag an assigned blocker back home (or click it) to unassign it. **"No blocks"** and a big **"Confirm blocks"** button.

## 4. Combat damage
- On confirm, a short, readable resolution: creatures that trade **bump** into each other, damage numbers pop, and
  creatures that die **fade and slide to the graveyard**, which ticks up. Life totals tick down for unblocked damage.
- Total resolution **under about 1.5 s**, and a setting-ready constant to speed it up.

## Where we beat Arena
- **Outcome preview before you confirm.** While declaring blocks (and attacks), show what will happen: a small skull
  on creatures that would die, the life change you'd take. You confirm knowing the result.
- **Readable at a glance:** bigger power/toughness badges, clear arrows and lines, no guessing which blocker is on
  which attacker.
- **Everything undoable until Confirm**, all by clicking.

## Fake state and controls
- Two scripted scenarios, switchable with **1 = Your attack**, **2 = Their attack**. Each: 3 lands and 3 creatures per
  side (varied power/toughness so trades, chumps and survivals all happen), a few cards in your hand.
- **R** resets the current scenario. **Esc** quits.

## Acceptance (Steve plays it)
1. The board reads instantly: whose stuff is where, lands vs creatures, life totals.
2. Playing a card from hand lands satisfyingly in the right row.
3. Declaring attackers and blockers by dragging feels natural and forgiving: drops snap, nothing needs precision.
4. Before confirming, you can see what combat will do.
5. Combat resolves quickly and clearly.

## Out of scope
Real rules, the stack and priority passing (beyond the fake targeting cards), mana payment, the opponent's AI, sounds, network.
Those come later; this is about the board and combat feel.
