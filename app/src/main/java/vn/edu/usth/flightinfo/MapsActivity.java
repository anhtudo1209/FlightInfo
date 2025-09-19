package vn.edu.usth.flightinfo;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;

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

    // 🔑 Thông tin OAuth2 (thay bằng của bạn)
    private static final String CLIENT_ID = "doanhtu1209-api-client";
    private static final String CLIENT_SECRET = "7LhSIF85OAyPGvS6NRDEcRXUuQ4oK4Lj";

    // Lưu token và thời gian hết hạn
    private String accessToken = null;
    private long tokenExpiryTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_maps);

        // Khởi tạo MapView
        mapView = findViewById(R.id.map);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(7.0);
        mapView.getController().setCenter(new GeoPoint(21.0285, 105.8542)); // Hà Nội

        // Xin token đầu tiên
        fetchAccessToken();
    }

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
                    JSONObject json = new JSONObject(response.body().string());
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
        String url = "https://opensky-network.org/api/states/all?" +
                "lamin=20.5&lomin=105.0&lamax=22.0&lomax=106.5";

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
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray states = json.getJSONArray("states");

                    runOnUiThread(() -> {
                        mapView.getOverlays().clear();
                        for (int i = 0; i < states.length(); i++) {
                            JSONArray plane = states.optJSONArray(i);
                            if (plane == null) continue;

                            double lat = plane.optDouble(6, 0.0);
                            double lon = plane.optDouble(5, 0.0);
                            String callsign = plane.optString(1, "Unknown");

                            if (lat == 0.0 && lon == 0.0) continue;

                            addPlaneMarker(lat, lon, callsign);
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
    // 🔹 Vẽ máy bay lên bản đồ
    private void addPlaneMarker(double lat, double lon, String title) {
        GeoPoint point = new GeoPoint(lat, lon);
        Marker marker = new Marker(mapView);
        marker.setPosition(point);
        marker.setTitle(title);

        Drawable icon = getResources().getDrawable(R.drawable.ic_plane);
        marker.setIcon(icon);

        mapView.getOverlays().add(marker);
    }
}
