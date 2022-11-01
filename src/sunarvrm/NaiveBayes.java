
package sunarvrm;

import jama.Matrix;
import java.security.acl.Owner;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Naive Bayes classification (model)
 * Wildcat (Hibernate) of the sunar.training_bayes
 *
   TABLE sunar.training_bayes (
      camera1 integer NOT NULL,
      camera2 integer NOT NULL,
      delta_t_avg integer,
      delta_t_std integer,
      firsts1_avg integer[],
      firsts2_avg integer[],
      firsts1_std integer[],
      firsts2_std integer[],
      lasts1_avg integer[],
      lasts2_avg integer[],
      lasts1_std integer[],
      lasts2_std integer[],
      avgs1_avg integer[],
      avgs2_avg integer[],
      avgs1_std integer[],
      avgs2_std integer[],
      sums1_avg integer[],
      sums2_avg integer[],
      sums1_std integer[],
      sums2_std integer[],
      delta_color_avg integer[],
      delta_color_std integer[],
      delta_color_dist_avg integer,
      delta_color_dist_std integer,
      cnt integer )
 *
 * @author petr
 */
public class NaiveBayes {

    /**
     * Je seznam seznamu, kamer... pozice (+1) je cislo kamery, zopakne se hned v [0] elementu pokazde
    */
    List<List> model;

    Commons commons;
    SunarIntegration owner;

    public NaiveBayes(Commons commons, SunarIntegration owner) {
        this.commons = commons;
        this.owner = owner;
        model = new ArrayList<List>();

        this.loadModel();
    }

    // loads the model...
    public boolean loadModel() {

        try {
            Statement stmtCameras = commons.conn.createStatement();
            Statement stmtHandovers = commons.conn.createStatement();

            //
            // FIXME: if any changes in the database ... run this caching:
            // SELECT * INTO sunar.training_handovers_cache FROM ONLY sunar.training_handovers
            //
            String videosQuery = "SELECT * \n" +
                    " FROM ONLY sunar.training_cameras \n" +
                    " ORDER BY camera1";
            ResultSet camerasRs = stmtCameras.executeQuery(videosQuery);

            // load the camera list
            int cameraCnt = 0;
            List<Integer> cam = new ArrayList<Integer>();

            // go through videos...
            while (camerasRs.next()) {
                cameraCnt++;
                final int camera1 = camerasRs.getInt("camera1");
                cam.add(camera1);
            }


            // go through the model in DB
            for (Integer cam1 : cam) {
                List<NBvector> nbVectorList = new ArrayList<NBvector>();

                for (Integer cam2 : cam) {

                    // perform selectioms...
                    String handowersQuery = "SELECT * \n" +
                            " FROM ONLY sunar.training_bayes\n" +
                            " WHERE camera1="+ cam1 +" AND camera2=" + cam2 +
                            " LIMIT 1";
                    ResultSet handoversRs = stmtHandovers.executeQuery(handowersQuery);

                    // toz, jeden by tady mal byt...
                    if (handoversRs.next()) {
                        NBvector nbVector = new NBvector(handoversRs); // tuna to mozna zhuci...
                        // commons.logTime(nbVector.toString());
                        nbVectorList.add(nbVector);
                    }
                } // 4 cam2

                // vraz ho tam...
                this.model.add(nbVectorList);

            } // 4 cam1

        } catch (SQLException ex) {
            commons.error(this, ex.getMessage());
            Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
        }

        // tedkon je v model 5 seznamu kamer
        return true;
    }

    // optimization
    public final static double sq2pi = Math.sqrt(Math.PI*2.0);
    /**
     * Counts normal distribution value (likelihood)
     * @param value
     * @param mean
     * @param stdev
     * @return
     */
    public static double normal(double value, double mean, double stdev) {
        // p(x) = ( 1 / (sqrt(2*pi)*stdev) ) * exp( (- (x-mean)^2) / (2 * stdev^2) )
        if (stdev == 0) return 0;
        return ( 1/(NaiveBayes.sq2pi*stdev) ) * Math.exp( (- (value-mean)*(value-mean)) / (2 * stdev*stdev) );
    }


