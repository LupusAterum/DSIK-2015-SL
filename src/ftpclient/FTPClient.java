/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ftpclient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 *
 * @author lupus
 */
public class FTPClient {

    private Socket commandSocket = null;
    private BufferedReader commandResponseReader = null;
    private BufferedWriter commandSender = null;
    private static boolean DEBUG = true;
    private Socket dataSocket = null;
    private BufferedReader dataSocketReader = null;
    private BufferedWriter dataSocketWriter = null;
    private String host;
    private int port;
    //private boolean passive = false;

    public FTPClient() {

    }

    public void switchDebugInfo() {
        DEBUG = !(DEBUG);
    }

    //send data to dataSocket
    //this method should be rewrited to send binary data in mode
    //"TYPE I"

    private synchronized void sendLineData(String line) throws IOException {
        if (dataSocket == null) {
            throw new IOException("Not connected in PASV");
        }
        try {
            dataSocketWriter.write(line + "\r\n");
            dataSocketWriter.flush();
            if (DEBUG) {
                if (line != null) {
                    System.out.println("PASV TX> " + line);
                }
            }
        } catch (IOException e) {
            dataSocket = null;
            throw e;
        }

    }

    // read data from dataSocket

    private String readLineData() throws IOException {
        String line = dataSocketReader.readLine();
        if (DEBUG) {
            System.out.println("PASV RX< " + line);
        }
        return line;
    }

    // send data to cmd socket

    private void sendCommand(String line) throws IOException {
        if (commandSocket == null) {
            throw new IOException("Not connected!");
        }
        try {
            commandSender.write(line + "\r\n");
            commandSender.flush();
            if (DEBUG) {
                System.out.println("TX> " + line);
            }
        } catch (IOException e) {
            commandSocket = null;
            throw e;
        }
    }

    // get data from cmd socket

    private String readResponse() throws IOException {
        String line = commandResponseReader.readLine();
        if (DEBUG) {
            System.out.println("RX< " + line);
        }
        return line;
    }

    // connect to server 'hostname'
    public synchronized void connect(String host) throws IOException {
        connect(host, 21, "anonymous", "anonymous@anonymous.domain");
    }
    public synchronized void connect(String host, int port) throws IOException {
        connect(host, port, "anonymous", "anonymous@anonymous.domain");
    }
    public synchronized void connect(String host, int port, String user, String pass)
            throws IOException {
        if (commandSocket != null) {
            throw new IOException("Already connected, disconnect first.");
        }
        commandSocket = new Socket(host, port);
        this.host = host;
        this.port = port;
        commandResponseReader = new BufferedReader(new InputStreamReader(commandSocket.getInputStream()));
        commandSender = new BufferedWriter(new OutputStreamWriter(commandSocket.getOutputStream()));
        String response = "";

        while (!response.equals("220 ") && !response.equals("220 OK")) {
            response = readResponse();
        }
        if (!response.startsWith("220")) {
            throw new IOException("Unknown response: " + response);
        }
        sendCommand("USER " + user);
        response = readResponse();
        if (!response.startsWith("331")) {
            throw new IOException("Unknown response after sending USER: " + response);
        }
        sendCommand("PASS " + pass);
        response = readResponse();
        if (!response.startsWith("230")) {
            throw new IOException("Unknown response after sending PASS: " + response);
        }
        //now logged in.
    }

    public synchronized void syst() throws IOException {
        sendCommand("SYST");
        readResponse();
    }

    // disconnect from server

    public synchronized void disconnect() throws IOException {
        try {
            sendCommand("QUIT");
            readResponse();
        } finally {
            commandSocket = null;
        }
    }

    // CWD command

    public synchronized void cd(String dir) throws IOException {
        sendCommand("CWD " + dir);
        readResponse();
        sendCommand("PWD");
        readResponse();
    }

    //tell FTP server to switch to passive mode.
    //opens 2nd socket for data transfer.

    public synchronized boolean enterPasv() throws IOException {
        sendCommand("PASV");
        String response = readResponse();
        String ip = "";
        int port = -1;
        int opening = response.indexOf('(');
        int closing = response.indexOf(')');
        if (closing > 0) {
            String dataLink = response.substring(opening + 1, closing);
            StringTokenizer tokenizer = new StringTokenizer(dataLink, ",");
            try {
                ip = tokenizer.nextToken() + "." + tokenizer.nextToken() + "."
                        + tokenizer.nextToken() + "." + tokenizer.nextToken();
                port = Integer.parseInt(tokenizer.nextToken()) * 256
                        + Integer.parseInt(tokenizer.nextToken());
            } catch (Exception e) {
                throw new IOException("badDataLink: " + response);
            }
        }
        dataSocket = new Socket(ip, port);
        dataSocketReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
        dataSocketWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream()));
        return true;
    }

    // command: RETR fileName

    public synchronized boolean retr(String fileName) throws IOException {
        enterPasv();
        sendCommand("TYPE I"); // switch to binary mode
        String response = readResponse();
        if (!response.startsWith("200")) {
            throw new IOException("Cannot switch to binary mode, server reported:\n" + response);
        }
        sendCommand("RETR " + fileName);
        response = readResponse();
        if (!response.startsWith("150")) {
            throw new IOException("Cannot retrieve file... Server reported:\n" + response);
        }
        FileOutputStream out = new FileOutputStream(fileName);
        BufferedInputStream dataIn = new BufferedInputStream(dataSocket.getInputStream());
        int bufferSize = 4096;
        byte[] inputBuffer = new byte[bufferSize];
        int i = 0;
        int c = 0;
        int offset = 0;
        System.out.print("Downloading: [");
        while ((i = dataIn.read(inputBuffer, 0, bufferSize)) != -1) {
            out.write(inputBuffer, 0, i);

            if (c % 32768 == 0) {
                c = 0;
                System.out.print(".");
            }
            c++;
        }
        System.out.print("]\nDownload Completed.\n");
        return (readResponse().startsWith("226"));
    }

    // cmd LIST

    public synchronized boolean list() throws IOException {
        if (enterPasv()) {
            sendCommand("LIST");
            String response = readResponse();
            if (response.startsWith("150")) {
                while (readLineData() != null) {
                }
                readResponse();
                return true;
            }
            return false;
        } else {
            return false;
        }
    }
    // currently unimplemented. use only passive mode
/*    public synchronized boolean port(int port) throws IOException{
     String ip = "150,254,106,92";
     int portB = port % 256;
     int portA = (port - portB) / 256;
     String cmd = "=" + ip + "," + Integer.toString(portA) + "," + Integer.toString(portB);
     sendLine("PORT "+cmd);
     readLine();
     dataSocket = new Socket("localhost", port);
     readLineData();
     return true;
     }*/

}
