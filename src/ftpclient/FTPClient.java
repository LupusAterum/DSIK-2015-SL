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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.CharBuffer;
import java.nio.file.Path;
import java.util.StringTokenizer;
import javax.swing.JTextArea;

/**
 *
 * @author lupus
 */
public class FTPClient {

    private Socket commandSocket = null;
    private BufferedReader commandResponseReader = null;
    private BufferedWriter commandSender = null;
    private static boolean WRITE_TO_STD_OUT = false;
    private Socket passiveDataSocket = null;
    private Socket activeDataSocket = null;
    private ServerSocket activeModeSocket = null;
    private BufferedReader dataSocketReader = null;
    private BufferedWriter dataSocketWriter = null;
    private String host;
    private String activeModeIP;
    private int port = 21; //default ftp port
    private int activePort = 38600; //default use 38600 port for active connection
    private boolean usingPassive = true; //use passive by default.
    private boolean portCmdGiven = false;
    private JTextArea logger = null;
    private boolean isConnected = false;

    public FTPClient() {

    }

    public boolean isConnected() {
        return isConnected;
    }

    private void logOutput(String text) {
        if (logger == null) {
            WRITE_TO_STD_OUT = true;
        } else {
            logger.append(text);
        }
    }

    public void setOutputTo(JTextArea area) {
        logger = area;
    }

    public void switchStdOutput() {
        WRITE_TO_STD_OUT = !(WRITE_TO_STD_OUT);
    }

    public synchronized void usePassive() throws IOException {
        closeActiveDataModeSockets();
        usingPassive = true;
    }

    public synchronized void useActiveOnPort(int port) throws Exception, IOException, InterruptedException {
        if(this.activeModeIP.isEmpty() || this.activeModeIP.equals("127,0,0,1") ) {
            throw new Exception("ERR: NO ACTIVE IP");
        }
        activePort = port;
        port(activePort);
        portCmdGiven = true;
        usingPassive = false;
    }
    public synchronized void setActiveIP(String IP) {
        this.activeModeIP = IP.replace(".", ",");
        
    }
    private synchronized void closeActiveDataModeSockets() throws IOException {
        if (activeModeSocket != null) {
            activeDataSocket.close();
            activeDataSocket = null;
            activeModeSocket.close();
            activeModeSocket = null;
        }
    }

    //send data to dataSocket
    //this method should be rewrited to send binary data in mode
    //"TYPE I"
    private synchronized void sendLineData(String line) throws IOException {
        if (passiveDataSocket == null) {
            throw new IOException("Not connected in PASV");
        }
        try {
            dataSocketWriter.write(line + "\r\n");
            dataSocketWriter.flush();
            if (line != null) {
                if (WRITE_TO_STD_OUT) {
                    System.out.println("PASV TX> " + line);
                    logOutput("PASV TX> " + line + "\n");
                }
            }
        } catch (IOException e) {
            passiveDataSocket = null;
            throw e;
        }

    }

    // read data from dataSocket
    private synchronized String readDataResponse() throws IOException, InterruptedException {
        dataSocketReader.mark(1);

        if (dataSocketReader.read() != -1) {
            dataSocketReader.reset();
            String line = dataSocketReader.readLine();
            logOutput("DATA RX<" + line + "\n");
            if (WRITE_TO_STD_OUT) {
                System.out.println("DATA RX< " + line);
            }
            wait(10);
            return line;
        }

        return null;
    }

// send data to cmd socket
    private void sendCommand(String line) throws IOException {
        if (commandSocket == null) {
            throw new IOException("Not connected!");
        }
        try {
            commandSender.write(line + "\r\n");
            commandSender.flush();
            logOutput("TX>" + line + "\n");
            if (WRITE_TO_STD_OUT) {
                System.out.println("TX> " + line);
            }
        } catch (IOException e) {
            commandSocket = null;
            throw e;
        }
    }

    // get data from cmd socket
    private synchronized String readCommandResponse() throws IOException, InterruptedException {
        String line = null;
        commandResponseReader.mark(2);

        if (commandResponseReader.read() != -1) {
            commandResponseReader.reset();
            line = commandResponseReader.readLine();
        }
        logOutput("RX<" + line + "\n");
        if (WRITE_TO_STD_OUT) {
            System.out.println("RX< " + line);
        }
        return line;
    }

