package br.com.javafinance.common;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+10)
public class RequestTimingFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().startsWith("/api/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException{
        String correlation=UUID.randomUUID().toString();long start=System.nanoTime();var sample=RequestMetrics.start();
        String previous=MDC.get("correlation_id");MDC.put("correlation_id",correlation);response.setHeader("X-Correlation-ID",correlation);
        boolean failed=true;
        try{chain.doFilter(request,response);failed=false;}finally{
            // Only fixed module names; no query strings, UUIDs, descriptions or arbitrary path segments.
            String route=request.getRequestURI().split("/",5).length>2?request.getRequestURI().split("/",5)[2]:"other";
            if(!java.util.Set.of("auth","reports","movements","budgets","banking","investments","goals","accounts","categories","health").contains(route))route="other";
            LoggerFactory.getLogger(getClass()).info("api_finished correlation_id={} module={} status={} duration_ms={} connection_ms={} jdbc_calls={} jdbc_ms={}",correlation,route,failed&&response.getStatus()<400?500:response.getStatus(),(System.nanoTime()-start)/1_000_000,sample.connectionNanos/1_000_000,sample.jdbcCalls,sample.jdbcNanos/1_000_000);
            RequestMetrics.clear();if(previous==null)MDC.remove("correlation_id");else MDC.put("correlation_id",previous);
        }
    }
}
