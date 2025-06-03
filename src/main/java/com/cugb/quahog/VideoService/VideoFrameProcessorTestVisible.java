package com.cugb.quahog.VideoService;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class VideoFrameProcessorTestVisible {

    private final FFmpegFrameGrabber frameGrabber;
    private LinkedBlockingQueue<Frame> frameQueue;
    private final ExecutorService executor;
    private volatile boolean isRunning = true;
    private final CanvasFrame canvas = new CanvasFrame("Video Player");
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private FFmpegFrameRecorder recorder;
    private OrtEnvironment env;
    private OrtSession session;
    private int numThreads;
    private String model_path = "./fire_20250315173627A009.onnx";
    private OpenCVFrameConverter.ToMat Converter;
    private int height;
    private int width;
    private double rate;
    private int channels = 3;
    private int batches = 1;
    private String push_url = "rtmp://202.204.101.80:8082/live/202502?secret=1f146fe5c855d09fdb8e59d203a9fe9e";


    public VideoFrameProcessorTestVisible(String pull_url) throws Exception{
        frameGrabber = new FFmpegFrameGrabber(pull_url);
        frameGrabber.start();
        canvas.setDefaultCloseOperation(CanvasFrame.EXIT_ON_CLOSE);
        frameQueue = new LinkedBlockingQueue<Frame>(10000);

        //executor = Executors.newCachedThreadPool();
        executor = Executors.newFixedThreadPool(1);
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
    public void start() throws Exception {
        isRunning = true;
        height = frameGrabber.getImageHeight();
        width = frameGrabber.getImageWidth();
        rate = frameGrabber.getFrameRate();
        frameQueue = new LinkedBlockingQueue<Frame>(1000);
        //executor = Executors.newCachedThreadPool();
        numThreads = 30;
        Converter = new OpenCVFrameConverter.ToMat();
        recorder = new FFmpegFrameRecorder(push_url, width, height);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("flv");
        recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
        recorder.setFrameRate(rate);
        // 设置比特率
        recorder.setVideoBitrate(2000000);
        // 设置关键帧间隔
        recorder.setGopSize(60);
        // 设置其他选项以优化低延迟推流
        recorder.setOption("preset", "ultrafast");
        recorder.setOption("tune", "zerolatency");
        recorder.setAudioChannels(0);
        recorder.setSampleFormat(avutil.AV_SAMPLE_FMT_NONE);
        recorder.setAudioBitrate(0);
        //recorder.setAudioCodec(org.bytedeco.ffmpeg.global.);
        recorder.setOption("an", "1"); // 禁用音频
        recorder.start();
        executor.submit(() -> {
            while (isRunning) {
                try{
                    Frame frame = frameGrabber.grab();
                    if (frame != null) {
                        frameQueue.add(frame);
                        recorder.record(frame);
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
