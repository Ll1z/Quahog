package com.cugb.quahog.VideoService.Impl;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.cugb.quahog.Pojo.Result;
import com.cugb.quahog.VideoService.VideoFrameProcessor;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.opencv.core.Mat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
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
    private FFmpegFrameRecorder recorder;
    private OrtEnvironment env;
    private OrtSession session;
    private int numThreads;
    private String model_path = "./fire_20250315173627A009.onnx";

    @Override
    public Result start(String pull_url) throws Exception {
        //Try to start frame grabber
        try{
            frameGrabber = new FFmpegFrameGrabber(pull_url);
            frameGrabber.start();
        }catch (Exception e){
            return Result.error("Pulling Wrong: Fail to Start FrameGrabber");
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

    @Override
    public Result InitAndStart(String pull_url) throws Exception {
        //Try to start frame grabber
        try{
            frameGrabber = new FFmpegFrameGrabber(pull_url);
            frameGrabber.start();
        }catch (Exception e){
            return Result.error("Initializing Wrong: Fail to Start FrameGrabber");
        }
        //Resource resource = new ClassPathResource("./fire_20250315173627A009.onnx");
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(model_path);
        frameQueue = new LinkedBlockingQueue<Frame>(1000);
        //executor = Executors.newCachedThreadPool();
        numThreads = 30;
        executor = Executors.newFixedThreadPool(numThreads);

        Result r = PullStream();

        return Result.success();
    }


    @Override
    public Result PullStream() throws Exception {

        for (int i = 0; i < numThreads; i++) {
            executor.submit(this::FrameDetect);
        }

        //Read and Enqueue Frames
        new Thread(() -> {
            try{
                Frame frame;
                while ((frame = frameGrabber.grab()) != null) {
                    if (!frameQueue.offer(frame, 1, TimeUnit.SECONDS)) {
                        System.err.println("Pulling Wrong: Frame queue is full, dropping frame.");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    frameGrabber.stop();
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        });

        return null;
    }

    @Override
    public Result FrameDetect() throws Exception {
        return null;
    }

    @Override
    public Result PushStream() throws Exception {
        return null;
    }


}
