package com.starrtc.demo.demo.voip;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.starrtc.demo.R;
import com.starrtc.demo.demo.BaseActivity;
import com.starrtc.demo.demo.MLOC;
import com.starrtc.demo.database.CoreDB;
import com.starrtc.demo.database.HistoryBean;
import com.starrtc.demo.ui.CircularCoverView;
import com.starrtc.demo.utils.AEvent;
import com.starrtc.demo.utils.ColorUtils;
import com.starrtc.demo.utils.DensityUtils;
import com.starrtc.starrtcsdk.api.XHClient;
import com.starrtc.starrtcsdk.api.XHConstants;
import com.starrtc.starrtcsdk.api.XHCustomConfig;
import com.starrtc.starrtcsdk.api.XHVoipManager;
import com.starrtc.starrtcsdk.apiInterface.IXHResultCallback;
import com.starrtc.starrtcsdk.core.StarRtcCore;
import com.starrtc.starrtcsdk.core.audio.StarRTCAudioManager;
import com.starrtc.starrtcsdk.core.player.StarPlayer;
import com.starrtc.starrtcsdk.core.pusher.ScreenRecorder;

import java.text.SimpleDateFormat;
import java.util.Set;

public class VoipActivity extends BaseActivity implements View.OnClickListener {

    private XHVoipManager voipManager;

    private StarPlayer targetPlayer;
    private StarPlayer selfPlayer;
    private Chronometer timer;

    public static String ACTION = "ACTION";
    public static String RING = "RING";
    public static String CALLING = "CALLING";
    private String action;
    private String targetId;
    private Boolean isTalking = false;

