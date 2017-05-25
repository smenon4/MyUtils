//package com.scb;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This can be used to simulate pulling out a network cable.
 *
 * USAGE EXAMPLE:  -listen=9999  -proxy=some.host.com:23000
 *
 * This process just reads all data from the -listen host and sends it to the -proxy host
 * and vice-versa.
 */
public class Proxy {

    int listenPort;
    String proxyHost;
    int proxyPort;

    Socket listenTo;
    Socket sendTo;

    volatile boolean connected;

    public static void main(String[] args) throws Exception {

        int listenPort = 9998;
        String proxyHost = "xxx";
        int proxyPort = 0;


        for(int i=0; i<args.length; i++) {
            if( args[i].startsWith("-listen=")) {
                listenPort = parsePort(args[i]);
            }
            else if( args[i].startsWith("-proxy=")) {
                proxyHost = parseHost(args[i]);
                proxyPort = parsePort(args[i]);
            }
        }

        if(  proxyHost==null || listenPort==0 || proxyPort == 0) {
            usage();
            return;
        }

        Proxy app = new Proxy(listenPort, proxyHost, proxyPort);
        app.run();
    }

    static void usage() {
        System.err.println("incorrect arguments");
        System.out.println("usage: -listen=host:port -proxy=host:port");
    }

    Proxy(int listenPort, String proxyHost, int proxyPort) {
        this.listenPort = listenPort;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        System.out.println("CLIENT: localhost:" + listenPort);
        System.out.println("PROXY: " + proxyHost + ":" + proxyPort);
    }

    static String parseHost(String s) {
        int n = s.indexOf('=');
        s = s.substring(n+1);
        String[] ss = s.split("[:]");
        return ss[0];
    }

    static int parsePort(String s) {
        int n = s.indexOf('=');
        s = s.substring(n+1);
        if( s.contains(":")) {
            String[] ss = s.split("[:]");
            return Integer.parseInt(ss[1]);
        }
        else {
            return Integer.parseInt(s);
        }
    }

    void run() throws Exception {
        listenForClient();
    }

    void listenForClient() throws Exception {

        ServerSocket ssd = new  ServerSocket(listenPort);
        for(;;) {
            System.out.println("WAITING FOR CLIENT CONNECTION");
            listenTo = ssd.accept();
            System.out.println("CLIENT CONNECTED: " + listenTo.getInetAddress());
            if( connected ) {
                System.err.println("already connected, reject connection  attempt from " + listenTo.getInetAddress());
                listenTo.close();
                continue;
            }
            connected = true;
            sendTo = new Socket(proxyHost, proxyPort);
            System.out.println("SERVER CONNECTED: " + sendTo.getInetAddress());
            runSession();
        }
    }

    void runSession() {
        Thread tCli = new  Thread() {
            public void run() {
                forward(listenTo, sendTo, "read:cli->server");
            }
        };
        tCli.setName("ListenClient");
        tCli.setDaemon(true);
        tCli.start();
        System.out.println("CLIENT LISTENER STARTED " );

        Thread tProxy = new  Thread() {
            public void run() {
                forward(sendTo, listenTo, "read:server->cli");
            }
        };
        tProxy.setName("Proxy");
        tProxy.setDaemon(true);
        tProxy.start();
        System.out.println("PROXY FORWARDER STARTED");
    }

    void forward(Socket from, Socket sendTo, String name) {

        int byteCount = 0;

        try {
            byte[] buff = new byte[20480];
            InputStream in = from.getInputStream();
            OutputStream out = sendTo.getOutputStream();
            for(;;) {
                int n = in.read(buff, 0, buff.length);
                if( n < 0 ) {
                    System.err.println("READ ERROR: " + n +" "+name);
                }
                out.write(buff, 0, n);
                if( byteCount == 0 ) {
                    System.out.println("process bytes count=" + n);
                    byteCount += n;
                }
                else {
                    byteCount += n;
                    if( (byteCount%10000)==0 ) {
                        System.out.println("process bytes count=" + byteCount);
                    }
                }
            }
        }
        catch( Exception e) {
            System.err.println("CONNECTION ERROR " + e.getMessage()+" "+name);
            closeAll();
            connected = false;

        }
    }

    void closeAll() {
        try {
            listenTo.close();
        }
        catch( Exception e) {

        }
        try {
            sendTo.close();
        }
        catch( Exception e) {

        }
    }
}
