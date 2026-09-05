package com.astune.gyromancy.item;

import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasTooltipImage;
import com.astune.gyromancy.canvas.CanvasEntity;
import com.astune.gyromancy.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;
import java.util.Optional;

/** Places the complete portable canvas document as a hanging entity. */
public final class CanvasItem extends Item {
    public CanvasItem() {
        super(new Properties().stacksTo(1)
                .component(ModDataComponents.CANVAS_DOCUMENT.get(), CanvasDocument.blank(1, 1)));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction direction = context.getClickedFace();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos placementPos = context.getClickedPos().relative(direction);
        if (player != null && !player.mayUseItemAt(placementPos, direction, stack)) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        CanvasDocument document = stack.getOrDefault(
                ModDataComponents.CANVAS_DOCUMENT.get(), CanvasDocument.blank(1, 1));
        // A normal placement stores the document as an inactive scroll. Hold
        // Shift to deliberately place an immediately active canvas instead.
        boolean collapsed = player == null || !player.isShiftKeyDown();
        CanvasEntity canvas = CanvasEntity.create(
                level, placementPos, direction, document, collapsed);
        boolean canPlace = collapsed ? canvas.survives() : canvas.canUnfurl();
        if (!canPlace) {
            if (!level.isClientSide && !collapsed && player != null) {
                CanvasEntity.notifyCannotUnfurl(player);
            }
            return InteractionResult.CONSUME;
        }

        if (!level.isClientSide) {
            canvas.playPlacementSound();
            level.gameEvent(player, GameEvent.ENTITY_PLACE, canvas.position());
            level.addFreshEntity(canvas);
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CanvasDocument document = stack.getOrDefault(
                ModDataComponents.CANVAS_DOCUMENT.get(), CanvasDocument.blank(1, 1));
        tooltip.add(Component.translatable("item.gyromancy.canvas.dimensions",
                document.physicalWidth(), document.physicalHeight()));
        tooltip.add(Component.translatable("item.gyromancy.canvas.resolution",
                document.resolutionWidth(), document.resolutionHeight()));
        tooltip.add(Component.translatable("item.gyromancy.canvas.help"));
    }

    @Override
    public Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(
            ItemStack stack) {
        CanvasDocument document = stack.get(ModDataComponents.CANVAS_DOCUMENT.get());
        return document == null || document.isEmpty()
                ? Optional.empty()
                : Optional.of(new CanvasTooltipImage(document));
    }
}
