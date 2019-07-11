package com.arcsoft.arcfacedemo.util.face;

import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;

import com.arcsoft.arcfacedemo.model.FacePreviewInfo;
import com.arcsoft.arcfacedemo.util.TrackUtil;
import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.LivenessInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FaceHelper {
    private static final String TAG = "FaceHelper";
    private FaceEngine ftEngine;
    private FaceEngine frEngine;
    private FaceEngine flEngine;

    private Camera.Size previewSize;

    private List<FaceInfo> faceInfoList = new ArrayList<>();
    private ExecutorService frExecutor;
    private ExecutorService flExecutor;
    private LinkedBlockingQueue<Runnable> frThreadQueue = null;
    private LinkedBlockingQueue<Runnable> flThreadQueue = null;
    private FaceListener faceListener;
    //trackId相关
    private int currentTrackId = 0;
    private List<Integer> formerTrackIdList = new ArrayList<>();
    private List<Integer> currentTrackIdList = new ArrayList<>();
    private List<FaceInfo> formerFaceRectList = new ArrayList<>();

    private List<FacePreviewInfo> facePreviewInfoList = new ArrayList<>();
    private ConcurrentHashMap<Integer, String> nameMap = new ConcurrentHashMap<>();
    private static final float SIMILARITY_RECT = 0.3f;

    private FaceHelper(Builder builder) {
        ftEngine = builder.ftEngine;
        faceListener = builder.faceListener;
        currentTrackId = builder.currentTrackId;
        previewSize = builder.previewSize;
        frEngine = builder.frEngine;
        flEngine = builder.flEngine;
        /*
      fr 线程数，建议和ft初始化时的maxFaceNum相同
     */
        int frThreadNum = 5;
        if (builder.frThreadNum > 0) {
            frThreadNum = builder.frThreadNum;
        } else {
            Log.e(TAG, "frThread num must > 0,now using default value:" + frThreadNum);
        }
        int flThreadNum = 5;
        if (builder.flThreadNum > 0) {
            flThreadNum = builder.flThreadNum;
        } else {
            Log.e(TAG, "flThread num must > 0,now using default value:" + flThreadNum);
        }
        frThreadQueue = new LinkedBlockingQueue<Runnable>(frThreadNum);
        frExecutor = new ThreadPoolExecutor(1, frThreadNum, 0, TimeUnit.MILLISECONDS, frThreadQueue);
        flThreadQueue = new LinkedBlockingQueue<Runnable>(flThreadNum);
        flExecutor = new ThreadPoolExecutor(1, frThreadNum, 0, TimeUnit.MILLISECONDS, flThreadQueue);
        if (previewSize == null) {
            throw new RuntimeException("previewSize must be specified!");
        }
    }


    /**
     * 请求获取人脸特征数据，需要传入FR的参数，以下参数同
     *
     * @param nv21     NV21格式的图像数据
     * @param faceInfo 人脸信息
     * @param width    图像宽度
     * @param height   图像高度
     * @param format   图像格式
     * @param trackId  请求人脸特征的唯一请求码，一般使用trackId
     */
    public void requestFaceFeature(byte[] nv21, FaceInfo faceInfo, int width, int height, int format, Integer trackId) {
        if (faceListener != null) {
            if (frEngine != null && frThreadQueue.remainingCapacity() > 0) {
                frExecutor.execute(new FaceRecognizeRunnable(nv21, faceInfo, width, height, format, trackId));
            } else {
                faceListener.onFaceFeatureInfoGet(null, trackId);
            }
        }
    }

    /**
     * 请求获取活体检测结果，需要传入活体的参数，以下参数同
     *
     * @param nv21     NV21格式的图像数据
     * @param faceInfo 人脸信息
     * @param width    图像宽度
     * @param height   图像高度
     * @param format   图像格式
     * @param trackId  请求人脸特征的唯一请求码，一般使用trackId
     */
    public void requestFaceLiveness(byte[] nv21, FaceInfo faceInfo, int width, int height, int format, Integer trackId) {
        if (faceListener != null) {
            if (flEngine != null && flThreadQueue.remainingCapacity() > 0) {
                flExecutor.execute(new FaceLivenessDetectRunnable(nv21, faceInfo, width, height, format, trackId));
            } else {
                faceListener.onFaceFeatureInfoGet(null, trackId);
            }
        }
    }


    public void release() {
        if (!frExecutor.isShutdown()) {
            frExecutor.shutdownNow();
            frThreadQueue.clear();
        }
        if (!flExecutor.isShutdown()) {
            flExecutor.shutdownNow();
            flThreadQueue.clear();
        }
        if (faceInfoList != null) {
            faceInfoList.clear();
        }

        if (nameMap != null) {
            nameMap.clear();
        }

        nameMap = null;
        faceListener = null;
        faceInfoList = null;
    }

    public List<FacePreviewInfo> onPreviewFrame(byte[] nv21) {
        if (faceListener != null) {
            if (ftEngine != null) {
                faceInfoList.clear();
                long ftStartTime = System.currentTimeMillis();
                int code = ftEngine.detectFaces(nv21, previewSize.width, previewSize.height, FaceEngine.CP_PAF_NV21, faceInfoList);
                if (code != ErrorInfo.MOK) {
                    faceListener.onFail(new Exception("ft failed,code is " + code));
                } else {
//                    Log.i(TAG, "onPreviewFrame: ft costTime = " + (System.currentTimeMillis() - ftStartTime) + "ms");
                }

                refreshTrackId(faceInfoList);
            }
            facePreviewInfoList.clear();
            for (int i = 0; i < faceInfoList.size(); i++) {
                facePreviewInfoList.add(new FacePreviewInfo(faceInfoList.get(i), currentTrackIdList.get(i)));
            }
            return facePreviewInfoList;
        } else {
            facePreviewInfoList.clear();
            return facePreviewInfoList;
        }
    }

    /**
     * 人脸解析的线程
     */
    public class FaceRecognizeRunnable implements Runnable {
        private FaceInfo faceInfo;
        private int width;
        private int height;
        private int format;
        private Integer trackId;
        private byte[] nv21Data;

        private FaceRecognizeRunnable(byte[] nv21Data, FaceInfo faceInfo, int width, int height, int format, Integer trackId) {
            if (nv21Data == null) {
                return;
            }
            this.nv21Data = nv21Data;
            this.faceInfo = new FaceInfo(faceInfo);
            this.width = width;
            this.height = height;
            this.format = format;
            this.trackId = trackId;
        }

        @Override
        public void run() {
            if (faceListener != null && nv21Data != null) {
                if (frEngine != null) {
                    FaceFeature faceFeature = new FaceFeature();
                    int frCode;
                    synchronized (FaceHelper.this) {
                        frCode = frEngine.extractFaceFeature(nv21Data, width, height, format, faceInfo, faceFeature);
                    }
                    if (frCode == ErrorInfo.MOK) {
                      faceListener.onFaceFeatureInfoGet(faceFeature, trackId);
                    } else {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        faceListener.onFaceFeatureInfoGet(null, trackId);
                        faceListener.onFail(new Exception("fr failed errorCode is " + frCode));
                    }
                } else {
                    faceListener.onFaceFeatureInfoGet(null, trackId);
                    faceListener.onFail(new Exception("fr failed ,frEngine is null"));
                }
            }
            nv21Data = null;
        }
    }

    /**
     * 活体检测的线程
     */
    public class FaceLivenessDetectRunnable implements Runnable {
        private FaceInfo faceInfo;
        private int width;
        private int height;
        private int format;
        private Integer trackId;
        private byte[] nv21Data;

        private FaceLivenessDetectRunnable(byte[] nv21Data, FaceInfo faceInfo, int width, int height, int format, Integer trackId) {
            if (nv21Data == null) {
                return;
            }
            this.nv21Data = nv21Data;
            this.faceInfo = new FaceInfo(faceInfo);
            this.width = width;
            this.height = height;
            this.format = format;
            this.trackId = trackId;
        }

        @Override
        public void run() {
            if (faceListener != null && nv21Data != null) {
                if (flEngine != null) {
                    List<LivenessInfo> livenessInfoList = new ArrayList<>();
                    int flCode;
                    flCode = flEngine.process(nv21Data, width, height, format, Arrays.asList(faceInfo), FaceEngine.ASF_LIVENESS);
                    if (flCode == ErrorInfo.MOK) {
                        flCode = flEngine.getLiveness(livenessInfoList);
                    }

                    if (flCode == ErrorInfo.MOK && livenessInfoList.size() > 0) {
                        faceListener.onFaceLivenessInfoGet(livenessInfoList.get(0), trackId);
                    } else {
                        faceListener.onFaceLivenessInfoGet(null, trackId);
                        faceListener.onFail(new Exception("fl failed errorCode is " + flCode));
                    }
                } else {
                    faceListener.onFaceLivenessInfoGet(null, trackId);
                    faceListener.onFail(new Exception("fl failed ,frEngine is null"));
                }
            }
            nv21Data = null;
        }
    }

    /**
     * 刷新trackId
     *
     * @param ftFaceList 传入的人脸列表
     */
    private void refreshTrackId(List<FaceInfo> ftFaceList) {
        currentTrackIdList.clear();
        //每项预先填充-1
        for (int i = 0; i < ftFaceList.size(); i++) {
            currentTrackIdList.add(-1);
        }
        //前一次无人脸现在有人脸，填充新增TrackId
        if (formerTrackIdList.size() == 0) {
            for (int i = 0; i < ftFaceList.size(); i++) {
                currentTrackIdList.set(i, ++currentTrackId);
            }
        } else {
            //前后都有人脸,对于每一个人脸框
            for (int i = 0; i < ftFaceList.size(); i++) {
                //遍历上一次人脸框
                for (int j = 0; j < formerFaceRectList.size(); j++) {
                    if (formerFaceRectList .get(j).getFaceId() == ftFaceList.get(i).getFaceId()){
                        currentTrackIdList.set(i, formerTrackIdList.get(j));
                        break;
                    }
                }
            }
        }
        //上一次人脸框列表不存在的人脸就是新进入的人脸，其trackId为currentTrackId递增值
        for (int i = 0; i < currentTrackIdList.size(); i++) {
            if (currentTrackIdList.get(i) == -1) {
                currentTrackIdList.set(i, ++currentTrackId);
            }
        }
        //刷新前帧数据
        formerTrackIdList.clear();
        formerFaceRectList.clear();
        for (int i = 0; i < ftFaceList.size(); i++) {
            formerFaceRectList.add(ftFaceList.get(i));
            formerTrackIdList.add(currentTrackIdList.get(i));
        }

        //刷新nameMap
        clearLeftName(currentTrackIdList);
    }

    /**
     * 获取当前的最大trackID,可用于退出时保存
     *
     * @return 当前trackId
     */
    public int getCurrentTrackId() {
        return currentTrackId;
    }

    /**
     * 新增搜索成功的人脸
     *
     * @param trackId 指定的trackId
     * @param name    trackId对应的人脸
     */
    public void addName(int trackId, String name) {
        if (nameMap != null) {
            nameMap.put(trackId, name);
        }
    }

    public String getName(int trackId) {
        return nameMap == null ? null : nameMap.get(trackId);
    }

    /**
     * 清除map中已经离开的人脸
     *
     * @param trackIdList 最新的trackIdList
     */
    private void clearLeftName(List<Integer> trackIdList) {
        Set<Integer> keySet = nameMap.keySet();
        for (Integer integer : keySet) {
            if (!trackIdList.contains(integer)) {
                nameMap.remove(integer);
            }
        }
    }

    public static final class Builder {
        private FaceEngine ftEngine;
        private FaceEngine frEngine;
        private FaceEngine flEngine;
        private Camera.Size previewSize;
        private FaceListener faceListener;
        private int frThreadNum;
        private int currentTrackId;
        private int flThreadNum;

        public Builder() {
        }


        public Builder flThreadNum(int val) {
            flThreadNum = val;
            return this;
        }

        public Builder ftEngine(FaceEngine val) {
            ftEngine = val;
            return this;
        }

        public Builder frEngine(FaceEngine val) {
            frEngine = val;
            return this;
        }

        public Builder flEngine(FaceEngine val) {
            flEngine = val;
            return this;
        }


        public Builder previewSize(Camera.Size val) {
            previewSize = val;
            return this;
        }


        public Builder faceListener(FaceListener val) {
            faceListener = val;
            return this;
        }

        public Builder frThreadNum(int val) {
            frThreadNum = val;
            return this;
        }

        public Builder currentTrackId(int val) {
            currentTrackId = val;
            return this;
        }

        public FaceHelper build() {
            return new FaceHelper(this);
        }
    }
}
