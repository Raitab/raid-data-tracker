package com.raidtracker.filereadwriter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerItem;
import com.raidtracker.RaidType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

@Slf4j
public class FileReadWriter {

    @Getter
    private String username;
    private String coxDir;
    private String tobDir;
    private String toaDir;
    private String defaultDir;

    @Inject
    private Gson gson;

    public void writeToFile(RaidTracker raidTracker) {
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

        try {
            log.info("writer started");
            // use json format so serializing and deserializing is easy
            JsonParser parser = new JsonParser();
            FileWriter fw = new FileWriter(fileName, true); // the true will append the new data
            gson.toJson(parser.parse(getJSONString(raidTracker, gson, parser)), fw);
            fw.append("\n");
            fw.close();
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage() + " in writeToFile");
        }
    }

    public String getJSONString(RaidTracker raidTracker, Gson gson, JsonParser parser) {
        JsonObject RTJson = parser.parse(gson.toJson(raidTracker)).getAsJsonObject();

        List<RaidTrackerItem> lootList = raidTracker.getLootList();

        // temporary fix until I can get gson.tojson to work for arraylist<RaidTrackerItem>
        JsonArray lootListToString = new JsonArray();

        for (RaidTrackerItem item : lootList) {
            lootListToString.add(parser.parse(gson.toJson(item, new TypeToken<RaidTrackerItem>() {
            }.getType())));
        }

        RTJson.addProperty("lootList", lootListToString.toString());

        // massive bodge, works for now
        return RTJson.toString().replace("\\\"", "\"").replace("\"[", "[").replace("]\"", "]");
    }

    public ArrayList<RaidTracker> readFromFile(String alternateFile, RaidType raidType) {
        String fileName = getRaidFileName(raidType);

        if (alternateFile.length() != 0) {
            fileName = alternateFile;
        }

        try {
            JsonParser parser = new JsonParser();
            BufferedReader bufferedreader = new BufferedReader(new FileReader(fileName));
            String line;
            ArrayList<RaidTracker> RTList = new ArrayList<>();

            while ((line = bufferedreader.readLine()) != null && line.length() > 0) {
                try {
                    RaidTracker parsed = gson.fromJson(parser.parse(line), RaidTracker.class);
                    RTList.add(parsed);
                } catch (JsonSyntaxException e) {
                    System.out.println("Bad line: " + line);
                }
            }

            bufferedreader.close();

            return RTList;
        } catch (IOException e) {
            e.printStackTrace();
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
        File dir = new File(RUNELITE_DIR, "raid-data tracker");
        IGNORE_RESULT(dir.mkdir());
        dir = new File(dir, username);
        IGNORE_RESULT(dir.mkdir());
        File dir_cox = new File(dir, "cox");
        File dir_tob = new File(dir, "tob");
        File dir_toa = new File(dir, "toa");
        File dir_default = new File(dir, "unknown");
        IGNORE_RESULT(dir_cox.mkdir());
        IGNORE_RESULT(dir_tob.mkdir());
        IGNORE_RESULT(dir_toa.mkdir());
        IGNORE_RESULT(dir_default.mkdir());
        this.coxDir = dir_cox.getAbsolutePath();
        this.tobDir = dir_tob.getAbsolutePath();
        this.toaDir = dir_toa.getAbsolutePath();
        this.defaultDir = dir_default.getAbsolutePath();

        try {
            IGNORE_RESULT(new File(getRaidFileName(RaidType.COX)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(RaidType.TOB)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(RaidType.TOA)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(null)).createNewFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateUsername(final String username) {
        this.username = username;
        createFolders();
    }

    // Used for making sure ToA loot and points is accurate
    // Initial write made on reward chest interface opened,
    // this updates after player leaves to account for purples/pets others receive
    public void updateRTLog(RaidTracker raidTracker, RaidType raidType) {
        try {
            JsonParser parser = new JsonParser();
            String fileName = getRaidFileName(raidType);
            ArrayList<RaidTracker> RTList = readFromFile(raidType);
            FileWriter fw = new FileWriter(fileName, false); // the true will append the new data

            for (RaidTracker RT : RTList) {

                if (RT.getUniqueID().equals(raidTracker.getUniqueID()) && !RT.equals(raidTracker)) {
                    log.info("writer updated log");
                    RT = raidTracker;
                }

                gson.toJson(parser.parse(getJSONString(RT, gson, parser)), fw);

                fw.append("\n");
            }

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateRTList(ArrayList<RaidTracker> RTList, RaidType raidType) {
        try {
            JsonParser parser = new JsonParser();
            String fileName = getRaidFileName(raidType);
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
            e.printStackTrace();
        }
    }

    public boolean delete(RaidType raidType) {
        File newFile = new File(getRaidFileName(raidType));

        boolean isDeleted = newFile.delete();

        try {
            IGNORE_RESULT(newFile.createNewFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return isDeleted;
    }

    public void IGNORE_RESULT(boolean b) {
    }

    public String getRaidFileName(RaidType raidType) {
        final String DATA_FILE_NAME = "/raid_tracker_data.log";

        if (raidType == null) {
            return defaultDir + DATA_FILE_NAME;
        }

        switch (raidType) {
            case COX:
                return coxDir + DATA_FILE_NAME;
            case TOB:
                return tobDir + DATA_FILE_NAME;
            case TOA:
                return toaDir + DATA_FILE_NAME;
            default:
                return defaultDir + DATA_FILE_NAME;
        }
    }
}
