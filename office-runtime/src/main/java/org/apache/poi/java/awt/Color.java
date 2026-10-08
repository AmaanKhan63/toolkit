package org.apache.poi.java.awt;

/** Toolkit's small sRGB value adapter for POI model APIs; no desktop rendering. */
public final class Color {
    private final int argb;
    public static final Color white=new Color(255,255,255), WHITE=white;
    public static final Color lightGray=new Color(192,192,192), LIGHT_GRAY=lightGray;
    public static final Color gray=new Color(128,128,128), GRAY=gray;
    public static final Color darkGray=new Color(64,64,64), DARK_GRAY=darkGray;
    public static final Color black=new Color(0,0,0), BLACK=black;
    public static final Color red=new Color(255,0,0), RED=red;
    public static final Color pink=new Color(255,175,175), PINK=pink;
    public static final Color orange=new Color(255,200,0), ORANGE=orange;
    public static final Color yellow=new Color(255,255,0), YELLOW=yellow;
    public static final Color green=new Color(0,255,0), GREEN=green;
    public static final Color magenta=new Color(255,0,255), MAGENTA=magenta;
    public static final Color cyan=new Color(0,255,255), CYAN=cyan;
    public static final Color blue=new Color(0,0,255), BLUE=blue;
    public Color(int r,int g,int b) { this(r,g,b,255); }
    public Color(int r,int g,int b,int a) {
        if ((r|g|b|a)<0 || r>255 || g>255 || b>255 || a>255) throw new IllegalArgumentException("Channel outside 0-255");
        argb=(a<<24)|(r<<16)|(g<<8)|b;
    }
    public Color(int rgb) { this(rgb,false); }
    public Color(int value,boolean hasAlpha) { argb=hasAlpha?value:value|0xff000000; }
    private static int channel(float n) { if(n<0||n>1||Float.isNaN(n)) throw new IllegalArgumentException("Channel outside 0-1"); return Math.round(n*255); }
    public Color(float r,float g,float b) { this(r,g,b,1f); }
    public Color(float r,float g,float b,float a) { this(channel(r),channel(g),channel(b),channel(a)); }
    public int getRGB(){return argb;}
    public int getRed(){return (argb>>>16)&255;}
    public int getGreen(){return (argb>>>8)&255;}
    public int getBlue(){return argb&255;}
    public int getAlpha(){return argb>>>24;}
    public float[] getRGBColorComponents(float[] result){float[] out=result==null?new float[3]:result;out[0]=getRed()/255f;out[1]=getGreen()/255f;out[2]=getBlue()/255f;return out;}
    public float[] getRGBComponents(float[] result){float[] out=result==null?new float[4]:result;getRGBColorComponents(out);out[3]=getAlpha()/255f;return out;}
    public float[] getComponents(float[] result){return getRGBComponents(result);}
    public float[] getColorComponents(float[] result){return getRGBColorComponents(result);}
    public Color darker(){return new Color((int)(getRed()*.7),(int)(getGreen()*.7),(int)(getBlue()*.7),getAlpha());}
    public Color brighter(){return new Color(Math.min(255,(int)(Math.max(3,getRed())/.7)),Math.min(255,(int)(Math.max(3,getGreen())/.7)),Math.min(255,(int)(Math.max(3,getBlue())/.7)),getAlpha());}
    public static Color decode(String value){return new Color(Integer.decode(value));}
    public static Color getColor(String key){return getColor(key,(Color)null);}
    public static Color getColor(String key,Color fallback){Integer value=Integer.getInteger(key);return value==null?fallback:new Color(value);}
    public static Color getColor(String key,int fallback){return new Color(Integer.getInteger(key,fallback));}
    public int getTransparency(){return getAlpha()==255?1:getAlpha()==0?2:3;}
    @Override public int hashCode(){return argb;}
    @Override public boolean equals(Object other){return other instanceof Color && ((Color)other).argb==argb;}
    @Override public String toString(){return "sRGB("+getRed()+","+getGreen()+","+getBlue()+","+getAlpha()+")";}
}
