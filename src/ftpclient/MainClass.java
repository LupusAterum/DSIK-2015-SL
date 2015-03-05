/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ftpclient;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author lupus
 */
public class MainClass {

    public static void main(String args[]) throws IOException, InterruptedException {
        FTPClient client = new FTPClient();
        String host = "localhost";
        int port = 21;
        String user = "anonymous";
        String pass = "anon@anon";
        client.connect(host, port, user, pass);
        client.useActiveOnPort(49200);
        File dest = new File("/home/lupus/welcome.msg");
        client.retr("welcome.msg", dest);
        client.list("NLST");
        /*
        client.list();
        client.cwd("/pub/os/Linux/distr/linuxmint/iso/stable/debian/");
        client.retr("linuxmint-xfce-201104-dvd-64bit.iso");
                */
        client.disconnect();

    }
}
