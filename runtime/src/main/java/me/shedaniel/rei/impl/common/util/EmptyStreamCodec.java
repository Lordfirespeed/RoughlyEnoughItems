package me.shedaniel.rei.impl.common.util;

import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class EmptyStreamCodec<B, V> implements StreamCodec<B, V> {
    private final Supplier<V> factory;
    
    public EmptyStreamCodec(final Supplier<V> factory) {
        this.factory = factory;
    }
    
    @Override
    public @NotNull V decode(B object) {
        return factory.get();
    }
    
    @Override
    public void encode(B object, V object2) {}
}
