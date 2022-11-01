
package sunarvrm;

import jama.Matrix;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Wildcat Track experiment ... always belongs to a sunar.track row
 * Wildcat (samohonka) of the Hibernate experiment/relational persistence and query service
 * @author chmelarp
 */
public class SunarTrack {
    public final int dataset;
    public final int video;
    public final int camera;
    public final int track;
    public int experiment = 0;
    public int offset = 0;
    public Matrix firsts = null;
    public Matrix lasts = null;
    public Matrix avgs = null;
    public Matrix stdevs = null;
    public Matrix sums = null;
    public Matrix avgcolors = null;
    public Matrix stdcolors = null;

    // list of the follownig tracks
    public List<SunarTrack> follows = null;
    public double bestProb = 0.0;   // this gives you an idea of beeing the succesor of the previous track

    Commons commons = null;

    /**
     * Constructor (simplified)
     * @return ... an SQLException if false
     * @param commons
     * @param dataset
     * @param video
     * @param camera
     * @param track
     */
    public SunarTrack(Commons commons, int dataset, int video, int camera, int track) throws SQLException {
        this.commons = commons;

        this.dataset = dataset;
        this.video = video;
        this.camera = camera;
        this.track = track;

        Statement stmt = commons.conn.createStatement();
        String query = "SELECT * FROM ONLY sunar.tracks \n" +
                " WHERE dataset="+ dataset +" AND video="+ video +" AND camera="+ camera +" AND track="+ track +" \n " +
                " LIMIT 1";
        ResultSet rs = stmt.executeQuery(query);

        // doladuj
        if (rs.next()) {
            experiment = rs.getInt("experiment");
            offset = rs.getInt("offset");
            firsts = new Matrix(rs.getString("firsts"));
            lasts = new Matrix(rs.getString("lasts"));
            avgs = new Matrix(rs.getString("avgs"));
            stdevs = new Matrix(rs.getString("stdevs"));
            sums = new Matrix(rs.getString("sums"));
            avgcolors = new Matrix(rs.getString("avgcolors"));
            stdcolors = new Matrix(rs.getString("stdcolors"));
        }
    }

    /**
     * Constructor (simplified)
     * @return ... an SQLException if false
     * @param commons
     * @param dataset
     * @param video
     * @param camera
     * @param track
     */
    public SunarTrack(Commons commons, ResultSet rs) throws SQLException {
        this.commons = commons;

        dataset = rs.getInt("dataset");
        video = rs.getInt("video");
        camera = rs.getInt("camera");
        track = rs.getInt("track");

        experiment = rs.getInt("experiment");
        offset = rs.getInt("offset");
        firsts = new Matrix(rs.getString("firsts"));
        lasts = new Matrix(rs.getString("lasts"));
        avgs = new Matrix(rs.getString("avgs"));
        stdevs = new Matrix(rs.getString("stdevs"));
        sums = new Matrix(rs.getString("sums"));
        avgcolors = new Matrix(rs.getString("avgcolors"));
        stdcolors = new Matrix(rs.getString("stdcolors"));
    }



    /**
     * Persistently and RECURSIVELY sets an experiment to the track...
     * // TODO: doesnt work in the proper order... probs might be litle different
     * Do not use before you know if there is a proper experiment (cannot be taken back)!!!
     * @param experiment
     */
    public void makeObjectPersistent(int experiment) throws SQLException {
        this.experiment = experiment;

        if(this.follows != null) { // may recurse!
            for (SunarTrack tr : this.follows) {
                tr.makeObjectPersistent(experiment);
            }
        }

        Statement stmt = commons.conn.createStatement();
        String query = "UPDATE ONLY sunar.tracks SET \"experiment\"="+ experiment +", prob="+ bestProb +" \n" +
                " WHERE dataset="+ dataset +" AND video="+ video +" AND camera="+ camera +" AND track="+ track +" \n ";
        stmt.executeUpdate(query);
    } // setObject

    /**
     * RECURSIVELY sets the best probabilities (sums the following first)
     * Do not use before you know if there is a proper experiment (cannot be taken beck)!!!
     */
    public double setBestProbs() throws SQLException {
        if(this.follows != null) {
            // napred ti dalsi (pocita se od konce)
            for (SunarTrack tr : this.follows) {
                this.bestProb += tr.setBestProbs();
            }
        } // else mi zustane moje prob.

        return this.bestProb;
    } // setBestProbs

    @Override
    public String toString() {
        return "D"+ dataset +" V"+ video +" C"+ camera +" T"+ track +" F"+ (int)firsts.get(0) +" E"+ experiment;
    }

    /**
     * Prints all trajectories recursively, idented by tabs
     * @return
     */
    public String toStringRecurse() {
        String res = "";
        res += this.toString() + "  %" + this.bestProb + "\n";

        if (follows != null) {
            for (SunarTrack f : follows) {
                res += f.toStringRecurse(1);
            }
        }
        return res;
    }

    private String toStringRecurse(int level) {
        String res = "";
        for (int i = 1; i < level; i++) res += "|     ";
        res += "|---- " + this.toString() + "  %" + this.bestProb + "\n";

        if (follows != null) {
            for (SunarTrack f : follows) {
                res += f.toStringRecurse(level+1);
            }
        }
        return res;
    }

}
