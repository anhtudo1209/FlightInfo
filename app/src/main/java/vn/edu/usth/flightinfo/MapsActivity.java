package vn.edu.usth.flightinfo; // your package name

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MapsActivity extends AppCompatActivity {

    private MapView map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMDroid requires a user agent (can be your app name)
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_maps);

        map = findViewById(R.id.map);
        map.setMultiTouchControls(true);

        // Set initial zoom and location (Hanoi for example)
        map.getController().setZoom(10.0);
        GeoPoint startPoint = new GeoPoint(21.0285, 105.8542);
        map.getController().setCenter(startPoint);

        // Add a marker
        Marker startMarker = new Marker(map);
        startMarker.setPosition(startPoint);
        startMarker.setTitle("Marker in Hanoi");
        map.getOverlays().add(startMarker);
    }
}
