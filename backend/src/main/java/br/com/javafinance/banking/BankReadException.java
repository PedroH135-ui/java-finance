package br.com.javafinance.banking;

import br.com.javafinance.common.ApiException;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Only allow-listed diagnostics; never attach provider bodies, URLs or exception causes. */
public final class BankReadException extends ApiException {
    public enum Stage { AUTH, ITEM, ACCOUNTS, TRANSACTIONS, STORAGE }
    public enum Code { AUTH_DENIED, ACCESS_DENIED, RATE_LIMIT, HTTP_ERROR, TIMEOUT, NETWORK,
        INVALID_RESPONSE, ITEM_NOT_READY, RESULT_LIMIT, ACCOUNT_NOT_FOUND, STORAGE_ERROR }
    public enum Field { NONE, RESPONSE, ACCOUNT_ID, CURRENCY, DATE, ID, NAME, DESCRIPTION, AMOUNT, STATUS }
    public final Stage stage;
    public final Code code;
    public final String reference;

    private BankReadException(Stage stage, Code code, int providerStatus, String reference, Field field) {
        super(code == Code.ITEM_NOT_READY || code == Code.RESULT_LIMIT ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY,
            message(stage, code) + " Código: " + stage + "_" + code
            + (providerStatus > 0 ? " (HTTP " + providerStatus + ")" : "")
            + ". Referência: " + reference + ". Nenhum lançamento foi confirmado.");
        this.stage = stage; this.code = code; this.reference = reference;
        LoggerFactory.getLogger(BankReadException.class).warn(
            "bank_read_failed stage={} code={} provider_status={} reference={} field={}", stage, code, providerStatus, reference, field);
    }
    public static BankReadException failure(Stage stage, Code code) { return failure(stage, code, 0); }
    public static BankReadException failure(Stage stage, Code code, int providerStatus) {
        return failure(stage, code, providerStatus, Field.NONE);
    }
    public static BankReadException failure(Stage stage, Code code, int providerStatus, Field field) {
        return new BankReadException(stage, code, providerStatus, UUID.randomUUID().toString(), field);
    }
    private static String message(Stage stage, Code code) {
        String step = switch (stage) {
            case AUTH -> "Autenticação no Meu Pluggy";
            case ITEM -> "Consulta da conexão";
            case ACCOUNTS -> "Consulta das contas";
            case TRANSACTIONS -> "Consulta dos lançamentos";
            case STORAGE -> "Gravação dos dados para revisão";
        };
        String reason = switch (code) {
            case AUTH_DENIED -> "autenticação recusada; confira as credenciais no servidor";
            case ACCESS_DENIED -> "acesso recusado; confira a autorização no Meu Pluggy";
            case RATE_LIMIT -> "limite do provedor atingido; aguarde antes de tentar novamente";
            case TIMEOUT -> "tempo de resposta excedido";
            case NETWORK -> "não foi possível comunicar com o provedor";
            case ITEM_NOT_READY -> "a conexão não está pronta; confira sua situação no Meu Pluggy";
            case RESULT_LIMIT -> "consulta grande ou incompleta; reduza o período (máximo de 2.000 lançamentos)";
            case ACCOUNT_NOT_FOUND -> "a conta vinculada não foi retornada pelo provedor";
            case STORAGE_ERROR -> "não foi possível salvar a leitura; os dados anteriores foram preservados";
            case INVALID_RESPONSE -> "o provedor retornou dados incompatíveis com esta leitura";
            case HTTP_ERROR -> "o provedor recusou a consulta ou está indisponível";
        };
        return step + ": " + reason + ".";
    }
}
