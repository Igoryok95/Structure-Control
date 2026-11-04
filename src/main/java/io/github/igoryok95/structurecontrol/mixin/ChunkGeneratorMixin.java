package io.github.igoryok95.structurecontrol.mixin;

import io.github.igoryok95.structurecontrol.StructureControl;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.function.Consumer;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Redirect(method = "getMobsAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/StructureManager;fillStartsForStructure(Lnet/minecraft/world/level/levelgen/structure/Structure;Lit/unimi/dsi/fastutil/longs/LongSet;Ljava/util/function/Consumer;)V"))
    private void structurecontrol$filterDisabled(StructureManager manager, Structure type, LongSet starts, Consumer<StructureStart> consumer) {
        var accessor = ((StructureManagerAccessor) manager).structurecontrol$level();
        manager.fillStartsForStructure(type, starts, start -> {
            if (!(accessor instanceof WorldGenLevel level) || !StructureControl.disabled(level.getLevel(), start)) {
                consumer.accept(start);
            }
        });
    }
}
