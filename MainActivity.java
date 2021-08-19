package com.navigine.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.navigine.idl.java.GeometryUtils;
import com.navigine.idl.java.Location;
import com.navigine.idl.java.LocationListener;
import com.navigine.idl.java.LocationManager;
import com.navigine.idl.java.LocationPoint;
import com.navigine.idl.java.MeasurementManager;
import com.navigine.idl.java.NavigationManager;
import com.navigine.idl.java.NavigineSdk;
import com.navigine.idl.java.Point;
import com.navigine.idl.java.Position;
import com.navigine.idl.java.PositionListener;
import com.navigine.idl.java.ResourceManager;
import com.navigine.idl.java.RouteEvent;
import com.navigine.idl.java.RouteListener;
import com.navigine.idl.java.RouteManager;
import com.navigine.idl.java.RoutePath;
import com.navigine.idl.java.Sublocation;
import com.navigine.idl.java.Venue;
import com.navigine.sdk.Navigine;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    Button startNavigationButton;
    TextView uploadStatusText, directionText, lenghtText, curPosText, destPosText, lenght1, lenght2, lenght3, lenght4, lenght5;

    private final String[] REQUIRED_PERMISSIONS = new String[]{ ///< Array of permissions to be granted.
           // Manifest.permission.CAMERA,
            //Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            /* Manifest.permission.BLUETOOTH,
             Manifest.permission.BLUETOOTH_ADMIN*/
    };

    //Navigine data
    public static final String  SERVER_URL     = "https://api.stage.navigine.com/";
    public static final String  USER_HASH      = "4220-CCFC-9E1F-82D6";
    public static final int     LOCATION_ID    = 17412;
    public static final int     SUBLOCATION_ID = 22129;
    public static final boolean WRITE_LOGS     = false;
    LocationListener mLocationListener;
    LocationManager mLocationManager;
    ResourceManager mResourceManager;
    NavigineSdk mNavigineSdk;
    NavigationManager navigationManager;
    RouteManager routeManager;
    Sublocation mSubLocation;
    MeasurementManager mMeasurementManager;
    ArrayList<String> mVenusAliases = new ArrayList<>();
    ArrayList<Venue> mRackList;                         ////rack list, getting from sublocation
    ArrayList<String> mVenusNames = new ArrayList<>();  ////rack names list
    //ArrayList<Product> tempProductsList = new ArrayList<>();
    String currentPositionX, currentPositionY, currentTargetX, currentTargetY;
    private boolean mIsGranted;
    private final static int PERMISSION_REQUEST_CODE = 0x01;             ///< Permissions request code.

    Toast distanceToast;
    boolean isShowToast;
    Point curPoint = null;
    Handler handler;
    LocationPoint mLocationPoint, mLocationPointGr1, mLocationPointSweets, mLocationPointDrinks, mLocationPointTea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startNavigationButton = findViewById(R.id.start_navigation_button);
        uploadStatusText = findViewById(R.id.contentstatus);
        directionText = findViewById(R.id.direction_text);
        lenghtText = findViewById(R.id.lenght_text);
        curPosText = findViewById(R.id.curPosText);
        destPosText = findViewById(R.id.destPosText);
        lenght1 = findViewById(R.id.lenght1);
        lenght2 = findViewById(R.id.lenght2);
        lenght3 = findViewById(R.id.lenght3);
        lenght4 = findViewById(R.id.lenght4);
        lenght5 = findViewById(R.id.lenght5);

        if (!mIsGranted) {
            makeRequestPermissions();
        }

        startNavigationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLocationTarget();
                isShowToast=true;
                showDistance();
                distanceToast = Toast.makeText(MainActivity.this, "distance = ", Toast.LENGTH_SHORT);
                distanceToast.show();
            }
        });

        initNavigation();

        handler = new Handler();

    }

    private void initNavigation(){

        Navigine.initialize(getApplicationContext());
        NavigineSdk.setUserHash(USER_HASH); // your user hash from the server
        NavigineSdk.setServer(SERVER_URL); // your server url (by default `https://api.navigine.com`)
        mNavigineSdk = NavigineSdk.getInstance();
        mLocationManager = mNavigineSdk.getLocationManager();
        mMeasurementManager = mNavigineSdk.getMeasurementManager();
        mResourceManager = mNavigineSdk.getResourceManager(mLocationManager);
        String temp = mNavigineSdk.getVersion();

        mLocationListener = new LocationListener() {
            @Override
            public void onLocationLoaded(Location location) {
                Toast.makeText(getApplicationContext(), "yahoo", Toast.LENGTH_LONG).show();
                mSubLocation = location.getSublocations().get(0);
                int version = location.getVersion();
                int subLo = mSubLocation.getId();
                mRackList = mSubLocation.getVenues();             //fill rackList
                for(int k=0; k<mRackList.size(); k++){
                    String temp = mRackList.get(k).getName();
                    mVenusNames.add(temp);                          //fill rackName list
                    //mVenusAliases.add(mVenues.get(k).getAlias());
                    uploadStatusText.setText("Location loaded successful");
                    startNavigationButton.setEnabled(true);

                    mLocationPoint = new LocationPoint(getProductPoint("Grocery2"), LOCATION_ID, SUBLOCATION_ID);
                    mLocationPointGr1 = new LocationPoint(getProductPoint("Grocery1"), LOCATION_ID, SUBLOCATION_ID);
                    mLocationPointSweets = new LocationPoint(getProductPoint("Sweets"), LOCATION_ID, SUBLOCATION_ID);
                    mLocationPointDrinks = new LocationPoint(getProductPoint("Drinks"), LOCATION_ID, SUBLOCATION_ID);
                    mLocationPointTea = new LocationPoint(getProductPoint("Tea"), LOCATION_ID, SUBLOCATION_ID);
                }
                navigationManager = mNavigineSdk.getNavigationManager(mLocationManager);
                navigationManager.addPositionListener(new PositionListener() {
                    @Override
                    public void onPositionUpdated(Position position) {
                        //Toast.makeText(m_activity.getApplicationContext(), position.getPoint().getX() + " " +position.getPoint().getY(), Toast.LENGTH_SHORT).show();
                       /* if(distanceToast!=null)
                        {
                            distanceToast.cancel();
                        }*/
                        currentPositionX = String.valueOf(position.getPoint().getX());
                        currentPositionY = String.valueOf(position.getPoint().getY());
                        curPosText.setText("curPosition = " + currentPositionX + "__" + currentPositionY);
                        curPoint = position.getPoint();

                        Point curPoint = position.getPoint();
                        LocationPoint curLocPoint = new LocationPoint(curPoint, LOCATION_ID, SUBLOCATION_ID);

                        RoutePath pathGrocery2 =  routeManager.makeRoute(curLocPoint, mLocationPoint);
                        lenght2.setText("Lenght Grocery2 = "+ pathGrocery2.getLength());
                        RoutePath pathGrocery1 =  routeManager.makeRoute(curLocPoint, mLocationPointGr1);
                        lenght1.setText("Lenght Grocery1 = "+ pathGrocery1.getLength());
                        RoutePath pathGrocerySweets =  routeManager.makeRoute(curLocPoint, mLocationPointSweets);
                        lenght3.setText("Lenght Sweets = "+ pathGrocerySweets.getLength());
                        RoutePath pathGroceryDrinks =  routeManager.makeRoute(curLocPoint, mLocationPointDrinks);
                        lenght4.setText("Lenght Drinks = "+ pathGroceryDrinks.getLength());
                        RoutePath pathGroceryTea =  routeManager.makeRoute(curLocPoint, mLocationPointTea);
                        lenght5.setText("Lenght Tea = "+ pathGroceryTea.getLength());

                        //float distance = GeometryUtils.distanceBetweenPoints(curPoint, destPoint);
                        /*Toast.makeText(m_activity.getApplicationContext(), "lenght = " + pathGrocery1.getLength() + "__" + lenghtPathGrocery2 + "__" + pathGrocerySweets.getLength()
                                + "__" + pathGroceryDrinks.getLength() + "__" + pathGroceryTea.getLength(), Toast.LENGTH_SHORT).show();*/
                        Toast.makeText(MainActivity.this, "Location updated", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPositionError(Error error) {
                        Toast.makeText(MainActivity.this, "Position update Error", Toast.LENGTH_SHORT).show();
                    }
                });
                //navigationManager.startLogRecording();
                routeManager = mNavigineSdk.getRouteManager(mLocationManager, navigationManager);
                routeManager.addRouteListener(new RouteListener() {
                    @Override
                    public void onPathsUpdated(ArrayList<RoutePath> arrayList) {
                        //for(int i = 0; i<arrayList.size(); i++) {
                        if(arrayList.size() != 0) {
                            ArrayList<RouteEvent> routeEvent = arrayList.get(0).getEvents();
                            ArrayList<LocationPoint> locationPoints = arrayList.get(0).getPoints();
                            //float mLenght = arrayList.get(0).getLength();
                            float mLenght = routeEvent.get(0).getDistance();
                            //Toast.makeText(m_activity.getApplicationContext(), routeEvent.get(0).getType() + " " + routeEvent.get(0).getDistance(), Toast.LENGTH_SHORT).show();

                            //m_guiProcessor.showDirectionNotification(arrayList.get(0), currentPositionX, currentPositionY, currentTargetX, currentTargetY);
                            RoutePath routePath = arrayList.get(0);
                            float tempLenght = routePath.getLength();

                            directionText.setText(routePath.getEvents().get(0).getType().toString());
                            lenghtText.setText(routePath.getEvents().get(0).getDistance() + "_");
                            curPosText.setText(currentPositionX + "__" + currentPositionY);
                            destPosText.setText(currentTargetX + "__" + currentTargetY);
                        }
                        //}
                    }
                });
                //setLocationTarget(mVenusNames.get(0));
            }

            @Override
            public void onDownloadProgress(int i, int i1) {

            }

            @Override
            public void onLocationFailed(Error error) {
                Toast.makeText(getApplicationContext(), "failed load location", Toast.LENGTH_LONG).show();
            }
        };
        mLocationManager.addLocationListener(mLocationListener);
        mLocationManager.setLocationId(LOCATION_ID);

    }

    public void setLocationTarget(){
        LocationPoint mLocationPoint = new LocationPoint(getProductPoint("Grocery1"), LOCATION_ID, SUBLOCATION_ID);
        if(routeManager != null)
            routeManager.addTarget(mLocationPoint);
        //Toast.makeText(getApplicationContext(), "Set target" + mLocationPoint.getPoint().getX() + "  " + mLocationPoint.getPoint().getY(), Toast.LENGTH_LONG).show();
        currentTargetX = String.valueOf(mLocationPoint.getPoint().getX());
        currentTargetY = String.valueOf(mLocationPoint.getPoint().getY());
    }

    private Point getProductPoint(String rackName){
        Point resultPoint = null;
        for(int k=0; k<mRackList.size(); k++){
            String temp = mRackList.get(k).getName();
            if(temp.equals(rackName)){
                resultPoint = mRackList.get(k).getPoint();
                break;
            }
            //mVenusAliases.add(mVenues.get(k).getAlias());
        }
        return resultPoint;
    }

    private void showDistance(){
        //Handler handler = new Handler();
        handler.postDelayed(runShowDistance, 100);
    }

    private Runnable runShowDistance = new Runnable() {
        @Override
        public void run() {
           /* if(distanceToast!=null)
            {*/
                distanceToast.cancel();
            //}


                        if(isShowToast && curPoint!=null) {

                            LocationPoint mLocationPoint = new LocationPoint(getProductPoint("Grocery2"), LOCATION_ID, SUBLOCATION_ID);
                            Point destPoint = mLocationPoint.getPoint();

                            float distance = GeometryUtils.distanceBetweenPoints(curPoint, destPoint);
                            distanceToast = Toast.makeText(MainActivity.this, "distance = " + distance, Toast.LENGTH_SHORT);
                            distanceToast.show();
                        }
            handler.postDelayed(runShowDistance, 1500);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        mIsGranted = true;
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE: {
                // Check the result of each permission granted.
                if (grantResults != null) {
                    for (int grantResult : grantResults) {
                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
                            mIsGranted = false;
                        }
                    }
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void makeRequestPermissions() {
        // If running on Android 6 (Marshmallow) or above, check to see if the necessary permissions
        // have been granted.

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mIsGranted = hasPermissions(this, REQUIRED_PERMISSIONS);
            if (!mIsGranted) {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
            }
        } else {
            mIsGranted = true;
        }
    }

    private static boolean hasPermissions(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (context.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }
}