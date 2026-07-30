package com.sisariku.datapack;

import com.lowdragmc.lowdraglib2.plugin.ILDLibPlugin;
import com.lowdragmc.lowdraglib2.plugin.LDLibPlugin;

/**
 * CialloMineDatapack 的 LDLib2 插件入口。
 */
@LDLibPlugin
public class CialloMineDatapackLDLibPlugin implements ILDLibPlugin {

    @Override
    public void onLoad() {
        CialloMineDatapack.LOGGER.info("[CialloMineDatapack] LDLib2 插件已加载。");
    }
}
