package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.StringTokenizer;

/**
 * ClientThread is a unique thread for each client connection to WebServer
 * ClientThread can be secure HTTPS or unsecure HTTP
 * ClientThread supports GET and HEAD requests and limited MIME types
 * ClientThread redirects based on URLs listed in /www/redirect.defs
 */

public class ClientThread extends Thread {

    // constants for server
    static final String[] SUPPORTED_METHODS = {"GET", "HEAD"};
    static final String[] SUPPORTED_FILE_TYPES = {".html", ".htm", ".txt", ".pdf", ".png", ".jpeg", ".jpg"};
    static final File ROOT_FOLDER = new File ("./www");
    static final String REDIRECT_FILE_NAME = "./www/redirect.defs";
    static final String HTML_START = "<html><body><b>";
    static final String HTML_END = "</b></body></html>";
    static final String END_LINE = "\r\n";

    // server instance variables
    private Socket connectedClient;
    private boolean bIsSecure;
    private Map<String, String> sRedirects;
    private List<String> sAvailableFiles;
    private BufferedReader inFromClient ;
    private DataOutputStream outToClient;

    // server constructor
    public ClientThread(Socket clientSocket, boolean bIsSecure) {
        // assign socket to communicate with client
        connectedClient = clientSocket;
        this.bIsSecure = bIsSecure;

        // out to console - new server started
        String sSocketType = (bIsSecure ? "(SECURE)" : "(unsecure)");
        System.out.println("New " + sSocketType + " client thread started");

        // create map for redirects & get list of valid files to serve from root folder
        sRedirects = new HashMap<>();
        sRedirects = getRedirects(REDIRECT_FILE_NAME);
        sAvailableFiles = new ArrayList<>();
        sAvailableFiles = getValidFiles(ROOT_FOLDER);
    }

    // extends Thread -> runnable
    public void run() {
        // variables to store key HTTP(S) request information
        String sReadLine;
        boolean bNewHTTPHeader = true;
        String sHTTPMethod = "";
        String sHTTPRequest = "";
        boolean bPersistentConnection = true;

        // print the client IP:port
        System.out.println("client " + connectedClient.getInetAddress() + ":"
                + connectedClient.getPort() + " is connected");
        System.out.println();

        // create BufferedReader to read in from socket & DataOutputStream to send out to socket
        try {
            inFromClient = new BufferedReader(new InputStreamReader(connectedClient.getInputStream()));
            outToClient = new DataOutputStream(connectedClient.getOutputStream());

        } catch (IOException e) {
            System.out.println("There was an error setting up the buffered reader, " +
                    "data stream, or reading from the client:");
            System.out.println("  " + e);
        }

        // process the HTTP(S) request
        try {
            // break apart the input to read the http method and request
            while ((sReadLine = inFromClient.readLine()) != null) {
                // create a tokenizer to parse the string
                StringTokenizer tokenizer = new StringTokenizer(sReadLine);

                // if new HTTP header, then pull out the Method and Request (first line)
                if (bNewHTTPHeader) {
                    sHTTPMethod = tokenizer.nextToken().toUpperCase(); // HTTP method: GET, HEAD
                    sHTTPRequest = tokenizer.nextToken().toLowerCase(); // HTTP query: file path
                    bNewHTTPHeader = false;  // next lines are not part of a new HTTP header

                    // print to console
                    System.out.println("New HTTP Header:");
                    System.out.println("  HTTP Method: " + sHTTPMethod);
                    System.out.println("  HTTP Request: " + sHTTPRequest);

                    // not a new HTTP header: check for key params Connection or empty line
                } else if (sReadLine.length() > 0) {
                    // get the next token in the input line
                    String sParam = tokenizer.nextToken();

                    //  check to update connection status
                    if (sParam.equalsIgnoreCase("Connection:")) {
                        String sConnectionRequest = tokenizer.nextToken();
                        bPersistentConnection = (sConnectionRequest.equalsIgnoreCase("keep-alive"));
                        // print param to console
                        System.out.println("  param: " + sParam + " " + sConnectionRequest);
                    } else {
                        // otherwise just print param to console
                        System.out.println("  param: " + sParam);
                    }

                    // no more lines to header: header is over
                } else {
                    //print to console
                    System.out.println("End of Header");
                    System.out.println();

                    // get the status code & response, then send back to client
                    int iStatusCode = getStatusCode(sHTTPMethod, sHTTPRequest);
                    String sResponse = getResponse(sHTTPMethod, sHTTPRequest, iStatusCode);
                    sendResponse(sHTTPMethod, sHTTPRequest, iStatusCode, sResponse, bPersistentConnection);

                    // next line is part of a new HTTP header
                    bNewHTTPHeader = true;
                }
            }

            // loop has ended: client input is null (client must want to disconnect)
            this.connectedClient.close();
            System.out.println("input from client " + connectedClient.getInetAddress() +
                    + connectedClient.getPort() + " is null.  This socket closed.");
        } catch (IOException e) {
            System.out.println("There was an error reading the HTTP request:");
            System.out.println("  " + e);
        }
    }


