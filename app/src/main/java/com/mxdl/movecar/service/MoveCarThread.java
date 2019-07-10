package com.mxdl.movecar.service;

/**
 * Description: <MoveCarThread><br>
 * Author:      mxdl<br>
 * Date:        2019/7/10<br>
 * Version:     V1.0.0<br>
 * Update:     <br>
 */

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.utils.overlay.MovingPointOverlay;
import com.mxdl.movecar.MainActivity;
import com.mxdl.movecar.util.TrackMoveUtil;

import java.lang.ref.WeakReference;
import java.util.List;

public class MoveCarThread extends Thread {
    public static final String TAG = MoveCarThread.class.getSimpleName();
    private Handler moveCarHandler;
    private Object lock = new Object();
    private LatLng startLatLng;
    private boolean moveing = false;
    private boolean pause = false;
    private boolean stop = false;
    private MovingPointOverlay mMovingPointOverlay;
    private WeakReference<MainActivity> mActivityWeakReference;
    public void init(){
        moveing = false;
        pause = false;
        stop = false;
    }
    public MoveCarThread(MainActivity activity) {
        mActivityWeakReference = new WeakReference<>(activity);
    }

    public void reStart() {
        synchronized (lock) {
            pause = false;
            lock.notify();
        }
    }

    public void pause() {
        pause = true;
    }

    // 轨迹运动核心方法
    public Handler getMoveCarHandler() {
        return moveCarHandler;
    }

    public boolean isMoveing() {
        return moveing;
    }

    public void setMoveing(boolean moveing) {
        this.moveing = moveing;
    }

