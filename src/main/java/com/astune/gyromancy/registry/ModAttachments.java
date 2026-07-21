package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Registry for NeoForge AttachmentType definitions.
 * Attachments are the primary storage mechanism for per-chunk element overrides
 * and per-level entity/array managers.
 */
public final class ModAttachments {

    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Gyromancy.MODID);

    /**
     * Per-chunk element concentration overrides.
     * Only positions that have been explicitly modified by magical effects are stored here.
     * Unmodified positions return their biome-default concentrations.
     */
    public static final Supplier<AttachmentType<HashMap<BlockPos, ElementConcentrations>>>
            ELEMENT_OVERRIDES = ATTACHMENTS.register("element_overrides",
            () -> AttachmentType.builder(() -> new HashMap<BlockPos, ElementConcentrations>())
                    .serialize(ElementConcentrations.mapCodec())
                    .build()
    );

    /**
     * Per-chunk persisted glyphs touching this chunk.
     * Cross-chunk glyphs are duplicated by UUID in every touched chunk.
     */
    public static final Supplier<AttachmentType<HashMap<UUID, PositionedGlyph>>>
            CHUNK_GLYPHS = ATTACHMENTS.register("chunk_glyphs",
            () -> AttachmentType.builder(() -> new HashMap<UUID, PositionedGlyph>())
                    .serialize(PositionedGlyph.mapCodec())
                    .build()
    );

    /**
     * Per-level magic array manager. Tracks all active arrays in a dimension.
     */
    public static final Supplier<AttachmentType<MagicArrayManager>>
            ARRAY_MANAGER = ATTACHMENTS.register("array_manager",
            () -> AttachmentType.builder(() -> new MagicArrayManager())
                    .serialize(MagicArrayManager.CODEC)
                    .build()
    );
}
