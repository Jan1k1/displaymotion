package org.jan1k.displaymotion.packetevents;

import java.util.UUID;

record PlayerView(UUID id, UUID world, double x, double y, double z, Object channel) {
}
