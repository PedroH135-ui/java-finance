package br.com.javafinance.banking;

import br.com.javafinance.common.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.*;
import java.math.*;
import java.time.*;
import java.util.*;
import static br.com.javafinance.banking.BankReadException.Code.*;
import static br.com.javafinance.banking.BankReadException.Stage.*;
import org.springframework.stereotype.Component;

/** Fixed host and read-only financial endpoints. No payments, item creation or refresh endpoints. */
@Component
public class PluggyReadClient implements BankProvider {
    private final BankProperties config;
    private final ObjectMapper json;
    private final HttpClient http;
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;
    @org.springframework.beans.factory.annotation.Autowired
    public PluggyReadClient(BankProperties config, ObjectMapper mapper) {
        this(config, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    PluggyReadClient(BankProperties config, ObjectMapper mapper, HttpClient http) {
        this.config = config; this.json = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS); this.http = http;
    }
    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpiry)) return token;
        if (!config.ready()) throw ApiException.bad("Configure a conexão pessoal no servidor antes de consultar o banco.");
        try {
            var body = json.writeValueAsString(Map.of("clientId", config.getClientId(), "clientSecret", config.getClientSecret()));
            JsonNode result = exchange(HttpRequest.newBuilder(URI.create("https://api.pluggy.ai/auth")).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), AUTH);
            String key = result.path("apiKey").asText();
            if (key.isBlank()) throw BankReadException.failure(AUTH, INVALID_RESPONSE);
            token = key; tokenExpiry = Instant.now().plusSeconds(6000); return token;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw BankReadException.failure(AUTH, INVALID_RESPONSE); }
    }
    private JsonNode get(String path) {
        // Only these fixed paths may reach the provider. Never accept a URL from a user or provider response.
        if (!(path.startsWith("/items/") || path.startsWith("/accounts?") || path.startsWith("/v2/transactions?") || path.startsWith("/bills?") || path.startsWith("/investments?"))) throw new IllegalArgumentException("Read endpoint required");
        var stage = path.startsWith("/items/") ? ITEM : path.startsWith("/accounts?") ? ACCOUNTS : TRANSACTIONS;
        return exchange(HttpRequest.newBuilder(URI.create("https://api.pluggy.ai" + path)).timeout(Duration.ofSeconds(20))
            .header("X-API-KEY", token()).GET().build(), stage);
    }
    private JsonNode exchange(HttpRequest request, BankReadException.Stage stage) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (response.statusCode() == 401) { token = null; tokenExpiry = Instant.EPOCH; }
                    var code = switch (response.statusCode()) {
                        case 401 -> AUTH_DENIED;
                        case 403 -> ACCESS_DENIED;
                        case 429 -> RATE_LIMIT;
                        default -> HTTP_ERROR;
                    };
                    throw BankReadException.failure(stage, code, response.statusCode());
                }
                byte[] bytes = stream.readNBytes(2_000_001);
                if (bytes.length > 2_000_000) throw BankReadException.failure(stage, INVALID_RESPONSE);
                JsonNode result=json.readTree(bytes);
                if(result==null) throw BankReadException.failure(stage, INVALID_RESPONSE);
                return result;
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw BankReadException.failure(stage, NETWORK); }
        catch (HttpTimeoutException e) { throw BankReadException.failure(stage, TIMEOUT); }
        catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) { throw BankReadException.failure(stage, INVALID_RESPONSE); }
        catch (java.io.IOException e) { throw BankReadException.failure(stage, NETWORK); }
    }
    @Override public Snapshot accounts(UUID item) {
        if (!config.getItemIds().contains(item)) throw ApiException.missing();
        try {
            JsonNode info = get("/items/" + item);
            if (!item.toString().equals(info.path("id").asText())) throw BankReadException.failure(ITEM, INVALID_RESPONSE);
            if (!"UPDATED".equals(info.path("status").asText()))
                throw BankReadException.failure(ITEM, ITEM_NOT_READY);
            JsonNode result = get("/accounts?itemId=" + item + "&type=BANK").path("results");
            if (!result.isArray() || result.size() > 100) throw invalidValue(BankReadException.Field.RESPONSE);
            List<RemoteAccount> accounts = new ArrayList<>();
            for (JsonNode a : result) {
                if (!item.toString().equals(a.path("itemId").asText())) throw invalidValue(BankReadException.Field.ACCOUNT_ID);
                if (!"BANK".equals(a.path("type").asText()) || !"BRL".equals(a.path("currencyCode").asText())) continue;
                accounts.add(new RemoteAccount(UUID.fromString(a.path("id").asText()), text(a,"name",100), "BANK", "BRL", decimal(a,"balance",false)));
            }
            String updated = info.path("lastUpdatedAt").asText("");
            return new Snapshot(updated.isBlank() ? null : Instant.parse(updated), List.copyOf(accounts));
        } catch (ProviderValueException e) { throw BankReadException.failure(ACCOUNTS, INVALID_RESPONSE, 0, e.field); }
        catch (IllegalArgumentException | DateTimeException e) { throw BankReadException.failure(ACCOUNTS, INVALID_RESPONSE); }
    }
    @Override public List<RemoteEntry> transactions(UUID account, LocalDate from, LocalDate to) {
        try {
            List<RemoteEntry> all = new ArrayList<>();
            Set<String> cursors = new HashSet<>();
            String query = "accountId=" + account + "&dateFrom=" + from.minusDays(1) + "&dateTo=" + to.plusDays(1);
            String after = null;
            int received = 0;
            long started=System.nanoTime();
            for (int page = 1; page <= 20; page++) {
                if(System.nanoTime()-started>60_000_000_000L) throw BankReadException.failure(TRANSACTIONS, TIMEOUT);
                JsonNode result = get("/v2/transactions?" + query
                    + (after == null ? "" : "&after=" + URLEncoder.encode(after, StandardCharsets.UTF_8)));
                JsonNode entries = result.path("results");
                if (!entries.isArray()) throw invalidValue(BankReadException.Field.RESPONSE);
                received += entries.size();
                if (entries.size() > 500 || received > 2000) throw BankReadException.failure(TRANSACTIONS, RESULT_LIMIT);
                for (JsonNode entry : entries) {
                    if (!account.toString().equals(entry.path("accountId").asText())) throw invalidValue(BankReadException.Field.ACCOUNT_ID);
                    if (!"BRL".equals(entry.path("currencyCode").asText())) throw invalidValue(BankReadException.Field.CURRENCY);
                    LocalDate date;
                    try { date = LocalDate.parse(entry.path("date").asText().substring(0,10)); }
                    catch (DateTimeException | IndexOutOfBoundsException ex) { throw invalidValue(BankReadException.Field.DATE); }
                    if (date.isBefore(from) || date.isAfter(to)) continue;
                    String direction = entry.path("type").asText(), status = entry.path("status").asText();
                    if (!Set.of("DEBIT","CREDIT").contains(direction) || !Set.of("POSTED","PENDING").contains(status)) throw invalidValue(BankReadException.Field.STATUS);
                    String documentType=entry.path("paymentData").path("DEBIT".equals(direction)?"receiver":"payer").path("documentNumber").path("type").asText();
                    String party="CPF".equals(documentType)?"PERSON":"CNPJ".equals(documentType)?"BUSINESS":!entry.path("merchant").path("businessName").asText().isBlank()?"BUSINESS":"UNKNOWN";
                    // Persist only the type: never CPF/CNPJ values, account numbers or raw payment data.
                    all.add(new RemoteEntry(text(entry,"id",200), text(entry,"description",200), decimal(entry,"amount",true), date, direction, status,party));
                }
                JsonNode next = result.path("next");
                if (next.isNull()) return List.copyOf(all);
                if (!next.isTextual()) throw invalidValue(BankReadException.Field.RESPONSE);
                after = nextCursor(next.textValue(), account, from, to);
                if (!cursors.add(after)) throw invalidValue(BankReadException.Field.RESPONSE);
            }
            throw BankReadException.failure(TRANSACTIONS, RESULT_LIMIT);
        } catch (ProviderValueException e) { throw BankReadException.failure(TRANSACTIONS, INVALID_RESPONSE, 0, e.field); }
        catch (IllegalArgumentException | DateTimeException | IndexOutOfBoundsException e) { throw BankReadException.failure(TRANSACTIONS, INVALID_RESPONSE); }
    }
    private List<JsonNode> productPages(String query){
        var all=new ArrayList<JsonNode>();
        var ids=new HashSet<String>();long began=System.nanoTime();
        for(int page=1;page<=20;page++){
            if(System.nanoTime()-began>50_000_000_000L)throw BankReadException.failure(ACCOUNTS,TIMEOUT);
            var response=get(query+"&pageSize=100&page="+page);var rows=response.path("results");
            if(!rows.isArray()||rows.size()>100||all.size()+rows.size()>2000)throw BankReadException.failure(ACCOUNTS,INVALID_RESPONSE);
            if(response.has("page")&&response.path("page").asInt()!=page)throw BankReadException.failure(ACCOUNTS,INVALID_RESPONSE);
            for(var row:rows){if(!ids.add(text(row,"id",200)))throw BankReadException.failure(ACCOUNTS,INVALID_RESPONSE);all.add(row);}
            int pages=response.path("totalPages").asInt(1);
            if(pages<0||pages>20)throw BankReadException.failure(ACCOUNTS,RESULT_LIMIT);
            if(page>=pages)return List.copyOf(all);
        }
        throw BankReadException.failure(ACCOUNTS,RESULT_LIMIT);
    }
    private static LocalDate productDate(JsonNode node,String field,boolean required){
        if(node.path(field).isNull()||node.path(field).isMissingNode()){if(!required)return null;throw invalidValue(BankReadException.Field.DATE);}
        return LocalDate.parse(node.path(field).asText().substring(0,10));
    }
    private static BigDecimal optionalMoney(JsonNode node,String field){return node.path(field).isNull()||node.path(field).isMissingNode()?null:decimal(node,field,false);}
    public List<BankProductsProvider.Position> investments(UUID item){
        if(!config.getItemIds().contains(item))throw ApiException.missing();
        try{
            var result=new ArrayList<BankProductsProvider.Position>();
            for(var row:productPages("/investments?itemId="+item)){
                if(!item.toString().equals(row.path("itemId").asText())||!"BRL".equals(row.path("currencyCode").asText()))throw invalidValue(BankReadException.Field.CURRENCY);
                String subtype=row.path("subtype").asText("");if(subtype.length()>60)throw invalidValue(BankReadException.Field.RESPONSE);
                String state=row.path("status").asText("");if(state.length()>30)throw invalidValue(BankReadException.Field.STATUS);
                result.add(new BankProductsProvider.Position(UUID.fromString(text(row,"id",200)),text(row,"name",100),text(row,"type",40),subtype,decimal(row,"balance",false),productDate(row,"date",false),optionalMoney(row,"amountProfit"),optionalMoney(row,"amountWithdrawal"),state));
            }
            return List.copyOf(result);
        }catch(IllegalArgumentException|DateTimeException|IndexOutOfBoundsException error){throw BankReadException.failure(ACCOUNTS,INVALID_RESPONSE);}
    }
    public List<BankProductsProvider.Card> cards(UUID item,LocalDate from,LocalDate to){
        if(!config.getItemIds().contains(item))throw ApiException.missing();
        try{
            var cards=new ArrayList<BankProductsProvider.Card>();long began=System.nanoTime();
            for(var account:productPages("/accounts?itemId="+item+"&type=CREDIT")){
                if(!"CREDIT".equals(account.path("type").asText())||!"CREDIT_CARD".equals(account.path("subtype").asText()))continue;
                if(!item.toString().equals(account.path("itemId").asText())||!"BRL".equals(account.path("currencyCode").asText()))throw invalidValue(BankReadException.Field.CURRENCY);
                UUID card=UUID.fromString(text(account,"id",200));var bills=new ArrayList<BankProductsProvider.Bill>();boolean available=true;
                try{for(var bill:productPages("/bills?accountId="+card)){
                    if(!"BRL".equals(bill.path("totalAmountCurrencyCode").asText()))throw invalidValue(BankReadException.Field.CURRENCY);
                    BigDecimal total=decimal(bill,"totalAmount",false);if(total.signum()<0)throw invalidValue(BankReadException.Field.AMOUNT);
                    bills.add(new BankProductsProvider.Bill(text(bill,"id",200),productDate(bill,"dueDate",true),total));
                }}catch(BankReadException error){if(error.code==ACCESS_DENIED||error.code==HTTP_ERROR&&error.providerStatus==404)available=false;else throw error;}
                var charges=new ArrayList<BankProductsProvider.Charge>();String query="accountId="+card+"&dateFrom="+from.minusDays(1)+"&dateTo="+to.plusDays(1),after=null;var seen=new HashSet<String>();
                for(int page=0;page<20;page++){
                    if(System.nanoTime()-began>60_000_000_000L)throw BankReadException.failure(TRANSACTIONS,TIMEOUT);
                    var response=get("/v2/transactions?"+query+(after==null?"":"&after="+URLEncoder.encode(after,StandardCharsets.UTF_8)));var rows=response.path("results");
                    if(!rows.isArray()||rows.size()>500||charges.size()+rows.size()>2000)throw BankReadException.failure(TRANSACTIONS,RESULT_LIMIT);
                    for(var row:rows){
                        if(!card.toString().equals(row.path("accountId").asText())||!"BRL".equals(row.path("currencyCode").asText()))throw invalidValue(BankReadException.Field.ACCOUNT_ID);
                        LocalDate date=productDate(row,"date",true);if(date.isBefore(from)||date.isAfter(to))continue;
                        String direction=row.path("type").asText(),status=row.path("status").asText();
                        if(!Set.of("DEBIT","CREDIT").contains(direction)||!Set.of("POSTED","PENDING").contains(status))throw invalidValue(BankReadException.Field.STATUS);
                        var meta=row.path("creditCardMetadata");String billId=meta.path("billId").asText(null);if(billId!=null&&billId.length()>200)throw invalidValue(BankReadException.Field.ID);
                        Integer installment=meta.path("installmentNumber").isInt()?meta.path("installmentNumber").asInt():null;
                        Integer installments=meta.path("totalInstallments").isInt()?meta.path("totalInstallments").asInt():null;
                        charges.add(new BankProductsProvider.Charge(text(row,"id",200),text(row,"description",200),decimal(row,"amount",true),date,direction,status,billId,installment,installments));
                    }
                    if(response.path("next").isNull()){after=null;break;}
                    if(!response.path("next").isTextual())throw invalidValue(BankReadException.Field.RESPONSE);
                    after=nextCursor(response.path("next").asText(),card,from,to);if(!seen.add(after))throw invalidValue(BankReadException.Field.RESPONSE);
                }
                if(after!=null)throw BankReadException.failure(TRANSACTIONS,RESULT_LIMIT);
                cards.add(new BankProductsProvider.Card(card,text(account,"name",100),List.copyOf(bills),List.copyOf(charges),available));
                if(cards.size()>10)throw BankReadException.failure(ACCOUNTS,RESULT_LIMIT);
            }
            return List.copyOf(cards);
        }catch(IllegalArgumentException|DateTimeException|IndexOutOfBoundsException error){throw BankReadException.failure(ACCOUNTS,INVALID_RESPONSE);}
    }
    private static String nextCursor(String next, UUID account, LocalDate from, LocalDate to) {
        // Never follow provider URLs. Extract only the cursor, keeping our host, account and dates fixed.
        if (!next.startsWith("?") || next.length() > 8192 || next.indexOf('#') >= 0)
            throw invalidValue(BankReadException.Field.RESPONSE);
        Map<String,String> parameters = new HashMap<>();
        for (String part : next.substring(1).split("&", -1)) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) throw invalidValue(BankReadException.Field.RESPONSE);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            if (!Set.of("accountId", "after", "dateFrom", "dateTo").contains(key)
                || parameters.putIfAbsent(key, value) != null) throw invalidValue(BankReadException.Field.RESPONSE);
        }
        if (!account.toString().equals(parameters.get("accountId"))
            || !from.minusDays(1).toString().equals(parameters.getOrDefault("dateFrom", from.minusDays(1).toString()))
            || !to.plusDays(1).toString().equals(parameters.getOrDefault("dateTo", to.plusDays(1).toString())))
            throw invalidValue(BankReadException.Field.RESPONSE);
        String cursor = parameters.get("after");
        if (cursor == null || cursor.isBlank() || cursor.length() > 4096 || cursor.chars().anyMatch(Character::isISOControl))
            throw invalidValue(BankReadException.Field.RESPONSE);
        return cursor;
    }
    private static String text(JsonNode node, String key, int max) {
        var field = switch (key) { case "id" -> BankReadException.Field.ID; case "name" -> BankReadException.Field.NAME; default -> BankReadException.Field.DESCRIPTION; };
        if(!node.path(key).isTextual()) throw invalidValue(field);
        String value = node.path(key).asText("").strip(); if (value.isBlank() || value.length() > max) throw invalidValue(field); return value;
    }
    private static BigDecimal decimal(JsonNode node, String key, boolean positive) {
        if (!node.path(key).isNumber()) throw invalidValue(BankReadException.Field.AMOUNT);
        BigDecimal value = node.get(key).decimalValue(); if (positive) value = value.abs();
        try { value = Money.parse(value.stripTrailingZeros().toPlainString()); } catch (ApiException e) { throw invalidValue(BankReadException.Field.AMOUNT); }
        if (positive && value.signum() <= 0) throw invalidValue(BankReadException.Field.AMOUNT); return value;
    }
    private static ProviderValueException invalidValue(BankReadException.Field field) { return new ProviderValueException(field); }
    private static final class ProviderValueException extends IllegalArgumentException {
        private final BankReadException.Field field;
        private ProviderValueException(BankReadException.Field field) { super("Invalid provider value"); this.field = field; }
    }
}
