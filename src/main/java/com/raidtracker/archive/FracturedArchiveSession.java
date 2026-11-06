package com.raidtracker.archive;

import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerItem;
import com.raidtracker.RaidType;
import com.raidtracker.UniqueDrop;
import com.raidtracker.WorldUtils;
import com.raidtracker.filereadwriter.FileReadWriter;
import com.raidtracker.ui.RaidUniques;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.HitsplatID;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.util.Text;

/**
 * Live Fractured Archive state. The plugin keeps the event subscriptions and calls in.
 * One reward window is one log line. Leaving after the completion message, before that window,
 * is also one line, with an empty chest. A teammate unique that arrives
 * after the chest stays on that line until the next boss, a leave, or a wipe.
 * Logout keeps that line open so a later chest updates it. A world hop and a leave close it.
 * Shutdown keeps it open only while the reward chest is still on screen.
 * A completion still in memory is written before the next Archive Guardian replaces it.
 */
@Slf4j
public final class FracturedArchiveSession {
	/** Logged-in ticks without the raid timer or chest before a disconnect is treated as having left. */
	public static final int RETURN_TICKS = 10;
	/** A line newer than this can still be the open chest after the client starts again. */
	public static final long OPEN_CLAIM_MS = 10 * 60 * 1000L;
	private static final Pattern BOSS_COMPLETE = Pattern.compile(
		"^Challenge complete: (?<boss>.+?)\\. Duration: (?<duration>(?:\\d+:)?\\d+:\\d+(?:\\.\\d+)?)");
	private static final Pattern RAID_COMPLETE = Pattern.compile(
		"^The Fractured Archive: Total completion time: (?<duration>(?:\\d+:)?\\d+:\\d+(?:\\.\\d+)?)");
	private static final Pattern KILL_COUNT = Pattern.compile(
		"^Your completed Fractured Archive count is: (?<count>\\d+)");
	private static final Pattern DEATH = Pattern.compile(
		"^(?:You have|.+ has) died\\. Total deaths: (?<count>\\d+)\\.?$");
	private static final Pattern SPECIAL = Pattern.compile("^(?<player>.+?) found something special: (?<item>.+?)\\.?$");
	private static final Pattern COLOR_TAG = Pattern.compile("<[^>]*>");
	private static final String WIPE = "You failed to survive the Fractured Archive";
	private static final String LEAVE = "You leave the Fractured Archive";

	private final Client client;
	private final Supplier<RaidTracker> raids;
	private final Supplier<FileReadWriter> writer;

	/** The raid whose append failed. It is not the live plugin field, so a reset cannot drop it. */
	private RaidTracker pending;
	private boolean stored;
	/** The chest line is still accepting a teammate unique. */
	private boolean claimOpen;
	/** Leave or wipe closed the raid. Ignore the HUD until it actually closes. */
	private boolean ignoreHud;
	/** Written because the player left after the completion message. The plugin adds this raid once. */
	private RaidTracker departed;
	/** That leave must detach the live tracker, including when the append is still queued. */
	private boolean detachLive;

	private final Map<ArchiveBoss, Integer> bossTimes = new EnumMap<>(ArchiveBoss.class);
	private int overall = -1;
	private int party = -1;
	private int killCount = -1;
	private final ArchiveContribution contribution = new ArchiveContribution();
	/** False until this session has seen the timer widget once. The first sample is not an opening. */
	private boolean timerKnown;
	/** Own deaths already on the tally when the saved raid's timer closed. Later deaths belong to the next raid. */
	private int deathsAtClaim;
	/** The tally was started for the next raid while the previous chest line was still open. */
	private boolean tallyOutlivesClaim;
	/** Points and deaths of a completion that is still only in memory, taken when its timer closed. */
	private boolean completionParked;
	private Double parkedPoints;
	private Integer parkedDeaths;
	private int parkedDeathCount;
	private final List<UniqueDrop> drops = new ArrayList<>();
	private RaidTracker lastClaimed;
	/** Claimed raids whose rewrite failed. The same raid is not queued twice. */
	private final List<RaidTracker> unwritten = new ArrayList<>();
	private boolean viewStale;
	private boolean defaultFfa;
	private long ffaCutoff = 1_000_000L;
	private Function<RaidUniques, Long> prices = unique -> null;
	private boolean sawHud;
	private boolean hudWasOpen;
	/** The raid timer is on screen this tick. Distinct from {@link #sawHud}, which stays set after the timer closes. */
	private boolean hudVisible;
	/** The reward chest interface is on screen this tick. */
	private boolean chestVisible;
	private boolean discardArmed;
	/**
	 * Logged out or disconnected with this attempt still in memory.
	 * Counts logged-in ticks until the raid widgets return or {@link #RETURN_TICKS} pass.
	 * Negative means not waiting.
	 */
	private int returnTicks = -1;
	/**
	 * The chest line is saved and still accepts a unique or a late chest, but the player is not in the raid.
	 * Other raids are tracked. A hop or shutdown closes the line.
	 */
	private boolean parked;
	/** This process already decided whether to resume a line from disk. */
	private boolean resumeChecked;
	private int resumeTicks;
	/** The empty-chest wait in the plugin should stop. It would otherwise append another row. */
	private boolean rewardWaitDone;

	public FracturedArchiveSession(Client client, Supplier<RaidTracker> raids, Supplier<FileReadWriter> writer) {
		this.client = client;
		this.raids = raids;
		this.writer = writer;
	}

	private RaidTracker raid() {
		return raids.get();
	}

