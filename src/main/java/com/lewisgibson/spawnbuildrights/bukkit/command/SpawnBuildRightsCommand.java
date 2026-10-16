package com.lewisgibson.spawnbuildrights.bukkit.command;

import com.lewisgibson.spawnbuildrights.bukkit.PluginRuntime;
import com.lewisgibson.spawnbuildrights.bukkit.SpawnBuildRightsPlugin;
import com.lewisgibson.spawnbuildrights.bukkit.permission.BuildPermission;
import com.lewisgibson.spawnbuildrights.bukkit.playtime.Playtime;
import com.lewisgibson.spawnbuildrights.core.access.AccessResult;
import com.lewisgibson.spawnbuildrights.core.access.OverrideState;
import com.lewisgibson.spawnbuildrights.core.access.PermissionState;
import com.lewisgibson.spawnbuildrights.core.grant.GrantEvent;
import com.lewisgibson.spawnbuildrights.core.grant.GrantOverride;
import com.lewisgibson.spawnbuildrights.core.grant.StorageException;
import com.lewisgibson.spawnbuildrights.core.message.Placeholders;
import com.lewisgibson.spawnbuildrights.core.region.RegionShape;
import com.lewisgibson.spawnbuildrights.core.region.SpawnRegion;
import com.lewisgibson.spawnbuildrights.core.settings.EnvironmentalProtection;
import com.lewisgibson.spawnbuildrights.core.settings.PortalArrivals;
import com.lewisgibson.spawnbuildrights.core.settings.SpawnBuildRightsSettings;
import com.lewisgibson.spawnbuildrights.core.time.PlaytimeDuration;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Handles {@code /spawnbuildrights} (alias {@code /sbr}): {@code info} for everyone; {@code
 * reload}, {@code set}, {@code grant}, {@code revoke}, {@code reset} and {@code history} for
 * admins.
 */
public final class SpawnBuildRightsCommand implements TabExecutor {