    /**
     * Create classifiers models for:
     *   1. (Next) camera classification using (not inverted) Kalman states
     *      (averages1, firsts1 of sunar.annotation_handovers) and -> camera (1 label) (trainCameraFName+".train")
     *   2. (Next) track classifications using Kalman states, cameras, delta_t, delta_color -> track probability
     *      (trainTrackFName+".train")
     * @return sucess
     */
    public boolean trainBayes() {

        // fill in the train table (and files)
        try {

            Statement stmtCameras = commons.conn.createStatement();
            Statement stmtHandovers = commons.conn.createStatement();
            Statement stmtUpdate = commons.conn.createStatement();

            // TRUNCATE TABLE sunar.training_bayes
            String truncateQuery = "TRUNCATE TABLE sunar.training_bayes";
            System.out.println(truncateQuery);
            stmtUpdate.executeUpdate(truncateQuery);

            //
            // FIXME: if any changes in the database ... run this caching:
            // SELECT * INTO sunar.training_handovers_cache FROM ONLY sunar.training_handovers
            //
            String videosQuery = "SELECT * \n" +
                    "  FROM ONLY sunar.training_cameras \n" +
                    " ORDER BY camera1";
            ResultSet camerasRs = stmtCameras.executeQuery(videosQuery);

            // load the camera list
            List<Integer[]> cameras= new ArrayList<Integer[]>();
            // go through videos...
            while (camerasRs.next()) {
                final int camera1 = camerasRs.getInt("camera1");
                final int handoversCount = camerasRs.getInt(2); // just FYI

                Integer[] cam1 = {camera1, handoversCount};
                cameras.add(cam1);
            }

            // go through cameras...
            for (Integer[] cam1 : cameras) {
                int camera1 = cam1[0];
                commons.logTime("trainging camera "+ camera1);

                for (Integer[] cam2 : cameras) {
                    int camera2 = cam2[0];

                    // perform selectioms...
                    String handowersQuery = "SELECT * \n" +
                            " FROM ONLY sunar.training_handovers_cache\n" +
                            " WHERE camera1="+ camera1 +" AND camera2="+ camera2 +" -- ORDER BY";
                    // System.out.println(tracksQuery);
                    ResultSet handoversRs = stmtHandovers.executeQuery(handowersQuery);

                    // prepare summ/square summ matrices and other stats
                    int hoverCount = 0;
                    int deltaT = 0, deltaTstd = 0;
                    Matrix firsts1 = null, firsts1std = null;
                    Matrix firsts2 = null, firsts2std = null;
                    Matrix lasts1 = null, lasts1std = null;
                    Matrix lasts2 = null, lasts2std = null;
                    Matrix avgs1 = null, avgs1std = null;
                    Matrix avgs2 = null, avgs2std = null;
                    Matrix sums1 = null, sums1std = null;
                    Matrix sums2 = null, sums2std = null;
                    Matrix deltaColors = null, deltaColorsStd = null;
                    double colorDist = 0.0, colorDistStd = 0.0;

                    // process the first one
                    if (handoversRs.next()) {
                        hoverCount++;   // 1
                        // avgs
                        deltaT = handoversRs.getInt("delta_t");
                        avgs1 = new Matrix(handoversRs.getString("avgs1"));
                        avgs2 = new Matrix(handoversRs.getString("avgs2"));
                        firsts1 = new Matrix(handoversRs.getString("firsts1"));
                        firsts2 = new Matrix(handoversRs.getString("firsts2"));
                        lasts1 = new Matrix(handoversRs.getString("lasts1"));
                        lasts2 = new Matrix(handoversRs.getString("lasts2"));
                        sums1 = new Matrix(handoversRs.getString("sums1"));
                        sums2 = new Matrix(handoversRs.getString("sums2"));
                        deltaColors = new Matrix(handoversRs.getString("delta_color"));
                        colorDist = handoversRs.getInt("delta_color_dist");
                        // stds
                        deltaTstd = handoversRs.getInt("delta_t")*handoversRs.getInt("delta_t");
                        avgs1std = new Matrix(handoversRs.getString("avgs1")).arrayTimes(new Matrix(handoversRs.getString("avgs1")));
                        avgs2std = new Matrix(handoversRs.getString("avgs2")).arrayTimes(new Matrix(handoversRs.getString("avgs2")));
                        firsts1std = new Matrix(handoversRs.getString("firsts1")).arrayTimes(new Matrix(handoversRs.getString("firsts1")));
                        firsts2std = new Matrix(handoversRs.getString("firsts2")).arrayTimes(new Matrix(handoversRs.getString("firsts2")));
                        lasts1std = new Matrix(handoversRs.getString("lasts1")).arrayTimes(new Matrix(handoversRs.getString("lasts1")));
                        lasts2std = new Matrix(handoversRs.getString("lasts2")).arrayTimes(new Matrix(handoversRs.getString("lasts2")));
                        sums1std = new Matrix(handoversRs.getString("sums1")).arrayTimes(new Matrix(handoversRs.getString("sums1")));
                        sums2std = new Matrix(handoversRs.getString("sums2")).arrayTimes(new Matrix(handoversRs.getString("sums2")));
                        deltaColorsStd = new Matrix(handoversRs.getString("delta_color")).arrayTimes(new Matrix(handoversRs.getString("delta_color")));
                        colorDistStd = handoversRs.getInt("delta_color_dist")*handoversRs.getInt("delta_color_dist");
                    }

                    // go through rest handovers...
                    while (handoversRs.next()) {
                        hoverCount++;

                        deltaT += handoversRs.getInt("delta_t");
                        Matrix avg1 = new Matrix(handoversRs.getString("avgs1"));
                        avgs1 = avgs1.plus(avg1);
                        Matrix avg2 = new Matrix(handoversRs.getString("avgs2"));
                        avgs2 = avgs2.plus(avg2);
                        Matrix first1 = new Matrix(handoversRs.getString("firsts1"));
                        firsts1 = firsts1.plus(first1);
                        Matrix first2 = new Matrix(handoversRs.getString("firsts2"));
                        firsts2 = firsts2.plus(first2);
                        Matrix last1 = new Matrix(handoversRs.getString("lasts1"));
                        lasts1 = lasts1.plus(last1);
                        Matrix last2 = new Matrix(handoversRs.getString("lasts2"));
                        lasts2 = lasts2.plus(last2);
                        Matrix sum1 = new Matrix(handoversRs.getString("sums1"));
                        sums1 = sums1.plus(sum1);
                        Matrix sum2 = new Matrix(handoversRs.getString("sums2"));
                        sums2 = sums2.plus(sum2);
                        Matrix deltaColor = new Matrix(handoversRs.getString("delta_color"));
                        deltaColors = deltaColors.plus(deltaColor);
                        colorDist += handoversRs.getInt("delta_color_dist");

                        // stds
                        deltaTstd += handoversRs.getInt("delta_t")*handoversRs.getInt("delta_t");
                        avgs1std = avgs1std.plus(avg1.arrayTimes(avg1));
                        avgs2std = avgs2std.plus(avg2.arrayTimes(avg2));
                        firsts1std = firsts1std.plus(first1.arrayTimes(first1));
                        firsts2std = firsts2std.plus(first2.arrayTimes(first2));
                        lasts1std = lasts1std.plus(last1.arrayTimes(last1));
                        lasts2std = lasts2std.plus(last2.arrayTimes(last2));
                        sums1std = sums1std.plus(sum1.arrayTimes(sum1));
                        sums2std = sums2std.plus(sum2.arrayTimes(sum2));
                        deltaColorsStd = deltaColorsStd.plus(deltaColor.arrayTimes(deltaColor));
                        colorDistStd += handoversRs.getInt("delta_color_dist")*handoversRs.getInt("delta_color_dist");

                    } // 4 camera2


                    if(hoverCount < 2) {
                        // there is nothing to do here... 1 sample is not a statistically interesting
                        String updateStr = "INSERT INTO sunar.training_bayes(camera1, camera2)\n" +
                                " VALUES ("+ camera1 +", "+ camera2 +")";
                        try {
                            stmtUpdate.executeUpdate(updateStr);
                        } catch (SQLException e) {
                            commons.error(this, e.getMessage());
                            // nothing...
                        }
                    }
                    else { // more than 1 sample - update
                        deltaT /= hoverCount;
                        // avgs
                        avgs1 = avgs1.times(1.0/hoverCount);
                        avgs1.set(0, 0, 0);
                        avgs2 = avgs2.times(1.0/hoverCount);
                        avgs2.set(0, 0, 0);
                        firsts1 = firsts1.times(1.0/hoverCount);
                        firsts1.set(0, 0, 0);
                        firsts2 = firsts2.times(1.0/hoverCount);
                        firsts2.set(0, 0, 0);
                        lasts1 = lasts1.times(1.0/hoverCount);
                        lasts1.set(0, 0, 0);
                        lasts2 = lasts2.times(1.0/hoverCount);
                        lasts2.set(0, 0, 0);
                        sums1 = sums1.times(1.0/hoverCount);
                        // sums[0]... je OK!
                        sums2 = sums2.times(1.0/hoverCount);
                        deltaColors = deltaColors.times(1.0/hoverCount);
                        colorDist /= hoverCount;

                        // stds - cannot be zeros but ones!
                        deltaTstd = (int)Math.round(Math.sqrt(deltaTstd/hoverCount - deltaT*deltaT));
                        avgs1std = avgs1std.times(1.0/hoverCount);
                        avgs1std = avgs1std.minus(avgs1.arrayTimes(avgs1));
                        avgs1std = avgs1std.arraySqrt().arrayPlus(0.5);
                        avgs1std.set(0, 0, 1);
                        avgs2std = avgs2std.times(1.0/hoverCount);
                        avgs2std = avgs2std.minus(avgs2.arrayTimes(avgs2));
                        avgs2std = avgs2std.arraySqrt().arrayPlus(0.5);
                        avgs2std.set(0, 0, 1);
                        firsts1std = firsts1std.times(1.0/hoverCount);
                        firsts1std = firsts1std.minus(firsts1.arrayTimes(firsts1));
                        firsts1std = firsts1std.arraySqrt().arrayPlus(0.5);
                        firsts1std.set(0, 0, 1);
                        firsts2std = firsts2std.times(1.0/hoverCount);
                        firsts2std = firsts2std.minus(firsts2.arrayTimes(firsts2));
                        firsts2std = firsts2std.arraySqrt().arrayPlus(0.5);
                        firsts2std.set(0, 0, 1);
                        lasts1std = lasts1std.times(1.0/hoverCount);
                        lasts1std = lasts1std.minus(lasts1.arrayTimes(lasts1));
                        lasts1std = lasts1std.arraySqrt().arrayPlus(0.5);
                        lasts1std.set(0, 0, 1);
                        lasts2std = lasts2std.times(1.0/hoverCount);
                        lasts2std = lasts2std.minus(lasts2.arrayTimes(lasts2));
                        lasts2std = lasts2std.arraySqrt().arrayPlus(0.5);
                        lasts2std.set(0, 0, 1);
                        sums1std = sums1std.times(1.0/hoverCount).arrayPlus(0.5);
                        sums1std = sums1std.minus(sums1.arrayTimes(sums1));
                        sums1std = sums1std.arraySqrt().arrayPlus(0.5);
                        sums2std = sums2std.times(1.0/hoverCount);
                        sums2std = sums2std.minus(sums2.arrayTimes(sums2));
                        sums2std = sums2std.arraySqrt().arrayPlus(0.5);
                        deltaColorsStd = deltaColorsStd.times(1.0/hoverCount);
                        deltaColorsStd = deltaColorsStd.minus(deltaColors.arrayTimes(deltaColors));
                        deltaColorsStd = deltaColorsStd.arraySqrt().arrayPlus(0.5);
                        colorDistStd = Math.sqrt(colorDistStd/hoverCount - colorDist*colorDist);

                        // insert (or update!)
                        String updateStr = "INSERT INTO sunar.training_bayes(camera1, camera2, delta_t_avg, delta_t_std, \n" +
                                " firsts1_avg, firsts2_avg, firsts1_std, firsts2_std, lasts1_avg, lasts2_avg, lasts1_std, lasts2_std, \n" +
                                " avgs1_avg, avgs2_avg, avgs1_std, avgs2_std, sums1_avg, sums2_avg, sums1_std, sums2_std, \n" +
                            " delta_color_avg, delta_color_std, \n" +
                            " delta_color_dist_avg, delta_color_dist_std, cnt, prior)\n" +
                                " VALUES ("+ camera1 +", "+ camera2 +", "+ deltaT +", "+ deltaTstd +", \n" +
                                " ARRAY["+ firsts1.toString(4,0) +"], ARRAY["+ firsts2.toString(4,0) +"], ARRAY["+ firsts1std.toString(4,0) +"], ARRAY["+ firsts2std.toString(4,0) +"], ARRAY["+ lasts1.toString(4,0) +"], ARRAY["+ lasts2.toString(4,0) +"], ARRAY["+ lasts1std.toString(4,0) +"], ARRAY["+ lasts2std.toString(4,0) +"], \n" +
                                " ARRAY["+ avgs1.toString(4,0) +"], ARRAY["+ avgs2.toString(4,0) +"], ARRAY["+ avgs1std.toString(4,0) +"], ARRAY["+ avgs2std.toString(4,0) +"], ARRAY["+ sums1.toString(4,0) +"], ARRAY["+ sums2.toString(4,0) +"], ARRAY["+ sums1std.toString(4,0) +"], ARRAY["+ sums2std.toString(4,0) +"], \n" +
                            " ARRAY["+ deltaColors.toString(4,0) +"], ARRAY["+ deltaColorsStd.toString(4,0) +"], \n" +
                            " "+ Math.round(colorDist) +", "+ Math.round(colorDistStd) +", "+ hoverCount +", "+ (double)hoverCount/(double)cam1[1] +")";

                            commons.logTime(updateStr);
                        try {
                            stmtUpdate.executeUpdate(updateStr);
                        } catch (SQLException e) {
                            commons.error(this, e.getMessage());
                        }

                    } // update

                } // 4 camera2

            } // 4 camera1


            commons.logTime("... done.");

        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            return false;
        } catch (Exception e) { // for sure... log
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            return false;
        }

        return true;
    }



