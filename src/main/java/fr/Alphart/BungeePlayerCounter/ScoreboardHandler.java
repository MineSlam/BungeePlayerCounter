package fr.Alphart.BungeePlayerCounter;

import fr.Alphart.BungeePlayerCounter.Servers.ServerCoordinator;
import fr.Alphart.BungeePlayerCounter.Servers.ServerGroup;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;


public class ScoreboardHandler {
    private final String MD_TOGGLE = "BPC_toggled";

    private final ServerCoordinator serverCoordinator;
    private Scoreboard SB;

    public ScoreboardHandler(final ServerCoordinator serverCoordinator) {
        this.serverCoordinator = serverCoordinator;
        initScoreboard();
        Bukkit.getScheduler().runTaskTimer(BPC.getInstance(), this::update, 20L, 20L * BPC.getInstance().getConf().getUpdateInterval());
    }

    @SuppressWarnings("deprecation")
    private void initScoreboard() {
        SB = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();

        // Clean the scoreboard a bit ...
        for (final String entries : SB.getEntries()) {
            SB.resetScores(entries);
        }

        if (SB.getObjective("playercounter") != null) {
            Objects.requireNonNull(SB.getObjective("playercounter")).unregister();
        }

        for (final String teamName : Arrays.asList("online", "offline", "defaultbpc")) {
            if (SB.getTeam(teamName) != null) {
                Objects.requireNonNull(SB.getTeam(teamName)).unregister();
            }
        }

        final Objective objective = SB.registerNewObjective("playercounter", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        final String offlinePrefix = BPC.getInstance().getConf().getOfflinePrefix();

        if (!offlinePrefix.isEmpty()) {
            SB.registerNewTeam("offline").setPrefix(offlinePrefix);
            Objects.requireNonNull(SB.getTeam("offline")).setSuffix(ChatColor.RED + ":");
        }

        final String onlinePrefix = BPC.getInstance().getConf().getOnlinePrefix();
        if (!onlinePrefix.isEmpty()) {
            SB.registerNewTeam("online").setPrefix(onlinePrefix);
            Objects.requireNonNull(SB.getTeam("online")).setSuffix(ChatColor.RED + ":");
        }

        SB.registerNewTeam("defaultbpc").setSuffix(ChatColor.RED + ":");

    }

    public void toggleScoreboard(final Player player) {
        // Toggle on
        if (player.hasMetadata(MD_TOGGLE)) {
            player.removeMetadata(MD_TOGGLE, BPC.getInstance());
            player.setScoreboard(SB);
        }

        // Toggle off
        else {
            player.setMetadata(MD_TOGGLE, new FixedMetadataValue(BPC.getInstance(), "off"));
            player.setScoreboard(Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard());
        }
    }

    public void update() {
        String objectiveName = BPC.getInstance().getConf().getNetworkName()
                .replace("%totalplayercount%", String.valueOf(serverCoordinator.getGlobalPlayerCount()))
                .replace("%maxplayers%", String.valueOf(serverCoordinator.getBungeeMaxPlayer()));

        if (objectiveName.length() > 32) {
            objectiveName = objectiveName.substring(0, 32);
        }

        Objects.requireNonNull(SB.getObjective(DisplaySlot.SIDEBAR)).setDisplayName(objectiveName);

        final Objective obj = SB.getObjective(DisplaySlot.SIDEBAR);
        for (final ServerGroup group : serverCoordinator.getServerGroups()) {

            if (group.getDisplayName().equals(ServerCoordinator.globalGroupName)) {
                continue;
            }

            if (SB.getEntries().size() < 14) { // Sidebar cannot contains more than 14 entries
                String line = group.getDisplayName();

                int playerCount = group.getPlayerCount();
                if (group.doesContainCurrentServer() && BPC.getInstance().getConf().isServerIndicatorEnabled()) {
                    SB.resetScores(group.getDisplayName()); // Remove eventually old entry
                    line = BPC.getInstance().getConf().getServerIndicator() + line;
                }

                if (line.length() > 16) {
                    line = line.substring(0, 16);
                }

                assert obj != null;

                // Apply score to scoreboard
                if (playerCount == 0) { // For null score, the scoreboard may be buggy, so we set an "intermediate" value
                    obj.getScore(line).setScore(-1);
                }

                obj.getScore(line).setScore(playerCount);

                // Set server status in the SB otherwise assign to a default team to use the suffix
                if (group.isAddressSet()) {
                    if (group.isOnline()) {
                        if (SB.getTeam("offline") != null) {
                            Objects.requireNonNull(SB.getTeam("offline")).removeEntry(line);
                        }

                        if (SB.getTeam("online") != null) {
                            Objects.requireNonNull(SB.getTeam("online")).addEntry(line);
                        }

                    } else {
                        if (SB.getTeam("offline") != null) {
                            Objects.requireNonNull(SB.getTeam("offline")).addEntry(line);
                        }

                        if (SB.getTeam("online") != null) {
                            Objects.requireNonNull(SB.getTeam("online")).removeEntry(line);
                        }
                    }

                } else {
                    Objects.requireNonNull(SB.getTeam("defaultbpc")).addEntry(line);
                }
            }
        }

        sendScoreboardToPlayers(Bukkit.getOnlinePlayers());
    }

    /**
     * Send the BPC scoreboard to this player
     */
    public void sendScoreboard(final Player player) {
        sendScoreboardToPlayers(Collections.singletonList(player));
    }

    private void sendScoreboardToPlayers(final Collection<? extends Player> playersToUpdateSB) {
        for (final Player p : playersToUpdateSB) {
            if (p.hasPermission(BaseCommands.DISPLAY_PERM) && !p.hasMetadata(MD_TOGGLE)) {
                p.setScoreboard(SB);
            }
        }
    }


}