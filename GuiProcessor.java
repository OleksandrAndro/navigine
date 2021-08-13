package com.robiretail.robikart.gui;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.navigine.idl.java.RouteEvent;
import com.navigine.idl.java.RouteEventType;
import com.navigine.idl.java.RoutePath;
import com.robiretail.robikart.MainActivity;
import com.robiretail.robikart.R;
import com.robiretail.robikart.basic.AudioPlayer;
import com.robiretail.robikart.basic.LedController;
import com.robiretail.robikart.gui.items.detected_product.DetectedItem;
import com.robiretail.robikart.gui.pages.PageBuilder;
import com.robiretail.robikart.gui.pages.PageController;
import com.robiretail.robikart.misc.Misc;
import com.robiretail.robikart.networkmodels.BarcodeResponse;
import com.robiretail.robikart.networkmodels.Footer;
import com.robiretail.robikart.networkmodels.ProductDetails;
import com.robiretail.robikart.util.UpdateCartCallback;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * GuiProcessor class.
 */
public final class GuiProcessor {

    private static final String TAG = GuiProcessor.class.getSimpleName(); ///< Class tag for using in logging.
    private MainActivity m_activity;             ///< Application main activity.
    private Context mAppContext;                ///< Application GUI context.
    private UpdateCartCallback mUpdateCartCallback;            ///< Callback to MainActivity
    public PageController mPageController;      ///< Page flipper creator.
    public PageBuilder mPageBuilder;           ///< Pages builder.
    private AudioPlayer mBeepSound;
    private LedController m_led;
    private MediaPlayer m;
    Toast toastDirection;

    private SoundPool mSoundPool, mSoundPoolIn, mSoundPoolOut, mSoundPoolError, mSoundPoolUnknown;
    float curVolume, maxVolume, leftVolume, rightVolume, leftVolumeHight, rightVolumeHight;
    int priority, no_loop;
    private int mSoundId = 1;
    int mStreamId;

    /* Class constructor */

    /**
     * User-defined constructor.
     *
     * @param activity           MainActivity object copy.
     * @param context            application context need to be used in GUI operations.
     * @param updateCartCallback
     */
    public GuiProcessor(MainActivity activity, Context context, UpdateCartCallback updateCartCallback,
                        LedController led) {
        this.m_activity = activity;
        this.mAppContext = context;
        this.m_led = led;
        this.mUpdateCartCallback = updateCartCallback;
        this.mPageController = new PageController(m_activity, this.mAppContext);
        this.mPageBuilder = new PageBuilder(m_activity);
        this.mBeepSound = new AudioPlayer();
        //mBeepSound.execute(R.raw.price_check);
        android.util.Log.d("Thread priority", "GuiProcessor: " + Thread.currentThread().getId());
        android.util.Log.d("StartupLog", "GuiProcessor was already started.");


        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

        mSoundPool = new SoundPool.Builder()
            .setAudioAttributes(attributes)
            .build();

        mSoundPoolIn = new SoundPool.Builder()
            .setAudioAttributes(attributes)
            .build();

        mSoundPoolOut = new SoundPool.Builder()
            .setAudioAttributes(attributes)
            .build();

        mSoundPoolError = new SoundPool.Builder()
            .setAudioAttributes(attributes)
            .build();

        mSoundPoolUnknown = new SoundPool.Builder()
            .setAudioAttributes(attributes)
            .build();

        mSoundPool.load(m_activity.getApplicationContext(), R.raw.price_check, 1);
        mSoundPoolIn.load(m_activity.getApplicationContext(), R.raw.notification, 1);
        mSoundPoolOut.load(m_activity.getApplicationContext(), R.raw.item_out1, 1);
        mSoundPoolError.load(m_activity.getApplicationContext(), R.raw.item_error, 1);
        mSoundPoolUnknown.load(m_activity.getApplicationContext(), R.raw.unknown_item, 1);

        /*mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int i, int i1) {
                mSoundPool.play(mSoundId, 0, 0, 0, 0, 1f);
            }
        });*/

        AudioManager audioManager = (AudioManager) m_activity.getSystemService(Context.AUDIO_SERVICE);
        curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        leftVolume = curVolume / maxVolume;
        rightVolume = curVolume / maxVolume;
        leftVolumeHight = 0.8f;
        rightVolumeHight = 0.8f;
        priority = 1;
        no_loop = 0;
    }

