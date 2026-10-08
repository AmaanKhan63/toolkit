package org.apache.poi.java.awt;
import org.apache.poi.java.awt.geom.Dimension2D;
/** Toolkit size adapter for POI model APIs. */
public class Dimension extends Dimension2D implements java.io.Serializable {
    public int width,height;
    public Dimension(){this(0,0);}
    public Dimension(int width,int height){this.width=width;this.height=height;}
    public Dimension(Dimension value){this(value.width,value.height);}
    public double getWidth(){return width;}
    public double getHeight(){return height;}
    public void setSize(double width,double height){this.width=(int)Math.ceil(width);this.height=(int)Math.ceil(height);}
    public void setSize(int width,int height){this.width=width;this.height=height;}
    public void setSize(Dimension value){setSize(value.width,value.height);}
    public Dimension getSize(){return new Dimension(this);}
    @Override public int hashCode(){return 31*width+height;}
    @Override public boolean equals(Object other){return other instanceof Dimension && ((Dimension)other).width==width && ((Dimension)other).height==height;}
}
