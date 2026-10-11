package forge.adventure.sound;

import forge.game.card.CardView;
import forge.game.event.EventValueChangeType;
import forge.game.event.GameEvent;
import forge.game.event.GameEventAttackersDeclared;
import forge.game.event.GameEventBlockersDeclared;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventCardDamaged;
import forge.game.event.GameEventCardDestroyed;
import forge.game.event.GameEventGameOutcome;
import forge.game.event.GameEventMulligan;
import forge.game.event.GameEventPlayerLivesChanged;
import forge.game.event.GameEventShuffle;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.event.GameEventSpellCountered;
import forge.game.event.GameEventSpellResolved;
import forge.game.event.GameEventTokenCreated;
import forge.game.event.GameEventZone;
import forge.game.zone.ZoneType;
import forge.gui.events.UiEvent;
import forge.gui.events.UiEventAttackerDeclared;
import forge.gui.events.UiEventBlockerAssigned;
import forge.player.GamePlayerUtil;
import forge.sound.ISoundPlaybackHooks;
import forge.sound.SoundCategory;
import forge.sound.SoundSystem;
import forge.util.TextUtil;

import java.util.Objects;

/**
 * AU1 duel/UI event enrichment registered on {@link forge.sound.SoundSystem}.
 * Suppresses stock clips only for events we remap; everything else keeps stock behaviour.
 *
 * <p>Counter sounds come only from {@link GameEventSpellCountered} (fired by
 * {@code CounterEffect} when a spell is actually countered) — never from card-name heuristics.
 *
 * <p>Attack sound plays once on {@link GameEventAttackersDeclared} (not on click /
 * un-declare UI events). Draw / exile / damage coalesce to one clip per burst.
 * Per-card {@code SVar:SoundEffect} clips are left to the stock path.
 */
public final class Au1SoundEnrichment implements ISoundPlaybackHooks {
    public static final Au1SoundEnrichment INSTANCE = new Au1SoundEnrichment();

    private Au1SoundEnrichment() {
    }

