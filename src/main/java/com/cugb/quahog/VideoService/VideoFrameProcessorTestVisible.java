package com.cugb.quahog.VideoService;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.cugb.quahog.Pojo.Result;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.opencv.core.CvType;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
    private String model_path = "D:\\IDEAProj\\Quahog\\src\\main\\resources\\fire_20250315173627A009.onnx";;
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
                    //canvas.showImage(converter.convert(frame));
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
        Converter = new OpenCVFrameConverter.ToMat();
        //opencv_imgcodecs.imwrite("D:\\IDEAProj\\Quahog\\src\\main\\java\\com\\cugb\\quahog\\Preview", Converter.convert(frameGrabber.grab()));
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(model_path);
        frameQueue = new LinkedBlockingQueue<Frame>(1000);
        numThreads = 2;
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
        //recorder.setAudioBitrate(0);
        //recorder.setAudioCodec(org.bytedeco.ffmpeg.global.);
        //recorder.setOption("an", "1"); // 禁用音频
        recorder.start();

        try{
            Frame frame = null;
            while (true) {
                frame = frameGrabber.grabImage();
                if (frame.image != null) {
                    frameQueue.add(frame);
                    FrameDetect();
                }else{
                    System.out.println("No more frames available or net error");
                    continue;
                }
//                    if (frame == null) {
//                        break;
//                    }
                if (!frameQueue.offer(frame, 1, TimeUnit.SECONDS)) {
                    System.err.println("Pulling Wrong: Frame queue is full, dropping frame.");
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void FrameDetect() throws Exception {

        int t =0 ;
        while (t == 0) {
            t = 1;
            try {
                System.out.println("Frame Detect");
                Frame frame = frameQueue.take();
                if (frame.image == null) {
                    System.out.println("----------------------No more image");
                    continue;
                }
                Mat processedMat = preprocessFrame(frame);

                OnnxTensor inputTensor = convertMatToOnnxTensor(processedMat);
                //if (inputTensor == null) {continue;}
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("images", inputTensor);
                OrtSession.Result result = session.run(inputs);
                Map<String, Object> detections = postprocess(result);
                Mat FuseResult = drawAndCount(processedMat, detections);
                //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
                //opencv_imgcodecs.imwrite("D:\\IDEAProj\\magic\\src\\main\\java\\com\\cugb\\quahog\\Preview", FuseResult);
                opencv_imgcodecs.imwrite("D:\\IDEAProj\\magic\\src\\main\\java\\com\\cugb\\quahog\\Preview\\q.jpg", FuseResult);
                recorder.record(Converter.convert(FuseResult));
                System.out.println(Converter.convert(FuseResult).imageWidth);
                System.out.println(Converter.convert(FuseResult).imageHeight);
//                canvas.setSize(640, 640);
//                canvas.showImage(converter.convert(Converter.convert(FuseResult)));
                inputTensor.close();
                processedMat.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
    private Mat preprocessFrame(Frame frame) {
        Mat mat = Converter.convert(frame);
        Mat resize = new Mat();
        opencv_imgproc.resize(mat, resize, new org.bytedeco.opencv.opencv_core.Size(640, 640));
        resize.convertTo(resize, CvType.CV_32FC3);
        //opencv_core.divide(255.0, resize, resize);
//        Mat c = new Mat();
//        opencv_core.transpose(resize, c);
//        resize = c.reshape(1,3);
        return resize;
    }
    private OnnxTensor convertMatToOnnxTensor(Mat mat) throws Exception {
        if (mat == null) {
            return null;
        }
        FloatBuffer f = mat.createBuffer();
        long[] shape = new long[]{1, channels, 640, 640}; // 示例形状
        return OnnxTensor.createTensor(env, f, shape);
    }

    private Map<String, Object> postprocess(OrtSession.Result result) throws OrtException {
        Map<String, Object> detections = new HashMap<>();
        OnnxTensor outputTensor = (OnnxTensor) result.get(0);

        float[][][] outputData = (float[][][]) outputTensor.getValue();
        // 解析模型输出，获取边界框、置信度、类别ID
        // 示例：假设输出包含边界框、置信度和类别ID的Tensor
        int numDetections = outputData.length;
        int n = outputData.length;
        int m = outputData[0].length;
        int s = outputData[0][0].length;
//        System.out.println(n);
//        System.out.println(m);
//        System.out.println(s);
        float[][][] boundingBoxes = new float[s][1][4]; // [x1, y1, x2, y2]
        float[][] confidenceScores = new float[s][1];   // confidence
        int[][] classIds = new int[s][1];
        for (int i = 0; i < n; i++){
            for (int k = 0; k < s; k++){
                float x = outputData[i][0][k];
                float y = outputData[i][1][k];
                float w = outputData[i][2][k];
                float h = outputData[i][3][k];
                float confidence = outputData[i][4][k];
                float class_id = outputData[i][5][k];
                boundingBoxes[k][0][0] = x; // x1
                boundingBoxes[k][0][1] = y; // y1
                boundingBoxes[k][0][2] = w; // x2
                boundingBoxes[k][0][3] = h; // y2
                confidenceScores[k][0] = confidence; // confidence
                classIds[k][0] = (int) class_id;   // classId
            }
        }

        detections.put("boundingBoxes", boundingBoxes);
        detections.put("confidenceScores", confidenceScores);
        detections.put("classIds", classIds);

        return detections;
    }

    private Mat drawAndCount(Mat frame, Map<String, Object> detections) {
        float[][][] boundingBoxes = (float[][][]) detections.get("boundingBoxes");
        float[][] confidenceScores = (float[][]) detections.get("confidenceScores");
        int[][] classIds = (int[][]) detections.get("classIds");

        // 统计当前帧中置信度大于0.7的各类别目标的数量
        Map<Integer, Integer> classCounts = new HashMap<>();
        for (int i = 0; i < confidenceScores.length; i++) {
            for (int j = 0; j < confidenceScores[i].length; j++) {
                if (confidenceScores[i][j] > 0.7) {
                    int classId = classIds[i][j];
                    classCounts.put(classId, classCounts.getOrDefault(classId, 0) + 1);
                }
            }
        }

        // 绘制边界框和类别标签、置信度
        for (int i = 0; i < confidenceScores.length; i++) {
            for (int j = 0; j < confidenceScores[i].length; j++) {
                if (confidenceScores[i][j] > 0.7) {
                    float x1 = boundingBoxes[i][j][0];
                    float y1 = boundingBoxes[i][j][1];
                    float x2 = boundingBoxes[i][j][2];
                    float y2 = boundingBoxes[i][j][3];
                    opencv_imgproc.rectangle(frame, new Point((int) x1, (int) y1), new Point((int) x2, (int) y2), new Scalar(0, 255, 0,0));

                    int classId = classIds[i][j];
                    float confidence = confidenceScores[i][j];
                    String label = "Class " + classId + ": " + String.format("%.2f", confidence);
                    opencv_imgproc.putText(frame, label, new Point((int) x1, (int)y1 - 10), 0, 0.5, new Scalar(0, 0, 255,0));
                }
            }
        }

        // 在原始Mat帧上绘制类别:数量
        int y = 20;
        for (Map.Entry<Integer, Integer> entry : classCounts.entrySet()) {
            String countLabel = "Class " + entry.getKey() + ": " + entry.getValue();
            opencv_imgproc.putText(frame, countLabel, new Point(10, y), 0, 0.5, new Scalar(255, 0, 0,0));
            y += 20;
        }

        return frame;
    }
}
