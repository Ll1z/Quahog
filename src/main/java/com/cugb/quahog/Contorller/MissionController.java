package com.cugb.quahog.Contorller;


import com.cugb.quahog.VideoService.VideoFrameProcessor;
import org.bytedeco.javacv.Frame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.cugb.quahog.Pojo.Result;


@RestController
@RequestMapping("/api")
public class MissionController {

    private VideoFrameProcessor vfp;

    @GetMapping("/start")
    public Result MissionStart(String pull_url) throws Exception {
        vfp = new VideoFrameProcessor(pull_url);
        vfp.start();

        new Thread(() -> {
            while (true) {
                try {
                    Frame frame = vfp.getFrame();
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }).start();

        Thread.sleep(10000);
        vfp.stop();
        System.out.println("Video Reading Complete");
        return Result.success("");
    }

    @PostMapping("/stop")
    public void MissionStop() {

    }

    @PostMapping("/stop")
    public void MissionStopFORCE() {
        vfp.stopNOW();
    }
}
