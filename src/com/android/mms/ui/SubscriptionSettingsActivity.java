/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mms.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.view.MenuItem;
import com.android.mms.R;

import static android.telephony.TelephonyManager.SIM_STATE_ABSENT;

public class SubscriptionSettingsActivity extends FragmentActivity {

    private static final String SUB_ARG = "sub";

    private ViewPager mViewPager;
    private PagerAdapter mPagerAdapter;
    private SmscBroadcastReceiver mSmscReceiver;

    static class PagerAdapter extends FragmentPagerAdapter {

        private int mSubscriptionCount;

        public PagerAdapter(FragmentManager fm, int subscriptionCount) {
            super(fm);
            mSubscriptionCount = subscriptionCount;
        }

        @Override
        public Fragment getItem(int position) {
            MSimPreferenceFragment fragment = new MSimPreferenceFragment();
            Bundle args = new Bundle();
            args.putInt(SUB_ARG, position);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getCount() {
            return mSubscriptionCount;
        }
    }

    public static class MSimPreferenceFragment extends PreferenceFragment {

        private int mSubscription;
        private MSimPreferencesController mPreferencesController;
        private SubscriptionSettingsActivity mActivity;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.msim_preferences);

            Bundle args = getArguments();
            mSubscription = args.getInt(SUB_ARG);

            mPreferencesController =
                    new MSimPreferencesController(mActivity,
                            getPreferenceManager(), mSubscription);
            mPreferencesController.setPreferenceHolder(this);
            mPreferencesController.setupPreferences();
        }

        @Override
        public void onResume() {
            super.onResume();
            setScreenState();
            mActivity.mSmscReceiver.registerController(mPreferencesController);
        }

        @Override
        public void onPause() {
            super.onPause();
            mActivity.mSmscReceiver.unregisterController(mPreferencesController);
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            SubscriptionSettingsActivity ssActivity = (SubscriptionSettingsActivity)activity;
            mActivity = ssActivity;
        }

        private void setScreenState() {
            int simState = MSimTelephonyManager.getDefault().getSimState(mSubscription);
            getPreferenceScreen().setEnabled(simState == TelephonyManager.SIM_STATE_READY);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.subscription_settings);

        int subCount = MSimTelephonyManager.getDefault().getPhoneCount();
        mPagerAdapter = new PagerAdapter(getFragmentManager(), subCount);

        mViewPager = (ViewPager)findViewById(R.id.viewPager);
        mViewPager.setAdapter(mPagerAdapter);

        connectViewPageAndActionBar(subCount);

        mSmscReceiver = new SmscBroadcastReceiver(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mSmscReceiver, mSmscReceiver.getIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mSmscReceiver);
    }

    private void connectViewPageAndActionBar(int subCount) {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayHomeAsUpEnabled(true);

        ActionBar.TabListener tabListener = new ActionBar.TabListener() {
            @Override
            public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
                mViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {}

            @Override
            public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {}
        };

        MSimTelephonyManager tm = MSimTelephonyManager.getDefault();

        for (int i=0; i < subCount; ++i) {
            String operatorName = tm.getSimState(i) != SIM_STATE_ABSENT
                    ? tm.getNetworkOperatorName(i) : getString(R.string.sub_no_sim);
            String label = getString(R.string.multi_sim_entry_format, operatorName, i + 1);

            actionBar.addTab(actionBar.newTab()
                    .setText(label)
                    .setTabListener(tabListener));
        }

        mViewPager.setOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        getActionBar().setSelectedNavigationItem(position);
                    }
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
