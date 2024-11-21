package com.raidtracker.ui;


import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerConfig;
import com.raidtracker.RaidTrackerItem;
import com.raidtracker.RaidType;
import com.raidtracker.WorldUtils;
import com.raidtracker.filereadwriter.FileReadWriter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import static java.util.Comparator.comparing;

@Slf4j
public class RaidTrackerPanel extends PluginPanel {

	@Setter
    private ItemManager itemManager;
    private final FileReadWriter fw;
    private final RaidTrackerConfig config;
    private final ClientThread clientThread;
    private final Client client;
	private final ConfigManager configManager;
	private final PluginManager pluginManager;

    @Setter
    private ArrayList<RaidTracker> coxRTList;
    private ArrayList<RaidTracker> tobRTList;
	private ArrayList<RaidTracker> toaRTList;

    private final HashMap<String, RaidTracker> coxUUIDMap = new LinkedHashMap<>();
    private final HashMap<String, RaidTracker> tobUUIDMap = new LinkedHashMap<>();
	private final HashMap<String, RaidTracker> toaUUIDMap = new LinkedHashMap<>();

    @Setter
    private boolean loaded = false;
    private final JPanel panel = new JPanel();

    private JButton update;

    @Setter
    private String dateFilter = "All Time";
    @Setter
    private String cmFilter = "CM & Normal";
    @Setter
    private String mvpFilter = "Both";
	@Setter
	private String raidLevelFilter = "All Levels";
    @Setter
    private String teamSizeFilter = "All sizes";

	private final HashMap<String, int[]> toaFilterMap = new HashMap<>(4);

	@Setter
	private int raidLevelFilterLow = 0;
	@Setter
	private int raidLevelFilterHigh = 600;

    private static final int toaRaidLevelMin = 0;
    private static final int toaRaidLevelMax = 600;

	@Setter
	private boolean raidLevelFilterIsAPI = false;

	@Getter
	@Setter
	private RaidType selectedRaidTab = RaidType.COX;

    private JPanel regularDrops = new JPanel();

    @Getter
    EnumSet<RaidUniques> tobUniques = EnumSet.of(
            RaidUniques.AVERNIC,
            RaidUniques.RAPIER,
            RaidUniques.SANGSTAFF,
            RaidUniques.JUSTI_FACEGUARD,
            RaidUniques.JUSTI_CHESTGUARD,
            RaidUniques.JUSTI_LEGGUARDS,
            RaidUniques.SCYTHE,
            RaidUniques.LILZIK
    );

    @Getter
    EnumSet<RaidUniques> coxUniques = EnumSet.of(
            RaidUniques.DEX,
            RaidUniques.ARCANE,
            RaidUniques.TWISTED_BUCKLER,
            RaidUniques.DHCB,
            RaidUniques.DINNY_B,
            RaidUniques.ANCESTRAL_HAT,
            RaidUniques.ANCESTRAL_TOP,
            RaidUniques.ANCESTRAL_BOTTOM,
            RaidUniques.DRAGON_CLAWS,
            RaidUniques.ELDER_MAUL,
            RaidUniques.KODAI,
            RaidUniques.TWISTED_BOW,
            RaidUniques.DUST,
            RaidUniques.TWISTED_KIT,
            RaidUniques.OLMLET
    );

	@Getter
	EnumSet<RaidUniques> toaUniques = EnumSet.of(
		RaidUniques.OSMUMTENS_FANG,
		RaidUniques.LIGHTBEARER,
		RaidUniques.ELIDINIS_WARD,
		RaidUniques.MASORI_MASK,
		RaidUniques.MASORI_BODY,
		RaidUniques.MASORI_CHAPS,
		RaidUniques.TUMEKENS_SHADOW,
		RaidUniques.TUMEKENS_GUARDIAN
	);

	private Component titleComponent;
	private Component filterComponent;

    public RaidTrackerPanel(
        final ItemManager itemManager,
        FileReadWriter fw,
        RaidTrackerConfig config,
        ClientThread clientThread,
        Client client,
        ConfigManager configManager,
        PluginManager pluginManager
    ) {
        this.itemManager = itemManager;
        this.fw = fw;
        this.config = config;
        this.clientThread = clientThread;
        this.client = client;
		this.configManager = configManager;
		this.pluginManager = pluginManager;

		toaFilterMap.put("All Levels", new int[]{0, 600});
		toaFilterMap.put("Entry Mode", new int[]{0, 149});
		toaFilterMap.put("Normal Mode", new int[]{150, 299});
		toaFilterMap.put("Expert Mode", new int[]{300, 600});
		toaFilterMap.put("Custom", new int[]{config.toaFilterCustomLow(), config.toaFilterCustomHigh()});

        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        add(panel, BorderLayout.NORTH);

        updateView();
    }

    /*
    View to show when the tracker is disabled.
    This could occur due to being on a temporary world where we don't want to save the data.
     */
    private void showDisabledView() {
        panel.removeAll();

        JPanel title = new JPanel();
        JPanel titleLabelWrapper = new JPanel();
        JLabel titleLabel = new JLabel("Tracker Disabled on Beta Worlds");
        titleLabel.setForeground(Color.WHITE);

        titleLabelWrapper.add(titleLabel, BorderLayout.CENTER);

        title.add(titleLabelWrapper);

        panel.add(title);
        panel.revalidate();
        panel.repaint();
    }

	public void showWarningView() {
		panel.removeAll();

		JPanel title = new JPanel();
		title.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.fill = SwingConstants.HORIZONTAL;


		JPanel titleLabelWrapper = new JPanel();
		String toaPluginExternalConfig = configManager.getConfiguration("tombsofamascut", "pointsTrackerAllowExternal");

		Plugin toaPlugin = pluginManager.getPlugins()
			.stream()
			.filter(p -> p.getName().contains("Tombs of Amascut"))
			.findFirst()
			.orElse(null);

		boolean isToAInstalled = toaPlugin != null;
		boolean isToAEnabled = isToAInstalled && pluginManager.isPluginEnabled(toaPlugin);

		if (!isToAInstalled) {
			titleLabelWrapper.add(getWarningLabel(0), BorderLayout.CENTER);
		} else if (!isToAEnabled) {
			titleLabelWrapper.add(getWarningLabel(1), BorderLayout.CENTER);
		} else if (!Boolean.parseBoolean(toaPluginExternalConfig)) {
			titleLabelWrapper.add(getWarningLabel(2), BorderLayout.CENTER);
		} else {
			updateView();
		}

		gbc.gridwidth = 2;
		title.add(titleLabelWrapper, gbc);

		gbc.gridwidth = 1;
		gbc.gridy++;
		JButton enable = new JButton();
		if (!isToAEnabled) {
			enable.setText("Enable plugin");
		} else {
			enable.setText("Enable setting");
		}
		enable.addActionListener(e -> {
			if (!isToAEnabled) {
				pluginManager.setPluginEnabled(toaPlugin, true);
				configManager.setConfiguration("tombsofamascut", "pointsTrackerAllowExternal", true);
			} else
			{
				configManager.setConfiguration("tombsofamascut", "pointsTrackerAllowExternal", true);
			}
			updateView();
		});

		if (isToAInstalled) {
			title.add(enable, gbc);
			gbc.gridx++;
		}
		JButton close = new JButton();
		close.setText("Close");
		close.addActionListener(e -> {
			updateView();
		});
		title.add(close, gbc);

		panel.add(title);
		panel.revalidate();
		panel.repaint();
	}

	public JLabel getWarningLabel(int option) {

		JLabel titleLabel;
		switch (option) {
			case 1:
				titleLabel = new JLabel(
                    "<html>Raid Data Tracker has detected<br>" +
                    "that you do not have the<br>" +
                    "Tombs of Amascut plugin enabled.<br><br>" +
                    "The Tracker benefits from the<br>" +
                    "accurate point tracking provided by<br>" +
                    "the Tombs of Amascut plugin.<br><br>" +
                    "It is recommended that you enable it.<br></html>"
                );
				break;
			case 2:
				titleLabel = new JLabel(
                    "<html>Raid Data Tracker has detected<br>" +
                    "that you do not have the<br>" +
                    "Tombs of Amascut plugin's <br>" +
                    "\"Send to External plugins\"<br>" +
                    "config setting enabled.<br><br>" +
                    "The Tracker benefits from the<br>" +
                    "accurate point tracking provided by<br>" +
                    "the Tombs of Amascut plugin.<br><br>" +
                    "It is recommended that you enable it.<br></html>"
                );
				break;
			case 0:
			default:
				titleLabel = new JLabel(
                    "<html>Raid Data Tracker has detected<br>" +
                    "that you do not have the<br>" +
                    "Tombs of Amascut plugin installed.<br><br>" +
                    "The Tracker benefits from the<br>" +
                    "accurate point tracking provided by<br>" +
                    "the Tombs of Amascut plugin.<br><br>" +
                    "It is recommended that you install<br>" +
                    "it from the plugin hub.<br></html>"
                );
				break;
		}

		titleLabel.setForeground(Color.WHITE);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		return titleLabel;
	}

	public void updateView() {
		updateView(false);
	}

