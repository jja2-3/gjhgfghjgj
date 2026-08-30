package com.focus.moment.data.model

/** 日程分类 */
enum class Category(val label: String, val colorHex: Long) {
    STUDY("学习", 0xFF4C8DFF),
    WORK("工作", 0xFFFF9F43),
    EXERCISE("运动", 0xFF2ED573),
    READ("阅读", 0xFF9C6BFF),
    LIFE("生活", 0xFFFF6B81),
    OTHER("其他", 0xFF8D99AE);

    companion object {
        fun from(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** 计时方式 */
enum class TimerMode { COUNTDOWN, COUNTUP;

    companion object {
        fun from(name: String?): TimerMode = entries.firstOrNull { it.name == name } ?: COUNTDOWN
    }
}

/** 重复规则 */
enum class RepeatRule(val label: String) {
    ONCE("单次"), DAILY("每天"), WEEKLY("每周"), MONTHLY("每月"), YEARLY("每年"),
    WORKDAYS("工作日"), CUSTOM("自定义");

    companion object {
        fun from(name: String?): RepeatRule = entries.firstOrNull { it.name == name } ?: ONCE
    }
}

/** 待办类型 */
enum class TodoType(val label: String) {
    NORMAL("普通"), GOAL("定目标"), HABIT("养习惯");

    companion object {
        fun from(name: String?): TodoType = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/** 待办计时方式 */
enum class TodoTiming(val label: String) {
    COUNTDOWN("倒计时"), COUNTUP("正计时"), NONE("不计时");

    companion object {
        fun from(name: String?): TodoTiming = entries.firstOrNull { it.name == name } ?: COUNTDOWN
    }
}

/** 专注记录状态 */
enum class SessionStatus { COMPLETED, EARLY_FINISH, ABANDONED;

    companion object {
        fun from(name: String?): SessionStatus = entries.firstOrNull { it.name == name } ?: COMPLETED
    }
}

/** 结束提示铃声 */
enum class AlarmSound(val label: String) {
    BELL("钟声"), DROP("水滴"), CHIME("编钟"), ELECTRONIC("电子音"), WINDCHIME("风铃"), SYSTEM("系统铃声");

    companion object {
        fun from(name: String?): AlarmSound = entries.firstOrNull { it.name == name } ?: CHIME
    }
}

/** 提醒方式 */
enum class RemindMode(val label: String) {
    VIBRATE("仅震动"), SOUND("仅铃声"), BOTH("震动 + 铃声");

    companion object {
        fun from(name: String?): RemindMode = entries.firstOrNull { it.name == name } ?: BOTH
    }
}

/** 锁机强度 */
enum class LockMode(val label: String, val desc: String) {
    STRICT("严格模式", "锁机期间强制拉回 App，并屏蔽通知弹窗"),
    GENTLE("温和模式", "切屏或退出时仅提醒，不强制拉回");

    companion object {
        fun from(name: String?): LockMode = entries.firstOrNull { it.name == name } ?: STRICT
    }
}

/** 白噪音类型 */
enum class WhiteNoiseType(val label: String) {
    RAIN("雨声"), WAVE("海浪"), FOREST("森林"), FIRE("篝火"), WIND("风声"), CRICKET("夜晚虫鸣"),
    WHITE("白噪音"), STREAM("溪流"), THUNDER("雷雨"), BIRDS("清晨鸟鸣"), CAFE("咖啡馆"),
    SNOWSTORM("风雪夜"), LEAVES("雨打树叶");

    companion object {
        fun from(name: String?): WhiteNoiseType = entries.firstOrNull { it.name == name } ?: RAIN
    }
}

/** 专注会话在计时页的三阶段 */
enum class TimerPhase { IDLE, RUNNING, FINISHED }
