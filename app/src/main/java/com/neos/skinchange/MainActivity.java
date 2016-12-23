package com.neos.skinchange;


import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class MainActivity extends AppCompatActivity implements OnClickListener {
    private final int MY_PERMISSIONS_REQUEST=1;
    public static SharedPreferences mSettings;
    public static String APP_PREFERENCES = "mysettings";
    public static String APP_PREFERENCES_PATH = "Path";
    private String Def = "default_skin";
    private String Pon = "pongo_skin";
    private String Alt = "alt_skin";
    private String Save = "/save";
    private String Save_Def = "/save_default";
    private String Save_Pon = "/save_pongo";
    private String Save_Alt = "/save_alt";
    private String Ux = "/ux";
    private String Ux_Def = "/ux_default";
    private String Ux_Pon = "/ux_pongo";
    private String Ux_Alt = "/ux_alt";
    private String TextSkinInclude;
    private String SkinOn;
    private String strPath;
    private String PackName;
    private Boolean ExitMyApp;
    private Boolean RunApp;
    private boolean choisePathRename;
    private long back_pressed;
    Button button1;
    Button button2;
    Button button3;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private void bzzzt() {
        Toast.makeText(this, "Enable write sdCard!", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);

        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        assert pInfo != null;
        String versionName = pInfo.versionName;
        String Text = ("v " + versionName);
        TextView textView = (TextView) findViewById(R.id.textView7);
        textView.setText(Text);
        InitPreference();
        Log.d("SC: ", "onStart");
    }
    
    public void InitPreference() {
        if (!isCheckPermission()){
            return;
        }
        button1 = (Button) findViewById(R.id.button);
        button2 = (Button) findViewById(R.id.button2);
        button3 = (Button) findViewById(R.id.button6);
        if (mSettings.contains(APP_PREFERENCES_PATH)) {
            strPath = mSettings.getString(APP_PREFERENCES_PATH, "");
        }
        ExitMyApp = mSettings.getBoolean("CloseProg", false);
        RunApp = mSettings.getBoolean("RunIgo", false);
        PackName = mSettings.getString("PackName", "");
        String fileName = "save.ch";
        File uxPath = new File(strPath + Ux, fileName);
        choisePathRename = (uxPath.exists() & uxPath.isFile());
        File saveFile = new File(strPath + Save, fileName);
        boolean filePathValid = (saveFile.exists() & saveFile.isFile());
        File sdFile = new File(strPath + Save, fileName);
        File AltButtonUxPath = new File(strPath + Ux_Alt, fileName);
        File AltButtonPath = new File(strPath + Save_Alt, fileName);
        boolean onButtonAlt = (AltButtonPath.exists() & AltButtonPath.isFile());
        boolean onButtonAltUx = (AltButtonUxPath.exists() & AltButtonUxPath.isFile());
        button1.setEnabled(false);
        button2.setEnabled(false);
        button3.setEnabled(false);
        if (!onButtonAlt & !onButtonAltUx) {
            button3.setVisibility(View.GONE);
        }
        if (choisePathRename & isCheckPermission()) {
            Log.d("SC: ", "choicePathRename true");
            try {
                BufferedReader br = new BufferedReader(new FileReader(uxPath));
                String line;
                StringBuilder text = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    text.append(line);
                    String textSkin = text.toString();
                    Log.d("SC: ", textSkin);
                    if (textSkin.equals(Def)) {
                        button2.setEnabled(true);
                        if (onButtonAltUx) {
                            button3.setEnabled(true);
                        }
                        TextSkinInclude = getString(R.string.text_skin_default);
                    }
                    if (textSkin.equals(Pon)) {
                        button1.setEnabled(true);
                        if (onButtonAltUx) {
                            button3.setEnabled(true);
                        }
                        TextSkinInclude = getString(R.string.text_skin_pongo);
                    }
                    if (textSkin.equals(Alt)) {
                        button1.setEnabled(true);
                        button2.setEnabled(true);
                        TextSkinInclude = getString(R.string.text_skin_alt);
                    }
                    SkinOn = textSkin;
                    TextView textView2 = (TextView) findViewById(R.id.textView2);
                    textView2.setText(TextSkinInclude);
                }
                br.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (filePathValid & isCheckPermission()) {
                Log.d("SC: ", "filePathValid true");
                try {
                    BufferedReader br = new BufferedReader(new FileReader(sdFile));
                    String line;
                    StringBuilder text = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                        String TextSkin = text.toString();
                        Log.d("SC: ", TextSkin);
                        if (TextSkin.equals(Def)) {
                            button2.setEnabled(true);
                            if (onButtonAlt) {
                                button3.setEnabled(true);
                            }
                            TextSkinInclude = getString(R.string.text_skin_default);
                        }
                        if (TextSkin.equals(Pon)) {
                            button1.setEnabled(true);
                            if (onButtonAlt) {
                                button3.setEnabled(true);
                            }
                            TextSkinInclude = getString(R.string.text_skin_pongo);
                        }
                        if (TextSkin.equals(Alt)) {
                            button1.setEnabled(true);
                            button2.setEnabled(true);
                            TextSkinInclude = getString(R.string.text_skin_alt);
                        }
                        SkinOn = TextSkin;
                        TextView textView2 = (TextView) findViewById(R.id.textView2);
                        textView2.setText(TextSkinInclude);
                    }
                    br.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public void onClick(View view) {
        if (!isCheckPermission()) {
            bzzzt();
        } else {
            Intent intent = new Intent(this, MainActivity_Settings.class);
            startActivity(intent);
        }
    }

    public void RenameDirPongo(View view) {
        String savePath = null;
        String uxPath = null;
        boolean renamed;
        boolean renamed2;
        if (SkinOn.equals(Def)) {
            savePath = Save_Def;
            uxPath = Ux_Def;
        } else if (SkinOn.equals(Alt)) {
            savePath = Save_Alt;
            uxPath = Ux_Alt;
        }
        if (!choisePathRename) {
            File save = new File(strPath + Save);
            File savePongo = new File(strPath + savePath);
            renamed = save.renameTo(savePongo);
            File saveOther = new File(strPath + Save_Pon);
            File saveRename = new File(strPath + Save);
            renamed2 = saveOther.renameTo(saveRename);
        } else {
            renamed = true;
            renamed2 = true;
        }
        File ux = new File(strPath + Ux);
        File uxPongo = new File(strPath + uxPath);
        boolean renamed1 = ux.renameTo(uxPongo);

        File uxOther = new File(strPath + Ux_Pon);
        File uxRename = new File(strPath + Ux);
        boolean renamed3 = uxOther.renameTo(uxRename);

        if (renamed && renamed1 && renamed2 && renamed3) {
            Toast.makeText(getApplicationContext(),
                    R.string.toast_succeeded, Toast.LENGTH_SHORT).show();
            InitPreference();
        }
        if (ExitMyApp) {
            if (RunApp) {
                RunAppPack();
            }
            finish();
        }
    }

    public void RenameDirDefault(View view) {
        String savePath = null;
        String uxPath = null;
        boolean renamed;
        boolean renamed2;
        if (SkinOn.equals(Pon)) {
            savePath = Save_Pon;
            uxPath = Ux_Pon;
        } else if (SkinOn.equals(Alt)) {
            savePath = Save_Alt;
            uxPath = Ux_Alt;
        }
        if (!choisePathRename) {
            File save = new File(strPath + Save);
            File saveOther = new File(strPath + savePath);
            renamed = save.renameTo(saveOther);
            File saveDefault = new File(strPath + Save_Def);
            File saveRename = new File(strPath + Save);
            renamed2 = saveDefault.renameTo(saveRename);
        } else {
            renamed = true;
            renamed2 = true;
        }
        File ux = new File(strPath + Ux);
        File uxOther = new File(strPath + uxPath);
        boolean renamed1 = ux.renameTo(uxOther);

        File uxDefault = new File(strPath + Ux_Def);
        File uxRename = new File(strPath + Ux);
        boolean renamed3 = uxDefault.renameTo(uxRename);

        if (renamed && renamed1 && renamed2 && renamed3) {
            Toast.makeText(getApplicationContext(),
                    R.string.toast_succeeded, Toast.LENGTH_SHORT).show();
            InitPreference();
        }
        if (ExitMyApp) {
            if (RunApp) {
                RunAppPack();
            }
            finish();
        }
    }

    public void RenameDirAlt(View view) {
        String savePath = null;
        String uxPath = null;
        boolean renamed;
        boolean renamed2;
        if (SkinOn.equals(Def)) {
            savePath = Save_Def;
            uxPath = Ux_Def;
        } else if (SkinOn.equals(Pon)) {
            savePath = Save_Pon;
            uxPath = Ux_Pon;
        }
        if (!choisePathRename) {
            File save = new File(strPath + Save);
            File saveOther = new File(strPath + savePath);
            renamed = save.renameTo(saveOther);
            File saveAlt = new File(strPath + Save_Alt);
            File saveRename = new File(strPath + Save);
            renamed2 = saveAlt.renameTo(saveRename);
        } else {
            renamed = true;
            renamed2 = true;
        }
        File ux = new File(strPath + Ux);
        File uxOther = new File(strPath + uxPath);
        boolean renamed1 = ux.renameTo(uxOther);

        File uxAlt = new File(strPath + Ux_Alt);
        File uxRename = new File(strPath + Ux);
        boolean renamed3 = uxAlt.renameTo(uxRename);

        if (renamed && renamed1 && renamed2 && renamed3) {
            Toast.makeText(getApplicationContext(),
                    R.string.toast_succeeded, Toast.LENGTH_SHORT).show();
            InitPreference();
        }
        if (ExitMyApp) {
            if (RunApp) {
                RunAppPack();
            }
            finish();
        }
    }
    
    public void RunAppPack() {
        Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(PackName);
        if (LaunchIntent == null) {
            Toast.makeText(getApplicationContext(),
                    R.string.text_toast_notfound,
                    Toast.LENGTH_SHORT).show();
        } else {
            startActivity(LaunchIntent);
        }
    }

    @Override
    public void onBackPressed() {
        if (back_pressed + 2000 > System.currentTimeMillis())
            super.onBackPressed();
        else
            Toast.makeText(getBaseContext(),
                    R.string.toast_exit,
                    Toast.LENGTH_SHORT).show();
        back_pressed = System.currentTimeMillis();
    }

 //////Permission//////////////
 private boolean isCheckPermission() {
     if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
             PackageManager.PERMISSION_GRANTED) {

         if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
             showMessageOKCancel("You need to allow access to SDCard");
             return false;
         }

         ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                 MY_PERMISSIONS_REQUEST);
         return false;
     }

     return true;
 }

    private void showMessageOKCancel(String message) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", listener)
                .setNegativeButton("Cancel", listener)
                .create()
                .show();
    }

    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

        final int BUTTON_NEGATIVE = -2;
        final int BUTTON_POSITIVE = -1;

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case BUTTON_NEGATIVE:
                    dialog.dismiss();
                    break;

                case BUTTON_POSITIVE:
                    ActivityCompat.requestPermissions(
                            MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST);
                    dialog.dismiss();
                    break;
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    InitPreference();

                } else {
                    Log.e("SC: ", "Permission Denied");
                }
            }
            default: {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }
}