package com.cugb.quahog.VideoTestVisible;

import com.cugb.quahog.Configuration.AppConfigurationProperties;
import com.cugb.quahog.VideoService.VideoFrameProcessorTestVisible;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;

public class WatchThem {
    private static OpenCVFrameConverter.ToMat Converter;
    public static void main(String[] args) throws Exception {
        VideoFrameProcessorTestVisible vfp = new VideoFrameProcessorTestVisible("https://resources.ucanfly.com.cn:8081/live/live/2.flv");
        Converter = new OpenCVFrameConverter.ToMat();
        vfp.start();

        int num = 0;
        while (true) {
            try {
                Frame frame = vfp.getFrame();
                try{
                    opencv_imgcodecs.imwrite("D:\\IDEAProj\\Quahog\\src\\main\\java\\com\\cugb\\quahog\\Preview", Converter.convert(frame));
                }catch (Exception e){
                    e.printStackTrace();
                }
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
