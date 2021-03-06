package Servers;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.sun.istack.internal.Nullable;
import org.json.JSONObject;
import tools.Tools;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.Map;

public class ServerMain {
    private static String version = "1.12";
    private static String redir_ip;
    private final int PORT = 6789;
    private final int timeout = 300;
    private String nextHost = null;
    private BiMap<String, String> users = HashBiMap.create(); //IP->Nick

    /**
     * Creating server
     * @param args
     */
    public static void main(String args[]) {
        redir_ip = System.getProperty("redir");
        System.out.println("Server main " + version);
        ServerMain srv = new ServerMain();
        srv.run();
    }

     /**
     * create server socket
     * create stream for receiving messages
     * process gotten message
     */
    private void run() {
        String received_text;
        sendConnectionMessageToRedir();
        ServerSocket serverSocket = Tools.createServer(PORT);
        while (true) {
            Socket connectionSocket = Tools.makeConnectionSocket(serverSocket);
            received_text = null;
            try {
                //This will return probably -1 if disconnection
                BufferedReader inFromClient =
                        new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                received_text = inFromClient.readLine();
            } catch (IOException e) {
                System.out.println("IO_exception");
            }
            if (received_text != null) {
                System.out.println("Received: " + received_text);
                processMessage(received_text);
            } else {
                System.out.print("Client has disconnected");
                break;
            }
        }
    }
    /**
     * Tell Redirect about connection
     */
    
    private void sendConnectionMessageToRedir() {
        nextHost = null;
        org.json.JSONObject mes = new org.json.JSONObject();
        mes.put("type", 4);
        sendToRedir(mes.toString());
    }

     /**
     * create socket to connect with Redirect
     * create stream for sending messages
     */
    private void sendToRedir(String message) {
        Socket clientSocket = Tools.connectTo(redir_ip, PORT);
        try {
            DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
            outToServer.writeBytes(message + '\n');
            clientSocket.close();
        } catch (IOException e) {
            System.out.println("Can't send message");
        }
    }
    
     /**
     * @param host
     * @param PORT_next
     * creating socket with specified host and port
     * in case of failure, inform Redirect
     */

    @Nullable
    private Socket makeSenderSocket(String host, int PORT_next) {
        System.out.println("Making new connection with " + host + ":" + PORT_next);
        Socket clientSocket = new Socket();
        try {
            //Trick to set timeout
            clientSocket.connect(new InetSocketAddress(host, PORT_next), timeout);
            System.out.println("Connected");
        } catch (IOException e) {
            System.out.println("Failed to makeConnectionSocket");
            clientSocket = null;
            nextHost = null;
            sendBrokenConnectionInfo();
        }
        return clientSocket;
    }

     /**
     * send to Redirect information that connection has been broken
     */
    private void sendBrokenConnectionInfo() {
        System.out.println("Sending info of broken connection");
        org.json.JSONObject mes = new org.json.JSONObject();
        mes.put("type", 6);
        sendToRedir(mes.toString());
    }

     /**
     * @param massage taken from stream
     * according to different types of massages invoke appropriate methods
     */
    private void processMessage(String message) {
        org.json.JSONObject obj = new org.json.JSONObject(message);
        int type = obj.getInt("type");
        switch (type) {
            case 1:
                addUser(obj);
                if (nextHost != null) sendForwardMessage(message);
                break;
            case 2:
                deleteUser(obj);
                if (nextHost != null) sendForwardMessage(message);
                break;
            case 3:
                sendForwardMessage(message);
                break;
            case 4:
                manageAddingServer(message);
                break;
            case 5:
                applySynchro(obj);
                break;
            case 7:
                sendConnectionMessageToRedir();
                break;
            default:
                System.out.println(message);
                break;
        }
    }
     /**
     * @param message is JSON object 
     * updating list of users
     */

