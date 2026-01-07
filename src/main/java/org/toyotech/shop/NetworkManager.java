package org.toyotech.shop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class NetworkManager {
    private final Shop plugin;
    private final HttpClient client;
    private final Gson gson;

    public NetworkManager(Shop plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new Gson();
    }

    public CompletableFuture<JsonArray> getPendingOrders() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(plugin.cfg.backendBaseUrl + "/api/plugin/orders/pending"))
                .header("X-TRADE-SECRET", plugin.cfg.pluginSecret)
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> gson.fromJson(body, JsonArray.class));
    }

    public CompletableFuture<Void> updateOrderStatus(String orderId, String status, String message) {
        JsonObject json = new JsonObject();
        if (message != null) json.addProperty("message", message);
        
        String endpoint = "/api/plugin/orders/" + orderId + "/" + status.toLowerCase(); // processing/succeeded/failed

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(plugin.cfg.backendBaseUrl + endpoint))
                .header("X-TRADE-SECRET", plugin.cfg.pluginSecret)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(json)))
                .build();
        
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(r -> {});
    }

    public void upsertDebt(String id, String playerName, String currencyMaterial, int remainingAmount, long dueTick, String status) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("playerName", playerName);
        json.addProperty("currencyMaterial", currencyMaterial);
        json.addProperty("remainingAmount", remainingAmount);
        json.addProperty("dueTick", dueTick);
        json.addProperty("status", status);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(plugin.cfg.backendBaseUrl + "/api/plugin/debts/upsert"))
                .header("X-TRADE-SECRET", plugin.cfg.pluginSecret)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(json)))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }
    
    public void reportPlayerState(String playerName, boolean online, int tpTickets, int invsaveTickets, long lastTick) {
        JsonObject json = new JsonObject();
        json.addProperty("playerName", playerName);
        json.addProperty("online", online);
        json.addProperty("tpTickets", tpTickets);
        json.addProperty("invsaveTickets", invsaveTickets);
        json.addProperty("lastTick", lastTick);
        
         HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(plugin.cfg.backendBaseUrl + "/api/plugin/player_state"))
                .header("X-TRADE-SECRET", plugin.cfg.pluginSecret)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(json)))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }
}
