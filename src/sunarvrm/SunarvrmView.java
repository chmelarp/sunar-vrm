/*
 * SunarvrmView.java
 */

package sunarvrm;

import java.awt.Component;
import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.Task;
import org.jdesktop.application.TaskMonitor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;

/**
 * The application's main frame.
 */
public class SunarvrmView extends FrameView {

    /**
     * Tohle je development cheat
     */
    public static final int dataset = 0;

    Commons commons;

    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;

    private JDialog aboutBox;
    private JDialog connectDialog;

    public SunarvrmView(SingleFrameApplication app) {
        super(app);
        initComponents();

        // init DB connection
        commons = new Commons(this.getFrame(), this.jTextPaneLog.getDocument());

        // status bar initialization - message timeout, idle icon and busy animation, etc
        ResourceMap resourceMap = getResourceMap();
        int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
        messageTimer = new Timer(messageTimeout, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                statusMessageLabel.setText("");
            }
        });
        messageTimer.setRepeats(false);
        int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
        for (int i = 0; i < busyIcons.length; i++) {
            busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
        }
        busyIconTimer = new Timer(busyAnimationRate, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
                statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
            }
        });
        idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
        statusAnimationLabel.setIcon(idleIcon);
        progressBar.setVisible(false);

        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                if ("started".equals(propertyName)) {
                    if (!busyIconTimer.isRunning()) {
                        statusAnimationLabel.setIcon(busyIcons[0]);
                        busyIconIndex = 0;
                        busyIconTimer.start();
                    }
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                } else if ("done".equals(propertyName)) {
                    busyIconTimer.stop();
                    statusAnimationLabel.setIcon(idleIcon);
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                } else if ("message".equals(propertyName)) {
                    String text = (String)(evt.getNewValue());
                    statusMessageLabel.setText((text == null) ? "" : text);
                    messageTimer.restart();
                } else if ("progress".equals(propertyName)) {
                    int value = (Integer)(evt.getNewValue());
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(value);
                }
            }
        });


        // Frame setting inicialization...
        this.jTextFieldSetUser.setText(commons.user);
        this.jPasswordFieldSetPass.setText("nahovno");
        this.jTextFieldSetLocation.setText(commons.location);
        this.jTextFieldSetDataset.setText(commons.dataset);

    }



    @Action
    public void loadVideoMetadata() {
        JOptionPane.showMessageDialog(commons.owner, "The metadata should be loaded automatically after the extraction process.\n Or use the /mnt/minerva1/chmelarp/i-Lids/check_tracks.sh XX script.\n Then manually validate the data(!!!) and process recent loads (see the hint).", "Load metadata", JOptionPane.INFORMATION_MESSAGE);
    }

    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = SunarvrmApp.getApplication().getMainFrame();
            aboutBox = new SunarvrmAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        SunarvrmApp.getApplication().show(aboutBox);
    }

    @Action
    public Task showConnectDialog() {
        return new ShowConnectDialogTask(getApplication());
    }

    private class ShowConnectDialogTask extends org.jdesktop.application.Task<Object, Void> {
        ShowConnectDialogTask(org.jdesktop.application.Application app) {
            // Runs on the EDT.  Copy GUI state that
            // doInBackground() depends on from parameters
            // to ShowConnectDialogTask fields, here.
            super(app);
            if (connectDialog == null) {
            JFrame mainFrame = SunarvrmApp.getApplication().getMainFrame();
            connectDialog = new ConnectDialog(mainFrame, commons);
            connectDialog.setLocationRelativeTo(mainFrame);
            }
            SunarvrmApp.getApplication().show(connectDialog);
        }
        @Override protected Object doInBackground() {
            // Your Task's code here.  This method runs
            // on a background thread, so don't reference
            // the Swing GUI from here.
            return null;  // return your result
        }
        @Override protected void succeeded(Object result) {
            // Runs on the EDT.  Update the GUI based on
            // the result computed by doInBackground().
        }
    }


    // **************************************************************************************************
    @Action
    public void importAnnots() {

        JFileChooser openDirs = new JFileChooser();
        openDirs.setCurrentDirectory(new File("../"));
        openDirs.setMultiSelectionEnabled(true);
        openDirs.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        if (openDirs.showDialog(this.getFrame(), "Open Annotations") == JFileChooser.APPROVE_OPTION) {
            ImportAnnotsWorker iAW= new ImportAnnotsWorker(commons, openDirs.getSelectedFiles());
            iAW.execute();
        }
    }

    class ImportAnnotsWorker extends LongWorker {
        Commons commons;
        File[] files;

        public ImportAnnotsWorker(Commons commons, File[] files) {
            super();

            this.commons = commons;
            this.files = files;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment
            
            // if files is a directory...
            SunarImportExport imports = new SunarImportExport(commons);
            // TODO: recurse!
            if(files.length > 0 && files[0].isDirectory())  {
                for (File files2 : files) { // 4 directory
                    for (File files3 : files2.listFiles()) { // 4 annotation files
                        imports.importAnnots(files3.listFiles());
                    }
                }
                
            }
            else { // it is a file
                imports.importAnnots(files);
            }
            return null;
        }

    }


    // *********************************************************************************************
    @Action
    public void importExperiments() {

        JFileChooser openDirs = new JFileChooser();
        openDirs.setCurrentDirectory(new File("../"));
        openDirs.setMultiSelectionEnabled(true);
        openDirs.setFileSelectionMode(JFileChooser.FILES_ONLY);

        if (openDirs.showDialog(this.getFrame(), "Open ExperimentControlFile") == JFileChooser.APPROVE_OPTION) {
            ImportExperimentWorker pEW= new ImportExperimentWorker(commons, openDirs.getSelectedFiles());
            pEW.execute();
        }
    }

    class ImportExperimentWorker extends LongWorker {
        Commons commons;
        File[] files;

        public ImportExperimentWorker(Commons commons, File[] files) {
            super();

            this.commons = commons;
            this.files = files;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment

            /*  // truncate the database status
                TRUNCATE TABLE sunar.experiments;
                SELECT setval('sunar.experiments_seq', 1000, true);

                TRUNCATE TABLE sunar.evaluation_tracks;
                TRUNCATE TABLE sunar.evaluation_states;
             */

            SunarImportExport impex = new SunarImportExport(commons);
            List<Integer> expTracks = impex.importECFs(files);
            // TODO: predelat list na neco rozumneho v DB

            commons.logTime("The following experiment tracks were loaded:");
            for (Integer i : expTracks) {
                commons.log(i.toString() + " ");
            }

            commons.logTime("//HINT: UPDATE sunar.experiments SET use='DRYRUN09' WHERE use='DRYRUN';");


            return null;
        }

    }


    // *************************************************************************************************
    @Action
    public Task processLoads() {
        return new ProcessLoadsTask(getApplication());
        // pLW.execute();
    }

    private class ProcessLoadsTask extends org.jdesktop.application.Task<Object, Void> {
        ProcessLoadsTask(org.jdesktop.application.Application app) {
            // Runs on the EDT.  Copy GUI state that
            // doInBackground() depends on from parameters
            // to ProcessLoadsTask fields, here.
            super(app);
            // TODO: select dataset or 0
            ProcessLoadsWorker pLW = new ProcessLoadsWorker(commons);
            // JOptionPane.showMessageDialog(commons.owner, "All loads are already processed...", "Process recent loads", JOptionPane.INFORMATION_MESSAGE);
            pLW.execute();
        }
        @Override protected Object doInBackground() {
            // Your Task's code here.  This method runs
            // on a background thread, so don't reference
            // the Swing GUI from here.
            return null;  // return your result
        }
        @Override protected void succeeded(Object result) {
            // Runs on the EDT.  Update the GUI based on
            // the result computed by doInBackground().
        }
    }

    class ProcessLoadsWorker extends LongWorker {
        Commons commons;

        public ProcessLoadsWorker(Commons commons) {
            super();

            this.commons = commons;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment

            ////////////////////////////////////////////////////////////////////
            // P3k: this is the only vtapi-sunar extension
            ////////////////////////////////////////////////////////////////////

            // TODO: dataset computations && dataset settings

            SunarCleaning sunarCleaner = new SunarCleaning(commons, 0);
            progressBar.setString("Counting statistics...");
            if (!sunarCleaner.countStatistics()) return null;

            // Deleting wired tracks (DANGEROUS!)... see below, SQL

            progressBar.setString("Deleting messy tracks...");
            // if (!sunarCleaner.deleteMessyTracks()) return null;

            progressBar.setString("Updating video offsets...");
            // if (!sunarCleaner.updateOffsets()) return null;

            progressBar.setString("Detecting occlusions...");
            // TODO: funkce v DB

            progressBar.setString("Merging tracks...");
            // TODO: na zaklade klasifikace (ano, ne) urcit, ktere trajektorie by se daly spojit a pak je spojit (2011, NUTNE!!!)
            // TODO: vygenerovat model nekde jinde

            return null;
        }

    }


    // **************************************************************************************************************
    @Action
    public void trainHandovers() {
        // TODO: select dataset or 0
        TrainHandoversWorker tHW = new TrainHandoversWorker(commons);
        tHW.execute();
    }

    class TrainHandoversWorker extends LongWorker {
        Commons commons;

        public TrainHandoversWorker(Commons commons) {
            super();

            this.commons = commons;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment

            progressBar.setString("Assigning annotations...");
            SunarIntegration sunarIntegration = new SunarIntegration(commons,  0); // nebo 0 - tady se nic generovat nemusi
            commons.logTime("WARNING! YOU SHOULD HAVE MADE A BACKUP OF THE sunar.training_handovers_cache TABLE!\n");
            // if (!sunarIntegration.assignAnnotations()) return null;

            progressBar.setString("Creating classification models... skipped");
            // if (!sunarIntegration.trainBayes()) return null;
            // TODO: Train SVM

            progressBar.setString("Testing classification model...");
            if (!sunarIntegration.testBayesPredictCamera()) return null;

            progressBar.setString("Creating verification model...");
            // if (!sunarIntegration.trainVerifications()) return null;

            progressBar.setString("Testing verification model...");
            // this is done somehow above

            return null;
        }

    }


    // ******************************************************************************************************
    @Action
    public void processExperiment() {
            // check constatnts:
            // SunarImportExport (expid... - CP/MC ...)
            // SunarIntegration (recurseTree)

            // vyber tracks toho experimentu
            JFileChooser openFile = new JFileChooser();
            openFile.setCurrentDirectory(new File("../"));
            openFile.setMultiSelectionEnabled(false);
            openFile.setFileSelectionMode(JFileChooser.FILES_ONLY);

            if (openFile.showDialog(this.getFrame(), "Open imported ExperimentControlFile") == JFileChooser.APPROVE_OPTION) {
                ProcessExperimentsWorker pEW = new ProcessExperimentsWorker(commons, openFile.getSelectedFile().getName());
                pEW.execute();
            }
    }


    class ProcessExperimentsWorker extends LongWorker {
        Commons commons;
        String ecf_filename;

        public ProcessExperimentsWorker(Commons commons, String ecf_filename) {
            super();

            this.commons = commons;
            this.ecf_filename = ecf_filename;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment

            // SunarImportExport impex = new SunarImportExport(commons);
            List<Integer> expTracks = new ArrayList<Integer>(); // impex.importECFs(files);

            commons.logTime("Cleanup... processing.");

            Statement expsStmt = commons.conn.createStatement();
            String updateStr = "UPDATE ONLY sunar.tracks SET experiment=NULL, prob=NULL";
            expsStmt.executeUpdate(updateStr);

            // find a -- tohle udela par anotace na camA - anotace na camB
            String expsQuery = "SELECT DISTINCT track, setup, use \n"
                    + " FROM ONLY sunar.experiments \n"
                    + " WHERE ecf_filename LIKE '"+ ecf_filename +"' \n"
                    + " ORDER BY track";
            // System.out.println(camerasQuery);
            ResultSet expsRs = expsStmt.executeQuery(expsQuery);

            while (expsRs.next()) {
                final int i = expsRs.getInt("track");
                expTracks.add(i);
                // thers are automatic
            }
            // or manually (see the sunar.experiments table!) as for (int i = 201; i <= 226; i++) expTracks.add(i);

            // log track experimetns
            if (expTracks.isEmpty()) {
                commons.logTime("There are no experiments loaded... Have you tried AVSS -> Import Experiments first?");
                return null;
            }
            else {
                commons.logTime("Processing tracks " + expTracks.toString());
            }

            // ////////////////////////////////////////////////////////////////////////////////////////////
            // MAIN PARAMETERS ARE TO BE SET HERE AT THE MOMENT 
            //
            // create results
            SunarIntegration sunarIntegration = new SunarIntegration(commons, 5);

            sunarIntegration.trainVerifications(); // this is necessary at the moment!
            sunarIntegration.runExperiments(expTracks);

            // export
            SunarImportExport impex = new SunarImportExport(commons, 1);
            impex.exportExperiments(expTracks);
            commons.logTime("Submission saved to " + impex.expidOutput);

            return null;
        }

    }












    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        jTabbedPaneLog = new javax.swing.JTabbedPane();
        jScrollPaneLog = new javax.swing.JScrollPane();
        jTextPaneLog = new javax.swing.JTextPane();
        jPanelAnnotations = new javax.swing.JPanel();
        jPanelExperiments = new javax.swing.JPanel();
        jPanelSettings = new javax.swing.JPanel();
        jLabelSetUser = new javax.swing.JLabel();
        jTextFieldSetUser = new javax.swing.JTextField();
        jLabelSetPass = new javax.swing.JLabel();
        jPasswordFieldSetPass = new javax.swing.JPasswordField();
        jLabelSetLocation = new javax.swing.JLabel();
        jTextFieldSetLocation = new javax.swing.JTextField();
        jLabelSetDataset = new javax.swing.JLabel();
        jTextFieldSetDataset = new javax.swing.JTextField();
        jButtonSetConnect = new javax.swing.JButton();
        menuBar = new javax.swing.JMenuBar();
        jMenuVRM = new javax.swing.JMenu();
        jMenuItemSettings = new javax.swing.JMenuItem();
        jMenuItemConnections = new javax.swing.JMenuItem();
        jMenuItemQuit = new javax.swing.JMenuItem();
        jMenuCVM = new javax.swing.JMenu();
        jMenuItemLoadMetadata = new javax.swing.JMenuItem();
        jMenuDataConvert = new javax.swing.JMenuItem();
        jMenuHMI = new javax.swing.JMenu();
        jMenuItemMonAnnotations = new javax.swing.JMenuItem();
        jMenuItemMonExperiments = new javax.swing.JMenuItem();
        jMenuAVSS = new javax.swing.JMenu();
        jMenuAVSSAnnotations = new javax.swing.JMenuItem();
        jMenuItemAVSSTrainHandovers = new javax.swing.JMenuItem();
        jMenuAvssImportExperiments = new javax.swing.JMenuItem();
        jMenuAvssExperiment = new javax.swing.JMenuItem();
        jMenuIAvssImportTests = new javax.swing.JMenuItem();
        jMenuSED = new javax.swing.JMenu();
        jMenuSEDAnnotations = new javax.swing.JMenuItem();
        jMenuFIX = new javax.swing.JMenuItem();
        jMenuSVMtrain = new javax.swing.JMenuItem();
        jMenuSVMeval = new javax.swing.JMenuItem();
        jMenuSVMimport = new javax.swing.JMenuItem();
        jMenuSEDExport = new javax.swing.JMenuItem();
        javax.swing.JMenu jMenuHelp = new javax.swing.JMenu();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
        statusPanel = new javax.swing.JPanel();
        statusMessageLabel = new javax.swing.JLabel();
        statusAnimationLabel = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();

        mainPanel.setName("mainPanel"); // NOI18N

        jTabbedPaneLog.setName("jTabbedPaneLog"); // NOI18N

        jScrollPaneLog.setName("jScrollPaneLog"); // NOI18N

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(sunarvrm.SunarvrmApp.class).getContext().getResourceMap(SunarvrmView.class);
        jTextPaneLog.setBackground(resourceMap.getColor("jTextPaneLog.background")); // NOI18N
        jTextPaneLog.setBorder(null);
        jTextPaneLog.setEditable(false);
        jTextPaneLog.setText(resourceMap.getString("jTextPaneLog.text")); // NOI18N
        jTextPaneLog.setName("jTextPaneLog"); // NOI18N
        jScrollPaneLog.setViewportView(jTextPaneLog);

        jTabbedPaneLog.addTab(resourceMap.getString("jScrollPaneLog.TabConstraints.tabTitle"), jScrollPaneLog); // NOI18N

        jPanelAnnotations.setEnabled(false);
        jPanelAnnotations.setName("jPanelAnnotations"); // NOI18N

        javax.swing.GroupLayout jPanelAnnotationsLayout = new javax.swing.GroupLayout(jPanelAnnotations);
        jPanelAnnotations.setLayout(jPanelAnnotationsLayout);
        jPanelAnnotationsLayout.setHorizontalGroup(
            jPanelAnnotationsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 472, Short.MAX_VALUE)
        );
        jPanelAnnotationsLayout.setVerticalGroup(
            jPanelAnnotationsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 321, Short.MAX_VALUE)
        );

        jTabbedPaneLog.addTab(resourceMap.getString("jPanelAnnotations.TabConstraints.tabTitle"), jPanelAnnotations); // NOI18N

        jPanelExperiments.setEnabled(false);
        jPanelExperiments.setName("jPanelExperiments"); // NOI18N

        javax.swing.GroupLayout jPanelExperimentsLayout = new javax.swing.GroupLayout(jPanelExperiments);
        jPanelExperiments.setLayout(jPanelExperimentsLayout);
        jPanelExperimentsLayout.setHorizontalGroup(
            jPanelExperimentsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 472, Short.MAX_VALUE)
        );
        jPanelExperimentsLayout.setVerticalGroup(
            jPanelExperimentsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 321, Short.MAX_VALUE)
        );

        jTabbedPaneLog.addTab(resourceMap.getString("jPanelExperiments.TabConstraints.tabTitle"), jPanelExperiments); // NOI18N

        jPanelSettings.setName("jPanelSettings"); // NOI18N

        jLabelSetUser.setText(resourceMap.getString("jLabelSetUser.text")); // NOI18N
        jLabelSetUser.setName("jLabelSetUser"); // NOI18N

        jTextFieldSetUser.setText(resourceMap.getString("jTextFieldSetUser.text")); // NOI18N
        jTextFieldSetUser.setName("jTextFieldSetUser"); // NOI18N

        jLabelSetPass.setText(resourceMap.getString("jLabelSetPass.text")); // NOI18N
        jLabelSetPass.setName("jLabelSetPass"); // NOI18N

        jPasswordFieldSetPass.setText(resourceMap.getString("jPasswordFieldSetPass.text")); // NOI18N
        jPasswordFieldSetPass.setName("jPasswordFieldSetPass"); // NOI18N

        jLabelSetLocation.setText(resourceMap.getString("jLabelSetLocation.text")); // NOI18N
        jLabelSetLocation.setName("jLabelSetLocation"); // NOI18N

        jTextFieldSetLocation.setText(resourceMap.getString("jTextFieldSetLocation.text")); // NOI18N
        jTextFieldSetLocation.setName("jTextFieldSetLocation"); // NOI18N

        jLabelSetDataset.setText(resourceMap.getString("jLabelSetDataset.text")); // NOI18N
        jLabelSetDataset.setName("jLabelSetDataset"); // NOI18N

        jTextFieldSetDataset.setText(resourceMap.getString("jTextFieldSetDataset.text")); // NOI18N
        jTextFieldSetDataset.setName("jTextFieldSetDataset"); // NOI18N

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(sunarvrm.SunarvrmApp.class).getContext().getActionMap(SunarvrmView.class, this);
        jButtonSetConnect.setAction(actionMap.get("showConnectDialog")); // NOI18N
        jButtonSetConnect.setText(resourceMap.getString("jButtonSetConnect.text")); // NOI18N
        jButtonSetConnect.setName("jButtonSetConnect"); // NOI18N

        javax.swing.GroupLayout jPanelSettingsLayout = new javax.swing.GroupLayout(jPanelSettings);
        jPanelSettings.setLayout(jPanelSettingsLayout);
        jPanelSettingsLayout.setHorizontalGroup(
            jPanelSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelSetLocation)
                    .addComponent(jLabelSetUser)
                    .addComponent(jLabelSetDataset))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelSettingsLayout.createSequentialGroup()
                        .addComponent(jTextFieldSetDataset, javax.swing.GroupLayout.DEFAULT_SIZE, 261, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButtonSetConnect))
                    .addGroup(jPanelSettingsLayout.createSequentialGroup()
                        .addComponent(jTextFieldSetUser, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabelSetPass)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPasswordFieldSetPass, javax.swing.GroupLayout.PREFERRED_SIZE, 129, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jTextFieldSetLocation, javax.swing.GroupLayout.DEFAULT_SIZE, 357, Short.MAX_VALUE))
                .addGap(27, 27, 27))
        );
        jPanelSettingsLayout.setVerticalGroup(
            jPanelSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSetUser)
                    .addComponent(jTextFieldSetUser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelSetPass)
                    .addComponent(jPasswordFieldSetPass, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSetLocation)
                    .addComponent(jTextFieldSetLocation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jTextFieldSetDataset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabelSetDataset))
                    .addComponent(jButtonSetConnect))
                .addContainerGap(199, Short.MAX_VALUE))
        );

        jTabbedPaneLog.addTab(resourceMap.getString("jPanelSettings.TabConstraints.tabTitle"), jPanelSettings); // NOI18N

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPaneLog, javax.swing.GroupLayout.DEFAULT_SIZE, 480, Short.MAX_VALUE)
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPaneLog, javax.swing.GroupLayout.DEFAULT_SIZE, 359, Short.MAX_VALUE)
        );

        menuBar.setName("menuBar"); // NOI18N

        jMenuVRM.setText(resourceMap.getString("jMenuVRM.text")); // NOI18N
        jMenuVRM.setName("jMenuVRM"); // NOI18N

        jMenuItemSettings.setText(resourceMap.getString("jMenuItemSettings.text")); // NOI18N
        jMenuItemSettings.setName("jMenuItemSettings"); // NOI18N
        jMenuItemSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSettingsActionPerformed(evt);
            }
        });
        jMenuVRM.add(jMenuItemSettings);

        jMenuItemConnections.setAction(actionMap.get("showConnectDialog")); // NOI18N
        jMenuItemConnections.setText(resourceMap.getString("jMenuItemConnections.text")); // NOI18N
        jMenuItemConnections.setName("jMenuItemConnections"); // NOI18N
        jMenuVRM.add(jMenuItemConnections);

        jMenuItemQuit.setAction(actionMap.get("quit")); // NOI18N
        jMenuItemQuit.setText(resourceMap.getString("jMenuItemQuit.text")); // NOI18N
        jMenuItemQuit.setName("jMenuItemQuit"); // NOI18N
        jMenuVRM.add(jMenuItemQuit);

        menuBar.add(jMenuVRM);

        jMenuCVM.setText(resourceMap.getString("jMenuCVM.text")); // NOI18N
        jMenuCVM.setName("jMenuCVM"); // NOI18N

        jMenuItemLoadMetadata.setAction(actionMap.get("loadVideoMetadata")); // NOI18N
        jMenuItemLoadMetadata.setText(resourceMap.getString("jMenuItemLoadMetadata.text")); // NOI18N
        jMenuItemLoadMetadata.setName("jMenuItemLoadMetadata"); // NOI18N
        jMenuCVM.add(jMenuItemLoadMetadata);

        jMenuDataConvert.setAction(actionMap.get("processLoads")); // NOI18N
        jMenuDataConvert.setText(resourceMap.getString("jMenuDataConvert.text")); // NOI18N
        jMenuDataConvert.setName("jMenuDataConvert"); // NOI18N
        jMenuCVM.add(jMenuDataConvert);

        menuBar.add(jMenuCVM);

        jMenuHMI.setText(resourceMap.getString("jMenuHMI.text")); // NOI18N
        jMenuHMI.setName("jMenuHMI"); // NOI18N

        jMenuItemMonAnnotations.setText(resourceMap.getString("jMenuItemMonAnnotations.text")); // NOI18N
        jMenuItemMonAnnotations.setEnabled(false);
        jMenuItemMonAnnotations.setName("jMenuItemMonAnnotations"); // NOI18N
        jMenuHMI.add(jMenuItemMonAnnotations);

        jMenuItemMonExperiments.setText(resourceMap.getString("jMenuItemMonExperiments.text")); // NOI18N
        jMenuItemMonExperiments.setEnabled(false);
        jMenuItemMonExperiments.setName("jMenuItemMonExperiments"); // NOI18N
        jMenuHMI.add(jMenuItemMonExperiments);

        menuBar.add(jMenuHMI);

        jMenuAVSS.setAction(actionMap.get("trainHandovers")); // NOI18N
        jMenuAVSS.setText(resourceMap.getString("jMenuAVSS.text")); // NOI18N
        jMenuAVSS.setName("jMenuAVSS"); // NOI18N

        jMenuAVSSAnnotations.setAction(actionMap.get("importAnnots")); // NOI18N
        jMenuAVSSAnnotations.setText(resourceMap.getString("jMenuAVSSAnnotations.text")); // NOI18N
        jMenuAVSSAnnotations.setName("jMenuAVSSAnnotations"); // NOI18N
        jMenuAVSS.add(jMenuAVSSAnnotations);

        jMenuItemAVSSTrainHandovers.setAction(actionMap.get("trainHandovers")); // NOI18N
        jMenuItemAVSSTrainHandovers.setText(resourceMap.getString("jMenuDataTrainHandovers.text")); // NOI18N
        jMenuItemAVSSTrainHandovers.setName("jMenuDataTrainHandovers"); // NOI18N
        jMenuAVSS.add(jMenuItemAVSSTrainHandovers);

        jMenuAvssImportExperiments.setAction(actionMap.get("importExperiments")); // NOI18N
        jMenuAvssImportExperiments.setText(resourceMap.getString("jMenuAvssImportExperiments.text")); // NOI18N
        jMenuAvssImportExperiments.setName("jMenuAvssImportExperiments"); // NOI18N
        jMenuAVSS.add(jMenuAvssImportExperiments);

        jMenuAvssExperiment.setAction(actionMap.get("processExperiment")); // NOI18N
        jMenuAvssExperiment.setText(resourceMap.getString("jMenuAvssExperiment.text")); // NOI18N
        jMenuAvssExperiment.setName("jMenuAvssExperiment"); // NOI18N
        jMenuAVSS.add(jMenuAvssExperiment);

        jMenuIAvssImportTests.setText(resourceMap.getString("jMenuIAvssImportTests.text")); // NOI18N
        jMenuIAvssImportTests.setEnabled(false);
        jMenuIAvssImportTests.setName("jMenuIAvssImportTests"); // NOI18N
        jMenuAVSS.add(jMenuIAvssImportTests);

        menuBar.add(jMenuAVSS);

        jMenuSED.setText(resourceMap.getString("jMenuSED.text")); // NOI18N
        jMenuSED.setName("jMenuSED"); // NOI18N

        jMenuSEDAnnotations.setText(resourceMap.getString("jMenuSEDAnnotations.text")); // NOI18N
        jMenuSEDAnnotations.setName("jMenuSEDAnnotations"); // NOI18N
        jMenuSEDAnnotations.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuSEDAnnotationsActionPerformed(evt);
            }
        });
        jMenuSED.add(jMenuSEDAnnotations);

        jMenuFIX.setText(resourceMap.getString("jMenuFIX.text")); // NOI18N
        jMenuFIX.setName("jMenuFIX"); // NOI18N
        jMenuFIX.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuFIXActionPerformed(evt);
            }
        });
        jMenuSED.add(jMenuFIX);

        jMenuSVMtrain.setText(resourceMap.getString("jMenuSVMtrain.text")); // NOI18N
        jMenuSVMtrain.setName("jMenuSVMtrain"); // NOI18N
        jMenuSVMtrain.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuSVMtrainActionPerformed(evt);
            }
        });
        jMenuSED.add(jMenuSVMtrain);

        jMenuSVMeval.setText(resourceMap.getString("jMenuSVMeval.text")); // NOI18N
        jMenuSVMeval.setName("jMenuSVMeval"); // NOI18N
        jMenuSVMeval.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuSVMevalActionPerformed(evt);
            }
        });
        jMenuSED.add(jMenuSVMeval);

        jMenuSVMimport.setText(resourceMap.getString("jMenuSVMimport.text")); // NOI18N
        jMenuSVMimport.setName("jMenuSVMimport"); // NOI18N
        jMenuSVMimport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuSVMimportActionPerformed(evt);
            }
        });
        jMenuSED.add(jMenuSVMimport);

        jMenuSEDExport.setText(resourceMap.getString("jMenuSEDExport.text")); // NOI18N
        jMenuSEDExport.setName("jMenuSEDExport"); // NOI18N
        jMenuSEDExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuSEDExportActionPerformed(evt);
            }
        });
        jMenuSED.add(jMenuSEDExport);

        menuBar.add(jMenuSED);

        jMenuHelp.setText(resourceMap.getString("jMenuHelp.text")); // NOI18N
        jMenuHelp.setName("jMenuHelp"); // NOI18N

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        jMenuHelp.add(aboutMenuItem);

        menuBar.add(jMenuHelp);

        statusPanel.setName("statusPanel"); // NOI18N

        statusMessageLabel.setName("statusMessageLabel"); // NOI18N

        statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

        progressBar.setName("progressBar"); // NOI18N

        javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
        statusPanel.setLayout(statusPanelLayout);
        statusPanelLayout.setHorizontalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(statusMessageLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 273, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusAnimationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        statusPanelLayout.setVerticalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(progressBar, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(statusAnimationLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(statusMessageLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        setComponent(mainPanel);
        setMenuBar(menuBar);
        setStatusBar(statusPanel);
    }// </editor-fold>//GEN-END:initComponents

    private void jMenuItemSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSettingsActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jMenuItemSettingsActionPerformed

    private void jMenuSEDAnnotationsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuSEDAnnotationsActionPerformed
        JFileChooser openDirs = new JFileChooser();
        openDirs.setCurrentDirectory(new File("../"));
        openDirs.setMultiSelectionEnabled(true);
        openDirs.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        if (openDirs.showDialog(this.getFrame(), "Open Annotations") == JFileChooser.APPROVE_OPTION) {
            ImportSINAnnotsWorker iAW= new ImportSINAnnotsWorker(commons, openDirs.getSelectedFiles());
            iAW.execute();
        }
    }

    class ImportSINAnnotsWorker extends LongWorker {
        Commons commons;
        File[] files;

        public ImportSINAnnotsWorker(Commons commons, File[] files) {
            super();

            this.commons = commons;
            this.files = files;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment

            // if files is a directory...
            SunarImportExport imports = new SunarImportExport(commons);
            // TODO: recurse!
            if(files.length > 0 && files[0].isDirectory())  {
                for (File files2 : files) { // 4 directory
                    for (File files3 : files2.listFiles()) { // 4 annotation files
                        imports.importAnnots(files3.listFiles());
                    }
                }

            }
            else { // it is a file
                imports.importSEDAnnots(files);
            }
            return null;
        }
    }//GEN-LAST:event_jMenuSEDAnnotationsActionPerformed

    private void jMenuSVMtrainActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuSVMtrainActionPerformed
        JFileChooser openDirs = new JFileChooser();
        openDirs.setCurrentDirectory(new File("../"));
        openDirs.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (openDirs.showDialog(this.getFrame(), "Save SVMtrain directory") == JFileChooser.APPROVE_OPTION) {
            ExportSVMtrain eSW= new ExportSVMtrain(commons, openDirs.getSelectedFile());
            eSW.execute();
        }
    }

    class ExportSVMtrain extends LongWorker {
        Commons commons;
        File file;

        public ExportSVMtrain(Commons commons, File file) {
            super();

            this.commons = commons;
            this.file = file;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment

            // if files is a directory...
            SunarImportExport exports = new SunarImportExport(commons);
            exports.exportSVMtrain(file);
            //exports.exportSVM2tracks(file);

            return null;
        }
    }//GEN-LAST:event_jMenuSVMtrainActionPerformed

/**
 * Tohle je jen FIX chyby v anotacich, co opravuje nejake kraviny ohledne next()
 * @param evt
 */
    private void jMenuFIXActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuFIXActionPerformed
            try {
                Statement stmt = commons.conn.createStatement();
                String query = "SELECT * FROM sin12.annotations WHERE seqname='XXX'\n";
                ResultSet rset = stmt.executeQuery(query);

                while (rset.next()) {
                    String seqname = rset.getString("seqname");
                    String t1 = rset.getString("t1");
                    String t2 = rset.getString("t2");
                    String event = rset.getString("event");
                    String xgtf_id = rset.getString("xgtf_id");

                    Statement s2 = commons.conn.createStatement();
                    String q2 = "SELECT track FROM sin12.annotation_tracks2 "
                              + " WHERE seqname = '"+ seqname +"' "
                              + " AND event = '"+ event +"' "
                              + " AND xgtf_id = "+ xgtf_id + "+1";
                    ResultSet r2 = s2.executeQuery(q2);

                    while (r2.next()) {
                        String track = r2.getString("track");

                        Statement s3 = commons.conn.createStatement();
                        String q3 = "INSERT INTO sin12.annotation_tracks(seqname, t1, t2, event, track, xgtf_id) "
                                + " VALUES ('"+ seqname +"', "+ t1 +", "+ t2+",'"+ event +"', "+ track +", "+ xgtf_id + ");";
                        s3.execute(q3);
                    }

                }

            } catch (SQLException ex) {
                Logger.getLogger(SunarImportExport.class.getName()).log(Level.SEVERE, null, ex);
            }


/*
        annTrack->add(annotation->getSequenceName(), annotation->getStartTime(), annotation->getEndTime());
                annTrack->insert->keyString("event", annotation->getString("event"));
                annTrack->insert->keyInt("track", track);
                annTrack->insert->keyInt("xgtf_id", annotation->getInt("xgtf_id"));
                annTrack->addExecute();
 */
    }//GEN-LAST:event_jMenuFIXActionPerformed

    private void jMenuSVMevalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuSVMevalActionPerformed
        JFileChooser openDirs = new JFileChooser();
        openDirs.setCurrentDirectory(new File("../"));
        openDirs.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (openDirs.showDialog(this.getFrame(), "Save SVMeval directory") == JFileChooser.APPROVE_OPTION) {
            ExportSVMeval eSW= new ExportSVMeval(commons, openDirs.getSelectedFile());
            eSW.execute();
        }
    }

    class ExportSVMeval extends LongWorker {
        Commons commons;
        File file;

        public ExportSVMeval(Commons commons, File file) {
            super();

            this.commons = commons;
            this.file = file;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment

            // if files is a directory...
            SunarImportExport exports = new SunarImportExport(commons);
            exports.exportSVMeval(file);

            return null;
        }        // TODO add your handling code here:
    }//GEN-LAST:event_jMenuSVMevalActionPerformed

    private void jMenuSVMimportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuSVMimportActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.addChoosableFileFilter(new FileFilter() {
            public String getDescription() {
                return "SVM Predict (*.predict)";
            }
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                } else {
                    return f.getName().toLowerCase().endsWith(".predict");
                }
            }
        });

        fileChooser.setCurrentDirectory(new File("../"));
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);


        if (fileChooser.showDialog(this.getFrame(), "Open SVM predictions") == JFileChooser.APPROVE_OPTION) {
            ImportSVMpredictsWorker iAW= new ImportSVMpredictsWorker(commons, fileChooser.getSelectedFiles());
            iAW.execute();
        }
    }

    class ImportSVMpredictsWorker extends LongWorker {
        Commons commons;
        File[] files;

        public ImportSVMpredictsWorker(Commons commons, File[] files) {
            super();

            this.commons = commons;
            this.files = files;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment

            // if files is a directory...
            SunarImportExport imports = new SunarImportExport(commons);
            // TODO: recurse!
            if(files[0].isDirectory())  {
                for (File files2 : files) { // list those files (i guess :)
                    imports.importSVMpredicts(files2.listFiles());
                }

            }
            else { // it is a file
                imports.importSVMpredicts(files);
            }
            return null;
        }
    }//GEN-LAST:event_jMenuSVMimportActionPerformed

    private void jMenuSEDExportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuSEDExportActionPerformed
            // check constatnts:
            // SunarImportExport (expid... - CP/MC ...)
            // SunarIntegration (recurseTree)

            // vyber tracks toho experimentu
            JFileChooser openFile = new JFileChooser();
            openFile.setCurrentDirectory(new File("../"));
            openFile.setMultiSelectionEnabled(false);
            openFile.setFileSelectionMode(JFileChooser.FILES_ONLY);

            if (openFile.showDialog(this.getFrame(), "Open ExperimentControlFile") == JFileChooser.APPROVE_OPTION) {
                ExportSEDExperimentsWorker pEE = new ExportSEDExperimentsWorker(commons, openFile.getSelectedFile());
                pEE.execute();
            }
    }


    class ExportSEDExperimentsWorker extends LongWorker {
        Commons commons;
        File ecf_file;

        public ExportSEDExperimentsWorker(Commons commons, File ecf_file) {
            super();

            this.commons = commons;
            this.ecf_file = ecf_file;
        }

        @Override
        protected Void doInBackground() throws Exception {
            start();    // prepare the environment


            // export
            SunarImportExport impex = new SunarImportExport(commons, 1);
            impex.expidTask = ecf_file.getName().split("_")[2];
            commons.logTime(impex.expidID_SED() + " ... ");

            impex.exportSEDExperiments(ecf_file);

            commons.logTime("OK!");
            return null;
        }
    }//GEN-LAST:event_jMenuSEDExportActionPerformed



    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonSetConnect;
    private javax.swing.JLabel jLabelSetDataset;
    private javax.swing.JLabel jLabelSetLocation;
    private javax.swing.JLabel jLabelSetPass;
    private javax.swing.JLabel jLabelSetUser;
    private javax.swing.JMenu jMenuAVSS;
    private javax.swing.JMenuItem jMenuAVSSAnnotations;
    private javax.swing.JMenuItem jMenuAvssExperiment;
    private javax.swing.JMenuItem jMenuAvssImportExperiments;
    private javax.swing.JMenu jMenuCVM;
    private javax.swing.JMenuItem jMenuDataConvert;
    private javax.swing.JMenuItem jMenuFIX;
    private javax.swing.JMenu jMenuHMI;
    private javax.swing.JMenuItem jMenuIAvssImportTests;
    private javax.swing.JMenuItem jMenuItemAVSSTrainHandovers;
    private javax.swing.JMenuItem jMenuItemConnections;
    private javax.swing.JMenuItem jMenuItemLoadMetadata;
    private javax.swing.JMenuItem jMenuItemMonAnnotations;
    private javax.swing.JMenuItem jMenuItemMonExperiments;
    private javax.swing.JMenuItem jMenuItemQuit;
    private javax.swing.JMenuItem jMenuItemSettings;
    private javax.swing.JMenu jMenuSED;
    private javax.swing.JMenuItem jMenuSEDAnnotations;
    private javax.swing.JMenuItem jMenuSEDExport;
    private javax.swing.JMenuItem jMenuSVMeval;
    private javax.swing.JMenuItem jMenuSVMimport;
    private javax.swing.JMenuItem jMenuSVMtrain;
    private javax.swing.JMenu jMenuVRM;
    private javax.swing.JPanel jPanelAnnotations;
    private javax.swing.JPanel jPanelExperiments;
    private javax.swing.JPanel jPanelSettings;
    private javax.swing.JPasswordField jPasswordFieldSetPass;
    private javax.swing.JScrollPane jScrollPaneLog;
    private javax.swing.JTabbedPane jTabbedPaneLog;
    private javax.swing.JTextField jTextFieldSetDataset;
    private javax.swing.JTextField jTextFieldSetLocation;
    private javax.swing.JTextField jTextFieldSetUser;
    private javax.swing.JTextPane jTextPaneLog;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JLabel statusAnimationLabel;
    private javax.swing.JLabel statusMessageLabel;
    private javax.swing.JPanel statusPanel;
    // End of variables declaration//GEN-END:variables





    /**
     * Trida reprezentujici bezici ulohu.
     */
    abstract class LongWorker extends SwingWorker<Void, Void>
    {
        public void start()
        {
            statusAnimationLabel.setIcon(busyIcons[0]);
            busyIconIndex = 0;
            busyIconTimer.start();
            progressBar.setVisible(true);
            progressBar.setStringPainted(true);
            progressBar.setString("Working...");
        }

        /*
         * Executed in event dispatch thread
         */
        @Override
        public void done()
        {
            progressBar.setIndeterminate(false);
            progressBar.setString("");
            progressBar.setVisible(false);
            busyIconTimer.stop();
            statusAnimationLabel.setIcon(idleIcon);
        }

        /*
        @Override
        protected Void doInBackground() throws Exception {
            start();
            throw new UnsupportedOperationException("Not supported yet.");
            return null;
        }
        */
    }

}
