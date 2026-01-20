package com.handtruth.mc.sgjcompat.compat.create;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackPropagator;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.povstalec.sgjourney.common.block_entities.stargate.AbstractStargateEntity;
import net.povstalec.sgjourney.common.blocks.stargate.AbstractStargateBaseBlock;
import net.povstalec.sgjourney.common.events.custom.ConnectionEvent;
import net.povstalec.sgjourney.common.sgjourney.StargateConnection;
import net.povstalec.sgjourney.common.sgjourney.stargate.Stargate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CreateCompatibility {

    public static List<Runnable> serverTickTasks = new ArrayList<>();

    @SubscribeEvent
    public static void handleServerTick(final TickEvent.ServerTickEvent event) {
        final List<Runnable> tasks = serverTickTasks;
        if (tasks.isEmpty()) {
            return;
        }
        serverTickTasks = new ArrayList<>();
        for (final Runnable task : tasks) {
            task.run();
        }
    }

    @SubscribeEvent
    public static void handleStargateConnect(final ConnectionEvent.Establish event) {
        final StargateConnection connection = event.getStargateConnection();
        final Stargate dialingStargate = connection.getDialingStargate();
        if (dialingStargate == null) {
            return;
        }
        final Stargate dialedStargate = connection.getDialedStargate();
        if (dialedStargate == null) {
            return;
        }
        final MinecraftServer server = event.getServer();
        unwrapStargate(server, dialingStargate).ifPresent(source -> {
            unwrapStargate(server, dialedStargate).ifPresent(target -> {
                final Runnable task = new StargateTrackConnectTask(source, target);
                serverTickTasks.add(task);
            });
        });
    }

    @SubscribeEvent
    public static void handleStargateDisconnect(final ConnectionEvent.Terminate event) {
        final StargateConnection connection = event.getStargateConnection();
        final MinecraftServer server = event.getServer();
        final Optional<TrackInfo> source = Optional.ofNullable(connection.getDialingStargate())
                .flatMap(stargate -> unwrapStargate(server, stargate))
                .flatMap(CreateCompatibility::resetTrack);
        final Optional<TrackInfo> target = Optional.ofNullable(connection.getDialedStargate())
                .flatMap(stargate -> unwrapStargate(server, stargate))
                .flatMap(CreateCompatibility::resetTrack);
        source.ifPresent(CreateCompatibility::disconnectTrack);
        target.ifPresent(CreateCompatibility::disconnectTrack);
    }

    private static final Vec3 CENTER_OFFSET = new Vec3(.5, .5, .5);

    private static Optional<AbstractStargateEntity> unwrapStargate(final MinecraftServer server, final Stargate stargate) {
        final Vec3 rawPosition = stargate.getPosition();
        if (rawPosition == null) {
            return Optional.empty();
        }
        final Vec3 cornerPosition = rawPosition.subtract(CENTER_OFFSET);
        if (!(isInteger(cornerPosition.x) && isInteger(cornerPosition.y) && isInteger(cornerPosition.z))) {
            return Optional.empty();
        }
        final BlockPos position = new BlockPos((int) cornerPosition.x, (int) cornerPosition.y, (int) cornerPosition.z);
        final Level level = stargate.getLevel(server);
        if (level == null) {
            return Optional.empty();
        }
        final BlockEntity entity = level.getBlockEntity(position);
        if (entity == null) {
            return Optional.empty();
        }
        return entity instanceof AbstractStargateEntity stargateEntity ? Optional.of(stargateEntity) : Optional.empty();
    }

    private static boolean isInteger(final double number) {
        return (number == Math.floor(number)) && !Double.isInfinite(number);
    }

    private static void disconnectTrack(final TrackInfo track) {
        getExpectedTrackShape(track.facing).ifPresent(newShape -> {
            final BlockState newTrackState = track.state
                    .setValue(TrackBlock.HAS_BE, false)
                    .setValue(TrackBlock.SHAPE, newShape);
            track.dimension.removeBlockEntity(track.position);
            track.dimension.setBlock(track.position, newTrackState, 0);
            TrackPropagator.onRailAdded(track.dimension, track.position, newTrackState);
        });
    }

    private static Optional<TrackInfo> resetTrack(final AbstractStargateEntity stargate) {
        return getTrack(stargate).flatMap(track -> {
            final BlockState trackState = track.state;
            final Direction stargateFacing = track.facing;
            final TrackShape trackShape = track.shape;
            final TrackShape expectedTrackShape = switch (stargateFacing) {
                case DOWN, UP -> null;
                default -> TrackShape.asPortal(stargateFacing.getOpposite());
            };
            return trackShape == expectedTrackShape ? Optional.of(track) : Optional.empty();
        });
    }

    private static TrackBlockEntity setupTrack(final TrackInfo track) {
        final BlockState newTrackState = track.state
                .setValue(TrackBlock.HAS_BE, true)
                .setValue(TrackBlock.SHAPE, TrackShape.asPortal(track.facing.getOpposite()));
        track.dimension.setBlockAndUpdate(track.position, newTrackState);
        return (TrackBlockEntity) track.dimension.getBlockEntity(track.position);
    }

    private static Optional<TrackInfo> checkTrack(final AbstractStargateEntity stargate) {
        return getTrack(stargate).flatMap(track -> {
            final BlockState trackState = track.state;
            final Direction stargateFacing = track.facing;
            final TrackShape shape = track.shape;
            return getExpectedTrackShape(stargateFacing).flatMap(expectedTrackShape -> {
                return shape == expectedTrackShape ? Optional.of(track) : Optional.empty();
            });
        });
    }

    private static Optional<TrackInfo> getTrack(final AbstractStargateEntity stargate) {
        final Level dimension = stargate.getLevel();
        if (dimension == null) {
            return Optional.empty();
        }
        final BlockPos stargatePos = stargate.getBlockPos();
        final BlockState stargateState = dimension.getBlockState(stargatePos);
        if (!(stargateState.getBlock() instanceof AbstractStargateBaseBlock)) {
            return Optional.empty();
        }
        final Direction stargateFacing = stargateState.getValue(AbstractStargateBaseBlock.FACING);
        final BlockPos trackPos = stargatePos.relative(stargateFacing).above();
        final BlockState trackState = dimension.getBlockState(trackPos);
        if (!(trackState.getBlock() instanceof TrackBlock)) {
            return Optional.empty();
        }
        final TrackShape shape = trackState.getValue(TrackBlock.SHAPE);
        return Optional.of(new TrackInfo(trackPos, trackState, dimension, stargateFacing, shape));
    }

    private static Optional<TrackShape> getExpectedTrackShape(final Direction stargateFacing) {
        final TrackShape shape = switch (stargateFacing) {
            case NORTH, SOUTH -> TrackShape.ZO;
            case WEST, EAST -> TrackShape.XO;
            default -> null;
        };
        return Optional.ofNullable(shape);
    }

    private record TrackInfo(BlockPos position, BlockState state, Level dimension, Direction facing, TrackShape shape) {}

    private static class StargateTrackConnectTask implements Runnable {
        private final AbstractStargateEntity sourceStargate;
        private final AbstractStargateEntity targetStargate;

        private StargateTrackConnectTask(AbstractStargateEntity source, AbstractStargateEntity target) {
            this.sourceStargate = source;
            this.targetStargate = target;
        }

        @Override
        public void run() {
            if (!sourceStargate.isConnected() || sourceStargate.isRemoved()) {
                return;
            }
            if (sourceStargate.getOpenTime() <= 0) {
                serverTickTasks.add(this);
                return;
            }
            checkTrack(sourceStargate).ifPresent(source -> {
                checkTrack(targetStargate).ifPresent(target -> {
                    final TrackBlockEntity sourceBlockEntity = setupTrack(source);
                    final TrackBlockEntity targetBlockEntity = setupTrack(target);
                    sourceBlockEntity.bind(target.dimension.dimension(), target.position);
                    targetBlockEntity.bind(source.dimension.dimension(), source.position);
                    TrackPropagator.onRailAdded(source.dimension, source.position, sourceBlockEntity.getBlockState());
                    TrackPropagator.onRailAdded(target.dimension, target.position, targetBlockEntity.getBlockState());
                });
            });
        }
    }
}
