/*
 * Message.java  
 *
 * Copyright (C) 2014 John Douyere (VK2ETA) 
 *
 * This program is distributed in the hope that it will be useful,  
 * but WITHOUT ANY WARRANTY; without even the implied warranty of  
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the  
 * GNU General Public License for more details.  
 *
 * You should have received a copy of the GNU General Public License  
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.  
 *
 */


package com.AndFlmsg;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;


public class Message {
    static {
        System.loadLibrary("AndFlmsg_Flmsg_Interface");
    }

    //Declaration of native classes
    public native static void saveEnv();

    public native static boolean ProcessWrapBuffer(String rawWrapBuffer);

    public native static String getUnwrapText();

    public native static String getUnwrapFilename();

    public native static String geterrtext();

    public native static String dateTimeStamp();

    public native static String escape(String stringToEscape);

    public static void addEntryToLog(String entry) {
        String logFileName = Processor.HomePath + Processor.Dirprefix + Processor.DirLogs +
                Processor.Separator + Processor.messageLogFile;
        File logFile = new File(logFileName);
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                loggingclass.writelog("IO Exception Error in Create file in 'addEntryToLog' " + e.getMessage(), null, true);
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(entry);
            buf.newLine();
            buf.flush();
            buf.close();
        } catch (IOException e) {
            loggingclass.writelog("IO Exception Error in add line in 'addEntryToLog' " + e.getMessage(), null, true);
        }
    }


    //Delete file
    public static boolean deleteFile(String mFolder, String fileName, boolean adviseDeletion) {

        String fullFileName = Processor.HomePath + Processor.Dirprefix + mFolder + Processor.Separator + fileName;
        File n = new File(fullFileName);
        if (!n.isFile()) {
            return false;
        } else {
            n.delete();
            if (adviseDeletion) {
                AndFlmsg.middleToastText(AndFlmsg.myContext.getString(R.string.txt_DocumentDeleted));
                addEntryToLog(Message.dateTimeStamp() + " - " + AndFlmsg.myContext.getString(R.string.txt_DocumentDeleted) + ": " + fileName);
            }
            return true;
        }
    }


    //Copy binary or text files from one folder to another CONSERVING THE NAME and CONDITIONALLY LOGGING THE ACTION
    public static boolean copyAnyFile(String originFolder, String fileName, String destinationFolder, boolean adviseCopy) {

        File dir = new File(Processor.HomePath + Processor.Dirprefix + destinationFolder);
        if (dir.exists()) {
            String fullFileName = Processor.HomePath + Processor.Dirprefix + originFolder + Processor.Separator + fileName;
            File mFile = new File(fullFileName);
            if (!mFile.isFile()) {
                loggingclass.writelog("File not found: " + fullFileName, null, true);
                return false;
            } else {
                FileOutputStream fileOutputStrm = null;
                FileInputStream fileInputStrm = null;
                try {
                    fileInputStrm = new FileInputStream(fullFileName);
                    String fullDestinationFileName = Processor.HomePath + Processor.Dirprefix + destinationFolder + Processor.Separator + fileName;
                    fileOutputStrm = new FileOutputStream(fullDestinationFileName);
                    byte[] mBytebuffer = new byte[256];
                    int byteCount = 0;
                    while ((byteCount = fileInputStrm.read(mBytebuffer)) != -1) {
                        fileOutputStrm.write(mBytebuffer, 0, byteCount);
                    }
                } catch (FileNotFoundException e) {
                    loggingclass.writelog("File not found: " + fullFileName, e, true);
                } catch (IOException e) {
                    loggingclass.writelog("Error copying: " + fileName, e, true);
                } finally {
                    try {
                        if (fileInputStrm != null) {
                            fileInputStrm.close();
                        }
                        if (fileOutputStrm != null) {
                            fileOutputStrm.close();
                        }
                    } catch (IOException e) {
                        loggingclass.writelog("File close error: " + fullFileName, e, true);
                    }
                }
                if (adviseCopy)
                    addEntryToLog(Message.dateTimeStamp() + " - " + AndFlmsg.myContext.getString(R.string.txt_CopiedFile) + ": " + fileName +
                            " " + AndFlmsg.myContext.getString(R.string.txt_ToFolder) + ": " + destinationFolder);
                return true;
            }
        } else {
            loggingclass.writelog("Directory not found: " + destinationFolder + " ", null, true);
            return false;
        }
    }

}
