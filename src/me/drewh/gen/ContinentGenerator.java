package me.drewh.gen;

import com.flowpowered.noise.module.source.Voronoi;
import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
        gen.setXScale(0.05);
        gen.setYScale(0.06);
        gen.setExpansionChanceMultiplier(4.0);
        gen.generate();
    }
    
    private int size = 1000, seed = 0;
    private int minimumLakeSize = 100;
    private double xScale = 0.01, yScale = 0.01;
    private double expansionChanceMultiplier = 3.0;
    
    public void setSeed(int seed) {
        this.seed = seed;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
    
    public void setExpansionChanceMultiplier(double multiplier) {
        this.expansionChanceMultiplier = multiplier;
    }
    
    public void setMinimumLakeSize(int minimumLakeSize) {
        this.minimumLakeSize = minimumLakeSize;
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
        
        //Generate a landmass.
        boolean[][] landMap = new boolean[this.size][this.size];
        Point fromPt = new Point(this.size/2, this.size/2);//new Point(this.size/2 + (int)(radius*Math.cos(theta)), this.size/2 + (int)(radius*Math.sin(theta)));
        Stack<Point> cellFillStack = new Stack<>();
        cellFillStack.push(fromPt);
        while(!cellFillStack.isEmpty()) {
            Point cellPt = cellFillStack.pop();
            double fillValue = voronoiMap[cellPt.x][cellPt.y];
            fill(cellPt, pt -> {
                if(pt.x >= 0 && pt.x < this.size && pt.y >= 0 && pt.y < this.size)
                    if (!landMap[pt.x][pt.y]) {
                        if(voronoiMap[pt.x][pt.y] == fillValue) {
                            landMap[pt.x][pt.y] = true;
                            return true;
                        } else if(rand.nextDouble() < this.expansionChanceMultiplier / pt.distance(fromPt)) {
                            cellFillStack.push(pt);
                        }
                    }
                return false;
            });
        }
        
        //Fill in the sea.
        boolean[][] seaMap = new boolean[this.size][this.size];
        Predicate<Point> seaFillPredicate = pt -> {
            if(pt.x >= 0 && pt.x < this.size && pt.y >= 0 && pt.y < this.size)
                if(!seaMap[pt.x][pt.y] && !landMap[pt.x][pt.y]) {
                    seaMap[pt.x][pt.y] = true;
                    return true;
                }
            return false;
        };
        for(int x = 0; x < this.size; x++) {
            fill(new Point(x, 0), seaFillPredicate);
            fill(new Point(x, this.size - 1), seaFillPredicate);
        }
        for(int y = 0; y < this.size; y++) {
            fill(new Point(0, y), seaFillPredicate);
            fill(new Point(this.size - 1, y), seaFillPredicate);
        }
        
        //Fill in lakes that are too small.
        for(int x = 0; x < this.size; x++) {
            for(int y = 0; y < this.size; y++) {
                if(!seaMap[x][y] && !landMap[x][y]) {
                    List<Point> checked = new ArrayList<>();
                    fill(new Point(x, y), pt -> {
                        if(!seaMap[pt.x][pt.y] && !landMap[pt.x][pt.y] && !checked.contains(pt)) {
                            checked.add(pt);
                            return checked.size() < this.minimumLakeSize;
                        }
                        return false;
                    });
                    if(checked.size() < this.minimumLakeSize) 
                        for(Point pt : checked)
                            landMap[pt.x][pt.y] = true;
                }
            }
        }
        
        //Find coastal and lakeshore tiles
        List<Point> coastalTiles = new ArrayList<>();
        for(int x = 0; x < this.size; x++) {
            CONT: for(int y = 0; y < this.size; y++) {
                if(seaMap[x][y])
                    for(int dx = -1; dx <= 1; dx++) 
                        for(int dy = -1; dy <= 1; dy++) {
                            int rx = x + dx, ry = y + dy;
                            if(rx >= 0 && rx < this.size && ry >= 0 && ry < this.size)
                                if(dx != dy && landMap[rx][ry]) { 
                                    coastalTiles.add(new Point(x, y));
                                    continue CONT;
                                }
                        }
            }
        }
        
        //Save the output in the landMap, for now
        BufferedImage img = new BufferedImage(this.size, this.size, BufferedImage.TYPE_INT_RGB);
        for(int x = 0; x < this.size; x++)
            for(int y = 0; y < this.size; y++)
                img.setRGB(x, y, new Color(coastalTiles.contains(new Point(x, y)) ? 255 : 0, landMap[x][y] ? 255 : 0, seaMap[x][y] ? 255 : 0).getRGB());
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
