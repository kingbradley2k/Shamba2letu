package com.example.shambaletu;

import com.google.android.gms.maps.model.LatLng;
import java.util.List;

public class Project {
    private String name;
    private List<LatLng> points;
    private double area;
    private double perimeter;
    private String unit;
    private long timestamp;

    public Project(String name, List<LatLng> points, double area, double perimeter, String unit, long timestamp) {
        this.name = name;
        this.points = points;
        this.area = area;
        this.perimeter = perimeter;
        this.unit = unit;
        this.timestamp = timestamp;
    }

    public String getName() { return name; }
    public List<LatLng> getPoints() { return points; }
    public double getArea() { return area; }
    public double getPerimeter() { return perimeter; }
    public String getUnit() { return unit; }
    public long getTimestamp() { return timestamp; }
}
