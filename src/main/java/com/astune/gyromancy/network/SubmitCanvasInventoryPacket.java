package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasEntity;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies only inventory slots that differ after a client-local canvas editing
 * session. Expected stacks and item conservation are verified server-side.
 */
public record SubmitCanvasInventoryPacket(
        int entityId,
        List<SlotChange> changes
) implements CustomPacketPayload {
    public static final Type<SubmitCanvasInventoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Gyromancy.MODID, "submit_canvas_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitCanvasInventoryPacket>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public SubmitCanvasInventoryPacket decode(RegistryFriendlyByteBuf buffer) {
                    int entityId = buffer.readInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > Inventory.INVENTORY_SIZE) {
                        throw new DecoderException("Invalid canvas inventory change count: " + count);
                    }
                    List<SlotChange> changes = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        changes.add(new SlotChange(
                                buffer.readUnsignedByte(),
                                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));
                    }
                    return new SubmitCanvasInventoryPacket(entityId, changes);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                                   SubmitCanvasInventoryPacket packet) {
                    if (packet.changes.size() > Inventory.INVENTORY_SIZE) {
                        throw new IllegalArgumentException(
                                "Too many canvas inventory changes");
                    }
                    buffer.writeInt(packet.entityId);
                    buffer.writeVarInt(packet.changes.size());
                    for (SlotChange change : packet.changes) {
                        buffer.writeByte(change.slot);
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, change.expected);
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, change.replacement);
                    }
                }
            };

    public SubmitCanvasInventoryPacket {
        changes = changes.stream()
                .map(SlotChange::copy)
                .toList();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SubmitCanvasInventoryPacket packet,
                                    IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> applyServer(player, packet));
    }

    private static void applyServer(ServerPlayer player,
                                    SubmitCanvasInventoryPacket packet) {
        if (!(player.level() instanceof ServerLevel level)
                || !(level.getEntity(packet.entityId) instanceof CanvasEntity canvas)
                || player.distanceToSqr(canvas) > 64.0
                || !validChanges(player.getInventory(), packet.changes)) {
            player.inventoryMenu.sendAllDataToRemote();
            return;
        }

        Inventory inventory = player.getInventory();
        for (SlotChange change : packet.changes) {
            inventory.setItem(change.slot, change.replacement.copy());
        }
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    static boolean validChanges(Inventory inventory, List<SlotChange> changes) {
        if (changes.isEmpty() || changes.size() > Inventory.INVENTORY_SIZE) {
            return false;
        }

        Set<Integer> seenSlots = new HashSet<>();
        for (SlotChange change : changes) {
            if (change.slot < 0
                    || change.slot >= Inventory.INVENTORY_SIZE
                    || !seenSlots.add(change.slot)
                    || !ItemStack.matches(inventory.getItem(change.slot), change.expected)
                    || !validStack(change.replacement)) {
                return false;
            }
        }
        return contentsConserved(changes);
    }

    private static boolean validStack(ItemStack stack) {
        return stack.isEmpty()
                || (stack.getCount() > 0 && stack.getCount() <= stack.getMaxStackSize());
    }

    static boolean contentsConserved(List<SlotChange> changes) {
        for (SlotChange basis : changes) {
            if (!basis.expected.isEmpty()
                    && totalMatching(changes, basis.expected, true)
                    != totalMatching(changes, basis.expected, false)) {
                return false;
            }
            if (!basis.replacement.isEmpty()
                    && totalMatching(changes, basis.replacement, true)
                    != totalMatching(changes, basis.replacement, false)) {
                return false;
            }
        }
        return true;
    }

    private static long totalMatching(List<SlotChange> changes,
                                      ItemStack item,
                                      boolean expected) {
        long total = 0;
        for (SlotChange change : changes) {
            ItemStack stack = expected ? change.expected : change.replacement;
            if (ItemStack.isSameItemSameComponents(item, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public record SlotChange(int slot,
                             ItemStack expected,
                             ItemStack replacement) {
        public SlotChange {
            expected = expected.copy();
            replacement = replacement.copy();
        }

        private SlotChange copy() {
            return new SlotChange(slot, expected, replacement);
        }
    }
}
