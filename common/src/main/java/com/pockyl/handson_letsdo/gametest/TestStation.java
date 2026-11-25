package com.pockyl.handson_letsdo.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A station of a type at a block, as the library would make it, with nobody at it; it remembers the effects played at it. */
final class TestStation implements StationContext {
    private final Level level;
    private final BlockPos pos;
    private final StationType type;
    private final StationState state;
    final List<Player> users = new ArrayList<>();
    final List<ResourceLocation> effects = new ArrayList<>();

    TestStation(Level level, BlockPos pos, StationType type) {
        this.level = level;
        this.pos = pos;
        this.type = type;
        this.state = new StationState(type.schema());
    }

    @Override
    public StationType type() {
        return type;
    }

    @Override
    public Level level() {
        return level;
    }

    @Override
    public BlockPos pos() {
        return pos;
    }

    @Override
    public BlockState blockState() {
        return level.getBlockState(pos);
    }

    @Override
    public Direction facing() {
        return Direction.NORTH;
    }

    @Override
    public StationState state() {
        return state;
    }

    @Override
    public Collection<? extends Player> users() {
        return users;
    }

    @Override
    public void effect(ResourceLocation effect, String node, CompoundTag data) {
        effects.add(effect);
    }
}
