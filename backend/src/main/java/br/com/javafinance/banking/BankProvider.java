package br.com.javafinance.banking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public interface BankProvider {
    record RemoteAccount(UUID id, String name, String type, String currency, BigDecimal balance) {}
    record RemoteEntry(String id, String description, BigDecimal amount, LocalDate date, String direction, String status) {}
    record Snapshot(Instant updatedAt, List<RemoteAccount> accounts) {}
    Snapshot accounts(UUID item);
    List<RemoteEntry> transactions(UUID account, LocalDate from, LocalDate to);
}
