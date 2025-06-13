package com.cugb.quahog.VideoService.Impl;

import ai.onnxruntime.*;
import com.cugb.quahog.Configuration.AppConfigurationProperties;
import com.cugb.quahog.Logger.MyLogger;
import com.cugb.quahog.Pojo.Result;
import com.cugb.quahog.VideoService.VideoFrameProcessor;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
//import org.opencv.core.Mat;
import org.bytedeco.opencv.opencv_core.Mat;
//import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.javacv.*;

//import org.opencv.core.Core;
//import org.opencv.core.CvType;
//import org.opencv.core.Scalar;
//import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import ch.qos.logback.classic.Logger;
//import ch.qos.logback.classic.spi.ILoggingEvent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

@Service
public class VideoFrameProcessorImpl implements VideoFrameProcessor {



    private AppConfigurationProperties appConfigurationProperties = new AppConfigurationProperties();

    //private Logger logger = new MyLogger(VideoFrameProcessorImpl.class).getLogger();
    private FFmpegFrameGrabber frameGrabber;
    private LinkedBlockingQueue<Frame> frameQueue;
    private ExecutorService executor;
    private volatile boolean isRunning = true;
    private FFmpegFrameRecorder recorder;
    private OrtEnvironment env;
    private OrtSession session;
    private int numThreads;
    @Value("${app.model_path}")
    private String model_path;
    private OpenCVFrameConverter.ToMat Converter;
    private int height;
    private int width;
    private double rate;
    private int channels = 3;
    private int batches = 1;
    @Value("${app.push_url}")
    private String push_url;
    private boolean STOP = false;

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
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);
        return Result.success();
    }

    @Override
    public void stop() throws InterruptedException {
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        isRunning = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)){
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
        STOP = false;
        System.out.println("Initialize and Start");
        try{
            frameGrabber = new FFmpegFrameGrabber(pull_url);
            frameGrabber.start();
        }catch (Exception e){
            return Result.error("Pulling Wrong: Fail to Start FrameGrabber");
        }

        height = frameGrabber.getImageHeight();
        width = frameGrabber.getImageWidth();
        rate = frameGrabber.getFrameRate();
        Converter = new OpenCVFrameConverter.ToMat();
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        //options.addCUDA(0);
        session = env.createSession(model_path, options);
        frameQueue = new LinkedBlockingQueue<Frame>(10000);
        numThreads = 2;
        executor = Executors.newFixedThreadPool(numThreads);
        recorder = new FFmpegFrameRecorder(push_url, width, height);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("flv");
        recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
        recorder.setFrameRate(30);
        // 设置比特率
        recorder.setVideoBitrate(4000000);
        // 设置关键帧间隔
        recorder.setGopSize(30);
        // 设置其他选项以优化低延迟推流
        recorder.setOption("preset", "ultrafast");
        recorder.setOption("tune", "zerolatency");
        recorder.start();
        System.out.println("Pulling stream");
        Result r = PullStream(pull_url);

        return Result.success();
    }


    @Override
    public Result PullStream(String pull_url) throws Exception {
        int t = 1;
        try{
            //Frame frame = null;
            while (!STOP) {
                double starttime = System.nanoTime();
                final Frame frame = frameGrabber.grabImage();
                double endtime = System.nanoTime();
                double duration = endtime - starttime;
                System.out.println("Pull Time: " + duration / 10000000 + "ms");

                if (frame.image != null) {
                    frameQueue.add(frame);
                    starttime = System.nanoTime();
                    FrameDetect();
                    endtime = System.nanoTime();
                    duration = endtime - starttime;
                    System.out.println("Detection Time: " + duration / 10000000 + "ms");
                    System.out.println("Have pushed " + t++ + " frames");

                }else{
                    System.out.println("No more frames available or net error");
                    continue;
                }
                if (!frameQueue.offer(frame, 1, TimeUnit.SECONDS)) {
                    System.err.println("Pulling Wrong: Frame queue is full, dropping frame.");
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void FrameDetect() throws Exception {
        long starttime;
        long endtime;
        long duration;
        int t =0 ;
        while (t == 0) {
            t = 1;
            try {
                Frame frame = frameQueue.take();
                if (frame.image == null) {
                    continue;
                }
                Mat processedMat = preprocessFrame(frame);
                OnnxTensor inputTensor = convertMatToOnnxTensor(processedMat);
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("images", inputTensor);
                OrtSession.Result result = session.run(inputs);
                Map<String, Object> detections = postprocess(result);
                opencv_imgcodecs.imwrite("src/main/java/com/cugb/quahog/Preview/p.jpg", processedMat);
                Mat FuseResult = drawAndCount(processedMat, detections);
                opencv_imgcodecs.imwrite("src/main/java/com/cugb/quahog/Preview/q.jpg", FuseResult);
                Mat image = opencv_imgcodecs.imread("src/main/java/com/cugb/quahog/Preview/q.jpg");
                recorder.record(new OpenCVFrameConverter.ToIplImage().convert(image));
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
        opencv_imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB);
        Mat resize = new Mat();
        opencv_imgproc.resize(mat, resize, new org.bytedeco.opencv.opencv_core.Size(640, 640));
        opencv_core.divide(255, resize);
        resize.convertTo(resize, CvType.CV_32FC3);
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
                if (confidenceScores[i][j] > 0.9) {
                    int classId = classIds[i][j];
                    classCounts.put(classId, classCounts.getOrDefault(classId, 0) + 1);
                }
            }
        }

        // 绘制边界框和类别标签、置信度
        for (int i = 0; i < confidenceScores.length; i++) {
            for (int j = 0; j < confidenceScores[i].length; j++) {
                if (confidenceScores[i][j] > 0.9) {
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
        opencv_imgproc.resize(frame, frame, new org.bytedeco.opencv.opencv_core.Size(1152, 720));
        return frame;
    }

    @Override
    public Result close() throws Exception {
        try {
            STOP = true;
            sleep(2000);
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
        return Result.success("任务结束");
    }

}
