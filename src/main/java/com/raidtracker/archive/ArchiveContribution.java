package com.raidtracker.archive;

/**
 * One raid's personal points and own deaths. Points already include the death penalty.
 * The other plugin's total replaces this tally. Otherwise a raid that was not seen from
 * the timer opening reports nothing.
 */
public final class ArchiveContribution {
	static final int ROOM_POINTS = 100;
	static final int OCELLUS_PHASE_1 = 60;
	static final int OCELLUS_PHASE_2 = 30;
	static final int OCELLUS_PHASE_3 = 158;
	static final int OCELLUS_PHASE_4 = 52;
	static final int RAID_POINTS = 700;
	static final int PERSONAL_FLOOR = -50;

	private boolean tracked;
	private boolean roomOpen;
	private boolean awaitingFirstBoss = true;
	/** Set when an external personal total replaced this tally. Later hits do not add to it. */
	private boolean replaced;
	private int ownDamage;
	private int phaseDamage;
	private int ocellusQuadrants;
	private int ocellusAwarded;
	private double points;
	private int ownDeaths;
	private int deathsAwaitingSize;

	/**
	 * The timer went from closed to open in this session. Hits in the open phase count.
	 * A death counted before this call, while the raid was not yet tracked, is kept.
	 */
	public void beginTrackedRaid() {
		beginTrackedRaid(tracked ? 0 : ownDeaths);
	}

	/** Same start, with deaths that already belong to this attempt. A penalty still waiting is kept with them. */
	public void beginTrackedRaid(int deathsToKeep) {
		int pending = deathsAwaitingSize;
		reset();
		tracked = true;
		roomOpen = true;
		ownDeaths = Math.max(0, deathsToKeep);
		deathsAwaitingSize = Math.min(Math.max(0, pending), ownDeaths);
	}

	/** Drop the fallback. An external total, and an own death already counted, stay. */
	public void omit() {
		if (replaced) {
			return;
		}
		tracked = false;
		roomOpen = false;
		awaitingFirstBoss = false;
		clearPhase();
		points = 0;
	}

	/** A test or the other plugin's personal total. Both values are present, including zero. */
	public void useTotals(double points, int ownDeaths) {
		reset();
		tracked = true;
		replaced = true;
		awaitingFirstBoss = false;
		this.points = points;
		this.ownDeaths = ownDeaths;
	}

	public boolean isIdle() {
		return !tracked && awaitingFirstBoss;
	}

	/** Deaths counted so far, including before the attempt is tracked. */
	public int countedDeaths() {
		return ownDeaths;
	}

	public void reset() {
		tracked = false;
		roomOpen = false;
		awaitingFirstBoss = true;
		replaced = false;
		ocellusQuadrants = 0;
		ocellusAwarded = 0;
		deathsAwaitingSize = 0;
		clearPhase();
		points = 0;
		ownDeaths = 0;
	}

	/**
	 * One hitsplat on an NPC in the open phase. Heals are filtered by the caller.
	 * The local player's hit counts for both totals. Every other hit counts for the phase only.
	 */
	public void addHit(boolean mine, int amount) {
		if (replaced || !roomOpen || amount < 1) {
			return;
		}
		phaseDamage += amount;
		if (mine) {
			ownDamage += amount;
		}
	}

	/** The boss's challenge-complete message. Ocellus awards its last phase. A phase with no hits adds nothing. */
	public void closeRoom(ArchiveBoss boss) {
		if (boss == null || replaced) {
			return;
		}
		if (awaitingFirstBoss) {
			awaitingFirstBoss = false;
			if (boss != ArchiveBoss.ARCHIVE_GUARDIAN) {
				omit();
				return;
			}
		}
		award(boss == ArchiveBoss.OCELLUS ? OCELLUS_PHASE_4 : ROOM_POINTS);
	}

	/**
	 * Ocellus phases 1 to 3. Each phase is awarded once. A phase before Archive Guardian drops the fallback.
	 */
	public void closeOcellusPhase(int phase) {
		if (!tracked || replaced || phase < 1 || phase > 3) {
			return;
		}
		if (awaitingFirstBoss) {
			omit();
			return;
		}
		int bit = 1 << phase;
		if ((ocellusAwarded & bit) != 0) {
			return;
		}
		ocellusAwarded |= bit;
		int weight = phase == 1 ? OCELLUS_PHASE_1 : phase == 2 ? OCELLUS_PHASE_2 : OCELLUS_PHASE_3;
		award(weight);
	}

	/** The third quadrant-destroy animation closes Ocellus phase 3. */
	public void noteOcellusQuadrant() {
		if (!tracked || replaced) {
			return;
		}
		ocellusQuadrants++;
		if (ocellusQuadrants == 3) {
			closeOcellusPhase(3);
		}
	}

	/**
	 * One local death. {@code teamSize} outside 1 to 8 waits until {@link #noteTeamSize(int)}.
	 * Personal points cannot go below {@link #PERSONAL_FLOOR}. An external total is not reduced again.
	 */
	public void addOwnDeath(int teamSize) {
		ownDeaths++;
		if (replaced) {
			return;
		}
		if (teamSize < 1 || teamSize > 8) {
			deathsAwaitingSize++;
			return;
		}
		penalize(teamSize);
	}

	/** Apply death penalties that arrived before the party size was known. */
	public void noteTeamSize(int teamSize) {
		if (!tracked || replaced || teamSize < 1 || teamSize > 8) {
			return;
		}
		while (deathsAwaitingSize > 0) {
			penalize(teamSize);
			deathsAwaitingSize--;
		}
	}

	public boolean hasOwnDeath() {
		return ownDeaths > 0;
	}

	/** Null when this raid was not tracked from the start. Zero is a real total. */
	public Double storedPoints() {
		if (!tracked) {
			return null;
		}
		return points;
	}

	/** Null when this raid was not tracked from the start. Zero means the player did not die. */
	public Integer storedDeaths() {
		if (!tracked) {
			return null;
		}
		return ownDeaths;
	}

	private void award(int weight) {
		if (tracked && phaseDamage > 0 && weight > 0) {
			points += weight * (long) ownDamage / phaseDamage;
		}
		clearPhase();
	}

	private void penalize(int teamSize) {
		if (!tracked || teamSize < 1) {
			return;
		}
		int penalty = (RAID_POINTS / teamSize) / 10;
		points = Math.max(PERSONAL_FLOOR, points - penalty);
	}

	private void clearPhase() {
		ownDamage = 0;
		phaseDamage = 0;
	}
}
