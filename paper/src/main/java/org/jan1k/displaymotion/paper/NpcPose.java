package org.jan1k.displaymotion.paper;

import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;

/** The poses a vanilla mannequin can take. */
public enum NpcPose {
    STANDING(EntityPose.STANDING),
    CROUCHING(EntityPose.CROUCHING),
    /** Lying flat on the belly, like a player swimming. */
    SWIMMING(EntityPose.SWIMMING),
    /** Stretched out like a player flying with an elytra. */
    GLIDING(EntityPose.FALL_FLYING),
    /** Lying on the back. */
    SLEEPING(EntityPose.SLEEPING);

    final EntityPose packet;

    NpcPose(EntityPose packet) {
        this.packet = packet;
    }
}
