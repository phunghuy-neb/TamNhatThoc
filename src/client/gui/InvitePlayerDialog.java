package client.gui;

import client.GameClient;
import org.json.JSONArray;
import org.json.JSONObject;
import shared.Protocol;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog de moi nguoi choi vao phong
 */
public class InvitePlayerDialog extends JDialog implements GameClient.MessageListener {
    private final GameClient client;
    private final String currentUserId;
    
    private JTextField searchField;
    private JTable playerTable;
    private DefaultTableModel tableModel;
    private JButton inviteButton;
    private JButton refreshButton;
    private JButton cancelButton;
    
    private final List<JSONObject> allPlayers;
    private final List<JSONObject> filteredPlayers;
    
    public InvitePlayerDialog(JFrame parent, GameClient client, String currentUserId, String roomId) {
        super(parent, "Moi Nguoi Choi", true);
        this.client = client;
        this.currentUserId = currentUserId;
        this.allPlayers = new ArrayList<>();
        this.filteredPlayers = new ArrayList<>();
        
        setSize(500, 400);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        client.addMessageListener(this);
        initComponents();
        
        // Yeu cau danh sach nguoi choi online
        requestOnlineUsers();
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Top panel - Search
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Tim kiem nguoi choi"));
        
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(300, 30));
        searchField.setFont(new Font("Arial", Font.PLAIN, 14));
        searchField.addActionListener(e -> filterPlayers());
        
        refreshButton = new JButton("Lam moi");
        refreshButton.setPreferredSize(new Dimension(100, 30));
        refreshButton.setBackground(new Color(33, 150, 243));
        refreshButton.setForeground(Color.WHITE);
        refreshButton.addActionListener(e -> requestOnlineUsers());
        
        JPanel searchInputPanel = new JPanel(new BorderLayout(5, 0));
        searchInputPanel.add(new JLabel("Ten nguoi choi:"), BorderLayout.WEST);
        searchInputPanel.add(searchField, BorderLayout.CENTER);
        searchInputPanel.add(refreshButton, BorderLayout.EAST);
        
        searchPanel.add(searchInputPanel, BorderLayout.CENTER);
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        
        // Center panel - Player list
        JPanel listPanel = new JPanel(new BorderLayout(5, 5));
        listPanel.setBorder(BorderFactory.createTitledBorder("Danh sach nguoi choi online"));
        