    // connect to server 'hostname'
    public synchronized void connect(String host) throws IOException, InterruptedException {
        connect(host, this.port, "anonymous", "anonymous@anonymous.domain");
    }

    public synchronized void connect(String host, int port) throws IOException, InterruptedException {
        connect(host, port, "anonymous", "anonymous@anonymous.domain");
    }

    public synchronized void connect(String host, int port, String user, String pass)
            throws IOException, InterruptedException {
        if (commandSocket != null) {
            throw new IOException("Already connected, disconnect first.");
        }
        commandSocket = new Socket(host, port);
        this.host = host;
        this.port = port;
        if (user == null) {
            user = "anonymous";
        }
        if (pass == null) {
            pass = "anonymous@anons.com";
        }
        commandResponseReader = new BufferedReader(new InputStreamReader(commandSocket.getInputStream()));
        commandSender = new BufferedWriter(new OutputStreamWriter(commandSocket.getOutputStream()));
        String response = "";

        while (!response.equals("220 ") && !response.equals("220 OK") && !response.startsWith("220 ")) {
            response = readCommandResponse();

        }

        if (!response.startsWith("220")) {
            throw new IOException("Unknown response: " + response);
        }

        sendCommand("USER " + user);
        response = readCommandResponse();
        while (!response.startsWith("331 ")) {
            response = readCommandResponse();
        }
        if (!response.startsWith("331")) {
            throw new IOException("Unknown response after sending USER: " + response);
        }
        sendCommand("PASS " + pass);
        response = readCommandResponse();
        while (!response.startsWith("230 ")) {
            response = readCommandResponse();
        }
        if (!response.startsWith("230")) {
            throw new IOException("Unknown response after sending PASS: " + response);
        }
        isConnected = true;
        //now logged in.
    }

    public synchronized void syst() throws IOException, InterruptedException {
        sendCommand("SYST");
        readCommandResponse();
    }

    // disconnect from server
    public synchronized void disconnect() throws IOException, InterruptedException {
        try {
            sendCommand("QUIT");
            readCommandResponse();
        } finally {
            if (passiveDataSocket != null) {
                passiveDataSocket.close();
                passiveDataSocket = null;
            }
            if (commandSocket != null) {
                commandSocket.close();
                commandSocket = null;
            }
            isConnected = false;
        }
    }

    // CWD command
    public synchronized void cwd(String dir) throws IOException, InterruptedException {
        sendCommand("CWD " + dir);
        readCommandResponse();
        sendCommand("PWD");
        readCommandResponse();
    }

    //tell FTP server to switch to passive mode.
    //opens 2nd socket for data transfer.
    public synchronized boolean enterPasv() throws IOException, InterruptedException {
        sendCommand("PASV");
        String response = readCommandResponse();
        String ip = "";
        int pasvPort = -1;
        int opening = response.indexOf('(');
        int closing = response.indexOf(')');
        if (closing > 0) {
            String dataLink = response.substring(opening + 1, closing);
            StringTokenizer tokenizer = new StringTokenizer(dataLink, ",");
            try {
                ip = tokenizer.nextToken() + "." + tokenizer.nextToken() + "."
                        + tokenizer.nextToken() + "." + tokenizer.nextToken();
                pasvPort = Integer.parseInt(tokenizer.nextToken()) * 256
                        + Integer.parseInt(tokenizer.nextToken());
            } catch (Exception e) {
                throw new IOException("badDataLink: " + response);
            }
        }
        passiveDataSocket = new Socket(ip, pasvPort);
        dataSocketReader = new BufferedReader(new InputStreamReader(passiveDataSocket.getInputStream()));
        dataSocketWriter = new BufferedWriter(new OutputStreamWriter(passiveDataSocket.getOutputStream()));
        usingPassive = true;
        return true;
    }

