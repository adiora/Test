import java.sql.*;

public class DatabaseHandler {

    private final PreparedStatement scheduleMsgStmt;
    private final PreparedStatement pendingMsgStmt;
    private final PreparedStatement deleteMsgStmt;
    private final PreparedStatement userExistsStmt;
    private final PreparedStatement registerUserStmt;

    private int lastUserID;

    DatabaseHandler(Connection con) throws SQLException {

        Statement stmt = con.createStatement();
        lastUserID = stmt.executeQuery("SELECT MAX(id) FROM users").getInt(1);

        scheduleMsgStmt = con.prepareStatement("INSERT INTO pendingMsg VALUES (?, ?, ?, ?)");
        pendingMsgStmt = con.prepareStatement("SELECT * FROM pendingMsg WHERE receiverID = ?");
        deleteMsgStmt = con.prepareStatement("DELETE FROM pendingMsg WHERE receiverID = ?");
        userExistsStmt = con.prepareStatement("SELECT id, pwd FROM users WHERE name = ?");
        registerUserStmt = con.prepareStatement("INSERT INTO users(name, pwd) VALUES (?, ?)");
    }

    void schedule() {
        try {
            scheduleMsgStmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    void schedule(int receiverID, Message message) {
        try {
            scheduleMsgStmt.setInt(1, receiverID);
            scheduleMsgStmt.setInt(2, message.type());
            scheduleMsgStmt.setInt(3, message.senderID());
            scheduleMsgStmt.setBytes(4, message.msg());

            scheduleMsgStmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    void scheduleBatch(int receiverID, Message message) {
        try {
            scheduleMsgStmt.setInt(1, receiverID);
            scheduleMsgStmt.setInt(2, message.type());
            scheduleMsgStmt.setInt(3, message.senderID());
            scheduleMsgStmt.setBytes(4, message.msg());

            scheduleMsgStmt.addBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    void deschedule(int receiver) {
        try {
            pendingMsgStmt.setInt(1, receiver);
            deleteMsgStmt.setInt(1, receiver);
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        try (ResultSet rs = pendingMsgStmt.executeQuery()) {
            if(rs.next()) {
                do {
                    Server.getMessageProcessor().handle(new Message(rs.getInt(2), rs.getInt(3), rs.getBytes(4)));
                } while (rs.next());

                deleteMsgStmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    int getUser(String name, String password) {
        try {
            userExistsStmt.setString(1, name);
            ResultSet rs = userExistsStmt.executeQuery();
            if(rs.next() && rs.getString(2).equals(password))
                return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    int registerUser(String name, String password) {
        try {
            registerUserStmt.setString(1, name);
            registerUserStmt.setString(2, password);
            registerUserStmt.executeUpdate();
            return ++lastUserID;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    int getLastUserID() {
        return lastUserID;
    }

}
