/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sunarvrm;

import java.sql.Struct;
import java.util.Vector;
import javax.vecmath.Point3d;

/**
 *
 * @author chmelarp
 */
public class LineString {
    public Vector<Point3d> points;

    // this parses "LINESTRING(287 208 4849,286 209 4853,286 209 4857,...)"
    public LineString(String lineStr) throws Exception {
        points = new Vector<Point3d>();

        String pointSplitter = ",";
        String coordSplitter = " ";
        // this is a LINESTRING(...)
        if (lineStr.contains("LINESTRING(")) {
            lineStr = lineStr.substring(11, lineStr.length()-1);
        }
        // this is a path or else!
        else if(lineStr.charAt(0) == '[' || lineStr.charAt(0) == '{' || lineStr.charAt(0) == '{') {
            throw new Exception("LineString is a (8.8.4.) Path... Unimplemented!!!");

            // lineStr = lineStr.substring(1, lineStr.length()-1);
            // pointSplitter = "),(";
            // coordSplitter = ",";
        }
        else {
            throw new Exception("LineString was not recognized... Unimplemented!!!");
        }

        // split points
        String[] pointsStrs = lineStr.split(pointSplitter);
        for (int i = 0; i < pointsStrs.length; i++) {

            String[] coords = pointsStrs[i].split(coordSplitter);
            if (coords.length != 3) {
                throw new Exception("LineString has not 3 dimensions... Unimplemented!!!");
            }

            double x =  Double.parseDouble(coords[0]);
            double y =  Double.parseDouble(coords[1]);
            double z =  Double.parseDouble(coords[2]);

            points.add(new Point3d(x, y, z));
        }

    }


    /**
     * This is there to construct the triples from two separate arrays of frames and points
     * @param frameStr
     * @param positionStr
     */
    public LineString(String frameStr, String positionStr) throws Exception {
        points = new Vector<Point3d>();

        // assert frames
        if(frameStr.startsWith("{")) {
            frameStr = frameStr.substring(1, frameStr.length()-1);
        }
        else {
            throw new Exception("FrameString was not recognized... Unimplemented!!!");
        }

        // split frames
        String[] frameStrs = frameStr.split(",");

        // assert points
        if(positionStr.startsWith("{\"(")) {
            positionStr = positionStr.substring(3, positionStr.length()-3);
        }
        else {
            throw new Exception("PositionString was not recognized... Unimplemented!!!");
        }

        // split points
        String[] pointStrs = positionStr.split("\\)\\\",\\\"\\(");

        // assert lenghts
        if (frameStrs.length != pointStrs.length) {
            throw new Exception("Baybe, wrong counts... Unimplemented!!!");
        }

        // fill in the vector
        for (int i = 0; i < frameStrs.length; i++) {

            String[] coords = pointStrs[i].split(",");
            if (coords.length != 2) {
                throw new Exception("A point gotta have 2 dimensions, dude... Unimplemented!!!");
            }

            int x =  Integer.parseInt(coords[0]);
            int y =  Integer.parseInt(coords[1]);
            int f =  Integer.parseInt(frameStrs[i]);

            points.add(new Point3d(x, y, f));
        }
    }

}
