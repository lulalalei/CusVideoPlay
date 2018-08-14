package com.example.videoplaysdk;

import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.example.cans.SDKConstant;
import com.example.utils.Utils;

/**
 * 业务逻辑层
 * Created by Administrator on 2018/5/10.
 */

public class VideoAdSlot implements CustomVideoView.ADVideoPlayerListener {

    private Context mContext;

    private CustomVideoView mVideoView;
    private ViewGroup mParentView;

    private AdValue mXAdInstance;
    private AdSDKSlotListener mSlotListener;
    private boolean canPause=false;//是否可自动暂停标志位
    private int lastArea=0;//防止将要滑入滑出时播放器的状态改变

    public VideoAdSlot(Context mContext, AdValue mXAdInstance, AdSDKSlotListener mSlotListener, CustomVideoView.ADFrameImageLoadListener frameLoadListener) {
        this.mContext = mContext;
        this.mXAdInstance = mXAdInstance;
        this.mSlotListener = mSlotListener;
        mParentView=mSlotListener.getAdParent();
        initVideoView(frameLoadListener);
    }

    private void initVideoView(CustomVideoView.ADFrameImageLoadListener frameImageLoadListener) {
        mVideoView = new CustomVideoView(mContext, mParentView);
        if (mXAdInstance != null) {
            mVideoView.setDataSource(mXAdInstance.resource);
            mVideoView.setFrameURI(mXAdInstance.thumb);
            mVideoView.setFrameLoadListener(frameImageLoadListener);
            mVideoView.setListener(this);
        }
        RelativeLayout paddingView = new RelativeLayout(mContext);
        paddingView.setBackgroundColor(mContext.getResources().getColor(android.R.color.black));
        paddingView.setLayoutParams(mVideoView.getLayoutParams());
        mParentView.addView(paddingView);
        mParentView.addView(mVideoView);
    }

    private boolean isPlaying() {
        if (mVideoView != null) {
            return mVideoView.isPlaying();
        }
        return false;
    }

    private boolean isRealPause() {
        if (mVideoView != null) {
            return mVideoView.isRealPause();
        }
        return false;
    }

    private boolean isComplete() {
        if (mVideoView != null) {
            return mVideoView.isComplete();
        }
        return false;
    }

    /*
    * 获取播放器当前秒数
    * */
    private int getPosition() {
        return mVideoView.getCurrentPosition() / SDKConstant.MILLION_UNIT;
    }

    private int getDuration() {
        return mVideoView.getDuration() / SDKConstant.MILLION_UNIT;
    }

    /*
    * 实现滑入播放,滑出暂停的功能
    * */
    public void updateVideoInScrollView(){
        int currentArea= Utils.getVisiblePercent(mParentView);
        //小于0表示未出现在屏幕上,不做任何处理
        if (currentArea<=0){
            return;
        }
        //刚要滑入和滑出时,异常状态的处理
        if(Math.abs(currentArea-lastArea)>=100){
            return;
        }
        if (currentArea<SDKConstant.VIDEO_SCREEN_PERCENT){
            //进入自动暂停状态
            if (canPause){
                pauseVideo(true);
                canPause=false;
            }
            lastArea=0;
            mVideoView.setIsComplete(false);
            mVideoView.setIsRealPause(false);
            return;
        }
        if (isRealPause()||isComplete()){
            if (canPause){
                pauseVideo(false);
                canPause=false;
            }
            return;
        }
        if (Utils.canAutoPlay(mContext,SDKConstant.getCurrentSetting())){
            lastArea = currentArea;
            resumeVideo();
            canPause = true;
            mVideoView.setIsRealPause(false);
            mVideoView.setIsComplete(false);
        }else {
            pauseVideo(false);
            mVideoView.setIsRealPause(true); //不能自动播放则设置为手动暂停效果
            mVideoView.setIsComplete(true);
        }
    }