	/**
	 * True when this message belongs to The Fractured Archive.
	 * A Chambers or Tombs chat line, including another raid's "found something special", is left alone.
	 */
	public boolean isArchiveMessage(String message) {
		message = normalize(message);
		if (message.startsWith(WIPE) || message.startsWith(LEAVE)) {
			return true;
		}
		if (RAID_COMPLETE.matcher(message).find() || KILL_COUNT.matcher(message).find()) {
			return true;
		}
		Matcher special = SPECIAL.matcher(message);
		if (special.find() && RaidUniques.archiveUnique(-1, special.group("item")) != null) {
			return true;
		}
		Matcher boss = BOSS_COMPLETE.matcher(message);
		return boss.find() && ArchiveBoss.fromChat(boss.group("boss")) != null;
	}

	/**
	 * The live tracker still belongs to Chambers, Theatre, or Tombs.
	 * Archive loot has to be written on a new tracker so that raid's object stays unchanged.
	 */
	public boolean foreignTracker() {
		RaidTracker live = raid();
		if (live == null || live.isInFracturedArchive()) {
			return false;
		}
		return live.isInRaidChambers()
			|| live.isInTheatreOfBlood()
			|| live.isInTombsOfAmascut()
			|| live.isRaidComplete()
			|| live.isChestOpened();
	}

	/**
	 * In the raid now: a boss or completion still in memory, the timer, or the chest interface.
	 * A parked chest line does not count. Chambers, Theatre, and Tombs keep logging while that line
	 * waits for a unique or a late chest.
	 */
	public boolean isEngaged() {
		if (parked) {
			return false;
		}
		return claimOpen || pending != null || stored || hudVisible || chestVisible || ignoreHud || keptAttempt() || !drops.isEmpty();
	}

	/** The chest line is saved and the player has left the raid widgets. */
	public boolean isParked() {
		return parked;
	}

	/** Logout or a lost connection. The attempt stays in memory until the player is back. */
	public void noteGameLeft() {
		if (returnTicks >= 0 || parked) {
			return;
		}
		if (hasUnstoredCompletion() || isClaimWindowOpen() || keptAttempt() || pending != null) {
			returnTicks = 0;
		}
	}

	/**
	 * One logged-in tick after {@link #noteGameLeft()}.
	 * The timer or chest means this is still the same raid, so nothing is written yet.
	 * After {@link #RETURN_TICKS} without those widgets, a missing completion is written and the line
	 * stays open for a chest that loads late. An incomplete attempt is dropped.
	 * The returned raid was just written. Null means nothing new was saved.
	 */
	public RaidTracker noteReturnTick(boolean archiveWidgetsOpen) {
		if (returnTicks < 0) {
			return null;
		}
		if (archiveWidgetsOpen) {
			returnTicks = -1;
			return null;
		}
		returnTicks++;
		if (returnTicks < RETURN_TICKS) {
			return null;
		}
		returnTicks = -1;
		park();
		return takeDepartedRaid();
	}

	/**
	 * A fresh session can adopt the newest log line only when that line is still waiting for the
	 * chest on screen. The raid timer does not adopt a line. A closed line stays closed.
	 * The line has to have been written within {@link #OPEN_CLAIM_MS}, and it has no loot yet.
	 * True when that line is now the open chest.
	 */
	public boolean needsResumeCheck() {
		return !resumeChecked
			&& returnTicks < 0
			&& !parked
			&& !claimOpen
			&& pending == null
			&& !stored
			&& !ignoreHud
			&& !keptAttempt();
	}

	public boolean noteResumeTick(boolean chestOpen, RaidTracker latest, long now) {
		if (!needsResumeCheck()) {
			return false;
		}
		boolean waiting = latest != null
			&& Boolean.TRUE.equals(latest.getOpenChest())
			&& (latest.getLootList() == null || latest.getLootList().isEmpty());
		if (!chestOpen || !waiting) {
			// A chest that is not this line settles the check. The timer is not a chest, so keep looking
			// until the chest widget arrives or the usual return ticks pass.
			if (chestOpen || ++resumeTicks >= RETURN_TICKS) {
				resumeChecked = true;
			}
			return false;
		}
		resumeChecked = true;
		if (latest.getUniqueID() == null || latest.getDate() <= 0) {
			return false;
		}
		long age = now - latest.getDate();
		if (age < 0 || age > OPEN_CLAIM_MS) {
			return false;
		}
		lastClaimed = latest;
		claimOpen = true;
		stored = true;
		contribution.reset();
		parked = false;
		return true;
	}

	/** True once, after leave, wipe, park, or shutdown. The plugin cancels the empty-chest wait. */
	public boolean takeRewardClosed() {
		boolean closed = rewardWaitDone;
		rewardWaitDone = false;
		return closed;
	}

	/** Completion is known and the chest line has not been written yet. Shutdown and hop still need that line. */
	public boolean hasUnstoredCompletion() {
		return overall > 0 && !claimOpen && pending == null && !stored;
	}

	/** The chest line still taking a teammate unique. Null once that window closes. */
	public RaidTracker liveClaim() {
		if (!claimOpen) {
			return null;
		}
		return pending != null ? pending : lastClaimed;
	}

