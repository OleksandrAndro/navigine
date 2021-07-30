package com.robiretail.robikart.basic;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;

import com.google.gson.Gson;
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
import com.navigine.idl.java.ResourceUploadListener;
import com.navigine.idl.java.RouteEvent;
import com.navigine.idl.java.RouteListener;
import com.navigine.idl.java.RouteManager;
import com.navigine.idl.java.RoutePath;
import com.navigine.idl.java.SensorMeasurement;
import com.navigine.idl.java.Sublocation;
import com.navigine.idl.java.Vector3d;
import com.navigine.idl.java.Venue;
import com.navigine.sdk.Navigine;
import com.robiretail.robikart.LoginWebViewActivity;
import com.robiretail.robikart.MainActivity;
import com.robiretail.robikart.R;
import com.robiretail.robikart.advertisement.AdvPlayer;
import com.robiretail.robikart.cartManagement.ShoppingAssistant;
import com.robiretail.robikart.db.Product;
import com.robiretail.robikart.gui.GuiProcessor;
import com.robiretail.robikart.misc.Misc;
import com.robiretail.robikart.mqtt.MqttWrapper;
import com.robiretail.robikart.network.NetworkApiClient;
import com.robiretail.robikart.network.RobiNetworkCallBack;
import com.robiretail.robikart.networkmodels.BarcodeRequest;
import com.robiretail.robikart.networkmodels.IRobiNetworkError;
import com.robiretail.robikart.networkmodels.NewSessionRequestBody;
import com.robiretail.robikart.sensors.BarQrScanner;
import com.robiretail.robikart.sensors.Brightness;
import com.robiretail.robikart.sensors.Motion;
import com.robiretail.robikart.services.BluetoothService;
import com.robiretail.robikart.services.Usb.UsbProcessor;
import com.robiretail.robikart.services.Usb.UsbService;
import com.robiretail.robikart.streaming.CameraStreamingService;
import com.robiretail.robikart.tensorflow.TFObjectTracker;
import com.robiretail.robikart.util.BeaconsDetails;
import com.robiretail.robikart.util.RobiPreferencesManager;
import com.robiretail.robikart.util.SharedPrefConstants;
import com.robiretail.robikart.util.UpdateCartCallback;
import com.robiretail.robikart.util.UtilClass;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.pushy.sdk.Pushy;

import static com.navigine.idl.java.SensorType.ACCELEROMETER;
import static com.navigine.idl.java.SensorType.GYROSCOPE;
import static com.navigine.idl.java.SensorType.MAGNETOMETER;


/**
 * ProcessManager class.
 */
public final class ProcessManager extends AsyncTask<String, String, String> {
    private final int m_kThreadDelay = 5;                   ///< Process manager's main cycle delay
    ///  in milliseconds.
    ///greetings page
    private final int m_kGreetingsCardId = R.id.welcomeScreenCard;
    public GuiProcessor m_guiProcessor;                    ///< Page selector class object.
    ///  object.
    //  private UserService m_userData;                         ///< User purchases information class
    ///  object.
    public CameraStreamingService m_streamingService;       ///< Camera streaming service class
    public UsbService m_usb;                               ///< USB wrapper class object.
    //private TensorFlowWrapper m_tfLibrary;
    public NetworkApiClient mnetworkservice;
    public MqttWrapper mqttHelper;                          ///<mqtt wrapper class  for topics to be update
    public CardView welcomescreencard;
    public String previousbeaconid = "";
    public AlertDialog internetalert = null;
    public CountDownTimer mcountDownTimer, mSplashScreenTimer;
    DialogBox m_unknownItem = null;                         ///< Unknown product dialog box.
    /* Class fields */
    private String m_kTag;                                  ///< Debug tag.
    private MainActivity m_activity;                        ///< Copy of MainActivity class object.
    private ProcessParameter m_parameter;                   ///< Inter class objects structure.
    private boolean m_isOnWork;                             ///< Manager cycle work state flag.
    private BluetoothService m_btService;                   ///< Bluetooth service.
    private AdvPlayer m_mediaPlayer;                        ///< ExoPlayer wrapper class object.
    public ShoppingAssistant m_shopAssist;
    private BatteryData m_battery;                          ///< Current battery information class
    ///  products.
    ///  object as a part of product
    ///  classifier process.
    private LedController m_led;                            ///< LED commander class object.
    private boolean m_isAliveSent;                          ///< Flag that indicates alive command
    ///  is sent.
    private AudioPlayer m_startSound;                       ///< Audio playback class object.
    //  private Thread m_shutdownThread;                        ///< Shutdown processing thread.
    private String m_incomingData;                          ///< Current USB incoming data.
    private Brightness m_brightSensor;                      ///< Device screen's current brightness.
    private BarQrScanner m_barCodeScanner;                  ///< Current bar code.
    private Motion m_motion;                                ///< Current motion sensor's data.
    private int m_command;                                  ///< Current manager command.
    private int m_amountOfUnscanned = 0;                    ///< Current amount of unscanned
    private UsbProcessor m_usbProcRef = null;
    public TFObjectTracker mtflibrary = null;

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
    ArrayList<Product> tempProductsList = new ArrayList<>();
    String currentPositionX, currentPositionY, currentTargetX, currentTargetY;

