package me.drewh.gen;

import com.flowpowered.noise.module.source.Voronoi;
import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Stack;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author Drew Heenan
 */
public class ContinentGenerator {
    
    public static void main(String[] args) {
        ContinentGenerator gen = new ContinentGenerator();
        gen.generate();
    }
    
    private int voronoiSelectorPoints = 50, voronoiPointsRadius = 200;
    private int size = 1000, seed = 0;
    private double xScale = 0.01, yScale = 0.01;
    
    public void setSeed(int seed) {
        this.seed = seed;
    }
    
    public void setSize(int size) {
        this.size = size;
        if(size < this.voronoiPointsRadius)
            this.voronoiPointsRadius = size;
    }
    
    public void setVoronoiSelectorPointCount(int count) {
        this.voronoiSelectorPoints = count;
    }
    
    public void setVoronoiSelectorPointRadius(int radius) {
        this.voronoiPointsRadius = radius > size ? size : radius;
    }
    
    public void setXScale(double xScale) {
        this.xScale = xScale;
    }
    
    public void setYScale(double yScale) {
        this.yScale = yScale;
    }
    
    public void generate() {
        double[][] voronoiMap = new double[this.size][this.size];
        Voronoi voronoiNoise = new Voronoi();
        voronoiNoise.setSeed(this.seed);
        for(int x = 0; x < this.size; x++) {
            for(int y = 0; y < this.size; y++) {
                voronoiMap[x][y] = voronoiNoise.getValue(this.xScale*x, this.yScale*y, 0);
            }
        }
        
        Random rand = new Random(this.seed);
        boolean[][] landMap = new boolean[this.size][this.size];
        for(int pointNum = 0; pointNum < this.voronoiSelectorPoints; pointNum++) {
            int radius = rand.nextInt(voronoiPointsRadius);
            double theta = rand.nextDouble() * Math.PI * 2;
            Point fromPt = new Point(this.size/2 + (int)(radius*Math.cos(theta)), this.size/2 + (int)(radius*Math.sin(theta)));
            if(!landMap[fromPt.x][fromPt.y]) {
                double fillValue = voronoiMap[fromPt.x][fromPt.y];
                fill(fromPt, pt -> {
                    if(pt.x >= 0 && pt.x < this.size && pt.y >= 0 && pt.y < this.size)
                        if (!landMap[pt.x][pt.y] && voronoiMap[pt.x][pt.y] == fillValue) {
                            landMap[pt.x][pt.y] = true;
                            return true;
                        }
                    return false;
                });
            }
        }
        
        //Save the output in the landMap, for now
        BufferedImage img = new BufferedImage(this.size, this.size, BufferedImage.TYPE_INT_RGB);
        for(int x = 0; x < this.size; x++)
            for(int y = 0; y < this.size; y++)
                img.setRGB(x, y, new Color(0, landMap[x][y] ? 255 : 0, 0).getRGB());
        try {
            ImageIO.write(img, "png", new File("out.png"));
        } catch (IOException ex) {
            Logger.getLogger(ContinentGenerator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    public static void fill(Point startPoint, Predicate<Point> fillPredicate) {
        Stack<Point> fillStack = new Stack<>();
        fillStack.push(startPoint);
        while(!fillStack.isEmpty()) {
            Point pt = fillStack.pop();
            if(!fillPredicate.test(pt))
                continue;
            for(int dx = -1; dx <= 1; dx++) {
                for(int dy = -1; dy <= 1; dy++) {
                    if(dx != dy) {
                        fillStack.push(new Point(pt.x + dx, pt.y + dy));
                    }
                }
            }
        }
    }

    
}
