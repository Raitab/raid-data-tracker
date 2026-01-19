/*
BSD 2-Clause License

Copyright (c) 2022, LlemonDuck
Copyright (c) 2022, TheStonedTurtle
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.raidtracker.toapointstracker.pointstracker;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.raidtracker.RaidTrackerConfig;
import com.raidtracker.RaidTrackerPlugin;
import com.raidtracker.RaidType;
import com.raidtracker.toapointstracker.module.PluginLifecycleComponent;
import com.raidtracker.toapointstracker.util.RaidRoom;
import com.raidtracker.toapointstracker.util.RaidState;
import com.raidtracker.toapointstracker.util.RaidStateChanged;
import com.raidtracker.toapointstracker.util.RaidStateTracker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PointsTracker implements PluginLifecycleComponent
{

	/*
	 * I have some insider knowledge here that the blog was describing points earning slightly wrong wrt deaths.
	 * Points are earned to both total and room points at the same time,
	 * rather than being queued up in room points and added onto total after the room.
	 * When dying, you preserve the room points amount toward cap, but subtract 20% from total.
	 * There is no special behaviour when wiping a room; the 20% points lost is intended to account for that.
	 */

	private static final String START_MESSAGE = "You enter the Tombs of Amascut";
	private static final String DEATH_MESSAGE = "You have died";
	private static final String ROOM_FAIL_MESSAGE = "Your party failed to complete";
	private static final String ROOM_FINISH_MESSAGE = "Challenge complete";

	private static final int BASE_POINTS = 5000;
	private static final int MAX_ROOM_POINTS = 20_000;
	private static final int CRONDIS_MAX_ROOM_POINTS = 10_000;

	private static final int MAX_TOTAL_POINTS = 64_000;

	private static final Map<Integer, Double> DAMAGE_POINTS_FACTORS = ImmutableMap.<Integer, Double>builder()
        .put(NpcID.TOA_WARDEN_TUMEKEN_CORE, 0.0)
        .put(NpcID.TOA_WARDEN_ELIDINIS_CORE, 0.0)
        .put(NpcID.WARDENS_P3_ORB_BLUE, 0.0) // no red orb?

        .put(NpcID.TOA_BABA_BOULDER, 0.0)
        .put(NpcID.TOA_BABA_BOULDER_WEAK, 0.0)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_MELEE_1, 1.2)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_MELEE_2, 1.2)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_RANGED_1, 1.2)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_RANGED_2, 1.2)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_MAGIC_1, 1.2)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_MAGIC_2, 1.2)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_SHAMAN, 1.2)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_ZOMBIE, 1.2)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_CURSED, 1.2)
        .put(NpcID.TOA_PATH_APMEKEN_BABOON_THRALL, 1.2)
        .put(NpcID.TOA_BABA, 2.0)
        .put(NpcID.TOA_BABA_COFFIN, 2.0)
        .put(NpcID.TOA_BABA_DIGGING, 2.0)

        .put(NpcID.TOA_ZEBAK_TRANSMOG, 1.5)
        .put(NpcID.TOA_ZEBAK, 1.5)
        .put(NpcID.TOA_ZEBAK_ENRAGED, 1.5)
        .put(NpcID.TOA_ZEBAK_DEAD, 1.5)

        .put(NpcID.TOA_KEPHRI_GUARDIAN_RANGED, 0.5)
        .put(NpcID.TOA_KEPHRI_GUARDIAN_MELEE, 0.5)
        .put(NpcID.TOA_KEPHRI_GUARDIAN_MAGE, 0.5)

        .put(NpcID.TOA_HET_GOAL_VULNERABLE, 2.5)

        .put(NpcID.TOA_WARDENS_P1_OBELISK_NPC_INACTIVE, 1.5)
        .put(NpcID.TOA_WARDENS_P1_OBELISK_NPC, 1.5)
        .put(NpcID.TOA_WARDENS_P2_OBELISK_NPC, 1.5)
        // non-combat wardens (prevents extra points during p1->p2 transition)
        .put(NpcID.TOA_WARDEN_ELIDINIS_PHASE1_INACTIVE, 0.0)
        .put(NpcID.TOA_WARDEN_ELIDINIS_PHASE1, 0.0)
        .put(NpcID.TOA_WARDEN_TUMEKEN_PHASE1_INACTIVE, 0.0)
        .put(NpcID.TOA_WARDEN_TUMEKEN_PHASE1, 0.0)

        // Phase 2
        .put(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE, 2.0)
        .put(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE, 2.0)
        .put(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED, 0.0) // downed
        .put(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, 2.0)
        .put(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE, 2.0)
        .put(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED, 0.0) // downed

        // Phase 3
        .put(NpcID.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE, 0.0)
        .put(NpcID.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE, 0.0)
        .put(NpcID.TOA_WARDEN_ELIDINIS_PHASE3, 2.5)
        .put(NpcID.TOA_WARDEN_TUMEKEN_PHASE3, 2.5)
        .put(NpcID.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING, 2.5)
        .put(NpcID.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING, 2.5)

		.build();

	// these have a cap at 3 "downs"
	private static final ImmutableSet<Integer> P2_WARDENS = ImmutableSet.of(
        NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE,
        NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE,
        NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED,
        NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE,
        NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE,
        NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED
	);

	private static final ImmutableSet<Integer> MVP_ITEMS = ImmutableSet.of(
        ItemID.TOA_ZEBAK_FANG,
        ItemID.TOA_KEPHRI_POO,
        ItemID.TOA_BABA_BANANA,
        ItemID.TOA_AKKHA_ASHES
	);

	private final EventBus eventBus;
	private final Client client;
	private final RaidTrackerConfig config;
	private final RaidStateTracker raidStateTracker;

	@Inject
	private RaidTrackerPlugin raidTrackerPlugin;

	@Getter
	private int personalRoomPoints;
	private int personalTotalPoints;
	private int nonPartyPoints; // points that are earned once by the entire party
	private final List<Integer> seenMvpItems = new ArrayList<>(4);

	@Getter
	private int teamSize;
	private int raidLevel;
	private int wardenDowns;

	@Override
	public boolean isEnabled(RaidTrackerConfig config, RaidState raidState)
	{
		// always track even if not displaying, so that party members get points totals
		return raidState.isInRaid();
	}

	@Override
	public void startUp()
	{
		eventBus.register(this);

		reset();
	}

	@Override
	public void shutDown()
	{
		eventBus.unregister(this);
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		raidLevel = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
	}

	@Subscribe
	public void onRaidStateChanged(RaidStateChanged e)
	{
		teamSize = e.getNewState().getPlayerCount();
		if (e.getPreviousState() == null || e.getPreviousState().getCurrentRoom() == null)
		{
			return;
		}

		switch (e.getPreviousState().getCurrentRoom())
		{
			case TOMB:
				raidTrackerPlugin.updateCurrentRT(RaidType.TOA);
				break;

			// puzzle estimates
			case SCABARAS:
				personalTotalPoints += 300;
				nonPartyPoints += 300;
				break;

			case APMEKEN:
				personalTotalPoints += 450;
				nonPartyPoints += 300;
				break;

			case CRONDIS:
				personalTotalPoints += 400;
				nonPartyPoints += 300;
				break;

			case HET:
			case WARDENS:
				nonPartyPoints += 300;
				break;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (e.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		if (e.getMessage().startsWith(START_MESSAGE))
		{
			reset();
		}
		else if (e.getMessage().startsWith(DEATH_MESSAGE))
		{
			personalTotalPoints -= (int) Math.max(0.2 * personalTotalPoints, 1000);
			if (personalTotalPoints < 0)
			{
				personalTotalPoints = 0;
			}

		}
		else if (e.getMessage().startsWith(ROOM_FAIL_MESSAGE))
		{
			wardenDowns = 0;
		}
		else if (e.getMessage().startsWith(ROOM_FINISH_MESSAGE))
		{
			personalRoomPoints = 0;

			boolean isWardens = e.getMessage().contains("Wardens");
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		if (e.getHitsplat().getAmount() < 1 || !(e.getActor() instanceof NPC))
		{
			return;
		}

		NPC target = (NPC) e.getActor();
		log.debug("Hitsplat type {} damage {} on {}", e.getHitsplat().getHitsplatType(), e.getHitsplat().getAmount(), target.getId());
		if (P2_WARDENS.contains(target.getId()) && wardenDowns > 3)
		{
			return;
		}

		double factor = DAMAGE_POINTS_FACTORS.getOrDefault(target.getId(), 1.0);
		if (e.getHitsplat().isMine())
		{
			int pointsEarned = (int) (e.getHitsplat().getAmount() * factor);
			int roomMax = raidStateTracker.getCurrentState().getCurrentRoom() == RaidRoom.CRONDIS ? CRONDIS_MAX_ROOM_POINTS : MAX_ROOM_POINTS;
			if (personalRoomPoints + pointsEarned > roomMax)
			{
				pointsEarned = roomMax - personalRoomPoints;
			}

			this.personalRoomPoints = Math.min(roomMax, personalRoomPoints + pointsEarned);
			this.personalTotalPoints = Math.min(MAX_TOTAL_POINTS, personalTotalPoints + pointsEarned);
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned e)
	{
		if (MVP_ITEMS.contains(e.getItem().getId()) && !seenMvpItems.contains(e.getItem().getId()))
		{
			personalTotalPoints += 300 * teamSize;
			seenMvpItems.add(e.getItem().getId());
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		if (!(e.getActor() instanceof NPC) || !P2_WARDENS.contains(((NPC) e.getActor()).getId()))
		{
			return;
		}

		if (e.getActor().getAnimation() == AnimationID.NPC_WARDENS_KNEEL01)
		{
			wardenDowns++;
		}
	}

	public int getPersonalTotalPoints()
	{
		return this.personalTotalPoints - BASE_POINTS;
	}

	public double getPersonalPercent()
	{
		if (raidStateTracker.getPlayerCount() == 1)
		{
			return 1.0;
		}

		return (double) getPersonalTotalPoints() / getTotalPoints();
	}

	public int getTotalPoints()
	{
		return getPersonalTotalPoints() + nonPartyPoints;
	}

	private void reset()
	{
		this.personalTotalPoints = BASE_POINTS;
		this.personalRoomPoints = 0;
		this.nonPartyPoints = 0;
		this.teamSize = 0;
		this.raidLevel = -1;
		this.wardenDowns = 0;
		this.seenMvpItems.clear();
	}

}
