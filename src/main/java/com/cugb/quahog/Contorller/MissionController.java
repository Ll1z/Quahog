package com.cugb.quahog.Contorller;


import com.cugb.quahog.VideoService.VideoFrameProcessor;
import org.bytedeco.javacv.Frame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import com.cugb.quahog.Pojo.Result;


@RestController
@RequestMapping("/mission")
public class MissionController {

    @Autowired
    private VideoFrameProcessor vfp;

    @GetMapping("/start")
    public Result MissionStart(String pull_url) throws Exception {

        //Request parameters validation
        if (pull_url == null || pull_url.isEmpty()) {
            return Result.error("Unexpected Stream Pull Url");
        }
        Result a = vfp.start(pull_url);
        if (a.getCode() == 1) {
            return Result.error(a.getMessage());
        }


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

    @GetMapping("/startnew")
    public Result MissionStartNew(String pull_url) throws Exception {
        System.out.println("/mission/starter");
        vfp.InitAndStart(pull_url);
        vfp.PushStream();
        vfp.close();

        return Result.success("");
    }

    @PostMapping("/stop")
    public void MissionStop() {

    }

    @PostMapping("/stopF")
    public void MissionStopFORCE() {
        vfp.stopNOW();
    }
}
