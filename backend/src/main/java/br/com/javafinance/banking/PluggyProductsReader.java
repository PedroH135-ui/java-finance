package br.com.javafinance.banking;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

/** Separate bean so optional product reads remain independently testable and fail independently. */
@Component
public class PluggyProductsReader implements BankProductsProvider {
 private final PluggyReadClient client;
 public PluggyProductsReader(BankProperties config,ObjectMapper mapper){this.client=new PluggyReadClient(config,mapper);}
 @Override public List<Card> cards(UUID item,LocalDate from,LocalDate to){return client.cards(item,from,to);}
 @Override public List<Position> investments(UUID item){return client.investments(item);}
}
