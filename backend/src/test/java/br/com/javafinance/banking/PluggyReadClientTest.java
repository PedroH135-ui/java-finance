package br.com.javafinance.banking;

import br.com.javafinance.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class PluggyReadClientTest {
    @Test void cardFailureKeepsSafeBillFieldWithoutFinancialPayload(CapturedOutput output) {
        responses.add("{\"apiKey\":\"synthetic-token\"}");
        responses.add("{\"results\":[{\"id\":\""+account+"\",\"itemId\":\""+item+"\",\"type\":\"CREDIT\",\"subtype\":\"CREDIT_CARD\",\"currencyCode\":\"BRL\",\"name\":\"private-card-name\"}]}");
        responses.add("{\"results\":[{\"id\":\"private-bill\",\"totalAmount\":123.45,\"totalAmountCurrencyCode\":\"unsupported-private-currency\"}]}");
        responses.add("{\"next\":null,\"results\":[]}");
        var result=client.cards(item,LocalDate.of(2026,10,1),LocalDate.of(2026,10,15));
        assertThat(result).hasSize(1);assertThat(result.getFirst().billsAvailable()).isFalse();assertThat(result.getFirst().bills()).isEmpty();
        assertThat(output.getAll()).contains("stage=BILLS","field=CURRENCY").doesNotContain("private-card-name","private-bill","123.45","unsupported-private-currency","synthetic-token");
    }
    @Test void invalidBillTotalsAreNotRoundedOrReplacedAndValidBillsSurvive(CapturedOutput output) {
        for(String total:List.of("-0.01","1.001","\"private-invalid-total\"")) {
            responses.add("{\"apiKey\":\"synthetic-token\"}");
            responses.add("{\"results\":[{\"id\":\""+account+"\",\"itemId\":\""+item+"\",\"type\":\"CREDIT\",\"subtype\":\"CREDIT_CARD\",\"currencyCode\":\"BRL\",\"name\":\"private-card\"}]}");
            responses.add("{\"results\":[{\"id\":\"bad\",\"dueDate\":\"2026-10-14\",\"totalAmount\":"+total+",\"totalAmountCurrencyCode\":\"BRL\"},{\"id\":\"good\",\"dueDate\":\"2026-10-14\",\"totalAmount\":500,\"totalAmountCurrencyCode\":\"BRL\"}]}");
            responses.add("{\"results\":[],\"next\":null}");
            var reader=new PluggyReadClient(config,new ObjectMapper(),http);
            var cards=reader.cards(item,LocalDate.of(2026,10,1),LocalDate.of(2026,10,15));
            assertThat(cards).hasSize(1);assertThat(cards.getFirst().billsAvailable()).isFalse();
            assertThat(cards.getFirst().bills()).hasSize(1);assertThat(cards.getFirst().bills().getFirst().id()).isEqualTo("good");assertThat(cards.getFirst().bills().getFirst().total()).isEqualByComparingTo("500.00");
        }
        assertThat(output.getAll()).contains("stage=BILLS","field=AMOUNT").doesNotContain("-0.01","1.001","private-invalid-total","private-card","synthetic-token");
    }
    @Test void additionalProductsUseReadOnlyAllowlistedPathsAndKeepOnlyNecessaryFields() {
        responses.add("{\"apiKey\":\"synthetic-token\"}");
        responses.add("{\"page\":1,\"totalPages\":1,\"results\":[{\"id\":\""+account+"\",\"itemId\":\""+item+"\",\"type\":\"CREDIT\",\"subtype\":\"CREDIT_CARD\",\"currencyCode\":\"BRL\",\"name\":\"Cartão fictício\",\"number\":\"never-copy-card-number\"}]}");
        responses.add("{\"results\":[{\"id\":\"bill\",\"dueDate\":\"2026-10-14T00:00:00Z\",\"totalAmount\":500,\"totalAmountCurrencyCode\":\"BRL\"}]}");
        responses.add("{\"next\":null,\"results\":[{\"id\":\"charge\",\"accountId\":\""+account+"\",\"description\":\"Mercado fictício\",\"amount\":500,\"date\":\"2026-10-12\",\"type\":\"DEBIT\",\"status\":\"POSTED\",\"currencyCode\":\"BRL\",\"creditCardMetadata\":{\"billId\":\"bill\",\"installmentNumber\":1,\"totalInstallments\":2,\"cardNumber\":\"never-copy-card-number\"}}]}");
        var cards=client.cards(item,LocalDate.of(2026,10,1),LocalDate.of(2026,10,15));
        assertThat(cards).hasSize(1);assertThat(cards.getFirst().charges().getFirst().billId()).isEqualTo("bill");assertThat(cards.toString()).doesNotContain("never-copy-card-number");
        UUID investment=UUID.randomUUID();responses.add("{\"results\":[{\"id\":\""+investment+"\",\"itemId\":\""+item+"\",\"name\":\"RDB fictício\",\"type\":\"FIXED_INCOME\",\"subtype\":\"RDB\",\"balance\":500,\"currencyCode\":\"BRL\",\"date\":\"2026-10-14\"}]}");
        assertThat(client.investments(item).getFirst().profit()).isNull();
        assertThat(requests.stream().skip(1)).allSatisfy(request->{assertThat(request.method()).isEqualTo("GET");assertThat(request.uri().getHost()).isEqualTo("api.pluggy.ai");assertThat(request.uri().getPath()).doesNotContain("payments","refresh");});
    }
    @Test void unavailableBillsDoNotPreventCardDetailsAndUnlistedProductsNeverReachNetwork() {
        assertThatThrownBy(()->client.investments(UUID.randomUUID())).isInstanceOf(ApiException.class);verifyNoInteractions(http);
        responses.add("{\"apiKey\":\"synthetic-token\"}");responses.add("{\"results\":[{\"id\":\""+account+"\",\"itemId\":\""+item+"\",\"type\":\"CREDIT\",\"subtype\":\"CREDIT_CARD\",\"currencyCode\":\"BRL\",\"name\":\"Cartão fictício\"}]}");
        responses.add("{\"sensitive\":\"never-log-provider-body\"}");responses.add("{\"next\":null,\"results\":[]}");statusCodes.addAll(List.of(200,200,403,200));
        var result=client.cards(item,LocalDate.of(2026,10,1),LocalDate.of(2026,10,15));assertThat(result.getFirst().billsAvailable()).isFalse();assertThat(result.getFirst().charges()).isEmpty();
    }
    HttpClient http; BankProperties config; PluggyReadClient client;
    UUID item=UUID.randomUUID(), account=UUID.randomUUID();
    List<HttpRequest> requests; Queue<String> responses;
    Queue<Integer> statusCodes;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        http=mock(HttpClient.class); config=new BankProperties(); config.setClientId("fake-client"); config.setClientSecret("fake-secret");
        config.setOwnerId(UUID.randomUUID()); config.setItemIds(Set.of(item)); requests=new ArrayList<>(); responses=new ArrayDeque<>(); statusCodes=new ArrayDeque<>();
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(invocation->{
            HttpRequest request=invocation.getArgument(0); requests.add(request);
            HttpResponse<InputStream> response=mock(HttpResponse.class); when(response.statusCode()).thenReturn(statusCodes.isEmpty()?200:statusCodes.remove());
            when(response.body()).thenReturn(new ByteArrayInputStream(responses.remove().getBytes(StandardCharsets.UTF_8))); return response;
        });
        client=new PluggyReadClient(config,new ObjectMapper().findAndRegisterModules(),http);
    }
    @Test void counterpartyUsesOnlyDocumentTypeAndNeverGuessesFromPersonName(CapturedOutput output) {
        responses.add("{\"apiKey\":\"synthetic-token\"}");
        String base="{\"id\":\"%s\",\"accountId\":\""+account+"\",\"description\":\"PIX nome fictício\",\"amount\":10,\"date\":\"2026-10-12\",\"type\":\"%s\",\"status\":\"POSTED\",\"currencyCode\":\"BRL\",\"paymentData\":%s}";
        String person="{\"receiver\":{\"documentNumber\":{\"type\":\"CPF\",\"value\":\"synthetic-document-not-for-output\"}}}";
        String business="{\"payer\":{\"documentNumber\":{\"type\":\"CNPJ\",\"value\":\"synthetic-document-not-for-output\"}}}";
        responses.add("{\"next\":null,\"results\":["+base.formatted("person","DEBIT",person)+","+base.formatted("business","CREDIT",business)+","+base.formatted("unknown","DEBIT","{}")+"]}");
        var entries=client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14));
        assertThat(entries).extracting(BankProvider.RemoteEntry::counterpartyType).containsExactly("PERSON","BUSINESS","UNKNOWN");
        assertThat(entries.toString()).doesNotContain("synthetic-document-not-for-output");
        assertThat(output.getAll()).doesNotContain("synthetic-document-not-for-output");
    }
    @Test void onlyAuthenticationPostAndReadOnlyFinancialGetsAreUsed() {
        responses.add("{\"apiKey\":\"synthetic-token\"}");
        responses.add("{\"id\":\""+item+"\",\"status\":\"UPDATED\",\"lastUpdatedAt\":\"2026-10-14T12:00:00Z\"}");
        responses.add("{\"results\":[{\"id\":\""+account+"\",\"itemId\":\""+item+"\",\"type\":\"BANK\",\"currencyCode\":\"BRL\",\"name\":\"Nubank fictício\",\"balance\":123.45}]}");
        assertThat(client.accounts(item).accounts().getFirst().balance()).isEqualByComparingTo("123.45");
        responses.add(page("entry-1","50.25","POSTED",2)); responses.add(page("entry-2","-7.10","PENDING",1));
        var entries=client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14));
        assertThat(entries).hasSize(2); assertThat(entries.getFirst().date()).isEqualTo(LocalDate.of(2026,10,12));
        assertThat(entries.get(1).amount()).isEqualByComparingTo("7.10");
        assertThat(requests.getFirst().method()).isEqualTo("POST"); assertThat(requests.getFirst().uri().toString()).isEqualTo("https://api.pluggy.ai/auth");
        assertThat(requests.subList(1,requests.size())).allSatisfy(request->{
            assertThat(request.method()).isEqualTo("GET"); assertThat(request.uri().getHost()).isEqualTo("api.pluggy.ai");
            assertThat(request.uri().getPath()).doesNotContain("payments","refresh");
        });
        assertThat(requests).hasSize(5);
    }
    @Test void unlistedItemDoesNotMakeAnyNetworkCall() {
        assertThatThrownBy(()->client.accounts(UUID.randomUUID())).isInstanceOf(ApiException.class); verifyNoInteractions(http);
    }
    @Test void malformedProviderValuesFailClosedWithoutExposingBody() {
        responses.add("{\"apiKey\":\"synthetic-token\"}"); responses.add(page("entry-1","1.001","POSTED",1));
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
            .isInstanceOf(ApiException.class).hasMessageNotContaining("synthetic-token").hasMessageNotContaining("fake-secret");
    }
    @Test void missingContinuationIsRejectedInsteadOfReturningPartialResults() {
        responses.add("{\"apiKey\":\"synthetic-token\"}"); responses.add(page("entry-1","10","POSTED",1).replace("\"next\":null,", ""));
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
            .isInstanceOfSatisfying(BankReadException.class,ex->assertThat(ex.code).isEqualTo(BankReadException.Code.INVALID_RESPONSE));
    }
    @Test void cursorApiUsesDocumentedDatesAndPreservesCivilDateBoundaries() {
        responses.add("{\"apiKey\":\"synthetic-token\"}");
        responses.add(page("entry-1","10","POSTED",1));
        var entries=client.transactions(account,LocalDate.of(2026,10,12),LocalDate.of(2026,10,12));
        assertThat(entries).hasSize(1);
        String query=requests.getLast().uri().getQuery();
        assertThat(requests.getLast().uri().getPath()).isEqualTo("/v2/transactions");
        assertThat(query).contains("dateFrom=2026-10-11&dateTo=2026-10-13").doesNotContain("pageSize=", "page=", "&from=", "&to=");
        responses.add(page("outside","10","POSTED",1));
        assertThat(client.transactions(account,LocalDate.of(2026,10,13),LocalDate.of(2026,10,14))).isEmpty();
    }
    @Test void cursorIsDecodedAndEncodedOnceAndFiltersAreKeptOnEveryPage() {
        responses.add("{\"apiKey\":\"synthetic-token\"}");
        responses.add(page("entry-1","10","POSTED",2).replace("cursor-2", "a%2Bb%2Fc%3D%3D"));
        responses.add(page("entry-2","20","POSTED",1));
        assertThat(client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14))).hasSize(2);
        assertThat(requests.getLast().uri().getRawQuery()).isEqualTo("accountId="+account+"&dateFrom=2026-09-30&dateTo=2026-10-15&after=a%2Bb%2Fc%3D%3D");
    }
    @Test void invalidOrUntrustedContinuationsAreRejectedBeforeAnotherRequest(CapturedOutput output) throws Exception {
        List<String> invalid=List.of("https://other.example/?accountId="+account+"&after=private-cursor",
            "//other.example/private-cursor", "?accountId="+UUID.randomUUID()+"&after=private-cursor",
            "?accountId="+account+"&after=private-cursor&after=duplicate",
            "?accountId="+account+"&after=private-cursor&dateFrom=1990-01-01",
            "?accountId="+account+"&after=private-cursor&refresh=true",
            "?accountId="+account+"&after=", "?accountId="+account+"&after=%ZZ");
        for(String next:invalid) {
            responses.add("{\"apiKey\":\"synthetic-token\"}");
            responses.add("{\"results\":[],\"next\":"+new ObjectMapper().writeValueAsString(next)+"}");
            var isolated=new PluggyReadClient(config,new ObjectMapper(),http);
            assertThatThrownBy(()->isolated.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
                .isInstanceOf(BankReadException.class);
        }
        assertThat(requests).hasSize(invalid.size()*2);
        assertThat(output.getAll()).doesNotContain("private-cursor", "other.example", account.toString());
    }
    @Test void repeatedCursorFailsClosedWithoutReturningEarlierEntries() {
        responses.add("{\"apiKey\":\"synthetic-token\"}");
        responses.add(page("entry-1","10","POSTED",2)); responses.add(page("entry-2","10","POSTED",2));
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
            .isInstanceOfSatisfying(BankReadException.class,ex->assertThat(ex.code).isEqualTo(BankReadException.Code.INVALID_RESPONSE));
        assertThat(requests).hasSize(3);
    }
    @Test void emptyTerminalPageIsAccepted() {
        responses.add("{\"apiKey\":\"synthetic-token\"}"); responses.add("{\"results\":[],\"next\":null}");
        assertThat(client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14))).isEmpty();
    }
    @Test void recordLimitCountsEveryReceivedRecordIncludingOutsideThePeriod() {
        responses.add("{\"apiKey\":\"synthetic-token\"}");
        String entry=page("outside","10","POSTED",1);
        entry=entry.substring(entry.indexOf("[{" )+1, entry.lastIndexOf(']'));
        for(int i=1;i<=5;i++) responses.add("{\"results\":["+String.join(",", Collections.nCopies(500,entry))+"],\"next\":\"?accountId="+account+"&after=cursor-"+i+"\"}");
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,13),LocalDate.of(2026,10,14)))
            .isInstanceOfSatisfying(BankReadException.class,ex->assertThat(ex.code).isEqualTo(BankReadException.Code.RESULT_LIMIT));
        assertThat(requests).hasSize(6);
    }
    @Test void pageLimitStopsEvenWhenProviderKeepsReturningNewCursors() {
        responses.add("{\"apiKey\":\"synthetic-token\"}");
        for(int i=1;i<=20;i++) responses.add("{\"results\":[],\"next\":\"?accountId="+account+"&after=cursor-"+i+"\"}");
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
            .isInstanceOfSatisfying(BankReadException.class,ex->assertThat(ex.code).isEqualTo(BankReadException.Code.RESULT_LIMIT));
        assertThat(requests).hasSize(21);
    }
    @Test void diagnosticDoesNotRetryUnauthorizedReads() {
        responses.addAll(List.of("{\"apiKey\":\"synthetic-token\"}","private-error"));
        statusCodes.addAll(List.of(200,401));
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
            .isInstanceOfSatisfying(BankReadException.class,ex->{
                assertThat(ex.stage).isEqualTo(BankReadException.Stage.TRANSACTIONS);
                assertThat(ex.code).isEqualTo(BankReadException.Code.AUTH_DENIED);
            });
        assertThat(requests).hasSize(2);
    }
    @Test void invalidAmountReportsOnlyFieldNameNotFinancialValue(CapturedOutput output) {
        responses.add("{\"apiKey\":\"synthetic-token\"}"); responses.add(page("private-id","1.001","POSTED",1));
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
            .isInstanceOf(BankReadException.class);
        assertThat(output.getAll()).contains("field=AMOUNT").doesNotContain("1.001","private-id",account.toString());
    }
    @Test void deniedAuthenticationIsNotRetriedAndDoesNotExposeBody(CapturedOutput output) {
        responses.add("private-provider-body fake-secret synthetic-token"); statusCodes.add(401);
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
            .isInstanceOfSatisfying(BankReadException.class,ex->{
                assertThat(ex.stage).isEqualTo(BankReadException.Stage.AUTH);
                assertThat(ex.code).isEqualTo(BankReadException.Code.AUTH_DENIED);
                assertThat(ex.getCause()).isNull();
                assertThat(ex.getMessage()).contains(ex.reference).doesNotContain("fake-secret","private-provider-body");
            });
        assertThat(requests).hasSize(1);
        assertThat(output.getAll()).contains("stage=AUTH", "code=AUTH_DENIED").doesNotContain("fake-secret","private-provider-body","synthetic-token");
    }
    @Test void accessDeniedAndRateLimitAreClassifiedWithoutRetriesOrPayloadLeaks(CapturedOutput output) {
        for(int status:List.of(403,429)) {
            responses.add("{\"apiKey\":\"synthetic-token\"}"); responses.add("private-financial-payload");
            statusCodes.addAll(List.of(200,status));
            var isolated=new PluggyReadClient(config,new ObjectMapper(),http);
            assertThatThrownBy(()->isolated.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
                .isInstanceOfSatisfying(BankReadException.class,ex->{
                    assertThat(ex.stage).isEqualTo(BankReadException.Stage.TRANSACTIONS);
                    assertThat(ex.code).isEqualTo(status==403?BankReadException.Code.ACCESS_DENIED:BankReadException.Code.RATE_LIMIT);
                });
        }
        assertThat(requests).hasSize(4);
        assertThat(output.getAll()).doesNotContain("private-financial-payload","synthetic-token",account.toString());
    }
    @Test void invalidJsonAndTimeoutHaveDifferentSafeDiagnostics() throws Exception {
        responses.add("{\"apiKey\":\"synthetic-token\"}"); responses.add("not-json-private");
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
            .isInstanceOfSatisfying(BankReadException.class,ex->assertThat(ex.code).isEqualTo(BankReadException.Code.INVALID_RESPONSE));
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("private-network-details"));
        assertThatThrownBy(()->client.transactions(account,LocalDate.of(2026,10,1),LocalDate.of(2026,10,14)))
            .isInstanceOfSatisfying(BankReadException.class,ex->{
                assertThat(ex.code).isEqualTo(BankReadException.Code.TIMEOUT);
                assertThat(ex.getMessage()).doesNotContain("private-network-details");
            });
    }
    String page(String id,String amount,String status,int pages) {
        return "{\"next\":"+(pages>1?"\"?accountId="+account+"&after=cursor-2\"":"null")+",\"results\":[{\"id\":\""+id+"\",\"description\":\"Compra fictícia\",\"amount\":"+amount+",\"date\":\"2026-10-12T00:00:00Z\",\"type\":\"DEBIT\",\"status\":\""+status+"\",\"currencyCode\":\"BRL\",\"accountId\":\""+account+"\"}]}";
    }
}
