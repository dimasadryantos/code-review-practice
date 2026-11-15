package com.practice.pricing;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Caches the latest price per ISIN so the order service doesn't hit the market-data
 * provider on every request. Refreshed every 5 seconds in the background.
 *
 * PR #491 — "cache market prices to reduce load on market-data provider"
 * Review this class. Assume it is a Spring singleton used by many request threads.
 */
public class PriceCache {

    private static PriceCache instance;
    private final Map<String, Double> prices = new HashMap<>(); //ConcurrentHashMap would be better here to avoid synchronization issues
    private final MarketDataClient client;
    private boolean refreshing = false; // This flag is not thread-safe. Multiple threads could enter refresh() simultaneously, leading to inconsistent state.
    private long lastRefresh; // This variable is not thread-safe. It should be declared as volatile or accessed in a synchronized block to ensure visibility across threads.

    //atomicity,visibility,happens before (ordering of operations),race conditions,deadlocks,thread safety,concurrency issues.


    private PriceCache(MarketDataClient client) {
        this.client = client;
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                refresh();
            }
        }, 0, 5000);
    }

    public static synchronized PriceCache getInstance(MarketDataClient client) {
            if (instance == null) {
                instance = new PriceCache(client);
            }
            return instance;
    }

    public Double getPrice(String isin) {
        return prices.computeIfAbsent(isin, (key) -> client.fetchPrice(key));
    }

    public void refresh() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        for (String isin : prices.keySet()) {
            prices.put(isin, client.fetchPrice(isin)); //thread safety issue: multiple threads could be updating the prices map simultaneously, leading to inconsistent state. Consider using a ConcurrentHashMap or synchronizing access to the map.
        }
        lastRefresh = System.currentTimeMillis();
        refreshing = false;
    }

    public synchronized long getLastRefresh() {
        return lastRefresh;
    }

    public int size() {
        return prices.size();
    }

    interface MarketDataClient {
        Double fetchPrice(String isin);
    }
}
