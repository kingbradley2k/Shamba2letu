package com.example.shambaletu;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
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

    private final ActivityResultLauncher<Intent> createFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        writeCsvToFile(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_projects);

        Toolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

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
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, projectToExport.getName() + ".csv");
        createFileLauncher.launch(intent);
    }

    private void writeCsvToFile(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            StringBuilder csv = new StringBuilder();
            csv.append("Project Name,Area,Perimeter,Unit\n");
            csv.append(String.format(Locale.US, "%s,%.2f,%.2f,%s\n\n",
                    projectToExport.getName(), projectToExport.getArea(), 
                    projectToExport.getPerimeter(), projectToExport.getUnit()));
            
            csv.append("Latitude,Longitude\n");
            for (com.google.android.gms.maps.model.LatLng p : projectToExport.getPoints()) {
                csv.append(String.format(Locale.US, "%.6f,%.6f\n", p.latitude, p.longitude));
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
