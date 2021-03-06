package ca.chirp.messenger;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.firebase.client.AuthData;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.firebase.client.ValueEventListener;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.messaging.Message;
import com.sinch.android.rtc.messaging.MessageClient;
import com.sinch.android.rtc.messaging.MessageClientListener;
import com.sinch.android.rtc.messaging.MessageDeliveryInfo;
import com.sinch.android.rtc.messaging.MessageFailureInfo;
import com.sinch.android.rtc.messaging.WritableMessage;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ca.chirp.chirpmessenger.R;

public class MessagingActivity extends Activity{

    private String recipientId;
    private EditText messageBodyField;
    private String messageBody;
    private MessageService.MessageServiceInterface messageService;
    private MessageAdapter messageAdapter;
    private ListView messagesList;
    private String currentUserId;
    private ServiceConnection serviceConnection = new MyServiceConnection();
    private MessageClientListener messageClientListener = new MyMessageClientListener();

    private Firebase fireRef;
    private AuthData authData;
    private UserDAO userDAO;
    private static String[] userIds;
    private Query chatQuery;

    private static String LOG_TAG = "MESSAGE_ACTIVITY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fireRef = MainDAO.getInstance().getFirebase();
        setContentView(R.layout.messaging);

        bindService(new Intent(this, MessageService.class), serviceConnection, BIND_AUTO_CREATE);

        Intent intent = getIntent();
        recipientId = intent.getStringExtra("RECIPIENT_ID");

        messagesList = (ListView) findViewById(R.id.listMessages);
        messageAdapter = new MessageAdapter(this);
        messagesList.setAdapter(messageAdapter);
        populateMessageHistory();

        messageBodyField = (EditText) findViewById(R.id.messageBodyField);

        findViewById(R.id.sendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });
    }

    // Get previous messages from Firebase to display
    private void populateMessageHistory() {
        userDAO = new UserDAO();
        authData = fireRef.getAuth();
        chatQuery = fireRef.child("chat");

        // Get the current user id from Firebase
        Firebase userRef = userDAO.getUserRef(authData.getUid());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Check if there is data at the database location
                if (dataSnapshot.exists()) {
                    currentUserId = dataSnapshot.getValue(UserModel.class).getEmail();
                    userIds = new String[] {currentUserId, recipientId};
                    // Check the senderId to see if it is the correct sender for the conversation
                    chatQuery.orderByChild("senderId").startAt(currentUserId).endAt(currentUserId+"\uf8ff");
                }
            }
            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.e(LOG_TAG, "Error trying to get current user data" + firebaseError.getMessage());
            }
        });

        chatQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot: dataSnapshot.getChildren()) {
                    // Check the recipientId to see if it is the correct recipient for the conversation
                    if (Arrays.asList(userIds).contains(userSnapshot.getValue(ChatModel.class).getSenderId()) && Arrays.asList(userIds).contains(userSnapshot.getValue(ChatModel.class).getRecipientId())) {
                        WritableMessage message = new WritableMessage(userSnapshot.getValue(ChatModel.class).getRecipientId(), userSnapshot.getValue(ChatModel.class).getMessageText());
                        if (userSnapshot.getValue(ChatModel.class).getSenderId().equals(currentUserId)) {
                            messageAdapter.addMessage(message, MessageAdapter.DIRECTION_OUTGOING, "Me");
                        } else {
                            messageAdapter.addMessage(message, MessageAdapter.DIRECTION_INCOMING, userSnapshot.getValue(ChatModel.class).getSenderId());
                        }
                    }
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    private void sendMessage() {
        messageBody = messageBodyField.getText().toString();
        if (messageBody.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_LONG).show();
            return;
        }

        messageService.sendMessage(recipientId, messageBody);
        messageBodyField.setText("");
    }

    @Override
    public void onDestroy() {
        messageService.removeMessageClientListener(messageClientListener);
        unbindService(serviceConnection);
        super.onDestroy();
    }

    private class MyServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            messageService = (MessageService.MessageServiceInterface) iBinder;
            messageService.addMessageClientListener(messageClientListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            messageService = null;
        }
    }

    private class MyMessageClientListener implements MessageClientListener {
        @Override
        public void onMessageFailed(MessageClient client, Message message,
                                    MessageFailureInfo failureInfo) {
            Toast.makeText(MessagingActivity.this, "Message failed to send.", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onIncomingMessage(MessageClient client, final Message message) {
            if (message.getSenderId().equals(recipientId)) {
                final WritableMessage writableMessage = new WritableMessage(message.getRecipientIds().get(0), message.getTextBody());
                messageAdapter.addMessage(writableMessage, MessageAdapter.DIRECTION_INCOMING, message.getSenderId());
            }
        }

        @Override
        public void onMessageSent(MessageClient client, Message message, String recipientId) {
            final WritableMessage writableMessage = new WritableMessage(message.getRecipientIds().get(0), message.getTextBody());

            DateFormat df = new SimpleDateFormat("h:mm a", Locale.CANADA);
            String time = df.format(Calendar.getInstance().getTime());
            Map<String, String> chatMessages = new HashMap<String, String>();
            chatMessages.put("senderId", currentUserId);
            chatMessages.put("recipientId", writableMessage.getRecipientIds().get(0));
            chatMessages.put("messageText", writableMessage.getTextBody());
            chatMessages.put("sinchId", message.getMessageId());
            chatMessages.put("timeSent", time);

            // Only add message to firebase database if it doesn't already exist there
            Firebase chatRef = fireRef.child("chat");
            chatRef.push().setValue(chatMessages);

            messageAdapter.addMessage(writableMessage, MessageAdapter.DIRECTION_OUTGOING, "Me");
        }

        @Override
        public void onMessageDelivered(MessageClient client, MessageDeliveryInfo deliveryInfo) {}

        @Override
        public void onShouldSendPushData(MessageClient client, Message message, List<PushPair> pushPairs) {}
    }
}
