package org.simplecyber.tpr;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

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

    public void sendMsg(ConsoleCommandSender target, String text) {
        target.sendMessage("[" + getName() + "] " + ChatColor.translateAlternateColorCodes('&', text));
    }
    public void sendMsg(Player target, String text) {
        target.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
    }
    public void sendMsg(CommandSender target, String text) {
        target.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
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

    public String bulkReplace(String source, Object[] targets, Object[] replacements) throws IllegalArgumentException {
        if (targets.length != replacements.length) {
            throw new IllegalArgumentException("Targets and replacements must be the same length!");
        }
        for (int i = 0; i < targets.length; i++) {
            source = source.replace((CharSequence) String.valueOf(targets[i]), (CharSequence) String.valueOf(replacements[i]));
        }
        return source;
    }

    public void reload() {
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();
        log("Config reloaded!");
    }

    public boolean cmd_tpr(CommandSender sender, Command cmd, String[] args) {
        // If the sender is the console, stop here
        if (sender instanceof ConsoleCommandSender) {
            sendMsg(sender, "&cOnly players can use this command!");
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
        int maxTries = config.getInt("max_tries");
        // Set some variables
        Player player = (Player) sender;
        World world = player.getWorld();
        Location loc = null;
        boolean isValid = false;
        int tries = 0;
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
        log("Searching for safe locations within " + radius + " blocks of (" + originX + ", " + originZ + ")...");
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
                log("warning", "Failed to load chunk at (" + loc.getChunk().getX() + ", " + loc.getChunk().getZ() + ")!");
                continue;
            }
            // Get the block at these coords
            Block block = world.getHighestBlockAt(loc);
            Material type = block.getType();
            log("Picked block at (" + block.getX() + ", " + block.getY() + ", " + block.getZ() + ") of type " + type);
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
        sendMsg(player, bulkReplace(
            config.getString("messages.teleporting"),
            new Object[]{ "{x}", "{y}", "{z}" },
            new Object[]{ loc.getBlockX(), loc.getBlockY(), loc.getBlockZ() }
        ));
        player.teleport(loc);
        log("Sent " + player.getName() + " to their destination!");
        return true;
    }

    public boolean cmd_cybertpr(CommandSender sender, Command cmd, String[] args) {
        if (args.length == 0 || !sender.hasPermission("cybertpr.admin")) {
            sendMsg(sender, "&bCyberTPR &3v" + getDescription().getVersion());
            sendMsg(sender, "&9https://github.com/CyberGen49/spigot-tpr");
            return true;
        }
        switch (args[0]) {
            case "reload":
                reload();
                sendMsg(sender, "&aReloaded config!");
                break;
            default:
                return cmd_cybertpr(sender, cmd, new String[0]);
        }
        return true;
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "tpr":
                return cmd_tpr(sender, cmd, args);
            case "cybertpr":
                return cmd_cybertpr(sender, cmd, args);
        }
        return false;
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
        int version = 3;
        if (config.getInt("config_version") != version) {
            log("Config version mismatch! Renaming current config file and reloading...");
            File configFile = new File(getDataFolder(), "config.yml");
            File newFile = new File(getDataFolder(), "config-" + System.currentTimeMillis() + ".yml");
            configFile.renameTo(newFile);
            reload();
        }
        log("&aReady to go!");
    }
    @Override public void onDisable() {
        log("&dSee you next time!");
    }
}