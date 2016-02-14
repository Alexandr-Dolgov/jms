import java.io.*;
import javax.jms.*;
import javax.naming.*;

public class Chat implements javax.jms.MessageListener {

    private TopicSession pubSession;
    private TopicPublisher publisher;
    private TopicConnection connection;
    private String username;

    /* Constructor used to Initialize Chat */
    public Chat(String username)
            throws Exception {

        // Obtain a JNDI connection using the jndi.properties file
        InitialContext ctx = new InitialContext();

        // Look up a JMS connection factory and create the connection
        TopicConnectionFactory conFactory =
                (TopicConnectionFactory) ctx.lookup("TopicCF");
        TopicConnection connection = conFactory.createTopicConnection();

        // Create two JMS session objects
        TopicSession pubSession = connection.createTopicSession(
                false, Session.AUTO_ACKNOWLEDGE);
        TopicSession subSession = connection.createTopicSession(
                false, Session.AUTO_ACKNOWLEDGE);

        // Look up a JMS topic
        Topic chatTopic = (Topic) ctx.lookup("ALL");

        // Create a JMS publisher and subscriber. The additional parameters
        // on the createSubscriber are a message selector (null) and a true
        // value for the noLocal flag indicating that messages produced from
        // this publisher should not be consumed by this publisher.
        TopicPublisher publisher =
                pubSession.createPublisher(chatTopic);
        TopicSubscriber subscriber =
                subSession.createSubscriber(chatTopic, null, true);

        // Set a JMS message listener
        subscriber.setMessageListener(this);

        // Initialize the Chat application variables
        this.connection = connection;
        this.pubSession = pubSession;
        this.publisher = publisher;
        this.username = username;

        // Start the JMS connection; allows messages to be delivered
        connection.start();
    }

    /* Receive Messages From Topic Subscriber */
    public void onMessage(Message message) {
        try {
            TextMessage textMessage = (TextMessage) message;
            System.out.println(textMessage.getText());
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    /* Create and Send Message Using Publisher */
    protected void writeMessage(String text) throws JMSException {
        TextMessage message = pubSession.createTextMessage();
        message.setText(username + ": " + text);
        publisher.publish(message);
    }

    /* Close the JMS Connection */
    public void close() throws JMSException {
        connection.close();
    }

    /* Run the Chat Client */
    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                System.out.println("username missing");
            }

            // args[0]=topicFactory; args[1]=username
            Chat chat = new Chat(args[0]);

            // Read from command line
            BufferedReader commandLine = new
                    java.io.BufferedReader(new InputStreamReader(System.in));


            // Loop until the word "exit" is typed
            while (true) {
                String s = commandLine.readLine();
                if (s.equalsIgnoreCase("exit")) {
                    chat.close();
                    System.exit(0);
                } else
                    chat.writeMessage(s);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}