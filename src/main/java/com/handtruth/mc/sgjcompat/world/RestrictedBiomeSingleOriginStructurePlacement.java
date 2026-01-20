package com.handtruth.mc.sgjcompat.world;

import com.handtruth.mc.sgjcompat.util.WithBiomeSource;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public final class RestrictedBiomeSingleOriginStructurePlacement extends StructurePlacement {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Codec<RestrictedBiomeSingleOriginStructurePlacement> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Biome.LIST_CODEC.fieldOf("biomes").forGetter(RestrictedBiomeSingleOriginStructurePlacement::getBiomes),
                    Codec.INT.optionalFieldOf("minBuildHeight", 0).forGetter(RestrictedBiomeSingleOriginStructurePlacement::getMinBuildHeight),
                    Codec.INT.optionalFieldOf("height", 256).forGetter(RestrictedBiomeSingleOriginStructurePlacement::getHeight),
                    Codec.INT.optionalFieldOf("radius", 5).forGetter(RestrictedBiomeSingleOriginStructurePlacement::getRadius)
            ).apply(instance, RestrictedBiomeSingleOriginStructurePlacement::new)
    );

    final HolderSet<Biome> biomes;

    final int minBuildHeight, height, radius;

    public HolderSet<Biome> getBiomes() {
        return biomes;
    }

    public int getMinBuildHeight() {
        return minBuildHeight;
    }

    public int getHeight() {
        return height;
    }

    public int getRadius() {
        return radius;
    }

    public RestrictedBiomeSingleOriginStructurePlacement(final HolderSet<Biome> biomes, final int minBuildHeight, final int height, final int radius) {
        super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1f, 0, Optional.empty());
        this.biomes = biomes;
        this.minBuildHeight = minBuildHeight;
        this.height = height;
        this.radius = radius;
    }

    private Optional<ChunkPos> findGoodChunk(ChunkGeneratorStructureState generatorStructureState) {
        final WithBiomeSource accessor = (WithBiomeSource) (Object) generatorStructureState;
        final BiomeSource biomeSource = accessor.getBiomeSource();
        final LevelReader levelReader = new LevelReaderImpl(minBuildHeight, height);
        final Climate.Sampler sampler = generatorStructureState.randomState().sampler();
        final Pair<BlockPos, Holder<Biome>> pair = biomeSource.findClosestBiome3d(BlockPos.ZERO, 6400, 32, 64, biomes::contains, sampler, levelReader);
        if (pair == null) {
            LOGGER.info("biome not found for a stargate");
            return Optional.empty();
        }
        final ChunkPos center = new ChunkPos(pair.getFirst());
        for (int x = center.x - radius; x <= center.x + radius; ++x) {
            for (int z = center.z - radius; z <= center.z + radius; ++z) {
                final ChunkPos chunk = new ChunkPos(x, z);
                final Set<Holder<Biome>> biomesInChunk = biomeSource.getBiomesWithin(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ(), sampler);
                if (biomesInChunk.stream().allMatch(biomes::contains)) {
                    LOGGER.info("found a good location for stargate {}", chunk);
                    return Optional.of(chunk);
                }
            }
        }
        LOGGER.info("biome is too small for a stargate");
        return Optional.empty();
    }

    private final Map<ChunkGeneratorStructureState, Optional<ChunkPos>> cache = new HashMap<>();

    public Optional<ChunkPos> getGoodChunk(final ChunkGeneratorStructureState generatorStructureState) {
        return cache.computeIfAbsent(generatorStructureState, this::findGoodChunk);
    }

    @Override
    public boolean isStructureChunk(final ChunkGeneratorStructureState generatorStructureState, final int x, final int z) {
        return isPlacementChunk(generatorStructureState, x, z);
    }

    @Override
    protected boolean isPlacementChunk(final ChunkGeneratorStructureState generatorStructureState, final int x, final int z) {
        return getGoodChunk(generatorStructureState)
                .map(chunk -> chunk.x == x && chunk.z == z)
                .orElse(false);
    }

    @Override
    public StructurePlacementType<?> type() {
        return SgjCompatStructurePlacementTypes.RESTRICTED_BIOME_SINGLE_ORIGIN.get();
    }

    private record LevelReaderImpl(int minBuildHeight, int height) implements LevelReader {
        @Override
        public int getHeight(Heightmap.Types p_46827_, int p_46828_, int p_46829_) {
            return height;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int getMinBuildHeight() {
            return minBuildHeight;
        }

        @Override
        public int minBuildHeight() {
            return minBuildHeight;
        }

        @Override
        public int getMaxBuildHeight() {
            return minBuildHeight + height;
        }

        @Override
        public @Nullable ChunkAccess getChunk(int p_46823_, int p_46824_, ChunkStatus p_46825_, boolean p_46826_) {
            return null;
        }

        @Override
        public boolean hasChunk(int p_46838_, int p_46839_) {
            return false;
        }

        @Override
        public int getSkyDarken() {
            return 0;
        }

        @Override
        public BiomeManager getBiomeManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Holder<Biome> getUncachedNoiseBiome(int p_204159_, int p_204160_, int p_204161_) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isClientSide() {
            return false;
        }

        @Override
        public int getSeaLevel() {
            return 0;
        }

        @Override
        public DimensionType dimensionType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RegistryAccess registryAccess() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FeatureFlagSet enabledFeatures() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getShade(Direction p_45522_, boolean p_45523_) {
            return 0;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldBorder getWorldBorder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<VoxelShape> getEntityCollisions(@Nullable Entity p_186427_, AABB p_186428_) {
            return List.of();
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos p_45570_) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos p_45571_) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FluidState getFluidState(BlockPos p_45569_) {
            throw new UnsupportedOperationException();
        }
    }
}
