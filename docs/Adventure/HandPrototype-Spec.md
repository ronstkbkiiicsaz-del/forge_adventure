# Hand prototype: Arena / Slay the Spire feel (spec for Grok, 2026-10-11)

**Purpose.** A tiny standalone libGDX window that proves the in-hand card feel before any more duel-screen work.
No Forge game, no rules, no network: just seven card images, the mouse, and motion. Steve tries it for a minute and
says "yes, that's it" or "no". If yes, this code becomes the hand component of the duel screen (DS1 is paused).

**Play notes (Steve, 2026-10-11, ~17:53 Phoenix).** Keep the responsiveness and the smooth cast. Reverse the resting
fan: rotation still runs −12° (far left) to +12° (far right) with outer cards lower on the arc (radius about 2.5×
hand width), which brings the tops together. Bottoms should converge and tops should spread, like cards held in a
hand. On hover, scale up and spread the **whole hand** so cards are not covered (the hovered card still rises on
top). Follow-up is **HP1** in `docs/HANDOFF.md`; that file is the source of truth. Do not redo the rest.

**Play notes, round 2 (Steve, 2026-10-11, ~23:20 Phoenix, tested commit 91d2365b2e).** *"So much better than
before."* The fan is good; keep it. **One fix: the hand opens too eagerly.** Any mouse position near the bottom of the
window pops it up. Tighten the hover zone to the **cards themselves**: the hand should only open when the cursor is
over a card's visible area (plus a small margin, about 0.02H), not across a wide band along the bottom edge. The next
prototype is the battlefield and combat: see `BattlefieldPrototype-Spec.md`.

**Reference feel.** MTG Arena's hand and Slay the Spire's hand (Slay the Spire is Java/libGDX, so this is achievable
in our stack). The point is the *behaviour*, not the layout.

## Deliverable

- A new launcher class in `forge-gui-mobile-dev` (e.g. `forge.app.HandPrototype` with its own `main`), plus
  `hand-prototype.cmd` at the repo root that runs it like `play-adventure.cmd` does. One window, 1920x1080
  (resizable; everything scales from screen height).
- Card art: load 7 images from the user's Forge image cache (`%LOCALAPPDATA%\Forge\Cache\pics\cards\**\*.jpg`, pick
  any 7; fall back to drawing labelled grey rectangles if fewer exist). No downloads.
- A plain dark table background, a faint horizontal **cast line**, and a small text counter "Cast: N" in a corner.
- A "Reset hand" key (R) that returns all cards to the hand. Esc quits.
- Self-contained: under ~400 lines, no changes to existing classes except adding the launcher.

## Behaviour (all numbers are starting values in one constants block so they're easy to tune)

Sizes use H = window height. A card's full size is **0.28H tall** (Arena-like), aspect 63:88.

### 1. Resting hand (nothing hovered)
- The hand sits **tucked low**: cards drawn at **60% of full size**, centred horizontally, their tops at about
  **0.14H above the bottom edge** so most of each card is below the screen edge (only the top third shows).
- Cards **overlap** (each card's visible strip about 45% of its width) in a gentle **arc**: rotation from -12° on
  the far left to +12° on the far right, outer cards slightly lower (an arc radius of about 2.5x the hand width).
- The hand takes no more than **55% of the window width**; with more cards, overlap increases instead.

### 2. Hover (mouse over a card in the hand)
- The hovered card **rises and grows**: it scales to **100% full size**, straightens to **0° rotation**, and lifts so
  its bottom edge sits **0.02H above the screen bottom** (fully visible). It is drawn on top of all others.
- Its **neighbours spread apart** sideways to make room (each side shifts out by about 0.35 of a full card width,
  falling off for cards further away), so the hovered card isn't covered.
- Moving the mouse along the hand **slides the hover** from card to card smoothly; there's no flicker when the mouse
  is between two cards (hover goes to the card whose centre column is nearest).
- When the mouse leaves the hand area (more than 0.05H above the hovered card's top, or outside the hand's width),
  everything **eases back** to the resting tucked hand.
- All motion is **eased**, never instant: position, scale and rotation each move toward their targets with an
  exponential approach (e.g. `value += (target - value) * (1 - exp(-delta * 14))`). Hover in and out should take
  about 0.12-0.15 s.

### 3. Drag to cast
- **Press and hold** on a hovered card and move: the card **follows the cursor** (centred on it, slight lag via the
  same easing) at full size, tilting a few degrees in the direction of motion (max ±8°) and settling back to 0°.
- A **cast line** sits at **0.45H from the bottom**. While the dragged card's centre is below the line, the card has
  its normal look. Once it's **above the line**, it shows a "will cast" state: a soft glow outline and the line
  brightens.
- **Release above the line → cast**: the card flies to the table centre (0.15 s), shrinks to 40%, fades out over
  0.25 s, the counter goes up by 1, and the hand **re-flows** smoothly to close the gap.
- **Release below the line → cancel**: the card **snaps back** into its slot in the hand (eased, about 0.2 s).
- A drag that moves **less than 12 px** before release is **not a drag** (see tap below). Casting must never happen
  by accident from a click.

### 4. Tap / click to inspect
- A quick click on a card (press and release with under 12 px of movement and under 250 ms) **does not cast**. It
  opens a **zoom**: the card shown large in the centre (0.75H tall) over a dimmed table. Click anywhere or press Esc
  to close it. While zoomed, nothing else reacts.
- Right-click on a card also opens the zoom.

### 5. Keyboard (nice to have)
- Left/Right moves the hover across the hand; Enter starts a "cast" of the hovered card (same fly animation);
  Space zooms the hovered card.

## Acceptance (Steve tries it)
1. Resting: the hand is mostly out of the way; the table is visible.
2. Moving over the hand: one card at a time rises and straightens, neighbours make room, no jitter.
3. Dragging up past the line and letting go casts; letting go short of it snaps back; a plain click never casts.
4. Everything moves smoothly, nothing pops.

## Out of scope
Game logic, mana, targets, the stack, the opponent, Forge's existing match screen, settings screens, sounds.
