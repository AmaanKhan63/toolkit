package org.apache.poi.java.awt.geom;

/** Rectangle value API used by POI's binary slide model; no desktop renderer. */
public abstract class Rectangle2D implements Cloneable, java.io.Serializable {
    public abstract double getX();
    public abstract double getY();
    public abstract double getWidth();
    public abstract double getHeight();
    public abstract void setRect(double x, double y, double width, double height);
    public void setRect(Rectangle2D r) { setRect(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    public double getMinX() { return getX(); }
    public double getMinY() { return getY(); }
    public double getMaxX() { return getX() + getWidth(); }
    public double getMaxY() { return getY() + getHeight(); }
    public double getCenterX() { return getX() + getWidth() / 2; }
    public double getCenterY() { return getY() + getHeight() / 2; }
    public boolean isEmpty() { return getWidth() <= 0 || getHeight() <= 0; }
    public Rectangle2D getBounds2D() { return new Double(getX(), getY(), getWidth(), getHeight()); }
    @Override public Object clone() { return getBounds2D(); }
    public static class Double extends Rectangle2D {
        public double x, y, width, height;
        public Double() { }
        public Double(double x, double y, double width, double height) { setRect(x, y, width, height); }
        public double getX() { return x; } public double getY() { return y; }
        public double getWidth() { return width; } public double getHeight() { return height; }
        public void setRect(double x, double y, double width, double height) { this.x = x; this.y = y; this.width = width; this.height = height; }
    }
    public static class Float extends Rectangle2D {
        public float x, y, width, height;
        public Float() { }
        public Float(float x, float y, float width, float height) { setRect(x, y, width, height); }
        public double getX() { return x; } public double getY() { return y; }
        public double getWidth() { return width; } public double getHeight() { return height; }
        public void setRect(double x, double y, double width, double height) { this.x = (float)x; this.y = (float)y; this.width = (float)width; this.height = (float)height; }
    }
}
