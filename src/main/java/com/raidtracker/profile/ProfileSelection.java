package com.raidtracker.profile;

public class ProfileSelection {
	public static final String ALL_PROFILE_TYPES = "All Profiles";
	public static final String ALL_PROFILE_HASHES = "All Accounts";
	public static final String UNKNOWN_PROFILE_HASHES = "Unknown";

	private String selectedProfileType = ALL_PROFILE_TYPES;
	private String profileHash = ALL_PROFILE_HASHES;

	public String getSelectedProfileType() {
		return selectedProfileType;
	}

	public void setSelectedProfileType(String selectedProfileType) {
		if (selectedProfileType == null || selectedProfileType.trim().isEmpty()) {
			return;
		}
		this.selectedProfileType = selectedProfileType;
	}

	public String getProfileHash() {
		return profileHash;
	}

	public void setProfileHash(String profileHash) {
		if (profileHash == null || profileHash.trim().isEmpty()) {
			return;
		}
		this.profileHash = profileHash;
	}

	public void reset() {
		this.selectedProfileType = ALL_PROFILE_TYPES;
		this.profileHash = ALL_PROFILE_HASHES;
	}
}
