package com.raidtracker.filereadwriter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerItem;
import com.raidtracker.RaidType;
import com.raidtracker.UniqueDrop;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static net.runelite.client.RuneLite.RUNELITE_DIR;
import net.runelite.client.util.Text;

@Slf4j
public class FileReadWriter {

	/**
	 * The Fractured Archive writes these keys only, and only when the value is real.
	 * A skipped boss is left out. Theatre and Tombs still write the whole object.
	 */
	static final String[] TFA_LOG_FIELDS = {
		"raidTime",
		"archiveGuardianTime",
		"ezekielTime",
		"borkTime",
		"moiaTime",
		"dreadnothTime",
		"dagonhaiTime",
		"xarthTime",
		"ocellusTime",
		"teamSize",
		"contributionPoints",
		"ownDeaths",
		"completionCount",
		"lootSplitReceived",
		"lootSplitPaid",
		"FreeForAll",
		"splitChoice",
		"openClaim",
		"openChest",
		"uniques",
		"lootList",
		"uniqueID",
		"date"
	};

	@Getter
	private String username;
	private File storageRoot;
	private String coxDir;
	private String tobDir;
	private String toaDir;
	private String tfaDir;
    private String defaultDir;

	@Inject
    @Setter
	private Gson gson;

	/** One Fractured Archive read or rewrite at a time. Update and a live unique both replace this file. */
	private final Object archiveLogLock = new Object();

    public void writeToFile(RaidTracker raidTracker) {
		if (raidTracker.isInFracturedArchive()) {
			appendArchive(raidTracker);
			return;
		}

        String fileName;
		if (raidTracker.isInTheatreOfBlood()) {
            fileName = getRaidFileName(RaidType.TOB);
		} else if (raidTracker.isInTombsOfAmascut()) {
            fileName = getRaidFileName(RaidType.TOA);
		} else if (raidTracker.isInRaidChambers()) {
            fileName = getRaidFileName(RaidType.COX);
		} else {
            fileName = getRaidFileName(null);
			log.warn("writeToFile called without an inRaid flag set.", new IllegalStateException());
		}
		if (fileName == null) {
			log.warn("No character folder yet, so this raid was not written");
			return;
		}

        try {
			log.debug("writer started");
			//use json format so serializing and deserializing is easy
			JsonParser parser = new JsonParser();
			FileWriter fw = new FileWriter(fileName,true); //the true will append the new data
			gson.toJson(parser.parse(getJSONString(raidTracker, gson, parser)), fw);
			fw.append("\n");
			fw.close();
        } catch (IOException ioe) {
			log.error("IOException: {} in writeToFile", ioe.getMessage());
		}
	}

	/** One Fractured Archive line. False leaves the raid unstored so the caller can retry. */
	public boolean appendArchive(RaidTracker raidTracker) {
		synchronized (archiveLogLock) {
			return appendArchiveUnlocked(raidTracker);
		}
	}

	private boolean appendArchiveUnlocked(RaidTracker raidTracker) {
		if (raidTracker == null || tfaDir == null) {
			return false;
		}
		try (OutputStreamWriter out = new OutputStreamWriter(
			new FileOutputStream(getRaidFileName(RaidType.TFA), true), StandardCharsets.UTF_8)) {
			out.write(archiveLine(raidTracker));
			out.write('\n');
			return true;
		} catch (Exception ex) {
			log.error("Could not append The Fractured Archive log", ex);
			return false;
		}
	}

	/** Replaces the archive log from a temporary file. The live log stays in place until the temp write finishes. */
	public boolean rewriteArchive(ArrayList<RaidTracker> raids) {
		synchronized (archiveLogLock) {
			return rewriteArchiveUnlocked(raids);
		}
	}

