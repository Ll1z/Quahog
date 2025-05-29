package com.cugb.quahog.Contorller;


import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;


@RestController("/api")
public class MissionController {

    @PostMapping("/start")
    public String MissionStart(@RequestParam String pull_url) {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(pull_url);
        try{
            //启动grabber
            grabber.start();
            //获取元数据
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            double frameRate = grabber.getFrameRate();
            System.out.println("视频宽度：" + width + " 像素");
            System.out.println("视频高度：" + height + " 像素");
            System.out.println("原始帧率：" + frameRate + " 帧/秒");
            // 停止抓取器
            grabber.stop();
        }catch (Exception e){
            System.out.println("Wasted");
            e.printStackTrace();
        }
        return "";
    }

    @PostMapping("/stop")
    public void MissionStop() {

    }
}