    public void updateView(boolean filterUpdate) {

        // If the panel is updated we don't need to show data for Beta worlds
        if (WorldUtils.playerOnBetaWorld(client)) {
			panel.removeAll();
            showDisabledView();
            return;
        }

        JPanel title = getTitle();
        JPanel filterPanel = getFilterPanel();
        JPanel killsLoggedPanel = getKillsLoggedPanel();
        JPanel uniquesPanel = getUniquesPanel();
        JPanel pointsPanel = getPointsPanel();
        JPanel splitsEarnedPanel = getSplitsEarnedPanel();
        JPanel changePurples = getChangePurples();
        JPanel mvpPanel = getMvpPanel();
        JPanel timeSplitsPanel = getTimeSplitsPanel();

		// Replaces panel.removeAll() to allow for selective component removal when dealing with ToA filter keypresses
		for(Component component : panel.getComponents()) {
			if ((component.equals(titleComponent) || component.equals(filterComponent)) && filterUpdate) {
				continue;
			}
			panel.remove(component);
		}

        if (config.showRegularDrops()) {
            SwingUtilities.invokeLater(() -> {
                regularDrops = getRegularDropsPanel();

				for(Component component : panel.getComponents()) {
					if ((component.equals(titleComponent) || component.equals(filterComponent)) && filterUpdate) {
						continue;
					}
					panel.remove(component);
				}

				if (config.showTitle() && !filterUpdate) {
					panel.add(title);
					titleComponent = title;
				}

				if (config.showFilters() && !filterUpdate) {
					panel.add(filterPanel);
					filterComponent = filterPanel;
				}

                panel.add(Box.createRigidArea(new Dimension(0, 5)));

                if (config.showKillsLogged()) {
                    panel.add(killsLoggedPanel);
                    panel.add(Box.createRigidArea(new Dimension(0, 5)));
                }

                if (config.showUniquesTable()) {
                    panel.add(uniquesPanel, BorderLayout.CENTER);
                    panel.add(Box.createRigidArea(new Dimension(0, 5)));
                }

                if (selectedRaidTab.equals(RaidType.TOB) && config.showMVPs()) {
                    panel.add(mvpPanel);
                    panel.add(Box.createRigidArea(new Dimension(0, 5)));
                }
                else if (config.showPoints()){
                    panel.add(pointsPanel);
                    panel.add(Box.createRigidArea(new Dimension(0, 5)));
                }

                if (config.showSplitGPEarned()) {
                    panel.add(splitsEarnedPanel);
                    panel.add(Box.createRigidArea(new Dimension(0, 15)));
                }

                if (config.showTimeSplits()) {
                    panel.add(timeSplitsPanel);
                    panel.add(Box.createRigidArea(new Dimension(0, 15)));
                }

                if (config.showRegularDrops()) {
                    panel.add(regularDrops);
                    panel.add(Box.createRigidArea(new Dimension(0, 15)));
                }

                if (config.showSplitChanger()) {
                    panel.add(changePurples);
                }

                panel.revalidate();
                panel.repaint();
            });
        }

		if (config.showTitle() && !filterUpdate) {
			panel.add(title);
			titleComponent = title;
		}

		if (config.showFilters() && !filterUpdate) {
			panel.add(filterPanel);
			filterComponent = filterPanel;
		}

        panel.add(Box.createRigidArea(new Dimension(0, 5)));

        if (config.showKillsLogged()) {
            panel.add(killsLoggedPanel);
            panel.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        if (config.showUniquesTable()) {
            panel.add(uniquesPanel, BorderLayout.CENTER);
            panel.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        if (selectedRaidTab.equals(RaidType.TOB) && config.showMVPs()) {
            panel.add(mvpPanel);
            panel.add(Box.createRigidArea(new Dimension(0, 5)));
        }
        else if (config.showPoints()){
            panel.add(pointsPanel);
            panel.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        if (config.showSplitGPEarned()) {
            panel.add(splitsEarnedPanel);
            panel.add(Box.createRigidArea(new Dimension(0, 15)));
        }

        if (config.showTimeSplits()) {
            panel.add(timeSplitsPanel);
            panel.add(Box.createRigidArea(new Dimension(0, 15)));
        }

        if (config.showRegularDrops()) {
            panel.add(regularDrops);
            panel.add(Box.createRigidArea(new Dimension(0, 15)));
        }

        if (config.showSplitChanger()) {
            panel.add(changePurples);
        }

        panel.revalidate();
        panel.repaint();
    }

    private JPanel getTitle() {
        final JPanel title = new JPanel();
        title.setBorder(new EmptyBorder(3, 0, 10, 0));
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));


        final JPanel buttonWrapper = new JPanel();
        buttonWrapper.setLayout(new GridLayout(0, 3));
        buttonWrapper.setBorder(new EmptyBorder(5, 0, 0, 0));

        JPanel coxButtonWrapper = new JPanel();
        coxButtonWrapper.setLayout(new GridLayout(0, 1));
        JButton coxButton = new JButton();

		JPanel tobButtonWrapper = new JPanel();
		tobButtonWrapper.setLayout(new GridLayout(0, 1));
		JButton tobButton = new JButton();

		JPanel toaButtonWrapper = new JPanel();
		toaButtonWrapper.setLayout(new GridLayout(0, 1));
		JButton toaButton = new JButton();

        coxButton.setText("CoX");
        coxButton.setForeground(Color.white);
        coxButton.setFont(FontManager.getRunescapeSmallFont());
        coxButton.setContentAreaFilled(false);
        coxButton.setOpaque(false);
        coxButton.setFocusable(false);
        coxButton.setBorderPainted(false);
        coxButton.setBorder(new EmptyBorder(10, 0, 10, 0));

		if (selectedRaidTab.equals(RaidType.COX)) {
			coxButtonWrapper.setBorder(new MatteBorder(1, 1, 0, 1, ColorScheme.LIGHT_GRAY_COLOR.darker()));
		} else {
			coxButtonWrapper.setBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR.darker()));
		}

        coxButton.setFocusPainted(false);
        coxButton.setToolTipText("Show Chambers of Xeric Loot");
        coxButton.addActionListener(e -> {
			if (!selectedRaidTab.equals(RaidType.COX)) {
				selectedRaidTab = RaidType.COX;
				updateView();
			}
        });

        tobButton.setText("ToB");
        tobButton.setForeground(Color.white);
        tobButton.setFont(FontManager.getRunescapeSmallFont());
        tobButton.setContentAreaFilled(false);
		tobButton.setOpaque(false);
        tobButton.setFocusable(false);
        tobButton.setBorderPainted(false);
        tobButton.setBorder(new EmptyBorder(7, 0, 7, 0));

