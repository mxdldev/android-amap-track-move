package com.mxdl.movecar.contract;

import com.amap.api.maps.model.LatLng;

import java.util.List;

/**
 * Description: <IMoveCar><br>
 * Author:      mxdl<br>
 * Date:        2019/7/11<br>
 * Version:     V1.0.0<br>
 * Update:     <br>
 */
public interface IMoveCar {
    void startMove(List<LatLng> latLngs);
    void reStartMove();
    void pauseMove();
    void stopMove();
}
