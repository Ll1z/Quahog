package com.cugb.quahog.VideoService.Impl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.cugb.quahog.Pojo.Result;
import com.cugb.quahog.VideoService.VideoFrameProcessor;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgproc;
//import org.opencv.core.Mat;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
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
    private OpenCVFrameConverter.ToMat Converter;
    private int height;
    private int width;
    private double rate;
    private int channels = 3;
    private int batches = 1;
    private String push_url = "rtmp://202.204.101.80:8082/live/202501?secret=1f146fe5c855d09fdb8e59d203a9fe9e";


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
        System.out.println("InitAndStart");
        try{
            frameGrabber = new FFmpegFrameGrabber(pull_url);
            frameGrabber.start();
        }catch (Exception e){
            return Result.error("Initializing Wrong: Fail to Start FrameGrabber");
        }
        height = frameGrabber.getImageHeight();
        width = frameGrabber.getImageWidth();
        rate = frameGrabber.getFrameRate();
        //Resource resource = new ClassPathResource("./fire_20250315173627A009.onnx");
        env = OrtEnvironment.getEnvironment();
        System.out.println(env.toString());
        session = env.createSession(model_path);
        frameQueue = new LinkedBlockingQueue<Frame>(1000);
        //executor = Executors.newCachedThreadPool();
        numThreads = 30;
        executor = Executors.newFixedThreadPool(numThreads);
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

        System.out.println("Pulling stream");
        Result r = PullStream();

        return Result.success();
    }


    @Override
    public Result PullStream() throws Exception {

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    FrameDetect();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
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
    public void FrameDetect() throws Exception {
        while (true) {
            try {
                Frame frame = frameQueue.take();
                Mat processedMat = preprocessFrame(frame);
                OnnxTensor inputTensor = convertMatToOnnxTensor(processedMat);
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("123", inputTensor);
                OrtSession.Result result = session.run(inputs);
                Map<String, Object> detections = postprocess(result);
                Mat FuseResult = drawAndCount(processedMat, detections);
                recorder.record(Converter.convert(FuseResult));
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

    @Override
    public Result PushStream() throws Exception {

        return null;
    }


    private Mat preprocessFrame(Frame frame) {
        Mat mat = Converter.convert(frame);
        //opencv_imgproc.resize();

        return mat;
    }
    private OnnxTensor convertMatToOnnxTensor(Mat mat) throws Exception {
        FloatBuffer buffer = mat.createBuffer();
        long[] shape = new long[]{1, channels, height, width}; // 示例形状
        return OnnxTensor.createTensor(env, buffer, shape);
    }

    private Map<String, Object> postprocess(OrtSession.Result result) throws OrtException {
        Map<String, Object> detections = new HashMap<>();

        // 解析模型输出，获取边界框、置信度、类别ID
        // 示例：假设输出包含边界框、置信度和类别ID的Tensor
        float[][][] boundingBoxes = (float[][][]) result.get(0).getValue(); // 根据模型调整索引
        float[][] confidenceScores = (float[][]) result.get(1).getValue();
        int[][] classIds = (int[][]) result.get(2).getValue();

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
                    opencv_imgproc.rectangle(frame, new org.bytedeco.opencv.opencv_core.Point((int) x1, (int) y1), new org.bytedeco.opencv.opencv_core.Point((int) x2, (int) y2), new Scalar(0, 255, 0,0));

                    int classId = classIds[i][j];
                    float confidence = confidenceScores[i][j];
                    String label = "Class " + classId + ": " + String.format("%.2f", confidence);
                    opencv_imgproc.putText(frame, label, new org.bytedeco.opencv.opencv_core.Point((int) x1, (int)y1 - 10), 0, 0.5, new Scalar(0, 0, 255,0));
                }
            }
        }

        // 在原始Mat帧上绘制类别:数量
        int y = 20;
        for (Map.Entry<Integer, Integer> entry : classCounts.entrySet()) {
            String countLabel = "Class " + entry.getKey() + ": " + entry.getValue();
            opencv_imgproc.putText(frame, countLabel, new org.bytedeco.opencv.opencv_core.Point(10, y), 0, 0.5, new Scalar(255, 0, 0,0));
            y += 20;
        }

        return frame;
    }

    @Override
    public Result close() throws Exception {
        try {
            frameGrabber.stop();
            recorder.stop();
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
            frameQueue.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.success();
    }

}