    /* Class constructor */

    /**
     * Process manager class constructor.
     *
     * @param activity  mainActivity object.
     * @param parameter inter class objects structure.
     */
    public ProcessManager(@NonNull MainActivity activity, ProcessParameter parameter, UpdateCartCallback updateCartCallback) {
        // class field initialization
        this.m_activity = activity;
        this.m_kTag = activity.getString(R.string.procManTagName);
        this.m_parameter = parameter;
       /* this.m_streamingService = new CameraStreamingService(activity.getApplicationContext(),
            activity,
            this.m_parameter.getWOWZCameraview());*/
//        this.m_btService = new BluetoothService(activity.getApplicationContext(), true);
        this.m_startSound = new AudioPlayer();
        this.mnetworkservice = new NetworkApiClient(activity.getApplicationContext(), this);
        this.m_usb = new UsbService(activity);
        this.m_shopAssist = new ShoppingAssistant(m_activity, mnetworkservice, m_startSound);
        //    this.m_userData = new UserService(activity);
        this.m_isAliveSent = false;
        this.m_led = new LedController(this.m_usb);
        this.m_guiProcessor = new GuiProcessor(activity,
            activity.getApplicationContext(),
            updateCartCallback, this.m_led);

//MP    this.m_mediaPlayer = new AdvPlayer(activity);
        this.m_incomingData = "";
        this.m_battery = new BatteryData();
        this.m_brightSensor = new Brightness();
        this.m_motion = new Motion();
        this.m_barCodeScanner = new BarQrScanner();

        // Init play list request at beginning.

        // download offline videos
        //      runBackgroundService(OfflineVideoDownloader.class);
        // Start basic fields to make thread works properly.
        this.m_isOnWork = true;
        m_command = Misc.m_kNoCommand;
        //  this.m_shopAssist = new ShoppingAssistant(activity);
        android.util.Log.d("ProcessManager", "Application is already started.");
        getMacAddr();
        Log.d(m_kTag, "Device mac:" + getMacAddr());
        m_activity.appendToLog("ProcessManager get DeviceMac " + getMacAddr(), 2);
        pushyTokenRegistration();
        initNavigation();
    }

