package com.cugb.quahog.VideoService;

import com.cugb.quahog.Pojo.Result;
import org.bytedeco.javacv.Frame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


public interface VideoFrameProcessor {
    Result start(String pull_url)throws Exception;
    void stop() throws InterruptedException;
    void stopNOW() ;
    Frame getFrame() throws InterruptedException;
    Result InitAndStart(String pull_url) throws Exception;
    Result PullStream() throws Exception;
    void FrameDetect() throws Exception;
    Result PushStream() throws Exception;
    Result close() throws Exception;
}
