package org.apache.poi.java.awt.geom;
/** Toolkit abstract size adapter for POI model APIs. */
public abstract class Dimension2D implements Cloneable {
    public abstract double getWidth();
    public abstract double getHeight();
    public abstract void setSize(double width,double height);
    public void setSize(Dimension2D value){setSize(value.getWidth(),value.getHeight());}
    public Object clone(){try{return super.clone();}catch(CloneNotSupportedException impossible){throw new AssertionError(impossible);}}
}
