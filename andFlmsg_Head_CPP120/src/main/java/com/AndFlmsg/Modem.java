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

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.os.Process;

import java.util.HashMap;

public class Modem {
    public static final int DEFAULT_MODEM_CODE = 90; // 8PSK1000

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
    public static boolean stopTX = false;
    // Modem modes
    public static HashMap<Integer, String> modemNamesByCode;
    public static HashMap<String, Integer> modemCodesByName;
    public static int modesQty = 0;
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

    public static void getModemsFromC() {
        //List of modems and modes returned from the C++ modems
        int[] modemCodeList = getmodemListInt();
        String[] modemNamesList = getmodemListString();
        modesQty = 0;

        modemNamesByCode = new HashMap<>();
        modemCodesByName = new HashMap<>();

        //Now find the end of modem list to know how many different modems codes we have
        for (int i = 0; i < MAXMODES; i++) {
            if (modemCodeList[i] == -1) {
                modesQty = i;
                //Exit loop
                i = MAXMODES;
            }
        }

        // Populate hastables
        for (int i = 0; i < modesQty; i++) {
            modemNamesByCode.put(modemCodeList[i], modemNamesList[i]);
            modemCodesByName.put(modemNamesList[i], modemCodeList[i]);
        }

        //Sort by mode code to re-group modes of the same modem (as they are in two arrays in rsid_def.cxx)
        int[] sortedModemCodeList = new int[modesQty];

        boolean swapped = true;
        int tmp;
        while (swapped) {
            swapped = false;
            for (int i = 0; i < modesQty - 1; i++) {
                if (sortedModemCodeList[i] > sortedModemCodeList[i + 1]) {
                    tmp = sortedModemCodeList[i];
                    sortedModemCodeList[i] = sortedModemCodeList[i + 1];
                    sortedModemCodeList[i + 1] = tmp;
                    swapped = true;
                }
            }
        }
    }

    //Returns the modem code given a modem name (String)
    public static String getModemNameByCode(int modeCode) {
        if (modemNamesByCode != null) {
            return modemNamesByCode.get(modeCode);
        }
        // Returns default '8PSK1000'
        return "8PSK1000";
    }

