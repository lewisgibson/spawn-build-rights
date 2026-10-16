package com.lewisgibson.spawnbuildrights.fixture;

import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A second plugin, loaded only in the server tests, that lets the harness fire a real {@link
 * BlockBreakEvent} at the world spawn for a fake player whose playtime statistic is whatever the
 * command says. The plugin under test sees an ordinary event from an ordinary-looking player and
 * the RCON reply reports whether it was cancelled and what the player was shown.
 *
 * <p>{@code /sbrtest break <playtimeTicks> [granted|revoked]}. Nothing here touches the plugin
 * under test: it is observed purely through the server's own event pipeline.
 */
public final class TestFixturePlugin extends JavaPlugin {

    private EnvironmentalScenarios environment;

    @Override
    public void onEnable() {
        environment = new EnvironmentalScenarios(this);
        getCommand("sbrtest").setExecutor(this::run);
    }

    private boolean run(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 2 && args[0].equals("environment")) {
            sender.sendMessage(environment.run(args[1]));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("spawn")) {
            Location spawn = getServer().getWorlds().get(0).getSpawnLocation();
            sender.sendMessage(
                    spawn.getBlockX() + " " + spawn.getBlockY() + " " + spawn.getBlockZ());
            return true;
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("breakat")) {
            World world = getServer().getWorlds().get(0);
            FakePlayer fake = new FakePlayer(Long.parseLong(args[4]), false, false);
            BlockBreakEvent event =
                    new BlockBreakEvent(
                            world.getBlockAt(
                                    Integer.parseInt(args[1]),
                                    Integer.parseInt(args[2]),
                                    Integer.parseInt(args[3])),
                            fake.proxy());
            getServer().getPluginManager().callEvent(event);
            sender.sendMessage("cancelled=" + event.isCancelled());
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("change")) {
            sender.sendMessage(change(Long.parseLong(args[1])));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("break")) {
            sender.sendMessage(
                    "usage: /sbrtest break <playtimeTicks> [granted|revoked]"
                            + " | change <playtimeTicks> | spawn | breakat <x> <y> <z> <playtimeTicks>");
            return true;
        }
        long playtime = Long.parseLong(args[1]);
        String node = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "unset";
        FakePlayer fake = new FakePlayer(playtime, node.equals("granted"), node.equals("revoked"));
        Player player = fake.proxy();

        World world = getServer().getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        BlockBreakEvent event =
                new BlockBreakEvent(
                        world.getBlockAt(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()),
                        player);
        getServer().getPluginManager().callEvent(event);

        Component shown = fake.lastActionBar();
        sender.sendMessage(
                "cancelled="
                        + event.isCancelled()
                        + " actionbar="
                        + (shown == null
                                ? "-"
                                : PlainTextComponentSerializer.plainText().serialize(shown)));
        return true;
    }

    /**
     * Fires, at the world spawn, the block changes a player makes without placing or breaking a
     * block, as the real server's own event classes: trampling farmland (an entity changing a
     * block, the entity being the player) and planting in a flower pot.
     */
    private String change(long playtime) {
        World world = getServer().getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        Block block = world.getBlockAt(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        Player player = new FakePlayer(playtime, false, false).proxy();
        EntityChangeBlockEvent trample =
                new EntityChangeBlockEvent(player, block, Material.DIRT.createBlockData());
        getServer().getPluginManager().callEvent(trample);
        PlayerFlowerPotManipulateEvent pot =
                new PlayerFlowerPotManipulateEvent(
                        player, block, new ItemStack(Material.POPPY), true);
        getServer().getPluginManager().callEvent(pot);
        return "trample=" + trample.isCancelled() + " pot=" + pot.isCancelled();
    }
}
