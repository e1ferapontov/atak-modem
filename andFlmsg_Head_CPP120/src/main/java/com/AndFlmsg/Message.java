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

    public native static String escape(String stringToEscape);

}