    /**
     * Returns sorted array (TreeMap) of vector.camera2(s) and the probabilities of the next track beeing there...
     * @param track SunarTrack
     * @return
     */
    public TreeMap<Double, NBvector> predictCamera(SunarTrack track) {
        TreeMap<Double, NBvector> map = new TreeMap<Double, NBvector>();

        // get a list of vectors belonging to this camera (the list starts by 0 :~)
        List<NBvector> camVectors = this.model.get(track.camera-1);
        // go through the camera vectors and classify the camera
        for (NBvector vector : camVectors) {
            map.put(vector.predictCamera(track.avgs, track.lasts), vector);
        }

        return map;
    }

    /**
     * Returns semi-ordered list of (most probably) following tracks, recursively
     * // TODO: camera overlapping analysis -- in progress
     * @param track SunarTrack
     * @param maxTracks is to stop the tree growth in the recursion (eg. using 2 there will be 2 leafs... 3-6, 4-24, 5-120)
     * @return the list
     */
    public SunarTrack predictTracks(SunarTrack track, int expTrack, int recurse) {
        if (recurse == 0) return track;

        // najdi, serad je podle pravdepodobnosti
        TreeMap<Double, SunarTrack> followingTracks = new TreeMap<Double, SunarTrack>();

        try {
            Statement stmtTracks = commons.conn.createStatement();

            // predict the next cameras...
            TreeMap<Double, NBvector> prCam = predictCamera(track);
            // commons.logTime(prCam.toString());

            // mark the best probability
            Double bestProb = 0.0;

            // keySet method returns a Set view of the keys contained in this map.
            Iterator<Double> iterator = prCam.descendingKeySet().iterator();
            while (iterator.hasNext()) {
                Double probCam = iterator.next();

                // delam se jenom s tema (kamerama), co jsou nejhure 0.7*nejlepsi a na mrzaky seru (by to bylo moc dotazu)
                if (bestProb == 0.0) bestProb = probCam;
                if (probCam < 0.5*bestProb) break;

                // vem si aktualni vektor...
                NBvector vect = prCam.get(probCam);

                // prepare the average time and the first possible (not to be enable to return in time :)
                int time = (int)track.lasts.get(0) + vect.deltaT;
                int firstime = ((time-vect.deltaTstd) > track.firsts.get(0)) ? (int)(time-vect.deltaTstd): (int)track.firsts.get(0);

                // rekurze by mela skoncit, pokud se nic takovyho nenajde... dal pak tou prom. recurse
                String queryTracks = "SELECT *, abs("+ time +" - firsts[1]) as delta_t, sqrt(distance_square_int4(avgcolors, ARRAY["+ track.avgcolors.toString(4,0) +"])) as color_dist\n" +
                        " FROM ONLY sunar.tracks \n" +
                        " WHERE camera IN (SELECT camera FROM ONLY sunar.evaluation_tracks WHERE dataset="+ track.dataset +" AND video="+ track.video +" AND track="+ expTrack +") \n" +
                        "   AND dataset="+ track.dataset +" AND video="+ track.video +" AND camera="+ vect.camera2 +" \n" + // tady a vyse muze nastat kolize camera, to je OK, zalezi na typu experimentu
                        "   AND firsts[1] > "+ (int)(firstime) + " AND firsts[1] < "+ (int)(time + 2*vect.deltaTstd) +" --time\n" +
                        "   AND sqrt(distance_square_int4(avgcolors, ARRAY["+ track.avgcolors.toString(4,0) +"])) < "+ (vect.colorDist+2*vect.colorDistStd) +" \n" +
                        "   AND firsts[2] > "+ (int)(vect.firsts2.get(1) - 2*vect.firsts2std.get(1)) +" AND firsts[2] < "+ (int)(vect.firsts2.get(1) + 2*vect.firsts2std.get(1)) +" --X\n" +
                        "   AND firsts[3] > "+ (int)(vect.firsts2.get(2) - 2*vect.firsts2std.get(2)) +" AND firsts[3] < "+ (int)(vect.firsts2.get(2) + 2*vect.firsts2std.get(2)) +" --Y\n" +
                        "   AND firsts[4] > "+ (int)(vect.firsts2.get(3) - 2*vect.firsts2std.get(3)) +" AND firsts[4] < "+ (int)(vect.firsts2.get(3) + 2*vect.firsts2std.get(3)) +" --W\n" +
                        "   AND firsts[5] > "+ (int)(vect.firsts2.get(4) - 2*vect.firsts2std.get(4)) +" AND firsts[5] < "+ (int)(vect.firsts2.get(4) + 2*vect.firsts2std.get(2)) +" --H\n";
                // commons.logTime(queryTracks);
                ResultSet rsTracks = stmtTracks.executeQuery(queryTracks);

                // prubni je, smejdy
                while (rsTracks.next()) {
                    int nextTrack = rsTracks.getInt("track");
                    int nextDeltaT = rsTracks.getInt("delta_t");
                    int nextColorDist = rsTracks.getInt("color_dist");

                    // count the joint probability of beeing a next track
                    SunarTrack nextOne = new SunarTrack(commons, rsTracks);
                    Double probIdent = vect.identifyTrack(nextDeltaT, nextColorDist, nextOne.firsts);
                    Double probVerif = owner.trackVerification(track.dataset, track.video, track.camera, vect.camera2, track.track, nextTrack);
                    // if (probVerif > 0) commons.log("p " + probCam +" "+ probIdent +" "+ probVerif +"\n"); // if (probVerif > 0)
                    nextOne.bestProb = probCam * probIdent * (0.2+probVerif)*5;    // normalizace C.K., aby to vychazelo tak nejak rozumne

                    followingTracks.put(nextOne.bestProb, nextOne);

                }

            } // first / "best camera"

            // presupej nejlepsi do follows a mrzaky poser garbage collector
            int i = (recurse/2 <= 0) ? 1 : recurse/2;

            // keySet method returns a sorted Set view of the keys contained in this map.
            iterator = followingTracks.descendingKeySet().iterator();
            if (track.follows == null && iterator.hasNext()) {
                track.follows = new ArrayList<SunarTrack>();
            }

            // tohle prida do follows nekolik (recurse/2) nejlepsich tracks a provede rekurzi
            while (iterator.hasNext()) {
                if (i <= 0) break; // mrzaky poser...
                i--;

                Double prob = iterator.next();
                SunarTrack tr = followingTracks.get(prob);

                track.follows.add(tr);
                // commons.logTime(track.toString()+" %"+tr.bestProb);

                // rekurze - ale dobre se rozmyslet, aby to zastavilo vubec pred koncem vesmiru.
                predictTracks(tr, expTrack, recurse-1);
            }

            // TODO: asi az nakonec... prepocitej track.bestProb - hmm, jo?

        } catch (SQLException ex) {
            Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
        }

        return track;
    }

