package vn.edu.usth.flightinfo;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OpenSkyService {
    public interface StatesCallback {
        void onSuccess(JSONArray states);
        void onError(Exception e);
    }
    public interface TrackCallback {
        void onSuccess(List<GeoPoint> points);
        void onError(Exception e);
    }

    private final OkHttpClient client;
    private final OpenSkyAuthProvider authProvider;

    public OpenSkyService(OkHttpClient client, OpenSkyAuthProvider authProvider) {
        this.client = client;
        this.authProvider = authProvider;
    }

    public void fetchPlanesWithValidToken(BoundingBox box, StatesCallback callback) {
        authProvider.getValidToken(new OpenSkyAuthProvider.TokenCallback() {
            @Override
            public void onTokenReady(String token) {
                fetchPlanes(box, token, callback);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    private void fetchPlanes(BoundingBox box, String token, StatesCallback callback) {
        double lamin = box.getLatSouth();
        double lamax = box.getLatNorth();
        double lomin = box.getLonWest();
        double lomax = box.getLonEast();
        String url = "https://opensky-network.org/api/states/all?" +
                "lamin=" + lamin + "&lomin=" + lomin + "&lamax=" + lamax + "&lomax=" + lomax;
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + token).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("API error code=" + response.code()));
                    return;
                }
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray states = json.optJSONArray("states");
                    callback.onSuccess(states != null ? states : new JSONArray());
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    public void fetchFlightTrackWithValidToken(String icao24, TrackCallback callback) {
        authProvider.getValidToken(new OpenSkyAuthProvider.TokenCallback() {
            @Override
            public void onTokenReady(String token) {
                fetchTrack(icao24, token, callback);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    private void fetchTrack(String icao24, String token, TrackCallback callback) {
        String url = "https://opensky-network.org/api/tracks/all" + "?icao24=" + icao24 + "&time=0";
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + token).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Track error code=" + response.code()));
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
                    callback.onSuccess(points);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }
}


