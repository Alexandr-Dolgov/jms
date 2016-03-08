import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.jms.*;
import javax.naming.*;

public class Chat implements javax.jms.MessageListener {

    private String username;
    private Context context;
    private long msgid;  //счетчик отправленных сообщений

    private TopicSession topicPublisherSession;
    private TopicPublisher topicPublisher;
    private TopicConnection topicConnection;

    private QueueConnection queueConnection;
    private QueueSession queueSession;

    private boolean queueExistInJndiProperties(String queueName) {
        boolean queueFound = false;
        try(BufferedReader in = new BufferedReader(new FileReader("jndi.properties"))) {
            String s;
            while ((s = in.readLine()) != null) {
                if (s.equals("queue." + queueName + " = " + queueName)) {
                    queueFound = true;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return queueFound;
    }

    private void addQueueInJndiProperties(String queueName) {
        try(BufferedWriter out = new BufferedWriter(new FileWriter("jndi.properties", true))) {
            out.append("\nqueue.").append(queueName).append(" = ").append(queueName);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /* Constructor used to Initialize Chat */
    public Chat(String username) throws Exception {

        boolean queueFound = queueExistInJndiProperties(username);
        if (!queueFound) {
            addQueueInJndiProperties(username);
        }

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
        this.context = context;

        // Start the JMS topicConnection; allows messages to be delivered
        topicConnection.start();

        //-------------------очередь------------------------

        QueueConnectionFactory queueCF = (QueueConnectionFactory) context.lookup("QueueCF");
        QueueConnection queueConnection = queueCF.createQueueConnection();
        QueueSession queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = (Queue) context.lookup(username);
        QueueReceiver qReceiver = queueSession.createReceiver(queue);
        qReceiver.setMessageListener(this);

        this.queueConnection = queueConnection;
        this.queueSession = queueSession;

        queueConnection.start();

    }

    /* Receive Messages From Topic Subscriber */
    public void onMessage(Message message) {
        try {
            TextMessage textMessage = (TextMessage) message;
            Map m = (JSONObject) new JSONParser().parse(textMessage.getText());
            if (m.get("success") == true) {
                System.out.println("Delivered to " + m.get("from") + "(" + m.get("msgid") + ")");
            } else {
                String from = (String) m.get("from");
                System.out.println("[" + m.get("date") + "] " + from +
                        (m.get("to").equals("ALL") ? " to ALL: " : ": ") + m.get("message"));
                writeMessageInQueue(from, createConfirmMsgJsonBody((long) m.get("msgid")));
            }
        } catch (JMSException e) {
            e.printStackTrace();
        } catch (ParseException ignore) {}
    }

    /* Create and Send Message Using Publisher */
    private void writeMessage(String input) throws JMSException {
        msgid++;

        if (input.startsWith("ALL ")) {
            writeMessageInTopicAll(input.substring("ALL ".length()));
        } else {
            String receiverName = input.substring(0, input.indexOf(' '));
            String messageText = input.substring(receiverName.length() + 1);
            String msgJsonBody = createMsgJsonBody(receiverName, messageText);
            writeMessageInQueue(receiverName, msgJsonBody);
        }
    }

    private void writeMessageInTopicAll(String text) throws JMSException {
        TextMessage message = topicPublisherSession.createTextMessage();
        message.setText(createMsgJsonBody("ALL", text));
        topicPublisher.publish(message);
    }

    private void writeMessageInQueue(String queue, String msgJsonBody) throws JMSException {
        Queue q = null;
        try {
            q = (Queue) context.lookup(queue);
        } catch (NamingException ignore) {}

        TextMessage message = queueSession.createTextMessage();
        message.setText(msgJsonBody);

        QueueSender queueSender = queueSession.createSender(q);
        queueSender.send(message);
    }

    private String createMsgJsonBody(final String TO, final String TEXT) {
        return JSONObject.toJSONString(new HashMap<String, Object>() {{
            put("msgid", msgid);
            put("from", username);
            put("to", TO);
            put("date", new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()));
            put("message", TEXT);
        }});
    }

    private String createConfirmMsgJsonBody(final long MSGID) {
        return JSONObject.toJSONString(new HashMap<String, Object>() {{
            put("msgid", MSGID);
            put("from", username);
            put("success", true);
        }});
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
                System.exit(1);
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