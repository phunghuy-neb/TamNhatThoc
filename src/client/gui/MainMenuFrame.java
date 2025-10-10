package client.gui;

import client.GameClient;
import org.json.JSONArray;
import org.json.JSONObject;
import shared.Protocol;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Màn hình chính sau khi đăng nhập
 */
public class MainMenuFrame extends JFrame implements GameClient.MessageListener {
    private static MainMenuFrame currentInstance = null; // Track current instance
    private boolean leaderboardDialogOpen = false; // Track leaderboard dialog
    
    private GameClient client;
    private JSONObject currentUser;
    
    private JLabel welcomeLabel;
    private JLabel scoreLabel;
    private JTable onlineUsersTable;
    private DefaultTableModel tableModel;
    private JTextField roomIdField;
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JButton leaderboardButton;
    private JButton historyButton;
    private JButton logoutButton;
    
    public MainMenuFrame(GameClient client, JSONObject user) {
        // Đóng instance cũ nếu có
        if (currentInstance != null && currentInstance != this) {
            currentInstance.dispose();
        }
        currentInstance = this;
        
        this.client = client;
        this.currentUser = user;
        
        setTitle("Tấm Nhặt Thóc - Menu Chính");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        client.addMessageListener(this);
        initComponents();
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - User info
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(76, 175, 80));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        
        JPanel userInfoPanel = new JPanel(new GridLayout(2, 1));
        userInfoPanel.setOpaque(false);
        