    // command: RETR fileName
    public synchronized boolean retr(String fileName, File destination) throws IOException, InterruptedException {
        if (destination == null) {
            destination = new File(fileName);
        } else {

        }
        if (!switchToBinary()) {
            throw new IOException("Cannot switch to binary mode.");
        }

        String commandResponse;
        if (usingPassive) {

            enterPasv();
            sendCommand("RETR " + fileName);
            commandResponse = readCommandResponse();
            if (!commandResponse.startsWith("150")) {
                throw new IOException("Cannot retrieve file... Server reported:\n" + commandResponse);
            }
            retrieveFile(passiveDataSocket, destination.getAbsolutePath());
            return (readCommandResponse().startsWith("226"));
        } else {
            createIncomingActiveDataSocket(activePort);
            sendCommand("RETR " + fileName);
            commandResponse = readCommandResponse();
            if (!commandResponse.startsWith("150")) {
                throw new IOException("Cannot retrieve file... Server reported: \n");
            }
            acceptOnActiveDataSocket();
            retrieveFile(activeDataSocket, destination.getAbsolutePath());
            closeActiveDataModeSockets();
            return (readCommandResponse().startsWith("226"));
        }

    }

    private synchronized void retrieveFile(Socket socket, String fileName) throws IOException {
        FileOutputStream out = new FileOutputStream(fileName);
        BufferedInputStream dataIn = new BufferedInputStream(socket.getInputStream());
        int bufferSize = 4096;
        byte[] inputBuffer = new byte[bufferSize];
        int bytesRead;
        int blockCount = 0;
        int offset = 0;
        logOutput("Downloading: [");
        while ((bytesRead = dataIn.read(inputBuffer, 0, bufferSize)) != -1) {
            out.write(inputBuffer, 0, bytesRead);

            if (blockCount % 32768 == 0) {
                blockCount = 0;
                logOutput(".");
            }
            blockCount++;
        }
        logOutput("]\nDownload Completed.\n");
    }

    // cmd LIST
    private synchronized boolean switchToBinary() throws IOException, InterruptedException {
        sendCommand("TYPE I");
        String response = readCommandResponse();
        return (response.startsWith("200"));
    }

    private synchronized boolean switchToAscii() throws IOException, InterruptedException {
        sendCommand("TYPE A");
        String r = readCommandResponse();
        return (r.startsWith("200"));
    }

    public synchronized boolean list(String listCommand) throws IOException, InterruptedException {
        String dataSocketResponse;
        String commandResponse;
        listCommand = listCommand.toUpperCase();
        if (!switchToAscii()) {
            throw new IOException("Cannot switch to ASCII Mode.");
        }
        wait(10);
        if (usingPassive) {
            enterPasv();
            wait(10);
            sendCommand(listCommand);
            wait(10);
            commandResponse = readCommandResponse();
            wait(10);
            if (commandResponse.startsWith("150")) {
                do {
                    dataSocketResponse = readDataResponse();
                } while (dataSocketResponse != null);
                readCommandResponse();
                return true;
            }
        } else {
            createIncomingActiveDataSocket(activePort);
            sendCommand(listCommand);
            acceptOnActiveDataSocket();
            commandResponse = readCommandResponse();
            if (commandResponse.startsWith("150")) {
                do {
                    dataSocketResponse = readDataResponse();
                } while (dataSocketResponse != null);
                readCommandResponse();
                closeActiveDataModeSockets();
                return true;
            }
        }
        return false;
    }

    private synchronized void acceptOnActiveDataSocket() throws IOException {
        if (activeDataSocket == null) {
            activeDataSocket = activeModeSocket.accept();
        }
        dataSocketReader = new BufferedReader(new InputStreamReader(activeDataSocket.getInputStream()));
        dataSocketWriter = new BufferedWriter(new OutputStreamWriter(activeDataSocket.getOutputStream()));

    }

    // sends port command.
    // using PASV is better.
    public boolean port(int port) throws IOException, InterruptedException {
        
        
        int portB = port % 256;
        int portA = (port - portB) / 256;
        String cmd = this.activeModeIP + "," + Integer.toString(portA) + "," + Integer.toString(portB);
        sendCommand("PORT " + cmd);

        createIncomingActiveDataSocket(port);
        readCommandResponse();
        portCmdGiven = true;

        return true;
    }

    private synchronized void createIncomingActiveDataSocket(int port) throws IOException {
        if (activeModeSocket == null) {
            activeModeSocket = new ServerSocket(port);
        }

    }
}
