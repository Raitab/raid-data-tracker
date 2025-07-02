package com.raidtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(RaidTrackerConfig.CONFIG_GROUP)
public interface RaidTrackerConfig extends Config
{

	String CONFIG_GROUP = "raidtracker";
	@ConfigItem(
		keyName = "defaultFFA",
		name = "default FFA",
		description = "Sets the default split to free for all, rather than split"
	)
	default boolean defaultFFA()
	{
		return false;
	}

	@ConfigItem(
			keyName = "FFACutoff",
			name = "FFA cut off",
			description = "The value of which, when the split reaches under that value, is considered free for all"
	)

	default int FFACutoff() {
		return 1000000;
	}

	@ConfigItem(
			keyName = "lastXKills",
			name = "Last X Kills",
			description = "When the 'Last X Kills' option is selected, this value is used as X"
	)

	default int lastXKills() {return 50;}

	@ConfigItem(
			keyName = "showTitle",
			name = "Show Title",
			description = "Disable this checkmark to hide the title in the ui"
	)
	default boolean showTitle()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showKillsLogged",
			name = "Show Kills Logged",
			description = "Disable this checkmark to hide the Kills Logged panel in the ui"
	)
	default boolean showKillsLogged()
	{
		return true;
	}


	@ConfigItem(
			keyName = "showFilters",
			name = "Show Filters",
			description = "Disable this checkmark to hide the Filter Panel in the ui"
	)
	default boolean showFilters()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showUniquesTable",
			name = "Show Uniques Table",
			description = "Disable this checkmark to hide the Uniques Table in the ui"
	)
	default boolean showUniquesTable()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showPoints",
			name = "Show Points",
			description = "Disable this checkmark to hide the Points Panel in the ui"
	)
	default boolean showPoints()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showTimeSplits",
			name = "Show Time Splits",
			description = "Disable this checkmark to hide the Time Splits Panel in the ui"
	)
	default boolean showTimeSplits()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showSplitGPEarned",
			name = "Show Split GP Earned",
			description = "Disable this checkmark to hide the Split GP Earned Panel in the ui"
	)
	default boolean showSplitGPEarned()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showMVPs",
			name = "Show MVPs",
			description = "Disable this checkmark to hide the MVPs Panel in the ui"
	)
	default boolean showMVPs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showRegularDrops",
			name = "Show Regular Drops",
			description = "Disable this checkmark to hide the Regular Drops Panel in the ui"
	)
	default boolean showRegularDrops()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showSplitChanger",
			name = "Show Split Changer",
			description = "Disable this checkmark to hide the Split Changer in the ui"
	)
	default boolean showSplitChanger()
	{
		return true;
	}

	@ConfigItem(
		keyName = "toaFilterCustomLow",
		name = "toaFilterCustomLow",
		description = "toaFilterCustomLow",
		hidden = true
	)
	default int toaFilterCustomLow() { return 0; }

	@ConfigItem(
		keyName = "toaFilterCustomHigh",
		name = "toaFilterCustomHigh",
		description = "toaFilterCustomHigh",
		hidden = true
	)
	default int toaFilterCustomHigh() { return 600; }

	@ConfigSection(
			name = "Discord Webhook",
			description = "Settings for posting raid completions to a Discord webhook",
			position = 99
	)
	String discordSection = "discord";

	@ConfigItem(
			keyName = "webhookEnabled",
			name = "Send to Discord",
			description = "Automatically send raid data to a Discord webhook upon completion.",
			position = 100,
			section = discordSection
	)
	default boolean webhookEnabled() {
		return false;
	}

	@ConfigItem(
			keyName = "webhookUrl",
			name = "Webhook URL",
			description = "The Discord webhook URL to send the raid data to.",
			position = 101,
			section = discordSection
	)
	default String webhookUrl() {
		return "";
	}

}
