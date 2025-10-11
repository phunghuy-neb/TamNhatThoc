package server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import shared.Grain;

/**
 * Class quản lý phòng chơi
 */
public class Room {
    private String roomId;
    private ClientHandler host;
    private ClientHandler guest;
    private String status; // "waiting", "playing", "finished"
    private boolean hostReady;
    private boolean guestReady;
    private long createdAt;
    private List<Grain> grains;
    private int hostScore;
    private int guestScore;
    private boolean hostFinished;
    private boolean guestFinished;
    private boolean hostQuit; // Flag để đánh dấu host đã thoát
    private boolean guestQuit; // Flag để đánh dấu guest đã thoát
    private long gameStartTime;
    private boolean resultCalculated; // Flag để đảm bảo chỉ tính kết quả 1 lần
    
    public Room(String roomId, ClientHandler host) {
        this.roomId = roomId;
        this.host = host;
        this.guest = null;
        this.status = "waiting";
        this.hostReady = false;
        this.guestReady = false;
        this.createdAt = System.currentTimeMillis();
        this.hostScore = 0;
        this.guestScore = 0;
        this.hostFinished = false;
        this.guestFinished = false;
        this.hostQuit = false;
        this.guestQuit = false;
        this.resultCalculated = false;
    }
    
    /**
     * Sinh danh sách hạt ngẫu nhiên
     */
    public List<Grain> generateGrains(int riceCount, int paddyCount) {
        grains = new ArrayList<>();
        Random rand = new Random();
        int id = 0;
        
        // Sinh hạt gạo
        for (int i = 0; i < riceCount; i++) {
            int x = 50 + rand.nextInt(600);
            int y = 100 + rand.nextInt(300);
            grains.add(new Grain(id++, "rice", x, y));
        }
        
        // Sinh hạt thóc
        for (int i = 0; i < paddyCount; i++) {
            int x = 50 + rand.nextInt(600);
            int y = 100 + rand.nextInt(300);
            grains.add(new Grain(id++, "paddy", x, y));
        }
        
        // Trộn ngẫu nhiên
        Collections.shuffle(grains);
        
        return grains;
    }
    
    // BUG FIX #23: Synchronized để tránh race condition khi join room
    public synchronized boolean isFull() {
        return guest != null;
    }
    
    public boolean isGameStarted() {
        return "playing".equals(status);
    }
    
    // BUG FIX #23: Synchronized để tránh 2 guest join cùng lúc
    public synchronized void addGuest(ClientHandler guest) {
        this.guest = guest;
    }
    
    // BUG FIX #23: Synchronized để tránh race condition
    public synchronized void removeGuest() {
        this.guest = null;
        this.guestReady = false;
    }
    
    // BUG FIX #23: Synchronized để tránh race condition
    public synchronized void removePlayer(ClientHandler player) {
        if (player == host) {
            host = null;
        } else if (player == guest) {
            guest = null;
            guestReady = false;
        }
    }
    
    public boolean isHost(ClientHandler player) {
        return player == host;
    }
    
    public ClientHandler getOpponent(ClientHandler player) {
        if (player == host) {
            return guest;
        } else if (player == guest) {
            return host;
        }
        return null;
    }
    
    public synchronized void updateScore(ClientHandler player, int newScore) {
        if (player == host) {
            hostScore = newScore;
            String name = (player.getUser() != null) ? player.getUser().getUsername() : "Unknown";
            System.out.println("📊 Host " + name + " score updated to: " + newScore);
        } else if (player == guest) {
            guestScore = newScore;
            String name = (player.getUser() != null) ? player.getUser().getUsername() : "Unknown";
            System.out.println("📊 Guest " + name + " score updated to: " + newScore);
        }
    }
    
    // BUG FIX #5: Thêm synchronized để tránh race condition
    public synchronized void setFinished(ClientHandler player) {
        if (player == host) {
            hostFinished = true;
        } else if (player == guest) {
            guestFinished = true;
        }
    }
    
    // BUG FIX #6: Thêm synchronized để tránh race condition
    public synchronized void setQuit(ClientHandler player) {
        if (player == host) {
            hostQuit = true;
            String name = (player.getUser() != null) ? player.getUser().getUsername() : "Unknown";
            System.out.println("🚪 Host " + name + " marked as quit");
        } else if (player == guest) {
            guestQuit = true;
            String name = (player.getUser() != null) ? player.getUser().getUsername() : "Unknown";
            System.out.println("🚪 Guest " + name + " marked as quit");
        }
    }
    
    // BUG FIX #7: Thêm synchronized để đọc 2 boolean atomic
    public synchronized boolean bothFinished() {
        return hostFinished && guestFinished;
    }
    
    // Getters - synchronized để đảm bảo visibility
    public synchronized boolean isHostFinished() {
        return hostFinished;
    }
    
    public synchronized boolean isGuestFinished() {
        return guestFinished;
    }
    
    public synchronized boolean isHostQuit() {
        return hostQuit;
    }
    
    public synchronized boolean isGuestQuit() {
        return guestQuit;
    }
    
    // BUG FIX (Phase 2 improvement): Synchronized score getters để đảm bảo visibility
    public synchronized int getPlayerScore(ClientHandler player) {
        if (player == host) {
            return hostScore;
        } else if (player == guest) {
            return guestScore;
        }
        return 0;
    }
    
    // Getters and Setters
    public String getRoomId() { return roomId; }
    
    // BUG FIX #23: Synchronized getters để đảm bảo visibility
    public synchronized ClientHandler getHost() { return host; }
    public synchronized ClientHandler getGuest() { return guest; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isHostReady() { return hostReady; }
    public void setHostReady(boolean ready) { this.hostReady = ready; }
    public boolean isGuestReady() { return guestReady; }
    public void setGuestReady(boolean ready) { this.guestReady = ready; }
    public long getCreatedAt() { return createdAt; }
    public List<Grain> getGrains() { return grains; }
    // Synchronized score getters
    public synchronized int getHostScore() { return hostScore; }
    public synchronized int getGuestScore() { return guestScore; }
    public long getGameStartTime() { return gameStartTime; }
    public void setGameStartTime(long time) { this.gameStartTime = time; }
    
    public synchronized boolean isResultCalculated() { return resultCalculated; }
    public synchronized void setResultCalculated(boolean calculated) { this.resultCalculated = calculated; }
    
    /**
     * Atomic check-and-set để đảm bảo chỉ 1 thread được phép tính kết quả
     * @return true nếu set thành công (chưa calculated), false nếu đã calculated
     */
    public synchronized boolean trySetResultCalculated() {
        if (resultCalculated) {
            return false; // Đã được tính rồi
        }
        resultCalculated = true;
        return true; // Set thành công, thread này được phép tính
    }
}

