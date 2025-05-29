package com.cugb.quahog.VideoService;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class VideoFrameProcessor {

    private final FFmpegFrameGrabber frameGrabber;
    private final LinkedBlockingQueue<Frame> frameQueue;
    private final ExecutorService executor;
    private volatile boolean isRunning = true;


    public VideoFrameProcessor(String pull_url) throws Exception{
        frameGrabber = new FFmpegFrameGrabber(pull_url);
        frameGrabber.start();
        frameQueue = new LinkedBlockingQueue<Frame>(1000);

        //executor = Executors.newCachedThreadPool();
        executor = Executors.newFixedThreadPool(30);
    }

    public void start() {
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
    }
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
    public void stopNOW() {
        executor.shutdownNow();
    }

    public Frame getFrame() throws InterruptedException {
        return frameQueue.take();
    }

}
