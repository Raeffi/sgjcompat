package com.handtruth.mc.sgjcompat.world;

import com.handtruth.mc.sgjcompat.SgjCompatMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class SgjCompatStructurePlacementTypes {
    public static final DeferredRegister<StructurePlacementType<?>> DEFERRED_REGISTRY_STRUCTURE_PLACEMENT_TYPE =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, SgjCompatMod.MODID);

    public static final RegistryObject<StructurePlacementType<RestrictedBiomeSingleOriginStructurePlacement>> RESTRICTED_BIOME_SINGLE_ORIGIN =
            DEFERRED_REGISTRY_STRUCTURE_PLACEMENT_TYPE.register("restricted_biome_single_origin", () -> explicitStructureTypeTyping(RestrictedBiomeSingleOriginStructurePlacement.CODEC));

    private static <T extends StructurePlacement> StructurePlacementType<T> explicitStructureTypeTyping(final Codec<T> structurePlacementTypeCodec) {
        return () -> structurePlacementTypeCodec;
    }

    public static void register(final IEventBus modBus) {
        DEFERRED_REGISTRY_STRUCTURE_PLACEMENT_TYPE.register(modBus);
    }
}
