package com.handtruth.mc.sgjcompat.mixin;

import com.handtruth.mc.sgjcompat.util.WithBiomeSource;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkGeneratorStructureState.class)
public interface ChunkGeneratorStructureStateMixin extends WithBiomeSource {
    @Override
    @Accessor
    BiomeSource getBiomeSource();
}
