package dev.pcoffline.meadow.data

import kotlin.math.roundToInt

/**
 * How a Bumblebee has answered, worked out on their own phone from their own copies of the
 * requests sent to them. Nobody else sees it unless they share it.
 */
data class BeeStats(
    val asked: Int,
    val yes: Int,
    val no: Int,
    /** Answered by another Bumblebee, withdrawn or expired before this one answered. */
    val missed: Int,
    val waiting: Int,
    val minutesGranted: Int,
    val people: List<Person>,
    /** 0 = always no, 100 = always a full yes. Null until there are enough answers to say. */
    val leniency: Int?,
) {
    data class Person(
        val uid: String,
        val name: String,
        val flower: String,
        val asked: Int,
        val yes: Int,
        val no: Int,
    ) {
        val approvalRate: Float? get() = rate(yes, no)
    }

    val answered: Int get() = yes + no
    val approvalRate: Float? get() = rate(yes, no)

    enum class Leaning { VERY_STRICT, STRICT, BALANCED, GENTLE, VERY_GENTLE }

    val leaning: Leaning?
        get() = leniency?.let {
            when {
                it < 20 -> Leaning.VERY_STRICT
                it < 40 -> Leaning.STRICT
                it <= 60 -> Leaning.BALANCED
                it <= 80 -> Leaning.GENTLE
                else -> Leaning.VERY_GENTLE
            }
        }

    companion object {
        const val MIN_ANSWERS = 3

        private fun rate(yes: Int, no: Int): Float? = if (yes + no == 0) null else yes.toFloat() / (yes + no)

        /**
         * A no counts 0 and a yes counts 1, except a yes to extra time that gave less than asked,
         * which counts the share given.
         */
        fun from(inbox: List<LimitRequest>): BeeStats {
            var yes = 0
            var no = 0
            var missed = 0
            var waiting = 0
            var granted = 0
            var lenientSum = 0f
            for (r in inbox) {
                when {
                    r.myDecision == "yes" -> {
                        yes++
                        val given = if (r.type == RequestType.EXTRA_TIME && r.minutes > 0) {
                            granted += r.grantedMinutes
                            (r.grantedMinutes.toFloat() / r.minutes).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                        lenientSum += given
                    }
                    r.myDecision == "no" -> no++
                    r.status == RequestStatus.PENDING -> waiting++
                    else -> missed++
                }
            }
            val people = inbox.groupBy { it.requesterId }.map { (uid, rs) ->
                Person(
                    uid = uid,
                    name = rs.first().requesterName,
                    flower = rs.first().requesterFlower,
                    asked = rs.size,
                    yes = rs.count { it.myDecision == "yes" },
                    no = rs.count { it.myDecision == "no" },
                )
            }.sortedByDescending { it.asked }
            val answered = yes + no
            val leniency = if (answered >= MIN_ANSWERS) (100 * lenientSum / answered).roundToInt() else null
            return BeeStats(inbox.size, yes, no, missed, waiting, granted, people, leniency)
        }
    }
}
