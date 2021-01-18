/*
 * Processor.java
 *
 * Copyright (C) 2011 John Douyere (VK2ETA)
 * Based on Pskmail from Per Crusefalk and Rein Couperus
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.AndFlmsg;

import java.util.concurrent.Semaphore;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;


/**
 * @author John Douyere (VK2ETA)
 */
public class Processor extends Service {


    static String application = "AndFlmsg 1.4.2"; // Used to preset an empty status
    static String version = "Version 1.4.2, 2020-12-01";

    static String ModemPreamble = "";  // String to send before any Tx Buffer
    static String ModemPostamble = ""; // String to send after any Tx buffer
    static String HomePath = "";
    static String Dirprefix = "";
    static boolean compressedMsg = false;
    static String CrcString = "";
    static String FileNameString = "";
    static boolean TXActive = false;


    //JD temp FIX: init as first modem in list
    static int TxModem = Modem.customModeListInt[0];
    static int RxModem = Modem.customModeListInt[0];

    static int imageTxModemIndex = 0;


    //Semaphores to instruct the RxTx Thread to start or stop
    public static Semaphore restartRxModem = new Semaphore(1, false);

    // globals to pass info to gui windows
    static String monitor = "";
    static String TXmonitor = "";
    static String TermWindow = "";
    static String Status = AndFlmsg.myContext.getString(R.string.txt_Listening);
    static int cpuload;

    // globals for communication
    static String mycall;     // my call sign from options
    static int DCDthrow;
    static boolean ReceivingForm = false;
    static String TX_Text; // output queue
    // Error handling and logging object
    static loggingclass log;


    public static void processor() {
        //Nothing as this is a service
    }

    @Override
    public void onCreate() {

        //Save Environment if we need to access Java code/variables from C++
        Message.saveEnv();

        // Create error handling class
        log = new loggingclass("AndFlmsg");

        // Get settings and initialize
        handleinitialization();

        //Initialize Modem (creates the various type of modem objects)
        Modem.ModemInit();

        //Check that we have a current mode, otherwise take the first one in the list (useful when we have a NIL list of custom modes)
        Processor.RxModem = Processor.TxModem = Modem.customModeListInt[Modem.getModeIndex(Processor.RxModem)];

        //Set the image modes defaults and limits
        imageTxModemIndex = Modem.getModeIndexFullList(Modem.getMode("MFSK64"));
        Modem.minImageModeIndex = Modem.getModeIndexFullList(Modem.getMode("MFSK16"));
        Modem.maxImageModeIndex = Modem.getModeIndexFullList(Modem.getMode("MFSK128"));

        //Reset frequency and squelch
        Modem.reset();

        //Make sure the display strings are blank
        Processor.monitor = "";
        Processor.TXmonitor = "";
        Processor.TermWindow = "";
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start the RxThread
        Modem.startmodem();

        //Make sure Android keeps this running even if resources are limited
        //Display the notification in the system bar at the top at the same time
        startForeground(1, AndFlmsg.myNotification);

        // Keep this service running until it is explicitly stopped, so use sticky.
        //VK2ETA To-DO: Check if START_STICKY causes the service restart on ACRA report
        //VK2ETA	return START_STICKY;
        return START_NOT_STICKY;

    }


    @Override
    public void onDestroy() {
        // Kill the Rx Modem thread
        Modem.stopRxModem();
    }


    //Post to main terminal window
    public static void PostToTerminal(String text) {
        Processor.TermWindow += text;
        AndFlmsg.mHandler.post(AndFlmsg.addtoterminal);
    }

    private static void handleinitialization() {

        try {
            // Initialize send queue
            TX_Text = "";
            ModemPreamble = config.getPreferenceS("MODEMPREAMBLE", "");
            ModemPostamble = config.getPreferenceS("MODEMPOSTAMBLE", "");
            // Compression settings
            compressedMsg = config.getPreferenceB("COMPRESSED");
            Processor.mycall = config.getPreferenceS("CALL");
            System.out.println("CALL: " + Processor.mycall);
        } catch (Exception e) {
            loggingclass.writelog("Problems with config parameter.", e, true);
        }
    }


    @Override
    public IBinder onBind(Intent arg0) {
        // Nothing here, not used
        return null;
    }


}

