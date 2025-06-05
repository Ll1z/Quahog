Below is the complete English translation of the Quahog project README:

Quahog

**Introduction**

Graduate course project for the Advanced Programming course (Spring Semester), School of Information Engineering, China University of Geosciences, Beijing.

**Software Architecture**

Architecture Overview  
Tech Stack:  
1.Core Framework: Spring Boot (Microservices Architecture)

2.Video Processing: JavaCV (based on OpenCV/FFmpeg)

3.Model Inference: ONNX Runtime (Cross-platform Model Deployment)

Dependency Management: Maven

Logging System: SLF4J + Logback  
1.Layered Architecture:

API Layer: RESTful Interfaces (Spring MVC)

Business Layer: Video Stream Processing Pipeline (Multi-threaded Concurrency)

Model Layer: YOLOv8 ONNX Model Loading & Inference

Resource Layer: Configuration Management (application.yml)  
2.Video Stream Processing Pipeline:


Stream Pull Module: Fetches video streams from RTMP sources via JavaCV.

Detection Module: YOLOv8 model preprocessing, inference, and post-processing (with NMS).

Stream Push Module: Encodes via FFmpeg and pushes to RTMP servers.  
3.Key Optimizations:

Dynamic thread pool management

DIoU-NMS for improved overlapping object detection

GPU-accelerated inference (ONNX Runtime)

**Installation Guide**

Environment Setup  
1.Hardware: NVIDIA GPU with CUDA support (optional)

2.Software:

JDK 17+

Maven 3.8+

Docker (optional for deployment)

Steps:  
1.Clone Repository:

      git clone https://gitee.com/li-longzhou/quahog.git

2.Install Dependencies:

      mvn clean install

3.Configure Model & Credentials:

Place ONNX models in src/main/resources/models.

Modify application.yml with RTMP addresses:

          app:  
       rtmp:  
         pull-url: rtmp://your-source-server  
         push-url: rtmp://202.204.101.80:8082/live/{group_id}?secret=xxx  

4.Start the System:

      mvn spring-boot:run

5.Docker Deployment:

      docker build -t yolov8-detector .  
docker run -p 8080:8080 yolov8-detector


**Usage Instructions**

API Calls  
1.Start a Task:

      curl -X POST http://localhost:8080/mission/start \  
     -H "Content-Type: application/json" \  
     -d '{"groupId": "202501"}'  

2.Stop a Task:

      curl -X POST http://localhost:8080/mission/stop \  
     -H "Content-Type: application/json" \  
     -d '{"taskId": "task-202501"}'  


Visualization  
View Stream:

https://resources.ucanfly.com.cn:8081/live/live/{group_id}.flv  
Example: Group 1 → 202501.flv

**Contribution Guide**
1.Fork Repository: Fork the project via GitHub.

2.Create Branch:

      git checkout -b Feat_OptimizeNMS  

3.Submit Code: Follow Google Java Style Guide.

4.PR Process:

Push branch to remote repository.

Submit Pull Request with detailed description.

Pass CI automated tests (JUnit integration).

**Technical Highlights**
1.Real-time Performance Optimizations:

Multi-scale frame processing (Letterbox + Padding)

Dynamic batch processing (ONNX Runtime)

Low-latency streaming preset (zerolatency)  
2.YOLOv8 Enhanced Features:

Anchor-free design (high recall rate)

TaskAlignedAssigner for label allocation

Per-frame multi-object statistics  
3.Production-ready Features:

Spring Boot graceful shutdown

Error retry mechanism (max 5 attempts)

Structured logging (JSON format)

**Note:**  
Replace placeholders (e.g., your-source-server, secret=xxx) with actual values for deployment.

For GPU acceleration, ensure CUDA drivers and ONNX Runtime GPU dependencies are installed.

Refer to the original application.yml for full configuration options.