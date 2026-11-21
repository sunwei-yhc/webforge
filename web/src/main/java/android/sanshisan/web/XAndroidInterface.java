package android.sanshisan.web;

import android.app.Activity;

import io.github.sunwei.webforge.WebForge;
import io.github.sunwei.webforge.bridge.AndroidInterface;

/**
 * @Author 萤火虫
 * @Date 2021/7/23
 * @Desc 根据业务新增与业务相关的交互逻辑
 */
public class XAndroidInterface extends AndroidInterface {

    public XAndroidInterface(WebForge agent, Activity activity) {
        super(agent, activity);
    }
}
