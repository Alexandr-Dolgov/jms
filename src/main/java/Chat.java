import java.io.*;
import javax.jms.*;
import javax.naming.*;

public class Chat implements javax.jms.MessageListener {

    private TopicSession topicPublisherSession;
    private TopicPublisher topicPublisher;
    private TopicConnection topicConnection;
    private String username;

    private long quantityMessages;

    /* Constructor used to Initialize Chat */
    public Chat(String username)
            throws Exception {

        // Obtain a JNDI topicConnection using the jndi.properties file
        InitialContext ctx = new InitialContext();

        // Look up a JMS topicConnection factory and create the topicConnection
        TopicConnectionFactory conFactory = (TopicConnectionFactory) ctx.lookup("TopicCF");
        TopicConnection topicConnection = conFactory.createTopicConnection();

        // Create two JMS session objects
        TopicSession topicPublisherSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicSession topicSubscriberSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

        // Look up a JMS topic
        Topic chatTopic = (Topic) ctx.lookup("ALL");

        // Create a JMS topicPublisher and topicSubscriber. The additional parameters
        // on the createSubscriber are a message selector (null) and a true
        // value for the noLocal flag indicating that messages produced from
        // this topicPublisher should not be consumed by this topicPublisher.
        TopicPublisher topicPublisher = topicPublisherSession.createPublisher(chatTopic);
        TopicSubscriber topicSubscriber = topicSubscriberSession.createSubscriber(chatTopic, null, true);

        // Set a JMS message listener
        topicSubscriber.setMessageListener(this);

        // Initialize the Chat application variables
        this.topicConnection = topicConnection;
        this.topicPublisherSession = topicPublisherSession;
        this.topicPublisher = topicPublisher;
        this.username = username;

        // Start the JMS topicConnection; allows messages to be delivered
        topicConnection.start();
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
        quantityMessages++;
        TextMessage message = topicPublisherSession.createTextMessage();
        message.setText(username + ": " + text);
        topicPublisher.publish(message);
    }

    /* Close the JMS Connection */
    public void close() throws JMSException {
        topicConnection.close();
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
            BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));


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