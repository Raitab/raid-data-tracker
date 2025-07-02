package com.raidtracker.webhook;

import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerItem;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DiscordWebhook {

    private final OkHttpClient httpClient;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    public DiscordWebhook(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Sends the raid data to the specified webhook URL.
     * @param raidTracker The fully populated raid data object.
     * @param webhookUrl The Discord webhook URL.
     */
    public void sendRaid(final RaidTracker raidTracker, final String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        String jsonPayload = buildJsonPayload(raidTracker);

        RequestBody body = RequestBody.create(JSON, jsonPayload);
        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("Failed to send raid data to Discord webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    log.error("Failed to send raid data to Discord. Server responded with: {} {}", response.code(), response.body() != null ? response.body().string() : "No Body");
                }
                response.close();
            }
        });
    }

    private String buildJsonPayload(RaidTracker rt) {
        // Main embed structure
        StringBuilder embed = new StringBuilder();
        embed.append("{");

        String description = "Raid Completion Data";
        embed.append("\"content\": null,");
        embed.append("\"embeds\": [{");
        embed.append("\"description\": \"").append(description).append("\",");

        // Determine raid type and set title/color accordingly
        if (rt.isInRaidChambers()) {
            embed.append("\"title\": \"Chambers of Xeric").append("\",");
            embed.append("\"color\": 4995442,"); // CoX Brown
            buildCoxFields(embed, rt);
        } else if (rt.isInTheatreOfBlood()) {
            embed.append("\"title\": \"Theatre of Blood").append("\",");
            embed.append("\"color\": 9320960,"); // ToB Red
            buildTobFields(embed, rt);
        } else if (rt.isInTombsOfAmascut()) {
            embed.append("\"title\": \"Tombs of Amascut").append("\",");
            embed.append("\"color\": 14594626,"); // ToA Gold
            buildToaFields(embed, rt);
        } else {
            embed.append("\"title\": \"Raid Completion").append("\",");
            embed.append("\"color\": 10181046,"); // Grey
            buildCommonFields(embed, rt); // Fallback to common fields if raid type is unknown
        }

        // Add footer with timestamp
        embed.append("\"footer\": {\"text\": \"Raid Data Tracker\"},");
        embed.append("\"timestamp\": \"").append(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new java.util.Date(rt.getDate()))).append("\"");

        embed.append("}]}");
        return embed.toString();
    }

    // Helper to build the "fields" array for the embed
    private void appendFields(StringBuilder embed, List<String> fields) {
        embed.append("\"fields\": [").append(String.join(",", fields)).append("],");
    }

    // Builder for Common Fields (used by all raid types)
    private void buildCommonFields(StringBuilder embed, RaidTracker rt) {
        List<String> fields = new ArrayList<>();

        // General Info
        fields.add(createField("Team Size", String.valueOf(rt.getTeamSize()), true));
        if (rt.isChallengeMode()) {
            fields.add(createField("Mode", "Challenge Mode", true));
        }
        fields.add(createField("Total Points", formatNumber(rt.getTotalPoints()), true));
        fields.add(createField("Personal Points", formatNumber(rt.getPersonalPoints()), true));
        fields.add(createField("Completion Time", secondsToMinuteString(rt.getRaidTime()), true));

        // Loot Section
        buildLootFields(fields, rt);

        appendFields(embed, fields);
    }

    // Specific builder for CoX
    private void buildCoxFields(StringBuilder embed, RaidTracker rt) {
        List<String> fields = new ArrayList<>();

        fields.add(createField("Team Size", String.valueOf(rt.getTeamSize()), true));
        if (rt.isChallengeMode()) {
            fields.add(createField("Mode", "Challenge Mode", true));
        } else {
            fields.add(createField("Mode", "Normal", true));
        }
        fields.add(createField("Total Points", formatNumber(rt.getTotalPoints()), true));
        fields.add(createField("Personal Points", formatNumber(rt.getPersonalPoints()), true));
        fields.add(createField("Your Deaths", String.valueOf(rt.getPersonalDeathCount()), true));

        // Times
        fields.add(createField("Raid Time", secondsToMinuteString(rt.getRaidTime()), false));
        fields.add(createField("Olm Phase", secondsToMinuteString(rt.getRaidTime() - rt.getLowerTime()), true));
        fields.add(createField("Lower Level", secondsToMinuteString(rt.getLowerTime() - rt.getMiddleTime()), true));
        fields.add(createField("Upper Level", secondsToMinuteString(rt.getUpperTime()), true));

        // Loot Section
        buildLootFields(fields, rt);

        appendFields(embed, fields);
    }

    // Specific builder for ToB
    private void buildTobFields(StringBuilder embed, RaidTracker rt) {
        List<String> fields = new ArrayList<>();

        fields.add(createField("Team Size", String.valueOf(rt.getTeamSize()), true));
        if (rt.isChallengeMode()) {
            fields.add(createField("Mode", "Hard Mode", true));
        } else {
            fields.add(createField("Mode", "Normal", true));
        }
        fields.add(createField("MVP", rt.getMvp().isEmpty() ? "N/A" : rt.getMvp(), true));
        fields.add(createField("Completion Time", secondsToMinuteString(rt.getTobCompTime()), false));

        // Deaths
        fields.add(createField("Your Deaths", String.valueOf(rt.getPersonalDeathCount()), true));
        fields.add(createField("Team Deaths", String.valueOf(rt.getTotalTeamDeathCount()), true));

        // Loot Section
        buildLootFields(fields, rt);

        appendFields(embed, fields);
    }

    // Specific builder for ToA
    private void buildToaFields(StringBuilder embed, RaidTracker rt) {
        List<String> fields = new ArrayList<>();

        fields.add(createField("Team Size", String.valueOf(rt.getTeamSize()), true));
        fields.add(createField("Raid Level", String.valueOf(rt.getRaidLevel()), true));
        fields.add(createField("Total Points", formatNumber(rt.getTotalPoints()), true));
        fields.add(createField("Completion Time", secondsToMinuteString(rt.getToaCompTime()), false));

        // Deaths
        fields.add(createField("Your Deaths", String.valueOf(rt.getPersonalDeathCount()), true));
        fields.add(createField("Team Deaths", String.valueOf(rt.getTotalTeamDeathCount()), true));

        // Path Times
        fields.add(createField("Path of Apmeken", secondsToMinuteString(rt.getApmekenTime() + rt.getBabaTime()), true));
        fields.add(createField("Path of Scabaras", secondsToMinuteString(rt.getScabarasTime() + rt.getKephriTime()), true));
        fields.add(createField("Path of Het", secondsToMinuteString(rt.getHetTime() + rt.getAkkhaTime()), true));
        fields.add(createField("Path of Crondis", secondsToMinuteString(rt.getCrondisTime() + rt.getZebakTime()), true));
        fields.add(createField("Wardens", secondsToMinuteString(rt.getWardensTime()), true));

        // Loot Section
        buildLootFields(fields, rt);

        appendFields(embed, fields);
    }

    // Helper to build the loot-related fields, which are common to all raids
    private void buildLootFields(List<String> fields, RaidTracker rt) {
        if (!rt.getSpecialLoot().isEmpty()) {
            String specialLootText = rt.isSpecialLootInOwnName() ?
                    "**" + rt.getSpecialLoot() + "** (In your name!)" :
                    rt.getSpecialLoot() + " (Received by: " + rt.getSpecialLootReceiver() + ")";
            fields.add(createField("💜 Special Loot", specialLootText, false));
        }

        StringBuilder lootString = new StringBuilder();
        long totalLootValue = 0;
        if (rt.getLootList() != null && !rt.getLootList().isEmpty()) {
            for (RaidTrackerItem item : rt.getLootList()) {
                long value = item.getPrice();
                totalLootValue += value;
                lootString.append("`").append(item.getQuantity()).append("x` ")
                        .append(item.getName())
                        .append(" *(").append(formatNumber(value)).append(" gp)*\n");
            }
        }

        if (lootString.length() == 0) {
            lootString.append("No notable loot.");
        }

        fields.add(createField("💰 Loot Chest (Value: " + formatNumber(totalLootValue) + " gp)", lootString.toString(), false));
    }


    // Creates a JSON object for a field
    private String createField(String name, String value, boolean inline) {
        // Escape quotes in name and value to prevent breaking the JSON
        String safeName = name.replace("\"", "\\\"");
        String safeValue = value.replace("\"", "\\\"").replace("\n", "\\n");
        return String.format("{\"name\": \"%s\", \"value\": \"%s\", \"inline\": %b}", safeName, safeValue, inline);
    }

    private String formatNumber(long number) {
        if (number < 0) return "N/A";
        return NUMBER_FORMAT.format(number);
    }

    private String secondsToMinuteString(int seconds) {
        if (seconds <= 0) {
            return "N/A";
        }
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}