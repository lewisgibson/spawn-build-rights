package com.lewisgibson.spawnbuildrights.fixture;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

/**
 * A {@link Player} that exists only as far as the plugin under test looks: an id, a name, a
 * playtime statistic, a permission node and somewhere to receive an action bar. Any other call
 * throws, naming the method, so a new dependency on the player surfaces as a test failure with a
 * clear message rather than a silent default.
 */
final class FakePlayer implements InvocationHandler {

    /** The offline-mode id Paper would give a player called TestPlayer. */
    static final String NAME = "TestPlayer";

    static final UUID ID =
            UUID.nameUUIDFromBytes(("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));

    private final long playtimeTicks;
    private final boolean granted;
    private final boolean revoked;
    private volatile Component lastActionBar;

    FakePlayer(long playtimeTicks, boolean granted, boolean revoked) {
        this.playtimeTicks = playtimeTicks;
        this.granted = granted;
        this.revoked = revoked;
    }

    Player proxy() {
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(), new Class<?>[] {Player.class}, this);
    }

    Component lastActionBar() {
        return lastActionBar;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "getUniqueId":
                return ID;
            case "getName":
                return NAME;
            case "getStatistic":
                if (args.length == 1 && args[0] == Statistic.PLAY_ONE_MINUTE) {
                    return (int) playtimeTicks;
                }
                break;
            case "hasPermission":
                return granted;
            case "isPermissionSet":
                return granted || revoked;
            case "sendActionBar":
                if (args.length == 1 && args[0] instanceof Component component) {
                    lastActionBar = component;
                    return null;
                }
                break;
            case "sendMessage":
                return null;
            case "isOp":
                return false;
            case "hashCode":
                return ID.hashCode();
            case "equals":
                return proxy == args[0];
            case "toString":
                return "FakePlayer[" + NAME + "]";
            default:
                break;
        }
        throw new UnsupportedOperationException(
                "FakePlayer does not implement "
                        + method
                        + " - the plugin under test now needs it");
    }
}
