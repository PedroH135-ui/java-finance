package br.com.javafinance.banking;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutomaticCategoryScopeTest {
    final String owner=UUID.randomUUID().toString();
    final String local="jdbc:postgresql://127.0.0.1:55434/java_finance_review";
    @Test void rejectsProductionOtherDatabasesAndNonVectorMode(){
        for(String url:new String[]{"jdbc:postgresql://example.test/real",local+"?currentSchema=public"})
            assertThatThrownBy(()->new AutomaticCategoryScope(true,owner,"local",url,"",true,mock(JdbcTemplate.class))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AutomaticCategoryScope(true,owner,"vercel",local,"",true,mock(JdbcTemplate.class))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AutomaticCategoryScope(true,owner,"local",local,"preview",true,mock(JdbcTemplate.class))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AutomaticCategoryScope(true,owner,"local",local,"",false,mock(JdbcTemplate.class))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void disabledModeNeverReadsDatabase(){
        var jdbc=mock(JdbcTemplate.class);var scope=new AutomaticCategoryScope(false,"","vercel","jdbc:postgresql://example.test/real","production",false,jdbc);
        assertThat(scope.activeFor(UUID.randomUUID())).isFalse();verifyNoInteractions(jdbc);
    }
    @Test void enabledModeRequiresTheConfiguredSyntheticIdentity(){
        var jdbc=mock(JdbcTemplate.class);var scope=new AutomaticCategoryScope(true,owner,"local",local,"",true,jdbc);
        assertThat(scope.activeFor(UUID.randomUUID())).isFalse();verifyNoInteractions(jdbc);
        assertThat(scope.activeFor(UUID.fromString(owner))).isFalse();
    }
}
