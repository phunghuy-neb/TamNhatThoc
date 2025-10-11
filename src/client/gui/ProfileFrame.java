package client.gui;

import client.GameClient;
import org.json.JSONObject;
import shared.Protocol;

import javax.swing.*;
import java.awt.*;
import java.security.MessageDigest;

/**
 * Màn hình xem thông tin cá nhân và đổi mật khẩu
 */
public class ProfileFrame extends JFrame implements GameClient.MessageListener {
    private GameClient client;
    private JSONObject currentUser;
    
    private JLabel usernameLabel;
    private JLabel statsLabel;
    private JPasswordField oldPasswordField;
    private JPasswordField newPasswordField;
    private JPasswordField confirmPasswordField;
    
    private JButton changePasswordButton;
    private JButton closeButton;
    
    public ProfileFrame(GameClient client, JSONObject user) {
        this.client = client;
        this.currentUser = user;
        
        setTitle("Thông tin cá nhân");
        setSize(500, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        client.addMessageListener(this);
        initComponents();
        loadProfile();
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel titleLabel = new JLabel("📋 THÔNG TIN CÁ NHÂN", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(76, 175, 80));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Center panel
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        
        // ===== THÔNG TIN TÀI KHOẢN =====
        JPanel infoPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        infoPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(76, 175, 80), 2),
            "Thông tin tài khoản",
            0, 0, new Font("Arial", Font.BOLD, 16)
        ));
        infoPanel.setMaximumSize(new Dimension(500, 60));
        
        // Username (read-only)
        infoPanel.add(new JLabel("👤 Tên đăng nhập:"));
        usernameLabel = new JLabel(currentUser.optString("username", ""));
        usernameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        infoPanel.add(usernameLabel);
        
        centerPanel.add(infoPanel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // ===== THỐNG KÊ =====
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(255, 152, 0), 2),
            "Thống kê trận đấu",
            0, 0, new Font("Arial", Font.BOLD, 16)
        ));
        statsPanel.setMaximumSize(new Dimension(500, 200));
        
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        updateStats();
        
        statsPanel.add(statsLabel, BorderLayout.CENTER);
        centerPanel.add(statsPanel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // ===== ĐỔI MẬT KHẨU =====
        JPanel passwordPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        passwordPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(244, 67, 54), 2),
            "Đổi mật khẩu",
            0, 0, new Font("Arial", Font.BOLD, 16)
        ));
        passwordPanel.setMaximumSize(new Dimension(500, 160));
        
        // Mật khẩu cũ
        passwordPanel.add(new JLabel("🔒 Mật khẩu cũ:"));
        oldPasswordField = new JPasswordField();
        oldPasswordField.setPreferredSize(new Dimension(150, 25));
        passwordPanel.add(oldPasswordField);
        
        // Mật khẩu mới
        passwordPanel.add(new JLabel("🔑 Mật khẩu mới:"));
        newPasswordField = new JPasswordField();
        newPasswordField.setPreferredSize(new Dimension(150, 25));
        passwordPanel.add(newPasswordField);
        
        // Xác nhận mật khẩu
        passwordPanel.add(new JLabel("✅ Xác nhận mật khẩu:"));
        confirmPasswordField = new JPasswordField();
        confirmPasswordField.setPreferredSize(new Dimension(150, 25));
        passwordPanel.add(confirmPasswordField);
        
        // Nút đổi mật khẩu (nằm ở giữa)
        passwordPanel.add(new JLabel("")); // Ô trống bên trái
        changePasswordButton = new JButton("🔐 Đổi Mật Khẩu");
        changePasswordButton.setBackground(new Color(244, 67, 54));
        changePasswordButton.setForeground(Color.BLACK);
        changePasswordButton.setFocusPainted(false);
        changePasswordButton.setPreferredSize(new Dimension(150, 30));
        changePasswordButton.addActionListener(e -> handleChangePassword());
        passwordPanel.add(changePasswordButton);
        
        centerPanel.add(passwordPanel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        closeButton = new JButton("Đóng");
        closeButton.setPreferredSize(new Dimension(100, 35));
        closeButton.addActionListener(e -> dispose());
        bottomPanel.add(closeButton);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    private void loadProfile() {
        JSONObject request = new JSONObject();
        request.put("type", Protocol.GET_PROFILE);
        client.sendMessage(request.toString());
    }
    
    private void updateStats() {
        int totalScore = currentUser.optInt("total_score", 0);
        int wins = currentUser.optInt("total_wins", 0);
        int losses = currentUser.optInt("total_losses", 0);
        int draws = currentUser.optInt("total_draws", 0);
        int totalMatches = wins + losses + draws;
        double winRate = currentUser.optDouble("win_rate", 0.0);
        
        String statsText = String.format(
            "<html><div style='padding: 10px;'>" +
            "<b>📊 Tổng điểm:</b> %d điểm<br><br>" +
            "<b>🎮 Tổng số trận:</b> %d trận<br><br>" +
            "<b>🏆 Thắng:</b> %d trận | " +
            "<b>❌ Thua:</b> %d trận | " +
            "<b>🤝 Hòa:</b> %d trận<br><br>" +
            "<b>📈 Tỷ lệ thắng:</b> %.1f%%<br>" +
            "</div></html>",
            totalScore, totalMatches, wins, losses, draws, winRate
        );
        
        statsLabel.setText(statsText);
    }
    
    private void handleChangePassword() {
        String oldPassword = new String(oldPasswordField.getPassword());
        String newPassword = new String(newPasswordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());
        
        // Validation
        if (oldPassword.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Vui lòng nhập mật khẩu cũ!", 
                "Lỗi", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (newPassword.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Vui lòng nhập mật khẩu mới!", 
                "Lỗi", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (newPassword.length() < 6) {
            JOptionPane.showMessageDialog(this, 
                "Mật khẩu mới phải có ít nhất 6 ký tự!", 
                "Lỗi", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this, 
                "Mật khẩu xác nhận không khớp!", 
                "Lỗi", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Hash passwords before sending
        String oldHashedPassword = hashPassword(oldPassword);
        String newHashedPassword = hashPassword(newPassword);
        
        // BUG FIX #18: Check null - nếu hash fail thì không gửi
        if (oldHashedPassword == null || newHashedPassword == null) {
            JOptionPane.showMessageDialog(this, 
                "Lỗi hệ thống: Không thể mã hóa mật khẩu!\nVui lòng thử lại sau.", 
                "Lỗi nghiêm trọng", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JSONObject request = new JSONObject();
        request.put("type", Protocol.UPDATE_PROFILE);
        request.put("old_password", oldHashedPassword);
        request.put("new_password", newHashedPassword);
        client.sendMessage(request.toString());
        
        changePasswordButton.setEnabled(false);
        changePasswordButton.setText("Đang xử lý...");
    }
    
    /**
     * Hash password using SHA-256
     * BUG FIX #18: Return null on error thay vì plain password
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            System.err.println("❌ CRITICAL: Cannot hash password! SHA-256 not available!");
            e.printStackTrace();
            // BUG FIX #18: KHÔNG return plain password! Return null để caller handle
            return null;
        }
    }
    
    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                JSONObject response = new JSONObject(message);
                String type = response.getString("type");
                
                switch (type) {
                    case Protocol.PROFILE_DATA:
                        handleProfileData(response);
                        break;
                    case Protocol.UPDATE_SUCCESS:
                        handleUpdateSuccess(response);
                        break;
                    case Protocol.ERROR:
                        handleError(response);
                        break;
                }
            } catch (Exception e) {
                System.out.println("Error parsing message: " + e.getMessage());
            }
        });
    }
    
    private void handleProfileData(JSONObject data) {
        // Cập nhật currentUser với dữ liệu mới nhất
        currentUser.put("username", data.getString("username"));
        currentUser.put("total_score", data.getInt("total_score"));
        currentUser.put("total_wins", data.getInt("total_wins"));
        currentUser.put("total_losses", data.getInt("total_losses"));
        currentUser.put("total_draws", data.getInt("total_draws"));
        currentUser.put("win_rate", data.getDouble("win_rate"));
        
        // Cập nhật UI
        usernameLabel.setText(data.getString("username"));
        updateStats();
    }
    
    private void handleUpdateSuccess(JSONObject response) {
        String message = response.optString("message", "Cập nhật thành công!");
        
        // Re-enable button
        changePasswordButton.setEnabled(true);
        changePasswordButton.setText("🔐 Đổi Mật Khẩu");
        
        // Clear password fields
        oldPasswordField.setText("");
        newPasswordField.setText("");
        confirmPasswordField.setText("");
        
        JOptionPane.showMessageDialog(this, 
            message, 
            "Thành công", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void handleError(JSONObject response) {
        String errorMessage = response.optString("message", "Có lỗi xảy ra!");
        
        // Re-enable button
        changePasswordButton.setEnabled(true);
        changePasswordButton.setText("🔐 Đổi Mật Khẩu");
        
        JOptionPane.showMessageDialog(this, 
            errorMessage, 
            "Lỗi", 
            JOptionPane.ERROR_MESSAGE);
    }
    
    @Override
    public void dispose() {
        client.removeMessageListener(this);
        super.dispose();
    }
}
