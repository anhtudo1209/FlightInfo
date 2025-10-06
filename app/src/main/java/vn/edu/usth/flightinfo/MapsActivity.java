package vn.edu.usth.flightinfo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapsActivity extends AppCompatActivity {
    private MapView mapView;
    private OkHttpClient client = new OkHttpClient();
    private FusedLocationProviderClient fusedLocationClient;
    private String selectedPLane = null;
    private PlaneOverlayManager overlayManager;

    private static final String AVIATIONSTACK_KEY = "4f9fc2f6e6718f86805710054b5caa42";
    private static final String CLIENT_ID = "doanhtu1209-api-client";
    private static final String CLIENT_SECRET = "7LhSIF85OAyPGvS6NRDEcRXUuQ4oK4Lj";

    private OpenSkyAuthProvider authProvider;
    private OpenSkyService openSkyService;

    // moved markers into PlaneOverlayManager
    private FlightCache flightCache = new FlightCache(5 * 60 * 1000L);
    private AirportCache airportCache = new AirportCache(24*60*60*1000L);
    private Handler handler = new Handler();
    private static final int LOCATION_PERMISSION_REQUEST = 1000;
    private boolean isActive = false;
    EditText searchEditText;
    ImageButton searchButton;
    private FrameLayout resultsContainer;
    private View resultsView;
    private ListView resultsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_maps);

        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        resultsContainer = findViewById(R.id.searchResultsContainer);
        resultsView = getLayoutInflater().inflate(R.layout.search_results, resultsContainer, false);
        resultsList = resultsView.findViewById(R.id.searchResultsList);
        resultsContainer.addView(resultsView);
        resultsContainer.setVisibility(View.GONE);

        mapView = findViewById(R.id.map);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(9.0);
        overlayManager = new PlaneOverlayManager(this, mapView, (icao24, callsign, position) -> {
            selectedPLane = icao24;
            fetchFlightTrack(icao24);
            handleMarkerClick(icao24, callsign, position);
        });
        authProvider = new OpenSkyAuthProvider(client, CLIENT_ID, CLIENT_SECRET);
        openSkyService = new OpenSkyService(client, authProvider);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        } else {
            setMapToCurrentLocation();
        }
        searchButton.setOnClickListener(v -> {
            String query = searchEditText.getText().toString().trim();
            if (!query.isEmpty()) {
                Toast.makeText(this, "Searching for: " + query, Toast.LENGTH_SHORT).show();
                searchFlights(query);
            }
            else {
                resultsContainer.setVisibility(View.GONE);
                Toast.makeText(this, "Please enter a flight number or country", Toast.LENGTH_SHORT).show();
            }
        });
        ImageButton myLocationButton = findViewById(R.id.myLocationButton);
        myLocationButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            } else {
                setMapToCurrentLocation();
            }
        });
        mapView.addMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                getPlanesWithValidToken();
                return true;
            }
            @Override
            public boolean onZoom(ZoomEvent event) {
                getPlanesWithValidToken();
                return true;
            }
        }, 1000));

        MapEventsReceiver mReceive = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                // User tapped the map (not a marker) -> xoá tất cả các đường vẽ
                clearLines();
                selectedPLane = null;
                return true; // event handled
            }
            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0) {
                    resultsContainer.setVisibility(View.GONE);
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });


        MapEventsOverlay overlayEvents = new MapEventsOverlay(mReceive);
        mapView.getOverlays().add(overlayEvents);

        // ensure fragment (if previously shown) is cleared
        clearDetailFields();

        // Polling will start in onStart to be lifecycle-aware
    }

    // ----------------------------
    // 🔹 Lặp lại update
    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            getPlanesWithValidToken();
            handler.postDelayed(this, 10000); // refresh sau 10 giây
        }
    };

    private void getPlanesWithValidToken() {
        BoundingBox box = mapView.getBoundingBox();
        openSkyService.fetchPlanesWithValidToken(box, new OpenSkyService.StatesCallback() {
            @Override
            public void onSuccess(JSONArray states) {
                runOnUiThread(() -> handleStatesUpdate(states));
            }

            @Override
            public void onError(Exception e) {
                Log.e("OpenSky", "Failed to refresh planes", e);
            }
        });
    }
    private void handleStatesUpdate(JSONArray states) {
        if (!isActive) return;
        try {
            Set<String> seenPlanes = new HashSet<>();
            for (int i = 0; i < states.length(); i++) {
                JSONArray plane = states.optJSONArray(i);
                if (plane == null) continue;
                String icao24 = plane.optString(0, "");
                String callsign = plane.optString(1, "Unknown");
                double lon = plane.optDouble(5, 0.0);
                double lat = plane.optDouble(6, 0.0);
                Double heading = plane.isNull(10) ? null : plane.optDouble(10);
                if (lat == 0.0 && lon == 0.0) continue;
                double baroAlt = plane.isNull(7) ? Double.NaN : plane.optDouble(7, Double.NaN);
                double speed = plane.isNull(9) ? Double.NaN : plane.optDouble(9, Double.NaN);
                double geoAlt = plane.isNull(13) ? Double.NaN : plane.optDouble(13, Double.NaN);
                double altToPass = !Double.isNaN(geoAlt) ? geoAlt : baroAlt;
                overlayManager.updatePlaneMarker(icao24, callsign, lat, lon, heading, altToPass, speed);
                seenPlanes.add(icao24);
                
                // If this is the selected plane, redraw its lines
                if (selectedPLane != null && selectedPLane.equals(icao24)) {
                    GeoPoint currentPos = new GeoPoint(lat, lon);
                    // Simple: just redraw the lines from the current position
                    fetchFlightTrack(icao24);
                    // Redraw dashed line to destination
                    JSONObject cachedFlight = flightCache.getIfFresh(icao24);
                    if (cachedFlight != null) {
                        JSONObject arrival = cachedFlight.optJSONObject("arrival");
                        if (arrival != null) {
                            double arrivalLat = arrival.optDouble("latitude", 0.0);
                            double arrivalLon = arrival.optDouble("longitude", 0.0);
                            if (arrivalLat != 0.0 || arrivalLon != 0.0) {
                                GeoPoint arrivalPoint = new GeoPoint(arrivalLat, arrivalLon);
                                overlayManager.drawDashedLine(currentPos, arrivalPoint);
                            }
                        }
                    }
                }
            }
            overlayManager.removeMarkersNotIn(seenPlanes);
        } catch (Exception uiEx) {
            Log.e("OpenSky", "Error updating map overlays", uiEx);
        }
    }
    // moved to PlaneOverlayManager

    // heading offset logic handled inside PlaneOverlayManager

    // helper: return the current FlightDetailSheet instance if present (or null)
    private FlightDetailFragment getDetailSheet() {
        try {
            return (FlightDetailFragment) getSupportFragmentManager().findFragmentByTag("flight_detail");
        } catch (Exception e) {
            return null;
        }
    }
    private void handleMarkerClick(String icao24, String callsign, GeoPoint currentPos) {
        selectedPLane = icao24;

        // Get OpenSky altitude and speed from the marker's relatedObject
        JSONObject openSkyData = null;
        Marker marker = overlayManager.getMarker(icao24);
        if (marker != null) {
            try {
                Object relObj = marker.getRelatedObject();
                if (relObj instanceof JSONObject) {
                    openSkyData = (JSONObject) relObj;
                }
            } catch (Exception e) {
                Log.w("OpenSky", "Failed to get marker relatedObject", e);
            }
        }

        final JSONObject finalOpenSkyData = openSkyData;

        // Clear old lines and dismiss any existing fragment FIRST
        clearLines();

        // FIXED: Show EXACTLY ONE placeholder (empty) - this will be updated later by cache/API
        // No check for existing here - after clearLines(), none should exist
        try {
            FlightDetailFragment placeholder = FlightDetailFragment.newInstance("{}");
            placeholder.show(getSupportFragmentManager(), "flight_detail");

            // FIXED: Force synchronous commit so the fragment is immediately added to the manager
            // This prevents timing race on cache hit (getDetailSheet() will now find it)
            getSupportFragmentManager().executePendingTransactions();

            Log.d("FlightSheet", "Placeholder shown and committed synchronously for " + icao24);
        } catch (Exception e) {
            Log.e("FlightSheet", "Failed to show placeholder for " + icao24, e);
        }

        // Check cache first (will NOW find the placeholder and update it)
        JSONObject cached = flightCache.getIfFresh(icao24);
        if (cached != null) {
            JSONObject arrival = cached.optJSONObject("arrival");
            processArrival(arrival, icao24, currentPos);
            JSONObject mergedData = mergeOpenSkyData(cached, finalOpenSkyData);
            showBasicInfo(icao24, mergedData);
            return;
        }
        // No cache - fetch async (will update placeholder later)
        fetchFlightInfo(callsign, icao24, currentPos, finalOpenSkyData);
    }
    private JSONObject mergeOpenSkyData(JSONObject aviationstackData, JSONObject openSkyData) {
        if (openSkyData == null) {
            return aviationstackData;
        }

        try {
            // Create a copy to avoid modifying the cached data
            JSONObject merged = new JSONObject(aviationstackData.toString());

            // Get or create "live" object to store OpenSky altitude and speed
            JSONObject live = merged.optJSONObject("live");
            if (live == null) {
                live = new JSONObject();
                merged.put("live", live);
            }

            if (openSkyData.has("geo_alt") && !openSkyData.isNull("geo_alt")) {
                double altMeters = openSkyData.getDouble("geo_alt");
                live.put("altitude", Math.round(altMeters));
            }

            // Override with OpenSky speed (convert m/s to km/h)
            if (openSkyData.has("speed") && !openSkyData.isNull("speed")) {
                double speedMs = openSkyData.getDouble("speed");
                double speedKmh = speedMs * 3.6; // Convert m/s to km/h
                live.put("speed_horizontal", Math.round(speedKmh));
            }

            // Add timestamp from OpenSky
            if (openSkyData.has("ts")) {
                live.put("updated", openSkyData.getString("ts"));
            }

            return merged;
        } catch (Exception e) {
            Log.e("OpenSky", "Error merging OpenSky data", e);
            return aviationstackData;
        }
    }
    private String cleanCallsign(String callsign) {
        if (callsign == null) return "";
        String s = callsign.trim().replaceAll("\\s+", "");
        s = s.replaceAll("[^A-Za-z0-9]", "");
        return s.toUpperCase();
    }

    private void fetchFlightInfo(String callsign, String icao24, GeoPoint currentPos, JSONObject openSkyData) {
        // defensive: check callsign
        if (callsign == null) callsign = "";

        final String cleanCalls = cleanCallsign(callsign);
        Log.d("Aviationstack", "Original callsign='" + callsign + "' cleaned='" + cleanCalls + "'");

        if (cleanCalls.isEmpty() || cleanCalls.equalsIgnoreCase("UNKNOWN")) {
            // If no usable callsign, show fallback and return (or try other ways)
            Log.w("Aviationstack", "Empty/Unknown callsign for " + icao24 + ", will attempt fallback or abort.");
            runOnUiThread(() -> {
                showNoAviationstackRecord();
            });
            return;
        }

        // Attempt 1: query by flight_icao using cleaned value
        final String encoded;
        try { encoded = URLEncoder.encode(cleanCalls, "UTF-8"); }
        catch (Exception e) { Log.w("Aviationstack","URLEncoder failed, using raw cleanCalls", e); throw new RuntimeException(e); }

        String base = "https://api.aviationstack.com/v1/flights?access_key=" + AVIATIONSTACK_KEY;
        String url1 = base + "&flight_icao=" + encoded + "&limit=5";
        Log.d("Aviationstack", "Query flight_icao URL: " + url1);
        Request req1 = new Request.Builder().url(url1).build();

        client.newCall(req1).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("Aviationstack", "API failed", e);
                runOnUiThread(() -> {
                    showNoAviationstackRecord();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d("Aviationstack", "Response code=" + response.code() + " body=" + body);
                if (!response.isSuccessful()) {
                    Log.e("Aviationstack", "API error: " + response.code());
                    showNoAviationstackRecord();  // FIXED: Clears existing (no new sheet)
                    return;
                }
                try {
                    JSONObject json = new JSONObject(body);
                    JSONArray data = json.optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        JSONObject match = data.getJSONObject(0);

                        // Merge OpenSky altitude and speed into the Aviationstack data
                        JSONObject mergedMatch = mergeOpenSkyData(match, openSkyData);

                        // update cache with merged data
                        flightCache.put(icao24, mergedMatch);

                        final JSONObject finalMatch = mergedMatch;
                        final String finalIcao24 = icao24;
                        final GeoPoint finalCurrentPos = currentPos;

                        // process arrival
                        processArrival(finalMatch.optJSONObject("arrival"), finalIcao24, finalCurrentPos);

                        // FIXED: ALWAYS update existing placeholder (no runOnUiThread needed, no new creation)
                        try {
                            FlightDetailFragment existing = getDetailSheet();
                            if (existing != null && existing.isAdded()) {
                                existing.updateFromJson(finalMatch);
                                Log.d("FlightSheet", "Updated existing sheet with API data for " + icao24);
                            } else {
                                // Edge case: Create if missing (shouldn't happen)
                                Log.w("FlightSheet", "No existing sheet for API update - creating");
                                FlightDetailFragment sheet = FlightDetailFragment.newInstance(finalMatch.toString());
                                sheet.show(getSupportFragmentManager(), "flight_detail");
                            }
                        } catch (Exception ignored) {}
                        return;
                    } else {
                        // No matches — clear existing
                        showNoAviationstackRecord();  // FIXED: Clears existing (no new sheet)
                    }
                } catch (Exception e) {
                    Log.e("Aviationstack", "Parse error", e);
                    showNoAviationstackRecord();  // FIXED: Clears existing (no new sheet)
                }
            }
        });
    }
    private void fetchFlightTrack(String icao24) {
        openSkyService.fetchFlightTrackWithValidToken(icao24, new OpenSkyService.TrackCallback() {
            @Override
            public void onSuccess(List<GeoPoint> points) {
                runOnUiThread(() -> { if (isActive) overlayManager.drawFlightPath(points); });
            }

            @Override
            public void onError(Exception e) {
                Log.e("OpenSky", "Track fetch failed", e);
            }
        });
    }

    private void fetchAirportCoords(String icao24, GeoPoint currentPos, String iata, String icao) {
        // chọn key cache ưu tiên IATA > ICAO
        String key = (iata != null && !iata.isEmpty()) ? iata : (icao != null && !icao.isEmpty() ? icao : null);
        if (key == null) {
            Log.w("Aviationstack", "No IATA/ICAO to lookup for " + icao24);
            return;
        }
        // check cache
        JSONObject cachedAirport = airportCache.getIfFresh(key);
        if (cachedAirport != null) {
                double lat = cachedAirport.optDouble("latitude", 0.0);
                double lon = cachedAirport.optDouble("longitude", 0.0);
                if (lat != 0.0 || lon != 0.0) {
                    GeoPoint arrivalPoint = new GeoPoint(lat, lon);
                    runOnUiThread(() -> { if (isActive) overlayManager.drawDashedLine(currentPos, arrivalPoint); });
                    // also update the flightInfoCache arrival coordinates for future use
                    try {
                        JSONObject cachedFlight = flightCache.getIfFresh(icao24);
                        if (cachedFlight != null) {
                            JSONObject arrival = cachedFlight.optJSONObject("arrival");
                            if (arrival == null) {
                                arrival = new JSONObject();
                                cachedFlight.put("arrival", arrival);
                            }
                            arrival.put("latitude", lat);
                            arrival.put("longitude", lon);
                            // re-put to ensure updated copy is cached
                            flightCache.put(icao24, cachedFlight);
                        }
                    } catch (Exception e) {
                        Log.w("Aviationstack", "Couldn't update cached flight arrival coords", e);
                    }
                    return;
                }
        }

        // Build URL
        String url = "https://api.aviationstack.com/v1/airports?access_key=" + AVIATIONSTACK_KEY;
        try {
            if (iata != null && !iata.isEmpty()) {
                url += "&iata_code=" + URLEncoder.encode(iata, "UTF-8");
            } else if (icao != null && !icao.isEmpty()) {
                url += "&icao_code=" + URLEncoder.encode(icao, "UTF-8");
            }
        } catch (Exception e) {
            // ignore encoding error, continue with raw
        }

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("Aviationstack", "Airport API failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("Aviationstack", "Airport API error: " + response.code());
                    return;
                }
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray data = json.optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        JSONObject airport = data.getJSONObject(0); // lấy kết quả đầu tiên
                        double lat = airport.optDouble("latitude", 0.0);
                        double lon = airport.optDouble("longitude", 0.0);

                        if (lat != 0.0 || lon != 0.0) {
                            // cache airport bằng key
                            airportCache.put(key, airport);

                            // cập nhật flightInfoCache arrival coords nếu có record flight
                            try {
                                JSONObject cachedFlight = flightCache.getIfFresh(icao24);
                                if (cachedFlight != null) {
                                    JSONObject arrival = cachedFlight.optJSONObject("arrival");
                                    if (arrival == null) {
                                        arrival = new JSONObject();
                                        cachedFlight.put("arrival", arrival);
                                    }
                                    arrival.put("latitude", lat);
                                    arrival.put("longitude", lon);
                                    flightCache.put(icao24, cachedFlight);
                                }
                            } catch (Exception e) {
                                Log.w("Aviationstack", "Couldn't update cached flight arrival coords", e);
                            }

                            GeoPoint arrivalPoint = new GeoPoint(lat, lon);
                            runOnUiThread(() -> { if (isActive) overlayManager.drawDashedLine(currentPos, arrivalPoint); });
                        } else {
                            Log.w("Aviationstack", "Airport record has no coords for " + key);
                        }
                    } else {
                        Log.w("Aviationstack", "No airport result for " + key);
                    }
                } catch (Exception e) {
                    Log.e("Aviationstack", "Airport parse error", e);
                }
            }
        });
    }
    private void processArrival(JSONObject arrival, String icao24, GeoPoint currentPos) {
        if (arrival != null) {
            String airportIata = arrival.optString("iata", "");
            String airportIcao = arrival.optString("icao", "");

            if (!airportIata.isEmpty() || !airportIcao.isEmpty()) {
                fetchAirportCoords(icao24, currentPos, airportIata, airportIcao);
            } else {
                Log.w("Aviationstack", "No arrival IATA/ICAO for flight " + icao24);
            }
        }
    }

    // dashed and solid path drawing moved to PlaneOverlayManager
    private void clearLines() {
        overlayManager.clearLines();
        try {
            FlightDetailFragment sheet = getDetailSheet();
            if (sheet != null && sheet.isAdded()) {
                sheet.dismiss();
            }
        } catch (Exception ignored) {}
    }
    private void clearDetailFields() {
        // ask the fragment (if visible) to clear itself
        runOnUiThread(() -> {
            try {
                FlightDetailFragment sheet = getDetailSheet();
                if (sheet != null && sheet.isAdded()) {
                    sheet.clearFields();
                }
            } catch (Exception ignored) {}
        });
    }

    private void showNoAviationstackRecord() {
        // FIXED: ONLY clear the existing placeholder (never create new - prevents duplicates)
        // Assume placeholder was shown in handleMarkerClick
        try {
            FlightDetailFragment sheet = getDetailSheet();
            if (sheet != null && sheet.isAdded()) {
                sheet.clearFields();  // Clears to "No data" state
                Log.d("FlightSheet", "Cleared existing sheet (no data)");  // Debug log
            } else {
                // Edge case: No sheet? Log warning but don't create (avoids spam)
                Log.w("FlightSheet", "No sheet to clear for no-data state");
            }
        } catch (Exception e) {
            Log.e("FlightSheet", "Error clearing sheet for no data", e);
        }
    }


    private void setMapToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        GeoPoint userPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                        mapView.getController().setZoom(9.0);
                        mapView.getController().setCenter(userPoint);
                        Log.d("OpenSky", "User location: " + userPoint);
                    } else {
                        Log.w("OpenSky", "Không lấy được vị trí hiện tại");
                    }
                });
    }
    private void showBasicInfo(String icao24, JSONObject flight) {
        // FIXED: ALWAYS update existing placeholder (never create new - prevents duplicates)
        // Assume placeholder was shown in handleMarkerClick
        try {
            FlightDetailFragment sheet = getDetailSheet();
            if (sheet != null && sheet.isAdded()) {
                sheet.updateFromJson(flight);
                Log.d("FlightSheet", "Updated existing sheet with data for " + icao24);
            } else {
                // Edge case: No sheet? Create one (shouldn't happen, but safe)
                Log.w("FlightSheet", "No existing sheet found - creating new for " + icao24);
                FlightDetailFragment newSheet = FlightDetailFragment.newInstance(flight.toString());
                newSheet.show(getSupportFragmentManager(), "flight_detail");
            }
        } catch (Exception e) {
            Log.e("FlightSheet", "Error updating sheet for " + icao24, e);
        }
    }
    private void searchFlights(String query) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("https://opensky-network.org/api/states/all")
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) return;

                String json = response.body().string();
                JSONObject root = new JSONObject(json);
                JSONArray states = root.getJSONArray("states");

                // store text + lat/lon together
                List<String> displayList = new ArrayList<>();
                List<double[]> coordsList = new ArrayList<>();
                List<String> icao24List = new ArrayList<>();
                List<String> callsignList = new ArrayList<>();

                for (int i = 0; i < states.length(); i++) {
                    JSONArray arr = states.getJSONArray(i);
                    String icao24 = arr.optString(0, "").trim();
                    String callsign = arr.optString(1, "").trim();
                    String origin = arr.optString(2, "").trim();
                    double lon = arr.isNull(5) ? 0.0 : arr.getDouble(5);
                    double lat = arr.isNull(6) ? 0.0 : arr.getDouble(6);

                    if (callsign.toLowerCase().contains(query.toLowerCase()) ||
                            origin.toLowerCase().contains(query.toLowerCase())) {

                        displayList.add(callsign + " — " + origin);
                        coordsList.add(new double[]{lat, lon});
                        icao24List.add(icao24);
                        callsignList.add(callsign);
                    }
                }

                runOnUiThread(() -> showSearchResults(displayList, coordsList, icao24List, callsignList));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Search failed. Please try again.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    private void showSearchResults(List<String> displayList, List<double[]> coordsList, List<String> icao24List, List<String> callsignList) {
        if (displayList.isEmpty()) {
            resultsContainer.setVisibility(View.GONE);
            Toast.makeText(this, "No results found", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                displayList
        );

        resultsList.setAdapter(adapter);
        resultsContainer.setVisibility(View.VISIBLE);

        resultsList.setOnItemClickListener((parent, view, position, id) -> {
            double[] coords = coordsList.get(position);
            double lat = coords[0];
            double lon = coords[1];
            String icao24 = icao24List.get(position);
            String callsign = callsignList.get(position);

            if (lat == 0 && lon == 0) {
                Toast.makeText(this, "No coordinates available", Toast.LENGTH_SHORT).show();
                return;
            }

            MapView map = findViewById(R.id.map);
            IMapController controller = map.getController();
            controller.setZoom(8.0);
            GeoPoint target = new GeoPoint(lat, lon);
            controller.animateTo(target);

            resultsContainer.setVisibility(View.GONE);

            // Attempt to open the corresponding marker's info
            tryOpenMarkerAfterMove(icao24, callsign, target, 0);
        });
    }

    // Tries to open the marker panel for a plane by icao24, retrying briefly if marker not yet present
    private void tryOpenMarkerAfterMove(String icao24, String callsign, GeoPoint target, int attempt) {
        if (icao24 == null || icao24.isEmpty()) return;

        Marker m = overlayManager.getMarker(icao24);
        if (m != null) {
            selectedPLane = icao24;
            fetchFlightTrack(icao24);
            handleMarkerClick(icao24, callsign != null ? callsign.trim() : "", m.getPosition());
            return;
        }

            // Not present yet: trigger a refresh and retry a few times
        if (attempt == 0) {
            getPlanesWithValidToken();
        }

        if (attempt < 10) {
            handler.postDelayed(() -> tryOpenMarkerAfterMove(icao24, callsign, target, attempt + 1), 300);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActive = true;
        getPlanesWithValidToken();
        handler.postDelayed(updateTask, 10000);
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActive = false;
        handler.removeCallbacks(updateTask);
        try {
            client.dispatcher().cancelAll();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTask);
        client.dispatcher().cancelAll();
    }

}