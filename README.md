# CyberTPR
A simple random teleportation plugin for Spigot Minecraft servers.

**Note:** Random teleportation into unloaded chunks can cause a lot of server lag! Consider using a plugin like [Chunky](https://www.spigotmc.org/resources/chunky.81534/) to pre-load your entire TPR area.

## Commands
* `/tpr`: Teleport to a random, safe location.
* `/cybertpr`: Outputs the plugin name and version, along with a link to this repository.
    * `/cybertpr reload`: Reloads the config.

## Configuration
See the comments in [config.yml](/src/main/resources/config.yml) for config documentation.

## Permissions
* `cybertpr.player`: Allows the player to use `/tpr`. All players have this by default.
* `cybertpr.cooldown.exempt`: Allows the player to bypass the configured cooldown.
* `cybertpr.admin`: Allows the player to use `/cybertpr reload`.