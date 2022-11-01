package sunarvrm;

import com.gregdennis.drej.*;
import jama.Matrix;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.vecmath.GMatrix;
import javax.vecmath.GVector;
import jkalman.JKalman;
import sunarvrm.NaiveBayes.NBvector;

/**
 *
 * @author chmelarp
 */
public class SunarIntegration {

    // custom settings
    public int recurseTree; // 5-7(-9) is OK, 7 je ve skutecnosti do hloubky 8, takze fine (<175 tracks ... 9 <~ 2800 tracks + stack overflow)

    Commons commons;
    NaiveBayes nb = null;
    // libsvm
    String trainCameraFName = "../libsvm/camera"; // + 1-5 + ".train" || ".model" || ".test";
    String trainTrackFName = "../libsvm/track"; // + 1-5 + ".train" || ".model" || ".test";

    // Overlapping camera1 to camera2 regression model and its statistical representation (min, max used as its domain)
    public static final int m_features = 2; // x, y
    Representer[][][] representersCC;
    StatisticsScalar[][][] statisticsCC;

    public SunarIntegration(Commons commons, int recurse) {
        this.commons = commons;
        this.recurseTree = recurse;

        representersCC = new Representer[5][5][2];      // [camera1] [camera2] [X/Y]
        statisticsCC = new StatisticsScalar[5][5][4];   // [camera1] [camera2] [X/Y/predX/predY]
    }

    /**
     * ECF file controled experiments
     * Just splits experiments in different cases
     * @return
     */
    public boolean runExperiments(List<Integer> tracks) {
        boolean result = true;

        // load a classification model
        if (nb == null) {
            nb = new NaiveBayes(commons, this);
            nb.loadModel();
        }

        // process experiments separately
        for (int track : tracks) {
            if (!runExperiment(track)) {
                result = false;
            }
        }

        return result;
    }