        // Table model
        String[] columnNames = {"Ten nguoi choi", "Diem so", "Trang thai", "Hanh dong"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // Chi cot "Hanh dong" co the edit
            }
        };
        
        playerTable = new JTable(tableModel);
        playerTable.setRowHeight(35);
        playerTable.setFont(new Font("Arial", Font.PLAIN, 12));
        playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Custom renderer cho cot hanh dong
        playerTable.getColumnModel().getColumn(3).setCellRenderer(new ButtonRenderer());
        playerTable.getColumnModel().getColumn(3).setCellEditor(new ButtonEditor(new JCheckBox()));
        
        JScrollPane scrollPane = new JScrollPane(playerTable);
        scrollPane.setPreferredSize(new Dimension(450, 200));
        
        listPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(listPanel, BorderLayout.CENTER);
        
        // Bottom panel - Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        inviteButton = new JButton("Moi nguoi duoc chon");
        inviteButton.setPreferredSize(new Dimension(150, 35));
        inviteButton.setBackground(new Color(76, 175, 80));
        inviteButton.setForeground(Color.WHITE);
        inviteButton.setEnabled(false);
        inviteButton.addActionListener(e -> inviteSelectedPlayer());
        
        cancelButton = new JButton("Dong");
        cancelButton.setPreferredSize(new Dimension(100, 35));
        cancelButton.setBackground(new Color(158, 158, 158));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(inviteButton);
        buttonPanel.add(cancelButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Selection listener
        playerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                inviteButton.setEnabled(playerTable.getSelectedRow() != -1);
            }
        });
    }
    
    private void requestOnlineUsers() {
        // Gui yeu cau lay danh sach nguoi choi online
        JSONObject packet = new JSONObject();
        packet.put("type", Protocol.GET_ONLINE_USERS);
        client.sendMessage(packet.toString());
        
        searchField.setText("");
        tableModel.setRowCount(0);
        allPlayers.clear();
        filteredPlayers.clear();
    }
    
    private void filterPlayers() {
        String searchText = searchField.getText().toLowerCase().trim();
        filteredPlayers.clear();
        
        for (JSONObject player : allPlayers) {
            String username = player.getString("username").toLowerCase();
            if (searchText.isEmpty() || username.contains(searchText)) {
                filteredPlayers.add(player);
            }
        }
        
        updateTable();
    }
    
    private void updateTable() {
        tableModel.setRowCount(0);
        
        for (JSONObject player : filteredPlayers) {
            String username = player.getString("username");
            int totalScore = player.getInt("total_score");
            String status = player.getString("status");
            
            // Chỉ ẩn người đang chơi (bản thân đã được lọc rồi)
            if (!"playing".equals(status)) {
                String statusText = "online".equals(status) ? "San sang" : "Ban";
                Object[] row = {username, totalScore, statusText, "Moi"};
                tableModel.addRow(row);
            }
        }
    }
    
    private void inviteSelectedPlayer() {
        int selectedRow = playerTable.getSelectedRow();
        if (selectedRow == -1) return;
        
        // Lấy username từ table
        String targetUsername = (String) playerTable.getValueAt(selectedRow, 0);
        
        // DEBUG: In ra để kiểm tra
        System.out.println("DEBUG: Selected row: " + selectedRow);
        System.out.println("DEBUG: Target username from table: " + targetUsername);
        System.out.println("DEBUG: Current user ID: " + currentUserId);
        
        // Tìm player trong filteredPlayers theo username
        JSONObject selectedPlayer = null;
        for (JSONObject player : filteredPlayers) {
            String playerUsername = player.getString("username");
            String playerUserId = String.valueOf(player.getInt("user_id"));
            System.out.println("DEBUG: Checking player - username: " + playerUsername + ", user_id: " + playerUserId);
            
            if (playerUsername.equals(targetUsername)) {
                selectedPlayer = player;
                System.out.println("DEBUG: Found matching player!");
                break;
            }
        }
        
        if (selectedPlayer == null) {
            JOptionPane.showMessageDialog(this,
                "Không tìm thấy người chơi!",
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int targetUserId = selectedPlayer.getInt("user_id");
        String foundUsername = selectedPlayer.getString("username");
        
        System.out.println("DEBUG: Final target - user_id: " + targetUserId + ", username: " + foundUsername);
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Ban co chac muon moi " + foundUsername + " vao phong?",
            "Xac nhan moi",
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            // Gui loi moi
            JSONObject packet = new JSONObject();
            packet.put("type", Protocol.INVITE);
            packet.put("to_user_id", targetUserId);
            client.sendMessage(packet.toString());
            
            JOptionPane.showMessageDialog(this,
                "Da gui loi moi den " + foundUsername + "!",
                "Thanh cong",
                JOptionPane.INFORMATION_MESSAGE);
            
            dispose();
        }
    }
    
    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                JSONObject response = new JSONObject(message);
                String type = response.getString("type");
                
                if (Protocol.ONLINE_USERS_UPDATE.equals(type)) {
                    handleOnlineUsersUpdate(response);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void handleOnlineUsersUpdate(JSONObject response) {
        allPlayers.clear();
        JSONArray usersArray = response.getJSONArray("users");
        
        for (int i = 0; i < usersArray.length(); i++) {
            JSONObject player = usersArray.getJSONObject(i);
            String playerUserId = String.valueOf(player.getInt("user_id"));
            
            // KHÔNG thêm bản thân vào allPlayers
            if (!playerUserId.equals(currentUserId)) {
                allPlayers.add(player);
            }
        }
        
        filterPlayers();
    }
    
    @Override
    public void dispose() {
        client.removeMessageListener(this);
        super.dispose();
    }
    
    // Custom button renderer and editor
    class ButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setText((value == null) ? "" : value.toString());
            return this;
        }
    }
    
    class ButtonEditor extends DefaultCellEditor {
        protected JButton button;
        private String label;
        private boolean isPushed;
        private int row;
        
        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> fireEditingStopped());
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            label = (value == null) ? "" : value.toString();
            button.setText(label);
            isPushed = true;
            this.row = row;
            return button;
        }
        
        @Override
        public Object getCellEditorValue() {
            if (isPushed) {
                // Moi nguoi choi tai row nay
                if (row < filteredPlayers.size()) {
                    JSONObject player = filteredPlayers.get(row);
                    String targetUserId = String.valueOf(player.getInt("user_id"));
                    String targetUsername = player.getString("username");
                    
                    int confirm = JOptionPane.showConfirmDialog(InvitePlayerDialog.this,
                        "Ban co chac muon moi " + targetUsername + " vao phong?",
                        "Xac nhan moi",
                        JOptionPane.YES_NO_OPTION);
                    
                    if (confirm == JOptionPane.YES_OPTION) {
                        JSONObject packet = new JSONObject();
                        packet.put("type", Protocol.INVITE);
                        packet.put("to_user_id", Integer.parseInt(targetUserId));
                        client.sendMessage(packet.toString());
                        
                        JOptionPane.showMessageDialog(InvitePlayerDialog.this,
                            "Da gui loi moi den " + targetUsername + "!",
                            "Thanh cong",
                            JOptionPane.INFORMATION_MESSAGE);
                        
                        dispose();
                    }
                }
            }
            isPushed = false;
            return label;
        }
        
        @Override
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }
        
        @Override
        protected void fireEditingStopped() {
            super.fireEditingStopped();
        }
    }
}