    /**
     * Just FYI
     */
    public void testPredictCamera() {
        commons.logTime("performing NaiveBayes test...");

        // select all transitions
        try {
            // true v | prediction > | TP is on the diagonal, [0][0] is the TP count, other stats are at [j][0] (true) and [0][i] (prediction)
            int[][] stats = new int[6][6];

            Statement stmt = commons.conn.createStatement();
            String handowersQuery = "SELECT * \n" +
                    " FROM ONLY sunar.training_handovers_cache \n" +
                    " ORDER BY camera1, camera2, dataset, video, track1";
            ResultSet rs = stmt.executeQuery(handowersQuery);

            Statement deleteStmt = commons.conn.createStatement();
            int hovnoCnt = 0;

            // handovers count
            int hoversCnt = 0;

            // handovers
            while(rs.next()) {
                hoversCnt++;
                int camera1 = rs.getInt("camera1");
                int camera2 = rs.getInt("camera2");
                int dataset = rs.getInt("dataset");
                int video = rs.getInt("video");
                int track1 = rs.getInt("track1");
                int track2 = rs.getInt("track2");
                Matrix avgs1 = new Matrix(rs.getString("avgs1"));
                Matrix lasts1 = new Matrix(rs.getString("lasts1"));

                // get a list of vectors belonging to this camera (the list starts by 0 :~)
                List<NBvector> camVectors = this.model.get(camera1-1);

                // prepare
                commons.log("C"+ camera1 + " -> C" + camera2 + "\t D:"+ dataset +" \t V:"+ video + "\t T1:" + track1 + " >> ");
                int bestCam = 0;
                int bestCam2 = 0;
                double bestCamR = 0;

                // go through the camera vectors and classify the camera
                for (NBvector vector : camVectors) {
                    double camR = vector.predictCamera(avgs1, lasts1);
                    if (camR > bestCamR) {
                        bestCam2 = bestCam;
                        bestCam = vector.camera2;
                        bestCamR = camR;
                    }

                    commons.log("\t C"+ vector.camera2 +":"+ camR);
                }

                // store the stats
                stats[camera2][bestCam]++;
                stats[camera2][0]++;
                stats[0][bestCam]++;
                if (bestCam == camera2) {
                    stats[0][0]++;
                    commons.log("\nOK\n");
                }
                else if (bestCam2 == camera2) {
                    stats[0][0]++;
                    commons.log("\n    mozna\n");
                }
                else {
                    commons.log("\n              hovno -> smazat? ... \n");
                    /*
                    String deleteHovno = "DELETE \n" +
                    " FROM ONLY sunar.training_handovers_cache \n" +
                    " WHERE camera1="+ camera1 +" AND camera2="+ camera2 +" AND dataset="+ dataset +" AND video="+ video +" AND track1="+ track1 +" AND track2="+ track2  +" \n";
                    deleteStmt.executeUpdate(deleteHovno);
                    */
                    hovnoCnt++;
                }
            } // while handoders

            // print summary result
            for (int j = 0;  j < 6; j++) {    // row
                for (int i = 0;  i < 6; i++) {   // col
                    commons.log(String.valueOf(stats[j][i]) +"; \t");
                }
                commons.log("\n");
            }

            commons.logTime("classification accuracy: "+ (double)(stats[0][0])/(double)(hoversCnt));
            commons.logTime("#hoven: "+ hovnoCnt);

        } catch (SQLException ex) {
            Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
        }


        // print stats

    }

