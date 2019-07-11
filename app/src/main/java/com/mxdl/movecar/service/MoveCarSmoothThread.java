package com.mxdl.movecar.service;

/**
 * Description: <MoveCarCustomThread><br>
 * Author:      mxdl<br>
 * Date:        2019/7/10<br>
 * Version:     V1.0.0<br>
 * Update:     <br>
 */

import android.os.Message;
import android.util.Log;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.utils.overlay.MovingPointOverlay;
import com.mxdl.movecar.MainActivity;
import com.mxdl.movecar.contract.IMoveCar;
import com.mxdl.movecar.enums.MOVE_STATE;

import java.lang.ref.WeakReference;
import java.util.List;
/**
 * Description: <MoveCarCustomThread><br>
 * Author:      mxdl<br>
 * Date:        2019/7/10<br>
 * Version:     V1.0.0<br>
 * Update:     <br>
 */
public class MoveCarSmoothThread implements IMoveCar {
    public static final String TAG = MoveCarSmoothThread.class.getSimpleName();
    private MovingPointOverlay mMovingPointOverlay;
    private WeakReference<MainActivity> mActivityWeakReference;
    private boolean isfirst = true;
    private MOVE_STATE currMoveState = MOVE_STATE.START_STATUS;

    public void setCurrMoveState(MOVE_STATE currMoveState) {
        this.currMoveState = currMoveState;
    }

    public MOVE_STATE getCurrMoveState() {
        return currMoveState;
    }

    public MoveCarSmoothThread(MainActivity activity) {
        mActivityWeakReference = new WeakReference<>(activity);
    }
    @Override
    public void startMove(List<LatLng> latLngs) {
        if (latLngs == null || latLngs.size() == 0) {
            return;
        }

        Log.v("MYTAG","startMove start:"+Thread.currentThread().getName());
        Log.v(TAG, "moveCoarseTrack start.........................................................");
        long startTime = System.currentTimeMillis();
        Log.v(TAG, "startTime:" + startTime);
        final MainActivity mainActivity = mActivityWeakReference.get();
        if (mMovingPointOverlay == null) {
            mMovingPointOverlay = new MovingPointOverlay(mainActivity.mAMap, mainActivity.mCarMarker);
            mMovingPointOverlay.setTotalDuration(5);
            mMovingPointOverlay.setMoveListener(new MovingPointOverlay.MoveListener() {
                @Override
                public void move(double v) {
                    if(isfirst){
                        isfirst = false;
                        Log.v("MYTAG","MoveCarSmoolthThread move start:"+Thread.currentThread().getName());
                    }

                    LatLng position = mMovingPointOverlay.getPosition();
                    mainActivity.mLatLngList.add(position);// 向轨迹集合增加轨迹点
                    mainActivity.mMovePolyline.setPoints(mainActivity.mLatLngList);// 轨迹画线开始

                    Message message = Message.obtain();
                    message.what = MainActivity.EventType.MapMove;
                    message.obj = position;
                    message.arg1 = (int)v;
                    mainActivity.mMainHandler.sendMessage(message);
                }
            });
        }
        mMovingPointOverlay.setPoints(latLngs);
        mMovingPointOverlay.startSmoothMove();
        long endTime = System.currentTimeMillis();
        Log.v(TAG, "endTime:" + endTime);
        Log.v(TAG, "moveCoarseTrack end.........................................................");
    }

    @Override
    public void reStartMove() {
        if(mMovingPointOverlay != null){
            mMovingPointOverlay.startSmoothMove();
        }
    }
    @Override
    public void pauseMove(){
        if(mMovingPointOverlay != null){
            mMovingPointOverlay.stopMove();
        }
    }
    @Override
    public void stopMove(){
        if(mMovingPointOverlay != null){
            mMovingPointOverlay.destroy();
            mMovingPointOverlay = null;
        }
        if(mActivityWeakReference.get() != null){
            mActivityWeakReference.get().mLatLngList.clear();
        }
    }

}