    @Override
    public void run() {
        super.run();
        Looper.prepare();
        moveCarHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                synchronized (lock) {
                    if (msg.obj != null && msg.obj instanceof List) {
                        List<LatLng> latLngList = (List<LatLng>) msg.obj;
                        if (msg.what == 0) {
                            moveCoarseTrack(latLngList);
                        } else {
                            moveSmoothTrack(latLngList);
                        }
                    }
                }
            }
        };
        Looper.loop();
    }

    private void moveCoarseTrack(List<LatLng> latLngList) {
        if (latLngList == null || latLngList.size() == 0 || latLngList.size() == 1) {
            return;
        }
        Log.v(TAG, "moveCoarseTrack start.........................................................");
        long startTime = System.currentTimeMillis();
        Log.v(TAG, "startTime:" + startTime);
        int step = TrackMoveUtil.getStep(latLngList);// 通过距离,计算轨迹动画运动步数
        Log.v(TAG, "move step:" + step);
        float distance = TrackMoveUtil.getDistance(latLngList);
        Log.v(TAG, "move distance:" + distance);
        double time = TrackMoveUtil.getMoveTime(distance, step);// 通过距离,计算轨迹动画时间间隔
        time = 0;// 每走一步停止10毫秒
        Log.v(TAG, "move time:" + time);

        moveing = true;
        for (int i = 0; i < latLngList.size() - 1; i++) {
            // 暂停状态，线程停止了
            if (pause) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (stop) {
                break;
            }
            moveing = true;
            LatLng startLatLng = latLngList.get(i);
            LatLng endLatLng = latLngList.get(i + 1);
            MainActivity mainActivity = mActivityWeakReference.get();
            if (mainActivity.mCarMarker != null) {
                mainActivity.mCarMarker.setPosition(startLatLng);// 小车移动
                mainActivity.mCarMarker.setRotateAngle((float) TrackMoveUtil.getAngle(startLatLng, endLatLng));// 设置小车车头的方向
            }
            mainActivity.mLatLngList.add(startLatLng);// 向轨迹集合增加轨迹点
            mainActivity.mMovePolyline.setPoints(mainActivity.mLatLngList);// 轨迹画线开始

            // 非暂停状态地图才进行跟随移动
            if (!pause) {
                Message message = Message.obtain();
                message.what = MainActivity.EventType.MapMove;
                message.obj = startLatLng;
                mainActivity.mMainHandler.sendMessage(message);
            }

            double slope = TrackMoveUtil.getSlope(startLatLng, endLatLng);// 计算两点间的斜率
            double intercept = TrackMoveUtil.getInterception(slope, startLatLng);// 根据点和斜率算取截距
            boolean isReverse = (startLatLng.latitude > endLatLng.latitude);// 是不是正向的标示（向上设为正向）
            double xMoveDistance = isReverse ? TrackMoveUtil.getXMoveDistance(slope) : -1 * TrackMoveUtil.getXMoveDistance(slope);
            // 应该对经纬度同时处理
            double sleep = 0;
            int flag = 0;
            for (double j = startLatLng.latitude; !((j >= endLatLng.latitude) ^ isReverse); j = j - xMoveDistance) {
                // 非暂停状态地图才进行跟随移动
                if (!pause) {
                    Message message = Message.obtain();
                    message.what = MainActivity.EventType.MapMove;
                    message.obj = startLatLng;
                    mainActivity.mMainHandler.sendMessage(message);
                }
                if (pause) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (stop) {
                    break;
                }

                moveing = true;
                flag++;
                if (slope != Double.MAX_VALUE) {
                    startLatLng = new LatLng(j, (j - intercept) / slope);
                } else {
                    startLatLng = new LatLng(j, startLatLng.longitude);
                }
                if (mainActivity.mCarMarker != null) {
                    mainActivity.mCarMarker.setPosition(startLatLng);// 小车微移动
                }
                mainActivity.mLatLngList.add(startLatLng);
                mainActivity.mMovePolyline.setPoints(mainActivity.mLatLngList);// 线段微增长

                // Log.v(TAG, "moveCoarseTrack mLatLngList
                // size:"+mLatLngList.size()+"......................................................");
                if (flag % 100 == 0) {
                    Message message = Message.obtain();
                    message.what = MainActivity.EventType.MapMove;
                    message.obj = startLatLng;
                    mainActivity.mMainHandler.sendMessage(message);
                }
                // 如果间隔时间小于1毫秒,则略过当前休眠,累加直到休眠时间到1毫秒:会损失精度
                if (time < 1) {
                    sleep += time;
                    if (sleep >= 1) {
                        SystemClock.sleep((long) sleep);
                        sleep = 0;
                    }
                } else {
                    SystemClock.sleep((long) time);
                }

            }
        }
        long endTime = System.currentTimeMillis();

        Log.v(TAG, "endTime:" + endTime);
        Log.v(TAG, "run time:" + (endTime - startTime));
        Log.v(TAG, "moveCoarseTrack end.........................................................");
    }

    // 轨迹运动核心方法
    private void moveSmoothTrack(List<LatLng> latLngList) {
        if (latLngList == null || latLngList.size() == 0) {
            return;
        }
        Log.v(TAG, "moveCoarseTrack start.........................................................");
        long startTime = System.currentTimeMillis();
        Log.v(TAG, "startTime:" + startTime);
        final MainActivity mainActivity = mActivityWeakReference.get();

        if (mMovingPointOverlay == null) {
            mMovingPointOverlay = new MovingPointOverlay(mainActivity.mAMap, mainActivity.mCarMarker);
            mMovingPointOverlay.setMoveListener(new MovingPointOverlay.MoveListener() {
                @Override
                public void move(double v) {
                    if (stop) {
                        mMovingPointOverlay.stopMove();
                        return;
                    }
                    LatLng position = mMovingPointOverlay.getPosition();
                    mainActivity.mLatLngList.add(position);// 向轨迹集合增加轨迹点
                    mainActivity.mMovePolyline.setPoints(mainActivity.mLatLngList);// 轨迹画线开始
                    Message message = Message.obtain();
                    message.what = MainActivity.EventType.MapMove;
                    message.obj = position;
                    mainActivity.mMainHandler.sendMessage(message);
                }
            });
        }
        mMovingPointOverlay.setTotalDuration(3);
        mMovingPointOverlay.setPoints(latLngList);
        mMovingPointOverlay.startSmoothMove();

        long endTime = System.currentTimeMillis();
        Log.v(TAG, "endTime:" + endTime);
        Log.v(TAG, "moveCoarseTrack end.........................................................");
    }

    public void stopMove() {
        stop = true;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

}
