package com.github.lumin.mixins;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface IMinecraft {

    @Accessor("rightClickDelay")
    void setRightClickDelay(int rightClickDelay);

    @Accessor("rightClickDelay")
    int getRightClickDelay();

}
