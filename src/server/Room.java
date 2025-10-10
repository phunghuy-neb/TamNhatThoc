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
    
    public boolean isFull() {
        return guest != null;
    }
    
    public boolean isGameStarted() {
        return "playing".equals(status);
    }
    
    public void addGuest(ClientHandler guest) {
        this.guest = guest;
    }
    
    public void removeGuest() {
        this.guest = null;
        this.guestReady = false;
    }
    
    public void removePlayer(ClientHandler player) {
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
    
    public void updateScore(ClientHandler player, int newScore) {
        if (player == host) {
            hostScore = newScore;
            System.out.println("📊 Host " + player.getUser().getUsername() + " score updated to: " + newScore);
        } else if (player == guest) {
            guestScore = newScore;
            System.out.println("📊 Guest " + player.getUser().getUsername() + " score updated to: " + newScore);
        }
    }
    
    public void setFinished(ClientHandler player) {
        if (player == host) {
            hostFinished = true;
        } else if (player == guest) {
            guestFinished = true;
        }
    }
    
    public boolean bothFinished() {
        return hostFinished && guestFinished;
    }
    
    public boolean isHostFinished() {
        return hostFinished;
    }
    
    public boolean isGuestFinished() {
        return guestFinished;
    }
    
    public int getPlayerScore(ClientHandler player) {
        if (player == host) {
            return hostScore;
        } else if (player == guest) {
            return guestScore;
        }
        return 0;
    }
    
    // Getters and Setters
    public String getRoomId() { return roomId; }
    public ClientHandler getHost() { return host; }
    public ClientHandler getGuest() { return guest; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isHostReady() { return hostReady; }
    public void setHostReady(boolean ready) { this.hostReady = ready; }
    public boolean isGuestReady() { return guestReady; }
    public void setGuestReady(boolean ready) { this.guestReady = ready; }
    public long getCreatedAt() { return createdAt; }
    public List<Grain> getGrains() { return grains; }
    public int getHostScore() { return hostScore; }
    public int getGuestScore() { return guestScore; }
    public long getGameStartTime() { return gameStartTime; }
    public void setGameStartTime(long time) { this.gameStartTime = time; }
    
    public synchronized boolean isResultCalculated() { return resultCalculated; }
    public synchronized void setResultCalculated(boolean calculated) { this.resultCalculated = calculated; }
}

