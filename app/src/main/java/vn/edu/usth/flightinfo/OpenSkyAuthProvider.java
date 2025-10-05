package vn.edu.usth.flightinfo;

import android.util.Log;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class OpenSkyAuthProvider {
    public interface TokenCallback {
        void onTokenReady(String token);
        void onError(Exception e);
    }

    private final OkHttpClient client;
    private final String clientId;
    private final String clientSecret;

    private String accessToken = null;
    private long tokenExpiryTime = 0L;

    public OpenSkyAuthProvider(OkHttpClient client, String clientId, String clientSecret) {
        this.client = client;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean isTokenValid() {
        return accessToken != null && System.currentTimeMillis() < tokenExpiryTime;
    }

    public void getValidToken(TokenCallback callback) {
        if (isTokenValid()) {
            callback.onTokenReady(accessToken);
            return;
        }
        fetchAccessToken(callback);
    }

    private void fetchAccessToken(TokenCallback callback) {
        String url = "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";
        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();
        Request request = new Request.Builder().url(url).post(formBody).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("OpenSky", "Token request failed", e);
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Token error code=" + response.code()));
                    return;
                }
                try {
                    String body = response.body().string();
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    accessToken = json.getString("access_token");
                    int expiresIn = json.getInt("expires_in");
                    tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000L);
                    callback.onTokenReady(accessToken);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }
}


