package kome.common.data;

/**
 * Canonical faction-scoped population bank. This deliberately has no offensive
 * or defensive subdivision and no population cap.
 */
public final class KOMEFactionPopulation {
    private long availablePopulationCenti;

    public long getAvailablePopulationCenti() {
        return availablePopulationCenti;
    }

    boolean trySpendCenti(long amountCenti) {
        if (amountCenti < 0L) {
            throw new IllegalArgumentException("Population spend must not be negative: " + amountCenti);
        }
        if (amountCenti > availablePopulationCenti) {
            return false;
        }
        availablePopulationCenti = Math.subtractExact(availablePopulationCenti, amountCenti);
        return true;
    }

    void grantCenti(long amountCenti) {
        if (amountCenti < 0L) {
            throw new IllegalArgumentException("Population grant must not be negative: " + amountCenti);
        }
        try {
            availablePopulationCenti = Math.addExact(availablePopulationCenti, amountCenti);
        } catch (ArithmeticException overflow) {
            throw new ArithmeticException("Population grant overflows available centi-population: " + amountCenti);
        }
    }

    void setAvailablePopulationCenti(long amountCenti) {
        if (amountCenti < 0L) {
            throw new IllegalArgumentException("Available centi-population must not be negative: " + amountCenti);
        }
        availablePopulationCenti = amountCenti;
    }
}
