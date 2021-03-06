package Redirect;


import org.json.JSONObject;
import tools.Tools;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Objects;

public class RedirectClientThread extends Thread {
    private static String nextHost = null;
    private static ArrayList<String> servers = new ArrayList<>();
    private final int PORT = 6789;
    private final int timeout = 200;
    private Socket socket;
    private BufferedReader brinp = null;

    /**
     * In constructor creating stream
     * @param clientSocket which is assigned to socket field 
     */
    RedirectClientThread(Socket clientSocket) {
        System.out.println("New client ip=" + clientSocket.getRemoteSocketAddress());
        socket = clientSocket;
        try {
            InputStream inp = socket.getInputStream();
            brinp = new BufferedReader(new InputStreamReader(inp));
        } catch (IOException e) {
            System.out.println("Cannot initialize socket");
            e.printStackTrace();
        }
    }

    /**
     * Reading messages from stream and interpreting them
     */
    public void run() {
        String line;
        while (true) {
            try {
                line = brinp.readLine();
                if ((line == null) || line.equalsIgnoreCase("QUIT")) {
                    System.out.println("Client has disconnected");
                    socket.close();
                    return;
                } else {
                    line = addIpToMessage(line);
                    processMessage(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }
    
    /**
     * @param host for creating socket
     * @param PORT_next for creating socket
     * @return socket
     * handle break of connection
     */

    private Socket make_connection(String host, int PORT_next) {
        System.out.println("Making new connection with " + host + ":" + PORT_next);
        Socket clientSocket = new Socket();
        try {
            //Trick to set timeout
            clientSocket.connect(new InetSocketAddress(host, PORT), timeout);
            System.out.println("Connected");
        } catch (IOException e) {
            System.out.println("Failed to makeConnectionSocket");
            clientSocket = null;
            nextHost = null;
            System.out.println("First is broken");
            handleFirstBrokenConnection();
        }
        return clientSocket;
    }
    
    /**
     * adding addition information to message
     * @param message which will be cast to JSON
     * @return String in JSON format
     */

    private String addIpToMessage(String message) {
        org.json.JSONObject obj = new org.json.JSONObject(message);
        String IPfrom = socket.getRemoteSocketAddress().toString();
        if (!servers.contains(Tools.getIp(IPfrom))) {
            obj.put("IP_from", IPfrom);
        }
        return obj.toString();
    }

    /**
     * according to type of message invoke appropriate method
     * @param message which will be cast to JSON
     */
    private void processMessage(String message) {
        org.json.JSONObject obj = new org.json.JSONObject(message);
        int type = obj.getInt("type");
        switch (type) {
            case 4:
                addNewSrv(obj);
                break;
            case 6:
                handleBrokenConnection();
                break;
            default:
                sendForward(message);
                break;
        }
    }
    /**
     * get IP of server whose connection broke
     * remove it from the list
     */

    private void handleBrokenConnection() {
        String ipOfBrokenConnection = Tools.getIp(socket.getRemoteSocketAddress().toString());
        System.out.println("Received info of broken connection from " + ipOfBrokenConnection);
        for (int i = 0; i < servers.size(); i++)
            if (Objects.equals(servers.get(i), ipOfBrokenConnection)) {
                servers.remove(i + 1);
                for (int j = i + 1; j < servers.size(); ) {
                    sendConnectAgainOrder(servers.get(j));
                    servers.remove(j);
                }
                break;
            }
        System.out.println("Servers after deleting broken ones " + servers.toString());
    }

    private void handleFirstBrokenConnection() {
        System.out.println("Handling first broken srv");
        servers.remove(0);
        for (int j = 0; j < servers.size(); ) {
            String tempSrvIp = servers.get(j);
            servers.remove(j);
            sendConnectAgainOrder(tempSrvIp);
        }
    }
    
    /**
     * 
     * @param target which specifies receiver of message
     */

    private void sendConnectAgainOrder(String target) {
        System.out.println("Sending order to connect again to " + target);
        org.json.JSONObject mes = new org.json.JSONObject();
        mes.put("type", 7);
        sendTo(target, PORT, mes.toString());
    }
    
    /**
     * creating socket from given parameters and output stream
     * @param target needed to create socket
     * @param PORT needed to create socket
     * @param message 
     * handle break of connection
     */

    private void sendTo(String target, int PORT, String message) {
        System.out.println("Sending to:" + target + " message=" + message);
        Socket clientSocket = make_connection(target, PORT);
        if (clientSocket != null) {
            try {
                DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                outToServer.writeBytes(message + '\n');
            } catch (IOException e) {
                System.out.println("Can't send message");
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        } else {
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            System.out.println("Sent using task");
                            if (nextHost != null)
                                sendForward(message);
                        }
                    },
                    1000
            );
        }
    }
    /**
     * adding server 
     * @param obj from which is got IP of server
     */

    private void addNewSrv(JSONObject obj) {
        String from = obj.getString("IP_from");
        String ipOfNewSrv = Tools.getIp(from);
        servers.add(ipOfNewSrv);
        if (nextHost == null) {
            nextHost = ipOfNewSrv;
            System.out.println("New direct srv connected. System can work");
        } else {
            sendForward(obj.toString());
            System.out.println("Forwarder request to connect srv");
        }
    }

    private void sendForward(String message) {
        sendTo(nextHost, PORT, message);
    }
}
