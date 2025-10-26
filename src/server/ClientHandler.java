package server;

import org.json.JSONArray;
import org.json.JSONObject;
import shared.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


/**
 * Xử lý kết nối từ mỗi client
 */
public class ClientHandler implements Runnable {
    private Socket socket;
    private GameServer server;
    private PrintWriter out;
    private BufferedReader in;
    private User user;
    private String status; // "online", "playing"
    private Room currentRoom;
    private long lastHeartbeat;
    
    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.status = "online";
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                handleMessage(inputLine);
            }
        } catch (IOException e) {
            System.out.println("❌ Client ngắt kết nối");
        } finally {
            handleDisconnect();
        }
    }
    
    private void handleMessage(String message) {
        try {
            JSONObject packet = new JSONObject(message);
            String type = packet.getString("type");
            
            switch (type) {
                case Protocol.REGISTER:
                    handleRegister(packet);
                    break;
                case Protocol.LOGIN:
                    handleLogin(packet);
                    break;
                case Protocol.LOGOUT:
                    handleLogout();
                    break;
                case Protocol.CREATE_ROOM:
                    handleCreateRoom();
                    break;
                case Protocol.JOIN_ROOM:
                    handleJoinRoom(packet);
                    break;
                case Protocol.LEAVE_ROOM:
                    handleLeaveRoom();
                    break;
                case Protocol.INVITE:
                    handleInvite(packet);
                    break;
                case Protocol.INVITE_RESPONSE:
                    handleInviteResponse(packet);
                    break;
                case Protocol.KICK:
                    handleKick(packet);
                    break;
                case Protocol.READY:
                    handleReady(packet);
                    break;
                case Protocol.START_GAME:
                    handleStartGame();
                    break;
                case Protocol.SCORE_UPDATE:
                    handleScoreUpdate(packet);
                    break;
                // FINISH không còn được sử dụng - game chỉ kết thúc bằng TIMEOUT
                case Protocol.MAX_SCORE:
                    handleMaxScore(packet);
                    break;
                case Protocol.TIMEOUT:
                    handleTimeout(packet);
                    break;
                case Protocol.CHAT:
                    handleChat(packet);
                    break;
                case Protocol.GET_LEADERBOARD:
                    handleGetLeaderboard();
                    break;
                case Protocol.GET_HISTORY:
                    handleGetHistory();
                    break;
                case Protocol.GET_PROFILE:
                    handleGetProfile();
                    break;
                case Protocol.UPDATE_PROFILE:
                    handleUpdateProfile(packet);
                    break;
                case Protocol.HEARTBEAT:
                    handleHeartbeat();
                    break;
                case Protocol.GET_ONLINE_USERS:
                    handleGetOnlineUsers();
                    break;
                default:
                    if (isConnected()) {
                        sendError(Protocol.ERR_INVALID_PACKET, "Unknown packet type");
                    }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Chỉ gửi error nếu client còn kết nối
            if (isConnected()) {
                sendError(Protocol.ERR_INVALID_PACKET, "Invalid packet format");
            }
        }
    }
    
    // ==================== AUTHENTICATION ====================
    
    private void handleRegister(JSONObject packet) {
        String username = packet.getString("username");
        String hashedPassword = packet.getString("password"); // BUG FIX #2: Client đã hash rồi
        String email = packet.optString("email", "");
        
        // BUG FIX #24: Server-side validation để chống bypass client validation
        // Validate username
        if (username == null || username.trim().isEmpty()) {
            sendError(Protocol.ERR_INVALID_PACKET, "Username không được để trống");
            return;
        }
        username = username.trim(); // Remove whitespace
        
        if (username.length() < 3 || username.length() > 20) {
            sendError(Protocol.ERR_INVALID_PACKET, "Username phải từ 3-20 ký tự");
            return;
        }
        
        // Validate username chỉ chứa alphanumeric và underscore
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            sendError(Protocol.ERR_INVALID_PACKET, "Username chỉ được chứa chữ, số và dấu gạch dưới");
            return;
        }
        
        // Validate password (đã hash, nên check length của hash)
        if (hashedPassword == null || hashedPassword.trim().isEmpty()) {
            sendError(Protocol.ERR_INVALID_PACKET, "Password không được để trống");
            return;
        }
        
        // SHA-256 hash luôn có độ dài 64 ký tự (hex)
        if (hashedPassword.length() != 64) {
            sendError(Protocol.ERR_INVALID_PACKET, "Invalid password format");
            return;
        }
        
        // BUG FIX #2: Không hash lần 2! Password từ client đã được hash bằng SHA-256
        boolean success = server.getDbManager().registerUser(username, hashedPassword, email);
        
        JSONObject response = new JSONObject();
        response.put("type", Protocol.REGISTER_RESPONSE);
        if (success) {
            response.put("status", "success");
            response.put("message", "Đăng ký thành công! Vui lòng đăng nhập.");
        } else {
            response.put("status", "error");
            response.put("error_code", Protocol.ERR_USERNAME_EXISTS);
            response.put("message", "Username đã được sử dụng");
        }
        sendMessage(response.toString());
    }
    
    private void handleLogin(JSONObject packet) {
        String username = packet.getString("username");
        String hashedPassword = packet.getString("password"); // BUG FIX #2: Client đã hash rồi
        
        // BUG FIX #24: Server-side validation
        if (username == null || username.trim().isEmpty()) {
            sendError(Protocol.ERR_INVALID_PACKET, "Username không được để trống");
            return;
        }
        username = username.trim();
        
        if (hashedPassword == null || hashedPassword.trim().isEmpty()) {
            sendError(Protocol.ERR_INVALID_PACKET, "Password không được để trống");
            return;
        }
        
        // SHA-256 hash validation
        if (hashedPassword.length() != 64) {
            sendError(Protocol.ERR_INVALID_PACKET, "Invalid password format");
            return;
        }
        
        // BUG FIX #2: Không hash lần 2! Password từ client đã được hash bằng SHA-256
        User user = server.getDbManager().loginUser(username, hashedPassword);
        
        JSONObject response = new JSONObject();
        response.put("type", Protocol.LOGIN_RESPONSE);
        
        if (user != null) {
            // Kiểm tra xem user đã đăng nhập chưa
            if (server.isUserOnline(String.valueOf(user.getUserId()))) {
                // TỪ CHỐI đăng nhập mới
                response.put("status", "error");
                response.put("error_code", Protocol.ERR_ALREADY_LOGGED_IN);
                response.put("message", "Tài khoản này đã đăng nhập ở nơi khác!\nVui lòng đăng xuất hoặc chờ session cũ hết hạn.");
                System.out.println("⚠️ TỪ CHỐI đăng nhập: User " + username + " đã online!");
            } else {
                // Đăng nhập session mới
                this.user = user;
                this.status = "online";
                server.addOnlineClient(String.valueOf(user.getUserId()), this);
                
                response.put("status", "success");
                JSONObject userObj = new JSONObject();
                userObj.put("user_id", user.getUserId());
                userObj.put("username", user.getUsername());
                userObj.put("total_score", user.getTotalScore());
                userObj.put("total_wins", user.getTotalWins());
                userObj.put("total_losses", user.getTotalLosses());
                userObj.put("total_draws", user.getTotalDraws());
                userObj.put("win_rate", user.getWinRate());
                response.put("user", userObj);
                
                System.out.println("✅ User đăng nhập: " + username);
                
                // Gửi danh sách online sau khi đăng nhập thành công
                new Thread(() -> {
                    try {
                        Thread.sleep(100); // Delay 100ms để client sẵn sàng
                        server.broadcastOnlineUsers();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } else {
            response.put("status", "error");
            response.put("error_code", Protocol.ERR_INVALID_CREDENTIALS);
            response.put("message", "Sai username hoặc password");
        }
        sendMessage(response.toString());
    }
    
    private void handleLogout() {
        if (user != null) {
            server.removeOnlineClient(String.valueOf(user.getUserId()));
            System.out.println("👋 User đăng xuất: " + user.getUsername());
        }
    }
    
    // ==================== ROOM MANAGEMENT ====================
    
    private void handleCreateRoom() {
        // BUG FIX #32: Validate authentication
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập trước!");
            return;
        }
        
        // BUG FIX #31: Prevent creating room while already in a room
        if (currentRoom != null) {
            sendError(Protocol.ERR_INVALID_PACKET, "Bạn đã ở trong một phòng rồi! Vui lòng rời phòng trước.");
            return;
        }
        
        Room room = server.createRoom(this);
        currentRoom = room;
        status = "playing";
        
        JSONObject response = new JSONObject();
        response.put("type", Protocol.ROOM_CREATED);
        response.put("room_id", room.getRoomId());
        sendMessage(response.toString());
        
        server.broadcastOnlineUsers();
        System.out.println("🏠 Phòng mới: " + room.getRoomId() + " - Host: " + user.getUsername());
    }
    
    private void handleJoinRoom(JSONObject packet) {
        // BUG FIX #32: Validate authentication
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập trước!");
            return;
        }
        
        String roomId = packet.getString("room_id");
        
        // BUG FIX #31: Prevent joining room while already in a room
        if (currentRoom != null) {
            sendError(Protocol.ERR_INVALID_PACKET, "Bạn đã ở trong một phòng rồi! Vui lòng rời phòng trước.");
            return;
        }
        
        Room room = server.getRoom(roomId);
        
        if (room == null) {
            sendError(Protocol.ERR_ROOM_NOT_FOUND, "Phòng không tồn tại");
            return;
        }
        
        if (room.isFull()) {
            sendError(Protocol.ERR_ROOM_FULL, "Phòng đã đầy");
            return;
        }
        
        if (room.isGameStarted()) {
            sendError(Protocol.ERR_GAME_STARTED, "Trận đấu đã bắt đầu");
            return;
        }
        
        // BUG FIX #29: Prevent self-join (user join own room)
        if (room.isHost(this)) {
            sendError(Protocol.ERR_INVALID_PACKET, "Bạn không thể tham gia phòng của chính mình!");
            return;
        }
        
        // BUG FIX #21: Validate host tồn tại TRƯỚC KHI thêm guest
        // Race condition: Host có thể disconnect NGAY SAU check room != null
        ClientHandler host = room.getHost();
        if (host == null || host.getUser() == null) {
            sendError(Protocol.ERR_ROOM_NOT_FOUND, "Phòng không còn tồn tại (chủ phòng đã rời đi)");
            return;
        }
        
        room.addGuest(this);
        currentRoom = room;
        status = "playing";
        
        // BUG FIX #21: NOW SAFE - host đã được validate
        // Thông báo cho guest
        JSONObject response = new JSONObject();
        response.put("type", Protocol.ROOM_JOINED);
        response.put("room_id", roomId);
        response.put("host_username", host.getUser().getUsername());
        sendMessage(response.toString());
        
        // Thông báo cho host
        JSONObject notification = new JSONObject();
        notification.put("type", Protocol.PLAYER_JOINED);
        notification.put("username", user.getUsername());
        notification.put("user_id", user.getUserId());
        host.sendMessage(notification.toString());
        
        server.broadcastOnlineUsers();
        System.out.println("👥 " + user.getUsername() + " tham gia phòng " + roomId);
    }
    
    private void handleLeaveRoom() {
        // BUG FIX #32: Validate authentication
        if (user == null) return;
        
        if (currentRoom == null) return;
        
        Room room = currentRoom;
        
        // BUG FIX: Không cho phép leave room khi đang chơi
        // Phải dùng TIMEOUT với is_quit = true thay vì LEAVE_ROOM
        if ("playing".equals(room.getStatus())) {
            System.out.println("⚠️ Cannot leave room while playing! Use quit instead.");
            sendError(Protocol.ERR_GAME_STARTED, "Không thể rời phòng khi đang chơi. Vui lòng dùng chức năng thoát game.");
            return;
        }
        
        boolean wasHost = room.isHost(this);
        
        // Thông báo cho người còn lại
        ClientHandler opponent = room.getOpponent(this);
        if (opponent != null) {
            JSONObject notification = new JSONObject();
            notification.put("type", Protocol.PLAYER_LEFT);
            notification.put("username", user.getUsername());
            
            // Nếu host rời đi, thông báo rõ ràng hơn
            if (wasHost) {
                notification.put("message", "Chủ phòng đã rời khỏi phòng. Phòng sẽ bị hủy!");
                notification.put("room_closed", true);
            }
            
            opponent.sendMessage(notification.toString());
            
            // Nếu host rời đi, đóng phòng
            if (wasHost) {
                opponent.currentRoom = null;
                opponent.status = "online";
                System.out.println("🏠 Chủ phòng " + user.getUsername() + " rời phòng. Hủy phòng " + room.getRoomId());
            }
        }
        
        // Xóa người chơi khỏi phòng
        if (wasHost) {
            // Host rời → xóa phòng
            server.removeRoom(room.getRoomId());
        } else {
            // Guest rời → xóa guest khỏi phòng
            room.removeGuest();
            System.out.println("👋 Guest " + user.getUsername() + " rời phòng " + room.getRoomId());
        }
        
        currentRoom = null;
        status = "online";
        server.broadcastOnlineUsers();
    }
    
    /**
     * BUG FIX #9: Thêm validation đầy đủ cho handleInvite
     */
    private void handleInvite(JSONObject packet) {
        // BUG FIX #32: Validate authentication
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập trước!");
            return;
        }
        
        int toUserId = packet.getInt("to_user_id");
        
        // BUG FIX #30: Prevent self-invite
        if (toUserId == user.getUserId()) {
            sendError(Protocol.ERR_INVALID_PACKET, "Bạn không thể mời chính mình!");
            return;
        }
        
        // BUG FIX #9: Validate currentRoom không null
        if (currentRoom == null) {
            sendError(Protocol.ERR_ROOM_NOT_FOUND, "Bạn chưa ở trong phòng nào!");
            return;
        }
        
        // BUG FIX #9: Validate phòng chưa đầy
        if (currentRoom.isFull()) {
            sendError(Protocol.ERR_ROOM_FULL, "Phòng đã đủ người, không thể mời thêm!");
            return;
        }
        
        // BUG FIX #9: Validate game chưa bắt đầu
        if (currentRoom.isGameStarted()) {
            sendError(Protocol.ERR_GAME_STARTED, "Game đã bắt đầu, không thể mời!");
            return;
        }
        
        String roomId = currentRoom.getRoomId();
        
        ClientHandler target = server.getClientHandler(String.valueOf(toUserId));
        if (target == null || target.getUser() == null) {
            sendError(Protocol.ERR_CONNECTION_LOST, "Người chơi không online");
            return;
        }
        
        // Lưu lời mời
        server.addInvitation(String.valueOf(user.getUserId()), String.valueOf(toUserId), roomId);
        
        // Gửi lời mời
        JSONObject invitation = new JSONObject();
        invitation.put("type", Protocol.INVITATION);
        invitation.put("from_user", user.getUsername());
        invitation.put("from_user_id", user.getUserId());
        invitation.put("room_id", roomId);
        invitation.put("expires_in", 30);
        target.sendMessage(invitation.toString());
        
        System.out.println("📨 Lời mời: " + user.getUsername() + " -> " + target.getUser().getUsername());
    }
    
    private void handleInviteResponse(JSONObject packet) {
        // BUG FIX #32: Validate authentication
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập trước!");
            return;
        }
        
        boolean accept = packet.getBoolean("accept");
        String roomId = packet.getString("room_id");
        int fromUserId = packet.getInt("from_user_id");
        
        ClientHandler sender = server.getClientHandler(String.valueOf(fromUserId));
        
        if (accept) {
            // Kiểm tra lời mời còn hợp lệ
            if (!server.isInvitationValid(String.valueOf(fromUserId), String.valueOf(user.getUserId()), roomId)) {
                sendError(Protocol.ERR_TIMEOUT, "Lời mời đã hết hạn");
                return;
            }
            
            server.removeInvitation(String.valueOf(fromUserId), String.valueOf(user.getUserId()), roomId);
            
            // Tham gia phòng
            JSONObject joinPacket = new JSONObject();
            joinPacket.put("room_id", roomId);
            handleJoinRoom(joinPacket);
            
            // Thông báo cho người gửi
            if (sender != null) {
                JSONObject notification = new JSONObject();
                notification.put("type", Protocol.INVITE_ACCEPTED);
                notification.put("username", user.getUsername());
                sender.sendMessage(notification.toString());
            }
        } else {
            server.removeInvitation(String.valueOf(fromUserId), String.valueOf(user.getUserId()), roomId);
            
            // Thông báo từ chối
            if (sender != null) {
                JSONObject notification = new JSONObject();
                notification.put("type", Protocol.INVITE_DECLINED);
                notification.put("username", user.getUsername());
                sender.sendMessage(notification.toString());
            }
        }
    }
    
    private void handleKick(JSONObject packet) {
        // BUG FIX #32: Validate authentication
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập trước!");
            return;
        }
        
        if (currentRoom == null || !currentRoom.isHost(this)) {
            sendError(Protocol.ERR_NOT_HOST, "Bạn không phải chủ phòng");
            return;
        }
        
        ClientHandler guest = currentRoom.getGuest();
        if (guest != null) {
            JSONObject notification = new JSONObject();
            notification.put("type", Protocol.PLAYER_KICKED);
            guest.sendMessage(notification.toString());
            
            guest.currentRoom = null;
            guest.status = "online";
            currentRoom.removePlayer(guest);
        }
    }
    
    // ==================== GAME LOGIC ====================
    
    /**
     * BUG FIX #11: Validate chỉ guest mới được ready
     */
    private void handleReady(JSONObject packet) {
        // BUG FIX #32: Validate authentication
        if (user == null) return;
        
        if (currentRoom == null) return;
        
        // BUG FIX #11: Chỉ guest được phép ready, host không cần ready
        if (currentRoom.isHost(this)) {
            sendError(Protocol.ERR_NOT_HOST, "Chủ phòng không cần ready. Hãy nhấn 'Bắt Đầu' khi guest đã sẵn sàng.");
            return;
        }
        
        boolean ready = packet.getBoolean("ready");
        currentRoom.setGuestReady(ready);
        
        // Broadcast trạng thái ready
        JSONObject notification = new JSONObject();
        notification.put("type", Protocol.PLAYER_READY);
        notification.put("username", user.getUsername());
        notification.put("ready", ready);
        
        if (currentRoom.getHost() != null) {
            currentRoom.getHost().sendMessage(notification.toString());
        }
        if (currentRoom.getGuest() != null) {
            currentRoom.getGuest().sendMessage(notification.toString());
        }
    }
    
    private void handleStartGame() {
        // BUG FIX #32: Validate authentication
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập trước!");
            return;
        }
        
        if (currentRoom == null || !currentRoom.isHost(this)) {
            sendError(Protocol.ERR_NOT_HOST, "Bạn không phải chủ phòng");
            return;
        }
        
        if (!currentRoom.isGuestReady()) {
            sendError(Protocol.ERR_NOT_READY, "Đối thủ chưa sẵn sàng");
            return;
        }
        
        // Sinh dữ liệu hạt (50 gạo + 50 thóc)
        List<Grain> grains = currentRoom.generateGrains(50, 50);
        currentRoom.setStatus("playing");
        currentRoom.setGameStartTime(System.currentTimeMillis());
        
        // Gửi dữ liệu cho cả 2 người
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
        gameStart.put("duration", 120); // 2 phút
        
        // BUG FIX #20: Validate BOTH players exist before accessing
        // Race condition: Player có thể disconnect NGAY TRƯỚC khi game start
        ClientHandler host = currentRoom.getHost();
        ClientHandler guest = currentRoom.getGuest();
        
        if (host == null || guest == null) {
            System.out.println("❌ CRITICAL: One player disconnected during game start!");
            System.out.println("   Host: " + (host != null ? host.getUser().getUsername() : "null"));
            System.out.println("   Guest: " + (guest != null ? guest.getUser().getUsername() : "null"));
            
            // Revert game status (chưa start được)
            currentRoom.setStatus("waiting");
            
            // Thông báo cho người còn lại (nếu có)
            if (host != null && host.isConnected()) {
                sendError(Protocol.ERR_CONNECTION_LOST, "Đối thủ đã mất kết nối trước khi game bắt đầu");
            }
            if (guest != null && guest.isConnected()) {
                guest.sendError(Protocol.ERR_CONNECTION_LOST, "Đối thủ đã mất kết nối trước khi game bắt đầu");
            }
            
            // Cleanup room vì không thể start game
            server.removeRoom(currentRoom.getRoomId());
            if (host != null) {
                host.currentRoom = null;
                host.status = "online";
            }
            if (guest != null) {
                guest.currentRoom = null;
                guest.status = "online";
            }
            
            server.broadcastOnlineUsers();
            return; // Không start game
        }
        
        // BUG FIX #20: Validate User objects are not null (defensive programming)
        if (host.getUser() == null || guest.getUser() == null) {
            System.out.println("❌ CRITICAL: User object is null!");
            sendError(Protocol.ERR_SESSION_EXPIRED, "Lỗi phiên đăng nhập. Vui lòng đăng nhập lại");
            return;
        }
        
        // ✅ NOW SAFE TO ACCESS - Both players exist and have valid User objects
        JSONObject hostGameStart = new JSONObject(gameStart.toString());
        hostGameStart.put("opponent_username", guest.getUser().getUsername());
        host.sendMessage(hostGameStart.toString());
        
        JSONObject guestGameStart = new JSONObject(gameStart.toString());
        guestGameStart.put("opponent_username", host.getUser().getUsername());
        guest.sendMessage(guestGameStart.toString());
        
        System.out.println("🎮 Trận đấu bắt đầu: " + currentRoom.getRoomId());
    }
    
    /**
     * ISSUE #3: Thêm validation cho score để chống hack
     */
    private void handleScoreUpdate(JSONObject packet) {
        // BUG FIX #32: Validate authentication
        if (user == null) return;
        
        if (currentRoom == null) return;
        
        int newScore = packet.getInt("new_score");
        
        // ISSUE #3: Validate score trong range hợp lệ (0-10)
        if (newScore < 0 || newScore > 100) {
            System.out.println("⚠️ HACK ATTEMPT: " + user.getUsername() + " sent invalid score: " + newScore);
            sendError(Protocol.ERR_INVALID_PACKET, "Điểm không hợp lệ!");
            return;
        }
        
        System.out.println("🎯 " + user.getUsername() + " gửi SCORE_UPDATE: " + newScore);
        currentRoom.updateScore(this, newScore);
        
        // Gửi điểm cho đối thủ
        ClientHandler opponent = currentRoom.getOpponent(this);
        if (opponent != null && opponent.getUser() != null) {
            JSONObject scoreUpdate = new JSONObject();
            scoreUpdate.put("type", Protocol.OPPONENT_SCORE);
            scoreUpdate.put("opponent_score", newScore);
            System.out.println("📤 Gửi điểm " + newScore + " của " + user.getUsername() + " cho " + opponent.getUser().getUsername());
            opponent.sendMessage(scoreUpdate.toString());
        }
    }
    
    private void handleMaxScore(JSONObject packet) {
        // BUG FIX #32: Validate authentication
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập trước!");
            return;
        }
        
        if (currentRoom == null) {
            System.out.println("⚠️ Warning: currentRoom is null in handleMaxScore");
            return;
        }
        
        // BUG FIX #1: Kiểm tra nếu đã tính kết quả rồi thì không làm gì nữa (tránh gọi 2 lần)
        if (currentRoom.isResultCalculated()) {
            System.out.println("⚠️ Warning: Result already calculated, ignoring MAX_SCORE packet from " + user.getUsername());
            return;
        }
        
        int finalScore = packet.getInt("final_score");
        
        // ISSUE #3: Validate final score
        if (finalScore < 0 || finalScore > 100) {
            System.out.println("⚠️ HACK ATTEMPT: " + user.getUsername() + " sent invalid final_score: " + finalScore);
            sendError(Protocol.ERR_INVALID_PACKET, "Điểm không hợp lệ!");
            return;
        }
        
        currentRoom.updateScore(this, finalScore);
        currentRoom.setFinished(this);
        
        System.out.println("🎯 " + user.getUsername() + " đạt điểm tối đa: " + finalScore);
        
        // BUG FIX #3: Thông báo cho đối thủ rằng player này đã hoàn thành
        ClientHandler opponent = currentRoom.getOpponent(this);
        if (opponent != null && opponent.isConnected() && opponent.getUser() != null) {
            JSONObject notification = new JSONObject();
            notification.put("type", Protocol.OPPONENT_FINISHED);
            notification.put("opponent_score", finalScore);
            notification.put("message", user.getUsername() + " đã hoàn thành tất cả hạt!");
            System.out.println("📤 Sending OPPONENT_FINISHED to " + opponent.getUser().getUsername());
            opponent.sendMessage(notification.toString());
        }
        
        // Người này đạt max điểm → Tự động kết thúc game
        // calculateGameResult() sẽ dùng trySetResultCalculated() để đảm bảo chỉ gọi 1 lần
        calculateGameResult();
    }
    
    private void handleTimeout(JSONObject packet) {
        // BUG FIX #32: Validate authentication
        if (user == null) return;
        
        System.out.println("📥 Received TIMEOUT packet from " + user.getUsername() + ": " + packet.toString());
        
        if (currentRoom == null) {
            System.out.println("⚠️ Warning: currentRoom is null in handleTimeout");
            return;
        }
        
        // Kiểm tra nếu đã tính kết quả rồi thì không làm gì nữa
        if (currentRoom != null && currentRoom.isResultCalculated()) {
            System.out.println("⚠️ Warning: Result already calculated, ignoring timeout packet");
            return;
        }
        
        // Note: bothFinished() check đã bị xóa vì redundant
        // trySetResultCalculated() trong calculateGameResult() đã đảm bảo chỉ 1 thread tính kết quả
        
        System.out.println("⏳ Player " + user.getUsername() + " timeout. Current status - Host finished: " + 
            currentRoom.isHostFinished() + ", Guest finished: " + currentRoom.isGuestFinished());
        
        int finalScore = packet.getInt("final_score");
        boolean isQuit = packet.optBoolean("is_quit", false); // Kiểm tra có phải thoát không
        
        // ISSUE #3: Validate final score
        if (finalScore < 0 || finalScore > 100) {
            System.out.println("⚠️ HACK ATTEMPT: " + user.getUsername() + " sent invalid timeout score: " + finalScore);
            finalScore = Math.max(0, Math.min(100, finalScore)); // Clamp to valid range
            System.out.println("🔧 Clamped score to: " + finalScore);
        }
        
        currentRoom.updateScore(this, finalScore);
        currentRoom.setFinished(this);
        
        // Đánh dấu người này đã thoát nếu isQuit = true
        if (isQuit) {
            currentRoom.setQuit(this);
            System.out.println("🚪 " + user.getUsername() + " đã thoát khỏi trận đấu");
        }
        
        // Thông báo đối thủ (nếu còn online)
        ClientHandler opponent = currentRoom.getOpponent(this);
        if (opponent != null && opponent.isConnected() && opponent.getUser() != null) {
            JSONObject notification = new JSONObject();
            if (isQuit) {
                // Player thoát game - Thông báo đối thủ
                notification.put("type", Protocol.OPPONENT_LEFT);
                notification.put("message", user.getUsername() + " đã thoát khỏi trận đấu. Bạn đã dành chiến thắng!");
                System.out.println("📤 Sending OPPONENT_LEFT to " + opponent.getUser().getUsername());
                opponent.sendMessage(notification.toString());
            }
            // Không gửi notification nếu chỉ timeout (không phải quit)
        }
        
        // BUG FIX #2: LUÔN gọi calculateGameResult() bên ngoài if
        // Đảm bảo game luôn kết thúc ngay cả khi opponent null hoặc disconnected
        System.out.println("🏁 Calling calculateGameResult() after timeout/quit");
        calculateGameResult();
    }
    
    private synchronized void calculateGameResult() {
        System.out.println("🏆 calculateGameResult() called by " + (user != null ? user.getUsername() : "unknown"));
        
        if (currentRoom == null) {
            System.out.println("⚠️ Warning: currentRoom is null in calculateGameResult");
            return;
        }
        
        // Lưu reference để tránh null pointer
        Room room = currentRoom;
        
        // Atomic check-and-set để đảm bảo chỉ 1 thread được phép tính kết quả
        if (!room.trySetResultCalculated()) {
            System.out.println("⚠️ Warning: Result already being calculated for room " + room.getRoomId() + ", skipping duplicate call");
            return;
        }
        
        System.out.println("✅ This thread will calculate result for room " + room.getRoomId());
        
        ClientHandler host = room.getHost();
        ClientHandler guest = room.getGuest();
        
        // BUG FIX: Nếu host/guest = null (lỗi logic), vẫn phải cleanup để tránh treo
        if (host == null || guest == null) {
            System.out.println("❌ CRITICAL ERROR: host or guest is null in calculateGameResult!");
            System.out.println("❌ This should never happen! Cleaning up room to prevent deadlock.");
            
            // Emergency cleanup
            if (host != null) {
                host.currentRoom = null;
                host.status = "online";
            }
            if (guest != null) {
                guest.currentRoom = null;
                guest.status = "online";
            }
            server.removeRoom(room.getRoomId());
            server.broadcastOnlineUsers();
            
            return; // Không thể tính kết quả, nhưng ít nhất đã cleanup
        }
        
        int hostScore = room.getHostScore();
        int guestScore = room.getGuestScore();
        boolean hostQuit = room.isHostQuit();
        boolean guestQuit = room.isGuestQuit();
        
        System.out.println("🏆 Game result - Host: " + host.getUser().getUsername() + " = " + hostScore + 
                          ", Guest: " + guest.getUser().getUsername() + " = " + guestScore);
        System.out.println("🔍 DEBUG - hostScore: " + hostScore + ", guestScore: " + guestScore + 
                          ", hostQuit: " + hostQuit + ", guestQuit: " + guestQuit);
        
        String hostResult, guestResult;
        String winnerId = null;
        
        // Tính kết quả - Ưu tiên xử lý trường hợp thoát
        if (hostQuit && !guestQuit) {
            // Host thoát -> Host thua, Guest thắng
            hostResult = "lose";
            guestResult = "win";
            winnerId = String.valueOf(guest.getUser().getUserId());
            System.out.println("🚪 Host quit -> Guest wins");
        } else if (guestQuit && !hostQuit) {
            // Guest thoát -> Guest thua, Host thắng
            hostResult = "win";
            guestResult = "lose";
            winnerId = String.valueOf(host.getUser().getUserId());
            System.out.println("🚪 Guest quit -> Host wins");
        } else if (hostQuit && guestQuit) {
            // Cả 2 đều thoát (trường hợp hiếm) -> so sánh điểm
            System.out.println("⚠️ Both players quit, comparing scores");
            if (hostScore > guestScore) {
                hostResult = "win";
                guestResult = "lose";
                winnerId = String.valueOf(host.getUser().getUserId());
            } else if (hostScore < guestScore) {
                hostResult = "lose";
                guestResult = "win";
                winnerId = String.valueOf(guest.getUser().getUserId());
            } else {
                hostResult = "draw";
                guestResult = "draw";
            }
        } else {
            // Không ai thoát - Tính kết quả dựa trên điểm số
            if (hostScore > guestScore) {
                hostResult = "win";
                guestResult = "lose";
                winnerId = String.valueOf(host.getUser().getUserId());
            } else if (hostScore < guestScore) {
                hostResult = "lose";
                guestResult = "win";
                winnerId = String.valueOf(guest.getUser().getUserId());
            } else {
                hostResult = "draw";
                guestResult = "draw";
            }
        }
        
        // Tính thời gian (BUG FIX #5: Dùng room thay vì currentRoom để tránh NPE)
        int duration = (int) ((System.currentTimeMillis() - room.getGameStartTime()) / 1000);
        
        // Lưu vào database (BUG FIX #3: Truyền thêm player names để tránh N+1 query)
        server.getDbManager().saveMatch(
            String.valueOf(host.getUser().getUserId()),
            String.valueOf(guest.getUser().getUserId()),
            hostScore, guestScore, winnerId, duration,
            host.getUser().getUsername(),  // ✅ Player 1 name
            guest.getUser().getUsername()  // ✅ Player 2 name
        );
        
        // Cập nhật điểm
        server.getDbManager().updateUserScore(String.valueOf(host.getUser().getUserId()), hostScore, hostResult);
        server.getDbManager().updateUserScore(String.valueOf(guest.getUser().getUserId()), guestScore, guestResult);
        
        // Gửi kết quả
        // Gửi kết quả cho cả 2 player (nếu còn kết nối)
        if (host.isConnected()) {
            System.out.println("📤 Sending GAME_END to host: " + host.getUser().getUsername() + " - " + hostResult);
            sendGameEnd(host, hostResult, hostScore, guestScore);
        } else {
            System.out.println("⚠️ Host not connected, skipping GAME_END");
        }
        if (guest.isConnected()) {
            System.out.println("📤 Sending GAME_END to guest: " + guest.getUser().getUsername() + " - " + guestResult);
            sendGameEnd(guest, guestResult, guestScore, hostScore);
        } else {
            System.out.println("⚠️ Guest not connected, skipping GAME_END");
        }
        
        // Dọn dẹp
        String roomId = room.getRoomId(); // Lưu roomId trước khi set null
        host.currentRoom = null;
        guest.currentRoom = null;
        host.status = "online";
        guest.status = "online";
        server.removeRoom(roomId);
        server.broadcastOnlineUsers();
        
        System.out.println("🏆 Trận đấu kết thúc: " + hostScore + " - " + guestScore);
    }
    
    private void sendGameEnd(ClientHandler player, String result, int myScore, int opponentScore) {
        // BUG FIX #25: Defensive null check
        if (player == null || player.getUser() == null) {
            System.out.println("❌ Cannot send GAME_END: player or user is null");
            return;
        }
        
        System.out.println("🔍 sendGameEnd DEBUG - Player: " + player.getUser().getUsername() + 
                          ", myScore: " + myScore + ", opponentScore: " + opponentScore);
        
        JSONObject gameEnd = new JSONObject();
        gameEnd.put("type", Protocol.GAME_END);
        gameEnd.put("result", result);
        gameEnd.put("my_score", myScore);
        gameEnd.put("opponent_score", opponentScore);
        
        // Reload user data
        User updatedUser = server.getDbManager().getUserById(String.valueOf(player.getUser().getUserId()));
        if (updatedUser != null) {
            gameEnd.put("new_total_score", updatedUser.getTotalScore());
        } else {
            // Fallback nếu không load được user
            gameEnd.put("new_total_score", player.getUser().getTotalScore() + myScore);
        }
        
        System.out.println("📤 Sending GAME_END to " + player.getUser().getUsername() + ": " + gameEnd.toString());
        player.sendMessage(gameEnd.toString());
    }
    
    // ==================== CHAT ====================
    
    /**
     * BUG FIX #28: Thêm validation cho chat message
     */
    private void handleChat(JSONObject packet) {
        // BUG FIX #32: Validate authentication
        if (user == null) return;
        
        if (currentRoom == null) return;
        
        String message = packet.getString("message");
        
        // BUG FIX #28: Validate chat message
        if (message == null || message.trim().isEmpty()) {
            return; // Ignore empty messages
        }
        
        // Limit message length (prevent DoS)
        if (message.length() > 500) {
            message = message.substring(0, 500); // Truncate
            System.out.println("⚠️ Chat message truncated from " + user.getUsername());
        }
        
        JSONObject chatMsg = new JSONObject();
        chatMsg.put("type", Protocol.CHAT_MESSAGE);
        chatMsg.put("from", user.getUsername());
        chatMsg.put("message", message.trim());
        chatMsg.put("timestamp", System.currentTimeMillis());
        
        // Broadcast trong phòng
        if (currentRoom.getHost() != null) {
            currentRoom.getHost().sendMessage(chatMsg.toString());
        }
        if (currentRoom.getGuest() != null) {
            currentRoom.getGuest().sendMessage(chatMsg.toString());
        }
    }
    
    // ==================== LEADERBOARD & HISTORY ====================
    
    private void handleGetLeaderboard() {
        List<User> leaderboard = server.getDbManager().getLeaderboard(100);
        
        JSONObject response = new JSONObject();
        response.put("type", Protocol.LEADERBOARD_DATA);
        
        JSONArray rankings = new JSONArray();
        for (User u : leaderboard) {
            JSONObject userObj = new JSONObject();
            userObj.put("username", u.getUsername());
            userObj.put("total_score", u.getTotalScore());
            userObj.put("total_wins", u.getTotalWins());
            userObj.put("win_rate", u.getWinRate());
            rankings.put(userObj);
        }
        response.put("rankings", rankings);
        
        sendMessage(response.toString());
    }
    
    private void handleGetHistory() {
        // BUG FIX #32: Validate authentication
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập trước!");
            return;
        }
        
        List<Match> history = server.getDbManager().getUserMatchHistory(
            String.valueOf(user.getUserId()), 50
        );
        
        JSONObject response = new JSONObject();
        response.put("type", Protocol.HISTORY_DATA);
        
        JSONArray matches = new JSONArray();
        for (Match match : history) {
            JSONObject matchObj = new JSONObject();
            matchObj.put("player1_name", match.getPlayer1Name());
            matchObj.put("player2_name", match.getPlayer2Name());
            matchObj.put("player1_score", match.getPlayer1Score());
            matchObj.put("player2_score", match.getPlayer2Score());
            matchObj.put("created_at", match.getCreatedAt().getTime());
            matches.put(matchObj);
        }
        response.put("matches", matches);
        
        sendMessage(response.toString());
    }
    
    // ==================== PROFILE MANAGEMENT ====================
    
    private void handleGetProfile() {
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập lại");
            return;
        }
        
        // Lấy thông tin user mới nhất từ database
        User updatedUser = server.getDbManager().getUserById(String.valueOf(user.getUserId()));
        
        if (updatedUser == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Không tìm thấy thông tin user");
            return;
        }
        
        JSONObject response = new JSONObject();
        response.put("type", Protocol.PROFILE_DATA);
        response.put("username", updatedUser.getUsername());
        response.put("total_score", updatedUser.getTotalScore());
        response.put("total_wins", updatedUser.getTotalWins());
        response.put("total_losses", updatedUser.getTotalLosses());
        response.put("total_draws", updatedUser.getTotalDraws());
        response.put("win_rate", updatedUser.getWinRate());
        response.put("total_matches", updatedUser.getTotalMatches());
        
        System.out.println("📤 Sending profile data to " + updatedUser.getUsername());
        sendMessage(response.toString());
    }
    
    private void handleUpdateProfile(JSONObject packet) {
        if (user == null) {
            sendError(Protocol.ERR_SESSION_EXPIRED, "Vui lòng đăng nhập lại");
            return;
        }
        
        // Chỉ xử lý đổi mật khẩu - YÊU CẦU MẬT KHẨU CŨ
        String oldPassword = packet.optString("old_password", "");
        String newPassword = packet.optString("new_password", "");
        
        // Validation
        if (oldPassword.isEmpty() || newPassword.isEmpty()) {
            sendError(Protocol.ERR_INVALID_PACKET, "Mật khẩu không được để trống");
            return;
        }
        
        if (newPassword.length() < 6) {
            sendError(Protocol.ERR_INVALID_PACKET, "Mật khẩu mới phải có ít nhất 6 ký tự");
            return;
        }
        
        // Passwords đã được hash ở client rồi
        boolean success = server.getDbManager().changePassword(
            String.valueOf(user.getUserId()),
            oldPassword,  // Old hashed password
            newPassword   // New hashed password
        );
        
        if (success) {
            JSONObject response = new JSONObject();
            response.put("type", Protocol.UPDATE_SUCCESS);
            response.put("message", "Đổi mật khẩu thành công!");
            System.out.println("✅ Password changed for user: " + user.getUsername());
            sendMessage(response.toString());
        } else {
            sendError(Protocol.ERR_INVALID_CREDENTIALS, "Mật khẩu cũ không đúng");
        }
    }
    
    // ==================== HELPERS ====================
    
    private void handleHeartbeat() {
        lastHeartbeat = System.currentTimeMillis();
    }
    
    private void handleGetOnlineUsers() {
        // Gửi danh sách người chơi online hiện tại cho client này
        JSONObject response = new JSONObject();
        response.put("type", Protocol.ONLINE_USERS_UPDATE);
        
        List<JSONObject> usersList = new ArrayList<>();
        for (Map.Entry<String, ClientHandler> entry : server.getOnlineClients().entrySet()) {
            ClientHandler handler = entry.getValue();
            User user = handler.getUser();
            if (user != null) {
                // Reload user data từ database để có điểm mới nhất
                User updatedUser = server.getDbManager().getUserById(String.valueOf(user.getUserId()));
                int currentScore = (updatedUser != null) ? updatedUser.getTotalScore() : user.getTotalScore();
                
                JSONObject userObj = new JSONObject();
                userObj.put("user_id", user.getUserId());
                userObj.put("username", user.getUsername());
                userObj.put("total_score", currentScore);
                userObj.put("status", handler.getStatus());
                usersList.add(userObj);
            }
        }
        response.put("users", usersList);
        
        sendMessage(response.toString());
    }
    
    public void handleDisconnect() {
        if (user != null) {
            // BUG FIX #4: Refactor để sử dụng calculateGameResult() thống nhất
            if (currentRoom != null && "playing".equals(currentRoom.getStatus())) {
                System.out.println("🔌 Player " + user.getUsername() + " disconnected during game");
                
                // Kiểm tra nếu đã tính kết quả rồi thì không làm gì nữa
                if (!currentRoom.isResultCalculated()) {
                    // Lấy điểm hiện tại trước khi người này disconnect
                    int currentScore = currentRoom.getPlayerScore(this);
                    
                    // Cập nhật điểm cuối cùng và đánh dấu finished
                    currentRoom.updateScore(this, currentScore);
                    currentRoom.setFinished(this);
                    
                    // Đánh dấu người này đã quit (disconnect = quit)
                    currentRoom.setQuit(this);
                    System.out.println("🚪 Player " + user.getUsername() + " marked as quit due to disconnect (score: " + currentScore + ")");
                    
                    // Thông báo đối thủ (nếu còn online)
                    ClientHandler opponent = currentRoom.getOpponent(this);
                    if (opponent != null && opponent.isConnected() && opponent.getUser() != null) {
                        JSONObject notification = new JSONObject();
                        notification.put("type", Protocol.OPPONENT_LEFT);
                        notification.put("message", user.getUsername() + " đã mất kết nối. Bạn đã dành chiến thắng!");
                        System.out.println("📤 Sending OPPONENT_LEFT to " + opponent.getUser().getUsername() + " (disconnect case)");
                        opponent.sendMessage(notification.toString());
                    }
                    
                    // Gọi calculateGameResult() thống nhất
                    // Logic tính điểm, lưu match, cleanup sẽ được xử lý ở đây
                    System.out.println("🏁 Calling calculateGameResult() after disconnect");
                    calculateGameResult();
                } else {
                    System.out.println("⚠️ Game already ended, skipping calculateGameResult() for disconnect");
                    // Cleanup vẫn cần làm nếu game đã kết thúc
                    currentRoom = null;
                    status = "online";
                }
            } else if (currentRoom != null) {
                // Không đang playing -> xử lý leave room bình thường
                handleLeaveRoom();
            }
            
            server.removeOnlineClient(String.valueOf(user.getUserId()));
        }
        
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    
    private void sendError(int errorCode, String message) {
        JSONObject error = new JSONObject();
        error.put("type", Protocol.ERROR);
        error.put("error_code", errorCode);
        error.put("message", message);
        System.out.println("⚠️ Sending error to " + (user != null ? user.getUsername() : "unknown") + ": " + message);
        sendMessage(error.toString());
    }
    
    // BUG FIX #2: Xóa hashPassword() method - không cần nữa vì client đã hash
    // Password từ client đã được hash bằng SHA-256
    
    public User getUser() {
        return user;
    }
    
    public String getStatus() {
        return status;
    }
    
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && out != null;
    }
}

