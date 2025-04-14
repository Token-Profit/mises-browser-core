/* Copyright (c) 2020 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.ntp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.graphics.Point;
import android.graphics.Rect;
import androidx.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.Log;
import org.chromium.base.jank_tracker.JankTracker;
import org.chromium.base.supplier.ObservableSupplier;
import org.chromium.base.supplier.OneshotSupplier;
import org.chromium.base.supplier.Supplier;
import org.chromium.base.MisesReflectionUtil;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.browser_controls.BrowserControlsStateProvider;
import org.chromium.chrome.browser.feed.FeedFeatures;
import org.chromium.chrome.browser.feed.FeedSurfaceCoordinator;
import org.chromium.chrome.browser.feed.FeedSurfaceProvider;
import org.chromium.chrome.browser.feed.FeedSwipeRefreshLayout;
import org.chromium.chrome.browser.feedback.HelpAndFeedbackLauncherImpl;
import org.chromium.chrome.browser.lifecycle.ActivityLifecycleDispatcher;
import org.chromium.chrome.browser.magic_stack.ModuleRegistry;
import org.chromium.chrome.browser.privacy.settings.PrivacyPreferencesManagerImpl;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.share.ShareDelegate;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab_ui.TabContentManager;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tasks.HomeSurfaceTracker;
import org.chromium.chrome.browser.toolbar.top.Toolbar;
import org.chromium.chrome.browser.ui.messages.snackbar.SnackbarManager;
import org.chromium.chrome.browser.ui.native_page.NativePageHost;
import org.chromium.chrome.browser.xsurface.feed.FeedLaunchReliabilityLogger.SurfaceType;
import org.chromium.components.browser_ui.bottomsheet.BottomSheetController;
import org.chromium.components.browser_ui.settings.SettingsLauncher;
import org.chromium.components.search_engines.TemplateUrlService;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.modaldialog.ModalDialogManager;
import org.chromium.base.MisesSysUtils;
import org.chromium.chrome.browser.feed.MisesFeedSurfaceCoordinator;
import org.chromium.chrome.browser.toolbar.bottom.BottomToolbarConfiguration;

public class MisesNewTabPage extends NewTabPage {
    private static final String TAG = "MisesNewTabPage";
    // To delete in bytecode, members from parent class will be used instead.
    private BrowserControlsStateProvider mBrowserControlsStateProvider;
    private NewTabPageLayout mNewTabPageLayout;
    private FeedSurfaceProvider mFeedSurfaceProvider;
    private Supplier<Toolbar> mToolbarSupplier;
    private TabModelSelector mTabModelSelector;
    private BottomSheetController mBottomSheetController;
    private ObservableSupplier<Integer> mTabStripHeightSupplier;
    private JankTracker mJankTracker;

  public MisesNewTabPage(
            Activity activity,
            BrowserControlsStateProvider browserControlsStateProvider,
            Supplier<Tab> activityTabProvider,
            ModalDialogManager modalDialogManager,
            SnackbarManager snackbarManager,
            ActivityLifecycleDispatcher lifecycleDispatcher,
            TabModelSelector tabModelSelector,
            boolean isTablet,
            NewTabPageUma uma,
            boolean isInNightMode,
            NativePageHost nativePageHost,
            Tab tab,
            String url,
            BottomSheetController bottomSheetController,
            Supplier<ShareDelegate> shareDelegateSupplier,
            WindowAndroid windowAndroid,
            JankTracker jankTracker,
            Supplier<Toolbar> toolbarSupplier,
            HomeSurfaceTracker homeSurfaceTracker,
            ObservableSupplier<TabContentManager> tabContentManagerSupplier,
            ObservableSupplier<Integer> tabStripHeightSupplier,
            OneshotSupplier<ModuleRegistry> moduleRegistrySupplier) {
        super(
                activity,
                browserControlsStateProvider,
                activityTabProvider,
                modalDialogManager,
                snackbarManager,
                lifecycleDispatcher,
                tabModelSelector,
                isTablet,
                uma,
                isInNightMode,
                nativePageHost,
                tab,
                url,
                bottomSheetController,
                shareDelegateSupplier,
                windowAndroid,
                jankTracker,
                toolbarSupplier,
                homeSurfaceTracker,
                tabContentManagerSupplier,
                tabStripHeightSupplier,
                moduleRegistrySupplier);

        
        assert mNewTabPageLayout instanceof MisesNewTabPageLayout;
        /*if (mNewTabPageLayout instanceof MisesNewTabPageLayout) {
            ((MisesNewTabPageLayout) mNewTabPageLayout).setTab(tab);
            ((MisesNewTabPageLayout) mNewTabPageLayout).setTabProvider(activityTabProvider);
        }*/
        MisesSysUtils.logEvent("ntp_open", "step", "open_native");
    }

    @Override
    protected void initializeMainView(
            Activity activity,
            WindowAndroid windowAndroid,
            SnackbarManager snackbarManager,
            boolean isInNightMode,
            Supplier<ShareDelegate> shareDelegateSupplier,
            String url) {
        // Override surface provider
        Profile profile = Profile.fromWebContents(mTab.getWebContents());

        LayoutInflater inflater = LayoutInflater.from(activity);
        mNewTabPageLayout = (NewTabPageLayout) inflater.inflate(R.layout.new_tab_page_layout, null);

        assert !FeedFeatures.isFeedEnabled(profile);
        FeedSurfaceCoordinator feedSurfaceCoordinator = new MisesFeedSurfaceCoordinator(
                        activity,
                        snackbarManager,
                        windowAndroid,
                        mJankTracker,
                        new SnapScrollHelperImpl(mNewTabPageManager, mNewTabPageLayout),
                        mNewTabPageLayout,
                        mBrowserControlsStateProvider.getTopControlsHeight(),
                        isInNightMode,
                        this,
                        profile,
                        mBottomSheetController,
                        shareDelegateSupplier,
                        /* externalScrollableContainerDelegate= */ null,
                        NewTabPageUtils.decodeOriginFromNtpUrl(url),
                        PrivacyPreferencesManagerImpl.getInstance(),
                        mToolbarSupplier,
                        mConstructedTimeNs,
                        FeedSwipeRefreshLayout.create(activity, R.id.toolbar_container),
                        /* overScrollDisabled= */ false,
                        /* viewportView= */ null,
                        /* actionDelegate= */ null,
                        mTabStripHeightSupplier);

        mFeedSurfaceProvider = feedSurfaceCoordinator;

    }

    public void updateSearchProviderHasLogo() {
        // Search provider logo is not used in Mises's NTP.
        mSearchProviderHasLogo = true;
        Log.d(TAG, "updateSearchProviderHasLogo");
    }
    private int getToolbarExtraYOffset() {
        assert (false);
        return 0;
    }
    
    public void updateMargins() {
        Log.d(TAG, "updateMargins");
                View view = getView();
        ViewGroup.MarginLayoutParams layoutParams =
                ((ViewGroup.MarginLayoutParams) view.getLayoutParams());
        if (layoutParams == null) return;
        final int topControlsDistanceToRest =
                mBrowserControlsStateProvider.getContentOffset()
                        - mBrowserControlsStateProvider.getTopControlsHeight();
        final int topMargin = getToolbarExtraYOffset() + topControlsDistanceToRest;

        int bottomMargin =
                mBrowserControlsStateProvider.getBottomControlsHeight()
                        - mBrowserControlsStateProvider.getBottomControlOffset();

        if (BottomToolbarConfiguration.isBottomToolbarEnabled()) {
            bottomMargin = 0;
        }

        if (topMargin != layoutParams.topMargin || bottomMargin != layoutParams.bottomMargin) {
            layoutParams.topMargin = topMargin;
            layoutParams.bottomMargin = bottomMargin;
            view.setLayoutParams(layoutParams);
        }


    }
    // @Override
    // public void getSearchBoxBounds(Rect bounds, Point translation) {
    //     super.getSearchBoxBounds(bounds, translation);
    // }
}
