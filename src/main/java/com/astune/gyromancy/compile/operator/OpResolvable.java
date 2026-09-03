package com.astune.gyromancy.compile.operator;

/**
 * Optional capability for an operator whose effective child representation
 * depends on the parent use site or on runtime array state.
 */
public interface OpResolvable {
    OpResolution resolve(OpResolveContext context);
}