        welcomeLabel = new JLabel("Chào mừng, " + currentUser.getString("username") + "!");
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 18));
        welcomeLabel.setForeground(Color.WHITE);
        
        scoreLabel = new JLabel("Tổng điểm: " + currentUser.getInt("total_score") + 
                               " | Thắng: " + currentUser.getInt("total_wins") + 
                               " | Thua: " + currentUser.getInt("total_losses"));
        scoreLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        scoreLabel.setForeground(Color.WHITE);
        
        userInfoPanel.add(welcomeLabel);
        userInfoPanel.add(scoreLabel);
        
        topPanel.add(userInfoPanel, BorderLayout.WEST);
        
        logoutButton = new JButton("Đăng Xuất");
        logoutButton.addActionListener(e -> handleLogout());
        topPanel.add(logoutButton, BorderLayout.EAST);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center panel - Online users
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Người chơi Online"));
        
        String[] columnNames = {"Tên", "Điểm", "Trạng thái"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        onlineUsersTable = new JTable(tableModel);
        onlineUsersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersTable.setRowHeight(30);
        
        JScrollPane scrollPane = new JScrollPane(onlineUsersTable);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Button panel for inviting
        JPanel invitePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton inviteButton = new JButton("Gửi lời mời");
        inviteButton.addActionListener(e -> handleInvite());
        invitePanel.add(inviteButton);
        centerPanel.add(invitePanel, BorderLayout.SOUTH);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel - Actions
        JPanel bottomPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        // Room panel
        JPanel roomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        roomPanel.setBorder(BorderFactory.createTitledBorder("Phòng chơi"));
        
        createRoomButton = new JButton("Tạo Phòng");
        createRoomButton.setPreferredSize(new Dimension(120, 35));
        createRoomButton.setBackground(new Color(76, 175, 80));
        createRoomButton.setForeground(Color.WHITE);
        createRoomButton.addActionListener(e -> handleCreateRoom());
        
        roomIdField = new JTextField(15);
        roomIdField.setBorder(BorderFactory.createTitledBorder("Nhập mã phòng"));
        
        joinRoomButton = new JButton("Tham Gia");
        joinRoomButton.setPreferredSize(new Dimension(120, 35));
        joinRoomButton.setBackground(new Color(33, 150, 243));
        joinRoomButton.setForeground(Color.WHITE);
        joinRoomButton.addActionListener(e -> handleJoinRoom());
        
        roomPanel.add(createRoomButton);
        roomPanel.add(roomIdField);
        roomPanel.add(joinRoomButton);
        
        // Info panel
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        leaderboardButton = new JButton("Bảng Xếp Hạng");
        leaderboardButton.setPreferredSize(new Dimension(150, 35));
        leaderboardButton.addActionListener(e -> handleLeaderboard());
        
        historyButton = new JButton("Lịch Sử Đấu");
        historyButton.setPreferredSize(new Dimension(150, 35));
        historyButton.addActionListener(e -> handleHistory());
        
        infoPanel.add(leaderboardButton);
        infoPanel.add(historyButton);
        
        bottomPanel.add(roomPanel);
        bottomPanel.add(infoPanel);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    private void handleCreateRoom() {
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.CREATE_ROOM);
        client.sendMessage(packet.toString());
    }
    
    private void handleJoinRoom() {
        String roomId = roomIdField.getText().trim();
        if (roomId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập mã phòng!", 
                "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.JOIN_ROOM);
        packet.put("room_id", roomId);
        client.sendMessage(packet.toString());
    }
    
    private void handleInvite() {
        int selectedRow = onlineUsersTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người chơi!", 
                "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Cần có phòng trước khi mời
        JOptionPane.showMessageDialog(this, 
            "Vui lòng tạo phòng trước khi gửi lời mời!", 
            "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void handleLeaderboard() {
        if (leaderboardDialogOpen) return; // Tránh mở 2 dialog
        
        leaderboardButton.setEnabled(false);
        leaderboardDialogOpen = true;
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.GET_LEADERBOARD);
        client.sendMessage(packet.toString());
    }
    
    private void handleHistory() {
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.GET_HISTORY);
        client.sendMessage(packet.toString());
    }
    
    private void handleLogout() {
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Bạn có chắc muốn đăng xuất?", 
            "Xác nhận", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            JSONObject packet = new JSONObject();
            packet.put("type", Protocol.LOGOUT);
            client.sendMessage(packet.toString());
            
            client.disconnect();
            dispose();
            System.exit(0);
        }
    }
    
    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                JSONObject response = new JSONObject(message);
                String type = response.getString("type");
                
                switch (type) {
                    case Protocol.ONLINE_USERS_UPDATE:
                        updateOnlineUsers(response);
                        break;
                    case Protocol.ROOM_CREATED:
                        handleRoomCreated(response);
                        break;
                    case Protocol.ROOM_JOINED:
                        handleRoomJoined(response);
                        break;
                    case Protocol.INVITATION:
                        handleInvitation(response);
                        break;
                    case Protocol.LEADERBOARD_DATA:
                        showLeaderboard(response);
                        break;
                    case Protocol.HISTORY_DATA:
                        showHistory(response);
                        break;
                    case Protocol.ERROR:
                        handleError(response);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void updateOnlineUsers(JSONObject response) {
        JSONArray users = response.getJSONArray("users");
        
        tableModel.setRowCount(0);
        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.getJSONObject(i);
            
            // Không hiển thị chính mình
            if (user.getInt("user_id") == currentUser.getInt("user_id")) {
                continue;
            }
            
            String username = user.getString("username");
            int score = user.getInt("total_score");
            String status = user.getString("status");
            
            String statusIcon = status.equals("online") ? "🟢 Online" : 
                              status.equals("playing") ? "🔴 Đang chơi" : "⚫ Offline";
            
            tableModel.addRow(new Object[]{username, score, statusIcon});
        }
    }
    
    private void handleRoomCreated(JSONObject response) {
        String roomId = response.getString("room_id");
        
        dispose();
        RoomFrame roomFrame = new RoomFrame(client, currentUser, roomId, true);
        roomFrame.setVisible(true);
    }
    
    private void handleRoomJoined(JSONObject response) {
        String roomId = response.getString("room_id");
        
        dispose();
        RoomFrame roomFrame = new RoomFrame(client, currentUser, roomId, false);
        roomFrame.setVisible(true);
    }
    
    private void handleInvitation(JSONObject response) {
        String fromUser = response.getString("from_user");
        int fromUserId = response.getInt("from_user_id");
        String roomId = response.getString("room_id");
        
        int result = JOptionPane.showConfirmDialog(this, 
            fromUser + " mời bạn vào phòng " + roomId + "\nChấp nhận?", 
            "Lời mời tham gia", 
            JOptionPane.YES_NO_OPTION);
        
        JSONObject responsePacket = new JSONObject();
        responsePacket.put("type", Protocol.INVITE_RESPONSE);
        responsePacket.put("accept", result == JOptionPane.YES_OPTION);
        responsePacket.put("room_id", roomId);
        responsePacket.put("from_user_id", fromUserId);
        client.sendMessage(responsePacket.toString());
    }
    
    private void showLeaderboard(JSONObject response) {
        // Kiểm tra xem có phải instance hiện tại không
        if (currentInstance != this) {
            System.out.println("⚠️ Ignoring leaderboard response for old instance");
            return;
        }
        
        JSONArray rankings = response.getJSONArray("rankings");
        
        String[] columns = {"#", "Tên", "Điểm", "Thắng", "Tỷ lệ"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        
        for (int i = 0; i < rankings.length(); i++) {
            JSONObject user = rankings.getJSONObject(i);
            model.addRow(new Object[]{
                i + 1,
                user.getString("username"),
                user.getInt("total_score"),
                user.getInt("total_wins"),
                String.format("%.1f%%", user.getDouble("win_rate"))
            });
        }
        
        JTable table = new JTable(model);
        table.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        
        JOptionPane.showMessageDialog(this, scrollPane, 
            "Bảng Xếp Hạng", JOptionPane.INFORMATION_MESSAGE);
        
        // Re-enable button và reset flag sau khi đóng dialog
        leaderboardButton.setEnabled(true);
        leaderboardDialogOpen = false;
    }
    
    private void showHistory(JSONObject response) {
        JSONArray matches = response.getJSONArray("matches");
        
        String[] columns = {"Đối thủ", "Điểm", "Kết quả"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        
        String myUsername = currentUser.getString("username");
        
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.getJSONObject(i);
            String p1Name = match.getString("player1_name");
            String p2Name = match.getString("player2_name");
            int p1Score = match.getInt("player1_score");
            int p2Score = match.getInt("player2_score");
            
            String opponent = p1Name.equals(myUsername) ? p2Name : p1Name;
            int myScore = p1Name.equals(myUsername) ? p1Score : p2Score;
            int oppScore = p1Name.equals(myUsername) ? p2Score : p1Score;
            
            String result = myScore > oppScore ? "🏆 Thắng" : 
                           myScore < oppScore ? "❌ Thua" : "🤝 Hòa";
            
            model.addRow(new Object[]{
                opponent,
                myScore + " - " + oppScore,
                result
            });
        }
        
        JTable table = new JTable(model);
        table.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        
        JOptionPane.showMessageDialog(this, scrollPane, 
            "Lịch Sử Đấu", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void handleError(JSONObject response) {
        String message = response.getString("message");
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
    
    @Override
    public void dispose() {
        // Clear static reference khi dispose
        if (currentInstance == this) {
            currentInstance = null;
        }
        super.dispose();
    }
}