    //pause the  video
    private void pauseVideo(boolean isAuto) {
        if (mVideoView != null) {
            /*if (isAuto) {
                //发自动暂停监测
                if (!isRealPause() && isPlaying()) {
                    try {
                        ReportManager.pauseVideoReport(mXAdInstance.event.pause.content, getPosition());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }*/
            mVideoView.seekAndPause(0);
        }
    }

    //resume the video
    private void resumeVideo() {
        if (mVideoView != null) {
            mVideoView.resume();
            /*if (isPlaying()) {
                sendSUSReport(true); //发自动播放监测
            }*/
        }
    }

    @Override
    public void onBufferUpdate(int time) {
        //可以通知服务器
    }

    @Override
    public void onClickFullScreenBtn() {
        //获取videoview在当前界面的属性
        Bundle bundle = Utils.getViewProperty(mParentView);
        //将播放器从View树种移除
        mParentView.removeView(mVideoView);
        //创建全屏dialog
        VideoFullDialog videoFullDialog = new VideoFullDialog(mContext, mVideoView, mXAdInstance, mVideoView.getCurrentPosition());
        videoFullDialog.setmListener(new VideoFullDialog.FullToSmallListener() {
            @Override
            public void getCurrentPlayPosition(int position) {
                //在全屏模式点了返回键
                backToSmallMode(position);
            }

            @Override
            public void playComplete() {
                //全屏播放完成以后的事件回调
                bigPlayComplete();
            }
        });
        videoFullDialog.setViewBundle(bundle); //为Dialog设置播放器数据Bundle对象
        videoFullDialog.setSlotListener(mSlotListener);
        videoFullDialog.show();
    }

    /*
    * 全屏播放结束后的回调
    * */
    private void bigPlayComplete() {
        if (mVideoView.getParent()==null){
            mParentView.addView(mVideoView);
        }
        mVideoView.setTranslationY(0); //防止动画导致偏离父容器
        mVideoView.isShowFullBtn(true);//显示我们的全屏按钮
        mVideoView.mute(true);//小屏静音播放
        mVideoView.setListener(this);//重新设置监听给我们的业务逻辑层
        mVideoView.seekAndPause(0);
        canPause=false;
    }

    //返回小屏的时候
    private void backToSmallMode(int position) {
        if (mVideoView.getParent()==null){
            mParentView.addView(mVideoView);
        }
        mVideoView.setTranslationY(0); //防止动画导致偏离父容器
        mVideoView.isShowFullBtn(true);//显示我们的全屏按钮
        mVideoView.mute(true);//小屏静音播放
        mVideoView.setListener(this);//重新设置监听给我们的业务逻辑层
        mVideoView.seekAndResume(position);
        canPause = true; // 标为可自动暂停
    }

    @Override
    public void onClickVideo() {
        //跳转到webview页面
    }

    @Override
    public void onClickBackBtn() {

    }

    @Override
    public void onClickPlay() {

    }

    @Override
    public void onAdVideoLoadSuccess() {
        if (mSlotListener!=null){
            mSlotListener.onAdVideoLoadSuccess();
        }
    }

    @Override
    public void onAdVideoLoadFailed() {
        if (mSlotListener != null) {
            mSlotListener.onAdVideoLoadFailed();
        }
        //加载失败全部回到初始状态
        canPause = false;
    }

    @Override
    public void onAdVideoLoadComplete() {
        /*播放结束,可以通知接口
        try {
            ReportManager.sueReport(mXAdInstance.endMonitor, false, getDuration());
        } catch (Exception e) {
            e.printStackTrace();
        }*/
        if (mSlotListener != null) {
            mSlotListener.onAdVideoLoadComplete();
        }
        mVideoView.setIsRealPause(true);
    }

    //传递消息到appcontext层
    public interface AdSDKSlotListener {

        public ViewGroup getAdParent();

        public void onAdVideoLoadSuccess();

        public void onAdVideoLoadFailed();

        public void onAdVideoLoadComplete();

        public void onClickVideo(String url);
    }
}