    private static final String INFO = "info";
    private static final String RELOAD = "reload";
    private static final String SET = "set";
    private static final String GRANT = "grant";
    private static final String REVOKE = "revoke";
    private static final String RESET = "reset";
    private static final String HISTORY = "history";
    private static final String THRESHOLD = "threshold";
    private static final String RADIUS = "radius";
    private static final String SHAPE = "shape";
    private static final String CENTER = "center";
    private static final int HISTORY_LIMIT = 10;
    private static final Duration HISTORY_FLUSH_TIMEOUT = Duration.ofSeconds(2);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);
    private static final List<String> ADMIN_SUBCOMMANDS =
            List.of(INFO, RELOAD, SET, GRANT, REVOKE, RESET, HISTORY);
    private static final List<String> PLAYER_SUBCOMMANDS = List.of(GRANT, REVOKE, RESET, HISTORY);
    private static final List<String> SETTINGS = List.of(THRESHOLD, RADIUS, SHAPE, CENTER);

    private final SpawnBuildRightsPlugin plugin;

    /**
     * @param plugin the plugin whose live runtime and config the subcommands read and edit
     */
    public SpawnBuildRightsCommand(SpawnBuildRightsPlugin plugin) {
        this.plugin = plugin;
    }

    /** A player named on the command line, resolved to an id without any network lookup. */
    private record Target(UUID id, String name) {}

    /** Everyone may run {@code info}; every other known subcommand needs the admin permission. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase(INFO)) {
            sendInfo(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBCOMMANDS.contains(subcommand)
                && !sender.hasPermission(SpawnBuildRightsPlugin.ADMIN_PERMISSION)) {
            sender.sendMessage(noPermission());
            return true;
        }
        switch (subcommand) {
            case RELOAD -> handleReload(sender);
            case SET -> handleSet(sender, args);
            case GRANT -> handleOverride(sender, args, OverrideState.GRANTED);
            case REVOKE -> handleOverride(sender, args, OverrideState.REVOKED);
            case RESET -> handleReset(sender, args);
            case HISTORY -> handleHistory(sender, args);
            // Only an admin is told the admin subcommands exist; everyone else only ever has info.
            default ->
                    sender.sendMessage(
                            error(
                                    sender.hasPermission(SpawnBuildRightsPlugin.ADMIN_PERMISSION)
                                            ? "Unknown subcommand. Use /sbr <"
                                                    + String.join("|", ADMIN_SUBCOMMANDS)
                                                    + ">."
                                            : "Unknown subcommand. Use /sbr " + INFO + "."));
        }
        return true;
    }

    private void sendInfo(CommandSender sender) {
        PluginRuntime runtime = plugin.runtime();
        SpawnBuildRightsSettings settings = runtime.settings();
        SpawnRegion region = settings.region();

        // A player is told where they stand in one sentence, the same one the action bar shows
        // when a change is refused. The version, the region and the switches are for admins and
        // the console.
        if (sender instanceof Player player
                && !player.hasPermission(SpawnBuildRightsPlugin.ADMIN_PERMISSION)) {
            long played = Playtime.ticks(player);
            plugin.messenger()
                    .send(
                            player,
                            settings.messages().about(evaluate(runtime, player, played)),
                            Placeholders.playtime(played, settings.requiredPlaytimeTicks()));
            return;
        }
        sender.sendMessage(
                Component.text("SpawnBuildRights", NamedTextColor.GREEN)
                        .append(
                                Component.text(
                                        " " + plugin.getPluginMeta().getVersion(),
                                        NamedTextColor.GRAY)));
        sender.sendMessage(
                detail(
                        "Spawn region",
                        region.shape().token()
                                + ", "
                                + region.radius()
                                + (region.radius() == 1 ? " block" : " blocks")
                                + " around "
                                + region.centerX()
                                + ", "
                                + region.centerZ()
                                + " in "
                                + region.world()));
        sender.sendMessage(
                detail(
                        "Build rights after",
                        PlaytimeDuration.display(settings.requiredPlaytimeTicks())
                                + " of playtime"));

        sendProtection(sender, settings.protection());

        if (sender instanceof Player player) {
            long played = Playtime.ticks(player);
            PermissionState permission = BuildPermission.of(player, settings.buildPermission());
            Optional<GrantOverride> override =
                    runtime.grants().registry().override(player.getUniqueId());
            AccessResult result = evaluate(runtime, player, played);
            long remaining = settings.requiredPlaytimeTicks() - played;
            sender.sendMessage(
                    detail(
                            "Your playtime",
                            PlaytimeDuration.display(played)
                                    + (remaining > 0
                                            ? " (" + PlaytimeDuration.display(remaining) + " to go)"
                                            : " (earned)")));
            sender.sendMessage(
                    detail("Manual decision", override.map(this::describe).orElse("none")));
            // Only a node set by hand says anything; unset is every ordinary player.
            if (permission != PermissionState.UNSET) {
                sender.sendMessage(
                        detail(
                                "Permission " + settings.buildPermission(),
                                permission == PermissionState.GRANTED ? "true" : "false"));
            }
            sender.sendMessage(
                    Component.text("You can build at spawn: ", NamedTextColor.GRAY)
                            .append(
                                    result.allowed()
                                            ? Component.text("yes", NamedTextColor.GREEN)
                                            : Component.text("no", NamedTextColor.RED)));
        }
    }

    private static AccessResult evaluate(PluginRuntime runtime, Player player, long played) {
        return runtime.policy()
                .evaluate(
                        played,
                        BuildPermission.of(player, runtime.settings().buildPermission()),
                        runtime.grants().registry().state(player.getUniqueId()));
    }

    /**
     * Which protections are on, by their config key, for admins and the console: what is actually
     * loaded, which may differ from the file on disk until a reload.
     */
    private void sendProtection(CommandSender sender, EnvironmentalProtection protection) {
        Map<String, Boolean> switches = new LinkedHashMap<>();
        switches.put("explosions", protection.explosions());
        switches.put("hostile-mob-changes", protection.hostileMobChanges());
        switches.put("display-damage", protection.displayDamage());
        switches.put("fire-damage", protection.fireDamage());
        switches.put("falling-blocks", protection.fallingBlocks());
        switches.put("fluid-flow", protection.fluidFlow());
        switches.put("pistons", protection.pistons());
        switches.put("dispensers", protection.dispensers());
        switches.put("growth", protection.growth());
        PortalArrivals arrivals = protection.portalArrivals();
        Map<String, Boolean> kinds = new LinkedHashMap<>();
        kinds.put("explosives", arrivals.explosives());
        kinds.put("wither-skulls", arrivals.witherSkulls());
        kinds.put("fireballs", arrivals.fireballs());
        kinds.put("projectiles", arrivals.projectiles());
        kinds.put("fireworks", arrivals.fireworks());
        kinds.put("falling-blocks", arrivals.fallingBlocks());
        sender.sendMessage(detail("Protections on", keys(switches, true)));
        sender.sendMessage(detail("Protections off", keys(switches, false)));
        sender.sendMessage(detail("Portal arrivals defused", keys(kinds, true)));
        sender.sendMessage(detail("Portal arrivals passed", keys(kinds, false)));
    }

    private static String keys(Map<String, Boolean> switches, boolean state) {
        List<String> matching = new ArrayList<>();
        switches.forEach(
                (key, on) -> {
                    if (on == state) {
                        matching.add(key);
                    }
                });
        return matching.isEmpty() ? "none" : String.join(", ", matching);
    }

    private String describe(GrantOverride override) {
        return override.state().name().toLowerCase(Locale.ROOT)
                + " by "
                + override.actor()
                + " at "
                + WHEN.format(override.at())
                + (override.reason().isEmpty() ? "" : " (" + override.reason() + ")");
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.reloadSettings();
            sender.sendMessage(success("Configuration reloaded."));
        } catch (RuntimeException ex) {
            sender.sendMessage(error("Reload failed: " + ex.getMessage()));
        }
    }

    private void handleOverride(CommandSender sender, String[] args, OverrideState state) {
        String verb = state == OverrideState.GRANTED ? GRANT : REVOKE;
        Optional<Target> target =
                resolveTarget(sender, args, "/sbr " + verb + " <player> [reason]");
        if (target.isEmpty()) {
            return;
        }
        String reason =
                GrantOverride.truncateReason(
                        String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        String actor = actorName(sender);
        if (state == OverrideState.GRANTED) {
            plugin.runtime().grants().grant(target.get().id(), target.get().name(), actor, reason);
            sender.sendMessage(
                    success(
                            target.get().name()
                                    + " can build at spawn now, whatever their playtime."));
        } else {
            plugin.runtime().grants().revoke(target.get().id(), target.get().name(), actor, reason);
            sender.sendMessage(
                    success(
                            target.get().name()
                                    + " cannot build at spawn until /sbr reset, however long they"
                                    + " play."));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        Optional<Target> target = resolveTarget(sender, args, "/sbr " + RESET + " <player>");
        if (target.isEmpty()) {
            return;
        }
        boolean removed =
                plugin.runtime()
                        .grants()
                        .reset(target.get().id(), target.get().name(), actorName(sender));
        sender.sendMessage(
                removed
                        ? success(target.get().name() + " is back to playtime deciding.")
                        : detail(target.get().name(), "had no override to remove."));
    }

    private void handleHistory(CommandSender sender, String[] args) {
        Optional<Target> target = resolveTarget(sender, args, "/sbr " + HISTORY + " <player>");
        if (target.isEmpty()) {
            return;
        }
        Optional<GrantOverride> override =
                plugin.runtime().grants().registry().override(target.get().id());
        sender.sendMessage(
                detail(
                        target.get().name() + " override",
                        override.map(this::describe).orElse("none")));
        // A grant or revoke a moment ago may still be queued for the storage thread; wait for it,
        // briefly, so the log read below includes it.
        if (!plugin.awaitStoreWrites(HISTORY_FLUSH_TIMEOUT)) {
            sender.sendMessage(error("Store writes are still pending; the log may be incomplete."));
        }
        List<GrantEvent> events;
        try {
            events = plugin.runtime().grants().history(target.get().id(), HISTORY_LIMIT);
        } catch (StorageException ex) {
            sender.sendMessage(error("Could not read the store: " + ex.getMessage()));
            return;
        }
        if (events.isEmpty()) {
            sender.sendMessage(detail("History", "nothing recorded."));
            return;
        }
        for (GrantEvent event : events) {
            String line =
                    event.kind().name().toLowerCase(Locale.ROOT)
                            + " by "
                            + event.actor()
                            + (event.kind() == GrantEvent.Kind.EARNED
                                    ? " at " + PlaytimeDuration.display(event.playtimeTicks())
                                    : "")
                            + (event.reason().isEmpty() ? "" : " (" + event.reason() + ")");
            sender.sendMessage(detail(WHEN.format(event.at()), line));
        }
    }

    /** Resolves the player named in {@code args[1]}, telling the sender why when it cannot. */
    private Optional<Target> resolveTarget(CommandSender sender, String[] args, String usage) {
        if (args.length < 2) {
            sender.sendMessage(error("Usage: " + usage));
            return Optional.empty();
        }
        Optional<Target> target = resolve(args[1]);
        if (target.isEmpty()) {
            sender.sendMessage(unknownPlayer(args[1]));
        }
        return target;
    }

    private Optional<Target> resolve(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return Optional.of(new Target(online.getUniqueId(), online.getName()));
        }
        // A UUID is accepted as-is, so an id read off a grief log or a stats file can be acted
        // on whether or not the server has a name for it.
        try {
            UUID id = UUID.fromString(name);
            String known = plugin.getServer().getOfflinePlayer(id).getName();
            return Optional.of(new Target(id, Objects.requireNonNullElse(known, name)));
        } catch (IllegalArgumentException notAnId) {
            // fall through to the name cache
        }
        // The cache only: getOfflinePlayer(String) may block on a Mojang lookup, and a player
        // nobody has ever seen here has nothing to grant or revoke anyway.
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(name);
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.of(
                new Target(
                        cached.getUniqueId(), Objects.requireNonNullElse(cached.getName(), name)));
    }

    private static String actorName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "console";
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    error("Usage: /sbr " + SET + " <" + String.join("|", SETTINGS) + "> ..."));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case THRESHOLD -> setThreshold(sender, args);
            case RADIUS -> setRadius(sender, args);
            case SHAPE -> setShape(sender, args);
            case CENTER -> setCenter(sender, args);
            default ->
                    sender.sendMessage(
                            error(
                                    "Unknown setting. Use "
                                            + THRESHOLD
                                            + ", "
                                            + RADIUS
                                            + ", "
                                            + SHAPE
                                            + " or "
                                            + CENTER
                                            + "."));
        }
    }

    private void setThreshold(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(
                    error("Usage: /sbr " + SET + " " + THRESHOLD + " <duration>  (e.g. 36h)"));
            return;
        }
        long ticks;
        try {
            // The parser ignores whitespace, so "1d 12h" typed as two words is one duration.
            ticks =
                    PlaytimeDuration.parseTicks(
                            String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(error(ex.getMessage()));
            return;
        }
        applyAndSave(
                sender,
                config -> plugin.codec().writeRequiredPlaytime(config, ticks),
                "Required playtime set to " + PlaytimeDuration.format(ticks) + ".");
    }

    private void setRadius(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Usage: /sbr " + SET + " " + RADIUS + " <blocks>"));
            return;
        }
        int radius;
        try {
            radius = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(error("Radius must be a whole number."));
            return;
        }
        if (radius < 0) {
            sender.sendMessage(error("Radius must be >= 0."));
            return;
        }
        applyAndSave(
                sender,
                config -> plugin.codec().writeRadius(config, radius),
                "Radius set to " + radius + ".");
    }

    private void setShape(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Usage: /sbr " + SET + " " + SHAPE + " <square|circle>"));
            return;
        }
        RegionShape shape;
        try {
            shape = RegionShape.fromToken(args[2]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(error(ex.getMessage()));
            return;
        }
        applyAndSave(
                sender,
                config -> plugin.codec().writeShape(config, shape),
                "Shape set to " + shape.token() + ".");
    }

    private void setCenter(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    error("Only a player can use /sbr " + SET + " " + CENTER + " here."));
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("here")) {
            sender.sendMessage(error("Usage: /sbr " + SET + " " + CENTER + " here"));
            return;
        }
        // The centre is only coordinates, so it has to come from the region's own world: the same
        // numbers in the nether or the End would move the overworld region somewhere unrelated.
        String regionWorld = plugin.runtime().settings().region().world();
        if (!player.getWorld().getName().equals(regionWorld)) {
            sender.sendMessage(
                    error("Stand in " + regionWorld + ", the region's world, to set its centre."));
            return;
        }
        // Read here, on the player's own region thread, never from inside the config edit.
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        applyAndSave(
                sender,
                config -> plugin.codec().writeCenter(config, x, z),
                "Region centre set to your location.");
    }

    private void applyAndSave(
            CommandSender sender, Consumer<FileConfiguration> edit, String message) {
        try {
            plugin.editConfig(edit);
            sender.sendMessage(success(message));
        } catch (RuntimeException ex) {
            sender.sendMessage(error("Saved, but reload failed: " + ex.getMessage()));
        }
    }

    /** Offers only {@code info} to non-admins, so the admin subcommands stay undiscoverable. */
    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(SpawnBuildRightsPlugin.ADMIN_PERMISSION);
        if (args.length == 1) {
            return filter(args[0], admin ? ADMIN_SUBCOMMANDS : List.of(INFO));
        }
        if (!admin) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && PLAYER_SUBCOMMANDS.contains(sub)) {
            List<String> names = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(args[1], names);
        }
        if (args.length == 2 && sub.equals(SET)) {
            return filter(args[1], SETTINGS);
        }
        if (args.length == 3 && sub.equals(SET)) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case SHAPE -> filter(args[2], List.of("square", "circle"));
                case CENTER -> filter(args[2], List.of("here"));
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }

    private static Component detail(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private static Component success(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    private static Component unknownPlayer(String name) {
        return error(
                "No player called '"
                        + name
                        + "' has joined this server. Use a name the server has seen, or a UUID.");
    }

    private static Component noPermission() {
        return error("You do not have permission to do that.");
    }
}
