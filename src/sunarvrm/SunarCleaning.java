/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sunarvrm;

import jama.Matrix;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import jkalman.JKalman;


/**
 *
 * @author chmelarp
 */
public class SunarCleaning {
    
    // Zimmerman's constant to maintain the speed in integer numbers
    public static final int speedTurboBoost = 100;

    Commons commons;
    int dataset;

    public SunarCleaning(Commons commons, int dataset) {
        this.commons = commons;
        this.dataset = dataset;
    }

     /**
     * Deletes unwanted short tracks (including states)
     * @return sucess
     */
    public boolean deleteMessyTracks() {
        // TODO: blbuvzdorne zakomentovano!
/*
        String SQLquery;
        ResultSet rset;
        Statement stmt = null;

        try {
            stmt = commons.conn.createStatement();

            // smazni tracky, co nemaji video v DB (referencni integrita)
            SQLquery = "DELETE FROM ONLY sunar.tracks \n" +
                    "WHERE (dataset, camera, video) NOT IN (SELECT DISTINCT dataset, camera, video FROM ONLY sunar.videos) ";
            if (dataset > 0) {
                SQLquery += " AND dataset="+ dataset;
            }
            commons.logTime(SQLquery);
            stmt.executeUpdate(SQLquery);

            // smazni mrnousy, co by akorat posimraly Kalmanuv filtr
            SQLquery = "DELETE FROM ONLY sunar.tracks \n"+
                       " WHERE (lasts[1] - firsts[1]) < 20";
            if (dataset > 0) {
                SQLquery += " AND dataset="+ dataset;
            }
            commons.logTime(SQLquery);
            stmt.executeUpdate(SQLquery);

            // smazni ty, ktere nikam nejdou (motaji se kolem 20 px)
            SQLquery = "DELETE FROM ONLY sunar.tracks WHERE (dataset, video, camera, track) IN ( \n"
                    + "     select dataset, video, camera, track \n"
                    + "     -- , max(position[0]) maxx, max(position[1]) maxy, min(position[0]) minx, min(position[1]) miny\n"
                    + "     from ONLY sunar.states \n"
                    + "     group by dataset, video, camera, track \n"
                    + "     having max(position[0]) - min(position[0]) < 20 and \n"
                    + "            max(position[1]) - min(position[1]) < 15 \n) ";
            if (dataset > 0) {
                SQLquery += " AND dataset="+ dataset;
            }
            commons.logTime(SQLquery);
            stmt.executeUpdate(SQLquery);


            // smazni take jejich stavy (referencni integrita v praxi :)
            SQLquery = "DELETE FROM ONLY sunar.states \n" +
                    " WHERE (dataset, camera, video, track) NOT IN (SELECT DISTINCT dataset, camera, video, track FROM ONLY sunar.tracks) ";
            if (dataset > 0) {
                SQLquery += " AND dataset="+ dataset;
            }
            commons.logTime(SQLquery);
            stmt.executeUpdate(SQLquery);

            stmt.close();

        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            JOptionPane.showMessageDialog(commons.owner, "SunarCleaning.deleteMessyTracks:\n"+ e, "SQL error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
 */
        return true;
    }


