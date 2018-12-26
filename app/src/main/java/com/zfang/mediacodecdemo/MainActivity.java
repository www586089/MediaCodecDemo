package com.zfang.mediacodecdemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.zfang.mediacodecdemo.util.MediaParseUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
//        TextView tv = (TextView) findViewById(R.id.sample_text);
//        tv.setText(stringFromJNI());
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.extractVideo:
                try {
                    MediaParseUtil.extractVideo("/sdcard/MediaCodecDemo/3.mp4", "/sdcard/MediaCodecDemo/3Video.mp4");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.extractAudio:
                try {
                    MediaParseUtil.extractMusic("/sdcard/MediaCodecDemo/3.mp4", "/sdcard/MediaCodecDemo/3Audio.mp4");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.unio2Video:
                try {
                    MediaParseUtil.union2Video("/sdcard/MediaCodecDemo/3Video.mp4", "/sdcard/MediaCodecDemo/3Audio.mp4",
                            "/sdcard/MediaCodecDemo/3VideoNew.mp4");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.mergeVideos:
                List<String> videoPath = new ArrayList<>();
                videoPath.add("/sdcard/MediaCodecDemo/1.mp4");
                videoPath.add("/sdcard/MediaCodecDemo/2.mp4");
                videoPath.add("/sdcard/MediaCodecDemo/3.mp4");
                try {
                    MediaParseUtil.mergeVideo(videoPath, "/sdcard/MediaCodecDemo/123Video.mp4");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.speedPlay:
                try {
                    MediaParseUtil.playXSpeed("/sdcard/MediaCodecDemo/speed.mp4", 2, "/sdcard/MediaCodecDemo/playSpeed2.mp4");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.addSubtitle:
                try {
                    MediaParseUtil.addSubtitles("/sdcard/MediaCodecDemo/subtitle.mp4", "/sdcard/MediaCodecDemo/subtitleTest.mp4", "字幕测试");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.videoClipHeader:
                try {
                    long duration = 5 * 1000 * 1000;
                    MediaParseUtil.videoClipHeader("/sdcard/MediaCodecDemo/clip.mp4", "/sdcard/MediaCodecDemo/clipHeader.mp4", duration);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.videoClipTail:
                try {
                    long duration = 5 * 1000 * 1000;
                    MediaParseUtil.videoClipTail("/sdcard/MediaCodecDemo/clip.mp4", "/sdcard/MediaCodecDemo/clipTail.mp4", duration);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.videoClip:
                try {
                    long startTime = 4 * 1000 * 1000;
                    long endTime = 9 * 1000 * 1000;
                    MediaParseUtil.videoClip("/sdcard/MediaCodecDemo/clip.mp4", "/sdcard/MediaCodecDemo/clipResult.mp4", startTime, endTime);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;


            case R.id.videoCrop:
                try {
                    MediaParseUtil.videoCrop("/sdcard/MediaCodecDemo/original.mp4", "/sdcard/MediaCodecDemo/crop.mp4");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.videoRotate:
                try {
                    long startTime = 4 * 1000 * 1000;
                    long endTime = 9 * 1000 * 1000;
                    MediaParseUtil.videoClip("/sdcard/MediaCodecDemo/clip.mp4", "/sdcard/MediaCodecDemo/clipResult.mp4", startTime, endTime);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }
    }
}
