package com.github.lumin.mixins;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface IClientInput {

    @Accessor("moveVector")
    void setMoveVector(Vec2 moveVector);

    @Accessor("keyPresses")
    void setKeyPresses(Input keyPresses);

    @Accessor("keyPresses")
    Input getKeyPresses();

}
