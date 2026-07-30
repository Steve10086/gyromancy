package com.astune.gyromancy.item;

import com.astune.gyromancy.canvas.CanvasDocument;
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
        CanvasEntity canvas = CanvasEntity.create(level, placementPos, direction, document);
        if (!canvas.survives()) return InteractionResult.CONSUME;

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
    }
}
