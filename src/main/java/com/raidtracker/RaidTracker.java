package com.raidtracker;

import lombok.Data;

import java.util.ArrayList;
import java.util.UUID;

@Data
public class RaidTracker {

    long accountHash = -1;
    String profileType = "";
    boolean chestOpened = false;
    boolean raidComplete = false;
    boolean loggedIn = false;
    boolean challengeMode = false;
    boolean inRaidChambers = false;
    boolean inTheatreOfBlood = false;
	// I'd prefer a single k:v pair with a RaidType enum, leaving as separate bools to simplify backwards compatibility
	boolean inTombsOfAmascut = false;
    boolean FreeForAll = false;

    /** Player or default setting. "ffa" or "split". Absent means the cutoff still decides. */
    public static final String SPLIT_CHOICE_FFA = "ffa";
    public static final String SPLIT_CHOICE_SPLIT = "split";
    String splitChoice;

    /**
     * The chest line is still accepting a teammate unique.
     * Absent once that window closes. Left off Chambers, Theatre, and Tombs lines.
     */
    Boolean openClaim;

    /**
     * This empty line is still waiting for the reward chest that was on screen.
     * Absent once that widget closes. The raid timer does not set this.
     */
    Boolean openChest;

    int upperTime = -1;
    int middleTime = -1;
    int lowerTime = -1;
    int raidTime = -1;

    int shamansTime = -1;
    int vasaTime = -1;
    int vanguardsTime = -1;
    int mysticsTime = -1;
    int tektonTime = -1;
    int muttadilesTime = -1;
    int guardiansTime = -1;
    int vespulaTime = -1;
    int iceDemonTime = -1;
    int thievingTime = -1;
    int tightropeTime = -1;
    int crabsTime = -1;

	int raidLevel = -1;
    int totalPoints = -1;
    int personalPoints = -1;
	int personalDeathCount = 0;
	int totalTeamDeathCount = 0;
    int teamSize = -1;
    double percentage = -1.0;
    int completionCount = -1;
    String specialLoot = "";
    String specialLootReceiver = "";
    boolean specialLootInOwnName = false;
    long specialLootValue = -1;
    String kitReceiver = "";
    String dustReceiver = "";
    String petReceiver = "";
    boolean petInMyName = false;
    long lootSplitReceived = -1;
    long lootSplitPaid = -1;
    /** A typed Archive split amount. A later unique keeps it. Left off every log line. */
    transient boolean splitAmountLocked;
    ArrayList<RaidTrackerItem> lootList = new ArrayList<>();

    int maidenTime = -1;
    int bloatTime = -1;
    int nyloTime = -1;
    int sotetsegTime = -1;
    int xarpusTime = -1;
    int verzikTime = -1;
    int tobCompTime = -1;

    String mvp= "";
    boolean mvpInOwnName = false;
    String tobPlayer1 = "";
    String tobPlayer2 = "";
    String tobPlayer3 = "";
    String tobPlayer4 = "";
    String tobPlayer5 = "";

    int tobPlayer1DeathCount = 0;
    int tobPlayer2DeathCount = 0;
    int tobPlayer3DeathCount = 0;
    int tobPlayer4DeathCount = 0;
    int tobPlayer5DeathCount = 0;

	int apmekenTime = -1;
	int babaTime = -1;
	int scabarasTime = -1;
	int kephriTime = -1;
	int hetTime = -1;
	int akkhaTime = -1;
	int crondisTime = -1;
	int zebakTime = -1;
	int wardensTime = -1;
	int toaCompTime = -1;

    /** Folder flag for The Fractured Archive. Left off the log line. */
    transient boolean inFracturedArchive = false;

    /** Boss durations in seconds. A skipped fight stays null and is left out of the line. */
    Integer archiveGuardianTime;
    Integer ezekielTime;
    Integer borkTime;
    Integer moiaTime;
    Integer dreadnothTime;
    Integer dagonhaiTime;
    Integer xarthTime;
    Integer ocellusTime;

    /** Read from older Fractured Archive lines. New lines do not write it. */
    Boolean ownMvp;

    /** Team death total on older Fractured Archive lines. New lines do not write it. */
    Integer deaths;

    /**
     * Personal points after the death penalty. The other plugin's total when that message
     * arrives, otherwise this plugin's tally. Null when neither source produced one.
     * Written only together with {@link #ownDeaths}. Zero is a real total.
     */
    Double contributionPoints;

    /**
     * How many times the local player died. Null when the raid was not tracked.
     * Written only together with {@link #contributionPoints}. Zero means none.
     */
    Integer ownDeaths;

    ArrayList<UniqueDrop> uniques;

    //Every RaidTracker has a unique uniqueID but not necessarily a unique killCountID, if there are multiple drops.
    String uniqueID = UUID.randomUUID().toString();
    String killCountID = UUID.randomUUID().toString();
    long date = System.currentTimeMillis();
}