	private boolean rewriteArchiveUnlocked(ArrayList<RaidTracker> raids) {
		if (tfaDir == null) {
			return false;
		}
		File logFile = new File(getRaidFileName(RaidType.TFA));
		File parent = logFile.getParentFile();
		if (parent == null) {
			return false;
		}
		File temp;
		try {
			temp = File.createTempFile("raid_tracker_data", ".log.tmp", parent);
		} catch (IOException e) {
			log.error("Error occurred writing the archive log", e);
			return false;
		}
		try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
			if (raids != null) {
				for (RaidTracker raid : raids) {
					if (raid == null) {
						continue;
					}
					out.write(archiveLine(raid));
					out.write('\n');
				}
			}
		} catch (Exception e) {
			log.error("Error occurred writing the archive log", e);
			IGNORE_RESULT(temp.delete());
			return false;
		}
		try {
			Files.move(temp.toPath(), logFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		} catch (AtomicMoveNotSupportedException e) {
			return replaceArchiveLog(temp, logFile);
		} catch (IOException e) {
			return replaceArchiveLog(temp, logFile);
		}
	}

	private boolean replaceArchiveLog(File temp, File logFile) {
		try {
			Files.move(temp.toPath(), logFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			log.error("Error occurred replacing the archive log", e);
			IGNORE_RESULT(temp.delete());
			return false;
		}
	}

	public String archiveLine(RaidTracker raidTracker) {
		JsonObject line = new JsonObject();
		for (String field : TFA_LOG_FIELDS) {
			JsonElement value = archiveValue(raidTracker, field);
			if (value != null) {
				line.add(field, value);
			}
		}
		return gson.toJson(line);
	}

	private JsonElement archiveValue(RaidTracker raidTracker, String field) {
		switch (field) {
			case "raidTime":
				return raidTracker.getRaidTime() > 0 ? new JsonPrimitive(raidTracker.getRaidTime()) : null;
			case "archiveGuardianTime":
				return positiveSeconds(raidTracker.getArchiveGuardianTime());
			case "ezekielTime":
				return positiveSeconds(raidTracker.getEzekielTime());
			case "borkTime":
				return positiveSeconds(raidTracker.getBorkTime());
			case "moiaTime":
				return positiveSeconds(raidTracker.getMoiaTime());
			case "dreadnothTime":
				return positiveSeconds(raidTracker.getDreadnothTime());
			case "dagonhaiTime":
				return positiveSeconds(raidTracker.getDagonhaiTime());
			case "xarthTime":
				return positiveSeconds(raidTracker.getXarthTime());
			case "ocellusTime":
				return positiveSeconds(raidTracker.getOcellusTime());
			case "teamSize":
				int teamSize = raidTracker.getTeamSize();
				return teamSize >= 1 && teamSize <= 8 ? new JsonPrimitive(teamSize) : null;
			case "contributionPoints":
				return trackedPoints(raidTracker);
			case "ownDeaths":
				if (trackedPoints(raidTracker) == null) {
					return null;
				}
				return new JsonPrimitive(raidTracker.getOwnDeaths());
			case "lootSplitReceived":
				return raidTracker.getLootSplitReceived() >= 0 ? new JsonPrimitive(raidTracker.getLootSplitReceived()) : null;
			case "lootSplitPaid":
				return raidTracker.getLootSplitPaid() >= 0 ? new JsonPrimitive(raidTracker.getLootSplitPaid()) : null;
			case "FreeForAll":
				return raidTracker.isFreeForAll() ? new JsonPrimitive(true) : null;
			case "splitChoice":
				return splitChoiceValue(raidTracker.getSplitChoice());
			case "openClaim":
				return Boolean.TRUE.equals(raidTracker.getOpenClaim()) ? new JsonPrimitive(true) : null;
			case "openChest":
				return Boolean.TRUE.equals(raidTracker.getOpenChest()) ? new JsonPrimitive(true) : null;
			case "completionCount":
				return raidTracker.getCompletionCount() > 0 ? new JsonPrimitive(raidTracker.getCompletionCount()) : null;
			case "uniques":
				JsonArray uniques = uniqueArray(raidTracker);
				return uniques.size() > 0 ? uniques : null;
			case "lootList":
				return lootArray(raidTracker);
			case "uniqueID":
				return textValue(raidTracker.getUniqueID());
			case "date":
				return new JsonPrimitive(raidTracker.getDate());
			default:
				return null;
		}
	}

	/** Both inputs, or neither. A missing or non-finite value leaves both keys off the line. */
	private JsonElement trackedPoints(RaidTracker raidTracker) {
		Double points = raidTracker.getContributionPoints();
		Integer ownDeaths = raidTracker.getOwnDeaths();
		if (points == null || ownDeaths == null || !Double.isFinite(points)) {
			return null;
		}
		return new JsonPrimitive(points);
	}

	private JsonElement splitChoiceValue(String choice) {
		if (RaidTracker.SPLIT_CHOICE_FFA.equals(choice) || RaidTracker.SPLIT_CHOICE_SPLIT.equals(choice)) {
			return new JsonPrimitive(choice);
		}
		return null;
	}

	private JsonElement positiveSeconds(Integer seconds) {
		if (seconds == null || seconds <= 0) {
			return null;
		}
		return new JsonPrimitive(seconds);
	}

	private JsonElement textValue(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		return new JsonPrimitive(text);
	}

	private JsonArray uniqueArray(RaidTracker raidTracker) {
		JsonArray uniques = new JsonArray();
		if (raidTracker.getUniques() == null) {
			return uniques;
		}
		for (UniqueDrop drop : raidTracker.getUniques()) {
			if (drop == null || drop.getItem() == null || drop.getItem().isEmpty()) {
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("item", drop.getItem());
			if (drop.getId() > 0) {
				entry.addProperty("id", drop.getId());
			}
			entry.addProperty("own", drop.isOwn());
			if (drop.getReceiver() != null && !drop.getReceiver().isEmpty()) {
				entry.addProperty("receiver", drop.getReceiver());
			}
			if (drop.getValue() != null && drop.getValue() >= 0) {
				entry.addProperty("value", drop.getValue());
			}
			uniques.add(entry);
		}
		return uniques;
	}

	private JsonArray lootArray(RaidTracker raidTracker) {
		JsonArray loot = new JsonArray();
		List<RaidTrackerItem> lootList = raidTracker.getLootList();
		if (lootList == null) {
			return loot;
		}
		for (RaidTrackerItem item : lootList) {
			if (item == null || item.getName() == null || item.getName().isEmpty() || item.getId() < 0) {
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("name", item.getName());
			entry.addProperty("id", item.getId());
			entry.addProperty("quantity", item.getQuantity());
			entry.addProperty("price", item.getPrice());
			loot.add(entry);
		}
		return loot;
	}

	/** Tests point the account folder at a temporary directory. The installed client keeps the RuneLite folder. */
	public void setStorageRoot(File storageRoot) {
		this.storageRoot = storageRoot;
	}

    public String getJSONString(RaidTracker raidTracker, Gson gson, JsonParser parser) {
		if (raidTracker.isInFracturedArchive()) {
			return archiveLine(raidTracker);
		}

		JsonObject RTJson =  parser.parse(gson.toJson(raidTracker)).getAsJsonObject();

		List<RaidTrackerItem> lootList = raidTracker.getLootList();

		//------------------ temporary fix until I can get gson.tojson to work for arraylist<RaidTrackerItem> ---------
		JsonArray lootListToString = new JsonArray();

		for (RaidTrackerItem item : lootList) {
			lootListToString.add(parser.parse(gson.toJson(item, new TypeToken<RaidTrackerItem>() {
			}.getType())));
		}

		RTJson.addProperty("lootList", lootListToString.toString());

		//-------------------------------------------------------------------------------------------------------------

		//massive bodge, works for now
        return RTJson.toString().replace("\\\"", "\"").replace("\"[", "[").replace("]\"", "]");
	}

    public ArrayList<RaidTracker> readFromFile(String alternateFile, RaidType raidType) {
		if (raidType == RaidType.TFA) {
			synchronized (archiveLogLock) {
				return readRaidLog(alternateFile, raidType);
			}
		}
		return readRaidLog(alternateFile, raidType);
	}

	private ArrayList<RaidTracker> readRaidLog(String alternateFile, RaidType raidType) {
        String fileName = getRaidFileName(raidType);
        boolean foundReplacementUnicode = false;

		if (alternateFile.length() != 0) {
			fileName = alternateFile;
		}
		if (fileName == null) {
			return new ArrayList<>();
		}

		try {
			JsonParser parser = new JsonParser();
			BufferedReader bufferedreader = raidType == RaidType.TFA
				? new BufferedReader(new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8))
				: new BufferedReader(new FileReader(fileName));
			String line;
			ArrayList<RaidTracker> RTList = new ArrayList<>();

			while ((line = bufferedreader.readLine()) != null) {
				if (raidType == RaidType.TFA) {
					if (line.trim().isEmpty()) {
						continue;
					}
				} else if (line.isEmpty()) {
					break;
				}
                if (line.contains("\uFFFD")) {
                    foundReplacementUnicode = true;
                    line = line.replace("\uFFFD", " ");
                }

				try {
					JsonElement parsedLine = parser.parse(line);
					RaidTracker parsed = gson.fromJson(parsedLine, RaidTracker.class);
					if (raidType == RaidType.TFA && parsed != null) {
						parsed.setInFracturedArchive(true);
						// Lines written before the field name was used stored a lower-case key.
						if (!parsed.isFreeForAll() && parsedLine.isJsonObject()) {
							JsonElement legacyFfa = parsedLine.getAsJsonObject().get("freeForAll");
							if (legacyFfa != null && legacyFfa.isJsonPrimitive() && legacyFfa.getAsBoolean()) {
								parsed.setFreeForAll(true);
							}
						}
						if (parsed.getLootList() != null) {
							parsed.getLootList().removeIf(item -> item == null || item.getName() == null);
						}
						if (parsed.getKillCountID() == null || parsed.getKillCountID().isEmpty()) {
							parsed.setKillCountID(parsed.getUniqueID());
						}
					}
					RTList.add(parsed);
				} catch (JsonSyntaxException e) {
					log.warn("Bad line: {}", line);
				}
			}

			bufferedreader.close();

            if (foundReplacementUnicode) {
                log.info("Found replacement unicode character while reading {} log: attempting to overwrite", Text.titleCase(raidType));
				if (raidType == RaidType.TFA) {
					rewriteArchiveUnlocked(RTList);
				} else {
					updateRTList(RTList, raidType);
				}
            }

			return RTList;
		} catch (IOException e) {
			log.error("Error occurred reading from file", e);
			return new ArrayList<>();
		}
	}

	public ArrayList<RaidTracker> readFromFile() {
		return readFromFile("", RaidType.COX);
	}

	public ArrayList<RaidTracker> readFromFile(RaidType raidType) {
		return readFromFile("", raidType);
	}

    public void createFolders() {
		String folder = accountFolderName(username);
		if (folder == null) {
			return;
		}
		File base = storageRoot != null ? storageRoot : RUNELITE_DIR;
		File dir = new File(base, "raid-data tracker");
		IGNORE_RESULT(dir.mkdir());
		dir = new File(dir, folder);
		IGNORE_RESULT(dir.mkdir());
		File dir_cox = new File(dir, "cox");
		File dir_tob = new File(dir, "tob");
		File dir_toa = new File(dir, "toa");
		File dir_tfa = new File(dir, "tfa");
        File dir_default = new File(dir, "unknown");
		IGNORE_RESULT(dir_cox.mkdir());
		IGNORE_RESULT(dir_tob.mkdir());
		IGNORE_RESULT(dir_toa.mkdir());
		IGNORE_RESULT(dir_tfa.mkdir());
        IGNORE_RESULT(dir_default.mkdir());
		this.coxDir = dir_cox.getAbsolutePath();
		this.tobDir = dir_tob.getAbsolutePath();
		this.toaDir = dir_toa.getAbsolutePath();
		this.tfaDir = dir_tfa.getAbsolutePath();
        this.defaultDir = dir_default.getAbsolutePath();

		try {
            IGNORE_RESULT(new File(getRaidFileName(RaidType.COX)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(RaidType.TOB)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(RaidType.TOA)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(RaidType.TFA)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(null)).createNewFile());
		} catch (IOException e) {
			log.error("Error occurred creating new files", e);
		}
	}

	/**
	 * Point the logs at one character. A blank login name is refused, so raids are not written
	 * into the shared plugin folder. False means the folder did not change.
	 */
	public boolean updateUsername(final String username) {
		String folder = accountFolderName(username);
		if (folder == null) {
			return false;
		}
		if (folder.equals(this.username) && coxDir != null) {
			return false;
		}
		this.username = folder;
		createFolders();
		return true;
	}

	/** A directory name for one character. Blank, current, parent, and path characters are refused. */
	static String accountFolderName(String username) {
		if (username == null) {
			return null;
		}
		String cleaned = username.replace('\u00A0', ' ').trim();
		if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
			return null;
		}
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if (c < ' ' || "\\/:*?\"<>|".indexOf(c) >= 0) {
				return null;
			}
		}
		return cleaned;
	}

