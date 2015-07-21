package com.samuel.cachelocator;

import android.app.Activity;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Intent;
import android.location.Location;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.parse.ParseACL;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseUser;
import com.parse.SaveCallback;

public class PostActivity extends Activity {

    public static final String TAG = MainActivity.class.getSimpleName();

    protected ParseGeoPoint geoPoint;
    protected EditText postEditText;
    protected Button postButton;

    private int maxCharCount = 64;
    private TextView charCountTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        Intent intent = getIntent();

        if(intent.hasExtra("myLoc")){
            Location location = intent.getParcelableExtra("myLoc");
            geoPoint = new ParseGeoPoint(location.getLatitude(), location.getLongitude());
        }

        postEditText = (EditText) findViewById(R.id.post_edittext);
        postEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePostButtonState();
                updateCharacterCountTextViewText();
            }
        });

        charCountTextView = (TextView)findViewById(R.id.charCountTextView);

        postButton = (Button) findViewById(R.id.postButton);
        postButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                post();
            }
        });
    }

    private void updatePostButtonState(){
        //Enables and Disables the "Post" submit button
        int length = postEditText.toString().trim().length();
        if(length > 0 && length < maxCharCount){
            postButton.setEnabled(true);
        }
        else{
            Log.i(TAG, "Can't post this.");
        }
    }

    private void updateCharacterCountTextViewText(){
        //Updates the displayed character count
        String charCountStr = String.format("%d/%d", postEditText.length(), maxCharCount);
        charCountTextView.setText(charCountStr);
    }

    private void post(){
        String text = postEditText.getText().toString().trim();

        UserPost post = new UserPost();

        post.setLocation(geoPoint);
        post.setText(text);
        post.setUser(ParseUser.getCurrentUser());

        ParseACL acl = new ParseACL();
        acl.setPublicReadAccess(true);
        post.setACL(acl);

        post.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                finish();
                navigateToMain();
            }
        });
    }

    private void navigateToMain(){
        //Go to main activity
        Intent intent = new Intent(this, MainActivity.class);
        //A flag is needed to skip the MainActivity from showing up?
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //Clear the task so that we cant back arrow into it
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_post, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
