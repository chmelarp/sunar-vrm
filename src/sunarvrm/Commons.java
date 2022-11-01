
package sunarvrm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 *
 * @author chmelarp
 */
public class Commons {

    // DB Connection
    public Connection conn = null;
    public String connectionStr = "jdbc:postgresql://localhost:5434/"; // "jdbc:postgresql://minerva3.fit.vutbr.cz:5432/"; // database default to the user...
    public String user = "vidte";
    public String password = "6atiluta";

    public String dataset = "sin12";
    public String location = "/mnt/vidte/datasets";

    public String[] eventSED = {"PersonRuns", "CellToEar", "ObjectPut", "PeopleMeet", "PeopleSplitUp",
                                "Embrace", "Pointing", "ElevatorNoEntry", "OpposingFlow"}; // "TakePicture"

    public String status = "N/A";

    // Dialogs etc.
    JFrame owner = null;
    Document doc = null;
    SimpleDateFormat formatter;
    public Logger logger = Logger.getLogger(Commons.class.getName());

    /**
     * Sets an owner and connects to the default DB
     * @param owner
     */
    public Commons(JFrame owner, Document doc) {
        this.owner = owner;
        this.doc = doc;
        this.formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        this.pgConnect();
    }

    public Commons() {
        this(null, null);
    }

    /**
     * Log to the main view
     * @param String
     */
    public synchronized void log(String str) {
        try {
            if (doc != null) {
                doc.insertString(doc.getLength(), str, null);
            }
            else {
                logger.log(Level.INFO, str);
            }
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, str, ex);
        }
    }

    /**
     *
     * @return
     */
    public void logTime(String str) {
        this.log(formatter.format(new java.util.Date()) + ": "+ str + "\n");
    }

    /**
     * Logs an error to the main view...
     * @param Object
     * @param String
     */
    public void error(Object o, String str) {
        this.logTime("Error at " + o.getClass().getName() + "\n" + str);
    }


    /**
     * Connect to the database...
     */
    public boolean pgConnect() {

        // String url = "jdbc:postgresql://localhost/test?user=fred&password=secret&ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory";
        // Connection conn = DriverManager.getConnection(url);

        // find library
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, null, ex);
            if (owner != null) {
                JOptionPane.showMessageDialog(owner, ex.getMessage() + "org.postgresql.Driver class not found.", "Connection failed", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }

        // connect
        try {   // with SSL

            // set properties
            final Properties properties = new Properties();
            properties.put("user", user);
            properties.put("password", password);
            properties.put("ssl", "true");
            // don't be paranoid... see http://jdbc.postgresql.org/documentation/83/ssl-client.html#nonvalidating
            properties.put("sslfactory", "org.postgresql.ssl.NonValidatingFactory");

            conn = DriverManager.getConnection(connectionStr, properties);
            status = "... connected using SSL";
            this.logTime("... connected using SSL: "+ connectionStr);
            return true;

        } catch (SQLException ex) {

            this.logTime("cannot connect using SSL, trying unsecure");
            logger.log(Level.SEVERE, null, ex);

            try {   // without SSL
                conn = DriverManager.getConnection(connectionStr, user, password);
                status = "... connected unsecurely";
                this.logTime("... connected unsecurely: "+ connectionStr);
                return true;

            } catch (SQLException e) {
                conn = null;
                this.error(this, e.getMessage());
                status = "... connection failed";
                this.logTime("... connection failed: "+ connectionStr);
                logger.log(Level.SEVERE, null, e);
                if (owner != null) {
                    JOptionPane.showMessageDialog(owner, e.getLocalizedMessage() + ".\nEdit Program -> Connection... settings.", "Connection failed", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }
       }

        // return false;
    }


}