	// Used for making sure ToA loot and points is accurate
	// Initial write made on reward chest interface opened,
	// this updates after player leaves to account for purples/pets others receive
	public boolean updateRTLog(RaidTracker raidTracker, RaidType raidType) {
		if (raidType == RaidType.TFA) {
			synchronized (archiveLogLock) {
				return replaceArchiveLine(raidTracker);
			}
		}

		try {
			JsonParser parser = new JsonParser();
            String fileName = getRaidFileName(raidType);
			if (fileName == null) {
				return false;
			}
			ArrayList<RaidTracker> RTList = readFromFile(raidType);

			FileWriter fw = new FileWriter(fileName, false); // the true will append the new data

			for (RaidTracker RT : RTList) {

				if (RT.getUniqueID().equals(raidTracker.getUniqueID()) && !RT.equals(raidTracker)) {
					log.debug("writer updated log");
					RT = raidTracker;
				}

				gson.toJson(parser.parse(getJSONString(RT, gson, parser)), fw);

				fw.append("\n");
			}

			fw.close();
			return true;
		} catch (IOException e) {
			log.error("Error occurred updating the log", e);
			return false;
		}
	}

	/** Applies one saved raid onto the current archive log. The read and the rewrite stay inside one lock. */
	private boolean replaceArchiveLine(RaidTracker raidTracker) {
		if (raidTracker == null || raidTracker.getUniqueID() == null) {
			return false;
		}
		ArrayList<RaidTracker> RTList = readRaidLog("", RaidType.TFA);
		boolean found = false;
		boolean changed = false;
		String updatedLine = archiveLine(raidTracker);
		for (int i = 0; i < RTList.size(); i++) {
			RaidTracker RT = RTList.get(i);
			if (RT == null || !raidTracker.getUniqueID().equals(RT.getUniqueID())) {
				continue;
			}
			found = true;
			if (!updatedLine.equals(archiveLine(RT))) {
				RTList.set(i, raidTracker);
				changed = true;
			}
		}
		if (!found) {
			return false;
		}
		return !changed || rewriteArchiveUnlocked(RTList);
	}

