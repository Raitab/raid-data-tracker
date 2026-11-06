package com.raidtracker;

import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.WorldType;

public final class WorldUtils {
    private WorldUtils() {}

    public static Boolean playerOnBetaWorld(Client client) {
        EnumSet<WorldType> types = worldTypes(client);
        return types != null && types.contains(WorldType.BETA_WORLD);
    }

    /**
     * Leagues, Deadman, beta, tournament, fresh-start, quest-speedrunning, legacy, and EoC are not logged.
     * An empty world-type set is not one of those worlds.
     */
    public static boolean playerOnUntrackedWorld(Client client) {
        EnumSet<WorldType> types = worldTypes(client);
        if (types == null || types.isEmpty()) {
            return false;
        }
        return types.contains(WorldType.SEASONAL)
            || types.contains(WorldType.DEADMAN)
            || types.contains(WorldType.BETA_WORLD)
            || types.contains(WorldType.TOURNAMENT_WORLD)
            || types.contains(WorldType.FRESH_START_WORLD)
            || types.contains(WorldType.QUEST_SPEEDRUNNING)
            || types.contains(WorldType.LEGACY_ONLY)
            || types.contains(WorldType.EOC_ONLY);
    }

    private static EnumSet<WorldType> worldTypes(Client client) {
        if (client == null) {
            return null;
        }
        return client.getWorldType();
    }
}
