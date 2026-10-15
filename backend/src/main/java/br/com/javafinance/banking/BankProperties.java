package br.com.javafinance.banking;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="app.banking")
public class BankProperties {
    private String clientId = "", clientSecret = "";
    private UUID ownerId;
    private Set<UUID> itemIds = new HashSet<>();
    public String getClientId() { return clientId; }
    public void setClientId(String value) { clientId = value; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String value) { clientSecret = value; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID value) { ownerId = value; }
    public Set<UUID> getItemIds() { return itemIds; }
    public void setItemIds(Set<UUID> value) { itemIds = value; }
    public boolean ready() { return ownerId != null && !clientId.isBlank() && !clientSecret.isBlank() && !itemIds.isEmpty() && itemIds.size() <= 5; }
}
