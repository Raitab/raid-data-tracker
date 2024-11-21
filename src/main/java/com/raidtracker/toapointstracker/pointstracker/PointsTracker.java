/*
BSD 2-Clause License

Copyright (c) 2022, LlemonDuck
Copyright (c) 2022, TheStonedTurtle
Copyright (c) 2019, Ron Young <https://github.com/raiyni>
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

import com.raidtracker.RaidTrackerConfig;
import com.raidtracker.RaidTrackerPlugin;
import com.raidtracker.RaidType;
import com.raidtracker.toapointstracker.module.PluginLifecycleComponent;
import com.raidtracker.toapointstracker.util.RaidRoom;
import com.raidtracker.toapointstracker.util.RaidState;
import com.raidtracker.toapointstracker.util.RaidStateChanged;
import com.raidtracker.toapointstracker.util.RaidStateTracker;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.Varbits;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PointsTracker implements PluginLifecycleComponent
{

	/* I have some insider knowledge here that the blog was describing points earning slightly wrong wrt deaths.
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

	private static final int ANIMATION_ID_WARDEN_DOWN = 9670;

	private static final Map<Integer, Double> DAMAGE_POINTS_FACTORS = ImmutableMap.<Integer, Double>builder()
		.put(NpcID.CORE, 0.0)
		.put(NpcID.CORE_11771, 0.0)
		.put(NpcID.ENERGY_SIPHON, 0.0)
		.put(NpcID.BOULDER_11782, 0.0)
		.put(NpcID.BOULDER_11783, 0.0)
		.put(NpcID.BABOON_BRAWLER, 1.2)
		.put(NpcID.BABOON_BRAWLER_11712, 1.2)
		.put(NpcID.BABOON_THROWER, 1.2)
		.put(NpcID.BABOON_THROWER_11713, 1.2)
		.put(NpcID.BABOON_MAGE, 1.2)
		.put(NpcID.BABOON_MAGE_11714, 1.2)
		.put(NpcID.BABOON_SHAMAN, 1.2)
		.put(NpcID.VOLATILE_BABOON, 1.2)
		.put(NpcID.CURSED_BABOON, 1.2)
		.put(NpcID.BABOON_THRALL, 1.2)
		.put(NpcID.BABA, 2.0)
		.put(NpcID.BABA_11779, 2.0)
		.put(NpcID.BABA_11780, 2.0)
		.put(NpcID.ZEBAK, 1.5)
		.put(NpcID.ZEBAK_11730, 1.5)
		.put(NpcID.ZEBAK_11732, 1.5)
		.put(NpcID.ZEBAK_11733, 1.5)
		.put(NpcID.SPITTING_SCARAB, 0.5)
		.put(NpcID.SOLDIER_SCARAB, 0.5)
		.put(NpcID.ARCANE_SCARAB, 0.5)
		.put(NpcID.HETS_SEAL_WEAKENED, 2.5)
		.put(NpcID.OBELISK_11750, 1.5)
		.put(NpcID.OBELISK_11751, 1.5)
		.put(NpcID.OBELISK_11752, 1.5)
		.put(NpcID.ELIDINIS_WARDEN, 0.0) // non-combat wardens (prevents extra points during p1->p2 transition)
		.put(NpcID.ELIDINIS_WARDEN_11748, 0.0)
		.put(NpcID.TUMEKENS_WARDEN, 0.0)
		.put(NpcID.TUMEKENS_WARDEN_11749, 0.0)
		.put(NpcID.ELIDINIS_WARDEN_11759, 0.0)
		.put(NpcID.TUMEKENS_WARDEN_11760, 0.0)
		.put(NpcID.ELIDINIS_WARDEN_11753, 2.0) // p2 wardens
		.put(NpcID.ELIDINIS_WARDEN_11754, 2.0)
		.put(NpcID.ELIDINIS_WARDEN_11755, 0.0) // downed
		.put(NpcID.TUMEKENS_WARDEN_11756, 2.0)
		.put(NpcID.TUMEKENS_WARDEN_11757, 2.0)
		.put(NpcID.TUMEKENS_WARDEN_11758, 0.0) // downed
		.put(NpcID.ELIDINIS_WARDEN_11761, 2.5) // p3 wardens
		.put(NpcID.ELIDINIS_WARDEN_11763, 2.5)
		.put(NpcID.TUMEKENS_WARDEN_11762, 2.5)
		.put(NpcID.TUMEKENS_WARDEN_11764, 2.5)
		.build();

	// these have a cap at 3 "downs"
	private static final ImmutableSet<Integer> P2_WARDENS = ImmutableSet.of(
		NpcID.ELIDINIS_WARDEN_11753,
		NpcID.ELIDINIS_WARDEN_11754,
		NpcID.ELIDINIS_WARDEN_11755,
		NpcID.TUMEKENS_WARDEN_11756,
		NpcID.TUMEKENS_WARDEN_11757,
		NpcID.TUMEKENS_WARDEN_11758
	);

	private static final ImmutableSet<Integer> MVP_ITEMS = ImmutableSet.of(
		ItemID.FANG_27219,
		ItemID.SCARAB_DUNG,
		ItemID.BIG_BANANA,
		ItemID.ELDRITCH_ASHES
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
		raidLevel = client.getVarbitValue(Varbits.TOA_RAID_LEVEL);
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

		if (e.getActor().getAnimation() == ANIMATION_ID_WARDEN_DOWN)
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
