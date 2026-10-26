package cn.anlele.checkin.data

/**
 * 备案信息（法规要求：ICP 备案号与公安联网备案号都要在「关于 / 设置」等显著位置展示，
 * 而且编号本身要能点进官方查询页供人核对）。
 *
 * 【为什么除服务端下发之外还留一份内置值】
 * 服务端 /app/version 会下发 App 备案号（补正、换号时不用重新发版），
 * 但那个请求要联网：登录页还没登录、首次冷启动、断网都可能拿不到。
 * 备案号不存在「暂时不显示」这种状态，所以内置一份兜底值 ——
 * 服务端配了就用服务端的，没配才回落到这里。
 *
 * 【编号规则】省简称 + ICP备 + 主体序列号 + 号 + 「-序号A」。
 * 本主体下网站占了第 1 个服务项（-1），App 是第 2 个，所以 App 号是 -2A。
 * 顺序号是主体下所有网站 / App 统一排的，App 只在末尾多一个大写 A ——
 * 不要写成 -2，那是「网站」的号。
 */
object Beian {

    /** App 备案号（本机兜底值；服务端下发的优先） */
    const val APP_ICP = "京ICP备00000000号-2A"

    /** 公安联网备案号（网站与 App 共用同一个联网备案主体编号） */
    const val POLICE = "京公网安备11000000000000号"

    /** 工信部备案管理系统（ICP 备案号统一指向这里） */
    const val ICP_URL = "https://beian.miit.gov.cn/"

    /** 全国互联网安全管理服务平台（公安备案号的查询页，带上本主体的 code） */
    const val POLICE_URL = "https://beian.mps.gov.cn/#/query/webSearch?code=11000000000000"

    /**
     * 取 App 备案号：服务端给了就用服务端的，没给（空串 / 没联网 / 还没请求到）用内置值。
     * 放在这里而不是各页面自己判断，是为了两边逻辑永远一致。
     */
    fun appIcp(fromServer: String?): String =
        if (!fromServer.isNullOrBlank()) fromServer.trim() else APP_ICP
}