    //*********************
    //  MAIN HTTP METHODS
    //*********************

    // send the HTTP response back to the client
    private void sendResponse(String sHTTPMethod, String sHTTPRequest, int iStatusCode, String sResponse, boolean bPersistentConnection) {
        // determine if sending file and if redirect -> determines response
        boolean bIsFileSend = sHTTPMethod.equalsIgnoreCase("GET") && iStatusCode == 200;
        boolean bIsRedirect = iStatusCode == 301;

        // write header
        String sStatus = getStatusLine(iStatusCode) + END_LINE;
        String sLocation = (bIsRedirect) ? ("Location: " + sResponse) + END_LINE : ("");  // only if redirect
        String sServerDetails = getServerDetails() + END_LINE;
        String sContentLength = "Content-Length: " + sResponse.length() + END_LINE;
        String sContentType = getContentType(sHTTPRequest) + END_LINE;
        String sConnection = getConnectionLine(bPersistentConnection) + END_LINE;
        String sSpaceBetweenHeaderAndBody = END_LINE;
        FileInputStream fin = null;

        // update content length if sending a file -> create file input stream
        if (bIsFileSend) {
            try {
                fin = new FileInputStream(ROOT_FOLDER.toString() + "/" + sResponse);  // if file request, then sResponse is the file path
                sContentLength = "Content-Length: " + Integer.toString(fin.available()) + END_LINE;
            } catch (IOException e) {
                System.out.println("There was an error creating the file input stream for the response:");
                System.out.println("  " + e);
            }
        }

        try {
            // send HTTP Header
            outToClient.writeBytes(sStatus);
            if (bIsRedirect) {
                outToClient.writeBytes(sLocation); // only send Location on redirect
            }
            outToClient.writeBytes(sServerDetails);
            outToClient.writeBytes(sContentType);
            outToClient.writeBytes(sContentLength);
            outToClient.writeBytes(sConnection);
            outToClient.writeBytes(sSpaceBetweenHeaderAndBody);

            // send HTTP Body
                if (bIsFileSend) {
                    sendFile(fin, outToClient);  // send file
                } else if (!bIsRedirect && !sHTTPMethod.equalsIgnoreCase("HEAD")) {
                    outToClient.writeBytes(sResponse);  // send HTML msg back
                }

            // print HTTP Header and Body to console
            System.out.println(sStatus);
            System.out.println(sServerDetails);
            System.out.println(sContentType);
            System.out.println(sContentLength);
            System.out.println(sConnection);
            if (bIsRedirect) {
                System.out.println(sLocation);
            }
            if (bIsFileSend) {
                System.out.println("File sent: " + sResponse);
            } else {
                System.out.println("Response: " + sResponse);
            }
            System.out.println();

            // close connection
            if (!bPersistentConnection) {
                outToClient.close();
            }

        } catch (IOException e) {
            System.out.println("writeBytes did not complete:");
            System.out.println("  " + e + "\n");
        }
    }

