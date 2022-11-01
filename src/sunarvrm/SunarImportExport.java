package sunarvrm;

import jama.Matrix;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jdom2.DataConversionException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

/**
 *
 * @author chmelarp
 */
public class SunarImportExport {
    Commons commons = null;

    public String  expidOutput = "/home/chmelarp/Projects/sunar/2010/output";     // TODO: make a nice GUI!

    /** EXP-ID */
    public String  expidSite = "BrnoUT";
    public String  expidYear = "2012";
    public static String[] expidSetups = {"MCSPT", "SCSPT", "CPSPT"};
    public String  expidSetup = "??SPT"; // expidSetups[0];
    public static String[] expidUses = {"DEV09", "EVAL09", "DRYRUN09"};
    public String  expidUse = "???09"; // expidUses[1];
    public String  expidSysId = "p-SUNAR"; //  It is to begin with p- for a primary system or with c- for any contrastive systems
    public int     expidVersion = 1;

    // SED 2012
    public String[] expidTasks = {"retroED", "interactiveED"};
    public String expidTask = "retroactiveED"; // WRONG!!! must be [ interactiveED | retroED ]
    public String expidData = "EVAL12";  // [ DEV12 | EVAL12 ]
    public String expidLang = "ENG";
    public String expidInput = "s-camera";


    public SunarImportExport(Commons c) {
        this.commons = c;
    }

    public SunarImportExport(Commons c, int version) {
        this.commons = c;
        this.expidVersion = version;
    }


    public String expidID() {
        return expidSite +"_"+ expidYear +"_"+ expidSetup +"_"+ expidUse +"_ENG_"+ expidSysId +"_"+ expidVersion;
    }

    /**
     * EXP-ID structure:
     * EXP-ID ::= <SITE>_<YEAR>_<TASK>_<DATA>_<LANG>_<INPUT>_<SYSID>_<VERSION>
     * <SITE> ::= expt | short name of participant’s site
     * The special SITE code “expt” is used in the EXP-ID to indicate a reference annotation.
     * <YEAR> ::= 2012
     * <TASK> ::= interactiveED | retroED
     * <DATA> ::= DEV12 | EVAL12
     * <LANG> ::= ENG
     * <INPUT> ::= s-camera
     * <SYSID> ::= a site-specified string (that does not contain underscores) designating the system used
     *          It is to begin with p- for a primary system or with c- for any contrastive systems.
     * <VERSION> ::= 1..n (with values greater than 1 indicating multiple runs of the same experiment/system)
     *
     * @return EXP-ID
     */
    public String expidID_SED() {
        return expidSite +"_"+ expidYear +"_"+ expidTask +"_"+ expidData +"_"+ expidLang +"_"+ expidInput +"_"+ expidSysId +"_"+ expidVersion;
    }


    /**
     * Parse the filename MCTTR0201a.mov.deint.mpeg or MCTTR0201a.ss.xml
     * @param filename
     * @return 0 if failed
     */
    public static Integer getFilenameDataset(String filename) {
        int dataset = 0;
        try {
            dataset = Integer.parseInt(filename.substring(5, 7));
            // camera = Integer.parseInt(filename.substring(7, 9));
            // video = (int)filename.charAt(9)  - (int)'a'+1;
            // track = 1; // to be incremented
        } catch (Exception e) {
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
        }

        return dataset;
    }

    /**
     * Parse the filename MCTTR0201a.mov.deint.mpeg or MCTTR0201a.ss.xml
     * @param filename
     * @return 0 if failed
     */
    public static Integer getFilenameCamera(String filename) {
        int camera = 0;
        try {
            camera = Integer.parseInt(filename.substring(7, 9));
        } catch (Exception e) {
            // commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            return 0;
        }

        return camera;
    }

    /**
     * Parse the filename MCTTR0201a.mov.deint.mpeg or MCTTR0201a.ss.xml
     * @param filename
     * @return 0 if failed
     */
    public static Integer getFilenameVideo(String filename) {
        int video = 0;
        try {
            video = (int)filename.charAt(9)  - (int)'a'+1;
        } catch (Exception e) {
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
        }

        return video;
    }


