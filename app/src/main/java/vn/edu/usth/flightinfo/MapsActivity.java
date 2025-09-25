package vn.edu.usth.flightinfo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;

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
    private MapView mapView;
    private OkHttpClient client = new OkHttpClient();

    private FusedLocationProviderClient fusedLocationClient;

    private String selectedPLane = null;
    private Polyline currentFlightPath = null;
    private Polyline currentFuturePath = null;

    // 🔑 Thông tin OAuth2 (thay bằng của bạn)
    private static final String AVIATIONSTACK_KEY = "fac89fbb28ecbc6405d77408ccd2ff8a";
    private static final String CLIENT_ID = "doanhtu1209-api-client";
    private static final String CLIENT_SECRET = "7LhSIF85OAyPGvS6NRDEcRXUuQ4oK4Lj";

    // Lưu token và thời gian hết hạn
    private String accessToken = null;
    private long tokenExpiryTime = 0;

    // Lưu danh sách marker của máy bay theo icao24
    private Map<String, Marker> planeMarkers = new HashMap<>();
    private Map<String, JSONObject> flightInfoCache = new HashMap<>();
    private Map<String, Long> flightInfoCacheTime = new HashMap<>();
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
                // User tapped the map (not a marker)
                if (currentFlightPath != null) {
                    mapView.getOverlays().remove(currentFlightPath);
                    currentFlightPath = null;
                    mapView.invalidate();
                }
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
        Long t = flightInfoCacheTime.get(icao24);
        if (t != null && System.currentTimeMillis() - t < FLIGHT_INFO_CACHE_TTL) {
            JSONObject cached = flightInfoCache.get(icao24);
            if (cached != null) {
                // vẽ nét đứt nếu có tọa độ arrival
                JSONObject arrival = cached.optJSONObject("arrival");
                if (arrival != null) {
                    double lat = arrival.optDouble("latitude", 0.0);
                    double lon = arrival.optDouble("longitude", 0.0);
                    if (lat != 0.0 && lon != 0.0) {
                        drawDashedLine(currentPos, new GeoPoint(lat, lon));
                    }
                }
                // show basic info từ cache
                showBasicInfo(icao24, cached);
                return;
            }
        }
        fetchFlightInfo(callsign, icao24, currentPos);
    }
    private void fetchFlightInfo(String callsign, String icao24, GeoPoint currentPos) {
        if (callsign == null || callsign.trim().isEmpty()) {
            Log.w("Aviationstack", "No callsign, skipping aviationstack fetch for " + icao24);
            return;
        }

        String cleaned = callsign.replaceAll("\\s+", ""); // remove spaces
        String encoded;
        try {
            encoded = java.net.URLEncoder.encode(cleaned, "UTF-8");
        } catch (Exception e) {
            encoded = cleaned;
        }

        String url = "https://api.aviationstack.com/v1/flights"
                + "?access_key=" + AVIATIONSTACK_KEY
                + "&flight_icao=" + encoded;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("Aviationstack", "API failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("Aviationstack", "API error: " + response.code());
                    return;
                }

                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray data = json.optJSONArray("data");
                    if (data == null || data.length() == 0) {
                        Log.w("Aviationstack", "No flights data for callsign=" + callsign);
                        return;
                    }

                    // Tìm record match nhất bằng aircraft.icao24 (nếu có); fallback = data[0]
                    JSONObject match = null;
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject f = data.getJSONObject(i);
                        JSONObject aircraft = f.optJSONObject("aircraft");
                        String aIcao24 = aircraft != null ? aircraft.optString("icao24", "") : "";
                        if (!aIcao24.isEmpty() && aIcao24.equalsIgnoreCase(icao24)) {
                            match = f;
                            break;
                        }
                    }
                    if (match == null) {
                        match = data.getJSONObject(0);
                    }

                    // Cache bằng icao24 (unique từ OpenSky)
                    flightInfoCache.put(icao24, match);
                    flightInfoCacheTime.put(icao24, System.currentTimeMillis());

                    // Chuẩn bị biến final cho lambda
                    final JSONObject finalMatch = match;
                    final String finalIcao24 = icao24;
                    final GeoPoint finalCurrentPos = currentPos;

                    // Lấy arrival coords, vẽ nét đứt
                    JSONObject arrival = finalMatch.optJSONObject("arrival");
                    if (arrival != null) {
                        double lat = arrival.optDouble("latitude", 0.0);
                        double lon = arrival.optDouble("longitude", 0.0);
                        if (lat != 0.0 && lon != 0.0) {
                            GeoPoint arrivalPoint = new GeoPoint(lat, lon);
                            runOnUiThread(() -> drawDashedLine(finalCurrentPos, arrivalPoint));
                        }
                    }

                    // Hiện thông tin cơ bản
                    runOnUiThread(() -> showBasicInfo(finalIcao24, finalMatch));

                } catch (Exception e) {
                    Log.e("Aviationstack", "Parse error", e);
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
            JSONObject dep = flight.optJSONObject("departure");
            JSONObject arr = flight.optJSONObject("arrival");
            JSONObject airline = flight.optJSONObject("airline");
            JSONObject flightObj = flight.optJSONObject("flight");

            String callsign = flightObj != null ? flightObj.optString("iata", flightObj.optString("icao", icao24)) : icao24;
            String info = "Chuyến: " + callsign + "\n"
                    + "Hãng: " + (airline != null ? airline.optString("name", "") : "") + "\n"
                    + "Từ: " + (dep != null ? dep.optString("airport", "") : "") + "\n"
                    + "Đến: " + (arr != null ? arr.optString("airport", "") : "") + "\n"
                    + "(Bấm 'Xem thêm' để chi tiết)";

            Marker marker = planeMarkers.get(icao24);
            if (marker != null) {
                marker.setSnippet(info);
                marker.showInfoWindow();

                // Thêm một hành động "Xem thêm" khi click snippet (tuỳ cách bạn implement InfoWindow)
                // Dễ nhất: khi người bấm "Xem thêm" bạn gọi:
                // showFlightDetailsBottomSheet(icao24);
            }

        } catch (Exception e) {
            Log.e("UI", "Show info error", e);
        }
    }

}
