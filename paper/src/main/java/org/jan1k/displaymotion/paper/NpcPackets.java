package org.jan1k.displaymotion.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemSwingAnimation;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSwingAnimation;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// the data of a mannequin. the slots come from Entity, LivingEntity, Avatar and Mannequin in 26.3, they are not
// the display slots at all: 17 is the skin here and the view range on a display, so never mix the two writers
final class NpcPackets {

    private static final int FLAGS_SLOT = 0;
    private static final int NAME_SLOT = 2;
    private static final int NAME_VISIBLE_SLOT = 3;
    private static final int POSE_SLOT = 6;
    private static final int SKIN_LAYERS_SLOT = 16;
    private static final int PROFILE_SLOT = 17;
    private static final int IMMOVABLE_SLOT = 18;
    private static final int DESCRIPTION_SLOT = 19;
    private static final byte ALL_LAYERS = 0x7F;
    private static final byte GLOWING = 0x40;
    private static final int SWING_TICKS = 6;

    private NpcPackets() {
    }

    static WrapperPlayServerEntityMetadata data(PartState npc) {
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(FLAGS_SLOT, EntityDataTypes.BYTE, npc.glowing ? GLOWING : (byte) 0));
        data.add(new EntityData<>(NAME_SLOT, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.ofNullable(npc.npcName)));
        data.add(new EntityData<>(NAME_VISIBLE_SLOT, EntityDataTypes.BOOLEAN, npc.npcName != null));
        data.add(new EntityData<>(POSE_SLOT, EntityDataTypes.ENTITY_POSE, npc.npcPose.packet));
        data.add(new EntityData<>(SKIN_LAYERS_SLOT, EntityDataTypes.BYTE, ALL_LAYERS));
        data.add(new EntityData<>(PROFILE_SLOT, EntityDataTypes.RESOLVABLE_PROFILE, npc.skin.profile()));
        data.add(new EntityData<>(IMMOVABLE_SLOT, EntityDataTypes.BOOLEAN, true));
        data.add(new EntityData<>(DESCRIPTION_SLOT, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.ofNullable(npc.description)));
        return new WrapperPlayServerEntityMetadata(npc.entityId, data);
    }

    // 26.3 took the swing out of the animate packet and gave it its own one, the old swing throws there.
    // games older than 26.3 never tick the swing of a mannequin, so they dont show it at all
    static PacketWrapper<?> swing(PartState npc, boolean offHand) {
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_26_3)) {
            return new WrapperPlayServerSwingAnimation(npc.entityId, offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                    new ItemSwingAnimation(ItemSwingAnimation.Type.WHACK, SWING_TICKS));
        }
        return new WrapperPlayServerEntityAnimation(npc.entityId, offHand
                ? WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND
                : WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM);
    }

    static WrapperPlayServerEntityEquipment equipment(PartState npc) {
        List<Equipment> items = new ArrayList<>();
        for (Map.Entry<EquipmentSlot, com.github.retrooper.packetevents.protocol.item.ItemStack> entry : npc.equipment.entrySet()) {
            items.add(new Equipment(entry.getKey(), entry.getValue()));
        }
        return new WrapperPlayServerEntityEquipment(npc.entityId, items);
    }

    static EquipmentSlot slot(org.bukkit.inventory.EquipmentSlot slot) {
        if (slot.name().equals("SADDLE")) {
            return EquipmentSlot.SADDLE;
        }
        return switch (slot) {
            case HAND -> EquipmentSlot.MAIN_HAND;
            case OFF_HAND -> EquipmentSlot.OFF_HAND;
            case HEAD -> EquipmentSlot.HELMET;
            case CHEST -> EquipmentSlot.CHEST_PLATE;
            case LEGS -> EquipmentSlot.LEGGINGS;
            case FEET -> EquipmentSlot.BOOTS;
            case BODY -> EquipmentSlot.BODY;
            default -> throw new IllegalArgumentException("no mannequin slot for " + slot);
        };
    }

    static com.github.retrooper.packetevents.protocol.item.ItemStack item(ItemStack item) {
        return item == null ? com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY : SpigotConversionUtil.fromBukkitItemStack(item);
    }
}
