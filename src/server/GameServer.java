package server;

import org.json.JSONObject;

import database.DatabaseManager;
import shared.Protocol;
import shared.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server chính của game
 */
public class GameServer {
    private static final int PORT = 8888;
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> onlineClients; // userId -> ClientHandler
    private Map<String, Room> rooms; // roomId -> Room
    private Map<String, Long> invitations; // invitationKey -> expiryTime
    private DatabaseManager dbManager;
    private boolean running;
    
    public GameServer() {
        onlineClients = new ConcurrentHashMap<>();
        rooms = new ConcurrentHashMap<>();
        invitations = new ConcurrentHashMap<>();
        dbManager = DatabaseManager.getInstance();
        running = true;
        
        // Thread cleanup lời mời hết hạn
        startInvitationCleanup();
        
        // Thread kiểm tra heartbeat
        startHeartbeatChecker();
    }
    
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("🎮 Game Server đã khởi động trên port " + PORT);
            System.out.println("⏳ Đang chờ kết nối từ client...\n");
            
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✅ Client mới kết nối: " + clientSocket.getInetAddress());
                
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Lỗi server: " + e.getMessage());
        }
    }
    
    public synchronized void addOnlineClient(String userId, ClientHandler handler) {
        onlineClients.put(userId, handler);
        broadcastOnlineUsers();
    }
    
    public synchronized void removeOnlineClient(String userId) {
        onlineClients.remove(userId);
        broadcastOnlineUsers();
    }
    
    public ClientHandler getClientHandler(String userId) {
        return onlineClients.get(userId);
    }
    
    public Map<String, ClientHandler> getOnlineClients() {
        return onlineClients;
    }
    
    public boolean isUserOnline(String userId) {
        return onlineClients.containsKey(userId);
    }
    
    public void broadcastOnlineUsers() {
        JSONObject response = new JSONObject();
        response.put("type", Protocol.ONLINE_USERS_UPDATE);
        
        List<JSONObject> usersList = new ArrayList<>();
        for (Map.Entry<String, ClientHandler> entry : onlineClients.entrySet()) {
            ClientHandler handler = entry.getValue();
            User user = handler.getUser();
            if (user != null) {
                JSONObject userObj = new JSONObject();
                userObj.put("user_id", user.getUserId());
                userObj.put("username", user.getUsername());
                userObj.put("total_score", user.getTotalScore());
                userObj.put("status", handler.getStatus());
                usersList.add(userObj);
            }
        }
        response.put("users", usersList);
        
        // Broadcast đến tất cả client
        for (ClientHandler handler : onlineClients.values()) {
            handler.sendMessage(response.toString());
        }
    }
    
    public synchronized Room createRoom(ClientHandler host) {
        String roomId = "ROOM_" + System.currentTimeMillis();
        Room room = new Room(roomId, host);
        rooms.put(roomId, room);
        return room;
    }
    
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }
    
    public synchronized void removeRoom(String roomId) {
        rooms.remove(roomId);
    }
    
    public synchronized void addInvitation(String fromUserId, String toUserId, String roomId) {
        String key = fromUserId + "_" + toUserId + "_" + roomId;
        long expiryTime = System.currentTimeMillis() + 30000; // 30 giây
        invitations.put(key, expiryTime);
    }
    
    public synchronized boolean isInvitationValid(String fromUserId, String toUserId, String roomId) {
        String key = fromUserId + "_" + toUserId + "_" + roomId;
        Long expiryTime = invitations.get(key);
        if (expiryTime != null && System.currentTimeMillis() < expiryTime) {
            return true;
        }
        invitations.remove(key);
        return false;
    }
    
    public synchronized void removeInvitation(String fromUserId, String toUserId, String roomId) {
        String key = fromUserId + "_" + toUserId + "_" + roomId;
        invitations.remove(key);
    }
    
    private void startInvitationCleanup() {
        Thread cleanupThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(5000); // Kiểm tra mỗi 5 giây
                    long now = System.currentTimeMillis();
                    
                    invitations.entrySet().removeIf(entry -> {
                        if (entry.getValue() < now) {
                            // Lời mời hết hạn - gửi thông báo
                            String[] parts = entry.getKey().split("_");
                            if (parts.length >= 3) {
                                String fromUserId = parts[0];
                                ClientHandler sender = onlineClients.get(fromUserId);
                                if (sender != null) {
                                    JSONObject expiredMsg = new JSONObject();
                                    expiredMsg.put("type", Protocol.INVITE_EXPIRED);
                                    expiredMsg.put("to_user", parts[1]);
                                    sender.sendMessage(expiredMsg.toString());
                                }
                            }
                            return true;
                        }
                        return false;
                    });
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    private void startHeartbeatChecker() {
        Thread heartbeatThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(10000); // Kiểm tra mỗi 10 giây
                    long now = System.currentTimeMillis();
                    
                    List<String> disconnected = new ArrayList<>();
                    for (Map.Entry<String, ClientHandler> entry : onlineClients.entrySet()) {
                        if (now - entry.getValue().getLastHeartbeat() > 15000) { // 15 giây
                            disconnected.add(entry.getKey());
                        }
                    }
                    
                    // Xử lý mất kết nối
                    for (String userId : disconnected) {
                        ClientHandler handler = onlineClients.get(userId);
                        if (handler != null) {
                            System.out.println("⚠️ Client timeout: " + handler.getUser().getUsername());
                            handler.handleDisconnect();
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
    
    public DatabaseManager getDbManager() {
        return dbManager;
    }
    
    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            dbManager.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        GameServer server = new GameServer();
        server.start();
    }
}