    @Override
    public boolean handleGameEvent(GameEvent evt) {
        if (!Au1Sounds.enabled() || evt == null) {
            return false;
        }
        if (evt instanceof GameEventSpellCountered) {
            Au1Sounds.flushEventBursts();
            Au1Sounds.playCounter();
            return true;
        }
        if (evt instanceof GameEventSpellAbilityCast cast) {
            if (cast.sa() != null && cast.sa().isSpell()) {
                CardView host = cast.sa().getHostCard();
                if (host != null && host.getCurrentState() != null && !host.getCurrentState().isLand()) {
                    Au1Sounds.flushEventBursts();
                    Au1Sounds.playCast(host);
                    return true;
                }
            }
            return false;
        }
        if (evt instanceof GameEventSpellResolved resolved) {
            if (resolved.spell() == null || !resolved.spell().isSpell()) {
                return false;
            }
            // Keep stock per-card SVar:SoundEffect (ScriptedEffect) working.
            if (hasScriptedCardSound(resolved.spell().getHostCard())) {
                return false;
            }
            Au1Sounds.flushEventBursts();
            Au1Sounds.playResolve();
            return true;
        }
        if (evt instanceof GameEventCardChangeZone change) {
            ZoneType from = change.from() != null ? change.from().zoneType() : null;
            ZoneType to = change.to() != null ? change.to().zoneType() : null;
            if (from == ZoneType.Library && to == ZoneType.Hand) {
                Au1Sounds.playDrawEvent();
                return true;
            }
            if (to == ZoneType.Exile) {
                Au1Sounds.playExileEvent();
                return true;
            }
            return false;
        }
        if (evt instanceof GameEventZone zone) {
            CardView card = zone.card();
            if (zone.mode() == EventValueChangeType.Added
                    && zone.zoneType() == ZoneType.Battlefield
                    && card != null
                    && card.getCurrentState() != null
                    && card.getCurrentState().isLand()) {
                if (hasScriptedCardSound(card)) {
                    return false;
                }
                Au1Sounds.flushEventBursts();
                forge.card.ColorSet produced = card.getCurrentState().origProduceMana();
                if (produced == null) {
                    produced = card.getCurrentState().getColors();
                }
                Au1Sounds.playLand(produced);
                return true;
            }
            return false;
        }
        if (evt instanceof GameEventCardDamaged damaged) {
            Au1Sounds.playCombatDamageEvent(damaged.amount());
            return true;
        }
        if (evt instanceof GameEventCardDestroyed) {
            Au1Sounds.flushEventBursts();
            Au1Sounds.playCreatureDies();
            return true;
        }
        if (evt instanceof GameEventAttackersDeclared attackers) {
            Au1Sounds.flushEventBursts();
            // One attack sting when attacks are committed — not per click / un-declare.
            if (attackers.attackersMap() != null && !attackers.attackersMap().isEmpty()) {
                Au1Sounds.playAttackDeclared();
                return true;
            }
            return false;
        }
        if (evt instanceof GameEventBlockersDeclared blockers) {
            Au1Sounds.flushEventBursts();
            // Local human already heard the block via UiEventBlockerAssigned.
            if (blockers.defendingPlayer() != null
                    && blockers.defendingPlayer().isLobbyPlayer(GamePlayerUtil.getGuiPlayer())) {
                return true; // suppress stock; UI path already played
            }
            if (blockers.blockers() != null && blockers.blockers().values().stream().anyMatch(
                    e -> e.entries().stream().anyMatch(block -> !block.getKey().equals(block.getValue())))) {
                Au1Sounds.playBlock();
                return true;
            }
            return false;
        }
        if (evt instanceof GameEventTokenCreated) {
            Au1Sounds.flushEventBursts();
            Au1Sounds.playToken();
            return true;
        }
        if (evt instanceof GameEventPlayerLivesChanged lives) {
            Au1Sounds.flushEventBursts();
            if (lives.newLives() < lives.oldLives()) {
                Au1Sounds.playLifeLoss();
            } else if (lives.newLives() > lives.oldLives()) {
                Au1Sounds.playLifeGain();
            }
            return true;
        }
        if (evt instanceof GameEventMulligan) {
            Au1Sounds.flushEventBursts();
            Au1Sounds.playMulligan();
            return true;
        }
        if (evt instanceof GameEventShuffle) {
            Au1Sounds.flushEventBursts();
            Au1Sounds.playShuffle();
            return true;
        }
        if (evt instanceof GameEventGameOutcome outcome) {
            Au1Sounds.flushEventBursts();
            boolean humanWon = Objects.equals(outcome.winningPlayerName(),
                    GamePlayerUtil.getGuiPlayer().getName());
            if (humanWon) {
                Au1Sounds.playWin();
            } else {
                Au1Sounds.playLose();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleUiEvent(UiEvent evt) {
        if (!Au1Sounds.enabled() || evt == null) {
            return false;
        }
        // Attack clicks / un-declares are silent; sound fires on GameEventAttackersDeclared.
        if (evt instanceof UiEventAttackerDeclared) {
            return false;
        }
        if (evt instanceof UiEventBlockerAssigned block) {
            if (block.attackerBeingBlocked() != null) {
                Au1Sounds.flushEventBursts();
                Au1Sounds.playBlock();
                return true;
            }
        }
        return false;
    }

    @Override
    public float categoryVolume(SoundCategory category) {
        if (!Au1Sounds.enabled()) {
            return -1f;
        }
        return Au1Sounds.categoryVolume(category == null ? SoundCategory.EFFECTS : category);
    }

    @Override
    public boolean muteAmbience() {
        return Au1Sounds.muteOverworldAmbience();
    }

    /** True when a per-card SoundEffect resource exists (stock ScriptedEffect path). */
    public static boolean hasScriptedCardSound(CardView card) {
        if (card == null || card.getName() == null) {
            return false;
        }
        String effect = TextUtil.fastReplace(TextUtil.fastReplace(
                TextUtil.fastReplace(card.getName(), ",", ""),
                " ", "_"), "'", "").toLowerCase();
        return SoundSystem.instance.getSoundResource(effect) != null;
    }
}
