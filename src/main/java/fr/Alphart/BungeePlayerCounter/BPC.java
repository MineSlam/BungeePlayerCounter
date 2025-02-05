package fr.Alphart.BungeePlayerCounter;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import fr.Alphart.BungeePlayerCounter.PluginMessage.PluginMessageWriter;
import fr.Alphart.BungeePlayerCounter.Servers.ServerCoordinator;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Objects;
import java.util.logging.*;

@Getter
public class BPC extends JavaPlugin {
    private static final String prefix = clr("&4[&aBPC&4]&e");
    @Getter
    private static BPC instance;
    private ServerCoordinator serverCoordinator;
    private PluginMessageWriter pmWriter;
    private Configuration conf;
    private ScoreboardHandler scoreboardHandler;
    private boolean debugMode = false;

    public void onEnable() {
        BPC.instance = this;

        conf = new Configuration(getConfig());
        pmWriter = new PluginMessageWriter(conf.getPluginMessageChannel());
        serverCoordinator = new ServerCoordinator();
        scoreboardHandler = new ScoreboardHandler(serverCoordinator);

        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
        Objects.requireNonNull(getCommand("bpc")).setExecutor(new BaseCommands());
    }

    public void onDisable() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this);
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        BPC.instance = null;
    }

    public void reload() {
        onDisable();
        reloadConfig();
        onEnable();
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            final Player sender = Bukkit.getOnlinePlayers().iterator().next();
            if (serverCoordinator.getCurrentServer().isEmpty()) {
                BPC.getInstance().getPmWriter().sendGetCurrentServerMessage(sender);
            }
            if (serverCoordinator.getServerGroups().size() <= 1) {
                BPC.getInstance().getPmWriter().sendGetServersListMessage(sender);
            }
        }
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    public boolean toggleDebug() {
        if (debugMode) {
            for (final Handler handler : getLogger().getHandlers()) {
                getLogger().removeHandler(handler);
            }

            getLogger().setLevel(Level.INFO);
            getLogger().setUseParentHandlers(true);
            getLogger().info("The debug mode is now disabled ! Log are available in the debug.log file located in BPC folder");

        } else {
            try {
                final File debugFile = new File(getDataFolder(), "debug.log");
                if (debugFile.exists()) {
                    debugFile.delete();
                }

                // Write header into debug log
                Files.asCharSink(debugFile, Charsets.UTF_8).writeLines(Arrays.asList("BPC log debug file"
                                + " - If you have an error with BPC, you should post this file on BPC topic on spigotmc",
                        "Bukkit build : " + Bukkit.getVersion(),
                        "BPC version : " + getDescription().getVersion(),
                        "Operating System : " + System.getProperty("os.name"),
                        "------------------------------------------------------------"));

                final FileHandler handler = getFileHandler(debugFile);

                getLogger().addHandler(handler);
                getLogger().setLevel(Level.CONFIG);
                getLogger().info("The debug mode is now enabled ! Log are available in the debug.log file located in BPC folder");
                getLogger().setUseParentHandlers(false);

            } catch (final Exception e) {
                getLogger().log(Level.SEVERE, "An exception occurred during the initialization of debug logging file", e);
            }
        }
        return debugMode = !debugMode;
    }

    private static FileHandler getFileHandler(File debugFile) throws IOException {
        final FileHandler handler = new FileHandler(debugFile.getAbsolutePath(), true);
        handler.setFormatter(new Formatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

            @Override
            public String format(LogRecord record) {
                String pattern = "time [level] message\n";
                return pattern.replace("level", record.getLevel().getName())
                        .replace("message", record.getMessage())
                        .replace("[BungeePlayerCounter]", "")
                        .replace("time", sdf.format(Calendar.getInstance().getTime()));
            }
        });
        return handler;
    }

    public static String clr(final String message, final Object... args) {
        return ChatColor.translateAlternateColorCodes('&', String.format(message, args));
    }

    public static String __(final String message, final Object... args) {
        return prefix + ChatColor.translateAlternateColorCodes('&', String.format(message, args));
    }

    public static void debug(final String message, final Object... args) {
        getInstance().getLogger().log(Level.CONFIG, String.format(message, args));
    }

    public static void debug(final String message, final Throwable throwable) {
        getInstance().getLogger().log(Level.CONFIG, message, throwable);
    }

    public static void severe(final String message, final Throwable throwable) {
        getInstance().getLogger().log(Level.SEVERE, message + "Please report this :", throwable);
    }
}