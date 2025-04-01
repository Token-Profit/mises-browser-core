package org.chromium.components.browser_ui.bottomsheet;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.chromium.base.Log;


import org.chromium.base.Callback;
import org.chromium.base.MisesReflectionUtil;
import org.chromium.base.supplier.ObservableSupplier;
import org.chromium.base.supplier.ObservableSupplierImpl;
import org.chromium.base.supplier.Supplier;
import org.chromium.components.browser_ui.widget.gesture.BackPressHandler;
import org.chromium.components.browser_ui.widget.scrim.ScrimCoordinator;
import org.chromium.components.browser_ui.widget.scrim.ScrimProperties;
import org.chromium.ui.KeyboardVisibilityDelegate;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.util.TokenHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Map;
import java.util.HashMap;

import com.openmediation.sdk.nativead.AdIconView;
import com.openmediation.sdk.nativead.AdInfo;
import com.openmediation.sdk.nativead.MediaView;
import com.openmediation.sdk.nativead.NativeAd;
import com.openmediation.sdk.nativead.NativeAdListener;
import com.openmediation.sdk.nativead.NativeAdView;
import com.openmediation.sdk.utils.error.Error;

import org.chromium.base.MisesAdsUtil;
import org.chromium.base.MisesSysUtils;

import org.chromium.chrome.browser.ephemeraltab.EphemeralTabSheetContent;
import org.chromium.components.browser_ui.bottomsheet.R;


class MisesBottomSheetControllerImpl extends BottomSheetControllerImpl {
    private static final String TAG = "MisesBottomSheetControllerImpl";

    

    private List<String> mBannerPlacmentIds;
    private Map<String, AdInfo> mLoadedPlacmentIds;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private ViewGroup mBottomSheetContainer;
    View mNativeBannerView;

    public MisesBottomSheetControllerImpl(
            final Supplier<ScrimCoordinator> scrim,
            Callback<View> initializedCallback,
            Window window,
            KeyboardVisibilityDelegate keyboardDelegate,
            Supplier<ViewGroup> root,
            boolean alwaysFullWidth,
            @NonNull Supplier<Integer> edgeToEdgeBottomInsetSupplier) {
        super(scrim, initializedCallback, window, keyboardDelegate, root, alwaysFullWidth, edgeToEdgeBottomInsetSupplier);
        Log.d(TAG, "construct");
        mBannerPlacmentIds = new ArrayList<>();
        mLoadedPlacmentIds = new HashMap<>();
        
    }

    protected void initializeSheet(
        Callback<View> initializedCallback,
        Window window,
        KeyboardVisibilityDelegate keyboardDelegate,
        Supplier<ViewGroup> root) {
        Log.d(TAG, "initializeSheet");
        MisesReflectionUtil.invokeMethod(
            BottomSheetControllerImpl.class,
            this,
            "initializeSheet",
            Callback.class,
            initializedCallback,
            Window.class,
            window,
            KeyboardVisibilityDelegate.class,
            keyboardDelegate,
            Supplier.class,
            root
        );


    }
    
    @Override
    public boolean requestShowContent(BottomSheetContent content, boolean animate) {
        boolean ret  = super.requestShowContent(content, animate);

        Log.d(TAG, "requestShowContent");
        if (mBottomSheetContainer == null) {
            //mBottomSheetContainer = getBottomSheetContainerForTesting();
            mBottomSheetContainer = (ViewGroup)MisesReflectionUtil.getField(
                        BottomSheetControllerImpl.class, "mBottomSheetContainer", this);
        }

        if (mBottomSheetContainer != null) {
            maybeInitBannerAd();
        }
        return ret;
    }

