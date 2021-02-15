package com.AtakModem.server;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.AndFlmsg.Modem;
import com.AndFlmsg.loggingclass;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

// 224.10.10.1:17012 chat
// 239.2.3.1:6969 SA multicast

public class UdpReceiver extends Thread {
    private static final int MAX_UDP_DATAGRAM_LEN = 0xffff;

    public static int CHAT_PORT = 17012;
    public static int SA_PORT = 6969;
    public static String CHAT_ADDR = "224.10.10.1";
    public static String SA_ADDR = "239.2.3.1";

    protected MulticastSocket socket = null;
    protected InetAddress group = null;

    private boolean running;
    private final Context mContext;
    private WifiManager.MulticastLock mMulticastLock;


    public UdpReceiver(Context ctx, String addr, int port) throws IOException {
        mContext = ctx;

        socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        group = InetAddress.getByName(addr);
        socket.joinGroup(group);
    }

    public void stopServer() throws IOException {
        running = false;

        socket.leaveGroup(group);
        socket.close();

        if (mMulticastLock != null && mMulticastLock.isHeld()) {
            mMulticastLock.release();
        }
    }

    public void run() {
        WifiManager wifi = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            mMulticastLock = wifi.createMulticastLock("UdpReceiver" + socket.getInetAddress());
            mMulticastLock.acquire();
        }

        running = true;

        while (running) {
            try {
                final byte[] buffer = new byte[MAX_UDP_DATAGRAM_LEN];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength());

                Log.d("UDP DATAGRAM:", packet.getAddress() + "\n" + received);
                loggingclass.writelog("COT packet received, length: " + packet.getLength(), null);

                Modem.txData(received + "\n");

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}