    /**
     * A vector of NaiveBayess model...
     */
    public class NBvector {
        public int camera1 = 0, camera2 = 0;
        public int hoverCount = 0;
        double prior = 0.0;
        public int deltaT = 0, deltaTstd = 0;
        public int colorDist = 0, colorDistStd = 0;
        public Matrix firsts1 = null, firsts1std = null;
        public Matrix firsts2 = null, firsts2std = null;
        public Matrix lasts1 = null, lasts1std = null;
        public Matrix lasts2 = null, lasts2std = null;
        public Matrix avgs1 = null, avgs1std = null;
        public Matrix avgs2 = null, avgs2std = null;
        public Matrix sums1 = null, sums1std = null;
        public Matrix sums2 = null, sums2std = null;
        public Matrix deltaColor = null, deltaColorStd = null;

        /**
         * Tuna to nacte vector z resultSetu nebo
         */
        public NBvector(ResultSet rs) throws SQLException {
            // if(rs != null && !rs.isClosed()) { // kdyztak bude vyjimka... constructor nema return
            this.camera1 = rs.getInt("camera1");
            this.camera2 = rs.getInt("camera2");
            this.hoverCount = rs.getInt("cnt");
            this.prior = rs.getDouble("prior");

            // there is nothing to do here...
            if (hoverCount == 0) return;
            else { // napln vektor
                this.deltaT = rs.getInt("delta_t_avg");
                this.deltaTstd = rs.getInt("delta_t_std");
                this.colorDist = rs.getInt("delta_color_dist_avg");
                this.colorDistStd = rs.getInt("delta_color_dist_std");

                this.firsts1 = new Matrix(rs.getString("firsts1_avg"));
                firsts1std = new Matrix(rs.getString("firsts1_std"));
                this.firsts2 = new Matrix(rs.getString("firsts2_avg"));
                firsts2std = new Matrix(rs.getString("firsts2_std"));
                this.lasts1 = new Matrix(rs.getString("lasts1_avg"));
                lasts1std = new Matrix(rs.getString("lasts1_std"));
                this.lasts2 = new Matrix(rs.getString("lasts2_avg"));
                lasts2std = new Matrix(rs.getString("lasts2_std"));
                this.avgs1 = new Matrix(rs.getString("avgs1_avg"));
                avgs1std = new Matrix(rs.getString("avgs1_std"));
                this.avgs2 = new Matrix(rs.getString("avgs2_avg"));
                avgs2std = new Matrix(rs.getString("avgs2_std"));
                this.sums1 = new Matrix(rs.getString("sums1_avg"));
                sums1std = new Matrix(rs.getString("sums1_std"));
                this.sums2 = new Matrix(rs.getString("sums2_avg"));
                sums2std = new Matrix(rs.getString("sums2_std"));
                this.deltaColor = new Matrix(rs.getString("delta_color_avg"));
                deltaColorStd = new Matrix(rs.getString("delta_color_std"));
            }
        }

