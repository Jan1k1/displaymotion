package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class FakePacketEvents {

    static final ServerVersion VERSION = ServerVersion.V_26_3;
    static final List<ByteBuf> WRITTEN = new CopyOnWriteArrayList<>();

    private FakePacketEvents() {
    }

    static void install() {
        NettyManager netty = new NettyManagerImpl();
        ServerManager server = () -> VERSION;
        ProtocolManager protocol = proxy(ProtocolManager.class, (method, args) -> {
            if (method.equals("writePacket") && args.length == 2 && args[1] instanceof ByteBuf buffer) {
                WRITTEN.add(buffer);
                ((Channel) args[0]).write(buffer);
            }
            return null;
        });
        ChannelInjector injector = proxy(ChannelInjector.class, (method, args) -> method.equals("isProxy") ? false : null);
        PacketEvents.setAPI(new PacketEventsAPI<>() {
            @Override
            public boolean isLoaded() {
                return true;
            }

            @Override
            public void init() {
            }

            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public Object getPlugin() {
                return null;
            }

            @Override
            public ServerManager getServerManager() {
                return server;
            }

            @Override
            public ProtocolManager getProtocolManager() {
                return protocol;
            }

            @Override
            public PlayerManager getPlayerManager() {
                return null;
            }

            @Override
            public NettyManager getNettyManager() {
                return netty;
            }

            @Override
            public ChannelInjector getInjector() {
                return injector;
            }
        });
    }

    interface Handler {
        Object handle(String method, Object[] args);
    }

    static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            Object[] safe = args == null ? new Object[0] : args;
            return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == safe[0];
                case "toString" -> type.getSimpleName() + " proxy";
                default -> handler.handle(method.getName(), safe);
            };
        }));
    }
}
