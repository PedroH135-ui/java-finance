package br.com.javafinance.banking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CategoryJobController {
    public record Job(UUID owner) {}
    public record Result(boolean more) {}
    private final String token;
    private final CloudCategoryJobs jobs;
    public CategoryJobController(@Value("${app.category-ai.job-token:}") String token,CloudCategoryJobs jobs){this.token=token;this.jobs=jobs;}
    @PostMapping("/api/banking/jobs/categories")
    ResponseEntity<Result> process(@RequestHeader(value="Authorization",defaultValue="") String received,@RequestBody Job job){
        if(token.length()<32 || received.length()>256 || !MessageDigest.isEqual(received.getBytes(StandardCharsets.UTF_8),("Bearer "+token).getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(401).build();
        if(job.owner()==null)return ResponseEntity.badRequest().build();
        try{return ResponseEntity.ok(new Result(jobs.process(job.owner())));}
        catch(RuntimeException e){return ResponseEntity.status(503).header("Retry-After","60").build();}
    }
}
