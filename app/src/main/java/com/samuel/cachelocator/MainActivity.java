package com.samuel.cachelocator;

import android.app.Application;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.ParseAnalytics;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseQuery;
import com.parse.ParseQueryAdapter;
import com.parse.ParseUser;
import com.parse.Parse;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.Builder;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class MainActivity
        extends ActionBarActivity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    public static final String TAG = MainActivity.class.getSimpleName();

    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    protected TextView mLatitudeText;
    protected TextView mLongitudeText;

    SupportMapFragment mapFragment;
    private Map<String, Marker> mapMarkers = new HashMap<String, Marker>();

    private float radius;
    private float lastRadius;
    private static final float METERS_PER_FEET = 0.3048f;
    private static final int METERS_PER_KILOMETER = 1000;

    private String selectedPostObjectId;

    private ParseQueryAdapter<UserPost> postsQueryAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        radius = 2;
        lastRadius = radius;

        ParseAnalytics.trackAppOpenedInBackground(getIntent());

        ParseUser currentUser = ParseUser.getCurrentUser();
        if(currentUser == null){
            navigateToLogin();
        }
        else{
            Log.i(TAG, currentUser.getUsername());
        }

        mLatitudeText = (TextView) findViewById(R.id.latitude_text);
        mLongitudeText = (TextView) findViewById(R.id.longitude_text);

        mapFragment = (SupportMapFragment) this.getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMap().setMyLocationEnabled(true);
        mapFragment.getMap().setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                //When the map camera is changed...
                mapQuery();
            }
        });

        buildGoogleApiClient();
        initParseQuery();

        Button postButton = (Button) findViewById(R.id.postButton);
        postButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLastLocation == null) {
                    //error
                    Log.i(TAG, "Error post Button");
                    return;
                }
                Intent intent = new Intent(MainActivity.this, PostActivity.class);
                intent.putExtra("myLoc", mLastLocation);
                startActivity(intent);
            }
        });
    }

    private void initParseQuery(){
        //Set up the query
        ParseQueryAdapter.QueryFactory<UserPost> factory =
                new ParseQueryAdapter.QueryFactory<UserPost>(){
                    public ParseQuery<UserPost> create(){
                        Location myLoc = mLastLocation;
                        ParseQuery<UserPost> query = UserPost.getQuery();
                        query.include("user");
                        query.orderByDescending("createdAt");
                        query.whereWithinKilometers("location", geoPointFromLocation(myLoc), radius *
                                METERS_PER_FEET / METERS_PER_KILOMETER);
                        query.setLimit(20);
                        return query;
                    }
                };

        postsQueryAdapter = new ParseQueryAdapter<UserPost>(this, factory){
            @Override
            public View getItemView(UserPost post, View view, ViewGroup parent){
                if(view == null){
                    view = View.inflate(getContext(), R.layout.userpost_post_item, null);
                }
                TextView contentView = (TextView) view.findViewById(R.id.content_view);
                TextView usernameView = (TextView) view.findViewById(R.id.username_view);
                contentView.setText(post.getText());
                usernameView.setText(post.getUser().getUsername());
                return view;
            }
        };

        postsQueryAdapter.setAutoload(false);
        postsQueryAdapter.setPaginationEnabled(false);

        ListView postsListView = (ListView) this.findViewById(R.id.postsListView);
        postsListView.setAdapter(postsQueryAdapter);

        postsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final UserPost item = postsQueryAdapter.getItem(position);
                selectedPostObjectId = item.getObjectId();
                mapFragment.getMap().animateCamera(
                        CameraUpdateFactory.newLatLng(new LatLng(item.getLocation().getLatitude(),
                                item.getLocation().getLongitude())), new GoogleMap.CancelableCallback() {
                            @Override
                            public void onFinish() {
                                Marker marker = mapMarkers.get(item.getObjectId());
                                if(marker != null){
                                    marker.showInfoWindow();
                                }
                            }

                            @Override
                            public void onCancel() {

                            }
                        }
                );
                Marker marker = mapMarkers.get(item.getObjectId());
                if(marker != null){
                    marker.showInfoWindow();
                }
            }
        });
    }

    private void listQuery(){
        Location myLoc = mLastLocation;
        if(myLoc != null){
            postsQueryAdapter.loadObjects();
        }
    }

    private void mapQuery(){
        Location myLoc = mLastLocation;
        if(myLoc == null){
            cleanUpMarkers(new HashSet<String>());
            return;
        }

        final ParseGeoPoint myPoint = geoPointFromLocation(myLoc);

        ParseQuery<UserPost> mapQuery = UserPost.getQuery();

        mapQuery.whereWithinKilometers("location", myPoint, 50);

        mapQuery.include("user");
        mapQuery.orderByDescending("createdAt");
        mapQuery.setLimit(20);

        mapQuery.findInBackground(new FindCallback<UserPost>() {
            @Override
            public void done(List<UserPost> listObjects, ParseException e) {
                //Handle Results

                Set<String> toKeep = new HashSet<String>();

                for(UserPost post : listObjects){
                    toKeep.add(post.getObjectId());

                    Marker oldMarker = mapMarkers.get(post.getObjectId());

                    MarkerOptions markerOpts = new MarkerOptions().position(
                            new LatLng(post.getLocation().getLatitude(),
                            post.getLocation().getLongitude()));

                    if(post.getLocation().distanceInKilometersTo(myPoint) > radius *
                            METERS_PER_FEET / METERS_PER_KILOMETER){
                        //Out of range
                        if(oldMarker != null){
                            if(oldMarker.getSnippet() == null){
                                //Marker is out of range already
                                continue;
                            }
                            else{
                                //This marker is out of range now, refresh to remove it
                                oldMarker.remove();
                            }
                        }
                        markerOpts = markerOpts.title(getResources().getString(R.string.post_out_of_range)).icon(
                                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                    }
                    else{
                        //within range
                        if(oldMarker != null){
                            if(oldMarker.getSnippet() != null){
                                //Marker is in range already
                                continue;
                            }
                            else{
                                //Marker is in range now, refresh
                                oldMarker.remove();
                            }
                        }
                        markerOpts = markerOpts.title(post.getText()).snippet(post.getUser().getUsername())
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    }

                    Marker marker = mapFragment.getMap().addMarker(markerOpts);
                    mapMarkers.put(post.getObjectId(), marker);

                    if(post.getObjectId().equals(selectedPostObjectId)){
                        marker.showInfoWindow();
                        selectedPostObjectId = null;
                    }
                }
                cleanUpMarkers(toKeep);
            }
        });
    }

    private ParseGeoPoint geoPointFromLocation(Location loc){
        return new ParseGeoPoint(loc.getLatitude(), loc.getLongitude());
    }

    private void cleanUpMarkers(Set<String> markersToKeep){
        for(String objId : new HashSet<String>(mapMarkers.keySet())){
            if(!markersToKeep.contains(objId)){
                Marker marker = mapMarkers.get(objId);
                marker.remove();
                mapMarkers.get(objId).remove();
                mapMarkers.remove(objId);
            }
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onResume(){
        super.onResume();

        radius = 2;
        if(mLastLocation != null){
            LatLng myLatLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            if(lastRadius != radius){
                //Update zoom
            }
            //Update the circle in map
        }
        //Save the radius
        lastRadius = radius;

        mLastLocation = new Location("temp");
        mLastLocation.setLatitude(0);
        mLastLocation.setLongitude(0);

        mapQuery();
        listQuery();
    }

    @Override
    protected void onStop(){
        super.onStop();
        if(mGoogleApiClient.isConnected()){
            mGoogleApiClient.disconnect();
        }
    }

    private void navigateToLogin() {
        //Go to log in activity
        Intent intent = new Intent(this, LoginActivity.class);
        //A flag is needed to skip the MainActivity from showing up?
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //Clear the task so that we cant back arrow into it
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onConnected(Bundle connectionHint){
        //Google Play Services is connected

        //Get last location
        //mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        mLastLocation = new Location("temp");
        mLastLocation.setLatitude(0);
        mLastLocation.setLongitude(0);

        if(mLastLocation != null){
            //Latitudes and longitudes
            mLatitudeText.setText(String.valueOf(mLastLocation.getLatitude()));
            mLongitudeText.setText(String.valueOf(mLastLocation.getLongitude()));
        }
        else{
            //No location
            Log.i(TAG, "Error at onConnected, mLastLocation is null");
        }
    }

    public void onConnectionSuspended(int cause){
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "Connection failed");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_logout) {
            ParseUser.logOut();
            navigateToLogin();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
