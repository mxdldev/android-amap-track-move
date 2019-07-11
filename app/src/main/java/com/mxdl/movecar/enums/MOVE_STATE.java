package com.mxdl.movecar.enums;

/**
 * Description: <MOVE_STATE><br>
 * Author:      mxdl<br>
 * Date:        2019/7/11<br>
 * Version:     V1.0.0<br>
 * Update:     <br>
 */
public enum MOVE_STATE {
        START_STATUS(0,"初始状态"),
        MOVE_STATUS(1,"移动状态"),
        PAUSE_STATUS(2,"暂停状态"),
        FINISH_STATUS(3,"完成状态");
        int id;
        String name;
        MOVE_STATE(int id ,String name){
            this.id = id;
            this.name = name;
        }
}
