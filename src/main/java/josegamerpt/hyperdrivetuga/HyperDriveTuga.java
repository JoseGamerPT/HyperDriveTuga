package josegamerpt.hyperdrivetuga;

import josegamerpt.hyperdrivetuga.api.EnumContainer;
import josegamerpt.hyperdrivetuga.api.Manager;
import josegamerpt.hyperdrivetuga.api.TeleportationHandler;
import josegamerpt.hyperdrivetuga.api.objects.SerializableLocation;
import josegamerpt.hyperdrivetuga.api.objects.Warp;
import josegamerpt.hyperdrivetuga.core.internals.BungeeListener;
import josegamerpt.hyperdrivetuga.core.internals.Listeners;
import josegamerpt.hyperdrivetuga.core.internals.cmds.MainCommands;
import josegamerpt.hyperdrivetuga.core.internals.cmds.TeleportationCommands;
import josegamerpt.hyperdrivetuga.core.internals.hooks.HookChecker;
import josegamerpt.hyperdrivetuga.core.internals.hooks.PapiHook;
import josegamerpt.hyperdrivetuga.core.internals.hooks.VaultHandler;
import josegamerpt.hyperdrivetuga.core.internals.hooks.WorldGuardHandler;
import josegamerpt.hyperdrivetuga.core.internals.tabs.WarpTabComplete;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class HyperDriveTuga extends JavaPlugin {

    private static HyperDriveTuga pluginInstance;
    private TeleportationHandler teleportationHandler;
    private MainCommands mainCommands;
    private TeleportationCommands teleportationCommands;
    private Manager manager;
    private BungeeListener bungeeListener;

    private Connection databaseConnection;
    private List<String> databaseWarps;

    private String serverVersion, tableName;
    private boolean asyncChunkMethodExists;
    private int teleportationHandlerTaskId, autoSaveTaskId, crossServerTaskId;

    private FileConfiguration langConfig, menusConfig;
    private File langFile, menusFile;

    private VaultHandler vaultHandler;
    private WorldGuardHandler worldGuardHandler;
    private HookChecker hookChecker;
    private PapiHook papiHook;

    @Override
    public void onLoad() {
        // Wrapped in try/catch to bypass an issue involving world guard custom flag registration when re-loaded live.
        try {
            Plugin worldGuard = getServer().getPluginManager().getPlugin("WorldGuard");
            if (worldGuard != null) setWorldGuardHandler(new WorldGuardHandler());
        } catch (Exception ignored) {}
    }

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        setPluginInstance(this);
        setServerVersion(getPluginInstance().getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3]);
        tableName = "create table if not exists warps (name varchar(100), location varchar(255), status varchar(100), creation_date varchar(100),"
                + " icon_theme varchar(100), animation_set varchar(100), description varchar(255), commands varchar(255), owner varchar(100), player_list varchar(255), "
                + "assistants varchar(255), traffic int, usage_price double, enchanted_look int, server_ip varchar(255), likes int, dislikes int, voters longtext, "
                + "white_list_mode int, notify int, primary key (name))";

        // duplicates old configuration.
        File file = new File(getDataFolder(), "/config.yml");
        if (file.exists()) {
            FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection cs = yaml.getConfigurationSection("");
            if (cs != null && cs.contains("language-section"))
                file.renameTo(new File(getDataFolder(), "/old-config.yml"));
        }

        // creates new configurations or loads them.
        saveDefaultConfigs();
        updateConfigs();

        setAsyncChunkMethodExists(false);
        for (Method method : World.class.getMethods())
            if (method.getName().equals("getChunkAtAsync") && method.getParameterCount() == 3) {
                setAsyncChunkMethodExists(true);
                break;
            }

        if (!asyncChunkMethodExists()) {
            log(Level.WARNING, "Async chunk retrieving methods were not found. Use Paper Spigot to resolve this issue.");
        } else log(Level.INFO, "Async chunk retrieving methods were found!");

        if (getConfig().getBoolean("general-section.use-vault")) setVaultHandler(new VaultHandler(getPluginInstance()));
        setHookChecker(new HookChecker(this));

        File warpsFile = new File(getDataFolder(), "/warps.db"), backupFile = new File(getDataFolder(), "/warps-backup.db");
        if (warpsFile.exists()) {
            if (backupFile.exists()) backupFile.delete();

            try {
                copy(warpsFile, backupFile);
            } catch (IOException e) {
                e.printStackTrace();
                log(Level.WARNING, "Unable to backup the warp file as it may not exist. (Took " + (System.currentTimeMillis() - startTime) + "ms)");
            }
        }

        setDatabaseWarps(new ArrayList<>());
        long databaseStartTime = System.currentTimeMillis();
        setupDatabase(getConfig().getBoolean("mysql-connection.use-mysql"));
        if (getDatabaseConnection() != null)
            log(Level.INFO, "Communication to the database was successful. (Took " + (System.currentTimeMillis() - databaseStartTime) + "ms)");
        else {
            log(Level.WARNING, "Communication to the database failed. (Took " + (System.currentTimeMillis() - databaseStartTime) + "ms)");
            getPluginInstance().getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getConfig().getBoolean("mysql-connection.use-mysql")) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            setBungeeListener(new BungeeListener(this));
            getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", getBungeeListener());
        }

        // sets up the manager class including all API methods.
        setManager(new Manager(this));

        // sets up all commands and their counterparts.
        setMainCommands(new MainCommands(this));
        final WarpTabComplete tabCompletion = new WarpTabComplete(this);

        String[] commandNames = {"warps", "hyperdrive"};
        for (String cmd : commandNames) {
            PluginCommand command = getCommand(cmd);
            if (command != null) {
                command.setExecutor(getMainCommands());
                if (cmd.equalsIgnoreCase("warps")) command.setTabCompleter(tabCompletion);
            }
        }

        setTeleportationCommands(new TeleportationCommands(this));
        String[] teleportCommandNames = {"teleport", "teleporthere", "teleportoverride", "teleportoverridehere", "teleportposition", "teleportask", "teleportaccept",
                "teleportdeny", "teleporttoggle", "back", "teleportaskhere", "crossserver", "spawn", "randomteleport", "randomteleportadmin", "grouprandomteleport"};
        for (String cmd : teleportCommandNames) {
            PluginCommand command = getCommand(cmd);
            if (command != null) {
                command.setExecutor(getTeleportationCommands());
                command.setTabCompleter(tabCompletion);
            }
        }

        // registers events
        getServer().getPluginManager().registerEvents(new Listeners(getPluginInstance()), getPluginInstance());

        // loads warps and runs conversion process.
        runConverter();
        loadWarps();
        startEssentialsConverter();

        if (getConfig().getBoolean("general-section.warp-cleaner")) {
            final List<Warp> warpList = new ArrayList<>(getManager().getWarpMap().values());
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                for (Warp warp : warpList) {
                    if (getManager().getWarpMap().containsKey(warp.getWarpName()) && warp.getStatus() != EnumContainer.Status.ADMIN)
                        try {
                            if (warp.getOwner() == null) continue;
                            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(warp.getOwner());
                            if (!offlinePlayer.hasPlayedBefore()) continue;

                            final long lastTimeSeen = (System.currentTimeMillis() - offlinePlayer.getLastPlayed()) / 1000;
                            if (lastTimeSeen >= getConfig().getInt("general-section.warp-clean-time")) {
                                warp.unRegister();
                                warp.deleteSaved(false);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            log(Level.WARNING, "There was an issue deleting the warp '" + warp.getWarpName() + "' due to inactivity.");
                        }
                }
            });
        }

        // starts the runnables/tasks.
        startTasks();


        Plugin papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null) {
            setPapiHook(new PapiHook(this));
            getPapiHook().register();
        }

        log(Level.INFO, "Everything is set and ready to go! (Took " + (System.currentTimeMillis() - startTime) + "ms)");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);

        saveWarps(false);
        saveData();

        if (getDatabaseConnection() != null)
            try {
                getDatabaseConnection().close();
                log(Level.WARNING, "The SQL connection has been completely closed.");
            } catch (SQLException e) {
                e.printStackTrace();
                log(Level.WARNING, "The MySQL connection was unable to be closed.");
            }
    }

    // configuration methods
    private void updateConfigs() {
        long startTime = System.currentTimeMillis();
        int totalUpdates = 0;

        boolean isOffhandVersion = !getPluginInstance().getServerVersion().startsWith("v1_8");
        String[] configNames = {"config", "lang", "menus"};
        for (int i = -1; ++i < configNames.length; ) {
            String name = configNames[i];

            InputStream inputStream = getClass().getResourceAsStream("/" + name + ".yml");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            FileConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            int updateCount = updateKeys(yaml, name.equalsIgnoreCase("config") ? getConfig() : name.equalsIgnoreCase("lang") ? getLangConfig() : getMenusConfig());
            if (name.equalsIgnoreCase("config")) {
                String teleporationSound = getConfig().getString("general-section.global-sounds.teleport"), standaloneTeleporationSound = getConfig().getString("general-section.global-sounds.teleport");
                if (isOffhandVersion) {
                    if (teleporationSound == null || teleporationSound.equalsIgnoreCase("ENDERMAN_TELEPORT")) {

                        if (getServerVersion().startsWith("v1_12") || getServerVersion().startsWith("v1_11") || getServerVersion().startsWith("v1_10") || getServerVersion().startsWith("v1_9"))
                            getConfig().set("general-section.global-sounds.teleport", "ENTITY_ENDERMEN_TELEPORT");
                        else getConfig().set("general-section.global-sounds.teleport", "ENTITY_ENDERMAN_TELEPORT");
                        updateCount++;
                    }

                    if (standaloneTeleporationSound == null || standaloneTeleporationSound.equalsIgnoreCase("ENDERMAN_TELEPORT")) {

                        if (getServerVersion().startsWith("v1_12") || getServerVersion().startsWith("v1_11") || getServerVersion().startsWith("v1_10") || getServerVersion().startsWith("v1_9"))
                            getConfig().set("general-section.global-sounds.teleport", "ENTITY_ENDERMEN_TELEPORT");
                        else getConfig().set("general-section.global-sounds.teleport", "ENTITY_ENDERMAN_TELEPORT");
                        updateCount++;
                    }

                    String warpIconClickSound = getConfig().getString("warp-icon-section.click-sound");
                    if (warpIconClickSound == null || warpIconClickSound.equalsIgnoreCase("CLICK")) {
                        getConfig().set("warp-icon-section.click-sound", "UI_BUTTON_CLICK");
                        updateCount++;
                    }
                } else {
                    if (teleporationSound == null || teleporationSound.equalsIgnoreCase("ENTITY_ENDERMAN_TELEPORT") || teleporationSound.equalsIgnoreCase("ENTITY_ENDERMEN_TELEPORT")) {
                        getConfig().set("general-section.global-sounds.teleport", "ENDERMAN_TELEPORT");
                        updateCount++;
                    }

                    if (standaloneTeleporationSound == null || standaloneTeleporationSound.equalsIgnoreCase("ENTITY_ENDERMAN_TELEPORT") || standaloneTeleporationSound.equalsIgnoreCase("ENTITY_ENDERMEN_TELEPORT")) {
                        getConfig().set("general-section.global-sounds.teleport", "ENDERMAN_TELEPORT");
                        updateCount++;
                    }

                    String warpIconClickSound = getConfig().getString("warp-icon-section.click-sound");
                    if (warpIconClickSound == null || warpIconClickSound.equalsIgnoreCase("UI_BUTTON_CLICK")) {
                        getConfig().set("warp-icon-section.click-sound", "CLICK");
                        updateCount++;
                    }
                }
            }

            try {
                inputStream.close();
                reader.close();
            } catch (IOException e) {
                log(Level.WARNING, e.getMessage());
            }

            updateCount = fixItems(name.equalsIgnoreCase("config") ? getConfig() : name.equalsIgnoreCase("lang")
                    ? getLangConfig() : getMenusConfig(), updateCount, isOffhandVersion);

            if (updateCount > 0)
                switch (name) {
                    case "config":
                        saveConfig();
                        break;
                    case "menus":
                        saveMenusConfig();
                        break;
                    case "lang":
                        saveLangConfig();
                        break;
                    default:
                        break;
                }

            if (updateCount > 0) {
                totalUpdates += updateCount;
                log(Level.INFO, updateCount + " things were fixed, updated, or removed in the '" + name + ".yml' configuration file. (Took " + (System.currentTimeMillis() - startTime) + "ms)");
            }
        }

        if (totalUpdates > 0) {
            reloadConfigs();
            log(Level.INFO, "A total of " + totalUpdates + " thing(s) were fixed, updated, or removed from all the configuration together. (Took " + (System.currentTimeMillis() - startTime) + "ms)");
            log(Level.WARNING, "Please go checkout the configuration files as they are no longer the same as their default counterparts.");
        } else
            log(Level.INFO, "Everything inside the configuration seems to be up to date. (Took " + (System.currentTimeMillis() - startTime) + "ms)");
    }

    private int fixItems(FileConfiguration yaml, int updateCount, boolean isOffhandVersion) {
        ConfigurationSection configurationSection = yaml.getConfigurationSection("");
        if (configurationSection == null) return updateCount;

        for (String key : configurationSection.getKeys(true)) {
            if (key.toLowerCase().endsWith(".material")) {
                String keyValue = yaml.getString(key);
                if (keyValue == null || keyValue.equalsIgnoreCase("")) {
                    yaml.set(key, "ARROW");
                    updateCount++;
                }

                if (keyValue != null)
                    switch (keyValue.toUpperCase().replace(" ", "_").replace("-", "_")) {
                        case "INK_SAC":
                            if (getServerVersion().startsWith("v1_14") || getServerVersion().startsWith("v1_15") || getServerVersion().startsWith("v1_16")) {
                                yaml.set(key, "RED_DYE");
                                updateCount++;

                            } else if (getServerVersion().startsWith("v1_13")) {
                                yaml.set(key, "ROSE_RED");
                                updateCount++;

                            } else if (getServerVersion().startsWith("v1_12") || getServerVersion().startsWith("v1_9")
                                    || getServerVersion().startsWith("v1_11") || getServerVersion().startsWith("v1_10")) {
                                yaml.set(key, "INK_SACK");
                                updateCount++;

                            }
                            break;
                        case "INK_SACK":
                            if (!getServerVersion().startsWith("v1_12") && !getServerVersion().startsWith("v1_9") && !getServerVersion().startsWith("v1_11") && !getServerVersion().startsWith("v1_10")
                                    && !getServerVersion().startsWith("v1_13") && !getServerVersion().startsWith("v1_14") && !getServerVersion().startsWith("v1_15") && !getServerVersion().startsWith("v1_16")) {
                                yaml.set(key, "INK_SAC");
                                updateCount++;
                            }
                            break;
                        case "ROSE_RED":
                            if (getServerVersion().startsWith("v1_14") || getServerVersion().startsWith("v1_15") || getServerVersion().startsWith("v1_16")) {
                                yaml.set(key, "RED_DYE");
                                updateCount++;
                            } else if (!getServerVersion().startsWith("v1_13")) {
                                yaml.set(key, "INK_SACK");
                                updateCount++;
                            }
                            break;
                        case "RED_DYE":
                            if (getServerVersion().startsWith("v1_13")) {
                                yaml.set(key, "ROSE_RED");
                                updateCount++;
                            } else if (!getServerVersion().startsWith("v1_14") && !getServerVersion().startsWith("v1_15") && !getServerVersion().startsWith("v1_16")) {
                                yaml.set(key, "INK_SACK");
                                updateCount++;
                            }

                            break;
                        case "CLOCK":
                            if (!getServerVersion().startsWith("v1_16") && !getServerVersion().startsWith("v1_15") && !getServerVersion().startsWith("v1_14")
                                    && !getServerVersion().startsWith("v1_13")) {
                                yaml.set(key, "WATCH");
                                updateCount++;
                            }
                            break;
                        case "WATCH":
                            if (getServerVersion().startsWith("v1_16") || getServerVersion().startsWith("v1_15") || getServerVersion().startsWith("v1_14")
                                    || getServerVersion().startsWith("v1_13")) {
                                yaml.set(key, "CLOCK");
                                updateCount++;
                            }
                            break;
                        case "BLACK_STAINED_GLASS_PANE":
                            if (!getServerVersion().startsWith("v1_16") && !getServerVersion().startsWith("v1_15") && !getServerVersion().startsWith("v1_14")
                                    && !getServerVersion().startsWith("v1_13")) {
                                yaml.set(key, "STAINED_GLASS_PANE");
                                yaml.set(key.replace(".material", ".durability"), 15);
                                updateCount++;
                            }
                            break;
                        case "STAINED_GLASS_PANE":
                            if (getServerVersion().startsWith("v1_16") || getServerVersion().startsWith("v1_15") || getServerVersion().startsWith("v1_14")
                                    || getServerVersion().startsWith("v1_13")) {
                                yaml.set(key, "BLACK_STAINED_GLASS_PANE");
                                updateCount++;
                            }
                            break;
                        case "OAK_SIGN":
                            if (!getServerVersion().startsWith("v1_16") && !getServerVersion().startsWith("v1_15") && !getServerVersion().startsWith("v1_14")) {
                                yaml.set(key, "SIGN");
                                updateCount++;
                            }
                            break;
                        case "SIGN":
                            if (getServerVersion().startsWith("v1_16") || getServerVersion().startsWith("v1_15") || getServerVersion().startsWith("v1_14")) {
                                yaml.set(key, "OAK_SIGN");
                                updateCount++;
                            }
                            break;
                        case "GREEN_WOOL":
                        case "LIME_WOOL":
                        case "RED_WOOL":
                            if (!getServerVersion().startsWith("v1_16") && !getServerVersion().startsWith("v1_15") && !getServerVersion().startsWith("v1_14") && !getServerVersion().startsWith("v1_13")) {
                                yaml.set(key, "WOOL");
                                updateCount++;
                            }
                            break;
                        case "GRASS_BLOCK":
                            if (!getServerVersion().startsWith("v1_16") && !getServerVersion().startsWith("v1_15") && !getServerVersion().startsWith("v1_14") && !getServerVersion().startsWith("v1_13")) {
                                yaml.set(key, "GRASS");
                                updateCount++;
                            }
                            break;
                        default:
                            break;
                    }
            } else if (key.toLowerCase().endsWith(".sound-name")) {
                String keyValue = getConfig().getString(key);
                if (keyValue == null) continue;

                keyValue = keyValue.toUpperCase().replace(" ", "_").replace("-", "_");
                if (keyValue.equalsIgnoreCase("") || ((isOffhandVersion && !keyValue.equalsIgnoreCase("CLICK"))
                        || (!isOffhandVersion && !keyValue.equalsIgnoreCase("UI_BUTTON_CLICK")))) {
                    yaml.set(key, isOffhandVersion ? "UI_BUTTON_CLICK" : "CLICK");
                    updateCount++;
                }
            }
        }

        return updateCount;
    }

    private int updateKeys(FileConfiguration jarYaml, FileConfiguration currentYaml) {
        int updateCount = 0;
        ConfigurationSection currentConfigurationSection = currentYaml.getConfigurationSection(""),
                latestConfigurationSection = jarYaml.getConfigurationSection("");
        if (currentConfigurationSection != null && latestConfigurationSection != null) {
            Set<String> newKeys = latestConfigurationSection.getKeys(true),
                    currentKeys = currentConfigurationSection.getKeys(true);
            for (String updatedKey : newKeys) {
                if (updatedKey.contains(".items") || updatedKey.contains("custom-menus-section"))
                    continue;
                if (!currentKeys.contains(updatedKey)) {
                    currentYaml.set(updatedKey, jarYaml.get(updatedKey));
                    updateCount++;
                }
            }

            for (String currentKey : currentKeys) {
                if (currentKey.contains(".items") || currentKey.contains("custom-menus-section"))
                    continue;
                if (!newKeys.contains(currentKey)) {
                    currentYaml.set(currentKey, null);
                    updateCount++;
                }
            }
        }

        return updateCount;
    }

    // core methods

    /**
     * Replaces PlaceholderAPI values in the text and returns it if possible.
     *
     * @param player The player to use.
     * @param text   The text to replace in.
     * @return The final result.
     */
    public String replacePapi(Player player, String text) {
        return (getPapiHook() != null) ? getPapiHook().replace(player, text) : text;
    }

    private void setupDatabase(boolean useMySQL) {
        if (getDatabaseConnection() != null) return;

        try {
            Statement statement;
            if (!useMySQL) {
                Class.forName("org.sqlite.JDBC");
                setDatabaseConnection(DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/warps.db"));
            } else {
                Class.forName("com.mysql.jdbc.Driver");

                String databaseName = getConfig().getString("mysql-connection.database-name"), host = getConfig().getString("mysql-connection.host"),
                        port = getConfig().getString("mysql-connection.port"), username = getConfig().getString("mysql-connection.username"),
                        password = getConfig().getString("mysql-connection.password"), properties = getPluginInstance().getConfig().getString("mysql-connection.sql-properties");

                setDatabaseConnection(DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + databaseName + properties, username, password));

                statement = getDatabaseConnection().createStatement();
                statement.executeUpdate("create database if not exists " + databaseName);
                statement.close();
                getDatabaseConnection().close();

                setDatabaseConnection(DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + databaseName + properties, username, password));
            }

            statement = getDatabaseConnection().createStatement();
            statement.executeUpdate(tableName);

            DatabaseMetaData md = getDatabaseConnection().getMetaData();
            ResultSet rs = md.getColumns(null, null, "warps", "notify");
            if (!rs.next()) statement.executeUpdate("alter table warps add column notify int");
            rs.close();

            if (useMySQL) {
                statement.executeUpdate("create table if not exists transfer (player_uuid varchar(100),location varchar(255), server_ip varchar(255),primary key (player_uuid))");
                statement.executeUpdate("truncate transfer");
            }

            statement.close();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            log(Level.WARNING, "There was an issue involving the MySQL connection.");
        }
    }

    private void runConverter() {
        int convertedWarpCount = 0, failedToConvertWarps = 0;
        long startTime = System.currentTimeMillis();

        File file = new File(getDataFolder(), "/warps.yml");
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection cs = yaml.getConfigurationSection("");
            if (cs == null) return;

            List<String> configurationLines = new ArrayList<>(cs.getKeys(false));
            for (int i = -1; ++i < configurationLines.size(); ) {
                try {
                    String warpName = configurationLines.get(i);

                    String id = yaml.getString(warpName + ".owner");
                    if (id == null || id.isEmpty()) continue;
                    UUID uuid = UUID.fromString(id);

                    SerializableLocation serializableLocation = new SerializableLocation(
                            yaml.getString(warpName + ".location.world"), yaml.getDouble(warpName + ".location.x"),
                            yaml.getDouble(warpName + ".location.y"), yaml.getDouble(warpName + ".location.z"),
                            (float) yaml.getDouble(warpName + ".location.yaw"),
                            (float) yaml.getDouble(warpName + ".location.pitch"));

                    Warp warp = new Warp(warpName, getPluginInstance().getServer().getOfflinePlayer(uuid), serializableLocation);
                    converterWarpSpecifics(yaml, warpName, warp);
                    warp.save(false);
                    convertedWarpCount++;
                } catch (Exception ignored) {
                    failedToConvertWarps++;
                }
            }

            file.renameTo(new File(getDataFolder(), "/converted-warps.yml"));
            log(Level.INFO, convertedWarpCount + " " + ((convertedWarpCount == 1) ? "warp was" : "warps were") + " converted and "
                    + failedToConvertWarps + " " + ((failedToConvertWarps == 1) ? "warp" : "warps") + " failed to convert. (Took " + (System.currentTimeMillis() - startTime) + "ms)");
        }
    }

    private void converterWarpSpecifics(FileConfiguration yaml, String warpName, Warp warp) {
        try {
            List<UUID> assistantList = new ArrayList<>(), playerList = new ArrayList<>(), voters = new ArrayList<>();
            List<String> assistants = yaml.getStringList(warpName + ".assistants"), playerListData = yaml.getStringList(warpName + ".player-list");
            for (int j = -1; ++j < assistants.size(); ) {
                UUID uniqueId = UUID.fromString(assistants.get(j));
                assistantList.add(uniqueId);
            }

            for (int j = -1; ++j < playerListData.size(); ) {
                UUID uniqueId = UUID.fromString(playerListData.get(j));
                playerList.add(uniqueId);
            }

            ConfigurationSection cs = yaml.getConfigurationSection(warpName);
            if (cs != null && cs.getKeys(false).contains("voters")) {
                List<String> voterList = yaml.getStringList(warpName + ".voters");
                for (int j = -1; ++j < voterList.size(); ) {
                    UUID uniqueId = UUID.fromString(voterList.get(j));
                    voters.add(uniqueId);
                }
            }

            String statusString = yaml.getString(warpName + ".status");
            if (statusString == null) statusString = EnumContainer.Status.PUBLIC.name();

            EnumContainer.Status status = EnumContainer.Status.valueOf(statusString.toUpperCase().replace(" ", "_").replace("-", "_"));
            warp.setStatus(status);
            warp.setAssistants(assistantList);
            warp.setPlayerList(playerList);
            warp.setCreationDate(yaml.getString(warpName + ".creation-date"));
            warp.setCommands(yaml.getStringList(warpName + ".commands"));
            warp.setAnimationSet(yaml.getString(warpName + ".animation-set"));
            warp.setIconTheme(Objects.requireNonNull(yaml.getString(warpName + ".icon.theme")).replace(":", ","));

            String descriptionColor = "";
            try {
                descriptionColor = yaml.getString(warpName + ".description-color");
                if (descriptionColor != null && !descriptionColor.isEmpty() && !descriptionColor.contains("§"))
                    if (descriptionColor.contains("&"))
                        descriptionColor = ChatColor.translateAlternateColorCodes('&', descriptionColor);
                    else descriptionColor = ChatColor.valueOf(descriptionColor).toString();
            } catch (Exception ignored) {}

            StringBuilder sb = new StringBuilder();
            List<String> description = yaml.getStringList(warpName + ".icon.description");
            for (String line : description)
                sb.append((descriptionColor == null || descriptionColor.isEmpty()) ? "" : descriptionColor).append(line).append(" ");
            warp.setDescription(getManager().colorText(sb.toString().trim().replaceAll("\\s+", " ").replace("'", "").replace("\"", "")));

            warp.setIconEnchantedLook(yaml.getBoolean(warpName + ".icon.use-enchanted-look"));
            warp.setUsagePrice(yaml.getDouble(warpName + ".icon.prices.usage"));
            warp.setTraffic(yaml.getInt(warpName + ".traffic"));
            warp.setVoters(voters);
            warp.setLikes(yaml.getInt(warpName + ".likes"));
            warp.setDislikes(yaml.getInt(warpName + ".dislikes"));
            warp.setWhiteListMode(yaml.getBoolean(warpName + ".white-list-mode"));
            warp.setServerIPAddress(Objects.requireNonNull(yaml.getString(warpName + ".server-ip")).replace("localhost", "127.0.0.1"));
        } catch (Exception e) {
            e.printStackTrace();
            log(Level.INFO, "There was an issue loading the warp " + warp.getWarpName() + "'s data aside it's location.");
        }
    }

    private void converterWarpSpecifics(ResultSet resultSet, String ipAddress, Warp warp) {
        try {
            String statusString = resultSet.getString("status");
            if (statusString == null) statusString = EnumContainer.Status.PUBLIC.name();

            EnumContainer.Status status = EnumContainer.Status.valueOf(statusString.toUpperCase().replace(" ", "_").replace("-", "_"));
            warp.setStatus(status);
            warp.setCreationDate(resultSet.getString("creation_date"));
            warp.setIconTheme(resultSet.getString("icon_theme").replace(":", ","));
            warp.setAnimationSet(resultSet.getString("animation_set"));


            String descriptionColor = "";
            try {
                descriptionColor = resultSet.getString("description_color");
                if (descriptionColor != null && !descriptionColor.isEmpty() && !descriptionColor.contains("§"))
                    if (descriptionColor.contains("&"))
                        descriptionColor = ChatColor.translateAlternateColorCodes('&', descriptionColor);
                    else descriptionColor = ChatColor.valueOf(descriptionColor).toString();
            } catch (SQLException ignored) {}

            String colorToAppend = (descriptionColor != null && !descriptionColor.isEmpty()) ? (descriptionColor.contains("§") ? descriptionColor : ChatColor.valueOf(descriptionColor)).toString() : "";
            StringBuilder sb = new StringBuilder();
            String[] description = resultSet.getString("description").split(" ");
            for (String line : description)
                sb.append((colorToAppend == null || colorToAppend.isEmpty()) ? "" : colorToAppend).append(line).append(" ");
            warp.setDescription(getManager().colorText(sb.toString().trim().replaceAll("\\s+", " ").replace("'", "").replace("\"", "")));


            String commandsString = resultSet.getString("commands");
            if (commandsString.contains(",")) {
                List<String> commands = new ArrayList<>();
                String[] commandsStringArgs = commandsString.split(",");
                for (int i = -1; ++i < commandsStringArgs.length; )
                    commands.add(commandsStringArgs[i]);
                warp.setCommands(commands);
            }

            if (resultSet.getString(11) != null && !resultSet.getString(11).equalsIgnoreCase(""))
                warp.setOwner(UUID.fromString(resultSet.getString("owner")));

            String playerListString = resultSet.getString("player_list");
            if (playerListString.contains(",")) {
                List<UUID> playerList = new ArrayList<>();
                String[] playerListStringArgs = playerListString.split(",");
                for (int i = -1; ++i < playerListStringArgs.length; )
                    playerList.add(UUID.fromString(playerListStringArgs[i]));
                warp.setPlayerList(playerList);
            }

            String assistantsString = resultSet.getString("assistants");
            if (assistantsString.contains(",")) {
                List<UUID> assistants = new ArrayList<>();
                String[] assistantsStringArgs = assistantsString.split(",");
                for (int i = -1; ++i < assistantsStringArgs.length; )
                    assistants.add(UUID.fromString(assistantsStringArgs[i]));
                warp.setAssistants(assistants);
            }

            String votersString = resultSet.getString("voters");
            if (votersString.contains(",")) {
                List<UUID> voters = new ArrayList<>();
                String[] votersStringArgs = votersString.split(",");
                for (int i = -1; ++i < votersStringArgs.length; ) voters.add(UUID.fromString(votersStringArgs[i]));
                warp.setVoters(voters);
            }

            warp.setTraffic(resultSet.getInt("traffic"));
            warp.setUsagePrice(resultSet.getDouble("usage_price"));
            warp.setIconEnchantedLook(resultSet.getInt("enchanted_look") >= 1);
            warp.setLikes(resultSet.getInt("likes"));
            warp.setDislikes(resultSet.getInt("dislikes"));
            warp.setServerIPAddress(ipAddress);
            warp.setWhiteListMode(resultSet.getInt("white_list_mode") >= 1);
            warp.setNotify(resultSet.getInt("notify") >= 1);
        } catch (Exception e) {
            e.printStackTrace();
            log(Level.INFO, "There was an issue loading the warp " + warp.getWarpName() + "'s data aside it's location.");
        }
    }

    private void startEssentialsConverter() {
        int convertedWarpCount = 0;
        long startTime = System.currentTimeMillis();

        File warpDirectory = new File(getDataFolder(), "warps");
        if (warpDirectory.exists()) {
            File[] files = warpDirectory.listFiles();
            if (files != null && files.length > 0) {
                for (int i = -1; ++i < files.length; ) {
                    File file = files[i];
                    if (file != null && file.getName().toLowerCase().endsWith(".yml")) {
                        FileConfiguration ymlFile = YamlConfiguration.loadConfiguration(file);

                        try {
                            String warpName = ymlFile.getString("Name");
                            if (getManager().getWarp(warpName) != null) continue;

                            double x = ymlFile.getDouble("Location.X"), y = ymlFile.getDouble("Location.Y"), z = ymlFile.getDouble("Location.Z");
                            float yaw = (float) ymlFile.getDouble("Location.Yaw"), pitch = (float) ymlFile.getDouble("Location.Pitch");

                            SerializableLocation serializableLocation = new SerializableLocation(ymlFile.getString("Location.World"), x, y, z, yaw, pitch);
                            Warp warp = new Warp(warpName, getServer().getOfflinePlayer(UUID.fromString(Objects.requireNonNull(ymlFile.getString("Main Owner")))), serializableLocation);

                            String statusName = ymlFile.getString("Status");
                            if (statusName != null)
                                warp.setStatus(statusName.equalsIgnoreCase("SHOP") ? EnumContainer.Status.PUBLIC : statusName.equalsIgnoreCase("SERVER") ? EnumContainer.Status.ADMIN
                                        : EnumContainer.Status.valueOf(statusName.toUpperCase().replace(" ", "_").replace("-", "_")));
                            warp.setCommands(ymlFile.getStringList("Commands"));
                            warp.setDescription(ymlFile.getStringList("Description").toString().replace("[", "").replace("]", "").replace(",", ""));
                            warp.setUsagePrice(ymlFile.getDouble("Usage Price"));
                            warp.setIconEnchantedLook(ymlFile.getBoolean("Enchanted Look"));
                            warp.setCreationDate(ymlFile.getString("Creation Date"));

                            List<String> commandList = new ArrayList<>();
                            commandList.add(ymlFile.getString("Command"));
                            warp.setCommands(commandList);

                            List<String> ownerList = ymlFile.getStringList("Owners");
                            for (int j = -1; ++j < ownerList.size(); ) {
                                UUID uniqueId = UUID.fromString(ownerList.get(j));
                                warp.getAssistants().add(uniqueId);
                            }

                            warp.register();
                            convertedWarpCount += 1;
                            log(Level.INFO, "The warp " + warp.getWarpName() + " has been successfully converted (Took " + (System.currentTimeMillis() - startTime) + "ms)");
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        if (convertedWarpCount > 0) {
            log(Level.INFO, "A total of " + convertedWarpCount + " old warp(s) were found and converted into new warp(s). " + "(Took " + (System.currentTimeMillis() - startTime) + "ms)");
            log(Level.INFO, "All old warp files are never deleted from the original location; therefore, nothing has been lost!");
        }

        if (getConfig().getBoolean("general-section.essentials-converter")) {
            convertedWarpCount = 0;
            final String name = getConfig().getString("general-section.essentials-name");
            File essentialsDirectory = new File(getDataFolder().getAbsolutePath().replace(getDescription().getName(), name != null ? name : "Essentials"));
            File[] essentialsFileList = essentialsDirectory.listFiles();
            if (essentialsFileList != null)
                for (int i = -1; ++i < essentialsFileList.length; ) {
                    File file = essentialsFileList[i];
                    if (file.isDirectory() && file.getName().equalsIgnoreCase("warps")) {
                        File[] essentialsWarpsList = file.listFiles();
                        if (essentialsWarpsList != null && essentialsWarpsList.length <= 0) {
                            log(Level.INFO, "There are no more warps that need to be converted.");
                            return;
                        }

                        if (essentialsWarpsList != null)
                            for (int j = -1; ++j < essentialsWarpsList.length; ) {
                                File warpFile = essentialsWarpsList[j];
                                if (!warpFile.isDirectory() && warpFile.getName().toLowerCase().endsWith(".yml")) {
                                    FileConfiguration ymlFile = YamlConfiguration.loadConfiguration(warpFile);

                                    try {
                                        String warpName = ymlFile.getString("name");
                                        if (getManager().getWarp(warpName) != null) continue;

                                        double x = ymlFile.getDouble("x"), y = ymlFile.getDouble("y"), z = ymlFile.getDouble("z");
                                        float yaw = (float) ymlFile.getDouble("yaw"), pitch = (float) ymlFile.getDouble("pitch");
                                        SerializableLocation serializableLocation = new SerializableLocation(ymlFile.getString("world"), x, y, z, yaw, pitch);

                                        Warp warp = new Warp(warpName, serializableLocation);
                                        warp.register();
                                        convertedWarpCount += 1;
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                    }
                }

            if (convertedWarpCount > 0) {
                log(Level.INFO, "A total of " + convertedWarpCount + " Essentials warp(s) were found and converted into HyperDrive warp(s). "
                        + "(Took " + (System.currentTimeMillis() - startTime) + "ms)");
                log(Level.INFO, "All Essentials warp files are never deleted from the original location; therefore, nothing has been lost!");
            } else
                log(Level.INFO, "No Essentials warps were found. Skipping the HyperDrive warp conversion process... (Took "
                        + (System.currentTimeMillis() - startTime) + "ms)");
        }
    }

    public void stopTasks(boolean useMySQL) {
        getServer().getScheduler().cancelTask(getTeleportationHandlerTaskId());

        if (getDatabaseConnection() != null && useMySQL)
            getServer().getScheduler().cancelTask(getCrossServerTaskId());
        if (getConfig().getInt("general-section.auto-save-interval") >= 0)
            getServer().getScheduler().cancelTask(getAutoSaveTaskId());
    }

    public void startTasks() {
        boolean useMySQL = getConfig().getBoolean("mysql-connection.use-mysql");
        TeleportationHandler teleportationHandler = new TeleportationHandler(this);
        int thID = getServer().getScheduler().scheduleSyncRepeatingTask(this, teleportationHandler, 0, 20);
        setTeleportationHandlerTaskId(thID);
        setTeleportationHandler(teleportationHandler);

        final int interval = getConfig().getInt("general-section.auto-save-interval");
        BukkitTask autoSaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> saveWarps(useMySQL), 20 * interval, 20 * interval);
        setAutoSaveTaskId(autoSaveTask.getTaskId());

        if (useMySQL) {
            BukkitTask crossServerTeleportTaskId = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (getBungeeListener() == null) {
                    getServer().getScheduler().cancelTask(getCrossServerTaskId());
                    return;
                }

                getDatabaseWarps().clear();
                try {
                    Statement statement = getDatabaseConnection().createStatement();
                    ResultSet resultSet = statement.executeQuery("select * from warps");
                    while (resultSet.next()) {
                        String warpName = resultSet.getString("name");
                        if (!getDatabaseWarps().contains(warpName)) getDatabaseWarps().add(warpName);
                    }

                    resultSet.close();
                    statement.close();

                    List<Warp> warps = new ArrayList<>(getManager().getWarpMap().values());
                    for (Warp warp : warps)
                        if (!getDatabaseWarps().contains(warp.getWarpName()))
                            warp.unRegister();

                    for (String warpName : getDatabaseWarps())
                        if (!getManager().getWarpMap().containsKey(warpName))
                            loadWarp(warpName);

                } catch (SQLException e) {
                    e.printStackTrace();
                }

                if (getBungeeListener().getServerAddressMap() == null || getBungeeListener().getServerAddressMap().isEmpty())
                    getBungeeListener().updateServerList();

                for (Map.Entry<UUID, SerializableLocation> mapEntry : getBungeeListener().getTransferMap().entrySet()) {
                    if (mapEntry.getKey() == null || mapEntry.getValue() == null) continue;
                    OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(mapEntry.getKey());
                    if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                        getBungeeListener().getTransferMap().remove(mapEntry.getKey());
                        getServer().getScheduler().runTask(getPluginInstance(), () -> {
                            Location location = mapEntry.getValue().asBukkitLocation();
                            getTeleportationHandler().teleportPlayer(offlinePlayer.getPlayer(), location);
                            getManager().sendCustomMessage(Objects.requireNonNull(getLangConfig().getString("cross-teleported"))
                                    .replace("{world}", Objects.requireNonNull(location.getWorld()).getName())
                                    .replace("{x}", String.valueOf(location.getBlockX()))
                                    .replace("{y}", String.valueOf(location.getBlockY()))
                                    .replace("{z}", String.valueOf(location.getBlockZ())), offlinePlayer.getPlayer());
                        });
                    }
                }
            }, 0, 20);
            setCrossServerTaskId(crossServerTeleportTaskId.getTaskId());
        }
    }

    private void saveData() {
        File file = new File(getDataFolder(), "/data.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try {
            if (getTeleportationCommands().getSpawnLocation() != null) {
                yaml.set("spawn.world", getTeleportationCommands().getSpawnLocation().getWorldName());
                yaml.set("spawn.x", getTeleportationCommands().getSpawnLocation().getX());
                yaml.set("spawn.y", getTeleportationCommands().getSpawnLocation().getY());
                yaml.set("spawn.z", getTeleportationCommands().getSpawnLocation().getZ());
                yaml.set("spawn.yaw", getTeleportationCommands().getSpawnLocation().getYaw());
                yaml.set("spawn.pitch", getTeleportationCommands().getSpawnLocation().getPitch());
            } else yaml.set("spawn", null);

            if (getTeleportationCommands().getFirstJoinLocation() != null) {
                yaml.set("first-join-spawn.world", getTeleportationCommands().getFirstJoinLocation().getWorldName());
                yaml.set("first-join-spawn.x", getTeleportationCommands().getFirstJoinLocation().getX());
                yaml.set("first-join-spawn.y", getTeleportationCommands().getFirstJoinLocation().getY());
                yaml.set("first-join-spawn.z", getTeleportationCommands().getFirstJoinLocation().getZ());
                yaml.set("first-join-spawn.yaw", getTeleportationCommands().getFirstJoinLocation().getYaw());
                yaml.set("first-join-spawn.pitch", getTeleportationCommands().getFirstJoinLocation().getPitch());
            } else yaml.set("first-join-spawn", null);

            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    public void saveWarps(boolean async) {
        long startTime = System.currentTimeMillis();
        for (Warp warp : getManager().getWarpMap().values()) warp.save(async);
        if (getConfig().getBoolean("general-section.auto-save-log"))
            log(Level.INFO, "All warps have been saved! (Took " + (System.currentTimeMillis() - startTime) + "ms)");
    }

    public void loadWarps() {
        long startTime = System.currentTimeMillis();
        int loadedWarps = 0, failedToLoadWarps = 0;

        boolean fixTables = false;
        try {
            Statement statement = getDatabaseConnection().createStatement();
            ResultSet resultSet = statement.executeQuery("select * from warps");
            while (resultSet.next()) {
                try {
                    String warpName = resultSet.getString("name").replace("'", "").replace("\"", ""), nameColor = null;
                    if (warpName.isEmpty()) {
                        try {
                            Statement deleteStatement = databaseConnection.createStatement();
                            deleteStatement.executeUpdate("delete from warps where name = '" + warpName + "'");
                            deleteStatement.close();
                        } catch (SQLException ignored) {}
                        continue;
                    }

                    try {
                        nameColor = resultSet.getString("name_color");
                        if (nameColor != null && !nameColor.isEmpty() && !nameColor.contains("§"))
                            if (nameColor.contains("&"))
                                nameColor = ChatColor.translateAlternateColorCodes('&', nameColor);
                            else
                                nameColor = ChatColor.valueOf(nameColor.toUpperCase().replace(" ", "_").replace("-", "_")).toString();
                        fixTables = true;

                        if (nameColor != null && !nameColor.isEmpty()) {
                            Statement deleteStatement = databaseConnection.createStatement();
                            deleteStatement.executeUpdate("delete from warps where name = '" + warpName + "'");
                            deleteStatement.close();
                        }
                    } catch (SQLException ignored) {}

                    warpName = (nameColor != null) ? (nameColor + getManager().colorText(warpName)) : getManager().colorText(warpName);
                    final String strippedName = ChatColor.stripColor(warpName);
                    if (strippedName.equalsIgnoreCase("") || strippedName.isEmpty()) continue;
                    if (!warpName.contains("§")) warpName = ("§f" + warpName);

                    String ipAddress = resultSet.getString("server_ip").replace("localhost", "127.0.0.1"),
                            locationString = resultSet.getString("location");
                    if (locationString.contains(",")) {
                        String[] locationStringArgs = locationString.split(",");

                        SerializableLocation serializableLocation = new SerializableLocation(locationStringArgs[0],
                                Double.parseDouble(locationStringArgs[1]), Double.parseDouble(locationStringArgs[2]),
                                Double.parseDouble(locationStringArgs[3]), Float.parseFloat(locationStringArgs[4]),
                                Float.parseFloat(locationStringArgs[5]));
                        UUID uuid = null;
                        String ownerId = resultSet.getString("owner");
                        if (ownerId != null && !ownerId.equalsIgnoreCase(""))
                            uuid = UUID.fromString(ownerId);

                        Warp warp;
                        if (uuid == null) warp = new Warp(warpName, serializableLocation);
                        else
                            warp = new Warp(warpName, getPluginInstance().getServer().getOfflinePlayer(uuid), serializableLocation);
                        warp.register();
                        loadedWarps += 1;

                        converterWarpSpecifics(resultSet, ipAddress, warp);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }

            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            failedToLoadWarps += 1;
            e.printStackTrace();
            log(Level.WARNING, "This is just a warning stating that a warp has failed to load.");
        }

        if (loadedWarps > 0 || failedToLoadWarps > 0)
            log(Level.INFO, loadedWarps + " " + ((loadedWarps == 1) ? "warp was" : "warps were") + " loaded and "
                    + failedToLoadWarps + " " + ((failedToLoadWarps == 1) ? "warp" : "warps") + " failed to load. (Took " + (System.currentTimeMillis() - startTime) + "ms)");

        if (fixTables) {
            try {
                Statement statement = getDatabaseConnection().createStatement();
                statement.executeUpdate("ALTER TABLE warps RENAME TO warps_old;");
                statement.executeUpdate(tableName);

                final String columnsSeparated = "name, location, status, creation_date, icon_theme, animation_set, description, "
                        + "commands, owner, player_list, assistants, traffic, usage_price, enchanted_look, server_ip, likes, "
                        + "dislikes, voters, white_list_mode, notify";

                statement.executeUpdate("INSERT INTO warps(" + columnsSeparated + ") SELECT " + columnsSeparated + " FROM warps_old;");
                statement.executeUpdate("DROP TABLE warps_old;");
                statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadWarp(String warpName) {
        if (getManager().doesWarpExist(warpName)) return;

        try {
            Statement statement = getDatabaseConnection().createStatement();
            ResultSet resultSet = statement.executeQuery("select * from warps");
            while (resultSet.next()) {
                String ipAddress = resultSet.getString("server_ip"), locationString = resultSet.getString("location");
                if (locationString.contains(",")) {
                    String[] locationStringArgs = locationString.split(",");

                    SerializableLocation serializableLocation = new SerializableLocation(locationStringArgs[0],
                            Double.parseDouble(locationStringArgs[1]), Double.parseDouble(locationStringArgs[2]),
                            Double.parseDouble(locationStringArgs[3]), Float.parseFloat(locationStringArgs[4]),
                            Float.parseFloat(locationStringArgs[5]));
                    UUID uuid = null;
                    String ownerId = resultSet.getString("owner");
                    if (ownerId != null && !ownerId.equalsIgnoreCase(""))
                        uuid = UUID.fromString(ownerId);

                    Warp warp;
                    if (uuid == null)
                        warp = new Warp(warpName, serializableLocation);
                    else
                        warp = new Warp(warpName, getPluginInstance().getServer().getOfflinePlayer(uuid), serializableLocation);
                    warp.register();
                    converterWarpSpecifics(resultSet, ipAddress, warp);
                }
            }

            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean doesWarpExistInDatabase(String warpName) {
        if (!getDatabaseWarps().isEmpty())
            for (String name : getDatabaseWarps())
                if (net.md_5.bungee.api.ChatColor.stripColor(name).equalsIgnoreCase(net.md_5.bungee.api.ChatColor.stripColor(warpName)))
                    return true;
        return false;
    }

    private static void copy(File source, File destination) throws IOException {
        try (InputStream is = new FileInputStream(source); OutputStream os = new FileOutputStream(destination)) {
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buf)) > 0) {
                os.write(buf, 0, bytesRead);
            }
        }
    }

    public void log(Level level, String message) {
        getPluginInstance().getServer().getLogger().log(level,
                "[" + getPluginInstance().getDescription().getName() + "] " + message);
    }

    // custom configurations

    /**
     * Reloads all configs associated with DisplayShops.
     */
    public void reloadConfigs() {
        reloadConfig();

        if (langFile == null) langFile = new File(getDataFolder(), "lang.yml");
        langConfig = YamlConfiguration.loadConfiguration(langFile);

        Reader defConfigStream;
        InputStream path = this.getResource("lang.yml");
        if (path != null) {
            defConfigStream = new InputStreamReader(path, StandardCharsets.UTF_8);
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
            langConfig.setDefaults(defConfig);

            try {
                defConfigStream.close();
            } catch (IOException e) {
                log(Level.WARNING, e.getMessage());
            }
        }

        if (menusFile == null) menusFile = new File(getDataFolder(), "menus.yml");
        menusConfig = YamlConfiguration.loadConfiguration(menusFile);

        InputStream path2 = this.getResource("menus.yml");
        if (path2 != null) {
            defConfigStream = new InputStreamReader(path2, StandardCharsets.UTF_8);
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
            langConfig.setDefaults(defConfig);

            try {
                defConfigStream.close();
            } catch (IOException e) {
                log(Level.WARNING, e.getMessage());
            }
        }
    }

    /**
     * Gets the language file configuration.
     *
     * @return The FileConfiguration found.
     */
    public FileConfiguration getLangConfig() {
        if (langConfig == null) reloadConfigs();
        return langConfig;
    }

    /**
     * Gets the menus file configuration.
     *
     * @return The FileConfiguration found.
     */
    public FileConfiguration getMenusConfig() {
        if (menusConfig == null) reloadConfigs();
        return menusConfig;
    }

    /**
     * Saves the default configuration files (Doesn't replace existing).
     */
    public void saveDefaultConfigs() {
        saveDefaultConfig();
        if (langFile == null) langFile = new File(getDataFolder(), "lang.yml");
        if (!langFile.exists()) saveResource("lang.yml", false);
        if (menusFile == null) menusFile = new File(getDataFolder(), "menus.yml");
        if (!menusFile.exists()) saveResource("menus.yml", false);

        reloadConfigs();
    }

    private void saveLangConfig() {
        if (langConfig == null || langFile == null) return;
        try {
            getLangConfig().save(langFile);
        } catch (IOException e) {
            log(Level.WARNING, e.getMessage());
        }
    }

    private void saveMenusConfig() {
        if (menusConfig == null || menusFile == null) return;
        try {
            getMenusConfig().save(menusFile);
        } catch (IOException e) {
            log(Level.WARNING, e.getMessage());
        }
    }

    // getters and setters
    public static HyperDriveTuga getPluginInstance() {
        return pluginInstance;
    }

    private static void setPluginInstance(HyperDriveTuga pluginInstance) {
        HyperDriveTuga.pluginInstance = pluginInstance;
    }

    public Manager getManager() {
        return manager;
    }

    private void setManager(Manager manager) {
        this.manager = manager;
    }

    public TeleportationHandler getTeleportationHandler() {
        return teleportationHandler;
    }

    private void setTeleportationHandler(TeleportationHandler teleportationHandler) {
        this.teleportationHandler = teleportationHandler;
    }

    public MainCommands getMainCommands() {
        return mainCommands;
    }

    private void setMainCommands(MainCommands mainCommands) {
        this.mainCommands = mainCommands;
    }

    public TeleportationCommands getTeleportationCommands() {
        return teleportationCommands;
    }

    private void setTeleportationCommands(TeleportationCommands teleportationCommands) {
        this.teleportationCommands = teleportationCommands;
    }

    public Connection getDatabaseConnection() {
        return databaseConnection;
    }

    private void setDatabaseConnection(Connection connection) {
        this.databaseConnection = connection;
    }

    private int getTeleportationHandlerTaskId() {
        return teleportationHandlerTaskId;
    }

    private void setTeleportationHandlerTaskId(int teleportationHandlerTaskId) {
        this.teleportationHandlerTaskId = teleportationHandlerTaskId;
    }

    private int getAutoSaveTaskId() {
        return autoSaveTaskId;
    }

    private void setAutoSaveTaskId(int autoSaveTaskId) {
        this.autoSaveTaskId = autoSaveTaskId;
    }

    private int getCrossServerTaskId() {
        return crossServerTaskId;
    }

    private void setCrossServerTaskId(int crossServerTaskId) {
        this.crossServerTaskId = crossServerTaskId;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    private void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public BungeeListener getBungeeListener() {
        return bungeeListener;
    }

    private void setBungeeListener(BungeeListener bungeeListener) {
        this.bungeeListener = bungeeListener;
    }

    private List<String> getDatabaseWarps() {
        return databaseWarps;
    }

    private void setDatabaseWarps(List<String> databaseWarps) {
        this.databaseWarps = databaseWarps;
    }

    public VaultHandler getVaultHandler() {
        return vaultHandler;
    }

    private void setVaultHandler(VaultHandler vaultHandler) {
        this.vaultHandler = vaultHandler;
    }

    public WorldGuardHandler getWorldGuardHandler() {
        return worldGuardHandler;
    }

    private void setWorldGuardHandler(WorldGuardHandler worldGuardHandler) {
        this.worldGuardHandler = worldGuardHandler;
    }

    public boolean asyncChunkMethodExists() {
        return asyncChunkMethodExists;
    }

    private void setAsyncChunkMethodExists(boolean asyncChunkMethodExists) {
        this.asyncChunkMethodExists = asyncChunkMethodExists;
    }

    public HookChecker getHookChecker() {
        return hookChecker;
    }

    private void setHookChecker(HookChecker hookChecker) {
        this.hookChecker = hookChecker;
    }

    public PapiHook getPapiHook() {
        return papiHook;
    }

    private void setPapiHook(PapiHook papiHook) {
        this.papiHook = papiHook;
    }
}
