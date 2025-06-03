package com.cugb.quahog.VideoTestVisible;

import com.cugb.quahog.Configuration.AppConfigurationProperties;
import com.cugb.quahog.VideoService.VideoFrameProcessorTestVisible;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

public class WatchThem {
    public static void main(String[] args) throws Exception {
        VideoFrameProcessorTestVisible vfp = new VideoFrameProcessorTestVisible("https://resources.ucanfly.com.cn:8081/live/live/2.flv");
        vfp.start();

        int num = 0;
        while (true) {
            try {
                Frame frame = vfp.getFrame();
                System.out.println("Reading frame: " + num);
                num++;
                if (frame == null) {
                    System.out.println("no frames any more");
                    break;
                }
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }

        vfp.stop();
    }
}