    /* Class methods */


    public void selectRenderPageToBuild(String l_httpresponse, int l_pageindex) {
        if (l_pageindex == 7) { // In case, when need to pop up product notification.
            showBarcodeNotification(l_httpresponse, this.m_led);
        } else {
            mPageBuilder.buildPage(l_httpresponse, l_pageindex);
            mPageController.selectPage(l_pageindex);
        }
    }

    /**
     * Current page state.
     *
     * @return page state.
     */
    public boolean pageGetState() {
        return mPageController.getState();
    }

    /**
     * Build side notification for detected/unknown product(s).
     *
     * @param response detected products notification.
     * @return array of detected items.
     */
    private ArrayList<DetectedItem> buildDetectedProductPage(String response) {
        ArrayList<DetectedItem> l_detectedItems = new ArrayList<>();
        JSONObject l_jsonObject;
        JSONObject l_innerObject;
        DetectedItem l_tempItem;
        l_detectedItems.clear();
        // parse response
        try {
            l_tempItem = new DetectedItem();
            l_jsonObject = new JSONObject(response);
            l_innerObject = l_jsonObject.getJSONObject(
                m_activity.getString(R.string.guiProductDetails));
            try {
                l_tempItem.setName(l_innerObject.getString(
                    m_activity.getString(R.string.guiProductName)));
            } catch (JSONException je) {
                je.printStackTrace();
                Log.e(TAG, m_activity.getString(R.string.guiEmptyDetectedName));
            }
            try {
                l_tempItem.setPrice(l_innerObject.getString(
                    m_activity.getString(R.string.guiPrice)));
            } catch (JSONException je) {
                je.printStackTrace();
                Log.e(TAG, m_activity.getString(R.string.guiEmptyDetectedPrice));
            }
            try {
                l_tempItem.setUpc(l_innerObject.getString(m_activity.getString(R.string.guiUpc)));
            } catch (JSONException je) {
                je.printStackTrace();
                Log.e(TAG, m_activity.getString(R.string.guiEmptyDetectedUpc));
            }
            try {
                l_tempItem.setInfo(l_innerObject.getString(
                    m_activity.getString(R.string.guiDescription)));
            } catch (JSONException je) {
                je.printStackTrace();
                Log.e(TAG, m_activity.getString(R.string.guiEmptyDetectedInfo));
            }
            try {
                l_tempItem.setImage(l_innerObject.getString(
                    m_activity.getString(R.string.guiLargeImage)));
            } catch (JSONException je) {
                je.printStackTrace();
                Log.e(TAG, m_activity.getString(R.string.guiEmptyDetectedImage));
            }
            try {
                l_tempItem.setRatings(l_innerObject.getString(
                    m_activity.getString(R.string.guiRating)));
            } catch (JSONException je) {
                je.printStackTrace();
                Log.e(TAG, m_activity.getString(R.string.guiEmptyDetectedRating));
            }
            try {
                l_tempItem.setResponse_type(l_jsonObject.getString(
                    m_activity.getString(R.string.guiResponse)));
            } catch (JSONException je) {
                je.printStackTrace();
                Log.e(TAG, m_activity.getString(R.string.guiEmptyResponseType));
            }
            l_detectedItems.add(l_tempItem);
        } catch (JSONException je) {
            je.printStackTrace();
            Log.e(TAG, m_activity.getString(R.string.guiJsonError));
        }

        return l_detectedItems;
    }

    private void displayItemImage(BarcodeResponse detectedItem, ImageView itemImage){
        itemImage.setVisibility(View.VISIBLE);
        String imageUrl = detectedItem.getProductDetails().getLargeImage();
        Glide.with(mAppContext)
            .load(imageUrl)
            .into(itemImage);
    }

    private void displayLocalItemImage(BarcodeResponse detectedItem, ImageView itemImage){
        itemImage.setVisibility(View.VISIBLE);
        String imageUri = detectedItem.getProductDetails().getUpc();
        Glide.with(mAppContext)
            .load(Uri.parse("file:///android_asset/" + imageUri+".jpg"))
            .into(itemImage);
    }

    private void displayLocalItemBitmap(BarcodeResponse detectedItem, ImageView itemImage){
        itemImage.setVisibility(View.VISIBLE);
        ProductDetails productDetails = detectedItem.getProductDetails();
        Bitmap bitmap = productDetails.getBitmapImage();
        itemImage.setImageBitmap(bitmap);
    }


