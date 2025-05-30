package com.cugb.quahog.VideoService.Impl;

import com.cugb.quahog.Pojo.Result;
import com.cugb.quahog.VideoService.VideoFrameProcessor;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class VideoFrameProcessorImpl implements VideoFrameProcessor {

    private FFmpegFrameGrabber frameGrabber;
    private LinkedBlockingQueue<Frame> frameQueue;
    private ExecutorService executor;
    private volatile boolean isRunning = true;

    @Override
    public Result start(String pull_url) throws Exception {
        try{
            frameGrabber = new FFmpegFrameGrabber(pull_url);
            frameGrabber.start();
        }catch (Exception e){
            return Result.error("视频流无法读取");
        }
        frameQueue = new LinkedBlockingQueue<Frame>(1000);
        //executor = Executors.newCachedThreadPool();
        executor = Executors.newFixedThreadPool(30);
        isRunning = true;
        executor.submit(() -> {
            int num = 0;
            while (isRunning) {
                try{
                    Frame frame = frameGrabber.grab();
                    if (frame != null) {
                        frameQueue.add(frame);
                        System.out.println("Reading frame: " + num++);
                    }else{
                        System.out.println("No more frames available or net error");
                        break;
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });
        return Result.success();
    }

    @Override
    public void stop() throws InterruptedException {
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        isRunning = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)){
                executor.shutdownNow();
            }
        }catch (InterruptedException e){
            executor.shutdownNow();
        }
        try {
            frameGrabber.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopNOW() {
        executor.shutdownNow();
    }

    @Override
    public Frame getFrame() throws InterruptedException {
        return frameQueue.take();
    }

}