    private void maybeInitBannerAd() {
      Log.d(TAG, "maybeInitBannerAd");
      if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)  {
        //do not support android api level 28 and lower, crash
        return;
      }
      if (MisesAdsUtil.getInstance().isInitSucess()) {
          mBannerPlacmentIds = NativeAd.getCachedPlacementIds("extension_popup");
          loadNativeAd();
      } else {
          mHandler.postDelayed( () -> {
              maybeInitBannerAd();
          }, 200);
      }
    }



    private final NativeAdListener mNativeAdListener = new NativeAdListener() {
        @Override
        public void onNativeAdLoaded(String placementId, AdInfo info) {
            Log.d(TAG, "onNativeAdLoaded, placementId: " + placementId + ", AdInfo : " + info);
            if (!mLoadedPlacmentIds.containsKey(placementId)) {
                mLoadedPlacmentIds.put(placementId, info);
            }
            displayNativeBanner(placementId);
        }

        @Override
        public void onNativeAdLoadFailed(String placementId, Error error) {
            Log.d(TAG, "onNativeAdLoadFailed, placementId: " + placementId + ", error : " + error);
            removeNativeAdListener();
        }

        @Override
        public void onNativeAdImpression(String placementId, AdInfo info) {
            Log.d(TAG, "onNativeAdImpression, placementId: " + placementId + ", info : " + info);
        }

        @Override
        public void onNativeAdClicked(String placementId, AdInfo info) {
            Log.d(TAG, "onNativeAdClicked, placementId: " + placementId + ", info : " + info);
        }
    };

    private boolean isExtensionPopup(final BottomSheet bottomSheet) {
        BottomSheetContent content = bottomSheet.getCurrentSheetContent();
        if (content != null && (content instanceof EphemeralTabSheetContent) && 
            content.getContentView() != null && (content.getContentView().getVisibility() == View.VISIBLE)) {
            return true;
        }

        return false;
        
    }

    private void displayNativeBanner(final String placementId) {
        Log.v(TAG, "displayNativeBanner");
        final AdInfo info = mLoadedPlacmentIds.get(placementId);
        if (info == null) {
            Log.v(TAG, "displayNativeBanner fail, no ad info");
            removeNativeAdListener();
            return;
        }
        if (mBottomSheetContainer == null || mBottomSheetContainer.getVisibility() == View.GONE) {
            removeNativeAdListener();
            return;
        }
        BottomSheet bottomSheet = mBottomSheetContainer.findViewById(R.id.bottom_sheet);
        if (bottomSheet == null || !isExtensionPopup(bottomSheet)) {
            removeNativeAdListener();
            return;
        }
        Context context = mBottomSheetContainer.getContext();
        View adView = LayoutInflater.from(context).inflate(R.layout.native_banner_layout, null);
        if (adView != null) {
            TextView title = adView.findViewById(R.id.ad_title);
            title.setText(info.getTitle());
            TextView desc = adView.findViewById(R.id.ad_desc);
            desc.setText(info.getDesc());
            Button btn = adView.findViewById(R.id.ad_btn);
            btn.setText(info.getCallToActionText());
            MediaView mediaView = adView.findViewById(R.id.ad_media);
            NativeAdView nativeAdView = new NativeAdView(context);
            AdIconView adIconView = adView.findViewById(R.id.ad_icon_media);

            nativeAdView.addView(adView);
            nativeAdView.setTitleView(title);
            nativeAdView.setDescView(desc);
            nativeAdView.setAdIconView(adIconView);
            nativeAdView.setCallToActionView(btn);
            nativeAdView.setMediaView(mediaView);

            nativeAdView.setBackgroundColor(
                            context.getColor(R.color.dialog_bg_color_baseline));
            int paddingPixelV = context.getResources().getDimensionPixelSize(R.dimen.extesnion_popup_ads_banner_padding_v);
            int paddingPixelH = context.getResources().getDimensionPixelSize(R.dimen.extesnion_popup_ads_banner_padding_h);
            nativeAdView.setPadding(paddingPixelH,paddingPixelV,paddingPixelH,paddingPixelV);

            NativeAd.registerNativeAdView(placementId, nativeAdView, info);
            //FrameLayout container = new FrameLayout(context);
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                context.getResources().getDimensionPixelSize(R.dimen.extesnion_popup_ads_banner_height)
            );
            flp.gravity = Gravity.TOP;
            //container.addView(nativeAdView, flp);
            //RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        
            mBottomSheetContainer.addView(nativeAdView, flp);
            bottomSheet.addObserver(
                new EmptyBottomSheetObserver() {
                    @Override
                    public void onSheetClosed(@StateChangeReason int reason) {
                        Log.d(TAG, "BottomSheet.onSheetClosed");
                        if (nativeAdView.getParent() != null) {
                            
                            ((ViewGroup) nativeAdView.getParent()).removeView(nativeAdView); // Remove container
                        }
                        bottomSheet.removeObserver(this);
                        cleanNativeAd(placementId);
                        
                    }
                }
            );
        }

             
    }

    private void cleanNativeAd(final String placementId) {
        mLoadedPlacmentIds.remove(placementId);
        removeNativeAdListener();
    }
    
    private void addNativeAdListener() {
        for (final String placementId : mBannerPlacmentIds) {
            NativeAd.addAdListener(placementId, mNativeAdListener);
        }
    }
    private void removeNativeAdListener() {
        for (final String placementId : mBannerPlacmentIds) {
            NativeAd.removeAdListener(placementId, mNativeAdListener);
        }
    }
    private void loadNativeAd() {
        Log.d(TAG, "loadNativeAd");

        addNativeAdListener();

        for (final String placementId : mBannerPlacmentIds) {
            if (mLoadedPlacmentIds.containsKey(placementId)) {
                Log.d(TAG, "skiping loaded placement " + placementId);
                displayNativeBanner(placementId);
                continue;
            }
            // for TikTok and TencentAd in China traffic
            NativeAd.setDisplayParams(placementId, 320, 70);// heigh == 0 will switch to native banner
            NativeAd.loadAd(placementId);
        }
    }

}