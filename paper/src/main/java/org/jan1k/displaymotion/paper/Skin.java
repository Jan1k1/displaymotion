package org.jan1k.displaymotion.paper;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemProfile;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The skin of a mannequin, see {@link ModelBuilder#npc(String, Skin)}. */
public final class Skin {

    private final ItemProfile profile;

    private Skin(ItemProfile profile) {
        this.profile = profile;
    }

    /** The skin this player has now. Works on offline mode servers too, as long as the player has one. */
    public static Skin of(Player player) {
        Objects.requireNonNull(player, "player");
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if (property.getName().equals("textures")) {
                return texture(property.getValue(), property.getSignature());
            }
        }
        return name(player.getName());
    }

    /**
     * The skin of the Minecraft account with this name. The players' games look it up by themselves,
     * so the server needs no internet and no online mode for it.
     */
    public static Skin name(String name) {
        Objects.requireNonNull(name, "name");
        return new Skin(new ItemProfile(name, null, List.of()));
    }

    /** A skin from its texture value and signature, for example from mineskin.org. The signature may be {@code null}. */
    public static Skin texture(String value, String signature) {
        Objects.requireNonNull(value, "value");
        return new Skin(new ItemProfile(null, UUID.randomUUID(), List.of(new ItemProfile.Property("textures", value, signature))));
    }

    /** The default skin, Steve or Alex. */
    public static Skin none() {
        return new Skin(new ItemProfile(null, null, List.of()));
    }

    ItemProfile profile() {
        return profile;
    }
}