    private StarRTCAudioManager starRTCAudioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        starRTCAudioManager = StarRTCAudioManager.create(this.getApplicationContext());
        starRTCAudioManager.start(new StarRTCAudioManager.AudioManagerEvents() {
            @Override
            public void onAudioDeviceChanged(StarRTCAudioManager.AudioDevice selectedAudioDevice, Set<StarRTCAudioManager.AudioDevice> availableAudioDevices) {
                MLOC.d("onAudioDeviceChanged ",selectedAudioDevice.name());
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams. FLAG_FULLSCREEN ,
                WindowManager.LayoutParams. FLAG_FULLSCREEN);
        setContentView(R.layout.activity_voip);
        voipManager = XHClient.getInstance().getVoipManager();
        voipManager.setRtcMediaType(XHConstants.XHRtcMediaTypeEnum.STAR_RTC_MEDIA_TYPE_VIDEO_AND_AUDIO);
        addListener();

        targetId = getIntent().getStringExtra("targetId");
        action = getIntent().getStringExtra(ACTION);

        targetPlayer = (StarPlayer) findViewById(R.id.voip_surface_target);
        selfPlayer = (StarPlayer) findViewById(R.id.voip_surface_self);
        selfPlayer.setZOrderMediaOverlay(true);
        timer = (Chronometer) findViewById(R.id.timer);
        targetPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isTalking){
                    findViewById(R.id.talking_view).setVisibility(findViewById(R.id.talking_view).getVisibility()==View.VISIBLE?View.INVISIBLE:View.VISIBLE);
                }
            }
        });

        ((TextView)findViewById(R.id.targetid_text)).setText(targetId);
        findViewById(R.id.head_bg).setBackgroundColor(ColorUtils.getColor(VoipActivity.this,targetId));
        ((CircularCoverView)findViewById(R.id.head_cover)).setCoverColor(Color.parseColor("#000000"));
        int cint = DensityUtils.dip2px(VoipActivity.this,45);
        ((CircularCoverView)findViewById(R.id.head_cover)).setRadians(cint, cint, cint, cint,0);

        findViewById(R.id.calling_hangup).setOnClickListener(this);
        findViewById(R.id.talking_hangup).setOnClickListener(this);
        findViewById(R.id.switch_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                voipManager.switchCamera();
            }
        });
        findViewById(R.id.screen_btn).setOnClickListener(this);
        voipManager.setupView(this,selfPlayer, targetPlayer, new IXHResultCallback() {
//        voipManager.setupView(this,null, targetPlayer, new IXHResultCallback() {
            @Override
            public void success(Object data) {
                MLOC.d("newVoip","setupView success");
                if(action.equals(CALLING)){
                    MLOC.d("newVoip","call");
                    voipManager.call(targetId, new IXHResultCallback() {
                        @Override
                        public void success(Object data) {
                            MLOC.d("newVoip","call success");
                        }
                        @Override
                        public void failed(String errMsg) {
                            MLOC.d("newVoip","call failed");
                            stopAndFinish();
                        }
                    });
                }else{
                    MLOC.d("newVoip","onPickup");
                    onPickup();
                }
            }

            @Override
            public void failed(String errMsg) {
                MLOC.d("newVoip","setupView failed");
                stopAndFinish();
            }
        });
        if(action.equals(CALLING)){
            showCallingView();
        }

    }

    public void addListener(){
        AEvent.addListener(AEvent.AEVENT_VOIP_INIT_COMPLETE,this);
        AEvent.addListener(AEvent.AEVENT_VOIP_REV_BUSY,this);
        AEvent.addListener(AEvent.AEVENT_VOIP_REV_REFUSED,this);
        AEvent.addListener(AEvent.AEVENT_VOIP_REV_HANGUP,this);
        AEvent.addListener(AEvent.AEVENT_VOIP_REV_CONNECT,this);
        AEvent.addListener(AEvent.AEVENT_VOIP_REV_ERROR,this);
    }

    public void removeListener(){
        MLOC.canPickupVoip = true;
        AEvent.removeListener(AEvent.AEVENT_VOIP_INIT_COMPLETE,this);
        AEvent.removeListener(AEvent.AEVENT_VOIP_REV_BUSY,this);
        AEvent.removeListener(AEvent.AEVENT_VOIP_REV_REFUSED,this);
        AEvent.removeListener(AEvent.AEVENT_VOIP_REV_HANGUP,this);
        AEvent.removeListener(AEvent.AEVENT_VOIP_REV_CONNECT,this);
        AEvent.removeListener(AEvent.AEVENT_VOIP_REV_ERROR,this);
    }

    @Override
    public void onResume(){
        super.onResume();
        MLOC.canPickupVoip = false;
        HistoryBean historyBean = new HistoryBean();
        historyBean.setType(CoreDB.HISTORY_TYPE_VOIP);
        historyBean.setLastTime(new SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date()));
        historyBean.setConversationId(targetId);
        historyBean.setNewMsgCount(1);
        MLOC.setHistory(historyBean,true);
    }

    @Override
    public void onPause(){
        super.onPause();
    }

    @Override
    public void onRestart(){
        super.onRestart();
        addListener();
    }

    @Override
    public void onDestroy(){
        removeListener();
        super.onDestroy();
    }

    @Override
    public void onBackPressed(){
        new AlertDialog.Builder(VoipActivity.this).setCancelable(true)
                .setTitle("是否挂断?")
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {

                    }
                }).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        timer.stop();
                        voipManager.hangup(new IXHResultCallback() {
                            @Override
                            public void success(Object data) {
                                removeListener();
                                stopAndFinish();
                            }

                            @Override
                            public void failed(final String errMsg) {
                                MLOC.d("","AEVENT_VOIP_ON_STOP errMsg:"+errMsg);
                                MLOC.showMsg(VoipActivity.this,errMsg);
                            }
                        });
                    }
                 }
        ).show();
    }

    @Override
    public void dispatchEvent(String aEventID, boolean success, final Object eventObj) {
        super.dispatchEvent(aEventID,success,eventObj);
        switch (aEventID){
            case AEvent.AEVENT_VOIP_REV_BUSY:
                MLOC.d("","对方线路忙");
                MLOC.showMsg(VoipActivity.this,"对方线路忙");
                stopAndFinish();
                break;
            case AEvent.AEVENT_VOIP_REV_REFUSED:
                MLOC.d("","对方拒绝通话");
                MLOC.showMsg(VoipActivity.this,"对方拒绝通话");
                stopAndFinish();
                break;
            case AEvent.AEVENT_VOIP_REV_HANGUP:
                MLOC.d("","对方已挂断");
                MLOC.showMsg(VoipActivity.this,"对方已挂断");
                timer.stop();
                stopAndFinish();
                break;
            case AEvent.AEVENT_VOIP_REV_CONNECT:
                MLOC.d("","对方允许通话");
                showTalkingView();
                break;
            case AEvent.AEVENT_VOIP_REV_ERROR:
                MLOC.d("",(String) eventObj);
                stopAndFinish();
                break;
        }
    }


    private void showCallingView(){
        findViewById(R.id.calling_view).setVisibility(View.VISIBLE);
        findViewById(R.id.talking_view).setVisibility(View.INVISIBLE);
    }

    private void showTalkingView(){
        isTalking = true;
        findViewById(R.id.calling_view).setVisibility(View.INVISIBLE);
        findViewById(R.id.talking_view).setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) findViewById(R.id.talking_view).getLayoutParams();
        flp.width = findViewById(R.id.calling_view).getWidth();
        findViewById(R.id.talking_view).setLayoutParams(flp);
        timer.setBase(SystemClock.elapsedRealtime());
        timer.start();
    }

    private void onPickup(){
        voipManager.accept(targetId, new IXHResultCallback() {
            @Override
            public void success(Object data) {
                MLOC.d("newVoip","onPickup OK ");
            }
            @Override
            public void failed(String errMsg) {
                MLOC.d("newVoip","onPickup failed ");
                stopAndFinish();
            }
        });
        showTalkingView();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.calling_hangup:
                voipManager.cancel(new IXHResultCallback() {
                    @Override
                    public void success(Object data) {
                        stopAndFinish();
                    }

                    @Override
                    public void failed(String errMsg) {
                        stopAndFinish();
                    }
                });
                break;
            case R.id.talking_hangup:
                voipManager.hangup(new IXHResultCallback() {
                    @Override
                    public void success(Object data) {
                        stopAndFinish();
                    }

                    @Override
                    public void failed(String errMsg) {
                        stopAndFinish();
                    }
                });
                break;
            case R.id.screen_btn:
                if(!XHCustomConfig.getInstance().getHardwareEnable()){
                    MLOC.showMsg(this,"需要打开硬编模式");
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if(mMediaProjectionManager==null){
                        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                    }
                    if (mRecorder != null) {
    //                    ((TextView)findViewById(R.id.screen_btn)).setText("屏");
                        findViewById(R.id.screen_btn).setSelected(false);
                        mRecorder.quit();
                        mRecorder = null;
                        StarRtcCore.getInstance().voipShareCamera();
                    } else {
                        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
                        startActivityForResult(captureIntent, REQUEST_CODE);
                    }
                }else{
                    MLOC.showMsg(this,"系统版本过低，无法使用录屏功能");
                }
                break;
        }
    }

    private static final int REQUEST_CODE = 1;
    private MediaProjectionManager mMediaProjectionManager;
    private ScreenRecorder mRecorder;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e("@@", "media projection is null");
            return;
        }
        findViewById(R.id.screen_btn).setSelected(true);

        // video size
        final int width = StarRtcCore.bigVideoW;
        final int height = StarRtcCore.bigVideoH;
        final int bitrate = StarRtcCore.bitRateBig*1000;

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        final int dpi = metrics.densityDpi;
        mRecorder = new ScreenRecorder(width, height, bitrate, dpi, mediaProjection);
        StarRtcCore.getInstance().voipShareScreen(mRecorder);
    }

    private void stopAndFinish(){
        if(starRTCAudioManager !=null){
            starRTCAudioManager.stop();
        }
        VoipActivity.this.finish();
    }

}
