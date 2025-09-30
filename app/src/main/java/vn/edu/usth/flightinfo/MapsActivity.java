package vn.edu.usth.flightinfo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.json.JSONArray;
import org.json.JSONObject;
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
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MapsActivity extends AppCompatActivity {
    // bottom sheet UI
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    private LinearLayout bottomSheet;
    private TextView textBasicInfo;
    private TextView textAirline;   // thêm nếu muốn hiển thị hãng
    private TextView textRoute;
    // removed old textDetail
    private ScrollView detailScroll;
    private Button btnSeeMore;

    // mini cards
    private TextView textAlt;
    private TextView textSpeed;
    private TextView textReg;
    private TextView textProgress;

    // Structured detail views (match the IDs in activity_maps.xml)
    private TextView depAirportTv, depCodesTv, depTerminalTv, depBaggageTv, depTimesTv, depRunwaysTv, depTimezoneTv;
    private TextView arrAirportTv, arrCodesTv, arrTerminalTv, arrBaggageTv, arrTimesTv, arrRunwaysTv, arrTimezoneTv;
    private TextView flightNumberTv, flightCodesharedTv, airlineNameTv;
    private TextView aircraftRegTv, aircraftCodesTv, aircraftModelTv;
    private TextView liveLatlonTv, liveAltTv, liveSpeedTv, liveHeadingTv, liveIsGroundTv, liveUpdatedTv;

    private MapView mapView;
    private OkHttpClient client = new OkHttpClient();

    private FusedLocationProviderClient fusedLocationClient;

    private String selectedPLane = null;
    private Polyline currentFlightPath = null;
    private Polyline currentFuturePath = null;

    // 🔑 Thông tin OAuth2 (thay bằng của bạn)
    private static final String AVIATIONSTACK_KEY = "4ef4a111e11025eba6e3c6d2318a3ec5";
    private static final String CLIENT_ID = "doanhtu1209-api-client";
    private static final String CLIENT_SECRET = "7LhSIF85OAyPGvS6NRDEcRXUuQ4oK4Lj";

    // Lưu token và thời gian hết hạn
    private String accessToken = null;
    private long tokenExpiryTime = 0;

    // Lưu danh sách marker của máy bay theo icao24
    private Map<String, Marker> planeMarkers = new HashMap<>();
    private Map<String, JSONObject> flightInfoCache = new HashMap<>();
    private Map<String, Long> flightInfoCacheTime = new HashMap<>();
    private Map<String, JSONObject> airportCache = new HashMap<>();
    private Map<String, Long> airportCacheTime = new HashMap<>();
    private static final long AIRPORT_CACHE_TTL = 24*60*60*1000L;
    private static final long FLIGHT_INFO_CACHE_TTL = 5 * 60 * 1000L;

    // Handler để update định kỳ
    private Handler handler = new Handler();
    private static final int LOCATION_PERMISSION_REQUEST = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_maps);

        // Khởi tạo MapView
        mapView = findViewById(R.id.map);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(9.0);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        } else {
            setMapToCurrentLocation();
        }
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

        MapEventsOverlay overlayEvents = new MapEventsOverlay(mReceive);
        mapView.getOverlays().add(overlayEvents);
        // --- Bottom sheet init ---
        bottomSheet = findViewById(R.id.bottomSheet);
        textBasicInfo = findViewById(R.id.textBasicInfo);
        textRoute = findViewById(R.id.textRoute);
        // removed old textDetail binding
        detailScroll = findViewById(R.id.detailScroll);
        btnSeeMore = findViewById(R.id.btnSeeMore);
        textAirline = findViewById(R.id.textAirline);
        textAlt = findViewById(R.id.textAlt);
        textSpeed = findViewById(R.id.textSpeed);
        textReg = findViewById(R.id.textReg);
        textProgress = findViewById(R.id.textProgress);

        // --- bind structured detail textviews ---
        depAirportTv     = findViewById(R.id.dep_airport);
        depCodesTv       = findViewById(R.id.dep_codes);
        depTerminalTv    = findViewById(R.id.dep_terminal);
        depBaggageTv     = findViewById(R.id.dep_baggage);
        depTimesTv       = findViewById(R.id.dep_times);
        depRunwaysTv     = findViewById(R.id.dep_runways);
        depTimezoneTv    = findViewById(R.id.dep_timezone);

        arrAirportTv     = findViewById(R.id.arr_airport);
        arrCodesTv       = findViewById(R.id.arr_codes);
        arrTerminalTv    = findViewById(R.id.arr_terminal);
        arrBaggageTv     = findViewById(R.id.arr_baggage);
        arrTimesTv       = findViewById(R.id.arr_times);
        arrRunwaysTv     = findViewById(R.id.arr_runways);
        arrTimezoneTv    = findViewById(R.id.arr_timezone);

        flightNumberTv   = findViewById(R.id.flight_number);
        flightCodesharedTv = findViewById(R.id.flight_codeshared);
        airlineNameTv    = findViewById(R.id.airline_name);

        aircraftRegTv    = findViewById(R.id.aircraft_reg);
        aircraftCodesTv  = findViewById(R.id.aircraft_codes);
        aircraftModelTv  = findViewById(R.id.aircraft_model);

        liveLatlonTv     = findViewById(R.id.live_latlon);
        liveAltTv        = findViewById(R.id.live_alt);
        liveSpeedTv      = findViewById(R.id.live_speed);
        liveHeadingTv    = findViewById(R.id.live_heading);
        liveIsGroundTv   = findViewById(R.id.live_isground);
        liveUpdatedTv    = findViewById(R.id.live_updated);

        // behavior
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setHideable(true);

        // start completely hidden
        bottomSheet.setVisibility(View.GONE);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // See more button: expand/collapse detail area
        btnSeeMore.setOnClickListener(v -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                detailScroll.setVisibility(View.GONE);
                btnSeeMore.setText("See more");
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                detailScroll.setVisibility(View.VISIBLE);
                btnSeeMore.setText("Hide");
            }
        });

        // ensure visibility toggled when hidden
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheetView, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheet.setVisibility(View.GONE);
                    detailScroll.setVisibility(View.GONE);
                    btnSeeMore.setText("See more");
                } else {
                    if (bottomSheet.getVisibility() != View.VISIBLE) {
                        bottomSheet.setVisibility(View.VISIBLE);
                    }
                }
            }
            @Override public void onSlide(@NonNull View bottomSheetView, float v) {}
        });

        // clear detail placeholders at startup
        clearDetailFields();

        // Gọi API lần đầu
        getPlanesWithValidToken();
        // Lặp lại sau mỗi 10 giây
        handler.postDelayed(updateTask, 10000);
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

    // ----------------------------
    // 🔹 Lấy Access Token
    private void fetchAccessToken() {
        String url = "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("OpenSky", "Token request failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("OpenSky", "Token request error: " + response.code());
                    return;
                }

                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    accessToken = json.getString("access_token");
                    int expiresIn = json.getInt("expires_in");

                    // Lưu thời gian hết hạn
                    tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000L);

                    Log.d("OpenSky", "Token fetched, expires in " + expiresIn + "s");

                    // Gọi API lấy máy bay sau khi có token
                    fetchPlanes();

                } catch (Exception e) {
                    Log.e("OpenSky", "Token parse error", e);
                }
            }
        });
    }

    // ----------------------------
    // 🔹 Kiểm tra token còn hạn không
    private boolean isTokenValid() {
        return accessToken != null && System.currentTimeMillis() < tokenExpiryTime;
    }

    // ----------------------------
    // 🔹 Đảm bảo luôn có token trước khi gọi API
    private void getPlanesWithValidToken() {
        if (!isTokenValid()) {
            Log.d("OpenSky", "Token expired, fetching new one...");
            fetchAccessToken();
        } else {
            fetchPlanes();
        }
    }

    // ----------------------------
    // 🔹 Gọi API lấy dữ liệu máy bay
    private void fetchPlanes() {
        BoundingBox box = mapView.getBoundingBox();
        double lamin = box.getLatSouth();
        double lamax = box.getLatNorth();
        double lomin = box.getLonWest();
        double lomax = box.getLonEast();
        String url = "https://opensky-network.org/api/states/all?" + "lamin=" + lamin + "&lomin=" + lomin + "&lamax=" + lamax + "&lomax=" + lomax;


        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("OpenSky", "API request failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("OpenSky", "API error: " + response.code());
                    return;
                }

                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray states = json.optJSONArray("states"); // dùng optJSONArray
                    if (states == null) {
                        Log.w("OpenSky", "No states data in response");
                        return;
                    }


                    runOnUiThread(() -> {
                        Set<String> seenPlanes = new HashSet<>();
                        for (int i = 0; i < states.length(); i++) {
                            JSONArray plane = states.optJSONArray(i);
                            if (plane == null) continue;

                            String icao24 = plane.optString(0, "");
                            String callsign = plane.optString(1, "Unknown");
                            double lon = plane.optDouble(5, 0.0);
                            double lat = plane.optDouble(6, 0.0);
                            double heading = plane.optDouble(10, 0.0);

                            if (lat == 0.0 && lon == 0.0) continue;

                            updatePlaneMarker(icao24, callsign, lat, lon, heading);
                            seenPlanes.add(icao24);
                        }
                        Iterator<String> it = planeMarkers.keySet().iterator();
                        while (it.hasNext()) {
                            String id = it.next();
                            if (!seenPlanes.contains(id)) {
                                mapView.getOverlays().remove(planeMarkers.get(id));
                                it.remove();
                            }
                        }
                        mapView.invalidate();
                    });

                } catch (Exception e) {
                    Log.e("OpenSky", "JSON parse error", e);
                }
            }
        });
    }

    // ----------------------------
    // 🔹 Cập nhật hoặc thêm máy bay
    private void updatePlaneMarker(String icao24, String title, double lat, double lon, double heading) {
        GeoPoint point = new GeoPoint(lat, lon);
        Marker marker;
        if (planeMarkers.containsKey(icao24)) {
            // Di chuyển marker cũ
            marker = planeMarkers.get(icao24);
            marker.setPosition(point);
            marker.setRotation((float) heading);
        } else {
            // Tạo marker mới
            marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle(title);
            Drawable icon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_plane, getTheme());
            marker.setOnMarkerClickListener((m, mapView) ->{
                String callsignTrim = title != null ? title.trim() : "";
                selectedPLane = icao24;
                fetchFlightTrack(icao24);
                handleMarkerClick(icao24, callsignTrim, m.getPosition());
                return false;
            });
            if (icon != null) {
                icon.setTint(0xFF2196F3);
                marker.setIcon(icon);
            }
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setRotation((float) heading);

            mapView.getOverlays().add(marker);
            planeMarkers.put(icao24, marker);
        }
    }
    private void handleMarkerClick(String icao24, String callsign, GeoPoint currentPos) {
        selectedPLane = icao24;

        // immediate minimal UI (so user doesn't only see XML defaults)
        final String quickCS = (callsign == null || callsign.isEmpty()) ? icao24 : callsign.trim();
        runOnUiThread(() -> {
            if (textBasicInfo != null) textBasicInfo.setText("Flight: " + quickCS);
            if (textRoute != null) textRoute.setText("(Loading...)");
            // clear structured detail fields (instead of textDetail)
            clearDetailFields();
            if (bottomSheetBehavior != null) {
                bottomSheet.setVisibility(View.VISIBLE);
                bottomSheetBehavior.setPeekHeight(dpToPx(120));
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        // clear old lines and fetch real details
        clearLines();

        // caching check + fetch as you already had
        Long t = flightInfoCacheTime.get(icao24);
        if (t != null && System.currentTimeMillis() - t < FLIGHT_INFO_CACHE_TTL) {
            JSONObject cached = flightInfoCache.get(icao24);
            if (cached != null) {
                JSONObject arrival = cached.optJSONObject("arrival");
                processArrival(arrival, icao24, currentPos);
                showBasicInfo(icao24, cached);
                return;
            }
        }
        fetchFlightInfo(callsign, icao24, currentPos);
    }


    private String cleanCallsign(String callsign) {
        if (callsign == null) return "";
        String s = callsign.trim().replaceAll("\\s+", "");
        s = s.replaceAll("[^A-Za-z0-9]", "");
        return s.toUpperCase();
    }

    private void fetchFlightInfo(String callsign, String icao24, GeoPoint currentPos) {
        // defensive: check callsign
        if (callsign == null) callsign = "";

        final String cleanCalls = cleanCallsign(callsign);
        Log.d("Aviationstack", "Original callsign='" + callsign + "' cleaned='" + cleanCalls + "'");

        if (cleanCalls.isEmpty() || cleanCalls.equalsIgnoreCase("UNKNOWN")) {
            // If no usable callsign, show fallback and return (or try other ways)
            Log.w("Aviationstack", "Empty/Unknown callsign for " + icao24 + ", will attempt fallback or abort.");
            runOnUiThread(() -> {
                if (textBasicInfo != null) textBasicInfo.setText("Flight: " + icao24);
                if (textRoute != null) textRoute.setText("(No schedule found)");
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
                    if (textRoute != null) textRoute.setText("(Aviationstack request failed)");
                    showNoAviationstackRecord();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d("Aviationstack", "Response code=" + response.code() + " body=" + body);
                if (!response.isSuccessful()) {
                    Log.e("Aviationstack", "API error: " + response.code());
                    runOnUiThread(() -> {
                        if (textRoute != null) textRoute.setText("(Aviationstack error)");
                        showNoAviationstackRecord();
                    });
                    return;
                }
                try {
                    JSONObject json = new JSONObject(body);
                    JSONArray data = json.optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        JSONObject match = data.getJSONObject(0);

                        // update caches (safe to do here)
                        flightInfoCache.put(icao24, match);
                        flightInfoCacheTime.put(icao24, System.currentTimeMillis());

                        // Make final copies for lambda capture
                        final JSONObject finalMatch = match;
                        final String finalIcao24 = icao24;
                        final GeoPoint finalCurrentPos = currentPos;

                        // process arrival (network/UI safe since processArrival queues its own requests)
                        processArrival(finalMatch.optJSONObject("arrival"), finalIcao24, finalCurrentPos);

                        // update UI using final copies
                        runOnUiThread(() -> showBasicInfo(finalIcao24, finalMatch));
                        return;
                    } else {
                        // No matches — show not found (we rely on flight_icao only)
                        runOnUiThread(() -> {
                            if (textRoute != null) textRoute.setText("(No schedule found)");
                            showNoAviationstackRecord();
                        });
                    }
                } catch (Exception e) {
                    Log.e("Aviationstack", "Parse error", e);
                    runOnUiThread(() -> showNoAviationstackRecord());
                }
            }
        });
    }

    private void fetchFlightTrack(String icao24) {
        String url = "https://opensky-network.org/api/tracks/all" + "?icao24=" + icao24 + "&time=0";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("OpenSky", "Track API failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("OpenSky", "Track API error: " + response.code());
                    return;
                }

                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray path = json.getJSONArray("path");

                    List<GeoPoint> points = new ArrayList<>();
                    for (int i = 0; i < path.length(); i++) {
                        JSONArray pos = path.getJSONArray(i);
                        double lat = pos.optDouble(1, 0.0);
                        double lon = pos.optDouble(2, 0.0);

                        if (lat != 0.0 && lon != 0.0) {
                            points.add(new GeoPoint(lat, lon));
                        }
                    }

                    runOnUiThread(() -> drawFlightPath(points));

                } catch (Exception e) {
                    Log.e("OpenSky", "Track JSON parse error", e);
                }
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
        Long t = airportCacheTime.get(key);
        if (t != null && System.currentTimeMillis() - t < AIRPORT_CACHE_TTL) {
            JSONObject cachedAirport = airportCache.get(key);
            if (cachedAirport != null) {
                double lat = cachedAirport.optDouble("latitude", 0.0);
                double lon = cachedAirport.optDouble("longitude", 0.0);
                if (lat != 0.0 || lon != 0.0) {
                    GeoPoint arrivalPoint = new GeoPoint(lat, lon);
                    runOnUiThread(() -> drawDashedLine(currentPos, arrivalPoint));
                    // also update the flightInfoCache arrival coordinates for future use
                    try {
                        JSONObject cachedFlight = flightInfoCache.get(icao24);
                        if (cachedFlight != null) {
                            JSONObject arrival = cachedFlight.optJSONObject("arrival");
                            if (arrival == null) {
                                arrival = new JSONObject();
                                cachedFlight.put("arrival", arrival);
                            }
                            arrival.put("latitude", lat);
                            arrival.put("longitude", lon);
                        }
                    } catch (Exception e) {
                        Log.w("Aviationstack", "Couldn't update cached flight arrival coords", e);
                    }
                    return;
                }
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
                            airportCacheTime.put(key, System.currentTimeMillis());

                            // cập nhật flightInfoCache arrival coords nếu có record flight
                            try {
                                JSONObject cachedFlight = flightInfoCache.get(icao24);
                                if (cachedFlight != null) {
                                    JSONObject arrival = cachedFlight.optJSONObject("arrival");
                                    if (arrival == null) {
                                        arrival = new JSONObject();
                                        cachedFlight.put("arrival", arrival);
                                    }
                                    arrival.put("latitude", lat);
                                    arrival.put("longitude", lon);
                                }
                            } catch (Exception e) {
                                Log.w("Aviationstack", "Couldn't update cached flight arrival coords", e);
                            }

                            GeoPoint arrivalPoint = new GeoPoint(lat, lon);
                            runOnUiThread(() -> drawDashedLine(currentPos, arrivalPoint));
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

    private void drawDashedLine(GeoPoint start, GeoPoint end) {
        // Xoá đường đứt cũ nếu có
        if (currentFuturePath != null) {
            mapView.getOverlays().remove(currentFuturePath);
            currentFuturePath = null;
        }

        Polyline dashedLine = new Polyline();
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(start);
        pts.add(end);
        dashedLine.setPoints(pts);
        dashedLine.setColor(Color.BLUE);
        dashedLine.setWidth(6f);

        // Dash effect: 20px line, 20px gap
        dashedLine.getOutlinePaint().setPathEffect(
                new android.graphics.DashPathEffect(new float[]{20, 20}, 0)
        );

        currentFuturePath = dashedLine;
        mapView.getOverlays().add(dashedLine);
        mapView.invalidate();
    }

    private void drawFlightPath(List<GeoPoint> points) {
        // Remove old path
        if (currentFlightPath != null) {
            mapView.getOverlays().remove(currentFlightPath);
        }

        // Draw new path
        currentFlightPath = new Polyline();
        currentFlightPath.setWidth(5f);
        currentFlightPath.setColor(Color.RED); // past path
        currentFlightPath.setPoints(points);

        mapView.getOverlays().add(currentFlightPath);
        mapView.invalidate();
    }
    private void clearLines() {
        // xoá nét liền (flight path)
        if (currentFlightPath != null) {
            mapView.getOverlays().remove(currentFlightPath);
            currentFlightPath = null;
        }

        // xoá nét đứt (future / arrival)
        if (currentFuturePath != null) {
            mapView.getOverlays().remove(currentFuturePath);
            currentFuturePath = null;
        }

        // ẩn bottom sheet nếu đang hiển thị
        if (bottomSheetBehavior != null && bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            // visibility será set to GONE in callback
        }
        mapView.invalidate();
    }

    private void clearDetailFields() {
        // set all structured detail TextViews to placeholders
        runOnUiThread(() -> {
            try {
                if (depAirportTv != null) depAirportTv.setText("Airport: -");
                if (depCodesTv != null) depCodesTv.setText("IATA / ICAO: - / -");
                if (depTerminalTv != null) depTerminalTv.setText("Terminal / Gate: - / -");
                if (depBaggageTv != null) depBaggageTv.setText("Baggage / Delay: - / -");
                if (depTimesTv != null) depTimesTv.setText("Scheduled / Estimated / Actual: - / - / -");
                if (depRunwaysTv != null) depRunwaysTv.setText("Estimated runway / Actual runway: - / -");
                if (depTimezoneTv != null) depTimezoneTv.setText("Timezone: -");

                if (arrAirportTv != null) arrAirportTv.setText("Airport: -");
                if (arrCodesTv != null) arrCodesTv.setText("IATA / ICAO: - / -");
                if (arrTerminalTv != null) arrTerminalTv.setText("Terminal / Gate: - / -");
                if (arrBaggageTv != null) arrBaggageTv.setText("Baggage / Delay: - / -");
                if (arrTimesTv != null) arrTimesTv.setText("Scheduled / Estimated / Actual: - / - / -");
                if (arrRunwaysTv != null) arrRunwaysTv.setText("Estimated runway / Actual runway: - / -");
                if (arrTimezoneTv != null) arrTimezoneTv.setText("Timezone: -");

                if (flightNumberTv != null) flightNumberTv.setText("Number / IATA / ICAO: - / - / -");
                if (flightCodesharedTv != null) flightCodesharedTv.setText("Codeshared: -");
                if (airlineNameTv != null) airlineNameTv.setText("Airline: - (IATA: - / ICAO: -)");

                if (aircraftRegTv != null) aircraftRegTv.setText("Registration: -");
                if (aircraftCodesTv != null) aircraftCodesTv.setText("ICAO / IATA: - / -");
                if (aircraftModelTv != null) aircraftModelTv.setText("Model: -");

                if (liveLatlonTv != null) liveLatlonTv.setText("Lat / Lon: - / -");
                if (liveAltTv != null) liveAltTv.setText("Altitude: -");
                if (liveSpeedTv != null) liveSpeedTv.setText("Speed H / V: - / -");
                if (liveHeadingTv != null) liveHeadingTv.setText("Heading: -");
                if (liveIsGroundTv != null) liveIsGroundTv.setText("IsGround: -");
                if (liveUpdatedTv != null) liveUpdatedTv.setText("Updated: -");
            } catch (Exception ignored) {}
        });
    }

    private void showNoAviationstackRecord() {
        // show a short 'no record' message on route/progress and clear details
        runOnUiThread(() -> {
            if (textRoute != null) textRoute.setText("(No Aviationstack record)");
            if (textProgress != null) textProgress.setText("Status: No info");
            clearDetailFields();
        });
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
        try {
            // --- top-level flight fields ---
            String flightDate = flight.optString("flight_date", "No info");
            String flightStatus = flight.optString("flight_status", "No info");

            // --- nested objects ---
            JSONObject dep = flight.optJSONObject("departure");
            JSONObject arr = flight.optJSONObject("arrival");
            JSONObject airline = flight.optJSONObject("airline");
            JSONObject flightObj = flight.optJSONObject("flight");
            JSONObject aircraft = flight.optJSONObject("aircraft");
            JSONObject live = flight.optJSONObject("live");

            // --- departure fields (use "No info" when missing) ---
            String depAirport   = dep != null ? dep.optString("airport", "No info") : "No info";
            String depIata      = dep != null ? dep.optString("iata", "No info") : "No info";
            String depIcao      = dep != null ? dep.optString("icao", "No info") : "No info";
            String depTerminal  = dep != null ? dep.optString("terminal", "No info") : "No info";
            String depGate      = dep != null ? dep.optString("gate", "No info") : "No info";
            String depBaggage   = dep != null ? dep.optString("baggage", "No info") : "No info";
            String depScheduled = dep != null ? dep.optString("scheduled", "No info") : "No info";
            String depEstimated = dep != null ? dep.optString("estimated", "No info") : "No info";
            String depActual    = dep != null ? dep.optString("actual", "No info") : "No info";
            String depDelay     = dep != null ? (dep.has("delay") ? String.valueOf(dep.opt("delay")) : "No info") : "No info";
            String depEstRunway = dep != null ? dep.optString("estimated_runway", "No info") : "No info";
            String depActRunway = dep != null ? dep.optString("actual_runway", "No info") : "No info";
            String depTimezone  = dep != null ? dep.optString("timezone", "No info") : "No info";

            // --- arrival fields ---
            String arrAirport   = arr != null ? arr.optString("airport", "No info") : "No info";
            String arrIata      = arr != null ? arr.optString("iata", "No info") : "No info";
            String arrIcao      = arr != null ? arr.optString("icao", "No info") : "No info";
            String arrTerminal  = arr != null ? arr.optString("terminal", "No info") : "No info";
            String arrGate      = arr != null ? arr.optString("gate", "No info") : "No info";
            String arrBaggage   = arr != null ? arr.optString("baggage", "No info") : "No info";
            String arrScheduled = arr != null ? arr.optString("scheduled", "No info") : "No info";
            String arrEstimated = arr != null ? arr.optString("estimated", "No info") : "No info";
            String arrActual    = arr != null ? arr.optString("actual", "No info") : "No info";
            String arrDelay     = arr != null ? (arr.has("delay") ? String.valueOf(arr.opt("delay")) : "No info") : "No info";
            String arrEstRunway = arr != null ? arr.optString("estimated_runway", "No info") : "No info";
            String arrActRunway = arr != null ? arr.optString("actual_runway", "No info") : "No info";
            String arrTimezone  = arr != null ? arr.optString("timezone", "No info") : "No info";

            // --- airline & flight objects ---
            String airlineName  = airline != null ? airline.optString("name", "No info") : "No info";
            String airlineIata  = airline != null ? airline.optString("iata", "No info") : "No info";
            String airlineIcao  = airline != null ? airline.optString("icao", "No info") : "No info";

            String flightNumber = flightObj != null ? flightObj.optString("number", "No info") : "No info";
            String flightIata   = flightObj != null ? flightObj.optString("iata", "No info") : "No info";
            String flightIcao   = flightObj != null ? flightObj.optString("icao", icao24) : icao24;
            String codeshared   = flightObj != null ? (flightObj.has("codeshared") && !flightObj.isNull("codeshared") ? flightObj.optString("codeshared","No info") : "No info") : "No info";

            // --- aircraft object ---
            String aircraftReg   = aircraft != null ? aircraft.optString("registration", "No info") : "No info";
            String aircraftIcao  = aircraft != null ? aircraft.optString("icao", "No info") : "No info";
            String aircraftIata  = aircraft != null ? aircraft.optString("iata", "No info") : "No info";
            String aircraftModel = aircraft != null ? aircraft.optString("model", "No info") : "No info"; // sometimes model key varies

            // --- live object (dynamic) ---
            String liveLatitude  = "No info";
            String liveLongitude = "No info";
            String liveAltitude  = "No info";
            String liveSpeedH    = "No info";
            String liveSpeedV    = "No info";
            String liveHeading   = "No info";
            String liveIsGround  = "No info";
            String liveUpdated   = "No info";
            if (live != null) {
                double lat = live.optDouble("latitude", Double.NaN);
                double lon = live.optDouble("longitude", Double.NaN);
                liveLatitude  = Double.isNaN(lat) ? "No info" : String.valueOf(lat);
                liveLongitude = Double.isNaN(lon) ? "No info" : String.valueOf(lon);

                double alt = live.optDouble("altitude", Double.NaN);
                liveAltitude = Double.isNaN(alt) ? "No info" : String.valueOf(alt);

                double spdH = live.optDouble("speed_horizontal", Double.NaN);
                liveSpeedH = Double.isNaN(spdH) ? "No info" : String.valueOf(spdH);

                double spdV = live.optDouble("speed_vertical", Double.NaN);
                liveSpeedV = Double.isNaN(spdV) ? "No info" : String.valueOf(spdV);

                double hdg = live.optDouble("heading", Double.NaN);
                liveHeading = Double.isNaN(hdg) ? "No info" : String.valueOf(hdg);

                if (live.has("is_ground")) {
                    liveIsGround = String.valueOf(live.optBoolean("is_ground", false));
                }
                liveUpdated = live.optString("updated", "No info");
            }

            // --- Compose UI strings ---
            String displayCallsign = (flightIata != null && !flightIata.equals("No info")) ? flightIata : flightIcao;
            String basic = "Flight: " + displayCallsign + "\n"
                    + "Airline: " + airlineName + "\n"
                    + "Date: " + flightDate + "\n"
                    + "Status: " + flightStatus;

            String fromText = depAirport + " (" + depIata + "/" + depIcao + ")";
            String toText = arrAirport + " (" + arrIata + "/" + arrIcao + ")";
            String route = fromText + " → " + toText;

            String progress = "Dep scheduled: " + depScheduled + " — Arr scheduled: " + arrScheduled;

            final String finalBasic = basic;
            final String finalRoute = route;
            final String finalProgress = progress;
            final String finalAltitude = liveAltitude;
            final String finalLongitude = liveLongitude;
            final String finalSpeed = liveSpeedH;
            final String finalRegistration = aircraftReg;
            final String finalAirlineName = airlineName;
            final String finalHeading = liveHeading;
            final String finalIsGround = liveIsGround;
            final String finalUpdated = liveUpdated;
            final String finalSpeedV = liveSpeedV;

            // --- update UI on main thread ---
            runOnUiThread(() -> {
                try {
                    if (textBasicInfo != null) textBasicInfo.setText(finalBasic);
                    if (textAirline != null) textAirline.setText(finalAirlineName);
                    if (textRoute != null) textRoute.setText(finalRoute);
                    if (textProgress != null) textProgress.setText(finalProgress);

                    if (textAlt != null) textAlt.setText("ALT\n" + (finalAltitude.equals("No info") ? "-" : finalAltitude));
                    if (textSpeed != null) textSpeed.setText("SPD\n" + (finalSpeed.equals("No info") ? "-" : finalSpeed));
                    if (textReg != null) textReg.setText("REG\n" + (finalRegistration.equals("No info") ? "-" : finalRegistration));

                    // Structured detail fields
                    if (depAirportTv != null) depAirportTv.setText("Airport: " + depAirport);
                    if (depCodesTv != null) depCodesTv.setText("IATA / ICAO: " + depIata + " / " + depIcao);
                    if (depTerminalTv != null) depTerminalTv.setText("Terminal / Gate: " + depTerminal + " / " + depGate);
                    if (depBaggageTv != null) depBaggageTv.setText("Baggage / Delay: " + depBaggage + " / " + depDelay);
                    if (depTimesTv != null) depTimesTv.setText("Scheduled / Estimated / Actual: " + depScheduled + " / " + depEstimated + " / " + depActual);
                    if (depRunwaysTv != null) depRunwaysTv.setText("Estimated runway / Actual runway: " + depEstRunway + " / " + depActRunway);
                    if (depTimezoneTv != null) depTimezoneTv.setText("Timezone: " + depTimezone);

                    if (arrAirportTv != null) arrAirportTv.setText("Airport: " + arrAirport);
                    if (arrCodesTv != null) arrCodesTv.setText("IATA / ICAO: " + arrIata + " / " + arrIcao);
                    if (arrTerminalTv != null) arrTerminalTv.setText("Terminal / Gate: " + arrTerminal + " / " + arrGate);
                    if (arrBaggageTv != null) arrBaggageTv.setText("Baggage / Delay: " + arrBaggage + " / " + arrDelay);
                    if (arrTimesTv != null) arrTimesTv.setText("Scheduled / Estimated / Actual: " + arrScheduled + " / " + arrEstimated + " / " + arrActual);
                    if (arrRunwaysTv != null) arrRunwaysTv.setText("Estimated runway / Actual runway: " + arrEstRunway + " / " + arrActRunway);
                    if (arrTimezoneTv != null) arrTimezoneTv.setText("Timezone: " + arrTimezone);

                    if (flightNumberTv != null) flightNumberTv.setText("Number / IATA / ICAO: " + flightNumber + " / " + flightIata + " / " + flightIcao);
                    if (flightCodesharedTv != null) flightCodesharedTv.setText("Codeshared: " + codeshared);
                    if (airlineNameTv != null) airlineNameTv.setText("Airline: " + airlineName + " (IATA: " + airlineIata + " / ICAO: " + airlineIcao + ")");

                    if (aircraftRegTv != null) aircraftRegTv.setText("Registration: " + aircraftReg);
                    if (aircraftCodesTv != null) aircraftCodesTv.setText("ICAO / IATA: " + aircraftIcao + " / " + aircraftIata);
                    if (aircraftModelTv != null) aircraftModelTv.setText("Model: " + aircraftModel);

                    if (liveLatlonTv != null) liveLatlonTv.setText("Lat / Lon: " + finalAltitude + " / " + finalLongitude);
                    if (liveAltTv != null) liveAltTv.setText("Altitude: " + (finalAltitude.equals("No info") ? "-" : finalAltitude));
                    if (liveSpeedTv != null) liveSpeedTv.setText("Speed H / V: " + finalSpeed + " / " + finalSpeedV);
                    if (liveHeadingTv != null) liveHeadingTv.setText("Heading: " + finalHeading);
                    if (liveIsGroundTv != null) liveIsGroundTv.setText("IsGround: " + finalIsGround);
                    if (liveUpdatedTv != null) liveUpdatedTv.setText("Updated: " + finalUpdated);

                    // show bottom sheet like before
                    if (bottomSheetBehavior != null) {
                        bottomSheet.setVisibility(View.VISIBLE);
                        bottomSheetBehavior.setPeekHeight(dpToPx(120));
                        detailScroll.setVisibility(View.GONE);
                        btnSeeMore.setText("See more");
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                } catch (Exception uiEx) {
                    Log.e("UI", "Error updating bottom sheet UI", uiEx);
                }
            });

        } catch (Exception e) {
            Log.e("UI", "Show info error", e);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTask);
        // optionally cancel all outstanding HTTP calls started by this client
        client.dispatcher().cancelAll();
    }

}
