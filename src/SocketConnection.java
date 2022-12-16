import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

record SocketConnection(int id, Socket socket, DataInputStream in, DataOutputStream out) {

    private static final MessageProcessor messageProcessor = Server.getMessageProcessor();

    void start() {

        while (true) {
            try {
                read();
            } catch (IOException e) {
                if (e instanceof SocketException)
                    return;
                else {
                    e.printStackTrace();
                }
            }
        }

    }

    void stop() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void read() throws IOException {
        int type = in.read();
        int len = in.readInt();
        byte[] msg = new byte[len];
        in.readNBytes(msg, 0, len);

        messageProcessor.handle(new Message(type, id, msg));
    }

    synchronized void write(Message message) throws IOException {
        byte[] msg = message.msg();

        out.write(message.type());
        out.writeInt(msg.length);
        out.write(message.msg(), 4, msg.length-4);

        out.flush();
    }
}