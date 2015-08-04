/*
 *  Copyright (C) Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo Caddisfly
 *
 *  Akvo Caddisfly is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo Caddisfly is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included below for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.caddisfly.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.app.AppPreferences;
import org.akvo.caddisfly.util.ApiUtils;

public class SettingsActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ApiUtils.lockScreenOrientation(this);

        setupActivity();
    }

    @Override
    public void onRestart() {
        super.onRestart();
        setupActivity();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        //Add a tool bar on this settings screen
        LinearLayout root = (LinearLayout) findViewById(android.R.id.list).getParent().getParent().getParent().getParent().getParent();
        if (root.findViewById(R.id.toolbarId) == null) {
            Toolbar bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.preferences_toolbar, root, false);
            bar.setId(R.id.toolbarId);
            root.addView(bar, 0);
            bar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

    }

    private void setupActivity() {
        setContentView(R.layout.activity_settings);

        getFragmentManager().beginTransaction()
                .replace(R.id.content, new PreferencesGeneralFragment())
                .commit();

        getFragmentManager().beginTransaction()
                .replace(R.id.content2, new PreferencesOtherFragment())
                .commit();

        if (AppPreferences.isDiagnosticMode(this)) {
            getFragmentManager().beginTransaction()
                    .add(R.id.content3, new PreferencesDiagnosticFragment())
                    .commit();
        }
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        Context context = this.getApplicationContext();
        assert context != null;

        if (context.getString(R.string.languageKey).equals(s)) {
            finish();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext())
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext())
                .registerOnSharedPreferenceChangeListener(this);
    }

}
