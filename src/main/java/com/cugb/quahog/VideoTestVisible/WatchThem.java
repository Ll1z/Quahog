package com.cugb.quahog.VideoTestVisible;

import com.cugb.quahog.Configuration.AppConfigurationProperties;
import com.cugb.quahog.VideoService.VideoFrameProcessorTestVisible;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;

public class WatchThem {
    public static void main(String[] args) throws Exception {
        VideoFrameProcessorTestVisible vfp = new VideoFrameProcessorTestVisible("https://resources.ucanfly.com.cn:8081/live/live/2.flv");
        vfp.start();
        vfp.stop();
    }
}