    public boolean exportExperiments(List<Integer> expTracks) {

        for (Integer expTrack : expTracks) {
            try {
                Statement exptsStmt = commons.conn.createStatement();
                Statement framespanStmt = commons.conn.createStatement();
                Statement expStmt = commons.conn.createStatement();
                
                // vybal na me co je z NISTu
                String exptsQuery = "SELECT ex.track, tr.dataset, tr.video, tr.camera, ex.tracking_trial_id, ex.exp_sysid, ex.\"version\", ex.setup, ex.\"use\" \n"+
                            "  FROM sunar.experiments AS ex \n"+
                            "  JOIN sunar.evaluation_tracks AS tr  \n"+
                            "    ON (ex.track=tr.track)  \n"+
                            " WHERE ex.track="+expTrack;
                // System.out.println(tracksQuery);
                ResultSet exptsRs = exptsStmt.executeQuery(exptsQuery);

                while (exptsRs.next()) {
                    final int expDataset = exptsRs.getInt("dataset");
                    final int expVideo = exptsRs.getInt("video");
                    final int expCamera = exptsRs.getInt("camera");
                    final String expTrialId = exptsRs.getString("tracking_trial_id");

                    this.expidSysId = exptsRs.getString("exp_sysid");
                    // this.expidVersion = exptsRs.getInt("version"); // this is different
                    this.expidSetup = exptsRs.getString("setup");
                    this.expidUse = exptsRs.getString("use");
                    int expObject = 0;

                    // find starter and experiment id
                    String expQuery = "SELECT \"object\" FROM ONLY sunar.evaluation_tracks \n" +
                            " WHERE dataset="+ expDataset +" AND video="+ expVideo +" AND track="+ expTrack +" \n" +
                            "   AND object IS NOT NULL \n" +
                            " LIMIT 1";
                    ResultSet expRs = expStmt.executeQuery(expQuery);
                    if (expRs.next()) {
                        expObject = expRs.getInt("object");
                    }
                    else { // tohle by neslo
                        commons.error(this, "Cannot find object id for track " + expTrack);
                        continue;
                    }

                    // priprav dokument...
                    // vydegeneruj jmeno souboru
                    DecimalFormat format = new DecimalFormat();
                    format.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
                    format.setMinimumIntegerDigits(2);
                    format.setMaximumFractionDigits(0);

                    // MCTTR0101a
                    char vid = (char) ((char) expVideo + (char) 'a' - (char) 1);
                    String name = "MCTTR"+format.format(expDataset)+format.format(expCamera)+vid;
                    String outputDir = this.expidOutput +"/"+ this.expidID();
                    File viperFile = new File(outputDir +"/"+ this.expidSetup +"/" + expTrialId +"/"+ name +".xml");

                    // zkopiruj ten sample file
                    this.copyFile(new File(this.expidOutput +"/../clear.xml"), viperFile);
                    // copy the <EXP-ID>.txt file
                    this.copyFile(new File(this.expidOutput +"/../sunar.txt"), new File(outputDir +"/"+ this.expidID() +".txt"));

                    SAXBuilder builder = new SAXBuilder();
                    Document doc = null;
                    try {
                        doc = builder.build(viperFile);
                    } catch (IOException f) {
                        commons.error(this, "XML "+ viperFile.getAbsolutePath() +"\n"+ f);
                        continue;
                    } catch (JDOMException f) {
                        commons.error(this, "XML "+ viperFile.getAbsolutePath() +"\n"+ f);
                        continue;
                    }

                    // korenovy element viper
                    Element viper = doc.getRootElement();
                    Namespace ns = viper.getNamespace();
                    Namespace dataNs = viper.getNamespace("data");

                    // skip config, read data
                    Element data = viper.getChild("data", ns);

                    // load source files (possibly more)
                    Element sourcefile = data.getChild("sourcefile", ns);
                    sourcefile.setAttribute("filename", name + ".mov.deint.mpeg");

                    // select evaluation framespan
                    int firstFrame = 0;
                    int lastFrame = 0;
                    String framespanAll = "";
                    String framespanObj = "";
                    // select jejich framespan
                    String framespanQuery = "SELECT DISTINCT \"time\" \n" +
                            " FROM ONLY sunar.evaluation_states AS ev \n"+
                            " WHERE track="+ expTrack +" AND dataset="+ expDataset +" AND video="+ expVideo +" AND camera="+ expCamera +"\n"+
                            " ORDER BY \"time\"";
                    ResultSet framespanRs = framespanStmt.executeQuery(framespanQuery);
                    while (framespanRs.next()) {
                        int frsp = framespanRs.getInt(1);

                        if (firstFrame == 0) firstFrame = frsp;
                        lastFrame = frsp;
                        framespanAll += frsp +":"+ frsp +" ";
                    } // framespan
                    framespanAll = framespanAll.substring(0, framespanAll.length()-1);

                    // dopln delku videa (ach jo)
                    Element file = sourcefile.getChild("file", ns);
                    List<Element> attribs = file.getChildren("attribute", ns);
                    for (Element attrib : attribs) {
                        if (attrib.getAttributeValue("name").compareToIgnoreCase("NUMFRAMES") == 0) {
                            Element dvalue = new Element("dvalue", dataNs);
                            dvalue.setAttribute("value", String.valueOf(lastFrame));
                            attrib.addContent(dvalue);
                        }
                    }

                    // dopln elementy object - prvne PERSON (a jeho LOCATION)
                    Element objectPerson = new Element("object", ns);
                    Element location = new Element("attribute", ns);
                    location.setAttribute("name", "LOCATION");
                    framespanObj = "";


                    // TODO: pro priste udelat jinak ty experimenty (dat tam novou tabulku... je nutne 2-3 - annot, eval, proc a prob)

                    // hrn vysledky (sunar.states) do me, at je po me...
                    String statesQuery = "SELECT ev.\"time\", proc.track AS proctrack, proc.position, proc.size, proc.prob \n" +
                            "  FROM ONLY sunar.processed AS proc JOIN ONLY sunar.evaluations AS ev \n"+
                            "    ON (ev.dataset = proc.dataset AND ev.video = proc.video AND ev.camera = proc.camera AND (ev.time = proc.time OR ev.time = (proc.time+1))) \n" +
                            " WHERE proc.\"experiment\"="+ expTrack +" AND ev.track="+ expTrack +" \n" + // tady to ma posunuty vyznam, ale co uz...
                            "   AND ev.dataset="+ expDataset +" AND ev.video="+ expVideo +" AND ev.camera="+ expCamera +"\n"+
                            " ORDER BY ev.\"time\", proc.prob";
                    ResultSet statesRs = expStmt.executeQuery(statesQuery);

                    // fcil to projdu... je mozne, ze tam bude 1 cas vicekrat, potom budu na ty nasledujici srat pekne z vysoka, nemaji narok
                    int firstTime = 0; // panic je nanic (fakt, jen ho porovnavam, abych mu mohl priradit slecnu a stejne nic nebude)
                    int lastTime = 0;

                    // projdi vysledky
                    while (statesRs.next()) {
                        final int time = statesRs.getInt("time");

                        if (time == lastTime) continue; // na tuhle pozicu jsem uz ulozil silnejsiho samce
                        else lastTime = time;
                        if (firstTime == 0) firstTime = time;

                        framespanObj += time +":"+ time +" ";

                        // nacti umisteni (na prob kalej)
                        // BACHA... oni to ukladaji x1, y1 a ja sX, sY
                        Matrix position = new Matrix(statesRs.getString("position"));
                        Matrix size = new Matrix(statesRs.getString("size"));

                        int trW = (int)Math.round(size.get(0));
                        int trH = (int)Math.round(size.get(1));
                        int trX = (int)position.get(0) - trW/2;
                        int trY = (int)position.get(1) - trH/2;
                        // they have added checker for my fake values in F4DE-b7 :))
                        trW = (trW <= 0)? 1 : trW;
                        trH = (trH <= 0)? 1 : trH;
                        trX = (trX < 0)? 0 : trX;
                        trY = (trY < 0)? 0 : trY;
                        // TODO: jestli bude vic, nez 720x576 ...

                        // toz a hod to do teho PDF
                        // <data:bbox framespan="7545:7545" x="13" y="137" width="103" height="298"/>
                        Element bbox = new Element("bbox", dataNs);
                        bbox.setAttribute("framespan", time +":"+ time);
                        bbox.setAttribute("x", String.valueOf(trX));
                        bbox.setAttribute("y", String.valueOf(trY));
                        bbox.setAttribute("width", String.valueOf(trW));
                        bbox.setAttribute("height", String.valueOf(trH));

                        location.addContent(bbox);
                    } // inserting data:bbox

                    // nenasli, takova hokna a na praseci chripku...
                    if (framespanObj.length() == 0) ;
                    else {
                        framespanObj = framespanObj.substring(0, framespanObj.length()-1);
                        objectPerson.setAttribute("name", "PERSON");
                        objectPerson.setAttribute("id", String.valueOf(expObject));
                        objectPerson.setAttribute("framespan", framespanObj);

                        // add it to the existing XML
                        objectPerson.addContent(location);
                        sourcefile.addContent(objectPerson);
                    }

                    // set I-FRAMES
                    Element objectIFframes = new Element("object", ns);
                    objectIFframes.setAttribute("name", "I-FRAMES");
                    objectIFframes.setAttribute("id", "0");
                    objectIFframes.setAttribute("framespan", framespanAll);
                    sourcefile.addContent(objectIFframes);

                    // set FRAME (ojojojojoj, chudak ja, takovy ptakoviny)
                    Element objectFframe = new Element("object", ns);
                    objectFframe.setAttribute("name", "FRAME");
                    objectFframe.setAttribute("id", "0");
                    objectFframe.setAttribute("framespan", framespanAll);
                    /* FRAME - the validator doesnt find this valid
                    Element bvalue = new Element("bvalue", dataNs);
                    bvalue.setAttribute("framespan", firstFrame +":"+ lastFrame);
                    bvalue.setAttribute("value", "true");
                    objectFframe.getChild("attribute", ns).addContent(bvalue);
                    */
                    sourcefile.addContent(objectFframe);

                    // write a nice XML...Buff
                    BufferedWriter writer = new BufferedWriter(new FileWriter(viperFile));
                    org.jdom2.output.Format xMLformat = org.jdom2.output.Format.getPrettyFormat();
                    xMLformat.setEncoding("UTF-8");
                    XMLOutputter serializer = new XMLOutputter(xMLformat);
                    serializer.output(doc, writer); // System.out

                    writer.flush();
                    writer.close();

                }

            } catch (SQLException e) {
                commons.error(this, e.getMessage());
                Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                continue;
            } catch (Exception e) { // for sure... log
                commons.error(this, e.getMessage());
                Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                continue;
            }

        } // 4 expTracks

        // TODO: UPDATE ONLY sunar.evaluation_tracks SET experiment=track

        return true;
    }


    
    /**
     * Imports NIST Experiment Control Files expt_*.xml (<EXP-ID>.xml)
     * @return list of experiments' datasets & objects' IDs
     */
    List<Integer> importECFs(File[] files) {

        // array list of the experiments
        List<Integer> expTracks = new ArrayList<Integer>();

        SAXBuilder builder = new SAXBuilder();
        // for file in the selected files... (no control)
        for (File file : files) {
            if (!file.getName().contains("expt_") || !file.getName().contains(".xml")) {
                commons.logTime("Skipping file " + file);
                continue;
            }

            // expt_2009_MCSPT_DRYRUN09_ENG_NIST_1.xml
            int i = file.getName().indexOf("_");
            i = file.getName().indexOf("_", i+1);
            i = file.getName().indexOf("_", i+1);
            String use = file.getName().substring(i+1, file.getName().indexOf("_", i+1));

            Document doc = null;
            try {
                doc = builder.build(file);
            } catch (IOException f) {
                commons.error(this, "XML "+ file.getAbsolutePath() +"\n"+ f);
                continue;
            } catch (JDOMException f) {
                commons.error(this, "XML "+ file.getAbsolutePath() +"\n"+ f);
                continue;
            }

            // root element ecf
            Element ecf = doc.getRootElement();
            Namespace ns = ecf.getNamespace();

            // load empty files (possibly more)
            List<Element> trials = ecf.getChildren("tracking_trial", ns);
            for (Element trial : trials) {
                String trackingTrialId = trial.getAttributeValue("id"); // MCTTR02a_MCSPT
                String trackingTrialSetup = trial.getAttributeValue("type").toUpperCase(); // mcspt -> uppercase
                // framespan?

                //
                // get the unique experiment ID (track)
                //
                int expTrack = 0;
                try {
                    Statement stmt = commons.conn.createStatement();

                    String experimentsQuery = "SELECT track\n" +
                            " FROM ONLY sunar.experiments\n" +
                            " WHERE ecf_filename='"+ file.getName() +"' AND tracking_trial_id='"+ trackingTrialId +"' AND exp_sysid='"+ expidSysId +"' AND \"version\"="+ expidVersion;
                    ResultSet experimentRs = stmt.executeQuery(experimentsQuery);

                    // if the experiment is already there... delete previously loaded data
                    if (experimentRs.next()) {
                        expTrack = experimentRs.getInt(1);

                        // clean the previous trial - states
                        experimentsQuery = "DELETE FROM ONLY sunar.evaluation_states\n" +
                                " WHERE track="+ expTrack;
                        stmt.executeUpdate(experimentsQuery);
                        // and tracks
                        experimentsQuery = "DELETE FROM ONLY sunar.evaluation_tracks\n" +
                                " WHERE track="+ expTrack;
                        stmt.executeUpdate(experimentsQuery);
                    }
                    else { // create a new experiment

                        // find the starter camera
                        int cameraStarts = 0;
                        int dataset = 0;
                        int video =  0;

                        List<Element> cameraEls = trial.getChildren("camera", ns);
                        for (Element cameraEl : cameraEls) {

                            String templateXML = cameraEl.getAttributeValue("template_xml"); // MCTTR0201a.ss.xml

                            // if it is a starter camera
                            if (cameraEl.getAttribute("target_training").getValue().toLowerCase().compareTo("true") == 0 &&
                                templateXML.toLowerCase().contains(".ss.xml")) {

                                dataset = getFilenameDataset(templateXML);
                                cameraStarts = getFilenameCamera(templateXML);
                                video = getFilenameVideo(templateXML);
                                // OR cameraStarts = (int)cameraEl.getAttribute("camid").getLongValue();
                            }
                        }

                        experimentsQuery = "SELECT nextval('sunar.experiments_seq'::regclass)";
                        experimentRs = stmt.executeQuery(experimentsQuery);

                        // create a new experiment
                        if (experimentRs.next()) {
                            expTrack = experimentRs.getInt(1);

                            experimentsQuery = "INSERT INTO sunar.experiments(dataset, video, camera_starts, track, "
                                    + "tracking_trial_id, ecf_filename, exp_sysid, \"version\", setup, use)\n" +
                                    " VALUES ("+ dataset +", "+ video +", "+ cameraStarts +", "+ expTrack +", "
                                    + "'"+ trackingTrialId +"', '"+ file.getName() +"', '"+ expidSysId +"', "+ expidVersion +", '"+ trackingTrialSetup +"', '"+ use +"')";
                            commons.logTime(experimentsQuery);
                            stmt.executeUpdate(experimentsQuery);
                        }
                        else {
                            commons.error(this, "Cannot get an experiment track id for "+ file.getName() +" ... aborting.");
                            continue;
                        }
                    }

                } catch (SQLException e) {
                    commons.error(this, e.getMessage());
                    Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                    continue;
                }



                // get the list of TTFs and SS XML files
                ArrayList<File> emptyFiles = new ArrayList<File>();

                List<Element> videos = trial.getChildren("camera", ns);
                for (Element video : videos) {
                    String filenameStr = video.getAttributeValue("template_xml");
                    // HINT: DO NOT use getFilename*()
                    String datasetStr =  filenameStr.substring(5, 7);
                    String cameraStr = filenameStr.substring(7, 9); // not necessary here
                    String videoStr = filenameStr.substring(9, 10);

                    // dodelej cestu /MCT_TR_01/MCTTR01a/
                    String pathStr = file.getParent() +"/MCT_TR_"+ datasetStr +"/MCTTR"+ datasetStr + videoStr +"/"+ filenameStr;
                    File empty = new File(pathStr);

                    // soupni ho do seznamu
                    emptyFiles.add(empty);
                } // videos

                // toz a vrat me secky IDcka objektu, co se tam nachazi
                File[] emptyFilesArr = emptyFiles.toArray(new File[emptyFiles.size()]);
                if (importSSEmptys(expTrack, emptyFilesArr)) {
                    expTracks.add(expTrack);
                }
            } // 4 trials

        } // 4 files

        // TODO: jak zajistit unikatnost, pokud by tam bylo vic objektu... ?
        return expTracks;

    }

