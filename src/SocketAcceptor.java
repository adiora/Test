import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;

class SocketAcceptor {

    private final int PORT;
    private ServerSocket serverSocket;
    private final Authenticator authenticator;

    SocketAcceptor(int PORT, Map<Integer, SocketConnection> users, DatabaseHandler databaseHandler) {
        this.PORT = PORT;
        this.authenticator = new Authenticator(users, databaseHandler);
    }

    void start() {

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT, 5, InetAddress.getByName("0.0.0.0"));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            Socket socket;
            InputStream in;
            OutputStream out;

            for(;;) {
                try {
                    socket = serverSocket.accept();
                    in = socket.getInputStream();
                    out = socket.getOutputStream();
                } catch (IOException e) {
                    if(e instanceof SocketException) break;
                    else continue;
                }

                authenticator.authenticate(socket,
                        new DataInputStream(new BufferedInputStream(in)),
                        new DataOutputStream((new BufferedOutputStream(out))));
            }

        }, "Message Processor").start();
    }

    void stop() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    private static class Authenticator {

        private final DatabaseHandler databaseHandler;
        private final Map<Integer, SocketConnection> users;

        Authenticator(Map<Integer, SocketConnection> users, DatabaseHandler databaseHandler) {
            this.users = users;
            this.databaseHandler = databaseHandler;
        }
        private void authenticate(Socket socket, DataInputStream in, DataOutputStream out) {
            new Thread(() -> {
                try {
                    out.write(MessageType.HANDSHAKE);
                    out.flush();
                    if (in.read() == MessageType.HANDSHAKE) {

                        for(SocketConnection socketCon = null; socketCon == null; ) {
                            boolean sign_in = in.read() == MessageType.SIGN_IN;
                            String username = in.readUTF(), password = in.readUTF();

                            int userID = sign_in? databaseHandler.getUser(username, password) :
                                    databaseHandler.registerUser(username, password);

                            if(userID == -1){
                                out.write(MessageType.INVALID_CREDENTIALS);
                                out.flush();
                                continue;
                            }
                            if(!sign_in) {
                                out.write(MessageType.REGISTERED_SUCCESSFULLY);
                                out.flush();
                                continue;
                            }
                            socketCon = new SocketConnection(userID, socket, in, out);
                            users.put(userID, socketCon);

                            out.write(MessageType.LOGIN_SUCCESSFULLY);
                            out.flush();

                            databaseHandler.deschedule(userID);

                            socketCon.start();
                        }

                    } else socket.close();
                } catch (IOException e) {
                    if (!socket.isClosed())
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                }

            }, "Connection "+(databaseHandler.getLastUserID())).start();
        }
    }

}