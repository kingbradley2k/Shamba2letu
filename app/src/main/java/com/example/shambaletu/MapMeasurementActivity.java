package com.example.shambaletu;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.widget.EditText;
import androidx.annotation.Nullable;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import java.io.OutputStream;
import java.util.List;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.maps.android.SphericalUtil;
import java.util.*;

public class MapMeasurementActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final float MIN_ACCURACY_METERS = 5f;
    private static final int STABLE_READINGS_REQUIRED = 3;
    private static final float MAX_SPEED_MPS = 0.2f;
    private static final int REQ_SAVE_IMAGE = 1002;
    private static final int MIN_SATELLITES = 6;
    private static final int READINGS_TO_COLLECT = 3;

    private String pendingFileName = "Plot_Map.jpg";
    private Bitmap mapSnapshotBitmap;
    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private int stepCount = 0;
    private SensorManager sensorManager;
    private Sensor magnetometer;
    private Sensor accelerometer;
    private Sensor stepDetector;
    private final float[] lastAccelerometer = new float[3];
    private final float[] lastMagnetometer = new float[3];
    private boolean haveMagnetometer = false;
    private boolean haveAccelerometer = false;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private float currentBearing = 0f;
    private LocationManager locationManager;
    private int satelliteCount = 0;
    private KalmanFilter latitudeFilter = new KalmanFilter(0.001f, 3f);
    private KalmanFilter longitudeFilter = new KalmanFilter(0.001f, 3f);
    private boolean isGpsLocked = false;
    private boolean isCameraInitialized = false;
    private int stableReadingsCount = 0;
    private Marker measureMarker;
    private LatLng activeMeasurePosition;
    private boolean isManualLock = false;
    private Circle accuracyCircle;

    private enum PlacementMode {
        GPS("GPS", Color.GREEN),
        WALKING("Walking", Color.MAGENTA),
        MANUAL("Manual Tap", Color.CYAN);
        String label;
        int color;
        PlacementMode(String l, int c) {
            label = l;
            color = c;
        }
    }

    private PlacementMode currentMode = PlacementMode.GPS;
    private List<LatLng> temporaryReadings = new ArrayList<>();
    private boolean isCollectingReadings = false;
    private int currentMapTypeIndex = 0;
    private final int[] MAP_TYPES = {
            GoogleMap.MAP_TYPE_HYBRID,
            GoogleMap.MAP_TYPE_SATELLITE,
            GoogleMap.MAP_TYPE_NORMAL,
            GoogleMap.MAP_TYPE_TERRAIN
    };
    private final String[] MAP_TYPE_NAMES = {"Hybrid", "Satellite", "Normal", "Terrain"};
    private TextView tvLatitude, tvLongitude, tvDistance, tvArea, tvGpsStatus;
    private TextView tvBearing, tvSatellites, tvAccuracy;
    private ProgressBar progressGps;
    private Spinner spinnerUnits;
    private Button btnAddPoint, btnUndoPoint, btnClearMap, btnSavePlot, btnLockMarker, btnToggleMode, btnToggleMapType;
    private final List<LatLng> points = new ArrayList<>();
    private Polyline currentPolyline;
    private Polygon currentPolygon;

    private enum MeasurementUnit {
        METERS("Meters", 1.0),
        FEET("Feet", 0.3048),
        YARDS("Yards", 0.9144),
        ACRES("Acres", 4046.86),
        HECTARES("Hectares", 10000.0);
        String label;
        double toMeters;
        MeasurementUnit(String l, double m) {
            label = l;
            toMeters = m;
        }
    }

    private MeasurementUnit selectedUnit = MeasurementUnit.METERS;

    private static class KalmanFilter {
        private final double processNoise;
        private final double measurementNoise;
        private double estimatedError = 1.0;
        private double currentEstimate = 0.0;
        private boolean initialized = false;

        public KalmanFilter(double processNoise, double measurementNoise) {
            this.processNoise = processNoise;
            this.measurementNoise = measurementNoise;
        }

        public double filter(double measurement) {
            if (!initialized) {
                currentEstimate = measurement;
                initialized = true;
                return currentEstimate;
            }
            double kalmanGain = estimatedError / (estimatedError + measurementNoise);
            currentEstimate = currentEstimate + kalmanGain * (measurement - currentEstimate);
            estimatedError = (1 - kalmanGain) * estimatedError + Math.abs(currentEstimate) * processNoise;
            return currentEstimate;
        }

        public void reset() {
            initialized = false;
            estimatedError = 10.0;
        }
    }

    private Marker selectedMarker = null;
    private int selectedPointIndex = -1;

    private SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);
                haveAccelerometer = true;
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
                haveMagnetometer = true;
            }

            if (haveAccelerometer && haveMagnetometer) {
                if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)) {
                    SensorManager.getOrientation(rotationMatrix, orientation);
                    currentBearing = (float) Math.toDegrees(orientation[0]);
                    if (currentBearing < 0) currentBearing += 360;
                    updateBearingUI();
                }
            }

            if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                if (currentMode == PlacementMode.WALKING && isGpsLocked) {
                    stepCount++;
                    updateWalkingButtonText();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_measurement);
        checkGpsStatus();
        initViews();
        setupSpinner();
        setupMap();
        setupSensors();
        setupLocation();
        setupButtons();
        setupGnssStatus();
    }

    private void checkGpsStatus() {
        final LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager != null && !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                    .setMessage("Your GPS seems to be disabled. Would you like to enable it?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", (dialog, which) ->
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("No", (dialog, which) -> {
                        dialog.cancel();
                        Toast.makeText(this, "GPS is required for this feature.", Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .create()
                    .show();
        }
    }

    private void initViews() {
        tvLatitude = findViewById(R.id.tvLatitude);
        tvLongitude = findViewById(R.id.tvLongitude);
        tvDistance = findViewById(R.id.tvDistance);
        tvArea = findViewById(R.id.tvArea);
        tvGpsStatus = findViewById(R.id.tvGpsStatus);
        tvBearing = findViewById(R.id.tvBearing);
        tvSatellites = findViewById(R.id.tvSatellites);
        tvAccuracy = findViewById(R.id.tvAccuracy);
        progressGps = findViewById(R.id.progressGps);
        spinnerUnits = findViewById(R.id.spinnerUnits);
        btnAddPoint = findViewById(R.id.btnAddPoint);
        btnUndoPoint = findViewById(R.id.btnUndoPoint);
        btnClearMap = findViewById(R.id.btnClearMap);
        btnSavePlot = findViewById(R.id.btnSavePlot);
        btnLockMarker = findViewById(R.id.btnLockMarker);
        btnToggleMode = findViewById(R.id.btnToggleMode);
        btnToggleMapType = findViewById(R.id.btnToggleMapType);
        btnAddPoint.setEnabled(false);
        if (tvBearing != null) tvBearing.setText("Bearing: --°");
        if (tvSatellites != null) tvSatellites.setText("Satellites: --");
        if (tvAccuracy != null) tvAccuracy.setText("Accuracy: --m");
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (magnetometer != null) {
            sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (accelerometer != null) {
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (stepDetector != null) {
            sensorManager.registerListener(sensorListener, stepDetector, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void setupGnssStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (checkPermission()) {
                try {
                    locationManager.registerGnssStatusCallback(new GnssStatus.Callback() {
                        @Override
                        public void onSatelliteStatusChanged(GnssStatus status) {
                            satelliteCount = status.getSatelliteCount();
                            int usedInFix = 0;
                            for (int i = 0; i < satelliteCount; i++) {
                                if (status.usedInFix(i)) usedInFix++;
                            }
                            updateSatelliteUI(usedInFix, satelliteCount);
                        }

                        @Override
                        public void onStarted() {
                            tvGpsStatus.setText("GPS Starting...");
                        }

                        @Override
                        public void onStopped() {
                            tvGpsStatus.setText("GPS Stopped");
                        }
                    }, null);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void updateBearingUI() {
        if (tvBearing != null) {
            String direction = getDirection(currentBearing);
            tvBearing.setText(String.format("Bearing: %.1f° %s", currentBearing, direction));
        }
    }

    private void updateSatelliteUI(int used, int total) {
        if (tvSatellites != null) {
            tvSatellites.setText(String.format("Satellites: %d/%d", used, total));
            if (used >= 8) {
                tvSatellites.setTextColor(Color.GREEN);
            } else if (used >= 4) {
                tvSatellites.setTextColor(Color.YELLOW);
            } else {
                tvSatellites.setTextColor(Color.RED);
            }
        }
    }

    private String getDirection(float bearing) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"};
        return directions[(int)Math.round(bearing / 45.0) % 8];
    }

    private void setupButtons() {
        btnAddPoint.setOnClickListener(v -> addPoint());
        btnUndoPoint.setOnClickListener(v -> undoLast());
        btnClearMap.setOnClickListener(v -> clearAll());
        btnSavePlot.setOnClickListener(v -> savePlot());
        btnLockMarker.setOnClickListener(v -> toggleLock());
        if (btnToggleMode != null) {
            updateModeUI();
            btnToggleMode.setOnClickListener(v -> togglePlacementMode());
        }
        if (btnToggleMapType != null) {
            btnToggleMapType.setText("Map: " + MAP_TYPE_NAMES[currentMapTypeIndex]);
            btnToggleMapType.setOnClickListener(v -> toggleMapType());
        }
    }

    private void toggleMapType() {
        if (map == null) return;
        currentMapTypeIndex = (currentMapTypeIndex + 1) % MAP_TYPES.length;
        map.setMapType(MAP_TYPES[currentMapTypeIndex]);
        btnToggleMapType.setText("Map: " + MAP_TYPE_NAMES[currentMapTypeIndex]);
        Toast.makeText(this, "Map Type: " + MAP_TYPE_NAMES[currentMapTypeIndex], Toast.LENGTH_SHORT).show();
    }

    private void togglePlacementMode() {
        switch (currentMode) {
            case GPS:
                currentMode = PlacementMode.WALKING;
                stepCount = 0;
                break;
            case WALKING:
                currentMode = PlacementMode.MANUAL;
                stepCount = 0;
                break;
            case MANUAL:
                currentMode = PlacementMode.GPS;
                break;
        }
        updateModeUI();
    }

    private void updateModeUI() {
        btnToggleMode.setText("Mode: " + currentMode.label);
        btnToggleMode.setBackgroundColor(currentMode.color);
        switch (currentMode) {
            case GPS:
                btnAddPoint.setEnabled(isGpsLocked);
                btnAddPoint.setText("Add Point");
                stepCount = 0;
                Toast.makeText(this, "GPS mode: Tap 'Add Point' to place markers", Toast.LENGTH_SHORT).show();
                break;
            case WALKING:
                btnAddPoint.setEnabled(isGpsLocked);
                stepCount = 0;
                updateWalkingButtonText();
                Toast.makeText(this, "Walking mode: Walk and tap button to add points", Toast.LENGTH_LONG).show();
                break;
            case MANUAL:
                btnAddPoint.setEnabled(true);
                btnAddPoint.setText("Tap Map");
                stepCount = 0;
                Toast.makeText(this, "Manual mode: Tap anywhere on map to add points", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void updateWalkingButtonText() {
        if (currentMode == PlacementMode.WALKING) {
            btnAddPoint.setText("Add Point (Steps: " + stepCount + ")");
        }
    }

    private void toggleLock() {
        if (measureMarker == null) return;
        isManualLock = !isManualLock;
        measureMarker.setDraggable(!isManualLock);
        if (isManualLock) {
            tvGpsStatus.setText("Position LOCKED (Manual)");
            tvGpsStatus.setTextColor(Color.CYAN);
            btnLockMarker.setText("Unlock Position");
        } else {
            tvGpsStatus.setText("Position UNLOCKED");
            tvGpsStatus.setTextColor(Color.YELLOW);
            btnLockMarker.setText("Lock Position");
        }
    }

    private void setupSpinner() {
        List<String> items = new ArrayList<>();
        for (MeasurementUnit u : MeasurementUnit.values()) items.add(u.label);
        spinnerUnits.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items));
        spinnerUnits.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int i, long l) {
                selectedUnit = MeasurementUnit.values()[i];
                updateMeasurements();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        map.getUiSettings().setZoomControlsEnabled(true);
        map.setOnMapClickListener(latLng -> {
            if (currentMode == PlacementMode.MANUAL) {
                addManualPoint(latLng);
            }
        });
        map.setOnMarkerClickListener(marker -> {
            marker.showInfoWindow();
            return true;
        });
        map.setOnInfoWindowClickListener(marker -> enableMarkerReadjust(marker));
        map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {}

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {
                if (marker.equals(measureMarker)) {
                    activeMeasurePosition = marker.getPosition();
                } else if (selectedPointIndex >= 0 && selectedPointIndex < points.size()) {
                    points.set(selectedPointIndex, marker.getPosition());
                    updateMap();
                    updateMeasurements();
                }
            }

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                if (marker.equals(measureMarker)) {
                    activeMeasurePosition = marker.getPosition();
                    Toast.makeText(MapMeasurementActivity.this, "GPS cursor repositioned", Toast.LENGTH_SHORT).show();
                } else if (selectedPointIndex >= 0 && selectedPointIndex < points.size()) {
                    points.set(selectedPointIndex, marker.getPosition());
                    updateMap();
                    updateMeasurements();
                    Toast.makeText(MapMeasurementActivity.this, "Point " + (selectedPointIndex + 1) + " adjusted", Toast.LENGTH_SHORT).show();
                }
                marker.setDraggable(false);
                selectedMarker = null;
                selectedPointIndex = -1;
            }
        });
        if (checkPermission()) enableLocation();
        else requestPermission();
    }

    private void enableMarkerReadjust(Marker marker) {
        selectedMarker = marker;
        marker.setDraggable(true);
        selectedPointIndex = -1;
        String title = marker.getTitle();
        if (title != null && title.startsWith("Point")) {
            try {
                String num = title.replaceAll("[^0-9]", "");
                selectedPointIndex = Integer.parseInt(num) - 1;
            } catch (Exception ignored) {}
        }
        if (marker.equals(measureMarker)) {
            isManualLock = true;
            btnLockMarker.setText("Unlock Position");
            tvGpsStatus.setText("Manual adjustment mode");
            tvGpsStatus.setTextColor(Color.CYAN);
        }
        Toast.makeText(this, "Drag marker to readjust position", Toast.LENGTH_SHORT).show();
    }

    private void setupLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            public void onLocationResult(@NonNull LocationResult result) {
                currentLocation = result.getLastLocation();
                if (currentLocation == null) return;
                double filteredLat = latitudeFilter.filter(currentLocation.getLatitude());
                double filteredLon = longitudeFilter.filter(currentLocation.getLongitude());
                Location filteredLocation = new Location(currentLocation);
                filteredLocation.setLatitude(filteredLat);
                filteredLocation.setLongitude(filteredLon);
                updateLocationUI(filteredLocation);
                if (!isCameraInitialized) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(filteredLocation.getLatitude(), filteredLocation.getLongitude()), 19f));
                    isCameraInitialized = true;
                }
                if (isCollectingReadings && temporaryReadings.size() < READINGS_TO_COLLECT) {
                    collectReading();
                }
                if (!isGpsLocked && currentLocation.getAccuracy() <= MIN_ACCURACY_METERS &&
                        currentLocation.getSpeed() <= MAX_SPEED_MPS && satelliteCount >= MIN_SATELLITES) {
                    stableReadingsCount++;
                    progressGps.setProgress((stableReadingsCount * 100) / STABLE_READINGS_REQUIRED);
                    if (stableReadingsCount >= STABLE_READINGS_REQUIRED) {
                        isGpsLocked = true;
                        if (currentMode == PlacementMode.GPS || currentMode == PlacementMode.WALKING) {
                            btnAddPoint.setEnabled(true);
                        }
                        createMeasureMarker(filteredLocation);
                        tvGpsStatus.setText("GPS LOCKED ✓");
                        tvGpsStatus.setTextColor(Color.GREEN);
                        progressGps.setProgress(100);
                    } else {
                        tvGpsStatus.setText(String.format("Locking... %d/%d", stableReadingsCount, STABLE_READINGS_REQUIRED));
                        tvGpsStatus.setTextColor(Color.YELLOW);
                    }
                } else if (!isGpsLocked) {
                    stableReadingsCount = 0;
                    progressGps.setProgress(0);
                    tvGpsStatus.setText("Waiting for stable signal...");
                    tvGpsStatus.setTextColor(Color.RED);
                }
                if (isGpsLocked && !isManualLock && measureMarker != null) {
                    LatLng newPos = new LatLng(filteredLocation.getLatitude(), filteredLocation.getLongitude());
                    measureMarker.setPosition(newPos);
                    activeMeasurePosition = newPos;
                }
            }
        };
    }

    private void setupMap() {
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);
    }

    private void createMeasureMarker(Location loc) {
        if (measureMarker != null) return;
        LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
        measureMarker = map.addMarker(new MarkerOptions()
                .position(pos)
                .title("Measurement Cursor")
                .snippet("Tap to reposition")
                .draggable(false)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        measureMarker.showInfoWindow();
    }

    private void updateLocationUI(Location loc) {
        tvLatitude.setText(String.format("Latitude: %.7f", loc.getLatitude()));
        tvLongitude.setText(String.format("Longitude: %.7f", loc.getLongitude()));
        if (tvAccuracy != null) {
            tvAccuracy.setText(String.format("Accuracy: ±%.2fm", loc.getAccuracy()));
            if (loc.getAccuracy() <= 3) {
                tvAccuracy.setTextColor(Color.GREEN);
            } else if (loc.getAccuracy() <= 5) {
                tvAccuracy.setTextColor(Color.YELLOW);
            } else {
                tvAccuracy.setTextColor(Color.RED);
            }
        }
        if (accuracyCircle != null) accuracyCircle.remove();
        accuracyCircle = map.addCircle(new CircleOptions()
                .center(new LatLng(loc.getLatitude(), loc.getLongitude()))
                .radius(loc.getAccuracy())
                .strokeColor(Color.BLUE)
                .strokeWidth(2f)
                .fillColor(Color.argb(40, 0, 0, 255)));
    }

    private void addManualPoint(LatLng position) {
        points.add(position);
        map.addMarker(new MarkerOptions()
                        .position(position)
                        .title("Point " + points.size())
                        .snippet("Tap to adjust")
                        .draggable(false)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
                .showInfoWindow();
        updateMap();
        updateMeasurements();
        Toast.makeText(this, "Manual point " + points.size() + " added", Toast.LENGTH_SHORT).show();
    }

    private void collectReading() {
        if (!isCollectingReadings) return;
        if (currentLocation != null && currentLocation.getAccuracy() <= MIN_ACCURACY_METERS) {
            double filteredLat = latitudeFilter.filter(currentLocation.getLatitude());
            double filteredLon = longitudeFilter.filter(currentLocation.getLongitude());
            temporaryReadings.add(new LatLng(filteredLat, filteredLon));
            btnAddPoint.setText("Collecting... " + temporaryReadings.size() + "/" + READINGS_TO_COLLECT);
            if (temporaryReadings.size() >= READINGS_TO_COLLECT) {
                btnAddPoint.setEnabled(true);
                btnAddPoint.setText("Add Averaged Point");
            }
        }
    }

    private double calculateStdDev(List<LatLng> readings, LatLng average) {
        if (readings.size() < 2) return 0;
        double sumSquares = 0;
        for (LatLng reading : readings) {
            double dist = SphericalUtil.computeDistanceBetween(reading, average);
            sumSquares += dist * dist;
        }
        return Math.sqrt(sumSquares / readings.size());
    }

    private void finalizeAveragedPoint() {
        if (temporaryReadings.isEmpty()) return;
        double avgLat = 0, avgLon = 0;
        for (LatLng reading : temporaryReadings) {
            avgLat += reading.latitude;
            avgLon += reading.longitude;
        }
        avgLat /= temporaryReadings.size();
        avgLon /= temporaryReadings.size();
        LatLng averagedPoint = new LatLng(avgLat, avgLon);
        points.add(averagedPoint);
        double stdDev = calculateStdDev(temporaryReadings, averagedPoint);
        map.addMarker(new MarkerOptions()
                .position(averagedPoint)
                .title("Point " + points.size())
                .snippet(String.format("Accuracy: ±%.2fm", stdDev))
                .draggable(false)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        updateMap();
        updateMeasurements();
        isCollectingReadings = false;
        temporaryReadings.clear();
        btnAddPoint.setText("Add Point");
        btnAddPoint.setEnabled(true);
        Toast.makeText(this, String.format("Point %d added (±%.2fm)", points.size(), stdDev), Toast.LENGTH_SHORT).show();
    }
    private void addPoint() {
        if (currentMode == PlacementMode.MANUAL) {
            Toast.makeText(this, "Tap anywhere on map to manually place a point", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentMode == PlacementMode.WALKING) {
            if (currentLocation == null) {
                Toast.makeText(this, "Waiting for GPS location...", Toast.LENGTH_SHORT).show();
                return;
            }
            double filteredLat = latitudeFilter.filter(currentLocation.getLatitude());
            double filteredLon = longitudeFilter.filter(currentLocation.getLongitude());
            LatLng position = new LatLng(filteredLat, filteredLon);
            points.add(position);
            map.addMarker(new MarkerOptions()
                    .position(position)
                    .title("Point " + points.size())
                    .snippet("Steps: " + stepCount)
                    .draggable(false)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
            updateMap();
            updateMeasurements();
            Toast.makeText(this, "Point " + points.size() + " added (Steps: " + stepCount + ")", Toast.LENGTH_SHORT).show();
            stepCount = 0;
            updateWalkingButtonText();
            return;
        }
        if (currentLocation != null && currentLocation.getAccuracy() > 5.0f && !isCollectingReadings) {
            checkAndWarnAccuracy();
        }
        if (!isCollectingReadings) {
            isCollectingReadings = true;
            temporaryReadings.clear();
            btnAddPoint.setText("Collecting... 0/" + READINGS_TO_COLLECT);
            btnAddPoint.setEnabled(false);
            Toast.makeText(this, "Stand still. Collecting " + READINGS_TO_COLLECT + " readings...", Toast.LENGTH_LONG).show();
        } else if (temporaryReadings.size() >= READINGS_TO_COLLECT) {
            finalizeAveragedPoint();
        }
    }

    private void checkAndWarnAccuracy() {
        if (currentLocation == null) return;
        float accuracy = currentLocation.getAccuracy();
        if (accuracy > 5.0f) {
            new AlertDialog.Builder(this)
                    .setTitle("Low GPS Accuracy")
                    .setMessage(String.format(
                            "Current GPS accuracy is ±%.1fm. For better results:\n\n" +
                                    "• Move to open sky area\n" +
                                    "• Wait for more satellites\n" +
                                    "• Avoid tall buildings/trees\n" +
                                    "• Stand still for 30 seconds\n\n" +
                                    "Continue anyway?", accuracy))
                    .setPositiveButton("Continue", (dialog, which) -> {})
                    .setNegativeButton("Wait", (dialog, which) -> {
                        isCollectingReadings = false;
                        temporaryReadings.clear();
                        btnAddPoint.setText("Add Point");
                    })
                    .show();
        }
    }

    private void updateMap() {
        if (currentPolyline != null) currentPolyline.remove();
        if (currentPolygon != null) currentPolygon.remove();
        if (points.size() >= 2) {
            currentPolyline = map.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .color(Color.BLUE)
                    .width(5f)
                    .geodesic(true));
        }
        if (points.size() >= 3) {
            currentPolygon = map.addPolygon(new PolygonOptions()
                    .addAll(points)
                    .fillColor(Color.argb(50, 255, 0, 0))
                    .strokeColor(Color.RED)
                    .strokeWidth(3f)
                    .geodesic(true));
        }
    }

    private void updateMeasurements() {
        if (points.size() < 2) {
            tvDistance.setText("Distance: --");
            tvArea.setText("Area: --");
            return;
        }
        double perimeterMeters = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            perimeterMeters += SphericalUtil.computeDistanceBetween(points.get(i), points.get(i + 1));
        }
        if (points.size() >= 3) {
            perimeterMeters += SphericalUtil.computeDistanceBetween(points.get(points.size() - 1), points.get(0));
        }
        double perimeterConverted = perimeterMeters / selectedUnit.toMeters;
        tvDistance.setText(String.format("Perimeter: %.2f %s", perimeterConverted, selectedUnit.label));
        if (points.size() >= 3) {
            List<LatLng> closedPolygonForArea = new ArrayList<>(points);
            closedPolygonForArea.add(points.get(0));
            double areaMeters = SphericalUtil.computeArea(closedPolygonForArea);
            double areaConverted = areaMeters / (selectedUnit.toMeters * selectedUnit.toMeters);
            if (currentLocation != null) {
                float accuracy = currentLocation.getAccuracy();
                double errorEstimate = estimateAreaError(points.size(), accuracy);
                double errorConverted = errorEstimate / (selectedUnit.toMeters * selectedUnit.toMeters);
                tvArea.setText(String.format("Area: %.4f ± %.4f %s²", areaConverted, errorConverted, selectedUnit.label));
            } else {
                tvArea.setText(String.format("Area: %.4f %s²", areaConverted, selectedUnit.label));
            }
        } else {
            tvArea.setText("Area: --");
        }
    }

    private double estimateAreaError(int numPoints, float gpsAccuracy) {
        double perimeterMeters = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            perimeterMeters += SphericalUtil.computeDistanceBetween(points.get(i), points.get(i + 1));
        }
        if (points.size() >= 3) {
            perimeterMeters += SphericalUtil.computeDistanceBetween(points.get(points.size() - 1), points.get(0));
        }
        return perimeterMeters * gpsAccuracy * 1.5;
    }

    private void undoLast() {
        if (points.isEmpty()) return;
        points.remove(points.size() - 1);
        map.clear();
        measureMarker = null;
        if (currentLocation != null) {
            Location filteredLoc = new Location(currentLocation);
            filteredLoc.setLatitude(latitudeFilter.filter(currentLocation.getLatitude()));
            filteredLoc.setLongitude(longitudeFilter.filter(currentLocation.getLongitude()));
            createMeasureMarker(filteredLoc);
        }
        for (int i = 0; i < points.size(); i++) {
            map.addMarker(new MarkerOptions()
                    .position(points.get(i))
                    .title("Point " + (i + 1))
                    .draggable(false)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        }
        updateMap();
        updateMeasurements();
        Toast.makeText(this, "Last point removed", Toast.LENGTH_SHORT).show();
    }

    private void clearAll() {
        points.clear();
        map.clear();
        measureMarker = null;
        latitudeFilter.reset();
        longitudeFilter.reset();
        isCollectingReadings = false;
        temporaryReadings.clear();
        stepCount = 0;
        btnAddPoint.setText("Add Point");
        btnAddPoint.setEnabled(isGpsLocked && currentMode == PlacementMode.GPS);
        if (currentLocation != null) {
            Location filteredLoc = new Location(currentLocation);
            filteredLoc.setLatitude(latitudeFilter.filter(currentLocation.getLatitude()));
            filteredLoc.setLongitude(longitudeFilter.filter(currentLocation.getLongitude()));
            createMeasureMarker(filteredLoc);
        }
        updateMeasurements();
        if (currentMode == PlacementMode.WALKING) {
            updateWalkingButtonText();
        }
        Toast.makeText(this, "All points cleared", Toast.LENGTH_SHORT).show();
    }

    private void savePlot() {
        if (points == null || points.isEmpty()) {
            Toast.makeText(this, "No points to save", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Map Image");
        final EditText input = new EditText(this);
        input.setHint("Enter file name");
        input.setText("Plot_" + System.currentTimeMillis());
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            pendingFileName = input.getText().toString().trim() + ".jpeg";
            captureMapSnapshot();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void captureMapSnapshot() {
        map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        map.snapshot(bitmap -> {
            mapSnapshotBitmap = drawCoordinatesOnBitmap(bitmap);
            openFilePicker();
        });
    }

    private Bitmap drawCoordinatesOnBitmap(Bitmap original) {
        Bitmap mutable = original.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.argb(180, 0, 0, 0));
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setAntiAlias(true);
        int padding = 20;
        int y = padding + 40;
        canvas.drawRect(0, 0, original.getWidth(), 250 + points.size() * 35, bgPaint);
        canvas.drawText("GPS Plot Data", padding, y, textPaint);
        y += 40;
        for (int i = 0; i < points.size(); i++) {
            LatLng p = points.get(i);
            canvas.drawText((i + 1) + ": " + String.format("%.6f", p.latitude) + ", " + String.format("%.6f", p.longitude), padding, y, textPaint);
            y += 35;
        }
        if (points.size() >= 2) {
            double perimeterMeters = 0;
            for (int i = 0; i < points.size() - 1; i++) {
                perimeterMeters += SphericalUtil.computeDistanceBetween(points.get(i), points.get(i + 1));
            }
            if (points.size() >= 3) {
                perimeterMeters += SphericalUtil.computeDistanceBetween(points.get(points.size() - 1), points.get(0));
            }
            canvas.drawText("Perimeter: " + String.format("%.2f", perimeterMeters / selectedUnit.toMeters) + " " + selectedUnit.label, padding, y + 20, textPaint);
        }
        if (points.size() >= 3) {
            double areaMeters = SphericalUtil.computeArea(points);
            canvas.drawText("Area: " + String.format("%.4f", areaMeters / (selectedUnit.toMeters * selectedUnit.toMeters)) + " " + selectedUnit.label + "²", padding, y + 60, textPaint);
        }
        return mutable;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_TITLE, pendingFileName);
        startActivityForResult(intent, REQ_SAVE_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SAVE_IMAGE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            saveBitmapToUri(data.getData());
        }
    }

    private void saveBitmapToUri(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            mapSnapshotBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            Toast.makeText(this, "Map image saved successfully", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableLocation();
                setupGnssStatus();
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void enableLocation() {
        try {
            if (map != null) {
                map.setMyLocationEnabled(true);
                LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
                        .setMinUpdateIntervalMillis(250)
                        .setMaxUpdateDelayMillis(1000)
                        .setWaitForAccurateLocation(true)
                        .build();
                fusedLocationClient.requestLocationUpdates(req, locationCallback, getMainLooper());
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkGpsStatus();
        if (map != null && checkPermission()) {
            enableLocation();
        }
        if (magnetometer != null) {
            sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (accelerometer != null) {
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (stepDetector != null) {
            sensorManager.registerListener(sensorListener, stepDetector, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (locationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.unregisterGnssStatusCallback(null);
        }
    }
}