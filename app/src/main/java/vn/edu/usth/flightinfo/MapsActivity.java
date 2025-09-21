package vn.edu.usth.flightinfo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

    // 🔑 Thông tin OAuth2 (thay bằng của bạn)
    private static final String CLIENT_ID = "doanhtu1209-api-client";
    private static final String CLIENT_SECRET = "7LhSIF85OAyPGvS6NRDEcRXUuQ4oK4Lj";

    // Lưu token và thời gian hết hạn
    private String accessToken = null;
    private long tokenExpiryTime = 0;

    // Lưu danh sách marker của máy bay theo icao24
    private Map<String, Marker> planeMarkers = new HashMap<>();

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
        mapView.getController().setZoom(10.0);
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
                    JSONArray states = json.getJSONArray("states");

                    runOnUiThread(() -> {
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
            if (icon != null) {
                icon.setTint(0xFF2196F3);
                marker.setIcon(icon);
            }
            marker.setRotation((float) heading);

            mapView.getOverlays().add(marker);
            planeMarkers.put(icao24, marker);
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
                        mapView.getController().setZoom(10.0);
                        mapView.getController().setCenter(userPoint);
                        Log.d("OpenSky", "User location: " + userPoint);
                    } else {
                        Log.w("OpenSky", "Không lấy được vị trí hiện tại");
                    }
                });
    }
}