	/**
	 * Game-chat lines for this raid. {@code raidHudOpen} is the raid timer on screen, so a death
	 * message outside the raid is ignored. The kill-count setting can be off; that line is optional.
	 */
	public void onGameMessage(String message, boolean raidHudOpen) {
		if (message == null || raid() == null || WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}
		message = normalize(message);
		if (message.startsWith(WIPE)) {
			if (stored || pending != null) {
				closeClaim();
			} else {
				wipe();
			}
			return;
		}
		if (message.startsWith(LEAVE)) {
			if (stored || pending != null) {
				closeClaim();
			} else if (overall > 0) {
				saveCompletedLeave(true);
			} else {
				wipe();
			}
			return;
		}
		Matcher special = SPECIAL.matcher(message);
		if (special.find()) {
			addBroadcast(special.group("player"), special.group("item"));
			return;
		}
		Matcher matcher = BOSS_COMPLETE.matcher(message);
		if (matcher.find()) {
			ArchiveBoss boss = ArchiveBoss.fromChat(matcher.group("boss"));
			int seconds = durationSeconds(matcher.group("duration"));
			// Any boss after the chest is a new raid. The guardian chat is the usual start.
			// A reload past that chat still has to let the next boss open its own line.
			if (claimOpen) {
				endClaimWindow();
			}
			if (blocked()) {
				return;
			}
			if (boss == ArchiveBoss.ARCHIVE_GUARDIAN && bossTimes.containsKey(ArchiveBoss.ARCHIVE_GUARDIAN)) {
				if (hasUnstoredCompletion()) {
					saveCompletedLeave(true);
				} else {
					clearLive();
				}
			}
			if (boss == ArchiveBoss.ARCHIVE_GUARDIAN) {
				ignoreHud = false;
			}
			recordBoss(boss, seconds);
			return;
		}
		matcher = RAID_COMPLETE.matcher(message);
		if (matcher.find()) {
			int seconds = durationSeconds(matcher.group("duration"));
			if (claimOpen && !keptAttempt()) {
				if (sameStoredDuration(seconds)) {
					return;
				}
				endClaimWindow();
			}
			if (blocked() || (ignoreHud && !claimOpen && !hasProgress())) {
				return;
			}
			setOverallTime(seconds);
			return;
		}
		if (isOwnDeath(message) && (raidHudOpen || !bossTimes.isEmpty() || overall > 0)) {
			contribution.addOwnDeath(party >= 1 ? party : 0);
		}
		if (blocked() || (ignoreHud && !claimOpen && !hasProgress())) {
			return;
		}
		matcher = KILL_COUNT.matcher(message);
		if (matcher.find()) {
			int count = Integer.parseInt(matcher.group("count"));
			if (count > 0) {
				killCount = count;
			}
		}
	}

	private static boolean isOwnDeath(String message) {
		return message.startsWith("You have died") && DEATH.matcher(message).matches();
	}

	/** A fight that actually happened. A duration that is not above 0 is left unset, not stored as 0. */
	public void recordBoss(ArchiveBoss boss, int seconds) {
		if (boss == null || seconds <= 0 || blocked()) {
			return;
		}
		boolean firstTime = !bossTimes.containsKey(boss);
		bossTimes.put(boss, seconds);
		if (firstTime) {
			contribution.closeRoom(boss);
		}
	}

	/** Real party size. A solo is 1. Sizes outside 1 to 8 are ignored. */
	public void setPartySize(int size) {
		if (blocked() || ignoreHud || size < 1 || size > 8) {
			return;
		}
		party = size;
		contribution.noteTeamSize(size);
	}

	/** Overall raid duration. A time that is not above 0 stays off the line. */
	public void setOverallTime(int seconds) {
		if (blocked() || seconds <= 0) {
			return;
		}
		overall = seconds;
	}

	/**
	 * Personal points from The Fractured Archive plugin. They replace the live tally, including a
	 * raid that was not tracked from the start. A line already written is updated. The team total
	 * is not stored, and the plugin's chat line is not read.
	 */
	public void noteExternalPoints(int personal) {
		if (raid() == null || WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}
		boolean liveHasProgress = overall > 0 || !bossTimes.isEmpty() || completionParked;
		if (!stored && liveHasProgress) {
			int deaths = contribution.countedDeaths();
			contribution.useTotals(personal, deaths);
			if (completionParked) {
				parkedPoints = (double) personal;
				parkedDeaths = deaths;
			}
			return;
		}
		RaidTracker target = openClaim();
		if (target == null) {
			if (!stored) {
				contribution.useTotals(personal, contribution.countedDeaths());
			}
			return;
		}
		Integer deaths = target.getOwnDeaths();
		target.setContributionPoints((double) personal);
		target.setOwnDeaths(deaths != null ? deaths : contribution.countedDeaths());
		publish(target);
	}

	/**
	 * Replaces the live tally. Either value missing omits both from the line.
	 * Zero is stored. This does not change a line that has already been written.
	 */
	public void setTrackedInputs(Double points, Integer deaths) {
		if (blocked()) {
			return;
		}
		if (points == null || deaths == null || !Double.isFinite(points)) {
			contribution.omit();
			return;
		}
		contribution.useTotals(points, deaths);
	}

	/**
	 * One hitsplat on an NPC. A heal is ignored. Only the open phase's two counters change.
	 */
	public void addHitsplat(boolean mine, int hitsplatType, int amount) {
		if (amount < 1 || hitsplatType == HitsplatID.HEAL || raid() == null || WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}
		contribution.addHit(mine, amount);
	}

	/** Ocellus changing into phase 2 or phase 3 closes the phase that just ended. */
	public void onOcellusNpc(int npcId) {
		if (raid() == null || WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}
		if (npcId == NpcID.TFA_OCELLUS_P2) {
			contribution.closeOcellusPhase(1);
		} else if (npcId == NpcID.TFA_OCELLUS_P3) {
			contribution.closeOcellusPhase(2);
		}
	}

	/** The third quadrant-destroy animation on Ocellus phase 3 closes that phase. */
	public void onOcellusAnimation(int npcId, int animation) {
		if (raid() == null || WorldUtils.playerOnUntrackedWorld(client) || npcId != NpcID.TFA_OCELLUS_P3) {
			return;
		}
		if (animation == AnimationID.NPC_OCELLUS_QUADRANT_DESTROY_IN) {
			contribution.noteOcellusQuadrant();
		}
	}

	/** Same free-for-all rule as the other raids. A share under the cutoff is not split. */
	public void setSplitRules(boolean defaultFfa, long ffaCutoff) {
		this.defaultFfa = defaultFfa;
		this.ffaCutoff = ffaCutoff;
	}

