package com.raidtracker;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.raidtracker.archive.FracturedArchiveSession;
import com.raidtracker.filereadwriter.FileReadWriter;
import com.raidtracker.toapointstracker.module.ComponentManager;
import com.raidtracker.toapointstracker.module.TombsOfAmascutModule;
import com.raidtracker.toapointstracker.pointstracker.PointsTracker;
import com.raidtracker.ui.RaidTrackerPanel;
import com.raidtracker.ui.RaidUniques;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;

@Slf4j
@PluginDescriptor(
	name = "Raid Data Tracker"
)
public class RaidTrackerPlugin extends Plugin
{
	private static final String LEVEL_COMPLETE_MESSAGE = "complete! Duration:";
	private static final String RAID_COMPLETE_MESSAGE_COX = "Congratulations - your raid is complete!";
	private static final String RAID_COMPLETE_MESSAGE_TOA = "Challenge complete: The Wardens.";
	private static final String DUST_RECIPIENTS = "Dust recipients: ";
	private static final String TWISTED_KIT_RECIPIENTS = "Twisted Kit recipients: ";

    private static final Pattern TOB_TOTAL_COMPLETION_PATTERN = Pattern.compile("Theatre of Blood total completion time: (?<duration>(?:(?:(?<hours>\\d+):)?(?:(?<minutes>\\d+):))?(?<seconds>\\d{1,2})(?:\\.(?<milliseconds>\\d+))?)(?:\\. )?.*");
    private static final Pattern TOB_COMPLETION_PATTERN = Pattern.compile(".*Theatre of Blood completion time: (?<duration>(?:(?:(?<hours>\\d+):)?(?:(?<minutes>\\d+):))?(?<seconds>\\d{1,2})(?:\\.(?<milliseconds>\\d+))?)(?:\\. )?.*");

	private static final Pattern TOA_ROOM_COMPLETE_PATTERN = Pattern.compile("Challenge complete: (?<room>[A-Za-z- ]+).*Duration:.*?(?<duration>(?:(?:(?<hours>\\d+):)?(?:(?<minutes>\\d+):))?(?<seconds>\\d{1,2})(?:\\.(?<milliseconds>\\d+))?)(?:\\. )?.*");
	private static final Pattern TOA_COMPLETION_PATTERN = Pattern.compile(".*Tombs of Amascut(?:: (?<mode>.*) Mode)? (?<type>total|challenge) completion time:.*?(?<duration>(?:(?:(?<hours>\\d+):)?(?<minutes>\\d+):)?(?<seconds>\\d{1,2})(?:\\.(?<milliseconds>\\d+))?)(?:\\. )?.*");
	private static final String TOA_EVENT_NAMESPACE = "tombs-of-amascut";
	private static final String TFA_EVENT_NAMESPACE = "the-fractured-archive";
	private static final String TOA_EVENT_NAME_POINTS = "raidCompletedPoints";

    private static final int TOA_TIMER_WIDGET_ID = 46;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private RaidTrackerConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private RaidTracker raidTracker;

	@Inject
	private PointsTracker pointsTracker;

	private ComponentManager componentManager = null;

	@Setter
    @Inject
	private FileReadWriter fw;
	private boolean writerStarted = false;
	private boolean raidStarted = false;

	private static final WorldPoint TEMP_LOCATION = new WorldPoint(3360, 5152, 2);

	@Setter
	private RaidTrackerPanel panel;
	private NavigationButton navButton;
	private FracturedArchiveSession archive;
	/** Ticks left before an empty reward container is accepted as the real chest. */
	private int archiveEmptyChestTicks;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

    private boolean isFirstGameTick = true;

