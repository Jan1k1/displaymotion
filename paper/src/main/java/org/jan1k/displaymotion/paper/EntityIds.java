package org.jan1k.displaymotion.paper;

import org.bukkit.Bukkit;
import org.bukkit.UnsafeValues;
import org.bukkit.World;

import java.lang.reflect.Method;

final class EntityIds {
    private static final Method WITH_WORLD = find(World.class);
    private static final Method NO_ARGS = find();

    private EntityIds() {
    }

    // 1.21 has nextEntityId() and 26.x wants the world, so we look for the one that is there
    static int next(World world) {
        try {
            Object unsafe = Bukkit.getUnsafe();
            return (int) (WITH_WORLD != null ? WITH_WORLD.invoke(unsafe, world) : NO_ARGS.invoke(unsafe));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not get an entity id from the server", e);
        }
    }

    private static Method find(Class<?>... args) {
        try {
            return UnsafeValues.class.getMethod("nextEntityId", args);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
