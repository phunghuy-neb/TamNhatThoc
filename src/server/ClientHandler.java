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
        String password = packet.getString("password");
        String email = packet.optString("email", "");
        
        String hashedPassword = hashPassword(password);
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
        String password = packet.getString("password");
        
        String hashedPassword = hashPassword(password);
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
        String roomId = packet.getString("room_id");
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
        
        room.addGuest(this);
        currentRoom = room;
        status = "playing";
        
        // Thông báo cho guest
        JSONObject response = new JSONObject();
        response.put("type", Protocol.ROOM_JOINED);
        response.put("room_id", roomId);
        response.put("host_username", room.getHost().getUser().getUsername());
        sendMessage(response.toString());
        
        // Thông báo cho host
        JSONObject notification = new JSONObject();
        notification.put("type", Protocol.PLAYER_JOINED);
        notification.put("username", user.getUsername());
        notification.put("user_id", user.getUserId());
        room.getHost().sendMessage(notification.toString());
        
        server.broadcastOnlineUsers();
        System.out.println("👥 " + user.getUsername() + " tham gia phòng " + roomId);
    }
    
    private void handleLeaveRoom() {
        if (currentRoom == null) return;
        
        Room room = currentRoom;
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
    
    private void handleInvite(JSONObject packet) {
        int toUserId = packet.getInt("to_user_id");
        String roomId = currentRoom != null ? currentRoom.getRoomId() : "";
        
        ClientHandler target = server.getClientHandler(String.valueOf(toUserId));
        if (target == null) {
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
    
    private void handleReady(JSONObject packet) {
        if (currentRoom == null) return;
        
        boolean ready = packet.getBoolean("ready");
        
        if (!currentRoom.isHost(this)) {
            currentRoom.setGuestReady(ready);
        }
        
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
        if (currentRoom == null || !currentRoom.isHost(this)) {
            sendError(Protocol.ERR_NOT_HOST, "Bạn không phải chủ phòng");
            return;
        }
        
        if (!currentRoom.isGuestReady()) {
            sendError(Protocol.ERR_NOT_READY, "Đối thủ chưa sẵn sàng");
            return;
        }
        
        // Sinh dữ liệu hạt (50 gạo + 50 thóc)
        List<Grain> grains = currentRoom.generateGrains(5, 5);
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
        
        // Gửi cho host với tên guest
        if (currentRoom.getHost() != null) {
            JSONObject hostGameStart = new JSONObject(gameStart.toString());
            hostGameStart.put("opponent_username", currentRoom.getGuest().getUser().getUsername());
            currentRoom.getHost().sendMessage(hostGameStart.toString());
        }
        
        // Gửi cho guest với tên host
        if (currentRoom.getGuest() != null) {
            JSONObject guestGameStart = new JSONObject(gameStart.toString());
            guestGameStart.put("opponent_username", currentRoom.getHost().getUser().getUsername());
            currentRoom.getGuest().sendMessage(guestGameStart.toString());
        }
        
        System.out.println("🎮 Trận đấu bắt đầu: " + currentRoom.getRoomId());
    }
    
    private void handleScoreUpdate(JSONObject packet) {
        if (currentRoom == null) return;
        
        int newScore = packet.getInt("new_score");
        System.out.println("🎯 " + user.getUsername() + " gửi SCORE_UPDATE: " + newScore);
        currentRoom.updateScore(this, newScore);
        
        // Gửi điểm cho đối thủ
        ClientHandler opponent = currentRoom.getOpponent(this);
        if (opponent != null) {
            JSONObject scoreUpdate = new JSONObject();
            scoreUpdate.put("type", Protocol.OPPONENT_SCORE);
            scoreUpdate.put("opponent_score", newScore);
            System.out.println("📤 Gửi điểm " + newScore + " của " + user.getUsername() + " cho " + opponent.getUser().getUsername());
            opponent.sendMessage(scoreUpdate.toString());
        }
    }
    
    private void handleMaxScore(JSONObject packet) {
        if (currentRoom == null) {
            System.out.println("⚠️ Warning: currentRoom is null in handleMaxScore");
            return;
        }
        
        int finalScore = packet.getInt("final_score");
        currentRoom.updateScore(this, finalScore);
        currentRoom.setFinished(this);
        
        System.out.println("🎯 " + user.getUsername() + " đạt điểm tối đa: " + finalScore);
        
        // Người này đạt max điểm → Tự động kết thúc game
        calculateGameResult();
    }
    
    private void handleTimeout(JSONObject packet) {
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
        
        // Kiểm tra nếu đã có player đạt max điểm (cả 2 đã finished) thì bỏ qua timeout
        if (currentRoom.bothFinished()) {
            System.out.println("⚠️ Warning: Both players already finished (max score reached), ignoring timeout packet");
            return;
        }
        
        System.out.println("⏳ Player " + user.getUsername() + " timeout. Current status - Host finished: " + 
            currentRoom.isHostFinished() + ", Guest finished: " + currentRoom.isGuestFinished());
        
        int finalScore = packet.getInt("final_score");
        boolean isQuit = packet.optBoolean("is_quit", false); // Kiểm tra có phải thoát không
        
        currentRoom.updateScore(this, finalScore);
        currentRoom.setFinished(this);
        
        // Thông báo đối thủ
        ClientHandler opponent = currentRoom.getOpponent(this);
        if (opponent != null && opponent.isConnected()) {
            JSONObject notification = new JSONObject();
            if (isQuit) {
                // Player thoát game - Đối thủ thắng ngay lập tức
                notification.put("type", Protocol.OPPONENT_LEFT);
                notification.put("message", user.getUsername() + " đã thoát khỏi trận đấu. Bạn đã dành chiến thắng!");
                opponent.sendMessage(notification.toString());
                
                // Tính kết quả ngay lập tức khi có người thoát
                calculateGameResult();
            } else {
                // Player hết thời gian - Tính kết quả ngay lập tức
                calculateGameResult();
            }
        }
    }
    
    private synchronized void calculateGameResult() {
        System.out.println("🏆 calculateGameResult() called by " + (user != null ? user.getUsername() : "unknown"));
        
        if (currentRoom == null) {
            System.out.println("⚠️ Warning: currentRoom is null in calculateGameResult");
            return;
        }
        
        // Lưu reference để tránh null pointer
        Room room = currentRoom;
        
        // Kiểm tra xem đã tính kết quả chưa (tránh tính 2 lần)
        if (room.isResultCalculated()) {
            System.out.println("⚠️ Warning: Result already calculated for room " + room.getRoomId());
            return;
        }
        
        // Đánh dấu đã tính kết quả
        room.setResultCalculated(true);
        
        ClientHandler host = room.getHost();
        ClientHandler guest = room.getGuest();
        
        if (host == null || guest == null) {
            System.out.println("⚠️ Warning: host or guest is null in calculateGameResult");
            return;
        }
        
        int hostScore = room.getHostScore();
        int guestScore = room.getGuestScore();
        
        System.out.println("🏆 Game result - Host: " + host.getUser().getUsername() + " = " + hostScore + 
                          ", Guest: " + guest.getUser().getUsername() + " = " + guestScore);
        System.out.println("🔍 DEBUG - hostScore: " + hostScore + ", guestScore: " + guestScore);
        
        String hostResult, guestResult;
        String winnerId = null;
        
        // Tính kết quả dựa trên điểm số
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
        
        // Tính thời gian
        int duration = (int) ((System.currentTimeMillis() - currentRoom.getGameStartTime()) / 1000);
        
        // Lưu vào database
        server.getDbManager().saveMatch(
            String.valueOf(host.getUser().getUserId()),
            String.valueOf(guest.getUser().getUserId()),
            hostScore, guestScore, winnerId, duration
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
    
    private void handleChat(JSONObject packet) {
        if (currentRoom == null) return;
        
        String message = packet.getString("message");
        
        JSONObject chatMsg = new JSONObject();
        chatMsg.put("type", Protocol.CHAT_MESSAGE);
        chatMsg.put("from", user.getUsername());
        chatMsg.put("message", message);
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
            // Nếu đang trong trận, xử lý thua
            if (currentRoom != null && "playing".equals(currentRoom.getStatus())) {
                ClientHandler opponent = currentRoom.getOpponent(this);
                if (opponent != null && opponent.isConnected()) {
                    JSONObject notification = new JSONObject();
                    notification.put("type", Protocol.OPPONENT_LEFT);
                    notification.put("result", "win");
                    notification.put("message", user.getUsername() + " đã thoát. Bạn thắng!");
                    opponent.sendMessage(notification.toString());
                    
                    // Cập nhật kết quả
                    server.getDbManager().updateUserScore(String.valueOf(opponent.getUser().getUserId()), 
                        currentRoom.getPlayerScore(opponent), "win");
                    server.getDbManager().updateUserScore(String.valueOf(user.getUserId()), 0, "lose");
                    
                    opponent.currentRoom = null;
                    opponent.status = "online";
                }
                
                server.removeRoom(currentRoom.getRoomId());
            } else if (currentRoom != null) {
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
    
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return password;
        }
    }
    
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

