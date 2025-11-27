/*
 * This file is licensed under the MIT License, part of Roughly Enough Items.
 * Copyright (c) 2018, 2019, 2020, 2021, 2022, 2023 shedaniel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.shedaniel.rei;

import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.transformers.SplitPacketTransformer;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.InputIngredient;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessorRegistry;
import me.shedaniel.rei.impl.common.networking.DisplaySyncPacket;
import me.shedaniel.rei.impl.common.transfer.InputSlotCrafter;
import me.shedaniel.rei.impl.common.transfer.NewInputSlotCrafter;
import me.shedaniel.rei.impl.common.util.EmptyStreamCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RoughlyEnoughItemsNetwork {
    public static final ResourceLocation DELETE_ITEMS_PACKET = ResourceLocation.fromNamespaceAndPath("roughlyenoughitems", "delete_item");
    public static final ResourceLocation CREATE_ITEMS_PACKET = ResourceLocation.fromNamespaceAndPath("roughlyenoughitems", "create_item");
    public static final ResourceLocation CREATE_ITEMS_HOTBAR_PACKET = ResourceLocation.fromNamespaceAndPath("roughlyenoughitems", "create_item_hotbar");
    public static final ResourceLocation CREATE_ITEMS_GRAB_PACKET = ResourceLocation.fromNamespaceAndPath("roughlyenoughitems", "create_item_grab");
    public static final ResourceLocation CREATE_ITEMS_MESSAGE_PACKET = ResourceLocation.fromNamespaceAndPath("roughlyenoughitems", "ci_msg");
    public static final ResourceLocation MOVE_ITEMS_NEW_PACKET = ResourceLocation.fromNamespaceAndPath("roughlyenoughitems", "move_items_new");
    public static final ResourceLocation NOT_ENOUGH_ITEMS_PACKET = ResourceLocation.fromNamespaceAndPath("roughlyenoughitems", "og_not_enough");
    public static final ResourceLocation SYNC_DISPLAYS_PACKET = ResourceLocation.fromNamespaceAndPath("roughlyenoughitems", "sync_displays");
    
    public static final CustomPacketPayload.Type<DeleteItemsPacketPayload> DELETE_ITEMS_PACKET_TYPE = new CustomPacketPayload.Type<>(DELETE_ITEMS_PACKET);
    public static final CustomPacketPayload.Type<CreateItemsPacketPayload> CREATE_ITEMS_PACKET_TYPE = new CustomPacketPayload.Type<>(CREATE_ITEMS_PACKET);
    public static final CustomPacketPayload.Type<CreateItemsHotbarPacketPayload> CREATE_ITEMS_HOTBAR_PACKET_TYPE = new CustomPacketPayload.Type<>(CREATE_ITEMS_HOTBAR_PACKET);
    public static final CustomPacketPayload.Type<CreateItemsGrabPacketPayload> CREATE_ITEMS_GRAB_PACKET_TYPE = new CustomPacketPayload.Type<>(CREATE_ITEMS_GRAB_PACKET);
    public static final CustomPacketPayload.Type<CreateItemsMessagePacketPayload> CREATE_ITEMS_MESSAGE_PACKET_TYPE = new CustomPacketPayload.Type<>(CREATE_ITEMS_MESSAGE_PACKET);
    public static final CustomPacketPayload.Type<MoveItemsNewPacketPayload> MOVE_ITEMS_NEW_PACKET_TYPE = new CustomPacketPayload.Type<>(MOVE_ITEMS_NEW_PACKET);
    public static final CustomPacketPayload.Type<NotEnoughItemsPacketPayload> NOT_ENOUGH_ITEMS_PACKET_TYPE = new CustomPacketPayload.Type<>(NOT_ENOUGH_ITEMS_PACKET);
    
    public record DeleteItemsPacketPayload() implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteItemsPacketPayload> STREAM_CODEC = new EmptyStreamCodec<>(DeleteItemsPacketPayload::new);
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return DELETE_ITEMS_PACKET_TYPE;
        }
    }
    
    public record CreateItemsPacketPayload(ItemStack itemStack) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateItemsPacketPayload> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, CreateItemsPacketPayload::itemStack,
                CreateItemsPacketPayload::new
        );
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return CREATE_ITEMS_PACKET_TYPE;
        }
    }
    
    public record CreateItemsHotbarPacketPayload(ItemStack itemStack, int hotbarSlotId) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateItemsHotbarPacketPayload> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, CreateItemsHotbarPacketPayload::itemStack,
                ByteBufCodecs.VAR_INT, CreateItemsHotbarPacketPayload::hotbarSlotId,
                CreateItemsHotbarPacketPayload::new
        );
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return CREATE_ITEMS_HOTBAR_PACKET_TYPE;
        }
    }
    
    public record CreateItemsGrabPacketPayload(ItemStack itemStack) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateItemsGrabPacketPayload> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, CreateItemsGrabPacketPayload::itemStack,
                CreateItemsGrabPacketPayload::new
        );

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return CREATE_ITEMS_GRAB_PACKET_TYPE;
        }
    }
    
    public record CreateItemsMessagePacketPayload(ItemStack itemStack, String playerName) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateItemsMessagePacketPayload> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, CreateItemsMessagePacketPayload::itemStack,
                ByteBufCodecs.STRING_UTF8, CreateItemsMessagePacketPayload::playerName,
                CreateItemsMessagePacketPayload::new
        );
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return CREATE_ITEMS_MESSAGE_PACKET_TYPE;
        }
    }
    
    public record MoveItemsNewPacketPayload(ResourceLocation categoryName, boolean shift, CompoundTag nbt) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MoveItemsNewPacketPayload> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, MoveItemsNewPacketPayload::categoryName,
                ByteBufCodecs.BOOL, MoveItemsNewPacketPayload::shift,
                ByteBufCodecs.COMPOUND_TAG, MoveItemsNewPacketPayload::nbt,
                MoveItemsNewPacketPayload::new
        );
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return MOVE_ITEMS_NEW_PACKET_TYPE;
        }
    }
    
    public record NotEnoughItemsPacketPayload(List<List<ItemStack>> ingredients) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, NotEnoughItemsPacketPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.collection(
                        ArrayList::new, ByteBufCodecs.collection(ArrayList::new, ItemStack.OPTIONAL_STREAM_CODEC)
                ), NotEnoughItemsPacketPayload::ingredients,
                NotEnoughItemsPacketPayload::new
        );

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return NOT_ENOUGH_ITEMS_PACKET_TYPE;
        }
    }
    
    public static void onInitialize() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), DELETE_ITEMS_PACKET_TYPE, DeleteItemsPacketPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player.getPermissionLevel() < player.level().getServer().operatorUserPermissionLevel()) {
                player.displayClientMessage(Component.translatable("text.rei.no_permission_cheat").withStyle(ChatFormatting.RED), false);
                return;
            }
            AbstractContainerMenu menu = player.containerMenu;
            if (!menu.getCarried().isEmpty()) {
                menu.setCarried(ItemStack.EMPTY);
                menu.broadcastChanges();
            }
        });
        NetworkManager.registerReceiver(NetworkManager.c2s(), CREATE_ITEMS_PACKET_TYPE, CreateItemsPacketPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player.getPermissionLevel() < player.level().getServer().operatorUserPermissionLevel()) {
                player.displayClientMessage(Component.translatable("text.rei.no_permission_cheat").withStyle(ChatFormatting.RED), false);
                return;
            }
            if (player.getInventory().add(payload.itemStack.copy())) {
                var packet = new CreateItemsMessagePacketPayload(payload.itemStack.copy(), player.getScoreboardName());
                NetworkManager.sendToPlayer(player, packet);
            } else {
                player.displayClientMessage(Component.translatable("text.rei.failed_cheat_items"), false);
            }
        });
        NetworkManager.registerReceiver(NetworkManager.c2s(), CREATE_ITEMS_GRAB_PACKET_TYPE, CreateItemsGrabPacketPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player.getPermissionLevel() < player.level().getServer().operatorUserPermissionLevel()) {
                player.displayClientMessage(Component.translatable("text.rei.no_permission_cheat").withStyle(ChatFormatting.RED), false);
                return;
            }
            
            AbstractContainerMenu menu = player.containerMenu;
            ItemStack stack = payload.itemStack.copy();
            if (!menu.getCarried().isEmpty() && ItemStack.isSameItemSameComponents(menu.getCarried(), stack)) {
                stack.setCount(Mth.clamp(stack.getCount() + menu.getCarried().getCount(), 1, stack.getMaxStackSize()));
            } else if (!menu.getCarried().isEmpty()) {
                return;
            }
            menu.setCarried(stack.copy());
            menu.broadcastChanges();
            var packet = new CreateItemsMessagePacketPayload(stack.copy(), player.getScoreboardName());
            NetworkManager.sendToPlayer(player, packet);
        });
        NetworkManager.registerReceiver(NetworkManager.c2s(), CREATE_ITEMS_HOTBAR_PACKET_TYPE, CreateItemsHotbarPacketPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player.getPermissionLevel() < player.level().getServer().operatorUserPermissionLevel()) {
                player.displayClientMessage(Component.translatable("text.rei.no_permission_cheat").withStyle(ChatFormatting.RED), false);
                return;
            }
            ItemStack stack = payload.itemStack;
            int hotbarSlotId = payload.hotbarSlotId;
            if (hotbarSlotId >= 0 && hotbarSlotId < 9) {
                AbstractContainerMenu menu = player.containerMenu;
                player.getInventory().setItem(hotbarSlotId, stack.copy());
                menu.broadcastChanges();
                var packet = new CreateItemsMessagePacketPayload(stack.copy(), player.getScoreboardName());
                NetworkManager.sendToPlayer(player, packet);
            } else {
                player.displayClientMessage(Component.translatable("text.rei.failed_cheat_items"), false);
            }
        });
        NetworkManager.registerReceiver(NetworkManager.c2s(), MOVE_ITEMS_NEW_PACKET_TYPE, MoveItemsNewPacketPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            CategoryIdentifier<Display> category = CategoryIdentifier.of(payload.categoryName);
            AbstractContainerMenu container = player.containerMenu;
            InventoryMenu playerContainer = player.inventoryMenu;
            try {
                boolean shift = payload.shift;
                try {
                    CompoundTag nbt = payload.nbt;
                    int version = nbt.getInt("Version").orElse(-1);
                    if (version != 1) throw new IllegalStateException("Server and client REI protocol version mismatch! Server: 1, Client: " + version);
                    List<InputIngredient<ItemStack>> inputs = readInputs(context.registryAccess(), nbt.getListOrEmpty("Inputs"));
                    List<SlotAccessor> input = readSlots(container, player, nbt.getListOrEmpty("InputSlots"));
                    List<SlotAccessor> inventory = readSlots(container, player, nbt.getListOrEmpty("InventorySlots"));
                    NewInputSlotCrafter<AbstractContainerMenu, Container> crafter = new NewInputSlotCrafter<>(container, input, inventory, inputs);
                    crafter.fillInputSlots(player, shift);
                } catch (InputSlotCrafter.NotEnoughMaterialsException e) {
                    if (!(container instanceof RecipeBookMenu)) {
                        return;
                    }
                } catch (IllegalStateException e) {
                    player.sendSystemMessage(Component.translatable(e.getMessage()).withStyle(ChatFormatting.RED));
                } catch (Exception e) {
                    player.sendSystemMessage(Component.translatable("error.rei.internal.error", e.getMessage()).withStyle(ChatFormatting.RED));
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(CREATE_ITEMS_MESSAGE_PACKET_TYPE, CreateItemsMessagePacketPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(NOT_ENOUGH_ITEMS_PACKET_TYPE, NotEnoughItemsPacketPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(DisplaySyncPacket.TYPE, DisplaySyncPacket.STREAM_CODEC, List.of(new SplitPacketTransformer()));
        }
    }
    
    private static List<SlotAccessor> readSlots(AbstractContainerMenu menu, Player player, ListTag tag) {
        List<SlotAccessor> slots = new ArrayList<>();
        for (Tag t : tag) {
            slots.add(SlotAccessorRegistry.getInstance().read(menu, player, (CompoundTag) t));
        }
        return slots;
    }
    
    private static List<InputIngredient<ItemStack>> readInputs(RegistryAccess registryAccess, ListTag tag) {
        List<InputIngredient<ItemStack>> inputs = new ArrayList<>();
        for (Tag t : tag) {
            CompoundTag compoundTag = (CompoundTag) t;
            InputIngredient<EntryStack<?>> stacks = InputIngredient.of(compoundTag.getInt("Index").orElseThrow(), EntryIngredient.codec().parse(registryAccess.createSerializationContext(NbtOps.INSTANCE), compoundTag.getListOrEmpty("Ingredient")).getOrThrow());
            inputs.add(InputIngredient.withType(stacks, VanillaEntryTypes.ITEM));
        }
        return inputs;
    }
}
