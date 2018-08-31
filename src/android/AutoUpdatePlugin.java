package com.wistron.ptsApp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Anne on 2018-08-03
 * Update by Anne on 2018-08-31
 */
public class AutoUpdatePlugin extends CordovaPlugin {
  public static final String TAG = "AutoUpdatePlugin";
  //用来请求版本数据的链接
  private String checkVersionUrl = null;
  //新版本的下载链接
  private String newVersionUrl = null;
  private Version latestVersion = null;
  private Context mContext;

  //拼接用的URL
  private static String UPDATE_URL = "/~pts/dispatcher/app/get_update.php?my_platform=Android&my_version=";

  //正式区
  private static String UPDATE_SERVER_URL_PTS = "https://pts.wistron.com/~pts/dispatcher/app/get_update.php?my_platform=Android&my_version=";
  //测试区
  private static String UPDATE_SERVER_URL_TW_TEST = "http://pts-test.wistron.com/~pts/dispatcher/app/get_update.php?my_platform=Android&my_version=";
  //？？？
  private static String UPDATE_SERVER_URL_SH = "http://10.43.146.38/~pts/dispatcher/app/get_update.php?my_platform=Android&my_version=";

  private static final int HAS_NEW_VERSION = 0x1111;

  //需要动态申请的权限
  private static String[] PERMISSIONS_STORAGE = {
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_EXTERNAL_STORAGE"
  };

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    if ("checkNewVersion".equals(action)) {
      //支持自带参数
      String arg = args.getString(0);
      if (null != arg && !arg.isEmpty()) {
        Log.i(TAG, "checkNewVersionUrl: " + arg);
        this.checkVersionUrl = arg + UPDATE_URL;
      }
      initBroadcastReceiver();
      checkNewVersion();
      return true;
    }
    return super.execute(action, args, callbackContext);
  }

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    this.checkVersionUrl = UPDATE_SERVER_URL_PTS;
    this.mContext = this.cordova.getActivity();
    Log.d(TAG, "initialize");
    initBroadcastReceiver();
