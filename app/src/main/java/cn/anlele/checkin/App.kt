package cn.anlele.checkin

import android.app.Application
import cn.anlele.checkin.push.NotifyChannels

/**
 * 进程入口。
 *
 * 为什么必须有这个类：通知渠道必须在任何通知发出之前建好，
 * 而闹钟到点时是「广播唤醒进程」—— 这时不走 Activity，只有 Application.onCreate 一定会跑。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 幂等：渠道已存在就什么都不做；重要性建完只能降不能升，所以第一次就要定对
        NotifyChannels.create(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
