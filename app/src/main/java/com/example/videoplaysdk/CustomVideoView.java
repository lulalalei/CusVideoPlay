package com.example.videoplaysdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.example.cans.SDKConstant;
import com.example.sdkdevelopmentproject.R;
import com.example.utils.LogUtil;
import com.example.utils.Utils;

/**
 * 负责视频播放,暂停,事件分发
 * Created by Administrator on 2018/5/9.
 */

public class CustomVideoView extends RelativeLayout implements View.OnClickListener,MediaPlayer.OnPreparedListener,MediaPlayer.OnInfoListener,
                                MediaPlayer.OnErrorListener,MediaPlayer.OnCompletionListener,MediaPlayer.OnBufferingUpdateListener,TextureView.SurfaceTextureListener{

    private static final String TAG = "CustomVideoView";

    private Context mContext;

    private static final int TIME_MSG=0x01;
    private static final int TIME_INVAL=1000;
    //播放器生命周期状态
    private static final int STATE_ERROR=-1;
    private static final int STATE_IDLE=0;
    private static final int STATE_PLAYING=1;
    private static final int STATE_PAUSING=2;
    //播放失败以后的重试次数
    private static final int LOAD_TOTAL_COUNT=3;

    private ViewGroup mParentContainer;//videoview要添加到的父容器
    private RelativeLayout mPlayerView;
    private TextureView mVideoView;
    private Button mMiniPlayBtn;
    private ImageView mFullBtn;
    private ImageView mLoadingBar;
    private ImageView mFrameView;
    //音量控制器
    private AudioManager audioManager;
    private Surface videoSurface;

    private String mUrl;//要加载的视频地址
    private String mFrameURI;
    private boolean isMute;//是否要静音
    private int mScreenWidth,mDestationHeight;

    /*
    * Status状态保护
    * */
    private boolean canPlay = true;
    private boolean mIsRealPause;
    private boolean mIsComplete;
    private int mCurrentCount;
    private int playerState=STATE_IDLE;//默认处于空闲状态

    private MediaPlayer mediaPlayer;
    private ADVideoPlayerListener listener;
    private ScreenEventReceiver mScreenReceiver;
    private ADFrameImageLoadListener mFrameLoadListener;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TIME_MSG:
                    if (isPlaying()) {
                        //还可以在这里更新progressbar
                        //LogUtils.i(TAG, "TIME_MSG");
                        if (listener!=null){
                            listener.onBufferUpdate(getCurrentPosition());
                        }
                        sendEmptyMessageDelayed(TIME_MSG, TIME_INVAL);
                    }
                    break;
            }
        }
    };


    public CustomVideoView(Context context, ViewGroup mParentContainer) {
        super(context);
        mContext=context;
        //mUrl=path;
        this.mParentContainer = mParentContainer;
        audioManager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        initData();
        initView();
        registerBroadcastReceiver();
    }

    private void registerBroadcastReceiver() {
        if (mScreenReceiver==null){
            mScreenReceiver=new ScreenEventReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            intentFilter.addAction(Intent.ACTION_USER_PRESENT);
            mContext.registerReceiver(mScreenReceiver,intentFilter);
        }
    }

    private void initView() {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mPlayerView = (RelativeLayout) inflater.inflate(R.layout.xadsdk_video_player, this);
        mVideoView = (TextureView) mPlayerView.findViewById(R.id.xadsdk_player_video_textureView);
        mVideoView.setOnClickListener(this);
        mVideoView.setKeepScreenOn(true);
        mVideoView.setSurfaceTextureListener(this);
        initSmallLayoutMode(); //init the small mode
    }

    private void initData() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        manager.getDefaultDisplay().getMetrics(metrics);
        mScreenWidth=metrics.widthPixels;
        mDestationHeight=mScreenWidth*9/16;
    }

    //小模式状态
    private void initSmallLayoutMode() {
        LayoutParams params = new LayoutParams(mScreenWidth, mDestationHeight);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        mPlayerView.setLayoutParams(params);

        mMiniPlayBtn = (Button) mPlayerView.findViewById(R.id.xadsdk_small_play_btn);
        mFullBtn = (ImageView) mPlayerView.findViewById(R.id.xadsdk_to_full_view);
        mLoadingBar = (ImageView) mPlayerView.findViewById(R.id.loading_bar);
        mFrameView = (ImageView) mPlayerView.findViewById(R.id.framing_view);
        mMiniPlayBtn.setOnClickListener(this);
        mFullBtn.setOnClickListener(this);
    }

    /*
    * 在view的显示发生改变时,回调此方法
    * */
    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        LogUtil.d(TAG,"onVisibilityChanged"+visibility);
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && playerState == STATE_PAUSING) {
            if (isRealPause() || isComplete()) {
                pause();
            } else {
                decideCanPlay();
            }
        } else {
            pause();
        }
    }

    public boolean isRealPause() {
        return mIsRealPause;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    /*
    * 一点接收到触摸事件,直接消费,避免与父容器产生事件冲突
    * */
    @Override
    public void onClick(View v) {
        if (v==this.mMiniPlayBtn){
            if (this.playerState==STATE_PAUSING){
                if (Utils.getVisiblePercent(mParentContainer)
                        > SDKConstant.VIDEO_SCREEN_PERCENT) {
                    resume();
                    if (listener!=null){
                        listener.onClickPlay();
                    }
                }
            }else {
                load();
            }
        }else if (v==this.mFullBtn){
            if (listener!=null){
                listener.onClickFullScreenBtn();
            }
        }else if (v==mVideoView){
            if (listener!=null){
                listener.onClickVideo();
            }
        }
    }

    /*
    * 播放器处于就绪状态,可以调用start方法开始视频播放
    * */
    @Override
    public void onPrepared(MediaPlayer mp) {
        LogUtil.d(TAG,"onPrepared");
        mediaPlayer=mp;
        if (mediaPlayer!=null){
            mediaPlayer.setOnBufferingUpdateListener(this);
            mCurrentCount=0;
            //回调加载成功的监听
            if (listener!=null){
                listener.onAdVideoLoadSuccess();
            }
            decideCanPlay();
        }
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return true;
    }

    /*
    * 播放器产生异常时回调
    * */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        this.playerState=STATE_ERROR;
        if (mCurrentCount>=LOAD_TOTAL_COUNT){
            //回调外部,通知失败
            if (listener!=null){
                listener.onAdVideoLoadFailed();
            }
            showPauseOrPlayView(false);
        }
        stop();//去重新加载
        return true;
    }

    /*
    * 在播放器播放完成后回调
    * */
    @Override
    public void onCompletion(MediaPlayer mp) {
        //回调给外部,播放结束
        if (listener!=null){
            listener.onAdVideoLoadComplete();
        }
        setIsComplete(true);
        //区别正在播放完毕的暂停和滑出屏幕的暂停
        setIsRealPause(true);
        playBack();//回到初始状态
    }


    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    /*
    * 标明我们的TextureView处于就绪状态
    * */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        LogUtil.d(TAG,"onSurfaceTextureAvailable");
        //只有当textureview准备完成,才可以加载视频
        videoSurface = new Surface(surface);
        //全屏和小屏切换时,mContainer及其内部的textureview会被重新绘制
        //本方法会被重新调用,需要给mediaPlayer重新设置videoSurface
        checkMediaPlayer();
        mediaPlayer.setSurface(videoSurface);
        load();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        LogUtil.d(TAG,"onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    public void setDataSource(String url){
        this.mUrl=url;
    }

    public void setFrameURI(String url) {
        mFrameURI = url;
    }

    /**
     * true is no voice
     *
     * @param mute
     */
    public void mute(boolean mute) {
        LogUtil.d(TAG, "mute");
        isMute = mute;
        if (mediaPlayer != null && this.audioManager != null) {
            float volume = isMute ? 0.0f : 1.0f;
            mediaPlayer.setVolume(volume, volume);
        }
    }

    /*
    * 加载视频url*/
    public void load(){
        //只有空闲时,才允许播放
        if (this.playerState!=STATE_IDLE){
            return;
        }
        try {
            showLoadingView();
            setCurrentPlayState(STATE_IDLE);
            checkMediaPlayer();//完成播放器的创建
            mediaPlayer.setDataSource(mUrl);
            mediaPlayer.prepareAsync();//异步加载视频
        }catch (Exception e){
            //e.printStackTrace();
            stop();
        }
    }

    private void setCurrentPlayState(int state) {
        playerState = state;
    }

    /*
    * 暂停视频*/
    public void pause(){
        if (this.playerState!=STATE_PLAYING){
            return;
        }
        setCurrentPlayState(STATE_PAUSING);
        if (isPlaying()){
            //真正执行暂停
            mediaPlayer.pause();
        }
        showPauseOrPlayView(false);
        mHandler.removeCallbacksAndMessages(null);
    }

    //全屏不显示暂停状态,后续可以整合，不必单独出一个方法
    public void pauseForFullScreen() {
        if (playerState != STATE_PLAYING) {
            return;
        }
        LogUtil.d(TAG, "do full pause");
        setCurrentPlayState(STATE_PAUSING);
        if (isPlaying()) {
            mediaPlayer.pause();
            if (!this.canPlay) {
                mediaPlayer.seekTo(0);
            }
        }
        mHandler.removeCallbacksAndMessages(null);
    }

    /*
    * 恢复视频播放
    * */
    public void resume(){
        /*if (playerState!=STATE_PAUSING){
            return;
        }*/
        if (!isPlaying()){
            entryResumeState();//置为播放中的值
            showPauseOrPlayView(true);
            mediaPlayer.start();
            mHandler.sendEmptyMessage(TIME_MSG);
        }
    }

    /*
    * 进入播放状态时的状态更新
    * */
    private void entryResumeState() {
        checkMediaPlayer();
        canPlay=true;
        setCurrentPlayState(STATE_PLAYING);
        setIsRealPause(false);
        setIsComplete(false);
    }

    public boolean isPlaying() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            return true;
        }
        return false;
    }

    /*
    * 播放完成后回到初始状态
    * */
    public void playBack(){
        setCurrentPlayState(STATE_PAUSING);
        mHandler.removeCallbacksAndMessages(null);
        if (mediaPlayer!=null){
            mediaPlayer.setOnSeekCompleteListener(null);
            mediaPlayer.seekTo(0);
            mediaPlayer.pause();
        }
        showPauseOrPlayView(false);
    }

    /*
    * 停止状态,不同于pause,停止以后要重新prepare
    * */
    public void stop(){
        LogUtil.d(TAG,"do stop");
        if (this.mediaPlayer!=null){
            this.mediaPlayer.reset();
            this.mediaPlayer.setOnSeekCompleteListener(null);
            this.mediaPlayer.stop();
            this.mediaPlayer.release();
            this.mediaPlayer=null;
        }
        mHandler.removeCallbacksAndMessages(null);
        setCurrentPlayState(STATE_IDLE);

        //尝试重新load
        if (mCurrentCount<LOAD_TOTAL_COUNT){
            mCurrentCount++;
            load();
        }else {
            //停止重试,
            showPauseOrPlayView(false);
        }
    }

    private void showPauseOrPlayView(boolean show) {
        mFullBtn.setVisibility(show ? View.VISIBLE : View.GONE);
        mMiniPlayBtn.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoadingBar.clearAnimation();
        mLoadingBar.setVisibility(View.GONE);
        if (!show) {
            mFrameView.setVisibility(View.VISIBLE);
            loadFrameImage();
        } else {
            mFrameView.setVisibility(View.GONE);
        }
    }

    /*
    * 销毁当前的自定义view*/
    public void destroy(){
        if (mediaPlayer!=null){
            mediaPlayer.setOnSeekCompleteListener(null);
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer=null;
        }
        setCurrentPlayState(STATE_IDLE);
        mCurrentCount=0;
        setIsComplete(false);
        setIsRealPause(false);
        unRegisterBroadcastReceiver();
        mHandler.removeCallbacksAndMessages(null);
        showPauseView(false);
    }

    public int getCurrentPosition() {
        if (this.mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    /*
    * 跳到指定点播放视频
    * */
    public void seekAndResume(int position){
        if (mediaPlayer!=null){
            showPauseView(true);
            entryResumeState();
            mediaPlayer.seekTo(position);
            mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    mediaPlayer.start();
                    mHandler.sendEmptyMessage(TIME_MSG);
                }
            });
        }
    }
    /*
    * 跳到指定点暂停视频
    * */
    public void seekAndPause(int position){
        if (this.playerState != STATE_PLAYING) {
            return;
        }
        showPauseView(false);
        setCurrentPlayState(STATE_PAUSING);
        if (isPlaying()) {
            mediaPlayer.seekTo(position);
            mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    LogUtil.d(TAG, "do seek and pause");
                    mediaPlayer.pause();
                    mHandler.removeCallbacksAndMessages(null);
                }
            });
        }
    }

    public void setListener(ADVideoPlayerListener listener){
        this.listener=listener;
    }

    public void setFrameLoadListener(ADFrameImageLoadListener mFrameLoadListener) {
        this.mFrameLoadListener = mFrameLoadListener;
    }

    private synchronized void checkMediaPlayer(){
        if (mediaPlayer==null){
            mediaPlayer=createMediaPlayer();//每次都重新创建一个新的播放器
        }
    }

    private MediaPlayer createMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.reset();
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        ///////////////////////////////////////////////////////////////////////?
        if (videoSurface != null && videoSurface.isValid()) {
            mediaPlayer.setSurface(videoSurface);
        } else {
            stop();
        }
        return mediaPlayer;
    }

    private void unRegisterBroadcastReceiver(){
        if (mScreenReceiver!=null){
            mContext.unregisterReceiver(mScreenReceiver);
        }
    }

    private void decideCanPlay(){
        if (Utils.getVisiblePercent(mParentContainer) > SDKConstant.VIDEO_SCREEN_PERCENT){
            //来回切换页面时，只有 >50,且满足自动播放条件才自动播放
            resume();
        }
        else{
            pause();
        }
    }

    public void isShowFullBtn(boolean isShow){
        mFullBtn.setImageResource(isShow ? R.mipmap.ic_launcher : R.mipmap.ic_launcher);
        mFullBtn.setVisibility(isShow ? View.VISIBLE : View.GONE);
    }

    public boolean isPauseBtnClicked(){
        return mIsRealPause;
    }

    public boolean isComplete(){
        return mIsComplete;
    }

    public void setIsComplete(boolean isComplete) {
        mIsComplete = isComplete;
    }

    public void setIsRealPause(boolean isRealPause) {
        this.mIsRealPause = isRealPause;
    }

    private void showPauseView(boolean show){
        mFullBtn.setVisibility(show ? View.VISIBLE : View.GONE);
        mMiniPlayBtn.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoadingBar.clearAnimation();
        mLoadingBar.setVisibility(View.GONE);
        if (!show) {
            mFrameView.setVisibility(View.VISIBLE);
            loadFrameImage();
        } else {
            mFrameView.setVisibility(View.GONE);
        }
    }

    private void loadFrameImage() {
        if (mFrameLoadListener != null) {
            mFrameLoadListener.onStartFrameLoad(mFrameURI, new ImageLoaderListener() {
                @Override
                public void onLoadingComplete(Bitmap loadedImage) {
                    if (loadedImage != null) {
                        mFrameView.setScaleType(ImageView.ScaleType.FIT_XY);
                        mFrameView.setImageBitmap(loadedImage);
                    } else {
                        mFrameView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        mFrameView.setImageResource(R.mipmap.ic_launcher);
                    }
                }
            });
        }
    }

    private void showLoadingView(){
        mFullBtn.setVisibility(View.GONE);
        mLoadingBar.setVisibility(View.VISIBLE);
        AnimationDrawable anim = (AnimationDrawable) mLoadingBar.getBackground();
        anim.start();
        mMiniPlayBtn.setVisibility(View.GONE);
        mFrameView.setVisibility(View.GONE);
        loadFrameImage();
    }

    private void showPlayView(){
        mLoadingBar.clearAnimation();
        mLoadingBar.setVisibility(View.GONE);
        mMiniPlayBtn.setVisibility(View.GONE);
        mFrameView.setVisibility(View.GONE);
    }

    /*
    * 监听锁屏事件的广播接收器
    * */
    private class ScreenEventReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            //主动锁屏时 pause,主动解锁屏幕时,resume
            switch (intent.getAction()){
                case Intent.ACTION_USER_PRESENT:
                    if (playerState==STATE_PAUSING){
                        if (mIsRealPause){
                            //播放结束,依然暂停
                            pause();
                        }else {
                            decideCanPlay();
                        }
                    }
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    if (playerState==STATE_PLAYING){
                        pause();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 供slot层来实现具体点击逻辑,具体逻辑还会变，
     * 如果对UI的点击没有具体监测的话可以不回调
     */
    public interface ADVideoPlayerListener {

        public void onBufferUpdate(int time);//视频播放器播放到了第几秒

        public void onClickFullScreenBtn();

        public void onClickVideo();

        public void onClickBackBtn();

        public void onClickPlay();

        public void onAdVideoLoadSuccess();

        public void onAdVideoLoadFailed();

        public void onAdVideoLoadComplete();
    }

    public interface ADFrameImageLoadListener {

        void onStartFrameLoad(String url, ImageLoaderListener listener);
    }

    public interface ImageLoaderListener {
        /**
         * 如果图片下载不成功，传null
         *
         * @param loadedImage
         */
        void onLoadingComplete(Bitmap loadedImage);
    }

}
