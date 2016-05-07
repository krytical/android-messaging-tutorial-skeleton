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
import com.firebase.client.ValueEventListener;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.messaging.Message;
import com.sinch.android.rtc.messaging.MessageClient;
import com.sinch.android.rtc.messaging.MessageClientListener;
import com.sinch.android.rtc.messaging.MessageDeliveryInfo;
import com.sinch.android.rtc.messaging.MessageFailureInfo;
import com.sinch.android.rtc.messaging.WritableMessage;

import ca.chirp.chirpmessenger.R;

public class MessagingActivity extends Activity{

    private String recipientId;
    private EditText messageBodyField;
    private String messageBody;
    private MessageService.MessageServiceInterface messageService;
    private MessageAdapter messageAdapter;
    private ListView messagesList;
    private String currentUserId;
    //private ServiceConnection serviceConnection = new MyServiceConnection();
    //private MessageClientListener messageClientListener = new MyMessageClientListener();

    private Firebase fireRef;
    private AuthData authData;
    private UserDAO userDAO;

    private static String LOG_TAG = "MESSAGE_ACTIVITY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fireRef = MainDAO.getInstance().getFirebase();
        setContentView(R.layout.messaging);

        //bindService(new Intent(this, MessageService.class), serviceConnection, BIND_AUTO_CREATE);

        Intent intent = getIntent();
        recipientId = intent.getStringExtra("RECIPIENT_ID");

        userDAO = new UserDAO();
        authData = fireRef.getAuth();

        // Get the current user id from Firebase
        Firebase userRef = userDAO.getUserRef(authData.getUid());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Check if there is data at the database location
                if (dataSnapshot.exists()) {
                    currentUserId = dataSnapshot.getValue(UserModel.class).getEmail();
                }
            }
            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.e(LOG_TAG, "Error trying to get current user data" + firebaseError.getMessage());
            }
        });

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

    }

    private void sendMessage() {

    }

    @Override
    public void onDestroy() {
        //messageService.removeMessageClientListener(messageClientListener);
        //unbindService(serviceConnection);
        super.onDestroy();
    }
}