        /**
         * Dont use this constructor (much :)
         */
        public NBvector() {};

        /**
         * Override
         * @return sring (selected)
         */
        @Override
        public String toString() {
            if (this.hoverCount == 0) return "C"+ camera1 +" -> C"+ camera2 + " %0.0";
            return "C"+ camera1 +" -> C"+ camera2 +" %"+ prior +" ("+ deltaT +", "+ colorDist + ")";
        }

        /**
         * Tohle da "pravdepodobnost" toho, s jakou se objekt zjevi v nejake delsi kamere... (tohle je castecne tajne)
         * @param avgs
         * @param lasts
         * @return
         */
        public double predictCamera(Matrix avgs, Matrix lasts) {
            if (this.hoverCount == 0) return 0.0;   // cannot happen (hm...)
            double avg = 0.0;
            double last = 0.0;

            // go through the fields (ignore time) // a nepouzivej zadny figle (byt dokazane), normalne je znasob...
            for (int i = 2; i < this.avgs1.getColumnDimension(); i++) {
                avg += 10*normal(avgs.get(0, i), this.avgs1.get(0, i), this.avgs1std.get(0, i));
                last += 10*normal(lasts.get(0, i), this.lasts1.get(0, i), this.lasts1std.get(0, i));
            }
            // commons.log("\npC "+ this.prior+ " "+ last + " "+ avg + "\n");

            return this.prior*last*avg; // *last NaiveBayes - product
        }

