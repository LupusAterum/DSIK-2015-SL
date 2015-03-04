/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ftpclient;

import java.io.IOException;

/**
 *
 * @author lupus
 */
public class MainClass {

    public static void main(String args[]) throws IOException {
        FTPClient client = new FTPClient();
        String host = "193.219.28.2";
        int port = 21;
        String user = "anonymous";
        String pass = "karmaz@st.amu.edu.pl";
        client.connect(host, port, user, pass);
        client.syst();
        client.list();
        //client.port(49200);
        /*client.cd("/pub/os/Linux/distr/tinycorelinux/6.x/x86/release/");
         client.list();
         client.retr("TinyCore-6.0.iso");
         client.retr("TinyCore-6.0.iso.md5.txt");*/
        //client.cup();
        // client.cd("/pub/os/Linux/distr/linuxmint/iso/stable/debian/");
        // client.retr("linuxmint-xfce-201104-dvd-64bit.iso");
        client.disconnect();

    }
}
