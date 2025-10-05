package vn.edu.usth.flightinfo;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class FlightCache {
    private final Map<String, JSONObject> cache = new HashMap<>();
    private final Map<String, Long> cacheTime = new HashMap<>();
    private final long ttlMs;

    public FlightCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public void put(String key, JSONObject value) {
        cache.put(key, value);
        cacheTime.put(key, System.currentTimeMillis());
    }

    public JSONObject getIfFresh(String key) {
        Long t = cacheTime.get(key);
        if (t == null) return null;
        if (System.currentTimeMillis() - t > ttlMs) return null;
        return cache.get(key);
    }
}