	/**
	 * Folds the panel rows into the current archive log.
	 * A raid saved after the panel copied its list stays on the line it already has.
	 */
	private void mergeArchiveLines(ArrayList<RaidTracker> edited) {
		ArrayList<RaidTracker> onDisk = readRaidLog("", RaidType.TFA);
		LinkedHashMap<String, RaidTracker> byId = new LinkedHashMap<>();
		if (edited != null) {
			for (RaidTracker raid : edited) {
				if (raid != null && raid.getUniqueID() != null) {
					byId.put(raid.getUniqueID(), raid);
				}
			}
		}
		ArrayList<RaidTracker> merged = new ArrayList<>();
		for (RaidTracker disk : onDisk) {
			if (disk == null) {
				continue;
			}
			String id = disk.getUniqueID();
			if (id == null) {
				merged.add(disk);
				continue;
			}
			RaidTracker replacement = byId.remove(id);
			merged.add(replacement != null ? replacement : disk);
		}
		merged.addAll(byId.values());
		rewriteArchiveUnlocked(merged);
	}

	public void updateRTList(ArrayList<RaidTracker> RTList, RaidType raidType) {
		if (raidType == RaidType.TFA) {
			synchronized (archiveLogLock) {
				mergeArchiveLines(RTList);
			}
			return;
		}

		try {
			JsonParser parser = new JsonParser();
            String fileName = getRaidFileName(raidType);
			if (fileName == null) {
				return;
			}
			FileWriter fw = new FileWriter(fileName, false); // the true will append the new data

			for (RaidTracker RT : RTList) {
				if (RT.getLootSplitPaid() > 0) {
					RT.setSpecialLootInOwnName(true);
                } else {
					// bit of a wonky check, so try to avoid with lootsplitpaid if possible
                    RT.setSpecialLootInOwnName(!RT.getLootList().isEmpty()
                            && RT.getLootList().get(0).getName().equalsIgnoreCase(RT.getSpecialLoot()));
				}

				gson.toJson(parser.parse(getJSONString(RT, gson, parser)), fw);

				fw.append("\n");
			}

			fw.close();

		} catch (IOException e) {
			log.error("Error occurred updating the log list", e);
		}
	}