    //Receives a modem code. Returns the modem index in the array of ALL modes supplied by the C++ modem
    public static int getModemCodeByName(String modeName) {
        if (modemCodesByName != null) {
            return modemCodesByName.get(modeName);
        }
        // Returns default '8PSK1000'
        return 90;
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

    private native static int[] getmodemListInt();

    private native static String[] getmodemListString();

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


    //Initialise RX modem
    public static void ModemInit() {
        //(re)get list of available modems
        getModemsFromC();

        //Android To-DO: Change to C++ resampler instead of Java quadratic resampler
        //Initialize Re-sampling to 11025Hz for RSID, THOR and MFSK modems
        //		myResampler = new SampleRateConversion(11025.0 / 8000.0);
        SampleRateConversion.SampleRateConversionInit((float) (11025.0 / 8000.0));
    }

    //Save last mode used for next app start
    @SuppressLint("ApplySharedPref")
    private static void saveLastModeUsed(int modemCode) {
        SharedPreferences.Editor editor = AndFlmsg.mysp.edit();
        editor.putString("LASTMODEUSED", Integer.toString(modemCode));
        // Commit the edits!
        editor.commit();
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

    private static void processRsid(short[] so8K, int numSamples8K, float[] so12K, int size12Kbuf) {
        //Re-sample to 11025Hz for RSID, THOR and MFSK modems
        int numSamples12K = SampleRateConversion.Process(so8K, numSamples8K, so12K, size12Kbuf);
        //Conditional Rx RSID (keep the FFT processing for the waterfall)
        String rsidReturnedString = RsidCModemReceive(so12K, numSamples12K, rxRsidOn);
        //As we flushed the RX pipe automatically, we need to process any left-over characters from the previous modem
        //Processing of the characters received
        for (int i = 0; i < rsidReturnedString.length(); i++) {
            processRxChar(rsidReturnedString.charAt(i));
        }

        if (rsidReturnedString.contains("\nRSID:")) {
            //We have a new modem and/or centre frequency
            frequency = getCurrentFrequency();
            ModemService.RxModem = getCurrentMode();
            saveLastModeUsed(ModemService.RxModem);
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
                    ModemService.restartRxModem.drainPermits();
                    //Since the callback is not working, implement a while loop.
                    short[] so8K = new short[bufferSize];
                    int size12Kbuf = (int) ((bufferSize + 1) * 11025.0 / 8000.0);
                    //Android changed to float to match rsid.cxx code
                    float[] so12K = new float[size12Kbuf];
                    //Initialise modem
                    createCModem(ModemService.RxModem);
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
                        if (numSamples8K > 0) {
                            ModemService.cpuload = (int) ((endproctime - startproctime) / buffertime * 100);
                        }
                        if (ModemService.cpuload > 100) {
                            ModemService.cpuload = 100;
                        }
                        AndFlmsg.mHandler.post(AndFlmsg.updatecpuload);
                        //Android try faster mode changes by having a smaller buffer to process
                        numSamples8K = audiorecorder.read(so8K, 0, 8000 / 8); //process only part of the buffer to avoid lumpy processing
                        if (numSamples8K > 0) {
                            modemState = RXMODEMRUNNING;
                            startproctime = System.currentTimeMillis();
                            //Process only if Rx is ON, otherwise discard (we have already decided to TX)
                            if (RxON) {
                                if (rxRsidOn) {
                                    processRsid(so8K, numSamples8K, so12K, size12Kbuf);
                                }
                                //Sets the latest squelch level for the modem to use
                                setSquelch(squelch);
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
                            //Post to TermWindow (Modem) window after each buffer processing
                            //Add TX frame too if present
                            if (Modem.MonitorString.length() > 0 || ModemService.TXmonitor.length() > 0) {
                                ModemService.TermWindow += Modem.MonitorString + ModemService.TXmonitor;
                                ModemService.TXmonitor = "";
                                Modem.MonitorString = "";
                                AndFlmsg.mHandler.post(AndFlmsg.addtoterminal);
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
                    ModemService.restartRxModem.acquireUninterruptibly(1);
                    //Make sure we don's have spare permits
                    ModemService.restartRxModem.drainPermits();
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
        ModemService.restartRxModem.release(1);
    }

    static void changemode(int newModemCode) {
        //Stop the modem receiving side to prevent using the wrong values
        pauseRxModem();

        ModemService.TxModem = ModemService.RxModem = newModemCode;
        saveLastModeUsed(newModemCode);
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

    @SuppressLint("ApplySharedPref")
    public static void setSquelch(double newSql) {
        double assignedSqlValue;

        if (newSql < 0) {
            assignedSqlValue = 0;
        } else if (newSql > 100) {
            assignedSqlValue = 100;
        } else {
            assignedSqlValue = newSql;
        }

        squelch = assignedSqlValue;

        // store value into preferences
        SharedPreferences.Editor editor = AndFlmsg.mysp.edit();
        editor.putFloat("SQUELCHVALUE", (float) assignedSqlValue);
        editor.commit();
        // pass to C++
        setSquelchLevel(assignedSqlValue);
    }

    public static void processRxChar(char inChar) {
        switch (inChar) {
            case 0:
                break; // do nothing
            case '[':
                if (FirstBracketReceived) {
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
                if (FirstBracketReceived) {
                    BlockString += inChar;
                }
                break;
            case 13: //ignore Carriage Returns
                break;
            default:
                if (FirstBracketReceived) {
                    BlockString += inChar;
                }
                break;
        }
        //Reset if header not found within
        //  1000 charaters of first bracket
        if (FirstBracketReceived && BlockString.length() > 1000) {
            BlockString = BlockString.substring(900);

            if (!BlockString.contains("]")) {
                FirstBracketReceived = false;
            }
        }
        //Reset if end marker not found within
        // 100,000 charaters of start (increased from 30,000 as new 8PSK modes allows mode data within a given period of time).
        //to-do: if found crc but not wrap end, process anyway
        if (BlockString.length() > 100000) {
            FirstBracketReceived = false;
        }
        //Display non-control characters
        if (inChar > 31) {
            MonitorString += inChar;
        }
    }

    //In a separate thread so that the UI thread is not blocked during TX
    public static void txData(String Sendline) {
        new Thread(new TxThread(Sendline)).start();
        pauseRxModem();
    }


    public static class TxThread implements Runnable {
        private String txSendline = "";

        public TxThread(String Sendline) {
            txSendline = Sendline;
        }

        public void run() {
            if (txSendline.length() > 0 & !ModemService.TXActive) {
                try {
                    // TODO: DEBUG
                    long startTime = System.nanoTime();

                    //Reset the stop flag if it was ON
                    Modem.stopTX = false;
                    //Set flags to TXing
                    ModemService.TXActive = true;
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
                    loggingclass.writelog(
                            "TX duration, ms: " + totalTime
                                    + "; TX, bytes: " + bytesToSend.length
                                    + "; TX rate, bit/s: " + (bytesToSend.length * 8) / (totalTime / 1000),
                            null);
                } catch (Exception e) {
                    loggingclass.writelog("Can't output sound. Is Sound device busy?", e);
                } finally {
                    ModemService.TXActive = false;
                    //Restart modem reception
                    unPauseRxModem();
                }

            }
        }
    }
}
