package server;

import org.json.JSONObject;
import org.json.JSONArray;

import database.DatabaseManager;
import shared.Protocol;
import shared.User;
import shared.Grain;

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
    private Map<String, Long> roomCooldowns; // roomId -> cooldownStartTime
    private Map<String, Integer> roomRequestCounts; // roomId -> currentRequestCount
    private List<ClientHandler> matchmakingQueue; // Danh sách người chơi đang tìm trận
    private DatabaseManager dbManager;
    private boolean running;
    
    public GameServer() {
        onlineClients = new ConcurrentHashMap<>();
        rooms = new ConcurrentHashMap<>();
        invitations = new ConcurrentHashMap<>();
        roomCooldowns = new ConcurrentHashMap<>();
        roomRequestCounts = new ConcurrentHashMap<>();
        matchmakingQueue = new ArrayList<>();
        dbManager = DatabaseManager.getInstance();
        running = true;
        
        // Thread cleanup lời mời hết hạn
        startInvitationCleanup();
        
        // Thread kiểm tra heartbeat
        startHeartbeatChecker();
        
        // Thread xử lý matchmaking
        startMatchmakingProcessor();
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
        broadcastAllUsers();
    }
    
    public synchronized void removeOnlineClient(String userId) {
        onlineClients.remove(userId);
        broadcastAllUsers();
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
    
    public void broadcastAllUsers() {
        List<User> allUsers = getAllUsers();
        
        JSONObject response = new JSONObject();
        response.put("type", Protocol.ONLINE_USERS_UPDATE);
        
        List<JSONObject> usersList = new ArrayList<>();
        for (User dbUser : allUsers) {
            JSONObject userObj = new JSONObject();
            userObj.put("user_id", dbUser.getUserId());
            userObj.put("username", dbUser.getUsername());
            userObj.put("total_score", dbUser.getTotalScore());
            
            // Kiểm tra trạng thái online
            ClientHandler onlineHandler = getClientHandler(String.valueOf(dbUser.getUserId()));
            if (onlineHandler != null) {
                userObj.put("status", onlineHandler.getStatus());
                
                // Thêm thông tin phòng nếu đang trong phòng
                if (("waiting".equals(onlineHandler.getStatus()) || "playing".equals(onlineHandler.getStatus())) && onlineHandler.currentRoom != null) {
                    Room room = onlineHandler.currentRoom;
                    JSONObject roomInfo = new JSONObject();
                    roomInfo.put("room_id", room.getRoomId());
                    roomInfo.put("players_count", room.getGuest() != null ? 2 : 1);
                    roomInfo.put("max_players", 2);
                    roomInfo.put("can_join", !isRoomInCooldown(room.getRoomId()));
                    userObj.put("room_info", roomInfo);
                }
            } else {
                userObj.put("status", "offline");
            }
            
            usersList.add(userObj);
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
        clearRoomCooldown(roomId); // Clear cooldown khi xóa phòng
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
                        if (handler != null && handler.getUser() != null) {
                            System.out.println("⚠️ Client timeout: " + handler.getUser().getUsername());
                            handler.handleDisconnect();
                        } else if (handler != null) {
                            // Handler exists but user is null - still disconnect
                            System.out.println("⚠️ Client timeout: Unknown user (null)");
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
    
    // ==================== ROOM COOLDOWN MANAGEMENT ====================
    
    /**
     * Kiểm tra xem phòng có đang cooldown không
     */
    public boolean isRoomInCooldown(String roomId) {
        Long cooldownStart = roomCooldowns.get(roomId);
        if (cooldownStart == null) return false;
        
        long currentTime = System.currentTimeMillis();
        return (currentTime - cooldownStart) < 30000; // 30 giây
    }
    
    /**
     * Lấy thời gian còn lại của cooldown (giây)
     */
    public long getRoomCooldownRemaining(String roomId) {
        Long cooldownStart = roomCooldowns.get(roomId);
        if (cooldownStart == null) return 0;
        
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - cooldownStart;
        long remaining = 30000 - elapsed; // 30 giây
        
        return Math.max(0, remaining / 1000); // Trả về giây
    }
    
    /**
     * Xử lý request gia nhập phòng (tăng counter, check cooldown)
     */
    public boolean handleRoomJoinRequest(String roomId) {
        // Kiểm tra cooldown
        if (isRoomInCooldown(roomId)) {
            return false; // Phòng đang cooldown
        }
        
        // Tăng counter
        int currentCount = roomRequestCounts.getOrDefault(roomId, 0) + 1;
        roomRequestCounts.put(roomId, currentCount);
        
        // Nếu đạt 5 request → trigger cooldown
        if (currentCount >= 5) {
            roomCooldowns.put(roomId, System.currentTimeMillis());
            roomRequestCounts.put(roomId, 0); // Reset counter
            System.out.println("🔒 Room " + roomId + " triggered cooldown (5 requests)");
        }
        
        return true; // Request được chấp nhận
    }
    
    /**
     * Reset cooldown khi phòng bị xóa
     */
    public void clearRoomCooldown(String roomId) {
        roomCooldowns.remove(roomId);
        roomRequestCounts.remove(roomId);
    }
    
    // ==================== MATCHMAKING ====================
    
    /**
     * Thêm người chơi vào hàng đợi tìm trận
     */
    public synchronized void addToMatchmakingQueue(ClientHandler player) {
        if (!matchmakingQueue.contains(player)) {
            matchmakingQueue.add(player);
            System.out.println("🎯 " + player.getUser().getUsername() + " đã tham gia hàng đợi tìm trận");
        }
    }
    
    /**
     * Xóa người chơi khỏi hàng đợi tìm trận
     */
    public synchronized void removeFromMatchmakingQueue(ClientHandler player) {
        matchmakingQueue.remove(player);
        System.out.println("❌ " + player.getUser().getUsername() + " đã rời khỏi hàng đợi tìm trận");
    }
    
    /**
     * Thread xử lý matchmaking
     */
    private void startMatchmakingProcessor() {
        Thread matchmakingThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(2000); // Kiểm tra mỗi 2 giây
                    
                    synchronized (this) {
                        if (matchmakingQueue.size() >= 2) {
                            // Lấy 2 người chơi đầu tiên
                            ClientHandler player1 = matchmakingQueue.remove(0);
                            ClientHandler player2 = matchmakingQueue.remove(0);
                            
                            // Tạo phòng và ghép cặp
                            Room room = createRoom(player1);
                            room.addGuest(player2);
                            
                            // Cập nhật trạng thái
                            player1.currentRoom = room;
                            player2.currentRoom = room;
                            player1.status = "waiting";
                            player2.status = "waiting";
                            
                            // Tự động start game ngay lập tức
                            startMatchmakingGame(room, player1, player2);
                            
                            System.out.println("🎮 Ghép cặp thành công: " + player1.getUser().getUsername() + " vs " + player2.getUser().getUsername());
                            
                            // Cập nhật danh sách online
                            broadcastAllUsers();
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        matchmakingThread.setDaemon(true);
        matchmakingThread.start();
    }
    
    /**
     * Lấy tất cả người chơi trong database
     */
    public List<User> getAllUsers() {
        return dbManager.getAllUsers();
    }
    
    /**
     * Tự động start game cho matchmaking
     */
    private void startMatchmakingGame(Room room, ClientHandler player1, ClientHandler player2) {
        try {
            // Sinh dữ liệu hạt ngẫu nhiên (50-100 hạt tổng cộng)
            Random rand = new Random();
            int totalGrains = 50 + rand.nextInt(51); // 50-100 hạt
            int riceCount = 25 + rand.nextInt(26);   // 25-50 hạt gạo
            int paddyCount = totalGrains - riceCount; // Phần còn lại là thóc
            
            List<Grain> grains = room.generateGrains(riceCount, paddyCount);
            room.setTotalGrains(totalGrains);
            room.setStatus("playing");
            room.setGameStartTime(System.currentTimeMillis());
            
            // Cập nhật status của cả 2 player thành "playing"
            player1.status = "playing";
            player2.status = "playing";
            
            // Gửi dữ liệu game cho cả 2 người
            JSONObject gameStart = new JSONObject();
            gameStart.put("type", Protocol.GAME_START);
            
            JSONArray grainsArray = new JSONArray();
            for (Grain grain : grains) {
                JSONObject grainObj = new JSONObject();
                grainObj.put("id", grain.getId());
                grainObj.put("type", grain.getType());
                grainObj.put("x", grain.getX());
                grainObj.put("y", grain.getY());
                grainsArray.put(grainObj);
            }
            gameStart.put("grains", grainsArray);
            gameStart.put("duration", 300); // 5 phút
            gameStart.put("total_grains", totalGrains);
            
            JSONObject hostGameStart = new JSONObject(gameStart.toString());
            hostGameStart.put("opponent_username", player2.getUser().getUsername());
            player1.sendMessage(hostGameStart.toString());
            
            JSONObject guestGameStart = new JSONObject(gameStart.toString());
            guestGameStart.put("opponent_username", player1.getUser().getUsername());
            player2.sendMessage(guestGameStart.toString());
            
            System.out.println("🎮 Matchmaking game started: " + room.getRoomId());
            
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Error starting matchmaking game: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        GameServer server = new GameServer();
        server.start();
    }
}