    // send the file
    private void sendFile(FileInputStream fin, DataOutputStream out) {
        byte[] buffer = new byte[1024];
        int bytesRead;

        try {
            while ((bytesRead = fin.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            fin.close();
        } catch (IOException e) {
            System.out.println("There was an error sending the file to the client:");
            System.out.println("  " + e);
        }
    }


    //****************
    //  HTTP HELPERS
    //****************

    // get the status code for based on the HTTP method and request from the client
    private int getStatusCode(String sHTTPMethod, String sHTTPRequest) {
        // default is internal server error
        int iStatusCode = 500;

        // update based on method and request
        if (!isSupportedMethod(sHTTPMethod)) {
            iStatusCode = 403;
        } else if (isRedirect(sHTTPRequest)) {
            iStatusCode = 301;
        } else if (!isValidFile(sHTTPRequest)) {
            iStatusCode = 404;
        } else if (!isValidContentType(sHTTPRequest)) {
            iStatusCode = 415;
        } else if (isSupportedMethod(sHTTPMethod) && !isRedirect(sHTTPRequest)
                && isValidFile(sHTTPRequest) && isValidContentType(sHTTPRequest)) {
            iStatusCode = 200;
        }

        // return final status code
        return iStatusCode;
    }

    // check if HTTP method is supported
    private boolean isSupportedMethod(String sHTTPMethod) {
        return Arrays.asList(SUPPORTED_METHODS).contains(sHTTPMethod.toUpperCase());
    }

    // get the response that should be sent to the client based on HTTP method, request, & status code
    private String getResponse(String sHTTPMethod, String sHTTPRequest, int iStatusCode) {
        // default is internal server error
        String sResponse = HTML_START + "There was an internal error with the server." + HTML_END;

        // update based on status code
        if (iStatusCode == 403) {
            sResponse = HTML_START + "HTTP method not supported" + HTML_END;
        } else if (iStatusCode == 301) {
            sResponse = getRedirectURL(sHTTPRequest);
        } else if (iStatusCode == 404) {
            sResponse = HTML_START + "File not found" + HTML_END;
        } else if (iStatusCode == 415) {
            sResponse = HTML_START + "The server does not support this file type" + HTML_END;
        } else if (iStatusCode == 200 ) {
            sResponse = (sHTTPMethod.equalsIgnoreCase("GET")) ? getFilePath(sHTTPRequest) : "";
        }

        // return final response
        return sResponse;
    }

    // get the status line to return based on status code
    private String getStatusLine(int iStatusCode) {
        String sStatus = "HTTP/1.1 ";
        if (iStatusCode == 200) {
            sStatus += iStatusCode + " OK";
        } else if (iStatusCode == 301) {
            sStatus += iStatusCode + " Moved Permanently";
        } else if (iStatusCode == 403) {
            sStatus += iStatusCode + " Forbidden";
        } else if (iStatusCode == 404) {
            sStatus += iStatusCode + " Not Found";
        } else if (iStatusCode == 415) {
            sStatus += iStatusCode + " Unsupported Media Type";
        } else if(iStatusCode == 500) {
            sStatus += iStatusCode + " Internal Server Error";
        }
        return sStatus;
    }

    // get the connection line
    private String getConnectionLine(boolean bPersistentConnection) {
        String sConnection = "Connection: ";
        String sConnectionStatus = (bPersistentConnection ? "keep-alive" : "close");
        return sConnection + sConnectionStatus;
    }

    // get the server details (secure or unsecure)
    private String getServerDetails() {
        String sServerLine = "Server: Ramon's Killer ";
        String sSocketType = (bIsSecure ? "(SECURE)" : "(unsecure)");
        sServerLine = sServerLine + sSocketType + " Server";
        return sServerLine;
    }

    // get the content type to return
    private String getContentType(String sHTTPRequest) {
        String sContentType = "Content-Type: ";

        // update based on file extension or if response starts with HTML/URL
            String sFileExtension = getFileExtension(sHTTPRequest);
            if (sFileExtension.equalsIgnoreCase(".html") || sFileExtension.equalsIgnoreCase(".htm")) {
                sContentType += "text/html";
            } else if (sFileExtension.equalsIgnoreCase(".txt")) {
                sContentType += "text/plain";
            } else if (sFileExtension.equalsIgnoreCase(".pdf")) {
                sContentType += "application/pdf";
            } else if (sFileExtension.equalsIgnoreCase(".png")) {
                sContentType += "image/png";
            } else if (sFileExtension.equalsIgnoreCase(".jpeg") || sFileExtension.equalsIgnoreCase(".jpg")) {
                sContentType += "image/jpeg";
            } else {
                sContentType += "text/html";  // default response
            }

        // return final content type
        return sContentType;
    }

    // check if request is redirect
    private boolean isRedirect(String sHTTPRequest) {
        return sRedirects.containsKey(sHTTPRequest);
    }

    // get the final URL location if request is a redirect
    private String getRedirectURL(String sHTTPRequest) {
        return sRedirects.get(sHTTPRequest);
    }

    // check if content type is supported
    private boolean isValidContentType(String sHTTPRequest) {
        String sFileType = getFileExtension(sHTTPRequest);
        return Arrays.asList(SUPPORTED_FILE_TYPES).contains(sFileType);
    }

    // check if HTTP request is a valid file - i.e. among available files in root server
    private boolean isValidFile(String sHTTPRequest) {
        String sRootFolder = "/" + ROOT_FOLDER.getName();
        return sAvailableFiles.contains(sRootFolder + sHTTPRequest.toLowerCase());
    }

    // get file extension, http://stackoverflow.com/questions/3571223/how-do-i-get-the-file-extension-of-a-file-in-java
    private String getFileExtension(String sFileName) {
        String sExt = "";
        int i = sFileName.lastIndexOf('.');
        if (i > 0) {
            sExt = sFileName.substring(i).toLowerCase();
        }
        return sExt;
    }

    // get the file path for the HTTP request
    private String getFilePath(String sHTTPRequest) {
        return sHTTPRequest.replaceFirst("/", "");

    }


    //*************************
    //  INITIALIZATION HELPERS
    //*************************

    // get list of valid file requests
    private List<String> getValidFiles(File currentFolder) {
        File[] subFiles = currentFolder.listFiles();
        List<String> sValidFiles = new ArrayList<>();

        // recurse through root directory, http://stackoverflow.com/questions/1844688/read-all-files-in-a-folder
        // valid file must not be a directory, not be the redirect file, and be a supported content type
        if (subFiles != null) {
            for (File fileEntry : subFiles) {
                String sFileName = fileEntry.getPath();
                if (fileEntry.isDirectory()) {
                    sValidFiles.addAll(getValidFiles(fileEntry));
                } else if (isValidContentType(sFileName) && !sFileName.equals(REDIRECT_FILE_NAME)) {
                    sValidFiles.add(sFileName.replaceFirst(".","").toLowerCase());
                }
            }
        }
        return sValidFiles;
    }

    // get a map of redirect original URLS and matching location URLs to redirect client
    private Map<String, String> getRedirects(String sRedirectFileName) {
        try {
            // create Buffered reader to read file and String to store each line
            BufferedReader reader = new BufferedReader(new FileReader(sRedirectFileName));
            String line;

            // read each line with space delimiter in form: sOriginal sNewURL
            while ((line = reader.readLine()) != null) {
                StringTokenizer tokenizer = new StringTokenizer(line);
                String sOriginalRequest =  tokenizer.nextToken();
                String sNewURL = tokenizer.nextToken();
                sRedirects.put(sOriginalRequest, sNewURL);
            }
            reader.close();
        } catch (IOException e) {
            System.out.println("There was an error created the map of redirects:");
            System.out.println("  " + e);
        }

        return sRedirects;
    }

}