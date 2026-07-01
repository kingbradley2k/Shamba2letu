package com.example.shambaletu;

import java.util.List;

public class Project {
    private String name;
    private List<MeasuredPoint> points;
    private double area;
    private double perimeter;
    private String unit;
    private long timestamp;
    private String snapshotPath;

    public Project(String name, List<MeasuredPoint> points, double area, double perimeter, String unit, long timestamp, String snapshotPath) {
        this.name = name;
        this.points = points;
        this.area = area;
        this.perimeter = perimeter;
        this.unit = unit;
        this.timestamp = timestamp;
        this.snapshotPath = snapshotPath;
    }

    public String getName() { return name; }
    public List<MeasuredPoint> getPoints() { return points; }
    public double getArea() { return area; }
    public double getPerimeter() { return perimeter; }
    public String getUnit() { return unit; }
    public long getTimestamp() { return timestamp; }
    public String getSnapshotPath() { return snapshotPath; }
}
