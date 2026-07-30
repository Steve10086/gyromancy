package com.astune.gyromancy.api.canvas;

/**
 * Marker for items which provide a tool inside the canvas editor.
 *
 * <p>Pen, compass and stamp behaviors use separate sub-interfaces so adding a
 * new tool does not require treating every selected inventory item as a pen.
 */
public interface CanvasEditorTool {
}
