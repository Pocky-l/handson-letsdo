package com.pockyl.handson_letsdo.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Server players for the tests that need one in the level: who has learned a recipe, who has a menu open, what a player has
 * crafted. Vanilla's own mock joins through a connection with no channel, which the mods' join hooks do not survive; this one
 * joins through a channel that goes nowhere.
 */
final class TestPlayers {
    private TestPlayers() {
    }

    /** A player who joins the test's level as a real one does; {@link #leave} takes them out again. */
    static ServerPlayer join(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-cook")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel();
        // Whatever is sent to them is dropped: nobody reads it.
        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                ReferenceCountUtil.release(message);
                promise.setSuccess();
            }
        });
        channel.pipeline().addLast("packet_handler", connection);
        // Forge reads from the channel which kind of client joined: one without Forge, which is sent nothing of the mods'.
        channel.attr(AttributeKey.<String>valueOf("fml:netversion")).set("NONE");
        channel.pipeline().fireChannelActive();
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
