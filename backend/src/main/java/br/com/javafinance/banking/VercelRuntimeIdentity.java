package br.com.javafinance.banking;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Vercel refreshes this identity on requests; never retain a build-time token in a cloud process. */
final class VercelRuntimeIdentity {
    private VercelRuntimeIdentity() {}
    static String token(){
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context){
            String token=context.getRequest().getHeader("x-vercel-oidc-token");
            if(token!=null && !token.isBlank() && token.length()<=16384)return token;
        }
        String environment=System.getenv("VERCEL_ENV");
        if(environment==null || environment.isBlank() || "development".equals(environment)){
            String token=System.getenv("VERCEL_OIDC_TOKEN");return token==null?"":token;
        }
        return "";
    }
}
