package kome.common.data;

/**
 * Canonical faction-scoped population bank. This deliberately has no offensive
 * or defensive subdivision and no population cap.
 */
public final class KOMEFactionPopulation {
    private int availablePopulation;

    public int getAvailablePopulation() {
        return availablePopulation;
    }

    boolean trySpend(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Population spend must not be negative: " + amount);
        }
        if (amount > availablePopulation) {
            return false;
        }
        availablePopulation -= amount;
        return true;
    }

    void grant(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Population grant must not be negative: " + amount);
        }
        if (amount > Integer.MAX_VALUE - availablePopulation) {
            throw new ArithmeticException("Population grant overflows available population: " + amount);
        }
        availablePopulation += amount;
    }

    void setAvailablePopulation(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Available population must not be negative: " + amount);
        }
        availablePopulation = amount;
    }
}
