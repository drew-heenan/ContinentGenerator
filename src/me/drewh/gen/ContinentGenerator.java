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
        gen.setSize(1024);
        gen.setXScale(0.04);
        gen.setYScale(0.04);
        gen.setMinimumLakeSize(0);
        gen.setExpansionChanceMultiplier(6);
        gen.generate();
    }
    
    private int size = 1000, seed = 0;
    private int minimumLakeSize = 100;
    private double xScale = 0.01, yScale = 0.01;
    private double expansionChanceMultiplier = 1.0;
    
    private boolean[][] landMap, seaMap;
    private double[][] heightMap;
    private Random rand;
    private List<Point> coastalTiles, lakeShoreTiles;
    
    
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
    	this.rand = new Random(this.seed);
        this.landMap = createLandmass();
        this.seaMap = fillSea();
        removeSmallLakes();
        findShoreTiles();
        this.heightMap = calculateGeneralAltitude();
        
        //Save the output in the landMap, for now
        BufferedImage img = new BufferedImage(this.size, this.size, BufferedImage.TYPE_INT_RGB);
        for(int x = 0; x < this.size; x++)
            for(int y = 0; y < this.size; y++)
                img.setRGB(x, y, new Color(this.landMap[x][y] ? 100 : 0, (int)(255*heightMap[x][y]), 0).getRGB());
        try {
            ImageIO.write(img, "png", new File("out.png"));
        } catch (IOException ex) {
            Logger.getLogger(ContinentGenerator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

	private double[][] calculateGeneralAltitude() {
		//Calculate general altitude
        double[][] heightMap = new double[this.size][this.size];
        double greatestGeneralAltitude = 0, lowestGeneralAltitude = Double.MAX_VALUE;
        for(int x = 0; x < this.size; x++) {
        	for(int y = 0; y < this.size; y++) {
        		if(!this.seaMap[x][y]) {
        			double nearestLakeShore = Double.MAX_VALUE, nearestCoast = Double.MAX_VALUE;
        			for(Point coast : this.coastalTiles) {
        				double dist2 = coast.distanceSq(x, y);
        				if(dist2 < nearestCoast)
        					nearestCoast = dist2;
        			}
        			for(Point shore : this.lakeShoreTiles) {
        				double dist2 = shore.distanceSq(x, y);
        				if(dist2 < nearestLakeShore)
        					nearestLakeShore = dist2;
        			}
        			if(this.lakeShoreTiles.isEmpty() || nearestLakeShore == 0)
        				nearestLakeShore = -0.1;
        			nearestLakeShore = Math.pow(nearestLakeShore, 0.1);
        			if(!this.landMap[x][y])
        				nearestLakeShore = -10*nearestLakeShore;
        			double rawAltitude = nearestLakeShore + nearestCoast;
        			if(rawAltitude > greatestGeneralAltitude)
        				greatestGeneralAltitude = rawAltitude;
        			if(rawAltitude < lowestGeneralAltitude)
        				lowestGeneralAltitude = rawAltitude;
        			heightMap[x][y] = rawAltitude;
        		}
        	}
        }
        for(int x = 0; x < this.size; x++) {
        	for(int y = 0; y < this.size; y++) {
        		if(!this.seaMap[x][y])
        			heightMap[x][y] = (heightMap[x][y] - lowestGeneralAltitude) / (greatestGeneralAltitude - lowestGeneralAltitude);
        	}
        }
		return heightMap;
	}

	private void findShoreTiles() {
		//Find coastal and lakeshore tiles
		this.coastalTiles = new ArrayList<>();
        this.lakeShoreTiles = new ArrayList<>();
        for(int x = 0; x < this.size; x++) {
            CONT: for(int y = 0; y < this.size; y++) {
                if(!this.landMap[x][y])
                    for(int dx = -1; dx <= 1; dx++) 
                        for(int dy = -1; dy <= 1; dy++) {
                            int rx = x + dx, ry = y + dy;
                            if(rx >= 0 && rx < this.size && ry >= 0 && ry < this.size)
                                if(dx != dy && this.landMap[rx][ry]) {
                                	if(this.seaMap[x][y])
                                		this.coastalTiles.add(new Point(x, y));
                                	else
                                		this.lakeShoreTiles.add(new Point(x, y));
                                    continue CONT;
                                }
                        }
            }
        }
	}

	private void removeSmallLakes() {
		//Fill in lakes that are too small.
        for(int x = 0; x < this.size; x++) {
            for(int y = 0; y < this.size; y++) {
                if(!this.seaMap[x][y] && !this.landMap[x][y]) {
                    List<Point> checked = new ArrayList<>();
                    fill(new Point(x, y), (from, pt) -> {
                        if(!this.seaMap[pt.x][pt.y] && !this.landMap[pt.x][pt.y] && !checked.contains(pt)) {
                            checked.add(pt);
                            return checked.size() < this.minimumLakeSize;
                        }
                        return false;
                    });
                    if(checked.size() < this.minimumLakeSize) 
                        for(Point pt : checked)
                            this.landMap[pt.x][pt.y] = true;
                }
            }
        }
	}

	private boolean[][] fillSea() {
		//Fill in the sea.
        boolean[][] seaMap = new boolean[this.size][this.size];
        FillPredicate seaFillPredicate = (from, pt) -> {
            if(pt.x >= 0 && pt.x < this.size && pt.y >= 0 && pt.y < this.size)
                if(!seaMap[pt.x][pt.y] && !this.landMap[pt.x][pt.y]) {
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
		return seaMap;
	}

	private boolean[][] createLandmass() {
		double[][] voronoiMap = new double[this.size][this.size];
        Voronoi voronoiNoise = new Voronoi();
        voronoiNoise.setSeed(this.seed);
        for(int x = 0; x < this.size; x++) {
            for(int y = 0; y < this.size; y++) {
                voronoiMap[x][y] = voronoiNoise.getValue(this.xScale*x, this.yScale*y, 0);
            }
        }
                
        //Generate a landmass.
        boolean[][] landMap = new boolean[this.size][this.size];
        Point fromPt = new Point(this.size/2, this.size/2);//new Point(this.size/2 + (int)(radius*Math.cos(theta)), this.size/2 + (int)(radius*Math.sin(theta)));
        Stack<Point> cellFillStack = new Stack<>();
        cellFillStack.push(fromPt);
        while(!cellFillStack.isEmpty()) {
            Point cellPt = cellFillStack.pop();
            double fillValue = voronoiMap[cellPt.x][cellPt.y];
            fill(cellPt, (from, pt) -> {
                if(pt.x >= 0 && pt.x < this.size && pt.y >= 0 && pt.y < this.size)
                    if (!landMap[pt.x][pt.y]) {
                        if(voronoiMap[pt.x][pt.y] == fillValue) {
                            landMap[pt.x][pt.y] = true;
                            return true;
                        } else if(this.rand.nextDouble() < this.expansionChanceMultiplier / pt.distance(fromPt)) {
                            cellFillStack.push(pt);
                        }
                    }
                return false;
            });
        }
		return landMap;
	}
    
    public static void fill(Point startPoint, FillPredicate fillPredicate) {
        Stack<Point[]> fillStack = new Stack<>();
        fillStack.push(new Point[]{startPoint, startPoint});
        while(!fillStack.isEmpty()) {
            Point[] pts = fillStack.pop();
            if(!fillPredicate.shouldFill(pts[0], pts[1]))
                continue;
            for(int dx = -1; dx <= 1; dx++) {
                for(int dy = -1; dy <= 1; dy++) {
                    if(!(dx == 0 && dy == 0)) {
                        fillStack.push(new Point[]{pts[1], new Point(pts[1].x + dx, pts[1].y + dy)});
                    }
                }
            }
        }
    }
    
    public static interface FillPredicate {
    	
    	public boolean shouldFill(Point from, Point pt);
    	
    }

    
}