	@Provides
	RaidTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RaidTrackerConfig.class);
	}

	@Override
	public void configure(Binder binder)
	{
		binder.install(new TombsOfAmascutModule());
	}

	@Override
	protected void startUp() {
		panel = new RaidTrackerPanel(itemManager, fw, config, clientThread, client, configManager, pluginManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel-icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Raid Data Tracker")
				.priority(6)
				.icon(icon)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		archive = new FracturedArchiveSession(client, () -> raidTracker, () -> fw);
		archive.setSplitRules(config.defaultFFA(), config.FFACutoff());
		archive.setUniquePrice(this::archiveUniquePrice);
		panel.setLiveArchiveRaid(archive::liveClaim);

		if (componentManager == null) {
			componentManager = injector.getInstance(ComponentManager.class);
		}
		componentManager.onPluginStart();

		if (client.getGameState().equals(GameState.LOGGED_IN) || client.getGameState().equals(GameState.LOADING))
		{
			bindAccountFolder();
			SwingUtilities.invokeLater(() -> panel.loadRTList());
		}
	}

	@Override
	protected void shutDown() {
		try {
			raidTracker.setInRaidChambers(false);
			clientToolbar.removeNavigation(navButton);
			finishArchiveNow(archiveChestVisible());
		} finally {
			if (componentManager != null) {
				componentManager.onPluginStop();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e) {
		if (!e.getGroup().equals(RaidTrackerConfig.CONFIG_GROUP)) {
			return;
		}
		if (archive != null) {
			archive.setSplitRules(config.defaultFFA(), config.FFACutoff());
		}
		panel.loadRTList();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (WorldUtils.playerOnBetaWorld(client)) {
			return;
		}

		// A stuck Tombs raid-level varbit would otherwise reset this raid or mark the tracker as Tombs.
		if (archive != null && archive.isEngaged()) {
			return;
		}

		boolean tempInRaid = client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
		boolean tempInTob = client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS) > 1;
		boolean tempInToa = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL) > 0;

		// if the player's raid state has changed
		if (tempInRaid ^ raidTracker.isInRaidChambers()) {
			// if the player is inside a raid then check the raid
			if (tempInRaid && raidTracker.isLoggedIn()) {
				checkRaidPresence();
			} else if (raidTracker.isRaidComplete() && !raidTracker.isChestOpened()) {
				//player just exited a raid, if the chest is not looted write the raid tracker anyway.
				//might deprecate the writing after widgetloaded in the future, not decided yet.
				if (writerStarted) {
					return;
				}

				fw.writeToFile(raidTracker);

				writerStarted = true;

				SwingUtilities.invokeLater(() -> {
					panel.addDrop(raidTracker);
					reset();
				});
			}
		}

		if (tempInTob ^ raidTracker.isInTheatreOfBlood()) {
			if (tempInTob && raidTracker.isLoggedIn()) {
				checkTobPresence();
			} else if (raidTracker.isRaidComplete()) {
				//not tested

				if (writerStarted) {
					return;
				}

				fw.writeToFile(raidTracker);

				writerStarted = true;

				SwingUtilities.invokeLater(() -> {
					panel.addDrop(raidTracker);
					reset();
				});
			}
			else {
				reset();
			}
		}

		if (tempInToa ^ raidTracker.isInTombsOfAmascut()) {

			// Removed else if for ToA
			// No varbits are changed when a player leaves ToA, so the else-if would never fire
			if (tempInToa && raidTracker.isLoggedIn()) {
				checkToaPresence();
			}
			else {
				reset();
			}
		}

		if (raidTracker.isInTombsOfAmascut()) {
			// Tombs of Amascut orb healths 0=hide 1-27=% of health - 27 is 100% health and 1 is 0% health, 30=dead
			if (event.getVarbitId() >= VarbitID.TOA_CLIENT_P0 && event.getVarbitId() <= VarbitID.TOA_CLIENT_P7 && event.getValue() == 30) { //14353
				int toa_member = event.getVarbitId() - VarbitID.TOA_CLIENT_P0;
				if (toa_member == 0) {
					raidTracker.personalDeathCount++;
				}

				raidTracker.totalTeamDeathCount++;
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (WorldUtils.playerOnBetaWorld(client)) {
			return;
		}

		if (event.getGameState() == GameState.LOGGING_IN)
		{
			// The login name is often blank here. The character folder is set once the player exists.
			bindAccountFolder();
		}

		if (client.getGameState() == GameState.LOGGED_IN) {
			// skip event while the game decides if the player belongs in a raid or not
			if (client.getLocalPlayer() == null
					|| client.getLocalPlayer().getWorldLocation().equals(TEMP_LOCATION))
			{
				//noinspection UnnecessaryReturnStatement
				return;
			}
		}
		else if (client.getGameState() == GameState.LOGIN_SCREEN
				|| client.getGameState() == GameState.CONNECTION_LOST)
		{
			// Keep the attempt. A hop and shutdown write it immediately. Logout may come back to the same chest.
			if (archive != null) {
				archive.noteGameLeft();
			}
			raidTracker.setLoggedIn(false);
            isFirstGameTick = true;
		}
		else if (client.getGameState() == GameState.HOPPING)
		{
            isFirstGameTick = true;
			finishArchiveNow();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (WorldUtils.playerOnBetaWorld(client)) {
			return;
		}

		checkChatMessage(event, raidTracker);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {

        if (client.getLocalPlayer() == null) {
            return;
        }

        if (isFirstGameTick) {
            isFirstGameTick = false;
            raidTracker.setProfileType(String.valueOf(RuneScapeProfileType.getCurrent(client)));
            raidTracker.setAccountHash(client.getAccountHash());
            bindAccountFolder();
            SwingUtilities.invokeLater(() -> panel.loadRTList());
        }

		if (archive != null) {
			try {
				boolean hudOpen = false;
				boolean chestOpen = false;
				boolean widgetsKnown = true;
				try {
					hudOpen = client.getWidget(InterfaceID.TfaHud.RAID_TIMER) != null;
					chestOpen = client.getWidget(InterfaceID.TfaChests.ITEMS) != null;
				} catch (RuntimeException ex) {
					log.warn("Could not read The Fractured Archive interface", ex);
					hudOpen = false;
					chestOpen = false;
					widgetsKnown = false;
				}
				if (widgetsKnown && archiveLocationReady()) {
					boolean archiveWidgets = hudOpen || chestOpen;
					RaidTracker returned = archive.noteReturnTick(archiveWidgets);
					settleArchiveResume(chestOpen);
					if (returned != null) {
						showArchiveClaim(returned);
						checkRaidPresence();
						checkTobPresence();
						checkToaPresence();
					}
				}
				if ((hudOpen || chestOpen) && archive.foreignTracker()) {
					replaceRaidTracker();
				}
				if (hudOpen) {
					archive.setPartySize(archivePartySize());
				}
				if (widgetsKnown) {
					archive.observeHud(hudOpen, chestOpen);
				}
				RaidTracker parkedRaid = archive.takeDepartedRaid();
				boolean releaseLive = archive.takeLiveReset();
				if (parkedRaid != null || releaseLive) {
					archiveEmptyChestTicks = 0;
					if (parkedRaid != null) {
						showArchiveClaim(parkedRaid);
					} else {
						replaceRaidTracker();
					}
					checkRaidPresence();
					checkTobPresence();
					checkToaPresence();
				}
				if (archive.takeRewardClosed()) {
					archiveEmptyChestTicks = 0;
				}
				if (chestOpen && archiveEmptyChestTicks > 0
					&& (!archive.isClaimWindowOpen() || archive.openChestNeedsLoot())) {
					if (!logArchiveReward(false)) {
						archiveEmptyChestTicks--;
						if (archiveEmptyChestTicks == 0) {
							logArchiveReward(true);
						}
					}
				} else if (!chestOpen) {
					archiveEmptyChestTicks = 0;
				}
				RaidTracker dirtyArchive = archive.takeDirty();
				if (dirtyArchive != null && fw.updateRTLog(dirtyArchive, RaidType.TFA)) {
					archive.markPersisted();
				}
				if (archive.takeViewUpdate()) {
					SwingUtilities.invokeLater(() -> {
						if (panel != null) {
							panel.updateView(true);
						}
					});
				}
				RaidTracker savedArchive = archive.retryPending();
				if (savedArchive != null) {
					showArchiveClaim(savedArchive);
				}
			} catch (RuntimeException ex) {
				log.warn("Could not retry The Fractured Archive log", ex);
			}
		}

		int WIDGET_TIMER = WidgetUtil.packComponentId(InterfaceID.TOA_HUD, TOA_TIMER_WIDGET_ID);
		if ((archive == null || !archive.isEngaged())
			&& raidTracker.isInTombsOfAmascut() && client.getWidget(WIDGET_TIMER) != null) {
			if (!Objects.equals(Objects.requireNonNull(client.getWidget(WIDGET_TIMER)).getText(), "00:00")
                    && !Objects.equals(Objects.requireNonNull(client.getWidget(WIDGET_TIMER)).getText(), "0:00.00")
                    && !raidStarted
            ) {
				raidStarted = true;
				raidTracker.setTeamSize(pointsTracker.getTeamSize());
				raidTracker.setRaidLevel(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL));
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event) {
		if (archive == null || event.getHitsplat() == null || !(event.getActor() instanceof NPC)) {
			return;
		}
		Hitsplat hitsplat = event.getHitsplat();
		archive.addHitsplat(hitsplat.isMine(), hitsplat.getHitsplatType(), hitsplat.getAmount());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event) {
		if (archive == null || event.getNpc() == null) {
			return;
		}
		archive.onOcellusNpc(event.getNpc().getId());
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event) {
		Actor actor = event.getActor();
		if (archive == null || !(actor instanceof NPC)) {
			return;
		}
		archive.onOcellusAnimation(((NPC) actor).getId(), actor.getAnimation());
	}

	// Thank Adam, LlemonDuck, and jocopa3 for creating PluginMessage event in core for us
	@Subscribe
	public void onPluginMessage(PluginMessage message) {
		if (message.getNamespace().equals(TFA_EVENT_NAMESPACE) && message.getName().equals(TOA_EVENT_NAME_POINTS)) {
			Object version = message.getData() == null ? null : message.getData().get("version");
			Object personal = message.getData() == null ? null : message.getData().get("personalPoints");
			if (archive != null && Integer.valueOf(1).equals(version) && personal instanceof Integer) {
				archive.noteExternalPoints((Integer) personal);
			}
			return;
		}
		if (message.getNamespace().equals(TOA_EVENT_NAMESPACE)
                && message.getName().equals(TOA_EVENT_NAME_POINTS)
                && (Integer) message.getData().get("version") == 1
        ) {
			log.debug("received PluginMessage from Tombs of Amascut plugin");
			int personalPoints = (Integer) message.getData().get("personalPoints");
			int totalPoints = (Integer) message.getData().get("totalPoints");

			raidTracker.setPersonalPoints(personalPoints);
			raidTracker.setTotalPoints(totalPoints);
			raidTracker.setPercentage(raidTracker.getPersonalPoints() / (raidTracker.getTotalPoints() / 100.0));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (WorldUtils.playerOnBetaWorld(client)) {
			return;
		}

		ItemContainer rewardItemContainer;
		switch (event.getGroupId()) {
			case (InterfaceID.RAIDS_REWARDS):
				if (raidTracker.isChestOpened() || !raidTracker.isRaidComplete()) {
					return;
				}

				raidTracker.setChestOpened(true);

				rewardItemContainer = client.getItemContainer(InventoryID.RAIDS_REWARDS);

				if (rewardItemContainer == null) {
					return;
				}

				if (writerStarted) {
					//unnecessary return check?
					return;
				}

                raidTracker.setLootList(lootListFactory(rewardItemContainer.getItems()));

                writerStarted = true;

                fw.writeToFile(raidTracker);

                SwingUtilities.invokeLater(() -> {
                    panel.addDrop(raidTracker);
                    reset();
                });

                break;

            case (InterfaceID.TOB_CHESTS):
                RaidTracker unclaimedRewardsRT = null;

                if (!raidTracker.isRaidComplete()) {
                    unclaimedRewardsRT = fw.getUnclaimedRewardsRT(raidTracker.getAccountHash(), RaidType.TOB);
                    if (unclaimedRewardsRT != null) {
                        log.info("Found unclaimed rewards RT");
                        raidTracker = unclaimedRewardsRT;
                    }
                }

                if (raidTracker.isChestOpened() || !raidTracker.isRaidComplete()) {
                    return;
                }

                raidTracker.setChestOpened(true);

                rewardItemContainer = client.getItemContainer(InventoryID.TOB_CHESTS);

                if (rewardItemContainer == null) {
                    return;
                }

                raidTracker.setLootList(lootListFactory(rewardItemContainer.getItems()));

                writerStarted = true;

                if (unclaimedRewardsRT != null) {
                    raidTracker.setChestOpened(true);
                    fw.updateRTLog(raidTracker, RaidType.TOB);
                } else {
                    fw.writeToFile(raidTracker);
                }

                SwingUtilities.invokeLater(() -> {
                    panel.addDropToPanel(raidTracker);
                    reset();
                });
                break;

            case (InterfaceID.TOB_INFOBOARD):
                AtomicReference<String> mvp = new AtomicReference<>("");
                AtomicReference<String> player1 = new AtomicReference<>("");
                AtomicReference<String> player2 = new AtomicReference<>("");
                AtomicReference<String> player3 = new AtomicReference<>("");
                AtomicReference<String> player4 = new AtomicReference<>("");
                AtomicReference<String> player5 = new AtomicReference<>("");
                AtomicInteger deathsPlayer1 = new AtomicInteger();
                AtomicInteger deathsPlayer2 = new AtomicInteger();
                AtomicInteger deathsPlayer3 = new AtomicInteger();
                AtomicInteger deathsPlayer4 = new AtomicInteger();
                AtomicInteger deathsPlayer5 = new AtomicInteger();

                clientThread.invokeLater(() -> {
                    mvp.set(getWidgetText(client.getWidget(InterfaceID.TOB_INFOBOARD, 14)));
                    player1.set(getWidgetText(client.getWidget(InterfaceID.TOB_INFOBOARD, 22)));
                    player2.set(getWidgetText(client.getWidget(InterfaceID.TOB_INFOBOARD, 24)));
                    player3.set(getWidgetText(client.getWidget(InterfaceID.TOB_INFOBOARD, 26)));
                    player4.set(getWidgetText(client.getWidget(InterfaceID.TOB_INFOBOARD, 28)));
                    player5.set(getWidgetText(client.getWidget(InterfaceID.TOB_INFOBOARD, 30)));
                    deathsPlayer1.set(getWidgetNumber(client.getWidget(InterfaceID.TOB_INFOBOARD, 23)));
                    deathsPlayer2.set(getWidgetNumber(client.getWidget(InterfaceID.TOB_INFOBOARD, 25)));
                    deathsPlayer3.set(getWidgetNumber(client.getWidget(InterfaceID.TOB_INFOBOARD, 27)));
                    deathsPlayer4.set(getWidgetNumber(client.getWidget(InterfaceID.TOB_INFOBOARD, 29)));
                    deathsPlayer5.set(getWidgetNumber(client.getWidget(InterfaceID.TOB_INFOBOARD, 31)));

                    raidTracker.setMvp(mvp.get());
                    raidTracker.setTobPlayer1(player1.get());
                    raidTracker.setTobPlayer2(player2.get());
                    raidTracker.setTobPlayer3(player3.get());
                    raidTracker.setTobPlayer4(player4.get());
                    raidTracker.setTobPlayer5(player5.get());

                    raidTracker.setTobPlayer1DeathCount(deathsPlayer1.get());
                    raidTracker.setTobPlayer2DeathCount(deathsPlayer2.get());
                    raidTracker.setTobPlayer3DeathCount(deathsPlayer3.get());
                    raidTracker.setTobPlayer4DeathCount(deathsPlayer4.get());
                    raidTracker.setTobPlayer5DeathCount(deathsPlayer5.get());

                    if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
                        raidTracker.setMvpInOwnName(mvp.get().equalsIgnoreCase(client.getLocalPlayer().getName()));
                    }
                });
                break;

            case (InterfaceID.TOA_CHESTS):
                if (raidTracker.isChestOpened() || !raidTracker.isRaidComplete()) {
                    return;
                }

                raidTracker.setChestOpened(true);

                rewardItemContainer = client.getItemContainer(InventoryID.TOA_CHESTS);

                if (rewardItemContainer == null) {
                    return;
                }

                raidTracker.setLootList(lootListFactory(rewardItemContainer.getItems()));

                fw.writeToFile(raidTracker);

                writerStarted = true;

                SwingUtilities.invokeLater(() -> {
                    // Doesn't use reset() due to ToA using a log update function on tomb exit
                    panel.addDrop(raidTracker);
                    writerStarted = false;
                });
                break;
            case (InterfaceID.TOA_LOBBY):
                SwingUtilities.invokeLater(() -> panel.showWarningView());
                break;

            case (InterfaceID.TFA_CHESTS):
                if (!logArchiveReward(false)) {
                    noteEmptyArchiveChest();
                }
                break;
        }
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		if (event.getContainerId() != InventoryID.TFA_CHESTS || archive == null) {
			return;
		}
		// A full chest line already has its items. A line resumed before the container filled still needs them.
		if (archive.isClaimWindowOpen() && !archive.openChestNeedsLoot()) {
			return;
		}
		boolean chestOpen = false;
		try {
			chestOpen = client.getWidget(InterfaceID.TfaChests.ITEMS) != null;
		} catch (RuntimeException ex) {
			log.warn("Could not read The Fractured Archive chest", ex);
			return;
		}
		if (!chestOpen) {
			return;
		}
		if (!logArchiveReward(false)) {
			noteEmptyArchiveChest();
		}
	}

	/**
	 * Writes the open reward chest. An empty container on the first event is not the chest yet:
	 * the item container often fills on a later tick. {@code allowEmpty} writes that later read.
	 * The log is written even when the side panel is closed.
	 */
	private boolean logArchiveReward(boolean allowEmpty) {
		if (archive == null || WorldUtils.playerOnUntrackedWorld(client)) {
			return false;
		}
		// The chest can load before the first settle tick. Resume first so this container updates that row.
		settleArchiveResume(true);
		if (archive.foreignTracker()) {
			replaceRaidTracker();
		}
		try {
			ItemContainer archiveChest = client.getItemContainer(InventoryID.TFA_CHESTS);
			if (archiveChest == null || archiveChest.getItems() == null) {
				return false;
			}
			ArrayList<RaidTrackerItem> loot = lootListFactory(archiveChest.getItems());
			if (loot.isEmpty() && !allowEmpty) {
				return false;
			}
			String archivePlayer = "";
			if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
				archivePlayer = client.getLocalPlayer().getName();
			}
			if (!archive.claimRewardWindow(loot, archivePlayer)) {
				return false;
			}
			archiveEmptyChestTicks = 0;
			showArchiveClaim(archive.claimedRaid());
			return true;
		} catch (RuntimeException ex) {
			log.warn("Could not log The Fractured Archive reward window", ex);
			return false;
		}
	}

	private void noteEmptyArchiveChest() {
		if (archive == null || archiveEmptyChestTicks > 0 || WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}
		if (archive.isClaimWindowOpen() && !archive.openChestNeedsLoot()) {
			return;
		}
		try {
			ItemContainer chest = client.getItemContainer(InventoryID.TFA_CHESTS);
			if (chest != null && chest.getItems() != null) {
				archiveEmptyChestTicks = 2;
			}
		} catch (RuntimeException ex) {
			log.warn("Could not read The Fractured Archive chest", ex);
		}
	}

	private int archivePartySize() {
		int[] orbs = {
			InterfaceID.TfaHud._0_CONTAINER,
			InterfaceID.TfaHud._1_CONTAINER,
			InterfaceID.TfaHud._2_CONTAINER,
			InterfaceID.TfaHud._3_CONTAINER,
			InterfaceID.TfaHud._4_CONTAINER,
			InterfaceID.TfaHud._5_CONTAINER,
			InterfaceID.TfaHud._6_CONTAINER,
			InterfaceID.TfaHud._7_CONTAINER
		};
		int count = 0;
		for (int orb : orbs) {
			Widget widget;
			try {
				widget = client.getWidget(orb);
				if (widget != null && !widget.isHidden()) {
					count++;
				}
			} catch (RuntimeException ex) {
				log.warn("Could not read a Fractured Archive party orb", ex);
			}
		}
		return count;
	}

	private long archiveUniquePrice(RaidUniques unique) {
		try {
			return getItemPrice(unique.getItemID());
		} catch (RuntimeException ex) {
			log.warn("Could not price a Fractured Archive unique", ex);
			return -1L;
		}
	}

	private String getWidgetText(Widget widget) {
		if (widget == null) {
			return "";
		}
		else if (widget.getText().equals("-")) {
			return "";
		}
		return widget.getText();
	}

	private int getWidgetNumber(Widget widget) {
		if (widget == null) {
			return 0;
		}
		else if (widget.getText().equals("-")) {
			return 0;
		}
		return Integer.parseInt(widget.getText());
	}

	public void checkChatMessage(ChatMessage event, RaidTracker raidTracker)
	{
		raidTracker.setLoggedIn(true);

		String playerName = "";

		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
			playerName = Text.toJagexName(client.getLocalPlayer().getName());
		}

		if (archive != null && event.getType() == ChatMessageType.GAMEMESSAGE && !WorldUtils.playerOnUntrackedWorld(client)) {
			try {
				String archiveMessage = JavaStrings.unescapeJavaString(Text.removeTags(event.getMessage())).replace("\u00A0", " ");
				boolean archiveMessageRecognized = archive.isArchiveMessage(archiveMessage);
				if (archiveMessageRecognized && archive.foreignTracker()) {
					replaceRaidTracker();
				}
				boolean raidHudOpen = false;
				try {
					raidHudOpen = client.getWidget(InterfaceID.TfaHud.RAID_TIMER) != null;
				} catch (RuntimeException ex) {
					log.warn("Could not read The Fractured Archive interface", ex);
					raidHudOpen = false;
				}
				archive.onGameMessage(archiveMessage, raidHudOpen);
				// A successful leave sets both. The reset has to be taken even when the raid is present,
				// or the next game message replaces the live tracker again.
				RaidTracker departedArchive = archive.takeDepartedRaid();
				boolean resetLive = archive.takeLiveReset();
				if (departedArchive != null || resetLive) {
					showArchiveClaim(departedArchive);
				}
				if (archive.takeViewUpdate() && panel != null) {
					SwingUtilities.invokeLater(() -> {
						if (panel != null) {
							panel.updateView(true);
						}
					});
				}
				if (archive.takeRewardClosed()) {
					archiveEmptyChestTicks = 0;
				}
				if (archiveMessageRecognized || archive.isEngaged()) {
					return;
				}
			} catch (RuntimeException ex) {
				log.warn("Could not read The Fractured Archive chat", ex);
			}
		}

		if ((raidTracker.isInRaidChambers() || raidTracker.isInTheatreOfBlood() || raidTracker.isInTombsOfAmascut()) &&
			(event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION || event.getType() == ChatMessageType.GAMEMESSAGE)) {
			//unescape java to avoid unicode
			String message = JavaStrings.unescapeJavaString(Text.removeTags(event.getMessage())).replaceAll("\u00A0", " ");
            Matcher m;

			// Fixes issue with inconsistent resets due to
			// the ToA Raid Level varbit (VarbitID.TOA_CLIENT_RAID_LEVEL) not resetting when you leave
			if (message.contains("You enter the Tombs of Amascut")) {
				reset();
			}

            // Filter out message with "-" so we don't accidentally consider it specialLoot
            if (message.contains("- patient record")) return;

			if (message.contains(LEVEL_COMPLETE_MESSAGE)) {
				String timeString = message.split("complete! Duration: ")[1];

				if (message.startsWith("Upper")) {
					raidTracker.setUpperTime(stringTimeToSeconds(timeString.split(" ")[timeString.split(" ").length - 1]));
				}
				if (message.startsWith("Middle")) {
					raidTracker.setMiddleTime(stringTimeToSeconds(timeString.split(" ")[timeString.split(" ").length - 1]));
				}
				if (message.startsWith("Lower")) {
					raidTracker.setLowerTime(stringTimeToSeconds(timeString.split(" ")[timeString.split(" ").length - 1]));
				}

				if (message.toLowerCase().contains("shamans")) {
					raidTracker.setShamansTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("vasa")) {
					raidTracker.setVasaTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("vanguards")) {
					raidTracker.setVanguardsTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("mystics")) {
					raidTracker.setMysticsTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("tekton")) {
					raidTracker.setTektonTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("muttadiles")) {
					raidTracker.setMuttadilesTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("vespula")) {
					raidTracker.setVespulaTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("ice demon")) {
					raidTracker.setIceDemonTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("thieving")) {
					raidTracker.setThievingTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("tightrope")) {
					raidTracker.setTightropeTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

				if (message.toLowerCase().contains("crabs")) {
					raidTracker.setCrabsTime(stringTimeToSeconds(timeString.split(" ")[0]));
				}

			}

			if (message.startsWith(RAID_COMPLETE_MESSAGE_COX) || message.startsWith(RAID_COMPLETE_MESSAGE_TOA)) {
				if (raidTracker.isInRaidChambers()) {
					raidTracker.setTotalPoints(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSCORE));
					raidTracker.setPersonalPoints(client.getVarpValue(VarPlayerID.RAIDS_PLAYERSCORE));
					raidTracker.setTeamSize(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE));
				} else if (raidTracker.isInTombsOfAmascut()) {
					raidTracker.setPersonalPoints(pointsTracker.getPersonalTotalPoints());
				}


				raidTracker.setPercentage(raidTracker.getPersonalPoints() / (raidTracker.getTotalPoints() / 100.0));

				// Points being 0 (for any reason) causes the % to be NaN and cause a log writing error
				if (Double.isNaN(raidTracker.getPercentage())) {
					raidTracker.setTotalPoints(-1);
					raidTracker.setPersonalPoints(-1);
					raidTracker.setPercentage(-1);
				}

				// Without TOA party point tracking, the points percentage can't be calculated properly
				if (raidTracker.getPercentage() < 0 && raidTracker.getPercentage() != -1) {
					raidTracker.setPercentage(-1);
				}

				raidTracker.setRaidComplete(true);

				raidTracker.setDate(System.currentTimeMillis());
			}

			//the message after every completed tob wave.
			if (message.toLowerCase().contains("wave '")) {
				String wave = message.toLowerCase().split("'")[1];

				switch (wave) {
					case("the maiden of sugadinti"):
						raidTracker.setMaidenTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("the pestilent bloat"):
						raidTracker.setBloatTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("the nylocas"):
						raidTracker.setNyloTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("sotetseg"):
						raidTracker.setSotetsegTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("xarpus"):
						raidTracker.setXarpusTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("the final challenge"):
						raidTracker.setVerzikTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split("theatre")[0]));
						break;
				}
			}

			if (message.contains("Challenge complete") || message.contains("total completion")) {
				if ((m = TOA_ROOM_COMPLETE_PATTERN.matcher(message)).matches()) {
                    String room = m.group("room").toLowerCase();
                    int duration = stringTimeToSeconds(m.group("duration"));

                    if (room.isEmpty()) {
                        log.warn("Failed to find room for completion string {}", event.getMessage());
                        return;
                    }

					switch (room) {
						case "path of crondis":
							raidTracker.setCrondisTime(duration);
							break;
						case "zebak":
							raidTracker.setZebakTime(duration);
							break;
						case "path of apmeken":
							raidTracker.setApmekenTime(duration);
							break;
						case "ba-ba":
							raidTracker.setBabaTime(duration);
							break;
						case "path of scabaras":
							raidTracker.setScabarasTime(duration);
							break;
						case "kephri":
							raidTracker.setKephriTime(duration);
							break;
						case "path of het":
							raidTracker.setHetTime(duration);
							break;
						case "akkha":
							raidTracker.setAkkhaTime(duration);
							break;
						case "the wardens":
							raidTracker.setWardensTime(duration);
							break;
                        default:
                            log.warn("Unexpected room found: {}", event.getMessage());
					}
				}

				if ((m = TOA_COMPLETION_PATTERN.matcher(message)).matches()) {
					int duration = stringTimeToSeconds(m.group("duration"));

                    raidTracker.setRaidLevel(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL));

					if (Objects.equals(m.group("type"), "challenge")) {
                        raidTracker.setToaCompTime(duration);
                    }
                    if (Objects.equals(m.group("type"), "total")) {
						raidTracker.setRaidTime(duration);
					}
				}
			}

            if ((m = TOB_COMPLETION_PATTERN.matcher(message)).matches()) {
                raidTracker.setTobCompTime(stringTimeToSeconds(m.group("duration")));
            }

            if ((m = TOB_TOTAL_COMPLETION_PATTERN.matcher(message)).matches()) {
                raidTracker.setRaidTime(stringTimeToSeconds(m.group("duration")));
            }

            if (!raidTracker.isInFracturedArchive() && raidTracker.isRaidComplete() && message.contains("Team size:")) {
				raidTracker.setRaidTime(stringTimeToSeconds(message.split("Duration: ")[1].split(" ")[0]));
			}

			//works for tob
			if (!raidTracker.isInFracturedArchive() && message.contains("count is:")) {
				raidTracker.setChallengeMode(message.contains("Chambers of Xeric Challenge Mode"));
				raidTracker.setCompletionCount(parseInt(message.split("count is:")[1].trim().replace(".", "")));
				if (raidTracker.isInTheatreOfBlood()) {
					int teamSize = 0;

					for (int i = 6442; i  < 6447; i++) {
						if (client.getVarbitValue(i) != 0) {
							teamSize++;
						}
					}
					raidTracker.setTeamSize(teamSize);
					raidTracker.setRaidComplete(true);
                    raidTracker.setDate(System.currentTimeMillis());
				}
			}

			// A Fractured Archive unique stays on the raid line. Clan chat about a drop is not a second line.
			if (!raidTracker.isInFracturedArchive()) {
			// Only special loot should contain the " - " (except for the raid complete message in Cox)
			if (raidTracker.isRaidComplete() && message.contains(" - ") && !message.startsWith(RAID_COMPLETE_MESSAGE_COX)) {
				//in case of multiple purples, a new purple is stored on a new line in the file, so a new raidtracker object will be used and written to the file
				if (!raidTracker.getSpecialLootReceiver().isEmpty()) {
					RaidTracker altRT = copyData();

					altRT.setSpecialLootReceiver(message.split(" - ")[0]);
					altRT.setSpecialLoot(message.split(" - ")[1]);

					altRT.setSpecialLootInOwnName(altRT.getSpecialLootReceiver().toLowerCase().trim().equals(playerName.toLowerCase().trim()));


					altRT.setSpecialLootValue(getItemPrice(itemManager.search(raidTracker.getSpecialLoot()).get(0)));

					setSplits(altRT);

					fw.writeToFile(altRT);

					SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
				} else {
					raidTracker.setSpecialLootReceiver(message.split(" - ")[0]);
					raidTracker.setSpecialLoot(message.split(" - ")[1]);

					raidTracker.setSpecialLootValue(getItemPrice(itemManager.search(raidTracker.getSpecialLoot()).get(0)));

					raidTracker.setSpecialLootInOwnName(raidTracker.getSpecialLootReceiver().toLowerCase().trim().equals(playerName.toLowerCase().trim()));

					setSplits(raidTracker);
				}
			}

			//for tob & toa it works a bit different, not possible to get duplicates.
			//toa tested, tob untested in game
			if (raidTracker.isRaidComplete() && message.toLowerCase().contains("found something special") && !message.toLowerCase().contains("lil' zik") && !message.toLowerCase().contains("tumeken's guardian")) {
				raidTracker.setSpecialLootReceiver(message.split(" found something special: ")[0]);
				raidTracker.setSpecialLoot(message.split(" found something special: ")[1]);

				raidTracker.setSpecialLootValue(getItemPrice(itemManager.search(raidTracker.getSpecialLoot()).get(0)));

				raidTracker.setSpecialLootInOwnName(raidTracker.getSpecialLootReceiver().toLowerCase().trim().equals(playerName.toLowerCase().trim()));

				setSplits(raidTracker);
			}

			if (raidTracker.isRaidComplete() && message.startsWith(TWISTED_KIT_RECIPIENTS)) {
				String[] recipients = message.split(TWISTED_KIT_RECIPIENTS)[1].split(",");

				for (String recipient : recipients) {
					if (raidTracker.getKitReceiver().isEmpty()) {
						raidTracker.setKitReceiver(recipient.trim());
					}
					else {
						RaidTracker altRT = copyData();
						altRT.setKitReceiver(recipient.trim());

						fw.writeToFile(altRT);

						SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
					}
				}
			}

			if (raidTracker.isRaidComplete() && message.startsWith(DUST_RECIPIENTS)) {
				String[] recipients = message.split(DUST_RECIPIENTS)[1].split(",");

				for (String recipient : recipients) {
					if (raidTracker.getDustReceiver().isEmpty()) {
						raidTracker.setDustReceiver(recipient.trim());
					}
					else {
						RaidTracker altRT = copyData();
						altRT.setDustReceiver(recipient.trim());

						fw.writeToFile(altRT);

						SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
					}
				}
			}

            String msg = message.toLowerCase();
			if (raidTracker.isRaidComplete() && (msg.contains("olmlet")
                || msg.contains("lil' zik")) // are these parentheses pointless?
                || msg.contains("tumeken's guardian")
                || msg.contains("would have been followed")) {
				boolean inOwnName = false;
				boolean duplicate = msg.contains("would have been followed");

				if (playerName.equals(message.split(" ")[0]) || duplicate)	{
					inOwnName = true;
				}

				if (!raidTracker.getPetReceiver().isEmpty()) {
					RaidTracker altRT = copyData();

					if (duplicate) {
						altRT.setPetReceiver(playerName);
					}
					else {
						altRT.setPetReceiver(message.split(" ")[0]);
					}

					altRT.setPetInMyName(inOwnName);

					fw.writeToFile(altRT);

					SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
				}
				else {
					if (duplicate) {
						raidTracker.setPetReceiver(playerName);
					}
					else {
						raidTracker.setPetReceiver(message.split(" ")[0]);
					}
					raidTracker.setPetInMyName(inOwnName);
				}
			}
			}
		}
	}

	public void setSplits(RaidTracker raidTracker)
	{

		long lootSplit = raidTracker.getSpecialLootValue() / raidTracker.getTeamSize();

		long cutoff = config.FFACutoff();

		//
		if (!raidTracker.getSpecialLoot().isEmpty()) {
			if (config.defaultFFA() || lootSplit < cutoff) {
				raidTracker.setFreeForAll(true);
				if (raidTracker.isSpecialLootInOwnName()) {
					raidTracker.setLootSplitReceived(raidTracker.getSpecialLootValue());
				}
			} else if (raidTracker.isSpecialLootInOwnName()) {
				raidTracker.setLootSplitPaid(raidTracker.getSpecialLootValue() - lootSplit);
				raidTracker.setLootSplitReceived(lootSplit);
			} else {
				raidTracker.setLootSplitReceived(lootSplit);
			}
		}
	}

	public ArrayList<RaidTrackerItem> lootListFactory(Item[] items)
	{
		ArrayList<RaidTrackerItem> lootList = new ArrayList<>();
		if (items == null) {
			return lootList;
		}
		Arrays.stream(items)
				.filter(item -> item != null && item.getId() > -1)
				.forEach(item -> {
					ItemComposition comp = itemManager.getItemComposition(item.getId());
					if (comp == null || comp.getName() == null) {
						return;
					}
					RaidTrackerItem tracked = new RaidTrackerItem();
					tracked.setName(comp.getName());
					tracked.setId(comp.getId());
					tracked.setQuantity(item.getQuantity());
					tracked.setPrice(getItemPrice(comp.getId()) * item.getQuantity());
					lootList.add(tracked);
				});
		return lootList;
	}

	private void checkRaidPresence()
	{
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		raidTracker.setInRaidChambers(client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1);
	}

	private void checkTobPresence() {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}
		//1 = in party outside, 2 = spectating, 3 = dead spectating
		raidTracker.setInTheatreOfBlood(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS) > 1);
	}

	private void checkToaPresence() {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		raidTracker.setInTombsOfAmascut(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL) > 0);
	}

	private int stringTimeToSeconds(String s)
	{
		String[] split = s.split(":");
		return split.length == 3 ? parseInt(split[0]) * 3600 + parseInt(split[1]) * 60 + Math.round(parseFloat(split[2])) : parseInt(split[0]) * 60 + Math.round(parseFloat(split[1]));
	}

	public RaidTracker copyData() {
		RaidTracker RT = new RaidTracker();
        RT.setAccountHash(raidTracker.getAccountHash());
        RT.setProfileType(raidTracker.getProfileType());
		RT.setDate(raidTracker.getDate());
		RT.setTeamSize(raidTracker.getTeamSize());
		RT.setChallengeMode(raidTracker.isChallengeMode());
		RT.setInTheatreOfBlood(raidTracker.isInTheatreOfBlood());
		RT.setCompletionCount(raidTracker.getCompletionCount());
		RT.setKillCountID(raidTracker.getKillCountID());

		return RT;
	}

	/** Use the character name, not the login name. A blank name must not select the shared plugin folder. */
	private void bindAccountFolder() {
		if (fw == null || client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) {
			return;
		}
		String name = Text.toJagexName(client.getLocalPlayer().getName());
		if (fw.updateUsername(name) && panel != null) {
			SwingUtilities.invokeLater(() -> {
				if (panel != null) {
					panel.loadRTList();
				}
			});
		}
	}

	/** The reward chest widget. Shutdown uses this so a restart can fill the raid that is still open. */
	private boolean archiveChestVisible() {
		if (client == null) {
			return false;
		}
		try {
			return client.getWidget(InterfaceID.TfaChests.ITEMS) != null;
		} catch (RuntimeException ex) {
			log.warn("Could not read The Fractured Archive chest", ex);
			return false;
		}
	}

	/** Logged in, and the game has finished placing the player. The temp tile is not a real location yet. */
	private boolean archiveLocationReady() {
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) {
			return false;
		}
		WorldPoint location = client.getLocalPlayer().getWorldLocation();
		return location != null && !location.equals(TEMP_LOCATION);
	}

	private RaidTracker latestArchiveRaid() {
		if (fw == null) {
			return null;
		}
		ArrayList<RaidTracker> raids = fw.readFromFile(RaidType.TFA);
		if (raids.isEmpty()) {
			return null;
		}
		return raids.get(raids.size() - 1);
	}

	/**
	 * A line still marked open can become the claim again. An open chest does not revive a closed line,
	 * so the next raid's loot cannot be written onto the previous raid.
	 */
	private void settleArchiveResume(boolean archiveWidgets) {
		if (archive == null || !archive.needsResumeCheck()) {
			return;
		}
		RaidTracker latest = latestArchiveRaid();
		if (archive.noteResumeTick(archiveWidgets, latest, System.currentTimeMillis())) {
			SwingUtilities.invokeLater(() -> {
				if (panel != null) {
					panel.loadRTList();
				}
			});
		}
	}

	/** Chambers, Theatre, or Tombs ended. A parked Archive line stays open for a later unique or chest. */
	private void reset()
	{
		if (archive != null && archive.isParked()) {
			archiveEmptyChestTicks = 0;
			replaceRaidTracker();
			return;
		}
		finishArchiveNow();
	}

	/** Hop, or shutdown away from the chest. A completion is written and the claim closes. */
	private void finishArchiveNow()
	{
		finishArchiveNow(false);
	}

	/**
	 * Shutdown with the reward chest still open keeps that line able to take its loot after a restart.
	 * A closed line is left closed, so the next raid's chest cannot be written onto it.
	 */
	private void finishArchiveNow(boolean chestStillOpen)
	{
		archiveEmptyChestTicks = 0;
		if (archive != null && archive.hasUnstoredCompletion() && archive.foreignTracker()) {
			replaceRaidTracker();
		}
		RaidTracker departedArchive = null;
		if (archive != null) {
			archive.finished(chestStillOpen);
			archive.takeRewardClosed();
			departedArchive = archive.takeDepartedRaid();
			archive.takeLiveReset();
		}
		if (departedArchive != null) {
			showArchiveClaim(departedArchive);
			return;
		}
		replaceRaidTracker();
	}

	/** The chest line stays open so a teammate unique can be written onto that same raid. */
	private void resetAfterArchiveClaim()
	{
		replaceRaidTracker();
	}

	/** Replace the live tracker before the next client event, then show the saved raid. */
	private void showArchiveClaim(RaidTracker savedArchive)
	{
		resetAfterArchiveClaim();
		if (savedArchive == null || panel == null) {
			return;
		}
		SwingUtilities.invokeLater(() -> {
			if (panel != null) {
				panel.addDrop(savedArchive);
			}
		});
	}

	private void replaceRaidTracker()
	{
		raidTracker = new RaidTracker();
        raidTracker.setAccountHash(client.getAccountHash());
        raidTracker.setProfileType(String.valueOf(RuneScapeProfileType.getCurrent(client)));
		writerStarted = false;
		raidStarted = false;
	}

	public long getItemPrice(ItemPrice itemPrice) {
		return itemPrice.getWikiPrice() > 0 ? itemPrice.getWikiPrice() : itemPrice.getPrice();
	}
	public long getItemPrice(int itemID) {
		return itemManager.getItemPrice(itemID) > 0 ? itemManager.getItemPrice(itemID) : itemManager.getItemComposition(itemID).getPrice();
	}

    public void updateCurrentRT(RaidType raidType) {
		if (writerStarted) {
			return;
		}

		writerStarted = true;

		fw.updateRTLog(raidTracker, raidType);

		SwingUtilities.invokeLater(() -> {
			panel.updateView(true);
            reset();
		});
	}

}
