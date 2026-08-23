package com.raidtracker;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.raidtracker.filereadwriter.FileReadWriter;
import com.raidtracker.ui.RaidTrackerPanel;
import com.raidtracker.ui.RaidUniques;
import junit.framework.TestCase;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestRaidTrackerPanel extends TestCase
{
    @Mock
    @Bind
    private Client client;

    @Inject
    private FileReadWriter fw;

    @Before
    public void setUp()
    {
        Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    }

    @Test
    public void TestFilter() throws ExecutionException, InterruptedException {
        fw.updateUsername("Canvasba");

        ArrayList<RaidTracker> l = fw.readFromFile();

        assertEquals(9, l.size());
        assertEquals("Adamantite ore", l.get(0).getLootList().get(0).getName());

        RaidTrackerPanel panel = mock(RaidTrackerPanel.class, CALLS_REAL_METHODS);
        panel.setLoaded(true);
        panel.setCoxRTList(l);
        panel.setCmFilter("CM & Normal");
        panel.setDateFilter("All Time");
        panel.setMvpFilter("Both");
        panel.setTeamSizeFilter("All sizes");
		panel.setSelectedRaidTab(RaidType.COX);

        when(panel.getUniquesList()).thenReturn(RaidUniques.COX_UNIQUES);

        ItemManager IM = mock(ItemManager.class);

        panel.setItemManager(IM);

        ArrayList<RaidTracker> arcanes = panel.filterRTListByName("Arcane Prayer Scroll");
        ArrayList<RaidTracker> dexes = panel.filterRTListByName("Dexterous Prayer Scroll");
        ArrayList<RaidTracker> dusts = panel.filterDustReceivers();
        ArrayList<RaidTracker> kits = panel.filterKitReceivers();
        ArrayList<RaidTracker> pets = panel.filterPetReceivers();
        ArrayList<RaidTracker> ownArcanes = panel.filterOwnDrops(arcanes);
        ArrayList<RaidTracker> ownDexes = panel.filterOwnDrops(dexes);
        ArrayList<RaidTracker> ownDusts = panel.filterOwnDusts(dusts);
        ArrayList<RaidTracker> ownKits = panel.filterOwnKits(kits);
        ArrayList<RaidTracker> ownPets = panel.filterOwnPets(pets);


        assertEquals(2, arcanes.size());
        assertEquals(1, ownArcanes.size());
        assertEquals(1, dexes.size());
        assertEquals(0, ownDexes.size());
        assertEquals(2, dusts.size());
        assertEquals(1, ownDusts.size());
        assertEquals(4, kits.size());
        assertEquals(2, ownKits.size());
        assertEquals(4, pets.size());
        assertEquals(2, ownPets.size());

        assertEquals(4, panel.getDistinctKills(l).size());

    }
}