        // public double predictParameters()
        // this function must be done in the database ... < avg +- 2*std of the following three parameters...
        // well, pick just 10 best or similar (where available...)

        /**
         * Tohle da pravdepodobnost identifikace trajektorie, s jakou je to navazujici tajektorie... (je to tajne a plne C.K.)
         * FIXME: how to count a prior???
         * @param dT
         * @param dColor
         * @param firsts
         * @return
         */
        public double identifyTrack(int dT, int dColor, Matrix firsts) {
            if (this.hoverCount == 0) return 0.0; // cannot happen (hm...)
            double t = 0.0;
            double color = 0.0;
            double first = 0.0;

            t = Math.sqrt(Math.sqrt(normal(dT, this.deltaT, this.deltaTstd))); // ten cas je tu schvalne znovu... (c) J.f.C.
            color = Math.sqrt(normal(dColor, this.colorDist, this.colorDistStd))*100;
                    // (double)(this.colorDist)/(double)dColor; // tohle je haluzoidni konstanta... co je lepsi jak prumer, tak to > 1
            // tohle projde firsts (t, x, y, w, h)
            for (int i = 2; i < this.firsts2.getColumnDimension(); i++) {
                first += 10*normal(firsts.get(0, i), this.firsts2.get(0, i), this.firsts2std.get(0, i));
            }

            return t*color*first;
        }

    } // NBvector

} // NaiveBayes
