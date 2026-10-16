package com.lewisgibson.spawnbuildrights.bukkit.message;

import com.lewisgibson.spawnbuildrights.core.message.Placeholders;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Renders MiniMessage templates (after placeholder substitution) and sends them to a recipient. */
public final class BukkitMessenger {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /**
     * Renders {@code template} and sends it as a chat message. A blank template is skipped so an
     * intentionally empty message never sends a stray line.
     *
     * @param audience the recipient (a Bukkit {@code Player} is an {@link Audience})
     * @param template the MiniMessage template, possibly containing {@code %name%} placeholders
     * @param placeholders the placeholder values keyed by name
     */
    public void send(Audience audience, String template, Map<String, String> placeholders) {
        Component rendered = render(template, placeholders);
        if (rendered != null) {
            audience.sendMessage(rendered);
        }
    }

    /**
     * Renders {@code template} and shows it on the action bar, the line above the hotbar. Used for
     * the denial messages because an action bar overwrites itself: a player who keeps trying to dig
     * at spawn sees one line that stays current instead of a chat window full of repeats.
     *
     * @param audience the recipient
     * @param template the MiniMessage template, possibly containing {@code %name%} placeholders
     * @param placeholders the placeholder values keyed by name
     */
    public void sendActionBar(
            Audience audience, String template, Map<String, String> placeholders) {
        Component rendered = render(template, placeholders);
        if (rendered != null) {
            audience.sendActionBar(rendered);
        }
    }

    private Component render(String template, Map<String, String> placeholders) {
        if (template.isBlank()) {
            return null;
        }
        return miniMessage.deserialize(Placeholders.apply(template, placeholders));
    }
}
