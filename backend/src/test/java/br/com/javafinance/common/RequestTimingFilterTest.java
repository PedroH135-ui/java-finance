package br.com.javafinance.common;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class RequestTimingFilterTest {
 @Test void timingContainsOnlyAggregatesAndClearsThreadStateAfterErrors(CapturedOutput output) throws Exception{
  var request=new MockHttpServletRequest("GET","/api/movements/synthetic-private-record");request.setQueryString("description=synthetic-private-query");
  request.addHeader("X-Correlation-ID","synthetic-private-header");var response=new MockHttpServletResponse();
  Connection raw=mock(Connection.class);PreparedStatement statement=mock(PreparedStatement.class);when(raw.prepareStatement(anyString())).thenReturn(statement);
  assertThatThrownBy(()->new RequestTimingFilter().doFilter(request,response,(a,b)->{
   try(var connection=RequestMetrics.acquire(()->raw)){connection.prepareStatement("select synthetic_private_sql").executeQuery();}catch(SQLException error){throw new RuntimeException(error);}
   throw new jakarta.servlet.ServletException("synthetic chain failure");
  })).isInstanceOf(jakarta.servlet.ServletException.class);
  assertThat(response.getHeader("X-Correlation-ID")).matches("[0-9a-f-]{36}");
  assertThat(output.getAll()).contains("jdbc_calls=1","module=movements").doesNotContain("synthetic-private-record","synthetic-private-query","synthetic-private-header","synthetic_private_sql");
  assertThat(RequestMetrics.acquire(()->raw)).isSameAs(raw);
 }
}
