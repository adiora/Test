import java.io.*;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final int PORT = 54739;

    private static Connection databaseCon;

    private static MessageProcessor messageProcessor;

    private static SocketAcceptor socketAcceptor;

    private static Map<Integer, ArrayList<Integer>> groups;

    private static Map<Integer, SocketConnection> users;

    private static boolean running;

    public static void main(String[] args) {
        System.out.println("Hello");

        Server server = new Server();

        if(args[0].equals("1"))
            try(ServerSocket serverSocket = new ServerSocket(54740, 0, InetAddress.getByName("[2405:201:6018:a031:a1dd:b1a4:cc23:18b0]"))) {
            
                server.start();
                serverSocket.accept();
                
                server.stop();
            } catch (IOException e) {
                if(!(e instanceof BindException)) e.printStackTrace();
            }
        else
            try(Socket ignored1 = new Socket(InetAddress.getLoopbackAddress(), 54740)) {
            } catch (IOException ignored) {
            }
            
    }

    public void start() {
        if(!running) {

            DatabaseHandler databaseHandler = null;
            try {
                databaseCon = DriverManager.getConnection("jdbc:sqlite:res/db.oblique");
                Statement statement = databaseCon.createStatement();
                statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT,
                    pwd TEXT
                );
                
                CREATE TABLE IF NOT EXISTS pendingMsg (
                    receiverID INT,
                    type INT,
                    senderID INT,
                    msg BLOB,
                    FOREIGN KEY(receiverID) REFERENCES users(id)
                );""");
                statement.close();
                databaseHandler = new DatabaseHandler(databaseCon);
            } catch (SQLException e) {
                e.printStackTrace();
            }

            HashMap<Integer, Command> commandHashMap = new HashMap<>(2);
            messageProcessor = new MessageProcessor(commandHashMap);

            users = new ConcurrentHashMap<>();
            groups = new ConcurrentHashMap<>();

            MessageSender messageSender = new MessageSender(users, groups, databaseHandler);
            commandHashMap.put(MessageType.DIRECT_MESSAGE, messageSender.getDirectMessageSender());
            commandHashMap.put(MessageType.GROUP_MESSAGE, messageSender.getGroupMessageSender());

            socketAcceptor = new SocketAcceptor(PORT, users, databaseHandler);

            messageProcessor.start();
            socketAcceptor.start();

            running = true;
        }
    }

    public void stop() {
        if(running) {
            socketAcceptor.stop();

            messageProcessor.stop();

            Iterator<SocketConnection> connections = users.values().iterator();
            while (connections.hasNext()) {
                connections.next().stop();
                connections.remove();
            }

            users.clear();
            groups.clear();

            try {
                databaseCon.close();
            } catch (SQLException ignored) {
            }
            running = false;
        }
    }

    static MessageProcessor getMessageProcessor() {
        return messageProcessor;
    }
}