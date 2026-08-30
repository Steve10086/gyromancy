# 秘文 rune resources

Add one binary PNG per `SecretText` enum constant in this directory. Every
source resource must be exactly 3×3 pixels; black pixels are foreground and
all other pixels are background. The filename is the enum constant's explicit
resource name (`1.png`, `2.png`, and so on). When loading, white borders are
clipped automatically, so the stored matching pattern may be any non-empty
rectangle no larger than 3×3.

Each enum value owns an independent `SecretTextSymbol` instance. All such
instances share the same class and have `PARAMETER_RUNE` role; their runtime
parameter form uses the symbol id `gyromancy:secret_text_<number>`.
