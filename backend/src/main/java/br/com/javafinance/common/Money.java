package br.com.javafinance.common;
import java.math.*;
public final class Money {
    private Money() {}
    public static BigDecimal parse(String value) {
        if (value == null || !value.matches("-?(0|[1-9][0-9]{0,14})(\\.[0-9]{1,2})?"))
            throw ApiException.bad("Use um valor decimal com até 2 casas (ex.: 125.50).");
        return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    }
    public static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() <= 0 ? null : numerator.multiply(new BigDecimal("100")).divide(denominator, 2, RoundingMode.HALF_UP);
    }
}

