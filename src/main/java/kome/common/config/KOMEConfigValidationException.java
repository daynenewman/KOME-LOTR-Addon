package kome.common.config;

/** Thrown when a KOME configuration value cannot be used safely at startup. */
public final class KOMEConfigValidationException extends RuntimeException {
    private final String key;
    private final String requirement;
    public KOMEConfigValidationException(String key, String value, String requirement) {
        super("Invalid KOME configuration value for '" + key + "': '" + value
                + "' (" + requirement + ")");
        this.key = key;
        this.requirement = requirement;
    }
    public String getKey() { return key; }
    public String getRequirement() { return requirement; }
}
