package com.handtruth.mc.sgjcompat.mixin;

import com.handtruth.mc.sgjcompat.world.RestrictedBiomeSingleOriginStructurePlacement;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    @Inject(method = "findNearestMapStructure", at = @At(value = "HEAD"), cancellable = true)
    public void findNearestMapStructureInject(final ServerLevel level,
                                              final HolderSet<Structure> structures,
                                              final BlockPos pos,
                                              final int p_223041_,
                                              final boolean p_223042_,
                                              final CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> callback) {
        final ChunkGeneratorStructureState chunkgeneratorstructurestate = level.getChunkSource().getGeneratorState();
        for (final Holder<Structure> holder : structures) {
            for (final StructurePlacement structureplacement : chunkgeneratorstructurestate.getPlacementsForStructure(holder)) {
                if (structureplacement instanceof final RestrictedBiomeSingleOriginStructurePlacement placement) {
                    final Optional<ChunkPos> chunkPos = placement.getGoodChunk(chunkgeneratorstructurestate);
                    if (chunkPos.isPresent()) {
                        callback.setReturnValue(Pair.of(chunkPos.get().getMiddleBlockPosition(60), holder));
                        return;
                    }
                }
            }
        }
    }
}