	public void setUniquePrice(Function<RaidUniques, Long> prices) {
		this.prices = prices == null ? unique -> null : prices;
	}

	/**
	 * The raid timer can disappear for more than a tick after a clear, before the chest opens.
	 * Dropping the attempt then would erase boss times and deaths that already arrived in chat.
	 * A timer that vanishes before any boss, death, or completion still drops an empty attempt.
	 * A wipe drops the attempt even after progress. Leave after the completion message writes
	 * the line. Leave before that message still drops the attempt.
	 */
	public void observeHud(boolean hudOpen, boolean chestOpen) {
		boolean timerOpened = timerKnown && !hudVisible && hudOpen;
		boolean timerClosed = timerKnown && hudVisible && !hudOpen;
		boolean chestClosed = chestVisible && !chestOpen;
		if (timerClosed && stored && claimOpen) {
			deathsAtClaim = contribution.countedDeaths();
		}
		if (timerClosed && hasUnstoredCompletion() && !completionParked) {
			parkedPoints = contribution.storedPoints();
			parkedDeaths = contribution.storedDeaths();
			parkedDeathCount = contribution.countedDeaths();
			completionParked = true;
		}
		hudVisible = hudOpen;
		chestVisible = chestOpen;
		if (chestClosed) {
			clearWaitingChest();
		}
		if ((hudOpen || chestOpen) && parked) {
			parked = false;
		} else if (claimOpen && stored && pending == null && !parked && !hudOpen && !chestOpen) {
			// The chest line is already saved. Closing the widgets keeps that row and lets the other raids log.
			releaseLiveAttempt();
		}
		if (ignoreHud) {
			sawHud = false;
			if (!hudOpen) {
				ignoreHud = false;
			} else {
				hudWasOpen = false;
				timerKnown = true;
				return;
			}
		}
		if (hudOpen) {
			sawHud = true;
			discardArmed = false;
		} else if (chestOpen) {
			discardArmed = false;
		} else if (sawHud && hudWasOpen && !stored && pending == null && !keptAttempt()) {
			discardArmed = true;
		} else if (discardArmed && !stored && pending == null && !keptAttempt()) {
			wipe();
		}
		if (timerOpened && stored && claimOpen && !tallyOutlivesClaim) {
			int deathsAfterClaim = Math.max(0, contribution.countedDeaths() - deathsAtClaim);
			contribution.beginTrackedRaid(deathsAfterClaim);
			tallyOutlivesClaim = true;
		} else if (timerOpened && completionParked && !stored && !tallyOutlivesClaim) {
			int deathsAfter = Math.max(0, contribution.countedDeaths() - parkedDeathCount);
			contribution.beginTrackedRaid(deathsAfter);
			tallyOutlivesClaim = true;
		} else if (timerOpened && !stored && !claimOpen && contribution.isIdle()) {
			contribution.beginTrackedRaid();
		}
		if (timerOpened) {
			contribution.noteTeamSize(party);
		}
		timerKnown = true;
		hudWasOpen = hudOpen;
	}

	/** Boss time, an own death, or the completion time. A teammate death does not keep the attempt. */
	private boolean keptAttempt() {
		return overall > 0 || contribution.hasOwnDeath() || !bossTimes.isEmpty();
	}

	private boolean hasProgress() {
		return keptAttempt() || killCount > 0;
	}

	/** Rewrites one claimed raid. A failure stays queued for a later tick. */
	private boolean persistClaimed(RaidTracker target) {
		FileReadWriter files = writer == null ? null : writer.get();
		if (files == null || target == null || target.getUniqueID() == null) {
			return false;
		}
		try {
			if (files.updateRTLog(target, RaidType.TFA)) {
				return true;
			}
		} catch (RuntimeException ex) {
			log.warn("Could not update The Fractured Archive log", ex);
			return false;
		}
		log.warn("The Fractured Archive line is still waiting to be rewritten");
		return false;
	}

	/**
	 * The next claimed raid whose rewrite failed. It stays queued until {@link #markPersisted()}.
	 * A pending append is not here; that raid is written by {@link #retryPending()}.
	 */
	public RaidTracker takeDirty() {
		return unwritten.isEmpty() ? null : unwritten.get(0);
	}

	/** The rewrite from {@link #takeDirty()} landed. */
	public void markPersisted() {
		if (!unwritten.isEmpty()) {
			unwritten.remove(0);
		}
	}

	/** The side panel is showing a claimed raid whose loot just changed. */
	public boolean takeViewUpdate() {
		boolean update = viewStale;
		viewStale = false;
		return update;
	}

	/** The chest line is open, so a later unique still belongs to it. */
	public boolean isClaimWindowOpen() {
		return claimOpen;
	}

	/** The open line has no chest items yet. A later container read should fill this row, not a new one. */
	public boolean openChestNeedsLoot() {
		if (!claimOpen) {
			return false;
		}
		RaidTracker open = liveClaim();
		return open == null || open.getLootList() == null || open.getLootList().isEmpty();
	}

	public void addUnique(String item, int id, String receiver, boolean own, Long value) {
		if (item == null || item.isEmpty() || raid() == null || WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}
		if (RaidUniques.archiveUnique(id, item) == null) {
			return;
		}
		UniqueDrop drop = new UniqueDrop();
		drop.setItem(item);
		drop.setId(id);
		drop.setReceiver(cleanName(receiver));
		drop.setOwn(own);
		if (value != null && value >= 0) {
			drop.setValue(value);
		}
		if (claimOpen) {
			RaidTracker target = pending != null ? pending : lastClaimed;
			if (target == null || containsDrop(target.getUniques(), drop)) {
				return;
			}
			if (target.getUniques() == null) {
				target.setUniques(new ArrayList<>());
			}
			// Lines saved before splitChoice existed: FFA plus a drop over the cutoff was a real choice.
			if (target.getSplitChoice() == null && forcedFreeForAll(target, ffaCutoff)) {
				target.setSplitChoice(RaidTracker.SPLIT_CHOICE_FFA);
			}
			target.getUniques().add(drop);
			applyChoice(target);
			// The chest line is already in the file. Write this unique now, before a leave can close the raid.
			publish(target);
			return;
		}
		if (containsDrop(drops, drop)) {
			return;
		}
		if (pending != null || ignoreHud) {
			return;
		}
		drops.add(drop);
	}

