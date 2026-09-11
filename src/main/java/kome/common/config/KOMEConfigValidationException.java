package kome.common.config;

/** Thrown when a KOME configuration value cannot be used safely at startup. */
public final class KOMEConfigValidationException extends RuntimeException {
    public KOMEConfigValidationException(String key, String value, String requirement) {
        super("Invalid KOME configuration value for '" + key + "': '" + value
                + "' (" + requirement + ")");
    }
}