    private void initNavigation(){

        Navigine.initialize(m_activity.getApplicationContext());
        NavigineSdk.setUserHash(USER_HASH); // your user hash from the server
        NavigineSdk.setServer(SERVER_URL); // your server url (by default `https://api.navigine.com`)
        mNavigineSdk = NavigineSdk.getInstance();
        mLocationManager = mNavigineSdk.getLocationManager();
        mMeasurementManager = mNavigineSdk.getMeasurementManager();
        mResourceManager = mNavigineSdk.getResourceManager(mLocationManager);
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationLoaded(Location location) {
                Toast.makeText(m_activity.getApplicationContext(), "yahoo", Toast.LENGTH_LONG).show();
                mSubLocation = location.getSublocations().get(0);
                mRackList = mSubLocation.getVenues();             //fill rackList
                for(int k=0; k<mRackList.size(); k++){
                    String temp = mRackList.get(k).getName();
                    mVenusNames.add(temp);                          //fill rackName list
                    //mVenusAliases.add(mVenues.get(k).getAlias());
                }
                navigationManager = mNavigineSdk.getNavigationManager(mLocationManager);
                navigationManager.addPositionListener(new PositionListener() {
                    @Override
                    public void onPositionUpdated(Position position) {
                        //Toast.makeText(m_activity.getApplicationContext(), position.getPoint().getX() + " " +position.getPoint().getY(), Toast.LENGTH_SHORT).show();
                        currentPositionX = String.valueOf(position.getPoint().getX());
                        currentPositionY = String.valueOf(position.getPoint().getY());
                    }

                    @Override
                    public void onPositionError(Error error) {

                    }
                });
                navigationManager.startLogRecording();
                routeManager = mNavigineSdk.getRouteManager(mLocationManager, navigationManager);
                routeManager.addRouteListener(new RouteListener() {
                    @Override
                    public void onPathsUpdated(ArrayList<RoutePath> arrayList) {
                        //for(int i = 0; i<arrayList.size(); i++) {
                        if(arrayList.size() != 0) {
                            ArrayList<RouteEvent> routeEvent = arrayList.get(0).getEvents();
                            ArrayList<LocationPoint> locationPoints = arrayList.get(0).getPoints();
                            float mLenght = arrayList.get(0).getLength();
                            //Toast.makeText(m_activity.getApplicationContext(), routeEvent.get(0).getType() + " " + routeEvent.get(0).getDistance(), Toast.LENGTH_SHORT).show();

                            m_guiProcessor.showDirectionNotification(arrayList.get(0), currentPositionX, currentPositionY, currentTargetX, currentTargetY);
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
                Toast.makeText(m_activity.getApplicationContext(), "failed load location", Toast.LENGTH_LONG).show();
            }
        };
        mLocationManager.addLocationListener(mLocationListener);
        mLocationManager.setLocationId(LOCATION_ID);

    }

    public void sendAMG_data(int sensorType, float x, float y, float z){
        if (mMeasurementManager == null)
            return;
        Vector3d values = new Vector3d(x, y, z);
        switch (sensorType){
            case (1):
                mMeasurementManager.addExternalSensorMeasurement(new SensorMeasurement(ACCELEROMETER, values));
                break;
            case (2):
                mMeasurementManager.addExternalSensorMeasurement(new SensorMeasurement(GYROSCOPE, values));
                break;
            case (3):
                mMeasurementManager.addExternalSensorMeasurement(new SensorMeasurement(MAGNETOMETER, values));
                break;
        }
    }

    public void setLocationTarget(Product product){
        LocationPoint mLocationPoint = new LocationPoint(getProductPoint(product.rackName), LOCATION_ID, SUBLOCATION_ID);
        if(routeManager != null)
        routeManager.addTarget(mLocationPoint);
        Toast.makeText(m_activity.getApplicationContext(), "Set target" + mLocationPoint.getPoint().getX() + "  " + mLocationPoint.getPoint().getY(), Toast.LENGTH_LONG).show();
        currentTargetX = String.valueOf(mLocationPoint.getPoint().getX());
        currentTargetY = String.valueOf(mLocationPoint.getPoint().getY());
    }

    public void initUSB(){
        //test block from Oleksandr(need to remove then)
        m_usbProcRef = AppAssistant.getUsbProcRef();

        Log.d(m_kTag, "processmanager we received pushy through eventbus");
        // Send "Start session" command.
        if (m_usbProcRef != null) {
            m_usbProcRef.sessionStateCommand(true);
            if (guiGetPageState())
                guiSelectPage(Misc.m_kNewGreatingsPageId);

        } else {
            Log.e(m_kTag, "USB PROCESSOR REFERENCE IS NULL");
        }
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

    public void stopSession(){
        if (m_usbProcRef != null) {
            m_usbProcRef.sessionStateCommand(false);
            m_usbProcRef.enableAMG(false);
            if (guiGetPageState())
                guiSelectPage(Misc.m_PageThankYou);
        } else {
            Log.e(m_kTag, "USB PROCESSOR REFERENCE IS NULL");
        }
        guiSelectPage(9);
        startSplashScreen();
        navigationManager.stopLogRecording();
        ArrayList<String> logList = mResourceManager.getLogsList();
        String lastFile = logList.get(logList.size()-1);
        mResourceManager.uploadLogFile(lastFile, new ResourceUploadListener() {
            @Override
            public void onUploaded() {

            }

            @Override
            public void onFailed(Error error) {

            }
        });
    }

    public void enablePeripherals(){
        if (m_usbProcRef != null) {
            m_usbProcRef.enableBarcodeCommand(true);
        } else {
            Log.e(m_kTag, "USB PROCESSOR REFERENCE IS NULL");
        }
    }

    public void showToLog(String log){
        m_activity.appendToLog(log, 2);
    }


    private void pushyTokenRegistration() {

        if (UtilClass.checkInternet(m_activity)) {
            /// internet is available go ahead further
            // Not registered yet?
            if (getDeviceToken() == null) {
                // Register with Pushy
                m_activity.appendToLog("Registering new token ", 2);
                new RegisterForPushNotificationsAsync().execute();
            } else {
                Log.d(m_kTag, "Pushy device id:::  " + getDeviceToken());
                m_activity.appendToLog("SavedToken " + getDeviceToken() + " Starting Pushy notification ", 2);
                // Start Pushy notification service if not already running
                Pushy.listen(m_activity);
            }
            clearlogintimer();
        } else {

            if (internetalert != null) {
                UtilClass.hideAlertDialog(internetalert);
            }
            internetalert = UtilClass.showAlertDialog(m_activity);
            //}
            startlogintimer();
        }
    }


    private void clearlogintimer() {
        if (mcountDownTimer != null) {
            mcountDownTimer.cancel();
        }
        if (internetalert != null && internetalert.isShowing()) {
            internetalert.dismiss();
        }
    }

    private void startlogintimer() {
        mcountDownTimer = new CountDownTimer(3000, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onFinish() {
                // TODO Auto-generated method stub

                exoStopVideo(true, false);
                pushyTokenRegistration();
            }
        }.start();
    }

    private void startSplashScreen() {
        mSplashScreenTimer = new CountDownTimer(20000, 5000) {

            @Override
            public void onTick(long millisUntilFinished) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onFinish() {
                // TODO Auto-generated method stub

                //  exoStopVideo(true, false);
                // pushyTokenRegistration();
                guiSelectPage(7);
                if (mSplashScreenTimer != null) {
                    mSplashScreenTimer.cancel();
                }

                m_activity.resetUserInfo();
            }
        }.start();
    }

   /* private void clearSplashScreenTimer() {
        if (mSplashScreenTimer != null) {
            mSplashScreenTimer.cancel();
        }

    }*/


    /* Override methods */

    /**
     * Pre execution method.
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        EventBus.getDefault().register(this);

        Misc.debugLog(m_kTag, Misc.getStr(R.string.procManStarted));
    }

    /**
     * Main AsyncTask method.
     *
     * @param strings input strings array.
     * @return always return 'null' object reference.
     */
    @Override
    protected String doInBackground(String... strings) {

        Log.i(m_kTag, "PM inside do in background");

        Misc.makeDelay(m_kThreadDelay);
        //   }

        return null;
    }

    /**
     * UI update method.
     *
     * @param values array of strings.
     */
    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
    }

    /**
     * Post running method on thread closing.
     *
     * @param s Unused.
     */
    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
        Misc.debugLog(m_kTag, Misc.getStr(R.string.procManClosed));
    }

    /**
     * once the push notofication is received i.e a device is alloted for the customer through
     * device manager from dashboard then we need to make a start session call
     *
     * @param pushymessage
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(NewSessionRequestBody pushymessage) {
        m_usbProcRef = AppAssistant.getUsbProcRef();
        Log.d(m_kTag, "processmanager we received pushy through eventbus");
        m_activity.appendToLog("Processmanager: we received pushy through eventbus ", 2);
        if (pushymessage.getSessionActive()) {
            m_activity.appendToLog("Processmanager: we received pushy StartSession ", 2);
            // Send "Start session" command.
            if (m_usbProcRef != null) {
                m_usbProcRef.sessionStateCommand(true);
            } else {
                Log.e(m_kTag, "USB PROCESSOR REFERENCE IS NULL");
            }
            m_activity.updateUserInfo(pushymessage);
            m_guiProcessor.selectRenderPageToBuild(new Gson().toJson(pushymessage), 8);
            new CountDownTimer(1000, 1000){
                @Override
                public void onTick(long l) {
                }

                @Override
                public void onFinish() {
                    if (m_usbProcRef != null) {
                        m_usbProcRef.enableBarcodeCommand(true);
                        //m_usbProcRef.enableAMG(true);
                    } else {
                        Log.e(m_kTag, "USB PROCESSOR REFERENCE IS NULL");
                    }
                }
            }.start();

            ///start what ever may be the threads after login successful i.e new Session is created

            // Start detection of the beacons.
//            runBackgroundService(BeaconsService.class);
            // start camera streaming...
            // startLiveStreaming();

            // startMqtt();
            mtflibrary = new TFObjectTracker(m_activity, m_shopAssist);
            //MP           m_mediaPlayer.resetDefaultPlaylist();
            //   m_activity.reloadActivityTimer(true);
            //  exoStartVideo();
            m_shopAssist.mBarcodeList.clear();
            m_shopAssist.temp_barcodeRequest = new BarcodeRequest();

            //if splashScreen timer is started - cancel it
            if (mSplashScreenTimer != null) {
                mSplashScreenTimer.cancel();
            }
        } else {
            m_activity.appendToLog("Processmanager: we received pushy StopSession ", 2);
            String pushytoken = RobiPreferencesManager.getInstance().getStringValue(SharedPrefConstants.PUSHYDEVICETOKEN, "");
            RobiPreferencesManager.getInstance().clear();
            RobiPreferencesManager.getInstance().setValue(SharedPrefConstants.PUSHYDEVICETOKEN, pushytoken);
            m_activity.updateUserInfo(pushymessage);
            guiSelectPage(9);
//            stopBackgroundService(BeaconsService.class);
            // stop camera streaming...
              // stopLiveStreaming();
            //stopmqtt();
//            exoStopVideo(true, true);
            m_activity.stopTimer();
            ///my comment
            if (mtflibrary != null) {
                mtflibrary.onPause();
            }
            m_shopAssist.temp_barcodeRequest = null;
            m_shopAssist.temp_Upc = "";


            // Send "Stop session" command.
            if (m_usbProcRef != null) {
                m_usbProcRef.sessionStateCommand(false);
                //m_usbProcRef.enableAMG(false);
            } else {
                Log.e(m_kTag, "USB PROCESSOR REFERENCE IS NULL");
            }

            startSplashScreen();
        }
    }


    ///test method from Oleksandr
    public void fullyStartSimulation(ArrayList<Product> shoppingList){
        tempProductsList = shoppingList;
        initUSB();
        new CountDownTimer(1000, 1000){

            @Override
            public void onTick(long l) {

            }

            @Override
            public void onFinish() {
                if (m_usbProcRef != null) {
                    //m_usbProcRef.enableBarcodeCommand(true);
                    m_usbProcRef.enableAMG(true);
                    if(!tempProductsList.isEmpty())
                    setLocationTarget(tempProductsList.get(0));   //set first location target
                } else {
                    Log.e(m_kTag, "USB PROCESSOR REFERENCE IS NULL");
                }
            }
        }.start();
       // startLiveStreaming();
        mtflibrary = new TFObjectTracker(m_activity, m_shopAssist);
    }

    //test method from Oleksandr
    public void fullyStopSimulation(){
        stopSession();
       // stopLiveStreaming();
        if (mtflibrary != null) {
            mtflibrary.onPause();
        }
        m_shopAssist.temp_barcodeRequest = null;
        m_shopAssist.temp_Upc = "";
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BeaconsDetails beaconsDetails) {
        Log.i(m_kTag, "inisde of Pm on Event Bus  method uuid:::" + beaconsDetails.getUuid());
        String l_uuid = beaconsDetails.getUuid();
        if (!l_uuid.equals("")) {
            //  previousbeaconid = RobiPreferencesManager.getInstance().getStringValue(SharedPrefConstants.BEACONID, "");
            //   if (previousbeaconid != l_uuid) {
//MP   m_mediaPlayer.getBeaconsPlaylist(l_uuid);   ///"32583660-efd5-4846-afd2-98dcc93276cc"
            //   }
            //   previousbeaconid = l_uuid;
            //  RobiPreferencesManager.getInstance().setValue(SharedPrefConstants.BEACONID, l_uuid);
            //  RobiPreferencesManager.getInstance().setValue(SharedPrefConstants.BEACONUPDATED, false);
        }

    }


    /**
     * Thread cancellation method.
     */
    @Override
    protected void onCancelled() {
        super.onCancelled();

        EventBus.getDefault().unregister(this);

        m_usb.closePort();
        Misc.debugLog(Misc.getStr(R.string.mainActDebugTag),
            Misc.getStr(R.string.procManUsbClosed));
        //      m_requestService.close();
        Misc.debugLog(Misc.getStr(R.string.mainActDebugTag),
            Misc.getStr(R.string.procManRequestClosed));
    }

    /* User methods */

    public void setIncomingUsbData(String data) {
        this.m_incomingData = data;
    }

    public boolean getIsOnWorkFlag() {
        return this.m_isOnWork;
    }

    /**
     * Process manager's objects launch method.
     */
    public final void run() {
        //  m_btService.execute();
        //      m_mediaPlayer.init(m_parameter.getMediaPlayer());
        m_startSound.execute(R.raw.welcome_sound);
        // m_activity.reloadActivityTimer(true);
        // m_activity.setTimerDelay(50000);
        //      exoStopVideo(true, true);
        // Start all basic threads.
        executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        this.m_shopAssist.start();

    }

    /**
     * Process state changing method. Can handle main cycle closing.
     *
     * @param state Incoming new state.
     */
    public void setWorkState(boolean state) {
        this.m_isOnWork = state;
    }

    /**
     * Set current command to be used in next iteration.
     *
     * @param command new command code.
     */
    public void setCommand(int command) {
        this.m_command = command;
    }

    /**
     * Launch toast message.
     *
     * @param message Text of message.
     */
    public void message(String message) {
        Toast.makeText(m_activity, message, Toast.LENGTH_SHORT).show();
    }

    /* GuiProcessor management methods */

    /**
     * Page selecting method according to incoming server response.
     *
     * @param index New page index.
     */
    public final void guiSelectPage(int index) {
        m_guiProcessor.mPageController.selectPage(index);
    }

    /**
     * Get state of GuiProcessor object init state.
     *
     * @return false - GuiProcessor object isn't init,
     * true - successful init
     */
    public boolean guiGetPageState() {
        return m_guiProcessor.pageGetState();
    }


    /**
     * Motion data sending method.
     */
    private void sendMotionData() {
        if (m_motion.getDirection() == -1) {
            m_streamingService.sendMotionSensorData("onMotionSensorEnd",
                Float.toString(m_motion.getDistance()),
                "MOTION",
                "-",
                barGetCurrentUpc(),
                AppAssistant.getBeaconUuid());
        } else {
            m_streamingService.sendMotionSensorData("onMotionSensorStart",
                Float.toString(m_motion.getDistance()),
                "MOTION",
                Misc.m_kDirections[m_motion.getDirection()],
                barGetCurrentUpc(),
                AppAssistant.getBeaconUuid());
        }
    }

    /**
     * Class service string method.
     *
     * @param service Name of service class need to be started.
     */
    private void runBackgroundService(Class service) {
        Intent l_intent = new Intent(m_activity, service);
        m_activity.startService(l_intent);
        Misc.debugLog(service.getName(), Misc.getStr(R.string.procManStartedService));
    }

    /**
     * Class service string method.
     *
     * @param service Name of service class need to be stop.
     */
    private void stopBackgroundService(Class service) {
        Intent l_intent = new Intent(m_activity, service);
        m_activity.stopService(l_intent);

        Misc.debugLog(service.getName(), Misc.getStr(R.string.procManStopService));
    }

    /**
     * Start ExoPlayer video playback.
     */
    public void exoStartVideo() {
//        m_mediaPlayer.startVideo();
    }

    /**
     * ExoPlayer playback stopping.
     *
     * @param needToHide    decides to hide player view layer.
     * @param needFromBegin decides to start next playback from beginning.
     */
    public void exoStopVideo(boolean needToHide, boolean needFromBegin) {
//MP      m_mediaPlayer.stopVideo(needToHide, needFromBegin);

    }

    /* CameraStreaming public method */

    /**
     * Camera streaming execution stopping.
     */
    public void stopLiveStreaming() {
        if (m_streamingService != null) {
            m_streamingService.stopStreaming();
        }
    }

    /**
     * Start Camera Live streaming.
     */
    //private void startLiveStreaming() {
        public void startLiveStreaming() {
        m_streamingService.startStreaming();
    }


    public void requestBarcodeStop() {
       /* if (this.m_barcodeRequest != null) {
            this.m_barcodeRequest.close();
            this.m_barcodeRequest.cancel(true);
            this.m_barcodeRequest = null;
        }*/
    }


    public void disbaleWelcomScreenCard(boolean isShow) {
        welcomescreencard = m_activity.findViewById(m_kGreetingsCardId);
        welcomescreencard.setVisibility(isShow ? View.VISIBLE : View.INVISIBLE);
    }


    public void enableadds() {
        //MP  m_activity.reloadActivityTimer(false);
    }


    /* BarCode scanner public method */
    private String barGetCurrentUpc() {
        return m_barCodeScanner.getCode();
    }

    /* MotionSensor public method */

    public int motionGetDirection() {
        return m_motion.getDirection();
    }


    /**
     * pushy notifcations methods
     */

    private String getDeviceToken() {
        // Get token stored in SharedPreferences
        return RobiPreferencesManager.getInstance().getStringValue(SharedPrefConstants.PUSHYDEVICETOKEN, null);
    }

    private void saveDeviceToken(String deviceToken) {
        // Save token locally in app SharedPreferences

        RobiPreferencesManager.getInstance().setValue(SharedPrefConstants.PUSHYDEVICETOKEN, deviceToken);

        // Your app should store the device token in your backend database
        //new URL("https://{YOUR_API_HOSTNAME}/register/device?token=" + deviceToken).openConnection();

        NewSessionRequestBody requestBody = new NewSessionRequestBody();
        requestBody.setDeviceId(getMacAddr());
        requestBody.setPushToken(deviceToken);
        m_activity.appendToLog("Sending mac adress to server " + getMacAddr() + "  Sending token to server " + deviceToken, 2);
        NetworkApiClient.getInstance().registerForPushNotifications(requestBody, new RobiNetworkCallBack<NewSessionRequestBody, IRobiNetworkError>() {

            @Override
            public void onSuccessResponse(NewSessionRequestBody sessionRequestBody) {

            }

            @Override
            public void onErrorResponse(IRobiNetworkError iRobiNetworkError) {

            }
        });

    }

    private String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02x:", b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
        }
        return "02:00:00:00:00:00";
    }

