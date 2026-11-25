package com.pockyl.handson_letsdo.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

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
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-cook"), ClientInformation.createDefault()) {
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
        channel.pipeline().fireChannelActive();
        try {
            level.getServer().getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        } catch (UnsupportedOperationException refused) {
            // NeoForge refuses a mod's packet to a connection that agreed on no channels (Foundation sends one on joining): the
            // player is in the list by then, which is all the tests need.
        }
        return player;
    }

    static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
