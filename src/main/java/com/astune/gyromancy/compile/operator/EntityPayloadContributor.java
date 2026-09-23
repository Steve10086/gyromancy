package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.runtime.OpRuntimeContext;

import java.util.List;

/** Runtime payload contribution capability used by EntityEffectOp. */
public interface EntityPayloadContributor {
    void contributeEntityPayloads(List<EntityPayload> payloads, OpRuntimeContext context);
}
