package com.raidtracker.ui;

import com.raidtracker.RaidTracker;
import net.runelite.api.ItemID;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RaidTrackerFilters {
	private RaidTrackerFilters() {
	}

	public static ArrayList<RaidTracker> byName(ArrayList<RaidTracker> records, String name) {
		if (records == null || name == null || name.trim().isEmpty()) {
			return new ArrayList<>();
		}
		return records.stream()
				.filter(RT -> name.toLowerCase().equals(RT.getSpecialLoot().toLowerCase()))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	public static ArrayList<RaidTracker> byNonEmptyReceiver(ArrayList<RaidTracker> records, Function<RaidTracker, String> receiverExtractor) {
		if (records == null) {
			return new ArrayList<>();
		}
		return records.stream()
				.filter(RT -> {
					String receiver = receiverExtractor.apply(RT);
					return receiver != null && !receiver.isEmpty();
				})
				.collect(Collectors.toCollection(ArrayList::new));
	}

	public static ArrayList<RaidTracker> ownDrops(ArrayList<RaidTracker> records, Function<String, RaidUniques> uniqueLookup) {
		if (records == null) {
			return new ArrayList<>();
		}
		return records.stream()
				.filter(RT -> {
					if (RT.getSpecialLoot().isEmpty() || RT.getLootList().isEmpty()) {
						return false;
					}
					RaidUniques unique = uniqueLookup.apply(RT.getSpecialLoot());
					return unique != null && RT.getLootList().get(0).getId() == unique.getItemID();
				})
				.collect(Collectors.toCollection(ArrayList::new));
	}

	public static ArrayList<RaidTracker> ownKits(ArrayList<RaidTracker> records) {
		if (records == null) {
			return new ArrayList<>();
		}
		return records.stream()
				.filter(RT -> RT.getLootList().stream()
					.anyMatch(loot -> loot.getId() == ItemID.TWISTED_ANCESTRAL_COLOUR_KIT))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	public static ArrayList<RaidTracker> ownDusts(ArrayList<RaidTracker> records) {
		if (records == null) {
			return new ArrayList<>();
		}
		return records.stream()
				.filter(RT -> RT.getLootList().stream()
					.anyMatch(loot -> loot.getId() == ItemID.METAMORPHIC_DUST))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	public static ArrayList<RaidTracker> ownPets(ArrayList<RaidTracker> records) {
		if (records == null) {
			return new ArrayList<>();
		}
		return records.stream()
				.filter(RaidTracker::isPetInMyName)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	public static ArrayList<RaidTracker> distinctKills(ArrayList<RaidTracker> records) {
		if (records == null) {
			return new ArrayList<>();
		}
		Map<String, RaidTracker> tempUUIDMap = new LinkedHashMap<>();
		for (RaidTracker RT : records) {
			tempUUIDMap.put(RT.getKillCountID(), RT);
		}
		return new ArrayList<>(tempUUIDMap.values());
	}
}
