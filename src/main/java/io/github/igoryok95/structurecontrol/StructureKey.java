package io.github.igoryok95.structurecontrol;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record StructureKey(ResourceLocation dimension, ResourceLocation structure, int chunkX, int chunkZ) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.toString());
        tag.putString("structure", structure.toString());
        tag.putInt("chunk_x", chunkX);
        tag.putInt("chunk_z", chunkZ);
        return tag;
    }

    public static StructureKey load(CompoundTag tag) {
        if (!tag.contains("chunk_x", 3) || !tag.contains("chunk_z", 3)) {
            throw new IllegalArgumentException("Structure key has no start coordinates");
        }
        return new StructureKey(ResourceLocation.parse(tag.getString("dimension")),
                ResourceLocation.parse(tag.getString("structure")), tag.getInt("chunk_x"), tag.getInt("chunk_z"));
    }
}
