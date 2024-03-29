package com.zhaoxianglu.WechatBackupNoRoot;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.GsonUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.vanniktech.rxpermission.RealRxPermission;
import com.zhaoxianglu.WechatBackupNoRoot.constant.NodeInfo;
import com.zhaoxianglu.WechatBackupNoRoot.constant.WeChatConstant;
import com.zhaoxianglu.WechatBackupNoRoot.floating.FloatingView;
import com.zhaoxianglu.WechatBackupNoRoot.floating.FloatingViewConfig;
import com.zhaoxianglu.WechatBackupNoRoot.utils.AutoUtils;
import com.zhaoxianglu.WechatBackupNoRoot.wechat.WeChatAuto;
import com.zhaoxianglu.WechatBackupNoRoot.wechat.WeChatMessage;
import com.zhaoxianglu.WechatBackupNoRoot.wechat.WeChatMessagePage;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.zhaoxianglu.WechatBackupNoRoot.constant.WeChatConstant.AdaptationVersionNames;
import static com.zhaoxianglu.WechatBackupNoRoot.utils.AutoUtils.SLIDE_DOWN;
import static com.zhaoxianglu.WechatBackupNoRoot.utils.AutoUtils.appPause;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, View.OnLongClickListener {

    private FloatingView floatingView;
    private ImageView fTv1;
    private LinearLayout ll_root;

    private static String TAG = "MainActivity";

    public static boolean isRunning = false;
    public static boolean isAccessibilityOpen = false;

    private static List<WeChatMessage> LastMessageList = new ArrayList<>();
    private static List<WeChatMessagePage> AllPageMessageList = new ArrayList<>();
    private static int PageIndex = 0;
    private static int SameIndexCount = 0;

    private TextView tvInfo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        openFloatWindow();
        autoOpenAccessibility();

        Log.e(TAG,Environment.getExternalStorageDirectory().getAbsolutePath());

        RealRxPermission.getInstance(getApplicationContext())
                .requestEach(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe();

        //获取版本



        tvInfo = (TextView) findViewById(R.id.tv_info);

        StringBuffer sb = new StringBuffer();

        WeChatConstant.VersionName =  AppUtils.getAppVersionName(WeChatConstant.PackageName);
        WeChatConstant.VersionCode =  AppUtils.getAppVersionCode(WeChatConstant.PackageName);

        sb.append("微信版本 "+WeChatConstant.VersionName+"\n");

        if(Arrays.asList(AdaptationVersionNames).contains(WeChatConstant.VersionName)){
            sb.append("当前微信版本支持");
        }else {
            sb.append("当前微信版本不支持");
        }

        tvInfo.setText(sb.toString());

    }

    private void openFloatWindow() {
        FloatingViewConfig config = new FloatingViewConfig.Builder()
                .setGravity(FloatingViewConfig.GRAVITY.RIGHT_CENTER)
                .build();
        floatingView = new FloatingView(this, R.layout.view_floating, config);
        fTv1 = floatingView.rootView.findViewById(R.id.f_tv_1);
        ll_root = (LinearLayout) floatingView.rootView;
        floatingView.setOnClickListener(this);
        fTv1.setOnClickListener(this);
        fTv1.setOnLongClickListener(this);
        floatingView.showOverlaySystem();
    }

    private void destroyFloatWindow() {
        if (floatingView != null) {
            floatingView.hide();
        }
    }

    private void autoOpenAccessibility() {
        new Thread(() -> {
            int i = 0;
            while (true){
                appPause(5*1000);
                runOnUiThread(() -> {
                    isAccessibilityOpen = isAccessibilitySettingsOn();
                    if (isAccessibilityOpen) {
                        ll_root.setBackgroundColor(Color.GREEN);
                    } else {
                        ll_root.setBackgroundColor(Color.RED);
                    }
                });
                i++;
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyFloatWindow();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.f_tv_1:

                if(isRunning){
                    isRunning = false;
                }else {
                    //判断无障碍服务
                    if(isAccessibilityOpen){
                        isRunning = true;
                        new Thread(() -> msgGet()).start();
                    }else {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                }

                break;
        }

    }


    public static void msgGet(){
        while (isRunning) {

            Log.e(TAG,"本次抓取" + PageIndex);

            WeChatMessagePage weChatMessagePage = new WeChatMessagePage(PageIndex);

            List<WeChatMessage> pageFilterMessageList = new ArrayList<>();
            List<WeChatMessage> pageAllMessageList = new ArrayList<>();

            NodeInfo nodeInfo = NodeInfo.NodeInfo(WeChatConstant.VersionName);

            AccessibilityNodeInfo titleName = AutoUtils.getAutoElementById(AutoUtils.service,nodeInfo.msg_page_title,0);

            if(titleName==null){
                ToastUtils.showLong("未找到正确元素，请确认所在位置。");
                Log.e(TAG,"未找到正确元素，请确认所在位置。");
                break;
            }

            AccessibilityNodeInfo rootListView = AutoUtils.getAutoElementById(AutoUtils.service,nodeInfo.msg_page_list_view,0);
            if(rootListView!=null){
                List<AccessibilityNodeInfo> listMsg = rootListView.findAccessibilityNodeInfosByViewId(nodeInfo.msg_page_text_msg_info);


                if(listMsg!=null&&listMsg.size()>0){
                    for(AccessibilityNodeInfo accessibilityNodeInfo:listMsg){
                        Rect rect = new Rect();
                        accessibilityNodeInfo.getBoundsInScreen(rect);
                        int person = msgPerson(rect);

                        WeChatMessage weChatMessage = null;

                        if(accessibilityNodeInfo.getText()!=null){
                            weChatMessage = new WeChatMessage(person,0,accessibilityNodeInfo.getText().toString());
                        }else {
                            weChatMessage = new WeChatMessage(person,0,"");
                        }

                        if(!isOnLastPage(weChatMessage)) {
                            pageFilterMessageList.add(weChatMessage);
                            //Log.e(TAG, person + ": " + accessibilityNodeInfo.getText().toString());
                        }
                        // 当前页面全部记录
                        pageAllMessageList.add(weChatMessage);

                        if(stopSlide(weChatMessage)){
                            isRunning = false;
                        }

                    }
                    weChatMessagePage.setChatMessages(pageFilterMessageList);
                    AllPageMessageList.add(weChatMessagePage);
                }
                //停止滑动方案2
                if(isSamePage(pageAllMessageList)){
                    SameIndexCount ++;
                }else {
                    SameIndexCount = 0;
                }

                if(SameIndexCount>5){
                    isRunning = false;
                }
            }

            LastMessageList  = pageAllMessageList;
            PageIndex ++ ;
            AutoUtils.slideScreenCenter(500,1,SLIDE_DOWN,1000,2000);
        }
        // 文件导出
        Collections.reverse(AllPageMessageList);

        saveMsgJson(AllPageMessageList);

        printMsg(AllPageMessageList);
    }


    public static boolean isAccessibilitySettingsOn() {
        if (AutoUtils.service != null&& AutoUtils.service.getRootInActiveWindow() != null){
            return true;
        }
        return false;
    }

    @Override
    public boolean onLongClick(View v) {
        switch (v.getId()){
            case R.id.f_tv_1:
                break;
        }
        return false;
    }

    private static void printMsg(List<WeChatMessagePage> messagePages){
        for(WeChatMessagePage weChatMessagePage:messagePages){
            Log.e(TAG,weChatMessagePage.getIndex()+"----------------------");
            if(weChatMessagePage.getChatMessages()!=null){
                for(WeChatMessage weChatMessage:weChatMessagePage.getChatMessages()){
                    Log.e(TAG,weChatMessage.getPerson()+":"+weChatMessage.getMsg());
                }
            }
        }
    }

    private static boolean isSamePage(List<WeChatMessage> pageAllMessageList){

        if(pageAllMessageList.size()==LastMessageList.size()){

            for(int i=0;i<pageAllMessageList.size();i++){

                if((pageAllMessageList.get(i).getPerson()==LastMessageList.get(i).getPerson())&&(pageAllMessageList.get(i).getMsg().equals(LastMessageList.get(i).getMsg()))){
                    //ok
                }else {
                    return false;
                }

            }

        }
        return true;
    }

    private static boolean isOnLastPage(WeChatMessage weChatMessage){
        for(WeChatMessage weChatMessage1:LastMessageList){
            if((weChatMessage.getPerson()==weChatMessage1.getPerson())&&(weChatMessage.getMsg().equals(weChatMessage1.getMsg()))){
                return true;
            }
        }
        return false;
    }

    private static int msgPerson(Rect rect){

        int mid =   ScreenUtils.getScreenWidth() /2 ;
        int rect_mid = rect.centerX();

        //Log.e(TAG,mid +" "+ rect_mid);

        if(rect_mid==mid){
            return 2;
        }

        if(rect_mid>mid){
            return 0;
        }

        return 1;
    }

    private static void saveMsgJson(List<WeChatMessagePage> AllPageMessageList){
        String file_path  = "/storage/emulated/0"+"/Download/save_msg.json";
        FileUtils.createFileByDeleteOldFile(file_path);
        FileIOUtils.writeFileFromString(file_path, GsonUtils.toJson(AllPageMessageList));
    }

    private static boolean stopSlide(WeChatMessage weChatMessage){
        //停止滑动方案  1  发现 以上是打招呼的内容
        if(weChatMessage.getMsg().equals("以上是打招呼的内容"))
        {
            return true;
        }

        //停止滑动方案2 没有新增
        if(weChatMessage.getMsg().equals("以上是打招呼的内容"))
        {
            return true;
        }

        return false;
    }
}