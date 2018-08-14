package com.example.sdkdevelopmentproject;

import android.Manifest;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import com.example.MyScrollView;
import com.example.videoplaysdk.AdValue;
import com.example.videoplaysdk.CustomVideoView;
import com.example.videoplaysdk.VideoAdSlot;

public class MainActivity extends AppCompatActivity implements VideoAdSlot.AdSDKSlotListener{

    MyScrollView scrollView;

    RelativeLayout rl_container;

    private VideoAdSlot videoAdSlot;

    String path="http://221.228.226.23/7/l/c/d/f/lcdfwlslqjlpskwbmkvfuapajyvwyb/he.yinyuetai.com/6DF1015CB9F64A11B2D7054C2E060BD8.mp4?sc\\u003d71d94aaa942f7308\\u0026br\\u003d3141\\u0026vid\\u003d2890982\\u0026aid\\u003d37\\u0026area\\u003dHT\\u0026vst\\u003d0 http://221.228.226.23/7/l/c/d/f/lcdfwlslqjlpskwbmkvfuapajyvwyb/he.yinyuetai.com/6DF1015CB9F64A11B2D7054C2E060BD8.mp4?sc\\u003d71d94aaa942f7308\\u0026br\\u003d3141\\u0026vid\\u003d2890982\\u0026aid\\u003d37\\u0026area\\u003dHT\\u0026vst\\u003d0";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //ContextCompat.checkSelfPermission(this, Manifest.permission.INT)
        scrollView=findViewById(R.id.scrollView);
        rl_container=findViewById(R.id.rl_container);
        videoAdSlot = new VideoAdSlot(this, new AdValue(), this, null);
        /*CustomVideoView customVideoView = new CustomVideoView(this, rl_container);
        customVideoView.setDataSource(path);
        rl_container.addView(customVideoView);*/
        scrollView.setListener(new MyScrollView.OnScrollListener() {
            @Override
            public void onScrollListener() {
                videoAdSlot.updateVideoInScrollView();
            }
        });

    }

    @Override
    public ViewGroup getAdParent() {
        return rl_container;
    }

    @Override
    public void onAdVideoLoadSuccess() {

    }

    @Override
    public void onAdVideoLoadFailed() {

    }

    @Override
    public void onAdVideoLoadComplete() {

    }

    @Override
    public void onClickVideo(String url) {

    }
}
