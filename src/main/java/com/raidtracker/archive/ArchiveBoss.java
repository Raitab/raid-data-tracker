package com.raidtracker.archive;

/**
 * Bosses in The Fractured Archive. Archive Guardian is always first and Ocellus is always last.
 * Each middle fight is one choice from a pair. A skipped fight is not a value here.
 */
public enum ArchiveBoss {
	ARCHIVE_GUARDIAN("Archive Guardian"),
	EZEKIEL("Ezekiel"),
	BORK("Bork"),
	MOIA("Moia"),
	DREADNOTH("Dreadnoth the Jellied"),
	DAGONHAI("The Dagon'hai"),
	XARTH("Xarth"),
	OCELLUS("Ocellus");

	private final String chatName;

	ArchiveBoss(String chatName) {
		this.chatName = chatName;
	}

	/** The boss name printed in "Challenge complete". Ocellus phases are not separate lines. */
	public static ArchiveBoss fromChat(String name) {
		if (name == null) {
			return null;
		}
		String normalized = name.replace('\u2019', '\'').replace('\u2018', '\'').trim();
		for (ArchiveBoss boss : values()) {
			if (boss.chatName.equalsIgnoreCase(normalized)) {
				return boss;
			}
		}
		return null;
	}
}
