package com.raidtracker.profile;

import com.raidtracker.RaidTracker;

public final class ProfileFilter {
	private ProfileFilter() {
	}

	public static boolean matchesSelectedProfileType(RaidTracker parsed, String profileName) {
		String trimmedProfileName = profileName.trim();

		if (parsed == null || profileName == null) {
			return false;
		}
		else if (ProfileSelection.ALL_PROFILE_TYPES.equals(trimmedProfileName)) {
			return true;
		}
		else {
			return normalizeProfileType(parsed.getProfileType()).equalsIgnoreCase(normalizeProfileType(trimmedProfileName));
		}
	}

	private static String normalizeProfileType(String profileType) {
		String trimmedProfileType = profileType.trim();
		if (profileType == null || trimmedProfileType.isEmpty()) {
			return "STANDARD";
		}
		else {
			return trimmedProfileType;
		}
	}

	public static boolean matchesSelectedProfileHash(RaidTracker parsed, String profileHash) {
		String parsedHash = String.valueOf(parsed.getAccountHash());
		if (parsed == null || profileHash == null) {
			return false;
		}
		else if (ProfileSelection.ALL_PROFILE_HASHES.equals(profileHash)) {
			return true;
		}
		else if (ProfileSelection.UNKNOWN_PROFILE_HASHES.equalsIgnoreCase(profileHash)) {
			return parsed.getAccountHash() == -1L || "unknown".equalsIgnoreCase(parsedHash);
		}
		else {
			return parsedHash.equals(profileHash);
		}
	}
}
