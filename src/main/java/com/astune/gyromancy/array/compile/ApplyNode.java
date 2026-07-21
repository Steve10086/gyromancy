package com.astune.gyromancy.array.compile;

public record ApplyNode(ArrayNode operator, ArrayNode target) implements ArrayNode {}