    private void applySynchro(org.json.JSONObject message) {
        System.out.println("Applying synchro = " + message.toString());
        users.clear();
        org.json.JSONObject content = new org.json.JSONObject(message.get("content").toString());
        Iterator<String> keys = content.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            users.put(key, content.getString(key));
        }

    }
    
    /** 
     * @param message which will be cast to JSON 
     * from message get IP which will be address of next host
     * synchronise
     */

    private void manageAddingServer(String message) {
        System.out.println("New srv wants to connect");
        if (nextHost == null) {
            org.json.JSONObject obj = new org.json.JSONObject(message);
            String newAddress = obj.getString("IP_from");
            nextHost = Tools.getIp(newAddress);
            System.out.println("New nextHost= " + nextHost);
            synchroNewSrv();
        } else {
            System.out.println("Forwarding request to make new srv connection");
            sendForwardMessage(message);
        }
    }

     /**
     * creating JSON object from list of users and sending them to next server
     */
    private void synchroNewSrv() {
        org.json.JSONObject mes = new org.json.JSONObject();
        org.json.JSONObject content = new org.json.JSONObject();
        mes.put("type", 5);
        for (Object o : users.entrySet()) {
            Map.Entry pair = (Map.Entry) o;
            content.put((String) pair.getKey(), pair.getValue());
        }
        mes.put("content", content);
        System.out.println("Sending synchro message = " + mes.toString());
        sendForwardMessage(mes.toString());
    }
    
    
    /**
     * @param message
     * get data from message to create new user
     */

    private void addUser(org.json.JSONObject message) {
        org.json.JSONObject content = new org.json.JSONObject(message.get("content").toString());
        String nick = content.getString("nick");
        String IP = message.getString("IP_from");
        String entry = users.get(nick);
        if (entry == null) users.put(nick, IP);
        System.out.println("Added new user " + nick + "=" + IP);
    }
    
    /**
     * @param message
     * delete specific user
     */

    private void deleteUser(org.json.JSONObject message) {
        String IP = message.getString("IP_from");
        System.out.println("All users = " + users.toString());
        String entry = users.inverse().get(IP);
        if (entry != null) {
            users.inverse().remove(IP);
            System.out.println("Deleted user");
        }
    }
     /**
     * @param message
     * getting IP from message
     * adding owner of the message
     * creating socket and output stream
     * handle break of connection
     */

    private void sendForwardMessage(String message) {
        String host = null;
        if (nextHost == null) {
            org.json.JSONObject obj = new org.json.JSONObject(message);
            String IP = getIPFromMessageObj(obj);
            if (IP != null) {
                host = IP;
            } else {
                System.out.println("No match. Ignoring message without user");
                return;
            }
            message = addFromUserToContent(obj);
        } else {
            host = nextHost;
        }
        Socket clientSocket = makeSenderSocket(host, PORT);
        if (clientSocket != null) {
            try {
                DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                outToServer.writeBytes(message + '\n');
            } catch (IOException e) {
                System.out.println("Can't send message");
            }
        }
        //This is called after sending info of broken connection. This goes again to redir to follow new path after pixing it
        else {
            String finalMessage = message;
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            System.out.println("Using task");
                            sendToRedir(finalMessage);
                        }
                    },
                    1000
            );
        }
    }
    /**
     * @param obj
     */

    @Nullable
    private String getIPFromMessageObj(JSONObject obj) {
        org.json.JSONObject content = new org.json.JSONObject(obj.get("content").toString());
        System.out.println(content.toString());
        String to = content.getString("to");
        System.out.println("User socket address" + users.get(to));
        if (users.get(to) != null)
            return Tools.getIp(users.get(to));
        else return null;
    }

    /**
     * @param obj
     * adding new field to JSON object
     */
    private String addFromUserToContent(JSONObject obj) {
        System.out.println("Sending to user. Adding from_user to the content.");
        org.json.JSONObject content = new org.json.JSONObject(obj.get("content").toString());
        String senderIp = obj.get("IP_from").toString();
        content.put("from_user", users.inverse().get(senderIp));
        obj.put("content", content);
        return obj.toString();
    }
}
