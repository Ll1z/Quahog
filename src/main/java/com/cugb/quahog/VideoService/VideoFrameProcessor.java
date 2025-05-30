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

}