	/**
	 * The reward window was claimed. One line, with every unique already on that raid.
	 * A later unique does not append another line. False means the append failed and a later tick should retry.
	 */
	public boolean claimRewardWindow(List<RaidTrackerItem> chest, String playerName) {
		if (pending != null) {
			return false;
		}
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			return false;
		}
		if (stored && claimOpen) {
			if (openChestNeedsLoot()) {
				return fillOpenChest(chest, playerName);
			}
			// This chest was already filled. The one on screen is a later raid.
			endClaimWindow();
		}
		if (stored) {
			return false;
		}
		RaidTracker raidTracker = raid();
		if (raidTracker == null) {
			return false;
		}
		raidTracker.setInFracturedArchive(true);
		raidTracker.setRaidComplete(true);
		raidTracker.setChestOpened(true);
		raidTracker.setDate(System.currentTimeMillis());
		if (chest != null) {
			raidTracker.setLootList(new ArrayList<>(chest));
			for (RaidTrackerItem item : chest) {
				RaidUniques unique = uniqueFor(item);
				if (unique != null) {
					addUnique(unique.getName(), unique.getItemID(), playerName, true, item.getPrice());
				}
			}
		}
		return store();
	}

	/**
	 * The open line already exists. Chest items update that row.
	 * An empty container leaves the saved loot in place and does not append a second row.
	 */
	private boolean fillOpenChest(List<RaidTrackerItem> chest, String playerName) {
		RaidTracker target = pending != null ? pending : lastClaimed;
		if (target == null) {
			return false;
		}
		if (chest == null || chest.isEmpty()) {
			return true;
		}
		target.setInFracturedArchive(true);
		target.setRaidComplete(true);
		target.setChestOpened(true);
		target.setLootList(new ArrayList<>(chest));
		for (RaidTrackerItem item : chest) {
			RaidUniques unique = uniqueFor(item);
			if (unique != null) {
				addUnique(unique.getName(), unique.getItemID(), playerName, true, item.getPrice());
			}
		}
		publish(target);
		return true;
	}

	/** Writes the raid already on the live tracker. Used when the chest, bosses, and party size were recorded first. */
	public boolean claim() {
		if (stored || pending != null) {
			return false;
		}
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			return false;
		}
		RaidTracker raidTracker = raid();
		if (raidTracker == null) {
			return false;
		}
		raidTracker.setInFracturedArchive(true);
		raidTracker.setRaidComplete(true);
		raidTracker.setChestOpened(true);
		return store();
	}

	/**
	 * A wipe writes nothing. Leave before the completion message writes nothing.
	 * A chest whose append has not landed yet is left queued; leave and wipe chat close that window instead.
	 */
	public void wipe() {
		if (stored || pending != null) {
			return;
		}
		pending = null;
		claimOpen = false;
		clearLive();
		sawHud = false;
		hudWasOpen = false;
		discardArmed = false;
		ignoreHud = true;
		parked = false;
		resumeChecked = true;
		rewardWaitDone = true;
		RaidTracker raidTracker = raid();
		if (raidTracker == null) {
			return;
		}
		raidTracker.setInFracturedArchive(false);
		raidTracker.setRaidComplete(false);
		raidTracker.setChestOpened(false);
		raidTracker.setRaidTime(-1);
		raidTracker.setTeamSize(-1);
		raidTracker.setArchiveGuardianTime(null);
		raidTracker.setEzekielTime(null);
		raidTracker.setBorkTime(null);
		raidTracker.setMoiaTime(null);
		raidTracker.setDreadnothTime(null);
		raidTracker.setDagonhaiTime(null);
		raidTracker.setXarthTime(null);
		raidTracker.setOcellusTime(null);
		raidTracker.setOwnMvp(null);
		raidTracker.setDeaths(null);
		raidTracker.setContributionPoints(null);
		raidTracker.setOwnDeaths(null);
		raidTracker.setCompletionCount(-1);
		raidTracker.setUniques(null);
		raidTracker.setLootList(new ArrayList<>());
	}

	/** The raid that was just stored, including after the plugin replaces its live tracker. */
	public RaidTracker claimedRaid() {
		if (pending != null) {
			return pending;
		}
		if (stored && lastClaimed != null) {
			return lastClaimed;
		}
		return raid();
	}

	public boolean isAwaitingRetry() {
		return pending != null;
	}

	/**
	 * The completion message already arrived and the reward window never opened.
	 * Write one line with the times, team size, kill count, and any unique already heard.
	 * The chest was not opened, so the loot list is empty. Leave closes the line, so a later unique is ignored.
	 * A failed append stays queued. The live tracker is detached either way.
	 */
	/**
	 * @param close false keeps the line open so a chest that appears after logout updates this row
	 */
	private void saveCompletedLeave(boolean close) {
		if (stored || pending != null || overall <= 0) {
			return;
		}
		RaidTracker raidTracker = raid();
		if (raidTracker == null || WorldUtils.playerOnUntrackedWorld(client)) {
			wipe();
			return;
		}
		raidTracker.setInFracturedArchive(true);
		raidTracker.setRaidComplete(true);
		raidTracker.setChestOpened(false);
		raidTracker.setDate(System.currentTimeMillis());
		raidTracker.setLootList(new ArrayList<>());
		boolean wrote = store();
		if (!wrote && pending == null) {
			return;
		}
		RaidTracker saved = wrote ? lastClaimed : null;
		if (close) {
			closeClaim();
		} else {
			clearLive();
			sawHud = false;
			hudWasOpen = false;
			discardArmed = false;
			ignoreHud = false;
			hudVisible = false;
			chestVisible = false;
			parked = true;
			resumeChecked = true;
			rewardWaitDone = true;
		}
		detachLive = true;
		departed = saved;
	}

	/** The raid just saved because the player left after the completion message. */
	public RaidTracker takeDepartedRaid() {
		RaidTracker saved = departed;
		departed = null;
		return saved;
	}

	/** True when that leave must replace the live tracker, including a queued append. */
	public boolean takeLiveReset() {
		boolean reset = detachLive;
		detachLive = false;
		return reset;
	}

	/** The logged raid stops taking uniques. Boss times already on that line stay in the file. */
	private void endClaimWindow() {
		boolean written = stored && pending == null && lastClaimed != null;
		RaidTracker closing = lastClaimed;
		stored = false;
		claimOpen = false;
		parked = false;
		resumeChecked = true;
		rewardWaitDone = true;
		if (pending != null) {
			pending.setOpenClaim(null);
		}
		if (written) {
			closing.setOpenClaim(null);
			publish(closing);
		}
		clearLive();
		tallyOutlivesClaim = false;
		sawHud = false;
		hudWasOpen = false;
		discardArmed = false;
	}

	/** Leave or wipe after the chest. A failed append stays pending. */
	private void closeClaim() {
		endClaimWindow();
		ignoreHud = true;
	}

	/**
	 * Hop, or shutdown away from the reward chest. The completion is written and the claim closes.
	 * {@link #finished(boolean)} keeps the claim open when that chest is still on screen.
	 */
	public void finished() {
		finished(false);
	}

	/**
	 * Hop or shutdown. A completion that never reached the chest is written first, as one empty-chest line.
	 * A failed append is tried once and a failed rewrite is flushed. A failed try stays queued.
	 * {@code chestStillOpen} keeps that empty line open so a restart at this same chest can fill it.
	 * A leave has already closed its line, so a later raid's chest cannot land there.
	 */
	public void finished(boolean chestStillOpen) {
		returnTicks = -1;
		parked = false;
		if (chestStillOpen) {
			chestVisible = true;
		}
		if (hasUnstoredCompletion()) {
			saveCompletedLeave(!chestStillOpen);
		}
		if (chestStillOpen) {
			chestVisible = true;
		}
		flushUnwritten();
		if (pending != null) {
			RaidTracker saved = retryPending();
			if (saved != null && departed == null) {
				departed = saved;
				detachLive = true;
			}
		}
		if (chestStillOpen && claimOpen && openChestNeedsLoot()) {
			return;
		}
		if (pending != null || claimOpen) {
			closeClaim();
			return;
		}
		endClaimWindow();
	}

	/** A failed append keeps that raid and tries again on a later tick. */
	public RaidTracker retryPending() {
		if (pending == null) {
			return null;
		}
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			return null;
		}
		FileReadWriter files = writer == null ? null : writer.get();
		if (files == null || !files.appendArchive(pending)) {
			return null;
		}
		RaidTracker saved = pending;
		pending = null;
		stored = claimOpen;
		lastClaimed = saved;
		return saved;
	}

	private RaidTracker openClaim() {
		if (!claimOpen) {
			return null;
		}
		if (pending != null) {
			return pending;
		}
		return lastClaimed;
	}

	/** Writes the change now. A failed rewrite stays queued, and the panel refreshes either way. */
	private void publish(RaidTracker target) {
		viewStale = true;
		stampClaim(target);
		if (target == null || pending != null) {
			return;
		}
		if (persistClaimed(target)) {
			forget(target);
		} else {
			remember(target);
		}
	}

	private void remember(RaidTracker target) {
		if (target == null) {
			return;
		}
		for (RaidTracker waiting : unwritten) {
			if (waiting == target) {
				return;
			}
		}
		unwritten.add(target);
	}

	private void forget(RaidTracker target) {
		for (int i = 0; i < unwritten.size(); i++) {
			if (unwritten.get(i) == target) {
				unwritten.remove(i);
				return;
			}
		}
	}

	private void flushUnwritten() {
		for (int i = 0; i < unwritten.size();) {
			if (persistClaimed(unwritten.get(i))) {
				unwritten.remove(i);
			} else {
				i++;
			}
		}
	}

	private boolean blocked() {
		boolean queuedLiveRaid = pending != null && (pending == raid() || claimOpen);
		return stored || queuedLiveRaid || WorldUtils.playerOnUntrackedWorld(client) || raid() == null;
	}

	private boolean store() {
		RaidTracker raidTracker = raid();
		if (raidTracker == null || writer == null || writer.get() == null) {
			return false;
		}
		// The chest line is still this object. A later raid needs its own id, or the rewrite would hit both rows.
		if (lastClaimed != null && raidTracker.getUniqueID() != null
			&& raidTracker.getUniqueID().equals(lastClaimed.getUniqueID())) {
			String nextId = java.util.UUID.randomUUID().toString();
			raidTracker.setUniqueID(nextId);
			raidTracker.setKillCountID(nextId);
			raidTracker.setSplitChoice(null);
			raidTracker.setOpenClaim(null);
			raidTracker.setOpenChest(null);
			raidTracker.setDate(System.currentTimeMillis());
		}
		apply(raidTracker);
		completionParked = false;
		parkedPoints = null;
		parkedDeaths = null;
		claimOpen = true;
		lastClaimed = raidTracker;
		stampClaim(raidTracker);
		if (!writer.get().appendArchive(raidTracker)) {
			pending = raidTracker;
			return false;
		}
		pending = null;
		stored = true;
		return true;
	}

	private void apply(RaidTracker raidTracker) {
		raidTracker.setInRaidChambers(false);
		raidTracker.setInTheatreOfBlood(false);
		raidTracker.setInTombsOfAmascut(false);
		raidTracker.setInFracturedArchive(true);
		raidTracker.setArchiveGuardianTime(bossTimes.get(ArchiveBoss.ARCHIVE_GUARDIAN));
		raidTracker.setEzekielTime(bossTimes.get(ArchiveBoss.EZEKIEL));
		raidTracker.setBorkTime(bossTimes.get(ArchiveBoss.BORK));
		raidTracker.setMoiaTime(bossTimes.get(ArchiveBoss.MOIA));
		raidTracker.setDreadnothTime(bossTimes.get(ArchiveBoss.DREADNOTH));
		raidTracker.setDagonhaiTime(bossTimes.get(ArchiveBoss.DAGONHAI));
		raidTracker.setXarthTime(bossTimes.get(ArchiveBoss.XARTH));
		raidTracker.setOcellusTime(bossTimes.get(ArchiveBoss.OCELLUS));
		raidTracker.setRaidTime(overall > 0 ? overall : -1);
		raidTracker.setTeamSize(party);
		raidTracker.setOwnMvp(null);
		raidTracker.setDeaths(null);
		Double points = completionParked ? parkedPoints : contribution.storedPoints();
		Integer deaths = completionParked ? parkedDeaths : contribution.storedDeaths();
		if (points == null || deaths == null || !Double.isFinite(points)) {
			raidTracker.setContributionPoints(null);
			raidTracker.setOwnDeaths(null);
		} else {
			raidTracker.setContributionPoints(points);
			raidTracker.setOwnDeaths(deaths);
		}
		raidTracker.setCompletionCount(killCount > 0 ? killCount : -1);
		raidTracker.setUniques(drops.isEmpty() ? null : new ArrayList<>(drops));
		if (raidTracker.getSplitChoice() == null && defaultFfa) {
			raidTracker.setSplitChoice(RaidTracker.SPLIT_CHOICE_FFA);
		}
		applyChoice(raidTracker);
	}

	private void clearLive() {
		bossTimes.clear();
		overall = -1;
		party = -1;
		killCount = -1;
		completionParked = false;
		parkedPoints = null;
		parkedDeaths = null;
		if (!tallyOutlivesClaim) {
			contribution.reset();
			deathsAtClaim = 0;
		}
		drops.clear();
	}

	private void addBroadcast(String player, String itemName) {
		if (player == null || itemName == null) {
			return;
		}
		if (ignoreHud && !claimOpen) {
			return;
		}
		boolean inRaid = claimOpen || sawHud || !bossTimes.isEmpty() || overall > 0 || !drops.isEmpty();
		if (!inRaid) {
			return;
		}
		RaidUniques unique = RaidUniques.archiveUnique(-1, itemName.trim());
		if (unique == null) {
			return;
		}
		String local = "";
		if (client != null && client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
			local = client.getLocalPlayer().getName();
		}
		boolean own = !local.isEmpty() && cleanName(player).equalsIgnoreCase(cleanName(local));
		Long value = prices.apply(unique);
		addUnique(unique.getName(), unique.getItemID(), cleanName(player), own, value);
	}

	private static boolean containsDrop(List<UniqueDrop> existing, UniqueDrop drop) {
		if (existing == null) {
			return false;
		}
		for (UniqueDrop candidate : existing) {
			if (candidate == null || candidate.getItem() == null) {
				continue;
			}
			if (candidate.getItem().equalsIgnoreCase(drop.getItem())
				&& cleanName(candidate.getReceiver()).equalsIgnoreCase(cleanName(drop.getReceiver()))) {
				return true;
			}
		}
		return false;
	}

	/** Theatre's split rule, summed across every unique on this one raid line. */
	public static void recalculateSplits(RaidTracker raid) {
		recalculateSplits(raid, 0L);
	}

	/**
	 * Team size changed. A raid marked free-for-all stays that way.
	 * Otherwise each unique uses the cutoff again, because the share changes with the team size.
	 */
	public static void recalculateSplits(RaidTracker raid, long ffaCutoff) {
		if (raid == null) {
			return;
		}
		String choice = raid.getSplitChoice();
		if (RaidTracker.SPLIT_CHOICE_FFA.equals(choice) || (choice == null && raid.isFreeForAll())) {
			applySplits(raid, true, ffaCutoff, false);
		} else if (RaidTracker.SPLIT_CHOICE_SPLIT.equals(choice)) {
			applySplits(raid, false, ffaCutoff, false);
		} else {
			applySplits(raid, false, ffaCutoff, true);
		}
	}

	/**
	 * Logout settled away from the raid. A completion is written once. The chest line stays open
	 * so a late chest or unique updates that row. An attempt with no completion is dropped.
	 */
	private void park() {
		rewardWaitDone = true;
		resumeChecked = true;
		if (pending != null || isClaimWindowOpen()) {
			releaseLiveAttempt();
			return;
		}
		if (hasUnstoredCompletion()) {
			saveCompletedLeave(false);
			return;
		}
		clearIncomplete();
	}

	/** Chest already stored, or its append is still queued. Keep that raid and forget the live boss times. */
	private void releaseLiveAttempt() {
		clearLive();
		sawHud = false;
		hudWasOpen = false;
		discardArmed = false;
		ignoreHud = false;
		hudVisible = false;
		chestVisible = false;
		parked = claimOpen || pending != null;
		detachLive = true;
	}

	/** No completion to save. Drop the attempt without leaving the HUD ignored. */
	private void clearIncomplete() {
		pending = null;
		claimOpen = false;
		stored = false;
		parked = false;
		clearLive();
		sawHud = false;
		hudWasOpen = false;
		discardArmed = false;
		ignoreHud = false;
		hudVisible = false;
		chestVisible = false;
		detachLive = true;
		RaidTracker live = raid();
		if (live == null) {
			return;
		}
		live.setInFracturedArchive(false);
		live.setRaidComplete(false);
		live.setChestOpened(false);
	}

	/** The saved line is still the chest the player has open, so a repeated completion is not a new raid. */
	private boolean sameStoredDuration(int seconds) {
		if (seconds <= 0) {
			return false;
		}
		RaidTracker open = pending != null ? pending : lastClaimed;
		return open != null && open.getRaidTime() == seconds;
	}

	/** Writes whether this line is still accepting a unique, and whether its chest is still on screen. */
	private void stampClaim(RaidTracker target) {
		if (target == null) {
			return;
		}
		boolean open = claimOpen && (target == lastClaimed || target == pending);
		target.setOpenClaim(open ? Boolean.TRUE : null);
		boolean lootEmpty = target.getLootList() == null || target.getLootList().isEmpty();
		target.setOpenChest(open && chestVisible && lootEmpty ? Boolean.TRUE : null);
	}

	/** The chest widget closed. The line can still take a unique, but a restart must not fill it. */
	private void clearWaitingChest() {
		RaidTracker open = liveClaim();
		if (open == null || !Boolean.TRUE.equals(open.getOpenChest())) {
			return;
		}
		publish(open);
	}

	/**
	 * A stored ffa or split choice wins. Otherwise the cutoff decides.
	 * A line with no choice that is already free-for-all, and already has a drop over the cutoff,
	 * keeps that choice. A flag that only came from the cutoff is recalculated.
	 */
	private void applyChoice(RaidTracker target) {
		if (target == null) {
			return;
		}
		String choice = target.getSplitChoice();
		if (RaidTracker.SPLIT_CHOICE_FFA.equals(choice)) {
			applySplits(target, true, ffaCutoff, false);
		} else if (RaidTracker.SPLIT_CHOICE_SPLIT.equals(choice)) {
			applySplits(target, false, ffaCutoff, false);
		} else {
			applySplits(target, defaultFfa, ffaCutoff, true);
		}
	}

	/** True when free-for-all was chosen, not merely because every share was under the cutoff. */
	private static boolean forcedFreeForAll(RaidTracker raid, long cutoff) {
		if (raid == null || !raid.isFreeForAll() || raid.getUniques() == null) {
			return false;
		}
		int team = raid.getTeamSize();
		if (team < 1 || team > 8) {
			return false;
		}
		for (UniqueDrop drop : raid.getUniques()) {
			if (drop == null || drop.getValue() == null || drop.getValue() < 0) {
				continue;
			}
			if (drop.getValue() / team >= cutoff) {
				return true;
			}
		}
		return false;
	}

	private static void applySplits(RaidTracker raid, boolean forceFfa, long cutoff, boolean useCutoff) {
		if (raid == null || raid.getUniques() == null || raid.isSplitAmountLocked()) {
			return;
		}
		if (raid.getTeamSize() < 1 || raid.getTeamSize() > 8) {
			raid.setLootSplitReceived(-1);
			raid.setLootSplitPaid(-1);
			raid.setFreeForAll(false);
			return;
		}
		long received = 0;
		long paid = 0;
		boolean any = false;
		boolean anyFfa = false;
		boolean anySplit = false;
		for (UniqueDrop drop : raid.getUniques()) {
			if (drop == null || drop.getValue() == null || drop.getValue() < 0) {
				continue;
			}
			any = true;
			long value = drop.getValue();
			long share = value / raid.getTeamSize();
			boolean ffa = forceFfa || (useCutoff && share < cutoff);
			if (ffa) {
				anyFfa = true;
				if (drop.isOwn()) {
					received += value;
				}
			} else {
				anySplit = true;
				if (drop.isOwn()) {
					received += share;
					paid += value - share;
				} else {
					received += share;
				}
			}
		}
		if (!any) {
			return;
		}
		raid.setFreeForAll(anyFfa && !anySplit);
		raid.setLootSplitReceived(received > 0 ? received : -1);
		raid.setLootSplitPaid(!raid.isFreeForAll() && paid > 0 ? paid : -1);
	}

	private static RaidUniques uniqueFor(RaidTrackerItem item) {
		if (item == null) {
			return null;
		}
		return RaidUniques.archiveUnique(item.getId(), item.getName());
	}

	private static String normalize(String message) {
		if (message == null) {
			return "";
		}
		return COLOR_TAG.matcher(message).replaceAll("").replace('\u00A0', ' ').trim();
	}

	/** Same name the chest and a teammate broadcast can be compared on. */
	private static String cleanName(String name) {
		if (name == null) {
			return "";
		}
		return Text.removeTags(name).replace('\u00A0', ' ').replace('_', ' ').trim();
	}

	/** Whole seconds, matching the other raids. 2:34.80 is stored as 2:35. */
	static int durationSeconds(String duration) {
		if (duration == null || duration.isEmpty()) {
			return -1;
		}
		String[] split = duration.split(":");
		if (split.length == 3) {
			return Integer.parseInt(split[0]) * 3600
				+ Integer.parseInt(split[1]) * 60
				+ Math.round(Float.parseFloat(split[2]));
		}
		if (split.length == 2) {
			return Integer.parseInt(split[0]) * 60 + Math.round(Float.parseFloat(split[1]));
		}
		return -1;
	}
}