    private void showBarcodeNotification(String response, LedController led) {
        LayoutInflater m_inflater = (LayoutInflater) m_activity.
            getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (m_inflater == null) {
            return;
        }
        final View l_layout = m_inflater.inflate(R.layout.product_detected_notification,
            (ViewGroup) m_activity.findViewById(R.id.rl_custom_notification));

        Gson gson = new Gson();
        BarcodeResponse detectedItem = gson.fromJson(response, BarcodeResponse.class);

        ///test block notification cart price
       /* BarcodeResponse detectedItem = new BarcodeResponse();
        ProductDetails productDetails = new ProductDetails();
        productDetails.setCustomerRating("");
        productDetails.setLargeImage("");
        productDetails.setPrice(1.11);
        productDetails.setProductName("test product");
        productDetails.setUpc("test upc");

        detectedItem.setProductDetails(productDetails);
        //detectedItem.setResponseType("priceCheck");
        detectedItem.setResponseType("MalformedUpc");*/


        Log.d(TAG, "mmk inside show barcode notfication::" + detectedItem.getResponseType());

        /// DetectedItem detectedItem = detectedItemsArray.get(0);

        ImageView image = l_layout.findViewById(R.id.nt_p_image);
        ImageView itemImage = l_layout.findViewById(R.id.itemImage);
        TextView upc = l_layout.findViewById(R.id.price_check_upc);
        //  image.setImageResource(R.drawable.android);
        TextView name_txt = l_layout.findViewById(R.id.nt_p_name);
        TextView price_txt = l_layout.findViewById(R.id.nt_p_position);
        TextView quantity_txt = l_layout.findViewById(R.id.nt_p_quantity);

        /*String imageUrl = detectedItem.getProductDetails().getLargeImage();
        Glide.with(mAppContext)
            .load(imageUrl)
            .into(itemImage);*/

        // Price check.
        if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType0)) {

            //displayItemImage(detectedItem, itemImage);
            displayLocalItemImage(detectedItem, itemImage);

            String l_tempName = "" + detectedItem.getProductDetails().getProductName();
            String l_tempPrice = "$" + detectedItem.getProductDetails().getPrice();
            upc.setText(detectedItem.getProductDetails().getUpc());
            image.setImageResource(R.drawable.barcode_edited);
            name_txt.setText(l_tempName);
            name_txt.setVisibility(View.VISIBLE);
            price_txt.setText(l_tempPrice);
            price_txt.setVisibility(View.VISIBLE);
            quantity_txt.setText("1");
            quantity_txt.setVisibility(View.VISIBLE);
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(0, 0, 255, 3500);
                led.blockFlagSetLed();
                //mStreamId = mSoundPoolIn.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
                //playBeep("price_check.mp3");
                //playAssetSound(m_activity.getApplicationContext(), "price_check.mp3");
                //mBeepSound.playSingle(R.raw.price_check);  //instead this play "beep" after usb-event
            }
            Log.d(TAG, "inside of eleseif Pricecheck product type");
            showToast(l_layout);
        }
        // Product is added.
       /* else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType1)) {
            displayItemImage(detectedItem, itemImage);
            String l_tempName = "" + detectedItem.getProductDetails().getProductName();
            String l_tempPrice = "$" + detectedItem.getProductDetails().getPrice();
            upc.setText("");
            image.setImageResource(R.drawable.ic_tick_product);
            name_txt.setText(l_tempName);
            name_txt.setVisibility(View.VISIBLE);
            price_txt.setText(l_tempPrice);
            price_txt.setVisibility(View.VISIBLE);
            quantity_txt.setText("1");
            quantity_txt.setVisibility(View.VISIBLE);
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(0, 255, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolIn.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
                //mBeepSound.playSingle(R.raw.notification);
            }
            Log.d(TAG, "inside of eleseif product Added type");
        }*/

        // Product is added local.
        else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType1_1)) {
            //displayLocalItemImage(detectedItem, itemImage);
            displayLocalItemBitmap(detectedItem, itemImage);
            String l_tempName = "" + detectedItem.getProductDetails().getProductName();
            String l_tempPrice = "$" + detectedItem.getProductDetails().getPrice();
            upc.setText("");
            image.setImageResource(R.drawable.ic_tick_product);
            name_txt.setText(l_tempName);
            name_txt.setVisibility(View.VISIBLE);
            price_txt.setText(l_tempPrice);
            price_txt.setVisibility(View.VISIBLE);
            quantity_txt.setText("1");
            quantity_txt.setVisibility(View.VISIBLE);
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(0, 255, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolIn.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
                //mBeepSound.playSingle(R.raw.notification);
            }
            Log.d(TAG, "inside of eleseif product Added type");
            showToast(l_layout);
        }

        /*// product is removed
        else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType2)) {
            displayItemImage(detectedItem, itemImage);
            String l_tempText = "-1";
            String l_tempPrice = "-$" + detectedItem.getProductDetails().getPrice();
            upc.setText("");
            image.setImageResource(R.drawable.ic_product_remove);
            name_txt.setText(detectedItem.getProductDetails().getProductName());
            price_txt.setText(l_tempPrice);
            quantity_txt.setText(l_tempText);
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(255, 0, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolOut.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
                //mBeepSound.playSingle(R.raw.item_out1);
            }
            Log.d(TAG, "inside of eleseif  product removed type");
        }*/

        // product is removed local
        else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType2_1)) {
            //displayLocalItemImage(detectedItem, itemImage);
            displayLocalItemBitmap(detectedItem, itemImage);
            String l_tempText = "-1";
            String l_tempPrice = "-$" + detectedItem.getProductDetails().getPrice();
            upc.setText("");
            image.setImageResource(R.drawable.ic_product_remove);
            name_txt.setText(detectedItem.getProductDetails().getProductName());
            price_txt.setText(l_tempPrice);
            quantity_txt.setText(l_tempText);
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(255, 0, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolOut.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
                //mBeepSound.playSingle(R.raw.item_out1);
            }
            Log.d(TAG, "inside of eleseif  product removed type");
            showToast(l_layout);
        }

        // Unknown product.
        else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType3)) {
            String l_tempValue = "$0.00";
            upc.setText("");
            image.setImageResource(R.drawable.ic_unknown_product);
            name_txt.setText(m_activity.getString(R.string.guiproductnotfound));
            price_txt.setText(l_tempValue);
            quantity_txt.setText("0");
            m_activity.resetUPC();
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(255, 0, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolError.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
                //mBeepSound.playSingle(R.raw.item_error);
            }
            Log.d(TAG,"inside of eleseif unknown product type/Product Not Available");
            showToast(l_layout);
        }
        // The UPC is invalid.
        else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType4)) {
            String l_tempValue = "$0.00";
            upc.setText("");
            image.setImageResource(R.drawable.ic_unknown_product);
            name_txt.setText(m_activity.getString(R.string.guiinvalidupc));
            price_txt.setText(l_tempValue);
            quantity_txt.setText("0");
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(255, 0, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolError.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
               // mBeepSound.playSingle(R.raw.item_error);
            }
            Log.d(TAG, "inside of eleseif Upc Invalid product type");
            showToast(l_layout);
        }
        // Server is unavailable.
       /* else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType5)) {
            String l_tempText = "-1";
            String l_tempPrice = "-$0";
            upc.setText("");
            image.setImageResource(R.drawable.ic_unknown);
            name_txt.setText(m_activity.getString(R.string.guiserverunavailable));
            price_txt.setVisibility(View.GONE);
            quantity_txt.setVisibility(View.GONE);
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(255, 0, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolError.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
                //mBeepSound.playSingle(R.raw.item_error);
            }
            Log.d(TAG, "inside of eleseif Server is unavailable product type");
            showToast(l_layout);
        }*/
        // Unscanned product in.
        /*else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType6)) {
            String l_tempValue = "$0.00";
            upc.setText("");
            image.setImageResource(R.drawable.ic_unknown_product_yellow);
            //name_txt.setTextSize(50);
            name_txt.setText(m_activity.getString(R.string.guiUnScannedIn));
            price_txt.setText(l_tempValue);
            quantity_txt.setText("1");
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(235, 150, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolUnknown.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
            }
            Log.d(TAG, "inside of eleseif Unscanned product type");
        }*/

        // Unscanned product in local
        else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType6_1)) {
            String l_tempValue = "$0.00";
            upc.setText("");
            image.setImageResource(R.drawable.ic_unknown_product_yellow);
            //name_txt.setTextSize(50);
            name_txt.setText(m_activity.getString(R.string.guiUnScannedIn));
            price_txt.setText(l_tempValue);
            quantity_txt.setText("1");
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(235, 150, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolUnknown.play(mSoundId, leftVolumeHight, rightVolumeHight, priority, no_loop, 1f);
            }
            Log.d(TAG, "inside of eleseif Unscanned product type");
            showToast(l_layout);
        }

        /*// Unscanned product out.
        else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType7)) {
            String l_tempValue = "$0.00";
            upc.setText("");
            image.setImageResource(R.drawable.ic_unknown_product_yellow);
           // name_txt.setTextSize(50);
            name_txt.setText(m_activity.getString(R.string.guiUnScannedOut));
            price_txt.setText(l_tempValue);
            quantity_txt.setText("1");
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(235, 150, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolUnknown.play(mSoundId, leftVolume, rightVolume, priority, no_loop, 1f);
            }
            Log.d(TAG, "inside of eleseif Unscanned product type");
        }*/

        // Unscanned product out local
        else if (detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType7_1)) {
            String l_tempValue = "$0.00";
            upc.setText("");
            image.setImageResource(R.drawable.ic_unknown_product_yellow);
            // name_txt.setTextSize(50);
            name_txt.setText(m_activity.getString(R.string.guiUnScannedOut));
            price_txt.setText(l_tempValue);
            quantity_txt.setText("1");
            // Light up LEDs.
            if (led != null) {
                led.setFlagBurnLed(true);
                led.setLed(235, 150, 0, 3500);
                led.blockFlagSetLed();
                mStreamId = mSoundPoolUnknown.play(mSoundId, leftVolumeHight, rightVolumeHight, priority, no_loop, 1f);
            }
            Log.d(TAG, "inside of eleseif Unscanned product type");
            showToast(l_layout);
        }

        // Play notification sound.
        ///mBeepSound.playSingle(R.raw.notification);



     //   Toast.makeText(mAppContext,"hi inside of gui processor",Toast.LENGTH_SHORT).show();

        /**
         * Update the navigation bar cart Details when ever the New product detected notification came
         */

        Footer footer = detectedItem.getFooter();

        ////if(!detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType4))
        if(detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType1) | detectedItem.getResponseType().equalsIgnoreCase(Misc.m_kProductPageType2))
       {
          // m_activity.updatcartInfo(footer.getPremiumMemberPoints(),footer.getSavings(),footer.getCartProductsCount(),footer.getTotalCartValue());
           mUpdateCartCallback.onUpdateCart(footer.getPremiumMemberPoints(),
               footer.getSavings(),
               footer.getCartProductsCount(),
               footer.getTotalCartValue());
       }
    }

    private void showToast(View l_layout){
        // Just makes it faster.
        m_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast toast = new Toast(mAppContext);
                toast.setGravity(Gravity.FILL, 900, 0);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setView(l_layout);
                toast.show();
            }
        });
    }

    public void showDirectionNotification(RoutePath routePath, String currentPositionX, String currentPositionY, String currentTargetX, String currentTargetY) {

        RouteEvent mRouteEvent = routePath.getEvents().get(0);
        RouteEventType mRouteEventType = mRouteEvent.getType();
        float mdistance = mRouteEvent.getDistance();
        String result = String.format("%.3f",mdistance);

        LayoutInflater m_inflater = (LayoutInflater) m_activity.
            getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (m_inflater == null) {
            return;
        }
        final View l_layout = m_inflater.inflate(R.layout.direction_notification,
            (ViewGroup) m_activity.findViewById(R.id.rl_custom_notification));

        ImageView image = l_layout.findViewById(R.id.nt_p_image);
        ImageView itemImage = l_layout.findViewById(R.id.itemImage);
        TextView upc = l_layout.findViewById(R.id.price_check_upc);
        TextView name_txt = l_layout.findViewById(R.id.nt_p_name);
        TextView price_txt = l_layout.findViewById(R.id.nt_p_position);
        TextView target_txt = l_layout.findViewById(R.id.nt_p_target);
        TextView quantity_txt = l_layout.findViewById(R.id.nt_p_quantity);

        final View l_layout_right = m_inflater.inflate(R.layout.direction_notification_right,
            (ViewGroup) m_activity.findViewById(R.id.rl_custom_notification_right));

        ImageView image_right = l_layout_right.findViewById(R.id.nt_p_image);
        ImageView itemImage_right = l_layout_right.findViewById(R.id.itemImage);
        TextView upc_right = l_layout_right.findViewById(R.id.price_check_upc);
        TextView name_txt_right = l_layout_right.findViewById(R.id.nt_p_name);
        TextView price_txt_right = l_layout_right.findViewById(R.id.nt_p_position);
        TextView target_txt_right = l_layout_right.findViewById(R.id.nt_p_target);
        TextView quantity_txt_right = l_layout_right.findViewById(R.id.nt_p_quantity);


        // turn right.
        if (mRouteEventType == RouteEventType.TURN_RIGHT) {

            String l_tempName = "Turn right in";
            String l_tempPrice = "Cur pos x "+ currentPositionX + "\n"+ "Cur pos y "+ currentPositionY;
            String l_tempTarget = "Targ pos x "+ currentTargetX + "\n"+ "Targ pos y "+ currentTargetY;
            upc_right.setText("");
            image_right.setImageResource(R.drawable.right_arrow);
            name_txt_right.setText(l_tempName);
            name_txt_right.setVisibility(View.VISIBLE);
            price_txt_right.setText(l_tempPrice);
            price_txt_right.setVisibility(View.VISIBLE);
            target_txt_right.setVisibility(View.VISIBLE);
            target_txt_right.setText(l_tempTarget);
            quantity_txt_right.setText(result);
            quantity_txt_right.setVisibility(View.VISIBLE);
            // Light up LEDs.
            showToastDirection(l_layout_right, 1);
        }
        // turn left.
        if (mRouteEventType == RouteEventType.TURN_LEFT) {

            String l_tempName = "Turn left in";
            String l_tempPrice = "Cur pos x "+ currentPositionX + "\n"+ "Cur pos y "+ currentPositionY;
            String l_tempTarget = "Targ pos x "+ currentTargetX + "\n" + "Targ pos y "+ currentTargetY;
            upc.setText("");
            image.setImageResource(R.drawable.left_arrow);
            name_txt.setText(l_tempName);
            name_txt.setVisibility(View.VISIBLE);
            price_txt.setText(l_tempPrice);
            price_txt.setVisibility(View.VISIBLE);
            target_txt.setVisibility(View.VISIBLE);
            target_txt.setText(l_tempTarget);
            quantity_txt.setText(result);
            quantity_txt.setVisibility(View.VISIBLE);
            // Light up LEDs.
            showToastDirection(l_layout,0);
        }

        if (mRouteEventType == RouteEventType.TRANSITION) {
            String l_tempName = "Move forward ";
            String l_tempPrice = "Cur pos x "+ currentPositionX + "\n"+ "Cur pos y "+ currentPositionY;
            String l_tempTarget = "Targ pos x "+ currentTargetX + "\n"+ "Targ pos y "+ currentTargetY;
            upc.setText("");
            image.setImageResource(R.drawable.forw_arrow);
            name_txt.setText(l_tempName);
            name_txt.setVisibility(View.VISIBLE);
            price_txt.setText(l_tempPrice);
            price_txt.setVisibility(View.VISIBLE);
            target_txt.setVisibility(View.VISIBLE);
            target_txt.setText(l_tempTarget);
            quantity_txt.setText("");
            quantity_txt.setVisibility(View.VISIBLE);
            // Light up LEDs.
            showToastDirection(l_layout,2);
        }
    }

    private void showToastDirection(View l_layout, int i){
        // Just makes it faster.
        m_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(toastDirection == null || toastDirection.getView().getWindowVisibility() != View.VISIBLE) {
                    ///toastDirection.cancel();
                    toastDirection = new Toast(mAppContext);
                    if(i == 0)
                    {toastDirection.setGravity(Gravity.LEFT, 0, 0);}
                    else if (i==1)
                    {toastDirection.setGravity(Gravity.RIGHT, 50, 0);}
                    else if(i==2){
                        toastDirection.setGravity(Gravity.CENTER, 0, 0);
                    }
                    toastDirection.setDuration(Toast.LENGTH_SHORT);
                    toastDirection.setView(l_layout);
                    toastDirection.show();
                }
            }
        });
    }
}