    /**
     * Imports i-LIDS .ss.xml (Starter System) .empty.xml (empty ViPER XML file) defined at the ECF
     * @return sucess
     */
    boolean importSSEmptys(int expTrack, File[] files) {

        Statement stmt = null;
        SAXBuilder builder = new SAXBuilder();

        try {
            stmt = commons.conn.createStatement();

        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
        }

        // for file in the selected files... (no control)
        for (File file : files) {
            if (!file.getName().contains(".ss.xml") && !file.getName().contains(".empty.xml")) {
                commons.logTime("Skipping file " + file);
                continue;
            }

            Document doc = null;
            try {
                doc = builder.build(file);
            } catch (IOException f) {
                commons.error(this, "XML "+ file.getAbsolutePath() +"\n"+ f);
                continue;
            } catch (JDOMException f) {
                commons.error(this, "XML "+ file.getAbsolutePath() +"\n"+ f);
                continue;
            }

            // korenovy element viper
            Element viper = doc.getRootElement();
            Namespace ns = viper.getNamespace();

            // skip config, read data
            Element data = viper.getChild("data", ns);

            // load source files (possibly more)
            List<Element> sourcefiles = data.getChildren("sourcefile", ns);
            for (Element sourcefile : sourcefiles) {

                // load and parse <sourcefile filename="MCTTR0201a.mov.deint.mpeg">
                String sourcefilename = sourcefile.getAttributeValue("filename");
                int dataset = getFilenameDataset(sourcefilename);
                int camera = getFilenameCamera(sourcefilename);
                int video = getFilenameVideo(sourcefilename);
                // int track = 0; // to be incremented

                // load objects - (possibly more)
                List<Element> objects = sourcefile.getChildren("object", ns);
                for (Element object : objects) {

                    // if a PERSON (omit I-FRAMES)
                    if (object.getAttributeValue("name").compareToIgnoreCase("PERSON") == 0) {

                        // <object name="PERSON" id="7" framespan="7545:7545 ... 7685:7685">
                        int  objectId = Integer.parseInt(object.getAttributeValue("id"));
                        String[] framespan = object.getAttributeValue("framespan").split("[:\\s]+");
                        int first = Integer.parseInt(framespan[0]);
                        int last = Integer.parseInt(framespan[framespan.length-1]);

                        // load attributes ... name = LOCATION, OCCLUSION (omit AMBIGUOUS, PRESENT, SYNTHETIC)
                        List<Element> attributes = object.getChildren("attribute", ns);
                        // insert states (position, size)
                        for (Element attribute : attributes) {
                            // load LOCATION
                            if (attribute.getAttributeValue("name").compareToIgnoreCase("LOCATION") == 0) {

                                /* INSERT states
                                   video integer NOT NULL,
                                   camera integer NOT NULL,
                                   video integer NOT NULL,
                                   track bigint NOT NULL,
                                   "time" integer NOT NULL,
                                   "position" point,
                                   size point,
                                   -- color integer[],
                                   -- occlusion boolean DEFAULT false
                                */
                                String insertStates = "INSERT INTO sunar.evaluation_states(dataset, camera, video, track, \"time\", \"position\", size) VALUES ";

                                // load bvalues
                                List<Element> bboxes = attribute.getChildren();
                                for (Element bbox : bboxes) {
                                    String[] time = bbox.getAttributeValue("framespan").split(":");
                                    String sx = bbox.getAttributeValue("x");
                                    String sy = bbox.getAttributeValue("y");
                                    String sw = bbox.getAttributeValue("width");
                                    String sh = bbox.getAttributeValue("height");

                                    // SERIOUS WARNING!!!
                                    // Evaluations are stored as rectangles corner(x1,y1), size(w, h)
                                    // BUT the Sunar stores center(x, y), size(w,h) in the DB
                                    int x = (int)Math.round(Double.parseDouble(sx) + Double.parseDouble(sw)/2.0);
                                    int y = (int)Math.round(Double.parseDouble(sy) + Double.parseDouble(sh)/2.0);

                                    insertStates += "("+ dataset +", "+ camera +", "+ video +", "+ expTrack +", "+ time[0] +", '("+ x +", "+ y +")', '("+ sw +", "+ sh +")'), ";
                                }

                                // TODO: je tam nejaka ignorace, zjisti, co je zac...

                                insertStates = insertStates.substring(0, insertStates.length()-2);
                                // commons.logTime(insertStates);
                                try {
                                    stmt.executeUpdate(insertStates);
                                } catch (SQLException e) {
                                    commons.error(this, e.getMessage());
                                    Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                                }

                            } // UPDATE, attributes OCCLUSION
                            else if (attribute.getAttributeValue("name").compareToIgnoreCase("OCCLUSION") == 0) {
                                //
                                String updateStates = "";

                                // load bvalues
                                List<Element> bvalues = attribute.getChildren();
                                for (Element bvalue : bvalues) {
                                    String[] time = bvalue.getAttributeValue("framespan").split(":");
                                    String value = bvalue.getAttributeValue("value");
                                    if (!value.contains("false")) {
                                        updateStates += "\"time\"="+ time[0] +" OR ";
                                    }
                                }

                                if (updateStates.compareTo("") != 0) {
                                   updateStates = "UPDATE sunar.evaluation_states SET occlusion=true WHERE dataset="+ dataset +" AND camera="+ camera +" AND video="+ video +" AND track="+ expTrack + " AND (" +
                                           updateStates.substring(0, updateStates.length()-3) + ")";
                                    commons.logTime(updateStates);
                                    try {
                                        stmt.executeUpdate(updateStates);
                                    } catch (SQLException e) {
                                        commons.error(this, e.getMessage());
                                        Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                                    }
                                }

                            }

                        } // attributes - LOCATION

                        /* INSERT track (finally)
                          video integer NOT NULL,
                          camera integer NOT NULL,
                          video integer NOT NULL,
                          track integer NOT NULL,
                          "object" integer,
                          -- "offset" integer,
                          "firsts" integer[],
                          "lasts" integer[],
                          -- "color[]" integer[]
                        */
                        String insertTrack = "INSERT INTO sunar.evaluation_tracks(dataset, camera, video, track, \"object\", firsts, lasts) VALUES ";
                        insertTrack += "("+ dataset +", "+ camera +", "+ video +", "+ expTrack +", "+ objectId +", ARRAY["+ first +"], ARRAY["+ last +"])";
                        // commons.logTime(insertTrack);
                        try {
                            stmt.executeUpdate(insertTrack);
                        } catch (SQLException e) {
                            commons.error(this, e.getMessage());
                            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                        }

                        // a new track maybe follows
                        // track++;
                    } // object PERSON

                    // if a FRAME
                    else if (object.getAttributeValue("name").compareToIgnoreCase("FRAME") == 0) {
                        // TODO: rozbi je!
                        String[] framespan = object.getAttributeValue("framespan").split("[:\\s]+");

                        // insert evaluation states first
                        for (int i = 0; i < framespan.length; i += 2) {
                            String insertStates = "INSERT INTO sunar.evaluation_states(dataset, camera, video, track, \"time\") \n" +
                                    "VALUES ("+ dataset +", "+ camera +", "+ video +", "+ expTrack +", "+ framespan[i] +")";
                            // commons.logTime(insertStates);
                            try {
                                stmt.executeUpdate(insertStates);
                            } catch (SQLException e) {
                                // tady to udela asi tak 5x za video... tak se nevzrusuj
                                commons.log("sunar.evaluation_states("+ dataset +", "+ camera +", "+ video +", "+ expTrack +", "+ framespan[i] +") already there\n");
                            }
                        }

                        // insert evaluation track
                        String insertTrack = "INSERT INTO sunar.evaluation_tracks(dataset, camera, video, track, firsts, lasts) VALUES \n" +
                                "("+ dataset +", "+ camera +", "+ video +", "+ expTrack +", ARRAY["+ framespan[0] +"], ARRAY["+ framespan[framespan.length-2] +"])";
                        // commons.logTime(insertTrack);
                        try {
                            stmt.executeUpdate(insertTrack);
                        } catch (SQLException e) { // vobcas to sem zajede ... se nic nedeje

                            // insert evaluation track
                            insertTrack = "UPDATE sunar.evaluation_tracks SET firsts=ARRAY["+ framespan[0] +"], lasts=ARRAY["+ framespan[framespan.length-2] +"] \n" +
                                    " WHERE dataset="+ dataset +" AND camera="+ camera +" AND video="+ video +" AND track="+ expTrack;
                            // commons.logTime(insertTrack);
                            try {
                                stmt.executeUpdate(insertTrack);
                            } catch (SQLException f) { // tuna uz rvi...
                                commons.error(this, f.getMessage());
                                commons.logTime(insertTrack);
                                Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, f);
                            }
                        }

                        // a new track maybe follows
                        //track++;
                    } // object FRAME

                } // objects

            } // sourcefiles

        } // files

        try {
            if (stmt != null) stmt.close();
        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
        }

