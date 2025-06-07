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
    private boolean HasBeenRunned = false;

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
        if (HasBeenRunned) {
            return Result.error("Mission already running");
        }
        HasBeenRunned = true;
        vfp.InitAndStart(pull_url);
        vfp.PushStream();

        return Result.success("");
    }

    @GetMapping("/stop")
    public Result MissionStop() throws Exception {
        vfp.close();
        HasBeenRunned = false;
        return Result.success("任务结束");
    }
    @GetMapping("/restart")
    public Result MissionRestart(String pull_url) throws Exception {
        vfp.close();
        HasBeenRunned = false;
        if (HasBeenRunned) {
            return Result.error("Mission already running");
        }
        HasBeenRunned = true;
        vfp.InitAndStart(pull_url);
        vfp.PushStream();
        return Result.success("重开");
    }

    @PostMapping("/stopF")
    public void MissionStopFORCE() {
        vfp.stopNOW();
    }
}