		if (selectedRaidTab.equals(RaidType.TOB)) {
			tobButtonWrapper.setBorder(new MatteBorder(1, 1, 0, 1, ColorScheme.LIGHT_GRAY_COLOR.darker()));
		} else {
			tobButtonWrapper.setBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR.darker()));
		}

        tobButton.setFocusPainted(false);
        tobButton.setToolTipText("Show Theatre of Blood Loot");
        tobButton.addActionListener(e -> {
			if (!selectedRaidTab.equals(RaidType.TOB)) {
				selectedRaidTab = RaidType.TOB;
				updateView();
			}
        });

		toaButton.setText("ToA");
		toaButton.setForeground(Color.white);
		toaButton.setFont(FontManager.getRunescapeSmallFont());
		toaButton.setContentAreaFilled(false);
		toaButton.setOpaque(false);
		toaButton.setFocusable(false);
		toaButton.setBorderPainted(false);
		toaButton.setBorder(new EmptyBorder(10, 0, 10, 0));

		if (selectedRaidTab.equals(RaidType.TOA)){
			toaButtonWrapper.setBorder(new MatteBorder(1, 1, 0, 1, ColorScheme.LIGHT_GRAY_COLOR.darker()));
		} else {
			toaButtonWrapper.setBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR.darker()));
		}

		toaButton.setFocusPainted(false);
		toaButton.setToolTipText("Show Tombs of Amascut Loot");
		toaButton.addActionListener(e -> {
			if (!selectedRaidTab.equals(RaidType.TOA)) {
				selectedRaidTab = RaidType.TOA;
				updateView();
			}
		});

        coxButtonWrapper.add(coxButton);
        tobButtonWrapper.add(tobButton);
		toaButtonWrapper.add(toaButton);

        buttonWrapper.add(coxButtonWrapper);
        buttonWrapper.add(tobButtonWrapper);
		buttonWrapper.add(toaButtonWrapper);

        JPanel titleLabelWrapper = new JPanel();
        JLabel titleLabel = new JLabel("Raid Data Tracker");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR.darker()),
                new EmptyBorder(0, 20, 5, 20)
        ));

        titleLabelWrapper.add(titleLabel, BorderLayout.CENTER);

        title.add(titleLabelWrapper);
        title.add(buttonWrapper);

        return title;
    }

    private JPanel getUniquesPanel() {
        final JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        final JPanel title = new JPanel();
        title.setLayout(new GridLayout(0,3));
        title.setBorder(new EmptyBorder(3, 3, 3, 3));
        title.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

        JLabel drop = textPanel("Drop");
        JLabel titleSeen = textPanel("Seen");
        JLabel titleReceived = textPanel("Received");

        title.add(drop);
        title.add(titleReceived);
        title.add(titleSeen);


        final JPanel uniques = new JPanel();

        uniques.setLayout(new GridLayout(0,3));
        uniques.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        uniques.setBorder(new EmptyBorder(5, 5, 5, 5));

        int totalUniques = 0;
        int totalOwnName = 0;

        for (RaidUniques unique : getUniquesList()) {
            boolean isKit = false;
            boolean isDust = false;
            boolean isPet = false;

            final AsyncBufferedImage image = itemManager.getImage(unique.getItemID(), 1, false);

            final JLabel icon = new JLabel();


            icon.setIcon(new ImageIcon(resizeImage(image, 0.7, AffineTransformOp.TYPE_BILINEAR)));
            uniques.add(icon);

            image.onLoaded(() ->
            {
                icon.setIcon(new ImageIcon(resizeImage(image, 0.7, AffineTransformOp.TYPE_BILINEAR)));
                icon.revalidate();
                icon.repaint();
            });

            String amountReceived;
            String amountSeen;
            ArrayList<RaidTracker> l;
            ArrayList<RaidTracker> l2;

            switch (unique.getName()) {
                case "Metamorphic Dust":
                    l = filterDustReceivers();
                    l2 = filterOwnDusts(l);
                    isDust = true;
                    break;
                case "Twisted Kit":
                    l = filterKitReceivers();
                    l2 = filterOwnKits(l);
                    isKit = true;
                    break;
                case "Olmlet":
                case "Lil' Zik":
				case "Tumeken's guardian":
                    l = filterPetReceivers();
                    l2 = filterOwnPets(l);
                    isPet = true;
                    break;
                default:
                    l = filterRTListByName(unique.getName());
                    l2 = filterOwnDrops(l);
                    break;
            }

            amountSeen = Integer.toString(l.size());
            amountReceived = Integer.toString(l2.size());


            final JLabel received = new JLabel(amountReceived, SwingConstants.LEFT);
            final JLabel seen = new JLabel(amountSeen, SwingConstants.LEFT);

            received.setForeground(Color.WHITE);
            received.setFont(FontManager.getRunescapeSmallFont());
            seen.setForeground(Color.WHITE);
            seen.setFont(FontManager.getRunescapeSmallFont());

            final String tooltip = getUniqueToolTip(unique, l.size(), l2.size());

            if (!isDust && !isKit && !isPet) {
                totalUniques += l.size();
                totalOwnName += l2.size();
            }

            int bottomBorder = 1;

            if (isPet) {
                bottomBorder = 0;
            }

            icon.setToolTipText(tooltip);
            icon.setBorder(new MatteBorder(0,0,bottomBorder,1,ColorScheme.LIGHT_GRAY_COLOR.darker()));
            icon.setVerticalAlignment(SwingConstants.CENTER);
            icon.setHorizontalAlignment(SwingConstants.CENTER);

            received.setToolTipText(tooltip);
            received.setBorder(new MatteBorder(0,0,bottomBorder,1,ColorScheme.LIGHT_GRAY_COLOR.darker()));
            received.setVerticalAlignment(SwingConstants.CENTER);
            received.setHorizontalAlignment(SwingConstants.CENTER);

            seen.setToolTipText(tooltip);
            seen.setBorder(new MatteBorder(0,0,bottomBorder,0,ColorScheme.LIGHT_GRAY_COLOR.darker()));
            seen.setVerticalAlignment(SwingConstants.CENTER);
            seen.setHorizontalAlignment(SwingConstants.CENTER);

            uniques.add(received);

            uniques.add(seen);
        }

        JPanel total = new JPanel();
        total.setLayout(new GridLayout(0,3));
        total.setBorder(new EmptyBorder(3, 3, 3, 3));
        total.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

        JLabel totalText = textPanel("Total Purples:");
        JLabel totalOwnNameLabel = textPanel(Integer.toString(totalOwnName));
        JLabel totalUniquesLabel = textPanel(Integer.toString(totalUniques));

        total.add(totalText);
        total.add(totalOwnNameLabel);
        total.add(totalUniquesLabel);

        wrapper.add(title);
        wrapper.add(uniques);
        wrapper.add(total);

        return wrapper;
    }

    private JPanel getPointsPanel() {
        final JPanel points = new JPanel();
        points.setLayout(new GridLayout(0,2));
        points.setBorder(new EmptyBorder(3, 3, 3, 3));
        points.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

        JLabel personalTitle = textPanel("Personal Points");
        JLabel totalTitle = textPanel("Total Points");

        points.add(personalTitle);
        points.add(totalTitle);

        int personalPoints = 0;
        int totalPoints = 0;

        if (loaded) {
            personalPoints = atleastZero(getFilteredRTList().stream().mapToInt(RaidTracker::getPersonalPoints).sum());
            totalPoints = atleastZero(getFilteredRTList().stream().mapToInt(RaidTracker::getTotalPoints).sum());
        }

        JLabel personalPointsLabel = textPanel(format(personalPoints));
        personalPointsLabel.setToolTipText(NumberFormat.getInstance().format(personalPoints) + " Personal Points");
        personalTitle.setToolTipText(NumberFormat.getInstance().format(personalPoints) + " Personal Points");

        JLabel totalPointsLabel = textPanel(format(totalPoints));
        totalPointsLabel.setToolTipText(NumberFormat.getInstance().format(totalPoints) + " Total Points");
        totalTitle.setToolTipText(NumberFormat.getInstance().format(totalPoints) + " Total Points");

        points.add(personalPointsLabel);
        points.add(totalPointsLabel);

        return points;
    }

    private JPanel getSplitsEarnedPanel() {
        final JPanel wrapper = new JPanel();
        wrapper.setLayout(new GridLayout(0,2));
        wrapper.setBorder(new EmptyBorder(3, 3, 3, 3));
        wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

        int splitGP = 0;

        if (loaded) {
            splitGP = atleastZero(getFilteredRTList().stream().mapToInt(RaidTracker::getLootSplitReceived).sum());


        }

        JLabel textLabel = textPanel("Split GP earned:");
        textLabel.setToolTipText("GP earned counting the split GP you earned from a drop");

        JLabel valueLabel = textPanel(format(splitGP));
        valueLabel.setToolTipText(NumberFormat.getInstance().format(splitGP) + " gp");

        if (splitGP > 1000000) {
            valueLabel.setForeground(Color.GREEN);
        }

        wrapper.add(textLabel);
        wrapper.add(valueLabel);

        return wrapper;
    }

    private JPanel getKillsLoggedPanel() {
        final JPanel wrapper = new JPanel();
        wrapper.setLayout(new GridLayout(0,2));
        wrapper.setBorder(new EmptyBorder(3, 3, 3, 3));
        wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

        int killsLogged = 0;

        if (loaded) {
            killsLogged = getDistinctKills(getFilteredRTList()).size();
        }

        JLabel textLabel = textPanel("Kills Logged:");
        JLabel valueLabel = textPanel(Integer.toString(killsLogged));

        wrapper.add(textLabel);
        wrapper.add(valueLabel);

        return wrapper;
    }

    private JPanel getRegularDropsPanel() {
        final JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        if (loaded) {
            Map<Integer, RaidTrackerItem> uniqueIDs = new HashMap<>();
            try {
                uniqueIDs = getDistinctRegularDrops().get();
            } catch (InterruptedException | ExecutionException e) {
                uniqueIDs = new HashMap<>();
            } finally {
                Map<Integer, Integer> priceMap = new HashMap<>();

                for (RaidTrackerItem item : uniqueIDs.values()) {
                    priceMap.put(item.getId(), item.getPrice());
                }

                if (!uniqueIDs.values().isEmpty()) {
                    for (RaidTracker RT : getFilteredRTList()) {
                        for (RaidTrackerItem item : RT.getLootList()) {
                            RaidTrackerItem RTI = uniqueIDs.get(item.getId());

                            //making sure to not change the clues here as it's been handled in getDistinctRegularDrops
                            if (RTI != null && RTI.getId() != 12073) {
                                int qty = RTI.getQuantity();
                                RTI.setQuantity(qty + item.getQuantity());

                                RTI.setPrice(priceMap.get(item.getId()) * RTI.getQuantity());

                                uniqueIDs.replace(item.getId(), RTI);
                            }
                        }
                    }

                    ArrayList<RaidTrackerItem> regularDropsList = new ArrayList<>(uniqueIDs.values());

                    regularDropsList.sort((o2, o1) -> Integer.compare(o1.getPrice(), o2.getPrice()));


                    int regularDropsSum = regularDropsList.stream().mapToInt(RaidTrackerItem::getPrice).sum();

                    final JPanel drops = new JPanel();
                    drops.setLayout(new GridLayout(0, 5));

                    for (RaidTrackerItem drop : regularDropsList) {
                        AsyncBufferedImage image = itemManager.getImage(drop.getId(), drop.getQuantity(), drop.getQuantity() > 1);

                        JPanel iconWrapper = new JPanel();
                        iconWrapper.setPreferredSize(new Dimension(40, 40));
                        iconWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);

                        JLabel icon = new JLabel();
                        image.addTo(icon);
                        icon.setBorder(new EmptyBorder(0, 5, 0, 0));

                        image.onLoaded(() ->
                        {
                            image.addTo(icon);
                            icon.revalidate();
                            icon.repaint();
                        });

                        iconWrapper.add(icon, BorderLayout.CENTER);
                        iconWrapper.setBorder(new MatteBorder(1, 0, 0, 1, ColorScheme.DARK_GRAY_COLOR));
                        iconWrapper.setToolTipText(getRegularToolTip(drop));

                        drops.add(iconWrapper);
                    }

                    final JPanel title = new JPanel();
                    title.setLayout(new GridLayout(0, 2));
                    title.setBorder(new EmptyBorder(3, 20, 3, 10));
                    title.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

                    JLabel textLabel = textPanel("Regular Drops");
                    textLabel.setHorizontalAlignment(SwingConstants.LEFT);

                    JLabel valueLabel = textPanel(format(regularDropsSum) + " gp");
                    valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
                    valueLabel.setForeground(Color.LIGHT_GRAY.darker());
                    valueLabel.setToolTipText(NumberFormat.getInstance().format(regularDropsSum));

                    title.add(textLabel);
                    title.add(valueLabel);


                    wrapper.add(title);
                    wrapper.add(drops);
                }
            }
        }

        return wrapper;
    }

    private JPanel getChangePurples() {
        final JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        ArrayList<SplitChanger> SCList = new ArrayList<>();

        JPanel titleWrapper = new JPanel();
        titleWrapper.setLayout(new GridBagLayout());
        titleWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
        titleWrapper.setBorder(new EmptyBorder(3,3,3,3));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = SwingConstants.HORIZONTAL;
        c.gridx = 0;
        c.weightx = 1;

        JLabel changes = textPanel("Change Purple Splits");
        changes.setBorder(new EmptyBorder(0,5,0,0));

        update = new JButton();
        update.setText("Update");
        update.setFont(FontManager.getRunescapeSmallFont());
        update.setPreferredSize(new Dimension(60,20));
        update.setEnabled(false);
        update.setBorder(new EmptyBorder(2,2,2,2));
        update.setFocusPainted(false);
        update.setToolTipText("Nothing to update");
        update.addActionListener(e -> {
			switch(selectedRaidTab) {
                case COX:
                    SCList.forEach(SC -> {
                        RaidTracker tempRaidTracker = SC.getRaidTracker();
                        coxUUIDMap.put(tempRaidTracker.getUniqueID(), tempRaidTracker);
                    });
                    coxRTList = new ArrayList<>(coxUUIDMap.values());
                    fw.updateRTList(coxRTList, RaidType.COX);
                    break;
				case TOB:
					SCList.forEach(SC -> {
						RaidTracker tempRaidTracker = SC.getRaidTracker();
						tobUUIDMap.put(tempRaidTracker.getUniqueID(), tempRaidTracker);
					});
					tobRTList = new ArrayList<>(tobUUIDMap.values());
					fw.updateRTList(tobRTList, RaidType.TOB);
					break;
				case TOA:
					SCList.forEach(SC -> {
						RaidTracker tempRaidTracker = SC.getRaidTracker();
						toaUUIDMap.put(tempRaidTracker.getUniqueID(), tempRaidTracker);
					});
					toaRTList = new ArrayList<>(toaUUIDMap.values());
					fw.updateRTList(toaRTList, RaidType.TOA);
					break;
				default:
                    log.error("Unexpected selectedRaidTab value, unable to update purple splits.");
                    break;
			}

            updateView();
        });

        c.anchor = GridBagConstraints.WEST;
        titleWrapper.add(changes, c);

        c.gridx++;
        c.anchor = GridBagConstraints.EAST;

        titleWrapper.add(update , c);

        if (loaded) {
            ArrayList<RaidTracker> purpleList = filterPurples();
            purpleList.sort((o2, o1) -> Long.compare(o1.getDate(), o2.getDate()));

            if (purpleList.size() > 0) {
                wrapper.add(titleWrapper);
                wrapper.add(Box.createRigidArea(new Dimension(0, 2)));

                for (int i = 0; i < Math.min(purpleList.size(), 10); i++) {
                    RaidTracker RT = purpleList.get(i);
                    SplitChanger SC = new SplitChanger(itemManager, RT, this);
                    SCList.add(SC);
                    wrapper.add(SC);
                    wrapper.add(Box.createRigidArea(new Dimension(0, 7)));
                }
            }
        }

        return wrapper;
    }

    @SuppressWarnings("ConstantConditions")
    private JPanel getFilterPanel() {
        final JPanel wrapper = new JPanel();
        wrapper.setLayout(new GridBagLayout());
        wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
        wrapper.setBorder(new EmptyBorder(5,5,5,5));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = SwingConstants.HORIZONTAL;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx = 0;
        c.gridy = 0;

        JLabel filter = textPanel("Filter kills logged");
        filter.setHorizontalAlignment(SwingConstants.LEFT);
        filter.setBorder(new EmptyBorder(0,0,0,17));
        c.anchor = GridBagConstraints.WEST;
        wrapper.add(filter);

        JComboBox<String> choices = new JComboBox<>(new String []{"All Time", "12 Hours", "Today", "3 Days", "Week", "Month","3 Months", "Year", "X Kills"});
        choices.setSelectedItem(dateFilter);
		choices.setPreferredSize(new Dimension(105, 25));
		choices.setMinimumSize(new Dimension(100, 25));
		choices.setMaximumSize(new Dimension(110, 25));
        choices.setFocusable(false);

        choices.addActionListener(e ->  {
            dateFilter = choices.getSelectedItem().toString();
            if (dateFilter.equals("X Kills")) {
                choices.setToolTipText("X can be changed in the settings");
            }
            else {
                choices.setToolTipText(null);
            }
            if (loaded) {
                updateView();
            }
        });

        JComboBox<String> cm = new JComboBox<>(new String []{"CM & Normal", "Normal Only", "CM Only"});
        cm.setFocusable(false);
		cm.setPreferredSize(new Dimension(105,25));
		cm.setMinimumSize(new Dimension(100, 25));
		cm.setMaximumSize(new Dimension(110, 25));
        cm.setSelectedItem(cmFilter);

        cm.addActionListener(e -> {
            cmFilter = cm.getSelectedItem().toString();
            if (loaded) {
                updateView();
            }
        });

        JComboBox<String> mvp = new JComboBox<>(new String []{"Both", "My MVP", "Not My MVP"});
        mvp.setFocusable(false);
		mvp.setPreferredSize(new Dimension(105,25));
		mvp.setMinimumSize(new Dimension(100, 25));
		mvp.setMaximumSize(new Dimension(110, 25));
        mvp.setSelectedItem(mvpFilter);

        mvp.addActionListener(e -> {
            mvpFilter = mvp.getSelectedItem().toString();
            if (loaded) {
                updateView();
            }
        });

		JComboBox<String> raidLevel = new JComboBox<>(new String []{"All Levels", "Entry Mode", "Normal Mode", "Expert Mode", "Custom"});
		raidLevel.setFocusable(false);
		raidLevel.setPreferredSize(new Dimension(105,25));
		raidLevel.setMinimumSize(new Dimension(100, 25));
		raidLevel.setMaximumSize(new Dimension(110, 25));
		raidLevel.setSelectedItem(raidLevelFilter);

		raidLevel.addActionListener(e -> {

			if (raidLevelFilterIsAPI) {
				raidLevelFilterIsAPI = false;
				return;
			}

			raidLevelFilter = raidLevel.getSelectedItem().toString();
			raidLevelFilterLow = toaFilterMap.get(raidLevelFilter)[0];
			raidLevelFilterHigh = toaFilterMap.get(raidLevelFilter)[1];

			if (loaded) {
				updateView();
			}
		});

        JComboBox<String> teamSize;

		switch (selectedRaidTab) {
            case COX:
                teamSize = new JComboBox<>(new String [] {
                    "All sizes",
                    "Solo",
                    "Duo",
                    "Trio",
                    "4-man",
                    "5-man",
                    "6-man",
                    "7-man",
                    "8-10 Players",
                    "11-14 Players",
                    "15-24 Players",
                    "24+ Players"
                });
                break;
			case TOB:
				teamSize = new JComboBox<>(new String [] {
                    "All sizes",
                    "Solo",
                    "Duo",
                    "Trio",
                    "4-man",
                    "5-man"
                });
				break;
			case TOA:
				teamSize = new JComboBox<>(new String [] {
                    "All sizes",
                    "Solo",
                    "Duo",
                    "Trio",
                    "4-man",
                    "5-man",
                    "6-man",
                    "7-man",
                    "8-man"
                });
				break;
			default:
                teamSize = new JComboBox<>(new String [] {
                    "All sizes",
                    "Solo"
                });
                break;
		}

        teamSize.setFocusable(false);
        teamSize.setPreferredSize(new Dimension(105, 25));
		teamSize.setMinimumSize(new Dimension(100, 25));
		teamSize.setMaximumSize(new Dimension(110, 25));
        teamSize.setSelectedItem(teamSizeFilter);

        teamSize.addActionListener(e -> {
            teamSizeFilter = teamSize.getSelectedItem().toString();
            if (loaded) {
                updateView();
            }
        });


        c.gridy = 1;
        wrapper.add(Box.createRigidArea(new Dimension(0, 5)), c);

        c.gridy = 2;
        wrapper.add(choices, c);

		c.gridy = 4;

		wrapper.add(teamSize, c);

		c.gridy = 2;
        c.gridx = 1;
        c.anchor = GridBagConstraints.EAST;

		switch (selectedRaidTab) {
            case COX:
                wrapper.add(cm, c);
                break;
			case TOB:
				wrapper.add(mvp, c);
				break;
			case TOA:
				wrapper.add(raidLevel, c);
				break;
			default:
				break;

		}

        c.gridy = 3;
        wrapper.add(Box.createRigidArea(new Dimension(0, 2)), c);

		c.gridy = 4;

		if (selectedRaidTab.equals(RaidType.TOA)) {
			wrapper.add(getToAFilterPanel(), c);
		}

        JPanel buttonWrapper = new JPanel();
        buttonWrapper.setPreferredSize(new Dimension(82, 20));
        buttonWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		buttonWrapper.setLayout(new GridLayout(0, 2, 2 ,0));
		if (selectedRaidTab.equals(RaidType.TOA)) {
			buttonWrapper.setLayout(new GridLayout(0, 3, 2 ,0));
		}

		BufferedImage saveIcon = ImageUtil.loadImageResource(getClass(), "save-grey.png");
		BufferedImage saveHover = ImageUtil.loadImageResource(getClass(), "save-white.png");
        BufferedImage refreshIcon = ImageUtil.loadImageResource(getClass(), "refresh-grey.png");
        BufferedImage refreshHover = ImageUtil.loadImageResource(getClass(), "refresh-white.png");
        BufferedImage deleteIcon = ImageUtil.loadImageResource(getClass(), "delete-grey.png");
        BufferedImage deleteHover = ImageUtil.loadImageResource(getClass(), "delete-white.png");

		JButton save = imageButton(saveIcon);
		save.setToolTipText("Save Custom Filter");
		save.addActionListener(e -> {
			final int saveInput = JOptionPane.showConfirmDialog(
                this.getRootPane(),
                "<html>Are you sure you want to save this Custom filter preset?<br>" +
                    "It will overwrite your previous Custom filter values.<br>" +
                    "There is no way to undo this action.</html>",
                "Warning - Raid Data Tracker Custom Filter",
                JOptionPane.YES_NO_OPTION);
			if (saveInput == JOptionPane.YES_OPTION)
			{
				configManager.setConfiguration(RaidTrackerConfig.CONFIG_GROUP, "toaFilterCustomLow", raidLevelFilterLow);
				configManager.setConfiguration(RaidTrackerConfig.CONFIG_GROUP, "toaFilterCustomHigh", raidLevelFilterHigh);
				toaFilterMap.put("Custom", new int[]{raidLevelFilterLow, raidLevelFilterHigh});
				updateView();
			}
		});

        JButton refresh = imageButton(refreshIcon);
        refresh.setToolTipText("Refresh kills logged");
        refresh.addActionListener(e -> {
            if (loaded) {
                loadRTList();
            }
        });

        JButton delete = imageButton(deleteIcon);
        delete.setToolTipText("Delete all logged kills");
        delete.addActionListener(e -> {
            if (loaded) {
                clearData();
            }
        });

		save.addMouseListener(new MouseAdapter() {
			public void mouseEntered (MouseEvent e){
				save.setIcon(new ImageIcon(saveHover));
			}

			public void mouseExited (MouseEvent e){
				save.setIcon(new ImageIcon(saveIcon));
			}
		});
        refresh.addMouseListener(new MouseAdapter() {
            public void mouseEntered (MouseEvent e){
                refresh.setIcon(new ImageIcon(refreshHover));
            }

            public void mouseExited (MouseEvent e){
                refresh.setIcon(new ImageIcon(refreshIcon));
            }
        });

        delete.addMouseListener(new MouseAdapter() {
            public void mouseEntered (MouseEvent e){
                delete.setIcon(new ImageIcon(deleteHover));
            }

            public void mouseExited (MouseEvent e){
                delete.setIcon(new ImageIcon(deleteIcon));
            }
        });

		if (selectedRaidTab.equals(RaidType.TOA)) {
			buttonWrapper.add(save);
		}
        buttonWrapper.add(refresh);
        buttonWrapper.add(delete);

        c.gridy = 0;

        wrapper.add(buttonWrapper, c);
        return wrapper;
    }

    private JPanel getMvpPanel() {
        final JPanel wrapper = new JPanel();
        wrapper.setLayout(new GridLayout(0,2));
        wrapper.setBorder(new EmptyBorder(3, 3, 3, 3));
        wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

        int mvpAmount = 0;


        if (loaded) {
            mvpAmount = tobRTList.stream().mapToInt(RT -> {
                if (RT.isMvpInOwnName()) {
                    return 1;
                }
                return 0;
            }).sum();


        }

        JLabel textLabel = textPanel("Total MVP's:");

        JLabel valueLabel = textPanel(Integer.toString(mvpAmount));

        wrapper.add(textLabel);
        wrapper.add(valueLabel);
        return wrapper;
    }

	private JPanel getToAFilterPanel() {
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new GridBagLayout());
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		wrapper.setPreferredSize(new Dimension(105,25));
		wrapper.setMinimumSize(new Dimension(100, 25));
		wrapper.setMaximumSize(new Dimension(110, 25));

		GridBagConstraints c2 = new GridBagConstraints();

		c2.weightx = 1;
		c2.weighty = 1;
		c2.gridx = 0;
		c2.gridy = 0;

		JTextField filterFieldLow = new JTextField();
		JTextField filterFieldHigh = new JTextField();

		filterFieldLow.setMinimumSize(new Dimension(40, 25));
		filterFieldLow.setMaximumSize(new Dimension(42, 25));
		filterFieldLow.setText(String.valueOf(raidLevelFilterLow));
		filterFieldLow.setFocusable(true);
		filterFieldLow.putClientProperty("JTextField.selectAllOnFocusPolicy", "never");

		String val1 = filterFieldLow.getText();
		filterFieldLow.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				String userInput = filterFieldLow.getText();


				if (userInput.isEmpty()) {
					raidLevelFilterLow = toaRaidLevelMin;
					return;
				}

				if (!Pattern.matches("-?\\d+", userInput)) {
					filterFieldLow.setText(val1);
					return;
				}

				if (userInput.startsWith("0")) {
					filterFieldLow.setText(String.valueOf(Integer.parseInt(userInput)));
				}

				raidLevelFilterLow = Integer.parseInt(userInput);

				if (Integer.parseInt(userInput) == toaRaidLevelMin) {
					raidLevelFilterLow = toaRaidLevelMin;
				}

				if (Integer.parseInt(userInput) > toaRaidLevelMax) {
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							filterFieldLow.setText(String.valueOf(toaRaidLevelMax));
							raidLevelFilterLow = toaRaidLevelMax;
						}
					});
				}

				updateRaidFilterSilently(wrapper);
			}
		});


		filterFieldHigh.setMinimumSize(new Dimension(40, 25));
		filterFieldHigh.setMaximumSize(new Dimension(42, 25));
		filterFieldHigh.setText(String.valueOf(raidLevelFilterHigh));
		filterFieldHigh.setFocusable(true);
		filterFieldHigh.putClientProperty("JTextField.selectAllOnFocusPolicy", "never");

		String val2 = filterFieldHigh.getText();

		filterFieldHigh.addKeyListener(new KeyAdapter() {

			@Override
			public void keyReleased(KeyEvent e) {
				String userInput = filterFieldHigh.getText();

				if (userInput.isEmpty()) {
					raidLevelFilterHigh = toaRaidLevelMax;
					return;
				}

				if (!Pattern.matches("-?\\d+", userInput)) {
					filterFieldHigh.setText(val2);
					return;
				}

				if (Integer.parseInt(userInput) == toaRaidLevelMin) {
					return;
				}

				if (userInput.startsWith("0")) {
					filterFieldHigh.setText(String.valueOf(Integer.parseInt(userInput)));
				}

				raidLevelFilterHigh = Integer.parseInt(userInput);

				if (Integer.parseInt(userInput) > toaRaidLevelMax) {
					SwingUtilities.invokeLater(new Runnable()
					{
						@Override
						public void run()
						{
							filterFieldHigh.setText(String.valueOf(toaRaidLevelMax));
							raidLevelFilterHigh = toaRaidLevelMax;
						}
					});
				}

				updateRaidFilterSilently(wrapper);

			}
		});

		wrapper.add(filterFieldLow, c2);

		c2.gridx = 1;

		JLabel to = new JLabel();
		to.setText("to");

		to.setHorizontalAlignment(SwingConstants.CENTER);

		to.setMinimumSize(new Dimension(22, 25));

		wrapper.add(to, c2);
		c2.gridx = 2;
		wrapper.add(filterFieldHigh, c2);

		return wrapper;
	}

	@SuppressWarnings("unchecked")
	private void updateRaidFilterSilently(JPanel wrapper) {
		JComboBox<String> tempField;
		for (int i = wrapper.getParent().getComponentCount() - 1; i >= 0; i--) {
			if (wrapper.getParent().getComponent(i) instanceof JComboBox) {
				tempField = (JComboBox<String>) wrapper.getParent().getComponent(i);

				SwingUtilities.invokeLater(new Runnable()
				{
					@Override
					public void run()
					{

						raidLevelFilterIsAPI = true;
						tempField.setSelectedIndex(tempField.getItemCount() - 1);
					}
				});

				break;
			}
		}

		raidLevelFilter = "Custom";
		updateView(true);
	}

	private JPanel getTimeSplitsPanel() {
        final JPanel wrapper = new JPanel();

        if (loaded) {
            wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
            wrapper.setBorder(new EmptyBorder(3, 0, 0, 0));
            wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

            final JPanel title = new JPanel();

            title.setBorder(new EmptyBorder(3, 20, 3, 10));
            title.setLayout(new GridLayout(0, 1));
            title.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

            JLabel textLabel = textPanel("Best Recorded Times");

            title.add(textLabel);

			wrapper.add(title);

			JPanel timeTableContainer = new JPanel();
			timeTableContainer.setBorder(new EmptyBorder(1, 7, 5, 7));
			timeTableContainer.setLayout(new GridLayout(0, 1));
			timeTableContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

			JPanel timeTable = new JPanel();
			JPanel timeTableFourCol = new JPanel();

			switch(selectedRaidTab) {
                case COX:
                    timeTable.setLayout(new GridLayout(0, 2));
                    timeTable.setBorder(new EmptyBorder(5,3,1,3));
                    timeTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);

                    timeTable.add(textPanel("Upper Level", 0));
                    timeTable.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getUpperTime() > 0)
                            .min(comparing(RaidTracker::getUpperTime))
                            .orElse(new RaidTracker())
                            .getUpperTime()
                    ), 1));

                    if (!cmFilter.equals("Normal Only")) {
                        int middleTime = getFilteredRTList().stream()
                                            .filter(RT -> RT.getMiddleTime() > 0)
                                            .min(comparing(RaidTracker::getMiddleTime))
                                            .orElse(new RaidTracker())
                                            .getMiddleTime();
                        if (middleTime > 0) {
                            timeTable.add(textPanel("Middle Level", 0));
                            timeTable.add(textPanel(secondsToMinuteString(middleTime), 1));
                        }

                    }
                    timeTable.add(textPanel("Lower Level", 0));
                    timeTable.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getLowerTime() > 0)
                            .min(comparing(RaidTracker::getLowerTime))
                            .orElse(new RaidTracker())
                            .getLowerTime()
                    ), 1));

                    timeTable.add(textPanel("Olm Time", 0));

                    RaidTracker olmTimeRT = getFilteredRTList().stream()
                        .filter(RT -> RT.getLowerTime() > 0 && RT.getRaidTime() > 0)
                        .min(Comparator.comparingInt(o -> o.getRaidTime() - o.getLowerTime()))
                        .orElse(new RaidTracker());

                    timeTable.add(textPanel(secondsToMinuteString(
                        olmTimeRT.getRaidTime() - olmTimeRT.getLowerTime()
                    ), 1));
                    wrapper.add(timeTable);
                    break;
				case TOB:
					timeTable.setLayout(new GridLayout(0, 2));
					timeTable.setBorder(new EmptyBorder(5,3,1,3));
					timeTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);
					timeTable.add(textPanel("Maiden Time", 0));
					timeTable.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getMaidenTime() > 0)
                            .min(comparing(RaidTracker::getMaidenTime))
                            .orElse(new RaidTracker())
                            .getMaidenTime()
                    ), 1));
					timeTable.add(textPanel("Bloat Time", 0));
					timeTable.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getBloatTime() > 0)
                            .min(comparing(RaidTracker::getBloatTime))
                            .orElse(new RaidTracker())
                            .getBloatTime()
                    ), 1));
					timeTable.add(textPanel("Nylo Time", 0));
					timeTable.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getNyloTime() > 0)
                            .min(comparing(RaidTracker::getNyloTime))
                            .orElse(new RaidTracker())
                            .getNyloTime()
                    ), 1));
					timeTable.add(textPanel("Sotetseg Time", 0));
					timeTable.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getSotetsegTime() > 0)
                            .min(comparing(RaidTracker::getSotetsegTime))
                            .orElse(new RaidTracker())
                            .getSotetsegTime()
                    ), 1));
					timeTable.add(textPanel("Xarpus Time", 0));
					timeTable.add(textPanel(secondsToMinuteString(
                            getFilteredRTList().stream()
                                .filter(RT -> RT.getNyloTime() > 0)
                                .min(comparing(RaidTracker::getXarpusTime))
                                .orElse(new RaidTracker())
                                .getXarpusTime()
                        ), 1));
					timeTable.add(textPanel("Verzik Time", 0));
					timeTable.add(textPanel(
                            secondsToMinuteString(
                                getFilteredRTList().stream()
                                    .filter(RT -> RT.getVerzikTime() > 0)
                                    .min(comparing(RaidTracker::getVerzikTime))
                                    .orElse(new RaidTracker())
                                    .getVerzikTime()
                            ),
                        1));
					break;
				case TOA:
					timeTableFourCol.setLayout(new GridLayout(0, 4));
					timeTableFourCol.setBorder(new EmptyBorder(5,0,0,0));
					timeTableFourCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);
					timeTableFourCol.add(textPanel("", 0));
					timeTableFourCol.add(textPanel("Puzzle", 0));
					timeTableFourCol.add(textPanel("Boss", 0));
					timeTableFourCol.add(textPanel("Path", 1));
					timeTableContainer.add(timeTableFourCol);

					timeTableFourCol = stripedPanel(true);
					timeTableFourCol.add(textPanel("Ba-Ba", 2));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getApmekenTime() > 0)
                            .min(comparing(RaidTracker::getApmekenTime))
                            .orElse(new RaidTracker())
                            .getApmekenTime()
                    ), 2));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getBabaTime() > 0)
                            .min(comparing(RaidTracker::getBabaTime))
                            .orElse(new RaidTracker())
                            .getBabaTime()), 2));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getApmekenTime() > 0 && RT.getBabaTime() > 0)
                            .mapToInt(RT -> RT.getApmekenTime() + RT.getBabaTime())
                            .reduce(Math::min)
                            .orElse(0)
                    ), 3));
					timeTableContainer.add(timeTableFourCol);

					timeTableFourCol = stripedPanel(false);
					timeTableFourCol.add(textPanel("Kephri", 2));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getScabarasTime() > 0)
                            .min(comparing(RaidTracker::getScabarasTime))
                            .orElse(new RaidTracker())
                            .getScabarasTime()
                    ), 2));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getKephriTime() > 0)
                            .min(comparing(RaidTracker::getKephriTime))
                            .orElse(new RaidTracker())
                            .getKephriTime()
                    ), 2));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getScabarasTime() > 0 && RT.getKephriTime() > 0)
                            .mapToInt(RT -> RT.getScabarasTime() + RT.getKephriTime())
                            .reduce(Math::min)
                            .orElse(0)
                    ), 3));
					timeTableContainer.add(timeTableFourCol);

					timeTableFourCol = stripedPanel(true);
					timeTableFourCol.add(textPanel("Akkha", 2));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getHetTime() > 0)
                            .min(comparing(RaidTracker::getHetTime))
                            .orElse(new RaidTracker())
                            .getHetTime()
                    ), 2));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getAkkhaTime() > 0)
                            .min(comparing(RaidTracker::getAkkhaTime))
                            .orElse(new RaidTracker())
                            .getAkkhaTime()
                    ), 2));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getHetTime() > 0 && RT.getAkkhaTime() > 0)
                            .mapToInt(RT -> RT.getHetTime() + RT.getAkkhaTime())
                            .reduce(Math::min)
                            .orElse(0)
                    ), 3));
					timeTableContainer.add(timeTableFourCol);

					timeTableFourCol = stripedPanel(false);
					timeTableFourCol.add(textPanel("Zebak", 4));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getCrondisTime() > 0)
                            .min(comparing(RaidTracker::getCrondisTime))
                            .orElse(new RaidTracker())
                            .getCrondisTime()
                    ), 4));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getZebakTime() > 0)
                            .min(comparing(RaidTracker::getZebakTime))
                            .orElse(new RaidTracker())
                            .getZebakTime()
                    ), 4));
					timeTableFourCol.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getCrondisTime() > 0 && RT.getZebakTime() > 0)
                            .mapToInt(RT -> RT.getCrondisTime() + RT.getZebakTime())
                            .reduce(Math::min)
                            .orElse(0)
                    ), 5));
					timeTableContainer.add(timeTableFourCol);

					timeTable = new JPanel();
					timeTable.setLayout(new GridLayout(0, 2));
					timeTable.setBackground(ColorScheme.DARK_GRAY_COLOR);
					timeTable.add(textPanel("Completion Time", 4));
					timeTable.add(textPanel(secondsToMinuteString(
                        getFilteredRTList().stream()
                            .filter(RT -> RT.getToaCompTime() > 0)
                            .min(comparing(RaidTracker::getToaCompTime))
                            .orElse(new RaidTracker())
                            .getToaCompTime()
                    ), 5));
					timeTableContainer.add(timeTable);
					break;
				default:
                    break;
			}

			timeTable = new JPanel();
			timeTable.setLayout(new GridLayout(0, 2));
			timeTable.setBorder(new EmptyBorder(0,3,0,3));
			timeTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);

			if (selectedRaidTab == RaidType.TOA) {
				timeTable.add(textPanel("Overall Time", 7));
				timeTable.add(textPanel(secondsToMinuteString(
                    getFilteredRTList().stream()
                        .filter(RT -> RT.getRaidTime() > 0)
                        .min(comparing(RaidTracker::getRaidTime))
                        .orElse(new RaidTracker())
                        .getRaidTime()
                ), 6));
				timeTableContainer.add(timeTable);
				wrapper.add(timeTableContainer);
			} else {
				timeTable.add(textPanel("Overall Time", 2));
				timeTable.add(textPanel(secondsToMinuteString(
                    getFilteredRTList().stream()
                        .filter(RT -> RT.getRaidTime() > 0)
                        .min(comparing(RaidTracker::getRaidTime))
                        .orElse(new RaidTracker())
                        .getRaidTime()
                ), 3));
				wrapper.add(timeTable);
			}
        }

        return wrapper;
    }

    public void setUpdateButton(boolean b) {
        update.setEnabled(b);
        update.setBackground(ColorScheme.BRAND_ORANGE);
        update.setToolTipText("Update");
    }

    public JLabel textPanel(String text) {
        JLabel label = new JLabel();
        label.setText(text);
        label.setForeground(Color.WHITE);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());

        return label;
    }

    public JLabel textPanel(String text, int borderOptions) {
        JLabel label = new JLabel();
        label.setText(text);
        label.setForeground(Color.WHITE);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());

        if (borderOptions == 0) {
            label.setBorder(new CompoundBorder(
                    new MatteBorder(0,0,1,1,ColorScheme.LIGHT_GRAY_COLOR.darker()),
                    new EmptyBorder(5,3,5,3)));
        }
        else if (borderOptions == 1) {
            label.setBorder(new MatteBorder(0,0,1,0,ColorScheme.LIGHT_GRAY_COLOR.darker()));
        }
        else if (borderOptions == 2) {
            label.setBorder(new MatteBorder(0,0,0,1,ColorScheme.LIGHT_GRAY_COLOR.darker()));
        }
		else if (borderOptions == 3) {
			label.setBorder(new CompoundBorder(
				new MatteBorder(0,0,0,0,ColorScheme.LIGHT_GRAY_COLOR.darker()),
				new EmptyBorder(5,3,5,3)));
		}  else if (borderOptions == 4) {
			label.setBorder(new MatteBorder(0,0,1,1,ColorScheme.LIGHT_GRAY_COLOR.darker()));
		} else if (borderOptions == 5) {
			label.setBorder(new CompoundBorder(
				new MatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR.darker()),
				new EmptyBorder(5, 3, 5, 3)));
		} else if (borderOptions == 6) {
			label.setBorder(new CompoundBorder(
				new MatteBorder(0,0,0,0,ColorScheme.LIGHT_GRAY_COLOR.darker()),
				new EmptyBorder(5,3,0,3)));
		}  else if (borderOptions == 7) {
			label.setBorder(new CompoundBorder(
				new MatteBorder(0,0,0,1,ColorScheme.LIGHT_GRAY_COLOR.darker()),
				new EmptyBorder(5,3,0,3)));
		} else {
            label.setBorder(new MatteBorder(0,0,0,0,ColorScheme.LIGHT_GRAY_COLOR.darker()));
        }

        return label;
    }

	public JPanel stripedPanel(boolean striped) {
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(0, 4));

		if (striped) {
			panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		} else {
			panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		}
		return panel;
	}

    public BufferedImage resizeImage(BufferedImage before, double scale, int af) {
        int w = before.getWidth();
        int h = before.getHeight();
        int w2 = (int) (w * scale);
        int h2 = (int) (h * scale);
        BufferedImage after = new BufferedImage(w2, h2, before.getType());
        AffineTransform scaleInstance = AffineTransform.getScaleInstance(scale, scale);
        AffineTransformOp scaleOp = new AffineTransformOp(scaleInstance, af);
        scaleOp.filter(before, after);

        return after;
    }

    public JButton imageButton(BufferedImage image) {
        JButton b = new JButton();
        b.setIcon(new ImageIcon(image));
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);

        return b;
    }

    public void loadRTList() {
        //TODO: support for a custom file so that it can be added to onedrive for example.
        coxRTList = fw.readFromFile(RaidType.COX);
        for (RaidTracker RT : coxRTList) {
            coxUUIDMap.put(RT.getUniqueID(), RT);
        }

        tobRTList = fw.readFromFile(RaidType.TOB);

        for (RaidTracker RT : tobRTList) {
            tobUUIDMap.put(RT.getUniqueID(), RT);
        }

		toaRTList = fw.readFromFile(RaidType.TOA);

		for (RaidTracker RT : toaRTList) {
			toaUUIDMap.put(RT.getUniqueID(), RT);
		}

        loaded = true;
        updateView();
    }

    public ArrayList<RaidTracker> filterRTListByName(String name) {
        if (loaded) {
            return getFilteredRTList().stream().filter(RT -> name.toLowerCase().equals(RT.getSpecialLoot().toLowerCase()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public ArrayList<RaidTracker> filterKitReceivers() {
        if (loaded) {
            return getFilteredRTList().stream().filter(RT -> !RT.getKitReceiver().isEmpty())
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public ArrayList<RaidTracker> filterDustReceivers() {
        if (loaded) {
            return getFilteredRTList().stream().filter(RT -> !RT.getDustReceiver().isEmpty()).collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public ArrayList<RaidTracker> filterPetReceivers() {
        if (loaded) {
            return getFilteredRTList().stream().filter(RT -> !RT.getPetReceiver().isEmpty()).collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public ArrayList<RaidTracker> filterOwnDrops(ArrayList<RaidTracker> l) {
        if (loaded) {
            return l.stream().filter(RT -> {

                if (RT.getSpecialLoot().isEmpty() || RT.getLootList().size() == 0) {
                    return false;
                }
                return RT.getLootList().get(0).getId() == getByName(RT.getSpecialLoot()).getItemID();
            }).collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public ArrayList<RaidTracker> filterOwnKits(ArrayList<RaidTracker> l) {
        if (loaded) {
            return l.stream().filter(RT -> RT.getLootList().stream()
                    .anyMatch(loot -> loot.getId() == ItemID.TWISTED_ANCESTRAL_COLOUR_KIT))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public ArrayList<RaidTracker> filterOwnDusts(ArrayList<RaidTracker> l) {
        if (loaded) {

            return l.stream().filter(RT -> RT.getLootList().stream()
                    .anyMatch(loot -> loot.getId() == ItemID.METAMORPHIC_DUST))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public ArrayList<RaidTracker> filterOwnPets(ArrayList<RaidTracker> l) {
        if (loaded) {
            return l.stream().filter(RaidTracker::isPetInMyName).collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public ArrayList<RaidTracker> filterPurples() {
        if (loaded) {
            return getFilteredRTList().stream().filter(RT -> {
                for (RaidUniques unique : getUniquesList()) {
                    if (unique.getName().toLowerCase().equals(RT.getSpecialLoot().toLowerCase())) {
                        return true;
                    }
                }
                return false;
            }).collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();

    }

    public String getUniqueToolTip(RaidUniques unique, int amountSeen, int amountReceived) {

        return "<html>" +
                unique.getName() +  "<br>" +
                "Received: " + amountReceived + "x" + "<br>" +
                "Seen: " + amountSeen + "x";
    }

    public String getRegularToolTip(RaidTrackerItem drop) {
        return "<html>" + drop.getName() + " x " + drop.getQuantity() + "<br>" +
                "Price: " + format(drop.getPrice()) + " gp";
    }

    public void addDrop(RaidTracker RT, boolean update) {
        if (RT.isInRaidChambers()) {
            coxRTList.add(RT);
        } else if (RT.isInTheatreOfBlood()) {
            tobRTList.add(RT);
        } else if (RT.isInTombsOfAmascut()) {
			toaRTList.add(RT);
		} else {
            log.error("New drop detected but no inRaid flags set.");
        }

        if (update) {
            //only add  item to the map when the parent raidtracker is added (child RT's are getting update false)
            if (RT.isInRaidChambers()) {
                coxUUIDMap.put(RT.getUniqueID(), RT);
            } else if (RT.isInTheatreOfBlood()) {
                tobUUIDMap.put(RT.getUniqueID(), RT);
            } else if (RT.isInTombsOfAmascut()) {
				toaUUIDMap.put(RT.getUniqueID(), RT);
			} else {
                log.error("Drop update detected but no inRaid flags set.");
            }
            updateView();
        }
    }

    public void addDrop(RaidTracker RT) {
        addDrop(RT, true);
    }

    public int atleastZero(int maybeLessThanZero) {
        return Math.max(maybeLessThanZero, 0);
    }

    //yoinked from stackoverflow
    private static final NavigableMap<Long, String> suffixes = new TreeMap<>();
    static {
        suffixes.put(1_000L, "k");
        suffixes.put(1_000_000L, "m");
        suffixes.put(1_000_000_000L, "b");
    }

    public static String format(long value) {
        //Long.MIN_VALUE == -Long.MIN_VALUE so we need an adjustment here
        if (value == Long.MIN_VALUE) return format(Long.MIN_VALUE + 1);
        if (value < 0) return "-" + format(-value);
        if (value < 1000) return Long.toString(value); //deal with easy case

        Map.Entry<Long, String> e = suffixes.floorEntry(value);
        Long divideBy = e.getKey();
        String suffix = e.getValue();

        long truncated = value / (divideBy / 100); //the number part of the output times 100
        boolean hasDecimal = truncated < 1000;
        return hasDecimal ? (truncated / 100d) + suffix : (truncated / 100) + suffix;
    }

    public Future<Map<Integer, RaidTrackerItem>> getDistinctRegularDrops()  {
        CompletableFuture<Map<Integer, RaidTrackerItem>> future = new CompletableFuture<>();

        clientThread.invokeLater(() -> {

            if (loaded) {
                HashSet<Integer> uniqueIDs = new HashSet<>();

                int clues = 0;

                for (RaidTracker RT : getFilteredRTList()) {
                    for (RaidTrackerItem item : RT.getLootList()) {
                        boolean addToSet = true;
                        for (RaidUniques unique : getUniquesList()) {
                            if (item.getId() == unique.getItemID()) {
                                addToSet = false;
                                break;
                            }
                        }
                        if (item.getName().toLowerCase().contains("clue")) {
                            addToSet = false;
                            clues++;
                        }
                        if (addToSet) {
                            uniqueIDs.add(item.id);
                        }
                    }
                }

                Map<Integer, RaidTrackerItem> m = new HashMap<>();

                for (Integer i : uniqueIDs) {
                    ItemComposition IC = itemManager.getItemComposition(i);

                    m.put(i, new RaidTrackerItem() {
                        {
                            name = IC.getName();
                            id = i;
                            quantity = 0;
                            price = itemManager.getItemPrice(i);
                        }
                    });

                }

                if (clues > 0) {
                    int finalClues = clues;
                    m.put(12073, new RaidTrackerItem() {
                        {
                            name = "Clue scroll (elite)";
                            id = 12073;
                            quantity = finalClues;
                            price = itemManager.getItemPrice(12073);
                        }
                    });
                }

                future.complete(m);
                return;
            }

            future.complete(new HashMap<>());

        });
        return future;
    }

    private ArrayList<RaidTracker> getFilteredRTList() {
        ArrayList<RaidTracker> tempRTList;

        if (!loaded) {
            return new ArrayList<>();
        }

		switch(selectedRaidTab) {
            case COX:
                if (cmFilter.equals("CM & Normal")) {
                    tempRTList = coxRTList;
                } else if (cmFilter.equals("CM Only")) {
                    tempRTList = coxRTList.stream().filter(RaidTracker::isChallengeMode)
                        .collect(Collectors.toCollection(ArrayList::new));
                } else {
                    tempRTList = coxRTList.stream().filter(RT -> !RT.isChallengeMode())
                        .collect(Collectors.toCollection(ArrayList::new));
                }
                break;
			case TOB:
				if (mvpFilter.equals("Both")) {
					tempRTList = tobRTList;
				} else if (mvpFilter.equals("My MVP")) {
					tempRTList = tobRTList.stream().filter(RaidTracker::isMvpInOwnName)
						.collect(Collectors.toCollection(ArrayList::new));
				} else {
					tempRTList = tobRTList.stream().filter(RT -> !RT.isMvpInOwnName())
						.collect(Collectors.toCollection(ArrayList::new));
				}
				break;
			case TOA:
				tempRTList = toaRTList.stream().filter(RT -> RT.getRaidLevel() >= raidLevelFilterLow && RT.getRaidLevel() <= raidLevelFilterHigh)
					.collect(Collectors.toCollection(ArrayList::new));
				break;
			default:
                tempRTList = new ArrayList<>();
                break;
		}

        switch (teamSizeFilter) {
            case "Solo":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() == 1))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            case "Duo":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() == 2))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            case "Trio":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() == 3))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            case "4-man":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() == 4))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            case "5-man":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() == 5))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            case "6-man":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() == 6))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            case "7-man":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() == 7))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
			case "8-man":
				tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() == 8))
					.collect(Collectors.toCollection(ArrayList::new));
				break;
            case "8-10 Players":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() >= 8 && RT.getTeamSize() <= 10))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            case "11-14 Players":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() >= 11 && RT.getTeamSize() <= 14))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            case "15-24 Players":
				tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() >= 15 && RT.getTeamSize() <= 24))
					.collect(Collectors.toCollection(ArrayList::new));
			case "24+ Players":
                tempRTList = tempRTList.stream().filter(RT -> (RT.getTeamSize() >= 25))
                        .collect(Collectors.toCollection(ArrayList::new));
                break;
            default:
                //all sizes

        }

        //if people want to crash my plugin using a system year of before 1970, that's fine
        long now = System.currentTimeMillis();


        long last12Hours = now - 43200000L;
        long yesterday = now - 86400000L;
        long last3Days = now - 259200000L;
        long lastWeek = now - 604800000L;
        long lastMonth = now - 2629746000L;
        long last3Months = now - 7889400000L;
        long lastYear = now - 31536000000L;

        switch (dateFilter) {
            case "All Time":
                return tempRTList;
			case "12 Hours":
				return tempRTList.stream().filter(RT -> RT.getDate() > last12Hours)
					.collect(Collectors.toCollection(ArrayList::new));
            case "Today":
                return tempRTList.stream().filter(RT -> RT.getDate() > yesterday)
                        .collect(Collectors.toCollection(ArrayList::new));
			case "3 Days":
				return tempRTList.stream().filter(RT -> RT.getDate() > last3Days)
					.collect(Collectors.toCollection(ArrayList::new));
            case "Week":
                return tempRTList.stream().filter(RT -> RT.getDate() > lastWeek)
                        .collect(Collectors.toCollection(ArrayList::new));
            case "Month":
                return tempRTList.stream().filter(RT -> RT.getDate() > lastMonth)
                        .collect(Collectors.toCollection(ArrayList::new));
			case "3 Months":
				return tempRTList.stream().filter(RT -> RT.getDate() > last3Months)
					.collect(Collectors.toCollection(ArrayList::new));
            case "Year":
                return tempRTList.stream().filter(RT -> RT.getDate() > lastYear)
                        .collect(Collectors.toCollection(ArrayList::new));
            case "X Kills":
                ArrayList<RaidTracker> tempUniqueKills = getDistinctKills(tempRTList);
                ArrayList<RaidTracker> uniqueKills = new ArrayList<>(tempUniqueKills.subList(Math.max(tempUniqueKills.size() - config.lastXKills(), 0), tempUniqueKills.size()));

                return tempRTList.stream().filter(RT -> uniqueKills.stream()
                        .anyMatch(temp -> RT.getKillCountID().equals(temp.getKillCountID())))
                        .collect(Collectors.toCollection(ArrayList::new));
        }

        return tempRTList;
    }

    public EnumSet<RaidUniques> getUniquesList() {
		switch(selectedRaidTab) {
            case COX:
                return coxUniques;
			case TOB:
				return tobUniques;
			case TOA:
				return toaUniques;
			default:
                return null;
		}
	}

    public RaidUniques getByName(String name) {
        EnumSet<RaidUniques> uniquesList = getUniquesList();
        for (RaidUniques unique: uniquesList) {
            if (unique.getName().toLowerCase().equals(name.toLowerCase())) {
                return unique;
            }
        }
        //should never reach this
        return RaidUniques.OLMLET;
    }

    public ArrayList<RaidTracker> getDistinctKills(ArrayList<RaidTracker> tempRTList) {
        HashMap<String, RaidTracker> tempUUIDMap = new LinkedHashMap<>();

        for (RaidTracker RT : tempRTList) {
            tempUUIDMap.put(RT.getKillCountID(), RT);
        }

        return new ArrayList<>(tempUUIDMap.values());
    }

    private void clearData()
    {
        // Confirm delete action
        final int delete = JOptionPane.showConfirmDialog(this.getRootPane(), "<html>Are you sure you want to clear all data for this tab?<br/>There is no way to undo this action.</html>", "Warning", JOptionPane.YES_NO_OPTION);
        if (delete == JOptionPane.YES_OPTION)
        {
            if (!fw.delete(selectedRaidTab))
            {
                JOptionPane.showMessageDialog(this.getRootPane(), "Unable to clear stored data, please try again.");
                return;
            }

            loadRTList();
        }
    }

    private String secondsToMinuteString(int seconds) {
        if (seconds < 0) {
            return "No time";
        }
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

}