    /**
     * Runs a dataset and "video" (a-i) bounded experiment
     * @return
     */
    public boolean runExperiment(int track) {
        try {
            commons.logTime("experiment " + track + " started ");
            // TODO: tohle dat do load experiments priste
            // first, extend the 5 frame starter...
            extendStarter(track);

            // find starter
            Statement stmtStarter = commons.conn.createStatement();

            // najdi 1. z tech 5 anotovanych framu
            String queryStarter = "SELECT * \n"
                    + " FROM ONLY sunar.evaluations \n"
                    + " WHERE position IS NOT NULL AND track=" + track + " \n"
                    + " ORDER BY \"time\" \n"
                    + " LIMIT 1"; // staci
            ResultSet rsStarter = stmtStarter.executeQuery(queryStarter);

            int ssDataset = 0;
            int ssVideo = 0;
            int ssCamera = 0;
            int ssTrack = 0;
            int ssFirst = 0;
            int ssObject = 0;
            // first - dalsi me jsou jedno, mam hledat odsud dal...
            if (rsStarter.next()) {
                ssDataset = rsStarter.getInt("dataset");
                ssVideo = rsStarter.getInt("video");
                ssCamera = rsStarter.getInt("camera");
                ssObject = rsStarter.getInt("object");  // tenhle nejspis k nicemu nebude...
                ssFirst = rsStarter.getInt("time");
            }

            // find the starter track
            String queryTrack = "SELECT proc.track AS proctrack, track.firsts[1], track.lasts[1], sum(sunar.overlaps(eval.position, eval.size, proc.position, proc.size)), min(proc.time), max(proc.time) \n"
                    + "  FROM ONLY sunar.processed AS proc JOIN ONLY sunar.evaluations AS eval \n"
                    + "       ON (eval.dataset=proc.dataset AND eval.video=proc.video AND eval.camera=proc.camera AND (eval.time=proc.time OR eval.time=(proc.time+1))), \n" + // TODO: k zamysleni +-2??? asi ne
                    "       ONLY sunar.tracks AS track \n"
                    + " WHERE track.dataset=proc.dataset AND track.video=proc.video AND track.camera=proc.camera AND track.track=proc.track AND \n"
                    + "       sunar.overlaps(eval.position, eval.size, proc.position, proc.size) > 0.05 \n"
                    + "       AND eval.position IS NOT NULL \n"
                    + "       AND eval.dataset=" + ssDataset + " AND eval.video=" + ssVideo + " AND eval.camera=" + ssCamera + " AND eval.track=" + track + " \n"
                    + " GROUP BY eval.dataset, eval.object, eval.video, eval.camera, eval.track, proc.track, track.firsts[1], track.lasts[1] \n"
                    + " ORDER BY sum(sunar.overlaps(eval.position, eval.size, proc.position, proc.size)) DESC \n"
                    + " LIMIT 1 "; // starter musi byt jenom 1 (i kdyz nevim proc, asi to tak bude)
            rsStarter = stmtStarter.executeQuery(queryTrack);

            if (rsStarter.next()) {
                ssTrack = rsStarter.getInt("proctrack");
                commons.logTime("starter " + track + " found: " + ssTrack);
            } else {
                commons.error(this, "Cannot find a starter track (" + track + ") aborting!");
                commons.log(queryTrack + "\n");
                return false;
            } // TODO: co s ostatnima? mozna se netrefim - nutne zkontrolovat!

            // create the track...
            SunarTrack starter = new SunarTrack(commons, ssDataset, ssVideo, ssCamera, ssTrack);
            starter.bestProb = 1.0; // there is nothing more sure than this :)

            // recursively find others...
            nb.predictTracks(starter, track, recurseTree); // 5-9 ... 7 je ve skutecnosti do hloubky 8, takze fine (<175 tracks)

            // find the best (or at least guess it...)
            starter.setBestProbs();
            commons.logTime("tracks found and probs to " + track + " set");

            // print the tree
            commons.log(starter.toStringRecurse());

            // export it
            starter.makeObjectPersistent(track); // tady se to ulozi do sunar.tracks.experiment nove
            commons.logTime("stored persistently " + track);

            // huraaaaaaa! pivo volaaaaaaaa!

        } catch (SQLException ex) {
            commons.error(this, ex.getMessage());
            Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        return true;
    }

    /**
     * The starter is usualy too short - extend it using Kalman filter (to 15 states each 5 frames = 3s)
     * @return
     */
    public boolean extendStarter(int track) {
        try {

            // find starter
            Statement stmtStarter = commons.conn.createStatement();
            // najdi 1. z tech 5 anotovanych framu
            String queryStarter = "SELECT * \n"
                    + " FROM ONLY sunar.evaluations \n"
                    + " WHERE position IS NOT NULL AND track=" + track + " \n"
                    + " ORDER BY \"time\" \n";
            ResultSet rsStarter = stmtStarter.executeQuery(queryStarter);

            int ssDataset = 0;
            int ssVideo = 0;
            int ssCamera = 0;
            int ssTrack = 0;
            int ssFirst = 0;
            int time = 0;
            int ssObject = 0;
            Matrix position = null;
            Matrix size = null;

            double x = 0;
            double y = 0;
            double dx = 0;
            double dy = 0;

            int stateCnt = 0;

            // first, mam hledat odsud dal...
            if (rsStarter.next()) {
                ssDataset = rsStarter.getInt("dataset");
                ssVideo = rsStarter.getInt("video");
                ssCamera = rsStarter.getInt("camera");
                ssObject = rsStarter.getInt("object"); // jo, fakt to tady na nic neeni
                ssFirst = rsStarter.getInt("time");
                position = new Matrix(rsStarter.getString("position"));
                size = new Matrix(rsStarter.getString("size"));

                x = position.get(0);
                y = position.get(1);
                stateCnt++;
            }

            // continue with 2-4th ...
            while (rsStarter.next()) {
                position = new Matrix(rsStarter.getString("position"));
                size = new Matrix(rsStarter.getString("size"));
                time = rsStarter.getInt("time");

                // if already updated
                stateCnt++;
                if (stateCnt > 10) {
                    return true; // nekdo to za nas uz zmakl...
                }
            }

            // melo by byt 5 stavu enem... (viz vys) - (linearni interpolace prvni-posledni / cnt), stavy jsou po 5
            dx = (position.get(0) - x) / stateCnt;
            dy = (position.get(1) - y) / stateCnt;
            x = position.get(0);
            y = position.get(1);

            // select next (empty) states... and "predict" the Kalman state
            // najdi 1. z tech 5 anotovanych framu
            queryStarter = "SELECT * \n"
                    + " FROM ONLY sunar.evaluations \n"
                    + " WHERE \"time\">" + time + " AND dataset=" + ssDataset + " AND video=" + ssVideo + " AND camera=" + ssCamera + " AND track=" + track + " \n"
                    + " ORDER BY \"time\" \n";
            rsStarter = stmtStarter.executeQuery(queryStarter);

            Statement stmtUpdater = commons.conn.createStatement();

            // dojed to do konce obrazovky nebo nasledujicich 30 framu (~1s)
            // TODO: zjistit velikost videa... 720x576 nebude nafurt!
            int frames_cnt = 0;
            while (rsStarter.next()
                    && (frames_cnt++) < 10
                    && x > size.get(0) / 2
                    && x < 720 - size.get(0) / 2
                    && y > size.get(1) / 2
                    && y < 576 - size.get(1) / 2) {

                time = rsStarter.getInt("time");

                // takhle to tady podfouknu...
                x += dx;
                y += dy;

                String queryUpdate = "UPDATE ONLY sunar.evaluation_states \n"
                        + " SET position='(" + (int) x + ", " + (int) y + ")', size='(" + size.toString(4, 0) + ")' \n"
                        + " WHERE \"time\"=" + time + " AND dataset=" + ssDataset + " AND video=" + ssVideo + " AND camera=" + ssCamera + " AND track=" + track;
                stmtUpdater.executeUpdate(queryUpdate);
            }

        } catch (SQLException ex) {
            commons.error(this, ex.getMessage());
            Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (Exception ex) {
            commons.error(this, ex.getMessage());
            Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }


        return true;
    }

    /**
     * Runs a camera classification test using naive Bayes classification
     * @return
     */
    public boolean testBayesPredictCamera() {
        if (nb == null) {
            nb = new NaiveBayes(commons, this);
            nb.loadModel();
        }
        nb.testPredictCamera();
        return true;
    }

    /**
     * A link to the bayes training in the appropriate class
     * @return
     */
    public boolean trainBayes() {
        if (nb == null) {
            nb = new NaiveBayes(commons, this);
        }
        return nb.trainBayes();
    }

    /**
     * Create SVM classifier models for:
     *   1. (Next) camera classification using (not inverted) Kalman states
     *      (averages1, firsts1 of sunar.annotation_handovers) and -> camera (1 label) (trainCameraFName+".train")
     *   2. (Next) track classifications using Kalman states, cameras, delta_t, delta_color -> track probability
     *      (trainTrackFName+".train")
     * @return sucess
     */
    public boolean trainSVM() {

        // fill in the train table (and files)
        try {

            Statement stmtCameras = commons.conn.createStatement();
            Statement stmtHandovers = commons.conn.createStatement();

            //
            // FIXME: if any changes in the database ... run this caching:
            // SELECT * INTO sunar.training_handovers_cache FROM sunar.training_handovers
            //

            String videosQuery = "SELECT * \n"
                    + "  FROM ONLY sunar.training_cameras \n"
                    + " ORDER BY camera1";
            ResultSet videosRs = stmtCameras.executeQuery(videosQuery);

            // go through videos...
            while (videosRs.next()) {
                final int camera1 = videosRs.getInt("camera1");
                final int handoversCount = videosRs.getInt(2); // just FYI

                commons.logTime("trainging camera " + camera1);

                // the libsvm reference...
                BufferedWriter writerCamera = null;
                try {
                    writerCamera = new BufferedWriter(new FileWriter(trainCameraFName + camera1 + ".train"));
                } catch (IOException ex) {
                    commons.error(this, "Cannot create files " + trainCameraFName + " and/or " + trainTrackFName);
                    Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
                    return false;
                }

                // perform selectioms...
                String handowersQuery = "SELECT * \n"
                        + " FROM ONLY sunar.training_handovers_cache\n"
                        + " WHERE camera1=" + camera1 + "\n"
                        + " ORDER BY camera2, dataset, video, firsts1[1]";
                // System.out.println(tracksQuery);
                ResultSet handoversRs = stmtHandovers.executeQuery(handowersQuery);

                // go through handovers...
                while (handoversRs.next()) {
                    final int camera2 = handoversRs.getInt("camera2"); // classification target
                    final String[] avgs1 = (handoversRs.getString("avgs1").substring(1, handoversRs.getString("avgs1").length() - 1)).split("[,]\\s*");
                    final String[] avgs2 = (handoversRs.getString("avgs2").substring(1, handoversRs.getString("avgs2").length() - 1)).split("[,]\\s*");
                    final String sumsT1 = (handoversRs.getString("sums1").substring(1, handoversRs.getString("sums1").length() - 1)).split("[,]\\s*")[0];
                    final String sumsT2 = (handoversRs.getString("sums2").substring(1, handoversRs.getString("sums2").length() - 1)).split("[,]\\s*")[0];
                    final String[] lasts1 = (handoversRs.getString("lasts1").substring(1, handoversRs.getString("lasts1").length() - 1)).split("[,]\\s*");
                    final String[] firsts2 = (handoversRs.getString("firsts2").substring(1, handoversRs.getString("firsts2").length() - 1)).split("[,]\\s*");
                    final String[] deltaColor1 = (handoversRs.getString("delta_color").substring(1, handoversRs.getString("delta_color").length() - 1)).split("[,]\\s*");
                    final int deltaT = handoversRs.getInt("delta_t");

                    // create the LibSVM training vector (camera target) reference (ugly, but fine at the moment)
                    int colNo = 1;
                    String train1row = camera2 + " " + colNo++ + ":" + camera1 + " ";
                    for (int i = 1; i < avgs1.length; i++) {
                        train1row += colNo++ + ":" + avgs1[i] + " ";
                    }
                    for (int i = 1; i < lasts1.length; i++) {
                        train1row += colNo++ + ":" + lasts1[i] + " ";
                    }
                    writerCamera.write(train1row + "\n");
                }

                // export the libsvm samplesC1
                writerCamera.flush();
                writerCamera.close();

            } // 4 handoverVideosRs


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
     * Creates a model of an annotated states (-5+4 frames)
     * occluding the annotations (>1.0) visible on more than 1 camera concurrently
     * @return
     */
    public boolean trainVerifications() {
        try {
            Statement camerasStmt = commons.conn.createStatement();
            Statement annotsStmt = commons.conn.createStatement();

            // find a -- tohle udela par anotace na camA - anotace na camB
            String camerasQuery = "SELECT DISTINCT an1.camera, an2.camera \n"
                    + " FROM ONLY sunar.videos an1 JOIN ONLY sunar.videos an2 ON (an1.camera<>an2.camera) -- WHERE an1.camera > 2 AND an2.camera > 2 \n"
                    + " ORDER BY an1.camera, an2.camera"; // TODO: smazat!!!!!!!!!11
            // System.out.println(camerasQuery);
            ResultSet camerasRs = camerasStmt.executeQuery(camerasQuery);

            while (camerasRs.next()) {
                final int camera1 = camerasRs.getInt(1);
                final int camera2 = camerasRs.getInt(2);
                commons.log("Processing camera "+ camera1 +"-"+ camera2 +" ... ");
                // commons.log("x1 x2 y1 y2 \n");

                // tohle je tam, aby to pak vedelo odkud kam se maji delat ty vypocty (domena, obor hodnot)
                statisticsCC[camera1-1][camera2-1][0] = new StatisticsScalar(); // X
                statisticsCC[camera1-1][camera2-1][1] = new StatisticsScalar(); // Y
                // tohle je tam, aby to pak vedelo, jak presna se ma cekat vysledna hodnota (0 je tam kdyz nic nebylo... to je uzitecne, odpovida NULL u statisticsCC)
                statisticsCC[camera1-1][camera2-1][2] = new StatisticsScalar(); // predX
                statisticsCC[camera1-1][camera2-1][3] = new StatisticsScalar(); // predY



                final String annotsQuery = "SELECT an1.dataset, an1.video, an1.camera, an2.camera, an1.\"offset\", an1.\"object\", an1.\"time\", st1.track, st2.track, --9\n"
                        + "     count(st1.\"time\"), count(st2.\"time\"), --11\n"
                        + "     sum(sunar.overlaps(an1.position, an1.size, st1.position, st1.size)),\n"
                        + "     sum(sunar.overlaps(an2.position, an2.size, st2.position, st2.size)), --13 --, an1.firsts[1], an2.firsts[1], an1.lasts[1], an2.lasts[1]\n"
                        + "     avg(st1.position[0]), avg(st2.position[0]), avg(st1.position[1]), avg(st2.position[1]), --17\n"
                        + "     avg(st1.size[0]), avg(st2.size[0]), avg(st1.size[1]), avg(st2.size[1]), --21\n"
                        + "     avg(distance_square_int4(st1.color, st2.color)) --22\n" //TODO: vector aggregation functions
                        + " FROM ONLY sunar.annotations an1 JOIN ONLY sunar.annotations an2 ON (an1.dataset=an2.dataset AND an1.video=an2.video AND an1.camera<>an2.camera AND an1.\"time\"=an2.\"time\"),\n"
                        + "     sunar.processed st1, sunar.processed st2 -- joined @where\n "
                        + " WHERE an1.dataset=st1.dataset AND an1.video=st1.video AND an1.camera=st1.camera AND st1.\"object\">0 AND st1.\"time\">=an1.\"time\"-5 AND st1.\"time\"<=an1.\"time\"+4 AND\n"
                        + "     an2.dataset=st2.dataset AND an2.video=st2.video AND an2.camera=st2.camera AND st2.\"object\">0 AND st2.\"time\">=an2.\"time\"-5 AND st2.\"time\"<=an2.\"time\"+4 AND\n"
                        + "     st1.\"object\"=st2.\"object\" AND st1.\"time\"=st2.\"time\" AND\n"
                        + "     sunar.overlaps(an1.position, an1.size, st1.position, st1.size) > 0.1 AND \n"
                        + "     sunar.overlaps(an2.position, an2.size, st2.position, st2.size) > 0.1 AND \n"
                        + "     an1.camera=" + camera1 + " AND an2.camera=" + camera2 + "\n" // independent on the dataset
                        + " GROUP BY an1.dataset, an1.video, an1.camera, an2.camera, an1.\"offset\", an1.\"object\", an1.\"time\", st1.track, st2.track\n"
                        + " HAVING count(st1.\"time\")>4 AND count(st2.\"time\") > 4 AND"
                        + "     sum(sunar.overlaps(an1.position, an1.size, st1.position, st1.size)) > 0.5 AND\n"
                        + "     sum(sunar.overlaps(an2.position, an2.size, st2.position, st2.size)) > 0.5\n"
                        + " ORDER BY an1.dataset, an1.video, an1.camera, an2.camera, an1.\"offset\", an1.\"object\", an1.\"time\", st1.track, st2.track\n";
                // System.out.println(annotsQuery);
                ResultSet annotsRs = annotsStmt.executeQuery(annotsQuery);

                // nakrm pole samplesC1
                ArrayList<GVector> samplesC1Arr = new ArrayList<GVector>();
                // nakrm pole values
                ArrayList<Double> valuesC2Xarr = new ArrayList<Double>();
                ArrayList<Double> valuesC2Yarr = new ArrayList<Double>();

                while (annotsRs.next()) {
                    final int dataset = annotsRs.getInt(1);
                    final int video = annotsRs.getInt(2);
                    // final int camera1 = annotsRs.getInt(3);
                    // final int camera2 = annotsRs.getInt(4);
                    final int offset = annotsRs.getInt(5);
                    final int object = annotsRs.getInt(6);
                    final int time = annotsRs.getInt(7);
                    final int track1 = annotsRs.getInt(8);
                    final int track2 = annotsRs.getInt(9);
                    final int count1 = annotsRs.getInt(10);
                    final int count2 = annotsRs.getInt(11);
                    final int overlaps1 = annotsRs.getInt(12);
                    final int overlaps2 = annotsRs.getInt(13);
                    commons.log("D:"+ dataset + " V:"+video + " T1:"+track1 +" T2:"+track2 +  " F:"+ time +" \n");

                    final int x1 = annotsRs.getInt(14);
                    final int x2 = annotsRs.getInt(15);
                    final int y1 = annotsRs.getInt(16);
                    final int y2 = annotsRs.getInt(17);

                    final int color_dst = annotsRs.getInt(22);

                    // just check if to create the domain stats
                    // spocitat obor platnosti - overlapping areas, sizes + stats + opacne... howk
                    // if (statC1) { -- tohle se muze pocitat vickrat, to nevadi
                    statisticsCC[camera1-1][camera2-1][0].add(x1);
                    statisticsCC[camera1-1][camera2-1][1].add(y1);

                    // narkm pole pro trenovani pro 1 kameru tam a zpet a pak taky cilove hodnoty target values
                    GVector sampleC1 = new GVector(2);
                    sampleC1.setElement(0, x1);
                    sampleC1.setElement(1, y1);
                    samplesC1Arr.add(sampleC1);

                    valuesC2Xarr.add((double)x2);  // x2
                    valuesC2Yarr.add((double)y2);  // y2

                    // commons.log(x1 +" "+ x2 +" "+ y1 +" "+ y2 +"\n");
                }

                // have n samplesC1 points, each of which have m features
                int n_vectors = samplesC1Arr.size();
                commons.log(" containing "+ n_vectors +" vectors: \n");
                
                // if there's not enough points...
                if (n_vectors > 3) {
                    GMatrix samplesC1 = new GMatrix(m_features, n_vectors);

                    // have target values (for camera2 x,y)
                    GVector valuesC2X = new GVector(n_vectors);
                    GVector valuesC2Y = new GVector(n_vectors);

                    // here you would put all your samplesC1 points into the samplesC1 matrix
                    // each samplesC1 point goes into a column of the matrix
                    // put the actual values for those samplesC1 points in the values vector
                    // the samplesC1 point in the ith column of the samplesC1 matrix should have
                    // the value in the ith entry in the values vector.
                    // Greg Dennis: I believe some kernels only work when your range of possible values has zero as a midpoint. for instance, if you're classifying samplesC1 points into "yes" and "no", best to choose their values as 1 and -1, as opposed to 1 and 0.
                    for (int i=0; i<n_vectors; i++) {
                        samplesC1.setColumn(i, samplesC1Arr.get(i));

                        valuesC2X.setElement(i, valuesC2Xarr.get(i));
                        valuesC2Y.setElement(i, valuesC2Yarr.get(i));
                    }
                    // commons.log("samplesC1:\n"+samplesC1.toString());
                    // commons.log("valuesC2X:\n"+valuesC2X.toString() +"\n");
                    // commons.log("valuesC2Y:\n"+valuesC2Y.toString() +"\n");

                    double gamma = 0.05;
                    // for (gamma = 100; gamma >= 0.001; gamma /=10) {
                    // construct the kernel you want to use:
                    Kernel kernel = LinearKernel.KERNEL;
                    // Kernel kernel = new GaussianKernel(gamma);

                    // choose a penalty factor on the complexity of the solution
                    // this helps to prevent overfitting the samplesC1
                    // I was told me this number should be between
                    // 10^-3 and 1, I often choose 0.5, but you can play with it
                    double lambda = 0.05; // 0.05;
                    // for (lambda = 0.8; lambda >= 0.001; lambda /=2) {

                        try {

                            // do the regression, which returns a function fit to the samplesC1
                            representersCC[camera1-1][camera2-1][0] = Regression.solve(samplesC1, valuesC2X, kernel, lambda);
                            representersCC[camera1-1][camera2-1][1] = Regression.solve(samplesC1, valuesC2Y, kernel, lambda);

                            // calculate how well the function fits the samples, you can first calculate the vector of values the representerX would predict for your samplesC1 points,
                            // subtract from that the vector of actual values, and take the norm squared of that difference.
                            // Let's call this the "cost". The lower the cost, the better the function fits the samples. You can try out different kernels, and see which one yields the best-fit curve (the lowest cost):

                            // C1X
                            GVector predictedValuesX = Matrices.mapCols(representersCC[camera1-1][camera2-1][0], samplesC1);
                            GVector predictedValuesY = Matrices.mapCols(representersCC[camera1-1][camera2-1][1], samplesC1);

                            predictedValuesX.sub(valuesC2X); // hm... in situ op.
                            predictedValuesY.sub(valuesC2Y);

                            // count proper statistics (log later)
                            for (int i=0; i<n_vectors; i++) {
                                statisticsCC[camera1-1][camera2-1][2].add(Math.abs(predictedValuesX.getElement(i)));
                                statisticsCC[camera1-1][camera2-1][3].add(Math.abs(predictedValuesY.getElement(i)));
                            }

                            double costX = Math.sqrt(predictedValuesX.normSquared());
                            double costY = Math.sqrt(predictedValuesY.normSquared());
                            // log now
                            commons.log("Gamma: "+ gamma +" Lambda: "+ lambda +"-> costX:"+ costX +" costY:"+ costY +"\n");

                        } catch (Exception ex) { // matrix inversion exception?
                            commons.error(this, ex.getMessage());
                            Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
                            continue;
                        }

                        // That's basically it. What happens next depends on what you want to use it for.
                        // If you'd like to use the regression to predict the value of a samples point y, just feed y into the representerX function:
                        // double predictedValue = representerX.eval(null);

                        // tady az na po 2. ... ohodnotit tu hodnotici funkci
                        if (camera1 > camera2 && representersCC[camera1-1][camera2-1][0] != null && representersCC[camera2-1][camera1-1][1] != null) {

                            // for (int i = 0; i < n_vectors; i++) {
                            //   double t = testVerification(camera1, camera2, (int) samplesC1.getElement(0, i), (int) samplesC1.getElement(1, i), (int) valuesC2X.getElement(i), (int) valuesC2Y.getElement(i));
                            //   commons.log("e" + (int) samplesC1.getElement(0, i) + " " + (int) samplesC1.getElement(1, i) + " " + (int) valuesC2X.getElement(i) + " " + (int) valuesC2Y.getElement(i) + ": " + t + "\n");
                            //}

                            // TODO: udelat pak neco jako tohle ale s datama, ktere jsou relevantni
                            /*
                            // debug - uncomment some code in trackVerification for more information
                            if (camera1 == 4 && camera2 == 3) {
                                trackVerification(2, 1, 4, 3, 63, 1659);
                                trackVerification(3, 3, 4, 3, 21, 273);
                                trackVerification(6, 2, 4, 3, 12, 228);
                                trackVerification(7, 4, 4, 3, 28, 502);
                                trackVerification(9, 4, 4, 3, 68, 601);
                            }
                            // debug - uncomment some code in trackVerification for more information
                            if (camera1 == 3 && camera2 == 2) {
                                trackVerification(2, 1, 3, 2, 1061, 1203);
                                trackVerification(2, 4, 3, 2, 413, 386);
                                trackVerification(3, 9, 3, 2, 178, 83);
                                trackVerification(4, 4, 3, 2, 165, 86);
                                trackVerification(7, 4, 3, 2, 363, 389);
                            }
                            */
                        }

                    //}
                    //}

                }
                else { // there is not enough handovers
                    commons.log(" NOOP\n");
                }

            }

            // print stats
            for (int c1=1; c1 <= 5; c1++) {
                for (int c2=1; c2 <= 5; c2++) {
                    if (statisticsCC[c1-1][c2-1][0] != null && statisticsCC[c1-1][c2-1][3] != null && statisticsCC[c1-1][c2-1][0].count() > 0) {
                        commons.log("Cameras "+ c1 + "->"+ c2 +": ("+ statisticsCC[c1-1][c2-1][0].count() +")\n");
                        commons.log("  X avg "+ statisticsCC[c1-1][c2-1][0].avg() + "  std "+ statisticsCC[c1-1][c2-1][0].std() + "  min "+ statisticsCC[c1-1][c2-1][0].min() + "  max "+ statisticsCC[c1-1][c2-1][0].max() + ": \n");
                        commons.log("  Y avg "+ statisticsCC[c1-1][c2-1][1].avg() + "  std "+ statisticsCC[c1-1][c2-1][1].std() + "  min "+ statisticsCC[c1-1][c2-1][1].min() + "  max "+ statisticsCC[c1-1][c2-1][1].max() + ": \n");

                        commons.log("prX avg "+ statisticsCC[c1-1][c2-1][2].avg() + "  std "+ statisticsCC[c1-1][c2-1][2].std() + "  min "+ statisticsCC[c1-1][c2-1][2].min() + "  max "+ statisticsCC[c1-1][c2-1][2].max() + ": \n");
                        commons.log("prY avg "+ statisticsCC[c1-1][c2-1][3].avg() + "  std "+ statisticsCC[c1-1][c2-1][3].std() + "  min "+ statisticsCC[c1-1][c2-1][3].min() + "  max "+ statisticsCC[c1-1][c2-1][3].max() + ": \n");
                    }
                    else {
                        commons.log("Cameras "+ c1 + "->"+ c2 +": NULL\n");
                    }
                }
            }

            return true;
        } catch (SQLException ex) {
            commons.error(this, ex.getMessage());
            Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }


    /**
     * Non-normalized likelihood computation of 2 objects in overlapping camera FOV
     * Only spatial dimension is taken in account (time is supposed to be equal).
     * @param cam1
     * @param cam2
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @return 0 if not overlapping, the probability else (a small number 0.1-0.8)
     */
    public double testVerification(int cam1, int cam2, int x1, int y1, int x2, int y2) {
        cam1 -= 1; // pro jednoduche adresovani poli
        cam2 -= 1;

        if (representersCC[cam1][cam2][0] == null || representersCC[cam1][cam2][1] == null ||
            representersCC[cam2][cam1][0] == null || representersCC[cam2][cam1][1] == null ||
            statisticsCC[cam1][cam2][0].count() < 2 || statisticsCC[cam1][cam2][1].count() < 2 ||
            statisticsCC[cam2][cam1][2].count() < 2 || statisticsCC[cam2][cam1][3].count() < 2) { // tohle neni uplny check, ale tak nejak vzdycky staci
            return 0;
        }

        // domain check ... definicni obor, jinde je to nedefinovane
        if (x1 < statisticsCC[cam1][cam2][0].min() || x1 > statisticsCC[cam1][cam2][0].max() ||
            y1 < statisticsCC[cam1][cam2][1].min() || y1 > statisticsCC[cam1][cam2][1].max() ||
            x2 < statisticsCC[cam2][cam1][0].min() || x2 > statisticsCC[cam2][cam1][0].max() ||
            y2 < statisticsCC[cam2][cam1][1].min() || y2 > statisticsCC[cam2][cam1][1].max()   ) {
            return 0;
        }
        // TODO: pravdepodobnostne by to bylo asi lepsi, ale ted nevim jak to zkombinovat
        // double ddx = NaiveBayes.normal(x1, statisticsCC[cam1][cam2][0].avg(), statisticsCC[cam1][cam2][0].std());
        // double ddy = NaiveBayes.normal(y1, statisticsCC[cam1][cam2][1].avg(), statisticsCC[cam1][cam2][1].std());

        GVector sampleC1 = new GVector(2);
        sampleC1.setElement(0, x1);
        sampleC1.setElement(1, y1);

        double dfx = Math.abs(x2 - representersCC[cam1][cam2][0].eval(sampleC1));
        double dfy = Math.abs(y2 - representersCC[cam1][cam2][1].eval(sampleC1));

        double nx = NaiveBayes.normal(dfx, statisticsCC[cam1][cam2][2].avg(), statisticsCC[cam1][cam2][2].std());
        double ny = NaiveBayes.normal(dfy, statisticsCC[cam1][cam2][3].avg(), statisticsCC[cam1][cam2][3].std());

        return Math.sqrt(nx*ny);
    }


    /**
     * Counts sum of testVerification() probabilities
     * @param dataset
     * @param video
     * @param cam1
     * @param cam2
     * @param track1
     * @param track2
     * @return sum of probabilities of beeing an experiment in two cameras with overlapping fields of view
     */
    public double trackVerification(int dataset, int video, int cam1, int cam2, int track1, int track2) {
        try {
            Statement tracksStmt = commons.conn.createStatement();
            String tracksQuery = "SELECT t1.\"time\", t1.position[0], t1.position[1], t2.position[0], t2.position[1] \n"
                + " FROM sunar.processed t1 JOIN sunar.processed t2 ON (t1.dataset=t2.dataset AND t1.video=t2.video AND t1.camera<>t2.camera AND t1.\"time\"=t2.\"time\")\n" 
                + " WHERE t1.dataset="+ dataset +" AND t1.video="+ video +" AND t1.camera="+ cam1 +" AND t2.camera="+ cam2 +" AND t1.track="+ track1 +" AND t2.track="+ track2 +" \n"
                + "ORDER BY t1.\"time\" \n";
            // System.out.println(tracksQuery);
            ResultSet tracksRs = tracksStmt.executeQuery(tracksQuery);
            
            int cnt = 0;
            double sum = 0;
            while (tracksRs.next()) {
                final int time = tracksRs.getInt(1);
                final int x1 = tracksRs.getInt(2);
                final int y1 = tracksRs.getInt(3);
                final int x2 = tracksRs.getInt(4);
                final int y2 = tracksRs.getInt(5);
                
                double probCC = testVerification(cam1, cam2, x1, y1, x2, y2);
                sum += probCC;
                cnt++;
            }
            if (cnt == 0) return 0;

            double res = sum/Math.sqrt(cnt); // (c) J.f.C - zalezi to na delce, ale ne tak moc
            // commons.log("d:"+ dataset + " v:"+ video +" c:"+ cam1 + "-"+ cam2 +" t:"+ track1 +"-" + track2 +" probsCC:"+ res +"\n");

            return res;
        } catch (SQLException ex) {
            commons.error(this, ex.getMessage());
            Logger.getLogger(SunarIntegration.class.getName()).log(Level.SEVERE, null, ex);
            return 0; // pokud se neco pos, porod 0, to je lepsi nez kdyz by to sletelo
        }
    }


    /**
     * Asigns processed tracks to annotations (i-Lids based)
     * @return sucess
     */
    public boolean assignAnnotations() {

        try {
            Statement tracksStmt = commons.conn.createStatement();
            Statement annotsStmt = commons.conn.createStatement();
            Statement tracksUpdateStmt = commons.conn.createStatement();

            // clean
            String updateStr = "UPDATE ONLY sunar.tracks SET object=NULL \n";
            //        + " WHERE experiment < 20 -- // TODO: magic constant 20 (No. of objects @ annotations)";
            commons.logTime(updateStr);
            tracksUpdateStmt.executeUpdate(updateStr);
            
            String annotsQuery = "SELECT dataset, video, camera, \"offset\", track, \"object\", firsts[1], lasts[1]\n"
                    + " FROM ONLY sunar.annotation_tracks\n"
                    + " ORDER BY dataset, video, camera, track";
            // System.out.println(tracksQuery);
            ResultSet annotsRs = annotsStmt.executeQuery(annotsQuery);

            //
            // go through tracks (27 000 @ dataset 2 in 49:23 (minutes) :)
            //
            while (annotsRs.next()) {
                final int anDataset = annotsRs.getInt(1);
                final int anVideo = annotsRs.getInt(2);
                final int anCamera = annotsRs.getInt(3);
                final int anOffset = annotsRs.getInt(4);
                final int anTrack = annotsRs.getInt(5);
                final int anObject = annotsRs.getInt(6); // not null?
                final int anFirst = annotsRs.getInt(7);
                final int anLast = annotsRs.getInt(8);

                // DEBUG
                commons.log("D" + anDataset + " V" + anVideo + " C" + anCamera + " T" + anTrack + " ... ");

                // count overlapping processed tracks // ann.dataset, ann.experiment, ann.video, ann.camera, ann.track AS antrack,
                String tracksQuery = "SELECT proc.track AS proctrack, track.firsts[1], track.lasts[1], sum(sunar.overlaps(ann.position, ann.size, proc.position, proc.size)), min(proc.time), max(proc.time)\n"
                        + "  FROM ONLY sunar.processed AS proc JOIN ONLY sunar.annotations AS ann\n"
                        + "       ON (ann.dataset=proc.dataset AND ann.video=proc.video AND ann.camera=proc.camera AND (proc.time >= ann.time-2 AND proc.time <= ann.time+2)), \n" + // TODO: kouknout na ostatni a predelat +-2
                        "       ONLY sunar.tracks AS track\n"
                        + " WHERE track.dataset=proc.dataset AND track.video=proc.video AND track.camera=proc.camera AND track.track=proc.track AND\n"
                        + "       sunar.overlaps(ann.position, ann.size, proc.position, proc.size) > 0.01\n" + // TODO: tady se to jen pocita, tak muze byt tak malo, jinak musi byt aspon 0.15
                        "       AND ann.dataset=" + anDataset + " AND ann.video=" + anVideo + " AND ann.camera=" + anCamera + " AND ann.track=" + anTrack + "\n"
                        + " GROUP BY ann.dataset, ann.object, ann.video, ann.camera, ann.track, proc.track, track.firsts[1], track.lasts[1]\n"
                        + " HAVING sum(sunar.overlaps(ann.position, ann.size, proc.position, proc.size)) > 0.5 \n" + // HINT: takhle jsem to poresil - musi tam byt aspon 3 po sobe s aspon 0.2 prekrytim, jinak je to blbost
                        " ORDER BY ann.dataset ASC, ann.video ASC, ann.camera ASC, ann.track ASC, sum(sunar.overlaps(ann.position, ann.size, proc.position, proc.size)) DESC;\n";
                // commons.logTime(tracksQuery);
                ResultSet tracksRs = tracksStmt.executeQuery(tracksQuery);

                // toz, tuna budou trajektorie, co se skamaradi s anotaci
                ArrayList<int[]> friends = new ArrayList<int[]>();

                // while neni zaplnena trajektorie
                while (tracksRs.next()) {
                    int track = tracksRs.getInt(1);
                    int first = tracksRs.getInt(2);
                    int last = tracksRs.getInt(3);

                    float overlaps = tracksRs.getFloat(4);
                    int first_min = tracksRs.getInt(5);
                    int last_max = tracksRs.getInt(6);

                    // toz tohle bude zajimave excelit :)
                    commons.log(anDataset + ";" + anVideo + ";" + anCamera + ";" + anTrack + ";" + track + ";" + overlaps + ";" + first + ";" + first_min + ";" + last_max + ";" + last + "\n");

                    // zorganizuj si milence (trajektorie, aby si ...)
                    SunarIntegration.nelezousidozeli(friends, track, first, last);
                }

                // toz a udelej si update... jestli mas nekoho tenhle tyden
                if (!friends.isEmpty()) {
                    String in = "";
                    for (int[] friend : friends) {
                        in += "track=" + friend[0] + " OR ";
                    }

                    in = in.substring(0, in.length() - 3);
                    updateStr = "UPDATE ONLY sunar.tracks SET object=" + anObject
                            + " WHERE dataset=" + anDataset + " AND video=" + anVideo + " AND camera=" + anCamera
                            + " AND (" + in + ")";
                    commons.logTime(updateStr);
                    tracksUpdateStmt.executeUpdate(updateStr);
                    // DEBUG
                    commons.log("OK\n");
                } else { // tak to je smula - zalogovat o co slo, kouknout na HMI
                    commons.log("NO annotations assigned at " + anDataset + ";" + anVideo + ";" + anCamera + ";" + anTrack + "\n");
                }
            } // go through tracks

            // TODO: tohle udela automaticky, co jsem musel predtim delat rucne... kouknout, jestli to mam delat... a kouknout, jestli sedi ty datasety (TODO: udelat dotazem...)
            Statement stmt = commons.conn.createStatement();
            String SQLquery = "UPDATE sunar.videos SET use='DEV09'";
            System.out.println(SQLquery);
            int updates = stmt.executeUpdate(SQLquery);   
            commons.logTime(SQLquery + " updated: " + updates);

            stmt = commons.conn.createStatement();
            SQLquery = "UPDATE sunar.videos SET use='EVAL09' \n"
                    + " WHERE dataset=1 OR dataset=5 OR dataset=8 OR dataset=11";
            System.out.println(SQLquery);
            updates = stmt.executeUpdate(SQLquery);
            commons.logTime(SQLquery + " updated: " + updates);            

            
            // tohle je celkem destruktivni, ale jestli to posem nezhucelo a nezaloholo se, tak snad OK...
            stmt = commons.conn.createStatement();
            SQLquery = "DROP VIEW IF EXISTS sunar.training_cameras";
            System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);

            SQLquery = "DROP TABLE IF EXISTS sunar.training_handovers_cache";
            System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);

            commons.logTime("Building sunar.training_handovers_cache. ");
            SQLquery = "CREATE TABLE sunar.training_handovers_cache AS (SELECT * FROM sunar.training_handovers)";
            System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);

            SQLquery = "GRANT ALL ON TABLE sunar.training_handovers_cache TO public";
            System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);

            SQLquery = "CREATE OR REPLACE VIEW sunar.training_cameras AS \n"
                    + " SELECT h.camera1, count(*) AS annotated_trakcs \n"
                    + "   FROM sunar.training_handovers_cache h \n"
                    + "   JOIN sunar.videos v ON h.dataset = v.dataset AND h.video = v.video AND h.camera1 = v.camera \n"
                    + "  WHERE v.use::text ~~ 'DEV09'::text \n"
                    + "  GROUP BY h.camera1 \n"
                    + "  ORDER BY h.camera1";
            System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);

            SQLquery = "GRANT ALL ON TABLE sunar.training_cameras TO public";
            System.out.println(SQLquery);
            stmt.executeUpdate(SQLquery);


            // exit nicely
            stmt.close();
            annotsStmt.close();
            annotsStmt.close();
            tracksUpdateStmt.close();

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
     * Tohel je funkce (tenmporalni logiky), ktera hlida kozate slecne milence tak, aby se nepotkali.
     * Slecna predpoklada, ze prvni si zamluvi schuzku nejschopnejsi jedinec (napriklad vikend v Alpach)
     * a ostatni budou vyplnovat prazdny cas (a prostor ... o prestavce na kopirce).
     * Pokud ma cas, tak se ji pridaji do kalendare (ArrayList<int[3]>).
     * @param friends kalendar s milenci
     * @param track identifikator souloznika
     * @param first od kdy chce
     * @param last do kdy chce
     * @return muze?
     */
    static boolean nelezousidozeli(ArrayList<int[]> friends, int track, int first, int last) {

        // toz a udelej si update... tento tyden uz mas plno
        for (int[] f : friends) {
            if ((first <= f[1] && last <= f[1]) || (first >= f[2] && last >= f[2])); // zatim jeste muze
            else {
                return false;  // chlapec narazil na silnejsiho jedince, co si slecnu zabookoval predtim :(
            }
        }

        int[] muze = {track, first, last};
        friends.add(muze);  // tak jo, je tam...

        return true;
    }
}
