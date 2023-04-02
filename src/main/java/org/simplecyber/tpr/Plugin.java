package org.simplecyber.tpr;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Plugin extends JavaPlugin {

    FileConfiguration config = null;

    public String translateColors(String text) {
        text = ChatColor.translateAlternateColorCodes('&', text);
        final char COLOR_CHAR = ChatColor.COLOR_CHAR;
        final Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(text);
        StringBuffer buffer = new StringBuffer(text.length() + 4 * 8);
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, COLOR_CHAR + "x"
                    + COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
                    + COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
                    + COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5)
                    );
        }
        return matcher.appendTail(buffer).toString();
    }

    public String strFill(String text, Object... replacements) {
        for (int i = 0; i < replacements.length; i++) {
            text = text.replace((CharSequence) ("%"+i), (CharSequence) String.valueOf(replacements[i]));
        }
        return text;
    }
    public void sendMsg(Object target, String text, Object... replacements) {
        text = strFill(text, replacements);
        text = translateColors(text);
        if (target instanceof ConsoleCommandSender) {
            ConsoleCommandSender console = (ConsoleCommandSender) target;
            console.sendMessage(strFill("[%0] %1", getName(), text));
            return;
        }
        if (target instanceof CommandSender) {
            ((CommandSender) target).sendMessage(text);
            return;
        }
        if (target instanceof Player) {
            ((Player) target).sendMessage(text);
            return;
        }
    }
    public void log(String type, String text) {
        Level level;
        switch (type) {
            case "info":
                level = Level.INFO;
                break;
            case "warning":
                level = Level.WARNING;
                break;
            default:
                level = Level.INFO;
                break;
        }
        if (type == "info") {
            sendMsg(getServer().getConsoleSender(), text);
        } else {
            getLogger().log(level, text);
        }
    }
    public void log(String text) {
        log("info", text);
    }

    public void reload() {
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();
        log("Config reloaded!");
    }

    HashMap<String, Long> cooldowns = new HashMap<>();

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "tpr": {
                // If the sender is the console, stop here
                if (sender instanceof ConsoleCommandSender) {
                    sendMsg(sender, config.getString("messages.player_only"));
                    return true;
                }
                // Get config values
                int radius = config.getInt("radius");
                int originX = config.getInt("origin.x");
                int originZ = config.getInt("origin.z");
                int safeY = config.getInt("safe_y");
                List<String> unsafeBlocks = config.getStringList("unsafe_blocks");
                List<String> worldsAllowed = config.getStringList("worlds.allowed");
                List<String> worldsBlocked = config.getStringList("worlds.blocked");
                boolean allowByDefault = config.getBoolean("worlds.allow_by_default");
                boolean blockOverworld = config.getBoolean("blocked_dimensions.overworld");
                boolean blockNether = config.getBoolean("blocked_dimensions.nether");
                boolean blockEnd = config.getBoolean("blocked_dimensions.end");
                int cooldownTime = config.getInt("cooldown");
                int maxTries = config.getInt("max_tries");
                // Update and check unsafe blocks
                for (int i = 0; i < unsafeBlocks.size(); i++) {
                    unsafeBlocks.set(i, unsafeBlocks.get(i).toUpperCase());
                    if (Material.getMaterial(unsafeBlocks.get(i)) == null) {
                        log("warning", unsafeBlocks.get(i) + " isn't a recognized material!");
                    }
                }
                // Set some variables
                Player player = (Player) sender;
                World world = player.getWorld();
                Location loc = null;
                boolean isValid = false;
                int tries = 0;
                // Check cooldown
                if (cooldowns.containsKey(player.getName()) && !player.hasPermission("cybertpr.player.bypassCooldown")) {
                    long lastRun = cooldowns.get(player.getName());
                    long msSinceLastRun = (System.currentTimeMillis() - lastRun);
                    long msLeft = ((cooldownTime * 1000) - msSinceLastRun);
                    if (msLeft > 0) {
                        sendMsg(player, config.getString("messages.cooldown"), Math.round(msLeft/1000));
                        return true;
                    }
                }
                // Check dimension
                Environment env = world.getEnvironment();
                if (blockOverworld && env == Environment.NORMAL || blockNether && env == Environment.NETHER || blockEnd && env == Environment.THE_END) {
                    sendMsg(player, config.getString("messages.dimension_blocked"));
                    return true;
                }
                // Check world
                if (allowByDefault) {
                    if (worldsBlocked.contains(world.getName())) {
                        sendMsg(player, config.getString("messages.world_blocked"));
                        return true;
                    }
                } else {
                    if (!worldsAllowed.contains(world.getName())) {
                        sendMsg(player, config.getString("messages.world_blocked"));
                        return true;
                    }
                }
                // Loop until we find a safe spot or run out of tries
                sendMsg(player, config.getString("messages.searching"));
                cooldowns.put(player.getName(), System.currentTimeMillis());
                log(strFill("Searching for safe locations within %0 blocks of (%1, %2)...", radius, originX, originZ));
                while (!isValid && tries < maxTries) {
                    tries++;
                    // Get random X and Z coordinates
                    int x = (int) (originX + ((Math.random() * 2) - 1) * radius);
                    int z = (int) (originZ + ((Math.random() * 2) - 1) * radius);
                    // Set our location
                    loc = new Location(world, x+0.5, 0, z+0.5);
                    // Load the chunk
                    boolean chunkLoadSuccessful = loc.getChunk().load();
                    // If the chunk couldn't be loaded, log and skip
                    if (!chunkLoadSuccessful) {
                        log("warning", strFill("Failed to load chunk at (%0, %1)!", loc.getChunk().getX(), loc.getChunk().getZ()));
                        continue;
                    }
                    // Get the block at these coords
                    Block block = world.getHighestBlockAt(loc);
                    Material type = block.getType();
                    log(strFill("Picked block at (%0, %1, %2) of type %3", block.getX(), block.getY(), block.getZ(), type));
                    // If the block is below the configured safe Y level, log and skip
                    if (block.getY() < safeY) {
                        log("Block is below the safe Y level.");
                        continue;
                    }
                    // If the block is in the list of unsafe blocks, log and skip
                    if (unsafeBlocks.contains(String.valueOf(type))) {
                        log("Block is in the list of unsafe blocks.");
                        continue;
                    }
                    // Add 1 to the Y coordinate and finish up
                    loc.setY(block.getY()+1);
                    isValid = true;
                    break;
                }
                // If we couldn't find a valid spot, log and give up
                if (!isValid) {
                    log("warning", "Failed to find a safe random location!");
                    sendMsg(player, config.getString("messages.search_fail"));
                    return true;
                }
                // Teleport the player and finish up
                sendMsg(player, config.getString("messages.teleporting"), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                player.teleport(loc);
                log("Sent %0 to their destination!", player.getName());
                return true;
            }
            case "cybertpr": {
                Runnable sendUsage = () -> {
                    sendMsg(sender, "&b%0 &3v%1", getName(), getDescription().getVersion());
                    sendMsg(sender, "&9https://github.com/CyberGen49/spigot-tpr");
                };
                if (args.length == 0 || !sender.hasPermission("cybertpr.admin")) {
                    sendUsage.run();
                    return true;
                }
                switch (args[0]) {
                    case "reload":
                        reload();
                        sendMsg(sender, config.getString("messages.reloaded"));
                        break;
                    default:
                        sendUsage.run();
                }
                return true;
            }
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        switch (cmd.getName().toLowerCase()) {
            case "cybertpr":
                if (args.length == 1 && sender.hasPermission("cybertpr.admin")) {
                    options.add("reload");
                }
                break;
        }
        return options;
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        int version = 7;
        if (config.getInt("config_version") != version) {
            log("Config version mismatch! Renaming current config file and reloading...");
            File configFile = new File(getDataFolder(), "config.yml");
            File newFile = new File(getDataFolder(), strFill("config-%0.yml", System.currentTimeMillis()));
            configFile.renameTo(newFile);
            reload();
        }
        log("&aReady to go!");
    }
    @Override public void onDisable() {
        log("&dSee you next time!");
    }
}