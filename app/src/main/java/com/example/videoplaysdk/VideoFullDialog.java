package com.example.videoplaysdk;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.example.sdkdevelopmentproject.R;
import com.example.utils.Utils;

/**
 * Created by Administrator on 2018/5/10.
 */

public class VideoFullDialog extends Dialog implements CustomVideoView.ADVideoPlayerListener{

    private static final String TAG = "VideoFullDialog";

    private CustomVideoView mVideoView;

    private Context mContext;
    private RelativeLayout mRootView;
    private ViewGroup mParentView;
    private ImageView mBackButton;

    private AdValue mXAdInstance;
    private int mPosition;//从小屏到全屏时的视频播放位置
    private FullToSmallListener mListener;
    private boolean isFirst = true;
    //动画要执行的平移值
    private int deltaY;
    private VideoAdSlot.AdSDKSlotListener mSlotListener;
    private Bundle mStartBundle;
    private Bundle mEndBundle; //用于Dialog出入场动画

    public VideoFullDialog(Context context, CustomVideoView mraidView, AdValue instance,
                           int position) {
        super(context, R.style.dialog_full_screen);
        mContext = context;
        mXAdInstance = instance;
        mPosition = position;
        mVideoView = mraidView;
    }

    public void setViewBundle(Bundle bundle) {
        mStartBundle = bundle;
    }

    public void setSlotListener(VideoAdSlot.AdSDKSlotListener slotListener) {
        this.mSlotListener = slotListener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);*/
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.xadsdk_dialog_video_layout);
        initVideoView();

    }

    private void initVideoView() {
        mParentView = (RelativeLayout) findViewById(R.id.content_layout);
        mBackButton = (ImageView) findViewById(R.id.xadsdk_player_close_btn);
        mBackButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onClick(View v) {
                onClickBackBtn();
            }
        });
        mRootView = (RelativeLayout) findViewById(R.id.root_view);
        mRootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickVideo();
            }
        });
        mRootView.setVisibility(View.INVISIBLE);

        mVideoView.setListener(this);
        mVideoView.mute(false);
        mParentView.addView(mVideoView);
        mParentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public boolean onPreDraw() {
                mParentView.getViewTreeObserver().removeOnPreDrawListener(this);
                prepareScene();
                runEnterAnimation();
                return true;
            }
        });
    }

    /*
    * 入场动画
    * */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void runEnterAnimation() {
        mVideoView.animate()
                .setDuration(200)
                .setInterpolator(new LinearInterpolator())
                .translationY(0)
                .withStartAction(new Runnable() {
                    @Override
                    public void run() {
                        mRootView.setVisibility(View.VISIBLE);
                    }
                })
                .start();
    }

    //准备出场动画
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void runExitAnimator() {
        mVideoView.animate()
                .setDuration(200)
                .setInterpolator(new LinearInterpolator())
                .translationY(deltaY)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        dismiss();
                        /*try {
                            ReportManager.exitfullScreenReport(mXAdInstance.event.exitFull.content, mVideoView.getCurrentPosition()
                                    / SDKConstant.MILLION_UNIT);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }*/
                        if (mListener != null) {
                            mListener.getCurrentPlayPosition(mVideoView.getCurrentPosition());
                        }
                    }
                }).start();
    }

    /*
    * 准备动画所需数据
    * */
    private void prepareScene() {
        mEndBundle = Utils.getViewProperty(mVideoView);
        /**
         * 将desationview移到originalview位置处
         */
        deltaY = (mStartBundle.getInt(Utils.PROPNAME_SCREENLOCATION_TOP)
                - mEndBundle.getInt(Utils.PROPNAME_SCREENLOCATION_TOP));
        mVideoView.setTranslationY(deltaY);
    }

    @Override
    public void onBufferUpdate(int time) {

    }

    @Override
    public void onClickFullScreenBtn() {

    }

    @Override
    public void onClickVideo() {

    }

    /*
    * 全屏关闭按钮的点击事件*/
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onClickBackBtn() {
        runExitAnimator();
        /*dismiss();
        if (mListener!=null){
            mListener.getCurrentPlayPosition(mVideoView.getCurrentPosition());
        }*/
    }

    /*
    * 物理返回键的监听
    * */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onBackPressed() {
        onClickBackBtn();
        //super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        //super.onWindowFocusChanged(hasFocus);
        mVideoView.isShowFullBtn(false);
        if (!hasFocus){
            //未获取焦点时的逻辑
            mPosition=mVideoView.getCurrentPosition();
            mVideoView.pause();
            //mVideoView.pauseForFullScreen();
        }else {
            //表明,我们的dialog是首次创建并且首次获得焦点
            if (isFirst){
                mVideoView.seekAndResume(mPosition);
                //mVideoView.resume();
            }else {
                //获取焦点时的逻辑
                mVideoView.resume();//恢复视频播放
            }
        }
        isFirst=false;
    }

    @Override
    public void dismiss() {
        mParentView.removeView(mVideoView);
        super.dismiss();

    }

    @Override
    public void onClickPlay() {

    }

    @Override
    public void onAdVideoLoadSuccess() {

    }

    @Override
    public void onAdVideoLoadFailed() {

    }

    @Override
    public void onAdVideoLoadComplete() {
        dismiss();
        if (mListener!=null){
            mListener.playComplete();
        }
    }

    public void setmListener(FullToSmallListener mListener) {
        this.mListener = mListener;
    }

    /*
        * 与业务逻辑层VideoAdSlot进行通信
        * */
    public interface FullToSmallListener {
        void getCurrentPlayPosition(int position);//全屏播放中点击关闭按钮或者back键时的回调

        void playComplete();//全屏播放结束时回调
    }
}
