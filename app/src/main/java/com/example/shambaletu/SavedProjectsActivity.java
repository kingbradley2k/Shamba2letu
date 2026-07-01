package com.example.shambaletu;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SavedProjectsActivity extends AppCompatActivity implements ProjectAdapter.OnProjectClickListener {

    public static final String PREFS_NAME = "ShambaletuPrefs";
    public static final String PROJECTS_KEY = "SavedProjects";

    private List<Project> projects;
    private ProjectAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private Project projectToExport;
    private String exportType = "csv";

    private final ActivityResultLauncher<Intent> createFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        switch (exportType) {
                            case "csv": writeCsvToFile(uri); break;
                            case "kml": writeKmlToFile(uri); break;
                            case "pdf": writePdfToFile(uri); break;
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String[]> importFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    importFile(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_projects);

        Toolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_import) {
                importFileLauncher.launch(new String[]{"text/*", "application/vnd.google-earth.kml+xml"});
                return true;
            }
            return false;
        });

        recyclerView = findViewById(R.id.projectsRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadProjects();
    }

    private void loadProjects() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(PROJECTS_KEY, "[]");
        Type type = new TypeToken<ArrayList<Project>>() {}.getType();
        projects = new Gson().fromJson(json, type);

        if (projects == null || projects.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter = new ProjectAdapter(projects, this);
            recyclerView.setAdapter(adapter);
        }
    }

    @Override
    public void onDeleteClick(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Project")
                .setMessage("Are you sure you want to delete '" + projects.get(position).getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    projects.remove(position);
                    saveProjects();
                    adapter.notifyItemRemoved(position);
                    if (projects.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onExportClick(int position) {
        projectToExport = projects.get(position);
        String[] options = {"CSV (.csv)", "KML (.kml)", "PDF Report (.pdf)"};
        new AlertDialog.Builder(this)
                .setTitle("Select Export Format")
                .setItems(options, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    switch (which) {
                        case 0:
                            exportType = "csv";
                            intent.setType("text/csv");
                            intent.putExtra(Intent.EXTRA_TITLE, projectToExport.getName() + ".csv");
                            break;
                        case 1:
                            exportType = "kml";
                            intent.setType("application/vnd.google-earth.kml+xml");
                            intent.putExtra(Intent.EXTRA_TITLE, projectToExport.getName() + ".kml");
                            break;
                        case 2:
                            exportType = "pdf";
                            intent.setType("application/pdf");
                            intent.putExtra(Intent.EXTRA_TITLE, projectToExport.getName() + ".pdf");
                            break;
                    }
                    createFileLauncher.launch(intent);
                })
                .show();
    }

    private void writeKmlToFile(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            StringBuilder kml = new StringBuilder();
            kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            kml.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
            kml.append("  <Document>\n");
            kml.append("    <name>").append(projectToExport.getName()).append("</name>\n");
            kml.append("    <Placemark>\n");
            kml.append("      <name>Boundary</name>\n");
            kml.append("      <Polygon>\n");
            kml.append("        <outerBoundaryIs>\n");
            kml.append("          <LinearRing>\n");
            kml.append("            <coordinates>\n");
            for (MeasuredPoint p : projectToExport.getPoints()) {
                kml.append(String.format(Locale.US, "              %.6f,%.6f,0\n",
                        p.getLatLng().longitude, p.getLatLng().latitude));
            }
            if (!projectToExport.getPoints().isEmpty()) {
                MeasuredPoint first = projectToExport.getPoints().get(0);
                kml.append(String.format(Locale.US, "              %.6f,%.6f,0\n",
                        first.getLatLng().longitude, first.getLatLng().latitude));
            }
            kml.append("            </coordinates>\n");
            kml.append("          </LinearRing>\n");
            kml.append("        </outerBoundaryIs>\n");
            kml.append("      </Polygon>\n");
            kml.append("    </Placemark>\n");
            kml.append("  </Document>\n");
            kml.append("</kml>");

            outputStream.write(kml.toString().getBytes());
            Toast.makeText(this, "Exported KML successfully!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "KML Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void writePdfToFile(Uri uri) {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        paint.setTextSize(24);
        paint.setFakeBoldText(true);
        canvas.drawText("Shamba Letu - Field Report", 50, 50, paint);

        paint.setTextSize(14);
        paint.setFakeBoldText(false);
        int y = 90;
        canvas.drawText("Project Name: " + projectToExport.getName(), 50, y, paint);
        y += 20;
        canvas.drawText("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(projectToExport.getTimestamp())), 50, y, paint);
        y += 20;
        canvas.drawText(String.format(Locale.US, "Area: %.4f %s²", projectToExport.getArea(), projectToExport.getUnit()), 50, y, paint);
        y += 20;
        canvas.drawText(String.format(Locale.US, "Perimeter: %.2f %s", projectToExport.getPerimeter(), projectToExport.getUnit()), 50, y, paint);

        y += 40;
        String snapshotPath = projectToExport.getSnapshotPath();
        if (snapshotPath != null && new File(snapshotPath).exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(snapshotPath);
            if (bitmap != null) {
                float scale = (float) (pageInfo.getPageWidth() - 100) / bitmap.getWidth();
                int targetHeight = (int) (bitmap.getHeight() * scale);
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, pageInfo.getPageWidth() - 100, targetHeight, true);
                canvas.drawBitmap(scaled, 50, y, paint);
                y += targetHeight + 30;
            }
        } else {
            canvas.drawText("[Map Snapshot Not Available]", 50, y, paint);
            y += 30;
        }

        canvas.drawText("Coordinates:", 50, y, paint);
        y += 20;
        paint.setTextSize(10);
        for (int i = 0; i < projectToExport.getPoints().size(); i++) {
            MeasuredPoint p = projectToExport.getPoints().get(i);
            canvas.drawText(String.format(Locale.US, "%d. %.6f, %.6f (%s)", i + 1, p.getLatLng().latitude, p.getLatLng().longitude, p.isLowConfidence() ? "Low Confidence" : "High Confidence"), 50, y, paint);
            y += 15;
            if (y > pageInfo.getPageHeight() - 50) break;
        }

        document.finishPage(page);
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            document.writeTo(outputStream);
            Toast.makeText(this, "Exported PDF successfully!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "PDF Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            document.close();
        }
    }

    private void importFile(Uri uri) {
        String fileName = getFileName(uri);
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (fileName.endsWith(".csv")) {
                importCsv(inputStream, fileName);
            } else if (fileName.endsWith(".kml")) {
                importKml(inputStream, fileName);
            } else {
                Toast.makeText(this, "Unsupported file format", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result != null ? result.toLowerCase() : "";
    }

    private void importCsv(InputStream inputStream, String fileName) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = reader.readLine(); // Header 1
        line = reader.readLine(); // Data 1
        if (line == null) return;
        String[] meta = line.split(",");
        String name = meta[0];
        double area = Double.parseDouble(meta[1]);
        double perimeter = Double.parseDouble(meta[2]);
        String unit = meta[3];

        reader.readLine(); // Blank
        reader.readLine(); // Header 2
        
        List<MeasuredPoint> importedPoints = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String[] p = line.split(",");
            if (p.length >= 2) {
                double lat = Double.parseDouble(p[0]);
                double lon = Double.parseDouble(p[1]);
                boolean low = p.length > 2 && p[2].equalsIgnoreCase("Low");
                importedPoints.add(new MeasuredPoint(lat, lon, low));
            }
        }
        
        Project newProject = new Project(name + " (Imported)", importedPoints, area, perimeter, unit, System.currentTimeMillis(), null);
        projects.add(newProject);
        saveProjects();
        loadProjects();
        Toast.makeText(this, "Imported CSV: " + name, Toast.LENGTH_SHORT).show();
    }

    private void importKml(InputStream inputStream, String fileName) throws Exception {
        // Simple KML parser for coordinates
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        String content = sb.toString();

        String coordTag = "<coordinates>";
        int start = content.indexOf(coordTag);
        int end = content.indexOf("</coordinates>");
        if (start == -1 || end == -1) throw new Exception("No coordinates found in KML");

        String coordsStr = content.substring(start + coordTag.length(), end).trim();
        String[] pointsArr = coordsStr.split("\\s+");
        List<MeasuredPoint> importedPoints = new ArrayList<>();
        for (String pStr : pointsArr) {
            String[] c = pStr.split(",");
            if (c.length >= 2) {
                double lon = Double.parseDouble(c[0]);
                double lat = Double.parseDouble(c[1]);
                importedPoints.add(new MeasuredPoint(lat, lon, false));
            }
        }

        // Calculate metadata if missing
        double area = SphericalUtil.computeArea(getLatLngs(importedPoints));
        double perimeter = 0;
        List<LatLng> latLngs = getLatLngs(importedPoints);
        for (int i = 0; i < latLngs.size() - 1; i++) {
            perimeter += SphericalUtil.computeDistanceBetween(latLngs.get(i), latLngs.get(i + 1));
        }
        if (latLngs.size() > 2) perimeter += SphericalUtil.computeDistanceBetween(latLngs.get(latLngs.size()-1), latLngs.get(0));

        Project newProject = new Project(fileName.replace(".kml", "") + " (KML)", importedPoints, area, perimeter, "Meters", System.currentTimeMillis(), null);
        projects.add(newProject);
        saveProjects();
        loadProjects();
        Toast.makeText(this, "Imported KML successfully", Toast.LENGTH_SHORT).show();
    }

    private List<LatLng> getLatLngs(List<MeasuredPoint> points) {
        List<LatLng> list = new ArrayList<>();
        for (MeasuredPoint p : points) list.add(p.getLatLng());
        return list;
    }

    private void writeCsvToFile(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            StringBuilder csv = new StringBuilder();
            csv.append("Project Name,Area,Perimeter,Unit\n");
            csv.append(String.format(Locale.US, "%s,%.2f,%.2f,%s\n\n",
                    projectToExport.getName(), projectToExport.getArea(), 
                    projectToExport.getPerimeter(), projectToExport.getUnit()));
            
            csv.append("Latitude,Longitude,Confidence\n");
            for (MeasuredPoint p : projectToExport.getPoints()) {
                csv.append(String.format(Locale.US, "%.6f,%.6f,%s\n", 
                    p.getLatLng().latitude, p.getLatLng().longitude, 
                    p.isLowConfidence() ? "Low" : "High"));
            }

            outputStream.write(csv.toString().getBytes());
            Toast.makeText(this, "Exported successfully!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveProjects() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = new Gson().toJson(projects);
        prefs.edit().putString(PROJECTS_KEY, json).apply();
    }
}
