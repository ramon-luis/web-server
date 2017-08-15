package server;

import javax.net.ssl.*;
import java.net.ServerSocket;
import java.io.*;
import java.security.KeyStore;

/**
 * WebServer serves files based in ./www directory
 * WebServer requires ports to be supplied for standard HTTP and secure HTTPS requests
 * WebServer client requests must match Server name and a valid port
 * WebServer supports GET and HEAD requests and limited MIME types (see constants in ClientThread class)
 * WebServer redirects based on URLs listed in /www/redirect.defs
 */

public class WebServer {

    // constants for flags (takes ports as args)
    private static final int REQUIRED_FLAG_COUNT = 2;
    private static final String FLAG_SERVER_PORT = "--serverPort=";
    private static final int FLAG_SERVER_PORT_MIN_LENGTH = FLAG_SERVER_PORT.length() + 1;
    private static final String FLAG_SSL_PORT = "--sslPort=";
    private static final int FLAG_SSL_PORT_MIN_LENGTH = FLAG_SSL_PORT.length() + 1;

    // constants for keystore info
    private static final String KEY_STORE_FILE_NAME = "server.jks";
    private static final char[] KEY_STORE_PASS = "mpcs54001".toCharArray();

    // main method to run program
    public static void main(String args[]) {
        // check if user entered input with correct format
        if (args.length != REQUIRED_FLAG_COUNT || !hasServerPortAndSSLPort(args)) {
            System.err.println("Usage: java ServerDriver " + FLAG_SERVER_PORT + "<port number> "
                + FLAG_SSL_PORT + "<port number>");
            System.exit(1);
        }

        // assign portNumber from user input
        final int iServerPort = getPortFromInput(args, FLAG_SERVER_PORT);
        final int iSSLPort = getPortFromInput(args, FLAG_SSL_PORT);

        // start independent threads to listen on standard & ssl sockets
        startServerSocketThread(iServerPort);
        startSSLSocketThread(iSSLPort);
    }

    // start the standard socket thread to listen
    private static void startServerSocketThread(final int iServerPort) {
        final boolean bIsSecure = false;
        new Thread() {
            public void run() {
                try {
                    // create the socket
                    ServerSocket serverSocket = new ServerSocket(iServerPort);
                    System.out.println("Ramon's Killer (unsecure) Server waiting for client on port " + iServerPort);

                    // loop to listen -> start a new ClientThread if socket accepts & binds with new client
                    while (true) {
                        new ClientThread(serverSocket.accept(), bIsSecure).start();
                    }
                } catch (IOException e) {
                    System.out.println("There was an error setting up the (unsecure) server socket:");
                    System.out.println("  " + e);
                }
            }
        }.start();
    }

    // start the SSL socket thread to listen
    private static void startSSLSocketThread(final int iSSLPort) {
        final boolean bIsSecure = true;
        new Thread() {
            public void run() {
                try {
                    // setup params for secure connection
                    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                    ks.load(new FileInputStream(KEY_STORE_FILE_NAME), KEY_STORE_PASS);
                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(ks, KEY_STORE_PASS);
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(kmf.getKeyManagers(), null, null);

                    // create the secure socket
                    SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
                    SSLServerSocket sslServer = (SSLServerSocket) ssf.createServerSocket(iSSLPort);
                    System.out.println("Ramon's Killer (SECURE) Server waiting for client on port " + iSSLPort);

                    // loop to listen -> start a new ClientThread if socket accepts & binds with new client
                    while (true) {
                        new ClientThread(sslServer.accept(), bIsSecure).start();
                    }
                } catch (Exception e) {
                    System.out.println("There was an error setting up the (SECURE) ssl socket:");
                    System.out.println("  " + e);
                }
            }
        }.start();
    }


    //*************************
    //  USER FLAG METHODS
    //*************************

    // check that user has provided serverPort and sslPort
    private static boolean hasServerPortAndSSLPort(String[] sUserInputs) {
        if (sUserInputs == null || sUserInputs.length != REQUIRED_FLAG_COUNT) {
            return false;
        }
        return ((isFlagWithServerPort(sUserInputs[0]) && isFlagWithSSLPort(sUserInputs[1])) ||
                isFlagWithServerPort(sUserInputs[1]) && isFlagWithSSLPort(sUserInputs[0]));
    }

    // check that the user input is a server flag and port number
    private static boolean isFlagWithServerPort(String sUserInput) {
        if (sUserInput == null || sUserInput.length() < FLAG_SERVER_PORT_MIN_LENGTH) {
            return false;
        }

        // get the flag label and port number as strings
        String sFlagLabel = sUserInput.substring(0, FLAG_SERVER_PORT.length());
        String sPort = sUserInput.substring(FLAG_SERVER_PORT_MIN_LENGTH);

        // check that the flag label matches expected form and that port is a number
        return isServerFlagLabel(sFlagLabel) && isInteger(sPort);
    }

    // check that the user input is an ssl flag and port number
    private static boolean isFlagWithSSLPort(String sUserInput) {
        if (sUserInput == null || sUserInput.length() < FLAG_SSL_PORT_MIN_LENGTH) {
            return false;
        }

        // get the flag label and port number as strings
        String sFlagLabel = sUserInput.substring(0, FLAG_SSL_PORT.length());
        String sPort = sUserInput.substring(FLAG_SSL_PORT_MIN_LENGTH);

        // check that the flag label matches expected form and that port is a number
        return isSSLFlagLabel(sFlagLabel) && isInteger(sPort);
    }

    // get the port number from the user input
    private static int getPortFromInput(String[] sArgs, String sServerType) {
        // assign args to Strings
        String sArg0 = sArgs[0];
        String sArg1 = sArgs[1];
        String sUserInput;

        // pick argument based on server type - used to get the port from the arg
        if (sServerType.equalsIgnoreCase(FLAG_SERVER_PORT)) {
            sUserInput = (isFlagWithServerPort(sArg0) ? sArg0 : sArg1);
        } else {
            sUserInput = (isFlagWithSSLPort(sArg0) ? sArg0 : sArg1);
        }

        // get count of letters in label
        int iLabelCount = (isFlagWithServerPort(sUserInput) ? FLAG_SERVER_PORT_MIN_LENGTH : FLAG_SSL_PORT_MIN_LENGTH);

        // return integer from end of user input string
        return Integer.parseInt(sUserInput.substring(iLabelCount - 1));
    }

    // check that flag input matches expected form for SSL
    private static boolean isSSLFlagLabel(String sFlagLabel) {
        // check for null
        if (sFlagLabel == null) {
            return false;
        }

        // compare to expected flag label
        return FLAG_SSL_PORT.compareToIgnoreCase(sFlagLabel) == 0;
    }

    // check that flag input matches expected form for Server
    private static boolean isServerFlagLabel(String sFlagLabel) {
        // check for null
        if (sFlagLabel == null) {
            return false;
        }

        // compare to expected flag label
        return FLAG_SERVER_PORT.compareToIgnoreCase(sFlagLabel) == 0;
    }

    // check that string is an integer
    private static boolean isInteger(String sInteger) {
        // check against null
        if (sInteger == null) {
            return false;
        }

        // check against zero length string
        int length = sInteger.length();
        if (length == 0) {
            return false;
        }

        // check first char
        char cFirstChar = sInteger.charAt(0);
        if (cFirstChar == '-' && length == 1) {
            return false;
        } else if (cFirstChar < '0' || cFirstChar > '9') {
            return false;
        }

        // check remaining chars in string
        char c;
        for (int i = 1; i < length; i++) {
            c = sInteger.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }

        // all chars are integers (or negative integer)
        return true;
    }
}