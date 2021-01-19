/*
 * Modem.java
 *
 * Copyright (C) 2011 John Douyere (VK2ETA) - for Android platforms
 * Partially based on Modem.java from Pskmail by Per Crusefalk and Rein Couperus
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

import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.os.Process;

import java.nio.charset.StandardCharsets;

public class Modem {


    public static final int RXMODEMIDLE = 0;
    public static final int RXMODEMSTARTING = 1;
    public static final int RXMODEMRUNNING = 2;
    public static final int RXMODEMPAUSED = 3;
    public static final int RXMODEMSTOPPING = 4;
    //The following value MUST Match the MAXMODES in modem.h
    public static final int MAXMODES = 300;
    private final static float sampleRate = 8000.0f;
    public static int modemState = RXMODEMIDLE;
    public static boolean modemThreadOn = true;
    public static int NumberOfOverruns = 0;
    public static String MonitorString = "";
    public static double frequency = 1500.0;
    //Semaphore for waterfall (new array of amplitudes needed for display)
    //Is now also accessed from the c++ native side in rsid.cxx
    public static boolean newAmplReady = false;
    public static boolean stopTX = false;
    //List of modems and modes returned from the C++ modems
    public static int[] modemCapListInt = new int[MAXMODES];
    public static String[] modemCapListString = new String[MAXMODES];
    public static int numModes = 0;
    //Custom list of modes as slected in the preferences (can include all modes above if
    //  "Custom List" is not selected, or alternatively all modes manually selected in preferences)
    public static int[] customModeListInt = new int[MAXMODES];
    public static String[] customModeListString = new String[MAXMODES];
    public static int customNumModes = 0;
    //Tx variables
    public static AudioTrack txAt = null;
    //RSID Flags
    public static boolean rxRsidOn;
    public static boolean txRsidOn;
    static double squelch = 20.0;
    static double metric = 50; //midrange
    private static AudioRecord audiorecorder = null;
    private static boolean RxON = false;
    private static int bufferSize = 0;
    private static boolean FirstBracketReceived = false;
    private static String BlockString = "";

    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("AndFlmsg_Modem_Interface");
    }

    //Declaration of native classes
    private native static String createCModem(int modemCode);

    private native static String initCModem(double frequency);

    private native static String rxCProcess(short[] buffer, int length);

    private native static int setSquelchLevel(double squelchLevel);

    private native static double getMetric();

    private native static double getCurrentFrequency();

    private native static int getCurrentMode();

    private native static String RsidCModemReceive(float[] myfbuffer, int length, boolean doSearch);

    private native static String createRsidModem();

    private native static int[] getModemCapListInt();

    private native static String[] getModemCapListString();

    private native static String txInit(double frequency);

    private native static boolean txCProcess(byte[] buffer, int length);

    private native static void saveEnv();

    private native static void setSlowCpuFlag(boolean slowcpu);

    //Called from the C++ side to modulate the audio output
    public static void txModulate(double[] outDBuffer, int length) {
        int res = 0;
        //Catch the stopTX flag at this point as well
        if (!Modem.stopTX) {
            short[] outSBuffer = new short[length];
            int volumebits = Integer.parseInt(config.getPreferenceS("VOLUME", "10"));
            //Change format and re-scale for Android
            //To be moved to c++ code for speed
            for (int i = 0; i < length; i++) {
                outSBuffer[i] = (short) ((int) (outDBuffer[i] * 8386560.0f) >> volumebits);
            }
            res = txAt.write(outSBuffer, 0, length);
            if (res < 0)
                loggingclass.writelog("Error in writing sound buffer: " + res, null);
        }
    }


    //Get capability list of modems from all the C++ modems (taken from rsid_defs.cxx)
    public static void updateModemCapabilityList() {
        //get modem list (int and string description). The C++ side returns its list of available modems.
        modemCapListInt = getModemCapListInt();
        modemCapListString = getModemCapListString();
        //Now find the end of modem list to know how many different modems codes we have
        Modem.numModes = MAXMODES; //Just in case
        for (int i = 0; i < MAXMODES; i++) {
            if (modemCapListInt[i] == -1) {
                Modem.numModes = i;
                //Exit loop
                i = MAXMODES;
            }
        }
        //Sort by mode code to re-group modes of the same modem (as they are in two arrays in rsid_def.cxx)
        boolean swapped = true;
        int tmp;
        String tmpS;
        while (swapped) {
            swapped = false;
            for (int i = 0; i < numModes - 1; i++) {
                if (modemCapListInt[i] > modemCapListInt[i + 1]) {
                    tmp = modemCapListInt[i];
                    tmpS = modemCapListString[i];
                    modemCapListInt[i] = modemCapListInt[i + 1];
                    modemCapListString[i] = modemCapListString[i + 1];
                    modemCapListInt[i + 1] = tmp;
                    modemCapListString[i + 1] = tmpS;
                    swapped = true;
                }
            }
        }
        customModeListInt = modemCapListInt;
        customModeListString = modemCapListString;
        customNumModes = numModes;
    }


    //Initialise RX modem
    public static void ModemInit() {
        //(re)get list of available modems
        updateModemCapabilityList();

        //Android To-DO: Change to C++ resampler instead of Java quadratic resampler
        //Initialize Re-sampling to 11025Hz for RSID, THOR and MFSK modems
        //		myResampler = new SampleRateConversion(11025.0 / 8000.0);
        SampleRateConversion.SampleRateConversionInit((float) (11025.0 / 8000.0));
    }


    //Returns the modem code given a modem name (String)
    public static int getMode(String mstring) {
        int j = 0;
        for (int i = 0; i < modemCapListInt.length; i++) {
            if (modemCapListString[i].equals(mstring)) j = i;
        }
        return modemCapListInt[j];
    }

    //Returns the modem index in the array of modes available given a modem code
    public static int getModeIndex(int mcode) {
        int j = -1;
        for (int i = 0; i < customNumModes; i++) {
            if (mcode == customModeListInt[i]) j = i;
        }
        //In case we didn't find it, return the first mode in the list to avoid segment fault
        if (j == -1) {
            j = 0;
        }
        return j;
    }


    //Receives a modem code. Returns the modem index in the array of ALL modes supplied by the C++ modem
    public static int getModeIndexFullList(int mcode) {
        int j = -1;
        for (int i = 0; i < numModes; i++) {
            if (mcode == modemCapListInt[i]) j = i;
        }
        //In case we didn't find it, return the first mode in the list to avoid segment fault
        if (j == -1) {
            j = 0;
        }
        return j;
    }


    //Return the new mode code or the same if it hits the end of list either way
    public static int getModeUpDown(int currentMode, int increment) {
        //Find position of current mode
        int j = getModeIndex(currentMode);
        j += increment;
        //Circular list
        if (j < 0) j = customNumModes - 1;
        if (j >= customNumModes) j = 0;
        return customModeListInt[j];
    }


    private static void soundInInit() {
        bufferSize = (int) sampleRate; // 1 second of Audio max
        if (bufferSize < AudioRecord.getMinBufferSize((int) sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)) {
            // Check to make sure buffer size is not smaller than the smallest allowed one
            bufferSize = AudioRecord.getMinBufferSize((int) sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        }
        int ii = 20; //number of 1/4 seconds wait
        while (--ii > 0) {
            audiorecorder = new AudioRecord(AudioSource.MIC, 8000, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (audiorecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                ii = 0;//ok done
            } else {
                if (ii < 16) { //Only if have to wait more than 1 seconds
                    loggingclass.writelog("Waiting for Audio MIC availability...", null);
                }
                try {
                    Thread.sleep(250);//1/4 second
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        if (audiorecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            //Android add exception catch here
            loggingclass.writelog("Can't open Audio MIC \n", null);
        }
    }


    public static void startmodem() {
        modemThreadOn = true;

        new Thread(new Runnable() {
            public void run() {
                //Brings back thread priority to foreground (balance between GUI and modem)
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

                while (modemThreadOn) {

                    //Save Environment for this thread so it can call back
                    // Java methods while in C++
                    Modem.saveEnv();

                    String rsidReturnedString;
                    String modemReturnedString;
                    modemState = RXMODEMSTARTING;
                    double startproctime = 0;
                    double endproctime = 0;
                    int numSamples8K = 0;
                    Modem.soundInInit();

                    NumberOfOverruns = 0;
                    try {
                        //Catch un-initialized audio recorder
                        audiorecorder.startRecording();
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                    RxON = true;
                    Processor.restartRxModem.drainPermits();
                    //Since the callback is not working, implement a while loop.
                    short[] so8K = new short[bufferSize];
                    int size12Kbuf = (int) ((bufferSize + 1) * 11025.0 / 8000.0);
                    //Android changed to float to match rsid.cxx code
                    float[] so12K = new float[size12Kbuf];
                    //Initialise modem
                    createCModem(Processor.RxModem);
                    //Initialize RX side of modem
                    boolean slowCpu = config.getPreferenceB("SLOWCPU", false);
                    setSlowCpuFlag(slowCpu);
                    //Changed to getPreferencesI in case it is not an interger representation
                    double centerfreq = config.getPreferenceI("AFREQUENCY", 1500);
                    //Limit it's values too
                    if (centerfreq > 2500) centerfreq = 2500;
                    if (centerfreq < 500) centerfreq = 500;
                    String modemInitResult = initCModem(centerfreq);
                    //Prepare RSID Modem
                    createRsidModem();
                    while (RxON) {
                        endproctime = System.currentTimeMillis();
                        double buffertime = (double) numSamples8K / 8000.0 * 1000.0; //in milliseconds
                        if (numSamples8K > 0)
                            Processor.cpuload = (int) ((endproctime - startproctime) / buffertime * 100);
                        if (Processor.cpuload > 100) Processor.cpuload = 100;
                        AndFlmsg.mHandler.post(AndFlmsg.updatecpuload);
                        //Android try faster mode changes by having a smaller buffer to process						numSamples8K = audiorecorder.read(so8K, 0, 8000/4); //process only part of the buffer to avoid lumpy processing
                        numSamples8K = audiorecorder.read(so8K, 0, 8000 / 8); //process only part of the buffer to avoid lumpy processing
                        if (numSamples8K > 0) {
                            modemState = RXMODEMRUNNING;
                            startproctime = System.currentTimeMillis();
                            //Process only if Rx is ON, otherwise discard (we have already decided to TX)
                            if (RxON) {
                                if (rxRsidOn || (AndFlmsg.currentview == AndFlmsg.MODEMVIEWwithWF)) {
                                    //Re-sample to 11025Hz for RSID, THOR and MFSK modems
                                    int numSamples12K = SampleRateConversion.Process(so8K, numSamples8K, so12K, size12Kbuf);
                                    //Conditional Rx RSID (keep the FFT processing for the waterfall)
                                    rsidReturnedString = RsidCModemReceive(so12K, numSamples12K, rxRsidOn);
                                    //As we flushed the RX pipe automatically, we need to process
                                    // any left-over characters from the previous modem
                                    //Processing of the characters received
                                    for (int i = 0; i < rsidReturnedString.length(); i++) {
                                        processRxChar(rsidReturnedString.charAt(i));
                                    }
                                } else {
                                    rsidReturnedString = "";
                                }
                                if (rxRsidOn) {
                                    if (rsidReturnedString.contains("\nRSID:")) {
                                        //We have a new modem and/or centre frequency
                                        //Update the RSID waterfall frequency too
                                        frequency = getCurrentFrequency();
                                        Processor.RxModem = getCurrentMode();
                                        AndFlmsg.saveLastModeUsed(Processor.RxModem);
                                    }
                                }
                                //Sets the latest squelch level for the modem to use
                                setSquelchLevel(squelch);
                                //Then process the RX data
                                modemReturnedString = rxCProcess(so8K, numSamples8K);
                                //Retreive latest signal quality for display
                                metric = getMetric();
                                AndFlmsg.mHandler.post(AndFlmsg.updatesignalquality);
                                //Now process all the characters received
                                for (int i = 0; i < modemReturnedString.length(); i++) {
                                    processRxChar(modemReturnedString.charAt(i));
                                }
                            }
                            //Post to monitor (Modem) window after each buffer processing
                            //Add TX frame too if present
                            if (Modem.MonitorString.length() > 0 || Processor.TXmonitor.length() > 0) {
                                Processor.monitor += Modem.MonitorString + Processor.TXmonitor;
                                Processor.TXmonitor = "";
                                Modem.MonitorString = "";
                                AndFlmsg.mHandler.post(AndFlmsg.addtomodem);
                            }
                        }
                    }
                    if (audiorecorder != null) {
                        //Avoid some crashes on wrong state
                        if (audiorecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                            if (audiorecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                                audiorecorder.stop();
                            }
                            audiorecorder.release();
                        }
                    }
                    modemState = RXMODEMPAUSED;
                    //Marker for end of thread (Stop modem thread flag)
                    if (!modemThreadOn) {
                        modemState = RXMODEMIDLE;
                        return;
                    }
                    //Now waits for a restart (or having this thread killed)
                    Processor.restartRxModem.acquireUninterruptibly(1);
                    //Make sure we don's have spare permits
                    Processor.restartRxModem.drainPermits();
                }
                modemState = RXMODEMIDLE;
            }
        }).start();
    }


    public static void stopRxModem() {
        modemThreadOn = false;
        RxON = false;
    }

    public static void pauseRxModem() {
        RxON = false;
    }

    public static void unPauseRxModem() {
        Processor.restartRxModem.release(1);
    }

    static void changemode(int newMode) {
        //Stop the modem receiving side to prevent using the wrong values
        pauseRxModem();
        //Restart modem reception
        unPauseRxModem();

    }

    static void setFrequency(double rxfreq) {
        frequency = rxfreq;
    }

    static void reset() {
        String frequencySTR = config.getPreferenceS("AFREQUENCY", "1500");
        frequency = Integer.parseInt(frequencySTR);
        if (frequency < 500) frequency = 500;
        if (frequency > 2500) frequency = 2500;
        squelch = AndFlmsg.mysp.getFloat("SQUELCHVALUE", (float) 20.0);
    }

    /**
     * @param squelchdiff the delta to add to squelch
     */
    public static void AddtoSquelch(double squelchdiff) {
        squelch += (squelch > 10) ? squelchdiff : squelchdiff / 2;
        if (squelch < 0) squelch = 0;
        if (squelch > 100) squelch = 100;
        //store value into preferences
        SharedPreferences.Editor editor = AndFlmsg.mysp.edit();
        editor.putFloat("SQUELCHVALUE", (float) squelch);
        // Commit the edits!
        editor.commit();
    }

    public static void processRxChar(char inChar) {
        //For UTF-8 let all characters through
        //if (inChar > 127) {
        // todo: unicode encoding
        //    inChar = 0;
        // }

        switch (inChar) {
            case 0:
                break; // do nothing
            case '[':
                if (Processor.ReceivingForm ||
                        FirstBracketReceived) {
                    BlockString += inChar;
                } else {
                    if (!FirstBracketReceived) {
                        FirstBracketReceived = true;
                        BlockString = "[";
                    }
                }
                break;
            case 10: //Line Feed
                MonitorString += "\n";
                if (Processor.ReceivingForm ||
                        FirstBracketReceived) {
                    BlockString += inChar;
                }
                break;
            case 13: //ignore Carriage Returns
                break;
            default:
                if (Processor.ReceivingForm ||
                        FirstBracketReceived) {
                    BlockString += inChar;
                }
                break;
        }    // end switch
        //Reset if header not found within
        //  1000 charaters of first bracket
        if (FirstBracketReceived &&
                !Processor.ReceivingForm &&
                BlockString.length() > 1000) {
            BlockString = BlockString.substring(900);
            if (!BlockString.contains("]")) FirstBracketReceived = false;
        }
        //Reset if end marker not found within
        // 100,000 charaters of start (increased from 30,000 as new 8PSK modes allows mode data within a given period of time).
        //to-do: if found crc but not wrap end, process anyway
        if (Processor.ReceivingForm &&
                BlockString.length() > 100000) {
            Processor.CrcString = "";
            Processor.FileNameString = "";
            Processor.ReceivingForm = false;
            FirstBracketReceived = false;
        }
        if (inChar > 31) //Display non-control characters
        {
            MonitorString += inChar;
        }
    }

    //In a separate thread so that the UI thread is not blocked during TX
    public static void txData(String sendingFolder, String sendingFileName, String Sendline,
                              int numberOfImagesToTx,
                              int pictureTxSpeed, Boolean pictureColour, String pictureTxMode) {
        Runnable TxRun = new TxThread(sendingFolder, sendingFileName, Sendline,
                numberOfImagesToTx,
                pictureTxSpeed, pictureColour, pictureTxMode);
        Sendline = "";
        new Thread(TxRun).start();
        pauseRxModem();
    }


    public static class TxThread implements Runnable {
        private String txSendline = "";
        private String txFolder = "";
        private String txFileName = "";
        private int txNumberOfImagesToTx = 0;
        private final int txPictureTxSpeed;
        private final int txPictureColour;
        private final String txPictureTxMode;

        public TxThread(String sendingFolder, String sendingFileName, String Sendline,
                        int numberOfImagesToTx,
                        int pictureTxSpeed, Boolean pictureColour, String pictureTxMode) {
            txFolder = sendingFolder;
            txFileName = sendingFileName;
            txSendline = Sendline;
            txNumberOfImagesToTx = numberOfImagesToTx;
            txPictureTxSpeed = pictureTxSpeed;
            txPictureColour = pictureColour ? -1 : 0; //Leave option open for further variations
            txPictureTxMode = pictureTxMode;
        }

        public void run() {

            if (txSendline.length() > 0 & !Processor.TXActive) {

                if (Processor.DCDthrow == 0) {
                    try {
                        // TODO: DEBUG
                        long startTime = System.nanoTime();

                        //Reset the stop flag if it was ON
                        Modem.stopTX = false;
                        //Set flags to TXing
                        Processor.TXActive = true;
                        //Stop the modem receiving side
                        pauseRxModem();

                        //Wait for the receiving side to be fully stopped???
                        Thread.sleep(500);

                        //Open and initialise the sound system
                        int intSize = 4 * android.media.AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT); //Android check the multiplier value for the buffer size

                        txAt = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, intSize, AudioTrack.MODE_STREAM);
                        //Launch TX
                        txAt.setStereoVolume(1.0f, 1.0f);
                        txAt.play();
                        //Initalise the C++ modem TX side
                        String frequencySTR = config.getPreferenceS("AFREQUENCY", "1500");
                        frequency = Integer.parseInt(frequencySTR);
                        if (frequency < 500) frequency = 500;
                        if (frequency > 2500) frequency = 2500;
                        //Save Environment for this thread so it can call back
                        // Java methods while in C++
                        Modem.saveEnv();
                        //Init current modem for Tx
                        Modem.txInit(frequency);
                        //Encode character buffer into sound
                        //Changed for Utf-8 variable length codes
                        byte[] bytesToSend = null;
                        try {
                            bytesToSend = txSendline.getBytes("UTF_8");
                        } catch (Exception e) { //Invalid UTF-8 characters
                            bytesToSend[0] = 0;//Null character
                        }
                        Modem.txCProcess(bytesToSend, bytesToSend.length);

                        //Stop audio track
                        txAt.stop();
                        //Wait for end of audio play to avoid
                        //overlaps between end of TX and start of RX
                        while (txAt.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        //Android debug add a fixed delay to avoid cutting off the tail end of the modulation
                        Thread.sleep(500);
                        txAt.release();

                        // TODO: DEBUG
                        long endTime = System.nanoTime();
                        long totalTime = (endTime - startTime) / 1000000;
                        loggingclass.writelog("TX duration, ms: " + totalTime, null);
                        loggingclass.writelog("TX, bytes: " + bytesToSend.length, null);
                        loggingclass.writelog("TX rate, bit/s: " + (bytesToSend.length * 8) / (totalTime / 1000), null);
                    } catch (Exception e) {
                        loggingclass.writelog("Can't output sound. Is Sound device busy?", e);
                    } finally {
                        Processor.TXActive = false;
                        //Restart modem reception
                        unPauseRxModem();
                    }

                }

            }
        }
    }
}
