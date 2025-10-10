package client.gui;

import client.GameClient;
import org.json.JSONObject;
import shared.Protocol;

import javax.swing.*;
import java.awt.*;

/**
 * Màn hình phòng chờ
 */
public class RoomFrame extends JFrame implements GameClient.MessageListener {
    private static RoomFrame currentInstance = null; // Track current instance
    
    private GameClient client;
    private JSONObject currentUser;
    private String roomId;
    private boolean isHost;
    private boolean isReady;
    
    private JLabel roomIdLabel;
    private JLabel playerCountLabel;
    private JLabel hostLabel;
    private JLabel guestLabel;
    private JTextArea chatArea;
    private JTextField chatInput;
    private JButton readyButton;
    private JButton startButton;
    private JButton leaveButton;
    private JButton inviteButton;
    
    public RoomFrame(GameClient client, JSONObject user, String roomId, boolean isHost) {
        // Đóng instance cũ nếu có
        if (currentInstance != null && currentInstance != this) {
            currentInstance.dispose();
        }
        currentInstance = this;
        
        this.client = client;
        this.currentUser = user;
        this.roomId = roomId;
        this.isHost = isHost;
        this.isReady = false;
        
        setTitle("Tấm Nhặt Thóc - Phòng Chờ");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        
        client.addMessageListener(this);
        initComponents();
        
        // Nếu là guest thì đã có 2 người (host + guest)
        if (!isHost && playerCountLabel != null) {
            playerCountLabel.setText("👥 Số người: 2/2");
            playerCountLabel.setForeground(new Color(0, 100, 200));
        }
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleLeaveRoom();
            }
        });
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Top panel - Room info
        JPanel topPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Thông tin phòng"));
        
        roomIdLabel = new JLabel("Mã phòng: " + roomId);
        roomIdLabel.setFont(new Font("Arial", Font.BOLD, 16));
        
        playerCountLabel = new JLabel("👥 Số người: 1/2");
        playerCountLabel.setFont(new Font("Arial", Font.BOLD, 14));
        playerCountLabel.setForeground(new Color(0, 128, 0));
        
        String hostName = isHost ? currentUser.getString("username") + " (Bạn)" : "Đang chờ...";
        hostLabel = new JLabel("Chủ phòng: " + hostName);
        
        guestLabel = new JLabel("Đối thủ: Đang chờ...");
        
        topPanel.add(roomIdLabel);
        topPanel.add(playerCountLabel);
        topPanel.add(hostLabel);
        topPanel.add(guestLabel);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center panel - Chat
        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        chatPanel.setBorder(BorderFactory.createTitledBorder("Chat"));
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        
        JPanel chatInputPanel = new JPanel(new BorderLayout(5, 0));
        chatInput = new JTextField();
        chatInput.addActionListener(e -> handleSendChat());
        
        JButton sendButton = new JButton("Gửi");
        sendButton.addActionListener(e -> handleSendChat());
        
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);
        
        mainPanel.add(chatPanel, BorderLayout.CENTER);
        
        // Bottom panel - Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        if (isHost) {
            startButton = new JButton("Bắt Đầu");
            startButton.setPreferredSize(new Dimension(120, 35));
            startButton.setBackground(new Color(76, 175, 80));
            startButton.setForeground(Color.WHITE);
            startButton.setEnabled(false); // Chờ guest ready
            startButton.addActionListener(e -> handleStartGame());
            buttonPanel.add(startButton);
            
            inviteButton = new JButton("Mời Người Chơi");
            inviteButton.setPreferredSize(new Dimension(140, 35));
            inviteButton.setBackground(new Color(33, 150, 243));
            inviteButton.setForeground(Color.WHITE);
            inviteButton.addActionListener(e -> handleInvite());
            buttonPanel.add(inviteButton);
        } else {
            readyButton = new JButton("Sẵn Sàng");
            readyButton.setPreferredSize(new Dimension(120, 35));
            readyButton.setBackground(new Color(76, 175, 80));
            readyButton.setForeground(Color.WHITE);
            readyButton.addActionListener(e -> handleReady());
            buttonPanel.add(readyButton);
        }
        
        leaveButton = new JButton("Rời Phòng");
        leaveButton.setPreferredSize(new Dimension(120, 35));
        leaveButton.setBackground(new Color(244, 67, 54));
        leaveButton.setForeground(Color.WHITE);
        leaveButton.addActionListener(e -> handleLeaveRoom());
        buttonPanel.add(leaveButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Hiển thị thông tin ban đầu
        if (!isHost) {
            addChatMessage("Hệ thống", "Bạn đã tham gia phòng. Nhấn 'Sẵn Sàng' khi bạn đã sẵn sàng chơi.");
        } else {
            addChatMessage("Hệ thống", "Phòng đã được tạo. Đang chờ đối thủ...");
        }
    }
    
    private void handleReady() {
        isReady = !isReady;
        
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.READY);
        packet.put("ready", isReady);
        client.sendMessage(packet.toString());
        
        if (isReady) {
            readyButton.setText("Hủy Sẵn Sàng");
            readyButton.setBackground(new Color(255, 152, 0));
            guestLabel.setText("Đối thủ: " + currentUser.getString("username") + " (Bạn) ✅ Đã sẵn sàng");
        } else {
            readyButton.setText("Sẵn Sàng");
            readyButton.setBackground(new Color(76, 175, 80));
            guestLabel.setText("Đối thủ: " + currentUser.getString("username") + " (Bạn) ❌ Chưa sẵn sàng");
        }
    }
    
    private void handleStartGame() {
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.START_GAME);
        client.sendMessage(packet.toString());
    }
    
    private void handleInvite() {
        // Hiển thị dialog chọn người chơi
        String currentUserId = String.valueOf(currentUser.getInt("user_id"));
        InvitePlayerDialog dialog = new InvitePlayerDialog(this, client, currentUserId, roomId);
        dialog.setVisible(true);
    }
    
    private void handleSendChat() {
        String message = chatInput.getText().trim();
        if (message.isEmpty()) return;
        
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.CHAT);
        packet.put("message", message);
        client.sendMessage(packet.toString());
        
        chatInput.setText("");
    }
    
    private void handleLeaveRoom() {
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Bạn có chắc muốn rời phòng?", 
            "Xác nhận", 
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            JSONObject packet = new JSONObject();
            packet.put("type", Protocol.LEAVE_ROOM);
            client.sendMessage(packet.toString());
            
            // Remove listener để tránh leak
            client.removeMessageListener(this);
            
            // Quay về main menu
            dispose();
            MainMenuFrame mainMenu = new MainMenuFrame(client, currentUser);
            mainMenu.setVisible(true);
            
            // Yêu cầu cập nhật danh sách online
            JSONObject request = new JSONObject();
            request.put("type", Protocol.GET_ONLINE_USERS);
            client.sendMessage(request.toString());
        }
    }
    
    private void addChatMessage(String from, String message) {
        chatArea.append("[" + from + "] " + message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
    
    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                JSONObject response = new JSONObject(message);
                String type = response.getString("type");
                
                switch (type) {
                    case Protocol.PLAYER_JOINED:
                        handlePlayerJoined(response);
                        break;
                    case Protocol.PLAYER_LEFT:
                        handlePlayerLeft(response);
                        break;
                    case Protocol.PLAYER_READY:
                        handlePlayerReady(response);
                        break;
                    case Protocol.GAME_START:
                        handleGameStart(response);
                        break;
                    case Protocol.CHAT_MESSAGE:
                        handleChatMessage(response);
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
    
    private void handlePlayerJoined(JSONObject response) {
        String username = response.getString("username");
        guestLabel.setText("Đối thủ: " + username + " ❌ Chưa sẵn sàng");
        playerCountLabel.setText("👥 Số người: 2/2");
        playerCountLabel.setForeground(new Color(0, 100, 200)); // Màu xanh dương (đủ người)
        addChatMessage("Hệ thống", username + " đã tham gia phòng!");
        
        if (inviteButton != null) {
            inviteButton.setEnabled(false);
        }
    }
    
    private void handlePlayerLeft(JSONObject response) {
        String username = response.getString("username");
        boolean roomClosed = response.optBoolean("room_closed", false);
        String message = response.optString("message", username + " đã rời phòng!");
        
        guestLabel.setText("Đối thủ: Đang chờ...");
        playerCountLabel.setText("👥 Số người: 1/2");
        playerCountLabel.setForeground(new Color(0, 128, 0)); // Màu xanh lá (thiếu người)
        addChatMessage("Hệ thống", message);
        
        if (isHost && startButton != null) {
            startButton.setEnabled(false);
        }
        
        if (inviteButton != null) {
            inviteButton.setEnabled(true);
        }
        
        // Nếu host rời hoặc phòng bị đóng, quay về menu
        if (!isHost || roomClosed) {
            String title = roomClosed ? "Phòng bị hủy" : "Thông báo";
            String dialogMessage = roomClosed ? 
                "Chủ phòng đã rời khỏi phòng.\nPhòng sẽ bị hủy!" : 
                "Chủ phòng đã rời. Phòng bị đóng!";
            
            JOptionPane.showMessageDialog(this, 
                dialogMessage, 
                title, 
                JOptionPane.INFORMATION_MESSAGE);
            
            // Remove listener để tránh leak
            client.removeMessageListener(this);
            
            dispose();
            MainMenuFrame mainMenu = new MainMenuFrame(client, currentUser);
            mainMenu.setVisible(true);
            
            // Yêu cầu cập nhật danh sách online
            JSONObject request = new JSONObject();
            request.put("type", Protocol.GET_ONLINE_USERS);
            client.sendMessage(request.toString());
        }
    }
    
    private void handlePlayerReady(JSONObject response) {
        String username = response.getString("username");
        boolean ready = response.getBoolean("ready");
        
        if (!username.equals(currentUser.getString("username"))) {
            String status = ready ? "✅ Đã sẵn sàng" : "❌ Chưa sẵn sàng";
            guestLabel.setText("Đối thủ: " + username + " " + status);
            addChatMessage("Hệ thống", username + (ready ? " đã sẵn sàng!" : " hủy sẵn sàng."));
            
            if (isHost && startButton != null) {
                startButton.setEnabled(ready);
            }
        }
    }
    
    private void handleGameStart(JSONObject response) {
        // Remove listener để tránh leak
        client.removeMessageListener(this);
        
        // Chuyển sang màn hình gameplay
        dispose();
        GameplayFrame gameplayFrame = new GameplayFrame(client, currentUser, response, isHost);
        gameplayFrame.setVisible(true);
    }
    
    private void handleChatMessage(JSONObject response) {
        String from = response.getString("from");
        String msg = response.getString("message");
        addChatMessage(from, msg);
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

