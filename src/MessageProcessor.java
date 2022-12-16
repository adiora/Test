import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

class MessageProcessor {

    private final LinkedBlockingQueue<Message> messages;
    private final Map<Integer, Command> commandMap;

    private Thread thread;

    MessageProcessor(Map<Integer, Command> commandMap) {
        this.commandMap = commandMap;
        messages = new LinkedBlockingQueue<>();
    }

    protected void start() {

        thread = new Thread(() -> {
            Message message;

            for(;;) {
                try {
                    message = messages.take();
                } catch(InterruptedException e) {
                    messages.clear();
                    e.printStackTrace();
                    break;
                }
                commandMap.get(message.type()).execute(message);
            }
        }, "Message Processor");

        thread.start();
    }

    protected void stop() {
        thread.interrupt();
    }

    protected void handle(Message message) {
        try {
            messages.put(message);
        } catch (InterruptedException e) {
            e.printStackTrace();
            handle(message);
        }
    }

}