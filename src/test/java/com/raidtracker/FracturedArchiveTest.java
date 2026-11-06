package com.raidtracker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.raidtracker.archive.ArchiveBoss;
import com.raidtracker.archive.FracturedArchiveSession;
import com.raidtracker.filereadwriter.FileReadWriter;
import com.raidtracker.ui.RaidUniques;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import junit.framework.TestCase;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FracturedArchiveTest extends TestCase
{
	@Mock
	private Client client;

	private Path root;
	private FileReadWriter fw;
	private RaidTracker raid;
	private FracturedArchiveSession session;

	@Before
	public void setUp() throws IOException
	{
		root = Files.createTempDirectory("fractured-archive");
		fw = new FileReadWriter();
		fw.setGson(new Gson());
		fw.setStorageRoot(root.toFile());
		raid = new RaidTracker();
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
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
	public void blankLoginDoesNotUseThePluginFolder() throws IOException
	{
		assertFalse(fw.updateUsername(""));
		assertFalse(fw.updateUsername(null));
		assertFalse(fw.updateUsername("   "));
		assertFalse(fw.updateUsername(".."));
		assertFalse(Files.exists(root.resolve("raid-data tracker").resolve("tfa")));
		assertFalse(Files.exists(root.resolve("raid-data tracker").resolve("cox")));

		assertTrue(fw.updateUsername("Disco Turtle"));
		assertTrue(Files.exists(logFile()));
		assertFalse(fw.updateUsername(""));
		assertEquals(logFile().normalize(), Path.of(fw.getRaidFileName(RaidType.TFA)).normalize());
		assertFalse(Files.exists(root.resolve("raid-data tracker").resolve("tfa")));
	}

	@Test
	public void oneLineOmitsASkippedBoss() throws IOException
	{
		openAccount();
		session.setPartySize(1);
		session.setOverallTime(900);
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 70);
		session.recordBoss(ArchiveBoss.EZEKIEL, 0);
		assertTrue(session.claimRewardWindow(Arrays.asList(
			chestItem("Blood rune", 565, 100, 20_000),
			chestItem("ignored chest name", RaidUniques.HORN_OF_BRAN.getItemID(), 1, 5_000_000)
		), "Disco Turtle"));

		assertEquals(1, logLines().size());
		JsonObject line = new JsonParser().parse(logLines().get(0)).getAsJsonObject();
		assertEquals(70, line.get("archiveGuardianTime").getAsInt());
		assertFalse(line.has("ezekielTime"));
		assertFalse(line.has("borkTime"));
		assertFalse(line.has("specialLoot"));
		assertFalse(line.has("inFracturedArchive"));
		assertEquals(1, line.getAsJsonArray("uniques").size());
		assertEquals(2, line.getAsJsonArray("lootList").size());

		RaidTracker loaded = fw.readFromFile(RaidType.TFA).get(0);
		assertTrue(loaded.isInFracturedArchive());
		assertNull(loaded.getEzekielTime());
		assertEquals(Integer.valueOf(70), loaded.getArchiveGuardianTime());
	}

	@Test
	public void theatreLineDoesNotGainArchiveKeys()
	{
		RaidTracker theatre = new RaidTracker();
		theatre.setInTheatreOfBlood(true);
		theatre.setSpecialLoot("Avernic defender hilt");
		theatre.setMaidenTime(120);
		String json = fw.getJSONString(theatre, new Gson(), new JsonParser());
		assertTrue(json.contains("specialLoot"));
		assertTrue(json.contains("maidenTime"));
		assertFalse(json.contains("archiveGuardianTime"));
		assertFalse(json.contains("inFracturedArchive"));
		assertFalse(json.contains("\"uniques\""));
		assertFalse(json.contains("contributionPoints"));
		assertFalse(json.contains("ownDeaths"));
	}

	@Test
	public void chatRecordsThisRaidAndIgnoresTheOthers() throws IOException
	{
		assertFalse(session.isArchiveMessage("Challenge complete: Zebak. Duration: 1:00. Total: 1:00."));
		assertTrue(session.isArchiveMessage("Challenge complete: The Dagon'hai. Duration: 1:00. Total: 1:00."));

		openAccount();
		session.onGameMessage("Disco Turtle has died. Total deaths: 9.", false);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 2:34.80. Total: 2:34.80", true);
		session.onGameMessage("Challenge complete: The Dagon\u2019hai. Duration: 1:05.00. Total: 6:59.60", true);
		session.onGameMessage("Challenge complete: Zebak. Duration: 4:00.00. Total: 4:00.00", false);
		session.onGameMessage("Someone has died. Total deaths: 4.", true);
		session.onGameMessage("The Fractured Archive: Total completion time: 20:15.20. Personal best: blabla", false);
		session.onGameMessage("Your completed Fractured Archive count is: 42.", false);
		assertTrue(session.claim());

		JsonObject line = new JsonParser().parse(logLines().get(0)).getAsJsonObject();
		assertEquals(155, line.get("archiveGuardianTime").getAsInt());
		assertEquals(65, line.get("dagonhaiTime").getAsInt());
		assertFalse(line.has("borkTime"));
		assertEquals(1215, line.get("raidTime").getAsInt());
		assertEquals(42, line.get("completionCount").getAsInt());
		assertFalse(line.has("deaths"));
		assertFalse(line.has("ownMvp"));
		assertFalse(line.has("contributionPoints"));
		assertFalse(line.has("ownDeaths"));
	}

	@Test
	public void trackedInputsAreStoredWithTheChest() throws IOException
	{
		openAccount();
		session.setPartySize(2);
		session.setOverallTime(500);
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 80);
		session.setTrackedInputs(0d, 0);
		assertTrue(session.claimRewardWindow(
			Arrays.asList(chestItem("Blood rune", 565, 10, 2_000)),
			"Disco Turtle"));

		JsonObject zeros = new JsonParser().parse(logLines().get(0)).getAsJsonObject();
		assertEquals(0d, zeros.get("contributionPoints").getAsDouble(), 0d);
		assertEquals(0, zeros.get("ownDeaths").getAsInt());
		assertEquals(1, zeros.getAsJsonArray("lootList").size());
		assertFalse(zeros.has("ownMvp"));
		assertFalse(zeros.has("deaths"));
		assertFalse(zeros.has("purpleChance"));
		RaidTracker loaded = fw.readFromFile(RaidType.TFA).get(0);
		assertEquals(0d, loaded.getContributionPoints(), 0d);
		assertEquals(Integer.valueOf(0), loaded.getOwnDeaths());

		freshSession();
		session.setPartySize(4);
		session.setOverallTime(600);
		session.recordBoss(ArchiveBoss.OCELLUS, 90);
		session.setTrackedInputs(250.5, 2);
		assertTrue(session.claim());
		JsonObject fraction = new JsonParser().parse(logLines().get(1)).getAsJsonObject();
		assertEquals(250.5, fraction.get("contributionPoints").getAsDouble(), 0d);
		assertEquals(2, fraction.get("ownDeaths").getAsInt());
		ArrayList<RaidTracker> both = fw.readFromFile(RaidType.TFA);
		assertEquals("Blood rune", both.get(0).getLootList().get(0).getName());
		assertEquals(0d, both.get(0).getContributionPoints(), 0d);
		assertEquals(Integer.valueOf(0), both.get(0).getOwnDeaths());
		assertEquals(250.5, both.get(1).getContributionPoints(), 0d);

		freshSession();
		session.setPartySize(1);
		session.setOverallTime(100);
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 40);
		session.setTrackedInputs(10d, null);
		assertTrue(session.claim());
		JsonObject partial = new JsonParser().parse(logLines().get(2)).getAsJsonObject();
		assertFalse(partial.has("contributionPoints"));
		assertFalse(partial.has("ownDeaths"));
	}

	@Test
	public void rewriteDropsMvpAndTeamDeaths() throws IOException
	{
		openAccount();
		String raw = "{\"raidTime\":400,\"teamSize\":3,\"ownMvp\":true,\"deaths\":4,"
			+ "\"uniqueID\":\"old-raid\",\"date\":1,\"contributionPoints\":12.5}";
		Files.write(logFile(), (raw + "\n").getBytes(StandardCharsets.UTF_8));

		RaidTracker loaded = fw.readFromFile(RaidType.TFA).get(0);
		assertEquals(Boolean.TRUE, loaded.getOwnMvp());
		assertEquals(Integer.valueOf(4), loaded.getDeaths());
		assertEquals(12.5, loaded.getContributionPoints(), 0d);
		assertNull(loaded.getOwnDeaths());
		assertEquals("old-raid", loaded.getUniqueID());

		loaded.setTeamSize(4);
		assertTrue(fw.updateRTLog(loaded, RaidType.TFA));
		JsonObject rewritten = new JsonParser().parse(logLines().get(0)).getAsJsonObject();
		assertEquals("old-raid", rewritten.get("uniqueID").getAsString());
		assertEquals(400, rewritten.get("raidTime").getAsInt());
		assertEquals(4, rewritten.get("teamSize").getAsInt());
		assertFalse(rewritten.has("ownMvp"));
		assertFalse(rewritten.has("deaths"));
		assertFalse(rewritten.has("contributionPoints"));
		assertFalse(rewritten.has("ownDeaths"));
	}

	@Test
	public void wipeAndLeaveBeforeCompletionWriteNothing() throws IOException
	{
		openAccount();
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 55);
		session.wipe();
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 1:00.00. Total: 1:00.00", true);
		session.onGameMessage("You leave the Fractured Archive.", false);
		session.onGameMessage("The Fractured Archive: Total completion time: 12:00.00.", false);

		freshSession();
		session.onGameMessage("Challenge complete: Ocellus. Duration: 8:00. Total: 20:00.", true);
		session.onGameMessage("The Fractured Archive: Total completion time: 20:00.", false);
		session.onGameMessage("You failed to survive the Fractured Archive.", false);

		assertEquals(0, logLines().size());
		assertFalse(session.isAwaitingRetry());
	}

	@Test
	public void completedLeaveSavesTimesAndIgnoresALaterUnique() throws IOException
	{
		openAccount();
		namePlayer("Disco Turtle");
		session.setPartySize(4);
		session.setUniquePrice(unique -> 4_000_000L);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 1:55. Total: 1:55.", true);
		session.onGameMessage("The Fractured Archive: Total completion time: 21:01.", false);
		session.onGameMessage("You have died. Total deaths: 2.", true);
		session.onGameMessage("Other Player found something special: Rondache", false);
		session.onGameMessage("You leave the Fractured Archive.", false);
		assertNotNull(session.takeDepartedRaid());

		JsonObject line = new JsonParser().parse(logLines().get(0)).getAsJsonObject();
		assertEquals(115, line.get("archiveGuardianTime").getAsInt());
		assertEquals(1261, line.get("raidTime").getAsInt());
		assertFalse(line.has("deaths"));
		assertFalse(line.has("ownDeaths"));
		assertEquals(0, line.getAsJsonArray("lootList").size());
		assertEquals("Rondache", line.getAsJsonArray("uniques").get(0).getAsJsonObject().get("item").getAsString());

		session.onGameMessage("Other Player found something special: Horn of Bran", false);
		assertEquals(1, fw.readFromFile(RaidType.TFA).get(0).getUniques().size());
	}

	@Test
	public void shutdownWritesACompletionBeforeTheChest() throws IOException
	{
		openAccount();
		session.setPartySize(3);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 1:10. Total: 1:10.", true);
		session.onGameMessage("The Fractured Archive: Total completion time: 12:00.", false);
		session.finished();

		JsonObject line = new JsonParser().parse(logLines().get(0)).getAsJsonObject();
		assertEquals(720, line.get("raidTime").getAsInt());
		assertEquals(70, line.get("archiveGuardianTime").getAsInt());
		assertEquals(3, line.get("teamSize").getAsInt());
		assertEquals(0, line.getAsJsonArray("lootList").size());
		assertEquals(1, logLines().size());
	}

	@Test
	public void nextGuardianSeparatesTheTwoRaids() throws IOException
	{
		openAccount();
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 2:00.00. Total: 2:00.00", false);
		session.onGameMessage("Someone has died. Total deaths: 3.", true);
		session.onGameMessage("The Fractured Archive: Total completion time: 12:00.00.", false);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 1:30.00. Total: 1:30.00", false);
		assertNotNull(session.takeDepartedRaid());
		freshRaidOnly();
		assertTrue(session.claim());

		ArrayList<RaidTracker> finished = fw.readFromFile(RaidType.TFA);
		assertEquals(2, finished.size());
		assertEquals(720, finished.get(0).getRaidTime());
		assertNull(finished.get(0).getDeaths());
		assertNull(finished.get(0).getOwnDeaths());
		assertEquals(Integer.valueOf(90), finished.get(1).getArchiveGuardianTime());
		assertNull(finished.get(1).getDeaths());
		assertNull(finished.get(1).getContributionPoints());

		freshSession();
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 2:00.00. Total: 2:00.00", false);
		session.onGameMessage("Challenge complete: Moia. Duration: 1:00.00. Total: 3:00.00", false);
		session.onGameMessage("Someone has died. Total deaths: 3.", true);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 1:30.00. Total: 1:30.00", false);
		assertTrue(session.claim());

		JsonObject dropped = new JsonParser().parse(logLines().get(logLines().size() - 1)).getAsJsonObject();
		assertEquals(90, dropped.get("archiveGuardianTime").getAsInt());
		assertFalse(dropped.has("moiaTime"));
		assertFalse(dropped.has("deaths"));
	}

	@Test
	public void laterUniqueStaysUntilTheNextRaid() throws IOException
	{
		openAccount();
		namePlayer("Disco Turtle");
		session.setPartySize(2);
		session.setUniquePrice(unique -> 4_000_000L);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 1:00.00. Total: 1:00.00", true);
		assertTrue(session.claim());
		String id = session.claimedRaid().getUniqueID();
		freshRaidOnly();

		session.onGameMessage("Other Player found something special: Rondache", false);
		session.onGameMessage("Challenge complete: Archive Guardian. Duration: 2:00.00. Total: 2:00.00", true);
		session.onGameMessage("Next Player found something special: Horn of Bran", false);
		assertTrue(session.claim());

		ArrayList<RaidTracker> raids = fw.readFromFile(RaidType.TFA);
		assertEquals(2, raids.size());
		assertEquals(id, raids.get(0).getUniqueID());
		assertEquals("Rondache", raids.get(0).getUniques().get(0).getItem());
		assertEquals("Horn of Bran", raids.get(1).getUniques().get(0).getItem());
	}

	@Test
	public void logoutWritesOnceUnlessThePlayerReturns() throws IOException
	{
		openAccount();
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 70);
		session.setOverallTime(400);
		session.noteGameLeft();
		for (int i = 0; i < FracturedArchiveSession.RETURN_TICKS - 1; i++)
		{
			assertNull(session.noteReturnTick(false));
		}
		assertNotNull(session.noteReturnTick(false));
		assertEquals(1, logLines().size());
		assertTrue(session.isParked());
		assertFalse(session.isEngaged());
		assertTrue(session.claimRewardWindow(
			Arrays.asList(chestItem("Blood rune", 565, 10, 2_000)),
			"Disco Turtle"));
		assertEquals(1, logLines().size());
		assertEquals(70, new JsonParser().parse(logLines().get(0)).getAsJsonObject().get("archiveGuardianTime").getAsInt());

		freshSession();
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 70);
		session.setOverallTime(400);
		session.noteGameLeft();
		assertNull(session.noteReturnTick(false));
		assertNull(session.noteReturnTick(true));
		assertEquals(1, logLines().size());
		assertTrue(session.hasUnstoredCompletion());

		freshSession();
		session.recordBoss(ArchiveBoss.EZEKIEL, 40);
		session.noteGameLeft();
		for (int i = 0; i < FracturedArchiveSession.RETURN_TICKS; i++)
		{
			assertNull(session.noteReturnTick(false));
		}
		assertEquals(1, logLines().size());
	}

	@Test
	public void restartFillsOnlyTheChestThatWasStillOpen() throws IOException
	{
		openAccount();
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 70);
		session.setOverallTime(400);
		session.finished();
		RaidTracker closed = fw.readFromFile(RaidType.TFA).get(0);
		assertNull(closed.getOpenClaim());
		assertNull(closed.getOpenChest());

		RaidTracker live = new RaidTracker();
		FracturedArchiveSession nextRaid = new FracturedArchiveSession(client, () -> live, () -> fw);
		assertFalse(nextRaid.noteResumeTick(true, closed, closed.getDate() + 1_000));
		assertTrue(nextRaid.claimRewardWindow(
			Arrays.asList(chestItem("Death rune", 560, 10, 2_000)),
			"Disco Turtle"));

		ArrayList<RaidTracker> split = fw.readFromFile(RaidType.TFA);
		assertEquals(2, split.size());
		assertEquals(Integer.valueOf(70), split.get(0).getArchiveGuardianTime());
		assertEquals(0, split.get(0).getLootList().size());
		assertEquals("Death rune", split.get(1).getLootList().get(0).getName());
		assertNull(split.get(1).getArchiveGuardianTime());

		freshSession();
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 80);
		session.setOverallTime(500);
		session.finished(true);
		RaidTracker held = fw.readFromFile(RaidType.TFA).get(2);
		assertEquals(Boolean.TRUE, held.getOpenClaim());
		assertEquals(Boolean.TRUE, held.getOpenChest());
		assertEquals(500, held.getRaidTime());

		FracturedArchiveSession stale = new FracturedArchiveSession(client, () -> new RaidTracker(), () -> fw);
		assertFalse(stale.noteResumeTick(true, held, held.getDate() + FracturedArchiveSession.OPEN_CLAIM_MS + 1));

		RaidTracker back = new RaidTracker();
		FracturedArchiveSession sameChest = new FracturedArchiveSession(client, () -> back, () -> fw);
		assertTrue(sameChest.noteResumeTick(true, held, held.getDate() + 1_000));
		assertTrue(sameChest.claimRewardWindow(
			Arrays.asList(chestItem("Blood rune", 565, 4, 800)),
			"Disco Turtle"));
		sameChest.onGameMessage("Challenge complete: Ezekiel. Duration: 1:00. Total: 4:00.", true);
		assertTrue(sameChest.claimRewardWindow(
			Arrays.asList(chestItem("Death rune", 560, 1, 100)),
			"Disco Turtle"));

		ArrayList<RaidTracker> raids = fw.readFromFile(RaidType.TFA);
		assertEquals(4, raids.size());
		assertEquals("Blood rune", raids.get(2).getLootList().get(0).getName());
		assertEquals(Integer.valueOf(80), raids.get(2).getArchiveGuardianTime());
		assertNull(raids.get(2).getEzekielTime());
		assertEquals(Integer.valueOf(60), raids.get(3).getEzekielTime());
		assertEquals("Death rune", raids.get(3).getLootList().get(0).getName());
	}

	@Test
	public void timerDoesNotResumeThePreviousLine() throws IOException
	{
		openAccount();
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 70);
		session.setOverallTime(400);
		session.finished(true);
		RaidTracker held = fw.readFromFile(RaidType.TFA).get(0);
		assertEquals(Boolean.TRUE, held.getOpenChest());

		RaidTracker live = new RaidTracker();
		FracturedArchiveSession restarted = new FracturedArchiveSession(client, () -> live, () -> fw);
		assertFalse(restarted.noteResumeTick(false, held, held.getDate() + 1_000));
		restarted.recordBoss(ArchiveBoss.EZEKIEL, 60);
		restarted.setOverallTime(500);
		assertTrue(restarted.claimRewardWindow(
			Arrays.asList(chestItem("Death rune", 560, 1, 100)),
			"Disco Turtle"));

		ArrayList<RaidTracker> raids = fw.readFromFile(RaidType.TFA);
		assertEquals(2, raids.size());
		assertEquals(Integer.valueOf(70), raids.get(0).getArchiveGuardianTime());
		assertNull(raids.get(0).getEzekielTime());
		assertEquals(0, raids.get(0).getLootList().size());
		assertEquals(Boolean.TRUE, raids.get(0).getOpenChest());
		assertEquals(Integer.valueOf(60), raids.get(1).getEzekielTime());
		assertEquals("Death rune", raids.get(1).getLootList().get(0).getName());
	}

	@Test
	public void closingTheChestClearsTheWaitingMark() throws IOException
	{
		openAccount();
		namePlayer("Disco Turtle");
		session.observeHud(false, true);
		session.setPartySize(2);
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 70);
		session.setOverallTime(400);
		assertTrue(session.claim());
		RaidTracker waiting = fw.readFromFile(RaidType.TFA).get(0);
		assertEquals(Boolean.TRUE, waiting.getOpenChest());
		assertEquals(Boolean.TRUE, waiting.getOpenClaim());

		session.observeHud(false, false);
		RaidTracker closedChest = fw.readFromFile(RaidType.TFA).get(0);
		assertNull(closedChest.getOpenChest());
		assertEquals(Boolean.TRUE, closedChest.getOpenClaim());
		assertFalse(logLines().get(0).contains("openChest"));

		session.onGameMessage("Other Player found something special: Rondache", false);
		assertEquals("Rondache", fw.readFromFile(RaidType.TFA).get(0).getUniques().get(0).getItem());
		assertNull(fw.readFromFile(RaidType.TFA).get(0).getOpenChest());

		RaidTracker latest = fw.readFromFile(RaidType.TFA).get(0);
		RaidTracker live = new RaidTracker();
		FracturedArchiveSession later = new FracturedArchiveSession(client, () -> live, () -> fw);
		assertFalse(later.noteResumeTick(true, latest, latest.getDate() + 1_000));
		assertTrue(later.claimRewardWindow(
			Arrays.asList(chestItem("Death rune", 560, 1, 100)),
			"Disco Turtle"));
		ArrayList<RaidTracker> raids = fw.readFromFile(RaidType.TFA);
		assertEquals(2, raids.size());
		assertTrue(raids.get(0).getLootList() == null || raids.get(0).getLootList().isEmpty());
		assertEquals("Death rune", raids.get(1).getLootList().get(0).getName());
	}

	@Test
	public void closingTheWidgetsReleasesTheOtherRaids() throws IOException
	{
		openAccount();
		namePlayer("Disco Turtle");
		session.setPartySize(2);
		session.setUniquePrice(unique -> 4_000_000L);
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 70);
		session.setOverallTime(400);
		assertTrue(session.claimRewardWindow(
			Arrays.asList(chestItem("Blood rune", 565, 4, 800)),
			"Disco Turtle"));
		assertTrue(session.isEngaged());

		session.observeHud(true, false);
		session.observeHud(false, false);

		assertTrue(session.isParked());
		assertFalse(session.isEngaged());
		session.onGameMessage("Other Player found something special: Rondache", false);
		assertEquals("Rondache", fw.readFromFile(RaidType.TFA).get(0).getUniques().get(0).getItem());
	}

	@Test
	public void splitChoiceSurvivesTheNextUnique() throws IOException
	{
		openAccount();
		namePlayer("Disco Turtle");
		session.setPartySize(2);
		session.setSplitRules(false, 1_000_000L);
		session.setUniquePrice(unique -> 8_000_000L);
		assertTrue(session.claimRewardWindow(
			Arrays.asList(chestItem("Horn of Bran", RaidUniques.HORN_OF_BRAN.getItemID(), 1, 8_000_000)),
			"Disco Turtle"));
		session.claimedRaid().setLootSplitReceived(123_456L);
		session.claimedRaid().setSplitAmountLocked(true);
		session.onGameMessage("Other Player found something special: Rondache", false);
		RaidTracker typed = fw.readFromFile(RaidType.TFA).get(0);
		assertEquals(123_456L, typed.getLootSplitReceived());
		assertEquals(2, typed.getUniques().size());
		assertFalse(fw.archiveLine(typed).contains("splitAmountLocked"));

		freshSession();
		namePlayer("Disco Turtle");
		session.setPartySize(4);
		session.setSplitRules(false, 1_000_000L);
		session.setUniquePrice(unique -> 2_000_000L);
		assertTrue(session.claimRewardWindow(
			Arrays.asList(chestItem("Horn of Bran", RaidUniques.HORN_OF_BRAN.getItemID(), 1, 2_000_000)),
			"Disco Turtle"));
		session.claimedRaid().setSplitChoice(RaidTracker.SPLIT_CHOICE_SPLIT);
		session.claimedRaid().setFreeForAll(false);
		FracturedArchiveSession.recalculateSplits(session.claimedRaid(), 1_000_000L);
		session.onGameMessage("Other Player found something special: Rondache", false);
		RaidTracker chosen = fw.readFromFile(RaidType.TFA).get(1);
		assertEquals(RaidTracker.SPLIT_CHOICE_SPLIT, chosen.getSplitChoice());
		assertFalse(chosen.isFreeForAll());

		freshSession();
		namePlayer("Disco Turtle");
		session.setPartySize(4);
		session.setSplitRules(false, 1_000_000L);
		session.setUniquePrice(unique -> 8_000_000L);
		assertTrue(session.claimRewardWindow(
			Arrays.asList(chestItem("Horn of Bran", RaidUniques.HORN_OF_BRAN.getItemID(), 1, 2_000_000)),
			"Disco Turtle"));
		assertTrue(session.claimedRaid().isFreeForAll());
		session.onGameMessage("Other Player found something special: Rondache", false);
		assertFalse(fw.readFromFile(RaidType.TFA).get(2).isFreeForAll());
	}

	@Test
	public void updateKeepsARaidSavedWhileTheSplitIsEdited() throws Exception
	{
		openAccount();
		session.setPartySize(1);
		session.setOverallTime(400);
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 70);
		assertTrue(session.claim());

		RaidTracker edited = fw.readFromFile(RaidType.TFA).get(0);
		edited.setLootSplitReceived(50L);
		UniqueDrop unique = new UniqueDrop();
		unique.setItem("Rondache");
		edited.setUniques(new ArrayList<>(Arrays.asList(unique)));

		RaidTracker later = new RaidTracker();
		later.setUniqueID("later-raid");
		later.setInFracturedArchive(true);
		later.setRaidTime(33);
		assertTrue(fw.appendArchive(later));

		ArrayList<RaidTracker> panelSnapshot = new ArrayList<>();
		panelSnapshot.add(edited);
		fw.updateRTList(panelSnapshot, RaidType.TFA);

		ArrayList<RaidTracker> saved = fw.readFromFile(RaidType.TFA);
		assertEquals(2, saved.size());
		assertEquals(50L, saved.get(0).getLootSplitReceived());
		assertEquals("Rondache", saved.get(0).getUniques().get(0).getItem());
		assertEquals("later-raid", saved.get(1).getUniqueID());
		assertEquals(33, saved.get(1).getRaidTime());

		saved.get(1).setLootSplitReceived(9L);
		assertTrue(fw.updateRTLog(saved.get(1), RaidType.TFA));
		ArrayList<RaidTracker> afterUnique = fw.readFromFile(RaidType.TFA);
		assertEquals(50L, afterUnique.get(0).getLootSplitReceived());
		assertEquals(9L, afterUnique.get(1).getLootSplitReceived());
	}

	@Test
	public void updateAndANewRaidCanRunTogether() throws Exception
	{
		openAccount();
		session.setPartySize(1);
		session.setOverallTime(400);
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 70);
		assertTrue(session.claim());
		RaidTracker edited = fw.readFromFile(RaidType.TFA).get(0);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		Future<?> updates = pool.submit(() -> {
			start.await();
			for (int i = 0; i < 20; i++) {
				edited.setLootSplitReceived(50L + i);
				ArrayList<RaidTracker> snapshot = new ArrayList<>();
				snapshot.add(edited);
				fw.updateRTList(snapshot, RaidType.TFA);
			}
			return null;
		});
		Future<?> appends = pool.submit(() -> {
			start.await();
			for (int i = 0; i < 20; i++) {
				RaidTracker extra = new RaidTracker();
				extra.setUniqueID("extra-" + i);
				extra.setInFracturedArchive(true);
				extra.setRaidTime(i + 1);
				if (!fw.appendArchive(extra)) {
					throw new IllegalStateException("append");
				}
			}
			return null;
		});
		start.countDown();
		updates.get();
		appends.get();
		pool.shutdown();

		ArrayList<RaidTracker> saved = fw.readFromFile(RaidType.TFA);
		assertEquals(21, saved.size());
		assertEquals(edited.getUniqueID(), saved.get(0).getUniqueID());
		assertTrue(saved.get(0).getLootSplitReceived() >= 50L);
		assertEquals("extra-19", saved.get(20).getUniqueID());
	}

	@Test
	public void failedAppendRetriesOnce() throws IOException
	{
		session.setPartySize(1);
		session.setOverallTime(120);
		session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 30);
		assertFalse(session.claim());
		assertTrue(session.isAwaitingRetry());

		openAccount();
		assertNotNull(session.retryPending());
		assertFalse(session.isAwaitingRetry());
		assertEquals(120, new JsonParser().parse(logLines().get(0)).getAsJsonObject().get("raidTime").getAsInt());
		assertEquals(1, logLines().size());
	}

	@Test
	public void untrackedWorldsWriteNothing() throws IOException
	{
		openAccount();
		WorldType[] untracked = {
			WorldType.SEASONAL,
			WorldType.DEADMAN,
			WorldType.BETA_WORLD,
			WorldType.TOURNAMENT_WORLD,
			WorldType.FRESH_START_WORLD,
			WorldType.QUEST_SPEEDRUNNING,
			WorldType.LEGACY_ONLY,
			WorldType.EOC_ONLY
		};
		for (WorldType type : untracked)
		{
			freshSession();
			when(client.getWorldType()).thenReturn(EnumSet.of(type));
			session.recordBoss(ArchiveBoss.ARCHIVE_GUARDIAN, 45);
			assertFalse(type.name(), session.claim());
		}
		assertEquals(0, logLines().size());

		freshSession();
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		session.setPartySize(2);
		assertTrue(session.claim());
		assertEquals(1, logLines().size());
	}

	private void freshSession()
	{
		raid = new RaidTracker();
		session = new FracturedArchiveSession(client, () -> raid, () -> fw);
	}

	private void freshRaidOnly()
	{
		raid = new RaidTracker();
	}

	private void namePlayer(String name)
	{
		Player local = org.mockito.Mockito.mock(Player.class);
		when(local.getName()).thenReturn(name);
		when(client.getLocalPlayer()).thenReturn(local);
	}

	private void openAccount()
	{
		fw.updateUsername("Disco Turtle");
	}

	private Path logFile()
	{
		return root.resolve("raid-data tracker").resolve("Disco Turtle").resolve("tfa").resolve("raid_tracker_data.log");
	}

	private List<String> logLines() throws IOException
	{
		Path log = logFile();
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

	private static RaidTrackerItem chestItem(String name, int id, int quantity, long price)
	{
		RaidTrackerItem item = new RaidTrackerItem();
		item.name = name;
		item.id = id;
		item.quantity = quantity;
		item.price = price;
		return item;
	}
}
