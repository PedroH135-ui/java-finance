package br.com.javafinance;
import br.com.javafinance.common.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MoneyTest {
    @Test void strictMoneyPrecision() {
        assertThat(Money.parse("100.01")).isEqualByComparingTo("100.01");
        assertThatThrownBy(() -> Money.parse("100.001")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.parse("1e6")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.parse("NaN")).isInstanceOf(ApiException.class);
        assertThat(Money.parse("-10")).isEqualByComparingTo("-10.00");
    }
    @Test void savingsRateAndZeroIncome() {
        assertThat(Money.percent(new BigDecimal("2500"),new BigDecimal("6000"))).isEqualByComparingTo("41.67");
        assertThat(Money.percent(BigDecimal.ZERO,BigDecimal.ZERO)).isNull();
        assertThat(Money.percent(new BigDecimal("-100"),new BigDecimal("100"))).isEqualByComparingTo("-100");
    }
}

