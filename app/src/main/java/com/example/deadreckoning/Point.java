package com.example.deadreckoning;

public class Point {
    double x;
    double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setX(double x){this.x=x;}
    public void setY(double y){this.y=y;}

    // Get
    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }


}