//    checkNewVersion();
  }

  private void initBroadcastReceiver() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction("android.intent.action.DOWNLOAD_COMPLETE");
    mContext.registerReceiver(broadcastReceiver, intentFilter);
  }

  @Override
  protected void pluginInitialize() {
    super.pluginInitialize();
  }

  private void downLoadFile(String url) {
    Log.i(TAG, "downloadFile");
    File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PTSAPP/ptsApp.apk");
    if (file.exists())
      file.delete();

    DownloadManager manager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
    request.setDestinationInExternalPublicDir("PTSAPP", "/ptsApp.apk");
    request.setVisibleInDownloadsUi(true);
    long id = manager.enqueue(request);
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
    sharedPreferences.edit().putLong(DownloadManager.EXTRA_DOWNLOAD_ID, id).apply();
    Log.i(TAG, "started DownloadManager");
  }

  /**
   * 向服务器请求判断是否有新版本
   */
  private boolean checkNewVersion() {
    final String requestUrl = checkVersionUrl + getVersionCode().replace(" ", "%20");
    Log.i(TAG, "requestUrl : " + requestUrl);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          BufferedReader reader = null;
          String line;
          StringBuffer buffer = new StringBuffer();
          URL url = new URL(requestUrl);
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          int responseCode = connection.getResponseCode();
          Log.d(TAG, "responseCode = " + responseCode);
          if (responseCode == 200) {
            //请求成功，对get的数据进行解析
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            while (null != (line = reader.readLine())) {
              buffer.append(line);
            }
            String data = buffer.toString();
            Log.d(TAG, "returned data :" + data);
            parseDownloadUrl(data);
            //解析成功，获取到当前的最新版本信息
            if (null != latestVersion) {
              if (!latestVersion.versionCode.equals(getVersionCode())) {
                mHandler.sendEmptyMessage(HAS_NEW_VERSION);
              } else
                Log.d(TAG, "currentVersionCode:" + getVersionCode()
                  + " \nlatestVersionCode:" + latestVersion.getVersionCode());
            }
          }
        } catch (java.io.IOException e) {
          e.printStackTrace();
        }
      }
    }).start();
    return false;
  }

  /**
   * 展示提示更新的弹窗
   */
  private void showUpdateDialog() {
    Log.i(TAG, "showUpdateDialog");
    AlertDialog.Builder alert = new AlertDialog.Builder(mContext);
    alert.setTitle(latestVersion.getSummary())
      .setMessage(latestVersion.getDescription())
      .setPositiveButton("确定下载", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          //确认下载的逻辑
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //Android系统在6.0以上进行动态申请权限
            int permission = ActivityCompat.checkSelfPermission(mContext,
              "android.permission.WRITE_EXTERNAL_STORAGE");
            if (PackageManager.PERMISSION_GRANTED != permission) {
              //没有文件存储权限，动态申请
              ActivityCompat.requestPermissions((Activity) mContext, PERMISSIONS_STORAGE, 1);
            } else
              //有权限，开始下载
              new Thread(new Runnable() {
                @Override
                public void run() {
                  downLoadFile(latestVersion.getUrl());
                }
              }).start();
          }
        }
      })
      .setNegativeButton("残忍拒绝", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          //取消下载的逻辑
          dialog.dismiss();
        }
      })
      .setCancelable(false);
    alert.create().show();
  }

  @Override
  public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
    Log.d(TAG, "onRequestPermissionResult: requestCode: " + requestCode);
    super.onRequestPermissionResult(requestCode, permissions, grantResults);
    if (requestCode == 1) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        new Thread(new Runnable() {
          @Override
          public void run() {
            downLoadFile(latestVersion.getUrl());
          }
        }).start();
    } else
      Log.d(TAG, "" + requestCode);
  }

  @Override
  public void onDestroy() {
    mContext.unregisterReceiver(broadcastReceiver);
    super.onDestroy();
  }

  /**
   * 从json数据中解析出apk的下载链接
   */
  private void parseDownloadUrl(String data) {
    Log.i(TAG, "parseDownloadUrl");
    latestVersion = null;
    try {
      JSONObject jsonObject = new JSONObject(data);
      JSONObject android = new JSONObject(jsonObject.getString("android"));
      latestVersion = new Version();
      latestVersion.description = android.getString("description");
      latestVersion.summary = android.getString("summary");
      latestVersion.versionCode = android.getString("version");
      latestVersion.url = android.getString("package");
      Log.d(TAG, "parseNewVersion successful!" + latestVersion.getUrl());
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  /**
   * 获取versionName
   *
   * @return 返回versionName
   */
  private String getVersionCode() {
    PackageInfo packageInfo = null;
    try {
      packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
      return packageInfo.versionName;

    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
    return packageInfo.versionName;
  }

  private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals("android.intent.action.DOWNLOAD_COMPLETE")) {
        installApk(context);
      }
    }
  };

  private void installApk(Context context) {
    Log.i(TAG, "installApk");
    File apkFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PTSAPP/ptsApp.apk");
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      Uri contentUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".fileprovider", apkFile); //与manifest中定义的provider中的authorities="com.wistron.ptsApp.fileprovider"保持一致
      intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
    } else {
      intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
    }
    mContext.startActivity(intent);
  }

  /**
   * 版本信息类
   */
  class Version {
    private String summary;
    private String description;
    private String versionCode;
    private String url;

    public String getSummary() {
      return summary;
    }

    public void setSummary(String summary) {
      this.summary = summary;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getVersionCode() {
      return versionCode;
    }

    public void setVersionCode(String versionCode) {
      this.versionCode = versionCode;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }

  private Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      super.handleMessage(msg);
      switch (msg.what) {
        case HAS_NEW_VERSION:
          showUpdateDialog();
          break;
        default:
          break;
      }
    }
  };
}
