package com.raidtracker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.raidtracker.archive.ArchiveBoss;
import com.raidtracker.archive.ArchiveContribution;
import com.raidtracker.archive.FracturedArchiveSession;
import com.raidtracker.filereadwriter.FileReadWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import junit.framework.TestCase;
import net.runelite.api.Client;
import net.runelite.api.HitsplatID;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ArchiveContributionTest extends TestCase
{
	private static final String GUARDIAN = "Challenge complete: Archive Guardian. Duration: 1:00.00. Total: 1:00.00";
	private static final String EZEKIEL = "Challenge complete: Ezekiel. Duration: 1:00.00. Total: 2:00.00";
	private static final String OCELLUS = "Challenge complete: Ocellus. Duration: 1:00.00. Total: 3:00.00";

	@Mock
	private Client client;

	private Path root;
	private FileReadWriter fw;
	private RaidTracker raid;
	private FracturedArchiveSession session;

	@Before
	public void setUp() throws IOException
	{
		root = Files.createTempDirectory("archive-contribution");
		fw = new FileReadWriter();
		fw.setGson(new Gson());
		fw.setStorageRoot(root.toFile());
		fw.updateUsername("Disco Turtle");
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		raid = new RaidTracker();
		session = new FracturedArchiveSession(client, () -> raid, () -> fw);
	}

	@After
	public void tearDown() throws IOException
	{
		if (root == null || !Files.exists(root))
		{
			return;
		}
		Files.walk(root)
			.sorted(Comparator.reverseOrder())
			.forEach(path -> {
				try
				{
					Files.deleteIfExists(path);
				}
				catch (IOException ignored)
				{
				}
			});
	}

	@Test
	public void everyHitExceptAZeroCountsTowardThePhase()
	{
		ArchiveContribution contribution = new ArchiveContribution();
		contribution.beginTrackedRaid();
		contribution.addHit(true, 40);
		contribution.addHit(false, 60);
		contribution.addHit(false, 500);
		contribution.addHit(true, 0);
		contribution.closeRoom(ArchiveBoss.ARCHIVE_GUARDIAN);
		// 100 * 40 / 600
		assertEquals(6d, contribution.storedPoints(), 0.0001);
		assertEquals(Integer.valueOf(0), contribution.storedDeaths());
	}

	@Test
	public void roomsUseTheirWeightsAndAMissedRoomAddsZero()
	{
		ArchiveContribution contribution = new ArchiveContribution();
		contribution.beginTrackedRaid();
		contribution.addHit(true, 25);
		contribution.addHit(false, 75);
		contribution.closeRoom(ArchiveBoss.ARCHIVE_GUARDIAN);
		contribution.closeRoom(ArchiveBoss.EZEKIEL);
		contribution.addHit(true, 10);
		contribution.addHit(false, 30);
		contribution.closeRoom(ArchiveBoss.OCELLUS);
		// 100 * 25/100 + 0 + 52 * 10/40
		assertEquals(38d, contribution.storedPoints(), 0.0001);
	}

	@Test
	public void ocellusPhasesUseTheirWeightsOnce()
	{
		ArchiveContribution contribution = new ArchiveContribution();
		contribution.beginTrackedRaid();
		contribution.closeRoom(ArchiveBoss.ARCHIVE_GUARDIAN);
		contribution.addHit(true, 60);
		contribution.addHit(false, 40);
		contribution.closeOcellusPhase(1);
		contribution.closeOcellusPhase(1);
		contribution.addHit(true, 10);
		contribution.addHit(false, 90);
		contribution.closeOcellusPhase(2);
		contribution.addHit(true, 50);
		contribution.addHit(false, 50);
		contribution.noteOcellusQuadrant();
		contribution.noteOcellusQuadrant();
		contribution.noteOcellusQuadrant();
		contribution.addHit(true, 20);
		contribution.addHit(false, 80);
		contribution.closeRoom(ArchiveBoss.OCELLUS);
		// 60*60/100 + 30*10/100 + 158*50/100 + 52*20/100
		assertEquals(128d, contribution.storedPoints(), 0.0001);
	}

	@Test
	public void aLaterBossFirstOmitsTheRaid()
	{
		ArchiveContribution contribution = new ArchiveContribution();
		contribution.beginTrackedRaid();
		contribution.addOwnDeath(0);
		contribution.addHit(true, 50);
		contribution.closeRoom(ArchiveBoss.EZEKIEL);
		contribution.addHit(true, 50);
		contribution.closeRoom(ArchiveBoss.OCELLUS);
		assertNull(contribution.storedPoints());
		assertNull(contribution.storedDeaths());
		assertTrue(contribution.hasOwnDeath());
	}

	@Test
	public void eachOwnDeathLineCountsOnce()
	{
		ArchiveContribution contribution = new ArchiveContribution();
		contribution.beginTrackedRaid();
		contribution.addOwnDeath(0);
		contribution.addOwnDeath(0);
		assertEquals(Integer.valueOf(2), contribution.storedDeaths());
		assertEquals(0d, contribution.storedPoints(), 0.0001);
	}

	@Test
	public void anOwnDeathSubtractsAnEvenSplitTenthAndStopsAtMinusFifty()
	{
		ArchiveContribution contribution = new ArchiveContribution();
		contribution.beginTrackedRaid();
		contribution.addOwnDeath(4);
		contribution.addOwnDeath(4);
		assertEquals(-34d, contribution.storedPoints(), 0.0001);

		contribution.reset();
		contribution.beginTrackedRaid();
		contribution.addOwnDeath(1);
		assertEquals(-50d, contribution.storedPoints(), 0.0001);
	}

	@Test
	public void pointsLineStoresWhenTheTimerWasAlreadyOpen() throws IOException
	{
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage(OCELLUS, true);
		session.noteExternalPoints(194);
		session.setPartySize(4);
		assertTrue(session.claim());

		JsonObject line = loggedLine();
		assertEquals(194d, line.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(0, line.get("ownDeaths").getAsInt());
		assertEquals(4, line.get("teamSize").getAsInt());
	}

	@Test
	public void thePointsLineStoresPersonalPointsAndZeroDeaths() throws IOException
	{
		session.observeHud(false, false);
		session.onGameMessage("You have died. Total deaths: 9.", false);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.onGameMessage(OCELLUS, true);
		session.noteExternalPoints(1234);
		assertTrue(session.claim());

		JsonObject line = loggedLine();
		assertEquals(1234d, line.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(0, line.get("ownDeaths").getAsInt());
	}

	@Test
	public void twoOwnDeathLinesStoreTwoAndIgnoreTheTeamTotal() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage("You have died. Total deaths: 9.", true);
		session.onGameMessage("You have died. Total deaths: 1.", true);
		session.onGameMessage(GUARDIAN, true);
		assertTrue(session.claim());

		JsonObject line = loggedLine();
		assertEquals(0d, line.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(2, line.get("ownDeaths").getAsInt());
		assertFalse(line.has("deaths"));
	}

	@Test
	public void aTeammateDeathDoesNotKeepAnEmptyAttempt() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage("Someone has died. Total deaths: 4.", true);
		session.setPartySize(4);
		session.observeHud(false, false);
		session.observeHud(false, false);
		session.setPartySize(4);
		assertTrue(session.claim());

		JsonObject line = loggedLine();
		assertFalse(line.has("teamSize"));
		assertFalse(line.has("ownDeaths"));
		assertFalse(line.has("contributionPoints"));
	}

	@Test
	public void anOwnDeathKeepsTheAttempt() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.setPartySize(3);
		session.onGameMessage("You have died. Total deaths: 8.", true);
		session.observeHud(false, false);
		session.observeHud(false, false);
		assertTrue(session.claim());

		JsonObject line = loggedLine();
		assertEquals(3, line.get("teamSize").getAsInt());
		assertEquals(-23d, line.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(1, line.get("ownDeaths").getAsInt());
	}

	@Test
	public void aDeathBeforeTheTimerOpensDoesNotKeepTheAttempt() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage("You have died. Total deaths: 5.", false);
		session.setPartySize(2);
		session.observeHud(false, false);
		session.observeHud(false, false);
		session.setPartySize(2);
		assertTrue(session.claim());

		assertFalse(loggedLine().has("teamSize"));
	}

	@Test
	public void aDeathBeforeTheTimerSampleStillStartsTheRaid() throws IOException
	{
		session.observeHud(false, false);
		session.onGameMessage("You have died. Total deaths: 4.", true);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.noteExternalPoints(194);
		assertTrue(session.claim());

		JsonObject line = loggedLine();
		assertEquals(194d, line.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(1, line.get("ownDeaths").getAsInt());
	}

	@Test
	public void theNextRaidStartsWhileTheChestLineIsOpen() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.onGameMessage("The Fractured Archive: Total completion time: 12:00.00.", false);
		session.noteExternalPoints(100);
		assertTrue(session.claim());

		session.observeHud(false, true);
		session.onGameMessage("You have died. Total deaths: 2.", true);
		session.observeHud(true, true);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 1:30.00. Total: 1:30.00", true);
		session.noteExternalPoints(30);
		assertTrue(session.claim());

		List<String> lines = logLines();
		assertEquals(2, lines.size());
		JsonObject first = new JsonParser().parse(lines.get(0)).getAsJsonObject();
		JsonObject second = new JsonParser().parse(lines.get(1)).getAsJsonObject();
		assertEquals(100d, first.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(0, first.get("ownDeaths").getAsInt());
		assertEquals(30d, second.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(1, second.get("ownDeaths").getAsInt());
		assertEquals(90, second.get("archiveGuardianTime").getAsInt());
	}

	@Test
	public void aTimerDropWhileTheChestLineIsOpenKeepsTheNextRaid() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.onGameMessage("The Fractured Archive: Total completion time: 12:00.00.", false);
		session.noteExternalPoints(100);
		assertTrue(session.claim());

		session.observeHud(false, true);
		session.observeHud(true, true);
		session.onGameMessage("You have died. Total deaths: 2.", true);
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 1:30.00. Total: 1:30.00", true);
		session.noteExternalPoints(30);
		assertTrue(session.claim());

		List<String> lines = logLines();
		assertEquals(2, lines.size());
		JsonObject second = new JsonParser().parse(lines.get(1)).getAsJsonObject();
		assertEquals(30d, second.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(1, second.get("ownDeaths").getAsInt());
	}

	@Test
	public void theNextRaidStartsWhileTheCompletionIsOnlyInMemory() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.onGameMessage("You have died. Total deaths: 1.", true);
		session.onGameMessage("The Fractured Archive: Total completion time: 12:00.00.", false);
		session.noteExternalPoints(100);
		session.observeHud(false, false);
		session.onGameMessage("You have died. Total deaths: 2.", true);
		session.observeHud(true, false);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 1:30.00. Total: 1:30.00", true);
		session.noteExternalPoints(40);
		assertTrue(session.claim());

		List<String> lines = logLines();
		assertEquals(2, lines.size());
		JsonObject first = new JsonParser().parse(lines.get(0)).getAsJsonObject();
		JsonObject second = new JsonParser().parse(lines.get(1)).getAsJsonObject();
		assertEquals(100d, first.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(1, first.get("ownDeaths").getAsInt());
		assertEquals(40d, second.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(1, second.get("ownDeaths").getAsInt());
		assertEquals(90, second.get("archiveGuardianTime").getAsInt());
		assertFalse(first.has("deaths"));
	}

	@Test
	public void aTimerFlickerBeforeTheChestKeepsTheTrackedPoints() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.onGameMessage("The Fractured Archive: Total completion time: 12:00.00.", false);
		session.noteExternalPoints(194);
		session.observeHud(false, false);
		session.observeHud(true, false);
		assertTrue(session.claim());

		JsonObject line = loggedLine();
		assertEquals(194d, line.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(0, line.get("ownDeaths").getAsInt());
	}

	@Test
	public void pointsLineKeepsDeathsWhenTheFirstBossIsNotTheGuardian() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.setPartySize(5);
		session.onGameMessage("You have died. Total deaths: 2.", true);
		session.onGameMessage(EZEKIEL, true);
		session.onGameMessage(OCELLUS, true);
		session.noteExternalPoints(194);
		session.observeHud(false, false);
		session.observeHud(false, false);
		assertTrue(session.claim());

		JsonObject line = loggedLine();
		assertEquals(5, line.get("teamSize").getAsInt());
		assertEquals(194d, line.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(1, line.get("ownDeaths").getAsInt());
		assertTrue(line.has("ezekielTime"));
	}

	@Test
	public void pointsLineUpdatesALineAlreadyWritten() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.onGameMessage("The Fractured Archive: Total completion time: 12:00.00.", false);
		assertTrue(session.claim());
		session.noteExternalPoints(194);

		JsonObject line = loggedLine();
		assertEquals(194d, line.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(0, line.get("ownDeaths").getAsInt());
	}

	@Test
	public void aDeathBeforeTheTimerStartsStillTakesThePenalty() throws IOException
	{
		session.observeHud(false, false);
		session.onGameMessage("You have died. Total deaths: 1.", true);
		session.setPartySize(4);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		assertTrue(session.claim());

		JsonObject line = loggedLine();
		assertEquals(-17d, line.get("contributionPoints").getAsDouble(), 0.0001);
		assertEquals(1, line.get("ownDeaths").getAsInt());
	}

	@Test
	public void aNegativeExternalTotalIsStored() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.noteExternalPoints(-50);
		assertTrue(session.claim());

		assertEquals(-50d, loggedLine().get("contributionPoints").getAsDouble(), 0.0001);
	}

	@Test
	public void anOcellusNpcBeforeTheRaidDoesNotBlockTheNextTally() throws IOException
	{
		session.onOcellusNpc(NpcID.TFA_OCELLUS_P2);
		session.onOcellusAnimation(NpcID.TFA_OCELLUS_P3, AnimationID.NPC_OCELLUS_QUADRANT_DESTROY_IN);
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.addHitsplat(true, HitsplatID.DAMAGE_ME, 10);
		session.onGameMessage(GUARDIAN, true);
		assertTrue(session.claim());

		assertEquals(100d, loggedLine().get("contributionPoints").getAsDouble(), 0.0001);
	}

	@Test
	public void thePointsChatLineIsNotTheSource() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.onGameMessage(GUARDIAN, true);
		session.onGameMessage("The Fractured Archive: Total completion time: 12:00.00.", false);
		session.onGameMessage("Total points: 666, Personal points: 194 (29.13%)", false);
		assertTrue(session.claim());

		assertEquals(0d, loggedLine().get("contributionPoints").getAsDouble(), 0.0001);
	}

	@Test
	public void aHealIsIgnoredAndTheFallbackUsesWholeNumbers() throws IOException
	{
		session.observeHud(false, false);
		session.observeHud(true, false);
		session.addHitsplat(true, HitsplatID.DAMAGE_ME, 10);
		session.addHitsplat(false, HitsplatID.HEAL, 90);
		session.addHitsplat(false, HitsplatID.DAMAGE_OTHER, 10);
		session.onGameMessage(GUARDIAN, true);
		session.addHitsplat(true, HitsplatID.DAMAGE_ME, 10);
		session.onOcellusNpc(NpcID.TFA_OCELLUS_P2);
		assertTrue(session.claim());

		// 100 * 10/20 + 60 * 10/10
		assertEquals(110d, loggedLine().get("contributionPoints").getAsDouble(), 0.0001);
	}

	private JsonObject loggedLine() throws IOException
	{
		List<String> lines = logLines();
		assertEquals(1, lines.size());
		return new JsonParser().parse(lines.get(0)).getAsJsonObject();
	}

	private List<String> logLines() throws IOException
	{
		Path log = root.resolve("raid-data tracker").resolve("Disco Turtle").resolve("tfa").resolve("raid_tracker_data.log");
		if (!Files.exists(log))
		{
			return new ArrayList<>();
		}
		List<String> lines = new ArrayList<>();
		for (String line : Files.readAllLines(log, StandardCharsets.UTF_8))
		{
			if (!line.isEmpty())
			{
				lines.add(line);
			}
		}
		return lines;
	}
}
