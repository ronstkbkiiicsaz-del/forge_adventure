# Alive slice: make one small piece of the world feel real (next for Reed)

*Steve + Wren, 2026-10-14. Comes **before** any more world scale, digging or building.*

## Why

Steve walked the stage-1 plane and felt **overwhelmed**: "it's all just paint and doesn't feel real ... there is no
life in it." Stage 1 proved walking. But 2000x2000 tiles where nothing moves, nothing sounds and nobody lives reads
as a lonely painting, and the bigger it is, the emptier it feels. Stardew and Core Keeper get away with size
because the part you are standing in is always full.

So we **shrink the focus**: make one five-minute walk feel alive, then grow outward.

## Scope: Havenbrook, the road, and the plains market town. Nothing else.

- Start the player in Havenbrook. The plains market town (`plains_market_town.tmx`) sits a short walk away on a
  road, about **1-2 minutes of walking**: move it closer if it's further.
- The rest of the plane can stay as it is. Don't polish it, and don't show the whole plane in the map yet: fog of
  war covers it.

## What "alive" means (all of these)

**People**
- 6-10 townsfolk in each town, using the existing townsfolk sprites (`sprites/townsfolk/*.atlas`: Walk/Idle
  animations).
- Each one walks a simple route between a few points, pauses and idles. Use the towns' `npc_spot` objects as
  anchors: the smith stays near the smithy, the farmer in the fields, children run around the square.
- 1-2 travellers walk the road between the two towns.
- Talking isn't needed yet. Facing the player and a one-line speech bubble on click is enough.

**Animals** (Ninja Adventure `Actor/Animal`, all animated, CC0)
- Cows, pigs, chickens and a donkey in the plains town's pens: they wander inside the pen and idle.
- A dog or a cat in each town.
- Birds that take off as you walk near.
- A frog or fish near water if there is any.

**Motion in the scenery**
- The windmill sails turn.
- Tree canopies and grass sway slightly (a subtle shader or a 2-frame swap is fine).
- Smoke from one or two chimneys and from the smithy.
- Butterflies in the fields.
- Water animates.

**Sound** (Ninja Adventure `Audio/`, Kenney packs; CC0)
- Footsteps that change on grass, dirt and wood.
- A wind and birdsong ambient bed outdoors.
- Town music that fades in near a town and out on the road. Candidates: Ninja `33 - Calm Village`, `36 - Village`
  and `23 - Road`.
- Point sounds near their source: the smithy anvil, livestock, the mill, the well.

**Light and time**
- A simple day tint that shifts over the SN1 day: warm morning, bright noon, orange evening, blue night.
- Lit windows and lanterns at night.
- Townsfolk go indoors at night: they fade out at their door.

**One bit of danger**
- One or two enemies wander the field edges off the road: Ninja `Actor/Monster`, or the existing Forge enemy
  sprites.
- Touching one can just show "Duel would start here" until stage 2's DuelBridge exists.

## Not in this slice

Interiors, shops working, quests, dialogue trees, gathering, digging, building, other biomes, the rest of the plane.

## Done when

Steve walks from Havenbrook to the market town and back, about five minutes. He sees people going about their day,
animals in the pens, smoke, turning sails and birds lifting off. He hears footsteps, birds, the town music swelling,
and the anvil. He watches evening come and the windows light up. And it **feels like a place**.

He tries it, and his feel notes win over this spec. Ship it in small pieces he can try: people first, then animals,
then sound, then light.

## Credits

Ninja Adventure is CC0, and the Kenney packs are CC0. Add the entries to `CREDITS.md` when the assets move in.
