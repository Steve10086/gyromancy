package com.astune.gyromancy.array.compile;

import java.util.List;

public record SequenceNode(List<ArrayNode> children) implements ArrayNode {}
