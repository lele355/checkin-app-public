package cn.anlele.checkin.data

/**
 * 备案信息。法规要求：ICP 备案号与公安联网备案都要在「关于 / 设置」等显著位置标明，
 * 且能点进官方查询页供人核对。
 *
 * 【两种备案，编号规则完全不同】
 * 1. **ICP 备案**（工信部）：
 *    省简称 + `ICP备` + 主体序列号 + 号 + `-序号`。App 的序号后面多一个大写 `A`。
 *    本主体下网站是 `-1`，App 是第 2 个服务项 → `-2A`。
 * 2. **公安联网备案**（属地公安机关）：
 *    省简称 + `公网安备` + 6 位属地代码 + 2 位标注位(02) + 6 位顺序码。
 *    **编号是按域名 / 网站发的，App 不发号** —— 实测公共查询
 *    （beian.mps.gov.cn → 公共查询）里，查网站会列出「公安备案号」一行，
 *    查 App 只列「名称 / 主体类型 / 开办者 / 注册地公安机关 / 注册时间」，没有编号字段。
 *    所以 App 侧写「已备案」并给核验入口，而不是编一个号，也不能拿网站的 `01655` 顶上。
 *
 * 【为什么除服务端下发之外还留一份内置值】
 * 服务端 `/app/version` 会下发备案信息（补正、换号时不用重新发版），
 * 但那个请求要联网：登录页还没登录、首次冷启动、断网都可能拿不到。
 * 备案信息不存在「暂时不显示」这种状态，所以内置一份兜底值 ——
 * 服务端配了就用服务端的，没配才回落到这里。
 */
object Beian {

    /** App 的 ICP 备案号（本机兜底值；服务端下发的优先） */
    const val APP_ICP = "京ICP备00000000号-2A"

    /**
     * App 的公安联网备案号 —— 目前**没有**（平台不发 App 编号，见上方说明）。
     * 万一以后属地公安补发，把号填到服务端 `App:AppPoliceBeiAn` 即可，代码不用动；
     * 这个常量只在断网时兜底。
     */
    const val APP_POLICE = ""

    /** 网站的公安联网备案号 —— 只在「网站」语境用，不要拿去标 App */
    const val SITE_POLICE = "京公网安备11000000000000号"

    /** 工信部备案管理系统（ICP 备案号统一指向这里） */
    const val ICP_URL = "https://beian.miit.gov.cn/"

    /** 网站的公安备案查询深链（带本主体 code） */
    const val POLICE_URL = "https://beian.mps.gov.cn/#/query/webSearch?code=11000000000000"

    /** 公共查询入口。App 没有编号，没法拼 code 深链，只能落到这里让人自己按名称查 */
    const val POLICE_QUERY = "https://beian.mps.gov.cn/"

    /** 取 App 的 ICP 备案号：服务端给了用服务端的，没给用内置值 */
    fun appIcp(fromServer: String?): String =
        if (!fromServer.isNullOrBlank()) fromServer.trim() else APP_ICP

    /** 取 App 的公安备案号；两边都空就返回空串（正常情况就是空，平台不发号） */
    fun police(fromServer: String?): String =
        if (!fromServer.isNullOrBlank()) fromServer.trim() else APP_POLICE

    /** 公安备案这一行显示什么：有号写号，没号写「已备案」 */
    fun policeText(fromServer: String?): String = police(fromServer).ifEmpty { "已备案" }

    /** 只有真拿到号才算「有编号」，界面据此决定用强调色（编号）还是灰字（状态） */
    fun hasPoliceCode(fromServer: String?): Boolean = police(fromServer).isNotEmpty()

    /** 有编号 → 带 code 的查询深链；没编号 → 公共查询首页 */
    fun policeUrl(fromServer: String?): String {
        val code = police(fromServer).filter { it.isDigit() }
        return if (code.isNotEmpty()) "https://beian.mps.gov.cn/#/query/webSearch?code=$code"
        else POLICE_QUERY
    }
}
