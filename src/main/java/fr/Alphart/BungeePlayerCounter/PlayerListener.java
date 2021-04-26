package fr.Alphart.BungeePlayerCounter;

import fr.Alphart.BungeePlayerCounter.Servers.ServerCoordinator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent ev) {
        final ServerCoordinator serverCoordinator = BPC.getInstance().getServerCoordinator();
        if (serverCoordinator.getCurrentServer().isEmpty() || serverCoordinator.getServerGroups().size() <= 1) {
            Bukkit.getScheduler().runTaskLater(BPC.getInstance(), () -> {
                if (serverCoordinator.getCurrentServer().isEmpty()) {
                    BPC.getInstance().getPmWriter().sendGetCurrentServerMessage(ev.getPlayer());
                }

                if (serverCoordinator.getServerGroups().size() <= 1) {
                    BPC.getInstance().getPmWriter().sendGetServersListMessage(ev.getPlayer());
                }
            }, 2L);
        }

        final Player pl = ev.getPlayer();
        Bukkit.getScheduler().runTaskLater(BPC.getInstance(), () -> BPC.getInstance().getScoreboardHandler().sendScoreboard(pl), 2L);
    }

}