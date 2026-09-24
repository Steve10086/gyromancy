package com.astune.gyromancy.array.compile;

public record CompileDiagnostic(String code, String message) {
    /**
     * Marks a failure caused by runtime state (for example an offline wireless
     * source) rather than by authored structure. Reported as a runtime error.
     */
    public static final String RUNTIME_ERROR = "runtime_error";
}
