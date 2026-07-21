package com.astune.gyromancy.array.compile;

public sealed interface ArrayNode permits SymbolNode, SequenceNode, GroupNode, ApplyNode {}
