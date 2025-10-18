package vn.edu.usth.flightinfo;

import android.Manifest;
import android.content.pm.PackageManager;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
import java.util.HashSet;
import java.util.List;
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

    private String AVIATIONSTACK_KEY;
    private String CLIENT_ID;
    private String CLIENT_SECRET;

    private OpenSkyAuthProvider authProvider;
    private OpenSkyService openSkyService;

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
    
    private LinearLayout searchStatusContainer;
    private ProgressBar searchProgressBar;
    private TextView searchStatusText;
    private TextView searchResultsCount;
    private LinearLayout noResultsContainer;
    private ImageButton closeResultsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_maps);

        AVIATIONSTACK_KEY = getString(R.string.aviationstack_key);
        CLIENT_ID = getString(R.string.opensky_client_id);
        CLIENT_SECRET = getString(R.string.opensky_client_secret);

        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        resultsContainer = findViewById(R.id.searchResultsContainer);
        resultsView = getLayoutInflater().inflate(R.layout.search_results, resultsContainer, false);
        resultsList = resultsView.findViewById(R.id.searchResultsList);
        resultsContainer.addView(resultsView);
        resultsContainer.setVisibility(View.GONE);
        
        searchStatusContainer = findViewById(R.id.searchStatusContainer);
        searchProgressBar = findViewById(R.id.searchProgressBar);
        searchStatusText = findViewById(R.id.searchStatusText);
        searchResultsCount = resultsView.findViewById(R.id.searchResultsCount);
        noResultsContainer = resultsView.findViewById(R.id.noResultsContainer);
        closeResultsButton = resultsView.findViewById(R.id.closeResultsButton);

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
                showSearchStatus("Searching for: " + query, true);
                searchFlights(query);
            }
            else {
                resultsContainer.setVisibility(View.GONE);
                showSearchStatus("Please enter a flight number or country", false);
            }
        });
        
        closeResultsButton.setOnClickListener(v -> {
            resultsContainer.setVisibility(View.GONE);
            hideSearchStatus();
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
        clearDetailFields();
    }

    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (isActive) { 
                getPlanesWithValidToken();
                handler.postDelayed(this, 10000);
            }
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
                
                if (selectedPLane != null && selectedPLane.equals(icao24)) {
                    GeoPoint currentPos = new GeoPoint(lat, lon);
                    fetchFlightTrack(icao24);
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

    private FlightDetailFragment getDetailSheet() {
        try {
            return (FlightDetailFragment) getSupportFragmentManager().findFragmentByTag("flight_detail");
        } catch (Exception e) {
            return null;
        }
    }

    private void handleMarkerClick(String icao24, String callsign, GeoPoint currentPos) {
        selectedPLane = icao24;
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
        clearLines();
        try {
            FlightDetailFragment placeholder = FlightDetailFragment.newInstance("{}");
            placeholder.show(getSupportFragmentManager(), "flight_detail");
            getSupportFragmentManager().executePendingTransactions();
        } catch (Exception e) {
            Log.e("FlightSheet", "Failed to show placeholder for " + icao24, e);
        }
        JSONObject cached = flightCache.getIfFresh(icao24);
        if (cached != null) {
            JSONObject arrival = cached.optJSONObject("arrival");
            processArrival(arrival, icao24, currentPos);
            JSONObject mergedData = mergeOpenSkyData(cached, finalOpenSkyData);
            showBasicInfo(icao24, mergedData);
            return;
        }
        fetchFlightInfo(callsign, icao24, currentPos, finalOpenSkyData);
    }

    private JSONObject mergeOpenSkyData(JSONObject aviationstackData, JSONObject openSkyData) {
        if (openSkyData == null) {
            return aviationstackData;
        }
        try {
            JSONObject merged = new JSONObject(aviationstackData.toString());
            JSONObject live = merged.optJSONObject("live");
            if (live == null) {
                live = new JSONObject();
                merged.put("live", live);
            }

            if (openSkyData.has("geo_alt") && !openSkyData.isNull("geo_alt")) {
                double altMeters = openSkyData.getDouble("geo_alt");
                live.put("altitude", Math.round(altMeters));
            }

            if (openSkyData.has("speed") && !openSkyData.isNull("speed")) {
                double speedMs = openSkyData.getDouble("speed");
                double speedKmh = speedMs * 3.6;
                live.put("speed_horizontal", Math.round(speedKmh));
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
        if (callsign == null) callsign = "";
        final String cleanCalls = cleanCallsign(callsign);
        if (cleanCalls.isEmpty() || cleanCalls.equalsIgnoreCase("UNKNOWN")) {
            Log.w("Aviationstack", "Empty/Unknown callsign for " + icao24 + ", will attempt fallback or abort.");
            runOnUiThread(() -> {
                showNoAviationstackRecord();
            });
            return;
        }
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
                    showNoAviationstackRecord();
                    return;
                }
                try {
                    JSONObject json = new JSONObject(body);
                    JSONArray data = json.optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        JSONObject match = data.getJSONObject(0);
                        JSONObject mergedMatch = mergeOpenSkyData(match, openSkyData);
                        flightCache.put(icao24, mergedMatch);

                        final JSONObject finalMatch = mergedMatch;
                        final String finalIcao24 = icao24;
                        final GeoPoint finalCurrentPos = currentPos;

                        processArrival(finalMatch.optJSONObject("arrival"), finalIcao24, finalCurrentPos);
                        runOnUiThread(() -> {
                            try {
                                FlightDetailFragment existing = getDetailSheet();
                                if (existing != null && existing.isAdded()) {
                                    existing.updateFromJson(finalMatch);
                                } else {
                                    FlightDetailFragment sheet = FlightDetailFragment.newInstance(finalMatch.toString());
                                    sheet.show(getSupportFragmentManager(), "flight_detail");
                                }
                            } catch (Exception ignored) {}
                        });
                        return;
                    } else {
                        runOnUiThread(() -> showNoAviationstackRecord());
                    }
                } catch (Exception e) {
                    Log.e("Aviationstack", "Parse error", e);
                    runOnUiThread(() -> showNoAviationstackRecord());
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
        String key = (iata != null && !iata.isEmpty()) ? iata : (icao != null && !icao.isEmpty() ? icao : null);
        if (key == null) {
            Log.w("Aviationstack", "No IATA/ICAO to lookup for " + icao24);
            return;
        }
        JSONObject cachedAirport = airportCache.getIfFresh(key);
        if (cachedAirport != null) {
                double lat = cachedAirport.optDouble("latitude", 0.0);
                double lon = cachedAirport.optDouble("longitude", 0.0);
                if (lat != 0.0 || lon != 0.0) {
                    GeoPoint arrivalPoint = new GeoPoint(lat, lon);
                    runOnUiThread(() -> { if (isActive) overlayManager.drawDashedLine(currentPos, arrivalPoint); });
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
                    return;
                }
        }

        String url = "https://api.aviationstack.com/v1/airports?access_key=" + AVIATIONSTACK_KEY;
        try {
            if (iata != null && !iata.isEmpty()) {
                url += "&iata_code=" + URLEncoder.encode(iata, "UTF-8");
            } else if (icao != null && !icao.isEmpty()) {
                url += "&icao_code=" + URLEncoder.encode(icao, "UTF-8");
            }
        } catch (Exception e) {
            Log.w("Aviationstack", "URL encoding failed for airport lookup", e);
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
                        JSONObject airport = data.getJSONObject(0); 
                        double lat = airport.optDouble("latitude", 0.0);
                        double lon = airport.optDouble("longitude", 0.0);

                        if (lat != 0.0 || lon != 0.0) {
                            airportCache.put(key, airport);
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
        try {
            FlightDetailFragment sheet = getDetailSheet();
            if (sheet != null && sheet.isAdded()) {
                sheet.clearFields();  // Clears to "No data" state
            }
        } catch (Exception e) {
            Log.e("FlightSheet", "Error clearing sheet for no data", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setMapToCurrentLocation();
            }
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
                    } else {
                        Log.w("OpenSky", "Cannot get user location");
                    }
                });
    }

    private void showBasicInfo(String icao24, JSONObject flight) {
        try {
            FlightDetailFragment sheet = getDetailSheet();
            if (sheet != null && sheet.isAdded()) {
                sheet.updateFromJson(flight);
            } else {
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
                runOnUiThread(() -> showSearchStatus("Search failed. Please try again.", false));
            }
        }).start();
    }

    private void showSearchResults(List<String> displayList, List<double[]> coordsList, List<String> icao24List, List<String> callsignList) {
        hideSearchStatus(); 
        if (displayList.isEmpty()) {
            resultsContainer.setVisibility(View.GONE);
            showSearchStatus("No results found", false);
            return;
        }
        searchResultsCount.setText(displayList.size() + " result" + (displayList.size() == 1 ? "" : "s") + " found");
        noResultsContainer.setVisibility(View.GONE);        
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
                showSearchStatus("No coordinates available", false);
                return;
            }
            MapView map = findViewById(R.id.map);
            IMapController controller = map.getController();
            controller.setZoom(8.0);
            GeoPoint target = new GeoPoint(lat, lon);
            controller.animateTo(target);
            resultsContainer.setVisibility(View.GONE);
            hideSearchStatus();
            tryOpenMarkerAfterMove(icao24, callsign, target, 0);
        });
    }

    private void tryOpenMarkerAfterMove(String icao24, String callsign, GeoPoint target, int attempt) {
        if (icao24 == null || icao24.isEmpty()) return;
        Marker m = overlayManager.getMarker(icao24);
        if (m != null) {
            selectedPLane = icao24;
            fetchFlightTrack(icao24);
            handleMarkerClick(icao24, callsign != null ? callsign.trim() : "", m.getPosition());
            return;
        }
        if (attempt == 0) {
            getPlanesWithValidToken();
        }
        if (attempt < 10) {
            handler.postDelayed(() -> tryOpenMarkerAfterMove(icao24, callsign, target, attempt + 1), 300);
        }
    }

    private void showSearchStatus(String message, boolean showProgress) {
        searchStatusText.setText(message);
        searchStatusContainer.setVisibility(View.VISIBLE);
        if (showProgress) {
            searchProgressBar.setVisibility(View.VISIBLE);
        } else {
            searchProgressBar.setVisibility(View.GONE);
        }
        if (!showProgress) {
            handler.postDelayed(this::hideSearchStatus, 3000);
        }
    }
    
    private void hideSearchStatus() {
        searchStatusContainer.setVisibility(View.GONE);
        searchProgressBar.setVisibility(View.GONE);
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