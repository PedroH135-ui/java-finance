package br.com.javafinance.banking;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.assertj.core.api.Assertions.*;

class VercelRuntimeIdentityTest {
    @AfterEach void clear(){RequestContextHolder.resetRequestAttributes();}
    @Test void readsCurrentRequestWithoutCachingOrForwardingInboundTrustedSourceToken(){
        var first=new MockHttpServletRequest();first.addHeader("x-vercel-oidc-token","synthetic-first");
        first.addHeader("x-vercel-trusted-oidc-idp-token","synthetic-caller-identity");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(first));
        assertThat(VercelRuntimeIdentity.token()).isEqualTo("synthetic-first");
        var second=new MockHttpServletRequest();second.addHeader("x-vercel-oidc-token","synthetic-refreshed");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(second));
        assertThat(VercelRuntimeIdentity.token()).isEqualTo("synthetic-refreshed");
    }
}