    private class RegisterForPushNotificationsAsync extends AsyncTask<String, Void, Exception> {
        ProgressDialog mLoading;

        public RegisterForPushNotificationsAsync() {
            // Create progress dialog and set it up
            mLoading = new ProgressDialog(m_activity);
            mLoading.setMessage("Registering Device...");
            mLoading.setCancelable(false);

            // Show it
            mLoading.show();
        }

        @Override
        protected Exception doInBackground(String... params) {
            try {
                // Assign a unique token to this device
                String deviceToken = Pushy.register(m_activity);

                // Save token locally / remotely
                saveDeviceToken(deviceToken);
            } catch (Exception exc) {
                // Return exc to onPostExecute
                return exc;
            }

            // Success
            return null;
        }

        @Override
        protected void onPostExecute(Exception exc) {
            // Activity died?
            if (m_activity.isFinishing()) {
                return;
            }

            // Hide progress bar
            mLoading.dismiss();

            // Registration failed?
            if (exc != null) {
                // Write error to logcat
                Log.e("Pushy", "Registration failed: " + exc.getMessage());

                // Display error dialog
                Toast.makeText(m_activity, "Pushy Registration failed", Toast.LENGTH_SHORT).show();
            }

        }
    }


    /**
     * mqtt server details

     */


  /* public void startMqtt()
   {
       mqttHelper = new MqttWrapper(m_activity.getApplicationContext());
       mqttHelper.setCallback(new MqttCallbackExtended() {
           @Override
           public void connectComplete(boolean reconnect, String serverURI) {

               Log.e(m_kTag,"Mqtt connectComplete()");
           }

           @Override
           public void connectionLost(Throwable cause) {

               Log.e(m_kTag,"Mqtt connectionLost()");
           }

           @Override
           public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {

               String urltype="";
               Log.i(m_kTag,"message arirved::"+mqttMessage.toString());

               Log.i(m_kTag,"topic::"+topic);

               //filter based on the topic types
               String[] aas = topic.split("/");

               Log.i(m_kTag,"string0  "+aas[0]);
               Log.i(m_kTag,"string1  "+aas[1]);
               switch (aas[1])
               {
                   case Misc.m_kOffersPagetype:
                       urltype=Misc.m_kBasicUrl+"offers?transactionId=";
                       break;
                   case Misc.m_kSearchPagetype:
                       urltype=Misc.m_kBasicUrl+"products?productIds=chocolate&transactionId=";
                       break;
                   case Misc.m_kShoppingListPagetype:
                       urltype=Misc.m_kBasicUrl+"shoppingList?userId=";
                       break;
                   case Misc.m_kShoppingCartPagetype:
                       urltype=Misc.m_kBasicUrl+"transaction?transactionId=";
                       break;
                   case Misc.m_kReceipePagetype:
                //       urltype=Misc.m_kBasicUrl+"offers?transactionId=";
                       break;
                   case Misc.m_knNewSessionPagetype:
                     //  urltype=Misc.m_kBasicUrl+"offers?transactionId=";
                       //mnetworkservice.startNewSessionRequest("");
                       break;
                       default:
                           break;
               }

               Toast.makeText(m_activity,""+mqttMessage.toString(),Toast.LENGTH_SHORT).show();

          mnetworkservice.getPageResponse(urltype);

           }

           @Override
           public void deliveryComplete(IMqttDeliveryToken token) {

               android.util.Log.d(m_kTag,"Mqtt deliveryComplete()");
           }
       });

   }*/


/*
  public void untittestbarcode(){
      new CountDownTimer(5000, 1000) {
          @Override
          public void onTick(long millisUntilFinished) {

          }

          @Override
          public void onFinish() {


              String l_upc ="852709002034";
              final BarcodeValue l_barcode = new BarcodeValue();
              l_barcode.setDirection(Misc.m_kDirections[4]);
              l_barcode.setQuantity("1");
              l_barcode.setUpc(l_upc);
              l_barcode.setTimestamp(String.valueOf(System.currentTimeMillis()));

              android.util.Log.i(m_kTag, "mmk scanned barcode upc::" + l_upc + ":: Direction::" + l_barcode.getDirection());

              // Add new product info to the array.
              mBarcodeList.add(l_barcode);

              mBrcPrerequestBody.setValues(mBarcodeList);

              //on after 5 seconds
              mnetworkservice.processBarcodeScanningInfo(new RobiNetworkCallBack<BarcodeResponse, IRobiNetworkError>() {
                  @Override
                  public void onSuccessResponse(BarcodeResponse barcodeResponse) {

                      android.util.Log.i(m_kTag, "mmk onSuccessResponse:: of barcode request::" + barcodeResponse.getResponseType());

                      if (!barcodeResponse.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType3)) {


                          android.util.Log.i(m_kTag, "mmk onSuccessResponse upc ::" + barcodeResponse.getProductDetails().getUpc());
                      }
                  }

                  @Override
                  public void onErrorResponse(IRobiNetworkError iRobiNetworkError) {
                      android.util.Log.i(m_kTag, "mmk onErrorResponse:: of barcode request::" + iRobiNetworkError.getErrorResponse());
                  }
              }, mBrcPrerequestBody);

          }
      }.start();
  }*/
}