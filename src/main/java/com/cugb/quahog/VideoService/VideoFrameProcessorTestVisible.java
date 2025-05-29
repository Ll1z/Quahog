package com.cugb.quahog.VideoService;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class VideoFrameProcessorTestVisible {

    private final FFmpegFrameGrabber frameGrabber;
    private final LinkedBlockingQueue<Frame> frameQueue;
    private final ExecutorService executor;
    private volatile boolean isRunning = true;
    private final CanvasFrame canvas = new CanvasFrame("Video Player");
    private final Java2DFrameConverter converter = new Java2DFrameConverter();


    public VideoFrameProcessorTestVisible(String pull_url) throws Exception{
        frameGrabber = new FFmpegFrameGrabber(pull_url);
        frameGrabber.start();
        canvas.setDefaultCloseOperation(CanvasFrame.EXIT_ON_CLOSE);
        frameQueue = new LinkedBlockingQueue<Frame>(10000);

        //executor = Executors.newCachedThreadPool();
        executor = Executors.newFixedThreadPool(100);
    }
    public void start1(){
        int num = 0;
        while (isRunning) {
            try{
                Frame frame = frameGrabber.grab();
                if (frame != null) {
                    frameQueue.add(frame);
                    System.out.println("Reading frame: " + num++);
                    canvas.showImage(converter.convert(frame));
                }else{
                    System.out.println("No more frames available or net error");
                    break;
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    public void start() {
        isRunning = true;
        executor.submit(() -> {
            while (isRunning) {
                try{
                    Frame frame = frameGrabber.grab();
                    if (frame != null) {
                        frameQueue.add(frame);
                        canvas.showImage(converter.convert(frame));
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
    public void stop1(){
        isRunning = false;
    }
    public void stop() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        isRunning = false;
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

    public Frame getFrame() throws InterruptedException {
        return frameQueue.take();
    }

}
