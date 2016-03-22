package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SERVER_PORT = 10000;
    static final String[] REMOTE_PORTS = {REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};

    private final Uri mUri;

    private int s = 0;
    private int counter = 0;

    private PriorityQueue<Message> queue;

    public GroupMessengerActivity() {
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    }

    /**
     * buildUri() demonstrates how to build a URI for a ContentProvider.
     *
     * @param scheme
     * @param authority
     * @return the URI
     */
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    public int getPid(String portNo){return (Integer.parseInt(portNo.substring(2))/4)-1;}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */

        TextView tv = (TextView) findViewById(R.id.remote_text_display);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;

        }

        queue = new PriorityQueue<Message>(50, new Comparator<Message>() {
            public int compare(Message x, Message y) {
                if (x.getPriority() < y.getPriority())
                {
                    return -1;
                }
                if (x.getPriority() > y.getPriority())
                {
                    return 1;
                }
                if (x.getPriority() == y.getPriority()){
                    if ((x.getDeliverable()) && (!y.getDeliverable()))
                        return 1;
                    if ((!x.getDeliverable()) && (y.getDeliverable()))
                        return -1;
                }
                return 0;
            }
        });

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        final EditText editText = (EditText) findViewById(R.id.editText1);
        final Button button4 = (Button) findViewById(R.id.button4);

        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText("");

                TextView localTextView = (TextView) findViewById(R.id.local_text_display);
                localTextView.append("\t" + msg);
                TextView remoteTextView = (TextView) findViewById(R.id.remote_text_display);
                remoteTextView.append("\n");

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, Message, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            ServerSocket serverSocket = sockets[0];
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {
                while (true) {
                    s++;
                    Socket clientSocket = serverSocket.accept();
                    ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
                    Message msg = (Message) input.readObject();
                    ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                    msg.setPriority(s);
                    out.writeObject(msg);

                    queue.add(msg);

                    msg = (Message) input.readObject();
                    Message newMsg = msg;
                    for (Message m:queue){
                        if (m.getMessage() == msg.getMessage()){
                            newMsg = m;
                        }
                    }
                    queue.remove(newMsg);

                    if (msg.getPriority() > s)
                        newMsg.setPriority(msg.getPriority());
                    else{
                        newMsg.setPriority(s);
                    }

                    newMsg.setDeliverable(true);
                    queue.add(newMsg);

                    ArrayList<Message> toBeRemoved = new ArrayList<Message>();

                    Message[] listOfMessages = (Message[]) queue.toArray();
                    Arrays.sort(listOfMessages);
                    for (int i = 0; i < listOfMessages.length; i++){
                        if(listOfMessages[i].getDeliverable() == true){
                            toBeRemoved.add(listOfMessages[i]);
                        }
                        else
                            break;
                    }

                    for (int i = 0; i < toBeRemoved.size(); i++){
                        publishProgress(toBeRemoved.get(i));
                        queue.remove(toBeRemoved.get(i));
                    }
                    input.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get message");
            }
            return null;
        }

        protected void onProgressUpdate(Message...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            Message rcvd = strings[0];
            String strReceived = rcvd.getMessage().trim();

            TextView remoteTextView = (TextView) findViewById(R.id.remote_text_display);
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.local_text_display);
            localTextView.append("\n");

            ContentValues cv = new ContentValues();
            cv.put("key", Integer.toString(rcvd.getPriority()));
            cv.put("value", strReceived);
            ContentResolver cr = getContentResolver();
            cr.insert(mUri, cv);
            //CNT++;
            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                ArrayList<int[]> listOfSequence = new ArrayList<int[]>();
                counter++;
                String remotePort = msgs[1];

                System.out.println("Works so far - Port number - " + remotePort + "\n");

                Message msg = new Message(msgs[0], counter, getPid(remotePort));
                for (int i = 0; i < REMOTE_PORTS.length; i++) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORTS[i]));
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(msg);
                    out.flush();

                    InputStream is = socket.getInputStream();
                    ObjectInputStream ois = new ObjectInputStream(is);
                    int[] seqNo = new int[2];
                    Message newMsg = (Message) ois.readObject();
                    seqNo[0] = newMsg.getPriority();
                    seqNo[1] = newMsg.getPid();
                    //Sequence seq1 = new Sequence(seqNo[0], seqNo[1]);
                    listOfSequence.add(seqNo);

                    //out.write(msg);
                    //out.close();
                    socket.close();
                }
                Collections.sort(listOfSequence, new Comparator<int[]>() {public int compare(int[] x, int[] y) {if (x[0] < y[0]) {return -1;}if (x[1] > y[1]) {return 1;}return 0;}});
                int[] seq = listOfSequence.get(0);
                if (listOfSequence.size() > 1){
                    if (listOfSequence.get(0)[0] == listOfSequence.get(1)[0]){
                        for (int i = 1; i < listOfSequence.size(); i++){
                            if (listOfSequence.get(i)[0] == seq[0]){
                                if (listOfSequence.get(i)[1] < seq[1])
                                    seq[1] = listOfSequence.get(i)[1];
                            }
                            else
                                break;
                        }
                    }
                }
                msg.setPriority(seq[0]);
                for (int i = 0; i < REMOTE_PORTS.length; i++) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORTS[i]));
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(msg);
                    out.flush();
                    out.close();
                    socket.close();
                }

            }catch(UnknownHostException e){
                Log.e(TAG, "ClientTask UnknownHostException");
            }catch(IOException e){
                Log.e(TAG, "ClientTask socket IOException");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private class Message implements Serializable{
        private String message;
        private int priority;
        private int pid;
        private boolean isDeliverable;

        public Message(String m, int pri, int p){
            message = m;
            priority = pri;
            pid = p;
            isDeliverable = false;
        }

        public String getMessage(){return message;}
        public int getPriority(){return priority;}
        public void setPriority(int p){this.priority = p;}
        public void setPortNo(int p){priority = p;}
        public int getPid(){return pid;}
        public boolean getDeliverable(){return isDeliverable;}
        public void setDeliverable(boolean b){isDeliverable = b;}
    }
}