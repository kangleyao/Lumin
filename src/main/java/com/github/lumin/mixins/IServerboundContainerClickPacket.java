package com.github.lumin.mixins;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundContainerClickPacket.class)
public interface IServerboundContainerClickPacket {

    @Accessor("clickType")
    ClickType getClickType();

}
