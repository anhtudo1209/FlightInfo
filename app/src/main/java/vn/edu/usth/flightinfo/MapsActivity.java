package vn.edu.usth.flightinfo; // your package name

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class MapsActivity extends AppCompatActivity {

    private MapView map;
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // OSMDroid requires a user agent (can be your app name)
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_maps);

        map = findViewById(R.id.map);
        map.setMultiTouchControls(true);
        // Set initial zoom and location (Hanoi for example)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE);
        }
        MyLocationNewOverlay myLocationNewOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this),map);
        myLocationNewOverlay.enableMyLocation();
        map.getOverlays().add(myLocationNewOverlay);

        myLocationNewOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint myLocation = myLocationNewOverlay.getMyLocation();
            if (myLocation != null) {
                map.getController().setZoom(10.0);
                map.getController().animateTo(myLocation);
            }
        }));
    }
}
