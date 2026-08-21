package net.schalker.SMPS.modules.cosmetics.util;

/**
 *   RGB  ()
 *   ProCosmetics
 */
public class RGBFade {

    private int r = 255;
    private int g = 0;
    private int b = 0;

    public void nextRGB() {
        if (r == 255 && g < 255 && b == 0) g++;
        else if (g == 255 && r > 0 && b == 0) r--;
        else if (g == 255 && b < 255 && r == 0) b++;
        else if (b == 255 && g > 0 && r == 0) g--;
        else if (b == 255 && r < 255 && g == 0) r++;
        else if (r == 255 && b > 0 && g == 0) b--;
    }

    public int getR() {
        return r;
    }

    public int getG() {
        return g;
    }

    public int getB() {
        return b;
    }

    public org.bukkit.Color toColor() {
        return org.bukkit.Color.fromRGB(r, g, b);
    }
}
