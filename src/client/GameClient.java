package client;

import org.json.JSONObject;
import shared.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Client kết nối tới server
 */
public class GameClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private List<MessageListener> listeners;
    private Thread receiveThread;
    private Thread heartbeatThread;
    private boolean connected;
    
    public GameClient() {
        listeners = new ArrayList<>();
        connected = false;
    }
    
    public boolean connect() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;
            
            // Thread nhận message
            receiveThread = new Thread(this::receiveMessages);
            receiveThread.start();
            
            // Thread heartbeat
            startHeartbeat();
            
            System.out.println("✅ Kết nối server thành công!");
            return true;
        } catch (IOException e) {
            System.err.println("❌ Không thể kết nối server: " + e.getMessage());
            return false;
        }
    }
    
    private void receiveMessages() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                final String message = line;
                // Notify listeners
                for (MessageListener listener : listeners) {
                    listener.onMessageReceived(message);
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("❌ Mất kết nối server");
                disconnect();
            }
        }
    }
    
    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (connected) {
                try {
                    Thread.sleep(5000); // Mỗi 5 giây
                    sendHeartbeat();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
    
    public void sendMessage(String message) {
        if (out != null && connected) {
            out.println(message);
        }
    }
    
    public void sendHeartbeat() {
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.HEARTBEAT);
        packet.put("timestamp", System.currentTimeMillis());
        sendMessage(packet.toString());
    }
    
    public void addMessageListener(MessageListener listener) {
        listeners.add(listener);
    }
    
    public void removeMessageListener(MessageListener listener) {
        listeners.remove(listener);
    }
    
    public void disconnect() {
        connected = false;
        try {
            if (socket != null) socket.close();
            if (receiveThread != null) receiveThread.interrupt();
            if (heartbeatThread != null) heartbeatThread.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public interface MessageListener {
        void onMessageReceived(String message);
    }
}

