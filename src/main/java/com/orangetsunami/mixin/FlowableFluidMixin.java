package com.orangetsunami.mixin;

import com.orangetsunami.TsunamiManager;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Impede que a água do tsunami escorra/espalhe sozinha (assim ela some 100% ao final). */
@Mixin(FlowableFluid.class)
public class FlowableFluidMixin {
    @Inject(method = "onScheduledTick", at = @At("HEAD"), cancellable = true)
    private void orangetsunami$freeze(World world, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (TsunamiManager.isWave(world, pos)) ci.cancel();
    }
}
