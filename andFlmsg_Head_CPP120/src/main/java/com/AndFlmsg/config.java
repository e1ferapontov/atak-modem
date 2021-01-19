/*
 * config.java  
 *
 * Copyright (C) 2011 John Douyere (VK2ETA) 
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceActivity;

public class config {
    public static String getPreferenceS(String Key) {
        String myReturn = "";

        try {
            myReturn = AndFlmsg.mysp.getString(Key, "");
        } catch (Exception e) {
            myReturn = "";
        }
        return myReturn;
    }

    /**
     * Get the saved value, if its not there then use the default value
     */
    public static String getPreferenceS(String Key, String Default) {
        String myReturn = "";

        try {
            myReturn = AndFlmsg.mysp.getString(Key, Default);
        } catch (Exception e) {
            myReturn = Default;
        }
        return myReturn;
    }

    //Reads an integer from preferences, with default value
    public static int getPreferenceI(String Key, int Default) {
        int myReturn = 0;
        String myPref = "";

        try {
            myPref = AndFlmsg.mysp.getString(Key, "");
            if (myPref.equals("")) {
                myReturn = Default;
            } else {
                //Try integer conversion
                try {
                    myReturn = Integer.parseInt(myPref);
                } catch (NumberFormatException ex) {
                    //Return zero is probably the best logic here since we cannot interract with the user anyway
                    loggingclass.writelog("Cannot convert preference [" + Key + "] to a number" + ex.getMessage(),
                            null, true);
                    myReturn = 0;
                }
            }
        } catch (Exception e) {
            myReturn = Default;
        }
        return myReturn;
    }

    //Reads a double from preferences, with default value
    public static double getPreferenceD(String Key, double Default) {
        double myReturn = 0;
        String myPref = "";

        try {
            myPref = AndFlmsg.mysp.getString(Key, "");
            if (myPref.equals("")) {
                myReturn = Default;
            } else {
                //Try double conversion
                try {
                    myReturn = Double.parseDouble(myPref);
                } catch (NumberFormatException ex) {
                    //Return zero is probably the best logic here since we cannot interract with the user anyway
                    loggingclass.writelog("Cannot convert preference [" + Key + "] to a number" + ex.getMessage(),
                            null, true);
                    myReturn = 0.0f;
                }
            }
        } catch (Exception e) {
            //No value entered or no preference not found
            myReturn = Default;
        }
        return myReturn;
    }


    public static boolean getPreferenceB(String Key) {
        boolean myReturn = false;

        try {
            myReturn = AndFlmsg.mysp.getBoolean(Key, false);
        } catch (Exception e) {
            myReturn = false;
        }
        return myReturn;
    }

    /**
     * Get the saved value, if its not there then use the default value
     */
    public static boolean getPreferenceB(String Key, boolean Default) {
        boolean myReturn = false;

        try {
            myReturn = AndFlmsg.mysp.getBoolean(Key, Default);
        } catch (Exception e) {
            myReturn = Default;
        }

        return myReturn;
    }


    /**
     * Sets the passes value into the assed preference Key, if its not there do nothing
     */
    public static Boolean setPreferenceS(String key, String newValue) {
        Boolean myReturn = true;

        try {
            //store value into preferences
            SharedPreferences.Editor editor = AndFlmsg.mysp.edit();
            editor.putString(key, newValue);
            // Commit the edits!
            editor.commit();
        } catch (Exception e) {
            myReturn = false;
        }
        return myReturn;
    }


    //For storing Boolean preferences
    public static boolean setPreferenceB(String pref, boolean flag) {
        Boolean myReturn = true;
        try {
            //store value into preferences
            SharedPreferences.Editor editor = AndFlmsg.mysp.edit();
            editor.putBoolean(pref, flag);
            // Commit the edits!
            editor.commit();
        } catch (Exception e) {
            myReturn = false;
        }
        return myReturn;
    }


    public static void restoreSettingsToDefault(final Activity activity) {
        AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(activity);
        myAlertDialog.setMessage(activity.getString(R.string.txt_YouWantToRestoreSettingsToDefault));
        myAlertDialog.setCancelable(false);
        myAlertDialog.setPositiveButton(activity.getString(R.string.txt_Yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        SharedPreferences.Editor editor = AndFlmsg.mysp.edit();

                        //Restore RX and TX RSID in case they were disabled by error
                        editor.putBoolean("RXRSID", false);
                        editor.putBoolean("TXRSID", false);


                        //General and GUI
                        editor.putBoolean("USEMODELIST", false);
                        editor.putString("BUTTONTEXTSIZE", "12");
                        //Modem - General
                        editor.putString("VOLUME", "10");
                        editor.putString("AFREQUENCY", "1500");
                        editor.putBoolean("SLOWCPU", false);
                        //RSID
                        editor.putBoolean("TXPOSTRSID", false);
                        editor.putBoolean("RSIDWIDESEARCH", false);
                        editor.putString("RSID_ERRORS", "2");
                        //8PSK
                        editor.putBoolean("8PSKPILOT", true);
                        editor.putString("8PSKPILOTPOWER", "-30");
                        //DominoEx
                        editor.putBoolean("DOMINOEXFILTER", true);
                        editor.putString("DOMINOEXBW", "2.0");
                        editor.putBoolean("DOMINOEXFEC", false);
                        editor.putString("DOMCWI", "0.0");
                        //Thor
                        editor.putString("THORCWI", "0.0");
                        editor.putBoolean("THORFILTER", true);
                        editor.putString("THORBW", "2.0");
                        editor.putBoolean("THORPREAMBLE", true);
                        editor.putBoolean("THORSOFTSYMBOLS", true);
                        editor.putBoolean("THORSOFTBITS", true);
                        //Olivia
                        editor.putString("OLIVIATONES", "2");
                        editor.putString("OLIVIABW", "2");
                        editor.putString("OLIVIASMARGIN", "8");
                        editor.putString("OLIVIASINTEG", "4");
                        editor.putBoolean("OLIVIARESETFEC", false);
                        editor.putBoolean("OLIVIA8BIT", true);
                        //MT63
                        editor.putBoolean("MT638BIT", true);
                        editor.putBoolean("MT63INTEGRATION", true);
                        editor.putBoolean("MT63USETONES", true);
                        editor.putBoolean("MT63TWOTONES", true);
                        editor.putString("MT63TONEDURATION", "4");
                        editor.putBoolean("MT63AT500", false);
                        //Image Attachments
                        editor.putString("TARGETMAXMEGAPIXELS", "0.5");
                        editor.putString("JPEGQUALITY", "70");
                        //Data exchange
                        editor.putBoolean("USECOMPRESSION", true);
                        editor.putString("COMPRESSIONENCODER", "1");
                        editor.putBoolean("FORCECOMPRESSION", false);
                        editor.putString("EXTRACTTIMEOUT", "4");
                        //Personnal data - No Change
                        //Date-Time format - No change
                        //File name format - No change
                        //Radiogram
                        //editor.putString("RGWORDSPERLINE", "5");
                        //editor.putBoolean("SHOWARLDESC",true);
                        //GPS Time
                        editor.putBoolean("USEGPSTIME", false);
                        editor.putString("LEAPSECONDS", "0");
                        //Commit the changes
                        editor.commit();

                        activity.onBackPressed();
                    }
                });
        myAlertDialog.setNegativeButton(activity.getString(R.string.txt_Cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        myAlertDialog.show();

    }


}

