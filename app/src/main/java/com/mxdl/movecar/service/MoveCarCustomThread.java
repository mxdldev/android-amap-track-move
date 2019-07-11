package com.mxdl.movecar.service;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import com.amap.api.maps.model.LatLng;
import com.mxdl.movecar.MainActivity;
import com.mxdl.movecar.enums.MOVE_STATE;
import com.mxdl.movecar.util.TrackMoveUtil;
import java.lang.ref.WeakReference;
import java.util.List;
/**
 * Description: <MoveCarCustomThread><br>
 * Author:      mxdl<br>
 * Date:        2019/7/10<br>
 * Version:     V1.0.0<br>
 * Update:     <br>
 */
public class MoveCarCustomThread extends Thread {
    public static final String TAG = MoveCarCustomThread.class.getSimpleName();
    private Handler moveCarHandler;//发送数据的异步消息处理器
    private Object lock = new Object();//线程锁
    private boolean moveing = false;//是否线程正在移动
    private boolean pause = false;//暂停状态，为true则暂停
    private boolean stop = false;//停止状态，为true则停止移动
    private WeakReference<MainActivity> mActivityWeakReference;//防止内存Activity导致的内容泄漏
    private MOVE_STATE currMoveState = MOVE_STATE.START_STATUS;

    public void setCurrMoveState(MOVE_STATE currMoveState) {
        this.currMoveState = currMoveState;
    }

    public MOVE_STATE getCurrMoveState() {
        return currMoveState;
    }
    public MoveCarCustomThread(MainActivity activity) {
        mActivityWeakReference = new WeakReference<>(activity);
    }
    //暂停移动
    public void pauseMove() {
        pause = true;
    }
    //设置暂停之后，再次移动调用它
    public void reStartMove() {
        synchronized (lock) {
            pause = false;
            lock.notify();
        }
    }

    public void stopMove() {
        stop = true;
        if(moveCarHandler != null){
            moveCarHandler.removeCallbacksAndMessages(null);
        }
        if(mActivityWeakReference.get() != null){
            mActivityWeakReference.get().mLatLngList.clear();
            mActivityWeakReference.get().mMainHandler.removeCallbacksAndMessages(null);
        }
    }

    public Handler getMoveCarHandler() {
        return moveCarHandler;
    }
    public boolean isMoveing() {
        return moveing;
    }


    @Override
    public void run() {
        super.run();
        //设置该线程为loop线程
        Looper.prepare();
        moveCarHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                //通过锁保证发过来的数据同步入列
                synchronized (lock) {
                    if (msg.obj != null && msg.obj instanceof List) {
                        List<LatLng> latLngList = (List<LatLng>) msg.obj;
                        moveCoarseTrack(latLngList);
                    }
                }
            }
        };
        //启动loop线程
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
        double mTimeInterval = TrackMoveUtil.getMoveTime(distance, step);// 通过距离,计算轨迹动画时间间隔
        mTimeInterval = 0;// 每走一步停止10毫秒
        Log.v(TAG, "move mTimeInterval:" + mTimeInterval);

        moveing = true;
        for (int i = 0; i < latLngList.size() - 1; i++) {
            // 暂停状态，线程停止了
            if (pause) {
                movePause();
            }
            if (stop) {
                break;
            }
            moveing = true;
            LatLng startLatLng = latLngList.get(i);
            LatLng endLatLng = latLngList.get(i + 1);
            MainActivity mainActivity = mActivityWeakReference.get();
            moveCar(startLatLng, endLatLng, mainActivity);
            moveLine(startLatLng, mainActivity);
            moveCamera(startLatLng, mainActivity);

            double slope = TrackMoveUtil.getSlope(startLatLng, endLatLng);// 计算两点间的斜率
            double intercept = TrackMoveUtil.getInterception(slope, startLatLng);// 根据点和斜率算取截距
            boolean isReverse = (startLatLng.latitude > endLatLng.latitude);// 是不是正向的标示（向上设为正向）
            double xMoveDistance = isReverse ? TrackMoveUtil.getXMoveDistance(slope) : -1 * TrackMoveUtil.getXMoveDistance(slope);
            // 应该对经纬度同时处理
            double sleep = 0;
            int flag = 0;
            for (double j = startLatLng.latitude; !((j >= endLatLng.latitude) ^ isReverse); j = j - xMoveDistance) {
                // 非暂停状态地图才进行跟随移动
                if (pause) {
                    movePause();
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
                moveCar(startLatLng, mainActivity);
                moveLine(startLatLng, mainActivity);
                if (flag % 100 == 0) {
                    moveCamera(startLatLng, mainActivity);
                }
                // 如果间隔时间小于1毫秒,则略过当前休眠,累加直到休眠时间到1毫秒:会损失精度
                if (mTimeInterval < 1) {
                    sleep += mTimeInterval;
                    if (sleep >= 1) {
                        Log.v(TAG, "sleep:" + sleep);
                        SystemClock.sleep((long) sleep);
                        sleep = 0;
                    }
                } else {
                    SystemClock.sleep((long) mTimeInterval);
                }

            }
        }
        long endTime = System.currentTimeMillis();
        moveing = false;
        Log.v(TAG, "endTime:" + endTime);
        Log.v(TAG, "run mTimeInterval:" + (endTime - startTime));
        Log.v(TAG, "moveCoarseTrack end.........................................................");
    }

    private void moveLine(LatLng startLatLng, MainActivity mainActivity) {
        mainActivity.mLatLngList.add(startLatLng);// 向轨迹集合增加轨迹点
        mainActivity.mMovePolyline.setPoints(mainActivity.mLatLngList);// 轨迹画线开始
    }

    private void moveCar(LatLng startLatLng, LatLng endLatLng, MainActivity mainActivity) {
        moveCar(startLatLng,mainActivity);
        if (mainActivity.mCarMarker != null) {
            mainActivity.mCarMarker.setRotateAngle((float) TrackMoveUtil.getAngle(startLatLng, endLatLng));// 设置小车车头的方向
        }
    }
    private void moveCar(LatLng startLatLng,MainActivity mainActivity) {
        if (mainActivity.mCarMarker != null) {
            mainActivity.mCarMarker.setPosition(startLatLng);// 小车移动
        }
    }
    private void movePause() {
        try {
            lock.wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void moveCamera(LatLng startLatLng, MainActivity mainActivity) {
        Message message = Message.obtain();
        message.what = MainActivity.EventType.MapMove;
        message.obj = startLatLng;
        mainActivity.mMainHandler.sendMessage(message);
    }

}
