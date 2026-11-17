package br.com.javafinance.banking;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Additional read-only products. Never holds credentials, documents, card numbers or raw payloads. */
public interface BankProductsProvider {
 record Charge(String id,String description,BigDecimal amount,LocalDate date,String direction,String status,String billId,Integer installment,Integer installments) {}
 record Bill(String id,LocalDate dueDate,BigDecimal total) {}
 record Card(UUID id,String name,List<Bill> bills,List<Charge> charges,boolean billsAvailable) {}
 record Position(UUID id,String name,String type,String subtype,BigDecimal balance,LocalDate date,BigDecimal profit,BigDecimal available,String status) {}
 List<Card> cards(UUID item,LocalDate from,LocalDate to);
 List<Position> investments(UUID item);
}