    /**
     * Update video and track offsets
     * @return sucess
     */
    public boolean updateOffsets() {

        String SQLquery;
        ResultSet rset;
        Statement stmt = null;

        try {
            stmt = commons.conn.createStatement();

            SQLquery = "UPDATE ONLY sunar.videos SET \"offset\"=sunar.video_offset(dataset, camera, video) ";
            if (dataset > 0) {
                SQLquery += " WHERE dataset="+ dataset;
            }
            // System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);

            SQLquery = "UPDATE ONLY sunar.tracks SET \"offset\"=sunar.video_offset(dataset, camera, video) ";
            if (dataset > 0) {
                SQLquery += " WHERE dataset="+ dataset;
            }
            // System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);

            SQLquery = "UPDATE ONLY sunar.annotation_tracks SET \"offset\"=sunar.video_offset(dataset, camera, video) ";
            if (dataset > 0) {
                SQLquery += " WHERE dataset="+ dataset;
            }
            // System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);

            SQLquery = "UPDATE ONLY sunar.evaluation_tracks SET \"offset\"=sunar.video_offset(dataset, camera, video) ";
            if (dataset > 0) {
                SQLquery += " WHERE dataset="+ dataset;
            }
            // System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);


            stmt.close();

        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            JOptionPane.showMessageDialog(commons.owner, "SunarCleaning.updateOffsets:\n"+ e, "SQL error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }


    /**
     * Updates the summarized statistics vector...
     * @return success
     */
    public boolean countStatistics() {
    try {
        // this should be done by sequences because of the ammount of data...
        Statement seqStmt = commons.conn.createStatement();
        String seqQuery = "SELECT seqname "
                           + " FROM "+ commons.dataset +".sequences\n"
                           // FIXME: tohle neni idealni zpusob rozlisovani datasetu :(
                           + " WHERE seqname LIKE '%LGW_2007%'\n" //  '%MMMMMMMMMMMCCCCCCCCCCCCCCCCCTTTTTTTTTTTTTTTRRRRRRRRR%'\n"
                           + " ORDER BY seqname";
        ResultSet seqRs = seqStmt.executeQuery(seqQuery);

        while (seqRs.next()){
            final String seqname = seqRs.getString("seqname");

            Statement tracksStmt = commons.conn.createStatement();
            Statement tracksUpdateStmt = commons.conn.createStatement();
            Statement statesStmt = commons.conn.createStatement();

            String tracksQuery = "SELECT seqname, t1, t2, track, frames, positions, sizes, colors, shapes\n" //  ST_AsEWKT(locations) AS loc\n"
                               + " FROM ONLY "+ commons.dataset +".tracks AS t\n"
                               + " WHERE seqname = '"+ seqname +"'\n"
            // FIXME: tohle je jen pro urychleni trenovani!!!
                               + " AND track in (SELECT track FROM ONLY "+ commons.dataset +".annotation_tracks AS a WHERE t.seqname=a.seqname)\n"
                               + " ORDER BY t1, t2;\n";
            // if (dataset > 0) where

            // if DEBUGGING...
            // tracksQuery += " LIMIT 3";
            commons.log(tracksQuery);
            ResultSet tracksRs = tracksStmt.executeQuery(tracksQuery);

            //
            // go through tracks (27 000 @ dataset 2 in 49:23 (minutes) :)
            //
            while (tracksRs.next()) {
                final int trT1 = tracksRs.getInt("t1");
                final int trT2 = tracksRs.getInt("t2");
                final int trTrack = tracksRs.getInt("track");
                String frameStr = tracksRs.getString("frames");
                String locationStr = tracksRs.getString("positions");
                String sizeStr = tracksRs.getString("sizes");
                String colorStr = tracksRs.getString("colors");
                String shapeStr = tracksRs.getString("shapes");

                // DEBUG
                commons.log("S:"+ seqname +" T1:"+ trT1 +" T2:"+ trT2 +" ... ");

                // parse locations
                LineString locations = new LineString(frameStr, locationStr);

                // there must be commented out in the regexes: <([{\^-=$!|]})?*+.>

                // {"(123,373)","(120,308)","(120,354)","(116,356)","(113,368)","(110,333)","(110,328)","(110,335)","(111,338)","(111,335)","(112,335)","(112,334)","(114,333)","(114,332)","(115,332)","(117,330)","(128,333)","(139,336)","(150,339)","(154,343)","(157,346)","(159,348)","(160,348)","(160,347)","(161,347)","(161,340)","(161,341)","(159,328)","(160,325)","(157,312)","(156,310)","(154,302)","(151,291)","(146,280)","(142,270)","(136,260)","(131,248)"}
                if (sizeStr.startsWith("{\"(")) {
                    sizeStr = sizeStr.substring(3, sizeStr.length()-3);
                }
                else commons.error(sizeStr, "sizeStrs are bad...");
                String[]   sizes = sizeStr.split("\\)\\\",\\\"\\(");

                // "{{102,138,135,139,1...,134},...,{125,15,...}}"
                if (colorStr.startsWith("{{")) {
                    colorStr = colorStr.substring(2, colorStr.length()-2);
                }
                else commons.error(colorStr, "colorStrs are bad...");
                String[] colors = colorStr.split("\\},\\{");

                // "{{102,138,135,139,1...,134},...,{125,15,...}}"
                if (shapeStr.startsWith("{{")) {
                    shapeStr = shapeStr.substring(2, shapeStr.length()-2);
                }
                else commons.error(shapeStr, "shapeStrs are bad...");
                String[] shapes = shapeStr.split("\\},\\{");


                // Zbyva porovnat delky
                if (locations.points.size() != sizes.length || sizes.length != colors.length || sizes.length != shapes.length) {
                    commons.error(tracksRs, "There are wrong numbers of elements... :(");
                }




                // prepare first and last (DESC!)
                int firstT = 0, firstX = 0, firstY = 0, firstW = 0, firstH = 0;   // last state is got at the BEGINNING of the process below!
                int lastT = 0, lastX = 0, lastY = 0, it = 0, ix = 0, iy = 0;      // this is here to be able to invert :)
                int t = 0, x = 0, y = 0, w = 0, h = 0;   // FIRST state is got at the END of the process below!

                // prepare summ/square summ matrices and other stats
                Double posX = 0.0, posY = 0.0;
                Double posXsd = 0.0, posYsd = 0.0;
                Double sizeW = 0.0, sizeH = 0.0;
                Double sizeWsd = 0.0, sizeHsd = 0.0;
                // speed
                Double speedV = 0.0, speedX = 0.0, speedY = 0.0;  // counted from the previous (Kalman) state/position
                Double speedVsd = 0.0, speedXsd = 0.0, speedYsd = 0.0;  // counted from the previous... poznam opilce???
                Double distXY = 0.0, distX = 0.0, distY = 0.0;   // to have it and verify the Kalman speed above
                // int duration12 = ... below to become final
                // color
                Matrix colorMat = null;
                Matrix colorSdMat = null;
                Matrix shapeMat = null;
                Matrix shapeSdMat = null;

                // prepare Kalman filter && inverted Kalman filter
                JKalman kalman;
                JKalman ikalman;
                try {
                    kalman = new JKalman(4, 2);
                    ikalman = new JKalman(4, 2);
                } catch (Exception ex) {
                    Logger.getLogger(SunarCleaning.class.getName()).log(Level.SEVERE, null, ex);
                    continue;
                }

                // transitions for x, y, dx, dy
                double[][] tr = { {1, 0, 1, 0},
                                  {0, 1, 0, 1},
                                  {0, 0, 1, 0},
                                  {0, 0, 0, 1} };
                // Matrix s = new Matrix(4, 1); // state [x, y, dx, dy, dxy]
                // Matrix c = new Matrix(4, 1); // corrected state [x, y, dx, dy, dxy]

                // init forward Kalman
                kalman.setTransition_matrix(new Matrix(tr));
                Matrix m = new Matrix(2, 1); // measurement [x]
                Matrix state_post = null;    // corrected measurement after the correct is called

                // init inverse Kalman
                ikalman.setTransition_matrix(new Matrix(tr));
                Matrix im = new Matrix(2, 1); // measurement [x]
                Matrix istate_post = null;    // corrected measurement after the correct is called

                /* Counting mean and the standard_deviation...
                double std_dev2(double a[], int n) {
                    if(n == 0)
                        return 0.0;
                    double sum = 0;
                    double sq_sum = 0;
                    for(int i = 0; i < n; ++i) {
                       sum += a[i];
                       sq_sum += a[i] * a[i];
                    }
                    double mean = sum / n;
                    double variance = sq_sum / n - mean * mean;
                    return sqrt(variance);
                }
                */

                /* count statistics
                String statesQuery = "SELECT \"time\", position[0]::int, position[1]::int, size[0]::int, size[1]::int, color\n" +
                    " FROM ONLY sunar.states\n" +
                    " WHERE dataset="+ trDataset +" AND video="+ trVideo +" AND camera="+ trCamera +" AND track="+ trTrack +"\n" +
                    " ORDER BY time -- DESC";
                // System.out.println(statesQuery);
                ResultSet statesRs = statesStmt.executeQuery(statesQuery);
                */

                // toz, ted si poresim prvni hodnoty
                firstT = (int) Math.round(locations.points.get(0).z);
                firstX = (int) Math.round(locations.points.get(0).x);
                firstY = (int) Math.round(locations.points.get(0).y);
                if (firstT != trT1) {
                    commons.log("FAILED! the firstT time is wrong\n");
                    // TODO: what to do?
                }

                lastT = (int) Math.round(locations.points.get(locations.points.size()-1).z);
                lastX = (int) Math.round(locations.points.get(locations.points.size()-1).x);
                lastY = (int) Math.round(locations.points.get(locations.points.size()-1).y);
                if (lastT != trT2) {
                    commons.log("FAILED? the lastT time is wrong\n");
                    // TODO: what to do?
                }

                // sizes
                String[] wh = sizes[0].split(",");
                firstW = (int) Math.round(Double.parseDouble(wh[0]));
                firstH = (int) Math.round(Double.parseDouble(wh[1]));


                // duration at least one half of second
                final int duration12 = (int) Math.round(1.0 + locations.points.get(locations.points.size()-1).z - locations.points.get(0).z);

                // TOZ, tady asi vazne nemas co delat... vykasli se na to
                // Kalman totiz s bidou startuje u 3. bodu... a za 3/4 sekundy (19f) tezko nekdo neco zmerci...
                if (sizes.length < 5 || duration12 < 10) {
                    commons.log("FAILED! (the duration is too short)\n");
                    continue;       // there is nothing more to be done there
                }



                // init the stats
                posX = (double)firstX;
                posY = (double)firstY;
                posXsd = (double)firstX*firstX;
                posYsd = (double)firstY*firstY;
                sizeW = (double)firstW;
                sizeH = (double)firstH;
                sizeWsd = (double)firstW*firstW;
                sizeHsd = (double)firstH*firstH;

                // init the color matrixes
                colorMat = new Matrix(colors[0]);
                colorSdMat = colorMat.arrayTimes(colorMat);
                shapeMat = new Matrix(shapes[0]);
                shapeSdMat = shapeMat.arrayTimes(shapeMat);

                // FIXME: tady byla po cela dlouha leta chyba... x=y=0!!!!
                // init the first Kalman state
                m.set(0, 0, firstX);
                m.set(1, 0, firstY);
                // init the last Kalman state
                im.set(0, 0, lastX);
                im.set(1, 0, lastY);


                //
                // go through tracks's states (2 059 895 @ dataset 2 in 49:23 (minutes) :-)
                // toz tohle je NENI Inverted Kalman state v praxi
                // TODO: takze bych to asi mel otocit jeste raz... NJN
                for (int i=1; i < sizes.length; i++) {
                    // toz, ted si poresim secky hodnoty
                    t = (int) Math.round(locations.points.get(i).z);
                    x = (int) Math.round(locations.points.get(i).x);
                    y = (int) Math.round(locations.points.get(i).y);

                    // vobracene
                    it = (int) Math.round(locations.points.get(locations.points.size()-1-i).z);
                    ix = (int) Math.round(locations.points.get(locations.points.size()-1-i).x);
                    iy = (int) Math.round(locations.points.get(locations.points.size()-1-i).y);

                    // sizes
                    wh = sizes[i].split(",");
                    w = (int) Math.round(Double.parseDouble(wh[0]));
                    h = (int) Math.round(Double.parseDouble(wh[1]));

                    // stats
                    posX += (double)x;
                    posY += (double)y;
                    posXsd += (double)x*x;
                    posYsd += (double)y*y;
                    sizeW += (double)w;
                    sizeH += (double)h;
                    sizeWsd += (double)w*w;
                    sizeHsd += (double)h*h;
                    // distance
                    distXY += Math.sqrt((m.get(0, 0) - x)*(m.get(0, 0) - x) + (m.get(1, 0) - y)*(m.get(1, 0) - y));
                    distX += Math.abs(m.get(0, 0) - x);
                    distY += Math.abs(m.get(1, 0) - y);

                    // color
                    Matrix clrMat = new Matrix(colors[i]);
                    colorMat = colorMat.plus(clrMat);
                    colorSdMat = colorSdMat.plus(clrMat.arrayTimes(clrMat));
                    Matrix shpMat = new Matrix(shapes[i]);
                    shapeMat = shapeMat.plus(shpMat);
                    shapeSdMat = shapeSdMat.plus(shpMat.arrayTimes(shpMat));

                    // predict and update the Kalman filter state
                    kalman.Predict();
                    m.set(0, 0, x);
                    m.set(1, 0, y);
                    state_post = kalman.Correct(m);

                    ikalman.Predict();
                    im.set(0, 0, ix);
                    im.set(1, 0, iy);
                    istate_post = ikalman.Correct(im);

                    // speed
                    speedX += state_post.get(2, 0);
                    speedY += state_post.get(3, 0);
                    speedV += Math.sqrt(state_post.get(2, 0)*state_post.get(2, 0) + state_post.get(3, 0)*state_post.get(3, 0));
                    speedXsd += state_post.get(2, 0)*state_post.get(2, 0);
                    speedYsd += state_post.get(3, 0)*state_post.get(3, 0);
                    speedVsd += (state_post.get(2, 0)*state_post.get(2, 0) + state_post.get(3, 0)*state_post.get(3, 0));

                } // through states



                // Finally, count the stats (of 1 track)
                posX /= sizes.length;
                posY /= sizes.length;
                posXsd = Math.sqrt(posXsd/sizes.length - posX*posX);
                posYsd = Math.sqrt(posYsd/sizes.length - posY*posY);
                sizeW /= sizes.length;
                sizeH /= sizes.length;
                sizeWsd = Math.sqrt(sizeWsd/sizes.length - sizeW*sizeW);
                sizeHsd = Math.sqrt(sizeHsd/sizes.length - sizeH*sizeH);
                // speed
                speedX /= sizes.length;
                speedY /= sizes.length;
                speedV /= sizes.length;
                speedXsd = Math.sqrt(speedXsd/sizes.length - speedX*speedX);
                speedYsd = Math.sqrt(speedYsd/sizes.length - speedY*speedY);
                speedVsd = Math.sqrt(speedVsd/sizes.length - speedV*speedV);

                Double speedVLast = Math.sqrt(state_post.get(2, 0)*state_post.get(2, 0) + state_post.get(3, 0)*state_post.get(3, 0));
                Double speedVFirst = Math.sqrt(istate_post.get(2, 0)*istate_post.get(2, 0) + istate_post.get(3, 0)*istate_post.get(3, 0));
                //  distXY, duration12 is a SUM... OK

                // count the color avg and stdev
                colorMat = colorMat.times(1.0/sizes.length);
                colorSdMat = colorSdMat.times(1.0/sizes.length);
                colorSdMat = colorSdMat.minus(colorMat.arrayTimes(colorMat));
                colorSdMat = colorSdMat.arraySqrt();
                shapeMat = shapeMat.times(1.0/sizes.length);
                shapeSdMat = shapeSdMat.times(1.0/sizes.length);
                shapeSdMat = shapeSdMat.minus(shapeMat.arrayTimes(shapeMat));
                shapeSdMat = shapeSdMat.arraySqrt();

                // Tohle je DE-FACTO dokumentace k update track ... neco malo je v jeste v queries.sql
                // state_post.get(1-3, 0); * 100!
                // TODO: PREDELAT podle toho, jak to bylo... ted je to udelana pouze pro evaluaci... inicializace & nasledujici!
                String updateQuery = "UPDATE ONLY "+ commons.dataset +".tracks SET \n"+
                    " firsts  = ARRAY["+ firstT +", "+ firstX +", "+ firstY +", "+ firstW +", "+ firstH + ", " + Math.round(istate_post.get(2, 0)*-speedTurboBoost) +", "+ Math.round(istate_post.get(3, 0)*-speedTurboBoost) +", "+ Math.round(speedVFirst*speedTurboBoost) +"],\n"+
                    " lasts = ARRAY["+ t +", "+ Math.round(state_post.get(0, 0)) +", "+ Math.round(state_post.get(1, 0)) +", "+ w +", "+ h +", "+ Math.round(state_post.get(2, 0)*speedTurboBoost) +", "+ Math.round(state_post.get(3, 0)*speedTurboBoost) +", "+ Math.round(speedVLast*speedTurboBoost) +"],\n"+
                    " avgs   = ARRAY["+ (t + firstT)/2 +", "+ Math.round(posX) +", "+ Math.round(posY) +", "+ Math.round(sizeW) +", "+ Math.round(sizeH) +", "+ Math.round(speedX*speedTurboBoost) +", "+ Math.round(speedY*speedTurboBoost) +", "+ Math.round(speedV*speedTurboBoost) +"],\n"+
                    " stdevs = ARRAY["+ 1 +", "+ Math.round(posXsd) +", "+ Math.round(posYsd) +", "+ Math.round(sizeWsd) +", "+ Math.round(sizeHsd) +", "+ Math.round(speedXsd*speedTurboBoost) +", "+ Math.round(speedYsd*speedTurboBoost) +", "+ Math.round(speedVsd*speedTurboBoost) +"],\n"+
                    " sums   = ARRAY["+ (t - firstT) +", "+ Math.round(distX) +", "+ Math.round(distY) +", "+ Math.round(distXY) +"], \n"+
                    " avgcolor = ARRAY["+ colorMat.toString(4,0) +"],\n"+
                    " stdcolor = ARRAY["+ colorSdMat.toString(4,0) +"],\n"+
                    " avgshape = ARRAY["+ shapeMat.toString(4,0) +"],\n"+
                    " stdshape = ARRAY["+ shapeSdMat.toString(4,0) +"]\n"+
                    "WHERE seqname='"+ seqname +"' AND t1="+ trT1 +" AND t2="+ trT2 +" AND track="+ trTrack;
                // commons.logTime(updateQuery);
                tracksUpdateStmt.executeUpdate(updateQuery);

                // DEBUG
                commons.log(" OK\n");

            } // go through tracks

            // exit nicely
            statesStmt.close();
            tracksStmt.close();
            tracksUpdateStmt.close();

            commons.logTime("All DONE.\n");
        }

        // TODO: BEVARE OF THIS FUNCTION!
        // this.deleteAfterStats();

    } catch (SQLException e) {
        commons.error(this, e.getMessage());
        Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
        JOptionPane.showMessageDialog(commons.owner, "SunarCleaning.countStatistics:\n"+ e, "SQL error", JOptionPane.ERROR_MESSAGE);
        return false;
    } catch (Exception e) { // for sure... log
        commons.error(this, e.getMessage());
        Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
        JOptionPane.showMessageDialog(commons.owner, "SunarCleaning.countStatistics:\n"+ e, "An error", JOptionPane.ERROR_MESSAGE);
        return false;
    }


        return true;
    }


    /**
     * TODO: BEVARE OF THIS FUNCTION! May delete also wanted states (not only those without stats)!
     * @return sucess
     */
    public boolean deleteAfterStats() {
/*
        commons.logTime("WARNING! YOU SHOULD HAVE MADE A BACKUP OF THE DATABASE!\n");

        String SQLquery;
        ResultSet rset;
        Statement stmt = null;

        try {
            stmt = commons.conn.createStatement();

            // smazni tracky, co nemaji video v DB (referencni integrita)
            SQLquery = "DELETE FROM ONLY sunar.tracks WHERE avgs IS NULL ";
            if (dataset > 0) {
                SQLquery += " AND dataset="+ dataset;
            }
            commons.logTime(SQLquery);
            stmt.executeUpdate(SQLquery);

            // smazni take jejich stavy (referencni integrita v praxi :) 
            SQLquery = "DELETE FROM ONLY "+ commons.dataset +".states WHERE (dataset, camera, video, track) NOT IN (SELECT DISTINCT dataset, camera, video, track FROM ONLY sunar.tracks) ";
            if (dataset > 0) {
                SQLquery += " AND dataset="+ dataset;
            }
            commons.logTime(SQLquery);
            stmt.executeUpdate(SQLquery);

            stmt.close();

        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            JOptionPane.showMessageDialog(commons.owner, "SunarCleaning.deleteAfterStats:\n"+ e, "SQL error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
*/
        return true;
    }

}
