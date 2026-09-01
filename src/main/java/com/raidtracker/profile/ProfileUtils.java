package com.raidtracker.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.raidtracker.RaidType;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class ProfileUtils {
	public List<String> getProfileNames(File rootDir) {
		List<String> profileTypes = new ArrayList<>();
		forEachRaidLog(rootDir, logFile -> collectProfileTypes(logFile, profileTypes));
		profileTypes.sort(String::compareToIgnoreCase);
		return profileTypes;
	}

	public List<String> getProfileHashes(File rootDir) {
		List<String> profileHashes = new ArrayList<>();
		forEachRaidLog(rootDir, logFile -> collectProfileHashes(logFile, profileHashes));
		profileHashes.sort(Comparator.comparingLong(value -> {
			try {
				return Long.parseLong(value);
			} catch (NumberFormatException e) {
				return Long.MIN_VALUE;
			}
		}));
		return profileHashes;
	}

	private void forEachRaidLog(File rootDir, Consumer<File> consumer) {
		File[] profileDirs = rootDir.listFiles(File::isDirectory);
		if (profileDirs == null) {
			return;
		}

		for (File profileDir : profileDirs) {
			for (RaidType raidType : RaidType.values()) {
				consumer.accept(getRaidLogFile(profileDir, raidType));
			}
		}
	}

	private File getRaidLogFile(File profileDir, RaidType raidType) {
		File logFile = new File(new File(profileDir, raidType.name().toLowerCase()), "raid_tracker_data.log");
		if (!logFile.isFile() && profileDir.getName().equalsIgnoreCase(raidType.name())) {
			return new File(profileDir, "raid_tracker_data.log");
		}
		return logFile;
	}

	private void collectProfileTypes(File logFile, List<String> profileTypes) {
		if (!logFile.isFile()) {
			return;
		}

		try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
			String line;
			while ((line = reader.readLine()) != null) {
				try {
					JsonObject json = new JsonParser().parse(line).getAsJsonObject();
					if (json.has("profileType") && !json.get("profileType").isJsonNull()) {
						String profileType = json.get("profileType").getAsString();
						if (!profileType.trim().isEmpty() && !profileTypes.contains(profileType)) {
							profileTypes.add(profileType);
						}
					}
				} catch (IllegalStateException | JsonSyntaxException ignored) {
				}
			}
		} catch (IOException e) {
			log.warn("Error discovering profile types from {}", logFile, e);
		}
	}

	private void collectProfileHashes(File logFile, List<String> profileHashes) {
		if (!logFile.isFile()) {
			return;
		}

		try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
			String line;
			while ((line = reader.readLine()) != null) {
				try {
					JsonObject json = new JsonParser().parse(line).getAsJsonObject();
					JsonElement accountHash = json.get("accountHash");
					String profileHash = accountHash == null || accountHash.isJsonNull()
							? ProfileSelection.UNKNOWN_PROFILE_HASHES
							: String.valueOf(accountHash.getAsLong());

					if ("-1".equals(profileHash)) {
						profileHash = ProfileSelection.UNKNOWN_PROFILE_HASHES;
					}

					if (!profileHashes.contains(profileHash)) {
						profileHashes.add(profileHash);
					}
				} catch (IllegalStateException | JsonSyntaxException ignored) {
				}
			}
		} catch (IOException e) {
			log.warn("Error discovering profile hashes from {}", logFile, e);
		}
	}
}
