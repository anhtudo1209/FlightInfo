package vn.edu.usth.flightinfo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.core.content.res.ResourcesCompat;

/**
 * Manages plane markers and polylines on the MapView.
 */
public class PlaneOverlayManager {
    public interface PlaneMarkerClickHandler {
        void onPlaneMarkerClick(String icao24, String callsign, GeoPoint position);
    }

    private final Context context;
    private final MapView mapView;
    private final PlaneMarkerClickHandler clickHandler;

    private final Map<String, Marker> planeMarkers = new HashMap<>();
    private Polyline currentFlightPath = null;
    private Polyline currentFuturePath = null;

    public PlaneOverlayManager(Context context, MapView mapView, PlaneMarkerClickHandler clickHandler) {
        this.context = context;
        this.mapView = mapView;
        this.clickHandler = clickHandler;
    }

    public Marker getMarker(String icao24) {
        return planeMarkers.get(icao24);
    }

    public void updatePlaneMarker(String icao24, String title, double lat, double lon, Double heading,
                                  double geoAlt, double speed) {
        GeoPoint point = new GeoPoint(lat, lon);
        Marker marker;
        if (planeMarkers.containsKey(icao24)) {
            marker = planeMarkers.get(icao24);
            if (marker == null) return;
            marker.setPosition(point);
            // Update related data on the marker for later retrieval
            try {
                Object relObj = marker.getRelatedObject();
                org.json.JSONObject rel = (relObj instanceof org.json.JSONObject)
                        ? (org.json.JSONObject) relObj : new org.json.JSONObject();
                if (!Double.isNaN(geoAlt)) rel.put("geo_alt", geoAlt);
                if (!Double.isNaN(speed)) rel.put("speed", speed);
                rel.put("ts", System.currentTimeMillis());
                marker.setRelatedObject(rel);
            } catch (Exception e) {
                Log.w("OpenSky", "Failed to update marker relatedObject", e);
            }

            if (heading != null) {
                float applied = applyHeadingOffset(heading);
                marker.setRotation(applied);
            }
        } else {
            marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle(title);
            Drawable icon = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_plane,
                    context.getTheme());
            if (icon != null) {
                icon.setTint(0xFF2196F3);
                marker.setIcon(icon);
            }
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);

            if (heading != null) {
                float applied = applyHeadingOffset(heading);
                marker.setRotation(applied);
            }

            // Store initial related data
            try {
                org.json.JSONObject rel = new org.json.JSONObject();
                if (!Double.isNaN(geoAlt)) rel.put("geo_alt", geoAlt);
                if (!Double.isNaN(speed)) rel.put("speed", speed);
                rel.put("ts", System.currentTimeMillis());
                marker.setRelatedObject(rel);
            } catch (Exception e) {
                Log.w("OpenSky", "Failed to set marker relatedObject", e);
            }

            // Delegate click back to activity/controller
            marker.setOnMarkerClickListener((m, mv) -> {
                String callsignTrim = title != null ? title.trim() : "";
                if (clickHandler != null) {
                    clickHandler.onPlaneMarkerClick(icao24, callsignTrim, m.getPosition());
                }
                return true;
            });

            mapView.getOverlays().add(marker);
            planeMarkers.put(icao24, marker);
        }
    }

    private float applyHeadingOffset(Double heading) {
        return (float) ((heading % 360.0 + 360.0) % 360.0);
    }

    public void drawFlightPath(List<GeoPoint> points) {
        if (currentFlightPath != null) {
            mapView.getOverlays().remove(currentFlightPath);
        }
        currentFlightPath = new Polyline();
        currentFlightPath.setWidth(5f);
        currentFlightPath.setColor(Color.RED);
        currentFlightPath.setPoints(new ArrayList<>(points));
        mapView.getOverlays().add(currentFlightPath);
        mapView.invalidate();
    }

    public void drawDashedLine(GeoPoint start, GeoPoint end) {
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
        dashedLine.getOutlinePaint().setPathEffect(
                new android.graphics.DashPathEffect(new float[]{20, 20}, 0)
        );

        currentFuturePath = dashedLine;
        mapView.getOverlays().add(dashedLine);
        mapView.invalidate();
    }

    public void clearLines() {
        if (currentFlightPath != null) {
            mapView.getOverlays().remove(currentFlightPath);
            currentFlightPath = null;
        }
        if (currentFuturePath != null) {
            mapView.getOverlays().remove(currentFuturePath);
            currentFuturePath = null;
        }
        mapView.invalidate();
    }

    public void removeMarkersNotIn(java.util.Set<String> seenIcao24) {
        java.util.Iterator<Map.Entry<String, Marker>> it = planeMarkers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Marker> e = it.next();
            if (!seenIcao24.contains(e.getKey())) {
                try {
                    mapView.getOverlays().remove(e.getValue());
                } catch (Exception ignored) {}
                it.remove();
            }
        }
        mapView.invalidate();
    }
}


