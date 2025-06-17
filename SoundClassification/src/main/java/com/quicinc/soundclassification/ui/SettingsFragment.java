package com.quicinc.soundclassification.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.quicinc.soundclassification.R;
import com.quicinc.soundclassification.service.ClassifierHandler;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        SwitchPreference togglePref = (SwitchPreference) findPreference("use_gpu");
        if (togglePref != null) {
            togglePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        ClassifierHandler.createAsync(getContext(), new ClassifierHandler.OnClassifierReadyCallback() {
                            @Override
                            public void onReady() {
                                Log.d("Settings", "Classifier 초기화 완료");
                            }

                            @Override
                            public void onError(Exception e) {
                                Toast.makeText(getContext(), "Classifier 초기화 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    return true;
                }
            });
        }
    }
}
