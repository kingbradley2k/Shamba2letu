package com.example.shambaletu;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MapMeasurementActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final double EARTH_RADIUS = 6371000.0; // meters

    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;

    // UI Elements
    private TextView tvLatitude;
    private TextView tvLongitude;
    private TextView tvDistance;
    private TextView tvArea;
    private TextView tvGpsStatus;
    private ProgressBar progressGps;
    private Spinner spinnerUnits;
    private Button btnAddPoint;
    private Button btnUndoPoint;
    private Button btnClearMap;
    private Button btnSavePlot;

    // Measurement data
    private List<LatLng> points = new ArrayList<>();
    private Polyline currentPolyline;
    private Polygon currentPolygon;

    // Unit conversion
    private enum MeasurementUnit {
        METERS("Meters", 1.0, false),
        FEET("Feet", 0.3048, false),
        YARDS("Yards", 0.9144, false),
        ACRES("Acres", 4046.86, true),
        HECTARES("Hectares", 10000.0, true);

        private final String displayName;
        private final double toMeters;
        private final boolean isAreaUnit;

        MeasurementUnit(String displayName, double toMeters, boolean isAreaUnit) {
            this.displayName = displayName;
            this.toMeters = toMeters;
            this.isAreaUnit = isAreaUnit;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getToMeters() {
            return toMeters;
        }

        public boolean isAreaUnit() {
            return isAreaUnit;
        }
    }

    private MeasurementUnit selectedUnit = MeasurementUnit.METERS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_measurement);

        initializeViews();
        setupUnitSpinner();
        setupButtons();
        setupMap();
        setupLocationServices();

        // Initialize display
        updateMeasurements();
    }

    private void initializeViews() {
        tvLatitude = findViewById(R.id.tvLatitude);
        tvLongitude = findViewById(R.id.tvLongitude);
        tvDistance = findViewById(R.id.tvDistance);
        tvArea = findViewById(R.id.tvArea);
        tvGpsStatus = findViewById(R.id.tvGpsStatus);
        progressGps = findViewById(R.id.progressGps);
        spinnerUnits = findViewById(R.id.spinnerUnits);
        btnAddPoint = findViewById(R.id.btnAddPoint);
        btnUndoPoint = findViewById(R.id.btnUndoPoint);
        btnClearMap = findViewById(R.id.btnClearMap);
        btnSavePlot = findViewById(R.id.btnSavePlot);
    }

    private void setupUnitSpinner() {
        List<String> units = new ArrayList<>();
        units.add("Meters");
        units.add("Feet");
        units.add("Yards");
        units.add("Acres");
        units.add("Hectares");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                units
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUnits.setAdapter(adapter);

        spinnerUnits.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedUnit = MeasurementUnit.values()[position];
                updateMeasurements();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupButtons() {
        btnAddPoint.setOnClickListener(v -> addCurrentLocationPoint());
        btnUndoPoint.setOnClickListener(v -> undoLastPoint());
        btnClearMap.setOnClickListener(v -> clearAllPoints());
        btnSavePlot.setOnClickListener(v -> savePlotData());
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        //we shall later on implement a code to toggle between satellite and normal map

        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMyLocationButtonEnabled(true);

        if (checkLocationPermission()) {
            enableMyLocation();
        } else {
            requestLocationPermission();
        }
    }

    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    currentLocation = location;
                    updateLocationUI(location);

                    // Move camera to current location on first fix
                    if (points.isEmpty()) {
                        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f));
                    }
                }
            }
        };
    }

    private void startLocationUpdates() {
        if (!checkLocationPermission()) return;

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000 // 2 seconds
        ).build();

        try {
            progressGps.setVisibility(View.VISIBLE);
            tvGpsStatus.setText("GPS: Acquiring...");

            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    getMainLooper()
            );
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void updateLocationUI(Location location) {
        progressGps.setVisibility(View.GONE);
        tvGpsStatus.setText(String.format(Locale.US, "GPS: Active (±%dm)", (int) location.getAccuracy()));
        tvLatitude.setText(String.format(Locale.US, "Latitude: %.6f", location.getLatitude()));
        tvLongitude.setText(String.format(Locale.US, "Longitude: %.6f", location.getLongitude()));
    }

    private void addCurrentLocationPoint() {
        if (currentLocation != null) {
            LatLng point = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            points.add(point);

            // Add marker
            map.addMarker(new MarkerOptions()
                    .position(point)
                    .title("Point " + points.size()));

            updateMapOverlays();
            updateMeasurements();

            Toast.makeText(this, "Point " + points.size() + " added", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Waiting for GPS location...", Toast.LENGTH_SHORT).show();
        }
    }

    private void undoLastPoint() {
        if (!points.isEmpty()) {
            points.remove(points.size() - 1);
            map.clear();

            // Re-add all markers
            for (int i = 0; i < points.size(); i++) {
                map.addMarker(new MarkerOptions()
                        .position(points.get(i))
                        .title("Point " + (i + 1)));
            }

            updateMapOverlays();
            updateMeasurements();

            Toast.makeText(this, "Last point removed", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearAllPoints() {
        points.clear();
        map.clear();
        currentPolyline = null;
        currentPolygon = null;
        updateMeasurements();
        Toast.makeText(this, "Map cleared", Toast.LENGTH_SHORT).show();
    }

    private void updateMapOverlays() {
        // Remove existing overlays
        if (currentPolyline != null) {
            currentPolyline.remove();
        }
        if (currentPolygon != null) {
            currentPolygon.remove();
        }

        if (points.size() >= 2) {
            // Draw polyline connecting points
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(points)
                    .color(Color.BLUE)
                    .width(5f);
            currentPolyline = map.addPolyline(polylineOptions);

            // If we have 3+ points, draw filled polygon
            if (points.size() >= 3) {
                PolygonOptions polygonOptions = new PolygonOptions()
                        .addAll(points)
                        .strokeColor(Color.RED)
                        .fillColor(Color.argb(50, 255, 0, 0))
                        .strokeWidth(3f);
                currentPolygon = map.addPolygon(polygonOptions);
            }
        }
    }

    private void updateMeasurements() {
        if (points == null || points.isEmpty()) {
            tvDistance.setText("Distance: 0.00 m");
            tvArea.setText("Area: 0.00 m²");
            return;
        }

        // Calculate total distance (perimeter)
        double totalDistance = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            totalDistance += calculateDistance(points.get(i), points.get(i + 1));
        }

        // If polygon is closed, add distance back to start
        if (points.size() >= 3) {
            totalDistance += calculateDistance(points.get(points.size() - 1), points.get(0));
        }

        // Calculate area
        double area = points.size() >= 3 ? calculateArea(points) : 0.0;

        // Convert to selected unit
        double distanceInUnit;
        double areaInUnit;
        String distanceUnitStr;
        String areaUnitStr;

        if (selectedUnit == MeasurementUnit.ACRES) {
            // Distance in feet for acres
            distanceInUnit = totalDistance / 0.3048;
            distanceUnitStr = "ft";
            areaInUnit = area / selectedUnit.getToMeters();
            areaUnitStr = "acres";
        } else if (selectedUnit == MeasurementUnit.HECTARES) {
            // Distance in meters for hectares
            distanceInUnit = totalDistance;
            distanceUnitStr = "m";
            areaInUnit = area / selectedUnit.getToMeters();
            areaUnitStr = "ha";
        } else {
            // Normal linear units
            distanceInUnit = totalDistance / selectedUnit.getToMeters();
            distanceUnitStr = selectedUnit.getDisplayName().toLowerCase();
            areaInUnit = area / (selectedUnit.getToMeters() * selectedUnit.getToMeters());
            areaUnitStr = distanceUnitStr + "²";
        }

        tvDistance.setText(String.format(Locale.US, "Distance: %.2f %s", distanceInUnit, distanceUnitStr));
        tvArea.setText(String.format(Locale.US, "Area: %.2f %s", areaInUnit, areaUnitStr));
    }

    private double calculateDistance(LatLng start, LatLng end) {
        // Haversine formula
        double lat1 = Math.toRadians(start.latitude);
        double lat2 = Math.toRadians(end.latitude);
        double dLat = Math.toRadians(end.latitude - start.latitude);
        double dLon = Math.toRadians(end.longitude - start.longitude);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c; // Distance in meters
    }

    private double calculateArea(List<LatLng> points) {
        // Spherical excess formula for accurate area calculation on Earth's surface
        if (points.size() < 3) return 0.0;

        double area = 0.0;
        int n = points.size();

        for (int i = 0; i < n; i++) {
            LatLng p1 = points.get(i);
            LatLng p2 = points.get((i + 1) % n);
            area += Math.toRadians(p2.longitude - p1.longitude) *
                    (2 + Math.sin(Math.toRadians(p1.latitude)) +
                            Math.sin(Math.toRadians(p2.latitude)));
        }

        area = Math.abs(area * EARTH_RADIUS * EARTH_RADIUS / 2.0);
        return area; // Area in square meters
    }

    private void savePlotData() {
        if (points == null || points.isEmpty()) {
            Toast.makeText(this, "No points to save", Toast.LENGTH_SHORT).show();
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String plotName = "Plot_" + timestamp;

        List<PointData> pointDataList = new ArrayList<>();
        for (LatLng point : points) {
            pointDataList.add(new PointData(point.latitude, point.longitude));
        }

        PlotData plot = new PlotData(
                String.valueOf(System.currentTimeMillis()),
                plotName,
                pointDataList,
                calculateArea(points),
                calculatePerimeter(),
                selectedUnit.getDisplayName(),
                System.currentTimeMillis()
        );

        // Save to internal storage
        Gson gson = new Gson();
        String json = gson.toJson(plot);

        try {
            File file = new File(getFilesDir(), plotName + ".json");
            FileWriter writer = new FileWriter(file);
            writer.write(json);
            writer.close();

            Toast.makeText(this, "Plot saved: " + plotName, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving plot: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private double calculatePerimeter() {
        double perimeter = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            perimeter += calculateDistance(points.get(i), points.get(i + 1));
        }
        if (points.size() >= 3) {
            perimeter += calculateDistance(points.get(points.size() - 1), points.get(0));
        }
        return perimeter;
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    private void enableMyLocation() {
        if (!checkLocationPermission()) return;

        try {
            map.setMyLocationEnabled(true);
            startLocationUpdates();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Location permission is required for this app",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkLocationPermission()) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    // Data models for saving plot information
    public static class PlotData {
        private String id;
        private String name;
        private List<PointData> points;
        private double area;
        private double perimeter;
        private String unit;
        private long timestamp;

        public PlotData(String id, String name, List<PointData> points, double area,
                        double perimeter, String unit, long timestamp) {
            this.id = id;
            this.name = name;
            this.points = points;
            this.area = area;
            this.perimeter = perimeter;
            this.unit = unit;
            this.timestamp = timestamp;
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public List<PointData> getPoints() { return points; }
        public double getArea() { return area; }
        public double getPerimeter() { return perimeter; }
        public String getUnit() { return unit; }
        public long getTimestamp() { return timestamp; }
    }

    public static class PointData {
        private double latitude;
        private double longitude;

        public PointData(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        // Getters
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }
}