        return true;

    } // importSSEmptys(File[] files)


    /**
     * Imports i-LIDS .clear.xml annotation data (CLEAR evaluation ViPER XML file)
     * @param files
     * @return success
     */
    boolean importAnnots(File[] files) {
        Statement stmt = null;
        SAXBuilder builder = new SAXBuilder();

        try {
            stmt = commons.conn.createStatement();
        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            return false;
        }

        // for file in the selected files... (no control)
        for (File file : files) {
            if (!file.getName().contains(".clear.xml")) {
                commons.logTime("Skipping file " + file);
                continue;
            }

            Document doc = null;
            try {
                doc = builder.build(file);
            } catch (IOException f) {
                commons.error(this, "XML "+ file.getAbsolutePath() +"\n"+ f);
                continue;
            } catch (JDOMException f) {
                commons.error(this, "XML "+ file.getAbsolutePath() +"\n"+ f);
                continue;
            }

            // korenovy element viper
            Element viper = doc.getRootElement();
            Namespace ns = viper.getNamespace();

            // skip config, read data
            Element data = viper.getChild("data", ns);

            // load source files (possibly more)
            List<Element> sourcefiles = data.getChildren("sourcefile", ns);
            for (Element sourcefile : sourcefiles) {

                // load and parse <sourcefile filename="MCTTR0201a.mov.deint.mpeg">
                String sourcefilename = sourcefile.getAttributeValue("filename");
                int dataset = getFilenameDataset(sourcefilename);
                int camera = getFilenameCamera(sourcefilename);
                int video = getFilenameVideo(sourcefilename);
                int track = 0; // to be incremented

                // load objects - (possibly more)
                List<Element> objects = sourcefile.getChildren("object", ns);
                for (Element object : objects) {

                    // if a PERSON (omit I-FRAMES, FRAME)
                    if (object.getAttributeValue("name").compareToIgnoreCase("PERSON") == 0) {

                        // <object name="PERSON" id="7" framespan="7545:7545 ... 7685:7685">
                        int objectId = Integer.parseInt(object.getAttributeValue("id"));
                        String[] framespan = object.getAttributeValue("framespan").split(" ");
                        int first = Integer.parseInt(framespan[0].split(":")[0]);
                        int last = Integer.parseInt(framespan[framespan.length-1].split(":")[1]);

                        // load attributes ... name = LOCATION, OCCLUSION (omit AMBIGUOUS, PRESENT, SYNTHETIC)
                        List<Element> attributes = object.getChildren("attribute", ns);
                        // insert states (position, size)
                        for (Element attribute : attributes) {
                            // load LOCATION
                            if (attribute.getAttributeValue("name").compareToIgnoreCase("LOCATION") == 0) {

                                /* INSERT states
                                   video integer NOT NULL,
                                   camera integer NOT NULL,
                                   video integer NOT NULL,
                                   track bigint NOT NULL,
                                   "time" integer NOT NULL,
                                   "position" point,
                                   size point,
                                   -- color integer[],
                                   -- occlusion boolean DEFAULT false
                                */
                                String insertStates = "INSERT INTO sunar.annotation_states(dataset, camera, video, track, \"time\", \"position\", size) VALUES ";

                                // load bvalues
                                List<Element> bboxes = attribute.getChildren();
                                for (Element bbox : bboxes) {
                                    String[] time = bbox.getAttributeValue("framespan").split(":");
                                    String sx = bbox.getAttributeValue("x");
                                    String sy = bbox.getAttributeValue("y");
                                    String sw = bbox.getAttributeValue("width");
                                    String sh = bbox.getAttributeValue("height");

                                    // SERIOUS WARNING!!!
                                    // Annotations are stored as rectangles corner(x1,y1), size(w, h)
                                    // BUT the Sunar stores center(x, y), size(w,h) in the DB
                                    int x = (int)Math.round(Double.parseDouble(sx)+Double.parseDouble(sw)/2.0);
                                    int y = (int)Math.round(Double.parseDouble(sy)+Double.parseDouble(sh)/2.0);

                                    insertStates += "("+ dataset +", "+ camera +", "+ video +", "+ track +", "+ time[0] +", '("+ x +", "+ y +")', '("+ sw +", "+ sh +")'), ";
                                }

                                insertStates = insertStates.substring(0, insertStates.length()-2);
                                // commons.logTime(insertStates);
                                try {
                                    stmt.executeUpdate(insertStates);
                                } catch (SQLException e) {
                                    commons.error(this, e.getMessage());
                                    Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                                }

                            } // UPDATE, attributes OCCLUSION
                            else if (attribute.getAttributeValue("name").compareToIgnoreCase("OCCLUSION") == 0) {
                                //
                                String updateStates = "";

                                // load bvalues
                                List<Element> bvalues = attribute.getChildren();
                                for (Element bvalue : bvalues) {
                                    String[] time = bvalue.getAttributeValue("framespan").split(":");
                                    String value = bvalue.getAttributeValue("value");
                                    if (!value.contains("false")) {
                                        updateStates += "\"time\"="+ time[0] +" OR ";
                                    }
                                }

                                if (updateStates.compareTo("") != 0) {
                                   updateStates = "UPDATE sunar.annotation_states SET occlusion=true WHERE dataset="+ dataset +" AND camera="+ camera +" AND video="+ video +" AND track="+ track + " AND (" +
                                           updateStates.substring(0, updateStates.length()-3) + ")";
                                    commons.logTime(updateStates);
                                    try {
                                        stmt.executeUpdate(updateStates);
                                    } catch (SQLException e) {
                                        commons.error(this, e.getMessage());
                                        Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                                    }
                                }

                            }

                        } // attributes - LOCATION

                        /* INSERT track (finally)
                          video integer NOT NULL,
                          camera integer NOT NULL,
                          video integer NOT NULL,
                          track integer NOT NULL,
                          "object" integer,
                          -- "offset" integer,
                          "firsts" integer[],
                          "lasts" integer[],
                          -- "color[]" integer[]
                        */
                        String insertTrack = "INSERT INTO sunar.annotation_tracks(dataset, camera, video, track, \"object\", firsts, lasts) VALUES ";
                        insertTrack += "("+ dataset +", "+ camera +", "+ video +", "+ track +", "+ objectId +", ARRAY["+ first +"], ARRAY["+ last +"])";
                        // commons.logTime(insertTrack);
                        try {
                            stmt.executeUpdate(insertTrack);
                        } catch (SQLException e) {
                            commons.error(this, e.getMessage());
                            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                        }

                        // a new track maybe follows
                        track++;
                    } // PERSONs
                } // objects

            } // sourcefiles

        } // files

        try {
            if (stmt != null) stmt.close();
        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            return false;
        }

        return true;

    } // void importAnnots(File[] files)


















    // ========================== SIN 2012 =====================================

    /**
     *
     * @param files
     * @return success
     */
    boolean importSVMpredicts(File[] files) {
        // for file in the selected files... (control)
        for (File file : files) {
            // this is not a predict file!
            if (!file.getName().toLowerCase().endsWith(".predict")) {
                commons.logTime("Skipping " + file.getName());
                continue;
            }
            commons.logTime("Processing "+ file.getName() +" ... ");

            // oukey doukey, here it comes, the filename parsing
            String[] fileStrs = file.getName().split("\\.");
            if (fileStrs.length < 4) {
                commons.logTime("Filename is too SHORT");
                continue;
            }

            String selection = "eeeerrrrrrrrooooooooorrrrrrrr";
            if (fileStrs[0].equals("SVMshapes")) selection = "svm_shapes";
            else if (fileStrs[0].equals("SVMtracks")) selection = "svm_tracks";

            String seqname = fileStrs[1];
            String event = fileStrs[2];

            Statement stmt, updateStmt;
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));

                String line = reader.readLine();
                if (Integer.parseInt(line.split(" ")[1]) != 1) {
                    commons.logTime("There is a wrong header: " + line);
                    continue;
                }

                // this must be handled carefully so that there is the same count and order as in the file!
                String query = "SELECT track, t1, t2\n"
                      + " FROM sin12.tracks AS t\n"
                      + " WHERE firsts IS NOT NULL AND seqname = '"+ seqname +"'\n"
                      + " ORDER BY track;\n";
                stmt = commons.conn.createStatement();
                updateStmt = commons.conn.createStatement();
                ResultSet rset = stmt.executeQuery(query);


                // read lines from the prediction file => track
                while ((line = reader.readLine()) != null) {
                    String[] lineStrs = line.split(" ");
                    double prob = Double.parseDouble(lineStrs[1]);

                    // what a track?
                    if (!rset.next()) {
                        commons.error(rset, line);
                        continue;
                    }
                    int track = rset.getInt("track");
                    int t1 = rset.getInt("t1");
                    int t2 = rset.getInt("t2");

                    // don't (be) panic?! ... there are 8:1 updates and inserts
                    String update = "UPDATE "+ commons.dataset +"."+ selection +"\n"
                                  + " SET "+ event +" = "+ prob +"\n"
                                  + " WHERE seqname = '"+ seqname +"' AND track = "+ track +";";
                    int rows = updateStmt.executeUpdate(update);
                    // if there are no rows affected ... ?
                    if (rows <= 0) {
                        String insert = "INSERT INTO "+ commons.dataset +"."+ selection +"(seqname, track, t1, t2, "+ event +")\n"
                                      + " VALUES('"+ seqname +"', "+ track +", "+ t1 +", "+ t2 +", "+ prob +");";
                        updateStmt.executeUpdate(insert);
                    }
                }

            } catch (SQLException ex) {
                Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, ex);
                commons.error(ex, SunarImportExport.class.getName());
            } catch (IOException ex) {
                Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, ex);
                commons.error(ex, SunarImportExport.class.getName());
            }
        }
        return true;
    }




    /**
     * SVMtracks.#.train exports the track as it is (excluding time) in its aggregated form
     * SVMshapes.#.train exports 3 shapes for each track (1/4 I 1/4 I 1/4 I 1/4)
     * In two files: first 3 times per track, second concatenated
     * @param file
     * @return success
     */
    boolean exportSVMeval(File file) {
        BufferedWriter writerTracks = null;
        BufferedWriter writerShapes = null;
        try {
            // this should be done by sequences because of the ammount of data...
            Statement seqStmt = commons.conn.createStatement();
            String seqQuery = "SELECT seqname "
                               + " FROM "+ commons.dataset +".sequences\n"
                               + " WHERE seqname LIKE '%MCTTR%'\n"
                               + " ORDER BY seqname";
            ResultSet seqRs = seqStmt.executeQuery(seqQuery);

            while (seqRs.next()) {
                final String seqname = seqRs.getString("seqname");

                commons.log(file.getAbsoluteFile() + " eval "+ seqname + " ...");
                writerTracks = new BufferedWriter(new FileWriter(file.getAbsoluteFile() + File.separator + "SVMtracks."+ seqname));
                writerShapes = new BufferedWriter(new FileWriter(file.getAbsoluteFile() + File.separator + "SVMshapes."+ seqname));

                // this must be handled carefully so that there is the same count and order as in the file!
                String query = "SELECT track, firsts, lasts, avgs, sums, avgcolor, avgshape, shapes\n"
                          + " FROM sin12.tracks AS t\n"
                          + " WHERE firsts IS NOT NULL AND seqname = '"+ seqname +"'\n"
                          + " ORDER BY track;\n";

                    Statement stmt = commons.conn.createStatement();
                    ResultSet rset = stmt.executeQuery(query);

                    // go through the samples
                    while (rset.next()) {
                        // int xgtf = rset.getInt("xgtf_id");
                        int track = rset.getInt("track");
                        String commentID = " #" + seqname + "-" + track;

                        String head = "1:"+ seqname.charAt(seqname.length()-1); // 1:camera

                        // pickup all bros and stick em togetha
                        String[] firstStrs = rset.getString("firsts").substring(rset.getString("firsts").indexOf(",") + 1, rset.getString("firsts").length() - 1).split(",");
                        String[] lastStrs = rset.getString("lasts").substring(rset.getString("lasts").indexOf(",") + 1, rset.getString("lasts").length() - 1).split(",");
                        String[] avgStrs = rset.getString("avgs").substring(rset.getString("avgs").indexOf(",") + 1, rset.getString("avgs").length() - 1).split(",");
                        String[] sumStrs = rset.getString("sums").substring(1, rset.getString("sums").length() - 1).split(",");
                        String[] colorStrs = rset.getString("avgcolor").substring(1, rset.getString("avgcolor").length() - 1).split(",");
                        String[] shapeStrs = rset.getString("avgshape").substring(1, rset.getString("avgshape").length() - 1).split(",");
                        String[] features = concatAll(firstStrs, lastStrs, avgStrs, sumStrs, colorStrs, shapeStrs);

                        String s1 = "";
                        for (int f=0; f < features.length; f++) {
                            s1 += " "+ (f+2) +":"+ features[f];
                        }
                        // TODO: assert the equal feature count 68!
                        writerTracks.write(head + s1 + commentID + "\n");

                        // shapes
                        // "{{95474,7031,-46673,1984,127403,-16242,23862},{875456,-371165,52522,-50352,54403,7221,4337},{145553,-15433,-6303,2073,118197,-17761,21309},{123365,6669,-10982,-9646,134691,-16158,25481},{82869,5406,-21567,9087,208900,-3210,41800},{114196,23102,-32419,-19890,207617,-15451,27955},{144837,19546,-40352,-7249,142656,-23288,20109},{100524,15909,-27874,-10665,175183,-17998,35756},{240599,-18703,7230,-306,156437,-55637,40079},{160958,41469,-30739,2423,143484,-31171,32889},{0,0,0,0,0,0,0},{250751,1561,138478,-3360,150297,-18474,-25493},{78463,5481,-16903,-5551,133984,-10409,21532},{118231,6249,-23380,-8121,127445,-15619,20982},{74300,15217,6330,-8293,246014,-29727,1848},{137478,11757,-41156,-1681,151209,-22289,25470},{174945,-7466,-22103,-3078,102090,-21010,17208},{102655,-6243,-43321,6855,90223,-2983,-525},{0,0,0,0,0,0,0},{137775,6506,-41305,-255,130481,-19522,14407},{106611,-15143,27257,-13422,92178,-1542,7346},{177102,39811,-40319,-26308,146381,-21821,20664},{438412,23491,-122118,-25002,137765,-13972,33595},{98227,-3356,-1391,1044,75384,151,-1457},{182176,-4064,-38847,-5596,56299,4014,-2734},{106539,14779,-22678,-5693,123911,-13902,15384},{75024,2366,-30379,-3093,139738,-8577,19574},{132540,-10165,-938,-1511,80410,-9187,5440},{169575,53953,-11902,-7761,121589,-20537,24705},{120071,10960,-4754,-8366,105940,-10583,11950},{73681,3329,-7800,-7006,140388,-9912,21797},{64881,2508,-19397,-4482,150883,-7558,20666},{192147,-27332,-56626,8808,118549,-22121,15302},{133330,-57144,6563,-16565,168693,-3992,44003},{117430,14218,-26723,-5276,102379,-13131,16368},{95313,9126,-14788,-5431,104667,-10402,15214},{95550,10665,-32135,-4678,126031,-10796,20602},{160104,-34996,-51382,11761,129374,-391,-12696},{121017,9682,1354,-1516,79356,-2449,20161},{149511,28764,-40510,-10975,130371,-1965,29314},{95887,10631,-8282,-14865,251171,-29232,44519},{179623,-12316,-48028,-358,113816,-19682,8411},{143704,6247,-25915,-10793,111235,-16613,12382},{63365,7208,25784,-12378,362177,18823,68464},{74667,2883,-18142,-4104,129775,-9458,14066},{108208,11950,-17659,-7622,138891,-15011,21604},{104922,-24425,-13036,-10222,150858,11490,-9332},{124326,9155,-25767,-7005,117847,-14190,11761},{70270,7781,-27377,-3747,187389,-11112,26114},{103233,12378,-42501,162,142536,-20725,27361},{126809,-39825,19885,-21070,106262,-8496,-8819},{203637,-6863,17692,-16648,91253,-13784,10554},{98522,5582,-35726,-2413,106616,-10322,15767},{66925,5236,-18514,-7532,162477,-10615,27274},{119444,8409,-26741,-9358,130740,-16940,23113},{73424,18697,-12464,-16463,305869,-6603,14126},{69338,22546,33568,-17265,401460,-24924,57257},{49472,-250,40953,-14156,423725,-22405,137429},{50442,2255,32611,-10658,321376,-18228,111929},{77456,3348,-19587,-2788,126872,-8551,14127},{26029,-2005,-9936,-641,291219,13014,-18386},{67189,5616,-13143,-7192,157005,-13489,22989},{138284,6369,-24536,-6657,134459,-22293,27037},{85139,12619,-36163,-8380,154539,-11448,30009},{58637,4200,-16373,-4425,167010,-6457,21709},{152488,-3244,-54336,826,135877,-26421,23917},{136617,14940,-34763,-13328,128466,-19788,24291},{86632,3686,-19959,-465,111627,-4968,13447},{148807,-102663,-32341,44903,118815,-9231,-6234},{89323,7246,-30849,-2784,124293,-13042,21905},{953070,-437521,359859,-232414,614873,-25075,-108517},{77190,5635,-12004,-3728,112649,-9728,16786},{93755,5430,-30030,-2215,104872,-7371,16111},{123885,-9433,5752,-2710,61053,-282,-359},{115137,-7873,1985,-3624,68089,-1332,119},{0,0,0,0,0,0,0},{0,0,0,0,0,0,0},{61043,-1680,-5258,-1689,108704,2795,1745},{123558,-5762,-39935,-3779,114414,-11253,24698},{0,0,0,0,0,0,0},{120740,-13455,6993,-3018,73457,-2730,2185},{152044,5777,-22517,-9550,107773,-20246,14635},{61728,-2425,0,0,98765,4850,0},{133218,1771,-552,-4899,82038,-218,-9106},{377332,-112248,73477,-26104,67971,-2147,-855},{153879,17082,3944,-6765,48835,-4605,3128},{168576,-14388,-44209,5548,108722,-10987,7921},{66801,12076,12862,-7638,200407,-15167,13649},{85492,506,2122,-14320,107183,-2637,6569},{100019,-10917,-9444,487,91199,3133,-8442},{94612,3540,-15396,-2301,95975,-8265,6708},{80393,4772,-9954,-4064,116442,-10006,11897},{83014,6516,-11972,-3394,118071,-12312,12130},{115031,-6946,-12032,386,73250,-3253,1423},{67629,3195,86537,-7530,391653,-28946,-20049},{62418,4336,-16096,-10311,175493,-9182,29928},{340575,-72293,-345415,72291,520789,-50360,8070},{174662,-107016,-50486,23420,125116,5149,-12654},{97111,-3170,-20437,5051,79324,-118,-4614},{469500,-703321,180332,-201189,196487,-23996,10195},{169562,-24311,-10125,-941,49055,6907,-2585},{71694,-5565,-59545,7410,157921,-1786,-10797},{93006,799,-12841,3037,78159,-2940,-2231},{58009,4903,2047,-4503,145260,-10435,16699},{121844,-2931,17257,-19574,87216,-6608,5343},{97511,-5662,-20393,3189,76382,3845,-3130},{85286,-4816,-23303,3994,88277,1981,-5875},{77997,-1113,-14476,2132,93654,-462,-5041},{0,0,0,0,0,0,0},{287920,77779,48964,-23673,70685,-26310,-12737},{320962,93630,312667,94041,397632,79322,48723},{65569,2622,-1654,-10043,155659,-6654,16381},{30887,-772,63640,-3540,460736,-14849,-11160},{294558,-74561,-110241,21830,203911,-40802,12213},{32175,-2409,-47192,2167,330415,18809,-72706},{93242,-3466,-47647,4735,105258,-1509,-4467},{88654,767,8074,466,73818,-510,-969},{75547,1766,-9330,-4962,117784,-6968,11177},{74761,3411,-5931,-5988,122298,-8328,11879},{70388,4096,-5420,-2650,109859,-7135,10318},{100431,2485,-45104,-138,115270,-7110,14602},{119852,-2471,-44401,3142,77750,-1220,941},{86962,3222,-9125,-5310,115293,-10673,10772},{55949,1358,2348,-9726,188154,-10980,21526},{57732,2457,-53528,525,210486,-13871,47454},{62848,-1437,-19579,3130,113577,-326,-5724},{59517,-2048,-23348,3884,124563,868,-8556},{87211,-2064,-12607,4796,81988,-1222,-4647},{87878,-4671,-15720,2840,80259,2608,-3461},{0,0,0,0,0,0,0},{0,0,0,0,0,0,0},{78462,-2336,-12403,3337,89010,21,-5021},{0,0,0,0,0,0,0},{53217,3192,16665,-8305,198546,-17255,12499},{88005,-9703,-34688,2561,100835,7119,-9478},{0,0,0,0,0,0,0},{102596,704,-40625,3355,98196,-7700,7742},{0,0,0,0,0,0,0},{158965,-13679,-9134,-66,46271,3301,-1769},{55859,-232,1172,-5976,127734,-2332,10700},{109887,-5697,-34110,8803,77963,-2903,-1973},{0,0,0,0,0,0,0},{83706,6320,-5204,-6027,113845,-11336,13084},{167264,-6314,-1287,-3777,62285,-3885,-3400},{111111,0,0,0,41667,0,0},{0,0,0,0,0,0,0},{272653,-7506,10497,-1408,30034,-612,1244}}"
                        String[] allShapes = rset.getString("shapes").substring(2, rset.getString("shapes").length() - 2).split("\\},\\{");
                        String shapeStr = allShapes[(int)Math.round((1.0/4.0)*allShapes.length)-1] +","
                                        + allShapes[(int)Math.round((2.0/4.0)*allShapes.length)-1] +","
                                        + allShapes[(int)Math.round((3.0/4.0)*allShapes.length)-1];
                        String[] shapes = shapeStr.split(",");

                        String s2 = "";
                        for (int f=0; f < shapes.length; f++) {
                            s2 += " "+ (f+2) +":"+ shapes[f];
                        }
                        // TODO: assert length 22
                        writerShapes.write(head + s2 + commentID + "\n");
                    }
                commons.log(" OK\n");

                writerTracks.flush();
                writerTracks.close();
                writerShapes.flush();
                writerShapes.close();
            } // while sequences

            commons.logTime("All DONE.");
        } catch (IOException ex) {
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                writerTracks.close();
                writerShapes.close();
            } catch (IOException ex) {
                Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return true;
    }



    /**
     * SVMtracks.#.train exports the track as it is (excluding time) in its aggregated form
     * SVMshapes.#.train exports 3 shapes for each track (1/4 I 1/4 I 1/4 I 1/4)
     * In two files: first 3 times per track, second concatenated
     * @param file
     * @return success
     */
    boolean exportSVMtrain(File file) {
        BufferedWriter writerTracks = null;
        BufferedWriter writerShapes = null;
        try {
            // Create the bidirectional map having event ids
            BidirectionalMap<String, Integer> eventsMap = new BidirectionalMap<String, Integer>();
            Statement eventStmt = commons.conn.createStatement();
            String eventQuery = "SELECT * FROM "+ commons.dataset +".events";
            ResultSet eventRset = eventStmt.executeQuery(eventQuery);

            // cycle throught all events available
            while (eventRset.next()) {
//            eventsMap.put(rset.getString("event"), rset.getInt("id"));
//            }
//            for (Map.Entry<String, Integer> entry : eventsMap.getKeyToValueMap().entrySet()) {
//                String event = entry.getKey();
//                Integer eventID = entry.getValue();

                String event = eventRset.getString("event");
                commons.log(file.getAbsoluteFile() + " "+ event +" ...");
                writerTracks = new BufferedWriter(new FileWriter(file.getAbsoluteFile() + File.separator + "SVMtracks."+ event +".train"));
                writerShapes = new BufferedWriter(new FileWriter(file.getAbsoluteFile() + File.separator + "SVMshapes."+ event +".train"));

                // there are positive and negative samples
                Integer[] posNeg = new Integer[2];
                posNeg[0] =  1;
                posNeg[1] = -1;

                // take positive first
                for (Integer pos : posNeg) {
                    // this is for the IN and NOT IN part
                    String subqery = "SELECT DISTINCT seqname, track FROM sin12.annotation_tracks "
                                   + "WHERE event = '"+ event +"'";
                    String subqeryNOT = "SELECT DISTINCT seqname, track FROM sin12.annotation_tracks "
                                      + "WHERE (seqname, t.track) NOT IN ("+ subqery +")";

                    String query = "SELECT seqname, track, firsts, lasts, avgs, sums, avgcolor, avgshape, shapes\n"
                          + " FROM sin12.tracks AS t\n"
                          + " WHERE firsts IS NOT NULL\n";
                    if (pos > 0) {
                        query  += "       AND (t.seqname, t.track) IN ("+ subqery +");\n";
                    }
                    else {
                        query  += "       AND (t.seqname, t.track) IN ("+ subqeryNOT +");\n";
                    }


                    Statement stmt = commons.conn.createStatement();
                    ResultSet rset = stmt.executeQuery(query);

                    // go through the samples
                    while (rset.next()) {
                        String seqname = rset.getString("seqname");
                        // int xgtf = rset.getInt("xgtf_id");
                        int track = rset.getInt("track");
                        String commentID = " #" + seqname + "-" + track;

                        String head = pos + " 1:"+ seqname.charAt(seqname.length()-1); // class 1:camera

                        // pickup all bros and stick em togetha
                        String[] firstStrs = rset.getString("firsts").substring(rset.getString("firsts").indexOf(",") + 1, rset.getString("firsts").length() - 1).split(",");
                        String[] lastStrs = rset.getString("lasts").substring(rset.getString("lasts").indexOf(",") + 1, rset.getString("lasts").length() - 1).split(",");
                        String[] avgStrs = rset.getString("avgs").substring(rset.getString("avgs").indexOf(",") + 1, rset.getString("avgs").length() - 1).split(",");
                        String[] sumStrs = rset.getString("sums").substring(1, rset.getString("sums").length() - 1).split(",");
                        String[] colorStrs = rset.getString("avgcolor").substring(1, rset.getString("avgcolor").length() - 1).split(",");
                        String[] shapeStrs = rset.getString("avgshape").substring(1, rset.getString("avgshape").length() - 1).split(",");
                        String[] features = concatAll(firstStrs, lastStrs, avgStrs, sumStrs, colorStrs, shapeStrs);

                        String s1 = "";
                        for (int f=0; f < features.length; f++) {
                            s1 += " "+ (f+2) +":"+ features[f];
                        }
                        // TODO: assert the equal feature count 68!
                        writerTracks.write(head + s1 + commentID + "\n");

                        // shapes
                        // "{{95474,7031,-46673,1984,127403,-16242,23862},{875456,-371165,52522,-50352,54403,7221,4337},{145553,-15433,-6303,2073,118197,-17761,21309},{123365,6669,-10982,-9646,134691,-16158,25481},{82869,5406,-21567,9087,208900,-3210,41800},{114196,23102,-32419,-19890,207617,-15451,27955},{144837,19546,-40352,-7249,142656,-23288,20109},{100524,15909,-27874,-10665,175183,-17998,35756},{240599,-18703,7230,-306,156437,-55637,40079},{160958,41469,-30739,2423,143484,-31171,32889},{0,0,0,0,0,0,0},{250751,1561,138478,-3360,150297,-18474,-25493},{78463,5481,-16903,-5551,133984,-10409,21532},{118231,6249,-23380,-8121,127445,-15619,20982},{74300,15217,6330,-8293,246014,-29727,1848},{137478,11757,-41156,-1681,151209,-22289,25470},{174945,-7466,-22103,-3078,102090,-21010,17208},{102655,-6243,-43321,6855,90223,-2983,-525},{0,0,0,0,0,0,0},{137775,6506,-41305,-255,130481,-19522,14407},{106611,-15143,27257,-13422,92178,-1542,7346},{177102,39811,-40319,-26308,146381,-21821,20664},{438412,23491,-122118,-25002,137765,-13972,33595},{98227,-3356,-1391,1044,75384,151,-1457},{182176,-4064,-38847,-5596,56299,4014,-2734},{106539,14779,-22678,-5693,123911,-13902,15384},{75024,2366,-30379,-3093,139738,-8577,19574},{132540,-10165,-938,-1511,80410,-9187,5440},{169575,53953,-11902,-7761,121589,-20537,24705},{120071,10960,-4754,-8366,105940,-10583,11950},{73681,3329,-7800,-7006,140388,-9912,21797},{64881,2508,-19397,-4482,150883,-7558,20666},{192147,-27332,-56626,8808,118549,-22121,15302},{133330,-57144,6563,-16565,168693,-3992,44003},{117430,14218,-26723,-5276,102379,-13131,16368},{95313,9126,-14788,-5431,104667,-10402,15214},{95550,10665,-32135,-4678,126031,-10796,20602},{160104,-34996,-51382,11761,129374,-391,-12696},{121017,9682,1354,-1516,79356,-2449,20161},{149511,28764,-40510,-10975,130371,-1965,29314},{95887,10631,-8282,-14865,251171,-29232,44519},{179623,-12316,-48028,-358,113816,-19682,8411},{143704,6247,-25915,-10793,111235,-16613,12382},{63365,7208,25784,-12378,362177,18823,68464},{74667,2883,-18142,-4104,129775,-9458,14066},{108208,11950,-17659,-7622,138891,-15011,21604},{104922,-24425,-13036,-10222,150858,11490,-9332},{124326,9155,-25767,-7005,117847,-14190,11761},{70270,7781,-27377,-3747,187389,-11112,26114},{103233,12378,-42501,162,142536,-20725,27361},{126809,-39825,19885,-21070,106262,-8496,-8819},{203637,-6863,17692,-16648,91253,-13784,10554},{98522,5582,-35726,-2413,106616,-10322,15767},{66925,5236,-18514,-7532,162477,-10615,27274},{119444,8409,-26741,-9358,130740,-16940,23113},{73424,18697,-12464,-16463,305869,-6603,14126},{69338,22546,33568,-17265,401460,-24924,57257},{49472,-250,40953,-14156,423725,-22405,137429},{50442,2255,32611,-10658,321376,-18228,111929},{77456,3348,-19587,-2788,126872,-8551,14127},{26029,-2005,-9936,-641,291219,13014,-18386},{67189,5616,-13143,-7192,157005,-13489,22989},{138284,6369,-24536,-6657,134459,-22293,27037},{85139,12619,-36163,-8380,154539,-11448,30009},{58637,4200,-16373,-4425,167010,-6457,21709},{152488,-3244,-54336,826,135877,-26421,23917},{136617,14940,-34763,-13328,128466,-19788,24291},{86632,3686,-19959,-465,111627,-4968,13447},{148807,-102663,-32341,44903,118815,-9231,-6234},{89323,7246,-30849,-2784,124293,-13042,21905},{953070,-437521,359859,-232414,614873,-25075,-108517},{77190,5635,-12004,-3728,112649,-9728,16786},{93755,5430,-30030,-2215,104872,-7371,16111},{123885,-9433,5752,-2710,61053,-282,-359},{115137,-7873,1985,-3624,68089,-1332,119},{0,0,0,0,0,0,0},{0,0,0,0,0,0,0},{61043,-1680,-5258,-1689,108704,2795,1745},{123558,-5762,-39935,-3779,114414,-11253,24698},{0,0,0,0,0,0,0},{120740,-13455,6993,-3018,73457,-2730,2185},{152044,5777,-22517,-9550,107773,-20246,14635},{61728,-2425,0,0,98765,4850,0},{133218,1771,-552,-4899,82038,-218,-9106},{377332,-112248,73477,-26104,67971,-2147,-855},{153879,17082,3944,-6765,48835,-4605,3128},{168576,-14388,-44209,5548,108722,-10987,7921},{66801,12076,12862,-7638,200407,-15167,13649},{85492,506,2122,-14320,107183,-2637,6569},{100019,-10917,-9444,487,91199,3133,-8442},{94612,3540,-15396,-2301,95975,-8265,6708},{80393,4772,-9954,-4064,116442,-10006,11897},{83014,6516,-11972,-3394,118071,-12312,12130},{115031,-6946,-12032,386,73250,-3253,1423},{67629,3195,86537,-7530,391653,-28946,-20049},{62418,4336,-16096,-10311,175493,-9182,29928},{340575,-72293,-345415,72291,520789,-50360,8070},{174662,-107016,-50486,23420,125116,5149,-12654},{97111,-3170,-20437,5051,79324,-118,-4614},{469500,-703321,180332,-201189,196487,-23996,10195},{169562,-24311,-10125,-941,49055,6907,-2585},{71694,-5565,-59545,7410,157921,-1786,-10797},{93006,799,-12841,3037,78159,-2940,-2231},{58009,4903,2047,-4503,145260,-10435,16699},{121844,-2931,17257,-19574,87216,-6608,5343},{97511,-5662,-20393,3189,76382,3845,-3130},{85286,-4816,-23303,3994,88277,1981,-5875},{77997,-1113,-14476,2132,93654,-462,-5041},{0,0,0,0,0,0,0},{287920,77779,48964,-23673,70685,-26310,-12737},{320962,93630,312667,94041,397632,79322,48723},{65569,2622,-1654,-10043,155659,-6654,16381},{30887,-772,63640,-3540,460736,-14849,-11160},{294558,-74561,-110241,21830,203911,-40802,12213},{32175,-2409,-47192,2167,330415,18809,-72706},{93242,-3466,-47647,4735,105258,-1509,-4467},{88654,767,8074,466,73818,-510,-969},{75547,1766,-9330,-4962,117784,-6968,11177},{74761,3411,-5931,-5988,122298,-8328,11879},{70388,4096,-5420,-2650,109859,-7135,10318},{100431,2485,-45104,-138,115270,-7110,14602},{119852,-2471,-44401,3142,77750,-1220,941},{86962,3222,-9125,-5310,115293,-10673,10772},{55949,1358,2348,-9726,188154,-10980,21526},{57732,2457,-53528,525,210486,-13871,47454},{62848,-1437,-19579,3130,113577,-326,-5724},{59517,-2048,-23348,3884,124563,868,-8556},{87211,-2064,-12607,4796,81988,-1222,-4647},{87878,-4671,-15720,2840,80259,2608,-3461},{0,0,0,0,0,0,0},{0,0,0,0,0,0,0},{78462,-2336,-12403,3337,89010,21,-5021},{0,0,0,0,0,0,0},{53217,3192,16665,-8305,198546,-17255,12499},{88005,-9703,-34688,2561,100835,7119,-9478},{0,0,0,0,0,0,0},{102596,704,-40625,3355,98196,-7700,7742},{0,0,0,0,0,0,0},{158965,-13679,-9134,-66,46271,3301,-1769},{55859,-232,1172,-5976,127734,-2332,10700},{109887,-5697,-34110,8803,77963,-2903,-1973},{0,0,0,0,0,0,0},{83706,6320,-5204,-6027,113845,-11336,13084},{167264,-6314,-1287,-3777,62285,-3885,-3400},{111111,0,0,0,41667,0,0},{0,0,0,0,0,0,0},{272653,-7506,10497,-1408,30034,-612,1244}}"
                        String[] allShapes = rset.getString("shapes").substring(2, rset.getString("shapes").length() - 2).split("\\},\\{");
                        String shapeStr = allShapes[(int)Math.round((1.0/4.0)*allShapes.length)-1] +","
                                        + allShapes[(int)Math.round((2.0/4.0)*allShapes.length)-1] +","
                                        + allShapes[(int)Math.round((3.0/4.0)*allShapes.length)-1];
                        String[] shapes = shapeStr.split(",");

                        String s2 = "";
                        for (int f=0; f < shapes.length; f++) {
                            s2 += " "+ (f+2) +":"+ shapes[f];
                        }
                        // TODO: assert length 22!
                        writerShapes.write(head + s2 + commentID + "\n");
                    }
                    commons.log(" " + pos);
                } // posNeg
                commons.log(" OK\n");

                writerTracks.flush();
                writerTracks.close();
                writerShapes.flush();
                writerShapes.close();
            } // 4 events


        } catch (IOException ex) {
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                writerTracks.close();
                writerShapes.close();
            } catch (IOException ex) {
                Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return true;
    }


    /**
     * This exports each pair of concurrent annotated tracks
     * @param file
     * @return
     */
    boolean exportSVM2tracks(File file) {
        return true;
    }


    /*
     * Imports i-LIDS .clear.xml annotation data (CLEAR evaluation ViPER XML file)
     */
    boolean importSEDAnnots(File[] files) {
        Statement stmt = null;
        SAXBuilder builder = new SAXBuilder();

        try {
            stmt = commons.conn.createStatement();
        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            return false;
        }

        // for file in the selected files... (no control)
        for (File file : files) {
            Document doc = null;
            try {
                doc = builder.build(file);
            } catch (IOException f) {
                commons.error(this, "XML "+ file.getAbsolutePath() +"\n"+ f);
                continue;
            } catch (JDOMException f) {
                commons.error(this, "XML "+ file.getAbsolutePath() +"\n"+ f);
                continue;
            }

            // korenovy element viper
            Element viper = doc.getRootElement();
            Namespace ns = viper.getNamespace();
            Namespace ns2 = viper.getNamespace("data");
            // skip config, read data
            List<Element> datas = viper.getChildren("data", ns);

            // load source files (possibly more)
            List<Element> sourcefiles = datas.get(0).getChildren("sourcefile", ns);
            for (Element sourcefile : sourcefiles) {

                // load and parse <sourcefile filename="LGW_20071112_E1_CAM1.mpeg">
                String sourcefilename = sourcefile.getAttributeValue("filename");
                String seqname = sourcefilename.substring(0, sourcefilename.indexOf("."));

                Element filee = sourcefile.getChild("file", ns);
                List<Element> attributes = filee.getChildren("attribute", ns);
                for (Element attribute : attributes) {
                    if (attribute.getAttribute("name").getValue().compareTo("NUMFRAMES") == 0) {
                        Element dvalue = attribute.getChild("dvalue", ns2);
                        int numframes = Integer.parseInt(dvalue.getAttributeValue("value")); // 188832

                            String updateStates = "UPDATE "+ commons.dataset +".sequences SET numframes="+ numframes +" WHERE seqname='"+ seqname +"';";
                            commons.logTime(updateStates);
                            try {
                                stmt.executeUpdate(updateStates);
                            } catch (SQLException e) {
                                commons.error(this, e.getMessage());
                                Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                            }
                    }
                    // ignore others??? -- there are H-FRAME-SIZE (720), V-FRAME-SIZE (576), FRAMERATE(1.0)
                }

                // load objects - (possibly more)
                List<Element> objects = sourcefile.getChildren("object", ns);
                for (Element object : objects) {

                    String event_name = object.getAttributeValue("name");
                    int xgtf_id = Integer.parseInt(object.getAttributeValue("id"));
                    String framespan = object.getAttributeValue("framespan");
                    int t1 = Integer.parseInt(framespan.split(":")[0]);
                    int t2 = Integer.parseInt(framespan.split(":")[1]);


                    String insertTrack = "INSERT INTO "+ commons.dataset +".annotations(seqname, t1, t2, event, xgtf_id) VALUES ";
                    insertTrack += "('"+ seqname +"', "+ t1 +", "+ t2 +", '"+ event_name +"', "+ xgtf_id +");";
                    // commons.logTime(insertTrack);
                    try {
                        stmt.executeUpdate(insertTrack);
                    } catch (SQLException e) {
                        commons.error(this, e.getMessage());
                        Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
                    }

                } // objects
            } // sourcefiles

        } // files

        try {
            if (stmt != null) stmt.close();
        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            return false;
        }

        return true;

    } // void importAnnots(File[] files)








    /**
     * SED_<SITE>_<TASK>_<DATA>_<SUB-NUM>.tgz
     *   output/<EXP-ID>/<EXP-ID>.txt                                   // TODO: check!
     *   output/<EXP-ID>/<SOURCE_FILE>*.xml
     *
     * tar -cvf - ./output | gzip > SED12_BrnoUT_retroactiveED_DEV12_1.tgz
     * tar -cvzf SED12_BrnoUT_retroactiveED_EVAL12_1.tar.gz output
     * output/
     *    BrnoUT_2012_retroactiveED_EVAL12_ENG_s-camera_p-SUNAR_1/
     *      BrnoUT_2012_retroactiveED_EVAL12_ENG_s-camera_p-SUNAR_1.txt      <= ./EXP-ID.txt
     *          MCTTR0101a.xml
     *          MCTTR0102a.xml
     *          ...
     * XMLs are evaluated against 
     * $ TV12SED-SubmissionChecker SED12_testTEAM_retroED_DEV12_1.tar.bz2 --ecf expt_2009_retroED_DEV09_ENG_s-camera_NIST_2.xml
     * @see http://www.itl.nist.gov/iad/mig//tests/trecvid/2012/doc/SED12-EvalPlan-v03.html
     *
     * @return success
     */
    public boolean exportSEDExperiments(File ecf_file) {
        // tohle je jen pro krasu a ty kuvy validacni
        DecimalFormat format = new DecimalFormat();
        format.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
        format.setMaximumFractionDigits(3);

        try {
            // copy the <EXP-ID>.txt file
            String outputDirectory = ecf_file.getParent() +"/output/"+ this.expidID_SED() +"/";
            this.copyFile(new File(ecf_file.getParent() +"/EXP-ID.txt"),
                          new File(outputDirectory + this.expidID_SED() +".txt"));

            // by accident all videos of the expt file can be identified as:
            Statement seqStmt = commons.conn.createStatement();
            String seqQuery = "SELECT * "
                               + " FROM "+ commons.dataset +".sequences\n"
                               + " WHERE seqname LIKE '%MCTTR%'\n"
                               + " ORDER BY seqname";
            ResultSet seqRs = seqStmt.executeQuery(seqQuery);

            // sequences
            while (seqRs.next()) {
                String sequence = seqRs.getString("seqname");
                File viperFile = new File(outputDirectory +"/"+ sequence +".xml");

                // zkopiruj ten sample file
                this.copyFile(new File(ecf_file.getParent() + "/CLEAR.xml"), viperFile);

                SAXBuilder builder = new SAXBuilder();
                Document doc = null;
                try {
                    doc = builder.build(viperFile);
                } catch (IOException f) {
                    commons.error(this, "XML "+ viperFile.getAbsolutePath() +"\n"+ f);
                    continue;
                } catch (JDOMException f) {
                    commons.error(this, "XML "+ viperFile.getAbsolutePath() +"\n"+ f);
                    continue;
                }

                // korenovy element viper
                Element viper = doc.getRootElement();
                Namespace ns = viper.getNamespace();
                Namespace dataNs = viper.getNamespace("data");

                // skip config, read data
                Element data = viper.getChild("data", ns);

                // set source file properties (possibly more)
                Element sourcefile = data.getChild("sourcefile", ns);
                sourcefile.setAttribute("filename", sequence + ".mov.deint.mpeg");
                Element filee = sourcefile.getChild("file", ns);
                List<Element> attributes = filee.getChildren("attribute", ns);
                for (Element attribute : attributes) {
                    if (attribute.getAttribute("name").getValue().compareTo("NUMFRAMES") == 0) {
                        Element dvalue = attribute.getChild("dvalue", dataNs);
                        dvalue.setAttribute("value", String.valueOf(seqRs.getInt("numframes")));
                    }
                    // ignore others??? -- there are H-FRAME-SIZE (720), V-FRAME-SIZE (576), FRAMERATE(1.0)
                }

                // this is to export verified only for the interactive task
                String interactive = "";
                if (expidTask.compareTo("interactiveED") == 0) { // expidTasks[1]
                    interactive = " AND verified > 0";
                }

                // go through all events and appropriate tracks
                for (String event : commons.eventSED) {
                    Statement stmt = commons.conn.createStatement();
                    String query = "SELECT e.seqname, t1, t2, e."+ event +", e.verified ,e.track\n" // frames, positions, sizes
                           + " FROM sin12.top_"+ event +" AS e JOIN sin12.tracks AS t\n" // _dryrun
                           + "      ON (e.seqname = t.seqname AND e.track = t.track)\n"
                           + " WHERE e.seqname = '"+ sequence +"'"+ interactive +"\n"
                           + " ORDER BY t1, e.track;";
                    ResultSet rs = stmt.executeQuery(query);


                    /* We nee to vomit this vomitus (object):
                     * <object framespan="38:93" id="0" name="DoorOpenClose">
                     *   <attribute name="Point"/>
                     *   <attribute name="BoundingBox"/>
                     *   <attribute name="DetectionScore">
                     *     <data:fvalue value="0.88"/>
                     *   </attribute>
                     *   <attribute name="DetectionDecision">
                     *     <data:bvalue value="true"/>
                     *   </attribute>
                     * </object>
                     */
                    Integer myID = 0;
                    while (rs.next()) {
                        Integer t1 = rs.getInt("t1");
                        Integer t2 = rs.getInt("t2");

                        // dopln elementy object - prvne PERSON (a jeho LOCATION)
                        Element objectEvent = new Element("object", ns);
                        objectEvent.setAttribute("framespan", t1.toString() +":"+ t2.toString() );
                        objectEvent.setAttribute("id", myID.toString());
                        myID++;
                        objectEvent.setAttribute("name", event);

                        Element att = new Element("attribute", ns);
                        att.setAttribute("name", "Point");
                        objectEvent.addContent(att);

                        att = new Element("attribute", ns);
                        att.setAttribute("name", "BoundingBox");
                        objectEvent.addContent(att);

                        float score = rs.getFloat(event);
                        if (score > 0.99) score -= 0.2;
                        att = new Element("attribute", ns);
                        att.setAttribute("name", "DetectionScore");
                        Element value = new Element("fvalue", dataNs);
                        value.setAttribute("value", format.format(score));
                        att.addContent(value);
                        objectEvent.addContent(att);

                        att = new Element("attribute", ns);
                        att.setAttribute("name", "DetectionDecision");
                        value = new Element("bvalue", dataNs);
                        value.setAttribute("value", "true");
                        att.addContent(value);
                        objectEvent.addContent(att);

                        sourcefile.addContent(objectEvent);
                    }
/*



                        // TODO: pro priste udelat jinak ty experimenty (dat tam novou tabulku... je nutne 2-3 - annot, eval, proc a prob)

                        // hrn vysledky (sunar.states) do me, at je po me...
                        String statesQuery = "SELECT ev.\"time\", proc.track AS proctrack, proc.position, proc.size, proc.prob \n" +
                                "  FROM ONLY sunar.processed AS proc JOIN ONLY sunar.evaluations AS ev \n"+
                                "    ON (ev.dataset = proc.dataset AND ev.video = proc.video AND ev.camera = proc.camera AND (ev.time = proc.time OR ev.time = (proc.time+1))) \n" +
                                " WHERE proc.\"experiment\"="+ "track" +" AND ev.track="+ "track" +" \n" + // tady to ma posunuty vyznam, ale co uz...
                                "   AND ev.dataset="+ expDataset +" AND ev.video="+ expVideo +" AND ev.camera="+ expCamera +"\n"+
                                " ORDER BY ev.\"time\", proc.prob";
                        ResultSet statesRs = expStmt.executeQuery(statesQuery);

                        // fcil to projdu... je mozne, ze tam bude 1 cas vicekrat, potom budu na ty nasledujici srat pekne z vysoka, nemaji narok
                        int firstTime = 0; // panic je nanic (fakt, jen ho porovnavam, abych mu mohl priradit slecnu a stejne nic nebude)
                        int lastTime = 0;

                        // projdi vysledky
                        while (statesRs.next()) {
                            final int time = statesRs.getInt("time");

                            if (time == lastTime) continue; // na tuhle pozicu jsem uz ulozil silnejsiho samce
                            else lastTime = time;
                            if (firstTime == 0) firstTime = time;

                            framespanObj += time +":"+ time +" ";

                            // nacti umisteni (na prob kalej)
                            // BACHA... oni to ukladaji x1, y1 a ja sX, sY
                            Matrix position = new Matrix(statesRs.getString("position"));
                            Matrix size = new Matrix(statesRs.getString("size"));

                            int trW = (int)Math.round(size.get(0));
                            int trH = (int)Math.round(size.get(1));
                            int trX = (int)position.get(0) - trW/2;
                            int trY = (int)position.get(1) - trH/2;
                            // they have added checker for my fake values in F4DE-b7 :))
                            trW = (trW <= 0)? 1 : trW;
                            trH = (trH <= 0)? 1 : trH;
                            trX = (trX < 0)? 0 : trX;
                            trY = (trY < 0)? 0 : trY;
                            // TODO: jestli bude vic, nez 720x576 ...

                            // toz a hod to do teho PDF
                            // <data:bbox framespan="7545:7545" x="13" y="137" width="103" height="298"/>
                            Element bbox = new Element("bbox", dataNs);
                            bbox.setAttribute("framespan", time +":"+ time);
                            bbox.setAttribute("x", String.valueOf(trX));
                            bbox.setAttribute("y", String.valueOf(trY));
                            bbox.setAttribute("width", String.valueOf(trW));
                            bbox.setAttribute("height", String.valueOf(trH));

                            location.addContent(bbox);
                        } // inserting data:bbox

                        // nenasli, takova hokna a na praseci chripku...
                        if (framespanObj.length() == 0) ;
                        else {
                            framespanObj = framespanObj.substring(0, framespanObj.length()-1);
                            objectPerson.setAttribute("name", "PERSON");
                            objectPerson.setAttribute("id", String.valueOf("expObject")); // TODO:
                            objectPerson.setAttribute("framespan", framespanObj);

                            // add it to the existing XML
                            objectPerson.addContent(location);
                            sourcefile.addContent(objectPerson);
                        }

                        // set I-FRAMES
                        Element objectIFframes = new Element("object", ns);
                        objectIFframes.setAttribute("name", "I-FRAMES");
                        objectIFframes.setAttribute("id", "0");
                        objectIFframes.setAttribute("framespan", framespanAll);
                        sourcefile.addContent(objectIFframes);

                        // set FRAME (ojojojojoj, chudak ja, takovy ptakoviny)
                        Element objectFframe = new Element("object", ns);
                        objectFframe.setAttribute("name", "FRAME");
                        objectFframe.setAttribute("id", "0");
                        objectFframe.setAttribute("framespan", framespanAll);
                        /* FRAME - the validator doesnt find this valid
                        Element bvalue = new Element("bvalue", dataNs);
                        bvalue.setAttribute("framespan", firstFrame +":"+ lastFrame);
                        bvalue.setAttribute("value", "true");
                        objectFframe.getChild("attribute", ns).addContent(bvalue);
                        *
                        sourcefile.addContent(objectFframe);
*/
                    }

                    // write a nice XML...Buff
                    BufferedWriter writer;
                    writer = new BufferedWriter(new FileWriter(viperFile));

                    org.jdom2.output.Format xMLformat = org.jdom2.output.Format.getPrettyFormat();
                    xMLformat.setEncoding("UTF-8");
                    XMLOutputter serializer = new XMLOutputter(xMLformat);
                    serializer.output(doc, writer); // System.out

                    writer.flush();
                    writer.close();

                
            }
            // TODO: UPDATE ONLY sunar.evaluation_tracks SET experiment=track

        } catch (SQLException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
        } catch (Exception e) { // for sure... log
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
        }
        
        return true;
    }




    /**
     * Fool-proof file copy
     * WARNING! rewrites files without asking!
     * @return success
     */
    public boolean copyFile(File from, File to) {

        InputStream in;
        OutputStream out;

        try {
            // jestli neni nadrazeny adresar, nebo soubor neexistuje, tak to nejak tise zarid...
            new File(to.getParent()).mkdirs();
            // if (to.exists()) commons.logTime("You should have backuped "+ to.getPath());
            to.delete();
            to.createNewFile();

            in = new FileInputStream(from);
            out= new FileOutputStream(to);

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }

            in.close();
            out.close();

        } catch (FileNotFoundException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            return false;
        } catch (IOException e) {
            commons.error(this, e.getMessage());
            Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, e);
            return false;
        }

        return true;
    }


    public static <T> T[] concatAll(T[] first, T[]... rest) {
      int totalLength = first.length;
      for (T[] array : rest) {
        totalLength += array.length;
      }
      T[] result = Arrays.copyOf(first, totalLength);
      int offset = first.length;
      for (T[] array : rest) {
        System.arraycopy(array, 0, result, offset, array.length);
        offset += array.length;
      }
      return result;
    }

}


