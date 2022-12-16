import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

class MessageSender {

    private final Map<Integer, SocketConnection> users;

    private final Map<Integer, ArrayList<Integer>> groups;

    private final DatabaseHandler databaseHandler;

    private final DirectMessageSender directMessageSender = new DirectMessageSender();

    private final GroupMessageSender groupMessageSender  = new GroupMessageSender();

    MessageSender(Map<Integer, SocketConnection> users, Map<Integer, ArrayList<Integer>> groups,
                  DatabaseHandler databaseHandler) {
        this.users = users;
        this.groups = groups;
        this.databaseHandler = databaseHandler;
    }

    private class DirectMessageSender implements Command {

        @Override
        public void execute(Message message) {
            byte[] msg = message.msg();
            int receiverID = ((msg[0] & 0xFF) << 24) +
                    ((msg[1] & 0xFF) << 16) +
                    ((msg[2] & 0xFF) << 8) +
                    ((msg[3] & 0xFF));

            SocketConnection socketCon = users.get(receiverID);

            if (socketCon != null)
                try {
                    socketCon.write(message);
                    return;
                } catch (IOException ignored) {
                }

            databaseHandler.schedule(receiverID, message);
        }
    }

    private class GroupMessageSender implements Command {

        @Override
        public void execute(Message message) {
            byte[] msg = message.msg();

            ArrayList<Integer> group = groups.get(
                    ((msg[0] & 0xFF) << 24) +
                            ((msg[1] & 0xFF) << 16) +
                            ((msg[2] & 0xFF) << 8) +
                            ((msg[3] & 0xFF))
            );

            if (group == null) return;

            boolean schedule = false;
            SocketConnection socketCon;
            for (int receiverID : group) {
                socketCon = users.get(receiverID);

                if (socketCon != null)
                    try {
                        socketCon.write(message);
                        continue;
                    } catch (IOException ignored) {
                    }

                databaseHandler.scheduleBatch(receiverID, message);
                schedule = true;
            }
            if (schedule) databaseHandler.schedule();
        }
    }

    public DirectMessageSender getDirectMessageSender() {
        return directMessageSender;
    }

    public GroupMessageSender getGroupMessageSender() {
        return groupMessageSender;
    }
}