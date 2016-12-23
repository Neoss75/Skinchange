package com.neos.skinchange;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;

import static com.neos.skinchange.MainActivity.APP_PREFERENCES;
import static com.neos.skinchange.MainActivity.APP_PREFERENCES_PATH;
import static com.neos.skinchange.MainActivity.mSettings;


public class MainActivity_Settings extends AppCompatActivity {
    public String PathFile = "/save/save.ch";
    public String PathFileUx = "/ux/save.ch";
    public EditText editPath;
    public CheckBox checkBox;
    public CheckBox checkBoxRun;
    private Spinner spinnerPack;
    String basePath = Environment.getExternalStorageDirectory().getAbsolutePath();
    String PathSD = (basePath + "/igo_avic");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        InitSettings();
        Search_Directory();
        spinerSelected();
    }

    public void InitSettings() {
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        editPath = (EditText) findViewById(R.id.editText);
        checkBox = (CheckBox) findViewById(R.id.checkBox);
        checkBoxRun = (CheckBox) findViewById(R.id.checkBox2);
		spinnerPack = (Spinner) findViewById(R.id.spinnerPack);
        if (mSettings.contains(APP_PREFERENCES_PATH)) {
            editPath.setText(mSettings.getString(APP_PREFERENCES_PATH, ""));
        }
        checkBox.setChecked(mSettings.getBoolean("CloseProg", false));
        checkBoxRun.setChecked(mSettings.getBoolean("RunIgo", false));
    }

    public void spinerSelected() {
        ArrayAdapter<?> adapterPack =
                ArrayAdapter.createFromResource(this, R.array.packageName, android.R.layout.simple_spinner_item);
        adapterPack.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerPack.setAdapter(adapterPack);

        spinnerPack.setSelection(mSettings.getInt("SpinPos", 0));

        spinnerPack.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                String PackName = spinnerPack.getSelectedItem().toString();
                savePrefInt("SpinPos", position);
                savePreferencesString("PackName", PackName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }

    public void onClickToastSet(View view) {
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        String strPath = editPath.getText().toString();
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(APP_PREFERENCES_PATH, strPath);
        editor.apply();
        String strPathUx = strPath + PathFileUx;
        String strPathFile = strPath + PathFile;
        File fileux = new File(strPathUx);
        File file = new File(strPathFile);
        if (fileux.exists() && fileux.isFile()) {
            Toast toast = Toast.makeText(getApplicationContext(),
                    R.string.text_toast_ok,
                    Toast.LENGTH_SHORT);
            toast.show();
        } else {
            if (file.exists() && file.isFile()) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.text_toast_ok,
                        Toast.LENGTH_SHORT);
                toast.show();
            } else {
                Toast toast_no = Toast.makeText(getApplicationContext(),
                        R.string.text_toast_no,
                        Toast.LENGTH_SHORT);
                toast_no.show();
            }
        }
    }

    public void onCheckboxClicked(View e) {
        savePreferences("CloseProg", checkBox.isChecked());
    }

    public void onCheckboxRunApp(View v) {
        savePreferences("RunIgo", checkBoxRun.isChecked());
    }

    private void savePreferences(String key, Boolean value) {
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(key, value);
        editor.commit();
        InitSettings();
    }

    private void savePreferencesString(String key, String value) {
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(key, value);
        editor.commit();
        InitSettings();
    }

    private void savePrefInt(String key, Integer value) {
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(key, value);
        editor.commit();
        InitSettings();
    }

    public void Search_Directory() {
        File file = new File(PathSD);
        if (file.isDirectory()) {
            editPath.setText(PathSD);
        }
    }

}


