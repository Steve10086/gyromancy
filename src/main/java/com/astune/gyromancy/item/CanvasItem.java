package com.astune.gyromancy.item;

import com.astune.gyromancy.canvas.CanvasCompileService;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasOrientation;
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
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Places the complete portable canvas document as a hanging entity. */
public final class CanvasItem extends Item {
    /** Empty canvases of the same size stack; canvases with content stay unique. */
    public static final int EMPTY_STACK_SIZE = 16;

    public CanvasItem() {
        super(new Properties().stacksTo(EMPTY_STACK_SIZE)
                .component(ModDataComponents.CANVAS_DOCUMENT.get(), CanvasDocument.blank(1, 1)));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stackSizeFor(stack.get(ModDataComponents.CANVAS_DOCUMENT.get()));
    }

    /** Only blank canvases may merge; any raster content keeps a stack of one. */
    public static int stackSizeFor(@Nullable CanvasDocument document) {
        return document == null || document.isEmpty() ? EMPTY_STACK_SIZE : 1;
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
        Direction orientation = placementOrientation(direction, placementPos, player, stack);
        // A canvas item normally carries the recognized structure, but an item
        // without it is re-recognized instead of placing a canvas whose arrays
        // could never activate.
        CanvasDocument structured = document;
        if (!level.isClientSide && !document.isEmpty() && document.glyphs().isEmpty()) {
            structured = CanvasCompileService.recognizeStructure(document);
        }
        // Placement mints fresh glyph identities so the same item can never
        // share runtime glyph state with another placed canvas.
        CanvasDocument placedDocument = structured.withFreshGlyphIdentities();
        // A normal placement stores the document as an inactive scroll. Hold
        // Shift to deliberately place an immediately active canvas instead.
        boolean collapsed = player == null || !player.isShiftKeyDown();
        CanvasEntity canvas = CanvasEntity.create(
                level, placementPos, direction, orientation, placedDocument, collapsed);
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
        // Blank canvases stay component-identical so they keep stacking.
        if (!document.isEmpty()) {
            stack.set(ModDataComponents.CANVAS_ORIENTATION.get(), orientation);
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The document's +V direction for a new placement: its bottom edge always
     * faces the placer, so the drawing reads right-side-up from their side.
     * Without a placer the orientation carried by the stack is reused.
     */
    static Direction placementOrientation(Direction face, BlockPos anchor,
                                          @Nullable Player player, ItemStack stack) {
        if (face.getAxis().isHorizontal()) return Direction.UP;
        if (player != null) {
            Direction side = CanvasOrientation.horizontalSide(
                    Vec3.atCenterOf(anchor), player.position());
            Direction facing = side != null ? side.getOpposite() : player.getDirection();
            if (facing.getAxis().isHorizontal()) return facing;
        }
        return CanvasOrientation.compatibleTop(
                face, stack.get(ModDataComponents.CANVAS_ORIENTATION.get()));
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
