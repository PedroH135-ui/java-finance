package br.com.javafinance.banking;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PluggyReadProbeServerTest {
    @Test void onlyExactHttpsOriginCanTriggerTheProtectedProbe() {
        assertThat(PluggyReadProbeServer.sameOrigin("https://probe.vercel.app", "probe.vercel.app")).isTrue();
        for (String origin : new String[]{"http://probe.vercel.app", "https://other.vercel.app", "https://probe.vercel.app.attacker.example",
            "https://probe.vercel.app:8443", "https://probe.vercel.app/path", "https://probe.vercel.app?x=1", "https://user@probe.vercel.app", "null"}) {
            assertThat(PluggyReadProbeServer.sameOrigin(origin, "probe.vercel.app")).isFalse();
        }
    }
}