    public RaidTracker getUnclaimedRewardsRT(long accountHash, RaidType raidType) {
        ArrayList<RaidTracker> logs = readFromFile(raidType);

        logs = logs.stream().filter(
            RT -> RT.getAccountHash() == accountHash
        ).collect(Collectors.toCollection(ArrayList::new));

        if (logs.isEmpty()) return null;

        RaidTracker lastLog = logs.get(logs.size() - 1);

        return lastLog.isChestOpened() ? null : lastLog;
    }

	public boolean delete(RaidType raidType) {
		if (raidType == RaidType.TFA) {
			synchronized (archiveLogLock) {
				return deleteLog(raidType);
			}
		}
		return deleteLog(raidType);
	}

	private boolean deleteLog(RaidType raidType) {
		String fileName = getRaidFileName(raidType);
		if (fileName == null) {
			return false;
		}
        File newFile = new File(fileName);

		boolean isDeleted = newFile.delete();

		try {
			IGNORE_RESULT(newFile.createNewFile());
		} catch (IOException e) {
			log.error("Error occurred creating new file", e);
		}

		return isDeleted;
	}

    public void IGNORE_RESULT(boolean b) {
    }

    public String getRaidFileName(RaidType raidType) {
        final String DATA_FILE_NAME = "/raid_tracker_data.log";
		String dir;
        if (raidType == null) {
            dir = defaultDir;
        } else {
			switch (raidType) {
				case COX:
					dir = coxDir;
					break;
				case TOB:
					dir = tobDir;
					break;
				case TOA:
					dir = toaDir;
					break;
				case TFA:
					dir = tfaDir;
					break;
				default:
					dir = defaultDir;
					break;
			}
		}
		if (dir == null) {
			return null;
		}
		return dir + DATA_FILE_NAME;
	}
}
