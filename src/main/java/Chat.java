import java.io.*;
import javax.jms.*;
import javax.naming.*;

public class Chat implements javax.jms.MessageListener {

    private String username;
    private long quantityMessages;  //msgid

    private TopicSession topicPublisherSession;
    private TopicPublisher topicPublisher;
    private TopicConnection topicConnection;

    private QueueConnection queueConnection;


    /* Constructor used to Initialize Chat */
    public Chat(String username) throws Exception {

        // Obtain a JNDI topicConnection using the jndi.properties file
        Context context = new InitialContext();

        //--------------------топик-------------------------

        // Look up a JMS topicConnection factory and create the topicConnection
        TopicConnectionFactory topicCF = (TopicConnectionFactory) context.lookup("TopicCF");
        TopicConnection topicConnection = topicCF.createTopicConnection();

        // Create two JMS session objects
        TopicSession topicPublisherSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicSession topicSubscriberSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

        // Look up a JMS topic
        Topic chatTopic = (Topic) context.lookup("ALL");

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

        //-------------------очередь------------------------

        QueueConnectionFactory queueCF = (QueueConnectionFactory) context.lookup("QueueCF");
        QueueConnection queueConnection = queueCF.createQueueConnection();
        QueueSession queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue requestQ = queueSession.createQueue(username);

        this.queueConnection = queueConnection;

        queueConnection.start();

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
    private void writeMessage(String text) throws JMSException {
        quantityMessages++;

        if (text.startsWith("ALL ")) {
            writeMessageInTopicAll(text);
        } else {
            String receiverName = text.substring(0, text.indexOf(' '));
            writeMessageInQueue(receiverName, text);
        }
    }

    private void writeMessageInTopicAll(String text) throws JMSException {
        TextMessage message = topicPublisherSession.createTextMessage();
        message.setText(username + ": " + text);
        topicPublisher.publish(message);
    }

    private void writeMessageInQueue(String queue, String text) {
        System.out.println("отправка сообдещия '" + text + "' в очередь '" + queue + "'");
    }

    /* Close the JMS Connection */
    public void close() throws JMSException {
        topicConnection.close();
        queueConnection.close();
    }

    /* Run the Chat Client */
    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                System.out.println("username missing");
            }

            String username = args[0];
            Chat chat = new Chat(username);

            System.out.println("Chat " + username + " started");

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