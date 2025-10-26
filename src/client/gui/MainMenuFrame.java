package client.gui;

import client.GameClient;
import org.json.JSONArray;
import org.json.JSONObject;
import shared.Protocol;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.AbstractCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
    private JButton profileButton;
    private JButton logoutButton;
    private JButton findMatchButton;
    private JButton cancelFindMatchButton;
    private boolean isFindingMatch = false;
    
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
        
        // Gửi request lấy tất cả người chơi
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.GET_ALL_USERS);
        client.sendMessage(packet.toString());
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
        logoutButton.setBackground(new Color(244, 67, 54));
        logoutButton.setForeground(Color.BLACK);
        logoutButton.setFocusPainted(false);
        logoutButton.setFont(new Font("Arial", Font.BOLD, 12));
        logoutButton.addActionListener(e -> handleLogout());
        topPanel.add(logoutButton, BorderLayout.EAST);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center panel - All users
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Tất cả người chơi"));
        
        String[] columnNames = {"Tên", "Điểm", "Trạng thái", "Hành động"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // Chỉ cột "Hành động" có thể edit
            }
            
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 3) return JButton.class;
                return String.class;
            }
        };
        
        onlineUsersTable = new JTable(tableModel);
        onlineUsersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersTable.setRowHeight(30);
        
        // Set column widths
        onlineUsersTable.getColumn("Tên").setPreferredWidth(120);
        onlineUsersTable.getColumn("Điểm").setPreferredWidth(80);
        onlineUsersTable.getColumn("Trạng thái").setPreferredWidth(300);
        onlineUsersTable.getColumn("Hành động").setPreferredWidth(100);
        
        // Renderer cho nút
        onlineUsersTable.getColumn("Hành động").setCellRenderer(new ButtonRenderer());
        onlineUsersTable.getColumn("Hành động").setCellEditor(new ButtonEditor());
        
        // Custom renderer cho status column với màu sắc
        onlineUsersTable.getColumn("Trạng thái").setCellRenderer(new StatusCellRenderer());
        
        JScrollPane scrollPane = new JScrollPane(onlineUsersTable);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Removed invite button panel as requested
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel - Actions
        JPanel bottomPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        // Room panel
        JPanel roomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        roomPanel.setBorder(BorderFactory.createTitledBorder("Phòng chơi"));
        
        createRoomButton = new JButton("Tạo Phòng");
        createRoomButton.setPreferredSize(new Dimension(120, 35));
        createRoomButton.setBackground(new Color(76, 175, 80));
        createRoomButton.setForeground(Color.BLACK);
        createRoomButton.setFocusPainted(false);
        createRoomButton.setFont(new Font("Arial", Font.BOLD, 13));
        createRoomButton.addActionListener(e -> handleCreateRoom());
        
        roomIdField = new JTextField(15);
        roomIdField.setBorder(BorderFactory.createTitledBorder("Nhập mã phòng"));
        
        joinRoomButton = new JButton("Tham Gia");
        joinRoomButton.setPreferredSize(new Dimension(120, 35));
        joinRoomButton.setBackground(new Color(33, 150, 243));
        joinRoomButton.setForeground(Color.BLACK);
        joinRoomButton.setFocusPainted(false);
        joinRoomButton.setFont(new Font("Arial", Font.BOLD, 13));
        joinRoomButton.addActionListener(e -> handleJoinRoom());
        
        roomPanel.add(createRoomButton);
        roomPanel.add(roomIdField);
        roomPanel.add(joinRoomButton);
        
        // Info panel
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        findMatchButton = new JButton("Tìm Trận");
        findMatchButton.setPreferredSize(new Dimension(120, 35));
        findMatchButton.setBackground(new Color(76, 175, 80));
        findMatchButton.setForeground(Color.BLACK);
        findMatchButton.setFocusPainted(false);
        findMatchButton.setFont(new Font("Arial", Font.BOLD, 12));
        findMatchButton.addActionListener(e -> handleFindMatch());
        
        cancelFindMatchButton = new JButton("Hủy Tìm Trận");
        cancelFindMatchButton.setPreferredSize(new Dimension(120, 35));
        cancelFindMatchButton.setBackground(new Color(244, 67, 54));
        cancelFindMatchButton.setForeground(Color.WHITE);
        cancelFindMatchButton.setFocusPainted(false);
        cancelFindMatchButton.setFont(new Font("Arial", Font.BOLD, 12));
        cancelFindMatchButton.addActionListener(e -> handleCancelFindMatch());
        cancelFindMatchButton.setVisible(false);
        
        leaderboardButton = new JButton("Bảng Xếp Hạng");
        leaderboardButton.setPreferredSize(new Dimension(150, 35));
        leaderboardButton.setBackground(new Color(255, 193, 7));
        leaderboardButton.setForeground(Color.BLACK);
        leaderboardButton.setFocusPainted(false);
        leaderboardButton.setFont(new Font("Arial", Font.BOLD, 12));
        leaderboardButton.addActionListener(e -> handleLeaderboard());
        
        historyButton = new JButton("Lịch Sử Đấu");
        historyButton.setPreferredSize(new Dimension(150, 35));
        historyButton.setBackground(new Color(255, 152, 0));
        historyButton.setForeground(Color.BLACK);
        historyButton.setFocusPainted(false);
        historyButton.setFont(new Font("Arial", Font.BOLD, 12));
        historyButton.addActionListener(e -> handleHistory());
        
        profileButton = new JButton("Thông Tin Cá Nhân");
        profileButton.setPreferredSize(new Dimension(150, 35));
        profileButton.setBackground(new Color(33, 150, 243));
        profileButton.setForeground(Color.BLACK);
        profileButton.setFocusPainted(false);
        profileButton.setFont(new Font("Arial", Font.BOLD, 12));
        profileButton.addActionListener(e -> handleProfile());
        
        infoPanel.add(findMatchButton);
        infoPanel.add(cancelFindMatchButton);
        infoPanel.add(leaderboardButton);
        infoPanel.add(historyButton);
        infoPanel.add(profileButton);
        
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
    
    private void handleProfile() {
        ProfileFrame profileFrame = new ProfileFrame(client, currentUser);
        profileFrame.setVisible(true);
    }
    
    private void handleFindMatch() {
        if (isFindingMatch) {
            return; // Đã đang tìm trận rồi
        }
        
        isFindingMatch = true;
        findMatchButton.setVisible(false);
        cancelFindMatchButton.setVisible(true);
        
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.FIND_MATCH);
        client.sendMessage(packet.toString());
    }
    
    private void handleCancelFindMatch() {
        if (!isFindingMatch) {
            return; // Không đang tìm trận
        }
        
        isFindingMatch = false;
        findMatchButton.setVisible(true);
        cancelFindMatchButton.setVisible(false);
        
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.CANCEL_FIND_MATCH);
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
                case Protocol.JOIN_REQUEST_NOTIFICATION:
                    handleJoinRequestNotification(response);
                    break;
                case Protocol.JOIN_REQUEST_RESULT:
                    handleJoinRequestResult(response);
                    break;
                case Protocol.MATCH_FOUND:
                    handleMatchFound(response);
                    break;
                case Protocol.GAME_START:
                    handleGameStart(response);
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
            
            // Debug log
            System.out.println("👤 User: " + username + ", Status: " + status + ", Has room_info: " + user.has("room_info"));
            if (user.has("room_info")) {
                JSONObject roomInfo = user.getJSONObject("room_info");
                System.out.println("   Room info: " + roomInfo.toString());
            }
            
            String statusText;
            Color statusColor;
            if ("online".equals(status)) {
                statusText = "🟢 Online";
                statusColor = new Color(76, 175, 80); // Xanh lá
            } else if ("waiting".equals(status)) {
                // Đang trong phòng chờ (có thể join được)
                if (user.has("room_info")) {
                    JSONObject roomInfo = user.getJSONObject("room_info");
                    String roomId = roomInfo.getString("room_id");
                    int playersCount = roomInfo.getInt("players_count");
                    int maxPlayers = roomInfo.getInt("max_players");
                    boolean canJoin = roomInfo.getBoolean("can_join");
                    
                    if (canJoin) {
                        statusText = "🔵 " + playersCount + "/" + maxPlayers + " đang trong phòng - " + roomId;
                        statusColor = new Color(33, 150, 243); // Xanh dương
                    } else {
                        statusText = "🔒 Phòng " + roomId + " (Cooldown)";
                        statusColor = new Color(255, 152, 0); // Cam
                    }
                } else {
                    statusText = "🔍 Đang tìm trận";
                    statusColor = new Color(255, 193, 7); // Vàng
                }
            } else if ("playing".equals(status)) {
                // Đang trong trận đấu
                statusText = "🎮 Đang trong trận";
                statusColor = new Color(156, 39, 176); // Tím
            } else {
                statusText = "⚫ Offline";
                statusColor = new Color(158, 158, 158); // Xám
            }
            
            // Tạo nút hành động
            JButton actionButton = createActionButton(user, status);
            
            tableModel.addRow(new Object[]{username, score, statusText, actionButton});
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
        String message = response.optString("message", "Lỗi không xác định");
        if (message == null || message.trim().isEmpty()) {
            message = "Lỗi không xác định";
        }
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
    
    private void handleJoinRequestNotification(JSONObject response) {
        String requesterUsername = response.getString("requester_username");
        int requesterId = response.getInt("requester_id");
        String roomId = response.getString("room_id");
        
        int choice = JOptionPane.showConfirmDialog(this,
            requesterUsername + " muốn gia nhập phòng " + roomId + ".\nBạn có đồng ý không?",
            "Yêu cầu gia nhập phòng",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        
        // Gửi phản hồi
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.JOIN_REQUEST_RESPONSE);
        packet.put("accept", choice == JOptionPane.YES_OPTION);
        packet.put("room_id", roomId);
        packet.put("requester_id", requesterId);
        client.sendMessage(packet.toString());
    }
    
    private void handleJoinRequestResult(JSONObject response) {
        String status = response.getString("status");
        String message = response.getString("message");
        
        if ("accepted".equals(status)) {
            JOptionPane.showMessageDialog(this, message, "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } else if ("denied".equals(status)) {
            JOptionPane.showMessageDialog(this, message, "Từ chối", JOptionPane.WARNING_MESSAGE);
        } else if ("sent".equals(status)) {
            JOptionPane.showMessageDialog(this, message, "Đã gửi", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private JButton createActionButton(JSONObject user, String status) {
        JButton button = new JButton();
        
        if ("online".equals(status)) {
            // Online nhưng không trong phòng - không có nút hành động
            button.setText("-");
            button.setEnabled(false);
        } else if ("waiting".equals(status) && user.has("room_info")) {
            // Đang trong phòng chờ - có nút xin gia nhập
            JSONObject roomInfo = user.getJSONObject("room_info");
            boolean canJoin = roomInfo.getBoolean("can_join");
            int playersCount = roomInfo.getInt("players_count");
            int maxPlayers = roomInfo.getInt("max_players");
            
            if (canJoin && playersCount < maxPlayers) {
                button.setText("Xin vào");
                button.setEnabled(true);
                button.addActionListener(e -> {
                    String roomId = roomInfo.getString("room_id");
                    System.out.println("🔍 DEBUG: Button clicked for room " + roomId);
                    requestJoinRoom(roomId);
                });
            } else if (playersCount >= maxPlayers) {
                button.setText("Đầy");
                button.setEnabled(false);
            } else {
                button.setText("Cooldown");
                button.setEnabled(false);
            }
        } else if ("waiting".equals(status)) {
            // Đang tìm trận (không có room_info) - không có nút hành động
            button.setText("-");
            button.setEnabled(false);
        } else if ("playing".equals(status)) {
            // Đang trong trận - không có nút hành động
            button.setText("-");
            button.setEnabled(false);
        } else {
            // Các trường hợp khác - không có nút hành động
            button.setText("-");
            button.setEnabled(false);
        }
        
        return button;
    }
    
    private void requestJoinRoom(String roomId) {
        System.out.println("🔍 DEBUG: Client requesting to join room " + roomId);
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.REQUEST_JOIN_ROOM);
        packet.put("room_id", roomId);
        System.out.println("📤 DEBUG: Sending packet: " + packet.toString());
        client.sendMessage(packet.toString());
    }
    
    private void handleMatchFound(JSONObject response) {
        String status = response.getString("status");
        String message = response.getString("message");
        
        if ("searching".equals(status)) {
            JOptionPane.showMessageDialog(this, message, "Tìm trận", JOptionPane.INFORMATION_MESSAGE);
        } else if ("cancelled".equals(status)) {
            isFindingMatch = false;
            findMatchButton.setVisible(true);
            cancelFindMatchButton.setVisible(false);
            JOptionPane.showMessageDialog(this, message, "Hủy tìm trận", JOptionPane.INFORMATION_MESSAGE);
        }
        // Không cần xử lý "found" status vì server sẽ tự động start game
    }
    
    private void handleGameStart(JSONObject response) {
        // Tự động chuyển sang GameplayFrame khi game bắt đầu
        dispose();
        GameplayFrame gameplayFrame = new GameplayFrame(client, currentUser, response, true);
        gameplayFrame.setVisible(true);
    }
    
    // ==================== STATUS CELL RENDERER ====================
    
    class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (value instanceof String) {
                String statusText = (String) value;
                
                // Set màu sắc dựa trên status text
                if (statusText.contains("🟢 Online")) {
                    c.setForeground(new Color(76, 175, 80)); // Xanh lá
                } else if (statusText.contains("🔍 Đang tìm trận")) {
                    c.setForeground(new Color(255, 193, 7)); // Vàng
                } else if (statusText.contains("🔵") && statusText.contains("đang trong phòng")) {
                    c.setForeground(new Color(33, 150, 243)); // Xanh dương
                } else if (statusText.contains("🔒") && statusText.contains("Cooldown")) {
                    c.setForeground(new Color(255, 152, 0)); // Cam
                } else if (statusText.contains("🎮 Đang trong trận")) {
                    c.setForeground(new Color(156, 39, 176)); // Tím
                } else if (statusText.contains("⚫ Offline")) {
                    c.setForeground(new Color(158, 158, 158)); // Xám
                } else {
                    c.setForeground(Color.BLACK); // Mặc định
                }
            }
            
            return c;
        }
    }
    
    // ==================== BUTTON RENDERER & EDITOR ====================
    
    class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }
        
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            if (value instanceof JButton) {
                JButton button = (JButton) value;
                return button;
            }
            return this;
        }
    }
    
    class ButtonEditor extends AbstractCellEditor implements TableCellEditor {
        protected JButton button;
        private String label;
        private boolean isPushed;
        private ActionListener originalActionListener;
        
        public ButtonEditor() {
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> {
                if (originalActionListener != null) {
                    originalActionListener.actionPerformed(e);
                }
                fireEditingStopped();
            });
        }
        
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            if (value instanceof JButton) {
                JButton originalButton = (JButton) value;
                button.setText(originalButton.getText());
                button.setEnabled(originalButton.isEnabled());
                
                // Store the original action listener instead of copying all
                ActionListener[] listeners = originalButton.getActionListeners();
                if (listeners.length > 0) {
                    originalActionListener = listeners[0]; // Take the first one
                } else {
                    originalActionListener = null;
                }
            }
            isPushed = true;
            return button;
        }
        
        public Object getCellEditorValue() {
            isPushed = false;
            return button;
        }
        
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }
    }
    
    @Override
    public void dispose() {
        // BUG FIX #26: Remove listener để tránh memory leak
        client.removeMessageListener(this);
        
        // Clear static reference khi dispose
        if (currentInstance == this) {
            currentInstance = null;
        }
        super.dispose();
    }
}

