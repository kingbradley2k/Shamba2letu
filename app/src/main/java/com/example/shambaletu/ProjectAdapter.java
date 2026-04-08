package com.example.shambaletu;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

    public interface OnProjectClickListener {
        void onDeleteClick(int position);
        void onExportClick(int position);
    }

    private final List<Project> projects;
    private final OnProjectClickListener listener;

    public ProjectAdapter(List<Project> projects, OnProjectClickListener listener) {
        this.projects = projects;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project, parent, false);
        return new ProjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        Project project = projects.get(position);
        holder.projectNameTextView.setText(project.getName());
        holder.projectDetailsTextView.setText(
            String.format(Locale.getDefault(), "Area: %.2f %s² | Perimeter: %.2f %s", 
                project.getArea(), project.getUnit(), project.getPerimeter(), project.getUnit())
        );
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    class ProjectViewHolder extends RecyclerView.ViewHolder {
        TextView projectNameTextView;
        TextView projectDetailsTextView;
        ImageButton deleteButton;
        ImageButton exportButton;

        public ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            projectNameTextView = itemView.findViewById(R.id.projectNameTextView);
            projectDetailsTextView = itemView.findViewById(R.id.projectDetailsTextView);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            exportButton = itemView.findViewById(R.id.exportButton);

            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onDeleteClick(position);
                    }
                }
            });

            exportButton.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onExportClick(position);
                    }
                }
            });
        }
    }
}
